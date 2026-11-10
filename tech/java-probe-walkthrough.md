# Java walkthrough: the `probe` package

## What it is

This file reads, one source file at a time, the Java package `io.plainround.seatwave.estimates.probe`. A **package** is a named folder of related Java files. This one times six things on your own machine: the clock itself, a read from the CPU's fast cache, a read from main memory, a random 4 KiB read from a file, a forced 4 KiB write to a file, and a one-byte round trip over TCP on the loopback address. Then it prints the times next to the published "latency numbers" table.

Read `java.md` first. It explains the Java words used here (class, record, enum, exception, `Optional`, `Properties`, `try`-with-resources, `FileChannel`, sockets, virtual threads) from zero. Read `java-units-walkthrough.md` second, because every duration is a `Quantity`. This file quotes each line of the source and says what the line does in this program.

## Why this repository uses it

Module 3 uses the latency orders of magnitude to decide what to put where: a cache hit costs about a hundred nanoseconds, a disk read about a hundred microseconds, a network round trip inside one data center about half a millisecond. A table in a book is easy to forget. A program that measures them on your own machine makes the gaps real, and shows when your machine disagrees with the table, which is when you learn something.

## Where it appears

| File | What it does |
|---|---|
| `ProbeSettings.java` | Every setting of the probe, read from `probe.properties`, with no defaults in code. |
| `LatencySamples.java` | The timings of one operation, sorted, with the percentile calculation. |
| `ProbeOperation.java` | The six things the probe times, each with its published comparison value. |
| `MemoryProbe.java` | Times the timer itself and array reads, from cache and from main memory. |
| `DiskProbe.java` | Times random block reads and forced block writes on a temporary file. |
| `LoopbackProbe.java` | Times a TCP round trip to an echo server on the loopback address. |
| `ProbeReport.java` | Prints the table, marking any gap above ten times. |
| `ProbeCli.java` | The program you start from the terminal. It loads the settings, runs every probe and prints. |

## How it works underneath

The flow runs in one **process** (one running copy of the Java program). `ProbeCli.main` loads `probe.properties`, then runs the probes one after another: timer, cache-sized array, memory-sized array, disk, loopback. Each probe returns a `LatencySamples`. `ProbeReport.render` turns the six results into a table. The probe writes one temporary file, which it deletes before it ends, and one TCP connection that never leaves the machine.

**The clock: `System.nanoTime`.** A program measures how long something took by reading a clock twice and subtracting. `System.nanoTime()` returns a count of nanoseconds (a nanosecond is one billionth of a second) from an arbitrary starting point. The starting point has no meaning, so the number is useful only as a difference between two calls. Its documentation says it can only be used to measure elapsed time and is not related to any wall-clock time, and that its resolution is at least as good as that of `currentTimeMillis`. `System.currentTimeMillis()` is the wall clock. It counts milliseconds since 1 January 1970, and the wall clock can be set by a person or corrected by the computer over the network while the program runs. A timing taken with it could be wrong by the size of such a correction, and cannot be finer than its own steps. So a program that times durations uses `nanoTime` and a program that needs the date uses the other.

**The timer has a cost and a step.** Reading the clock takes time, and the clock advances in steps. On the verification machine, two back-to-back `nanoTime` calls differed by about 41 ns in one run and by 0 ns in another. A single read of a value in the CPU's cache takes about 1 ns, so one timer read cannot see it. That is why `MemoryProbe` times 1,000 reads together and divides.

**Warm-up and the JIT compiler.** Java does not run bytecode only by interpreting it. The JVM starts by interpreting, counts how often each method runs, and the **JIT compiler** (just-in-time compiler) turns the busy methods into native machine code while the program runs. The first runs of a loop are slow, because they are interpreted and the compiler is busy. A **warm-up** is a number of runs that are made and then thrown away, so the timed runs happen after the compiler is done. Each probe runs `warmup.samples` (500) untimed rounds first.

**The CPU cache.** Main memory is slow compared with the CPU. To hide that, the CPU keeps recently used memory in small, very fast stores on the chip, called **caches**. The first-level cache (L1) is the smallest and fastest, tens of kilobytes. Bigger ones sit behind it. When the CPU reads an address that is in a cache, it is a **cache hit** and takes about a nanosecond. When it is not, it is a **miss** and the CPU waits for main memory, about 100 ns. An array of 16 KiB fits in L1, so after the first pass every read hits. An array of 256 MiB is far larger than any cache on the machine, so a read at a random place almost always misses. That is the whole experiment: the same loop over two array sizes.

**Why a large array defeats the cache, and why the reads must depend on each other.** A cache holds the recently used lines of memory. If the next address is far from the recent ones, the line is not there. Random jumps through 256 MiB almost never land on a line that is already cached. The CPU also tries to **prefetch**, which means to guess the next address and fetch it before it is asked for, and it can start many independent reads at once. Both tricks would make a loop of reads look faster than one read really is. The probe defeats them with **pointer chasing**: each array element holds the index of the next element to read. The CPU cannot know the next address until the previous read has finished, so the reads cannot overlap. The time per read is then the true latency of one read.

**The page cache.** When a program reads a file, the operating system copies the data from the storage device into memory, and keeps it there in case the program asks again. That memory is the **page cache**. A page is the unit the system works in, usually 4 KiB. A read that finds its data in the page cache does not touch the device: it costs a system call (a request from the program to the operating system) and a copy, about a microsecond. A read that misses must wait for the device. The probe writes its 64 MiB file just before it reads it, so almost every read finds its data in the page cache. The result is a few microseconds, far below the published 150 microseconds for an SSD read. The report marks that gap. It is honest: the probe measures the page cache and the system call, not the device. A production disk benchmark reads a file larger than memory, or asks the system to bypass the cache.

**What a forced write asks the operating system to do.** A normal write to a file puts the bytes in the page cache and returns. The system writes them to the device later. If the power fails first, the bytes are lost. `FileChannel.force(false)` asks the operating system to write the modified data of the file to the storage device, and not to return until the system says it has. The `false` says that only the file's content must be written, not its metadata (such as the modification time), which is what a database log needs. The JDK documentation says the effect of the parameter depends on the operating system. The call is slow because it waits for the device. On the verification machine, a write plus a force took about 4 ms, thousands of times more than a read from the page cache. The cost of a forced write is why databases batch many writes into one force.

**TCP and the loopback address.** **TCP** is the protocol that carries most traffic on the internet: it gives two programs a reliable stream of bytes between them. A **socket** is one end of such a connection. A **server socket** waits for connections on a **port**, a number from 0 to 65,535 that tells the operating system which program a connection is for. The **loopback address** (`127.0.0.1`) means "this same machine". A connection to it never leaves the machine: the operating system's network code hands the bytes from one program to the other in memory, and no network card is involved. So the probe measures the cost of the operating system's network stack, not of any wire. A published 500 microseconds for a round trip inside one data center includes real cables and switches, which is why the loopback result is much smaller.

**Percentiles.** The probe takes 2,000 timings of each operation. The **median**, or **p50**, is the timing that half of the samples are below: it describes a typical operation. The **p99** is the timing that 99 percent of the samples are below: it describes the slow end, the operations a user occasionally gets. A mean hides the slow end. This program computes a percentile by the **nearest-rank** method: sort the samples from smallest to largest, compute the rank `ceiling(percent / 100 x count)`, counting from 1, and take the sample at that rank. With 2,000 samples, p50 is rank ceiling(0.5 x 2000) = 1000, the 1,000th smallest, and p99 is rank ceiling(0.99 x 2000) = 1980, the 1,980th smallest, so 20 samples are above it. That is what the comment in `probe.properties` means by "2,000 samples put 20 above p99". For the five samples 5, 1, 9, 3, 7, sorted as 1, 3, 5, 7, 9, p50 is rank ceiling(2.5) = 3, which is 5, and p99 is rank ceiling(4.95) = 5, which is 9. I ran this and got `5 ns | 9 ns`.

## Walkthrough of this repository's use

### `ProbeSettings.java`

**Lines 1 to 35: imports, comment and record header**

```java
package io.plainround.seatwave.estimates.probe;

import com.github.sviperll.result4j.Result;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * How much the latency probe measures. Every value comes from {@code probe.properties}; there are
 * no defaults in code, so a missing key stops the probe before it measures anything.
 *
 * @param samples timed samples kept for each operation
 * @param warmupSamples samples run and thrown away first, so the JIT compiler has compiled the loop
 * @param readsPerSample array reads timed together in one sample
 * @param memoryArrayBytes size of the array that must come from main memory
 * @param cacheArrayBytes size of the array small enough to stay in the CPU's first-level cache
 * @param diskFileBytes size of the temporary file read and written
 * @param diskBlockBytes size of one read or write
 * @param diskWriteSamples timed writes, fewer than reads because each one waits for the device
 * @param socketTimeout the longest any socket call may block
 * @param randomSeed the seed that makes every run visit the same positions
 */
public record ProbeSettings(
    int samples,
    int warmupSamples,
    int readsPerSample,
    int memoryArrayBytes,
    int cacheArrayBytes,
    int diskFileBytes,
    int diskBlockBytes,
    int diskWriteSamples,
    Duration socketTimeout,
    long randomSeed) {
```

