# Tests of the `probe` package, walked through

## What it is

The **latency probe** is a program that measures how long small operations take on your own machine: reading an array in memory, reading a block from a file, forcing a write to disk, and sending one byte over a network connection to your own computer (the **loopback** address, which never leaves the machine). **Latency** is the time one operation takes, usually measured in nanoseconds (ns, a billionth of a second), microseconds (µs, a millionth) or milliseconds (ms, a thousandth).

A program that measures time has a testing problem. The numbers it prints are different on every machine and on every run, so a test cannot say "the disk read took 1.25 µs". This file explains how the five test files of the `probe` package solve that: they test the **calculations** with exact hand-made numbers, and they test the **measuring code** only for facts that are true on every machine. `tests-units-walkthrough.md` explains what a test, an annotation and an assertion are, and `tests-estimate-walkthrough.md` explains the helper vocabulary that is reused here.

## Why this repository uses it

The probe teaches the orders of magnitude (memory about 100 ns, a read from a solid-state disk about 150 µs in the published table, a round trip inside a data center about 500 µs), and the README prints one real run. A learner will trust those numbers only if the program that prints them is correct: the percentile arithmetic, the settings checks, the comparison with the published table. Those parts are tested exactly. The `probe` package has the lowest coverage of the three packages, 233 of 290 lines (`jacoco.md`): `ProbeCli`, the `main` method that prints and exits, is not called by any test, and a few failure branches of the probes (for example "the echo server answered with the wrong byte") are not provoked.

## Where it appears

| Path | What it does |
| --- | --- |
| `src/test/java/io/plainround/seatwave/estimates/probe/LatencySamplesTest.java` | 6 tests of the percentile arithmetic. |
| `.../probe/ProbeReportTest.java` | 5 tests of the report: units, the 10 times gap marker, the published values. |
| `.../probe/ProbeSettingsFixtures.java` | A helper (not a test): small settings so the probes run in milliseconds. |
| `.../probe/ProbeSettingsTest.java` | 5 tests of the settings: the shipped file, missing keys, bad values. |
| `.../probe/ProbesTest.java` | 4 tests that run each probe once, with small settings. |
| `probe.properties` | The real settings file; `ProbeSettingsTest` reads it. |

## How it works underneath

1. The probe code takes its timings with `System.nanoTime()`, a Java method that returns the current reading of the machine's most precise timer, in nanoseconds. Two readings subtracted give an elapsed time. The values mean nothing on their own, only their difference.
2. The probe keeps many such differences, called **samples**, and sorts them. A **percentile** is a position in the sorted list: the 50th percentile (p50, the **median**) is the middle one, and the 99th (p99) is the value that 99 percent of the samples are not larger than. p99 shows the slow tail that a median hides.
3. The measuring code and the calculating code are separate classes. `LatencySamples` does the sorting and the percentiles. `ProbeReport` turns samples into the printed table. `ProbeSettings` reads and checks the settings. `MemoryProbe`, `DiskProbe` and `LoopbackProbe` take the measurements. Only the last three depend on the machine.
4. **Making it deterministic.** A test is deterministic when it gives the same result on every run and every machine. The tests here get there in five ways:
   - The calculating classes are tested with **hand-made samples**: arrays like `{120L}` or `{100_000L}` that a test writes itself. The expected output is exact, because no clock is involved.
   - The measuring classes are tested only for facts that do not depend on speed: how many samples came back, that a duration is greater than zero, that the temporary file was deleted.
   - The random choices of a probe (which array position or file offset comes next) use a **seeded** generator: `new Random(seed)` with a fixed number gives the same sequence on every run. The test settings use the seed 20261110.
   - The settings are tiny (50 samples, a 64 KiB file), so each test takes milliseconds instead of seconds.
   - The disk test works in a **temporary folder** that JUnit creates and deletes, and the network test uses only the loopback address and a port that the operating system picks, so no test touches your files or your network.

## Walkthrough of this repository's use

