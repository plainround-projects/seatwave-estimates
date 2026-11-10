# Java 25 (the language and the standard library)

## What it is

Java is a programming language. You write **classes** in text files that end in `.java`. A program called the **compiler** (`javac`) turns them into **bytecode**, a compact instruction format stored in `.class` files. A second program, the **Java Virtual Machine** (JVM), reads the bytecode and runs it. The JVM is the same on a Mac, a Windows PC and a Linux server, so the same bytecode runs on all of them.

Java is **statically typed**. Every variable, parameter and return value has a type, such as `int` (a whole number) or `String` (text). The compiler checks the types before the program runs. A mistake such as passing text where a number is expected stops the build instead of crashing later.

The **JDK** (Java Development Kit) is the download that contains the compiler, the JVM, and the **standard library**: ready-made classes such as `String`, `List`, `BigDecimal`, `Duration` and `FileChannel`. This repository uses **JDK 25**. The machine used to write these notes had Eclipse Temurin 25.0.2. JDK 25 is a long-term-support release, which means it receives fixes for years.

## Why this repository uses it

The repository is the hands-on project of Module 3 of a system design course. It has two programs for a ticket-booking case called SeatWave. The first reads an estimation sheet, rejects unit slips, and prints every step of the arithmetic. The second times memory, disk and loopback network on your own machine. Java 25 gives the features that make these small programs safe to read:

- **Records** make number-carrying types short, and their constructors reject bad numbers.
- **Sealed interfaces** list every possible shape of a problem, so the compiler proves that every `switch` handles all of them.
- **Pattern-matching `switch`** reads a value's shape and pulls its parts out in one step.
- **`BigDecimal`** holds decimal numbers exactly, so money and sizes never carry a binary rounding error.
- **`System.nanoTime`, `FileChannel` and sockets** are in the standard library, so the probe needs no extra library.
- The standard library covers everything else, so the project has only one runtime library (`result4j.md`).

## Where it appears

| Path | What it does with Java |
|---|---|
| `src/main/java/io/plainround/seatwave/estimates/units/` | Three files: `Dimension`, `Unit` and `Quantity`. A number with its unit. See `java-units-walkthrough.md`. |
| `src/main/java/io/plainround/seatwave/estimates/estimate/` | Sixteen files: the sheet reader, the input rules, the estimator, the sensitivity check, the report and the command-line program. See `java-estimate-walkthrough.md`. |
| `src/main/java/io/plainround/seatwave/estimates/probe/` | Eight files: the settings, the timing samples, the memory, disk and loopback probes, the report and the command-line program. See `java-probe-walkthrough.md`. |
| `src/test/java/io/plainround/seatwave/estimates/` | The tests, in Java too. See `junit.md`. |
| `build.gradle.kts` | Tells Gradle to compile with Java 25, with every warning treated as an error. See `gradle.md`. |

## How it works underneath

**From text to a running program.** You write `Quantity.java`. The compiler reads it and writes `Quantity.class`. Gradle does this for you: `./gradlew -q compileJava` put 39 class files under `build/classes/java/main/` on my machine (one class file per class, per record, per enum and per nested record). To start the program, a JVM process starts, loads the class you name, and calls its `main` method.

**A process** is one running copy of a program, with its own memory. The JVM is one process. When `main` returns, the process ends with **exit status** 0, which means success. A program may call `System.exit(n)` to end at once with another number. By convention a non-zero status means failure. This repository uses 1, 2 and 3 for three kinds of failure.

**Memory.** Two areas matter. The **stack** holds the method calls in progress, with their local variables. Each call adds a frame and each return removes one. The **heap** holds objects. `new Quantity(...)` makes an object on the heap. A variable holds a **reference**, which is like an address that points to the object. The JVM frees objects that no variable can reach any more. This is called **garbage collection**. You never free memory by hand. The heap has a size limit: the probe prints it as `heap limit 8192 MiB`.

**The JIT compiler.** The JVM first interprets bytecode. It counts how often each method runs, and the **JIT compiler** (just-in-time compiler) turns the busy methods into native machine code while the program runs. A loop is slow in its first runs and fast after. That is why a timing program warms up first (`java-probe-walkthrough.md`).

**A thread** is one line of execution inside a process. A program may have several, and they run at the same time on different processor cores. A **virtual thread** is a lightweight thread that the JVM manages itself.

**The class path** is the list of places where the JVM looks for compiled classes and libraries (jar files, which are zip files of classes). Gradle builds the right class path for each task.

### The basic words

**Class.** A class is a blueprint for objects. It holds fields (data) and methods (code). Everything in Java lives in a class. A record, an enum and an interface are special kinds of class.

**Package.** A package is a named group of classes, written with dots: `io.plainround.seatwave.estimates.units`. The name must match the folder path under `src/main/java`. It keeps names from clashing. The first line of a file is `package ...;`. After it come the imports.

```java
package io.plainround.seatwave.estimates.units;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
```

**Import.** `import java.math.BigDecimal;` lets the file write `BigDecimal` instead of `java.math.BigDecimal`. Classes in the same package need no import. Classes in `java.lang` (such as `String`, `Math`, `System`, `Integer`, `Thread` and `IllegalArgumentException`) are imported for you.

**Method.** A method is a named block of code that may take **parameters** (inputs) and may **return** a value. This one is from `Unit.java`:

```java
  public static Optional<Unit> withSymbol(String symbol) {
    return Arrays.stream(values()).filter(unit -> unit.symbol.equals(symbol)).findFirst();
  }
```

