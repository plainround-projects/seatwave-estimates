# Instancio 6.2.1

## What it is

Tests need input data. Writing it by hand is slow, and it only covers the cases you think of. **Instancio** is a Java library that **generates** test data: it can fill a list with random values or build an object with random fields. A generator that is random but **seeded** is repeatable: the **seed** is the starting number of the random sequence. The same seed always produces the same values, so a test that fails today fails the same way tomorrow.

## Why this repository uses it

The rule of the `units` package is that every printed number ends with its unit symbol. A rule like that is only convincing if it is tried on many values, not three chosen ones. `QuantityTest.everyPrintedQuantityEndsWithItsUnitSymbol` asks Instancio for 200 amounts and prints each one in every one of the 33 units: 6,600 combinations. The README gives the reason: "Seeded generated amounts for the test that every printed quantity ends with its unit." The fixed seed keeps every run identical.

## Where it appears

| Path | What it does with Instancio |
| --- | --- |
| `gradle/libs.versions.toml` | `instancio = "6.2.1"` and `instancio-junit`. |
| `build.gradle.kts` | `testImplementation(libs.instancio.junit)`. |
| `gradle.lockfile` | `instancio-junit`, `instancio-core`, `jspecify` 1.0.1 and `slf4j-api` 2.0.20 (`dependency-locking.md`). |
| `src/test/java/io/plainround/seatwave/estimates/units/QuantityTest.java` | The only file that uses it. |

## How it works underneath

