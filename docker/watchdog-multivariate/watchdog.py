import sched, time
import click
import pandas as pd
from influxdb import DataFrameClient
import json
from kazoo.client import KazooClient
import subprocess
import re
import hashlib
import numpy

from sklearn.linear_model import SGDRegressor
from sklearn.model_selection import cross_val_score
from sklearn.metrics import mean_absolute_error

df_idle = None
formulae_hash = "none"

cpu_model_str = subprocess.Popen("cat /proc/cpuinfo | grep 'model name' | head -n 1 | cut -d ':' -f 2", shell=True, stdout=subprocess.PIPE).stdout.read()
cpu_reg = re.compile(".+\s+(.+)(?:\s+v?\d?)\s+@.+")
cpu_model = cpu_reg.match(str(cpu_model_str)).group(1).lower()

@click.command()
@click.option('--influx-h', help='Influx host.', required=True)
@click.option('--influx-p', default=8086, help='Influx port.')
@click.option('--influx-usr', default='root', help='Influx user.')
@click.option('--influx-pwd', default='root', help='Influx pwd.')
@click.option('--influx-db', help='Influx database.', required=True)
@click.option('--zk-h', help='Zookeeper host', required=True)
@click.option('--zk-p', default=2181, help='Zookeeper port')
@click.option('--time-range', default=2*60, help='Time range in seconds for the checking/regression.')
@click.option('--interval', default=30, help='Interval to check the relative error in seconds.')
@click.option('--threshold', default=5, help='Absolute error to trigger a regression.')
def setup(influx_h, influx_p, influx_usr, influx_pwd, influx_db, zk_h, zk_p, time_range, interval, threshold):
    global idle_cpu
    global idle_ram
    global df_idle
    global formulae_hash

    influx = DataFrameClient(influx_h, influx_p, influx_usr, influx_pwd, influx_db)
    idle_rs = influx.query("select * from idlemon")

    if "idlemon" in idle_rs:
        df_idle = idle_rs["idlemon"]

    zk = KazooClient(hosts=zk_h + ":" + str(zk_p))
    zk.start()

    if not zk.exists("/" + cpu_model):
        zk.create("/" + cpu_model, b"")

    else:
        data, stat = zk.get("/" + cpu_model)
        if data.decode() != '':
            hash = hashlib.md5(data)
            formulae_hash = hash.hexdigest()

    s = sched.scheduler(time.time, time.sleep)
    periodic(s, interval, watch, (influx, zk, time_range, threshold))
    s.run()

def periodic(scheduler, interval, action, args=()):
    scheduler.enter(interval, 1, periodic, (scheduler, interval, action, args))
    watch(*args)

def watch(influx, zk, time_range, threshold):
    global formulae_hash

    reporting_rs = influx.query(("select * from activerep where hash = '%s' and target = 'All' and time >= now() - %ds") % (formulae_hash, time_range))

    if "activerep" in reporting_rs:
        df_reporting = reporting_rs["activerep"]
        error = mean_absolute_error(df_reporting["gcpu"], df_reporting["vcpu"] + df_reporting["vcpuidle"])

        if error > threshold:
            monitoring_rs = influx.query(("select * from activemon where time >= now() - %ds") % time_range)

            if "activemon" in monitoring_rs:
                df_monitoring = pd.concat([df_idle, monitoring_rs["activemon"]])
                sockets = set(df_monitoring["socket"])
                columns = [ column for column in df_monitoring.columns.values.tolist() if column.startswith("c") ]
                columns = sorted(columns, key=lambda x: int(x.replace("c", "")))
                errors = dict()
                pmodels = []

                for socket in sockets:
                    df_monitoring_socket = df_monitoring.loc[df_monitoring["socket"] == socket].sort_values("gcpu")
                    X = df_monitoring_socket[columns]
                    X = X / 1e09
                    X = X.as_matrix()
                    y = df_monitoring_socket['gcpu'].as_matrix()
                    clf = SGDRegressor(n_iter=100000)
                    clf.fit(X, y)
                    predictions = clf.predict(X)
                    scores = cross_val_score(clf, X, y, cv=5, scoring='neg_median_absolute_error')
                    coefficients = clf.intercept_.tolist() + clf.coef_.tolist()
                    errors[socket] = (numpy.mean(scores), mean_absolute_error(y, predictions))
                    pmodels.append({"socket": socket, "coefficients": coefficients})

                if len(pmodels) != 0:
                    zk.set("/" + cpu_model, json.dumps(pmodels).encode())
                    hash = hashlib.md5(json.dumps(pmodels).encode())
                    formulae_hash = hash.hexdigest()

                    now = pd.Timestamp(dt.datetime.now())
                    df_models_stats = pd.DataFrame(columns = ["c", "x", "x2", "cv5", "mae", "hash", "socket"])

                    for socket in sockets:
                        model = list(filter(lambda model: model["socket"] == socket, pmodels))[0]
                        socket_df = pd.DataFrame([[model["coefficients"][0], model["coefficients"][1], model["coefficients"][2], errors[socket][0], errors[socket][1], formulae_hash, socket]], columns = ["c", "x", "x2", "cv5", "mae", "hash", "socket"])
                        df_models_stats = df_models_stats.append(socket_df)

                    df_models_stats.index = [now, now]
                    influx.write_points(dataframe = df_models_stats, measurement = "statsmodels", tag_columns = ["hash", "socket"])

if __name__ == '__main__':
    setup()
