# JUnit Jupiter 6.1.3

## What it is

JUnit is the standard Java library for writing **automated tests**. A test is an ordinary method, marked with the annotation `@Test`, that runs some code and checks the result. If a check fails, JUnit reports the failing test by name and the build stops. **JUnit Jupiter** is the part of JUnit you write tests with. The **JUnit Platform** is the part that finds and runs them. This repository uses version **6.1.3** of both. JUnit 6 needs Java 17 or newer and keeps the Jupiter style of version 5.

An **annotation** is a label written with `@` in front of a method, class or parameter, such as `@Test`. It does nothing by itself. Tools read it.

## Why this repository uses it

The project's tests double as its requirements: every test method has a long name that states one behavior in plain words, and all 119 run with `./gradlew check`. JUnit gives the structure. AssertJ (`assertj.md`) gives the readable checks, and Instancio (`instancio.md`) gives generated data.

## Where it appears

| Path | What it does with JUnit |
| --- | --- |
| `gradle/libs.versions.toml` | `junit = "6.1.3"` and the entries `junit-bom`, `junit-jupiter` and `junit-platform-launcher`. |
| `build.gradle.kts` | `platform(libs.junit.bom)`, `libs.junit.jupiter` and `libs.junit.platform.launcher`, and `useJUnitPlatform()`. |
| `gradle.lockfile` | Locks the JUnit libraries at 6.1.3 (`dependency-locking.md`). |
| `src/test/java/**` | 14 test classes with 119 `@Test` methods, and 2 helper classes that hold no tests. |

## How it works underneath

1. `./gradlew test` starts a new JVM and hands it the test classes, with the JUnit Platform launcher (`junit-platform-launcher`) as the entry point.
2. The Platform asks the **Jupiter engine** (`junit-jupiter-engine`) to **discover** tests: it scans the classes under `build/classes/java/test` for methods marked `@Test`.
3. For **each** test method Jupiter creates a **new instance** of the test class, runs the method, and throws the instance away. State cannot leak from one test to the next. This is why a field such as `private final Estimate production = ...` in `EstimatorTest` is rebuilt for every test.
4. A test **passes** if the method returns normally. It **fails** if it throws an `AssertionError`, which is what a wrong check throws. It is an **error** if it throws anything else.
5. Results go to `build/test-results/test/*.xml` (one file per class, 14 here), and Gradle prints one line per result because of `testLogging { events("passed", "failed", "skipped") }`.
6. The classes and methods are **package-private** (no `public`). JUnit 5 and 6 allow that, which keeps tests out of the public surface.

JUnit does not promise an order between tests, and no test here depends on one.

## Walkthrough of this repository's use

### `@Test`: one behavior, one sentence

```java
  @Test
  void convertsYearsIntoDaysOf365() {
    assertThat(Quantity.of("5", Unit.YEARS).to(Unit.DAYS).amount()).isEqualByComparingTo("1825");
  }
```

`@Test` marks the method as a test. The name is a sentence about behavior, so a failure reads like a broken requirement: "converts years into days of 365". The body has three steps that most tests here follow: **arrange** (build the input), **act** (call the code), **assert** (check the outcome). Short tests such as this one fold the three steps into one line.

### `@TempDir`: a folder for one test

```java
  @Test
  void measuresDiskReadsAndWritesAndDeletesItsFile(@TempDir Path folder) throws IOException {
```

`@TempDir` on a method parameter asks JUnit to create a new empty temporary folder, pass it in as `folder`, and delete it after the test. `ProbesTest` uses it so the disk probe never writes into a real folder. It is the only annotation besides `@Test`: I searched the test sources and found `@Test` 119 times and `@TempDir` once.

### What this repository does not use

