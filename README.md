# seatwave-estimates

An estimation sheet filled in for SeatWave, an estimator that prints every step of the arithmetic with its unit, and a latency probe that measures memory, disk and loopback network on your own machine.

## Which module this belongs to

This is the hands-on project of Module 3, "Numbers before boxes", of the course System Design Fundamentals. The running case is SeatWave, a ticket-booking service with four operations: list events, view seats, hold a seat and confirm a booking.

Module 3 turns business numbers (daily users, requests per user, bytes per record) into the five-line estimate: rate, size, storage, bandwidth and servers, plus the memory for a cache. It also measures the latency orders of magnitude the estimate leans on. This repository holds:

- The estimation sheets, as CSV files that any spreadsheet program opens and the estimator checks: `sheets/seatwave-production.csv` (the national SeatWave), `sheets/seatwave-simple.csv` (20,000 daily users), and the lab sheets.
- The estimator (`estimate` task), which rejects a sheet with a unit slip, prints every step, and flags any result that moves more than 10 times when one input doubles.
- The latency probe (`probe` task), which times array reads, file reads, forced file writes and a TCP round trip on the loopback address.

## Prerequisites

- JDK 25. The build was verified on Eclipse Temurin 25.0.2 on macOS on 10 November 2026. The current JDK 25 update on that date was 25.0.5 (build 25.0.5+8), which was not installed on the verification machine.
- Nothing else. The Gradle wrapper (9.9.1) downloads Gradle and the libraries below on the first build.

## Dependencies and build plugins

Every version is exact. Each was checked on Maven Central on 10 November 2026 and against the OSV database on the same day, with no known vulnerability at the pinned version. The versions live in `gradle/libs.versions.toml`, and `gradle.lockfile` pins the transitive ones. There is no new runtime library: the probe uses only the JDK (`System.nanoTime`, `FileChannel`, `Socket`).

| Coordinates | Version | Checked | License | Why the JDK alone is not enough |
|---|---|---|---|---|
| `com.github.sviperll:result4j` | 1.2.0 | 10 Nov 2026 | Apache-2.0 | The JDK has no success-or-typed-error type, so a rejected sheet would have to be an exception. |
| `org.junit:junit-bom` and `org.junit.jupiter:junit-jupiter` | 6.1.3 | 10 Nov 2026 | EPL-2.0 | The test engine: it finds and runs the tests. |
| `org.assertj:assertj-core` | 3.27.7 | 10 Nov 2026 | Apache-2.0 | Fluent assertions with clear failure messages. |
| `org.instancio:instancio-junit` | 6.2.1 | 10 Nov 2026 | Apache-2.0 | Seeded generated amounts for the test that every printed quantity ends with its unit. |
| `com.diffplug.spotless` (Gradle plugin) | 8.11.0 | 10 Nov 2026 | Apache-2.0 | Runs the formatter in the build. |
| `com.google.googlejavaformat:google-java-format` (used by Spotless) | 2.0.0 | 10 Nov 2026 | Apache-2.0 | One enforced code style, so nobody formats by hand. |
| `org.jacoco:org.jacoco.agent` and the JaCoCo Gradle plugin | 0.8.16 | 10 Nov 2026 | EPL-2.0 | Measures line coverage and enforces the floor. |

`org.junit.platform:junit-platform-launcher` is also on the test runtime path. Its version comes from the JUnit BOM, so it is 6.1.3 too. AssertJ 4.0.0-M1 exists on Maven Central; it is a milestone, so 3.27.7 is the latest stable release.

## Build and test

```
./gradlew clean check
```

This checks formatting with Spotless, compiles with `-Xlint:all -Werror`, runs the tests and fails if line coverage drops below 0.80. Run `./gradlew spotlessApply` first to fix formatting.

Result on 10 November 2026 (Temurin 25.0.2, macOS): `BUILD SUCCESSFUL`, 119 tests, 0 failures, line coverage 91.3 percent (900 of 986 lines). The untested lines are the `main` methods of `EstimateCli` and `ProbeCli`, which print and call `System.exit`, and a few failure branches of the probes.

