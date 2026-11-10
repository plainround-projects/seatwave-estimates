# The `.properties` format and `probe.properties`

## What it is

A **properties file** is a plain text file of settings. Each setting is one line with a **key** (the name) and a **value**, joined by an equals sign: `samples=2000`. The format comes from Java. The JDK class `java.util.Properties` reads it into a map from text to text. A **map** is a collection of pairs where you look a value up by its key. The file ends in `.properties` by habit, but the name does not matter to the reader: the program is told which file to open.

Everything in a properties file is text. The program decides that `2000` is a number and converts it. If the text is not a number, the conversion fails, and the program must say so.

## Why this repository uses it

The latency probe has ten settings: how many samples to take, how big the arrays and the file are, how long a socket may wait, which random seed to use. They are in a file, not in the code, for three reasons. A person can change a number without compiling anything. Every number has a comment that says why it has that value. And there are **no defaults in code**: the probe refuses to run when a key is missing, so two machines can never run two different experiments by accident.

## Where it appears

| File | What it does with the format |
|---|---|
| `probe.properties` | The ten settings, each with a comment line above it. |
| `src/main/java/io/plainround/seatwave/estimates/probe/ProbeCli.java` | `load` opens the file as UTF-8 text and calls `Properties.load`. |
| `src/main/java/io/plainround/seatwave/estimates/probe/ProbeSettings.java` | `from` reads each key with `getProperty`, converts it to a number and checks it. |

## How it works underneath

**The format.** The reader goes through the file line by line.

- A line that starts with `#` or `!` is a **comment**. It is ignored. This file uses `#`.
- A blank line is ignored.
- A line like `key=value` sets a key. The key is the text before the first separator. The separator may be `=`, `:` or white space. This file uses `=`.
- White space around the key and before the value is dropped. White space at the **end** of a value is kept. I loaded `name = hello world ` and got `hello world ` with a trailing space. That is why `ProbeSettings.positiveInt` calls `strip()` before it converts the text.
- A `#` in the middle of a line is not a comment: it is part of the value. I loaded `h=x # not a comment` and got `x # not a comment`. So a comment must be on its own line, as in this file.
- If a key appears twice, the last one wins. I loaded `e=1` and then `e=2` and got `2`.
- A backslash starts an escape: `\t` is a tab, `\n` a line break, `\\` a backslash, `\uXXXX` a character by its code. I loaded `g=C:\temp` and got `C:` and a tab and `emp`. A backslash at the end of a line continues the value on the next line. Windows paths in a properties file must write each backslash twice. This file has no backslashes.
- Dots in a key mean nothing to the reader. `warmup.samples` is just the name `warmup.samples`. The dots are a naming habit that groups keys.

**What the program does with it.** `ProbeCli.load` opens the file with `Files.newBufferedReader(path, StandardCharsets.UTF_8)` and passes the reader to `properties.load(reader)`. A `Reader` gives characters, decoded as UTF-8. Passing an `InputStream` instead would decode the file as ISO-8859-1, an old encoding. `properties.getProperty("samples")` then returns the text `2000`, or `null` when the key is absent. `ProbeSettings.positiveInt` turns the text into an `int` with `Integer.parseInt`. That throws `NumberFormatException` for `2,000` or for `2147483648` (too large for an `int`), and the program catches it and reports a problem line. I ran both and got `NumberFormatException: For input string: "2,000"` and `For input string: "2147483648"`.

**Binary sizes.** Several values are powers of two. A **KiB** (kibibyte) is 1,024 bytes, a **MiB** (mebibyte) is 1,024 x 1,024 = 1,048,576 bytes. The comments in the file use these names.

## Walkthrough of this repository's use

The file has 21 lines: a header comment, then ten pairs of a comment and a setting. Every line that is not a comment is quoted and explained below. Each setting goes through `ProbeSettings.positiveInt`, which requires a whole number of 1 or more.

**Line 1: the header comment**

