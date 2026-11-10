# Java walkthrough: the `units` package

## What it is

This file reads, one source file at a time, the Java package `io.plainround.seatwave.estimates.units`. A **package** is a named folder of related Java files. This one holds the three types that let the program say "38,212 bytes" instead of "38212". The package answers one question: how can a program make it impossible to add bytes to bits, or to print a number without its unit?

Read `java.md` first. It explains the Java words used here (class, record, enum, constructor, exception, `BigDecimal`, `Optional`, `String.format`) from zero. This file does not repeat those explanations in full. It quotes each line of the source and says what that line does in this program.

## Why this repository uses it

Module 3 teaches the habit of writing the unit next to every number. The most common mistake in an estimate is a unit slip: a response size in bits where bytes are expected, a size in KB where bytes are expected, a rate per day where a rate per second is expected. A person catches such a slip by reading. A program can catch it by construction, if a number is never allowed to exist without its unit. The `units` package is that rule written as code.

## Where it appears

| File | What it does |
|---|---|
| `Dimension.java` | A list of what can be measured: data, time, money and request rate. Two units can be converted only when they have the same dimension. |
| `Unit.java` | A list of every unit the program knows, each with its printed symbol, its dimension and its size in the base unit of that dimension. |
| `Quantity.java` | A number together with a unit. It converts, adds, scales, compares and prints. Every other package holds and prints numbers only as `Quantity` values. |

The `units` package imports only the JDK. The `estimate` and `probe` packages import `units`. Nothing in `units` imports them. `ARCHITECTURE.md` calls this the dependency rule.

## How it works underneath

The whole package runs in memory inside one process. A **process** is one running copy of a program, with its own memory. There is no file, no network and no thread here.

Three ideas carry the package.

1. **A dimension says what is measured.** Bytes, gigabytes and terabytes all measure an amount of data, so they share the dimension `INFORMATION`. Requests per second and requests per day both measure how fast requests come, so they share `REQUEST_RATE`. Bytes and requests per second share nothing, so converting one into the other is a bug.
2. **Each unit knows its size in a base unit.** The base unit of `INFORMATION` is the byte. A gigabyte is 1,000,000,000 bytes. To convert, the program multiplies by the size of the unit you have and divides by the size of the unit you want. Gigabytes to terabytes is `x 1,000,000,000 / 1,000,000,000,000`.
3. **A quantity is a pair.** `Quantity` holds an `amount` (a `BigDecimal`, an exact decimal number) and a `unit`. Its methods check the units before they calculate.

**Powers of ten and powers of two.** Kilo, mega, giga and tera in this program are **decimal**: they are powers of ten. 1 GB is 10^9 = 1,000,000,000 bytes, and 1 TB is 10^12 bytes. Disk makers and network engineers use these. A **binary** unit is a power of two. A GiB (gibibyte) is 2^30 = 1,073,741,824 bytes. Memory is sold in these, because a memory chip is addressed with binary numbers. The two differ by about 7 percent at the giga level: 1 GiB is 1.0737 GB. Mixing them silently would make a memory estimate wrong by 7 percent, so the program keeps both as separate units with separate symbols, `GB` and `GiB`. The one binary unit in the program is `GiB`. The symbol itself says which kind it is.

## Walkthrough of this repository's use

### `Dimension.java`

An **enum** is a type with a fixed list of values. Each value is a constant object. `Dimension` is an enum with no fields: the values are just names.

**Lines 1 to 7: package, comment and header**

```java
package io.plainround.seatwave.estimates.units;

/**
 * What a unit measures. Two units can be converted into each other only when they share a
 * dimension, so a byte count can become terabytes but never requests per second.
 */
public enum Dimension {
```

- `package io.plainround.seatwave.estimates.units;` says which package the file belongs to. The folder path `src/main/java/io/plainround/seatwave/estimates/units/` must match the package name.
- Lines 3 to 6 are a **Javadoc** comment. It starts with `/**` and ends with `*/`. A tool turns it into documentation, and people read it in the source. This one states the rule in words: two units can be converted only when they share a dimension.
- `public enum Dimension {` declares a public enum. `public` means code in any package may use it.

**Lines 8 to 45: the values**

```java
  /** People who use the system. */
  USERS,
  /** Requests one user makes in one day. */
  REQUESTS_PER_USER,
  /** Requests per unit of time. */
  REQUEST_RATE,
  /** Plain numbers: ratios, percents and change factors. */
  DIMENSIONLESS,
  /** An amount of stored or sent data. */
  INFORMATION,
  /** Data added per day. */
  INFORMATION_PER_DAY,
  /** Data sent per second. */
  INFORMATION_RATE,
  /** Data sent per month. */
  INFORMATION_PER_MONTH,
  /** Records or files written per day. */
  ITEMS_PER_DAY,
  /** A span of calendar time. */
  TIME_SPAN,
  /** How many copies of each byte are kept. */
  COPIES,
  /** Entries in a cache. */
  KEYS,
  /** Machines. */
  SERVERS,
  /** Money per hour of running time. */
  MONEY_PER_HOUR,
  /** Money per month. */
  MONEY_PER_MONTH,
  /** Money per gigabyte sent. */
  PRICE_PER_GIGABYTE,
  /** Money per gigabyte stored for a month. */
  PRICE_PER_GIGABYTE_MONTH,
  /** A measured duration of one operation. */
  DURATION,
  /** Measurements taken. */
  SAMPLES
}
```

