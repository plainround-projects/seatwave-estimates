# Tests of the `estimate` package, walked through

## What it is

These are the automated tests of the estimator: the part of the program that reads an estimation sheet (a CSV file, "comma-separated values", where each line is one input and the commas separate its columns), checks every input against its rule, does the arithmetic of the five-line estimate, and prints a report. The tests live in `src/test/java/io/plainround/seatwave/estimates/estimate/`. There are nine files: one helper class (`Sheets.java`) that the others share, and eight test classes with 72 test methods in all. A **test** is a small program that calls your code with a chosen input and checks the result; `tests-units-walkthrough.md` explains tests, annotations, assertions and the vocabulary of the `units` package from zero, and this file builds on it.

## Why this repository uses it

The estimator is the main program of Module 3, and its promise is "every printed number carries a unit, and every result can be reproduced from the printed inputs". The tests turn that promise into checks. They pin the real numbers of the production sheet (5,000,000 daily users give 1,157 requests per second on average, 66 servers at peak), so a change to a formula shows up at once. They also try every wrong sheet a learner is likely to write (a size in bits, a size in KB, a rate per day, a share above 100 percent) and check that each is rejected with the right line number. The coverage tool shows that all lines of the estimate code run except the command-line class `EstimateCli` and one `continue` in `Sensitivity` (`jacoco.md`).

## Where it appears

| Path | What it does |
| --- | --- |
| `src/test/java/io/plainround/seatwave/estimates/estimate/Sheets.java` | The helper: opens shipped sheets and unwraps parser results so a test states only what it expects. Not a test itself. |
| `.../estimate/CsvSheetTest.java` | 11 tests of the CSV reader and writer. |
| `.../estimate/InputsReaderTest.java` | 13 tests of the rules that check every input. |
| `.../estimate/RecordGuardsTest.java` | 11 tests that try to build each record in an invalid state. |
| `.../estimate/EstimatorTest.java` | 19 tests of the arithmetic, with the real numbers. |
| `.../estimate/SensitivityTest.java` | 7 tests of the "double one input" check. |
| `.../estimate/EstimateReportTest.java` | 6 tests of the printed report. |
| `.../estimate/EstimateCliTest.java` | 1 test of the error messages. |
| `.../estimate/ShippedSheetsTest.java` | 4 tests that run the real files in `sheets/`. |
| `sheets/*.csv` | The five sheets the tests read: `seatwave-production.csv`, `seatwave-simple.csv`, `photo-sharing.csv`, `photo-sharing-given.csv` and `lab1-unit-slips.csv`. |

## How it works underneath

1. Gradle runs the tests with the project folder as the **working directory**, the folder a program treats as "here". So a relative path such as `sheets/seatwave-production.csv` finds the file (`gradle.md`).
2. Every test class creates a new object per test, so the fields at the top of a class (such as `production` in `EstimatorTest`) are rebuilt for each test. They are cheap, so this is fine.
3. Most tests need a valid set of inputs. They read the real production sheet through the helper `Sheets`, then change one line and check what the program says. A test that starts from a real sheet and changes one thing shows exactly which rule that one change breaks.
4. The program never throws an exception for a bad sheet. It returns a **`Result`**, a value that is either a success holding the answer or an error holding a list of problems. A `Result` comes from the library result4j (`result4j.md`). The tests unwrap it with a `switch` (explained where it first appears, in `Sheets.java`).
5. A problem is one of ten kinds of `SheetProblem`, a **sealed interface**: a Java type that lists all the records allowed to implement it. Each kind is a **record** (a small immutable data type; `tests-units-walkthrough.md`) such as `WrongUnit(9, "response_bytes", "bytes", "bits")`: line 9, the quantity, the unit that was expected and the unit that was found. Because records have a correct `equals`, a test can compare a whole problem with `containsExactly(...)`.

## Walkthrough of this repository's use

Words that first appear here.

- A **fixture** is the prepared input a test starts from. `Sheets.PRODUCTION` is the fixture of most tests.
- A **helper** is a method that is not a test but shortens tests. `Sheets` is a helper class.
- **Generics**: `List<SheetEntry>` means "a list whose elements are `SheetEntry` objects". `Result<List<SheetEntry>, List<SheetProblem>>` means "a result whose success holds a list of entries and whose error holds a list of problems".
- A **stream** is a pipeline: `list.stream().filter(...).map(...).toList()` takes a list, keeps some elements, transforms each, and collects the result into a new list. `entry -> something` is a **lambda**, a function written inline.
- `assertThat(list).containsExactly(a, b)` checks that the list holds exactly these elements in this order. `hasSize(n)` checks the length. `as("text")` adds a label to the failure message.


### `Sheets.java`