- `Duration` (from `java.time`) is a length of time. `Properties` is the JDK class for the key-value settings format of `probe.properties` (`java-properties.md` explains the format). `Result` is the result4j type: a success or an error.
- The Javadoc says every value comes from `probe.properties` and there are no defaults in code, so a missing key stops the probe before it measures anything. A default hidden in code would make two machines run two different experiments without saying so.
- The record has ten components. Nine are numbers of type `int` (a 32-bit whole number), and `randomSeed` is a `long` (a 64-bit one). `socketTimeout` is a `Duration`. The `@param` lines document the components.
- `memoryArrayBytes` and `cacheArrayBytes` are the sizes of the two arrays in **bytes**. `diskFileBytes` and `diskBlockBytes` are the file size and the block size. `readsPerSample` is how many array reads are timed together. `warmupSamples` is how many rounds are thrown away first. `randomSeed` makes the random numbers the same on every run, so every run visits the same positions.

**Lines 37 to 66: the compact constructor**

```java
  /**
   * Creates the settings.
   *
   * @throws IllegalArgumentException if a count or size is below 1, an array or file cannot hold
   *     one block, or the timeout is not positive
   */
  public ProbeSettings {
    Objects.requireNonNull(socketTimeout, "socketTimeout");
    int[] mustBePositive = {
      samples,
      warmupSamples,
      readsPerSample,
      memoryArrayBytes,
      cacheArrayBytes,
      diskFileBytes,
      diskBlockBytes,
      diskWriteSamples
    };
    for (int value : mustBePositive) {
      if (value < 1) {
        throw new IllegalArgumentException("every count and size must be 1 or more: " + value);
      }
    }
    if (diskFileBytes < diskBlockBytes || cacheArrayBytes < Integer.BYTES) {
      throw new IllegalArgumentException("the file and the arrays must hold at least one block");
    }
    if (socketTimeout.isNegative() || socketTimeout.isZero()) {
      throw new IllegalArgumentException("the socket timeout must be positive: " + socketTimeout);
    }
  }
```

- The **compact constructor** has no parameter list, and the components are available as variables. It runs before the fields are set. This one only checks.
- Line 44 rejects a null timeout.
- Lines 45 to 54: `int[] mustBePositive = { ... };` makes an **array**, a fixed-length row of values of one type, and fills it with the eight counts and sizes. The curly brackets are an **array initializer**.
- Lines 55 to 59: the loop `for (int value : mustBePositive)` visits each value. A value below 1 throws `IllegalArgumentException` with the message `every count and size must be 1 or more: ` and the value. A count or size of zero would make no sense: no samples, or an empty file.
- Line 60: `diskFileBytes < diskBlockBytes` is true when the file cannot hold even one block. `cacheArrayBytes < Integer.BYTES` is true when the small array cannot hold even one `int`. `Integer.BYTES` is the constant 4, the size of an `int` in bytes. `||` means "or". The message says the file and the arrays must hold at least one block.
- Lines 63 to 65: `socketTimeout.isNegative() || socketTimeout.isZero()` rejects a timeout that is not positive. A timeout of zero would mean "wait forever" in some socket calls, which would let a broken probe hang.
- A record built through this constructor cannot have a setting that makes the probe meaningless.

**Lines 68 to 108: `from`, the loader**

```java
  /**
   * Reads and checks the settings.
   *
   * @param properties the loaded {@code probe.properties}
   * @return the settings, or one line per missing or invalid key
   */
  public static Result<ProbeSettings, List<String>> from(Properties properties) {
    Objects.requireNonNull(properties, "properties");
    List<String> problems = new ArrayList<>();
    int samples = positiveInt(properties, "samples", problems);
    int warmup = positiveInt(properties, "warmup.samples", problems);
    int reads = positiveInt(properties, "memory.reads.per.sample", problems);
    int memory = positiveInt(properties, "memory.array.bytes", problems);
    int cache = positiveInt(properties, "cache.array.bytes", problems);
    int file = positiveInt(properties, "disk.file.bytes", problems);
    int block = positiveInt(properties, "disk.block.bytes", problems);
    int writes = positiveInt(properties, "disk.write.samples", problems);
    int timeoutMillis = positiveInt(properties, "socket.timeout.millis", problems);
    long seed = positiveInt(properties, "random.seed", problems);
    if (!problems.isEmpty()) {
      return Result.error(List.copyOf(problems));
    }
    try {
      return Result.success(
          new ProbeSettings(
              samples,
              warmup,
              reads,
              memory,
              cache,
              file,
              block,
              writes,
              Duration.ofMillis(timeoutMillis),
              seed));
    } catch (IllegalArgumentException inconsistent) {
      // Each key was valid alone, but together they break a rule, such as a file smaller than a
      // block.
      return Result.error(List.of(inconsistent.getMessage()));
    }
  }
```

- `from(Properties properties)` turns a loaded `Properties` object into a `Result<ProbeSettings, List<String>>`: the settings, or one line of text for each missing or invalid key. The problems are plain text because they describe a file a person edits.
- Lines 77 to 86: ten calls to `positiveInt`, one for each key. Each returns the number, or 0 when the key is missing or wrong, and adds a problem line to the list. `int timeoutMillis` holds the timeout as a number of milliseconds. Line 86: `long seed = positiveInt(...)` stores the `int` into a `long`: Java widens it with no loss. All ten keys are read before the method decides, so a person sees every problem at once.
- Lines 87 to 89: if the problem list is not empty, return an error with an unmodifiable copy.
- Lines 90 to 102: if all ten were valid on their own, build the record. `Duration.ofMillis(timeoutMillis)` turns the number into a `Duration`. The record's compact constructor runs and may still throw.
- Lines 103 to 106: `catch (IllegalArgumentException inconsistent)` catches that case. The comment says each key was valid alone, but together they break a rule, such as a file smaller than a block. The exception's message becomes the one problem. This is a mixed use of both kinds of failure on purpose: the record's own constructor is still the single place where the rule is written. The settings file is typed by a person, so the failure is returned as a value.
- `List.of(inconsistent.getMessage())` makes a one-item unmodifiable list. `Result.success(...)` and `Result.error(...)` make the two kinds of result.

**Lines 110 to 129: `positiveInt`**

```java
  private static int positiveInt(Properties properties, String key, List<String> problems) {
    String text = properties.getProperty(key);
    if (text == null || text.isBlank()) {
      problems.add(key + " is missing");
      return 0;
    }
    try {
      int value = Integer.parseInt(text.strip());
      if (value < 1) {
        problems.add(key + " must be 1 or more, but was " + value);
      }
      return value;
    } catch (NumberFormatException notANumber) {
      // A typo is an expected failure of a config file, so it becomes a problem line, not a crash.
      problems.add(
          key + " must be a whole number up to " + Integer.MAX_VALUE + ", but was " + text);
      return 0;
    }
  }
}
```

- `properties.getProperty(key)` returns the text of the key, or `null` if the key is absent. Line 112: `text == null || text.isBlank()` is true for an absent key or one with only spaces. The problem line is `<key> is missing`.
- Line 117: `Integer.parseInt(text.strip())` converts text to an `int`. It throws `NumberFormatException` for text that is not a whole number, or one too big for an `int`.
- Lines 118 to 120: a value below 1 adds a problem line, and the method still returns the value.
- Lines 122 to 127: `catch (NumberFormatException notANumber)` catches a typo. The comment says a typo is an expected failure of a config file, so it becomes a problem line and not a crash. The message says the largest allowed number, `Integer.MAX_VALUE`, which is 2,147,483,647 and is why the random seed must also fit in an `int`.
- The helper is `private static`: a function with no object, visible only inside the class.
- I ran the probe with a file missing `samples`, with `disk.block.bytes=0` and with `random.seed=abc`, and got `invalid setting: samples is missing`, `invalid setting: disk.block.bytes must be 1 or more, but was 0` and `invalid setting: random.seed must be a whole number up to 2147483647, but was abc`, together, in one run.

### `LatencySamples.java`

**Lines 1 to 22: imports, comment and fields**

```java
package io.plainround.seatwave.estimates.probe;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Objects;

/**
 * The measured durations of one operation, kept sorted so any percentile can be read.
 *
 * <p>Each sample may time several operations in a row, because {@code System.nanoTime} cannot see a
 * single 1-nanosecond read: its own resolution is coarser. A sample of 1,000 reads that took
 * 100,000 ns counts as 100 ns per read. Instances are immutable and safe to share between threads.
 */
public final class LatencySamples {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final long[] sortedSampleNanos;
  private final int operationsPerSample;
```

- A `LatencySamples` is a plain class, not a record, because it holds an array and a record's equality would compare the array by identity. The Javadoc says each sample may time several operations in a row, because `System.nanoTime` cannot see a single 1-nanosecond read: its own resolution is coarser. A sample of 1,000 reads that took 100,000 ns counts as 100 ns per read.
- `HUNDRED` is the exact number 100.
- `private final long[] sortedSampleNanos;` is an array of `long` values: the timings in nanoseconds, sorted. `private final int operationsPerSample;` is how many operations each timing covers.