## Run the estimator

```
./gradlew -q estimate --args='sheets/seatwave-production.csv'
```

The report has four parts: the inputs (printed as a sheet you can save and run again), every step with its formula, the five-line estimate, and the sensitivity check. The five-line estimate it printed:

```
THE FIVE-LINE ESTIMATE
  1. RATE      average 1,157 requests/s; peak 30,093 requests/s
  2. SIZE      101 GB/day of new data
  3. STORAGE   35.72 TB stored at the horizon; 821.60 USD/month
  4. BANDWIDTH peak egress 9.2 Gbps; 115 TB/month; 10,317.24 USD/month
  5. SERVERS   66 servers at peak, 4 servers at the average rate
  +  CACHE     0.925 GB for the hot set, against 18.5 GB for every key
  =  COST      11,727.51 USD/month at the average fleet; the peak fleet costs 13.31 USD/hour
```

The full output is long; Module 3, Section 8 shows all of it. The SIMPLE sheet:

```
./gradlew -q estimate --args='sheets/seatwave-simple.csv'
```

A sheet with unit slips is rejected before anything is estimated:

```
./gradlew -q estimate --args='sheets/lab1-unit-slips.csv'
```

```
REJECTED line 9: response_bytes must be in bytes, not bits
REJECTED line 12: store.bookings.bytes_per_item must be in bytes, not KB
REJECTED line 35: knee_per_server must be in requests/s, not requests/day
REJECTED missing input: headroom
SHEET REJECTED: fix every line above; nothing was estimated.
```

The program exits with status 1. Gradle reports that as a failed task. Exit status 2 means bad usage or an unreadable file; 3 means the estimate printed but a result was flagged by the sensitivity check.

## Run the latency probe

```
mkdir -p build/probe
./gradlew -q probe --args='probe.properties build/probe'
```

The whole command took 4.4 seconds on the verification machine: an Apple M2 Max Mac (model identifier Mac14,5) with 12 logical processors, 32 GiB of memory and its internal SSD, on macOS 27.2, with other programs running. These are that machine's numbers. Yours will differ.

```
Machine: Mac OS X 27.2 on aarch64, 12 logical processors, Java 25.0.2, heap limit 8192 MiB
Operation                        Samples        p50          p99          Published    Published line                  p50 / published
timer: two nanoTime calls        2,000 samples  0 ns         42 ns        -            none in the table               not compared
array read, cache-sized array    2,000 samples  2.13 ns      6 ns         0.5 ns       L1 cache reference              4.25 times
array read, memory-sized array   2,000 samples  102 ns       119 ns       100 ns       main memory reference           1.02 times
random 4 KiB file read           2,000 samples  1.25 µs      4.54 µs      150 µs       read 4K randomly from SSD       0.008333 times   <-- gap over 10 times: explain it
4 KiB write + force (fsync)      500 samples    4.02 ms      4.97 ms      -            none in the table               not compared
TCP round trip on loopback       2,000 samples  32.92 µs     61.25 µs     500 µs       round trip in one data center   0.06583 times   <-- gap over 10 times: explain it
These numbers describe this machine only. Yours will differ.
```

The probe listens only on the loopback address, on a port the operating system picks, and sends nothing to any other host. The temporary 64 MiB file is created in the folder you name and deleted at the end.

## The sheet

A sheet is a CSV file with the header `quantity,value,unit,source`. Lines that start with `#` are comments. A source that contains a comma is written in double quotes, as every spreadsheet program does. A value is a plain decimal number; underscores may group digits, so `5_000_000` is five million.

The estimator enforces these rules and reports every broken one:

- Every input below is present exactly once, written in exactly its unit. `KB` is not accepted anywhere, because it can mean 1,000 or 1,024 bytes.
- Every value is inside its range: shares from 0 to 100 percent, multipliers 1 or more, spare servers a whole number.
- Every line has a source.
- At least one store, and every store has all five fields.