```properties
# Settings of the latency probe. Every key is required; the probe has no defaults.
```

- It says every key is required and the probe has no defaults. `ProbeSettings.from` reports each missing key as `<key> is missing`.

**`samples`**

```properties
# Timed samples kept for each operation, after the warm-up. 2,000 samples put 20 above p99.
samples=2000
```

- `samples` is the number of timed measurements kept for each operation after the warm-up. The record component is `samples`. 2,000 samples put 20 above p99, because the 99th percentile of 2,000 samples is the 1,980th smallest, and 2,000 - 1,980 = 20 samples are slower. Fewer samples would make p99 depend on a handful of values. `MemoryProbe`, `DiskProbe` and `LoopbackProbe` each allocate an array of this length.

**`warmup.samples`**

```properties
# Samples run and thrown away first, so the JIT compiler has compiled the timed loops.
warmup.samples=500
```

- `warmup.samples` is how many rounds are run and thrown away before the timed ones. The comment says why: the JIT compiler (the part of the JVM that turns busy bytecode into machine code) has to compile the timed loops first, or the first samples would time the interpreter. 500 is this project's choice. It is a number of rounds, not a proof that compilation has finished, so on a very slow machine a larger value is safer. The disk reads, the memory reads and the loopback round trips each use it.

**`memory.reads.per.sample`**

```properties
# Array reads timed together in one sample: nanoTime cannot see a single read on its own.
memory.reads.per.sample=1000
```

- `memory.reads.per.sample` is how many array reads are timed together as one sample. One read of a cached value takes about a nanosecond, which is less than the timer's own step, so a single read cannot be timed. The probe times 1,000 reads and divides by 1,000. With 2,000 samples that is 2,000,000 reads per array, plus 500,000 for the warm-up.

**`memory.array.bytes`**

```properties
# 256 MiB: far larger than any CPU cache, so reads come from main memory.
memory.array.bytes=268435456
```

- `memory.array.bytes` is the size of the array that must come from main memory: 268,435,456 bytes is 256 x 1,048,576 = 2^28 bytes, which is 256 MiB. As an `int[]` that is 268,435,456 / 4 = 67,108,864 elements. It is far larger than any CPU cache, so reads at random places miss the cache and wait for main memory. The key must fit in an `int`, whose limit is 2,147,483,647.

**`cache.array.bytes`**

```properties
# 16 KiB: small enough to stay in the first-level data cache of a current CPU.
cache.array.bytes=16384
```

- `cache.array.bytes` is the size of the small array: 16,384 bytes is 16 x 1,024 = 2^14 bytes, which is 16 KiB, or 4,096 `int` elements. The comment says it is small enough to stay in the first-level data cache of a current CPU. `ProbeSettings` also requires it to hold at least one `int` (4 bytes).

**`disk.file.bytes`**

```properties
# 64 MiB temporary file for the disk reads and writes; it is deleted at the end.
disk.file.bytes=67108864
```

- `disk.file.bytes` is the size of the temporary file: 67,108,864 bytes is 64 x 1,048,576 = 2^26 bytes, which is 64 MiB. The probe writes it, times reads and writes on it and deletes it at the end. With 4 KiB blocks it has 16,384 blocks.

**`disk.block.bytes`**

```properties
# 4 KiB, the block size of the published "read 4K randomly from SSD" line.
disk.block.bytes=4096
```

- `disk.block.bytes` is the size of one read or write: 4,096 bytes is 2^12 bytes, 4 KiB. The comment says it is the block size of the published "read 4K randomly from SSD" line, so the measurement matches the table it is compared with. `ProbeSettings` requires the file to be at least one block: with `disk.file.bytes=1000` I got `invalid setting: the file and the arrays must hold at least one block`.

**`disk.write.samples`**

```properties
# Forced writes wait for the device, so fewer are timed.
disk.write.samples=500
```