**Lines 24 to 45: the constructor**

```java
  /**
   * Creates the samples.
   *
   * @param sampleNanos the duration of each sample in nanoseconds; copied, so later changes to the
   *     array have no effect
   * @param operationsPerSample how many operations each sample timed
   * @throws IllegalArgumentException if there are no samples, a sample is negative, or the count of
   *     operations is below 1
   */
  public LatencySamples(long[] sampleNanos, int operationsPerSample) {
    Objects.requireNonNull(sampleNanos, "sampleNanos");
    if (sampleNanos.length == 0 || operationsPerSample < 1) {
      throw new IllegalArgumentException("need at least one sample of at least one operation");
    }
    long[] copy = sampleNanos.clone();
    Arrays.sort(copy);
    if (copy[0] < 0) {
      throw new IllegalArgumentException("a duration is never negative, but was " + copy[0]);
    }
    this.sortedSampleNanos = copy;
    this.operationsPerSample = operationsPerSample;
  }
```

- A normal **constructor** has the name of the class, takes parameters, and runs when `new LatencySamples(...)` is written. The Javadoc says the array is copied, so later changes to the caller's array have no effect.
- Line 34 rejects a null. Lines 35 to 37 reject an empty array and fewer than 1 operation per sample.
- Line 38: `sampleNanos.clone()` makes a copy of the array. A copy keeps a caller from changing the timings later. Line 39: `Arrays.sort(copy)` sorts it in place, smallest first.
- Lines 40 to 42: after sorting, the first element is the smallest. If it is below zero the whole array holds a negative duration, which cannot happen with a correct clock, so the constructor throws.
- Lines 43 and 44 assign the two fields with `this.`, to tell the field from the parameter.

**Lines 47 to 54: `count`**

```java
  /**
   * Returns the number of samples.
   *
   * @return the count, in {@link Unit#SAMPLES}
   */
  public Quantity count() {
    return new Quantity(BigDecimal.valueOf(sortedSampleNanos.length), Unit.SAMPLES);
  }
```

- `count()` returns the number of samples as a `Quantity` in `samples`. `BigDecimal.valueOf(sortedSampleNanos.length)` makes an exact decimal from a whole number. The report prints it as `2,000 samples`.

**Lines 56 to 80: `percentile`**

```java
  /**
   * Reads a percentile by the nearest-rank method: the smallest sample that at least that share of
   * samples does not exceed.
   *
   * @param percent the percentile, above 0 and at most 100, for example 99 for p99
   * @return the duration of one operation at that percentile, in nanoseconds
   * @throws IllegalArgumentException if the percent is outside its range
   */
  public Quantity percentile(BigDecimal percent) {
    Objects.requireNonNull(percent, "percent");
    if (percent.signum() <= 0 || percent.compareTo(HUNDRED) > 0) {
      throw new IllegalArgumentException("a percentile is above 0 and at most 100: " + percent);
    }
    // Nearest rank: rank = ceiling(percent / 100 x count), counted from 1.
    BigDecimal rank =
        percent
            .multiply(BigDecimal.valueOf(sortedSampleNanos.length))
            .divide(HUNDRED, 0, RoundingMode.CEILING);
    long sampleNanos = sortedSampleNanos[rank.intValueExact() - 1];
    BigDecimal perOperation =
        BigDecimal.valueOf(sampleNanos)
            .divide(BigDecimal.valueOf(operationsPerSample), Quantity.PRECISION);
    return new Quantity(perOperation, Unit.NANOSECONDS);
  }
}
```

- `percentile(BigDecimal percent)` returns the duration of one operation at that percentile. Lines 66 to 68 reject a percent that is zero or less, or above 100.
- Lines 69 to 73 compute the **rank**: `percent x count / 100`, rounded up. `divide(HUNDRED, 0, RoundingMode.CEILING)` divides and keeps 0 decimals, rounding toward the larger whole number. For p99 of 2,000 samples that is 99 x 2,000 / 100 = 1,980. For p50 it is 1,000.
- Line 74: `sortedSampleNanos[rank.intValueExact() - 1]` takes the sample at that rank. The array counts from 0 and a rank from 1, so the `- 1`. `intValueExact()` converts to an `int`, and throws if the number does not fit.
- Lines 75 to 78: the sample is divided by the number of operations it covered, with 16 significant digits, and wrapped as a `Quantity` in nanoseconds. A sample of 1,000 reads at 100,000 ns gives 100 ns. I ran `new LatencySamples(new long[]{100000, 300000}, 1000).percentile(new BigDecimal("50"))` and got `100 ns`.
- Because the array is sorted, taking the percentile needs only one lookup.

### `ProbeOperation.java`

**Lines 1 to 26: the six operations**

```java
package io.plainround.seatwave.estimates.probe;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.Optional;

/**
 * What the probe times, and the published order of magnitude each one is compared with.
 *
 * <p>The published values are from the "Latency Numbers Every Programmer Should Know" table
 * credited to Jeff Dean and Peter Norvig, as read on 10 November 2026. They describe hardware of
 * about 2012 and are orders of magnitude, not specifications.
 */
public enum ProbeOperation {
  /** Two back-to-back calls of System.nanoTime: the finest step the timer can show. */
  TIMER_STEP("timer: two nanoTime calls", null, "none in the table"),
  /** A dependent read from an array small enough to stay in the first-level cache. */
  CACHE_READ("array read, cache-sized array", "0.5", "L1 cache reference"),
  /** A dependent read from an array far larger than every CPU cache. */
  MEMORY_READ("array read, memory-sized array", "100", "main memory reference"),
  /** A random 4 KiB read through the operating system from a file the probe wrote. */
  DISK_READ("random 4 KiB file read", "150000", "read 4K randomly from SSD"),
  /** A 4 KiB write followed by a force to the storage device. */
  DISK_WRITE_FORCED("4 KiB write + force (fsync)", null, "none in the table"),
  /** One byte to an echo server on the loopback address and one byte back. */
  LOOPBACK_ROUND_TRIP("TCP round trip on loopback", "500000", "round trip in one data center");
```

- This is an **enum** with fields: a fixed list of six values, each carrying data. Each line is `NAME("label", "published nanoseconds", "published line")`. The Javadoc says the published values are from the "Latency Numbers Every Programmer Should Know" table credited to Jeff Dean and Peter Norvig, and describe hardware of about 2012: they are orders of magnitude, not specifications.
- `TIMER_STEP` and `DISK_WRITE_FORCED` pass `null` for the published value, because the table has no matching line. Their third argument says `none in the table`.
- `CACHE_READ` is compared with 0.5 ns (an L1 cache reference). `MEMORY_READ` with 100 ns (a main memory reference). `DISK_READ` with 150,000 ns, that is 150 microseconds (reading 4 KB randomly from an SSD). `LOOPBACK_ROUND_TRIP` with 500,000 ns, that is 500 microseconds (a round trip in one data center).
- The values are texts such as `"0.5"`, not numbers, so they become exact `BigDecimal` values in `Quantity.of`.

**Lines 28 to 37: fields and constructor**

```java
  private final String label;
  private final Quantity textbook;
  private final String textbookLabel;

  ProbeOperation(String label, String textbookNanos, String textbookLabel) {
    this.label = label;
    // A missing published value is kept as null inside the enum and exposed only as an Optional.
    this.textbook = textbookNanos == null ? null : Quantity.of(textbookNanos, Unit.NANOSECONDS);
    this.textbookLabel = textbookLabel;
  }
```

- Three `private final` fields. The constructor is called once for each value. `textbookNanos == null ? null : Quantity.of(textbookNanos, Unit.NANOSECONDS)` is a conditional expression: if there is no published text, store `null`, otherwise make a `Quantity` in nanoseconds. The comment explains the choice: a missing published value is kept as null inside the enum and exposed only as an `Optional`.

**Lines 39 to 65: accessors**

```java
  /**
   * Returns the name printed in the report.
   *
   * @return for example "array read, memory-sized array"
   */
  public String label() {
    return label;
  }

  /**
   * Returns the published order of magnitude.
   *
   * @return the published duration, or empty when the table has no matching line
   */
  public Optional<Quantity> textbook() {
    return Optional.ofNullable(textbook);
  }

  /**
   * Returns the line of the published table this operation is compared with.
   *
   * @return for example "main memory reference"
   */
  public String textbookLabel() {
    return textbookLabel;
  }
}
```

- `label()` returns the name printed in the report. `textbookLabel()` returns the name of the published line.
- `textbook()` returns `Optional.ofNullable(textbook)`: a box that is empty when the field is null and holds the `Quantity` otherwise. A caller cannot forget to handle the missing case, because the type says it may be absent. `Optional.ofNullable` is the form that accepts a null.

### `MemoryProbe.java`

**Lines 1 to 16: imports, comment and constructor**

