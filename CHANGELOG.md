# Changelog

All notable changes to this project are written here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-11-10

### Added

- `tech/`: 24 beginner notes, one per technology, with line-by-line walkthroughs of the main code,
  the tests, the build files and the wrapper scripts, and an index with a reading order.

### Fixed

- `.editorconfig` now states four spaces for `*.kts` files, which is what the build files use.

## [1.0.0] - 2026-11-10

### Added

- `Quantity`, `Unit` and `Dimension`: every number carries its unit, and two different units
  cannot be added or compared without an explicit conversion.
- Estimation sheets as CSV files: the national SeatWave (PRODUCTION), the 20,000-user SeatWave
  (SIMPLE), the Lab 1 sheet with unit slips, and the Lab 3 photo-sharing sheets.
- Estimator: reads a sheet, rejects every unit slip and missing input, prints every step of the
  arithmetic, the five-line estimate and a sensitivity check that flags a result moving more than
  10 times when one input doubles.
- Latency probe: the timer's step, array reads from cache and from memory, random file reads,
  forced file writes and a loopback TCP round trip, each with p50 and p99 against the published
  orders of magnitude.
- Tests with JUnit Jupiter, AssertJ and a seeded Instancio test.
- Build with a pinned Gradle wrapper, dependency lock files, `-Xlint:all -Werror`, Spotless with
  google-java-format, a JaCoCo line-coverage floor of 0.80 and an XML coverage report.
- Optional Docker Compose file that runs the build in a JDK 25 container.
