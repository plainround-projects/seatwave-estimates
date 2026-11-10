# Tests of the `units` package, walked through

## What it is

A **test** is a small program that calls part of your code with a chosen input and checks that the answer is the one you expect. If the answer is wrong, the test fails and says which check broke. A **unit test** checks one small piece, such as one method of one class, in isolation. The two files described here, `UnitTest.java` and `QuantityTest.java`, are the unit tests of the `units` package: the package that holds `Unit` (a name for a measure, such as `bytes` or `Gbps`), `Dimension` (what kind of thing a unit measures) and `Quantity` (a number together with its unit).

The tests are Java classes that live under `src/test/java`, next to a folder tree that copies the folder tree of `src/main/java`. They are not part of the program you run. Gradle compiles them and runs them when you type `./gradlew test` or `./gradlew check` (`gradle.md`). JUnit finds and runs them (`junit.md`), and AssertJ gives the readable checks (`assertj.md`).

## Why this repository uses it

The lesson of this repository is that a number in an estimate must never travel without its unit, and that two different units must never be added by accident. A promise like that is only worth something if a program keeps checking it. These tests are that program. Each test name is a sentence about one behavior, for example `refusesToAddBitsPerSecondToBytesPerSecond`, so the list of test names reads like the list of rules of the package. The `units` package has the highest coverage of the three packages: all 106 of its lines run during the tests (`jacoco.md`).

## Where it appears

| Path | What it does |
| --- | --- |
| `src/test/java/io/plainround/seatwave/estimates/units/UnitTest.java` | 4 tests of `Unit`: finding a unit by its symbol, distinct symbols, the size of a gigabit. |
| `src/test/java/io/plainround/seatwave/estimates/units/QuantityTest.java` | 23 tests of `Quantity`: conversions, refusals, arithmetic, printing, and one test on generated data. |
| `src/main/java/io/plainround/seatwave/estimates/units/` | The code under test: `Unit.java`, `Dimension.java`, `Quantity.java`. |
| `build.gradle.kts` | Puts JUnit, AssertJ and Instancio on the test classpath (`gradle.md`). |

## How it works underneath

Here is what happens, in order, when `./gradlew test` reaches these two files.

1. Gradle compiles every file under `src/test/java` together with the compiled main code. A class with a name that ends in `Test` is only a naming habit; what makes a method a test is the label `@Test` above it. A **label of this kind is called an annotation**: a word with `@` in front that a tool reads.
2. JUnit scans the compiled test classes for `@Test` methods. For each one it creates a fresh object of the test class, calls the method, and throws the object away. So one test cannot leave anything behind for the next.
3. A test method that returns normally has passed. A method that throws an error has failed. An **assertion** is a line that throws an error when a fact is false. `assertThat(x).isEqualTo(y)` throws if `x` is not equal to `y`.
4. Gradle prints one line per test, for example `QuantityTest > addsQuantitiesOfTheSameUnit() PASSED`, and writes an XML result file per class under `build/test-results/test/`.

Words used in the code below, explained once here.

- A **package** is a named folder of classes. `package io.plainround.seatwave.estimates.units;` is the first line of a file and says which folder the class belongs to. A test class in the same package as the class it tests can use that class without further ceremony, and can also use members that have no `public` in front of them.
- An **import** line brings a class from another package into the file, so its short name can be used. `import static ...assertThat;` is a **static import**: it brings in a method, so you can write `assertThat(...)` instead of `Assertions.assertThat(...)`.
- A **method** is a named block of code. `void` in front of a name means "returns nothing". `private static final` on a field means: a value owned by the class, set once, never changed (a **constant**).
- A **local variable** is a name for a value inside a method, such as `Quantity bytes = ...;`.
- A **record** is a Java type that holds named values and gets its constructor, getters, `equals` and `toString` written for it. `Quantity` is a record with two parts, `amount` and `unit`. `equals` on a record compares all parts, so two `Quantity` objects with the same amount and the same unit are equal.
- An **enum** is a type with a fixed list of named values. `Unit` is an enum: `Unit.BYTES`, `Unit.GIGABITS_PER_SECOND` and thirty-one more. `Unit.values()` returns all of them as an array.
- `BigDecimal` is Java's exact decimal number. Unlike `double`, it never turns 0.1 into 0.1000000000000000055. Estimates involving money need exact decimals. Its `amount()` is written as text in tests, for example `"125000000"`, so no precision is lost on the way in.
- A **lambda** is a short function written `() -> something`. A test passes one to `assertThatThrownBy` so that the code that should fail runs inside AssertJ's watch, where an exception can be caught and checked.
- **Arrange, act, assert** is the shape of most tests here: build the inputs (arrange), call the code (act), check the result (assert). A blank line separates the parts.

