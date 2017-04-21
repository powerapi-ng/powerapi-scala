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

import likwid.LikwidLibrary
import org.powerapi.module.hwc.RAPLDomain.RAPLDomain

case class HWThread(threadId: Int, coreId: Int, packageId: Int, apicId: Int, inCpuSet: Int)

case class CacheLevel(cacheType: Int, associativity: Int, sets: Int, lineSize: Int, size: Int, threads: Int, inclusive: Int)

case class CpuTopology(numSockets: Int, numCoresPerSocket: Int, numThreadsPerCore: Int, threadPool: Seq[HWThread], cacheLevels: Seq[CacheLevel])

case class AffinityDomain(tag: String, numberOfCores: Int, processorList: Seq[Int])

case class AffinityDomains(numberOfSocketDomains: Int, numberOfNumaDomains: Int, numberOfProcessorsPerSocket: Int, numberOfCacheDomains: Int, numberOfCoresPerCache: Int, numberOfProcessorsPerCache: Int, domains: Seq[AffinityDomain])

case class TurboBoost(steps: Array[Double])

case class PowerDomain(domainType: Int, supportFlags: Int, energyUnit: Double, tdp: Double, minPower: Double, maxPower: Double, maxTimeWindow: Double)

case class PowerInfo(baseFrequency: Double, minFrequency: Double, turbo: TurboBoost, hasRAPL: Boolean, powerUnit: Double, timeUnit: Double, uncoreMinFreq: Double, uncoreMaxFreq: Double, perfBias: Byte, domains: Array[PowerDomain])

case class PowerData(domain: Int, before: Int, after: Int)

case class CpuInfo(family: Int, model: Int, stepping: Int, clock: Long, turbo: Int, osname: String, name: String, shortName: String, features: String, isIntel: Int, supportUncore: Int, featureFlags: Int, perfVersion: Int, perfNumCtr: Int, perfWidthCtr: Int, perfNumFixedCtr: Int)

object RAPLDomain extends Enumeration {
  type RAPLDomain = Value
  val PKG = Value("rapl-cpu")
  val PP0 = Value
  val PP1 = Value
  val DRAM = Value("rapl-dram")
}