- Each value is written as a NAME in capital letters. A comment above each one starts with `/**` and says what it measures. The values are separated by commas. The last one, `SAMPLES`, has no comma.
- `USERS`, `REQUESTS_PER_USER`, `REQUEST_RATE`: people, requests one person makes in a day, and requests per unit of time.
- `DIMENSIONLESS` covers plain numbers: ratios, percents and "how many times" factors. They have no physical measure, only a size.
- `INFORMATION`, `INFORMATION_PER_DAY`, `INFORMATION_RATE`, `INFORMATION_PER_MONTH` are four different dimensions, not one. Data stored (bytes), data added per day, data sent per second and data sent per month are different things. Keeping them apart means the program refuses to convert "bytes per day" into "bytes per second" on its own. A person must say how: the estimator divides by 86,400 in a visible step.
- `ITEMS_PER_DAY`, `TIME_SPAN`, `COPIES`, `KEYS`, `SERVERS` are the counted things of the estimate: records written per day, days or years, copies of each byte, cache entries and machines.
- `MONEY_PER_HOUR`, `MONEY_PER_MONTH`, `PRICE_PER_GIGABYTE`, `PRICE_PER_GIGABYTE_MONTH` are the money dimensions. A price per gigabyte and an amount of money per month are different dimensions, so the program cannot add them.
- `DURATION` and `SAMPLES` are for the latency probe: the time one operation took, and how many measurements were taken.
- There are 19 values. I counted them by running `Dimension.values().length`, which printed `19`. `values()` is a method Java adds to every enum. It returns an array of all the values.

### `Unit.java`

**Lines 1 to 15: imports, comment and header**

```java
package io.plainround.seatwave.estimates.units;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * Every unit a number in this program may carry, with the dimension it measures and its size in the
 * base unit of that dimension.
 *
 * <p>The symbol is what a sheet must write in its unit column and what every printed number ends
 * with. Kilo, mega, giga and tera are decimal (1 GB is 10^9 bytes); the one binary unit, GiB, says
 * so in its symbol. The enum is immutable and safe to share between threads.
 */
public enum Unit {
```

- An **import** lets the file write a short name for a class from another package. `BigDecimal` is a number type that stores decimal digits exactly. `Arrays` is a helper class for arrays. `Optional` is a box that holds one value or nothing.
- The Javadoc says three things. The symbol is what a sheet must write in its unit column, and what every printed number ends with. Kilo, mega, giga and tera are decimal. The one binary unit is `GiB`, and its symbol says so. The last sentence, "immutable and safe to share between threads", means that no code can change a `Unit` after it exists, so two threads can use the same one without a clash. A **thread** is one line of execution inside a process.
- `public enum Unit {` starts the enum. This enum has fields, so each value is written with arguments in brackets.

**Lines 16 to 31: the unit values for people, requests, ratios**

```java
  /** People. */
  USERS("users", Dimension.USERS, "1"),
  /** Requests one user makes in one day. */
  REQUESTS_PER_USER_PER_DAY("requests/user/day", Dimension.REQUESTS_PER_USER, "1"),
  /** The base unit of request rates. */
  REQUESTS_PER_DAY("requests/day", Dimension.REQUEST_RATE, "1"),
  /** One request per second is 86,400 requests per day: 24 h x 60 min x 60 s. */
  REQUESTS_PER_SECOND("requests/s", Dimension.REQUEST_RATE, "86400"),
  /** A plain ratio, such as a peak factor or an overhead multiplier. */
  RATIO("ratio", Dimension.DIMENSIONLESS, "1"),
  /** One percent is one hundredth. */
  PERCENT("percent", Dimension.DIMENSIONLESS, "0.01"),
  /** How many times larger one result became than another. */
  TIMES("times", Dimension.DIMENSIONLESS, "1"),
  /** Read requests for each write request. */
  READS_PER_WRITE("reads/write", Dimension.DIMENSIONLESS, "1"),
```

- Each line has the form `NAME("symbol", Dimension.X, "size")`. The three arguments are passed to the constructor you meet at line 87. The first is the printed symbol. The second is the dimension. The third is the size of this unit in the base unit of its dimension, written as text.
- `USERS("users", Dimension.USERS, "1")` is people. The size is 1 because it is its own base unit.
- `REQUESTS_PER_USER_PER_DAY("requests/user/day", ...)` is the number of requests one user makes in a day.
- `REQUESTS_PER_DAY` is the base unit of the `REQUEST_RATE` dimension, so its size is 1. `REQUESTS_PER_SECOND` has size 86400. One request per second is 86,400 requests per day, because a day is 24 hours x 60 minutes x 60 seconds = 86,400 seconds. So 1 request/s equals 86,400 base units (requests/day). Converting 400,000 requests/day to requests per second multiplies by 1 and divides by 86,400: 4.63 requests/s.
- `RATIO("ratio", Dimension.DIMENSIONLESS, "1")` is a plain multiplier such as a peak factor of 26.
- `PERCENT` has size `"0.01"`. One percent is one hundredth of a ratio. Converting 15 percent to a ratio multiplies by 0.01 and divides by 1: 0.15.
- `TIMES` ("times") and `READS_PER_WRITE` ("reads/write") also have size 1. They share the `DIMENSIONLESS` dimension, so a ratio can be converted into times, but each keeps its own printed symbol.
- The size is a string, not a number, for a reason line 90 explains.

