# result4j 1.2.0 (a library for results that are values)

## What it is

result4j is a small Java library. Its main type is `Result<R, E>`. A **result type** is an object that stands for "either this worked and here is the value, or it failed and here is the reason". The letter `R` is the type of the good value and `E` is the type of the reason.

A `Result` is one of exactly two things. `Result.Success` holds a good value. `Result.Error` holds an error value. The caller must look at which one it got. The Java compiler can check that the caller handled both.

The Java standard library has no such type. `Optional` says "a value or nothing" but cannot say why there is nothing. An exception says why, but it jumps out of the normal flow. `Result` fills the gap.

## Why this repository uses it

Bad input is normal in this program. A person writes `5_000_000` as `five million` in a sheet. A settings file has a typo in a number. A line of a sheet has a unit in bits where bytes are expected. These are **expected failures**. They should come back as ordinary values that the program can list, count and print, not as exceptions that unwind the program. One run must list every problem, so that the person fixes them all before the next run.

So the sheet reader, the input reader and the settings loader return `Result` values. **Exceptions are kept for programmer errors**: a null argument, a record built with a value its constructor forbids, a formula that adds bytes to GB, an id that no step has. Those mean the code is wrong, not the user. The line is: if a person can cause it by typing, it is a `Result`. If only a bug can cause it, it is an exception.

The dependency is declared in `gradle/libs.versions.toml` as `result4j = "1.2.0"`, used in `build.gradle.kts` as `implementation(libs.result4j)`, and pinned in `gradle.lockfile`. `implementation` means the library is needed to compile and run the main code (see `gradle.md`). The Maven coordinates are `com.github.sviperll:result4j`. The license is Apache-2.0. I read the class files of the 1.2.0 jar with `javap -v`: they are Java class-file version 65, which is Java 21, so the library runs on the Java 25 used here.

## Where it appears

| File | What it does with `Result` |
|---|---|
| `estimate/CsvSheet.java` | `parse` returns `Result<List<SheetEntry>, List<SheetProblem>>`. Uses `Result.success` and `Result.error`. |
| `estimate/InputsReader.java` | `read` returns `Result<Inputs, List<SheetProblem>>`. Uses `Result.success` and `Result.error`. |
| `estimate/EstimateCli.java` | Chains the two with `flatMap` and `map`, and examines the outcome with a pattern-matching `switch`. |
| `estimate/Sensitivity.java` | Examines `InputsReader.read` with a `switch`, to turn it into an `Optional`. |
| `probe/ProbeSettings.java` | `from` returns `Result<ProbeSettings, List<String>>`. Uses `Result.success` and `Result.error`. |
| `probe/ProbeCli.java` | Examines the settings result with a `switch`. |

No other class imports the library. `Quantity`, `Unit`, the records, `Estimator`, `EstimateReport` and the probes never see a `Result`.

## How it works underneath

`Result<R, E>` is a **sealed interface** with two permitted types, both **records**. I ran `javap` on the jar and confirmed that `Result$Success` and `Result$Error` are `final` classes that extend `java.lang.Record`. A sealed interface lists every type that may implement it, so the compiler knows the full list. Records are short classes that only carry values (see `java.md`).

- `Result.Success<R, E>` is a record with one component, `result`. Its `toString` prints `Success[result=5]`.
- `Result.Error<R, E>` is a record with one component, `error`. Its `toString` prints `Error[error=negative]`.

Both have the same two type parameters. A `Success` simply never uses `E`, and an `Error` never uses `R`. That is why a pattern is written `Result.Success<Checked, List<SheetProblem>>(Checked checked)`: both type parameters are spelled out.

Everything is in memory. A `Result` is an ordinary object on the heap. There is no thread, no network and no file. Making one costs one small object.

The methods this repository uses:

- `Result.success(value)` makes a `Success`.
- `Result.error(reason)` makes an `Error`.
- `result.map(function)` runs `function` on the good value and wraps what it returns in a new `Success`. If `result` is an `Error`, `function` is not called and the same error is returned.
- `result.flatMap(function)` runs `function` on the good value and returns the `Result` that it returns. If `result` is an `Error`, `function` is not called and the same error is returned. This is how two steps that can both fail are joined: the second runs only if the first worked.
- A `switch` over the `Result` with record patterns `Result.Success<...>(value)` and `Result.Error<...>(error)`. The compiler knows there are only two cases, so no `default` is needed. If a library update added a third case, the switch would stop compiling.

The library offers more (`andThen`, `mapError`, `isError`, `recoverError`, `orOnErrorThrow`, `throwError`, `peekError`, `peekSuccess`, `ifSuccess`, `discardError`, `fromOptional` and others). This repository does not use them. I listed the public methods with `javap -public` to know.

I ran a small program with the 1.2.0 jar on the class path:

```
Success[result=5] | Error[error=negative]
Error[error=too big]        (ok.flatMap(...) where the function returned an error)
Error[error=negative]       (bad.flatMap(...): the function never ran)
Success[result=10] | Error[error=negative]    (ok.map(n -> n * 2) | bad.map(n -> n * 2))
got 5                       (a switch with both record patterns)
```

