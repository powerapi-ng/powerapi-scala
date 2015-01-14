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
 * API of a Software-Defined Power Meter.
 *
 * @author Romain Rouvoy <romain.rouvoy@univ-lille1.fr>
 */
class PowerMeter {
    /**
     * Start the monitoring of a given target with a predefined sampling rate.
     *
     * @param frequency: Sampling frequency for estimating the power consumption.
     * @param targets: System targets to be monitored (and grouped by timestamp).
     */
    def monitor(frequency: Duration)(targets: Target*): PowerSubscription = ???

    /**
     * Wait for a specific period before returning.
     *
     * @param duration: period to wait for.
     * @return the instance of the power meter.
     */
    def waitFor(duration: Duration): this.type = ???

    /**
     * Stop PowerAPI properly
     */
    def shutdown(): Unit = ???
}

object PowerMeter {
    /**
     * Load a specific PowerAPI module as a tuple (sensor,formula).
     *
     * @param modules: the list of PowerAPI modules to be loaded within the PowerMeter.
     * @return the resulting instance of the requested power meter.
     */
    def load(modules: PowerModule*): PowerMeter = ???

    def load(display: Props): PowerDisplay = ???
}


/**
 * A PowerModule groups a sets of tightly coupled elements that need to be deployed together.
 *
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
 * Acknowledgment of a power monitoring
 *
 */
trait PowerSubscription {
    def apply((aggregator: (Seq[PowerReport])=>Option[PowerReport])): this.type

    def to(output:PowerDisplay): this.type

    /**
     * Cancel the subscription and stops the associated monitoring.
     */
    def cancel()
}

trait PowerDisplay {
    def listen(topic:String)
}