**Lines 32 to 53: data units**

```java
  /** The base unit of information in this program. */
  BYTES("bytes", Dimension.INFORMATION, "1"),
  /** Decimal gigabyte: 10^9 bytes. */
  GIGABYTES("GB", Dimension.INFORMATION, "1000000000"),
  /** Decimal terabyte: 10^12 bytes. */
  TERABYTES("TB", Dimension.INFORMATION, "1000000000000"),
  /** Binary gibibyte: 2^30 bytes, the unit memory is sold in. */
  GIBIBYTES("GiB", Dimension.INFORMATION, "1073741824"),
  /** The base unit of data added per day. */
  BYTES_PER_DAY("bytes/day", Dimension.INFORMATION_PER_DAY, "1"),
  /** Decimal gigabytes added per day. */
  GIGABYTES_PER_DAY("GB/day", Dimension.INFORMATION_PER_DAY, "1000000000"),
  /** The base unit of data rates. */
  BYTES_PER_SECOND("bytes/s", Dimension.INFORMATION_RATE, "1"),
  /** One gigabit per second is 10^9 bits, which is 125,000,000 bytes, every second. */
  GIGABITS_PER_SECOND("Gbps", Dimension.INFORMATION_RATE, "125000000"),
  /** The base unit of data sent per month. */
  BYTES_PER_MONTH("bytes/month", Dimension.INFORMATION_PER_MONTH, "1"),
  /** Decimal gigabytes per month, the unit egress is priced in. */
  GIGABYTES_PER_MONTH("GB/month", Dimension.INFORMATION_PER_MONTH, "1000000000"),
  /** Decimal terabytes per month. */
  TERABYTES_PER_MONTH("TB/month", Dimension.INFORMATION_PER_MONTH, "1000000000000"),
```

- `BYTES("bytes", Dimension.INFORMATION, "1")` is the base unit of data.
- `GIGABYTES("GB", ..., "1000000000")` is 10^9 bytes. `TERABYTES("TB", ..., "1000000000000")` is 10^12 bytes. These are decimal units, powers of ten.
- `GIBIBYTES("GiB", ..., "1073741824")` is 2^30 bytes, a power of two. 1,073,741,824 = 1024 x 1024 x 1024. The comment says it is the unit memory is sold in.
- `BYTES_PER_DAY` and `GIGABYTES_PER_DAY` are the amounts of data added per day. They share the `INFORMATION_PER_DAY` dimension.
- `BYTES_PER_SECOND("bytes/s", ...)` is the base unit of data rates. `GIGABITS_PER_SECOND("Gbps", ..., "125000000")` is a network speed. One gigabit is 10^9 **bits**. A byte has 8 bits, so 10^9 bits is 10^9 / 8 = 125,000,000 bytes. So 1 Gbps is 125,000,000 bytes/s. I ran `Quantity.of("1", Unit.GIGABITS_PER_SECOND).to(Unit.BYTES_PER_SECOND)` and got `125,000,000 bytes/s`. This one number is the whole "bytes to bits" step of the estimate.
- `BYTES_PER_MONTH`, `GIGABYTES_PER_MONTH` and `TERABYTES_PER_MONTH` are the amounts of data sent per month, with sizes 1, 10^9 and 10^12.

**Lines 54 to 81: the remaining units**

```java
  /** Records or files written per day. */
  ITEMS_PER_DAY("items/day", Dimension.ITEMS_PER_DAY, "1"),
  /** The base unit of calendar time. */
  DAYS("days", Dimension.TIME_SPAN, "1"),
  /** A 365-day year, as in Module 2. */
  YEARS("years", Dimension.TIME_SPAN, "365"),
  /** Copies of each stored byte. */
  COPIES("copies", Dimension.COPIES, "1"),
  /** Cache entries. */
  KEYS("keys", Dimension.KEYS, "1"),
  /** Machines. */
  SERVERS("servers", Dimension.SERVERS, "1"),
  /** US dollars per hour. */
  USD_PER_HOUR("USD/hour", Dimension.MONEY_PER_HOUR, "1"),
  /** US dollars per month. */
  USD_PER_MONTH("USD/month", Dimension.MONEY_PER_MONTH, "1"),
  /** US dollars per decimal gigabyte sent. */
  USD_PER_GB("USD/GB", Dimension.PRICE_PER_GIGABYTE, "1"),
  /** US dollars per decimal gigabyte kept for one month. */
  USD_PER_GB_MONTH("USD/GB-month", Dimension.PRICE_PER_GIGABYTE_MONTH, "1"),
  /** The base unit of measured durations. */
  NANOSECONDS("ns", Dimension.DURATION, "1"),
  /** 1,000 nanoseconds. */
  MICROSECONDS("µs", Dimension.DURATION, "1000"),
  /** 1,000,000 nanoseconds. */
  MILLISECONDS("ms", Dimension.DURATION, "1000000"),
  /** Measurements. */
  SAMPLES("samples", Dimension.SAMPLES, "1");
```