The line `never printed`, which the function would have printed, did not appear: `flatMap` on an error never calls the function. I also ran `Result.success(null)` and got `Success[result=null]`: the library does not forbid a null value. This repository never passes null.

## Walkthrough of this repository's use

### Building results: `CsvSheet.parse`

```java
  public static Result<List<SheetEntry>, List<SheetProblem>> parse(List<String> lines) {
    Objects.requireNonNull(lines, "lines");
```

- The return type `Result<List<SheetEntry>, List<SheetProblem>>` says in one line what the method gives: a list of entries, or a list of problems. Both are lists, so one run can report every bad line.
- `Objects.requireNonNull` stays an exception: a null list is a programmer error.

```java
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
```

- `Result.error(List.of(new SheetProblem.WrongHeader(line)))` makes the failure for a wrong header. `SheetProblem` is a sealed interface of ten record types, one for each reason (see `java-estimate-walkthrough.md`). The first non-comment line must be the header, so a wrong one returns at once with a list of one problem.
- The last line of `parse` is a conditional expression: if there is no problem, `Result.success(List.copyOf(entries))`, otherwise `Result.error(problems)`.

### Building results: `InputsReader.read`

```java
  public static Result<Inputs, List<SheetProblem>> read(List<SheetEntry> entries) {
    Objects.requireNonNull(entries, "entries");
```

```java
    addMissing(firstLineOf.keySet(), stores.keySet(), problems);
    if (!problems.isEmpty()) {
      return Result.error(List.copyOf(problems));
    }
    List<Store> storeList = new ArrayList<>();
    stores.forEach((name, fields) -> storeList.add(new Store(name, fields)));
    return Result.success(new Inputs(values, storeList));
  }
```

- `read` collects every problem while it works. At the end, one decision: a non-empty list is an error and an empty one allows `Result.success(new Inputs(values, storeList))`. `Inputs`' constructor may still throw, but only if the code above it has a bug, because the reader has already found every possible fault.
- This is the design in one place: user mistakes are values in a list, a bug is an exception.

### Joining two steps: `EstimateCli`

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

- `CsvSheet.parse(lines)` returns a `Result<List<SheetEntry>, List<SheetProblem>>`. `.flatMap(EstimateCli::withInputs)` calls `withInputs` with the entries only if the parse succeeded. `EstimateCli::withInputs` is a **method reference**: a short way to write the lambda `entries -> EstimateCli.withInputs(entries)`. If the parse failed, `withInputs` never runs, and the same error comes out. The two steps share the error type `List<SheetProblem>`, which is what `flatMap` needs.
- The `switch` examines the final `Result`. `case Result.Success<Checked, List<SheetProblem>>(Checked checked) -> print(checked);` is a **record pattern**. It tests that the value is a `Success`, takes the record's component out and names it `checked`, all in one step. `Result.Error<...>(List<SheetProblem> problems)` takes the problems out. Each problem is printed with `explain`, and the program exits with status 1.
- There is no `default`. The `switch` is a statement over a sealed type with patterns, and Java requires it to be exhaustive. You cannot forget the error case.

```java
  private record Checked(List<SheetEntry> entries, Inputs inputs) {}

  private static Result<Checked, List<SheetProblem>> withInputs(List<SheetEntry> entries) {
    return InputsReader.read(entries).map(inputs -> new Checked(entries, inputs));
  }
```

- `InputsReader.read(entries)` returns a `Result<Inputs, List<SheetProblem>>`. `.map(inputs -> new Checked(entries, inputs))` turns the good value into a `Checked`, which holds the entries and the inputs together, and leaves an error alone. `map` is `flatMap` for a function that returns a plain value instead of a `Result`. The result is a `Result<Checked, List<SheetProblem>>`, the type that `flatMap` in `main` expects.

### Turning a result into an `Optional`: `Sensitivity.inputsFrom`

```java
  private static Optional<Inputs> inputsFrom(List<SheetEntry> entries) {
    return switch (InputsReader.read(entries)) {
      case Result.Success<Inputs, List<SheetProblem>>(Inputs inputs) -> Optional.of(inputs);
      case Result.Error<Inputs, List<SheetProblem>> _ -> Optional.empty();
    };
  }
```

- The sensitivity check changes one value and asks whether the changed sheet is still valid. It needs only a yes or no, and the inputs when yes.
- `case Result.Success<Inputs, List<SheetProblem>>(Inputs inputs) -> Optional.of(inputs);` takes the inputs out. `case Result.Error<Inputs, List<SheetProblem>> _ -> Optional.empty();` matches an error and does not name it: `_` is the **unnamed pattern** of Java 22, "there is a value here and I do not need it". The problems themselves are not used.

### Building results: `ProbeSettings.from`

```java
  public static Result<ProbeSettings, List<String>> from(Properties properties) {
    Objects.requireNonNull(properties, "properties");
```

```java
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
```