1. `Instancio.ofList(Double.class)` starts a **builder** for a list of `Double` values (a `Double` is Java's boxed decimal floating-point number).
2. Each builder call (`size`, `generate`, `withSeed`) changes one setting and returns the builder.
3. `create()` builds the list. For each element Instancio asks a **generator** for a value. A generator is the piece of code that makes one kind of value.
4. The random numbers come from a random-number generator that starts from the seed. With `withSeed(SEED)` the sequence is fixed, so `create()` gives the same list on every run and every machine.
5. Without `withSeed`, Instancio picks a random seed each run. This repository does not use the JUnit extension that reports the seed on failure; it does not need it, because the seed is always known.

`instancio-junit` is the artifact that brings the JUnit extension and `instancio-core`. The code here calls only the core builder API, so it works as a plain library.

## Walkthrough of this repository's use

### The seed and the constants

```java
  // A fixed seed makes every run generate the same amounts, so a failure can be replayed.
  private static final long SEED = 20_261_110L;
  private static final int GENERATED_AMOUNTS = 200;
```

- `SEED = 20_261_110L` is the seed. Java lets you write underscores in numbers for readability and `L` marks a `long`. The digits are a date, 2026-11-10. The comment says why it is fixed.
- `GENERATED_AMOUNTS = 200` is how many amounts the test generates.

### The generator

```java
    List<Double> amounts =
        Instancio.ofList(Double.class)
            .size(GENERATED_AMOUNTS)
            .generate(Select.all(Double.class), generator -> generator.doubles().range(0.0, 1e15))
            .withSeed(SEED)
            .create();
```

- `Instancio.ofList(Double.class)` is the builder for a list of `Double`.
- `.size(GENERATED_AMOUNTS)` makes it 200 elements long.
- `.generate(Select.all(Double.class), generator -> generator.doubles().range(0.0, 1e15))` says how to make each `Double`. `Select.all(Double.class)` is a **selector** that picks every `Double` in what is being built. The lambda `generator -> ...` receives Instancio's generators, and `generator.doubles().range(0.0, 1e15)` restricts the values to the range 0 to 10 to the power 15 (`1e15` is scientific notation). The range matters: I tried the same builder without it, and with this seed the 200 default values lay between 35.06 and 9995.16 (none negative). With the range they lie between 6.6 times 10 to the power 12 and 9.995 times 10 to the power 14, so the test exercises large amounts.
- `.withSeed(SEED)` fixes the random sequence.
- `.create()` builds and returns the list.

I confirmed the repeatability with a small program, kept outside the repository, that uses this exact call with a list of three values. Calling it twice with the seed 20261110 printed `[9.53225063012764E12, 3.0281309363852706E14, 2.929330944074827E14]` both times, and the seed 1 printed `[4.074398012118764E14, 3.327170559595112E14, 7.107396275716601E14]`. The notation `9.53E12` means 9.53 times 10 to the power 12.

### How the list is used

```java
    for (Unit unit : Unit.values()) {
      for (Double amount : amounts) {
        String printed = new Quantity(BigDecimal.valueOf(amount), unit).toString();

        assertThat(printed).endsWith(" " + unit.symbol());
      }
    }
```

The outer loop takes each of the 33 units. The inner loop takes each of the 200 amounts. `BigDecimal.valueOf(amount)` turns a `Double` into an exact decimal through its shortest text form. Each pair is printed by `Quantity.toString()`, and `endsWith(" " + unit.symbol())` checks that the text finishes with a space and the unit symbol. With this seed every generated amount is far above 100, so they exercise the "no decimals and grouped digits" printing rule (`tests-units-walkthrough.md`). The small-number, middle-number and money rules are checked by their own tests with chosen values.

## Try it yourself

1. Find the only file that uses Instancio:

```
grep -rl "org.instancio" src/test
```

   Expected: `src/test/java/io/plainround/seatwave/estimates/units/QuantityTest.java`.

2. Run the test that uses it:

```
./gradlew cleanTest test --tests '*QuantityTest.everyPrintedQuantityEndsWithItsUnitSymbol'
```

   Expected: one `PASSED` line and `BUILD SUCCESSFUL`.

3. Look at Instancio in the dependency tree:

```
./gradlew -q dependencies --configuration testRuntimeClasspath | grep -A3 '^+--- org.instancio:instancio-junit:6.2.1'
```

   Expected: `+--- org.instancio:instancio-junit:6.2.1`, then `instancio-core:6.2.1` with `jspecify:1.0.1` and `slf4j-api:2.0.20` below it.

4. Check the repeatability yourself. In a scratch folder outside the repository, write a `Demo.java` that prints the list twice with `.withSeed(20_261_110L)`, and run it with the jars of `instancio-core`, `slf4j-api` and `jspecify` (from the Gradle cache folder `~/.gradle/caches/modules-2`) on the class path: `java -cp "<the three jars>" Demo.java`. Expected: the same three numbers printed twice.

## Mistakes people make

- **Generating random data without a seed.** A failure cannot be replayed.
- **Forgetting the range.** Without `range(0.0, 1e15)` the default doubles are small (35.06 to 9995.16 with this seed), so the test would not reach the very large amounts.
- **Treating generated data as a replacement for chosen examples.** The printing rules have six chosen-value tests next to this one; the generated test only proves the one invariant (the unit at the end).
- **Using a huge list.** 200 amounts times 33 units is 6,600 checks, which run in milliseconds. A million would only slow the build.
- **Changing the seed to make a failing test pass.** A failing seed is a found bug; keep it as a chosen-value test.

## Check yourself

1. *What does `.withSeed(SEED)` do?* It fixes the random sequence, so every run generates the same list.
2. *What does `Select.all(Double.class)` pick?* Every `Double` in what is being built.
3. *Why is the generator restricted with `range(0.0, 1e15)`?* To get amounts of a known, very large size instead of the small default values, and never a negative one (a `Quantity` refuses a negative amount).
4. *How many printed quantities does the test check?* 200 amounts times 33 units, which is 6,600.
5. *Which file is the only user of Instancio?* `QuantityTest.java`.

## Further reading

- Instancio user guide: <https://www.instancio.org/user-guide/>
- Instancio 6.2.1 Javadoc: <https://javadoc.io/doc/org.instancio/instancio-core/6.2.1/index.html>