- `ITEMS_PER_DAY` is records or files written per day.
- `DAYS("days", Dimension.TIME_SPAN, "1")` is the base unit of calendar time. `YEARS("years", ..., "365")` is 365 days. The comment says "as in Module 2". A leap year is ignored on purpose, so every sheet means the same thing by a year. I ran `Quantity.of("5", Unit.YEARS).to(Unit.DAYS)` and got `1,825 days`.
- `COPIES`, `KEYS` and `SERVERS` count copies, cache entries and machines.
- `USD_PER_HOUR`, `USD_PER_MONTH`, `USD_PER_GB` and `USD_PER_GB_MONTH` are the money units. USD is US dollars. Each has size 1 in its own dimension, so no conversion between them exists.
- `NANOSECONDS("ns", Dimension.DURATION, "1")`, `MICROSECONDS("µs", ..., "1000")` and `MILLISECONDS("ms", ..., "1000000")` are the time units of the probe. A nanosecond is one billionth of a second. A microsecond is 1,000 ns. A millisecond is 1,000,000 ns. The symbol `µs` holds the Greek letter mu, which Java stores in UTF-8 source without trouble.
- `SAMPLES("samples", Dimension.SAMPLES, "1");` is the last value. The **semicolon** after the last value ends the list. What follows is the body of the enum.
- There are 33 values. I ran `Unit.values().length` and got `33`.

**Lines 83 to 92: fields and constructor**

```java
  private final String symbol;
  private final Dimension dimension;
  private final BigDecimal sizeInBaseUnits;

  Unit(String symbol, Dimension dimension, String sizeInBaseUnits) {
    this.symbol = symbol;
    this.dimension = dimension;
    // Written as text so the size is an exact decimal, not the nearest double.
    this.sizeInBaseUnits = new BigDecimal(sizeInBaseUnits);
  }
```

- Lines 83 to 85 declare three **fields**: data that each enum value holds. `private` means only code inside `Unit` can touch them. `final` means each is set once, in the constructor, and never changed.
- Line 87 is the **constructor**. A constructor is special code that runs when an object is created. It has the name of the class and no return type. An enum constructor is always private: nobody outside may create a new unit. Java calls it once for each of the 33 lines above.
- Lines 88 and 89 copy the symbol and the dimension into the fields. `this.symbol` means "the field of this object", to tell it from the parameter with the same name.
- Line 90 is a comment. Line 91 turns the text into a `BigDecimal`. `new BigDecimal("0.01")` is exactly one hundredth. If the code had passed the number 0.01 as a `double`, the stored value would be the nearest binary fraction, which is not exactly 0.01. Written as text, the size is exact, so every conversion is exact.

**Lines 94 to 129: lookup and accessors**

```java
  /**
   * Finds the unit a sheet names.
   *
   * @param symbol the text of a unit column, for example "bytes"
   * @return the unit with exactly that symbol, or empty when no unit has it
   */
  public static Optional<Unit> withSymbol(String symbol) {
    return Arrays.stream(values()).filter(unit -> unit.symbol.equals(symbol)).findFirst();
  }
```

- `withSymbol` is a **static** method: it belongs to the class, not to one value, so you call it as `Unit.withSymbol("GB")`. It takes the text of a unit column and returns an `Optional<Unit>`. An `Optional` is a box that holds one value or is empty. It makes "no unit has this symbol" part of the type, so a caller must decide what to do.
- `Arrays.stream(values())` turns the array of all 33 units into a **stream**: a pipeline of items. `.filter(unit -> unit.symbol.equals(symbol))` keeps the units whose symbol equals the given text. The part before `->` is a **lambda**: a small unnamed function that takes `unit` and gives a true or false answer. `.findFirst()` ends the pipeline and returns the first item that is left, or an empty `Optional`.
- `equals` compares text exactly, so `"KB"` finds nothing and `"GB"` finds `GIGABYTES`. I ran both: `Unit.withSymbol("KB")` printed `Optional.empty` and `Unit.withSymbol("GB")` printed `Optional[GIGABYTES]`. That is how the estimator refuses KB: no unit has that symbol.

```java
  /**
   * Returns the text that follows every number in this unit.
   *
   * @return the symbol, for example "requests/s"
   */
  public String symbol() {
    return symbol;
  }

  /**
   * Returns what this unit measures.
   *
   * @return the dimension
   */
  public Dimension dimension() {
    return dimension;
  }

  /**
   * Returns how many base units of the dimension one of this unit holds.
   *
   * @return for example 1000000000 for GB, whose base unit is the byte
   */
  public BigDecimal sizeInBaseUnits() {
    return sizeInBaseUnits;
  }
```

- `symbol()`, `dimension()` and `sizeInBaseUnits()` are plain **accessors**: methods that return a field. They are not `static`, so you call them on a value: `Unit.GIGABYTES.symbol()` gives `GB`. An enum field is private, so an accessor is the only way to read it.
- The `@return` lines in the Javadoc give an example of each result.