## Walkthrough of this repository's use

### `UnitTest.java`

`UnitTest.java` has four tests of the enum `Unit`. The enum is the list of every unit the program knows, each with a symbol (the text printed after a number), a dimension (what kind of thing it measures) and a size in base units (how many of the smallest unit of that dimension it is worth). The first block below is the top of the file: the package line, the imports and the class line.

```java
package io.plainround.seatwave.estimates.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UnitTest {
```

- `package ...units;` puts the test in the same package as `Unit`.
- `assertThat` is imported statically from AssertJ.
- `java.util.Arrays` is used by one test to turn an array into a stream.
- `org.junit.jupiter.api.Test` is the label that marks a test method.
- `class UnitTest {` opens the class. It has no `public` in front: JUnit 6 does not need it, so the class stays out of the public surface.

```java
  @Test
  void findsAUnitByItsSymbol() {
    assertThat(Unit.withSymbol("Gbps")).contains(Unit.GIGABITS_PER_SECOND);
  }
```

A symbol is the text a sheet writes after a number, such as `Gbps`. `Unit.withSymbol("Gbps")` returns an `Optional<Unit>`: a box that holds a unit or holds nothing, so a missing unit is a value and not an error. `.contains(Unit.GIGABITS_PER_SECOND)` checks that the box is not empty and holds exactly that unit. The behavior matters because the sheet reader (`InputsReader`) finds the unit named in a sheet through this lookup.

```java
  @Test
  void findsNoUnitForAnAmbiguousSymbol() {
    // KB is not a unit here on purpose: it can mean 1,000 or 1,024 bytes.
    assertThat(Unit.withSymbol("KB")).isEmpty();
    assertThat(Unit.withSymbol("bits")).isEmpty();
  }
```

The comment gives the reason in words: `KB` is not a unit in this program on purpose, because it can mean 1,000 bytes or 1,024 bytes, and an estimator that guesses hides the slip it exists to catch. `.isEmpty()` checks that the `Optional` holds nothing. The second line checks `bits`, which is also not a unit (the bit rate is written `Gbps`). If someone ever added `KB` as a convenience, this test would fail and force a decision.

```java
  @Test
  void givesEveryUnitADistinctSymbol() {
    long distinct = Arrays.stream(Unit.values()).map(Unit::symbol).distinct().count();

    assertThat(distinct).isEqualTo(Unit.values().length);
  }
```

`Arrays.stream(Unit.values())` turns the array of all units into a **stream**, a pipeline of values. `.map(Unit::symbol)` replaces each unit by its symbol (`Unit::symbol` is a **method reference**: shorthand for "call `symbol()` on each one"). `.distinct()` drops repeats and `.count()` counts what is left, a `long`. If two units shared a symbol, the count would be smaller than `Unit.values().length`. The behavior matters because the symbol is the key used to read a sheet: two units with one symbol would make the sheet ambiguous.

```java
  @Test
  void keepsEightBitsInEveryByteOfAGigabit() {
    assertThat(Unit.GIGABITS_PER_SECOND.sizeInBaseUnits()).isEqualByComparingTo("125000000");
    assertThat(Unit.GIGABITS_PER_SECOND.dimension()).isEqualTo(Unit.BYTES_PER_SECOND.dimension());
  }
```

Each unit knows its size in base units. For information rates the base unit is bytes per second, and one gigabit per second is 1,000,000,000 bits per second divided by 8 bits per byte, which is 125,000,000 bytes per second. `isEqualByComparingTo("125000000")` compares `BigDecimal` values by size, so `125000000` and `125000000.0` count as equal (plain `isEqualTo` would treat them as different, because their scales differ). The second line checks that `Gbps` and `bytes/s` have the same dimension: that is what allows converting one into the other.

