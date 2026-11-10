# AssertJ 3.27.7

## What it is

An **assertion** is a check inside a test: "this value must be that value". AssertJ is a library of assertions with a **fluent** style: you write `assertThat(actual)` and then chain method calls that read like a sentence, each returning the object so the next call can follow. When a check fails, AssertJ prints the expected and the actual value in a clear message.

## Why this repository uses it

Tests are the requirements here, so they should read like requirements. `assertThat(production.result("rate.peak").toString()).isEqualTo("30,093 requests/s")` is easier to read than a hand-written `if` and `throw`. The README gives the reason: "Fluent assertions with clear failure messages."

## Where it appears

| Path | What it does with AssertJ |
| --- | --- |
| `gradle/libs.versions.toml` | `assertj = "3.27.7"` and `assertj-core`. |
| `build.gradle.kts` | `testImplementation(libs.assertj.core)`. |
| `gradle.lockfile` | Locks `assertj-core` and its dependency `byte-buddy` 1.18.3 (`dependency-locking.md`). |
| `src/test/java/**` | Every test file uses it. The static imports are: `assertThat` (14 files), `assertThatThrownBy` (6) and `fail` (2). About 170 `assertThat` calls and 32 `assertThatThrownBy` calls in all. |

## How it works underneath

1. `assertThat(x)` looks at the type of `x` and returns the matching **assertion object**: one for a `String`, one for a `List`, one for a `BigDecimal`, one for a `boolean`, one for any other object. Each has the methods that make sense for that type.
2. Each method (`isEqualTo`, `contains`, `hasSize`) checks the value. If the check holds, it returns the same assertion object, so another call can follow. If not, it throws an `AssertionError` with a message, and JUnit marks the test as failed (`junit.md`).
3. For exceptions, `assertThatThrownBy(() -> code)` runs the code in a try block, catches whatever is thrown and lets you check its type and message. The `() -> code` part is a **lambda**: a small unnamed function that is handed over and run later, inside AssertJ, so AssertJ can catch what it throws.
4. Because the checks are chained on one `assertThat`, several conditions on the same value come in one statement.

## Walkthrough of this repository's use

### `isEqualTo` for text, records and numbers

```java
    assertThat(production.result("rate.peak").toString()).isEqualTo("30,093 requests/s");
```

`isEqualTo` compares with `equals`. For text it compares every character. Java **records** (such as `Quantity`, `SheetProblem.WrongUnit` and `Estimate`) get a correct `equals` automatically, so two records with the same contents are equal. That is how whole problems and whole estimates are compared in one line:

```java
    assertThat(again).isEqualTo(production);
```

`isEqualTo` is the most used method here (77 calls).

### `isEqualByComparingTo` for `BigDecimal`

```java
    assertThat(bytes.amount()).isEqualByComparingTo("125000000");
```

`BigDecimal` has a **scale**, the number of decimals it carries. `new BigDecimal("1.0")` and `new BigDecimal("1")` are the same size but not `equals`. `isEqualByComparingTo` compares by size, using `compareTo`, and accepts the expected value as text. It is the second most used method (26 calls). The repository uses exact decimals everywhere, so there is no tolerance (`isCloseTo`) anywhere.

### Lists: `hasSize`, `containsExactly`, `contains`, `isEmpty`

```java
    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.WrongUnit(9, "response_bytes", "bytes", "bits"));
```

`containsExactly` checks that the list holds exactly these elements, in this order, and no others (20 calls). `contains` checks only that the given elements are present (19 calls). `hasSize(n)` checks the length. `isEmpty()` checks that a list, an `Optional` or a text is empty.

### `extracting`: check one field of every element

```java
    assertThat(inputs.stores())
        .extracting(Store::name)
        .containsExactly("bookings", "seats", "logs", "posters");
```

`extracting(Store::name)` first turns each element into one of its fields, then checks the resulting list. `Store::name` is a **method reference**, a short way to write "call `name()` on each element". With several references, `extracting` builds rows of values (a `Tuple`), as in the round-trip test of the CSV reader.

### Text: `contains`, `startsWith`, `endsWith`

```java
    assertThat(printed).endsWith(" " + unit.symbol());
```

On text these check a part: `contains("explain it")`, `startsWith("line 4:")`, `endsWith(...)` and `doesNotEndWith(...)`.

### Optionals

```java
    assertThat(Unit.withSymbol("Gbps")).contains(Unit.GIGABITS_PER_SECOND);
```

An `Optional` is a box that holds a value or nothing. `contains(x)` checks that the box holds `x`, and `isEmpty()` checks that it holds nothing.