```java
package io.plainround.seatwave.estimates.probe;

import java.util.Objects;
import java.util.Random;

/**
 * Times the timer itself and array reads, from cache and from main memory.
 *
 * <p>An array read is timed by pointer chasing: each element holds the index of the next one to
 * read, in a random single cycle through the whole array. Each read must wait for the one before,
 * so the CPU cannot prefetch or overlap them, and the time per read is the true latency. Stateless
 * and safe to use from any thread; each call allocates its own array.
 */
public final class MemoryProbe {

  private MemoryProbe() {}
```

- `Random` is the JDK's pseudo-random number generator. Given the same **seed** (a starting number), it produces the same sequence of numbers every time. That makes every run of the probe visit the same positions.
- The Javadoc names the technique, pointer chasing, which `How it works underneath` explains. "Stateless and safe to use from any thread; each call allocates its own array": the class keeps no data between calls.
- `private MemoryProbe() {}` means no one creates an object of this class of static methods.

**Lines 18 to 32: `timerStep`**

```java
  /**
   * Measures the smallest step System.nanoTime can show on this machine.
   *
   * @param settings how many samples to take
   * @return the difference between two back-to-back calls, one per sample
   */
  public static LatencySamples timerStep(ProbeSettings settings) {
    Objects.requireNonNull(settings, "settings");
    long[] samples = new long[settings.samples()];
    for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
      long start = System.nanoTime();
      samples[sampleIndex] = System.nanoTime() - start;
    }
    return new LatencySamples(samples, 1);
  }
```

- It measures the smallest step the timer can show. `new long[settings.samples()]` makes an array of 2,000 `long` values, all zero. The loop takes `System.nanoTime()` twice, back to back, and stores the difference. `samples[sampleIndex] = System.nanoTime() - start;` reads the clock a second time and subtracts. The difference is the cost of one clock read plus the step of the clock.
- `new LatencySamples(samples, 1)`: each sample covers one operation. The result is not compared with the published table.
- There is no warm-up here. The timer is cheap, and the first few samples being slower only affects the p99 slightly.

**Lines 34 to 61: `arrayReads`**

```java
  /**
   * Measures dependent reads from an array of the given size.
   *
   * @param settings counts and the random seed
   * @param arrayBytes the size of the array; small sizes stay in cache, large ones do not
   * @return one sample per batch of {@code readsPerSample} reads
   */
  public static LatencySamples arrayReads(ProbeSettings settings, int arrayBytes) {
    Objects.requireNonNull(settings, "settings");
    int[] nextIndex = singleCycle(arrayBytes / Integer.BYTES, new Random(settings.randomSeed()));
    int position = 0;
    // Warm-up: the first batches run interpreted and fault the array's pages in; they are
    // discarded.
    for (int warmupIndex = 0; warmupIndex < settings.warmupSamples(); warmupIndex++) {
      position = chase(nextIndex, position, settings.readsPerSample());
    }
    long[] samples = new long[settings.samples()];
    for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
      long start = System.nanoTime();
      position = chase(nextIndex, position, settings.readsPerSample());
      samples[sampleIndex] = System.nanoTime() - start;
    }
    // Using the final position keeps the compiler from removing the reads as dead code.
    if (position < 0 || position >= nextIndex.length) {
      throw new IllegalStateException("the chase left the array at " + position);
    }
    return new LatencySamples(samples, settings.readsPerSample());
  }
```

- `arrayBytes / Integer.BYTES` is the number of `int` elements: 16,384 / 4 = 4,096 for the cache-sized array, and 268,435,456 / 4 = 67,108,864 for the memory-sized one. `singleCycle(...)` builds the array of "next indexes". `new Random(settings.randomSeed())` is a generator with the configured seed.
- `int position = 0;` is where the chase starts.
- Lines 45 to 49: the **warm-up**. The comment says the first batches run interpreted and fault the array's pages in, and are discarded. "Fault the array's pages in" means the operating system gives out a memory page only when it is first used, and the first use is slow. `chase(nextIndex, position, settings.readsPerSample())` follows the chain for 1,000 reads and returns where it ended. The loop runs this 500 times and keeps only the final `position`, so the timed part continues from there.
- Lines 50 to 55: the **timed samples**. For each of 2,000 samples: read the clock, chase 1,000 reads, read the clock, and store the difference. Each sample is the time of 1,000 reads.
- Lines 56 to 59: the comment says that using the final position keeps the compiler from removing the reads as dead code. **Dead code** is code whose result nobody uses. A compiler may delete it. If the program never used `position`, the JIT compiler could remove the whole loop and the probe would time nothing. The check `position < 0 || position >= nextIndex.length` is always false, and it uses `position`, so the loop must run. If it were ever true, the program would throw `IllegalStateException`, the exception for an impossible state.
- Line 60: `new LatencySamples(samples, settings.readsPerSample())`: each sample covers 1,000 reads, so the percentile divides by 1,000.

**Lines 63 to 69: `chase`**

```java
  private static int chase(int[] nextIndex, int start, int reads) {
    int position = start;
    for (int readIndex = 0; readIndex < reads; readIndex++) {
      position = nextIndex[position];
    }
    return position;
  }
```

- This is the loop that is timed. `position = nextIndex[position];` reads the element at `position` and uses it as the next position. Each read depends on the one before it, so the reads cannot overlap. After `reads` reads it returns the last position. The loop has no other work, so the time is the time of the reads.

**Lines 71 to 86: `singleCycle`**

```java
  // Sattolo's algorithm: a random permutation that is one single cycle, so the chase visits every
  // element before it repeats and never gets stuck in a small loop that would stay in cache.
  private static int[] singleCycle(int length, Random random) {
    int[] nextIndex = new int[length];
    for (int index = 0; index < length; index++) {
      nextIndex[index] = index;
    }
    for (int index = length - 1; index > 0; index--) {
      int swapWith = random.nextInt(index);
      int held = nextIndex[index];
      nextIndex[index] = nextIndex[swapWith];
      nextIndex[swapWith] = held;
    }
    return nextIndex;
  }
}
```

- The comment names **Sattolo's algorithm**: a way to shuffle an array so that the result is one single cycle. If the chase used any random permutation, it might fall into a small loop of a few elements, which would sit in the cache and look fast. A single cycle visits every element before it repeats.
- Lines 74 to 77 fill the array with the identity: element `i` holds `i`. Line 78: for `index` from the last position down to 1, pick a random position `swapWith` from 0 up to, but not including, `index` (`random.nextInt(index)`), and swap the two elements. The `held` variable keeps one value while the swap happens. Because `swapWith` is always below `index`, no element stays in place, and the result is one cycle. The elements are then "next indexes": starting at any position and following the values visits all positions.
- The method is `private static`. The result is a `int[]`.

### `DiskProbe.java`

**Lines 1 to 22: imports, comment and constructor**

```java
package io.plainround.seatwave.estimates.probe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Random;

/**
 * Times random block reads and forced block writes on a temporary file.
 *
 * <p>The reads go through the operating system's page cache. The probe has just written the file,
 * so most reads are served from memory, not from the device; the report says so. A forced write
 * must reach the storage device before {@code FileChannel.force} returns, so it measures the device
 * and the file system. Stateless; each call creates and deletes its own file.
 */
public final class DiskProbe {

  private DiskProbe() {}
```

- The imports bring in the file tools of `java.nio` ("new input and output"). `ByteBuffer` is a block of bytes in memory that a channel reads into or writes from. `FileChannel` is a connection to an open file that can read and write at any position. `Files` and `Path` name and create files. `StandardOpenOption` lists the ways to open one. `IOException` is the **checked exception** for an input or output failure.
- The Javadoc says what the numbers mean. The reads go through the page cache. The probe has just written the file, so most reads are served from memory, and the report says so. A forced write must reach the storage device before `FileChannel.force` returns, so it measures the device and the file system. "Each call creates and deletes its own file."
- `private DiskProbe() {}` means the class is only static methods.

**Lines 24 to 46: `measure`**

```java
  /**
   * Runs both disk measurements on one temporary file in the given folder.
   *
   * @param settings sizes, counts and the random seed
   * @param folder where the temporary file is created; it is deleted before the method returns
   * @return the reads and the forced writes
   * @throws IOException if the file cannot be created, written, read or deleted
   */
  public static DiskResults measure(ProbeSettings settings, Path folder) throws IOException {
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(folder, "folder");
    Path file = Files.createTempFile(folder, "latency-probe-", ".bin");
    try (FileChannel channel =
        FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
      Random random = new Random(settings.randomSeed());
      fill(channel, settings, random);
      LatencySamples reads = timeReads(channel, settings, random);
      LatencySamples writes = timeForcedWrites(channel, settings, random);
      return new DiskResults(reads, writes);
    } finally {
      Files.deleteIfExists(file);
    }
  }
```

