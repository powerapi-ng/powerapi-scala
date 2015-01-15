/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
 *
 * PowerAPI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * PowerAPI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with PowerAPI.
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi

import akka.actor.{ActorRefFactory,Props}
import org.powerapi.core.Target
import scala.concurrent.duration.Duration

/**
 * Implements the main functionalities for configuring a <i>Software-Defined Power Meter</i>.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 */
class PowerMeter {
    /**
     * Triggers a new power monitoring for a specific set of targets at a given frequency.
     *
     * @param frequency Sampling frequency for estimating the power consumption.
     * @param targets System targets to be monitored (and grouped by timestamp).
     * @return the acknowledgment of the triggered power monitoring.
     */
    def monitor(frequency: Duration)(targets: Target*): PowerMonitoring = ???

    /**
     * Blocks and actively waits for a specific duration before returning.
     *
     * @param duration: duration to wait for.
     * @return the instance of the power meter.
     */
    def waitFor(duration: Duration): this.type = ???

    /**
     * Shuts down the current instance of power meter.
     */
    def shutdown(): Unit = ???
}

object PowerMeter {
    /**
     * Loads a specific power module as a tuple (sensor,formula).
     *
     * Example: `PowerMeter.load(PowerSpyModule)`
     *
     * @param modules: the list of power modules to be loaded within the PowerMeter.
     * @return the resulting instance of the requested power meter.
     */
    def load(modules: PowerModule*): PowerMeter = ???

    /**
     * Loads a specific display to render the power estimations produced by the power meter.
     *
     * Example: `PowerMeter.load(ConsoleDisplay.props)`
     *
     * @param display: the configuration of the power display to be loaded.
     * @return the resulting instance of the requested power display.
     */
    def load(display: Props): PowerDisplay = ???
}


/**
 * A PowerModule groups a sets of tightly coupled elements that need to be deployed together.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 */
trait PowerModule {
    /**
     * Initiate a power module
     */
    def start(factory: ActorRefFactory)

    /**
     * Stop a power module
     */
    def stop(factory: ActorRefFactory)
}

/**
 * Defines the interface that can be used to control a power monitoring.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 */
trait PowerMonitoring {
    /**
     * Configures the aggregation function to apply on power estimation per sample.
     */
    def apply(aggregator: (Seq[Power])=>Option[Power]): this.type

    /**
     * Configures the power display to use for rendering power estimations.
     */
    def to(output:PowerDisplay): this.type

    /**
     * Cancel the subscription and stops the associated monitoring.
     */
    def cancel()
}

/**
 * Defines the interface used by the power meter to configure the power display.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 */
trait PowerDisplay {
    /**
     * Tells the power display to listen on power reports sent to a specific channel.
     */
    def listen(channel:String)
}
