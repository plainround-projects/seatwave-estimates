# Java walkthrough: the `estimate` package

## What it is

This file reads, one source file at a time, the Java package `io.plainround.seatwave.estimates.estimate`. A **package** is a named folder of related Java files. This one reads an estimation sheet (a CSV file of assumptions), checks every number against a rule, computes the five-line estimate step by step, tests how sensitive the result is to each input, and prints a report. Sixteen files make up the package.

Read `java.md` first. It explains the Java words used here (class, record, enum, sealed interface, `switch` with patterns, `Optional`, streams, `BigDecimal`, exceptions) from zero. Read `java-units-walkthrough.md` second, because every number in this package is a `Quantity`. This file does not repeat those explanations in full. It quotes each line and says what the line does in this program. `result4j.md` explains the `Result` type that several files use.

## Why this repository uses it

Module 3 asks the learner to turn business numbers (daily users, requests per user, bytes per record) into the five-line estimate: rate, size, storage, bandwidth and servers, plus the memory for a cache. The estimate is only useful if a reader can check it. So this package does three things a spreadsheet does not do by itself. It refuses a sheet whose numbers are written in the wrong unit. It prints every step of the arithmetic with its unit. And it doubles each input in turn to find a formula that grows faster than its input, which in an estimate is almost always a mistake.

## Where it appears

| File | What it does |
|---|---|
| `CsvSheet.java` | Reads the lines of a sheet into entries, and writes entries back as lines. |
| `SheetEntry.java` | One data line of a sheet, read but not yet checked. |
| `SheetProblem.java` | The ten reasons a sheet can be refused, as a sealed interface of records. |
| `Range.java` | The six value ranges an input may have, each with a check and a description. |
| `InputRule.java` | One rule per input: its name, its one unit and its range. |
| `StoreField.java` | The five numbers each kind of stored data needs, as an enum with the same shape. |
| `Inputs.java` | The checked inputs of one estimate, in a record that cannot exist with a wrong value. |
| `Store.java` | One kind of stored data, with all five fields, in a record. |
| `InputsReader.java` | Turns entries into `Inputs`, collecting every problem instead of stopping at the first. |
| `Step.java` | One line of visible arithmetic. |
| `Estimate.java` | The list of steps of one estimate, with a lookup by id. |
| `Estimator.java` | The formulas: rate, size, storage, bandwidth, servers, cache and cost. |
| `Sensitivity.java` | Doubles each input in turn and finds the result that moved most. |
| `SensitivityRow.java` | One row of the sensitivity check. |
| `EstimateReport.java` | Builds the text report. |
| `EstimateCli.java` | The program you start from the terminal. It reads a file, prints, and exits with a status. |

## How it works underneath

The flow is one line, left to right. It runs in one **process** (one running copy of the Java program), in memory, apart from the one file it reads.

1. `EstimateCli.main` receives the path of a sheet from the terminal and reads the file as lines of text.
2. `CsvSheet.parse` turns the lines into a list of `SheetEntry` values, or into a list of `SheetProblem` values.
3. `InputsReader.read` matches each entry to a rule, checks its unit and range, and either builds `Inputs` or returns every problem.
4. `Estimator.estimate` computes about fifty steps from the inputs.
5. `Sensitivity.check` changes each entry in turn, rebuilds the inputs through the same reader, estimates again and compares the steps.
6. `EstimateReport.render` builds one string. `EstimateCli.main` prints it and exits.

**A CSV file** is a text file of rows. Each row is one line, and its cells are separated by commas. "CSV" means comma-separated values. A spreadsheet program opens such a file as a table. `csv-sheets.md` explains the format of the five sheets.

**A step.** The word step is used for one line of arithmetic: an id, a section, a label, a formula in words and a result. The report prints about fifty of them. Each result is a `Quantity`, so it has a unit.

**Expected failure and programmer error.** The package uses two kinds of failure. A person who types a wrong unit in a sheet causes an **expected failure**. It is returned as a value (`SheetProblem` inside a `Result`) so the program can list every problem together. A **programmer error** (a null, a formula that adds bytes to GB, a record built with a value it forbids) throws an exception, because only a bug causes it. `result4j.md` draws the line.

## Walkthrough of this repository's use

### `CsvSheet.java`

**Lines 1 to 19: package, imports and the class comment**

```java
package io.plainround.seatwave.estimates.estimate;

import com.github.sviperll.result4j.Result;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads and writes the estimation sheet: a CSV file with the columns quantity, value, unit and
 * source, which any spreadsheet program opens.
 *
 * <p>Lines that start with {@code #} and blank lines are comments. The source column is last, so it
 * may contain commas; like any spreadsheet export, a source with a comma is written in double
 * quotes. A value is a plain decimal number; underscores may group digits, as in Java, so 5_000_000
 * is five million. Stateless and safe to use from any thread.
 */
public final class CsvSheet {
```

- `package ...estimate;` names the package. `import` lines let the file use short names for classes from other places. `Result` is the type of the library result4j: a value that is either a success or an error. `BigDecimal` holds exact decimal numbers. `ArrayList` and `List` are growable lists. `Objects` has null checks. `Pattern` is the regular-expression class: a **regular expression** is a short text that describes the shape of other text, and a `Pattern` is the compiled form of one.
- The Javadoc says what the class is for: reading and writing a CSV sheet. A line that starts with `#` is a comment. A source with a comma in it is written in double quotes, as a spreadsheet does when it exports a CSV. A value is a plain decimal number, and underscores may group digits, so `5_000_000` means five million. `{@code #}` marks code in documentation.
- "Stateless and safe to use from any thread" means the class keeps no data between calls, so two threads (two lines of execution inside the process) can call it at the same time.

**Lines 19 to 31: the class and its constants**

```java
public final class CsvSheet {

  /** The header every sheet starts with, after any comments. */
  public static final String HEADER = "quantity,value,unit,source";

  private static final String COMMENT_PREFIX = "#";
  private static final String COLUMN_SEPARATOR = ",";
  private static final int COLUMNS = 4;
  private static final Pattern PLAIN_DECIMAL = Pattern.compile("[0-9]+(\\.[0-9]+)?");
  private static final String DIGIT_GROUP_SEPARATOR = "_";
  private static final String QUOTE = "\"";

  private CsvSheet() {}
```

- `public final class CsvSheet` declares a class that no other class may extend (`final`).
- `HEADER` is the first line every sheet must have after its comments: `quantity,value,unit,source`. `public static final` makes it a constant that other classes read as `CsvSheet.HEADER`. The error message and the writer both use it.
- `COMMENT_PREFIX` is the text that starts a comment line. `COLUMN_SEPARATOR` is the comma. `COLUMNS = 4` is the number of columns. Named constants stand in for numbers and text the code would otherwise repeat.
- `PLAIN_DECIMAL = Pattern.compile("[0-9]+(\\.[0-9]+)?")` is the shape of an allowed value. Read it left to right. `[0-9]` is one digit from 0 to 9. `+` means one or more of the thing before it. `(\\.[0-9]+)?` is an optional group: a dot followed by one or more digits. In Java text, a backslash must be written twice, so `\\.` is the regular expression `\.`, which means a literal dot (a bare `.` would match any character). So `38212` and `1.5` match. `-5`, `1e3`, `1,5` and `.5` do not. The pattern allows no sign, so a negative number is refused at this first step.
- `DIGIT_GROUP_SEPARATOR` is the underscore. `QUOTE` is one double quote character. In Java text, a quote inside text is written `\"`.
- `private CsvSheet() {}` is a **constructor** that is private and empty. Nobody outside can write `new CsvSheet()`. A class of only static methods uses this to say "do not make objects of me".

**Lines 33 to 64: `parse`**

```java
  /**
   * Reads the lines of a sheet.
   *
   * @param lines the file's lines, in order
   * @return every data line as an entry, or every problem found; one bad line rejects the sheet
   * @throws NullPointerException if the list or a line is null
   */
  public static Result<List<SheetEntry>, List<SheetProblem>> parse(List<String> lines) {
    Objects.requireNonNull(lines, "lines");
    List<SheetEntry> entries = new ArrayList<>();
    List<SheetProblem> problems = new ArrayList<>();
    boolean isHeaderSeen = false;
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      String line = lines.get(lineIndex).strip();
      if (line.isEmpty() || line.startsWith(COMMENT_PREFIX)) {
        continue;
      }
      if (!isHeaderSeen) {
        isHeaderSeen = true;
        if (!HEADER.equals(line)) {
          return Result.error(List.of(new SheetProblem.WrongHeader(line)));
        }
        continue;
      }
      readLine(lineIndex + 1, line, entries, problems);
    }
    if (!isHeaderSeen) {
      return Result.error(List.of(new SheetProblem.WrongHeader("")));
    }
    return problems.isEmpty() ? Result.success(List.copyOf(entries)) : Result.error(problems);
  }

```

- The method is `public static`: any code calls it as `CsvSheet.parse(lines)`. Its return type is `Result<List<SheetEntry>, List<SheetProblem>>`. It is either a success holding a list of entries, or an error holding a list of problems. The two angle-bracket parts are **generics**: they tell the compiler what the `Result` holds.
- Line 41 rejects a null list, with the message `lines`.
- Lines 42 and 43 make two empty lists. `new ArrayList<>()` uses the **diamond** `<>`: Java fills in the type from the left side. Entries collect good lines. Problems collect bad ones. The method never stops at the first bad line, so one run lists all the work.
- `boolean isHeaderSeen = false;` remembers whether the header line has been passed.
- The `for` loop on line 45 counts `lineIndex` from 0 up to the last line. `lines.get(lineIndex).strip()` takes the line and removes spaces at both ends.
- Lines 47 to 49: a blank line or a comment line is skipped. `continue` jumps to the next turn of the loop.
- Lines 50 to 56: the first line that is not a comment must be the header. `isHeaderSeen = true` marks it. `HEADER.equals(line)` compares the text. If it differs, the method returns at once with `Result.error(List.of(new SheetProblem.WrongHeader(line)))`: an error with one problem. `List.of(x)` makes an unmodifiable list of one item. If it matches, `continue` goes on to the next line.
- Line 57: every other line is a data line. `readLine(lineIndex + 1, line, entries, problems)` handles it. The first argument is `lineIndex + 1` because arrays start at 0 and people count lines from 1. The method adds to the two lists it is given. This is why the sheet's line numbers in error messages match the numbers an editor shows.
- Lines 59 to 61: a sheet with no header at all (empty, or only comments) returns an error with `WrongHeader("")`.
- Line 62: `problems.isEmpty() ? Result.success(List.copyOf(entries)) : Result.error(problems)` is a **conditional expression**: `condition ? a : b` gives `a` when the condition is true and `b` otherwise. If there are no problems, the result is a success holding an unmodifiable copy of the entries. Otherwise it is an error holding the problems. `List.copyOf` makes an unmodifiable copy, so no caller can change the list later.
- "One bad line rejects the sheet": a sheet with a single problem returns no entries at all.

**Lines 65 to 84: `render`**

```java
  /**
   * Writes entries back as sheet lines, so a printed estimate can be read in again unchanged.
   *
   * @param entries the entries to write
   * @return the header followed by one line per entry
   */
  public static List<String> render(List<SheetEntry> entries) {
    List<String> lines = new ArrayList<>();
    lines.add(HEADER);
    for (SheetEntry entry : entries) {
      lines.add(
          String.join(
              COLUMN_SEPARATOR,
              entry.quantity(),
              entry.value().toPlainString(),
              entry.unit(),
              quoted(entry.source())));
    }
    return List.copyOf(lines);
  }
```

- `render` is the reverse of `parse`. It writes entries back as lines, so the report can print the inputs as a valid sheet that anyone can save and run again.
- Line 73 adds the header first. The loop on line 74 visits each entry.
- `String.join(COLUMN_SEPARATOR, a, b, c, d)` glues four texts with commas between them. The four are the quantity name, the value, the unit, and the source (after `quoted`). `entry.value().toPlainString()` writes the number in plain decimal form, never in scientific form such as `5E+6`. That is why a report prints `5000000` where the sheet had `5_000_000`.
- `List.copyOf(lines)` returns an unmodifiable list.

**Lines 86 to 95: `unquoted` and `quoted`**

```java
  // A spreadsheet writes a cell that holds a comma inside double quotes, and doubles any quote.
  private static String unquoted(String cell) {
    boolean isQuoted = cell.length() >= 2 && cell.startsWith(QUOTE) && cell.endsWith(QUOTE);
    return isQuoted ? cell.substring(1, cell.length() - 1).replace(QUOTE + QUOTE, QUOTE) : cell;
  }

  private static String quoted(String cell) {
    boolean needsQuotes = cell.contains(COLUMN_SEPARATOR) || cell.contains(QUOTE);
    return needsQuotes ? QUOTE + cell.replace(QUOTE, QUOTE + QUOTE) + QUOTE : cell;
  }
```

- A spreadsheet writes a cell that holds a comma inside double quotes, and doubles any quote inside it. A source such as `Module 1: 20 requests per visit, 5 of them reads` would otherwise break into two columns. `quoted` does this when a cell contains a comma or a quote. `unquoted` does the reverse when reading.
- `cell.length() >= 2 && cell.startsWith(QUOTE) && cell.endsWith(QUOTE)` is true when the cell starts and ends with a quote and is at least two characters long. `&&` means "and". `cell.substring(1, cell.length() - 1)` removes the first and last character. `.replace(QUOTE + QUOTE, QUOTE)` turns each doubled quote back into one quote.
- `quoted` reverses it: `cell.replace(QUOTE, QUOTE + QUOTE)` doubles the quotes, and the result is wrapped in a quote on each side. A cell with no comma and no quote is returned unchanged.

**Lines 97 to 119: `readLine`**

```java
  private static void readLine(
      int lineNumber, String line, List<SheetEntry> entries, List<SheetProblem> problems) {
    // The limit keeps every comma after the third one inside the source column.
    String[] columns = line.split(COLUMN_SEPARATOR, COLUMNS);
    if (columns.length != COLUMNS) {
      problems.add(new SheetProblem.WrongColumnCount(lineNumber));
      return;
    }
    String valueText = columns[1].strip().replace(DIGIT_GROUP_SEPARATOR, "");
    if (!PLAIN_DECIMAL.matcher(valueText).matches()) {
      problems.add(new SheetProblem.NotANumber(lineNumber, columns[1].strip()));
      return;
    }
    String source = unquoted(columns[3].strip());
    if (source.isEmpty()) {
      problems.add(new SheetProblem.MissingSource(lineNumber));
      return;
    }
    entries.add(
        new SheetEntry(
            lineNumber, columns[0].strip(), new BigDecimal(valueText), columns[2].strip(), source));
  }
}
```