`Sheets.java` is the helper class. Its Javadoc line (the `/** ... */` comment) says what it is for. Nothing in it is a test. It has three jobs: open the shipped sheets, unwrap `Result` values, and make a changed copy of the production sheet. `final class Sheets` means no other class can extend it. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.fail;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Opens shipped sheets and parser results, so a test states only what it expects. */
final class Sheets {
```

- `fail` is a static import from AssertJ. `fail("text")` ends a test with a failure and the text, and it can sit anywhere a value is expected, which is why the `switch` statements below can use it as a branch.
- `Result` is the result4j type.
- `IOException` and `UncheckedIOException` are Java's file-trouble errors. The first must be declared or handled by the compiler's rules (a **checked** exception); the second can travel up without being declared (an **unchecked** one).
- `BigDecimal` is an exact decimal number (`tests-units-walkthrough.md`).
- `StandardCharsets`, `Files` and `Path` are Java's tools for reading files: a `Path` names a file, `Files.readAllLines` reads all of its lines into a list, and `StandardCharsets.UTF_8` says how the bytes are turned into text.
- `List` is Java's list type.

```java
  static final Path PRODUCTION = Path.of("sheets", "seatwave-production.csv");
  static final Path SIMPLE = Path.of("sheets", "seatwave-simple.csv");
  static final Path PHOTO_SHARING = Path.of("sheets", "photo-sharing.csv");
  static final Path PHOTO_SHARING_GIVEN = Path.of("sheets", "photo-sharing-given.csv");
  static final Path LAB1_UNIT_SLIPS = Path.of("sheets", "lab1-unit-slips.csv");
```

Five constants, one per shipped sheet. `Path.of("sheets", "seatwave-production.csv")` builds the relative path `sheets/seatwave-production.csv`. `static final` means: one value for the whole class, never changed. The names say what each sheet is: the national SeatWave (`PRODUCTION`), the small SeatWave with 20,000 daily users (`SIMPLE`), the photo-sharing app of Lab 3 (`PHOTO_SHARING`), its version with a mistake given to the learner (`PHOTO_SHARING_GIVEN`) and the Lab 1 sheet with unit slips (`LAB1_UNIT_SLIPS`).

```java
  private Sheets() {}
```

A **private constructor** with an empty body: no code outside the class can create a `Sheets` object, because the class has only static methods. It is the standard way to say "this is a toolbox, not a thing".

```java
  static List<String> linesOf(Path sheet) {
    try {
      return Files.readAllLines(sheet, StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
```

Reads every line of a sheet as text. `try { ... } catch (IOException unreadable) { ... }` runs the code in the `try` block, and if a file error happens, runs the `catch` block instead. The catch block wraps the error in an `UncheckedIOException` and throws that, so test code does not have to declare file errors everywhere. A test fails with this error if a sheet file is missing.

```java
  static List<SheetEntry> entriesOf(Path sheet) {
    return entries(CsvSheet.parse(linesOf(sheet)));
  }
```

Reads a sheet file and parses it: `CsvSheet.parse(linesOf(sheet))` returns a `Result`, and `entries(...)` (the next method) unwraps it into a plain list of `SheetEntry` records, one per input line. A `SheetEntry` holds the line number, the quantity name, the value, the unit text and the source text.

```java
  static List<SheetEntry> entries(Result<List<SheetEntry>, List<SheetProblem>> parsed) {
    return switch (parsed) {
      case Result.Success<List<SheetEntry>, List<SheetProblem>>(List<SheetEntry> entries) ->
          entries;
      case Result.Error<List<SheetEntry>, List<SheetProblem>>(List<SheetProblem> problems) ->
          fail("expected entries but got " + problems);
    };
  }
```

The unwrapping. `return switch (parsed) { ... };` is a **switch expression**: it looks at what kind of `Result` it was given and returns a value. `case Result.Success<...>(List<SheetEntry> entries) -> entries;` is a **record pattern**: if the result is a success, take the list out of it and name it `entries`. `case Result.Error<...>(List<SheetProblem> problems) -> fail("expected entries but got " + problems);` handles the other kind: the test fails with a message that lists the problems. So a test that expects a good sheet and gets a bad one fails with a readable reason. The compiler checks that both kinds are handled.

```java
  static List<SheetProblem> parseProblems(Result<List<SheetEntry>, List<SheetProblem>> parsed) {
    return switch (parsed) {
      case Result.Success<List<SheetEntry>, List<SheetProblem>>(List<SheetEntry> entries) ->
          fail("expected problems but got " + entries);
      case Result.Error<List<SheetEntry>, List<SheetProblem>>(List<SheetProblem> problems) ->
          problems;
    };
  }
```

The mirror of `entries`: it is used by tests that expect the parser to reject a sheet. On an error it returns the list of problems; on a success it fails the test with the entries it got.

```java
  static Inputs inputsOf(Path sheet) {
    return inputs(entriesOf(sheet));
  }
```

Reads a sheet file all the way to an `Inputs` object: parse the file (`entriesOf`), then check every entry against its rule (`inputs`). `Inputs` is the validated set of every number the estimate needs.

```java
  static Inputs inputs(List<SheetEntry> entries) {
    return switch (InputsReader.read(entries)) {
      case Result.Success<Inputs, List<SheetProblem>>(Inputs inputs) -> inputs;
      case Result.Error<Inputs, List<SheetProblem>>(List<SheetProblem> problems) ->
          fail("expected inputs but got " + problems);
    };
  }
```

Runs `InputsReader.read(entries)` and unwraps it the same way: success gives the `Inputs`, error fails the test with the problems.

```java
  static List<SheetProblem> inputProblems(List<SheetEntry> entries) {
    return switch (InputsReader.read(entries)) {
      case Result.Success<Inputs, List<SheetProblem>>(Inputs inputs) ->
          fail("expected problems but got " + inputs);
      case Result.Error<Inputs, List<SheetProblem>>(List<SheetProblem> problems) -> problems;
    };
  }
```

The mirror: returns the problems of `InputsReader.read`, and fails the test if the entries were accepted. Tests that expect a rejection use it.

```java
  /** Returns the production entries with one line's value, unit or presence changed. */
  static List<SheetEntry> productionWith(String quantity, String value, String unit) {
    return entriesOf(PRODUCTION).stream()
        .map(entry ->
            entry.quantity().equals(quantity)
                ? new SheetEntry(
                    entry.lineNumber(), quantity, new BigDecimal(value), unit, entry.source())
                : entry)
        .toList();
  }
```

Builds a changed copy of the production entries: the entry named `quantity` gets a new value and a new unit, every other entry is kept. `entriesOf(PRODUCTION).stream()` starts a stream over the entries. `.map(entry -> condition ? changed : entry)` uses the **conditional operator** `a ? b : c`: if the entry has the name asked for, build a new `SheetEntry` (same line number and source, new value and unit), otherwise keep the entry. `new BigDecimal(value)` turns the text into an exact decimal. `.toList()` collects the result. This is how a test makes "the production sheet, but `response_bytes` is in bits".

```java
  static List<SheetEntry> productionWithout(String quantity) {
    return entriesOf(PRODUCTION).stream()
        .filter(entry -> !entry.quantity().equals(quantity))
        .toList();
  }
```

A changed copy with one entry removed: `.filter(entry -> !entry.quantity().equals(quantity))` keeps the entries whose name is not the one asked for (`!` means "not"). Used to test "missing input".

### `CsvSheetTest.java`

`CsvSheetTest.java` tests `CsvSheet`, the reader and writer of the CSV format. The rules of the format: the first line that is not a comment must be the header `quantity,value,unit,source`; blank lines and lines that start with `#` are skipped; each other line has four columns; the value is a plain decimal number (underscores may group digits); the source is never empty and may be in double quotes. The reader reports every bad line, not only the first. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class CsvSheetTest {
```

- `Tuple` is an AssertJ type that groups several values into one row, used by the round-trip test.
- `HEADER` is a constant that copies the real header text from the main code, so the tests never repeat the spelling.

```java
  private static final String HEADER = CsvSheet.HEADER;
```

The constant `HEADER`: `CsvSheet.HEADER` is `"quantity,value,unit,source"`. Tests build sheets as lists of text lines, and the header is the first of them.

```java
  @Test
  void readsOneLineWithItsLineNumber() {
    List<SheetEntry> entries =
        Sheets.entries(
            CsvSheet.parse(List.of("# comment", HEADER, "peak_factor,26,ratio,the sale")));

    assertThat(entries).hasSize(1);
    assertThat(entries.getFirst().lineNumber()).isEqualTo(3);
    assertThat(entries.getFirst().quantity()).isEqualTo("peak_factor");
    assertThat(entries.getFirst().value()).isEqualByComparingTo("26");
    assertThat(entries.getFirst().unit()).isEqualTo("ratio");
  }
```

The sheet is a list of three lines: a comment, the header and one entry. `List.of(...)` builds a list from its arguments. The entry gets line number 3, because lines are counted from 1 in the file, comments included. `getFirst()` takes the first element. The five assertions check the line number, the quantity name, the value (`isEqualByComparingTo("26")` compares decimals by size) and the unit. Line numbers matter because every error message in the program names the line to fix.

```java
  @Test
  void skipsBlankLinesAndComments() {
    List<SheetEntry> entries =
        Sheets.entries(
            CsvSheet.parse(List.of("", "# a", HEADER, "", "# b", "peak_factor,2,ratio,x")));

    assertThat(entries).hasSize(1);
  }
```

A sheet with a blank line, two comment lines and one entry yields one entry. Learners write comments and spacing in their sheets, and these must not disturb the reading.

```java
  @Test
  void readsUnderscoresAsDigitGroups() {
    List<SheetEntry> entries =
        Sheets.entries(CsvSheet.parse(List.of(HEADER, "daily_active_users,5_000_000,users,x")));

    assertThat(entries.getFirst().value()).isEqualByComparingTo("5000000");
  }
```

`5_000_000` is read as 5000000. Underscores group digits, as in Java source code, because `5000000` is hard to read at a glance. The reader removes them before it checks that the text is a plain number.

```java
  @Test
  void keepsCommasInsideAQuotedSource() {
    List<SheetEntry> entries =
        Sheets.entries(
            CsvSheet.parse(
                List.of(HEADER, "peak_factor,26,ratio,\"1,000,000 fans, \"\"the sale\"\"\"")));

    assertThat(entries.getFirst().source()).isEqualTo("1,000,000 fans, \"the sale\"");
  }
```

A source can contain commas if it is written in double quotes, as every spreadsheet program does when it saves a CSV file. Inside quotes, a double quote is written twice. The Java text `"peak_factor,26,ratio,\"1,000,000 fans, \"\"the sale\"\"\""` is the CSV line `peak_factor,26,ratio,"1,000,000 fans, ""the sale"""`; the backslash before each quote is Java's way to put a quote inside a text. The reader gives back the source `1,000,000 fans, "the sale"`. The reason: the sources of the real sheets contain commas (for example "1,000,000 fans x 6 requests").

```java
  @Test
  void writesWhatItReadsSoASheetSurvivesARoundTrip() {
    List<SheetEntry> entries = Sheets.entriesOf(Sheets.PRODUCTION);

    List<SheetEntry> again = Sheets.entries(CsvSheet.parse(CsvSheet.render(entries)));

    assertThat(again)
        .extracting(SheetEntry::quantity, SheetEntry::value, SheetEntry::unit, SheetEntry::source)
        .containsExactlyElementsOf(
            entries.stream()
                .map(entry ->
                    Tuple.tuple(entry.quantity(), entry.value(), entry.unit(), entry.source()))
                .toList());
  }
```

A **round trip**: read the production sheet, write it back out with `CsvSheet.render(entries)`, read the written lines again, and compare. `extracting(SheetEntry::quantity, SheetEntry::value, SheetEntry::unit, SheetEntry::source)` turns each entry into a row of four values (a `Tuple`), leaving out the line number, which changes because rendering drops the comments. `containsExactlyElementsOf(...)` then compares with the same rows built from the original entries. This proves the report's first part is a valid sheet: the program prints its inputs as a sheet, and the learner can save the lines and run them again.

```java
  @Test
  void rejectsASheetWithoutTheHeader() {
    List<SheetProblem> problems =
        Sheets.parseProblems(CsvSheet.parse(List.of("name,value", "peak_factor,26,ratio,x")));

    assertThat(problems).containsExactly(new SheetProblem.WrongHeader("name,value"));
  }
```

A sheet whose first line is `name,value` is rejected with one problem, `WrongHeader("name,value")`, which carries the text that was found. Without the exact header the program cannot know what the columns mean.

```java
  @Test
  void rejectsAnEmptyFile() {
    assertThat(Sheets.parseProblems(CsvSheet.parse(List.of("# only a comment"))))
        .containsExactly(new SheetProblem.WrongHeader(""));
  }
```

A file that holds only a comment has no header at all. The reader reports `WrongHeader("")`, a wrong header with nothing found. An empty sheet is a mistake, not a valid sheet with no inputs.

```java
  @Test
  void rejectsALineWithTooFewColumns() {
    assertThat(Sheets.parseProblems(CsvSheet.parse(List.of(HEADER, "peak_factor,26"))))
        .containsExactly(new SheetProblem.WrongColumnCount(2));
  }
```

`peak_factor,26` has two columns, not four. The problem `WrongColumnCount(2)` names line 2.

```java
  @Test
  void rejectsAValueThatIsNotAPlainNumber() {
    List<String> lines =
        List.of(
            HEADER,
            "peak_factor,lots,ratio,x",
            "write_share,-5,percent,x",
            "request_bytes,3e2,bytes,x",
            "response_bytes,38 212,bytes,x");

    assertThat(Sheets.parseProblems(CsvSheet.parse(lines)))
        .containsExactly(
            new SheetProblem.NotANumber(2, "lots"),
            new SheetProblem.NotANumber(3, "-5"),
            new SheetProblem.NotANumber(4, "3e2"),
            new SheetProblem.NotANumber(5, "38 212"));
  }
```

Four bad values, each on its own line: `lots` (a word), `-5` (a sign: estimates are never negative), `3e2` (scientific notation) and `38 212` (a space inside the number). All four are rejected as `NotANumber`, with the line number and the text. The reader accepts only digits, an optional decimal point with more digits, and underscores. Being strict avoids a silent misreading: `38 212` read as 38 would be an error by a factor of 1,000.

```java
  @Test
  void rejectsALineWithNoSource() {
    assertThat(Sheets.parseProblems(CsvSheet.parse(List.of(HEADER, "peak_factor,26,ratio,  "))))
        .containsExactly(new SheetProblem.MissingSource(2));
  }
```

A source made only of spaces counts as empty, and the problem `MissingSource(2)` names the line. The sheet rule is "every line says where its number comes from", so a number with no source is refused.

```java
  @Test
  void reportsEveryBadLineNotOnlyTheFirst() {
    List<String> lines = List.of(HEADER, "a,1", "b,x,ratio,y", "c,1,ratio,");

    assertThat(Sheets.parseProblems(CsvSheet.parse(lines))).hasSize(3);
  }
```

Three bad lines (the wrong column count, a value that is not a number, an empty source) give three problems in one run, so the learner can fix the whole sheet at once. `hasSize(3)` checks the number of problems.

### `InputsReaderTest.java`

`InputsReaderTest.java` tests `InputsReader`, the class that turns the list of `SheetEntry` records into a validated `Inputs` object. It checks, for every entry: the quantity is one the program knows (an `InputRule` such as `peak_factor`, or `store.<name>.<field>`); the unit symbol is exactly the one required; the value is in its range; the quantity appears once. After the entries, it checks that every required quantity is present. Every problem is collected, none stops the run. The tests start from the production sheet and break one thing at a time. The line numbers in the expected problems are the real line numbers of `sheets/seatwave-production.csv`: for example line 9 is `response_bytes`. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputsReaderTest {
```

- `ArrayList` is a list that can grow, used where a test adds entries to a copy of the sheet.
- `BigDecimal` builds exact values for added entries.
- The static import is `assertThat` only: the tests here check results with `containsExactly`, not exceptions.

```java
  @Test
  void acceptsTheProductionSheet() {
    Inputs inputs = Sheets.inputsOf(Sheets.PRODUCTION);

    assertThat(inputs.get(InputRule.DAILY_ACTIVE_USERS).toString()).isEqualTo("5,000,000 users");
    assertThat(inputs.stores())
        .extracting(Store::name)
        .containsExactly("bookings", "seats", "logs", "posters");
  }
```

The good case. `Sheets.inputsOf(Sheets.PRODUCTION)` reads the real production sheet. `inputs.get(InputRule.DAILY_ACTIVE_USERS).toString()` is `5,000,000 users`: the value with its unit, printed by `Quantity.toString`. `inputs.stores()` is the list of stores; `extracting(Store::name)` takes each store's name, and `containsExactly("bookings", "seats", "logs", "posters")` checks the four names in the order of the sheet. A **store** is one place data is kept: it has five fields (items per day, bytes per item, retention days, replication factor, index overhead).

```java
  @Test
  void rejectsAResponseSizeWrittenInBits() {
    List<SheetEntry> entries = Sheets.productionWith("response_bytes", "305696", "bits");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.WrongUnit(9, "response_bytes", "bytes", "bits"));
  }
```

`productionWith("response_bytes", "305696", "bits")` changes line 9: the same size, but written in bits (38,212 bytes times 8 is 305,696 bits). The expected problem is `WrongUnit(9, "response_bytes", "bytes", "bits")`: line 9, the quantity, the unit that was expected, the unit that was found. This is the first of the three unit slips of Lab 1. Because only this problem is expected (`containsExactly` with one element), the test also proves that nothing else is reported.

```java
  @Test
  void rejectsASizeWrittenInKilobytesBecauseKilobyteIsAmbiguous() {
    List<SheetEntry> entries = Sheets.productionWith("store.bookings.bytes_per_item", "2", "KB");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(
            new SheetProblem.WrongUnit(12, "store.bookings.bytes_per_item", "bytes", "KB"));
  }
```

A bytes-per-item written as `2` with the unit `KB` is rejected on line 12. `KB` is ambiguous (1,000 or 1,024 bytes), and the program does not convert: the sheet must say `bytes`. The expected problem names the expected unit `bytes` and the found unit `KB`.

```java
  @Test
  void rejectsARateWrittenPerDayWhereOnePerSecondIsExpected() {
    List<SheetEntry> entries = Sheets.productionWith("knee_per_server", "69120000", "requests/day");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(
            new SheetProblem.WrongUnit(35, "knee_per_server", "requests/s", "requests/day"));
  }
```

`knee_per_server` is the number of requests per second one server can take before it slows down. Here it is written as 69,120,000 `requests/day` (800 requests per second times 86,400). The value is the same speed, but the unit is wrong for this input, so line 35 is rejected. A per-day rate where a per-second rate is expected is a mistake of 86,400 times.

```java
  @Test
  void rejectsAShareAboveOneHundredPercent() {
    List<SheetEntry> entries = Sheets.productionWith("write_share", "170", "percent");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.OutOfRange(7, "write_share", "from 0 to 100"));
  }
```

A write share of 170 percent cannot exist. The problem is `OutOfRange(7, "write_share", "from 0 to 100")`: the line, the quantity and the rule in words. A range is checked separately from the unit, and only after the unit is right.

```java
  @Test
  void rejectsAPeakFactorBelowOne() {
    List<SheetEntry> entries = Sheets.productionWith("peak_factor", "0.5", "ratio");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.OutOfRange(6, "peak_factor", "1 or more"));
  }
```

The peak factor is the busiest rate divided by the day's average, so it cannot be below 1. A value of 0.5 gives `OutOfRange(6, "peak_factor", "1 or more")`.

```java
  @Test
  void rejectsZeroUsersAndAHalfSpareServer() {
    List<SheetEntry> entries =
        Sheets.productionWith("daily_active_users", "0", "users").stream()
            .map(entry ->
                entry.quantity().equals("spare_servers")
                    ? entry.withValue(new BigDecimal("0.5"))
                    : entry)
            .toList();

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(
            new SheetProblem.OutOfRange(4, "daily_active_users", "greater than 0"),
            new SheetProblem.OutOfRange(38, "spare_servers", "a whole number, 0 or more"));
  }
```

Two range slips in one sheet. First `productionWith("daily_active_users", "0", "users")` sets users to 0, which breaks "greater than 0". The stream after it changes `spare_servers` to 0.5: `entry.withValue(new BigDecimal("0.5"))` makes a copy of the entry with a new value. A spare server must be a whole number, so 0.5 breaks "a whole number, 0 or more". `containsExactly` with two problems checks that both are reported, in the order of the lines (4 then 38). The test shows that one run lists all the work.

```java
  @Test
  void rejectsAnUnknownQuantity() {
    List<SheetEntry> entries = new ArrayList<>(Sheets.entriesOf(Sheets.PRODUCTION));
    entries.add(new SheetEntry(99, "peak_factr", BigDecimal.TEN, "ratio", "typo"));
    entries.add(new SheetEntry(100, "store.bookings.colour", BigDecimal.ONE, "ratio", "x"));
    entries.add(
        new SheetEntry(101, "store.Bookings.items_per_day", BigDecimal.ONE, "items/day", "x"));
    entries.add(new SheetEntry(102, "store..items_per_day", BigDecimal.ONE, "items/day", "x"));

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(
            new SheetProblem.UnknownQuantity(99, "peak_factr"),
            new SheetProblem.UnknownQuantity(100, "store.bookings.colour"),
            new SheetProblem.UnknownQuantity(101, "store.Bookings.items_per_day"),
            new SheetProblem.UnknownQuantity(102, "store..items_per_day"));
  }
```

Four entries that no rule knows are added to a copy of the sheet: a typo (`peak_factr`), a store field that does not exist (`store.bookings.colour`), a store name with a capital letter (`store.Bookings.items_per_day`; store names are lower case) and a store with an empty name (`store..items_per_day`). `new ArrayList<>(...)` makes a changeable copy, because the list from the helper cannot be changed. Each added line gets a line number from 99 up, so the expected problems can name it. All four are `UnknownQuantity`. The point: a typo in a quantity name must not be silently ignored, or a required input might go unnoticed.

```java
  @Test
  void rejectsTheSameQuantityTwice() {
    List<SheetEntry> entries = new ArrayList<>(Sheets.entriesOf(Sheets.PRODUCTION));
    entries.add(new SheetEntry(99, "peak_factor", BigDecimal.TEN, "ratio", "second guess"));

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.Duplicate(99, "peak_factor", 6));
  }
```

A second `peak_factor` on line 99 gives `Duplicate(99, "peak_factor", 6)`: the repeated line, the quantity, and the line where it was first given. A sheet must not contradict itself.

```java
  @Test
  void rejectsAMissingInput() {
    List<SheetEntry> entries = Sheets.productionWithout("headroom");

    assertThat(Sheets.inputProblems(entries)).containsExactly(new SheetProblem.Missing("headroom"));
  }
```

Without the `headroom` line the problem is `Missing("headroom")`. A missing input has no line number, because there is no line. The reader checks the missing inputs after reading the lines.

```java
  @Test
  void rejectsAStoreWithAMissingField() {
    List<SheetEntry> entries = Sheets.productionWithout("store.logs.retention_days");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.Missing("store.logs.retention_days"));
  }
```

A store needs all five of its fields. Without `store.logs.retention_days`, the problem names exactly that quantity: `Missing("store.logs.retention_days")`.

```java
  @Test
  void rejectsASheetWithNoStore() {
    List<SheetEntry> entries =
        Sheets.entriesOf(Sheets.PRODUCTION).stream()
            .filter(entry -> !entry.quantity().startsWith("store."))
            .toList();

    assertThat(Sheets.inputProblems(entries)).containsExactly(new SheetProblem.NoStore());
  }
```

The stream keeps only the entries whose quantity does not start with `store.`. `startsWith("store.")` is a text check. With no store line at all the problem is `NoStore()`, a problem with no parts. An estimate with no data to store has no storage line, so a sheet must have at least one store.

```java
  @Test
  void rejectsAnEmptyListWithEveryMissingInput() {
    List<SheetProblem> problems = Sheets.inputProblems(List.of());

    assertThat(problems).hasSize(InputRule.values().length + 1);
  }
```

An empty list of entries: every one of the 18 inputs is missing, plus the missing store, so 19 problems. The test writes the expected count as `InputRule.values().length + 1`, so adding a rule to the enum updates the test automatically.

### `RecordGuardsTest.java`

`RecordGuardsTest.java` is the safety net under the safety net. The reader checks a sheet, but the records `Inputs`, `Store`, `Estimate`, `Step`, `SheetEntry` and `SensitivityRow` also check themselves in their **compact constructors** (the code in a record that runs whenever one is created). The class Javadoc says what the file does: "Tries to build every record of the estimate in an invalid state, and expects a refusal." That is the way the architecture guarantees that no code path, not even a test or a future feature, can make an `Inputs` with a wrong unit. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tries to build every record of the estimate in an invalid state, and expects a refusal. */
class RecordGuardsTest {
```

- `EnumMap` is a map (a table from keys to values) whose keys are the constants of one enum; here the keys are `InputRule` constants.
- `Map` is Java's key-to-value table type.
- `assertThatThrownBy` is the static import that checks that code throws (`tests-units-walkthrough.md`).
- The `/** ... */` comment above the class is the sentence quoted above.

```java
  private final Inputs production = Sheets.inputsOf(Sheets.PRODUCTION);
```

One field, `production`: the validated inputs of the production sheet, built once per test object. Each test copies its values and changes one.

```java
  @Test
  void refusesInputsWithAValueInTheWrongUnit() {
    Map<InputRule, Quantity> values = new EnumMap<>(production.values());
    values.put(InputRule.RESPONSE_BYTES, Quantity.of("38212", Unit.GIGABYTES));

    assertThatThrownBy(() -> new Inputs(values, production.stores()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("response_bytes must be greater than 0 bytes");
  }
```

`new EnumMap<>(production.values())` makes a changeable copy of the values. `values.put(InputRule.RESPONSE_BYTES, Quantity.of("38212", Unit.GIGABYTES))` replaces the response size with a number of the right size in the wrong unit (GB, not bytes). Creating `new Inputs(values, production.stores())` must throw an `IllegalArgumentException` whose message contains `response_bytes must be greater than 0 bytes`: the rule's name, its range and the unit it needs. `hasMessageContaining` checks a part of the message.

```java
  @Test
  void refusesInputsWithAValueOutOfRange() {
    Map<InputRule, Quantity> values = new EnumMap<>(production.values());
    values.put(InputRule.UTILIZATION_TARGET, Quantity.of("0", Unit.PERCENT));

    assertThatThrownBy(() -> new Inputs(values, production.stores()))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

A utilization target of 0 percent breaks the range "above 0 and at most 100". The constructor throws `IllegalArgumentException`. A server planned to be used 0 percent would make the server count infinite.

```java
  @Test
  void refusesInputsWithAMissingValue() {
    Map<InputRule, Quantity> values = new EnumMap<>(production.values());
    values.remove(InputRule.PEAK_FACTOR);

    assertThatThrownBy(() -> new Inputs(values, production.stores()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("peak_factor");
  }
```

After `values.remove(InputRule.PEAK_FACTOR)` the map has no peak factor, so `Inputs` throws a `NullPointerException` whose message is exactly `peak_factor`: the name of the missing input. `hasMessage` compares the whole message.

```java
  @Test
  void refusesInputsWithoutStoresOrWithTwoOfOneName() {
    Store bookings = production.stores().getFirst();

    assertThatThrownBy(() -> new Inputs(production.values(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Inputs(production.values(), List.of(bookings, bookings)))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

Two refusals. An empty list of stores is refused. A list holding the same store twice (`List.of(bookings, bookings)`) is refused because two stores must not share a name. `production.stores().getFirst()` takes the first store, `bookings`.

```java
  @Test
  void refusesAStoreWithABadNameOrAMissingField() {
    Store bookings = production.stores().getFirst();
    Map<StoreField, Quantity> fields = new EnumMap<>(bookings.fields());
    fields.remove(StoreField.RETENTION_DAYS);

    assertThatThrownBy(() -> new Store("Bookings!", bookings.fields()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Store("bookings", fields))
        .isInstanceOf(NullPointerException.class);
  }
```

Two refusals of `Store`. The name `"Bookings!"` breaks the name pattern (a lower-case letter, then lower-case letters, digits or underscores), so an `IllegalArgumentException` is thrown. A store made from a copy of the fields without `RETENTION_DAYS` throws a `NullPointerException`, because a field is missing. The fields are copied first so the real store stays unchanged.

```java
  @Test
  void refusesAStoreFieldInTheWrongUnit() {
    Store bookings = production.stores().getFirst();
    Map<StoreField, Quantity> fields = new EnumMap<>(bookings.fields());
    fields.put(StoreField.REPLICATION_FACTOR, Quantity.of("3", Unit.RATIO));

    assertThatThrownBy(() -> new Store("bookings", fields))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bookings.replication_factor must be 1 or more copies");
  }
```

A replication factor of 3 written as a `ratio` instead of `copies` is refused with the message `bookings.replication_factor must be 1 or more copies`. The message names the store, the field, the range and the unit that was needed.

```java
  @Test
  void keepsInputsUnchangedWhenTheCallersMapChanges() {
    Map<InputRule, Quantity> values = new EnumMap<>(production.values());
    Inputs inputs = new Inputs(values, production.stores());

    values.put(InputRule.PEAK_FACTOR, Quantity.of("99", Unit.RATIO));

    assertThat(inputs.get(InputRule.PEAK_FACTOR).amount()).isEqualByComparingTo("26");
  }
```

A **defensive copy**: `Inputs` copies the map it is given. The test builds an `Inputs` from `values`, then changes `values` (peak factor 99), and checks that the `Inputs` still holds 26. Without the copy, a caller could change a validated object after the checks. The data of an estimate must not change under its feet.

```java
  @Test
  void refusesAnEstimateWithTwoStepsOfOneIdAndAnUnknownId() {
    Step step =
        new Step("rate.peak", "RATE", "Peak", "x", Quantity.of("1", Unit.REQUESTS_PER_SECOND));

    assertThatThrownBy(() -> new Estimate(List.of(step, step)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Estimate(List.of(step)).result("rate.average"))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

A `Step` is one line of the arithmetic: an id (such as `rate.peak`), a section (such as `RATE`), a label, the formula as text and the result as a `Quantity`. An `Estimate` holds steps and refuses two with the same id, because a result is looked up by id. Looking up an id that no step has (`rate.average` in an estimate with only `rate.peak`) also throws.

```java
  @Test
  void refusesASheetEntryWithoutALineNumberOrASource() {
    assertThatThrownBy(() -> new SheetEntry(0, "peak_factor", BigDecimal.ONE, "ratio", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SheetEntry(1, "peak_factor", BigDecimal.ONE, "ratio", null))
        .isInstanceOf(NullPointerException.class);
  }
```

Line number 0 is refused (lines start at 1), and a `null` source is refused with a `NullPointerException`. These are the two parts of an entry that every error message depends on.

```java
  @Test
  void refusesAStepOrARowWithMissingParts() {
    assertThatThrownBy(() -> new Step("id", "RATE", "label", "formula", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new SensitivityRow("q", "doubled", "step", null, false))
        .isInstanceOf(NullPointerException.class);
  }
```

A `Step` with no result, and a `SensitivityRow` with no movement, are refused with `NullPointerException`. The null in the fifth and fourth argument is the missing part.

```java
  @Test
  void findsRulesAndFieldsByTheirSheetNames() {
    assertThat(InputRule.withKey("peak_factor")).contains(InputRule.PEAK_FACTOR);
    assertThat(InputRule.withKey("peak")).isEmpty();
    assertThat(StoreField.withKey("retention_days")).contains(StoreField.RETENTION_DAYS);
    assertThat(InputRule.HEADROOM.unit()).isEqualTo(Unit.PERCENT);
    assertThat(StoreField.ITEMS_PER_DAY.unit()).isEqualTo(Unit.ITEMS_PER_DAY);
  }
```

Lookups by name return an `Optional`. `InputRule.withKey("peak_factor")` finds `PEAK_FACTOR`; `withKey("peak")` finds nothing (`isEmpty()`); `StoreField.withKey("retention_days")` finds `RETENTION_DAYS`. The last two lines check that `HEADROOM` is counted in percent and `ITEMS_PER_DAY` in `items/day`. These lookups are how a sheet line becomes a rule.

### `EstimatorTest.java`

`EstimatorTest.java` tests `Estimator`, the class that does the arithmetic. Every number in it is the number printed by the real report, worked out by hand in Module 3. The estimate has seven sections (RATE, SIZE, STORAGE, BANDWIDTH, SERVERS, CACHE, COST), and each step has an id such as `rate.peak`. `production.result("rate.peak")` returns the step's `Quantity`, and `.toString()` prints it with its unit. The hand arithmetic behind the tests is written next to each. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstimatorTest {
```

- `Unit` is imported to check the unit of one result.
- `BigDecimal` is used in the last test.
- `assertThatThrownBy` is used by one test that expects an error.
- Two fields below the class line hold two finished estimates, so each test does not repeat the work.

```java
  private final Estimate production = Estimator.estimate(Sheets.inputsOf(Sheets.PRODUCTION));
  private final Estimate simple = Estimator.estimate(Sheets.inputsOf(Sheets.SIMPLE));
```

Two fields, both built by `Estimator.estimate(Sheets.inputsOf(...))`: `production` is the estimate of the national SeatWave sheet, and `simple` is the estimate of the small one. A field marked `private final` is set once and only visible inside the class.

```java
  @Test
  void turnsFiveMillionUsersIntoAboutTwelveHundredRequestsPerSecondOnAverage() {
    assertThat(production.result("rate.per_day").toString()).isEqualTo("100,000,000 requests/day");
    assertThat(production.result("rate.average").toString()).isEqualTo("1,157 requests/s");
  }
```

5,000,000 users times 20 requests per user per day is 100,000,000 requests per day. Divided by the 86,400 seconds of a day this is 1,157.4 requests per second, printed as `1,157 requests/s`. The first assertion checks `100,000,000 requests/day` (grouped digits, a unit), and the second checks the average. This is the first step of every estimate: from daily numbers to a rate.

```java
  @Test
  void multipliesTheAverageByThePeakFactor() {
    assertThat(production.result("rate.peak").toString()).isEqualTo("30,093 requests/s");
  }
```

The peak rate is the average times the peak factor of 26 (the sale: the sheet counts 1,000,000 fans arriving within about 300 seconds): 1,157.4 times 26 is 30,092.6, printed as `30,093 requests/s`. The estimate sizes servers for this peak, not for the average.

```java
  @Test
  void splitsThePeakIntoReadsAndWrites() {
    assertThat(production.result("rate.reads").toString()).isEqualTo("25,579 requests/s");
    assertThat(production.result("rate.writes").toString()).isEqualTo("4,514 requests/s");
    assertThat(production.result("rate.read_write").toString()).isEqualTo("5.67 reads/write");
  }
```

With a write share of 15 percent, the peak 30,092.6 splits into 85 percent reads (25,579 requests per second) and 15 percent writes (4,514 requests per second). The ratio is 85 divided by 15, which is 5.67 reads for each write, printed `5.67 reads/write`. Reads and writes need different machines later in the course, so the split is an early step.

```java
  @Test
  void leavesOutTheReadWriteRatioWhenNothingIsWritten() {
    List<SheetEntry> entries = Sheets.productionWith("write_share", "0", "percent");

    Estimate allReads = Estimator.estimate(Sheets.inputs(entries));

    assertThatThrownBy(() -> allReads.result("rate.read_write"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(allReads.result("rate.writes").amount()).isEqualByComparingTo("0");
  }
```

With a write share of 0 percent, dividing reads by writes would divide by zero. The estimator then leaves the step `rate.read_write` out. The test asks for it and expects `IllegalArgumentException` ("no step has the id"), and it checks that `rate.writes` is zero. `allReads` is a local name for the estimate where nothing is written. A special case that is handled on purpose instead of crashing or printing infinity.

```java
  @Test
  void addsUpNewDataPerDayAcrossStores() {
    assertThat(production.result("size.bookings").toString()).isEqualTo("1 GB/day");
    assertThat(production.result("size.total").toString()).isEqualTo("101 GB/day");
  }
```

Bookings: 500,000 items a day times 2,000 bytes is 1,000,000,000 bytes, printed `1 GB/day` (a gigabyte is 10 to the power 9 bytes). The total over the four stores is 101 GB per day, of which 100 GB is the logs (100,000,000 lines of 1,000 bytes). The test shows the data is dominated by logs, not by bookings.

```java
  @Test
  void multipliesStorageByCopiesAndIndexOverhead() {
    assertThat(production.result("storage.bookings.raw").toString()).isEqualTo("1.83 TB");
    assertThat(production.result("storage.bookings.stored").toString()).isEqualTo("8.21 TB");
  }
```

Bookings are kept for 1,825 days: 1 GB a day times 1,825 is 1.825 TB, printed `1.83 TB`. With 3 copies (a primary and two replicas) and an index overhead of 1.5 (indexes add half again), the stored size is 1.825 times 3 times 1.5 = 8.21 TB. The test checks both the raw size and the size actually stored.

```java
  @Test
  void keepsLogsOnlyForTheirRetention() {
    assertThat(production.result("storage.logs.days").toString()).isEqualTo("90 days");
    assertThat(production.result("storage.logs.year").toString()).isEqualTo("36.5 TB");
    assertThat(production.result("storage.logs.stored").toString()).isEqualTo("27 TB");
  }
```

The logs are kept 90 days, not for the whole horizon. One year of logs is 100 GB times 365 = 36.5 TB (`36.5 TB`). Kept for 90 days it is 9 TB, and with 3 copies and no index it is 27 TB. The assertions check the days kept (`90 days`), the year figure and the stored figure. Logs are the biggest store per day but not the biggest at the horizon, because they are discarded.

```java
  @Test
  void capsRetentionAtTheHorizon() {
    assertThat(production.result("storage.bookings.days").toString()).isEqualTo("1,825 days");
  }
```

The bookings retention in the sheet is 2,555 days (7 years), but the estimate looks 5 years ahead (1,825 days). The days kept is the smaller of the two: `1,825 days`. Keeping data past the horizon does not belong in an estimate of the horizon.

```java
  @Test
  void addsUpStorageAndPricesIt() {
    assertThat(production.result("storage.total").toString()).isEqualTo("35.72 TB");
    assertThat(production.result("storage.cost").toString()).isEqualTo("821.60 USD/month");
  }
```

The four stores stored at the horizon total 35.72 TB. Priced at 0.023 USD per gigabyte-month (35,720 GB), the monthly cost is 821.60 USD, printed `821.60 USD/month`: money of a dollar or more always shows cents.

```java
  @Test
  void convertsPeakEgressFromBytesIntoBits() {
    assertThat(production.result("bandwidth.egress_peak").unit()).isEqualTo(Unit.BYTES_PER_SECOND);
    assertThat(production.result("bandwidth.egress_peak_gbps").toString()).isEqualTo("9.2 Gbps");
  }
```

**Egress** is data leaving the service. At peak: 30,092.6 requests per second times 38,212 response bytes is about 1.15 billion bytes per second. The test checks that this step is in `BYTES_PER_SECOND` and that the next step, the same figure in gigabits per second, prints `9.2 Gbps`. The estimate keeps bytes and bits as two separate steps, never one number, because the network is sold in bits and the data is measured in bytes.

```java
  @Test
  void estimatesMonthlyEgressAndItsCost() {
    assertThat(production.result("bandwidth.egress_month").toString()).isEqualTo("115 TB/month");
    assertThat(production.result("bandwidth.cost").toString()).isEqualTo("10,317.24 USD/month");
  }
```

Per month: 100,000,000 requests a day times 38,212 bytes times 30 days is 114.6 TB, printed `115 TB/month`. At 0.09 USD per GB (114,636 GB) the cost is 10,317.24 USD a month. Egress is the largest cost of the estimate.

```java
  @Test
  void sizesTheFleetAtTheUtilizationTargetWithHeadroomAndASpare() {
    assertThat(production.result("servers.safe_rate").toString()).isEqualTo("560 requests/s");
    assertThat(production.result("servers.peak").toString()).isEqualTo("66 servers");
    assertThat(production.result("servers.average").toString()).isEqualTo("4 servers");
  }
```

A **knee** is the rate at which one server starts to slow down (800 requests per second here). The plan uses 70 percent of it: 800 times 0.7 is 560 requests per second per server (`servers.safe_rate`). At peak, with 20 percent headroom, the planned rate is 30,092.6 times 1.2 = 36,111, which needs 65 servers (36,111 divided by 560 is 64.5, **rounded up** because half a server does not exist), plus 1 spare, so `66 servers`. At the average rate it is 1,157.4 times 1.2 = 1,388.9, so 3 servers plus 1 spare, `4 servers`.

```java
  @Test
  void pricesTheFleetWithTheModuleOneMonth() {
    assertThat(production.result("servers.peak_hour_cost").toString()).isEqualTo("13.31 USD/hour");
    assertThat(production.result("servers.average_month_cost").amount())
        .isEqualByComparingTo("588.672");
  }
```

At 0.2016 USD per server hour, 66 servers cost 13.3056, printed `13.31 USD/hour`. The average fleet of 4 servers over the 730 hours that a cloud bill counts in a month costs 4 times 0.2016 times 730 = 588.672 USD a month. The second assertion compares the exact amount, `isEqualByComparingTo("588.672")`, because the printed form would round it.

```java
  @Test
  void sizesTheCacheFromTheHotSetNotFromEveryKey() {
    assertThat(production.result("cache.hot_keys").toString()).isEqualTo("20,000 keys");
    assertThat(production.result("cache.hot_memory").toString()).isEqualTo("0.925 GB");
    assertThat(production.result("cache.hot_memory_gib").toString()).isEqualTo("0.8615 GiB");
    assertThat(production.result("cache.full_memory").toString()).isEqualTo("18.5 GB");
  }
```

A **cache** keeps the most used data in memory. There are 400,000 keys in all, and 5 percent are hot (read often): 20,000 hot keys. Each entry is 37,000 bytes and the overhead factor is 1.25, so the hot set needs 20,000 times 37,000 times 1.25 = 925,000,000 bytes, printed `0.925 GB`, which is `0.8615 GiB` in binary units. The memory to cache every key would be `18.5 GB`. The test shows why a cache is sized from the hot set.

```java
  @Test
  void addsTheMonthlyCostOfEachLine() {
    assertThat(production.result("cost.month").toString()).isEqualTo("11,727.51 USD/month");
  }
```

The monthly cost is the storage cost (821.60) plus the egress cost (10,317.24) plus the average fleet (588.672): 11,727.51 USD a month. The peak fleet is not in the sum because it only exists during the sale.

```java
  @Test
  void needsOneServerForTheSimpleSeatWave() {
    assertThat(simple.result("rate.average").toString()).isEqualTo("4.63 requests/s");
    assertThat(simple.result("rate.peak").toString()).isEqualTo("13.89 requests/s");
    assertThat(simple.result("servers.peak").toString()).isEqualTo("1 servers");
  }
```

The small SeatWave has 20,000 daily users with 20 requests each: 400,000 requests a day, 4.63 requests per second on average and 13.89 at peak. With 20 percent headroom the planned peak is 16.67 requests per second, far below the safe rate of 560 per server, so the ceiling is 1 server, and the small sheet adds 0 spare servers. The result prints as `1 servers` (the program does not change the plural).

```java
  @Test
  void givesTheSameEstimateForTheSameInputsEveryTime() {
    Estimate again = Estimator.estimate(Sheets.inputsOf(Sheets.PRODUCTION));

    assertThat(again).isEqualTo(production);
  }
```

**Determinism**: the estimator uses no clock and no randomness. Running it twice on the same inputs gives two `Estimate` records that are `isEqualTo`, because records compare all their parts. The module's rule is that a result can be reproduced from the printed inputs.

```java
  @Test
  void namesEveryStepOnceAndGivesEveryResultAUnit() {
    assertThat(production.steps()).allSatisfy(step -> assertThat(step.result().unit()).isNotNull());
    assertThat(production.steps()).extracting(Step::id).doesNotHaveDuplicates();
  }
```

`allSatisfy` runs a check on every element: each step's result has a unit (not null). `extracting(Step::id).doesNotHaveDuplicates()` checks that no two steps share an id.

```java
  @Test
  void neverStoresLessThanOneCopy() {
    for (Store store : Sheets.inputsOf(Sheets.PRODUCTION).stores()) {
      BigDecimal raw = production.result("storage." + store.name() + ".raw").amount();
      BigDecimal stored = production.result("storage." + store.name() + ".stored").amount();

      assertThat(stored).isGreaterThanOrEqualTo(raw);
    }
  }
```

For each store in the sheet, the stored size must be at least the raw size, since the replication factor and the index overhead are both at least 1. The loop uses `store.name()` to build the step ids (`storage.bookings.raw`). `isGreaterThanOrEqualTo` compares the two `BigDecimal` amounts. This is a **property**: a rule that holds for every store, not one chosen number.

### `SensitivityTest.java`

`SensitivityTest.java` tests `Sensitivity`, the check that doubles each input in turn (or halves it when doubling would leave its range), runs the estimator again, and finds the step that moved most. A result that moves more than 10 times when one input is doubled is **flagged**: a formula that reacts that strongly to a doubling is probably wrong (for example a square in the wrong place). The estimator is passed in as a **function** (`Function<Inputs, Estimate>`), so a test can hand it a deliberately broken formula and see the flag. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SensitivityTest {
```

- `Function` is Java's type for "a function from one type to another"; here from `Inputs` to `Estimate`.
- `Quantity` and `Unit` are used to build the fake estimates of the broken formulas.
- The field below the class line holds the 38 entries of the production sheet.

```java
  private final List<SheetEntry> production = Sheets.entriesOf(Sheets.PRODUCTION);
```

The 38 entries of the production sheet, read once per test object.

```java
  @Test
  void flagsNothingForTheRealEstimator() {
    List<SensitivityRow> rows = Sensitivity.check(production, Estimator::estimate);

    assertThat(EstimateReport.hasFlag(rows)).isFalse();
    assertThat(rows).hasSize(production.size());
  }
```

`Sensitivity.check(production, Estimator::estimate)` returns one row per entry. `Estimator::estimate` is a method reference: the real estimator passed as the function. `EstimateReport.hasFlag(rows)` is false: no real result moves more than 10 times. `hasSize(production.size())` checks there is one row for each of the 38 entries.

```java
  @Test
  void movesThePeakRateTwiceWhenUsersDouble() {
    SensitivityRow users = varied(Sensitivity.check(production, Estimator::estimate), 0);

    assertThat(users.quantity()).isEqualTo("daily_active_users");
    assertThat(users.change()).isEqualTo("doubled");
    assertThat(users.movement().amount()).isEqualByComparingTo("2");
    assertThat(users.mostMovedStep()).isEqualTo("Requests per day");
  }
```

`varied(rows, 0)` takes the row at index 0, the first entry, `daily_active_users`. Doubling it is reported as `"doubled"`. The movement is exactly 2 times, and the step that moved is `Requests per day`, the first step that doubles (later steps double too, but the test reports the first with the largest movement). `users.movement().amount()` is the `BigDecimal` inside the movement `Quantity`.

```java
  @Test
  void halvesAShareThatCannotDouble() {
    List<SheetEntry> entries = Sheets.productionWith("utilization_target", "70", "percent");

    SensitivityRow utilization = varied(Sensitivity.check(entries, Estimator::estimate), 32);

    assertThat(utilization.quantity()).isEqualTo("utilization_target");
    assertThat(utilization.change()).isEqualTo("halved");
  }
```

A utilization target of 70 percent cannot be doubled (140 percent is impossible), so the check halves it. The test sets the entry to 70 explicitly with `productionWith`, then takes the row at index 32, which is `utilization_target`, and checks that its change is `"halved"`. This is why `Sensitivity` never fails on a share near its limit.

```java
  @Test
  void flagsAFormulaThatGrowsFasterThanItsInputs() {
    // A deliberate bug: users to the fourth power, so doubling users moves the result 16 times.
    Function<Inputs, Estimate> squaring =
        inputs -> {
          Quantity users = inputs.get(InputRule.DAILY_ACTIVE_USERS);
          Quantity squared = users.scaledBy(users.amount().pow(3));
          return new Estimate(List.of(new Step("rate.peak", "RATE", "Peak rate", "bug", squared)));
        };

    List<SensitivityRow> rows = Sensitivity.check(production, squaring);

    assertThat(varied(rows, 0).isFlagged()).isTrue();
    assertThat(varied(rows, 0).movement().amount()).isEqualByComparingTo("16");
    assertThat(EstimateReport.hasFlag(rows)).isTrue();
  }
```

The comment says it: a deliberate bug. The function `squaring` builds a fake estimate with one step whose result is users times users cubed, so users to the fourth power. `inputs -> { ... }` is a lambda with a body in braces. Doubling users multiplies the result by 2 to the power 4, 16. The test checks that the row is flagged, that the movement is 16, and that `hasFlag` is true. This proves that the check can see a bug at all. A check that has never been seen to fail proves nothing. (The local name `squaring` says "squaring", but the code raises to the fourth power; the comment states the real meaning.)

```java
  @Test
  void flagsAResultThatShrinksToLessThanATenth() {
    Function<Inputs, Estimate> collapsing =
        inputs -> {
          BigDecimal users = inputs.get(InputRule.DAILY_ACTIVE_USERS).amount();
          BigDecimal shrinking = BigDecimal.ONE.divide(users.pow(4), Quantity.PRECISION);
          Quantity result = new Quantity(shrinking, Unit.RATIO);
          return new Estimate(List.of(new Step("odd", "RATE", "Odd", "bug", result)));
        };

    assertThat(varied(Sensitivity.check(production, collapsing), 0).isFlagged()).isTrue();
  }
```

The opposite bug: a result that is one divided by users to the fourth power. Doubling users shrinks it 16 times, to 1/16. `BigDecimal.ONE.divide(users.pow(4), Quantity.PRECISION)` computes it exactly. Movement is measured in both directions, so a shrink below a tenth is flagged like a growth above 10 times.

```java
  @Test
  void skipsResultsThatAreZero() {
    List<SheetEntry> entries = Sheets.productionWith("write_share", "0", "percent");

    List<SensitivityRow> rows = Sensitivity.check(entries, Estimator::estimate);

    assertThat(EstimateReport.hasFlag(rows)).isFalse();
  }
```

With a write share of 0 percent, the step `rate.writes` is zero. A ratio against zero is undefined, so the check skips any step whose baseline or changed result is zero instead of counting an infinite move. The test runs the real estimator on this sheet and checks that no row is flagged. (The coverage report shows the `continue` line of `Sensitivity` as not run, although this case is skipped: the compiled code jumps straight to the next step when the changed result is zero, so the `continue` instruction itself is reached only for a step that is zero in the baseline and not zero after the change, which no input can cause.)

```java
  @Test
  void rejectsASheetThatIsNotValid() {
    List<SheetEntry> broken = Sheets.productionWithout("peak_factor");

    assertThatThrownBy(() -> Sensitivity.check(broken, Estimator::estimate))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

A sheet without `peak_factor` cannot be estimated, so `Sensitivity.check` throws `IllegalArgumentException`. It must be given a valid sheet; checking a broken one would be meaningless.

```java
  private static SensitivityRow varied(List<SensitivityRow> rows, int index) {
    return rows.get(index);
  }
```

A helper at the bottom of the class: it returns the row at a given index. `private static` means that only this class can use it, and it needs no object. The tests call it to keep the assertions short.

### `EstimateReportTest.java`

`EstimateReportTest.java` tests `EstimateReport`, which renders the whole printout: the inputs as a sheet, the steps, the five-line estimate and the sensitivity rows. The three fields at the top build the report once from the production sheet. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EstimateReportTest {
```

- `Pattern` and `Matcher` are Java's tools for **regular expressions**: patterns that describe text. A `Matcher` tries a pattern against a text.
- `Arrays` turns the array of all units into a stream.
- The comment above `STEP_RESULT` explains the pattern: a step line ends with `= <number> <unit>`, so the last word after the number is the unit.

```java
  // A step line ends with "= <number> <unit>", so the last token after the number is the unit.
  private static final Pattern STEP_RESULT = Pattern.compile(".* = [0-9][0-9,.]* (\\S+)$");
```

The regular expression is `.* = [0-9][0-9,.]* (\S+)$`. In the Java text every backslash is written twice (`\\S`), because a backslash is also Java's own escape character. Read it as: any text, then ` = `, then a number made of digits, commas and dots, then a space, then one or more characters that are not spaces (`\S+`), then the end of the line (`$`). The part in round brackets is a **group** that captures the unit.

```java
  private final List<SheetEntry> entries = Sheets.entriesOf(Sheets.PRODUCTION);
  private final Estimate estimate = Estimator.estimate(Sheets.inputs(entries));
  private final String report =
      EstimateReport.render(entries, estimate, Sensitivity.check(entries, Estimator::estimate));
```

Three fields: the entries of the production sheet, the estimate built from them, and the finished report text. `EstimateReport.render(entries, estimate, rows)` returns one long string with line breaks.

```java
  @Test
  void reproducesTheSameEstimateFromThePrintedInputs() {
    List<String> printedSheet = report.lines().skip(1).takeWhile(line -> !line.isBlank()).toList();

    Estimate fromPrintout =
        Estimator.estimate(Sheets.inputs(Sheets.entries(CsvSheet.parse(printedSheet))));

    assertThat(fromPrintout).isEqualTo(estimate);
  }
```

The key reproducibility test. `report.lines()` is a stream of the report's lines. `.skip(1)` drops the first line (the title `INPUTS (a complete sheet: ...)`). `.takeWhile(line -> !line.isBlank())` keeps lines until the first blank one, which is the end of the printed sheet. The kept lines are then parsed as a sheet, turned into inputs and estimated again. The new estimate must be `isEqualTo` the original. So a learner who saves the printed inputs as a `.csv` file and runs them again gets the same numbers.

```java
  @Test
  void endsEveryStepWithAKnownUnit() {
    List<String> stepLines =
        report
            .lines()
            .filter(
                line -> line.startsWith("  ") && !line.startsWith("  =") && line.contains(" = "))
            .toList();

    assertThat(stepLines).hasSize(estimate.steps().size());
    for (String line : stepLines) {
      Matcher matcher = STEP_RESULT.matcher(line);
      assertThat(matcher.matches()).as(line).isTrue();
      String lastWord = line.substring(line.lastIndexOf(' ') + 1);
      assertThat(Arrays.stream(Unit.values()).map(Unit::symbol)).as(line).contains(lastWord);
    }
  }
```

The step lines of the report (the lines that start with two spaces, do not start with `  =` and contain ` = `) must be exactly as many as the estimate's steps (`hasSize`). For each line, the regular expression must match (`as(line)` puts the line in the failure message), and the last word of the line, `line.substring(line.lastIndexOf(' ') + 1)`, must be the symbol of one of the 33 units. This checks the invariant of the module on the real report: every step ends with a unit.

```java
  @Test
  void neverPrintsBitsAndBytesInOneResult() {
    assertThat(report.lines().filter(line -> line.endsWith("Gbps")))
        .allSatisfy(line -> assertThat(line).doesNotEndWith("bytes/s"));
    assertThat(estimate.result("bandwidth.egress_peak_gbps").unit())
        .isNotEqualTo(estimate.result("bandwidth.egress_peak").unit());
  }
```

Lines that end in `Gbps` must not end in `bytes/s`, and the egress step in bytes per second and the one in Gbps are different units. The second assertion is the stronger one: the two steps do not share a unit. (The first assertion cannot fail by construction, because a line cannot end with both texts; it documents the intent.)

```java
  @Test
  void printsTheFiveLinesInOrder() {
    int rate = report.indexOf("1. RATE");
    int size = report.indexOf("2. SIZE");
    int storage = report.indexOf("3. STORAGE");
    int bandwidth = report.indexOf("4. BANDWIDTH");
    int servers = report.indexOf("5. SERVERS");

    assertThat(List.of(rate, size, storage, bandwidth, servers)).isSorted().doesNotContain(-1);
  }
```

The five lines of the estimate print in the order RATE, SIZE, STORAGE, BANDWIDTH, SERVERS. `report.indexOf("1. RATE")` gives the position of the text in the report, or -1 if it is not there. `isSorted()` checks that the positions grow, and `doesNotContain(-1)` checks that every line was found.

```java
  @Test
  void saysWhenNothingWasFlagged() {
    assertThat(report).contains("No result moved more than 10 times when any one input doubled.");
  }
```

With no flagged rows the report says: "No result moved more than 10 times when any one input doubled." A clean run states its result instead of staying silent.

```java
  @Test
  void marksAFlaggedRow() {
    List<SensitivityRow> flagged =
        List.of(
            new SensitivityRow(
                "daily_active_users", "doubled", "Peak rate", Quantity.of("8", Unit.TIMES), true));

    String flaggedReport = EstimateReport.render(entries, estimate, flagged);

    assertThat(flaggedReport)
        .contains("<-- FLAGGED")
        .contains("FLAGGED: a result moved more than 10 times");
  }
```

A hand-made `SensitivityRow` with `true` for flagged is given to `render`. The report must show the marker `<-- FLAGGED` and the sentence `FLAGGED: a result moved more than 10 times`. `.contains(...).contains(...)` chains two checks. This test covers the flagged branch, which the real production sheet never triggers.

### `EstimateCliTest.java`

`EstimateCliTest.java` tests only the part of the command-line class that can be tested without starting a program: the static method `EstimateCli.explain`, which turns a `SheetProblem` into the sentence the learner reads. The `main` method calls `System.exit`, which would stop the test run itself, so it is not tested (the README and `jacoco.md` list it as the untested remainder). The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EstimateCliTest {
```

One static import, `assertThat`, and the `@Test` label. The class has a single test.

```java
  @Test
  void explainsEveryKindOfSheetProblemWithItsLine() {
    assertThat(EstimateCli.explain(new SheetProblem.WrongHeader("a,b")))
        .isEqualTo("header: expected 'quantity,value,unit,source', found 'a,b'");
    assertThat(EstimateCli.explain(new SheetProblem.WrongColumnCount(4))).startsWith("line 4:");
    assertThat(EstimateCli.explain(new SheetProblem.NotANumber(5, "lots")))
        .isEqualTo("line 5: 'lots' is not a plain decimal number");
    assertThat(EstimateCli.explain(new SheetProblem.MissingSource(6))).contains("source");
    assertThat(EstimateCli.explain(new SheetProblem.UnknownQuantity(7, "peak")))
        .isEqualTo("line 7: 'peak' is not an input");
    assertThat(
            EstimateCli.explain(new SheetProblem.WrongUnit(9, "response_bytes", "bytes", "bits")))
        .isEqualTo("line 9: response_bytes must be in bytes, not bits");
    assertThat(EstimateCli.explain(new SheetProblem.OutOfRange(7, "write_share", "from 0 to 100")))
        .isEqualTo("line 7: write_share must be from 0 to 100");
    assertThat(EstimateCli.explain(new SheetProblem.Duplicate(99, "peak_factor", 6)))
        .isEqualTo("line 99: peak_factor was already given on line 6");
    assertThat(EstimateCli.explain(new SheetProblem.Missing("headroom")))
        .isEqualTo("missing input: headroom");
    assertThat(EstimateCli.explain(new SheetProblem.NoStore())).contains("store.");
  }
```

One assertion per kind of problem, ten in all: the wrong header, wrong column count, not a number, missing source, unknown quantity, wrong unit, out of range, duplicate, missing input and no store. Most use `isEqualTo` with the full sentence, for example `line 9: response_bytes must be in bytes, not bits`. A few use `startsWith` or `contains` for a part of the sentence: `startsWith("line 4:")` checks only that the sentence starts with the line number, and `contains("store.")` checks that the no-store sentence mentions the `store.` prefix. The messages matter because they are the whole user interface of a rejected sheet: the learner fixes the sheet from these lines. Two long `assertThat(...)` calls are wrapped over several lines by the formatter.

### `ShippedSheetsTest.java`

`ShippedSheetsTest.java` runs the real files in `sheets/`. The other tests build their input from the production sheet; these four tests prove that each file that the course hands out does what the course says it does. The block below is the top of the file.

```java
package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShippedSheetsTest {
```

`Path` and `List` are imported for the loop of the last test. There is no `BigDecimal` here: amounts are compared as text.

```java
  @Test
  void acceptsTheSimpleAndProductionSheets() {
    assertThat(Sheets.inputsOf(Sheets.SIMPLE).stores()).hasSize(2);
    assertThat(Sheets.inputsOf(Sheets.PRODUCTION).stores()).hasSize(4);
  }
```

The simple sheet has 2 stores and the production sheet has 4. If either file stopped passing the rules, `inputsOf` would fail the test with the list of problems.

```java
  @Test
  void rejectsTheLabOneSheetForEachOfItsSlips() {
    List<SheetProblem> problems = Sheets.inputProblems(Sheets.entriesOf(Sheets.LAB1_UNIT_SLIPS));

    assertThat(problems)
        .containsExactly(
            new SheetProblem.WrongUnit(9, "response_bytes", "bytes", "bits"),
            new SheetProblem.WrongUnit(12, "store.bookings.bytes_per_item", "bytes", "KB"),
            new SheetProblem.WrongUnit(35, "knee_per_server", "requests/s", "requests/day"),
            new SheetProblem.Missing("headroom"));
  }
```

`lab1-unit-slips.csv` is the production sheet with three unit slips and one missing line, made for Lab 1. The test lists the four problems the learner must find: line 9 (bits), line 12 (KB), line 35 (per day) and the missing `headroom`. `containsExactly` also fixes their order. The README prints exactly these four lines.

```java
  @Test
  void findsTheFactorOfOneThousandInTheGivenPhotoEstimate() {
    Estimate solution = Estimator.estimate(Sheets.inputsOf(Sheets.PHOTO_SHARING));
    Estimate given = Estimator.estimate(Sheets.inputsOf(Sheets.PHOTO_SHARING_GIVEN));

    assertThat(
            solution
                .result("storage.photos.stored")
                .timesAsMany(given.result("storage.photos.stored"))
                .amount())
        .isEqualByComparingTo("1000");
    assertThat(given.result("rate.peak")).isEqualTo(solution.result("rate.peak"));
  }
```

Lab 3 gives the learner a photo-sharing estimate with one factor-of-1,000 mistake: a photo is `2_000` bytes in the given sheet (labeled "2,000 KB") and `2_000_000` bytes in the solution. The test estimates both. `solution.result("storage.photos.stored").timesAsMany(given.result(...))` asks how many times larger the correct storage is: exactly 1,000. The second assertion shows that the peak rate is the same in both, so the mistake lives only in the storage.

```java
  @Test
  void flagsNoResultInAnyShippedSheet() {
    for (Path sheet :
        List.of(
            Sheets.SIMPLE, Sheets.PRODUCTION, Sheets.PHOTO_SHARING, Sheets.PHOTO_SHARING_GIVEN)) {
      List<SensitivityRow> rows = Sensitivity.check(Sheets.entriesOf(sheet), Estimator::estimate);

      assertThat(EstimateReport.hasFlag(rows)).as(sheet.toString()).isFalse();
    }
  }
```

A `for` loop over the four sheets that are valid. For each, `Sensitivity.check(...)` must flag nothing. `as(sheet.toString())` labels a failure with the sheet's name. This guards the shipped numbers: a learner who opens any of them never sees a flagged row.

## Try it yourself

1. Run the estimate package's tests (the filter is the full package name, because the word `estimate` alone would also match the package `estimates` that every test lives in):

```
./gradlew cleanTest test --tests 'io.plainround.seatwave.estimates.estimate.*'
```

   Expected: 72 lines that end in `PASSED` and `BUILD SUCCESSFUL`.

2. Count the tests per class, right after step 1:

```
grep -o 'tests="[0-9]*"' build/test-results/test/TEST-io.plainround.seatwave.estimates.estimate.*.xml
```

   Expected: eight lines, with the counts 11 (`CsvSheetTest`), 1 (`EstimateCliTest`), 6 (`EstimateReportTest`), 19 (`EstimatorTest`), 13 (`InputsReaderTest`), 11 (`RecordGuardsTest`), 7 (`SensitivityTest`) and 4 (`ShippedSheetsTest`), in an order that can vary.

3. Run one test class:

```
./gradlew cleanTest test --tests '*InputsReaderTest'
```

   Expected: 13 `PASSED` lines.

4. See the three unit slips and the missing line that `ShippedSheetsTest` expects, from the real program:

```
./gradlew -q estimate --args='sheets/lab1-unit-slips.csv'
```

   Expected: `REJECTED line 9: response_bytes must be in bytes, not bits`, `REJECTED line 12: store.bookings.bytes_per_item must be in bytes, not KB`, `REJECTED line 35: knee_per_server must be in requests/s, not requests/day`, `REJECTED missing input: headroom` and `SHEET REJECTED: fix every line above; nothing was estimated.`, then Gradle's `finished with non-zero exit value 1`.

5. Read a number that `EstimatorTest` pins, from the real report:

```
./gradlew -q estimate --args='sheets/seatwave-production.csv' | grep "Peak rate:"
```

   Expected: `  Peak rate: 1,157 requests/s x 26 ratio = 30,093 requests/s`.

## Mistakes people make

- **Testing with a made-up sheet.** The tests start from the real production sheet and change one line, so every expected problem has a real line number that a learner can open.
- **Checking only that an error happens.** Most tests check the whole problem (line, quantity, expected and found), so a vague error would fail.
- **Letting a check pass without ever seeing it fail.** `SensitivityTest` hands the check two deliberately broken formulas to prove it can fail.
- **Printing a number without a unit in a new step.** `endsEveryStepWithAKnownUnit` catches it.
- **Using `==` or `isEqualTo` on `BigDecimal`.** Amounts are compared with `isEqualByComparingTo`.
- **Relying on the working directory.** The tests read `sheets/...` as a relative path, so they only work when started from the project folder, which Gradle does.

## Check yourself

1. *Why does `InputsReaderTest` expect `WrongUnit(9, "response_bytes", "bytes", "bits")` and not just an exception?* The program never throws for a bad sheet. It returns every problem with its line, and the test checks the whole problem.
2. *What does `Sheets.productionWith("write_share", "0", "percent")` return?* The entries of the production sheet where the line `write_share` has the value 0 and the unit `percent`.
3. *Why does `leavesOutTheReadWriteRatioWhenNothingIsWritten` exist?* Dividing reads by zero writes has no answer, so the estimator leaves that step out, and the test proves it.
4. *How does the test of the report prove that the printed inputs can be run again?* It parses the first part of the report as a sheet, estimates from it, and compares with the first estimate.
5. *Why does `SensitivityTest` use a function parameter?* So a test can pass a deliberately broken formula and check that it is flagged.

## Further reading

- JUnit 6.1.3 user guide: <https://docs.junit.org/6.1.3/overview.html>
- AssertJ 3.27.7 documentation: <https://assertj.github.io/doc/>
- Java records and sealed interfaces: <https://docs.oracle.com/en/java/javase/25/language/records.html> and <https://docs.oracle.com/en/java/javase/25/language/sealed-classes-and-interfaces.html>
- Java pattern matching for `switch`: <https://docs.oracle.com/en/java/javase/25/language/pattern-matching.html>
- Java regular expressions (`Pattern`): <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html>