### `QuantityTest.java`

`QuantityTest.java` has 23 tests of the record `Quantity`. They fall into five groups: converting between units (6 tests), refusing a conversion, an addition or a comparison that makes no sense (6), the arithmetic that is allowed (3), refusing bad values (2), printing (5), and one more test that runs on generated data (1). Every `Quantity` here is built by `Quantity.of("text", Unit...)`: a factory method that turns decimal text into a `BigDecimal` and pairs it with a unit. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.Test;

class QuantityTest {
```

- Two static imports from AssertJ: `assertThat` for ordinary checks and `assertThatThrownBy` for checks that code throws.
- `BigDecimal` and `List` come from Java itself.
- `Instancio` and `Select` come from the Instancio library, used by the last test (`instancio.md`).
- `Test` is the JUnit label.
- The comment before `SEED` says why a fixed seed exists. A **seed** is the starting number of a random generator: the same seed always produces the same sequence. `20_261_110L` is the number 20261110, written with underscores for readability (Java allows them in numbers) and with an `L` at the end because it is a `long`, a 64-bit whole number.
- `GENERATED_AMOUNTS = 200` is how many random amounts the last test uses.

```java
  // A fixed seed makes every run generate the same amounts, so a failure can be replayed.
  private static final long SEED = 20_261_110L;
  private static final int GENERATED_AMOUNTS = 200;
```

These two lines are the constants described above, `SEED` and `GENERATED_AMOUNTS`. `private` means that only this class can see them.

```java
  @Test
  void convertsOneGigabitPerSecondIntoBytesPerSecond() {
    Quantity oneGigabit = Quantity.of("1", Unit.GIGABITS_PER_SECOND);

    Quantity bytes = oneGigabit.to(Unit.BYTES_PER_SECOND);

    assertThat(bytes.amount()).isEqualByComparingTo("125000000");
  }
```

Arrange: one gigabit per second. Act: `.to(Unit.BYTES_PER_SECOND)` converts it. Assert: the amount is 125,000,000. Here `.amount()` returns the `BigDecimal` inside the quantity. The test pins the rule "a bit is one eighth of a byte" and the decimal meaning of giga (10 to the power 9), not 2 to the power 30. A bandwidth estimate in this repository goes from bytes per second to gigabits per second, so this conversion must be right.

```java
  @Test
  void convertsBytesPerSecondIntoGigabitsByMultiplyingByEight() {
    Quantity bytes = Quantity.of("1149898148", Unit.BYTES_PER_SECOND);

    Quantity gigabits = bytes.to(Unit.GIGABITS_PER_SECOND);

    assertThat(gigabits.amount()).isEqualByComparingTo("9.199185184");
  }
```

The opposite direction, with a real number from the production estimate: 1,149,898,148 bytes per second is 9.199185184 Gbps. Multiplying by 8 bits per byte gives 9,199,185,184 bits per second, which is 9.199185184 gigabits per second. The test name states the rule, and the number is the actual egress figure of the SeatWave sheet (see `tests-estimate-walkthrough.md`, `convertsPeakEgressFromBytesIntoBits`).

```java
  @Test
  void convertsOneMillionRequestsPerDayIntoAboutTwelvePerSecond() {
    Quantity perDay = Quantity.of("1000000", Unit.REQUESTS_PER_DAY);

    Quantity perSecond = perDay.to(Unit.REQUESTS_PER_SECOND);

    assertThat(perSecond.toString()).isEqualTo("11.57 requests/s");
  }
```

A day has 86,400 seconds, and 1,000,000 divided by 86,400 is 11.574..., so one million requests a day is about twelve requests a second. The test checks the printed form, `"11.57 requests/s"`, so it also checks the printing rule for numbers between 1 and 100 (two decimals). This is the most common estimate in system design, so it deserves its own test.

```java
  @Test
  void convertsGigabytesIntoGibibytes() {
    Quantity decimal = Quantity.of("1073741824", Unit.BYTES);

    assertThat(decimal.to(Unit.GIBIBYTES).amount()).isEqualByComparingTo("1");
    assertThat(decimal.to(Unit.GIGABYTES).amount()).isEqualByComparingTo("1.073741824");
  }
```

A gigabyte (GB) is 1,000,000,000 bytes. A gibibyte (GiB) is 1,073,741,824 bytes (2 to the power 30). The test starts from 1,073,741,824 bytes and checks both: it is exactly 1 GiB and it is 1.073741824 GB. The two `assertThat` lines check two conversions of the same starting value. It matters because a memory size reported by a program in GiB looks about 7 percent smaller than the same size in GB, and the cache estimate prints both.

```java
  @Test
  void convertsYearsIntoDaysOf365() {
    assertThat(Quantity.of("5", Unit.YEARS).to(Unit.DAYS).amount()).isEqualByComparingTo("1825");
  }
```

Five years become 1,825 days: the repository defines a year as exactly 365 days (leap years are ignored, and the README says so). The five-year horizon of the sheet is turned into days this way before the storage estimate compares it with each store's retention.

```java
  @Test
  void convertsPercentIntoAFraction() {
    assertThat(Quantity.of("85", Unit.PERCENT).to(Unit.RATIO).amount())
        .isEqualByComparingTo("0.85");
  }
```

85 percent becomes 0.85 of the whole. A percent is stored with the size `0.01` against the ratio (`Unit.PERCENT`), so the conversion multiplies by 0.01. The `.amount()` call is on a new line only because the formatter wraps lines longer than 100 columns. Every share in the sheet (write share, hot share, utilization) passes through this conversion before it multiplies anything.

```java
  @Test
  void refusesToConvertBytesIntoRequests() {
    Quantity bytes = Quantity.of("1", Unit.BYTES);

    assertThatThrownBy(() -> bytes.to(Unit.REQUESTS_PER_SECOND))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot convert bytes into requests/s");
  }
```

Bytes measure information and requests per second measure a rate: they have different dimensions, so no conversion exists. `assertThatThrownBy(() -> bytes.to(...))` runs the lambda and expects it to throw. `.isInstanceOf(IllegalArgumentException.class)` checks the kind of error and `.hasMessage("cannot convert bytes into requests/s")` checks that the message names both units exactly. Checking the message proves that the error explains the problem instead of only failing. This is the first line of defense against a unit slip.

```java
  @Test
  void refusesToAddBitsPerSecondToBytesPerSecond() {
    Quantity bytes = Quantity.of("125000000", Unit.BYTES_PER_SECOND);
    Quantity bits = Quantity.of("1", Unit.GIGABITS_PER_SECOND);

    assertThatThrownBy(() -> bytes.plus(bits))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot add Gbps and bytes/s");
  }
```

This is the classic mistake the module teaches: adding a figure in bits to a figure in bytes. Both are information rates (the same dimension), yet `plus` refuses because the units differ. The message `cannot add Gbps and bytes/s` names the other quantity first and this one second (the code builds it as "other, then this"), which is why `Gbps` comes first although `bytes` is the receiver. The arrange step builds one quantity per unit, and the assert step runs `bytes.plus(bits)` in a lambda.

```java
  @Test
  void refusesToAddPerDayToPerSecond() {
    Quantity perSecond = Quantity.of("12", Unit.REQUESTS_PER_SECOND);
    Quantity perDay = Quantity.of("1000000", Unit.REQUESTS_PER_DAY);

    assertThatThrownBy(() -> perSecond.plus(perDay))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot add requests/day and requests/s");
  }
```

Two rates that differ by a factor of 86,400: 12 requests per second and 1,000,000 requests per day. `Quantity` refuses to add them even though a conversion exists. The rule of the package is that a conversion is always written out as `to(...)`, never done silently. The message `cannot add requests/day and requests/s` shows the two units.

```java
  @Test
  void refusesToAddGigabytesToBytesWithoutAConversion() {
    Quantity bytes = Quantity.of("1000000000", Unit.BYTES);
    Quantity gigabytes = Quantity.of("1", Unit.GIGABYTES);

    assertThatThrownBy(() -> bytes.plus(gigabytes)).isInstanceOf(IllegalArgumentException.class);
  }
```

Even two units of one dimension (bytes and gigabytes both measure information) are refused by `plus`. Only the type of exception is checked here, because the message is already checked by the two tests above. The behavior is the heart of the design: a result is only ever the sum of numbers in one and the same unit.

```java
  @Test
  void addsQuantitiesOfTheSameUnit() {
    Quantity sum =
        Quantity.of("822", Unit.USD_PER_MONTH).plus(Quantity.of("10317", Unit.USD_PER_MONTH));

    assertThat(sum).isEqualTo(Quantity.of("11139", Unit.USD_PER_MONTH));
  }
```

The allowed case: 822 USD per month plus 10,317 USD per month is 11,139 USD per month. `Quantity.of("822", ...).plus(...)` is written across two lines by the formatter. Because `Quantity` is a record, `isEqualTo(Quantity.of("11139", ...))` compares the amount and the unit together. The two numbers are close to the real storage and bandwidth costs of the production sheet.

```java
  @Test
  void refusesToCompareDifferentUnits() {
    Quantity days = Quantity.of("90", Unit.DAYS);
    Quantity years = Quantity.of("5", Unit.YEARS);

    assertThatThrownBy(() -> days.timesAsMany(years)).isInstanceOf(IllegalArgumentException.class);
  }
```

`timesAsMany` answers "how many times larger is this than that" and gives a plain ratio in the unit `times`. It only makes sense for the same unit. Here 90 days and 5 years are refused, although both are time spans: the program does not convert behind your back.

```java
  @Test
  void refusesToCompareWithZero() {
    Quantity zero = Quantity.of("0", Unit.SERVERS);

    assertThatThrownBy(() -> Quantity.of("4", Unit.SERVERS).timesAsMany(zero))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

Dividing by zero has no answer, so `timesAsMany` refuses a zero denominator with an `IllegalArgumentException`. The test builds a quantity of 0 servers and asks "how many times 4 servers is of 0 servers". The sensitivity check later skips zero results for this reason (`tests-estimate-walkthrough.md`).

```java
  @Test
  void comparesInTimes() {
    Quantity ratio = Quantity.of("66", Unit.SERVERS).timesAsMany(Quantity.of("33", Unit.SERVERS));

    assertThat(ratio).isEqualTo(new Quantity(new BigDecimal("2"), Unit.TIMES));
  }
```

66 servers against 33 servers is 2 times. The result is a `Quantity` in the unit `TIMES`, built here with the constructor `new Quantity(new BigDecimal("2"), Unit.TIMES)` instead of `Quantity.of`. Both ways make the same record, so `isEqualTo` passes. The test shows that the answer of a comparison carries a unit too (`times`), and a ratio of servers is not a number of servers.

```java
  @Test
  void scalesAndKeepsTheUnit() {
    Quantity scaled = Quantity.of("1157", Unit.REQUESTS_PER_SECOND).scaledBy(new BigDecimal("26"));

    assertThat(scaled).isEqualTo(Quantity.of("30082", Unit.REQUESTS_PER_SECOND));
  }
```

`scaledBy` multiplies by a plain number and keeps the unit: 1,157 requests per second times a peak factor of 26 is 30,082 requests per second. The factor is a plain `BigDecimal`, not a `Quantity`, because it is a pure multiplier, and the result is still requests per second. This is how the estimator turns the average rate into the peak rate.

```java
  @Test
  void rejectsANegativeAmount() {
    assertThatThrownBy(() -> Quantity.of("-1", Unit.BYTES))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

An estimate is never negative, so the `Quantity` constructor throws for `-1` bytes. The check lives in the constructor, so no code path can create a negative quantity. `Quantity.of("-1", Unit.BYTES)` is inside the lambda, because the constructor call itself is what must throw.

```java
  @Test
  void rejectsAMissingAmountOrUnit() {
    assertThatThrownBy(() -> new Quantity(null, Unit.BYTES))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Quantity(BigDecimal.ONE, null))
        .isInstanceOf(NullPointerException.class);
  }
```

`null` is Java's "no value". A quantity must have both parts, so a null amount or a null unit throws a `NullPointerException`. The two `assertThatThrownBy` lines cover the two parts. Without this guard a missing unit would show up later as a confusing error far away from its cause.

```java
  @Test
  void printsLargeNumbersWithoutDecimalsAndWithGrouping() {
    assertThat(Quantity.of("30092.59", Unit.REQUESTS_PER_SECOND).toString())
        .isEqualTo("30,093 requests/s");
  }
```

The printing rules appear in the next five tests. Numbers from 100 upward print without decimals and with thousands separators: 30,092.59 prints as `30,093 requests/s`. Decimals on a number that large are noise, and the commas make it readable. `toString()` is the method Java calls whenever an object is printed.

```java
  @Test
  void printsMiddleNumbersWithAtMostTwoDecimals() {
    assertThat(Quantity.of("9.1991", Unit.GIGABITS_PER_SECOND).toString()).isEqualTo("9.2 Gbps");
  }
```

Numbers from 1 up to 100 keep at most two decimals: 9.1991 prints as `9.2 Gbps`. The test shows that a trailing zero is dropped (9.20 becomes 9.2).

```java
  @Test
  void printsSmallNumbersWithFourSignificantDigits() {
    assertThat(Quantity.of("0.2016", Unit.USD_PER_HOUR).toString()).isEqualTo("0.2016 USD/hour");
    assertThat(Quantity.of("0.000123456", Unit.TERABYTES).toString()).isEqualTo("0.0001235 TB");
  }
```

Numbers below 1 keep four significant digits, the digits from the first one that is not zero: 0.2016 stays `0.2016 USD/hour`, and 0.000123456 prints as `0.0001235 TB`. The first value is the server price of the sheet, which must print in full.

```java
  @Test
  void printsMoneyOfADollarOrMoreWithCents() {
    assertThat(Quantity.of("147.168", Unit.USD_PER_MONTH).toString()).isEqualTo("147.17 USD/month");
    assertThat(Quantity.of("822", Unit.USD_PER_MONTH).toString()).isEqualTo("822.00 USD/month");
    assertThat(Quantity.of("13.3056", Unit.USD_PER_HOUR).toString()).isEqualTo("13.31 USD/hour");
  }
```

Money of one dollar or more always shows cents: 147.168 prints as `147.17 USD/month`, a round 822 prints as `822.00 USD/month`, and 13.3056 prints as `13.31 USD/hour`. The three lines check rounding up, a whole number, and the hourly unit. The production estimate prints `13.31 USD/hour` as its peak fleet cost.

```java
  @Test
  void printsZeroWithItsUnit() {
    assertThat(Quantity.of("0", Unit.SERVERS).toString()).isEqualTo("0 servers");
  }
```

Zero prints as `0 servers`: there is no special case that drops the unit. The invariant of the module is that every printed number carries its unit, even zero.

```java
  @Test
  void everyPrintedQuantityEndsWithItsUnitSymbol() {
    List<Double> amounts =
        Instancio.ofList(Double.class)
            .size(GENERATED_AMOUNTS)
            .generate(Select.all(Double.class), generator -> generator.doubles().range(0.0, 1e15))
            .withSeed(SEED)
            .create();

    for (Unit unit : Unit.values()) {
      for (Double amount : amounts) {
        String printed = new Quantity(BigDecimal.valueOf(amount), unit).toString();

        assertThat(printed).endsWith(" " + unit.symbol());
      }
    }
  }
```

This is the one test that uses generated data. `Instancio.ofList(Double.class)` starts a request for a list of random doubles. `.size(GENERATED_AMOUNTS)` asks for 200 of them. `.generate(Select.all(Double.class), generator -> generator.doubles().range(0.0, 1e15))` says: for every `Double` in the result, draw a number between 0 and 10 to the power 15 (`1e15` is scientific notation; with this seed the 200 numbers lie between about 6.6 times 10 to the power 12 and 10 to the power 15). `.withSeed(SEED)` fixes the random sequence, so every run gets the same 200 numbers and a failure can be replayed. `.create()` builds the list.

Then two nested `for` loops run through all 33 units and all 200 amounts: 6,600 combinations. For each, the test builds a `Quantity` with `new Quantity(BigDecimal.valueOf(amount), unit)` and checks that its printed text `endsWith(" " + unit.symbol())`. `BigDecimal.valueOf(double)` turns a decimal number into a `BigDecimal` through its shortest text form. The test proves the invariant "every printed number ends with its unit" for a wide spread of sizes and for every unit, not for three chosen examples. The seed keeps it deterministic (`instancio.md`).

## Try it yourself

1. Run only the two test classes of this package:

```
./gradlew cleanTest test --tests '*units*'
```

   Expected: 27 lines that end in `PASSED` (4 for `UnitTest`, 23 for `QuantityTest`) and `BUILD SUCCESSFUL`. `cleanTest` forces the tests to run again instead of being skipped as `UP-TO-DATE`.

2. Run one test by name:

```
./gradlew cleanTest test --tests '*QuantityTest.refusesToAddBitsPerSecondToBytesPerSecond'
```

   Expected: one line `QuantityTest > refusesToAddBitsPerSecondToBytesPerSecond() PASSED`.

3. See how many tests each class ran:

```
grep -o 'tests="[0-9]*"' build/test-results/test/TEST-io.plainround.seatwave.estimates.units.*.xml
```

   Expected: one line per class, ending in `tests="23"` for `QuantityTest` and `tests="4"` for `UnitTest`. The order of the two lines can vary.

4. Break a rule on purpose in a scratch copy and watch the tests catch it. In a copy of the repository (for example under `/tmp`), change `"125000000"` in the `GIGABITS_PER_SECOND` line of `Unit.java` to `"12500000"`, then run:

```
./gradlew test --tests '*units*'
```

   Expected: `UnitTest > keepsEightBitsInEveryByteOfAGigabit() FAILED`, `QuantityTest > convertsOneGigabitPerSecondIntoBytesPerSecond() FAILED` and `QuantityTest > convertsBytesPerSecondIntoGigabitsByMultiplyingByEight() FAILED`, then `27 tests completed, 3 failed` and `BUILD FAILED`. Never change the real files for this.

5. See every unit and its symbol, which is what `givesEveryUnitADistinctSymbol` checks:

```
grep -c '^  [A-Z_]*("' src/main/java/io/plainround/seatwave/estimates/units/Unit.java
```

   Expected: `33`.

## Mistakes people make

- **Comparing `BigDecimal` with `isEqualTo`.** `new BigDecimal("1.0")` and `new BigDecimal("1")` are not equal by `equals`, because their scales differ. These tests use `isEqualByComparingTo` for amounts.
- **Testing the printed text instead of the value, or the reverse.** The conversion tests check the `amount()`; only the printing tests check `toString()`. One concern per test.
- **Leaving out a seed.** Random data without a seed cannot be replayed when a test fails. The generated test fixes it.
- **A test that checks only the exception type.** The two refusal tests that check the message also prove that the message is useful.
- **Adding a new unit and forgetting its symbol.** `givesEveryUnitADistinctSymbol` fails if the symbol repeats one.

## Check yourself

1. *Why does `refusesToAddGigabytesToBytesWithoutAConversion` exist when the two units measure the same dimension?* The rule of the package is that mixing any two units is refused, so every conversion is written out with `to(...)`.
2. *What does `isEqualByComparingTo("125000000")` do that `isEqualTo` does not?* It compares `BigDecimal` amounts by their size, ignoring the scale (the number of decimals).
3. *Why does `everyPrintedQuantityEndsWithItsUnitSymbol` use a seed?* So every run draws the same 200 amounts and a failure can be replayed.
4. *What does the unit `times` mean?* The answer of `timesAsMany`: how many times one amount of a unit is larger than another amount of the same unit.
5. *Which test would catch someone adding `KB` as a unit?* `findsNoUnitForAnAmbiguousSymbol`.

## Further reading

- JUnit 6.1.3 user guide: <https://docs.junit.org/6.1.3/overview.html>
- AssertJ 3.27.7 documentation: <https://assertj.github.io/doc/>
- Instancio user guide: <https://www.instancio.org/user-guide/>
- Java `BigDecimal`: <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html>