- `readLine` is `private`, a helper that only this class may call. It returns nothing (`void`) and adds to the lists it receives.
- Line 100: `line.split(COLUMN_SEPARATOR, COLUMNS)` cuts the line at each comma, but at most into 4 pieces. The second argument is the **limit**. With a limit of 4, every comma after the third stays inside the fourth piece. That is how a source can contain commas: it is the last column. The comment on line 99 says so. The result is an array of text (`String[]`).
- Lines 101 to 104: if there are not exactly four columns, add a `WrongColumnCount` problem and `return` from this method.
- Line 105: the value text is column 1, stripped of spaces, with every underscore removed. `replace("_", "")` turns `5_000_000` into `5000000`.
- Lines 106 to 109: `PLAIN_DECIMAL.matcher(valueText).matches()` is true when the whole text fits the pattern. If not, add a `NotANumber` problem. It carries the original text of the column, so the message shows what the person wrote.
- Line 110: the source is column 3 with the quotes removed. Lines 111 to 114: an empty source is a `MissingSource` problem. A number with no source cannot be checked by anybody, so the sheet refuses it.
- Lines 115 to 117: a good line becomes a `SheetEntry`: the line number, the quantity name (column 0), the value as a `BigDecimal` made from the text, the unit (column 2) and the source. `new BigDecimal(valueText)` makes an exact decimal from text. It cannot fail here, because the pattern already allowed only digits and one dot.
- The last `}` closes the class.

### `SheetEntry.java`

```java
package io.plainround.seatwave.estimates.estimate;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One data line of an estimation sheet, read but not yet checked against the input rules.
 *
 * @param lineNumber the line in the file, counted from 1, so a problem can point at it
 * @param quantity the name in the first column, for example "peak_factor"
 * @param value the number in the second column
 * @param unit the text of the third column, for example "ratio"
 * @param source where the number comes from: a measurement, a document or an assumption
 */
public record SheetEntry(
    int lineNumber, String quantity, BigDecimal value, String unit, String source) {
```

- A **record** is a short way to write a class that only carries values. The header lists the **components**: the line number, the quantity name, the value, the unit text and the source. Java writes the constructor, the accessors `lineNumber()`, `quantity()`, `value()`, `unit()`, `source()`, `equals`, `hashCode` and `toString`.
- The record keeps the unit as plain text, not as a `Unit`. At this stage the entry is "read but not yet checked": the text may name a unit that does not exist or is wrong for this input. The reader checks it in the next step.

```java
  /**
   * Creates an entry.
   *
   * @throws NullPointerException if any text or the value is null
   * @throws IllegalArgumentException if the line number is below 1
   */
  public SheetEntry {
    Objects.requireNonNull(quantity, "quantity");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(source, "source");
    if (lineNumber < 1) {
      throw new IllegalArgumentException("line numbers start at 1, but was " + lineNumber);
    }
  }
```

- A **compact constructor** is the record form of a constructor. It has no parameter list, and the components are available as variables. It runs before the fields are set.
- Four `Objects.requireNonNull(x, "x")` calls reject a null. Each returns the value if it is not null and throws `NullPointerException` with the name of the argument otherwise.
- A line number below 1 throws `IllegalArgumentException`. Line numbers start at 1. Both checks are for the programmer: `CsvSheet` never passes a null or a number below 1.

```java
  /**
   * Returns the same entry with another value, for the sensitivity check.
   *
   * @param newValue the value to put in place of the current one
   * @return a copy that differs only in its value
   */
  public SheetEntry withValue(BigDecimal newValue) {
    return new SheetEntry(lineNumber, quantity, newValue, unit, source);
  }
}
```

- `withValue` returns a copy of the entry that differs only in its value. A record is immutable, so "change the value" means "build a new one". The sensitivity check uses it to double a value and keep everything else the same.

### `SheetProblem.java`

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
```

- `public sealed interface SheetProblem` declares an **interface**, a named set of abilities with no state, that is also **sealed**: it names who may implement it. This one has no `permits` clause, so the compiler takes the list from the records declared inside the same file.
- A `switch` over a sealed interface must handle every case, with no `default`. When a new problem is added, every switch over the type stops compiling until it handles the new one. The comment says so.
- `record WrongHeader(String found) implements SheetProblem {}` is a record **nested** in the interface. A record nested in an interface is public and static. `implements SheetProblem` says "a `WrongHeader` is a kind of `SheetProblem`". The body `{}` is empty: the components are all it needs. Seen from outside, it is `SheetProblem.WrongHeader`.

```java
  /**
   * A line does not have four columns.
   *
   * @param lineNumber the line in the file
   */
  record WrongColumnCount(int lineNumber) implements SheetProblem {}

  /**
   * A value is not a plain decimal number.
   *
   * @param lineNumber the line in the file
   * @param text the value as it was written
   */
  record NotANumber(int lineNumber, String text) implements SheetProblem {}

  /**
   * A line has no source, so nobody can check where its number comes from.
   *
   * @param lineNumber the line in the file
   */
  record MissingSource(int lineNumber) implements SheetProblem {}
```

- `WrongColumnCount(int lineNumber)`: the line does not have four columns.
- `NotANumber(int lineNumber, String text)`: the value is not a plain decimal. `text` is what was written.
- `MissingSource(int lineNumber)`: the source column is empty.

```java
  /**
   * The quantity name is not an input of the estimator.
   *
   * @param lineNumber the line in the file
   * @param quantity the name as it was written
   */
  record UnknownQuantity(int lineNumber, String quantity) implements SheetProblem {}

  /**
   * The unit is not the one the input must be written in.
   *
   * @param lineNumber the line in the file
   * @param quantity the input
   * @param expected the unit the input requires
   * @param found the unit the sheet wrote
   */
  record WrongUnit(int lineNumber, String quantity, String expected, String found)
      implements SheetProblem {}
```

- `UnknownQuantity(int lineNumber, String quantity)`: the name is not an input of the estimator and not a store field.
- `WrongUnit(int lineNumber, String quantity, String expected, String found)`: the unit text is not the one the input requires. The record header continues on a second line with `implements SheetProblem {}`; Java allows a line break there.

```java
  /**
   * The value is outside the range of the input.
   *
   * @param lineNumber the line in the file
   * @param quantity the input
   * @param rule the range in words
   */
  record OutOfRange(int lineNumber, String quantity, String rule) implements SheetProblem {}

  /**
   * The same quantity is given twice.
   *
   * @param lineNumber the later line
   * @param quantity the input
   * @param firstLine the line that gave it first
   */
  record Duplicate(int lineNumber, String quantity, int firstLine) implements SheetProblem {}

  /**
   * An input the estimator needs is not in the sheet.
   *
   * @param quantity the missing name
   */
  record Missing(String quantity) implements SheetProblem {}

  /** The sheet describes no stored data at all. */
  record NoStore() implements SheetProblem {}
}
```

- `OutOfRange(int lineNumber, String quantity, String rule)`: the value is outside the allowed range. `rule` is the range in words.
- `Duplicate(int lineNumber, String quantity, int firstLine)`: the same quantity is given twice. It carries both line numbers.
- `Missing(String quantity)`: an input the estimator needs is not in the sheet. It has no line number, because no line exists.
- `NoStore()`: the sheet describes no `store.<name>.<field>` line at all. A record with no components is a marker for "this happened".
- The last `}` closes the interface. There are ten problems in all.

### `Range.java`

```java
package io.plainround.seatwave.estimates.estimate;

import java.math.BigDecimal;

/**
 * The values an input may take. Each range is a rule written once and used by both the sheet
 * reader, which reports a broken rule, and the input records, which refuse to exist with one.
 */
enum Range {
```

- `enum Range` has no `public` before it: it is **package-private**, so only classes in the same package can use it. The reader and the input records use it, and nobody else needs to.
- A range is "a rule written once": the reader, which reports a broken rule, and the records, which refuse to exist with one, share it.

```java
  /** Greater than zero. */
  POSITIVE("greater than 0"),
  /** Zero or more. */
  NON_NEGATIVE("0 or more"),
  /** One or more: a multiplier that can only add, such as a replication factor. */
  AT_LEAST_ONE("1 or more"),
  /** A share from 0 to 100 percent. */
  SHARE("from 0 to 100"),
  /** A share above 0 and at most 100 percent. */
  POSITIVE_SHARE("above 0 and at most 100"),
  /** A whole number, zero or more. */
  WHOLE_NUMBER("a whole number, 0 or more");

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final String description;

  Range(String description) {
    this.description = description;
  }
```

- Six values, each with a text description passed to the constructor: `POSITIVE` ("greater than 0"), `NON_NEGATIVE` ("0 or more"), `AT_LEAST_ONE` ("1 or more"), `SHARE` ("from 0 to 100"), `POSITIVE_SHARE` ("above 0 and at most 100") and `WHOLE_NUMBER` ("a whole number, 0 or more"). The descriptions go straight into error messages.
- `private static final BigDecimal HUNDRED = new BigDecimal("100");` is the exact number 100, used by the two share ranges.
- The constructor stores the description in a `private final` field.

```java
  /**
   * Tells whether a value obeys the rule.
   *
   * @param value the value to check
   * @return true when the value is inside the range
   */
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

  /**
   * Describes the rule in words.
   *
   * @return for example "from 0 to 100"
   */
  String description() {
    return description;
  }
}
```

- `contains` answers "does this value obey the rule?". It is a **switch expression**: a `switch` that produces a value. Each `case NAME -> expression;` is one arm, with an arrow and no fall-through. `switch (this)` switches on the enum value itself. The switch covers all six constants and has no `default`, so adding a seventh range makes the compiler ask for a new arm.
- `value.signum()` is -1, 0 or 1. `signum() > 0` means "above zero". `signum() >= 0` means "zero or more".
- `value.compareTo(BigDecimal.ONE) >= 0` means "at least 1". `compareTo` compares the numeric value. It is used instead of `equals`, because `equals` on a `BigDecimal` also compares the scale (`1.0` and `1` are not equal).
- `SHARE` is "zero or more and at most 100". `POSITIVE_SHARE` is "above zero and at most 100". A utilization target of 0 percent is not allowed, because it would divide by zero later.
- `WHOLE_NUMBER` is "zero or more and with no decimals": `value.stripTrailingZeros().scale() <= 0`. `stripTrailingZeros` turns `2.0` into `2`. The scale is the number of digits after the point. A scale of 0 or less means no fraction. So `2` and `2.0` pass and `1.5` fails. `1E+2` (a hundred) has scale -2 and passes.
- `description()` returns the words.

### `InputRule.java`

```java
package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One rule per input a sheet may give: its name, the one unit it must be written in, and its range.
 *
 * <p>A sheet must write each input in exactly its unit. "500 KB" where bytes are expected is
 * rejected, not converted, because KB is ambiguous (1,000 or 1,024) and the slip is what this
 * module teaches to catch.
 */
```

- This enum has one value per input a sheet may give. Imports bring in `Quantity` and `Unit` from the `units` package, `Arrays` for the stream, `Objects` and `Optional`.
- The Javadoc states the rule: "500 KB" where bytes are expected is rejected, not converted. KB is ambiguous (1,000 or 1,024 bytes), and the slip is what this module teaches to catch.

```java
public enum InputRule {
  /** People who use the system on a typical day. */
  DAILY_ACTIVE_USERS("daily_active_users", Unit.USERS, Range.POSITIVE),
  /** Requests one daily user makes in a day. */
  REQUESTS_PER_USER_PER_DAY(
      "requests_per_user_per_day", Unit.REQUESTS_PER_USER_PER_DAY, Range.POSITIVE),
  /** Peak rate divided by the average rate of the whole day. */
  PEAK_FACTOR("peak_factor", Unit.RATIO, Range.AT_LEAST_ONE),
  /**
   * The share of requests that change data. It is an input instead of the read share because
   * doubling a small write share is a fair test, while doubling a read share of 85 percent is
   * impossible.
   */
  WRITE_SHARE("write_share", Unit.PERCENT, Range.SHARE),
  /** Average size of one request as it arrives, headers included. */
  REQUEST_BYTES("request_bytes", Unit.BYTES, Range.POSITIVE),
  /** Average size of one response as it leaves, headers included. */
  RESPONSE_BYTES("response_bytes", Unit.BYTES, Range.POSITIVE),
  /** How far ahead the storage estimate looks. */
  HORIZON_YEARS("horizon_years", Unit.YEARS, Range.POSITIVE),
  /** Every key the cache could ever hold. */
  CACHE_KEYS("cache_keys", Unit.KEYS, Range.NON_NEGATIVE),
  /** The share of keys that receives most reads. */
  CACHE_HOT_SHARE("cache_hot_share", Unit.PERCENT, Range.SHARE),
  /** Size of one cached value. */
  CACHE_ENTRY_BYTES("cache_entry_bytes", Unit.BYTES, Range.POSITIVE),
  /** Memory used per byte of value: keys, object headers and the table itself. */
  CACHE_OVERHEAD("cache_overhead", Unit.RATIO, Range.AT_LEAST_ONE),
  /** The measured rate at which one server's p99 starts to jump. */
  KNEE_PER_SERVER("knee_per_server", Unit.REQUESTS_PER_SECOND, Range.POSITIVE),
  /** The share of the knee a server is planned to run at. */
  UTILIZATION_TARGET("utilization_target", Unit.PERCENT, Range.POSITIVE_SHARE),
  /** Extra capacity above the estimated peak, for spikes the estimate missed. */
  HEADROOM("headroom", Unit.PERCENT, Range.NON_NEGATIVE),
  /** Servers added on top, so one can fail or be deployed without losing capacity. */
  SPARE_SERVERS("spare_servers", Unit.SERVERS, Range.WHOLE_NUMBER),
  /** On-demand price of one server. */
  SERVER_PRICE("server_price", Unit.USD_PER_HOUR, Range.NON_NEGATIVE),
  /** Price of one decimal gigabyte sent to the internet. */
  EGRESS_PRICE("egress_price", Unit.USD_PER_GB, Range.NON_NEGATIVE),
  /** Price of keeping one decimal gigabyte for a month. */
  STORAGE_PRICE("storage_price", Unit.USD_PER_GB_MONTH, Range.NON_NEGATIVE);
```

- Each value has three arguments: the name as a sheet writes it, the one unit it must be written in, and its range. For example `DAILY_ACTIVE_USERS("daily_active_users", Unit.USERS, Range.POSITIVE)` says the quantity `daily_active_users` must be in `users` and greater than zero.
- The values follow the order of the five-line estimate. People and traffic: `DAILY_ACTIVE_USERS`, `REQUESTS_PER_USER_PER_DAY` (a positive count), `PEAK_FACTOR` (a ratio of at least 1, because a peak cannot be lower than the average), `WRITE_SHARE` (a percent from 0 to 100). Message sizes: `REQUEST_BYTES` and `RESPONSE_BYTES`. `HORIZON_YEARS` is how many years ahead the storage looks.
- The Javadoc of `WRITE_SHARE` explains a design choice: the sheet gives the write share, not the read share. Doubling a read share of 85 percent is impossible (170 percent), while doubling a small write share is a fair test.
- Cache inputs: `CACHE_KEYS` (zero or more), `CACHE_HOT_SHARE` (0 to 100 percent), `CACHE_ENTRY_BYTES` and `CACHE_OVERHEAD` (a ratio of 1 or more: the cache cannot use less memory than the value itself).
- Server inputs: `KNEE_PER_SERVER` is the rate at which one server's p99 latency starts to jump, in `requests/s`. `UTILIZATION_TARGET` is the share of the knee a server runs at, above 0 and at most 100 percent. `HEADROOM` is extra capacity above the peak, zero or more. `SPARE_SERVERS` must be a whole number.
- Price inputs: `SERVER_PRICE` in `USD/hour`, `EGRESS_PRICE` in `USD/GB`, `STORAGE_PRICE` in `USD/GB-month`. **Egress** is data that leaves the cloud to the internet.
- The last value ends with `;`. There are 18 rules in all. I ran `InputRule.values().length` and got 18. The `Inputs` constructor checks each of them by looping over `InputRule.values()`.

```java
  private final String key;
  private final Unit unit;
  private final Range range;