- `measure(ProbeSettings settings, Path folder) throws IOException` runs both measurements. `throws IOException` in the header tells the compiler that this method may throw a checked exception that its callers must handle.
- Line 35: `Files.createTempFile(folder, "latency-probe-", ".bin")` creates an empty file in the folder with a name made of the prefix, a random number and the suffix, for example `latency-probe-123456789.bin`. The folder must exist. The probe's own command creates nothing: that is why the README says `mkdir -p build/probe` first.
- Lines 36 and 37: `try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) { ... }` is **try-with-resources**: the resource named in the brackets is closed automatically when the block ends, even if it throws. Without it, a failed measurement would leave the file open. The channel is opened for reading and writing.
- Line 38: a `Random` with the configured seed. The same generator is shared by the three steps below, so the whole run is reproducible.
- Line 39: `fill` writes the whole file. Line 40: `timeReads` times the reads. Line 41: `timeForcedWrites` times the writes. Line 42: the two results are wrapped in a `DiskResults` record and returned.
- Lines 43 and 44: `finally { Files.deleteIfExists(file); }`. A `finally` block runs after the `try` block, whether it ended normally or by an exception. It deletes the temporary file. `deleteIfExists` does not fail if the file is already gone. The order matters: the channel is closed first (by try-with-resources), and the file is deleted after.

**Lines 48 to 54: `DiskResults`**

```java
  /**
   * The two disk measurements.
   *
   * @param reads random block reads
   * @param forcedWrites block writes, each forced to the device
   */
  public record DiskResults(LatencySamples reads, LatencySamples forcedWrites) {}
```

- A record nested in the class, to return two values from one method: the reads and the forced writes, each a `LatencySamples`.

**Lines 56 to 65: `fill`**

```java
  private static void fill(FileChannel channel, ProbeSettings settings, Random random)
      throws IOException {
    ByteBuffer block = ByteBuffer.allocate(settings.diskBlockBytes());
    for (long offset = 0; offset < settings.diskFileBytes(); offset += settings.diskBlockBytes()) {
      random.nextBytes(block.array());
      block.clear();
      writeFully(channel, block, offset);
    }
    channel.force(true);
  }
```

- `fill` writes `diskFileBytes` bytes (64 MiB) of random data in blocks of `diskBlockBytes` (4,096 bytes). The method has `throws IOException` on a second line because the formatter wraps a long header.
- `ByteBuffer.allocate(settings.diskBlockBytes())` makes a buffer of 4,096 bytes. The loop variable `offset` is a `long`, the position in the file, and it goes up by one block each turn: 16,384 turns in all.
- `random.nextBytes(block.array())` fills the buffer's backing array with random bytes. The data is random so that no file system can compress it or store it as an empty "hole" and make the later reads unrealistic. `block.clear()` sets the buffer's position back to 0 and its limit to the capacity, so the whole buffer is ready to be written. `writeFully(channel, block, offset)` writes it at that position.
- Line 64: `channel.force(true)` asks the operating system to write the file's content **and** metadata to the device. After this, the file is safely on the device. It stays in the page cache too, which is why the reads are fast.

**Lines 67 to 81: `timeReads`**

```java
  private static LatencySamples timeReads(
      FileChannel channel, ProbeSettings settings, Random random) throws IOException {
    ByteBuffer block = ByteBuffer.allocate(settings.diskBlockBytes());
    for (int warmupIndex = 0; warmupIndex < settings.warmupSamples(); warmupIndex++) {
      readFully(channel, block, randomOffset(settings, random));
    }
    long[] samples = new long[settings.samples()];
    for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
      long offset = randomOffset(settings, random);
      long start = System.nanoTime();
      readFully(channel, block, offset);
      samples[sampleIndex] = System.nanoTime() - start;
    }
    return new LatencySamples(samples, 1);
  }
```

- A single buffer of 4,096 bytes is reused for every read. The first loop is the **warm-up**: 500 untimed random reads, so that the code is compiled by the JIT compiler and the system's read path is in use.
- The timed loop takes 2,000 samples. For each: pick a random `offset` first, so the work of choosing is not timed, then read the clock, read one block, read the clock again, and store the difference. `readFully(channel, block, offset)` reads one block from that position.
- `new LatencySamples(samples, 1)`: each sample is one operation.

**Lines 83 to 98: `timeForcedWrites`**

```java
  private static LatencySamples timeForcedWrites(
      FileChannel channel, ProbeSettings settings, Random random) throws IOException {
    ByteBuffer block = ByteBuffer.allocate(settings.diskBlockBytes());
    random.nextBytes(block.array());
    long[] samples = new long[settings.diskWriteSamples()];
    for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
      long offset = randomOffset(settings, random);
      block.clear();
      long start = System.nanoTime();
      writeFully(channel, block, offset);
      // force(false) asks only for the data, not the file's metadata: what a database log needs.
      channel.force(false);
      samples[sampleIndex] = System.nanoTime() - start;
    }
    return new LatencySamples(samples, 1);
  }
```

- It fills one buffer with random bytes once. The array has `settings.diskWriteSamples()` entries (500). The comment in `probe.properties` says forced writes wait for the device, so fewer are timed.
- In each sample: pick a random offset, `block.clear()` to make the buffer ready, read the clock, write the block (`writeFully`), force it with `channel.force(false)`, read the clock, store the difference. So each sample covers one write and one force.
- Line 93 is a comment: `force(false)` asks only for the data, not the file's metadata, which is what a database log needs. The method asks the operating system to write the modified content of the file to the storage device and returns when the system says it is done. The reads are cached; this call is not.
- This step has no warm-up. A forced write mostly waits for the device, and compiling the loop is a tiny share of 4 ms.

**Lines 100 to 103: `randomOffset`**

```java
  private static long randomOffset(ProbeSettings settings, Random random) {
    int blocks = settings.diskFileBytes() / settings.diskBlockBytes();
    return (long) random.nextInt(blocks) * settings.diskBlockBytes();
  }
```

- `blocks` is the number of blocks in the file: 67,108,864 / 4,096 = 16,384. `random.nextInt(blocks)` is a random whole number from 0 up to 16,383. `(long) random.nextInt(blocks) * settings.diskBlockBytes()` multiplies by the block size to get a byte offset. `(long)` is a **cast**: it converts the `int` to a `long` before the multiplication, so the product is computed in 64 bits. Without the cast, `int` times `int` is computed in 32 bits and would overflow for a file larger than 2 GiB, giving a negative offset. The offset is always a multiple of the block size, so every read and write is block-aligned.

**Lines 105 to 124: `readFully` and `writeFully`**

```java
  // A positional read or write may move fewer bytes than asked, so both loop until the block is
  // done.
  private static void readFully(FileChannel channel, ByteBuffer block, long offset)
      throws IOException {
    block.clear();
    while (block.hasRemaining()) {
      int read = channel.read(block, offset + block.position());
      if (read < 0) {
        throw new IOException("the probe file ended before offset " + offset);
      }
    }
  }

  private static void writeFully(FileChannel channel, ByteBuffer block, long offset)
      throws IOException {
    while (block.hasRemaining()) {
      channel.write(block, offset + block.position());
    }
  }
}
```

- A read or write at a position may move fewer bytes than asked. The comment says both loop until the block is done.
- In `readFully`: `block.clear()` resets the buffer. `block.hasRemaining()` is true while the buffer is not full. `channel.read(block, offset + block.position())` reads into the buffer from the file position `offset + position` and returns the number of bytes read, or -1 at the end of the file. `block.position()` is how many bytes the buffer already holds. A result below 0 means the file ended early, which would be a bug, so the method throws an `IOException`.
- In `writeFully`: `while (block.hasRemaining())` is true while some bytes of the buffer are not yet written, and `channel.write(block, offset + block.position())` writes from the buffer to the file at that position. After each call the buffer's position moves on by the number of bytes written.
- The two methods take a **positional** read or write: the channel's own position is not used, and the position is passed in. That is why the calls are safe to make with a random offset.

### `LoopbackProbe.java`

**Lines 1 to 30: imports, comment and constants**

```java
package io.plainround.seatwave.estimates.probe;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Times a TCP round trip to an echo server that this class starts on the loopback address.
 *
 * <p>No byte leaves the machine: the server listens only on the loopback address, on a port the
 * operating system picks, and lives only for the duration of one call. Every socket call is bounded
 * by the configured timeout. Stateless; each call has its own server.
 */
public final class LoopbackProbe {

  private static final int ONE_WAITING_CONNECTION = 1;
  private static final int PING = 1;

  private LoopbackProbe() {}
```

- `ServerSocket` waits for connections. `Socket` is one end of a connection. `InetAddress` is a network address. `InputStream` and `OutputStream` read and write bytes. `InterruptedIOException` is an input or output exception that means "interrupted". From `java.util.concurrent` come `ExecutorService`, `Executors`, `Future`, `ExecutionException`, `TimeoutException` and `TimeUnit`.
- The Javadoc promises that no byte leaves the machine: the server listens only on the loopback address, on a port the operating system picks, and lives only for one call. Every socket call is bounded by the configured timeout.
- `ONE_WAITING_CONNECTION = 1` is the size of the queue of connections that wait to be accepted. `PING = 1` is the one byte value sent back and forth.

**Lines 32 to 52: `roundTrips`**

