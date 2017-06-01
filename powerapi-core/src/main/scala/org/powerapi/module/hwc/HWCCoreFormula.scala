/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2016 Inria, University of Lille 1.
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
package org.powerapi.module.hwc

import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.Actor
import com.twitter.util.Return
import com.twitter.zk.{ZNode, ZkClient}
import org.influxdb.InfluxDB
import org.influxdb.dto.Query
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.core.power._
import org.powerapi.core.target.{All, Target}
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.hwc.HWCChannel.{HWCReport, subscribeHWCReport, unsubscribeHWCReport}
import spray.json._

import scala.collection.JavaConverters._

import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

case class PowerModel(socket: String, coefficients: Seq[Double])

object PowerModelJsonProtocol extends DefaultJsonProtocol {
  implicit val powerModelFormat = jsonFormat2(PowerModel)
}

case class ZKTick(topic: String, timestamp: Long, cpuIdle: Double, formulaeHash: String) extends Tick

/**
  * Formula that uses cycles and ref_cycles to compute the power estimation.
  * ref_cycles is only used to compute the frequency coefficient.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class HWCCoreFormula(eventBus: MessageBus, muid: UUID, target: Target, likwidHelper: LikwidHelper, zkClient: ZkClient, influx: InfluxDB, influxDB: String, influxRp: String) extends Formula(eventBus, muid, target) {
  import PowerModelJsonProtocol._

  val affinityDomains = likwidHelper.getAffinityDomains()
  val powerInfo = likwidHelper.getPowerInfo()

  def init(): Unit = subscribeHWCReport(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribeHWCReport(muid, target)(eventBus)(self)

  def handler: Actor.Receive = {
    val ModelRegex = ".+\\s+(.+)(?:\\s+v?\\d?)\\s+@.+".r
    val cpuModel = likwidHelper.getCpuInfo().osname match {
      case ModelRegex(model) => model.toLowerCase
    }
    try {
      val zkNodeModel = zkClient(s"/$cpuModel")
      val monitoring = zkNodeModel.getData.monitor()

      monitoring.foreach {
        case Return(node) =>
          node.getData() onSuccess {
            case data: ZNode.Data =>
              val bytes = Array[Byte]() ++ data.bytes
              val formulae = {
                if (new String(bytes) == "") Seq()
                else new String(bytes).parseJson.convertTo[Seq[PowerModel]]
              }
              val hash = {
                if (new String(bytes) == "") "none"
                else MessageDigest.getInstance("MD5").digest(bytes).map(0xFF & _).map {
                  "%02x".format(_)
                }.mkString
              }
              context.become(compute(formulae, hash) orElse formulaDefault)
            case _ =>

          }
        case _ =>

      }
    }
    catch {
      case e: Exception =>

    }

    compute(Seq(), "none")
  }

  def compute(formulae: Seq[PowerModel], formulaeHash: String): Actor.Receive = {
    case msg: HWCReport =>
      //val now = System.nanoTime()

      // POLYNOMIAL
      /*val queryBuilder = new Query(s"select * from $influxRp.activemon where time = ${msg.tick.timestamp}ms", influxDB)
      var query = influx.query(queryBuilder, TimeUnit.MILLISECONDS)

      while (query.getResults.get(0).getSeries == null || (query.getResults.get(0).getSeries.size == 1 && query.getResults.get(0).getSeries.get(0).getValues.size != 2)) {
        Thread.sleep(5)
        query = influx.query(queryBuilder, TimeUnit.MILLISECONDS)
      }

      val socketColumn = allColumns.zipWithIndex.find(_._1 == "socket").get._2
      val coreColumns = allColumns.zipWithIndex.filter(_._1.startsWith("c")).map(_._2)
      val rows = query.getResults.get(0).getSeries.get(0).getValues.asScala

      val allSocketHwcs: Map[String, Double] = (for (row <- rows) yield {
        (row.get(socketColumn).asInstanceOf[String], (for (column <- coreColumns) yield row.get(column).asInstanceOf[Double]).sum)
      }).toMap

      */

      val queryBuilder = new Query(s"select * from $influxRp.activemon where time > now() - 10s", influxDB)
      var query = influx.query(queryBuilder, TimeUnit.MILLISECONDS)

      val allColumns = if (query.getResults.get(0).getSeries != null) query.getResults.get(0).getSeries.get(0).getColumns.asScala else Seq()
      val socketColumn = if (query.getResults.get(0).getSeries != null) allColumns.zipWithIndex.find(_._1 == "socket").get._2 else -1
      val coreColumns = if (query.getResults.get(0).getSeries != null) allColumns.zipWithIndex.filter(_._1.startsWith("c")).map(_._2) else Seq()

        val powers = for (socket <- affinityDomains.domains.filter(_.tag.startsWith("S"))) yield {
          if (formulae.nonEmpty) {
            val model = formulae.find(_.socket == socket.tag).get
            val sIndex = socket.tag.substring(1).toInt

            val targetCycles = msg.values.filter(_.hwThread.packageId == sIndex).filter(_.event == "CPU_CLK_UNHALTED_CORE:FIXC1").map(_.value).sum
            val socketCycles = {
              if (query.getResults.get(0).getSeries != null) {
                val socketLastRow = query.getResults.get(0).getSeries.get(0).getValues.asScala.filter(_.get(socketColumn) == socket.tag).last
                (for (column <- coreColumns) yield socketLastRow.get(column).asInstanceOf[Double]).sum
              }
              else targetCycles
            }


            val formula = model.coefficients.updated(0, 0d)
            val allPower = formula.zipWithIndex.foldLeft(0d)((acc, tuple) => acc + (tuple._1 * math.pow(socketCycles, tuple._2)))

            val ratio: Double = {
              if (socketCycles < 0) 0
              else if (targetCycles / socketCycles > 1) 1
              else targetCycles / socketCycles
            }

            val power = allPower * ratio
            if (power < 0) 0
            else power
          }

          else 0
        }

      // MULTIVARIATE -- TO REWRITE WITH INFLUX
      /*val powers = for (socket <- affinityDomains.domains.filter(_.tag.startsWith("S"))) yield {
          if (formulae.nonEmpty) {
            val formula = formulae.find(_.socket == socket.tag).get
            val sIndex = socket.tag.substring(1).toInt
            val socketHwcs = msg.values.filter(_.hwThread.packageId == sIndex)
            assert(socketHwcs.length == socket.processorList.length * 2)

            var index = 1
            val sPowers = for ((core, hwcs) <- ListMap(socketHwcs.groupBy(_.hwThread.coreId).toSeq.sortBy(_._1): _*)) yield {
              assert(hwcs.length == 4)

              val cycles = hwcs.filter(_.event == "CPU_CLK_UNHALTED_CORE:FIXC1").map(_.value).sum / 1e09
              val power = formula.coefficients(index) * cycles

              index += 1
              power
            }

            val power = sPowers.sum

            if (power < 0) 0
            else if (power > powerInfo.domains(RAPLDomain.PKG.id).tdp) powerInfo.domains(RAPLDomain.PKG.id).tdp
            else power
          }

          else 0
        }*/


      val accPower = {
        try {
          if (powers.sum > affinityDomains.domains.count(_.tag.startsWith("S")) * powerInfo.domains(RAPLDomain.PKG.id).tdp) {
            (affinityDomains.domains.count(_.tag.startsWith("S")) * powerInfo.domains(RAPLDomain.PKG.id).tdp).W
          }
          else powers.sum.W
        }
        catch {
          case _: Exception =>
            log.warning("The power value is out of range. Skip.")
            0.W
        }
      }

      val tick = ZKTick(msg.tick.topic, msg.tick.timestamp, formulae.map(_.coefficients.head).sum, formulaeHash)
      publishRawPowerReport(muid, target, accPower, "cpu", tick)(eventBus)
  }
}