  InputRule(String key, Unit unit, Range range) {
    this.key = key;
    this.unit = unit;
    this.range = range;
  }
```

- Three `private final` fields hold what each value was given. The constructor copies the three arguments into them.

```java
  /**
   * Finds the rule for a quantity name written in a sheet.
   *
   * @param key the name, for example "peak_factor"
   * @return the rule, or empty when the name is not an input
   */
  public static Optional<InputRule> withKey(String key) {
    return Arrays.stream(values()).filter(rule -> rule.key.equals(key)).findFirst();
  }

  /**
   * Returns the name a sheet uses.
   *
   * @return for example "daily_active_users"
   */
  public String key() {
    return key;
  }

  /**
   * Returns the one unit the input must be written in.
   *
   * @return the unit
   */
  public Unit unit() {
    return unit;
  }

  Range range() {
    return range;
  }
```

- `withKey` is a static lookup like `Unit.withSymbol`. `Arrays.stream(values())` streams all the rules. `.filter(rule -> rule.key.equals(key))` is a **lambda** (a small unnamed function) that keeps the rules whose key equals the text. `.findFirst()` returns an `Optional<InputRule>`: the rule or empty.
- `key()` and `unit()` are public accessors. `range()` has no `public`, so it is package-private: only the `estimate` package needs the range, so it stays hidden from the rest.

```java
  /**
   * Checks a quantity against this rule.
   *
   * @param quantity the value to check
   * @return the same quantity
   * @throws IllegalArgumentException if the unit is wrong or the value is out of range
   */
  Quantity requireValid(Quantity quantity) {
    Objects.requireNonNull(quantity, key);
    if (quantity.unit() != unit || !range.contains(quantity.amount())) {
      throw new IllegalArgumentException(
          key + " must be " + range.description() + " " + unit.symbol() + ", but was " + quantity);
    }
    return quantity;
  }
}
```

- `requireValid` is the check the `Inputs` record calls. It takes a `Quantity` and returns it when it obeys the rule, so the call can be chained. It is package-private.
- `Objects.requireNonNull(quantity, key)` fails with the input's name when the quantity is null. A null here means the `Inputs` constructor was given a map with a missing input.
- `quantity.unit() != unit || !range.contains(quantity.amount())` is true when the unit is wrong, or the value is out of range. `||` means "or". Either throws `IllegalArgumentException`. The message is built from the key, the range description, the unit symbol and the quantity itself. Example: `response_bytes must be greater than 0 bytes, but was 0 bytes`.
- This check is the second line of defence. `InputsReader` reports the same two problems as values for a person. This one throws, for a programmer who builds `Inputs` by hand with a wrong value.

### `StoreField.java`

```java
package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The five numbers every kind of stored data needs. A sheet writes each one as {@code
 * store.<name>.<field>}, for example {@code store.bookings.bytes_per_item}.
 */
public enum StoreField {
```

- A **store** is one kind of stored data: bookings, log lines, photos. Every store needs the same five numbers. A sheet writes each one as `store.<name>.<field>`, for example `store.bookings.bytes_per_item`. `{@code ...}` marks the example in documentation.

```java
  /** New records or files written per day. */
  ITEMS_PER_DAY("items_per_day", Unit.ITEMS_PER_DAY, Range.NON_NEGATIVE),
  /** Size of one record or file. */
  BYTES_PER_ITEM("bytes_per_item", Unit.BYTES, Range.POSITIVE),
  /** How long one item is kept before it is deleted. */
  RETENTION_DAYS("retention_days", Unit.DAYS, Range.POSITIVE),
  /** Copies kept of every byte. */
  REPLICATION_FACTOR("replication_factor", Unit.COPIES, Range.AT_LEAST_ONE),
  /** Bytes stored per byte of data, for indexes and page overhead. */
  INDEX_OVERHEAD("index_overhead", Unit.RATIO, Range.AT_LEAST_ONE);

  private final String key;
  private final Unit unit;
  private final Range range;

  StoreField(String key, Unit unit, Range range) {
    this.key = key;
    this.unit = unit;
    this.range = range;
  }
```

- Five values: `ITEMS_PER_DAY` (items written per day, zero or more), `BYTES_PER_ITEM` (size of one item, greater than zero), `RETENTION_DAYS` (how many days an item is kept, greater than zero), `REPLICATION_FACTOR` (copies kept of every byte, 1 or more) and `INDEX_OVERHEAD` (bytes stored per byte of data, 1 or more, for indexes and page overhead).
- It has the same three fields and the same constructor shape as `InputRule`: a key, a unit and a range.

```java
  /**
   * Finds the field a sheet names after the store name.
   *
   * @param key for example "retention_days"
   * @return the field, or empty when there is none with that name
   */
  public static Optional<StoreField> withKey(String key) {
    return Arrays.stream(values()).filter(field -> field.key.equals(key)).findFirst();
  }

  /**
   * Returns the name a sheet uses after the store name.
   *
   * @return for example "items_per_day"
   */
  public String key() {
    return key;
  }

  /**
   * Returns the one unit the field must be written in.
   *
   * @return the unit
   */
  public Unit unit() {
    return unit;
  }

  Range range() {
    return range;
  }

  Quantity requireValid(String storeName, Quantity quantity) {
    Objects.requireNonNull(quantity, key);
    if (quantity.unit() != unit || !range.contains(quantity.amount())) {
      throw new IllegalArgumentException(
          storeName
              + "."
              + key
              + " must be "
              + range.description()
              + " "
              + unit.symbol()
              + ", but was "
              + quantity);
    }
    return quantity;
  }
}
```

- `withKey` looks up a field by the text after the store name, with a stream and `findFirst`, as `InputRule.withKey` does.
- `key()`, `unit()` and `range()` are accessors. Again `range()` is package-private.
- `requireValid(String storeName, Quantity quantity)` is the same check as in `InputRule`, with the store name added to the message, so the text reads `bookings.bytes_per_item must be greater than 0 bytes, but was 0 bytes`.
- The message is built by joining pieces with `+`. A long chain of `+` is written one piece per line by the formatter that the build enforces (`spotless-google-java-format.md`), which explains the shape of lines 71 to 79.

### `Inputs.java`

```java
package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Every input of one estimate, each in its one allowed unit and inside its range.
 *
 * <p>The constructor checks every rule of {@link InputRule}, so an {@code Inputs} with a response
 * size in bits, a missing peak factor or a read share of 170 percent cannot exist. Instances are
 * immutable and safe to share between threads.
 *
 * @param values one value for every {@link InputRule}
 * @param stores the kinds of stored data, at least one, with distinct names
 */
