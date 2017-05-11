#!/usr/bin/env python

import sched, time

import click
import pandas as pd
from influxdb import DataFrameClient
import statsmodels.formula.api as smf
import json
from kazoo.client import KazooClient
import subprocess
import re

idle_cpu = 0
idle_ram = 0
df_idle = None

cpu_model_str = subprocess.Popen("cat /proc/cpuinfo | grep 'model name' | head -n 1 | cut -d ':' -f 2", shell=True, stdout=subprocess.PIPE).stdout.read()
cpu_model = re.match(".+\s+(.+)(?:\s+v?\d?)\s+@.+", cpu_model_str).group(1).lower()

@click.command()
@click.option('--influx-h', help='Influx host.')
@click.option('--influx-p', default=8086, help='Influx port.')
@click.option('--influx-usr', default='root', help='Influx user.')
@click.option('--influx-pwd', default='root', help='Influx pwd.')
@click.option('--influx-db', help='Influx database.')
@click.option('--zk-h', help='Zookeeper host')
@click.option('--zk-p', default=2181, help='Zookeeper port')
@click.option('--time-range-reg', default=2*60, help='Time range in seconds to retrieve data for the regression.')
@click.option('--time-range-rep', default=2*60, help='Time range in seconds to retrieve data for checking the error.')
@click.option('--interval', default=30, help='Interval to check the relative error in seconds.')
@click.option('--threshold', default=5, help='Relative error threshold in percent.')
@click.option('--agg', default="90%", type=click.Choice(["mean", "median", "75%", "90%"]), help='Aggregate method to use.')
def setup(influx_h, influx_p, influx_usr, influx_pwd, influx_db, zk_h, zk_p, time_range_reg, time_range_rep, interval, threshold, agg):
  global idle_cpu
  global idle_ram
  global df_idle
  influx = DataFrameClient(influx_h, influx_p, influx_usr, influx_pwd, influx_db)
  idle_rs = influx.query("select * from idlemon")

  if "idlemon" in idle_rs:
    idle_per_cmp = idle_rs["idlemon"].groupby(['socket']).median().sum()
    idle_cpu = idle_per_cmp["gcpu"]
    idle_ram = idle_per_cmp["gdram"]
    df_idle = idle_rs["idlemon"]

  zk = KazooClient(hosts=zk_h + ":" + str(zk_p))
  zk.start()

  s = sched.scheduler(time.time, time.sleep)
  periodic(s, interval, watch, (influx, zk, time_range_reg, time_range_rep, threshold, agg))
  s.run()

def periodic(scheduler, interval, action, args=()):
  scheduler.enter(interval, 1, periodic, (scheduler, interval, action, args))
  watch(*args)

def watch(influx, zk, time_range_reg, time_range_rep, threshold, agg):
  reporting_rs = influx.query(("select gcpu,vcpu from activerep where target = 'All' and time >= now() - %ds") % time_range_rep)
  if "activerep" in reporting_rs:
    df_reporting = reporting_rs["activerep"]
    stats = ((df_reporting["gcpu"].subtract(df_reporting["vcpu"] + idle_cpu)) / df_reporting["gcpu"]).abs().describe([0.5, 0.75, 0.90])
    err_in_percent = stats[agg] * 100

    if err_in_percent > threshold:
      monitoring_rs = influx.query(("select * from activemon where time >= now() - %ds") % time_range_reg)

      if "activemon" in monitoring_rs:
        df_monitoring = pd.concat([df_idle, monitoring_rs["activemon"]])
        sockets = set(df_monitoring["socket"])
        pmodels = []
        for socket in sockets:
          df_monitoring_socket = df_monitoring.loc[df_monitoring["socket"] == socket].sort_values("gcpu")
          columns = [ column for column in df_monitoring_socket.columns.values.tolist() if column.startswith("c") ]
          data4reg = df_monitoring_socket[columns + ['gcpu']]
          formula_to_find = 'gcpu ~ ' + ' + '.join(columns)
          lm = smf.ols(formula=formula_to_find, data=data4reg).fit()
          pmodels.append({"socket": socket, "coefficients": [ lm.params[column] for column in columns ]})
          print("Socket " + socket)
          print(lm.summary())
          print("")

        print("==============================================================================================")

        if not zk.exists("/" + cpu_model):
          zk.create("/" + cpu_model)

        zk.set("/" + cpu_model, b"%s" % json.dumps(pmodels))

if __name__ == '__main__':
  setup()