/**
  * Wrapper for the likwidHelper library.
  * Contains only the methods used in PowerAPI.
  * See likwidHelper documentation for further information about the native functions.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class LikwidHelper {

  def useDirectMode(): Unit = {
    // Direct access
    LikwidLibrary.INSTANCE.HPMmode(0)
  }

  def topologyInit(): Int = {
    LikwidLibrary.INSTANCE.topology_init()
  }

  def topologyFinalize(): Unit = {
    LikwidLibrary.INSTANCE.topology_finalize()
  }

  def affinityInit(): Unit = {
    LikwidLibrary.INSTANCE.affinity_init()
  }

  def affinityFinalize(): Unit = {
    LikwidLibrary.INSTANCE.affinity_finalize()
  }

  def powerInit(i: Int): Int = {
    LikwidLibrary.INSTANCE.power_init(i)
  }

  def powerFinalize(): Unit = {
    LikwidLibrary.INSTANCE.power_finalize()
  }

  def perfmonInit(threadsToCpu: Seq[Int]): Int = {
    LikwidLibrary.INSTANCE.perfmon_init(threadsToCpu.length, threadsToCpu.toArray)
  }

  def perfmonAddEventSet(events: String): Int = {
    LikwidLibrary.INSTANCE.perfmon_addEventSet(events)
  }

  def perfmonSetupCounters(groupId: Int): Int = {
    LikwidLibrary.INSTANCE.perfmon_setupCounters(groupId)
  }

  def perfmonStartCounters(): Int = {
    LikwidLibrary.INSTANCE.perfmon_startCounters()
  }

  def perfmonStopCounters(): Int = {
    LikwidLibrary.INSTANCE.perfmon_stopCounters()
  }

  def perfmonGetLastResult(groupId: Int, eventId: Int, hwThreadId: Int): Double = {
    LikwidLibrary.INSTANCE.perfmon_getLastResult(groupId, eventId, hwThreadId)
  }

  def perfmonFinalize(): Unit = {
    LikwidLibrary.INSTANCE.perfmon_finalize()
  }

  def HPMaddThread(core: Int): Unit = {
    LikwidLibrary.INSTANCE.HPMaddThread(core)
  }

  def powerStart(core: Int, domain: RAPLDomain): PowerData = {
    val likwidData = new likwid.PowerData()
    LikwidLibrary.INSTANCE.power_start(likwidData, core, domain.id)
    PowerData(likwidData.domain, likwidData.before, likwidData.after)
  }

  def powerStop(data: PowerData, core: Int): PowerData = {
    val likwidData = new likwid.PowerData(data.domain, data.before, data.after)
    LikwidLibrary.INSTANCE.power_stop(likwidData, core, data.domain)
    PowerData(likwidData.domain, likwidData.before, likwidData.after)
  }

  def getEnergy(data: PowerData): Double = {
    val likwidData = new likwid.PowerData(data.domain, data.before, data.after)
    LikwidLibrary.INSTANCE.power_printEnergy(likwidData)
  }

  def getCpuTopology(): CpuTopology = {
    val topology = LikwidLibrary.INSTANCE.get_cpuTopology()
    val threadPool = topology.threadPool.toArray(topology.numHWThreads).asInstanceOf[Array[likwid.HWThread]].map {
      hwThread =>
        HWThread(hwThread.threadId, hwThread.coreId, hwThread.packageId, hwThread.apicId, hwThread.inCpuSet)
    }
    val cacheLevels = topology.cacheLevels.toArray(topology.numCacheLevels).asInstanceOf[Array[likwid.CacheLevel]].map {
      cacheLevel =>
        CacheLevel(cacheLevel.`type`, cacheLevel.associativity, cacheLevel.sets, cacheLevel.lineSize, cacheLevel.size, cacheLevel.threads, cacheLevel.inclusive)
    }
    CpuTopology(topology.numSockets, topology.numCoresPerSocket, topology.numThreadsPerCore, threadPool, cacheLevels)
  }

  def getAffinityDomains(): AffinityDomains = {
    val affinities = LikwidLibrary.INSTANCE.get_affinityDomains()
    val domains = affinities.domains.toArray(affinities.numberOfAffinityDomains).asInstanceOf[Array[likwid.AffinityDomain]].map {
      affinityDomain =>
        AffinityDomain(affinityDomain.tag.data.getString(0), affinityDomain.numberOfCores, affinityDomain.processorList.getPointer.getIntArray(0, affinityDomain.numberOfProcessors))
    }
    AffinityDomains(affinities.numberOfSocketDomains, affinities.numberOfNumaDomains, affinities.numberOfProcessorsPerSocket, affinities.numberOfCacheDomains, affinities.numberOfCoresPerCache, affinities.numberOfProcessorsPerCache, domains)
  }

  def getPowerInfo(): PowerInfo = {
    val powerInfo = LikwidLibrary.INSTANCE.get_powerInfo()
    val tb = TurboBoost(powerInfo.turbo.steps.getPointer.getDoubleArray(0, powerInfo.turbo.numSteps))
    val powerDomains = powerInfo.domains.map {
      powerDomain =>
        PowerDomain(powerDomain.`type`, powerDomain.supportFlags, powerDomain.energyUnit, powerDomain.tdp, powerDomain.minPower, powerDomain.maxPower, powerDomain.maxTimeWindow)
    }
    PowerInfo(powerInfo.baseFrequency, powerInfo.minFrequency, tb, powerInfo.hasRAPL > 0, powerInfo.powerUnit, powerInfo.timeUnit, powerInfo.uncoreMinFreq, powerInfo.uncoreMaxFreq, powerInfo.perfBias, powerDomains)
  }

  def getCpuInfo(): CpuInfo = {
    val cpuInfo = LikwidLibrary.INSTANCE.get_cpuInfo()
    CpuInfo(
      cpuInfo.family, cpuInfo.model, cpuInfo.stepping, cpuInfo.clock, cpuInfo.turbo, cpuInfo.osname.getString(0),
      cpuInfo.name.getString(0), cpuInfo.short_name.getString(0), cpuInfo.features.getString(0), cpuInfo.isIntel, cpuInfo.supportUncore,
      cpuInfo.featureFlags, cpuInfo.perf_version, cpuInfo.perf_num_ctr, cpuInfo.perf_width_ctr, cpuInfo.perf_num_fixed_ctr
    )
  }
}

object LikwidHelper {

  def apply(): LikwidHelper = {
    new LikwidHelper()
  }
}