- `from` returns `Result<ProbeSettings, List<String>>`. The problems are plain text, one line for each missing or invalid key.
- If any of the ten keys had a problem, the first `Result.error` returns the whole list. Otherwise the settings are built inside a `try`. The record's constructor may still throw `IllegalArgumentException` when two valid keys disagree (a file smaller than a block). `catch (IllegalArgumentException inconsistent)` turns that exception into a `Result.error` with one problem line. This is the one place where an exception is caught and converted: the record's own constructor is the only place that holds the rule, and the settings file is typed by a person, so a broken combination is an expected failure.

### Examining a result: `ProbeCli.main`

```java
    switch (ProbeSettings.from(load(Path.of(arguments[0])))) {
      case Result.Success<ProbeSettings, List<String>>(ProbeSettings settings) ->
          run(settings, Path.of(arguments[1]));
      case Result.Error<ProbeSettings, List<String>>(List<String> problems) -> {
        problems.forEach(problem -> System.err.println("invalid setting: " + problem));
        System.exit(EXIT_BAD_USAGE);
      }
    }
```

- `ProbeSettings.from(load(Path.of(arguments[0])))` gives a `Result`. A success runs the probe. An error prints `invalid setting: ` for each problem and exits with status 2.

## Try it yourself

Run these in `seatwave-estimates/`.

1. See the version pinned and where it is declared:

```
grep -n result4j gradle/libs.versions.toml build.gradle.kts gradle.lockfile
```

I got `gradle/libs.versions.toml:3:result4j = "1.2.0"`, the library line on line 12, `build.gradle.kts:17:    implementation(libs.result4j)` and `gradle.lockfile:5:com.github.sviperll:result4j:1.2.0=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath`.

2. Find every place the program uses the library:

```
grep -rn "Result\." src/main/java
```

I got the lines listed in the "Where it appears" table: nine `Result.success` or `Result.error` calls and the `case Result.Success` and `case Result.Error` patterns.

3. Watch many problems come back as values in one run. Save these lines as `build/bad2.csv` (the `build` folder is not tracked by Git):

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

and run `./gradlew -q estimate --args='build/bad2.csv'`. I got four `REJECTED line ...` messages, then one `REJECTED missing input: ...` for every input the file did not give, in one run. That is a `Result.Error` holding a list of problems.

4. Watch the settings loader do the same. Copy `probe.properties` to `build/bad.properties`, delete the `samples` line and change `random.seed` to `abc`:

```
./gradlew -q probe --args='build/bad.properties build/probe'
```

I got `invalid setting: samples is missing` and `invalid setting: random.seed must be a whole number up to 2147483647, but was abc`, then exit value 2.

5. See what an exception is kept for. A `Quantity` with a negative amount is a programmer error. Compile the project and run a small program with `Quantity.of("-1", Unit.BYTES)`: I got `java.lang.IllegalArgumentException: an estimate is never negative, but was -1`. A sheet that writes `-1` is stopped earlier by the sheet reader, as a value. I copied `sheets/seatwave-simple.csv` to `build/neg.csv`, changed the `peak_factor` value to `-1`, ran the estimator on it, and got `REJECTED line 6: '-1' is not a plain decimal number`, because the number pattern allows no sign.

## Mistakes people make

- **Throwing an exception for input a person typed.** The program stops at the first one and the person fixes them one run at a time. Return a list of problems as a value.
- **Returning a `Result` for a programmer error.** A formula that adds bytes to GB is a bug. A `Result` would hide it behind a message nobody reads. This repository throws.
- **Writing a `default` in the `switch` over a `Result`.** It would hide a new case if the library ever added one.
- **Using `get`-style unwrapping and forgetting the error case.** The record pattern gives you both cases or a compile error.
- **Putting a null in `Result.success`.** The library accepts it. This repository never does.
- **Forgetting that `flatMap` runs the function only on success.** That is its purpose. It makes a chain of steps stop at the first failure.

## Check yourself

1. What is the difference between `map` and `flatMap` on a `Result`? Answer: both run a function only on a success. `map` wraps what the function returns in a new `Success`. `flatMap` expects the function to return a `Result` itself, so a step that can fail can follow a step that can fail.
2. Why does `CsvSheet.parse` return `Result<List<SheetEntry>, List<SheetProblem>>` and not throw? Answer: a bad line is an expected failure of a file a person typed. A list of problems lets one run report all of them.
3. Why does the `switch` in `EstimateCli.main` have no `default` branch? Answer: `Result` is sealed with two cases, and Java requires the switch to cover both. A third case would stop the build.
4. Which kinds of failure remain exceptions in this repository? Answer: programmer errors: a null argument, a record built with a forbidden value, adding two different units, asking for a step id that does not exist.
5. What does the underscore in `Result.Error<Inputs, List<SheetProblem>> _` mean, and which Java version does it need? Answer: an unnamed pattern: the value is matched and not used. Java 22.

## Further reading

- result4j project page and README, https://github.com/sviperll/result4j
- result4j 1.2.0 on Maven Central, https://central.sonatype.com/artifact/com.github.sviperll/result4j/1.2.0
- Pattern matching, https://docs.oracle.com/en/java/javase/25/language/pattern-matching.html
- Sealed classes and interfaces, https://docs.oracle.com/en/java/javase/25/language/sealed-classes-and-interfaces.html