Words that first appear here.

- A **fixture class** such as `ProbeSettingsFixtures` holds prepared inputs for several tests.
- `long[]` is an **array** of `long` numbers (64-bit whole numbers). `new long[] {1L, 2L}` creates an array with two elements; the `L` marks a `long` number. `new long[100]` creates an array of 100 zeros. Arrays are indexed from 0.
- `1 << 16` is a **bit shift**: it means 1 times 2, sixteen times: 65,536. `1 << 10` is 1,024 and `1 << 12` is 4,096.
- A **Duration** is Java's object for a length of time, such as `Duration.ofSeconds(5)`.
- A **Properties** object is a table of text keys and text values that Java reads from a `.properties` file: lines of `key=value`, with `#` starting a comment.


### `LatencySamplesTest.java`

`LatencySamplesTest.java` tests `LatencySamples`: the class that holds the raw samples of one operation, sorts them, and answers "what is the p50?" or "what is the p99?". It also divides each sample by the number of operations that one sample timed (see the third test). It uses **nearest rank**: the p-th percentile of n samples is the sample at position ceiling(p times n divided by 100) in the sorted list, counting from 1. The tests use hand-made arrays, so every number is exact. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LatencySamplesTest {
```

- `BigDecimal` is used for the percentile argument: `new BigDecimal("99")` means exactly 99.
- `Arrays` is used by the second test to fill an array.
- The two constants `P50` and `P99` (shown with the first test) hold the percentiles used most.

```java
  private static final BigDecimal P50 = new BigDecimal("50");
  private static final BigDecimal P99 = new BigDecimal("99");
```

The two constants: 50 and 99, as exact decimals. `private static final` means a value that belongs to the class and never changes.

```java
  @Test
  void readsPercentilesByNearestRank() {
    long[] hundredSamples = new long[100];
    for (int sampleIndex = 0; sampleIndex < hundredSamples.length; sampleIndex++) {
      hundredSamples[sampleIndex] = sampleIndex + 1;
    }

    LatencySamples samples = new LatencySamples(hundredSamples, 1);

    assertThat(samples.percentile(P50).amount()).isEqualByComparingTo("50");
    assertThat(samples.percentile(P99).amount()).isEqualByComparingTo("99");
    assertThat(samples.percentile(new BigDecimal("100")).amount()).isEqualByComparingTo("100");
  }
```

A `for` loop fills an array of 100 samples with the values 1, 2, ..., 100: at position `sampleIndex` it stores `sampleIndex + 1`. `new LatencySamples(hundredSamples, 1)` builds the object; the second argument, 1, says each sample timed one operation. With 100 samples the p50 is the 50th value (50), the p99 is the 99th (99) and the p100 is the 100th (100). The `.amount()` call reads the number out of the `Quantity` that `percentile` returns. The arrays are hand-made, so this test is the same on every machine.

```java
  @Test
  void letsOneSlowSampleInAHundredShowOnlyInTheTail() {
    long[] mostlyFast = new long[100];
    Arrays.fill(mostlyFast, 100L);
    mostlyFast[0] = 1_000_000L;

    LatencySamples samples = new LatencySamples(mostlyFast, 1);

    assertThat(samples.percentile(P99).amount()).isEqualByComparingTo("100");
    assertThat(samples.percentile(new BigDecimal("100")).amount()).isEqualByComparingTo("1000000");
  }
```

99 samples of 100 ns and one sample of 1,000,000 ns (a one-in-a-hundred slow sample, such as a disk that paused). `Arrays.fill(mostlyFast, 100L)` sets every element to 100, then `mostlyFast[0] = 1_000_000L` changes the first. After sorting, the slow one is last. The p99 is the 99th value, still 100: one outlier in a hundred does not move the p99. The p100 is the maximum, 1,000,000. This is why the probe reports p99 beside p50: the p99 describes the usual tail, and only the maximum shows a one-off stall.

```java
  @Test
  void dividesEachSampleByTheOperationsItTimed() {
    LatencySamples samples = new LatencySamples(new long[] {100_000L}, 1_000);

    assertThat(samples.percentile(P50).toString()).isEqualTo("100 ns");
  }
```

A single read takes only a few nanoseconds, which the timer cannot see on its own (its own step is about as large). So the probe times 1,000 reads together and divides. The test: one sample of 100,000 ns that covers 1,000 operations is 100 ns per operation, printed `100 ns`. The second constructor argument, `1_000`, is the count of operations per sample.

```java
  @Test
  void copiesTheArraySoLaterChangesHaveNoEffect() {
    long[] raw = {10L, 20L, 30L};
    LatencySamples samples = new LatencySamples(raw, 1);

    raw[0] = 1_000L;

    assertThat(samples.percentile(new BigDecimal("1")).amount()).isEqualByComparingTo("10");
    assertThat(samples.count().toString()).isEqualTo("3 samples");
  }
```

A **defensive copy**: the object clones the array it is given. The test builds the object from `raw`, then changes `raw[0]` to 1,000, and checks that the 1st percentile is still the original smallest value, 10. Then `samples.count().toString()` is `3 samples`: a count is a `Quantity` in the unit `samples`. Without the copy, code outside could change the measurements after the fact.

```java
  @Test
  void rejectsNoSamplesANegativeSampleOrZeroOperations() {
    assertThatThrownBy(() -> new LatencySamples(new long[0], 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LatencySamples(new long[] {-1L}, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LatencySamples(new long[] {1L}, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

Three bad objects, each rejected with `IllegalArgumentException`: an empty array (nothing to measure), a negative duration (time never runs backwards, a sign of a broken measurement) and zero operations per sample (a division by zero later).

```java
  @Test
  void rejectsAPercentileOutsideItsRange() {
    LatencySamples samples = new LatencySamples(new long[] {1L}, 1);

    assertThatThrownBy(() -> samples.percentile(BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> samples.percentile(new BigDecimal("100.1")))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

A percentile must be above 0 and at most 100. Asking for 0 or for 100.1 is refused. The test builds one sample and asks for each of the two bad values.

### `ProbeReportTest.java`

`ProbeReportTest.java` tests `ProbeReport`, which prints the table of the probe: for each operation the sample count, p50, p99, the published value and the ratio between your p50 and the published one. The ratio is marked "explain it" when the gap is more than 10 times in either direction, because a gap that large means that your machine, or the published number, needs an explanation (for example the file read comes from the operating system's page cache). The tests give the report hand-made samples, so the table is the same on every machine. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.probe;

import static org.assertj.core.api.Assertions.assertThat;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProbeReportTest {
```

- `LinkedHashMap` is a table that keeps its entries in the order they were added, so the printed rows have a fixed order. `Map` is the general table type.
- `Quantity` and `Unit` come from the `units` package.

```java
  @Test
  void printsEachDurationInItsLargestReadableUnit() {
    assertThat(ProbeReport.readable(Quantity.of("98.5", Unit.NANOSECONDS)).toString())
        .isEqualTo("98.5 ns");
    assertThat(ProbeReport.readable(Quantity.of("12300", Unit.NANOSECONDS)).toString())
        .isEqualTo("12.3 µs");
    assertThat(ProbeReport.readable(Quantity.of("4100000", Unit.NANOSECONDS)).toString())
        .isEqualTo("4.1 ms");
  }
```

`ProbeReport.readable` turns a duration into the unit in which it reads best: under 1,000 ns it stays in nanoseconds (98.5 ns), from 1,000 ns up it becomes microseconds (12,300 ns is 12.3 µs) and from 1,000 µs up it becomes milliseconds (4,100,000 ns is 4.1 ms). Each line builds a quantity of nanoseconds and checks the printed text. The symbol `µs` is the real micro sign, which is in the unit table.

```java
  @Test
  void marksAGapOfMoreThanTenTimesInEitherDirection() {
    Map<ProbeOperation, LatencySamples> measured = new LinkedHashMap<>();
    measured.put(ProbeOperation.MEMORY_READ, new LatencySamples(new long[] {120L}, 1));
    measured.put(ProbeOperation.DISK_READ, new LatencySamples(new long[] {3_000L}, 1));
    measured.put(ProbeOperation.LOOPBACK_ROUND_TRIP, new LatencySamples(new long[] {40_000L}, 1));

    String report = ProbeReport.render(measured);

    assertThat(report.lines().filter(line -> line.contains("explain it"))).hasSize(2);
    assertThat(report).contains("1.2 times").contains("0.02 times").contains("0.08 times");
  }
```

Three operations are measured by hand-made samples: a memory read of 120 ns (the published value is 100 ns), a disk read of 3,000 ns (published 150,000 ns) and a loopback round trip of 40,000 ns (published 500,000 ns). The map is a `LinkedHashMap`, so the rows come out in the order they were added. `report.lines().filter(line -> line.contains("explain it"))` keeps the rows that carry the marker, and `hasSize(2)` checks that exactly two do: the disk (0.02 times: the published value is 50 times larger than measured) and the loopback (0.08 times: 12.5 times larger). The memory row, 1.2 times, is not marked. The last line checks that the three ratios `1.2 times`, `0.02 times` and `0.08 times` are printed. The test proves that the gap is judged in both directions: measured much smaller than published is flagged just like measured much larger.

```java
  @Test
  void comparesNothingWhereTheTableHasNoLine() {
    Map<ProbeOperation, LatencySamples> measured = new LinkedHashMap<>();
    measured.put(ProbeOperation.DISK_WRITE_FORCED, new LatencySamples(new long[] {50_000L}, 1));

    assertThat(ProbeReport.render(measured)).contains("not compared").contains("none in the table");
  }
```

The forced disk write has no line in the published table, so there is nothing to compare with. The report prints `not compared` and `none in the table` instead of a ratio.

```java
  @Test
  void saysWhenADurationIsBelowTheTimersResolution() {
    Map<ProbeOperation, LatencySamples> measured = new LinkedHashMap<>();
    measured.put(ProbeOperation.CACHE_READ, new LatencySamples(new long[] {0L}, 1));

    assertThat(ProbeReport.render(measured)).contains("below the timer's resolution");
  }
```

A sample of 0 ns means that the operation was shorter than one step of the timer. A ratio against zero is meaningless, so the report says `below the timer's resolution` instead of printing a ratio or failing on a division by zero.

```java
  @Test
  void keepsThePublishedValuesOfTheTable() {
    assertThat(ProbeOperation.MEMORY_READ.textbook())
        .contains(Quantity.of("100", Unit.NANOSECONDS));
    assertThat(ProbeOperation.TIMER_STEP.textbook()).isEmpty();
    assertThat(ProbeOperation.DISK_READ.label()).isEqualTo("random 4 KiB file read");
  }
```

The table values are part of the program: the memory read is published at 100 ns (`textbook()` returns an `Optional` that `contains` the quantity of 100 ns), the timer step has no published value (`isEmpty()`), and the disk read has the label `random 4 KiB file read`. If someone changes a published value by mistake, this test fails.

### `ProbeSettingsFixtures.java`

`ProbeSettingsFixtures.java` is a helper class, not a test: its Javadoc line says "Small settings, so the probes run in milliseconds inside a unit test." It is used by `ProbesTest`. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.probe;

import java.time.Duration;

/** Small settings, so the probes run in milliseconds inside a unit test. */
final class ProbeSettingsFixtures {
```

- `java.time.Duration` is the length-of-time type used for the socket timeout.
- `final class` means that nothing can extend it.

```java
  static final int SAMPLES = 50;
  static final int WARMUP = 5;
  static final int READS_PER_SAMPLE = 10;
  static final int MEMORY_BYTES = 1 << 16;
  static final int CACHE_BYTES = 1 << 10;
  static final int FILE_BYTES = 1 << 16;
  static final int BLOCK_BYTES = 1 << 12;
  static final int WRITES = 5;
  static final Duration TIMEOUT = Duration.ofSeconds(5);
  static final long SEED = 20_261_110L;
```

Ten constants, the settings of a very small probe. `SAMPLES = 50` timed samples per operation (the real file uses 2,000), `WARMUP = 5` samples that are run and thrown away first (the real value is 500; a **warm-up** lets the Java compiler optimize the loops before the timed part), `READS_PER_SAMPLE = 10` reads timed together (the real value is 1,000), `MEMORY_BYTES = 1 << 16` which is 65,536 bytes (64 KiB, instead of 256 MiB), `CACHE_BYTES = 1 << 10` which is 1,024 bytes, `FILE_BYTES = 1 << 16` (a 64 KiB file instead of 64 MiB), `BLOCK_BYTES = 1 << 12` which is 4,096 bytes (the real block size), `WRITES = 5` forced writes (the real value is 500), `TIMEOUT` of 5 seconds for each socket call, and `SEED = 20_261_110L` for the random generator.

```java
  private ProbeSettingsFixtures() {}
```

A private constructor with an empty body: nobody can create an object of this class, because it only offers static members.

```java
  static ProbeSettings small() {
    return new ProbeSettings(
        SAMPLES,
        WARMUP,
        READS_PER_SAMPLE,
        MEMORY_BYTES,
        CACHE_BYTES,
        FILE_BYTES,
        BLOCK_BYTES,
        WRITES,
        TIMEOUT,
        SEED);
  }
```

Builds a `ProbeSettings` from the ten constants. The constructor parameters are in the order: samples, warm-up samples, reads per sample, memory array bytes, cache array bytes, disk file bytes, disk block bytes, disk write samples, socket timeout, random seed. Each call returns a new settings object.

### `ProbeSettingsTest.java`

`ProbeSettingsTest.java` tests `ProbeSettings`, the record that holds every setting of the probe and the method `ProbeSettings.from(Properties)` that reads them from `probe.properties`. The program has **no defaults in code**: every key must be in the file, or the probe refuses to start and says which keys are missing. The tests read the real file `probe.properties` from the project folder. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ProbeSettingsTest {
```

- `fail` and `Result` work as in `Sheets.java` (`tests-estimate-walkthrough.md`): the helper methods at the bottom unwrap a `Result`.
- `IOException`, `Reader`, `Files`, `Path` and `StandardCharsets` are Java's file-reading tools. `Reader` is a stream of characters.
- `Duration` and `List` are the time and list types.
- `Properties` is the table type for `key=value` files.

```java
  @Test
  void acceptsTheShippedSettingsFile() throws IOException {
    ProbeSettings settings = settingsOf(shipped());

    assertThat(settings.samples()).isEqualTo(2_000);
    assertThat(settings.memoryArrayBytes()).isEqualTo(268_435_456);
    assertThat(settings.socketTimeout()).isEqualTo(Duration.ofSeconds(2));
  }
```

The good case. `settingsOf(shipped())` loads `probe.properties` and builds a `ProbeSettings`. The method says `throws IOException` because reading a file can fail; a test method may declare that. The three assertions check values from the real file: 2,000 samples, a memory array of 268,435,456 bytes (256 MiB), and a socket timeout of 2 seconds (the file has `socket.timeout.millis=2000`, which becomes `Duration.ofSeconds(2)`). If someone edited the file into something invalid, this test would show it.

```java
  @Test
  void reportsEveryMissingKey() {
    List<String> problems = problemsOf(new Properties());

    assertThat(problems).hasSize(10).contains("samples is missing", "random.seed is missing");
  }
```

`new Properties()` is an empty table: every key is missing. The result is a list of problems, and `hasSize(10)` checks that one is reported for each of the ten keys, not only the first. `contains("samples is missing", "random.seed is missing")` checks the exact wording of two of them. Reporting all the problems at once lets the user fix the file in one go.

```java
  @Test
  void reportsAValueThatIsNotAWholeNumberOrIsBelowOne() throws IOException {
    Properties properties = shipped();
    properties.setProperty("samples", "lots");
    properties.setProperty("disk.write.samples", "0");

    assertThat(problemsOf(properties))
        .containsExactly(
            "samples must be a whole number up to 2147483647, but was lots",
            "disk.write.samples must be 1 or more, but was 0");
  }
```

Start from the real settings, then break two keys: `samples` set to `lots` and `disk.write.samples` set to `0`. `properties.setProperty(key, value)` changes one entry. The expected problems, in this order: `samples must be a whole number up to 2147483647, but was lots` (2,147,483,647 is the largest `int`) and `disk.write.samples must be 1 or more, but was 0`. `containsExactly` also proves that no other problem is reported.

```java
  @Test
  void reportsValuesThatAreValidAloneButNotTogether() throws IOException {
    Properties properties = shipped();
    properties.setProperty("disk.file.bytes", "1024");

    assertThat(problemsOf(properties))
        .containsExactly("the file and the arrays must hold at least one block");
  }
```

`disk.file.bytes` set to 1,024 is a fine number by itself, but it is smaller than one disk block (4,096 bytes), so no block could be read. The check after all the single values reports `the file and the arrays must hold at least one block`. This proves the second layer: a rule that compares two settings.

```java
  @Test
  void refusesSettingsBuiltInCodeWithAZeroCountOrTimeout() {
    assertThatThrownBy(() ->
            new ProbeSettings(
                0, 1, 1, 1 << 16, 1 << 10, 1 << 16, 1 << 12, 1, Duration.ofSeconds(1), 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new ProbeSettings(1, 1, 1, 1 << 16, 1 << 10, 1 << 16, 1 << 12, 1, Duration.ZERO, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

The same rules apply when code builds the settings directly with `new ProbeSettings(...)`, not through a file. The first call has `0` samples (the first argument), the second has `Duration.ZERO` as its timeout. Both throw `IllegalArgumentException`. `1 << 16`, `1 << 10` and `1 << 12` are 65,536, 1,024 and 4,096. The `() ->` lambda delays the construction until AssertJ is ready to catch the exception. The layout is the formatter's: it puts the lambda body on the next lines.

```java
  private static Properties shipped() throws IOException {
    Properties properties = new Properties();
    try (Reader reader =
        Files.newBufferedReader(Path.of("probe.properties"), StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    return properties;
  }
```

Loads `probe.properties`. `try (Reader reader = ...) { ... }` is a **try-with-resources**: it opens the file, runs the block, and always closes the file at the end, even when an error happens. `Files.newBufferedReader(Path.of("probe.properties"), StandardCharsets.UTF_8)` opens the file as UTF-8 text in the project folder (the working directory that Gradle gives the tests). `properties.load(reader)` reads the `key=value` lines and skips the `#` comment lines.

```java
  private static ProbeSettings settingsOf(Properties properties) {
    return switch (ProbeSettings.from(properties)) {
      case Result.Success<ProbeSettings, List<String>>(ProbeSettings settings) -> settings;
      case Result.Error<ProbeSettings, List<String>>(List<String> problems) ->
          fail("expected settings but got " + problems);
    };
  }
```

Unwraps a `Result` that must be a success: `ProbeSettings.from(properties)` returns a success holding the settings or an error holding a list of texts. The `switch` takes the settings out of a success and calls `fail(...)` for an error, so a test that expects good settings gets a readable failure otherwise (`tests-estimate-walkthrough.md`, `Sheets.entries`).

```java
  private static List<String> problemsOf(Properties properties) {
    return switch (ProbeSettings.from(properties)) {
      case Result.Success<ProbeSettings, List<String>>(ProbeSettings settings) ->
          fail("expected problems but got " + settings);
      case Result.Error<ProbeSettings, List<String>>(List<String> problems) -> problems;
    };
  }
```

The mirror: returns the list of problem texts and fails the test if the properties were accepted.

### `ProbesTest.java`

`ProbesTest.java` runs each of the measuring probes once. Its Javadoc says the rule of the file: "Durations depend on the machine, so these tests check only what is true everywhere: the count of samples, and that every duration is a real one." So there is no check such as "a memory read is under 200 ns", which would fail on a slow or busy machine and teach nothing. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.probe;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs each probe with small settings. Durations depend on the machine, so these tests check only
 * what is true everywhere: the count of samples, and that every duration is a real one.
 */
class ProbesTest {
```

- `TempDir` is a JUnit annotation (`org.junit.jupiter.api.io.TempDir`) that asks JUnit to create a temporary folder for one test and delete it afterwards.
- `Files` and `Path` are Java's file tools; `Stream` is a pipeline of values, here the list of files in a folder.
- The field after the class line gives every test the same small settings.
- `throws IOException` appears on tests that touch files or sockets.

```java
  private static final BigDecimal P99 = new BigDecimal("99");
  private final ProbeSettings settings = ProbeSettingsFixtures.small();
```

`P99` is the exact decimal 99. The field `settings` holds `ProbeSettingsFixtures.small()`: 50 samples, 5 forced writes, a 64 KiB file and a seed of 20,261,110.

```java
  @Test
  void measuresTheTimerStep() {
    LatencySamples samples = MemoryProbe.timerStep(settings);

    assertThat(samples.count().amount()).isEqualByComparingTo("50");
  }
```

`MemoryProbe.timerStep(settings)` times two `System.nanoTime()` calls back to back, 50 times. The test checks only that 50 samples came back: `samples.count().amount()` is the number of samples, compared with `isEqualByComparingTo("50")`. The measured values (usually a few tens of nanoseconds) are not checked at all, because they are machine speeds.

```java
  @Test
  void measuresArrayReadsFromBothArraySizes() {
    LatencySamples cache = MemoryProbe.arrayReads(settings, settings.cacheArrayBytes());
    LatencySamples memory = MemoryProbe.arrayReads(settings, settings.memoryArrayBytes());

    assertThat(cache.count().amount()).isEqualByComparingTo("50");
    assertThat(memory.percentile(P99).amount().signum()).isPositive();
  }
```

The memory probe follows a chain through an array: each element holds the index of the next one, so the reads cannot overlap. The chain is built with the seeded random generator, so the order is the same every run. The test runs it twice, with the small cache-sized array and with the larger memory-sized array, and checks that the first returned 50 samples and that the 99th percentile of the second is above zero (`signum()` is 1 for a positive number, and `isPositive()` checks it). A duration of exactly zero would mean the loop did not run. (The p99 of 50 samples is the largest one, so this fails only if all 50 timings read 0 ns.)

```java
  @Test
  void measuresDiskReadsAndWritesAndDeletesItsFile(@TempDir Path folder) throws IOException {
    DiskProbe.DiskResults results = DiskProbe.measure(settings, folder);

    assertThat(results.reads().count().amount()).isEqualByComparingTo("50");
    assertThat(results.forcedWrites().count().amount()).isEqualByComparingTo("5");
    try (Stream<Path> left = Files.list(folder)) {
      assertThat(left).isEmpty();
    }
  }
```

`@TempDir Path folder` is a **method parameter** that JUnit fills in with a new empty temporary folder. `DiskProbe.measure(settings, folder)` creates a file in it, fills it with seeded random bytes, times 50 random 4 KiB reads and 5 forced writes (writes followed by `force`, which makes the operating system push the data to the disk), and returns both sets of samples as a `DiskResults` record. The test checks the two counts, 50 and 5. Then it lists the folder with `Files.list(folder)` inside a **try-with-resources**, which closes the stream at the end, and asserts that nothing is left: the probe must delete its file. This protects the user's disk: the real probe writes a 64 MiB file, and it must not leave it behind.

```java
  @Test
  void measuresRoundTripsOnLoopbackOnly() throws IOException {
    LatencySamples samples = LoopbackProbe.roundTrips(settings);

    assertThat(samples.count().amount()).isEqualByComparingTo("50");
    assertThat(samples.percentile(P99).amount().signum()).isPositive();
  }
```

`LoopbackProbe.roundTrips(settings)` starts a tiny echo server in a background thread on the loopback address, with a port that the operating system chooses (port 0 means "any free port", so two runs never collide), connects to it, and times one byte out and one byte back, 50 times. The test checks the 50 samples and that the 99th percentile is above zero. A round trip on loopback is real network software, but it stays inside the machine, so the test needs no network and sends nothing to any other host.

## Try it yourself

1. Run the probe package's tests:

```
./gradlew cleanTest test --tests '*probe*'
```

   Expected: 20 lines that end in `PASSED` and `BUILD SUCCESSFUL`.

2. Run the real probe once, so you can see what the tests deliberately do not check (the numbers differ on every machine and every run):

```
mkdir -p build/probe
./gradlew -q probe --args='probe.properties build/probe'
```

   Expected: a `Machine:` line, a table with six rows (timer, array read cache-sized, array read memory-sized, random 4 KiB file read, 4 KiB write + force, TCP round trip on loopback), and the closing line `These numbers describe this machine only. Yours will differ.` The folder `build/probe` is empty again afterwards: `ls build/probe` prints nothing.

3. Count the tests per class:

```
grep -o 'tests="[0-9]*"' build/test-results/test/TEST-io.plainround.seatwave.estimates.probe.*.xml
```

   Expected: four lines with the counts 6 (`LatencySamplesTest`), 4 (`ProbesTest`), 5 (`ProbeSettingsTest`) and 5 (`ProbeReportTest`), in an order that can vary.

4. See that the seed matters. Open `probe.properties` and read the last two lines:

```
tail -2 probe.properties
```

   Expected: the comment `# The seed that makes every run visit the same positions.` and the line `random.seed=20261110`.

5. Run one test by name:

```
./gradlew cleanTest test --tests '*ProbesTest.measuresDiskReadsAndWritesAndDeletesItsFile'
```

   Expected: one `PASSED` line.

## Mistakes people make

- **Asserting a speed.** A test such as "a memory read takes less than 200 ns" passes on one machine and fails on another. These tests assert counts, positivity and cleanup instead.
- **Using the real settings in a unit test.** The real file needs a 64 MiB file and 256 MiB of memory and takes seconds. The fixtures keep the test small.
- **Random data without a seed.** The probe seeds its generator, so a run can be repeated.
- **Leaving files behind.** The disk test checks that the temporary file is deleted, and `@TempDir` removes the folder.
- **Testing against the network.** The probe talks only to the loopback address, so the test cannot depend on an outside host.
- **Forgetting the warm-up.** The first runs of a Java loop are slow, until the compiler optimizes it. The real probe discards 500 samples first.

## Check yourself

1. *How does a test of a timing program stay deterministic?* It tests the calculations with hand-made sample arrays, and it tests the real measuring code only for facts that do not depend on speed, such as the number of samples and the cleanup.
2. *Which sample is the p99 of 100 samples?* The 99th smallest one.
3. *Why does `ProbeReportTest` use hand-made `LatencySamples`?* So the table, the ratios and the "explain it" marker can be checked with exact numbers.
4. *What does `@TempDir` do?* JUnit makes a new empty folder for the test and deletes it afterwards.
5. *Why is the port `0` in the loopback probe?* It lets the operating system pick any free port, so two runs never collide.

## Further reading

- JUnit 6.1.3 user guide, temporary directories: <https://docs.junit.org/6.1.3/writing-tests/built-in-extensions.html>
- Java `System.nanoTime`: <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html#nanoTime()>
- Java `Properties`: <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Properties.html>
- Java `FileChannel`: <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/channels/FileChannel.html>
