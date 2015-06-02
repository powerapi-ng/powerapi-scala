# Change Log

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

**Fixed bugs:**

- Increase the actors timeout [\#59](https://github.com/Spirals-Team/powerapi/issues/59)

- Stops actors created for each PowerDisplay when a Monitor is canceled [\#55](https://github.com/Spirals-Team/powerapi/issues/55)

- Fix estimations returned by Sigar module under Windows [\#61](https://github.com/Spirals-Team/powerapi/issues/61)

**Merged pull requests:**

- Merge branch "develop" into "master" [\#69](https://github.com/Spirals-Team/powerapi/pull/69) ([mcolmant](https://github.com/mcolmant))

- fix\(\#59\): Increases the actor timeouts [\#68](https://github.com/Spirals-Team/powerapi/pull/68) ([mcolmant](https://github.com/mcolmant))

- fix\(\#58\): Refactors the Configuration trait [\#67](https://github.com/Spirals-Team/powerapi/pull/67) ([mcolmant](https://github.com/mcolmant))

- fix\(\#54\): Refactors the LibpfmChildSensor [\#66](https://github.com/Spirals-Team/powerapi/pull/66) ([mcolmant](https://github.com/mcolmant))

- fix\(\#61\): closes \#61 [\#65](https://github.com/Spirals-Team/powerapi/pull/65) ([huertas](https://github.com/huertas))

- fix\(\#55\): fix the corresponding issue [\#63](https://github.com/Spirals-Team/powerapi/pull/63) ([mcolmant](https://github.com/mcolmant))

- refactor\(sampling\): adds the possibility to collect different counters and to use different type of regression [\#62](https://github.com/Spirals-Team/powerapi/pull/62) ([mcolmant](https://github.com/mcolmant))

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

**Merged pull requests:**

- fix\(\#56\): fix an issue [\#64](https://github.com/Spirals-Team/powerapi/pull/64) ([mcolmant](https://github.com/mcolmant))

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

- hotfix/newline\#27 [\#28](https://github.com/Spirals-Team/powerapi/pull/28) ([mcolmant](https://github.com/mcolmant))

- hotfix/clocksuite\#22 [\#26](https://github.com/Spirals-Team/powerapi/pull/26) ([mcolmant](https://github.com/mcolmant))

- feature/configuration [\#23](https://github.com/Spirals-Team/powerapi/pull/23) ([mcolmant](https://github.com/mcolmant))

- feature/subscription [\#21](https://github.com/Spirals-Team/powerapi/pull/21) ([mcolmant](https://github.com/mcolmant))

- Bug \#19 [\#20](https://github.com/Spirals-Team/powerapi/pull/20) ([mcolmant](https://github.com/mcolmant))

- Design of base components + Clock [\#2](https://github.com/Spirals-Team/powerapi/pull/2) ([mcolmant](https://github.com/mcolmant))



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*