It is a method named `withSymbol`. It takes one `String` and returns an `Optional<Unit>`. `void` means a method returns nothing. `public` means any code can call it.

**`static`.** A static member belongs to the class, not to one object. You call a static method with the class name: `Unit.withSymbol("GB")`. A non-static method needs an object: `Unit.GIGABYTES.symbol()`.

**`final`.** On a variable or field, `final` means "assigned once, never changed". On a class, `final` means "no other class may extend it". A **constant** is a `static final` field:

```java
  /** Sixteen significant digits: far more than any estimate input has, and exact for money. */
  public static final MathContext PRECISION = MathContext.DECIMAL64;
```

`PRECISION` is a constant of the type `MathContext`. By convention constants are written in capital letters with underscores. `public final class CsvSheet` and `public final class Estimator` are classes that no one may extend.

**Access: `public`, `private`, and nothing.** `public` means any code can use it. `private` means only the same class can. No keyword means **package-private**: only classes in the same package can. This repository keeps helpers `private`. It uses package-private for things the same package needs and nobody else does: `enum Range` has no `public`, and `InputRule.range()` has no `public` either.

```java
  Range range() {
    return range;
  }
```

**Constructor.** A constructor is special code that runs when you write `new`. It has the name of the class and no return type. It sets up the object. An enum constructor looks like this:

```java
  Unit(String symbol, Dimension dimension, String sizeInBaseUnits) {
    this.symbol = symbol;
    this.dimension = dimension;
    // Written as text so the size is an exact decimal, not the nearest double.
    this.sizeInBaseUnits = new BigDecimal(sizeInBaseUnits);
  }
```

`this.symbol` means "the field of this object", to tell it from the parameter with the same name. A **private empty constructor** such as `private CsvSheet() {}` means nobody can create an object of the class. A class of only static methods uses this to say "do not instantiate me".

**Exception.** An exception is an object that says "something went wrong". `throw new IllegalArgumentException("...")` stops the current method and travels up the chain of callers until a `catch` block handles it. If none does, the program ends and prints a **stack trace**, the list of calls that led to the failure.

```java
  public Quantity {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(unit, "unit");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("an estimate is never negative, but was " + amount);
    }
  }
```

There are two families. **Unchecked** exceptions (`NullPointerException`, `IllegalArgumentException`, `IllegalStateException`) are for programmer errors, and the compiler does not force you to handle them. **Checked** exceptions (such as `IOException`) are for outside troubles like a file that cannot be read, and the compiler forces you to catch them or declare them with `throws`. This repository uses unchecked exceptions for bugs and the `Result` type for expected failures (`result4j.md`).

**`try`, `catch`, `finally`.** The `try` block holds code that may throw. A `catch` block names an exception type and handles it. A `finally` block runs after the `try`, whether it ended normally or by an exception. This is from `DiskProbe`:

```java
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

**Try-with-resources.** `try (FileChannel channel = FileChannel.open(...)) { ... }` closes the named resource when the block ends, even if it throws. Without it, a failed measurement would leave the file open. It works for any type that implements `AutoCloseable` (since Java 7). A `Socket` and a `FileChannel` are such types. `ExecutorService` became one in Java 19. I confirmed that by compiling a file with `--release 18`, which fails with `try-with-resources not applicable to variable type`, and with `--release 19`, which compiles.

**Null.** `null` means "no object". Calling a method on null throws `NullPointerException`. This repository rejects null at the door with `Objects.requireNonNull`.

**Types.** `int` is a 32-bit whole number, up to 2,147,483,647 (`Integer.MAX_VALUE`). `long` is a 64-bit whole number. `double` is a 64-bit number with a decimal point. `boolean` is `true` or `false`. `char` is one character, written in single quotes (`'.'`). `String` is text, and it is immutable: operations return new strings. `List<String>` is a list of strings: the `<String>` part is a **generic**, which tells the compiler what the list holds. `Integer.BYTES` is 4, the size of an `int` in bytes. `Long.BYTES` is 8.

**Comments.** `// text` runs to the end of the line. `/* ... */` can span lines. `/** ... */` is a **Javadoc** comment (below).

## Walkthrough of this repository's use

Each feature below says which Java version added it. The build sets `options.release = 25`, so all of them are available. The table lists the minimum version for each. I confirmed the language features by compiling a small file with `javac --release N`: the error message names the first release that does not have the feature. The library methods come from the API documentation.

| Feature | First final in | Where this repository uses it |
|---|---|---|
| Underscores in number literals (`1_000_000L`), `try`-with-resources, `java.nio.file.Files` | Java 7 | `EstimateCli`, `DiskProbe`, `CsvSheet` |
| Lambdas, method references, streams, `Optional`, `java.time.Duration`, `Math.toIntExact`, `Integer.BYTES` | Java 8 | `Unit`, `Sensitivity`, `ProbeSettings`, `LoopbackProbe` |
| `List.of`, `Set.of`, `Map.of` | Java 9 | `CsvSheet`, `ProbeSettings` |
| `List.copyOf`, `Map.copyOf` | Java 10 | `Inputs`, `Store`, `Estimate` |
| `String.isBlank()`, `String.strip()`, `Optional.isEmpty()`, `Path.of` | Java 11 | `ProbeSettings`, `CsvSheet`, `InputsReader`, `EstimateCli` |
| Switch expressions (`switch` that gives a value, `->` arms) | Java 14 | `Range.contains`, `EstimateCli.explain` |
| Records and compact constructors | Java 16 | `Quantity`, `SheetEntry`, `Inputs`, `ProbeSettings` and many more |
| Sealed interfaces | Java 17 | `SheetProblem` |
| `ExecutorService` as `AutoCloseable` | Java 19 | `LoopbackProbe.roundTrips` |
| Pattern-matching `switch` and record patterns | Java 21 | `Sensitivity.inputsFrom`, `EstimateCli.main`, `ProbeCli.main` |
| Virtual threads, `Executors.newVirtualThreadPerTaskExecutor()` | Java 21 | `LoopbackProbe.roundTrips` |
| The unnamed pattern `_` | Java 22 | `Sensitivity.inputsFrom`, `EstimateCli.explain` |