```java
  /**
   * Measures round trips of one byte each way.
   *
   * @param settings counts and the socket timeout
   * @return one sample per round trip
   * @throws IOException if a socket fails or a call times out
   */
  public static LatencySamples roundTrips(ProbeSettings settings) throws IOException {
    Objects.requireNonNull(settings, "settings");
    int timeoutMillis = Math.toIntExact(settings.socketTimeout().toMillis());
    // Port 0 asks the operating system for any free port, so two runs never collide.
    try (ServerSocket server =
            new ServerSocket(0, ONE_WAITING_CONNECTION, InetAddress.getLoopbackAddress());
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      server.setSoTimeout(timeoutMillis);
      Future<Void> echo = executor.submit(() -> echoOneClient(server, timeoutMillis));
      LatencySamples samples = timeClient(server, settings, timeoutMillis);
      awaitEcho(echo, settings);
      return samples;
    }
  }
```

- Line 41: `Math.toIntExact(settings.socketTimeout().toMillis())` turns the timeout `Duration` into a number of milliseconds as an `int`, and throws if it does not fit.
- Lines 42 to 45: **try-with-resources** with two resources, separated by a semicolon. `new ServerSocket(0, ONE_WAITING_CONNECTION, InetAddress.getLoopbackAddress())` opens a server socket. The first argument is the port. Port 0 means "any free port", chosen by the operating system, so two runs never collide (the comment says so). The second is the queue size. The third is the address to listen on: `InetAddress.getLoopbackAddress()` is the loopback address, so the server listens only for programs on this machine. The second resource is an `ExecutorService`: `Executors.newVirtualThreadPerTaskExecutor()` gives a service that starts a **virtual thread** for each task. Both are closed at the end of the block, in the reverse order: the executor first, then the server socket. Closing an `ExecutorService` waits for its tasks to finish, which needs Java 19 or later.
- **A thread** is one line of execution in a process. A **virtual thread** (Java 21 and later) is a very cheap thread that the JVM manages itself. It is made for tasks that mostly wait, as a server waiting on a socket does. The probe needs a second line of execution, so that the server can accept and echo while the client measures, and a virtual thread is the lightest way to get one.
- Line 46: `server.setSoTimeout(timeoutMillis)` sets how long the server's `accept` may wait for a client before it throws `SocketTimeoutException`. "SO" stands for socket option.
- Line 47: `executor.submit(() -> echoOneClient(server, timeoutMillis))` starts the echo task and returns a `Future<Void>`. A **`Future`** is a receipt for work in progress: you can wait on it. The lambda `() -> echoOneClient(...)` is a task with no input that returns a value. `Void` is the type of "no value", and the task returns `null`.
- Line 48: `timeClient(...)` does the measuring in the current thread. Line 49: `awaitEcho(echo, settings)` waits until the echo task has ended. Line 50 returns the samples.

**Lines 54 to 74: `timeClient`**

```java
  private static LatencySamples timeClient(
      ServerSocket server, ProbeSettings settings, int timeoutMillis) throws IOException {
    try (Socket client = new Socket()) {
      client.connect(server.getLocalSocketAddress(), timeoutMillis);
      // Without TCP_NODELAY the kernel may hold a 1-byte write back to batch it, adding delay.
      client.setTcpNoDelay(true);
      client.setSoTimeout(timeoutMillis);
      OutputStream out = client.getOutputStream();
      InputStream in = client.getInputStream();
      for (int warmupIndex = 0; warmupIndex < settings.warmupSamples(); warmupIndex++) {
        roundTrip(out, in);
      }
      long[] samples = new long[settings.samples()];
      for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
        long start = System.nanoTime();
        roundTrip(out, in);
        samples[sampleIndex] = System.nanoTime() - start;
      }
      return new LatencySamples(samples, 1);
    }
  }
```

- `try (Socket client = new Socket())` makes an unconnected socket that closes at the end. `client.connect(server.getLocalSocketAddress(), timeoutMillis)` connects to the server's address (the loopback address and the chosen port), waiting at most the timeout. Because the server's queue holds one waiting connection, the connect completes even before the server has called `accept`.
- Line 59: `client.setTcpNoDelay(true)` turns off **Nagle's algorithm**. By default, TCP may hold a very small write back for a moment to combine it with the next one, to save packets. That would add delay to a one-byte message. The comment says so. `TCP_NODELAY` is the name of this option. `client.setSoTimeout(timeoutMillis)` makes a read throw if it waits longer than the timeout.
- Lines 61 and 62: the two streams of the connection: `out` to send bytes, `in` to receive them.
- Lines 63 to 65: the **warm-up**: 500 untimed round trips.
- Lines 66 to 71: 2,000 timed samples. Each: read the clock, do one `roundTrip`, read the clock, store the difference.
- Line 72: `new LatencySamples(samples, 1)`. At the end of the block the client socket is closed. That makes the server's read return -1, and the echo task ends.

**Lines 76 to 82: `roundTrip`**

```java
  private static void roundTrip(OutputStream out, InputStream in) throws IOException {
    out.write(PING);
    out.flush();
    if (in.read() != PING) {
      throw new IOException("the echo server answered with the wrong byte or closed early");
    }
  }
```

- `out.write(PING)` sends one byte. `out.flush()` pushes it out of any buffer. `in.read()` waits for and returns one byte as a number from 0 to 255, or -1 if the connection closed. If what comes back is not the byte that was sent, the method throws an `IOException`. One call of this method is one **round trip**: a byte out and the same byte back.

**Lines 84 to 97: `echoOneClient`**

```java
  private static Void echoOneClient(ServerSocket server, int timeoutMillis) throws IOException {
    try (Socket connection = server.accept()) {
      connection.setTcpNoDelay(true);
      connection.setSoTimeout(timeoutMillis);
      InputStream in = connection.getInputStream();
      OutputStream out = connection.getOutputStream();
      // The client closing its socket ends the stream with -1, which ends the echo.
      for (int received = in.read(); received >= 0; received = in.read()) {
        out.write(received);
        out.flush();
      }
    }
    return null;
  }
```

- This is the task that runs in the virtual thread. `server.accept()` waits for one client to connect and returns a `Socket` for it. It is in a try-with-resources, so it closes at the end.
- The same two options are set on the accepted connection.
- Line 91: `for (int received = in.read(); received >= 0; received = in.read())` reads a byte, and while the byte is not -1 it writes the same byte back and flushes. The comment says that the client closing its socket ends the stream with -1, which ends the echo. The loop runs once for every round trip.
- `return null;` returns the `Void` value that a `Callable<Void>` must return.

**Lines 99 to 115: `awaitEcho`**

```java
  private static void awaitEcho(Future<Void> echo, ProbeSettings settings) throws IOException {
    try {
      echo.get(settings.socketTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException failed) {
      throw new IOException("the echo server failed", failed.getCause());
    } catch (TimeoutException tooSlow) {
      echo.cancel(true);
      throw new IOException("the echo server did not stop within the socket timeout", tooSlow);
    } catch (InterruptedException interrupted) {
      // Keep the interrupt visible to the caller and stop measuring.
      Thread.currentThread().interrupt();
      InterruptedIOException stopped = new InterruptedIOException("interrupted while measuring");
      stopped.initCause(interrupted);
      throw stopped;
    }
  }
}
```

- `echo.get(timeout, TimeUnit.MILLISECONDS)` waits for the echo task to finish, at most the timeout. It can fail in three ways, and each is turned into an `IOException` so the caller has one failure type.
- `ExecutionException`: the task itself threw. The message says the echo server failed, and `failed.getCause()` carries the original exception.
- `TimeoutException`: the task did not end in time. `echo.cancel(true)` interrupts it. The message says the echo server did not stop within the socket timeout.
- `InterruptedException`: the thread running the probe was interrupted while waiting. The code calls `Thread.currentThread().interrupt()` to set the flag again, because catching the exception cleared it, so the caller can still see that an interrupt happened. It then throws an `InterruptedIOException` made with a message and the original exception as its cause (`initCause`).

### `ProbeReport.java`

**Lines 1 to 29: imports, comment and constants**

```java
package io.plainround.seatwave.estimates.probe;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Renders the measured latencies next to the published orders of magnitude.
 *
 * <p>Every duration is a {@link Quantity}, printed in the largest unit that keeps it at 1 or more.
 * A measured p50 that differs from the published value by more than ten times in either direction
 * is marked, because Lab 2 asks for each such gap to be explained. Stateless and safe to use from
 * any thread.
 */
public final class ProbeReport {

  /** A gap above this many times between measured and published values is marked. */
  static final BigDecimal GAP_FACTOR = new BigDecimal("10");

  private static final BigDecimal P50 = new BigDecimal("50");
  private static final BigDecimal P99 = new BigDecimal("99");
  private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
  private static final String ROW_FORMAT = "%-32s %-14s %-12s %-12s %-12s %-31s %s%n";

  private ProbeReport() {}
```

