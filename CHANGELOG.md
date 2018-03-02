# Change Log

## [4.2.2](https://github.com/Spirals-Team/powerapi/tree/4.2.2) (2018-03-02)
[Full Changelog](https://github.com/Spirals-Team/powerapi/compare/4.2.1...4.2.2)

**Implemented enhancements:**

- The containers can now be monitored using theirs Name, short and full ID.
- Use of the container's name as target name instead of the full ID for the power measurements reporting.
- Trying to monitor an unknown container is considered as fatal error and PowerAPI will shutdown.
- Verify Docker socket availability when containers are monitored.
- Use the same working directory (/powerapi) for cli and cpu sampling Docker images.

## [4.2.1](https://github.com/Spirals-Team/powerapi/tree/4.2.1) (2018-02-19)
[Full Changelog](https://github.com/Spirals-Team/powerapi/compare/4.2...4.2.1)

**Implemented enhancements:**

- Rework of PowerAPI Docker images : based on Ubuntu Xenial (LTS), use of distribution packages instead of building the dependencies from sources

**Fixed bugs:**

- CPU sampling: Add filter to CPU device(s) selection
- RAPL: Remove MSR kernel module load (problems in Docker images)
- Travis CI build: Docker images are now properly pushed to Docker hub

## [4.2](https://github.com/Spirals-Team/powerapi/tree/4.2) (2017-11-02)
[Full Changelog](https://github.com/Spirals-Team/powerapi/compare/4.0...4.2)

**Implemented enhancements:**

- Update projects and libraries to Scala version 2.12
- Implement a disk module (only for Linux)
- Use an asynchronous API for InfluxDB
- Use the cgroup statistics to retrieve the cpu usage of a docker container
- Improving the resilience of power conversions

**Fixed bugs:**

- Bluecove libraries download URL for sbt
- Travis CI build: JDK version 7 to 8, Ruby installation, build failing on pull-request

**Closed issues:**

- Version mismatch [\#87](https://github.com/Spirals-Team/powerapi/issues/87)

## [4.0](https://github.com/Spirals-Team/powerapi/tree/4.0) (2016-04-14)
[Full Changelog](https://github.com/Spirals-Team/powerapi/compare/3.4...4.0)

**Implemented enhancements:**

- Use a settings file to declare the architectures compatible with RAPL [\#78](https://github.com/Spirals-Team/powerapi/issues/78)
- Packaging PowerAPI as docker images [\#77](https://github.com/Spirals-Team/powerapi/issues/77)
- feature\(v4.0\): refactoring [\#81](https://github.com/Spirals-Team/powerapi/pull/81) ([mcolmant](https://github.com/mcolmant))
- feature\(v4.0\): refactoring [\#80](https://github.com/Spirals-Team/powerapi/pull/80) ([mcolmant](https://github.com/mcolmant))

**Fixed bugs:**

- Cannot compile CLI: “error: not found: value Universal” [\#79](https://github.com/Spirals-Team/powerapi/issues/79)

## [3.4](https://github.com/Spirals-Team/powerapi/tree/3.4) (2016-02-02)
[Full Changelog](https://github.com/Spirals-Team/powerapi/compare/3.3...3.4)

**Closed issues:**

- Link error while using Sigar module [\#75](https://github.com/Spirals-Team/powerapi/issues/75)

## [3.3](https://github.com/Spirals-Team/powerapi/tree/3.3) (2015-11-03)
[Full Changelog](https://github.com/Spirals-Team/powerapi/compare/3.2...3.3)

**Implemented enhancements:**

- Refactors the PowerSpy module [\#53](https://github.com/Spirals-Team/powerapi/issues/53)
- Create the wiki with the documentation [\#46](https://github.com/Spirals-Team/powerapi/issues/46)
- Implement the filesystem interface [\#16](https://github.com/Spirals-Team/powerapi/issues/16)
- Implement the daemon mode [\#15](https://github.com/Spirals-Team/powerapi/issues/15)
- Implement the Web interface [\#14](https://github.com/Spirals-Team/powerapi/issues/14)

**Merged pull requests:**

- feature\(docker\): implement energy monitoring of a container [\#74](https://github.com/Spirals-Team/powerapi/pull/74) ([huertas](https://github.com/huertas))
- feature\(g5k-sensor\): implement the external powermeter OmegaWatt \(fro… [\#73](https://github.com/Spirals-Team/powerapi/pull/73) ([huertas](https://github.com/huertas))
- feature\(rest\): implement the REST reporter [\#72](https://github.com/Spirals-Team/powerapi/pull/72) ([huertas](https://github.com/huertas))
- feature\(fuse\): implement the FUSE reporter [\#71](https://github.com/Spirals-Team/powerapi/pull/71) ([huertas](https://github.com/huertas))
- feature\(daemon\): implement the PowerAPI daemon [\#70](https://github.com/Spirals-Team/powerapi/pull/70) ([huertas](https://github.com/huertas))

## [3.2](https://github.com/Spirals-Team/powerapi/tree/3.2) (2015-06-02)
[Full Changelog](https://github.com/Spirals-Team/powerapi/compare/3.1...3.2)

**Implemented enhancements:**

- Give the possibility to give directly the parameters to the PowerModule [\#60](https://github.com/Spirals-Team/powerapi/issues/60)
- Increase the actors timeout [\#59](https://github.com/Spirals-Team/powerapi/issues/59)
- Add prefix for each parameter written in configuration files [\#58](https://github.com/Spirals-Team/powerapi/issues/58)
- Adds the MUID as an argument in the PowerDisplay trait [\#56](https://github.com/Spirals-Team/powerapi/issues/56)
- Stops actors created for each PowerDisplay when a Monitor is canceled [\#55](https://github.com/Spirals-Team/powerapi/issues/55)
- Refactors the LibpfmChildSensor [\#54](https://github.com/Spirals-Team/powerapi/issues/54)
- Fix estimations returned by Sigar module under Windows [\#61](https://github.com/Spirals-Team/powerapi/issues/61)
- Implement the SIGAR module [\#17](https://github.com/Spirals-Team/powerapi/issues/17)
- Merge branch "develop" into "master" [\#69](https://github.com/Spirals-Team/powerapi/pull/69) ([mcolmant](https://github.com/mcolmant))
- fix\(\#58\): Refactors the Configuration trait [\#67](https://github.com/Spirals-Team/powerapi/pull/67) ([mcolmant](https://github.com/mcolmant))
- fix\(\#54\): Refactors the LibpfmChildSensor [\#66](https://github.com/Spirals-Team/powerapi/pull/66) ([mcolmant](https://github.com/mcolmant))
- refactor\(sampling\): adds the possibility to collect different counters and to use different type of regression [\#62](https://github.com/Spirals-Team/powerapi/pull/62) ([mcolmant](https://github.com/mcolmant))

**Fixed bugs:**

- Increase the actors timeout [\#59](https://github.com/Spirals-Team/powerapi/issues/59)
- Stops actors created for each PowerDisplay when a Monitor is canceled [\#55](https://github.com/Spirals-Team/powerapi/issues/55)
- Fix estimations returned by Sigar module under Windows [\#61](https://github.com/Spirals-Team/powerapi/issues/61)
- Merge branch "develop" into "master" [\#69](https://github.com/Spirals-Team/powerapi/pull/69) ([mcolmant](https://github.com/mcolmant))
- fix\(\#59\): Increases the actor timeouts [\#68](https://github.com/Spirals-Team/powerapi/pull/68) ([mcolmant](https://github.com/mcolmant))
- fix\(\#61\): closes \#61 [\#65](https://github.com/Spirals-Team/powerapi/pull/65) ([huertas](https://github.com/huertas))
- fix\(\#55\): fix the corresponding issue [\#63](https://github.com/Spirals-Team/powerapi/pull/63) ([mcolmant](https://github.com/mcolmant))

## [3.1](https://github.com/Spirals-Team/powerapi/tree/3.1) (2015-04-28)
**Implemented enhancements:**

- Implement the sampling project [\#44](https://github.com/Spirals-Team/powerapi/issues/44)
- Implement the Reporter actor [\#33](https://github.com/Spirals-Team/powerapi/issues/33)
- Implement the configuration interface. [\#24](https://github.com/Spirals-Team/powerapi/issues/24)
- Implement the CLI [\#13](https://github.com/Spirals-Team/powerapi/issues/13)
- Implement the Libpfm module [\#12](https://github.com/Spirals-Team/powerapi/issues/12)
- Implement the RAPL module [\#11](https://github.com/Spirals-Team/powerapi/issues/11)
- Implement the PowerSpy module [\#10](https://github.com/Spirals-Team/powerapi/issues/10)
- Implement the ProcFS module [\#9](https://github.com/Spirals-Team/powerapi/issues/9)
- Implement the PowerMeter API [\#8](https://github.com/Spirals-Team/powerapi/issues/8)
- Implement the subscription actor [\#7](https://github.com/Spirals-Team/powerapi/issues/7)
- Install and deploy the Jenkins infrastructure [\#6](https://github.com/Spirals-Team/powerapi/issues/6)
- Implement the new version of the Clock actor [\#5](https://github.com/Spirals-Team/powerapi/issues/5)
- Import the Topic Bus [\#4](https://github.com/Spirals-Team/powerapi/issues/4)
- Bootstrap the project structure [\#3](https://github.com/Spirals-Team/powerapi/issues/3)
- Add a Gitter chat badge to README.md [\#57](https://github.com/Spirals-Team/powerapi/pull/57) ([gitter-badger](https://github.com/gitter-badger))
- refactor\(sampling\): Improves the sampling project [\#51](https://github.com/Spirals-Team/powerapi/pull/51) ([mcolmant](https://github.com/mcolmant))
- feature\(sigar\): implement the sigar module [\#50](https://github.com/Spirals-Team/powerapi/pull/50) ([huertas](https://github.com/huertas))
- feat: version 3.0 of PowerAPI [\#49](https://github.com/Spirals-Team/powerapi/pull/49) ([rouvoy](https://github.com/rouvoy))
- Implement the CLI [\#48](https://github.com/Spirals-Team/powerapi/pull/48) ([huertas](https://github.com/huertas))
- Implement RAPL module [\#47](https://github.com/Spirals-Team/powerapi/pull/47) ([huertas](https://github.com/huertas))
- feature\(sampling\): Implement the sampling project [\#45](https://github.com/Spirals-Team/powerapi/pull/45) ([mcolmant](https://github.com/mcolmant))
- feature\(libpfm\): Sensors and Formula implementations [\#39](https://github.com/Spirals-Team/powerapi/pull/39) ([mcolmant](https://github.com/mcolmant))
- feature\(api\): Draft of the new power meter API. [\#38](https://github.com/Spirals-Team/powerapi/pull/38) ([rouvoy](https://github.com/rouvoy))
- Feature/reporter [\#36](https://github.com/Spirals-Team/powerapi/pull/36) ([huertas](https://github.com/huertas))
- Feature/powerspy [\#32](https://github.com/Spirals-Team/powerapi/pull/32) ([mcolmant](https://github.com/mcolmant))
- Feature/procfs module [\#31](https://github.com/Spirals-Team/powerapi/pull/31) ([mcolmant](https://github.com/mcolmant))
- hotfix/cleaning-imports [\#30](https://github.com/Spirals-Team/powerapi/pull/30) ([mcolmant](https://github.com/mcolmant))
- feature/configuration [\#23](https://github.com/Spirals-Team/powerapi/pull/23) ([mcolmant](https://github.com/mcolmant))
- feature/subscription [\#21](https://github.com/Spirals-Team/powerapi/pull/21) ([mcolmant](https://github.com/mcolmant))
- Design of base components + Clock [\#2](https://github.com/Spirals-Team/powerapi/pull/2) ([mcolmant](https://github.com/mcolmant))

**Fixed bugs:**

- \[develop\] Problem with a test in the OSHelperSuite [\#52](https://github.com/Spirals-Team/powerapi/issues/52)
- \[develop\] Problem with the Sensor in module/powerspy [\#43](https://github.com/Spirals-Team/powerapi/issues/43)
- \[develop\] Problem with the Sensor in module/cpu/simple [\#42](https://github.com/Spirals-Team/powerapi/issues/42)
- \[develop\] Problem with the LibpfmCoreConfiguration [\#41](https://github.com/Spirals-Team/powerapi/issues/41)
- \[develop\] Problem with the cache updating in the simple CpuSensor [\#40](https://github.com/Spirals-Team/powerapi/issues/40)
- Add a newline in each file for scanning tool [\#27](https://github.com/Spirals-Team/powerapi/issues/27)
- Problem with the clock suite test with limited resources [\#22](https://github.com/Spirals-Team/powerapi/issues/22)
- Bus is a singleton object [\#19](https://github.com/Spirals-Team/powerapi/issues/19)
- Error in ClockSuite when the EventFilter is used for listening log messages [\#18](https://github.com/Spirals-Team/powerapi/issues/18)
- fix\(\#56\): fix an issue [\#64](https://github.com/Spirals-Team/powerapi/pull/64) ([mcolmant](https://github.com/mcolmant))
- refactor\\(sampling\\): Improves the sampling project [\#51](https://github.com/Spirals-Team/powerapi/pull/51) ([mcolmant](https://github.com/mcolmant))
- hotfix/newline\#27 [\#28](https://github.com/Spirals-Team/powerapi/pull/28) ([mcolmant](https://github.com/mcolmant))
- hotfix/clocksuite\#22 [\#26](https://github.com/Spirals-Team/powerapi/pull/26) ([mcolmant](https://github.com/mcolmant))
- Bug \#19 [\#20](https://github.com/Spirals-Team/powerapi/pull/20) ([mcolmant](https://github.com/mcolmant))


\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*