Evidence, from compiling with `--release`. Records fail under `--release 15` (`records are not supported in -source 15`) and compile under 16. Sealed classes fail under 16 (`sealed classes are not supported in -source 16`) and compile under 17. A switch expression fails under 13 (`switch expressions are not supported in -source 13`) and compiles under 14. A pattern in a `switch` fails under 20 (`patterns in switch statements are not supported in -source 20`) and compiles under 21. A `case B _ ->` fails under 21 (`unnamed variables are not supported in -source 21`) and compiles under 22. `Executors.newVirtualThreadPerTaskExecutor()` fails under 20 (`newVirtualThreadPerTaskExecutor() is a preview API and is disabled by default`) and compiles under 21. The repository sets 25, so none of these matters at run time. It matters if you copy a snippet into an older project.

### Records and compact constructors

A **record** is a class whose job is to carry values. You list the **components** in the header. Java writes the private final fields, the constructor, the accessor methods, `equals`, `hashCode` and `toString`.

```java
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

- `public record Quantity(BigDecimal amount, Unit unit)` makes a class with two fields. The accessors are `amount()` and `unit()` (no `get`). I confirmed with `javap` that the compiled class `extends java.lang.Record`, is `final`, and has `amount()`, `unit()`, `equals` and `hashCode`.
- A record is immutable: the fields cannot change after the constructor. Immutable objects are safe to share between threads.
- A record may write its own `toString`, as `Quantity` does, and may have its own methods and static members.

A **compact constructor** is the record form of a constructor. It has no parameter list. The components are available as variables. You write checks, and you may reassign the variables. Java copies them into the fields when the constructor ends. `Quantity`'s one (shown above under Exception) only validates. This one **normalizes** as well:

```java
  public Inputs {
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(stores, "stores");
    Map<InputRule, Quantity> checked = new EnumMap<>(InputRule.class);
    for (InputRule rule : InputRule.values()) {
      checked.put(rule, rule.requireValid(values.get(rule)));
    }
    values = Map.copyOf(checked);
    stores = List.copyOf(stores);
    if (stores.isEmpty()) {
      throw new IllegalArgumentException("an estimate needs at least one store");
    }
    Set<String> names = new HashSet<>();
    for (Store store : stores) {
      if (!names.add(store.name())) {
        throw new IllegalArgumentException("two stores are named " + store.name());
      }
    }
  }
```

- `values = Map.copyOf(checked);` reassigns the parameter, and Java stores the new map in the field. The caller's map is replaced by an unmodifiable copy.

### Sealed interfaces with nested records

An **interface** names a set of abilities and has no state. A **sealed** interface also names who may implement it. The compiler knows the whole list.

```java
package io.plainround.seatwave.estimates.estimate;

/**
 * Why a sheet cannot be used. These are expected failures of input typed by a person, so they are
 * returned as values. The interface is sealed, so a switch over it must handle every reason.
 */
public sealed interface SheetProblem {

  /**
   * The first line that is not a comment is not the expected header.
   *
   * @param found the line that was found
   */
  record WrongHeader(String found) implements SheetProblem {}

  /**
   * A line does not have four columns.
   *
   * @param lineNumber the line in the file
   */
  record WrongColumnCount(int lineNumber) implements SheetProblem {}
```

- `sealed interface SheetProblem` has no `permits` clause: the ten records are declared inside the same file, so the compiler infers the list.
- The records are **nested** inside the interface. A record nested in an interface is implicitly `public` and `static`. From outside they are named `SheetProblem.WrongHeader`, `SheetProblem.Missing` and the other eight.
- `implements SheetProblem` says "a `WrongHeader` is a kind of `SheetProblem`".
- `record NoStore() implements SheetProblem {}` (line 83 of the file) is a record with no components and an empty body. It is a marker for "this happened".
- The point: the list of problems is written once, and the compiler checks every use of it.

### Enums with fields

An **enum** is a type with a fixed list of values. Each value is a constant object. An enum can have fields, a constructor and methods.

```java
public enum Unit {
  /** People. */
  USERS("users", Dimension.USERS, "1"),
```

```java
  private final String symbol;
  private final Dimension dimension;
  private final BigDecimal sizeInBaseUnits;