| Quantity | Unit |
|---|---|
| `daily_active_users` | users |
| `requests_per_user_per_day` | requests/user/day |
| `peak_factor` | ratio (peak rate divided by the day's average rate) |
| `write_share` | percent |
| `request_bytes`, `response_bytes` | bytes |
| `horizon_years` | years (365 days each) |
| `store.<name>.items_per_day` | items/day |
| `store.<name>.bytes_per_item` | bytes |
| `store.<name>.retention_days` | days |
| `store.<name>.replication_factor` | copies |
| `store.<name>.index_overhead` | ratio |
| `cache_keys` | keys |
| `cache_hot_share` | percent |
| `cache_entry_bytes` | bytes |
| `cache_overhead` | ratio |
| `knee_per_server` | requests/s |
| `utilization_target`, `headroom` | percent |
| `spare_servers` | servers |
| `server_price` | USD/hour |
| `egress_price` | USD/GB |
| `storage_price` | USD/GB-month |

## Project structure

```
seatwave-estimates/
  build.gradle.kts            Build: Java 25, -Werror, Spotless, JaCoCo floor, one task per entry point
  settings.gradle.kts         Project name and the single repository (Maven Central)
  gradle/                     Wrapper files and libs.versions.toml, the one place for versions
  gradle.lockfile             Pinned transitive versions (settings-gradle.lockfile does the same for settings)
  probe.properties            Every setting of the latency probe; no defaults in code
  sheets/                     The estimation sheets, as CSV files
  tech/                       Beginner notes: one file per technology, plus line-by-line walkthroughs
  src/main/java/io/plainround/seatwave/estimates/
    units/                    Unit, Dimension and Quantity: a number is never without its unit
    estimate/                 Sheet reader, input rules, estimator, sensitivity check, report, the estimate command
    probe/                    Memory, disk and loopback probes, percentiles, report, the probe command
  src/test/java/io/plainround/seatwave/estimates/
    units/ estimate/ probe/   Tests for each package, including the shipped sheets
```

## Technology notes

The `tech/` folder explains every technology this repository uses, from zero: Java, Gradle and its wrapper scripts, the version catalog, dependency locking, the formatter, the coverage tool, the test libraries, the CSV and properties formats, Docker Compose, EditorConfig and Git. Six more files walk through the main code and the tests line by line. Start at `tech/README.md`, which gives a reading order.

## What is simplified for teaching

- Every input is an assumption with a source line, not a measurement, except the knee of 800 requests per second, which Module 1 scaled from a sandbox run.
- Egress is priced at the first tier of the price list for every byte. The later tiers are cheaper, so the egress cost is an upper bound.
- All stored data is priced at one object-storage price. A database volume costs more per gigabyte; Module 15 chooses where each store lives.
- The probe's file reads come mostly from the operating system's page cache, because the probe has just written the file. A production disk benchmark reads a file larger than memory, or bypasses the cache.
- The probe uses `System.nanoTime` loops with a warm-up, not JMH. It shows orders of magnitude, not precise micro-benchmarks.

## Continuous integration

There is no CI workflow in this repository on purpose. `./gradlew check` is the gate: run it before every commit.

## Docker Compose (optional)

`compose.yaml` runs `./gradlew --no-daemon check` inside `eclipse-temurin:25.0.5_8-jdk-noble`, with the repository mounted at `/workspace` and a named volume for `/root/.gradle`. The image tag was checked on Docker Hub on 10 November 2026.

```
docker compose up
```

[not run] Docker was not running on the verification machine, so this path was not tested. It would also use JDK 25.0.5, which is newer than the 25.0.2 used for every result above. Do not run the probe inside the container to describe your machine: a container adds its own file system and network layers.

## License

MIT. Copyright 2026 Sumit Srivastava. See `LICENSE`.