public record Inputs(Map<InputRule, Quantity> values, List<Store> stores) {
```

- Imports: `EnumMap` is a map whose keys are enum values; `HashSet` and `Set` hold distinct items; `Map` and `List` are the general map and list types. A **map** is a collection of pairs, a key and a value, where you look a value up by its key.
- The Javadoc says the constructor checks every rule of `InputRule`, so an `Inputs` with a response size in bits, a missing peak factor or a share of 170 percent cannot exist.
- `public record Inputs(Map<InputRule, Quantity> values, List<Store> stores)` is a record with two components. `values` maps each rule to a `Quantity`. `stores` is the list of kinds of stored data.

```java
  /**
   * Creates the inputs.
   *
   * @throws IllegalArgumentException if a value is missing or invalid, there is no store, or two
   *     stores share a name
   */
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

- The **compact constructor** runs before the fields are set. Lines 30 and 31 reject nulls.
- Line 32 makes an `EnumMap<InputRule, Quantity>`. `new EnumMap<>(InputRule.class)` needs the class of the enum so it can size itself. `InputRule.class` is the object that stands for the type. An `EnumMap` keeps its keys in the order of the enum, and is compact and fast.
- Lines 33 to 35 loop over every rule. `values.get(rule)` takes the quantity given for that rule (or null, if none), and `rule.requireValid(...)` throws if it is null, in the wrong unit, or out of range. The accepted quantity goes into `checked`. After the loop, `checked` holds exactly one valid quantity for each of the 18 rules, or the constructor has thrown.
- Line 36 reassigns the component: `values = Map.copyOf(checked);`. Reassigning a component inside a compact constructor changes what Java stores in the field. `Map.copyOf` makes an **unmodifiable** map, one that throws `UnsupportedOperationException` on any change. The caller's map is no longer reachable from the record. If the caller keeps and changes its own map, the `Inputs` does not change.
- Line 37 does the same for the stores with `List.copyOf`.
- Lines 38 to 40: an estimate needs at least one store, because storage and size are computed from the stores.
- Lines 41 to 46: names must be distinct. `names.add(store.name())` returns `false` when the set already holds that name. `!` negates it, so a repeated name throws. A `HashSet` answers "have I seen this?" quickly.

```java
  /**
   * Returns one input.
   *
   * @param rule the input wanted
   * @return its value; never null, because the constructor requires every input
   */
  public Quantity get(InputRule rule) {
    return values.get(rule);
  }
}
```

- `get(rule)` returns one input. It can promise "never null" because the constructor required every rule.
- A record built this way is **immutable**: nothing can change after the constructor ends. That is what "safe to share between threads" means.

### `Store.java`

```java
package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One kind of stored data, such as bookings or log lines, with all five of its numbers.
 *
 * <p>The constructor refuses a store with a missing field, a field in the wrong unit or a value out
 * of range, so a half-described store cannot reach the estimator. Instances are immutable.
 *
 * @param name a short lowercase name, for example "bookings"
 * @param fields every {@link StoreField} with its value
 */
public record Store(String name, Map<StoreField, Quantity> fields) {

  /** Lowercase letters, digits and underscores, starting with a letter, at most 32 characters. */
  static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");
```

- `Store` is a record with a name and a map of fields. `NAME` is a pattern (a compiled regular expression): `[a-z][a-z0-9_]{0,31}` means one lowercase letter, then zero to 31 letters, digits or underscores. So a name is 1 to 32 characters long and starts with a letter. `bookings` and `logs_2` pass. `Bookings`, `2fast` and `a-b` do not. It has no `public`, so the reader in the same package can use `Store.NAME` too.

```java
  /**
   * Creates a store.
   *
   * @throws IllegalArgumentException if the name is not allowed or a field is missing or invalid
   */
  public Store {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(fields, "fields");
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("a store name must match " + NAME + ", but was " + name);
    }
    Map<StoreField, Quantity> checked = new EnumMap<>(StoreField.class);
    for (StoreField field : StoreField.values()) {
      checked.put(field, field.requireValid(name, fields.get(field)));
    }
    fields = Map.copyOf(checked);
  }

  /**
   * Returns one field.
   *
   * @param field the field wanted
   * @return its value; never null, because the constructor requires every field
   */
  public Quantity get(StoreField field) {
    return fields.get(field);
  }
}
```

- The compact constructor rejects nulls, then checks the name against the pattern. `NAME.matcher(name).matches()` is true when the whole name fits the pattern. The error message prints the pattern itself (`" + NAME + "`).
- Lines 34 to 37 build an `EnumMap<StoreField, Quantity>` in the same way `Inputs` does: loop over the five fields, check each with `field.requireValid(name, fields.get(field))`, and keep the accepted ones.
- `fields = Map.copyOf(checked)` stores an unmodifiable copy. A store with a missing field, a field in the wrong unit or a value out of range cannot exist.
- `get(field)` returns one field, never null.

### `InputsReader.java`

```java
package io.plainround.seatwave.estimates.estimate;

import com.github.sviperll.result4j.Result;
import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Turns sheet entries into checked {@link Inputs}, collecting every problem instead of stopping at
 * the first, so one run shows all the work left.
 *
 * <p>It uses the same rules as the records' constructors, so each rule is written once. Stateless
 * and safe to use from any thread.
 */
public final class InputsReader {

  private static final String STORE_PREFIX = "store.";
  private static final char NAME_SEPARATOR = '.';

  private InputsReader() {}
```

- This class turns the list of `SheetEntry` values into checked `Inputs`. Its Javadoc says it collects every problem instead of stopping at the first, and that it uses the same rules as the constructors, so each rule is written once.
- The imports include `HashMap` (a map with no order), `LinkedHashMap` (a map that remembers the order of insertion), `EnumMap`, `Set`, `Optional` and `Result`.
- `STORE_PREFIX = "store."` is the start of every store line. `NAME_SEPARATOR = '.'` is a **`char`**, one character, written in single quotes. A string uses double quotes.
- `private InputsReader() {}`: a private empty constructor means "a class of static methods; do not instantiate".

```java
  /**
   * Checks entries against the input rules.
   *
   * @param entries the entries of one sheet
   * @return the inputs, or every problem found
   */
  public static Result<Inputs, List<SheetProblem>> read(List<SheetEntry> entries) {
    Objects.requireNonNull(entries, "entries");
    List<SheetProblem> problems = new ArrayList<>();
    Map<String, Integer> firstLineOf = new HashMap<>();
    Map<InputRule, Quantity> values = new EnumMap<>(InputRule.class);
    Map<String, Map<StoreField, Quantity>> stores = new LinkedHashMap<>();
    for (SheetEntry entry : entries) {
      Integer firstLine = firstLineOf.putIfAbsent(entry.quantity(), entry.lineNumber());
      if (firstLine != null) {
        problems.add(new SheetProblem.Duplicate(entry.lineNumber(), entry.quantity(), firstLine));
        continue;
      }
      readEntry(entry, values, stores).ifPresent(problems::add);
    }
    addMissing(firstLineOf.keySet(), stores.keySet(), problems);
    if (!problems.isEmpty()) {
      return Result.error(List.copyOf(problems));
    }
    List<Store> storeList = new ArrayList<>();
    stores.forEach((name, fields) -> storeList.add(new Store(name, fields)));
    return Result.success(new Inputs(values, storeList));
  }
```

- `read` returns `Result<Inputs, List<SheetProblem>>`: either the inputs or every problem found.
- Line 38: the list of problems. Line 39: `firstLineOf` remembers the first line on which each quantity name appeared. Line 40: `values` collects the accepted plain inputs. Line 41: `stores` collects the accepted store fields: for each store name, a map from field to quantity. A `LinkedHashMap` keeps the stores in the order the sheet first named them, so the report lists them in sheet order.
- Line 43: `firstLineOf.putIfAbsent(entry.quantity(), entry.lineNumber())` puts the line number in the map only if the name is not there yet, and returns the old value, or `null` when there was none. It is of type `Integer`, the **object form** of `int`. A map holds objects, so Java converts between `int` and `Integer` for you. That is called autoboxing.
- Lines 44 to 47: if the result is not null, the name was seen before, so this line is a `Duplicate`. The problem records the later line and the first line. `continue` skips the rest of the loop body for this entry.
- Line 48: `readEntry(entry, values, stores).ifPresent(problems::add);`. `readEntry` returns an `Optional<SheetProblem>`: a problem, or nothing when the entry was good. `ifPresent` runs a function only when the box holds a value. `problems::add` is a **method reference**, short for the lambda `problem -> problems.add(problem)`. It adds the problem to the list.
- Line 50: `addMissing(...)` adds a `Missing` problem for every input the sheet never named, and for every store field that is absent. It receives the names that were given and the names of the stores.
- Lines 51 to 53: if there is any problem, return an error with an unmodifiable copy of the list.
- Lines 54 to 56: otherwise build the stores. `stores.forEach((name, fields) -> storeList.add(new Store(name, fields)))` calls a lambda for each pair: it builds a `Store` from the name and the fields map. The `Store` constructor checks all five fields are there. `Result.success(new Inputs(values, storeList))` returns the success. `Inputs` checks every rule again. Because `addMissing` found no problem, these constructors cannot throw.

```java
  private static Optional<SheetProblem> readEntry(
      SheetEntry entry,
      Map<InputRule, Quantity> values,
      Map<String, Map<StoreField, Quantity>> stores) {
    Optional<InputRule> rule = InputRule.withKey(entry.quantity());
    if (rule.isPresent()) {
      Optional<SheetProblem> problem = check(entry, rule.get().unit(), rule.get().range());
      if (problem.isEmpty()) {
        values.put(rule.get(), new Quantity(entry.value(), rule.get().unit()));
      }
      return problem;
    }
    return readStoreEntry(entry, stores);
  }
```

- `readEntry` handles one entry and returns the problem it found, if any.
- Line 63 looks for a rule with the entry's quantity name. If one is found (`rule.isPresent()`), `check(entry, unit, range)` tests the unit and the range. If `check` returns an empty `Optional` (`problem.isEmpty()`), the entry is good, and `values.put(...)` stores a new `Quantity` of the value and the rule's unit. `rule.get()` takes the value out of the `Optional`. It is safe here because `isPresent()` was just tested. Either way, `return problem` hands back the problem (or the empty box).
- If no plain rule matches, the name may be a store field, so the method returns `readStoreEntry(entry, stores)`.

```java
  private static Optional<SheetProblem> readStoreEntry(
      SheetEntry entry, Map<String, Map<StoreField, Quantity>> stores) {
    String quantity = entry.quantity();
    int separator = quantity.lastIndexOf(NAME_SEPARATOR);
    if (!quantity.startsWith(STORE_PREFIX) || separator <= STORE_PREFIX.length()) {
      return Optional.of(new SheetProblem.UnknownQuantity(entry.lineNumber(), quantity));
    }
    String storeName = quantity.substring(STORE_PREFIX.length(), separator);
    Optional<StoreField> field = StoreField.withKey(quantity.substring(separator + 1));
    if (field.isEmpty() || !Store.NAME.matcher(storeName).matches()) {
      return Optional.of(new SheetProblem.UnknownQuantity(entry.lineNumber(), quantity));
    }
    Optional<SheetProblem> problem = check(entry, field.get().unit(), field.get().range());
    if (problem.isEmpty()) {
      stores
          .computeIfAbsent(storeName, name -> new EnumMap<>(StoreField.class))
          .put(field.get(), new Quantity(entry.value(), field.get().unit()));
    }
    return problem;
  }
```

- `readStoreEntry` reads a name such as `store.bookings.bytes_per_item`. Line 77: `quantity.lastIndexOf(NAME_SEPARATOR)` finds the position of the last dot. For `store.bookings.bytes_per_item` that is the dot before `bytes_per_item`.
- Lines 78 to 80: the name must start with `store.` and the last dot must be after the prefix (`separator <= STORE_PREFIX.length()` is the rejected case, for example `store.x`). If not, it is an `UnknownQuantity`. `Optional.of(...)` makes a box holding one value.
- Line 81: `quantity.substring(STORE_PREFIX.length(), separator)` takes the text between `store.` and the last dot: the store name, `bookings`. Line 82: `quantity.substring(separator + 1)` takes the text after the last dot: the field name `bytes_per_item`, which is looked up with `StoreField.withKey`.
- Lines 83 to 85: if no such field exists, or the store name does not match the pattern, it is an `UnknownQuantity`. `!Store.NAME.matcher(storeName).matches()` is true for a bad store name.
- Lines 86 to 92: the unit and range are checked with the same `check` helper. When the entry is good, `stores.computeIfAbsent(storeName, name -> new EnumMap<>(StoreField.class)).put(...)` finds the map for this store, creating an empty one if the store is new, and puts the field into it. `computeIfAbsent` calls its lambda only when the key is not in the map yet.

```java
  private static Optional<SheetProblem> check(SheetEntry entry, Unit unit, Range range) {
    if (!unit.symbol().equals(entry.unit())) {
      return Optional.of(
          new SheetProblem.WrongUnit(
              entry.lineNumber(), entry.quantity(), unit.symbol(), entry.unit()));
    }
    if (!range.contains(entry.value())) {
      return Optional.of(
          new SheetProblem.OutOfRange(entry.lineNumber(), entry.quantity(), range.description()));
    }
    return Optional.empty();
  }
```

- `check` compares the unit text with the rule's unit symbol. If they differ, the problem is a `WrongUnit`, with the line number, the quantity name, the expected symbol and the found text. This is the line that rejects `KB` and `bits`: no conversion is tried. Then the range: `range.contains(entry.value())` false gives an `OutOfRange` with the range's description. Otherwise `Optional.empty()`, "no problem".
- Because the unit is checked first, a wrong unit and a wrong value on the same line report only the unit.

```java
  // An input counts as given when any line names it, even a rejected one, so a wrong unit is
  // reported once, as a wrong unit, and not a second time as missing.
  private static void addMissing(
      Set<String> givenNames, Set<String> storeNames, List<SheetProblem> problems) {
    for (InputRule rule : InputRule.values()) {
      if (!givenNames.contains(rule.key())) {
        problems.add(new SheetProblem.Missing(rule.key()));
      }
    }
    boolean hasStoreLine = givenNames.stream().anyMatch(name -> name.startsWith(STORE_PREFIX));
    if (!hasStoreLine) {
      problems.add(new SheetProblem.NoStore());
    }
    for (String storeName : storeNames) {
      for (StoreField field : StoreField.values()) {
        String name = STORE_PREFIX + storeName + NAME_SEPARATOR + field.key();
        if (!givenNames.contains(name)) {
          problems.add(new SheetProblem.Missing(name));
        }
      }
    }
  }
}
```

- `addMissing` has a comment that explains one decision: an input counts as given when any line names it, even a rejected one. A line with a wrong unit is reported once, as a wrong unit, not a second time as missing. The method receives the set of every name given (`givenNames`) and the store names.
- Lines 112 to 116: for every rule, if its key is not among the names given, add `Missing`. Line 117: `givenNames.stream().anyMatch(name -> name.startsWith(STORE_PREFIX))` is true if at least one given name starts with `store.`. If not, add `NoStore`.
- Lines 121 to 128: for every store that had at least one accepted field, and for every field, build the full name `store.<name>.<field>` and add `Missing` if the sheet never named it. A store with three of five fields therefore reports the two that are missing. The rejected lines count as given.
- That is why `sheets/lab1-unit-slips.csv` reports `REJECTED missing input: headroom` once for the deleted line, and one line for each unit slip, and nothing more.

### `Step.java`

```java
package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import java.util.Objects;

/**
 * One line of visible arithmetic: what is computed, how, and the result with its unit.
 *
 * @param id a stable name, for example "rate.peak", so a result can be found and compared
 * @param section the line of the five-line estimate it belongs to, for example "RATE"
 * @param label what the result is, in words
 * @param formula the arithmetic with every operand and its unit
 * @param result the result; a {@link Quantity}, so it always carries a unit
 */
public record Step(String id, String section, String label, String formula, Quantity result) {

  /**
   * Creates a step.
   *
   * @throws NullPointerException if any part is null
   */
  public Step {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(section, "section");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(formula, "formula");
    Objects.requireNonNull(result, "result");
  }
}
```

- A `Step` is one line of visible arithmetic: a stable `id` such as `rate.peak`, the `section` of the five-line estimate it belongs to (`RATE`), the `label` in words, the `formula` with every operand and its unit, and the `result`, a `Quantity`.
- The compact constructor requires every part to be non-null. A record that carries only text and a `Quantity` needs nothing else.

### `Estimate.java`

```java
package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The result of one estimate: every step of the arithmetic, in the order it was done.
 *
 * <p>Instances are immutable and safe to share between threads.
 *
 * @param steps the steps, each with a distinct id
 */
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

- An `Estimate` is the list of steps, in the order they were computed. Its constructor requires a non-null list, replaces it with `List.copyOf(steps)` (an unmodifiable copy), and checks that no two steps share an id: `ids.add(step.id())` returns `false` for a repeat. The id is how a result is found later, so two steps with one id would be ambiguous.

```java
  /**
   * Finds the result of one step.
   *
   * @param id the step's id, for example "servers.peak"
   * @return the result
   * @throws IllegalArgumentException if no step has that id
   */
  public Quantity result(String id) {
    return steps.stream()
        .filter(step -> step.id().equals(id))
        .findFirst()
        .map(Step::result)
        .orElseThrow(() -> new IllegalArgumentException("no step has the id " + id));
  }
}
```

- `result(String id)` finds a step by id and returns its `Quantity`. Line 42 opens a **stream** over the steps. `.filter(step -> step.id().equals(id))` keeps the step with the id. `.findFirst()` returns an `Optional<Step>`. `.map(Step::result)` applies a method reference (`step -> step.result()`) to the value inside the box, if there is one, and gives an `Optional<Quantity>`. `.orElseThrow(() -> new IllegalArgumentException("no step has the id " + id))` takes the value out, or throws the exception the **supplier** makes. A supplier is a lambda with no input: `() -> ...`. Asking for an id that no step has is a bug in the formulas, so it throws.

### `Estimator.java`

This is the longest file. It holds every formula of the estimate. To make the arithmetic concrete, each method below is worked through with the numbers of `sheets/seatwave-production.csv`, the national SeatWave, and the result is compared with what the program printed. Those numbers came from a real run of `./gradlew -q estimate --args='sheets/seatwave-production.csv'`.

**Lines 1 to 19: imports and class comment**

```java
package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns inputs into the five-line estimate (rate, size, storage, bandwidth, servers) plus the cache
 * memory, keeping every step of the arithmetic.
 *
 * <p>Every result is computed from the inputs alone, with no clock and no randomness, so the same
 * inputs always give the same estimate. One instance computes one estimate and is confined to the
 * call that created it; use {@link #estimate(Inputs)} from any thread.
 */
public final class Estimator {
```

- `RoundingMode` lists the ways to round a decimal number. The estimator uses one of them, `CEILING`, for a server count.
- The Javadoc says every result is computed from the inputs alone, with no clock and no randomness, so the same inputs always give the same estimate. This is the property that lets a test print the inputs as a sheet, read them back and get an equal `Estimate`.
- `public final class Estimator`: no other class may extend it.

**Lines 21 to 41: constants, fields and constructor**

```java
  /** A traffic month is 30 days, as in Module 2. */
  static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");

  /** A machine is billed for 730 hours a month, as in Module 1 ($0.2016 x 730 = $147.17). */
  static final BigDecimal BILLING_HOURS_PER_MONTH = new BigDecimal("730");

  private static final String RATE = "RATE";
  private static final String SIZE = "SIZE";
  private static final String STORAGE = "STORAGE";
  private static final String BANDWIDTH = "BANDWIDTH";
  private static final String SERVERS = "SERVERS";
  private static final String CACHE = "CACHE";
  private static final String COST = "COST";
  private static final String TIMES = " x ";

  private final Inputs inputs;
  private final List<Step> steps = new ArrayList<>();

  private Estimator(Inputs inputs) {
    this.inputs = inputs;
  }
```

- `DAYS_PER_MONTH` is 30. The comment says a traffic month is 30 days, as in Module 2. `BILLING_HOURS_PER_MONTH` is 730. A cloud machine is billed for 730 hours a month (365 x 24 / 12 = 730), as in Module 1: 0.2016 x 730 = 147.17. They have no `public`, so tests in the same package can read them.
- `RATE`, `SIZE`, `STORAGE`, `BANDWIDTH`, `SERVERS`, `CACHE` and `COST` are the names of the report's sections, as text constants. `TIMES = " x "` is the text between two operands in a formula. Using a constant means the report never has a typo in one of them.
- Two **instance fields**. `inputs` is the checked inputs. `steps` is an empty list that the methods add to. An instance field belongs to one object, unlike a `static` one. `private final` means only this class touches them, and the reference is set once, although the list's contents grow.
- The constructor is `private`. The only way to make an `Estimator` is the static method `estimate` below. So an `Estimator` lives for one call.

**Lines 43 to 60: `estimate`, the one public method**

```java
  /**
   * Computes the estimate.
   *
   * @param inputs checked inputs
   * @return every step, in the order rate, size, storage, bandwidth, servers, cache, cost
   * @throws NullPointerException if the inputs are null
   */
  public static Estimate estimate(Inputs inputs) {
    Estimator estimator = new Estimator(Objects.requireNonNull(inputs, "inputs"));
    estimator.estimateRate();
    estimator.estimateSize();
    estimator.estimateStorage();
    estimator.estimateBandwidth();
    estimator.estimateServers();
    estimator.estimateCache();
    estimator.estimateCost();
    return new Estimate(estimator.steps);
  }
```

- The method makes an estimator and calls seven private methods in order, each of which adds steps to the list. Then `new Estimate(estimator.steps)` wraps the steps. `Objects.requireNonNull(inputs, "inputs")` returns the inputs if not null, and throws `NullPointerException` otherwise. The order matters: bandwidth reads `rate.peak`, servers read `rate.peak` and `rate.average`, and cost reads three earlier results.

**Lines 62 to 95: `estimateRate`, the rate**

```java
  private void estimateRate() {
    Quantity users = input(InputRule.DAILY_ACTIVE_USERS);
    Quantity perUser = input(InputRule.REQUESTS_PER_USER_PER_DAY);
    Quantity perDay = product(Unit.REQUESTS_PER_DAY, users, perUser);
    add("rate.per_day", RATE, "Requests per day", users + TIMES + perUser, perDay);
    // The unit conversion divides by 86,400, the seconds in 24 x 60 x 60.
    Quantity average = perDay.to(Unit.REQUESTS_PER_SECOND);
    add("rate.average", RATE, "Average rate", perDay + " / 86,400 s/day", average);
    Quantity peakFactor = input(InputRule.PEAK_FACTOR);
    Quantity peak = average.scaledBy(peakFactor.amount());
    add("rate.peak", RATE, "Peak rate", average + TIMES + peakFactor, peak);
    Quantity writeShare = input(InputRule.WRITE_SHARE);
    BigDecimal writeFraction = writeShare.to(Unit.RATIO).amount();
    BigDecimal readFraction = BigDecimal.ONE.subtract(writeFraction);
    Quantity readShare = new Quantity(readFraction, Unit.RATIO).to(Unit.PERCENT);
    add("rate.reads", RATE, "Reads at peak", peak + TIMES + readShare, peak.scaledBy(readFraction));
    add(
        "rate.writes",
        RATE,
        "Writes at peak",
        peak + TIMES + writeShare,
        peak.scaledBy(writeFraction));
    if (writeFraction.signum() > 0) {
      Quantity readsPerWrite =
          new Quantity(
              readFraction.divide(writeFraction, Quantity.PRECISION), Unit.READS_PER_WRITE);
      add(
          "rate.read_write",
          RATE,
          "Read-write ratio",
          readShare + " / " + writeShare,
          readsPerWrite);
    }
  }
```

- Lines 63 to 66 compute **requests per day**: users times requests per user. `input(InputRule.DAILY_ACTIVE_USERS)` reads a quantity from the inputs. `product(Unit.REQUESTS_PER_DAY, users, perUser)` multiplies the two amounts and names the unit of the answer. Production: 5,000,000 users x 20 requests/user/day = 100,000,000 requests/day. The `add` call records the step: an id `rate.per_day`, the section `RATE`, a label, a formula text and the result. The formula text is `users + TIMES + perUser`, which joins the two quantities with their units: `5,000,000 users x 20 requests/user/day`.
- Lines 67 to 69: the **average rate**. `perDay.to(Unit.REQUESTS_PER_SECOND)` converts by the unit sizes: 100,000,000 x 1 / 86,400 = 1,157.4 requests/s. The comment says why: 86,400 is the number of seconds in 24 x 60 x 60. The formula shows `/ 86,400 s/day`.
- Lines 70 to 72: the **peak rate**. `average.scaledBy(peakFactor.amount())` multiplies by the peak factor, a plain number: 1,157.4 x 26 = 30,092.6 requests/s, printed as `30,093 requests/s`. The peak factor is the ratio of the busiest rate to the day's average. For the national sale the sheet derived 26 from 1,000,000 fans x 6 requests / 300 s x 1.5 = 30,000 requests/s.
- Lines 73 to 77: **reads at peak**. The sheet gives the write share (15 percent). `writeShare.to(Unit.RATIO)` converts it to the ratio 0.15 (15 x 0.01 / 1). `BigDecimal.ONE.subtract(writeFraction)` is the read fraction, 0.85. `new Quantity(readFraction, Unit.RATIO).to(Unit.PERCENT)` converts it to 85 percent, for the formula text. Reads at peak are `peak.scaledBy(readFraction)` = 30,092.6 x 0.85 = 25,579 requests/s.
- Lines 78 to 83: **writes at peak** = 30,092.6 x 0.15 = 4,514 requests/s. The call is spread over several lines because each argument is on its own line, the layout the formatter enforces.
- Lines 84 to 94: the **read-write ratio** is added only when the write share is above zero (`writeFraction.signum() > 0`). Dividing by a zero write share would fail, so with a write share of 0 the step is left out. When present: `readFraction.divide(writeFraction, Quantity.PRECISION)` = 0.85 / 0.15 = 5.67 reads per write, as a `Quantity` in `Unit.READS_PER_WRITE`.

**Lines 97 to 115: `estimateSize`, the size**

```java
  private void estimateSize() {
    Quantity total = new Quantity(BigDecimal.ZERO, Unit.BYTES_PER_DAY);
    for (Store store : inputs.stores()) {
      Quantity perDay = newBytesPerDay(store);
      add(
          "size." + store.name(),
          SIZE,
          "New " + store.name() + " data per day",
          store.get(StoreField.ITEMS_PER_DAY) + TIMES + store.get(StoreField.BYTES_PER_ITEM),
          perDay.to(Unit.GIGABYTES_PER_DAY));
      total = total.plus(perDay);
    }
    add(
        "size.total",
        SIZE,
        "New data per day, all stores",
        "sum of the lines above",
        total.to(Unit.GIGABYTES_PER_DAY));
  }
```

- The **size** is the new data written per day. It starts from a total of zero bytes/day. For each store, `newBytesPerDay(store)` (defined at line 320) is items per day times bytes per item, in `BYTES_PER_DAY`. The step shows it in GB/day with `perDay.to(Unit.GIGABYTES_PER_DAY)`. The step id is `"size." + store.name()`, so each store gets its own id.
- Production, bookings: 500,000 items/day x 2,000 bytes = 1,000,000,000 bytes/day = 1 GB/day. Logs: 100,000,000 x 1,000 = 100,000,000,000 bytes/day = 100 GB/day.
- `total = total.plus(perDay)` adds in the same unit, bytes/day. This is the line that would throw if a store's amount were in another unit. `Quantity` is immutable, so `plus` returns a new quantity and the variable is reassigned each turn of the loop.
- After the loop, the total step shows the sum in GB/day: the four stores give 101.07 GB/day, printed as `101 GB/day`.

**Lines 117 to 137: `estimateStorage`, the storage**

```java
  private void estimateStorage() {
    Quantity horizonDays = input(InputRule.HORIZON_YEARS).to(Unit.DAYS);
    Quantity total = new Quantity(BigDecimal.ZERO, Unit.BYTES);
    for (Store store : inputs.stores()) {
      total = total.plus(storedBytes(store, horizonDays));
    }
    add(
        "storage.total",
        STORAGE,
        "Stored at the horizon, all stores",
        "sum of the stored lines above",
        total.to(Unit.TERABYTES));
    Quantity price = input(InputRule.STORAGE_PRICE);
    Quantity gigabytes = total.to(Unit.GIGABYTES);
    add(
        "storage.cost",
        STORAGE,
        "Storage cost per month at the horizon",
        gigabytes + TIMES + price,
        product(Unit.USD_PER_MONTH, gigabytes, price));
  }
```

- The **storage** is how much data is stored at the end of the horizon. The horizon, in years, is converted to days: `input(InputRule.HORIZON_YEARS).to(Unit.DAYS)` = 5 x 365 = 1,825 days.
- Each store adds its stored bytes (`storedBytes`, next). `total` starts at zero bytes and grows with `plus`. The total step shows terabytes: 35.72 TB for production.
- Lines 129 to 136: the **storage cost per month**. The price is in USD per GB-month. `total.to(Unit.GIGABYTES)` gives 35,722 GB. `product(Unit.USD_PER_MONTH, gigabytes, price)` = 35,722 x 0.023 = 821.6 USD/month. The cost is for keeping the full amount for one month, at the horizon.

**Lines 139 to 176: `storedBytes`, per store**

```java
  private Quantity storedBytes(Store store, Quantity horizonDays) {
    String name = store.name();
    Quantity perDay = newBytesPerDay(store);
    Quantity perYear =
        new Quantity(perDay.amount().multiply(Unit.YEARS.sizeInBaseUnits()), Unit.BYTES);
    add(
        "storage." + name + ".year",
        STORAGE,
        name + ": one year of data",
        perDay + " x 365 days",
        perYear.to(Unit.TERABYTES));
    Quantity retention = store.get(StoreField.RETENTION_DAYS);
    Quantity keptDays =
        retention.amount().compareTo(horizonDays.amount()) < 0 ? retention : horizonDays;
    add(
        "storage." + name + ".days",
        STORAGE,
        name + ": days kept",
        "the smaller of " + retention + " and " + horizonDays,
        keptDays);
    Quantity raw = product(Unit.BYTES, perDay, keptDays);
    add(
        "storage." + name + ".raw",
        STORAGE,
        name + ": one copy",
        perDay + TIMES + keptDays,
        raw.to(Unit.TERABYTES));
    Quantity copies = store.get(StoreField.REPLICATION_FACTOR);
    Quantity overhead = store.get(StoreField.INDEX_OVERHEAD);
    Quantity stored = product(Unit.BYTES, raw, copies, overhead);
    add(
        "storage." + name + ".stored",
        STORAGE,
        name + ": stored with copies and indexes",
        raw.to(Unit.TERABYTES) + TIMES + copies + TIMES + overhead,
        stored.to(Unit.TERABYTES));
    return stored;
  }
```

- `perDay` is again items/day x bytes/item. `perYear` multiplies by `Unit.YEARS.sizeInBaseUnits()`, which is the number 365: the bytes written in one year. It is not used in the total. It is a step for the reader: "how much data is one year of this store". Production, bookings: 1,000,000,000 x 365 = 0.365 TB.
- Lines 150 to 152: **days kept**. A store keeps an item for `retention_days`, but the estimate looks only `horizon` days ahead. So the number of days that count is the smaller of the two. `retention.amount().compareTo(horizonDays.amount()) < 0 ? retention : horizonDays` is a conditional expression: if the retention is below the horizon, use the retention, otherwise the horizon. Bookings: retention 2,555 days is above the horizon 1,825, so 1,825. Logs: retention 90 days is below, so 90.
- Lines 159 to 165: **one copy** is `perDay` x `keptDays` bytes. Bookings: 1,000,000,000 bytes/day x 1,825 days = 1.825 TB (printed `1.83 TB`).
- Lines 166 to 174: **stored with copies and indexes** = one copy x copies x index overhead. Bookings: 1.825 TB x 3 copies x 1.5 = 8.2125 TB. A replica is a full second copy of the data. An index is a structure that helps find records fast, and it takes extra space. The overhead is the bytes stored per byte of data.
- `return stored;` hands back the quantity in bytes so `estimateStorage` can add it up. Note `product(Unit.BYTES, perDay, keptDays)`: bytes/day times days is bytes. The units are named by the caller at each call, so the unit of each formula is visible where it is written.

**Lines 178 to 215: `estimateBandwidth`, the bandwidth**

```java
  private void estimateBandwidth() {
    Quantity peak = result("rate.peak");
    Quantity responseBytes = input(InputRule.RESPONSE_BYTES);
    Quantity egress = product(Unit.BYTES_PER_SECOND, peak, responseBytes);
    add("bandwidth.egress_peak", BANDWIDTH, "Egress at peak", peak + TIMES + responseBytes, egress);
    // Bytes to bits: the unit conversion multiplies by 8 and divides by 10^9.
    add(
        "bandwidth.egress_peak_gbps",
        BANDWIDTH,
        "Egress at peak, in bits",
        egress + " x 8 bits/byte",
        egress.to(Unit.GIGABITS_PER_SECOND));
    Quantity requestBytes = input(InputRule.REQUEST_BYTES);
    Quantity ingress = product(Unit.BYTES_PER_SECOND, peak, requestBytes);
    add(
        "bandwidth.ingress_peak_gbps",
        BANDWIDTH,
        "Ingress at peak, in bits",
        peak + TIMES + requestBytes + " x 8 bits/byte",
        ingress.to(Unit.GIGABITS_PER_SECOND));
    Quantity perDay = result("rate.per_day");
    Quantity month = new Quantity(DAYS_PER_MONTH, Unit.DAYS);
    Quantity monthly = product(Unit.BYTES_PER_MONTH, perDay, responseBytes, month);
    add(
        "bandwidth.egress_month",
        BANDWIDTH,
        "Egress per 30-day month",
        perDay + TIMES + responseBytes + TIMES + month,
        monthly.to(Unit.TERABYTES_PER_MONTH));
    Quantity gigabytes = monthly.to(Unit.GIGABYTES_PER_MONTH);
    Quantity price = input(InputRule.EGRESS_PRICE);
    add(
        "bandwidth.cost",
        BANDWIDTH,
        "Egress cost per month",
        gigabytes + TIMES + price,
        product(Unit.USD_PER_MONTH, gigabytes, price));
  }
```

- **Bandwidth** is how much data moves per second. `result("rate.peak")` fetches the peak rate computed earlier (line 331). Egress is data leaving to the internet: peak rate times response size. 30,092.6 requests/s x 38,212 bytes = 1,149,898,148 bytes/s.
- Lines 183 to 189: the same amount in **gigabits per second**. The comment says the conversion multiplies by 8 and divides by 10^9. The unit table does it in one step: 1,149,898,148 bytes/s x 1 / 125,000,000 = 9.199 Gbps. Network links are sold in bits per second, which is why the step exists. A byte has 8 bits.
- Lines 190 to 197: **ingress** (data coming in) is the peak rate times the request size: 30,092.6 x 300 = 9,027,778 bytes/s = 0.07222 Gbps.
- Lines 198 to 206: **egress per month**. `result("rate.per_day")` is 100,000,000 requests/day. The month is a `Quantity` of 30 days. `product(Unit.BYTES_PER_MONTH, perDay, responseBytes, month)` multiplies requests/day x bytes x days = 114,636,000,000,000 bytes per month, shown as 114.6 TB/month (printed `115 TB/month`).
- Lines 207 to 214: **egress cost**. `monthly.to(Unit.GIGABYTES_PER_MONTH)` = 114,636 GB/month. Times the egress price: 114,636 x 0.09 USD/GB = 10,317.24 USD/month. The README notes this is an upper bound: later price tiers are cheaper.

**Lines 217 to 247: `estimateServers`, the servers**

```java
  private void estimateServers() {
    Quantity knee = input(InputRule.KNEE_PER_SERVER);
    Quantity utilization = input(InputRule.UTILIZATION_TARGET);
    Quantity safe = knee.scaledBy(utilization.to(Unit.RATIO).amount());
    add("servers.safe_rate", SERVERS, "Safe rate per server", knee + TIMES + utilization, safe);
    Quantity headroom = input(InputRule.HEADROOM);
    BigDecimal withHeadroom = BigDecimal.ONE.add(headroom.to(Unit.RATIO).amount());
    Quantity peakServers = fleetFor("peak", result("rate.peak"), safe, withHeadroom);
    Quantity averageServers = fleetFor("average", result("rate.average"), safe, withHeadroom);
    Quantity price = input(InputRule.SERVER_PRICE);
    Quantity peakHour = product(Unit.USD_PER_HOUR, peakServers, price);
    add(
        "servers.peak_hour_cost",
        SERVERS,
        "Peak fleet cost per hour",
        peakServers + TIMES + price,
        peakHour);
    add(
        "servers.peak_month_cost",
        SERVERS,
        "Peak fleet cost per month, if kept all month",
        peakHour + " x 730 hours",
        monthOf(peakHour));
    Quantity averageMonth = monthOf(product(Unit.USD_PER_HOUR, averageServers, price));
    add(
        "servers.average_month_cost",
        SERVERS,
        "Average fleet cost per month",
        averageServers + TIMES + price + " x 730 hours",
        averageMonth);
  }
```

- The knee is the rate at which one server's p99 latency starts to jump (a measured value). A server is planned to run at the **utilization target** of that rate. `utilization.to(Unit.RATIO).amount()` turns 70 percent into 0.7. The safe rate per server is 800 requests/s x 0.7 = 560 requests/s.
- Headroom is extra capacity for spikes the estimate missed. `BigDecimal.ONE.add(headroom.to(Unit.RATIO).amount())` is the multiplier 1 + 0.2 = 1.2.
- `fleetFor` (next) computes the server count for the peak rate and for the average rate.
- Lines 226 to 233: the **peak fleet cost per hour**. 66 servers x 0.2016 USD/hour = 13.3056 USD/hour, printed `13.31 USD/hour`.
- Lines 234 to 239: the **peak fleet cost per month**, if kept all month. `monthOf(peakHour)` multiplies by 730 hours: 13.3056 x 730 = 9,713.09 USD/month.
- Lines 240 to 246: the **average fleet cost per month**: 4 servers x 0.2016 x 730 = 588.67 USD/month. This is the figure used in the total cost, because a fleet that follows the load costs about that much. The peak fleet is only kept for the sale.

**Lines 249 to 272: `fleetFor`, how many servers**

```java
  private Quantity fleetFor(String load, Quantity rate, Quantity safe, BigDecimal withHeadroom) {
    Quantity planned = rate.scaledBy(withHeadroom);
    add(
        "servers." + load + "_planned",
        SERVERS,
        "Planned " + load + " rate with headroom",
        rate + " x " + withHeadroom.toPlainString() + " (1 + headroom)",
        planned);
    // Round up: 53.7 servers means 54, because 53 would run above the utilization target.
    BigDecimal needed =
        planned
            .amount()
            .divide(safe.amount(), Quantity.PRECISION)
            .setScale(0, RoundingMode.CEILING);
    Quantity spare = input(InputRule.SPARE_SERVERS);
    Quantity servers = new Quantity(needed, Unit.SERVERS).plus(spare);
    add(
        "servers." + load,
        SERVERS,
        "Servers for the " + load + " rate",
        "ceiling of " + planned + " / " + safe + ", + " + spare + " (spare)",
        servers);
    return servers;
  }
```

- The method takes a load name (`"peak"` or `"average"`), the rate, the safe rate per server and the headroom multiplier.
- Lines 250 to 256: the **planned rate** is the rate times the headroom multiplier. Peak: 30,092.6 x 1.2 = 36,111 requests/s. `withHeadroom.toPlainString()` prints the multiplier as `1.2` (plain, not scientific). The step id is built with the load name: `servers.peak_planned`.
- Lines 257 to 262: the servers needed. `planned.amount().divide(safe.amount(), Quantity.PRECISION)` = 36,111.1 / 560 = 64.48. `.setScale(0, RoundingMode.CEILING)` rounds **up** to a whole number: 65. The comment says why: 53.7 servers means 54, because 53 would run above the utilization target. `CEILING` rounds toward positive infinity: 64.01 and 64.99 both become 65. This is the only place in the estimate that rounds a result up on purpose.
- Lines 263 to 270: add the **spare** servers. `new Quantity(needed, Unit.SERVERS).plus(spare)` = 65 + 1 = 66. A spare server lets one fail, or be deployed, without losing capacity. For the average rate: 1,388.9 / 560 = 2.48, up to 3, plus 1 = 4. The id is `servers.peak` or `servers.average`.
- `return servers;` gives the count to the caller, which multiplies by the price.

**Lines 274 to 301: `estimateCache`, the cache memory**

```java
  private void estimateCache() {
    Quantity keys = input(InputRule.CACHE_KEYS);
    Quantity hotShare = input(InputRule.CACHE_HOT_SHARE);
    Quantity hotKeys = keys.scaledBy(hotShare.to(Unit.RATIO).amount());
    add("cache.hot_keys", CACHE, "Hot keys", keys + TIMES + hotShare, hotKeys);
    Quantity entry = input(InputRule.CACHE_ENTRY_BYTES);
    Quantity overhead = input(InputRule.CACHE_OVERHEAD);
    Quantity hot = product(Unit.BYTES, hotKeys, entry, overhead);
    add(
        "cache.hot_memory",
        CACHE,
        "Memory for the hot set",
        hotKeys + TIMES + entry + TIMES + overhead,
        hot.to(Unit.GIGABYTES));
    add(
        "cache.hot_memory_gib",
        CACHE,
        "Memory for the hot set, in binary units",
        hot.to(Unit.GIGABYTES) + " / 1.073741824",
        hot.to(Unit.GIBIBYTES));
    Quantity full = product(Unit.BYTES, keys, entry, overhead);
    add(
        "cache.full_memory",
        CACHE,
        "Memory to cache every key, for comparison",
        keys + TIMES + entry + TIMES + overhead,
        full.to(Unit.GIGABYTES));
  }
```

- A **cache** keeps recent answers in memory so that they are fast to get again. The sheet gives the total number of keys, the share that is hot (read most), the size of a value and an overhead multiplier.
- Hot keys: 400,000 keys x 5 percent (0.05) = 20,000 keys. Memory for the hot set: `product(Unit.BYTES, hotKeys, entry, overhead)` = 20,000 x 37,000 bytes x 1.25 = 925,000,000 bytes = 0.925 GB.
- Lines 288 to 293: the same memory in **GiB**. The formula text says `/ 1.073741824`. 0.925 GB / 1.073741824 = 0.8615 GiB. Memory is sold in the binary unit, so the report gives both. A cloud instance with 1 GiB of memory could hold the hot set.
- Lines 294 to 300: the memory to cache **every** key, for comparison: 400,000 x 37,000 x 1.25 = 18.5 GB. The report prints the pair so the reader sees what the hot-set choice saves.

**Lines 303 to 313: `estimateCost`, the monthly cost**

```java
  private void estimateCost() {
    Quantity storage = result("storage.cost");
    Quantity egress = result("bandwidth.cost");
    Quantity servers = result("servers.average_month_cost");
    add(
        "cost.month",
        COST,
        "Monthly cost: storage, egress and the average fleet",
        storage + " + " + egress + " + " + servers,
        storage.plus(egress).plus(servers));
  }
```

- The cost is the sum of three earlier results, each fetched by id: the storage cost (`storage.cost`), the egress cost (`bandwidth.cost`) and the average fleet cost (`servers.average_month_cost`). `storage.plus(egress).plus(servers)` adds quantities in the same unit, `USD/month`, so the `Quantity.plus` check passes. 821.60 + 10,317.24 + 588.67 = 11,727.51 USD/month.

**Lines 315 to 348: the helpers**

```java
  private static Quantity monthOf(Quantity perHour) {
    return new Quantity(
        perHour.amount().multiply(BILLING_HOURS_PER_MONTH, Quantity.PRECISION), Unit.USD_PER_MONTH);
  }

  private Quantity newBytesPerDay(Store store) {
    return product(
        Unit.BYTES_PER_DAY,
        store.get(StoreField.ITEMS_PER_DAY),
        store.get(StoreField.BYTES_PER_ITEM));
  }

  private Quantity input(InputRule rule) {
    return inputs.get(rule);
  }

  private Quantity result(String id) {
    return new Estimate(steps).result(id);
  }

  private void add(String id, String section, String label, String formula, Quantity result) {
    steps.add(new Step(id, section, label, formula, result));
  }

  // A product of quantities has a new unit, which the caller names: users x requests/user/day is
  // requests/day. Naming it at every call keeps each formula's unit visible where it is written.
  private static Quantity product(Unit resultUnit, Quantity... factors) {
    BigDecimal amount = BigDecimal.ONE;
    for (Quantity factor : factors) {
      amount = amount.multiply(factor.amount(), Quantity.PRECISION);
    }
    return new Quantity(amount, resultUnit);
  }
}
```

- `monthOf` multiplies a price per hour by 730 hours and names the result unit `USD/month`. The call uses `BILLING_HOURS_PER_MONTH`. It is `static` because it needs no instance data.
- `newBytesPerDay` is items per day times bytes per item, in `BYTES_PER_DAY`.
- `input(rule)` reads an input. `result(id)` asks an `Estimate` made from the steps so far: `new Estimate(steps).result(id)`. That copies the list each time, which does not matter at fifty steps.
- `add` records one step.
- `product` is the one place that multiplies. Its parameter `Quantity... factors` is a **varargs** parameter: the caller passes any number of quantities, and Java gives the method an array. `product(Unit.BYTES, perDay, keptDays)` passes two, `product(Unit.BYTES, raw, copies, overhead)` passes three. The loop starts from `BigDecimal.ONE` and multiplies each amount in with `Quantity.PRECISION` (16 digits). The comment explains why the caller names the unit: a product of quantities has a new unit, which Java cannot derive, so naming it at every call keeps each formula's unit visible. The method checks no unit. That is the price of a short helper, and `EstimateReportTest` checks that every printed step ends with a known unit symbol.

### `Sensitivity.java` and `SensitivityRow.java`

**The idea.** Every formula of the estimator is a product or a sum. Doubling one input of a product doubles the result, so a result can move by about 2 times. If a result moves by more than 10 times when one input doubles, a formula grows faster than its input, and in an estimate that is almost always a mistake. The check tries each input in turn and reports the result that moved most.

**`SensitivityRow.java`**

```java
package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import java.util.Objects;

/**
 * What happened to the estimate when one input was doubled, or halved when doubling would leave its
 * range.
 *
 * @param quantity the input that was changed
 * @param change "doubled" or "halved"
 * @param mostMovedStep the label of the result that moved most
 * @param movement how many times larger or smaller that result became, always 1 or more
 * @param isFlagged true when the movement is above the flag factor: a sign of a broken formula
 */
public record SensitivityRow(
    String quantity, String change, String mostMovedStep, Quantity movement, boolean isFlagged) {

  /**
   * Creates a row.
   *
   * @throws NullPointerException if any text or the movement is null
   */
  public SensitivityRow {
    Objects.requireNonNull(quantity, "quantity");
    Objects.requireNonNull(change, "change");
    Objects.requireNonNull(mostMovedStep, "mostMovedStep");
    Objects.requireNonNull(movement, "movement");
  }
}
```

- A `SensitivityRow` is one row of the check. `quantity` is the input that was changed. `change` is the text `doubled` or `halved`. `mostMovedStep` is the label of the result that moved most. `movement` is a `Quantity` in `times`: how many times larger or smaller that result became, always 1 or more. `isFlagged` is true when the movement is above the flag factor.
- The compact constructor rejects nulls in the four reference components. `isFlagged` is a `boolean`, which cannot be null.

**`Sensitivity.java`, lines 1 to 32: imports, constants, class header**

```java
package io.plainround.seatwave.estimates.estimate;

import com.github.sviperll.result4j.Result;
import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Doubles each input in turn and reports the result that moved most.
 *
 * <p>Every formula of the estimator is a product or a sum, so doubling one input can move a result
 * by at most a small factor. A result that moves more than {@link #FLAG_FACTOR} times means a
 * formula grows faster than its inputs, which in an estimate is almost always a mistake. Stateless
 * and safe to use from any thread.
 */
public final class Sensitivity {

  /** The prompt's rule: a result that changes more than ten times when one input doubles. */
  public static final BigDecimal FLAG_FACTOR = new BigDecimal("10");

  private static final BigDecimal TWO = new BigDecimal("2");
  private static final MathContext MOVEMENT_DIGITS = new MathContext(6);

  private Sensitivity() {}
```

- `Function<Inputs, Estimate>` is a type from `java.util.function`: "a function that takes `Inputs` and gives an `Estimate`". It lets the estimator be passed in as a value.
- `Collectors` is used to build a map from a stream.
- `FLAG_FACTOR` is 10, public so that the report and the tests can read it. `TWO` is the number 2. `MOVEMENT_DIGITS = new MathContext(6)` is a setting for 6 significant digits. Its rounding mode is the default, `HALF_UP`.
- The Javadoc says a result that moves more than `FLAG_FACTOR` times means a formula grows faster than its inputs.

**Lines 34 to 55: `check`**

```java
  /**
   * Runs the check.
   *
   * @param entries the sheet entries the estimate came from
   * @param estimator the formula to test; the real one is {@code Estimator::estimate}
   * @return one row per entry, in sheet order
   * @throws IllegalArgumentException if the entries themselves do not make valid inputs
   */
  public static List<SensitivityRow> check(
      List<SheetEntry> entries, Function<Inputs, Estimate> estimator) {
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(estimator, "estimator");
    Estimate baseline =
        estimator.apply(
            inputsFrom(entries)
                .orElseThrow(() -> new IllegalArgumentException("the sheet is not valid")));
    List<SensitivityRow> rows = new ArrayList<>();
    for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
      rows.add(vary(entries, entryIndex, baseline, estimator));
    }
    return List.copyOf(rows);
  }
```

- `check(List<SheetEntry> entries, Function<Inputs, Estimate> estimator)` takes the sheet entries and the formula to test. The real one is `Estimator::estimate`, a **method reference**: a short way to write the lambda `inputs -> Estimator.estimate(inputs)`. Passing the formula in lets a test hand it a deliberately broken formula and see the flag.
- Lines 46 to 49: the **baseline** is the estimate of the unchanged sheet. `inputsFrom(entries)` returns an `Optional<Inputs>`. `.orElseThrow(() -> new IllegalArgumentException("the sheet is not valid"))` takes the value out or throws. The check is only called after a sheet has been accepted, so an invalid sheet here is a bug.
- Lines 50 to 54: for each entry, in sheet order, `vary(...)` returns a row. The method returns an unmodifiable copy of the list. There is one row per entry, so the report has one row per input.

**Lines 57 to 76: `vary`**

```java
  private static SensitivityRow vary(
      List<SheetEntry> entries,
      int entryIndex,
      Estimate baseline,
      Function<Inputs, Estimate> estimator) {
    SheetEntry entry = entries.get(entryIndex);
    BigDecimal doubled = entry.value().multiply(TWO);
    Optional<Inputs> changed = inputsFrom(replaced(entries, entryIndex, doubled));
    String change = "doubled";
    if (changed.isEmpty()) {
      // A share of 85 percent cannot double, so it is halved: the test still moves it by 2 times.
      changed =
          inputsFrom(replaced(entries, entryIndex, entry.value().divide(TWO, Quantity.PRECISION)));
      change = "halved";
    }
    // Every range allows either the double or the half of a valid value, so this cannot be empty.
    Inputs varied =
        changed.orElseThrow(() -> new IllegalStateException("cannot vary " + entry.quantity()));
    return compare(entry.quantity(), change, baseline, estimator.apply(varied));
  }
```

- Line 62: the entry at this position. Line 63: `entry.value().multiply(TWO)` is the doubled value. Line 64: `replaced(entries, entryIndex, doubled)` builds a copy of the list with that one entry changed, and `inputsFrom(...)` runs it through `InputsReader`, so a doubled value that breaks a rule is caught by the same rules the learner meets. The answer is an `Optional<Inputs>`: empty when the doubled sheet is invalid.
- Lines 65 to 71: if the doubled sheet is not valid, try halving. A utilization target of 70 percent cannot be doubled (140 percent is above 100), so it is halved to 35: the test still moves it by 2 times. `entry.value().divide(TWO, Quantity.PRECISION)` divides with 16 significant digits. The text in `change` is switched to `halved`.
- Lines 72 to 75: the comment says every range allows either the double or the half of a valid value, so the optional cannot be empty here. `orElseThrow(() -> new IllegalStateException(...))` throws an `IllegalStateException`, the standard exception for "the program is in a state that should be impossible", if it is. Then `compare(...)` compares the baseline with the changed estimate.
- A lambda such as `() -> new IllegalStateException("cannot vary " + entry.quantity())` uses `entry`, a variable from the surrounding method. Java allows this because `entry` is never reassigned (it is **effectively final**).

**Lines 78 to 98: `compare`**

```java
  private static SensitivityRow compare(
      String quantity, String change, Estimate baseline, Estimate changed) {
    Map<String, Quantity> changedById =
        changed.steps().stream().collect(Collectors.toMap(Step::id, Step::result));
    String mostMoved = "no result moved";
    Quantity largest = new Quantity(BigDecimal.ONE, Unit.TIMES);
    for (Step step : baseline.steps()) {
      Quantity after = changedById.get(step.id());
      // A zero has no ratio; such a step is skipped rather than counted as an infinite move.
      if (after == null || after.amount().signum() == 0 || step.result().amount().signum() == 0) {
        continue;
      }
      Quantity movement = movementBetween(step.result(), after);
      if (movement.amount().compareTo(largest.amount()) > 0) {
        largest = movement;
        mostMoved = step.label();
      }
    }
    boolean isFlagged = largest.amount().compareTo(FLAG_FACTOR) > 0;
    return new SensitivityRow(quantity, change, mostMoved, largest, isFlagged);
  }
```

- Line 80: `changed.steps().stream().collect(Collectors.toMap(Step::id, Step::result))` builds a `Map<String, Quantity>` from step id to result, so each baseline step can find its partner in the changed estimate. `Step::id` and `Step::result` are method references to the accessors of the record.
- Lines 82 and 83: the search starts with "no result moved" and a largest movement of 1 times.
- Lines 84 to 95: for each baseline step, get its partner (`changedById.get(step.id())`). Line 87 skips the step when the partner is missing (`after == null`), or when either result is zero. The comment says a zero has no ratio, so such a step is skipped and not counted as an infinite move. `||` means "or"; Java stops at the first true, so `after.amount()` is never called on a null.
- Line 90: `movementBetween` gives the movement as a quantity in `times`. Lines 91 to 94: if it is larger than the largest so far (`compareTo(...) > 0`), remember it and the step's label. The test is strict, so when two steps move by the same amount, the first one in the list wins.
- Lines 96 and 97: the row is flagged when the largest movement is above 10. `new SensitivityRow(...)` builds it.

**Lines 100 to 107: `movementBetween`**

```java
  private static Quantity movementBetween(Quantity before, Quantity after) {
    Quantity grown = after.timesAsMany(before);
    // Shrinking to a tenth is as suspicious as growing ten times, so both count the same way.
    Quantity movement =
        grown.amount().compareTo(BigDecimal.ONE) >= 0 ? grown : before.timesAsMany(after);
    // Rounding hides the last-digit noise of division, so equal moves tie and the first one wins.
    return new Quantity(movement.amount().round(MOVEMENT_DIGITS), Unit.TIMES);
  }
```

- `after.timesAsMany(before)` is how many times larger the new result is than the old one. If it is 1 or more, that is the movement. If it is below 1, the result shrank, and `before.timesAsMany(after)` gives how many times smaller. The comment says shrinking to a tenth is as suspicious as growing ten times, so both count the same way: the movement is always 1 or more.
- `timesAsMany` refuses two different units, so a formula that changed the unit of a result when an input changed would throw here.
- Line 106 rounds to 6 significant digits (`round(MOVEMENT_DIGITS)`). The comment says rounding hides the last-digit noise of division, so equal moves tie and the first one wins. Without it, 2.0000000000000001 would beat 2.

**Lines 109 to 121: `replaced` and `inputsFrom`**

```java
  private static List<SheetEntry> replaced(
      List<SheetEntry> entries, int entryIndex, BigDecimal value) {
    List<SheetEntry> copy = new ArrayList<>(entries);
    copy.set(entryIndex, entries.get(entryIndex).withValue(value));
    return copy;
  }

  private static Optional<Inputs> inputsFrom(List<SheetEntry> entries) {
    return switch (InputsReader.read(entries)) {
      case Result.Success<Inputs, List<SheetProblem>>(Inputs inputs) -> Optional.of(inputs);
      case Result.Error<Inputs, List<SheetProblem>> _ -> Optional.empty();
    };
  }
}
```

- `replaced` copies the list (`new ArrayList<>(entries)`) and changes one position (`copy.set(index, entry.withValue(value))`). The original list is not touched.
- `inputsFrom` is a `switch` that gives a value, over the `Result` that `InputsReader.read` returns. It has two arms. `case Result.Success<Inputs, List<SheetProblem>>(Inputs inputs) -> Optional.of(inputs);` is a **record pattern**: it matches a `Success` and binds its component to the variable `inputs`. `case Result.Error<Inputs, List<SheetProblem>> _ -> Optional.empty();` matches an `Error`. The `_` is the **unnamed pattern**: it says "there is a value here and I do not need it". The problems themselves do not matter, only that the sheet is invalid. The unnamed pattern needs Java 22. The two arms cover both cases of a sealed interface, so there is no `default`.

**What the check prints.** For the production sheet, the largest movements are all small. Doubling the write share moves the read-write ratio by 2.43 times (from 5.67 to 2.33 reads per write). Doubling the headroom moves the planned peak rate by 1.17 times: 1.4 / 1.2. Doubling the spare servers moves the average fleet by 1.25 times: from 4 servers to 5. Halving the utilization target moves the safe rate by 2 times. Doubling a retention that is already above the horizon moves nothing, so the row says `1 times` and `no result moved`. No row is above 10, so the last line of the report is `No result moved more than 10 times when any one input doubled.`

### `EstimateReport.java`

**Lines 1 to 23: imports, constants, class header**

```java
package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders an estimate as text: the inputs as a sheet, every step, the five-line estimate and the
 * sensitivity check.
 *
 * <p>Every number in the text comes from a {@code Quantity}, so every number carries its unit. The
 * inputs block is itself a valid sheet, so anyone can reproduce the estimate from the printout.
 * Stateless and safe to use from any thread.
 */
public final class EstimateReport {

  private static final String NEW_LINE = System.lineSeparator();
  private static final String INDENT = "  ";
  private static final Quantity FLAG = new Quantity(Sensitivity.FLAG_FACTOR, Unit.TIMES);

  private EstimateReport() {}
```

- `NEW_LINE = System.lineSeparator()` is the line break of the machine the program runs on: `\n` on macOS and Linux, `\r\n` on Windows. `INDENT` is two spaces. `FLAG` is the number 10 as a `Quantity` in `times`, so the report prints `10 times`.
- The Javadoc says the inputs block is itself a valid sheet, so anyone can reproduce the estimate from the printout. A test reads it back and gets an equal estimate.

**Lines 25 to 52: `render`**

```java
  /**
   * Renders the report.
   *
   * @param entries the sheet entries the estimate came from
   * @param estimate the estimate
   * @param sensitivity the rows of the sensitivity check
   * @return the report, ending with a line break
   */
  public static String render(
      List<SheetEntry> entries, Estimate estimate, List<SensitivityRow> sensitivity) {
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(estimate, "estimate");
    Objects.requireNonNull(sensitivity, "sensitivity");
    StringBuilder text = new StringBuilder();
    text.append(
            "INPUTS (a complete sheet: save these lines as a .csv file to reproduce this estimate)")
        .append(NEW_LINE);
    CsvSheet.render(entries).forEach(line -> text.append(line).append(NEW_LINE));
    text.append(NEW_LINE).append("STEPS").append(NEW_LINE);
    appendSteps(text, estimate);
    text.append(NEW_LINE).append("THE FIVE-LINE ESTIMATE").append(NEW_LINE);
    appendFiveLines(text, estimate);
    text.append(NEW_LINE)
        .append("SENSITIVITY (each input doubled, or halved when doubling leaves its range)")
        .append(NEW_LINE);
    appendSensitivity(text, sensitivity);
    return text.toString();
  }
```

- `StringBuilder` builds text by appending pieces. Adding strings with `+` in a loop makes many temporary strings. A `StringBuilder` keeps one growing buffer. `append` returns the builder itself, so calls chain: `text.append("a").append(NEW_LINE)`.
- Lines 39 to 41: the first line says the block is a complete sheet that can be saved as a `.csv` file. The long text is on its own line, and `.append(NEW_LINE)` follows it.
- Line 42: `CsvSheet.render(entries).forEach(line -> text.append(line).append(NEW_LINE));` writes the sheet lines. `forEach` calls a lambda for every line.
- Lines 43 to 50: a blank line and the `STEPS` heading, then `appendSteps`; a blank line and `THE FIVE-LINE ESTIMATE`, then `appendFiveLines`; a blank line and the `SENSITIVITY` heading, then `appendSensitivity`.
- Line 51: `text.toString()` turns the buffer into one string. The caller prints it. `render` itself prints nothing. That is the rule in `ARCHITECTURE.md`: only the command-line classes print.

**Lines 54 to 79: `hasFlag` and `appendSteps`**

```java
  /**
   * Tells whether any row of a sensitivity check was flagged.
   *
   * @param sensitivity the rows
   * @return true when at least one result moved more than the flag factor
   */
  public static boolean hasFlag(List<SensitivityRow> sensitivity) {
    return sensitivity.stream().anyMatch(SensitivityRow::isFlagged);
  }

  private static void appendSteps(StringBuilder text, Estimate estimate) {
    String section = "";
    for (Step step : estimate.steps()) {
      if (!step.section().equals(section)) {
        section = step.section();
        text.append(section).append(NEW_LINE);
      }
      text.append(INDENT)
          .append(step.label())
          .append(": ")
          .append(step.formula())
          .append(" = ")
          .append(step.result())
          .append(NEW_LINE);
    }
  }
```

- `hasFlag` asks the stream of rows: `anyMatch(SensitivityRow::isFlagged)` is true when at least one row is flagged. The method reference `SensitivityRow::isFlagged` is the accessor of the boolean component.
- `appendSteps` prints each step on one line: the label, a colon, the formula, an equals sign, and the result. `section` remembers the section of the previous step. When a step's section differs (`!step.section().equals(section)`), the section name is printed on its own line first. So the report has `RATE`, `SIZE`, `STORAGE`, `BANDWIDTH`, `SERVERS`, `CACHE` and `COST` as headings. `step.result()` is a `Quantity`, so `append` prints it with `toString`, with its unit.

**Lines 81 to 126: `appendFiveLines` and `line`**

```java
  private static void appendFiveLines(StringBuilder text, Estimate estimate) {
    line(
        text,
        "1. RATE      ",
        "average " + estimate.result("rate.average") + "; peak " + estimate.result("rate.peak"));
    line(text, "2. SIZE      ", estimate.result("size.total") + " of new data");
    line(
        text,
        "3. STORAGE   ",
        estimate.result("storage.total")
            + " stored at the horizon; "
            + estimate.result("storage.cost"));
    line(
        text,
        "4. BANDWIDTH ",
        "peak egress "
            + estimate.result("bandwidth.egress_peak_gbps")
            + "; "
            + estimate.result("bandwidth.egress_month")
            + "; "
            + estimate.result("bandwidth.cost"));
    line(
        text,
        "5. SERVERS   ",
        estimate.result("servers.peak")
            + " at peak, "
            + estimate.result("servers.average")
            + " at the average rate");
    line(
        text,
        "+  CACHE     ",
        estimate.result("cache.hot_memory")
            + " for the hot set, against "
            + estimate.result("cache.full_memory")
            + " for every key");
    line(
        text,
        "=  COST      ",
        estimate.result("cost.month")
            + " at the average fleet; the peak fleet costs "
            + estimate.result("servers.peak_hour_cost"));
  }

  private static void line(StringBuilder text, String heading, String content) {
    text.append(INDENT).append(heading).append(content).append(NEW_LINE);
  }
```

- Each of the seven lines of the five-line estimate pulls named results out of the estimate with `estimate.result("id")` and joins them with text. `+` joins a `Quantity` to a string by calling its `toString`.
- Line 1, RATE: the average and peak rates (`rate.average`, `rate.peak`). Line 2, SIZE: `size.total` plus the words `of new data`. Line 3, STORAGE: `storage.total` and `storage.cost`. Line 4, BANDWIDTH: `bandwidth.egress_peak_gbps`, `bandwidth.egress_month` and `bandwidth.cost`. Line 5, SERVERS: `servers.peak` and `servers.average`. The extra line, CACHE: `cache.hot_memory` and `cache.full_memory`. The last line, COST: `cost.month` and `servers.peak_hour_cost`.
- Every number comes from a `Quantity`, so every number in the five-line estimate has its unit. The heading texts are padded with spaces to the same width, so the numbers line up.
- `line` appends the indent, the heading and the content, and a line break.

**Lines 128 to 150: `appendSensitivity`**

```java
  private static void appendSensitivity(StringBuilder text, List<SensitivityRow> sensitivity) {
    for (SensitivityRow row : sensitivity) {
      text.append(INDENT)
          .append(
              String.format(
                  Locale.ROOT,
                  "%-34s %-8s %-12s %s%s",
                  row.quantity(),
                  row.change(),
                  row.movement(),
                  row.mostMovedStep(),
                  row.isFlagged() ? "   <-- FLAGGED" : ""))
          .append(NEW_LINE);
    }
    text.append(
            hasFlag(sensitivity)
                ? "FLAGGED: a result moved more than "
                    + FLAG
                    + " when one input doubled. Check that formula before using the estimate."
                : "No result moved more than " + FLAG + " when any one input doubled.")
        .append(NEW_LINE);
  }
}
```

- For each row, `String.format(Locale.ROOT, "%-34s %-8s %-12s %s%s", ...)` fills a template. `%-34s` means "text, left-aligned, in a field 34 characters wide": the `-` flag aligns left. The four values are the quantity name, the change, the movement (a `Quantity` printed with `toString`, for example `2.43 times`) and the step label. The last `%s` is either `   <-- FLAGGED` or an empty text, chosen with a conditional expression. `Locale.ROOT` keeps the format the same on every computer.
- Lines 142 to 148: the closing line. If any row is flagged, it says `FLAGGED: a result moved more than 10 times when one input doubled. Check that formula before using the estimate.` Otherwise `No result moved more than 10 times when any one input doubled.` The nested conditional expression chooses between two texts, and `FLAG` prints as `10 times`.

### `EstimateCli.java`

**Lines 1 to 26: imports, comment and constants**

```java
package io.plainround.seatwave.estimates.estimate;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point of the estimator: reads one sheet, prints the report.
 *
 * <p>Exit status 0 means a clean estimate. 1 means the sheet was rejected; every problem is
 * printed. 2 means the command was used wrongly or the file could not be read. 3 means the estimate
 * was printed but the sensitivity check flagged a result, so a script cannot use it by accident.
 */
public final class EstimateCli {

  private static final int EXIT_SHEET_REJECTED = 1;
  // Exit status 2 is the shell convention for "the command was used incorrectly".
  private static final int EXIT_BAD_USAGE = 2;
  private static final int EXIT_FLAGGED = 3;
  // A sheet has a few dozen lines; anything near a megabyte is the wrong file.
  private static final long MAX_SHEET_BYTES = 1_000_000L;

  private EstimateCli() {}
```

- `IOException` is the **checked exception** for a file that cannot be read. A checked exception must be caught or declared in the method header, and the compiler enforces that. `StandardCharsets` names text encodings. `Files` and `Path` read files and name them.
- The Javadoc gives the exit status. Every program that ends gives the operating system a number, its **exit status**. 0 means success. Non-zero means failure. This program uses 0 for a clean estimate, 1 for a rejected sheet, 2 for wrong use or an unreadable file, and 3 for an estimate that printed but was flagged by the sensitivity check. A script that runs the program can read these numbers. The comment says status 2 is the shell convention for "the command was used incorrectly".
- `MAX_SHEET_BYTES = 1_000_000L` is a limit. The `L` makes it a `long`, a 64-bit whole number. The underscores group the digits for the reader. A sheet has a few dozen lines, so a file near a megabyte is the wrong file, and the program refuses to read it.

**Lines 28 to 47: `main`**

```java
  /**
   * Runs the estimator.
   *
   * @param arguments one path to a sheet, for example {@code sheets/seatwave-production.csv}
   */
  public static void main(String[] arguments) {
    if (arguments.length != 1) {
      System.err.println("usage: estimate <sheet.csv>");
      System.exit(EXIT_BAD_USAGE);
    }
    List<String> lines = readSheet(Path.of(arguments[0]));
    switch (CsvSheet.parse(lines).flatMap(EstimateCli::withInputs)) {
      case Result.Success<Checked, List<SheetProblem>>(Checked checked) -> print(checked);
      case Result.Error<Checked, List<SheetProblem>>(List<SheetProblem> problems) -> {
        problems.forEach(problem -> System.err.println("REJECTED " + explain(problem)));
        System.err.println("SHEET REJECTED: fix every line above; nothing was estimated.");
        System.exit(EXIT_SHEET_REJECTED);
      }
    }
  }
```

- `public static void main(String[] arguments)` is the **entry point**: the method the JVM calls when the program starts. `String[] arguments` is the array of command-line arguments. The Gradle task passes the text after `--args=` here.
- Lines 34 to 37: if there is not exactly one argument, print a usage line to `System.err` (the error stream) and call `System.exit(2)`. `System.exit(n)` ends the program at once with status `n`.
- Line 38: read the file. `Path.of(arguments[0])` turns the text into a `Path`.
- Line 39: `CsvSheet.parse(lines).flatMap(EstimateCli::withInputs)` chains the two steps. `parse` returns a `Result`. `flatMap` calls the function you give it only if the result is a success, and returns what that function returns. If `parse` returned an error, the function is not called, and the same error comes back. So `withInputs` runs only when the CSV was well-formed. That is why a sheet with a bad column count is reported before its units are checked.
- Lines 40 to 45: the `switch` handles the two cases with record patterns. A **switch statement** (not an expression) runs code. `Result.Success<Checked, List<SheetProblem>>(Checked checked) -> print(checked);` matches a success and runs `print`. `Result.Error<...>(List<SheetProblem> problems) -> { ... }` matches an error: it prints one `REJECTED ...` line for each problem, with `problems.forEach(problem -> System.err.println("REJECTED " + explain(problem)))`, then the closing line, then exits with status 1. Because the interface is sealed and both cases are covered, no `default` is needed.
- Problems go to the error stream, and the report goes to `System.out` (standard output). A script can separate them.

**Lines 49 to 62: `Checked`, `withInputs` and `print`**

```java
  private record Checked(List<SheetEntry> entries, Inputs inputs) {}

  private static Result<Checked, List<SheetProblem>> withInputs(List<SheetEntry> entries) {
    return InputsReader.read(entries).map(inputs -> new Checked(entries, inputs));
  }

  private static void print(Checked checked) {
    Estimate estimate = Estimator.estimate(checked.inputs());
    List<SensitivityRow> sensitivity = Sensitivity.check(checked.entries(), Estimator::estimate);
    System.out.print(EstimateReport.render(checked.entries(), estimate, sensitivity));
    if (EstimateReport.hasFlag(sensitivity)) {
      System.exit(EXIT_FLAGGED);
    }
  }
```

- `private record Checked(List<SheetEntry> entries, Inputs inputs) {}` is a record that is private to this class. It keeps the entries and the inputs together, because the sensitivity check needs the entries and the estimator needs the inputs.
- `withInputs` calls `InputsReader.read(entries)` and uses `.map(inputs -> new Checked(entries, inputs))`. `map` is like `flatMap`, but the function returns a plain value, not a `Result`. It changes the success value and leaves an error alone.
- `print` runs the estimate: `Estimator.estimate(checked.inputs())`. It runs the sensitivity check: `Sensitivity.check(checked.entries(), Estimator::estimate)`. It prints the report with `System.out.print` (no extra line break, because the report ends with one). If any row was flagged, `System.exit(3)` follows, after the report is printed, so a script cannot take the estimate by accident.

**Lines 64 to 78: `readSheet`**

```java
  private static List<String> readSheet(Path path) {
    try {
      if (!Files.isRegularFile(path) || Files.size(path) > MAX_SHEET_BYTES) {
        System.err.println("not a sheet file of at most " + MAX_SHEET_BYTES + " bytes: " + path);
        System.exit(EXIT_BAD_USAGE);
      }
      return Files.readAllLines(path, StandardCharsets.UTF_8);
    } catch (IOException readFailure) {
      // The file vanished or is unreadable: report it as bad usage, with the reason the system
      // gave.
      System.err.println("cannot read " + path + ": " + readFailure.getMessage());
      System.exit(EXIT_BAD_USAGE);
      throw new IllegalStateException("System.exit returned", readFailure);
    }
  }
```

- `Files.isRegularFile(path)` is false for a missing file or a folder. `Files.size(path)` is the size in bytes. If the file is not a regular file, or it is bigger than the limit, the program prints a message and exits with 2.
- `Files.readAllLines(path, StandardCharsets.UTF_8)` reads the whole file as a list of lines. **UTF-8** is a way to store text as bytes that every system understands, and it covers every character. Naming it means the result does not depend on the computer's default.
- The `try` block holds code that may throw. `catch (IOException readFailure)` runs if reading fails (the file vanished or is unreadable). The message includes `readFailure.getMessage()`, the reason the operating system gave.
- The method must return a list on every path. After `System.exit(...)` the compiler does not know the program ended, so it needs a `return` or a `throw`. The last line, `throw new IllegalStateException("System.exit returned", readFailure)`, is that: it never runs. The second argument is the **cause**, the exception that led to this one, so a stack trace would show both.

**Lines 80 to 114: `explain`**

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
          "line "
              + wrong.lineNumber()
              + ": "
              + wrong.quantity()
              + " must be in "
              + wrong.expected()
              + ", not "
              + wrong.found();
      case SheetProblem.OutOfRange wrong ->
          "line " + wrong.lineNumber() + ": " + wrong.quantity() + " must be " + wrong.rule();
      case SheetProblem.Duplicate duplicate ->
          "line "
              + duplicate.lineNumber()
              + ": "
              + duplicate.quantity()
              + " was already given on line "
              + duplicate.firstLine();
      case SheetProblem.Missing missing -> "missing input: " + missing.quantity();
      case SheetProblem.NoStore _ -> "the sheet describes no store.<name>.<field> lines";
    };
  }
}
```

- `explain` turns a `SheetProblem` into one line of text for a person. It is a `switch` expression over the sealed interface, with one arm for each of the ten problem types, and no `default`. Add an eleventh problem and this method stops compiling, until it has an arm.
- `case SheetProblem.WrongHeader wrong ->` is a **type pattern**: if the value is a `WrongHeader`, name it `wrong` and use it in the arm. The names `wrong` and `missing` are reused in different arms, which is allowed because each is scoped to its own arm.
- The messages carry the line number and the facts: `line 9: response_bytes must be in bytes, not bits` is a `WrongUnit` with the expected symbol `bytes` and the found text `bits`. `line 4: daily_active_users was already given on line 3` is a `Duplicate`. `missing input: headroom` is a `Missing`.
- `case SheetProblem.NoStore _ ->` uses the unnamed pattern: the arm does not need the record. It needs Java 22.
- The method has no `public`, so it is package-private: the test in the same package calls it, and the rest of the code cannot.
- The last `}` closes the class.

## Try it yourself

Run these in `seatwave-estimates/`.

1. Run the estimator on the SIMPLE sheet and read the steps:

```
./gradlew -q estimate --args='sheets/seatwave-simple.csv'
```

The last block of the report is the five-line estimate. I got `1. RATE      average 4.63 requests/s; peak 13.89 requests/s`, `5. SERVERS   1 servers at peak, 1 servers at the average rate` and `=  COST      189.22 USD/month at the average fleet; the peak fleet costs 0.2016 USD/hour`. Look at the step `Servers for the peak rate`: it says `ceiling of 16.67 requests/s / 560 requests/s, + 0 servers (spare) = 1 servers`. 16.67 / 560 is 0.03, which rounds up to 1.

2. Run the sheet with unit slips:

```
./gradlew -q estimate --args='sheets/lab1-unit-slips.csv'
```

I got `REJECTED line 9: response_bytes must be in bytes, not bits`, then `REJECTED line 12: store.bookings.bytes_per_item must be in bytes, not KB`, then `REJECTED line 35: knee_per_server must be in requests/s, not requests/day`, then `REJECTED missing input: headroom` and `SHEET REJECTED: fix every line above; nothing was estimated.` Gradle added `finished with non-zero exit value 1`: that is the exit status of the program.

3. Make a sheet that parses but breaks the rules. Save these lines as `build/bad2.csv` (the `build` folder is not tracked by Git):

```
# parses, but breaks the input rules
quantity,value,unit,source
daily_active_users,5_000_000,users,Marketing forecast
daily_active_users,6_000_000,users,Second try
write_share,150,percent,too big
tomatoes,3,bytes,not an input
store.bookings.bytes_per_item,2_000,bytes,2 KB per booking row
spare_servers,1.5,servers,half a server
```

and run `./gradlew -q estimate --args='build/bad2.csv'`. I got `REJECTED line 4: daily_active_users was already given on line 3`, `REJECTED line 5: write_share must be from 0 to 100`, `REJECTED line 6: 'tomatoes' is not an input` and `REJECTED line 8: spare_servers must be a whole number, 0 or more`. Then came one `REJECTED missing input: ...` line for every input the file did not give, including `store.bookings.items_per_day`, and the closing line. All problems come in one run. Line 7, the one store field that was valid, raised no problem of its own.

4. Use the program wrongly:

```
./gradlew -q estimate
./gradlew -q estimate --args='sheets/nope.csv'
```

I got `usage: estimate <sheet.csv>` and `not a sheet file of at most 1000000 bytes: sheets/nope.csv`. Gradle reported `finished with non-zero exit value 2` both times.

5. See the planted mistake pass the checks. `sheets/photo-sharing-given.csv` has a factor-of-1,000 slip that every rule accepts (see `csv-sheets.md`):

```
./gradlew -q estimate --args='sheets/photo-sharing-given.csv'
```

It prints an estimate and the line `No result moved more than 10 times when any one input doubled.`, because the unit is right and the number is in range. The program catches unit slips. It cannot catch a wrong number in the right unit. A person must read the estimate.

## Mistakes people make

- **Stopping at the first bad line.** The reader collects every problem, so one run shows all the work left.
- **Converting a wrong unit silently.** `KB` could mean 1,000 or 1,024 bytes. The reader refuses it.
- **Letting a record hold a map or list the caller can change.** `Inputs` and `Store` store `Map.copyOf` and `List.copyOf`.
- **Rounding a server count to the nearest whole number.** 64.48 servers rounded to 64 would run above the target. The estimator rounds up (`CEILING`).
- **Adding up amounts in different units.** `Quantity.plus` throws, so the formula must convert first.
- **Trusting an estimate because every check passed.** The checks catch unit slips and runaway formulas. They do not catch a wrong assumption.
- **Dividing by a zero write share.** The estimator leaves out the read-write ratio step when the write share is zero.

## Check yourself

1. Why does `CsvSheet.parse` return a `Result` and not throw an exception for a bad line? Answer: a bad line is an expected failure of a file a person typed. A value lets the program collect every problem and print them all. Exceptions are kept for programmer errors.
2. A sheet writes `response_bytes,305_696,bits,...`. Which problem is reported and which code produces it? Answer: a `WrongUnit` problem, produced by `InputsReader.check`, which compares the unit text with the rule's unit symbol. No conversion is tried.
3. The peak load needs 36,111 requests/s and one server is planned at 560 requests/s. How many servers are needed with one spare? Answer: 36,111 / 560 = 64.48, rounded up to 65, plus 1 spare = 66.
4. Why is the sensitivity check passed `Estimator::estimate` instead of calling `Estimator.estimate` itself? Answer: so a test can hand it a deliberately broken formula and watch the flag appear.
5. What do the exit statuses 0, 1, 2 and 3 mean? Answer: 0 a clean estimate, 1 a rejected sheet, 2 wrong usage or an unreadable file, 3 an estimate that printed but was flagged by the sensitivity check.

## Further reading

- `java.math.BigDecimal`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html
- `java.util.Optional`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Optional.html
- `java.util.stream`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/package-summary.html
- `java.util.regex.Pattern`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html
- Pattern matching for `switch`, https://docs.oracle.com/en/java/javase/25/language/pattern-matching.html
- Sealed classes and interfaces, https://docs.oracle.com/en/java/javase/25/language/sealed-classes-and-interfaces.html
- `java.nio.file.Files`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html