### Conditions on every element: `allSatisfy`, `isSorted`, `doesNotHaveDuplicates`

```java
    assertThat(production.steps()).allSatisfy(step -> assertThat(step.result().unit()).isNotNull());
    assertThat(production.steps()).extracting(Step::id).doesNotHaveDuplicates();
```

`allSatisfy` runs a check on every element. `doesNotHaveDuplicates` checks that no two elements are equal. `isSorted()` checks that a list is in ascending order, which the report test uses on the positions of five headings.

### `as`: a label for the failure message

```java
      assertThat(matcher.matches()).as(line).isTrue();
```

`as(text)` puts the text in the failure message. Without it, a failure inside a loop would say only "expected true", not which line it was.

### Exceptions: `assertThatThrownBy`

```java
    assertThatThrownBy(() -> bytes.to(Unit.REQUESTS_PER_SECOND))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot convert bytes into requests/s");
```

`assertThatThrownBy(() -> ...)` runs the lambda and expects it to throw. `.isInstanceOf(IllegalArgumentException.class)` checks the type of the exception (32 calls). `.hasMessage("...")` checks the whole message (4 calls) and `.hasMessageContaining("...")` checks a part (2 calls). Checking the message proves the error says what is wrong, not just that something went wrong.

### Numbers and booleans

`isTrue()` and `isFalse()` check a `boolean`. `isPositive()` checks that a whole number is above zero (the probe tests use it on `signum()`). `isGreaterThanOrEqualTo(raw)` compares two values of one type. `isNotNull()` checks that a value exists. `isNotEqualTo(...)` checks that two values differ.

### `fail`

```java
          fail("expected entries but got " + problems);
```

`fail("message")` ends the test with a failure. It is used as an **expression** that returns a value of any type, so it can sit in a `switch` branch whose other branch returns a real value. The helpers `Sheets` and `ProbeSettingsTest` use it to unwrap a `Result` (`tests-estimate-walkthrough.md`, `tests-probe-walkthrough.md`).

## Try it yourself

1. See which assertion methods are imported and how often:

```
grep -rhn "import static org.assertj" src/test | sed 's/.*Assertions\.//' | sort | uniq -c
```

   Expected: `14 assertThat;`, `6 assertThatThrownBy;` and `2 fail;`.

2. Count the uses of `isEqualByComparingTo`:

```
grep -rho "isEqualByComparingTo" src/test | wc -l
```

   Expected: `26`.

3. Look at AssertJ in the dependency tree:

```
./gradlew -q dependencies --configuration testRuntimeClasspath | grep -A1 '^+--- org.assertj:assertj-core:3.27.7'
```

   Expected: `+--- org.assertj:assertj-core:3.27.7` followed by a line with `net.bytebuddy:byte-buddy:1.18.3`.

4. Run the report tests that use `contains` on long text:

```
./gradlew cleanTest test --tests '*EstimateReportTest'
```

   Expected: six lines ending in `PASSED`.

## Mistakes people make

- **Comparing `BigDecimal` with `isEqualTo`.** The scale differs, so equal numbers can fail. Use `isEqualByComparingTo`, as here.
- **Forgetting the final assertion.** `assertThat(x)` alone checks nothing. Only the chained call does.
- **Checking only the exception type.** The message check proves the failure is the right one.
- **Putting code outside the lambda in `assertThatThrownBy`.** The exception would escape before AssertJ can catch it.
- **Asserting with a condition that cannot fail.** One line of `neverPrintsBitsAndBytesInOneResult` checks that a text ends with `Gbps` and does not end with `bytes/s`, which no text can break. The test is saved by its second assertion.
- **Asserting on text that includes line breaks without care.** The report tests use `contains` rather than the whole text where only a part matters.

## Check yourself

1. *What does `assertThat(a).isEqualTo(b)` do when `a` and `b` differ?* Throws an `AssertionError` that shows both values; JUnit marks the test failed.
2. *Why use `isEqualByComparingTo("125000000")` for an amount?* A `BigDecimal` also has a scale, and `isEqualTo` would treat `125000000` and `125000000.0` as different.
3. *What does `assertThatThrownBy(() -> f()).isInstanceOf(X.class)` prove?* That `f()` throws an exception of type `X`.
4. *What does `containsExactly` check?* The elements and their order, and that there are no others.
5. *Why can `fail(...)` be used inside a `switch` that returns a value?* It returns a value of any type, so it fits where a value is expected.

## Further reading

- AssertJ core documentation: <https://assertj.github.io/doc/>
- AssertJ 3.27.7 Javadoc: <https://javadoc.io/doc/org.assertj/assertj-core/3.27.7/index.html>