- `disk.write.samples` is how many forced writes are timed. A forced write waits for the storage device, about 4 ms on the verification machine, so 500 of them take about 2 seconds. 2,000 would take 8 seconds and add little.

**`socket.timeout.millis`**

```properties
# The longest any socket call may block before the probe gives up.
socket.timeout.millis=2000
```

- `socket.timeout.millis` is in milliseconds: 2,000 is 2 seconds. It is the longest any socket call may block (connect, accept, read, and the wait for the echo task) before the probe gives up. `ProbeSettings` turns it into a `Duration` with `Duration.ofMillis`. Without a bound, a broken connection would hang the probe.

**`random.seed`**

```properties
# The seed that makes every run visit the same positions.
random.seed=20261110
```

- `random.seed` is the starting number for the random generator. The same seed gives the same sequence, so every run builds the same array cycle and visits the same file positions. 20261110 is the date 10 November 2026 written as digits. The record component is a `long`, but the loader reads it with `positiveInt`, so it must be a whole number from 1 to 2,147,483,647.

## Try it yourself

Run these in `seatwave-estimates/`.

1. Print the file with line numbers:

```
cat -n probe.properties
```

I got the 21 lines quoted above.

2. Run the probe with the shipped file:

```
mkdir -p build/probe
./gradlew -q probe --args='probe.properties build/probe'
```

It prints a `Machine:` line, a table of six operations and a closing line. The numbers differ on every run. See `java-probe-walkthrough.md` for one real run.

3. Remove a key. Copy the file to `build/bad.properties` with the `samples` line deleted, and run:

```
./gradlew -q probe --args='build/bad.properties build/probe'
```

I got `invalid setting: samples is missing` and Gradle reported `finished with non-zero exit value 2`.

4. Put in a typo and a zero. Change `random.seed` to `abc` and `disk.block.bytes` to `0`:

```
./gradlew -q probe --args='build/bad.properties build/probe'
```

I got `invalid setting: disk.block.bytes must be 1 or more, but was 0` and `invalid setting: random.seed must be a whole number up to 2147483647, but was abc`. Both problems are reported in one run.

5. Name a file that does not exist:

```
./gradlew -q probe --args='build/nope.properties build/probe'
```

I got `cannot read build/nope.properties: build/nope.properties` and exit value 2.

## Mistakes people make

- **Putting a comment after a value.** `samples=2000 # many` makes the value `2000 # many`, which is not a number. Write the comment on its own line.
- **Leaving a space at the end of a value.** The reader keeps it. This code strips it before converting, and a number with a stray space still works.
- **Writing `2,000` or `2_000`.** The reader keeps the comma or underscore, and `Integer.parseInt` rejects it. Write `2000`.
- **A Windows path with single backslashes.** `\t` becomes a tab. Write `\\` or use forward slashes.
- **A value too large for an `int`.** `Integer.parseInt` throws above 2,147,483,647. The loader reports the limit in its message.
- **A default hidden in code.** A key that silently falls back to a number in code makes two machines run different experiments. This probe has none.
- **A file in the wrong encoding.** Pass a `Reader` with UTF-8, as `ProbeCli.load` does.

## Check yourself

1. What does a line starting with `#` mean? Answer: it is a comment, and the reader ignores it.
2. What is the value of `h` in the line `h=x # note`? Answer: `x # note`, the whole rest of the line. A `#` in the middle of a line is not a comment.
3. How many `int` elements fit in `memory.array.bytes=268435456`? Answer: 268,435,456 / 4 = 67,108,864.
4. Why does the file say there are no defaults in code? Answer: so that a missing key stops the probe with a clear message, and no machine runs a different experiment from another without saying so.
5. Why does the loader read all ten keys before it decides? Answer: so that a person who made three mistakes sees all three problems in one run.

## Further reading

- `java.util.Properties`, including the full description of the file format, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Properties.html
- `java.lang.Integer.parseInt`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Integer.html#parseInt(java.lang.String)