### `Quantity.java`

**Lines 1 to 20: imports, comment and header**

```java
package io.plainround.seatwave.estimates.units;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * A number together with its unit. It is the only way this program holds or prints a number.
 *
 * <p>Two rules make unit slips impossible to print. First, {@link #plus} and {@link #timesAsMany}
 * refuse two different units, even two of one dimension such as bytes and GB, so every conversion
 * is written out with {@link #to}. Second, {@link #toString} always ends with the unit symbol.
 * Instances are immutable and safe to share between threads.
 *
 * @param amount the number, never negative
 * @param unit the unit the number is counted in
 */
public record Quantity(BigDecimal amount, Unit unit) {
```

- `MathContext` bundles a precision (the number of significant digits) and a rounding rule for `BigDecimal` arithmetic. `RoundingMode` lists the rounding rules. `Locale` names a country's formatting habits. `Objects` is a helper with a null check.
- The Javadoc explains the two rules that make a unit slip impossible to print. First, `plus` and `timesAsMany` refuse two different units, even two of one dimension such as bytes and GB, so every conversion is written out with `to`. Second, `toString` always ends with the unit symbol. `{@link #plus}` makes a clickable link to the method in the generated documentation.
- `@param amount` and `@param unit` document the two components of the record. For a record, `@param` documents the components.
- `public record Quantity(BigDecimal amount, Unit unit) {` declares a **record**: a short way to write a class that only carries values. Java writes for you the private final fields `amount` and `unit`, the constructor, the accessors `amount()` and `unit()`, and `equals`, `hashCode` and `toString`. This record writes its own `toString` at line 120, which replaces the generated one.

**Lines 22 to 31: constants**

```java
  /** Sixteen significant digits: far more than any estimate input has, and exact for money. */
  public static final MathContext PRECISION = MathContext.DECIMAL64;

  // Numbers at or above this are printed without decimals: 30,093 requests/s, not 30,092.59.
  private static final BigDecimal WHOLE_NUMBER_FROM = new BigDecimal("100");
  // Numbers between 1 and 100 keep two decimals: 9.2 Gbps, 1.5 ratio.
  private static final int DECIMALS_BELOW_WHOLE = 2;
  private static final int CENTS = 2;
  // Numbers below 1 keep four significant digits, so a price of 0.2016 USD/hour prints in full.
  private static final MathContext SMALL_NUMBER_DIGITS = new MathContext(4, RoundingMode.HALF_UP);
```

- `public static final MathContext PRECISION = MathContext.DECIMAL64;` is a constant. `static` means it belongs to the class. `final` means it is set once. `MathContext.DECIMAL64` is a ready-made setting from the JDK: 16 significant digits, rounding half to even. The Javadoc says it is far more than any estimate input has, and exact for money. A **significant digit** is a digit that carries meaning, counted from the first non-zero digit: 0.00123 has three.
- Every multiplication, division and addition in this package passes `PRECISION`. Without it, `BigDecimal.divide` throws `ArithmeticException` when the result has an endless decimal expansion, as 1 divided by 3 does. With it, the result is rounded to 16 digits.
- `WHOLE_NUMBER_FROM` is the number 100 as an exact `BigDecimal`. From this value up, a quantity prints without decimals: `30,093 requests/s`, not `30,092.59 requests/s`.
- `DECIMALS_BELOW_WHOLE = 2` is the number of decimals for a number from 1 up to 100: `9.2 Gbps`, `1.5 ratio`. The comment above the line says so. `CENTS = 2` is the number of decimals of money.
- `SMALL_NUMBER_DIGITS` is a `MathContext` of 4 significant digits with the rounding mode `HALF_UP` (school rounding: 0.5 goes up). It is used for numbers below 1, so a price of 0.2016 USD/hour prints in full, as `0.2016 USD/hour`.
- The values are named constants, not bare numbers in the code. A bare number such as `100` in the middle of a line is a **magic number**: a reader cannot tell what it means. A name explains it.

**Lines 33 to 45: the compact constructor**

```java
  /**
   * Creates a quantity.
   *
   * @throws NullPointerException if the amount or the unit is null
   * @throws IllegalArgumentException if the amount is negative
   */
  public Quantity {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(unit, "unit");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("an estimate is never negative, but was " + amount);
    }
  }
```

- A **compact constructor** is the record form of a constructor. It has no parameter list. The components `amount` and `unit` are available as variables. Java copies them into the fields when the constructor ends. This one only checks them.
- `Objects.requireNonNull(amount, "amount")` returns the value when it is not null and throws `NullPointerException` with the message `amount` when it is. A null stops here, at the door, with the name of the bad argument, instead of failing deep inside later code.
- `amount.signum()` returns -1, 0 or 1 for a negative, zero or positive number. `< 0` means "is negative". The program throws `IllegalArgumentException`, the standard exception for "you gave me a value I cannot accept". An **exception** is an object that says "something went wrong"; `throw` stops the method and the exception travels up through the callers until a `catch` handles it. Nothing here catches it, because a negative quantity is a bug in a formula, not an input mistake. A person who types a negative number in a sheet is stopped earlier, at the range check, with a clear message.
- So no `Quantity` in the program is ever negative, null or without a unit. I ran `Quantity.of("-1", Unit.BYTES)` and got `an estimate is never negative, but was -1`.

