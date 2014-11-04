# PowerAPI
PowerAPI is a middleware toolkit for buidling software-defined power meters. Software-defined power meters are configurable software librairies that can estimate the power consumption of software in real-time. PowerAPI supports the acquisition of raw metrics from a wide diversity of sensors (*eg.*, physical meters, processor interfaces, hardware counters, OS counters) and the delivery of power consumptions via different channels (including filesystem, network, web, graphical). As a middleware toolkit, PowerAPI offers the capability of assemblying power meters *«à la carte»* to accomodate user requirements.

# About
PowerAPI is an open-source project developed by the [Spirals research group](https://team.inria.fr/spirals) (University of Lille 1 and Inria).

# Mailing list
You can follow the latest news and asks questions by subscribing to our [mailing list](https://sympa.inria.fr/sympa/info/powerapi).

# Contributing
If you would like to contribute code you can do so through GitHub by forking the repository and sending a pull request.

When submitting code, please make every effort to follow existing conventions and style in order to keep the code as readable as possible.

# Acknowledgments
We all stand on the shoulders of giants and get by with a little help from our friends. PowerAPI is written in [Scala](http://www.scala-lang.org) (version 2.11.4 under [3-clause BSD license](http://www.scala-lang.org/license.html)) and built on top of:
* [Akka](http://akka.io) (version 2.2.4 under [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0)), for asynchronous processing
* [Typesage Config](https://github.com/typesafehub/config) (version 1.2.1 under [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0)), for reading configuration files.

# Licence
This software is licensed under the *GNU Affero General Public License*, quoted below.

Copyright (C) 2011-2014 Inria, University of Lille 1.

PowerAPI is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

PowerAPI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along with PowerAPI. If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