  Unit(String symbol, Dimension dimension, String sizeInBaseUnits) {
```

- `USERS("users", Dimension.USERS, "1")` creates the constant `USERS` by calling the constructor with three arguments. The last constant ends with `;`.
- The constructor has no access keyword, because an enum constructor is always private.
- `values()` (not written in the file) is added by Java to every enum. It returns an array of all constants in declaration order. `Unit.values().length` is 33. `Arrays.stream(values())` turns that array into a stream.
- `==` and `!=` compare enum constants correctly, because each constant exists once. `Quantity` writes `other.unit != unit`.
- `EnumMap` is a map made for enum keys. `new EnumMap<>(InputRule.class)` needs the class object of the enum. It keeps its keys in the enum's order.
- Enums are available since Java 5.

### `switch` expressions, pattern matching and record patterns

A **switch statement** runs code. A **switch expression** produces a value. Its arms use `->` and do not fall through to the next arm.

```java
  boolean contains(BigDecimal value) {
    return switch (this) {
      case POSITIVE -> value.signum() > 0;
      case NON_NEGATIVE -> value.signum() >= 0;
      case AT_LEAST_ONE -> value.compareTo(BigDecimal.ONE) >= 0;
      case SHARE -> value.signum() >= 0 && value.compareTo(HUNDRED) <= 0;
      case POSITIVE_SHARE -> value.signum() > 0 && value.compareTo(HUNDRED) <= 0;
      case WHOLE_NUMBER -> value.signum() >= 0 && value.stripTrailingZeros().scale() <= 0;
    };
  }
```

- `switch (this)` switches on the enum value. Each arm is `case NAME -> expression;`. The switch lists all six constants and has no `default`, so adding a seventh makes the compiler stop until a seventh arm is written.

A **type pattern** names the value when its type matches:

```java
  static String explain(SheetProblem problem) {
    return switch (problem) {
      case SheetProblem.WrongHeader wrong ->
          "header: expected '" + CsvSheet.HEADER + "', found '" + wrong.found() + "'";
      case SheetProblem.WrongColumnCount wrong ->
          "line " + wrong.lineNumber() + ": expected 4 columns: quantity,value,unit,source";
      case SheetProblem.NotANumber wrong ->
          "line " + wrong.lineNumber() + ": '" + wrong.text() + "' is not a plain decimal number";
      case SheetProblem.MissingSource missing ->
          "line " + missing.lineNumber() + ": the source column is empty";
      case SheetProblem.UnknownQuantity unknown ->
          "line " + unknown.lineNumber() + ": '" + unknown.quantity() + "' is not an input";
      case SheetProblem.WrongUnit wrong ->
```

- `case SheetProblem.WrongHeader wrong ->` means "if the problem is a `WrongHeader`, call it `wrong` and use it in the arm". No cast is needed. The switch covers every record of the sealed interface and has no `default`: the compiler checks that the cases are **exhaustive**. Add an eleventh problem and this method stops compiling.

A **record pattern** takes a record apart in the pattern:

```java
    switch (CsvSheet.parse(lines).flatMap(EstimateCli::withInputs)) {
      case Result.Success<Checked, List<SheetProblem>>(Checked checked) -> print(checked);
      case Result.Error<Checked, List<SheetProblem>>(List<SheetProblem> problems) -> {
        problems.forEach(problem -> System.err.println("REJECTED " + explain(problem)));
        System.err.println("SHEET REJECTED: fix every line above; nothing was estimated.");
        System.exit(EXIT_SHEET_REJECTED);
      }
    }
```

- `Result.Success<Checked, List<SheetProblem>>(Checked checked)` matches a `Success` and binds its component to `checked`. The type arguments are written out in full.
- This `switch` is a statement, not an expression: its arms run code. A `System.exit` in an arm ends the program.

The **unnamed pattern** `_` says "there is a value here and I do not need it":

```java
  private static Optional<Inputs> inputsFrom(List<SheetEntry> entries) {
    return switch (InputsReader.read(entries)) {
      case Result.Success<Inputs, List<SheetProblem>>(Inputs inputs) -> Optional.of(inputs);
      case Result.Error<Inputs, List<SheetProblem>> _ -> Optional.empty();
    };
  }
```

- `Result.Error<Inputs, List<SheetProblem>> _ -> Optional.empty();` matches an error and does not name it. The sheet's problems are not needed here, only the fact that the sheet is invalid. The unnamed variable `_` is final in Java 22.

`switch` over a sealed type with a `default` would hide the compiler's reminder to handle a new case. This repository has none.

### `Optional`

`Optional<T>` is a box that holds one value or nothing. It makes "maybe not found" part of the type.

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

- The return type `Optional<Unit>` forces a caller to decide what to do when there is no match. `findFirst()` ends a stream and returns the first element, or an empty `Optional`.
- `Optional.of(x)` makes a box with a value. `Optional.empty()` makes an empty one. `Optional.ofNullable(x)` makes an empty box when `x` is null: `ProbeOperation.textbook()` returns `Optional.ofNullable(textbook)`.
- `isPresent()` and `isEmpty()` (Java 11) ask whether there is a value. `get()` takes it out and throws if there is none, so it is called only after `isPresent()`, as `InputsReader.readEntry` does.
- `map(f)` applies a function to the value if there is one. `orElse(x)` takes the value or a default. `orElseThrow(supplier)` takes the value or throws the exception the supplier makes. `ifPresent(f)` runs a function only if there is a value.

```java
  public Quantity result(String id) {
    return steps.stream()
        .filter(step -> step.id().equals(id))
        .findFirst()
        .map(Step::result)
        .orElseThrow(() -> new IllegalArgumentException("no step has the id " + id));
  }
```

- This chain is a stream that ends in an `Optional`. `.map(Step::result)` changes the step into its result if there is one. `.orElseThrow(() -> new IllegalArgumentException(...))` throws if no step has the id. `() -> ...` is a lambda with no input.

### Streams, lambdas and method references

A **stream** is a pipeline over a series of items: a source, some steps, and an end. `.filter(p)` keeps items for which `p` is true. `.map(f)` changes each item. The end step (`.findFirst()`, `.anyMatch(p)`, `.collect(...)`, `.forEach(f)`) produces the result. A stream is used once.

A **lambda** is a small unnamed function: `rule -> rule.key.equals(key)` takes `rule` and gives a boolean. A lambda can use a surrounding variable only if that variable is never reassigned (it is **effectively final**). A **method reference** is a short way to write a lambda that only calls one method: `Step::id` means `step -> step.id()`, and `problems::add` means `problem -> problems.add(problem)`.

```java
  private static SensitivityRow compare(
      String quantity, String change, Estimate baseline, Estimate changed) {
    Map<String, Quantity> changedById =
        changed.steps().stream().collect(Collectors.toMap(Step::id, Step::result));
    String mostMoved = "no result moved";
```

- `changed.steps().stream()` opens a stream of steps. `Collectors.toMap(Step::id, Step::result)` ends it by building a `Map<String, Quantity>`: the key is the step's id and the value is its result.

```java
  public static boolean hasFlag(List<SensitivityRow> sensitivity) {
    return sensitivity.stream().anyMatch(SensitivityRow::isFlagged);
  }
```

- `anyMatch(SensitivityRow::isFlagged)` is true when at least one row satisfies the test.

`Function<Inputs, Estimate>` is a type from `java.util.function`: a function that takes `Inputs` and gives an `Estimate`. `Sensitivity.check` takes one as a parameter, and the real call passes `Estimator::estimate`.

### Immutable collections

`List.of(a, b, c)` makes an **unmodifiable** list: it throws `UnsupportedOperationException` on `add`, `remove` or `set`. I ran `List.of(1).add(2)` and got that exception. It also rejects null elements. `List.copyOf(list)` and `Map.copyOf(map)` make unmodifiable copies. I ran `Map.copyOf(...).put("b", 2)` and got `UnsupportedOperationException`.

```java
public record Estimate(List<Step> steps) {

  /**
   * Creates an estimate.
   *
   * @throws IllegalArgumentException if two steps share an id
   */
  public Estimate {
    Objects.requireNonNull(steps, "steps");
    steps = List.copyOf(steps);
    Set<String> ids = new HashSet<>();
    for (Step step : steps) {
      if (!ids.add(step.id())) {
        throw new IllegalArgumentException("two steps have the id " + step.id());
      }
    }
  }
```

- `steps = List.copyOf(steps);` replaces the caller's list with an unmodifiable copy, so a caller who keeps the original list and changes it later cannot change the estimate. That is what makes the record truly immutable. A record that holds a modifiable list looks immutable and is not.
- `new HashSet<>()` makes a set that answers "have I seen this?" quickly. `ids.add(x)` adds and returns `false` if the item was already there.

`new ArrayList<>()` uses the **diamond** `<>`: Java fills in the type from the left side. `LinkedHashMap` keeps insertion order. `HashMap` keeps no order.

### `BigDecimal` and why money uses it

A `double` stores numbers in binary and cannot hold most decimal fractions exactly. I ran `0.1 + 0.2` and got `0.30000000000000004`. For a price, or a size that you print, that is unacceptable.

`BigDecimal` stores the decimal digits and a **scale** (the number of digits after the point). Its operations are exact. It is slower than `double`, which does not matter here. `new BigDecimal("0.1").add(new BigDecimal("0.2"))` gave `0.3`.

```java
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

- A `BigDecimal` is made from text (`new BigDecimal("0.01")`), never from a `double`, so no binary rounding error enters.
- `multiply(x, PRECISION)` multiplies and rounds to the `MathContext`. `MathContext.DECIMAL64` is 16 significant digits. Without a `MathContext`, `divide` throws `ArithmeticException: Non-terminating decimal expansion` when the result never ends, as 1 divided by 3 does. I ran it. With `DECIMAL64`, 1 / 3 is `0.3333333333333333`.
- `signum()` is -1, 0 or 1 for a negative, zero or positive number. `Quantity` uses `amount.signum() < 0`.
- `compareTo` compares the **value**, and `equals` also compares the scale, so 1.0 and 1 are not `equals`. I ran both: `equals` gave `false`, `compareTo` gave `0`. The code uses `compareTo`:

```java
      case AT_LEAST_ONE -> value.compareTo(BigDecimal.ONE) >= 0;
```

- `setScale(0, RoundingMode.CEILING)` fixes the number of decimals and rounds toward the larger number. The estimator uses it for a server count:

```java
    // Round up: 53.7 servers means 54, because 53 would run above the utilization target.
    BigDecimal needed =
        planned
            .amount()
            .divide(safe.amount(), Quantity.PRECISION)
            .setScale(0, RoundingMode.CEILING);
```

- 64.48 becomes 65, and 64.01 also becomes 65. I ran both. `stripTrailingZeros()` removes zeros at the end of the decimals. `new BigDecimal("100").stripTrailingZeros()` prints `1E+2` with scale -2, so the code never prints it directly: `toPlainString()` writes `100`.
- `RoundingMode.HALF_UP` is school rounding: 0.5 goes up. `Quantity.rounded` uses it for display.

### `Duration`

`java.time.Duration` is a length of time (not a moment on the clock). It is immutable.

```java
    if (diskFileBytes < diskBlockBytes || cacheArrayBytes < Integer.BYTES) {
      throw new IllegalArgumentException("the file and the arrays must hold at least one block");
    }
    if (socketTimeout.isNegative() || socketTimeout.isZero()) {
      throw new IllegalArgumentException("the socket timeout must be positive: " + socketTimeout);
    }
  }
```

- `Duration.ofMillis(2000)` is two seconds. I printed one and got `PT2S`, the standard text form, and `toMillis()` gave `2000`.
- `isNegative()` and `isZero()` ask about its sign. The probe rejects a timeout that is not positive.
- `LoopbackProbe` converts a `Duration` back to an `int` number of milliseconds with `Math.toIntExact(settings.socketTimeout().toMillis())`. `toIntExact` throws instead of silently wrapping when the number does not fit in an `int`.

### `System.nanoTime`, and why not `currentTimeMillis`

To measure how long something takes, read a clock before and after, and subtract.

```java
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

- `System.nanoTime()` returns a count of nanoseconds from an arbitrary starting point. The JDK documentation says it can only be used to measure elapsed time, and is not related to any wall-clock time. Its resolution is at least as good as that of `currentTimeMillis`.
- `System.currentTimeMillis()` returns the wall-clock time in milliseconds since 1 January 1970. The wall clock can be set by a person or corrected by the computer over the network while the program runs, and it counts in milliseconds. A timing of a one-microsecond operation would be 0 or 1 millisecond, and a correction in the middle could make it wrong.
- So durations use `nanoTime` and dates use `currentTimeMillis` or `java.time`. On the verification machine two `nanoTime` calls in a row differed by 41 ns in one run and 0 ns in another, so a single read of a 1 ns operation could not be seen. That is why the probe times 1,000 reads together.
- `long` holds the result. A `long` holds up to about 9.2 x 10^18, so nanoseconds do not overflow for about 292 years of difference.

### Arrays, and why a large array defeats the CPU cache

An **array** is a fixed-length row of values of one type. `new long[2000]` makes 2,000 `long` values, all zero. `samples[sampleIndex] = ...` writes one. `samples.length` is the length. `int[] a = {3, 1, 2};` is an **array initializer**. `a.clone()` makes a copy. `Arrays.sort(copy)` sorts it in place.

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

- This builds an `int[]` of `length` elements where element `i` holds the index of the next element to read, in one single cycle.
- Elements of an array sit next to each other in memory. The CPU has small, fast stores called **caches** between itself and main memory. A read that finds its address in a cache takes about a nanosecond. A read that does not takes about a hundred. A 16 KiB array (4,096 `int` values) fits in the smallest cache, so after one pass every read hits it. A 256 MiB array (67,108,864 `int` values) is far larger than any cache, so a read at a random place almost always misses. The probe uses the two sizes to show the difference: on one run, 2.17 ns for the small array and 115 ns for the large one.

### `FileChannel` and `force`

A `FileChannel` is a connection to an open file that can read and write at any position. `ByteBuffer` is a block of bytes in memory that the channel reads into or writes from.

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

- `channel.read(block, position)` reads into the buffer from the given position in the file and returns the number of bytes read, or -1 at the end. A read or a write may move fewer bytes than asked, so both are in a loop until `block.hasRemaining()` is false.
- `FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)` opens a file for both. `Files.createTempFile(folder, "latency-probe-", ".bin")` creates an empty file with a unique name.
- `channel.write(...)` puts bytes into the operating system's **page cache**, memory that holds recently used file data, and returns. The system writes them to the storage device later. `channel.force(false)` asks the operating system to write the modified file content to the storage device and to return only when it has. The `false` says that only the content must be written, not the file's metadata. The JDK documentation says the effect of that parameter depends on the operating system. `force(true)` writes the metadata too.

### Sockets, `ServerSocket` and the loopback address

A **socket** is one end of a TCP connection: a reliable stream of bytes between two programs. A **server socket** waits for connections on a **port**, a number from 0 to 65,535. The **loopback address** (`127.0.0.1`) means this same machine. A connection to it never leaves the machine.

```java
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

- `new ServerSocket(0, 1, InetAddress.getLoopbackAddress())`: port 0 asks the operating system for any free port. The 1 is the number of connections that may wait to be accepted. The address makes the server listen only on the loopback address, so no other computer can connect.
- `client.connect(address, timeoutMillis)` connects, giving up after the timeout. `setSoTimeout(ms)` makes a read or an `accept` throw if it waits longer than that. A program that waits forever on a broken connection hangs, so every socket call here has a bound.
- `client.setTcpNoDelay(true)` turns off the delay that TCP may add to a very small write.

### Executors and virtual threads

An `ExecutorService` runs tasks on threads. `submit(task)` starts a task and returns a `Future`, a receipt you can wait on with `get(timeout, unit)`. `Executors.newVirtualThreadPerTaskExecutor()` starts a new **virtual thread** for each task. A virtual thread (Java 21) is cheap to create and parks while it waits for input or output. The probe needs a second thread so the echo server can run while the client measures. The line `try (... ExecutorService executor = ...)` closes the executor at the end of the block, and closing it waits for its tasks.

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
```

- A thread can be **interrupted**. When a thread blocked in `get` is interrupted, Java throws `InterruptedException` and clears the thread's interrupted flag. Good practice is to set the flag again with `Thread.currentThread().interrupt()` so the caller sees it. This code does so, then throws an `InterruptedIOException` that carries the original as its cause.

### `Properties`

`java.util.Properties` is a map from text to text that can read the `key=value` format of a `.properties` file. `java-properties.md` explains the format.

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
```

- `properties.getProperty(key)` returns the text or `null` when the key is absent. Values keep trailing spaces: I loaded `name = hello world ` and got `hello world ` with the trailing space, which is why `ProbeSettings.positiveInt` calls `strip()`.
- Passing a `Reader` (here a UTF-8 reader) decodes the file as UTF-8. The `InputStream` form would use ISO-8859-1.

### `String.format` and `Locale.ROOT`

`String.format(template, values...)` fills slots in a template: `%s` text, `%d` a whole number, `%f` a decimal number, `%n` a line break. A flag or number before the letter shapes the output: `%-32s` pads text to width 32 on the right, `%,d` adds thousands separators, `%.2f` keeps two decimals.

```java
  @Override
  public String toString() {
    BigDecimal rounded = rounded();
    int decimals = isWholeMoney() ? CENTS : Math.max(0, rounded.stripTrailingZeros().scale());
    return String.format(Locale.ROOT, "%,." + decimals + "f", rounded) + " " + unit.symbol();
  }
```

- The first argument, `Locale.ROOT`, says "use no country's rules". Without it the computer's language setting decides the decimal mark. I ran `String.format(Locale.GERMANY, "%,.2f", 2894.71)` and got `2.894,71`, and with `Locale.ROOT` I got `2,894.71`. So the same sheet prints the same report on every machine.
- `"%,." + decimals + "f"` builds the template at run time, for example `%,.2f`.
- `System.out.printf(Locale.ROOT, ...)` is the same, printed. `System.lineSeparator()` is the machine's line break: `\n` on macOS and Linux and `\r\n` on Windows. `%n` in a template is the same.

### `Objects.requireNonNull`

`Objects.requireNonNull(value, "name")` returns the value if it is not null and throws `NullPointerException` with the message `name` otherwise. I ran it with null and got `java.lang.NullPointerException: name`. It makes a null fail at the door, with the name of the bad argument, instead of deep inside later code. It starts every compact constructor and every public method here.

### Other language features used

- **Generics.** `Result<List<SheetEntry>, List<SheetProblem>>`, `Map<InputRule, Quantity>` and `List<Step>` tell the compiler what a container holds.
- **Varargs.** `Quantity... factors` means "any number of quantities", received as an array: `product(Unit.BYTES, perDay, keptDays)`.
- **Conditional expression.** `condition ? a : b` picks one of two values.
- **Loops.** The enhanced `for (Step step : estimate.steps())` visits each element. The classic `for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++)` counts. `while (block.hasRemaining())` repeats while a condition holds. `continue` jumps to the next turn.
- **Casts.** `(long) random.nextInt(blocks) * blockBytes` converts the `int` to `long` before the multiplication, so it is done in 64 bits. I ran `(long) 40000 * 70000`, which gave `2800000000`, and `40000 * 70000` as `int`, which gave `-1494967296`: it overflowed.
- **Shifts.** `1L << 20` moves 1 twenty places left: 2 to the power 20, 1,048,576.
- **Autoboxing.** `Integer` is the object form of `int`. A `Map<String, Integer>` holds objects, and Java converts between `int` and `Integer` automatically.
- **Regular expressions.** `Pattern.compile("[0-9]+(\\.[0-9]+)?")` compiles a text that describes a shape of text. `pattern.matcher(text).matches()` is true when the whole text has that shape. In Java text a backslash is written twice.
- **`StringBuilder`.** A growing buffer of text. `append` returns the builder, so calls chain. It avoids making many temporary strings.
- **`@Override`.** Marks a method that replaces one of the parent class. `Quantity.toString` has it. The compiler checks the signature matches.
- **`System.exit(n)`** ends the program with status `n`. `System.err` is the error stream and `System.out` is the standard output.

### Javadoc comments

A **Javadoc** comment starts with `/**` and is attached to the class, field or method that follows. A tool turns them into web pages. Tags: `@param name text` documents a parameter (for a record, a component), `@return text` documents the result, `@throws Type text` documents an exception, `{@code x}` shows code, `{@link Type}` makes a link, `<p>` starts a paragraph.

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

- Every public method in this repository has a Javadoc with the `@param`, `@return` and `@throws` it needs. That is why the walkthroughs can quote the promises of each method.
- Plain `//` comments are used for the reason behind a line. The convention in this code: Javadoc says what a thing promises, a `//` comment says why a line is written the way it is. This one is from `Unit`:

```java
    // Written as text so the size is an exact decimal, not the nearest double.
    this.sizeInBaseUnits = new BigDecimal(sizeInBaseUnits);
```

### The compiler settings

The relevant lines of `build.gradle.kts`:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    // Every warning is an error: a warning that is allowed to stay is a defect nobody owns.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
```

The build compiles with `options.release = 25` and UTF-8 source encoding. `-Xlint:all` turns on every compiler warning. `-Werror` turns each warning into an error. A warning that is allowed to stay is a defect nobody owns, says the comment in `build.gradle.kts`. `gradle.md` explains the file.

## Try it yourself

Run these in `seatwave-estimates/`.

1. See the Java version:

```
java --version
```

I got `openjdk 25.0.2 2026-01-20 LTS` and `OpenJDK Runtime Environment Temurin-25.0.2+10 (build 25.0.2+10-LTS)`.

2. Compile the program and count the class files:

```
./gradlew -q compileJava
find build/classes/java/main -name '*.class' | wc -l
```

The first command prints nothing and ends with exit status 0. The second printed `39`: one class file for each class, record, enum and nested record in the three packages.

3. Look at what a record became:

```
javap -cp build/classes/java/main io.plainround.seatwave.estimates.units.Quantity
```

I got `public final class io.plainround.seatwave.estimates.units.Quantity extends java.lang.Record`, then `PRECISION`, a constructor, `of`, `to`, `plus`, `scaledBy`, `timesAsMany`, `toString`, `hashCode`, `equals`, `amount()` and `unit()`. `javap` is the JDK's class-file reader.

4. See that a sealed interface stores its list. The compiled `SheetProblem` has a `PermittedSubclasses` attribute with the ten records:

```
javap -cp build/classes/java/main -v 'io.plainround.seatwave.estimates.estimate.SheetProblem' | grep -A 12 PermittedSubclasses
```

I got `PermittedSubclasses:` followed by ten lines, from `SheetProblem$WrongHeader` to `SheetProblem$NoStore`.

5. See `BigDecimal` against `double`, and `Locale.ROOT`. Save this as `build/try/Money.java` (the `build` folder is not tracked by Git):

```
import java.math.BigDecimal;
import java.util.Locale;

public class Money {
  public static void main(String[] args) {
    System.out.println(0.1 + 0.2);
    System.out.println(new BigDecimal("0.1").add(new BigDecimal("0.2")));
    System.out.println(String.format(Locale.GERMANY, "%,.2f", 2894.71));
    System.out.println(String.format(Locale.ROOT, "%,.2f", 2894.71));
  }
}
```

then run `java build/try/Money.java`. Java compiles and runs a single source file when you give `java` its path. I got `0.30000000000000004`, `0.3`, `2.894,71` and `2,894.71`.

6. Prove the minimum version of a feature. Save this as `build/try/Unnamed.java`:

```
public class Unnamed {
  sealed interface Shape permits Circle, Square {}
  record Circle(int r) implements Shape {}
  record Square(int s) implements Shape {}
  static String name(Shape shape) {
    return switch (shape) {
      case Circle _ -> "circle";
      case Square _ -> "square";
    };
  }
  public static void main(String[] a) { System.out.println(name(new Circle(1))); }
}
```

Compile it with `javac --release 21 -d build/try/o21 build/try/Unnamed.java`. I got `error: unnamed variables are not supported in -source 21`. Compile it with `--release 22` into another folder and run `java -cp build/try/o22 Unnamed`. I got `circle`.

## Mistakes people make

- **Comparing with `==` when you mean "equal".** For objects, `==` asks "same object?". Use `equals`. For enum constants `==` is right, because each constant exists once. This code uses it for units and dimensions.
- **Using `double` for exact decimals.** Use `BigDecimal`, built from text.
- **Comparing `BigDecimal` with `equals`.** It compares the scale too, so 1.0 and 1 differ. Use `compareTo`.
- **Dividing `BigDecimal` with no `MathContext`.** It throws on an endless decimal expansion.
- **Overflowing an `int`.** `int` times `int` is computed in 32 bits. Cast one side to `long` first, as `DiskProbe.randomOffset` does.
- **Using `System.currentTimeMillis` to time a short operation.** It is the wall clock, in milliseconds. Use `System.nanoTime`.
- **Timing a loop cold.** The first runs are interpreted. Warm up and throw the warm-up away.
- **Catching an exception and ignoring it.** The code catches only what it can report or return as a problem.
- **Using a mutable list inside a record.** The record would look immutable and would not be. This code copies with `List.copyOf` and `Map.copyOf`.
- **Using the default locale in `String.format` or `toLowerCase`.** The output then depends on the computer. Pass `Locale.ROOT`.
- **Adding a `default` branch to a switch over a sealed type.** It hides the compiler's reminder to handle a new case. This repository has none.
- **Throwing exceptions for ordinary bad input.** See `result4j.md`.
- **Forgetting to close a file or socket.** Use try-with-resources.

## Check yourself

1. What is the difference between `static` and `final`? Answer: `static` means the member belongs to the class, not to an object. `final` means it is assigned once. A constant is both.
2. What does a compact constructor of a record do? Answer: it runs before the fields are set. It may validate and may reassign the component variables. Java then stores them in the fields. `Inputs` uses it to replace a map with an unmodifiable copy.
3. Why does the `switch` in `EstimateCli.explain` have no `default` branch? Answer: `SheetProblem` is sealed, so the compiler knows all ten shapes and checks that all are handled. A new problem breaks the build until every switch handles it.
4. Why does the probe use `System.nanoTime` and not `System.currentTimeMillis`? Answer: `nanoTime` is made for measuring elapsed time and is not tied to the wall clock, which can be corrected while the program runs. `currentTimeMillis` counts in milliseconds, which is too coarse for operations of microseconds.
5. Which Java version does the unnamed pattern `_` need, and how can you check? Answer: Java 22. Compile a file that uses `case Circle _ ->` with `javac --release 21` and read the error `unnamed variables are not supported in -source 21`.

## Further reading

- Java SE 25 API documentation, https://docs.oracle.com/en/java/javase/25/docs/api/
- The Java Language Guide for JDK 25: records, https://docs.oracle.com/en/java/javase/25/language/records.html
- Sealed classes and interfaces, https://docs.oracle.com/en/java/javase/25/language/sealed-classes-and-interfaces.html
- Pattern matching, https://docs.oracle.com/en/java/javase/25/language/pattern-matching.html
- Enums, https://docs.oracle.com/en/java/javase/25/language/enums.html
- Virtual threads, https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html
- `java.math.BigDecimal`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html
- `java.time.Duration`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Duration.html
- `java.nio.channels.FileChannel`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/channels/FileChannel.html
- `java.util.Formatter`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Formatter.html
- Javadoc tool, https://docs.oracle.com/en/java/javase/25/javadoc/javadoc.html