**Lines 47 to 56: `of`**

```java
  /**
   * Creates a quantity from decimal text.
   *
   * @param amount the number as text, for example "37000"
   * @param unit the unit
   * @return the quantity
   */
  public static Quantity of(String amount, Unit unit) {
    return new Quantity(new BigDecimal(amount), unit);
  }
```

- `of` is a static factory: a method that makes an object without writing `new` at the call site. It takes the number as text and builds the `BigDecimal` from it, so `"0.2016"` is exact. The probe and the tests use it.

**Lines 58 to 73: `to`, the unit conversion**

```java
  /**
   * Converts this quantity into another unit of the same dimension.
   *
   * @param target the unit wanted, for example {@link Unit#GIGABITS_PER_SECOND}
   * @return the same amount of the same thing, counted in the target unit
   * @throws IllegalArgumentException if the target measures something else
   */
  public Quantity to(Unit target) {
    Objects.requireNonNull(target, "target");
    if (target.dimension() != unit.dimension()) {
      throw new IllegalArgumentException(
          "cannot convert " + unit.symbol() + " into " + target.symbol());
    }
    BigDecimal inBaseUnits = amount.multiply(unit.sizeInBaseUnits(), PRECISION);
    return new Quantity(inBaseUnits.divide(target.sizeInBaseUnits(), PRECISION), target);
  }
```

- `to(Unit target)` returns a new `Quantity` in another unit. It does not change `this`: records are immutable.
- Line 67 compares the two dimensions with `!=`. For enum values, `!=` asks "is this a different constant?" and is correct, because each enum value exists once. If the dimensions differ, the method throws. I ran `Quantity.of("1", Unit.BYTES).to(Unit.USERS)` and got `cannot convert bytes into users`.
- Line 71 multiplies the amount by the size of the unit it is in. That gives the amount counted in base units. `amount.multiply(x, PRECISION)` is a multiplication rounded to 16 digits.
- Line 72 divides by the size of the target unit and wraps the result in a new `Quantity` in the target unit.
- Example: 38,212 bytes to GB. Bytes have size 1, so 38,212 x 1 = 38,212 base units. GB has size 1,000,000,000, so 38,212 / 1,000,000,000 = 0.000038212 GB. The program printed `0.00003821 GB` (four significant digits, because the number is below 1).
- Example with the two kinds of gigabyte: `Quantity.of("1", Unit.GIGABYTES).to(Unit.GIBIBYTES)` gave `0.9313 GiB`, and `Quantity.of("1", Unit.GIBIBYTES).to(Unit.GIGABYTES)` gave `1.07 GB`. A GiB is a larger unit than a GB, so a given amount of data is fewer GiB than GB.
- `requests/s` to `requests/day`: 1 x 86,400 / 1 = 86,400. The program printed `86,400 requests/day`.

**Lines 75 to 85: `plus`, and why two units are refused**

```java
  /**
   * Adds a quantity in exactly the same unit.
   *
   * @param other the quantity to add
   * @return the sum, in this unit
   * @throws IllegalArgumentException if the units differ, for example bytes and bits per second
   */
  public Quantity plus(Quantity other) {
    requireSameUnit(other, "add");
    return new Quantity(amount.add(other.amount, PRECISION), unit);
  }
```

- `plus` adds two quantities. It first calls `requireSameUnit`, a helper at line 147, which throws if the two units are not the same unit.
- Why refuse? Because a number without a matching unit is a wrong answer. 1 GB plus 1 byte is not "2". Adding the two amounts would give 2 of something. The program stops, and shows which units clashed. Even two units of one dimension, such as bytes and GB, are refused. The program could convert one into the other, but that would hide a slip: if a formula adds bytes to GB, it is almost certainly wrong. Forcing the author to write `.to(...)` makes each conversion visible in the code. I ran `Quantity.of("1", Unit.GIGABYTES).plus(Quantity.of("1", Unit.BYTES))` and got `cannot add bytes and GB`.
- `amount.add(other.amount, PRECISION)` adds the two decimal numbers. The result is a new `Quantity` in this unit.

**Lines 87 to 96: `scaledBy`**

```java
  /**
   * Multiplies by a plain number and keeps the unit.
   *
   * @param factor the multiplier, for example a peak factor of 26
   * @return the scaled quantity, in this unit
   */
  public Quantity scaledBy(BigDecimal factor) {
    Objects.requireNonNull(factor, "factor");
    return new Quantity(amount.multiply(factor, PRECISION), unit);
  }
```

- `scaledBy` multiplies by a plain number and keeps the unit. A peak factor of 26 applied to an average rate of 1,157 requests/s gives 30,093 requests/s. The factor is a `BigDecimal`, not a `Quantity`, because a factor has no unit to check.

**Lines 98 to 111: `timesAsMany`**