There is no `@ParameterizedTest`, `@ValueSource`, `@EnumSource`, `@BeforeEach`, `@AfterEach`, `@Nested`, `@DisplayName` or `@ExtendWith`. Where several inputs need one check, a test uses an ordinary `for` loop (`everyPrintedQuantityEndsWithItsUnitSymbol`, `flagsNoResultInAnyShippedSheet`). The tests build what they need with ordinary helper classes (`Sheets` and `ProbeSettingsFixtures`; see `tests-estimate-walkthrough.md` and `tests-probe-walkthrough.md`). That keeps each test readable from top to bottom. A helper class holds no `@Test`, so JUnit ignores it.

### Static imports and the assertion style

All tests start with lines such as `import static org.assertj.core.api.Assertions.assertThat;`. A **static import** lets you write `assertThat(...)` instead of `Assertions.assertThat(...)`. The checks come from AssertJ, not from JUnit's own `Assertions` class (`assertj.md`).

### Where tests run from

`Sheets` and `ProbeSettingsTest` read real files with a relative path:

```java
    return Files.readAllLines(sheet, StandardCharsets.UTF_8);
```

The path comes from `Path.of("sheets", "seatwave-production.csv")`, and `probe.properties` is opened as `Path.of("probe.properties")`. Gradle runs the tests with the project folder as the working directory, so both are found. A test run from another folder would fail with a file-not-found error.

### The build wiring

`testImplementation(libs.junit.jupiter)` makes the annotations and the engine available. `testRuntimeOnly(libs.junit.platform.launcher)` adds the launcher. `useJUnitPlatform()` tells the `test` task to use the Platform. The BOM `platform(libs.junit.bom)` fixes every JUnit library at 6.1.3 (`gradle.md`).

## Try it yourself

1. Run all tests and count the results:

```
./gradlew cleanTest test | grep -c PASSED
```

   Expected: `119`.

2. Run one class:

```
./gradlew cleanTest test --tests '*UnitTest'
```

   Expected: four lines ending in `PASSED` and `BUILD SUCCESSFUL`.

3. Run one method:

```
./gradlew cleanTest test --tests '*QuantityTest.convertsYearsIntoDaysOf365'
```

   Expected: one `PASSED` line.

4. Count the annotations:

```
grep -rhoE "^ *@[A-Za-z]+" src/test | sed 's/^ *//' | sort | uniq -c
```

   Expected: `119 @Test`. (`@TempDir` sits inside a method signature, so this pattern does not show it; `grep -rn "@TempDir" src/test` finds the one use.)

5. Read the totals from the result files:

```
grep -o 'tests="[0-9]*"' build/test-results/test/*.xml
```

   Each of the 14 files has a `tests="..."` count after a full run; they add up to 119.

## Mistakes people make

- **Vague names** such as `test1`. A good name is a sentence about behavior, as here.
- **Tests that depend on each other's order.** JUnit gives no order promise. Each test builds its own input.
- **Asserting nothing.** A test that only calls code passes even if the code is wrong.
- **Forgetting the launcher with Gradle 9.** Without `junit-platform-launcher` on the test runtime classpath the tests can not start. The build declares it.
- **Running the tests from the wrong folder.** The sheet and probe tests need the project folder.
- **A filter that matches too much.** `--tests '*estimate*'` also matches the package `estimates`, so it runs all 119 tests. Use the full package name.

## Check yourself

1. *What does `@Test` do?* Marks a method that JUnit runs as a test.
2. *Why does JUnit create a new instance of the class for every test?* So no test can see state left by another.
3. *What does `@TempDir` do?* JUnit makes a new empty folder for the test, passes it as the parameter, and deletes it afterwards.
4. *How do 119 `@Test` methods give 119 results?* Each method runs once; this repository has no parameterized tests.
5. *Why does a test method name read like a sentence?* So a failing test reads like a broken requirement.

## Further reading

- JUnit 6 user guide (6.1.3): <https://docs.junit.org/6.1.3/overview.html>
- Writing tests and annotations: <https://docs.junit.org/6.1.3/writing-tests/annotations.html>
- Running JUnit on Gradle: <https://docs.gradle.org/9.9.1/userguide/java_testing.html>
