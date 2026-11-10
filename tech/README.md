# Technology notes for seatwave-estimates

These notes teach every technology this repository uses, from zero, through this repository's own files. They assume you have never seen a terminal or Java. Each file follows the same nine headings: what it is, why this repository uses it, where it appears, how it works underneath, a walkthrough of this repository's use, things to try, common mistakes, questions to check yourself, and official reading.

The repository is the hands-on project of Module 3 of a system design course, "Numbers before boxes". It holds estimation sheets in CSV for a ticket-booking case called SeatWave, an estimator that prints every step of the arithmetic, and a latency probe that times memory, disk and loopback network on your own machine. It is written in Java 25 and built with Gradle.

## The files

**The language, its library and its data formats**

| File | Role |
|---|---|
| `java.md` | Java 25 from zero: class, package, method, exception, records, sealed interfaces, enums, `switch` with patterns, `Optional`, streams, `BigDecimal`, `Duration`, `System.nanoTime`, arrays and the CPU cache, `FileChannel`, sockets, virtual threads, `Properties`, `String.format`, Javadoc, and the minimum Java version of each feature. |
| `result4j.md` | The result4j 1.2.0 library: a `Result` that is either a value or an error, and why expected failures are values here and exceptions are kept for programmer errors. |
| `csv-sheets.md` | The CSV format of the five sheets under `sheets/`: the columns, a row, how the program reads and checks them, and the planted mistake in `photo-sharing-given.csv`. |
| `java-properties.md` | The `.properties` format and `probe.properties`, key by key. |

**The build tool and its files**

| File | Role |
|---|---|
| `gradle.md` | Gradle: the build tool, `build.gradle.kts`, `settings.gradle.kts` and the tasks `estimate` and `probe`. |
| `gradle-wrapper.md` | The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) that downloads the right Gradle. |
| `posix-shell.md` | The `gradlew` script, which is a POSIX shell script. |
| `windows-batch.md` | The `gradlew.bat` script, a Windows batch file. |
| `toml-version-catalog.md` | `gradle/libs.versions.toml`, the one place for dependency versions. |
| `dependency-locking.md` | `gradle.lockfile` and `settings-gradle.lockfile`, which pin exact versions. |
| `spotless-google-java-format.md` | The formatter that the build enforces. |
| `jacoco.md` | The line-coverage tool and its 80 percent floor. |

**The code walkthroughs**

| File | Role |
|---|---|
| `java-units-walkthrough.md` | Every file of the `units` package, line by line: `Dimension`, `Unit` and `Quantity`, why adding two different units is refused, and powers of ten against powers of two. |
| `java-estimate-walkthrough.md` | Every file of the `estimate` package, line by line: the sheet reader, the input rules, the arithmetic of each estimate line, the sensitivity check, the report and the command. |
| `java-probe-walkthrough.md` | Every file of the `probe` package, line by line: warm-up, p50 and p99, the page cache, forced writes, the loopback round trip, the report. |

**The tests**

| File | Role |
|---|---|
| `junit.md` | JUnit: how the test engine finds and runs the tests. |
| `assertj.md` | AssertJ: the fluent assertions used in the tests. |
| `instancio.md` | Instancio: seeded generated amounts for the test that every printed quantity ends with its unit. |
| `tests-units-walkthrough.md` | The tests of the `units` package, line by line. |
| `tests-estimate-walkthrough.md` | The tests of the `estimate` package, line by line. |
| `tests-probe-walkthrough.md` | The tests of the `probe` package, line by line. |

**Everything else**

| File | Role |
|---|---|
| `docker-compose.md` | `compose.yaml`, the optional way to run the build in a container. |
| `editorconfig.md` | `.editorconfig`, the editor settings. |
| `git.md` | Git: the version control that tracks the files. |

## Reading order

1. **The language first.** `java.md`, then `result4j.md`. Do not skip `java.md`: every walkthrough relies on it.
2. **The data formats.** `csv-sheets.md` and `java-properties.md`: the two kinds of file the programs read.
3. **The build tool.** `gradle.md`, `gradle-wrapper.md`, `posix-shell.md`, `windows-batch.md`, `toml-version-catalog.md`, `dependency-locking.md`, `spotless-google-java-format.md`, `jacoco.md`.
4. **The code walkthroughs.** `java-units-walkthrough.md` first, because every number in the other two is a `Quantity`. Then `java-estimate-walkthrough.md` and `java-probe-walkthrough.md`.
5. **The tests.** `junit.md`, `assertj.md`, `instancio.md`, `tests-units-walkthrough.md`, `tests-estimate-walkthrough.md`, `tests-probe-walkthrough.md`.
6. **The rest.** `docker-compose.md`, `editorconfig.md`, `git.md`.

## If you have ten minutes

1. `java.md`: the words and features that every other file uses. Read the walkthrough sections on records, sealed interfaces, `switch` and `BigDecimal`.
2. `java-units-walkthrough.md`: the heart of the project. Read the parts on `Unit.java` and `Quantity.java` to see why `1 GB` plus `1 byte` is refused, and why a gigabyte (10^9 bytes) is not a gibibyte (2^30 bytes).
3. `java-probe-walkthrough.md`: the "How it works underneath" section, for what warm-up, p99, the CPU cache, the page cache and a forced write mean.

## How to run what the notes describe

Every command in these notes was run in this repository, on Java 25.0.2 and Gradle 9.9.1 through `./gradlew`, on 10 November 2026, on a Mac with an ARM processor (`aarch64`). Start with:

```
./gradlew -q estimate --args='sheets/seatwave-production.csv'
```

It prints the inputs, every step, the five-line estimate and the sensitivity rows. The last line of the five-line estimate is `=  COST      11,727.51 USD/month at the average fleet; the peak fleet costs 13.31 USD/hour`. Then run the probe, which times your own machine and prints different numbers on every run:

```
mkdir -p build/probe
./gradlew -q probe --args='probe.properties build/probe'
```