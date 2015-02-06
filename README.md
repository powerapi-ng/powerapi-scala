# PowerAPI
PowerAPI is a middleware toolkit for building software-defined power meters.
Software-defined power meters are configurable software libraries that can estimate the power consumption of software in real-time.
PowerAPI supports the acquisition of raw metrics from a wide diversity of sensors (*eg.*, physical meters, processor interfaces, hardware counters, OS counters) and the delivery of power consumptions via different channels (including file system, network, web, graphical).
As a middleware toolkit, PowerAPI offers the capability of assembling power meters *«à la carte»* to accommodate user requirements.

# About
PowerAPI is an open-source project developed by the [Spirals research group](https://team.inria.fr/spirals) (University of Lille 1 and Inria) and fully managed with [sbt](http://www.scala-sbt.org/).

## Mailing list
You can follow the latest news and asks questions by subscribing to our [mailing list](https://sympa.inria.fr/sympa/info/powerapi).

## Contributing
If you would like to contribute code you can do so through GitHub by forking the repository and sending a pull request.

When submitting code, please make every effort to follow existing conventions and style in order to keep the code as readable as possible.

## Publications
* **Process-level Power Estimation in VM-based Systems**: M. Colmant, M. Kurpicz, L. Huertas, R. Rouvoy, P. Felber, A. Sobe. *European Conference on Computer Systems* (EuroSys). April 2015, Bordeaux, France. pp.1-14. To appear.
* **[Monitoring Energy Hotspots in Software](https://hal.inria.fr/hal-01069142)**: A. Noureddine, R. Rouvoy, L. Seinturier. *Journal of Automated Software Engineering*, Springer, 2015, pp.1-42.
* **[Unit Testing of Energy Consumption of Software Libraries](https://hal.inria.fr/hal-00912613)**: A. Noureddine, R. Rouvoy, L. Seinturier. *International Symposium On Applied Computing* (SAC), Mar 2014, Gyeongju, South Korea. pp.1200-1205.
* **[Informatique : Des logiciels mis au vert](http://www.jinnove.com/Actualites/Informatique-des-logiciels-mis-au-vert)**: L. Seinturier, R. Rouvoy. *J'innove en Nord Pas de Calais*, [NFID](http://www.jinnove.com), 2013.
* **[PowerAPI: A Software Library to Monitor the Energy Consumed at the Process-Level](http://ercim-news.ercim.eu/en92/special/powerapi-a-software-library-to-monitor-the-energy-consumed-at-the-process-level)**: A. Bourdon, A. Noureddine, R. Rouvoy, L. Seinturier. *ERCIM News, Special Theme: Smart Energy Systems*, 92,  pp.43-44. [ERCIM](http://www.ercim.eu), 2013.
* **[Mesurer la consommation en énergie des logiciels avec précision](http://www.lifl.fr/digitalAssets/0/807_01info_130110_16_39.pdf)**: A. Bourdon, R. Rouvoy, L. Seinturier. *01 Business & Technologies*, 2013.
* **[A review of energy measurement approaches](https://hal.inria.fr/hal-00912996v2)**: A. Noureddine, R. Rouvoy, L. Seinturier. *ACM SIGOPS Operating Systems Review*, ACM, 2013, 47 (3), pp.42-49.
* **[Runtime Monitoring of Software Energy Hotspots](https://hal.inria.fr/hal-00715331)**: A. Noureddine, A. Bourdon, R. Rouvoy, L. Seinturier. *International Conference on Automated Software Engineering* (ASE), Sep 2012, Essen, Germany. pp.160-169.
* **[A Preliminary Study of the Impact of Software Engineering on GreenIT](https://hal.inria.fr/hal-00681560)**: A. Noureddine, A. Bourdon, R. Rouvoy, L. Seinturier. *International Workshop on Green and Sustainable Software* (GREENS), Jun 2012, Zurich, Switzerland. pp.21-27.


## Acknowledgments
We all stand on the shoulders of giants and get by with a little help from our friends. PowerAPI is written in [Scala](http://www.scala-lang.org) (version 2.11.4 under [3-clause BSD license](http://www.scala-lang.org/license.html)) and built on top of:
* [Akka](http://akka.io) (version 2.3.6 under [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0)), for asynchronous processing
* [Typesage Config](https://github.com/typesafehub/config) (version 1.2.1 under [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0)), for reading configuration files.
* [Apache log4j2](http://logging.apache.org/log4j/2.x/) (version 2.1 under [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0)), for logging outside actors.
* [powerspy.scala](https://github.com/Spirals-Team/powerspy.scala) (version 1.0.1 under [AGPL license](http://www.gnu.org/licenses/agpl-3.0.html)), for using the [PowerSpy powermeter](http://www.alciom.com/en/products/powerspy2-en-gb-2.html).
* [BridJ](https://code.google.com/p/bridj/) (version 0.6.2 under [3-clause BSD license](https://github.com/ochafik/nativelibs4java/blob/master/libraries/BridJ/LICENSE)), for system or C calls.
* [perfmon2](http://sourceforge.net/p/perfmon2/libpfm4/ci/master/tree/) (version 4.6.0 under [MIT license](http://sourceforge.net/p/perfmon2/libpfm4/ci/master/tree/COPYING)), for accessing hardware performance counters.
* [JFreeChart](http://www.jfree.org/jfreechart/) (version 1.0.19 under [LGPL license](https://www.gnu.org/licenses/lgpl.html)), for creation of interactive and animated charts,
* [Scala IO](http://jesseeichar.github.io/scala-io-doc/0.4.3/index.html#!/overview) (version 0.4.3 under [3-clause BSD license](http://www.scala-lang.org/license.html)), for an extensions of IO.

# License
This software is licensed under the *GNU Affero General Public License*, quoted below.

Copyright (C) 2011-2014 Inria, University of Lille 1.

PowerAPI is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

PowerAPI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along with PowerAPI. If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