```java
  /**
   * Tells how many times larger this quantity is than another in exactly the same unit.
   *
   * @param other the quantity to compare with; it must not be zero
   * @return this amount divided by the other amount, in {@link Unit#TIMES}
   * @throws IllegalArgumentException if the units differ or the other amount is zero
   */
  public Quantity timesAsMany(Quantity other) {
    requireSameUnit(other, "compare");
    if (other.amount.signum() == 0) {
      throw new IllegalArgumentException("cannot compare with zero " + unit.symbol());
    }
    return new Quantity(amount.divide(other.amount, PRECISION), Unit.TIMES);
  }
```

- `timesAsMany` tells how many times larger this quantity is than another in the same unit. It is a division of one amount by the other, and the answer is a `Quantity` in the unit `TIMES`. It refuses two different units for the same reason `plus` does: 1,000 bytes divided by 8 GB is a meaningless number.
- Lines 107 to 109 refuse a zero divisor, with a clear message, instead of the bare `ArithmeticException` that `BigDecimal` would throw.
- The sensitivity check and the probe report use it. For example, `Quantity.of("1000", Unit.BYTES).timesAsMany(Quantity.of("8", Unit.BYTES))` gave `125 times`.

**Lines 113 to 124: `toString`, which always ends with the unit**

```java
  /**
   * Formats the quantity for a person: grouped digits, a sensible number of decimals, then the
   * unit.
   *
   * @return for example "30,093 requests/s", "9.2 Gbps" or "147.17 USD/month"
   */
  @Override
  public String toString() {
    BigDecimal rounded = rounded();
    int decimals = isWholeMoney() ? CENTS : Math.max(0, rounded.stripTrailingZeros().scale());
    return String.format(Locale.ROOT, "%,." + decimals + "f", rounded) + " " + unit.symbol();
  }
```

- `@Override` says "this replaces a method of the parent class". Every Java class inherits `toString` from the root class `Object`. A record would generate one. This one is written by hand so the printout is meant for a person.
- Line 121 gets the rounded amount. Line 122 decides how many decimals to print: two for money of a dollar or more, otherwise as many as the rounded number has after its trailing zeros are removed. `stripTrailingZeros()` turns `9.20` into `9.2`. `.scale()` is the number of digits after the decimal point. `Math.max(0, ...)` keeps it from going negative: `stripTrailingZeros` on 100 gives `1E+2`, whose scale is -2.
- Line 123 builds the text. `"%,." + decimals + "f"` makes a template such as `%,.2f`. In a template, `%f` prints a decimal number, `,` adds thousands separators, and `.2` keeps two decimals. `Locale.ROOT` fixes the separators to the English style on every computer, so `30093` prints as `30,093` even on a German machine. Then comes a space and `unit.symbol()`. So every `Quantity` prints with its unit, always. That is the second rule of the Javadoc.
- Examples I ran: `Quantity.of("38212", Unit.BYTES)` prints `38,212 bytes`. `Quantity.of("9.2345", Unit.GIGABITS_PER_SECOND)` prints `9.23 Gbps`. `Quantity.of("30092.59", Unit.REQUESTS_PER_SECOND)` prints `30,093 requests/s`. `Quantity.of("0.00003333", Unit.GIGABITS_PER_SECOND)` prints `0.00003333 Gbps`.

**Lines 126 to 132: `isWholeMoney`**

```java
  // Money of a dollar or more keeps its cents, so the Module 1 price reads 147.17, not 147.
  private boolean isWholeMoney() {
    boolean isMoney =
        unit.dimension() == Dimension.MONEY_PER_HOUR
            || unit.dimension() == Dimension.MONEY_PER_MONTH;
    return isMoney && amount.compareTo(BigDecimal.ONE) >= 0;
  }
```

- A `private` method is a helper that only this class may call. The name is a little misleading: it answers "is this money of a dollar or more, which keeps its cents?". `isMoney` is true when the dimension is money per hour or money per month. `||` means "or". `amount.compareTo(BigDecimal.ONE) >= 0` asks "is the amount at least 1?". `compareTo` returns a negative number, zero or a positive number. It is used instead of `equals` because `equals` on `BigDecimal` also compares the scale, so `1.0` and `1` are not equal.
- The result: 147.17 USD/month keeps its cents, and 0.2016 USD/hour (below 1) does not take this branch.

**Lines 134 to 145: `rounded`**

```java
  private BigDecimal rounded() {
    if (isWholeMoney()) {
      return amount.setScale(CENTS, RoundingMode.HALF_UP);
    }
    if (amount.compareTo(WHOLE_NUMBER_FROM) >= 0) {
      return amount.setScale(0, RoundingMode.HALF_UP);
    }
    if (amount.compareTo(BigDecimal.ONE) >= 0) {
      return amount.setScale(DECIMALS_BELOW_WHOLE, RoundingMode.HALF_UP);
    }
    return amount.round(SMALL_NUMBER_DIGITS);
  }
```

- This method picks the rounding by the size of the number, in four steps.
- Money of a dollar or more: `setScale(2, RoundingMode.HALF_UP)`. `setScale` fixes the number of decimals; `HALF_UP` rounds 0.5 upward. So 147.168 USD/month prints as `147.17 USD/month`.
- A number of 100 or more: no decimals. 30,092.59 becomes 30,093.
- A number from 1 up to 100: two decimals. 9.2345 becomes 9.23.
- A number below 1: `amount.round(SMALL_NUMBER_DIGITS)` keeps four significant digits. 0.00003821 stays as it is, and 0.20160 becomes 0.2016.
- Because of the third case, 99.999 users prints as `100 users`: it rounds to 100.00 and the trailing zeros are stripped. That is a feature of display only. The stored amount is unchanged.