- The Javadoc says every duration is a `Quantity`, printed in the largest unit that keeps it at 1 or more. A measured p50 that differs from the published value by more than ten times in either direction is marked, because Lab 2 asks for each such gap to be explained.
- `GAP_FACTOR` is 10. `P50` and `P99` are the percents used. `ONE_THOUSAND` is the step between nanoseconds, microseconds and milliseconds.
- `ROW_FORMAT` is the template of one table row: `%-32s %-14s %-12s %-12s %-12s %-31s %s%n`. Each `%-32s` is text, left-aligned, in a field 32 characters wide. The last `%s` is the comparison, and `%n` is a line break in the machine's style. The same template prints the header and every row, so the columns line up.

**Lines 31 to 53: `render`**

```java
  /**
   * Renders the latency table.
   *
   * @param measured the samples of every operation that was measured, in report order
   * @return one header line, then one line per operation
   */
  public static String render(Map<ProbeOperation, LatencySamples> measured) {
    Objects.requireNonNull(measured, "measured");
    StringBuilder text = new StringBuilder();
    text.append(
        String.format(
            Locale.ROOT,
            ROW_FORMAT,
            "Operation",
            "Samples",
            "p50",
            "p99",
            "Published",
            "Published line",
            "p50 / published"));
    measured.forEach((operation, samples) -> text.append(row(operation, samples)));
    return text.toString();
  }
```

- `render(Map<ProbeOperation, LatencySamples> measured)` returns the table as one string. A **map** pairs keys with values. The caller passes a `LinkedHashMap`, which keeps the order in which entries were added, so the rows come in the order the probes ran.
- Lines 40 to 50 append the header line: `Operation`, `Samples`, `p50`, `p99`, `Published`, `Published line`, `p50 / published`. `String.format(Locale.ROOT, ROW_FORMAT, ...)` fills the template with those seven texts. `Locale.ROOT` keeps the format the same on every computer.
- Line 51: `measured.forEach((operation, samples) -> text.append(row(operation, samples)));` calls a lambda for every entry of the map, with the key and the value, and appends the row.
- `StringBuilder` grows one buffer by appending, and `text.toString()` turns it into the result. `render` prints nothing.

**Lines 55 to 68: `readable`**

```java
  /**
   * Prints a duration in the largest unit that keeps the number at 1 or more.
   *
   * @param duration a duration in any duration unit
   * @return for example "98.54 ns", "12.3 µs" or "4.1 ms"
   */
  static Quantity readable(Quantity duration) {
    Quantity nanos = duration.to(Unit.NANOSECONDS);
    if (nanos.amount().compareTo(ONE_THOUSAND) < 0) {
      return nanos;
    }
    Quantity micros = nanos.to(Unit.MICROSECONDS);
    return micros.amount().compareTo(ONE_THOUSAND) < 0 ? micros : nanos.to(Unit.MILLISECONDS);
  }
```

- It prints a duration in the largest unit that keeps the number at 1 or more. First it converts to nanoseconds (`duration.to(Unit.NANOSECONDS)`). If that is below 1,000, it is returned as it is: `98.54 ns`. Otherwise it converts to microseconds. If that is below 1,000, it is returned: `12.3 µs`. Otherwise it converts to milliseconds: `4.1 ms`. The conditional expression `micros... < ONE_THOUSAND ? micros : nanos.to(Unit.MILLISECONDS)` picks one of two quantities.
- A duration of 150,000 ns becomes `150 µs`. A duration of 4,020,000 ns becomes `4.02 ms`.

**Lines 70 to 84: `row`**

```java
  private static String row(ProbeOperation operation, LatencySamples samples) {
    Quantity median = samples.percentile(P50);
    Optional<Quantity> published = operation.textbook();
    String comparison = published.map(value -> gap(median, value)).orElse("not compared");
    return String.format(
        Locale.ROOT,
        ROW_FORMAT,
        operation.label(),
        samples.count(),
        readable(median),
        readable(samples.percentile(P99)),
        published.map(ProbeReport::readable).map(Quantity::toString).orElse("-"),
        operation.textbookLabel(),
        comparison);
  }
```

- `samples.percentile(P50)` is the median. `operation.textbook()` is an `Optional<Quantity>`, the published value or empty.
- Line 73: `published.map(value -> gap(median, value)).orElse("not compared")`. If the box holds a value, `map` calls `gap` with it and returns a box with the text. `orElse("not compared")` takes the text out, or uses the default when the box was empty. That is the text for the two operations that have no published line.
- Lines 74 to 83: the row has the label, `samples.count()` (a `Quantity` printed as `2,000 samples`), the readable p50, the readable p99, the published value (`published.map(ProbeReport::readable).map(Quantity::toString).orElse("-")`: if there is one, make it readable and turn it into text; otherwise a dash), the published line's label and the comparison. `ProbeReport::readable` and `Quantity::toString` are **method references**.

**Lines 86 to 96: `gap`**

```java
  private static String gap(Quantity measured, Quantity published) {
    if (measured.amount().signum() == 0) {
      return "below the timer's resolution";
    }
    Quantity ratio = measured.timesAsMany(published);
    boolean isFar =
        ratio.amount().compareTo(GAP_FACTOR) > 0
            || published.timesAsMany(measured).amount().compareTo(GAP_FACTOR) > 0;
    return ratio + (isFar ? "   <-- gap over 10 times: explain it" : "");
  }
}
```

- `measured.amount().signum() == 0` is true for a median of zero: the operation was faster than the timer can show. The text is `below the timer's resolution`.
- `measured.timesAsMany(published)` is p50 divided by the published value, as a quantity in `times`. A result of 4.25 times means the measured value is 4.25 times the published one. A result of 0.0083 means it is 120 times smaller.
- Lines 91 to 93: `isFar` is true when the ratio is above 10, or when the opposite ratio (published divided by measured) is above 10. That covers a gap in either direction.
- Line 94: the text is the ratio, followed by `   <-- gap over 10 times: explain it` when `isFar`. Both ratios use `timesAsMany`, which throws if the two units differ. Here both are in nanoseconds, because `ProbeOperation` makes the published values in nanoseconds and `percentile` returns nanoseconds.

### `ProbeCli.java`

**Lines 1 to 28: imports, comment and constants**

```java
package io.plainround.seatwave.estimates.probe;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Command-line entry point of the latency probe.
 *
 * <p>It reads {@code probe.properties}, prints the machine it runs on, measures each operation and
 * prints the table. Exit status 2 means bad settings; 1 means a measurement failed.
 */
public final class ProbeCli {

  private static final int EXIT_MEASUREMENT_FAILED = 1;
  // Exit status 2 is the shell convention for "the command was used incorrectly".
  private static final int EXIT_BAD_USAGE = 2;
  private static final long BYTES_PER_MEBIBYTE = 1L << 20;

  private ProbeCli() {}
```

- `Reader` is a source of text characters. `LinkedHashMap` is a map that remembers insertion order. `Locale` and `Properties` are as before.
- The Javadoc: it reads `probe.properties`, prints the machine it runs on, measures each operation and prints the table. Exit status 2 means bad settings and 1 means a measurement failed.
- `EXIT_MEASUREMENT_FAILED = 1` and `EXIT_BAD_USAGE = 2`. The comment says status 2 is the shell convention for "the command was used incorrectly". `BYTES_PER_MEBIBYTE = 1L << 20` is `1 << 20`, a left shift: 1 moved 20 bits to the left is 2 to the power 20, 1,048,576. The `L` makes it a `long`. The program divides the heap limit in bytes by it to get MiB.

**Lines 30 to 48: `main`**

```java
  /**
   * Runs the probe.
   *
   * @param arguments the path to the settings file, then the folder for the temporary file
   */
  public static void main(String[] arguments) {
    if (arguments.length != 2) {
      System.err.println("usage: probe <probe.properties> <folder for the temporary file>");
      System.exit(EXIT_BAD_USAGE);
    }
    switch (ProbeSettings.from(load(Path.of(arguments[0])))) {
      case Result.Success<ProbeSettings, List<String>>(ProbeSettings settings) ->
          run(settings, Path.of(arguments[1]));
      case Result.Error<ProbeSettings, List<String>>(List<String> problems) -> {
        problems.forEach(problem -> System.err.println("invalid setting: " + problem));
        System.exit(EXIT_BAD_USAGE);
      }
    }
  }
```

- `main` is the entry point. Two arguments are required: the settings file and the folder for the temporary file. With any other count, it prints the usage to `System.err` and calls `System.exit(2)`.
- Line 40: `ProbeSettings.from(load(Path.of(arguments[0])))` loads the properties and converts them to settings. The `switch` has two arms, each a **record pattern**. A success binds the settings and calls `run(settings, Path.of(arguments[1]))`. An error binds the list of problems and prints `invalid setting: ` and one line for each, then exits with 2. Both cases of the sealed `Result` are covered, so there is no `default`.

**Lines 50 to 79: `run`**