**Lines 147 to 155: `requireSameUnit`**

```java
  private void requireSameUnit(Quantity other, String operation) {
    Objects.requireNonNull(other, "other");
    if (other.unit != unit) {
      // A mix of units is a bug in the formula, so it fails loudly instead of returning a value.
      throw new IllegalArgumentException(
          "cannot " + operation + " " + other.unit.symbol() + " and " + unit.symbol());
    }
  }
}
```

- `other.unit != unit` compares two enum values. If they differ, the method throws an `IllegalArgumentException` whose message names both units.
- The comment explains why this is an exception and not a `Result`: a mix of units is a bug in the formula, not an input mistake that a person typed. So it fails loudly. `result4j.md` explains the line between the two.
- The message names `other` first and `this` second. `Quantity.of("1", Unit.GIGABYTES).plus(Quantity.of("1", Unit.BYTES))` therefore says `cannot add bytes and GB`.
- The last two lines, `}` and `}`, close the method and the record.

## Try it yourself

Run these in `seatwave-estimates/`.

1. See the compiled classes. The first command compiles; the second counts the class files.

```
./gradlew -q compileJava
find build/classes/java/main/io/plainround/seatwave/estimates/units -name '*.class'
```

I got `Dimension.class`, `Quantity.class` and `Unit.class`.

2. Look at what a record became:

```
javap -cp build/classes/java/main io.plainround.seatwave.estimates.units.Quantity
```

I got `public final class ...Quantity extends java.lang.Record`, then the constructor, `of`, `to`, `plus`, `scaledBy`, `timesAsMany`, `toString`, `hashCode`, `equals`, `amount()` and `unit()`. `javap` is the JDK's class-file reader.

3. Try the conversions and the refusal. Save this as `Try.java` in any folder outside the repository, then run it from the repository folder:

```
java -cp build/classes/java/main /path/to/Try.java
```

with a `main` that prints `Quantity.of("38212", Unit.BYTES).to(Unit.GIGABYTES)`, then `Quantity.of("1", Unit.GIGABYTES).plus(Quantity.of("1", Unit.BYTES))` inside a `try` and `catch`. I got `0.00003821 GB` and `java.lang.IllegalArgumentException: cannot add bytes and GB`.

4. See the units refuse a KB in a sheet. The `lab1-unit-slips.csv` sheet writes a size in KB:

```
./gradlew -q estimate --args='sheets/lab1-unit-slips.csv'
```

I got `REJECTED line 12: store.bookings.bytes_per_item must be in bytes, not KB`. No unit has the symbol KB, and the sheet reader compares symbols exactly.

## Mistakes people make

- **Adding two numbers in different units because both are "bytes of something".** `Quantity.plus` refuses it, and so should any code you add.
- **Mixing GB and GiB.** They differ by about 7 percent. This program keeps two symbols so a reader always knows which one a number means.
- **Using a `double` for money or sizes.** A `double` cannot hold 0.1 exactly. `Quantity` holds a `BigDecimal` built from text.
- **Forgetting that `equals` on `BigDecimal` compares the scale.** `1.0` and `1` are not `equals`. The code uses `compareTo`.
- **Dividing with no `MathContext`.** `BigDecimal.divide` throws on an endless expansion such as 1 / 3. Every division here passes `PRECISION`.
- **Letting a printed number lose its unit.** `toString` always appends the symbol, so the report never prints a bare number.

## Check yourself

1. Why does `Quantity.plus` refuse bytes and GB, when it could convert one into the other? Answer: a formula that adds two different units is almost always wrong, and a silent conversion would hide that. Forcing an explicit `.to(...)` shows each conversion in the code.
2. How many bytes are in 1 GB and in 1 GiB? Answer: 1,000,000,000 and 1,073,741,824. The first is 10^9, the second is 2^30.
3. Why is the size of a unit stored as text such as `"0.01"`? Answer: `new BigDecimal("0.01")` is exactly one hundredth. A `double` 0.01 is only the nearest binary fraction.
4. Why is a negative `Quantity` an exception and not a `Result`? Answer: a negative amount means a formula is wrong, which is a programmer error. A person's typo in a sheet is stopped earlier by the range check, which returns problems as values.
5. What does `to` do when the target has another dimension? Answer: it throws `IllegalArgumentException` with `cannot convert bytes into users`, for example.

## Further reading

- `java.math.BigDecimal`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html
- `java.math.MathContext`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/MathContext.html
- Records, https://docs.oracle.com/en/java/javase/25/language/records.html
- Enums, https://docs.oracle.com/en/java/javase/25/language/enums.html
- `java.util.Formatter` (the `%,.2f` template), https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Formatter.html
- Prefixes for binary multiples (kibi, mebi, gibi), from the US standards institute NIST, https://physics.nist.gov/cuu/Units/binary.html