```java
  private static void run(ProbeSettings settings, Path folder) {
    Runtime runtime = Runtime.getRuntime();
    System.out.printf(
        Locale.ROOT,
        "Machine: %s %s on %s, %d logical processors, Java %s, heap limit %d MiB%n",
        System.getProperty("os.name"),
        System.getProperty("os.version"),
        System.getProperty("os.arch"),
        runtime.availableProcessors(),
        System.getProperty("java.version"),
        runtime.maxMemory() / BYTES_PER_MEBIBYTE);
    try {
      Map<ProbeOperation, LatencySamples> measured = new LinkedHashMap<>();
      measured.put(ProbeOperation.TIMER_STEP, MemoryProbe.timerStep(settings));
      measured.put(
          ProbeOperation.CACHE_READ, MemoryProbe.arrayReads(settings, settings.cacheArrayBytes()));
      measured.put(
          ProbeOperation.MEMORY_READ,
          MemoryProbe.arrayReads(settings, settings.memoryArrayBytes()));
      DiskProbe.DiskResults disk = DiskProbe.measure(settings, folder);
      measured.put(ProbeOperation.DISK_READ, disk.reads());
      measured.put(ProbeOperation.DISK_WRITE_FORCED, disk.forcedWrites());
      measured.put(ProbeOperation.LOOPBACK_ROUND_TRIP, LoopbackProbe.roundTrips(settings));
      System.out.print(ProbeReport.render(measured));
      System.out.println("These numbers describe this machine only. Yours will differ.");
    } catch (IOException measurementFailed) {
      System.err.println("a measurement failed: " + measurementFailed);
      System.exit(EXIT_MEASUREMENT_FAILED);
    }
  }
```

- `Runtime.getRuntime()` is the object that describes the running JVM. `System.out.printf(Locale.ROOT, template, values...)` prints a formatted line. The template `Machine: %s %s on %s, %d logical processors, Java %s, heap limit %d MiB%n` has three `%s` for text, one `%d` for a whole number, `%s` again and `%d`. The values: `System.getProperty("os.name")` (the operating system's name), `os.version`, `os.arch` (the processor type, for example `aarch64`), `runtime.availableProcessors()` (the number of logical processors), `java.version` and `runtime.maxMemory() / BYTES_PER_MEBIBYTE` (the heap limit, in MiB). A **logical processor** is a hardware thread the operating system can schedule.
- The heap limit matters: `memory.array.bytes` is 256 MiB, and the array must fit in the heap. The default limit is a quarter of the machine's memory, which on the verification machine was 8,192 MiB.
- Lines 62 to 72: a `LinkedHashMap` keeps the order. The six probes are run in order: the timer step, the cache-sized array (`settings.cacheArrayBytes()`), the memory-sized array (`settings.memoryArrayBytes()`), the disk (one call gives both `reads()` and `forcedWrites()`) and the loopback round trip. Each result is `put` into the map under its `ProbeOperation`.
- Line 73: `System.out.print(ProbeReport.render(measured))` prints the table. Line 74 prints the closing line: `These numbers describe this machine only. Yours will differ.`
- Lines 75 to 78: `catch (IOException measurementFailed)` catches a failure of the disk or the network probe, prints `a measurement failed: ` and the exception, and exits with status 1.

**Lines 81 to 93: `load`**

```java
  private static Properties load(Path path) {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      properties.load(reader);
      return properties;
    } catch (IOException unreadable) {
      // A missing settings file is bad usage: say which file and why, then stop before measuring.
      System.err.println("cannot read " + path + ": " + unreadable.getMessage());
      System.exit(EXIT_BAD_USAGE);
      throw new IllegalStateException("System.exit returned", unreadable);
    }
  }
}
```

- `Files.newBufferedReader(path, StandardCharsets.UTF_8)` opens the file as a reader of UTF-8 text. It is in a try-with-resources, so it closes at the end. `properties.load(reader)` reads the `key=value` lines into the `Properties` object. The `Reader` form means the characters are decoded as UTF-8, not as the old ISO-8859-1 default of the byte-stream form.
- `catch (IOException unreadable)`: a missing or unreadable file is bad usage. The comment says to say which file and why, then stop before measuring. The message is `cannot read ` + the path + the reason. After `System.exit(...)`, the compiler needs the method to return or throw, so the last line is `throw new IllegalStateException("System.exit returned", unreadable)`, which never runs.
- The last `}` closes the class.

## Try it yourself

Run these in `seatwave-estimates/`.

1. Run the probe. The first command makes the folder for the temporary file (`build/` is not tracked by Git). The second runs the probe.

```
mkdir -p build/probe
./gradlew -q probe --args='probe.properties build/probe'
```

It takes about 4.5 seconds. The numbers differ on every run and on every machine. This is one real run, on a Mac with an M2 Max processor, 12 logical processors and Java 25.0.2:

```
Machine: Mac OS X 27.2 on aarch64, 12 logical processors, Java 25.0.2, heap limit 8192 MiB
Operation                        Samples        p50          p99          Published    Published line                  p50 / published
timer: two nanoTime calls        2,000 samples  41 ns        42 ns        -            none in the table               not compared
array read, cache-sized array    2,000 samples  2.17 ns      6.42 ns      0.5 ns       L1 cache reference              4.33 times
array read, memory-sized array   2,000 samples  115 ns       145 ns       100 ns       main memory reference           1.15 times
random 4 KiB file read           2,000 samples  1.21 µs      2.58 µs      150 µs       read 4K randomly from SSD       0.008053 times   <-- gap over 10 times: explain it
4 KiB write + force (fsync)      500 samples    4.02 ms      5.01 ms      -            none in the table               not compared
TCP round trip on loopback       2,000 samples  35.42 µs     63.38 µs     500 µs       round trip in one data center   0.07083 times   <-- gap over 10 times: explain it
These numbers describe this machine only. Yours will differ.
```

Read it like this. The memory read is about 1 times the published 100 ns: main memory has not changed much. The cache read is a few times the published 0.5 ns, because the probe's loop also does a little work. The file read is more than 100 times faster than the published SSD figure, because the file is in the page cache. The loopback round trip is 14 times faster than the published data-center figure, because no wire is involved. Two rows are marked, and Lab 2 asks you to explain each.

2. Prove the temporary file is deleted:

```
ls build/probe
```

It prints nothing: the folder is empty after the run.

3. Break the settings. Copy `probe.properties` to `build/bad.properties`, delete the `samples` line, change `disk.block.bytes` to `0` and `random.seed` to `abc`, and run:

```
./gradlew -q probe --args='build/bad.properties build/probe'
```

I got `invalid setting: samples is missing`, `invalid setting: disk.block.bytes must be 1 or more, but was 0` and `invalid setting: random.seed must be a whole number up to 2147483647, but was abc`, and Gradle reported `finished with non-zero exit value 2`.

4. Ask for a file that does not exist:

```
./gradlew -q probe --args='build/nope.properties build/probe'
```

I got `cannot read build/nope.properties: build/nope.properties` and exit value 2.

5. Make the file smaller than one block. Copy `probe.properties` to `build/bad3.properties` with `disk.file.bytes=1000`:

```
./gradlew -q probe --args='build/bad3.properties build/probe'
```

I got `invalid setting: the file and the arrays must hold at least one block`. Each key was valid on its own, but together they broke a rule, which is the case that lines 103 to 106 of `ProbeSettings.java` catch.

## Mistakes people make

- **Timing a loop once, cold.** The first runs are interpreted. This probe warms up and throws the warm-up away.
- **Timing one very fast operation.** The timer's step is larger than a 1 ns read. The probe times 1,000 reads together and divides.
- **Letting reads overlap.** The CPU overlaps independent reads and prefetches the next address. Pointer chasing makes each read depend on the last.
- **Letting the compiler delete the work.** A loop whose result nobody uses may be removed. The probe uses the final position.
- **Reading a file the system has just written and calling it disk speed.** The page cache serves the read. The report says so.
- **Calling a write "durable" because `write` returned.** Only `force` asks for the data to reach the device.
- **Using the wall clock to time a duration.** Use `System.nanoTime`.
- **Using the mean.** A mean hides the slow end. Read p99 beside p50.
- **Expecting your numbers to match the table.** The table describes 2012 hardware, and your machine is not that.

## Check yourself

1. Why does the probe time 1,000 reads together instead of one? Answer: a single read of a cached value takes about 1 ns, which is smaller than the timer's own step, so one timing could not show it. Dividing the time of 1,000 reads by 1,000 gives a usable figure.
2. How is p99 computed from 2,000 samples? Answer: sort them, compute the rank ceiling(99 x 2000 / 100) = 1980, and take the 1,980th smallest sample. 20 samples are slower.
3. Why does a random file read take about a microsecond and not 150 microseconds? Answer: the probe has just written the file, so the operating system's page cache holds it in memory. The read costs a system call and a copy, not a trip to the device.
4. What does `channel.force(false)` ask the operating system to do? Answer: write the modified content of the file to the storage device, and return only when the system says it has. The `false` says the file's metadata need not be written.
5. Why is the loopback round trip much faster than the published 500 microseconds? Answer: the bytes never leave the machine. The operating system hands them from one program to the other in memory. There is no cable, switch or network card.

## Further reading

- `java.lang.System.nanoTime`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html#nanoTime()
- `java.nio.channels.FileChannel`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/channels/FileChannel.html
- `java.net.ServerSocket`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/ServerSocket.html
- `java.util.concurrent.Executors`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/Executors.html
- Virtual threads, https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html
- `java.util.Properties`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Properties.html