# Gradle 9.9.1 (build tool) and the Kotlin DSL

## What it is

Source code is text. A computer cannot run text until other programs have turned it into something runnable. A **build tool** is the program that does all those steps in the right order, from one command: it fetches the libraries the code needs, compiles the Java source files into **class files** (the form the Java Virtual Machine, the JVM, can run), runs the tests, checks the code style and measures the test coverage. **Gradle** is a build tool for Java. You tell it what you want (for example `check`) and it works out which smaller steps are needed.

A Gradle build is described in files written in a language. This repository uses the **Kotlin DSL**: the build file is a Kotlin script. "DSL" means domain-specific language, a small language made for one job. A file that ends in `.kts` is a Kotlin script, a Kotlin program that is run directly instead of being compiled first.

A **terminal** is a window where you type commands. The word after the program name, such as `check` in `./gradlew check`, is the name of a **task**. A task is one unit of work Gradle knows how to do.

## Why this repository uses it

The hands-on project of Module 3 must build and test with one command and the same result on every machine. Gradle gives that: `./gradlew check` checks the formatting of the code, compiles it with every warning turned into an error, runs 119 tests and fails if the line coverage is below 80 percent. The build file also gives each of the two programs (the estimator and the latency probe) its own task, so a learner types one short command instead of assembling a long Java command line.

## Where it appears

| Path | What it does with Gradle |
| --- | --- |
| `settings.gradle.kts` | Names the project and says where libraries may be downloaded from. |
| `build.gradle.kts` | The build itself: plugins, Java version, libraries, compiler flags, formatter, coverage, test task, the two program tasks. |
| `gradle/libs.versions.toml` | The version catalog: one place for every version (`toml-version-catalog.md`). |
| `gradle.lockfile`, `settings-gradle.lockfile` | Pinned versions of every library, including the ones libraries bring along (`dependency-locking.md`). |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/` | The wrapper that downloads and starts the right Gradle (`gradle-wrapper.md`, `posix-shell.md`, `windows-batch.md`). |
| `build/` and `.gradle/` | Folders Gradle creates while it works. Git ignores them (`git.md`). |

## How it works underneath

1. You run `./gradlew check`. The wrapper starts a **Gradle process**, a Java program that stays alive in the background for a while (the **daemon**) so the next command starts quickly.
2. Gradle reads `settings.gradle.kts` first. That file decides which projects are part of the build. Here there is one project.
3. Gradle then reads `build.gradle.kts`. Running the file does not build anything. It only **configures**: it creates tasks and tells each one what it depends on.
4. Gradle looks at the task you asked for and at everything it depends on, and builds a **task graph**: a list of tasks in a legal order, each after the tasks it needs.
5. It runs the tasks. For each task it checks whether the inputs (source files, settings) and outputs (class files, reports) are the same as last time. If yes, the task is marked `UP-TO-DATE` and skipped. This is why a second `./gradlew check` takes a fraction of a second: on this machine the second run said `BUILD SUCCESSFUL in 361ms` with `7 actionable tasks: 7 up-to-date`.
6. Everything it makes goes into the `build/` folder. Downloaded libraries are kept in a cache folder in your home directory (`~/.gradle`), shared by all projects.

A **plugin** is a package of tasks and settings that you switch on in one line. The `java` plugin adds the tasks that compile and test Java. A **dependency** is a library your code needs. A **configuration** is a named list of dependencies for one purpose, such as "what the tests need at run time". A **repository** is a server that stores libraries; Maven Central is the big public one.

## Walkthrough of this repository's use

### `settings.gradle.kts`

```kotlin
// One place decides where dependencies come from; a project that adds its own repository fails the build.
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "seatwave-estimates"
```

- Line 1 is a comment: `//` starts a comment in Kotlin. It says that one place decides where libraries come from.
- `dependencyResolutionManagement { ... }` is a block. A block is a name followed by braces; everything inside configures that one topic. This block configures how Gradle **resolves** dependencies, which means finding the exact file for each library.
- `repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS` makes the build fail if `build.gradle.kts` ever adds a repository of its own. All repositories must be declared here. This stops a project from quietly pulling libraries from an unreviewed server.
- `repositories { mavenCentral() }` lists the only allowed repository: Maven Central.
- `rootProject.name = "seatwave-estimates"` names the project. The name appears in reports, for example "Rule violated for bundle seatwave-estimates".

### `build.gradle.kts`, the plugins block

```kotlin
plugins {
    java
    jacoco
    alias(libs.plugins.spotless)
}
```

- `plugins { ... }` lists the plugins to switch on.
- `java` adds the Java tasks: `compileJava`, `test`, `jar` and the `check` task.
- `jacoco` adds the coverage tool (`jacoco.md`).
- `alias(libs.plugins.spotless)` switches on the Spotless formatter plugin. `alias` means "use the plugin described in the version catalog". `libs.plugins.spotless` points at the `spotless` line in the `[plugins]` section of `gradle/libs.versions.toml`, which holds the plugin id and its version (`toml-version-catalog.md`).

### Project coordinates

```kotlin
group = "io.plainround"
version = "1.1.0"
```

`group` and `version` are the project's own identity in the Maven style: a group, a name (from `settings.gradle.kts`) and a version. They would matter if the project were published as a library. Here they only label the build.

### The Java toolchain

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

A **toolchain** is the exact JDK (Java Development Kit) that builds the code. `languageVersion = JavaLanguageVersion.of(25)` says "use Java 25". Gradle looks for an installed JDK 25 on the machine and uses it for compiling and for running tests, even if the JDK that started Gradle is different. This build does not configure an automatic download of a JDK, so a JDK 25 must be installed (the README names Eclipse Temurin 25.0.2). The `java { toolchain { ... } }` nesting is a block inside a block: `java` is the extension that the `java` plugin added, and `toolchain` is one of its parts.

### Dependencies and configurations

```kotlin
dependencies {
    implementation(libs.result4j)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.instancio.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

- `implementation(libs.result4j)` puts the result4j library on the classpath of the main code, both for compiling and for running. A **classpath** is the list of jar files and folders the JVM searches for classes. `libs.result4j` is a catalog entry: it expands to `com.github.sviperll:result4j:1.2.0`. The estimator uses it so that a rejected sheet is a value, not an exception (`result4j.md`).
- A name like `implementation`, `testImplementation` or `testRuntimeOnly` is a configuration. The `test` prefix means "only for the test code". `Implementation` means "needed to compile and run". `RuntimeOnly` means "needed only when running, not when compiling".
- `testImplementation(platform(libs.junit.bom))` brings in a **BOM**, a bill of materials. A BOM is a small published file that lists the matching versions of many libraries of one family. `platform(...)` tells Gradle "use this list to decide versions". Here the JUnit family has several libraries (jupiter api, engine, params, platform launcher). The BOM says all of them are 6.1.3, so the catalog does not need a version for each.
- `testImplementation(libs.junit.jupiter)` adds the JUnit Jupiter bundle: the annotations and the engine that finds and runs the tests. It has no version in the catalog; the BOM above supplies 6.1.3. `./gradlew dependencies` prints `org.junit.jupiter:junit-jupiter -> 6.1.3`, where the arrow means "the version was chosen by something else".
- `testImplementation(libs.assertj.core)` adds AssertJ, the readable assertions (`assertj.md`).
- `testImplementation(libs.instancio.junit)` adds Instancio, the seeded test-data generator (`instancio.md`). Only `QuantityTest` uses it.
- `testRuntimeOnly(libs.junit.platform.launcher)` adds the JUnit Platform launcher, the piece Gradle calls to start the tests. Gradle 9 expects the project to declare it, and it is needed only when tests run, so it is `testRuntimeOnly`. Its version also comes from the BOM.

### Dependency locking

```kotlin
// Transitive versions are written to gradle.lockfile, so two builds can never resolve different jars.
dependencyLocking {
    lockAllConfigurations()
}
```

Libraries bring other libraries (their **transitive dependencies**). `lockAllConfigurations()` makes Gradle write the exact version of every library of every configuration into `gradle.lockfile`, and read that file on every later build. If the resolved versions would differ from the lock, the build fails. The whole story is in `dependency-locking.md`.

### The Java compiler flags

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    // Every warning is an error: a warning that is allowed to stay is a defect nobody owns.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
```

- `tasks.withType<JavaCompile>().configureEach { ... }` means: for every task of the type `JavaCompile` (there is one for main code and one for test code), run this block. `<JavaCompile>` is a Kotlin type argument, the type to select. `configureEach` applies the block to each one lazily, only when needed.
- `options.encoding = "UTF-8"` makes the compiler read source files as UTF-8 text, so the result does not depend on the machine's default.
- `options.release = 25` makes the compiler accept only Java 25 language features and use only Java 25 library classes.
- The comment on line 34 gives the reason for the next line.
- `options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))` passes two flags to `javac`, the Java compiler. `-Xlint:all` turns on every optional warning (raw types, unchecked casts, missing serial ids and more). `-Werror` turns each warning into an error, so the build stops. The result: no warning can stay in the code. I ran an experiment in a scratch copy: adding a class that uses the raw types `java.util.List` and `java.util.ArrayList` made `compileJava` fail with `warning: [rawtypes] found raw type: List` followed by `error: warnings found and -Werror specified` (`1 error`, `2 warnings`).

### Spotless and the formatter

```kotlin
spotless {
    java {
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        removeUnusedImports()
        endWithNewline()
    }
}
```

Spotless is a Gradle plugin that checks and rewrites source files to one layout (`spotless-google-java-format.md`).
- `spotless { java { ... } }` configures Spotless for Java files.
- `googleJavaFormat(libs.versions.googleJavaFormat.get())` chooses the formatter. `libs.versions.googleJavaFormat` is the version entry `googleJavaFormat = "2.0.0"` from the catalog, and `.get()` turns it into the text `2.0.0`.
- `removeUnusedImports()` deletes `import` lines the file never uses.
- `endWithNewline()` makes every file end with one line break.
- Spotless adds two tasks: `spotlessCheck` (fails if a file would change) and `spotlessApply` (changes the files). The `java` plugin's `check` task depends on `spotlessCheck`, which is how formatting becomes part of the gate.

### JaCoCo, the coverage tool

```kotlin
jacoco {
    toolVersion = libs.versions.jacoco.get()
}
```

`jacoco { toolVersion = ... }` fixes the JaCoCo version to the catalog entry `jacoco = "0.8.16"`. Without it, Gradle would pick a default version. `jacoco.md` explains what the tool measures.

### The test task

```kotlin
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
    finalizedBy(tasks.jacocoTestReport)
}
```

- `tasks.test { ... }` configures the existing task named `test`.
- `useJUnitPlatform()` tells it to run tests through the JUnit Platform (JUnit 5 and 6 style tests). Without it Gradle would look for older JUnit 4 tests and find none.
- `testLogging { events("passed", "failed", "skipped") }` prints one line per test result while the task runs. That is where lines such as `EstimatorTest > neverStoresLessThanOneCopy() PASSED` come from. Without it, only failures show.
- `finalizedBy(tasks.jacocoTestReport)` means: after `test` finishes, always run `jacocoTestReport`, even if tests failed. That creates the coverage report in `build/reports/jacoco/test/html`.

### The XML coverage report

```kotlin
// The XML report gives the exact covered and missed line counts that the README quotes.
tasks.jacocoTestReport {
    reports {
        xml.required = true
    }
}
```

This block is new compared with the sibling repository. `tasks.jacocoTestReport { ... }` configures the task that writes the coverage report. `reports { xml.required = true }` switches on the **XML report**. By default the task writes only the HTML report, which people read in a browser. XML is a text format made of tags such as `<counter type="LINE" missed="86" covered="900"/>`; a program can read it, so the exact numbers can be copied into the README without counting by eye. The file lands in `build/reports/jacoco/test/jacocoTestReport.xml`. The comment above the block says the same thing: the README quotes those counts (900 covered lines and 86 missed lines, 91.3 percent).

### The coverage rule wired into `check`

```
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                // The floor for the whole project; the command-line entry points are the untested remainder.
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

- `tasks.jacocoTestCoverageVerification` is the task that checks coverage against a rule. `dependsOn(tasks.test)` makes sure the tests have run, so there is coverage data to read.
- `violationRules { rule { limit { ... } } }` is the rule. A **limit** names a counter, here `counter = "LINE"`, and a bound. `minimum = "0.80".toBigDecimal()` means the covered share of lines must be at least 0.80. `.toBigDecimal()` turns the text into an exact decimal number, which the plugin requires. The comment explains that the untested remainder is the command-line entry points.
- `tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }` adds the coverage check to `check`. By default `check` does not run it, so this line is what makes the floor a real gate. I proved it in a scratch copy: with the minimum raised to 0.95 the build failed with `Rule violated for bundle seatwave-estimates: lines covered ratio is 0.91, but expected minimum is 0.95`.
- `check` is the standard "is this project healthy" task. It runs the tests, `spotlessCheck` and the coverage verification.

### The two program tasks

```kotlin
// One command per example: each entry point gets its own task, so a reader never assembles a classpath.
val entryPoints = mapOf(
    "estimate" to "io.plainround.seatwave.estimates.estimate.EstimateCli",
    "probe" to "io.plainround.seatwave.estimates.probe.ProbeCli",
)

entryPoints.forEach { (taskName, entryPoint) ->
    tasks.register<JavaExec>(taskName) {
        group = "application"
        description = "Runs $entryPoint."
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = entryPoint
        // Paths in --args are read from the project folder, wherever Gradle was started.
        workingDir = rootDir
    }
}
```

- `val entryPoints = mapOf("estimate" to "...EstimateCli", "probe" to "...ProbeCli")` creates a read-only map in Kotlin: each pair is a task name and the full name of a class that has a `main` method (an **entry point**, the place where a Java program starts). `"a" to "b"` builds one pair.
- `entryPoints.forEach { (taskName, entryPoint) -> ... }` runs the block once per pair. The round brackets `(taskName, entryPoint)` take the pair apart into its two parts.
- `tasks.register<JavaExec>(taskName) { ... }` creates a new task of the type `JavaExec`, which runs a Java program. `register` creates it lazily.
- `group = "application"` files the task in the `application` group in the `./gradlew tasks` list. `description = "Runs $entryPoint."` is the text shown next to it; `$entryPoint` inserts the variable into the text.
- `classpath = sourceSets.main.get().runtimeClasspath` gives the program the compiled main classes plus the libraries it needs. A **source set** is a named group of source folders; `main` is `src/main/java`. Because `JavaExec` depends on the classpath, Gradle compiles the code first.
- `mainClass = entryPoint` names the class to start.
- The comment on line 94 and `workingDir = rootDir` are the line that is new here. A **working directory** is the folder a program treats as "here": a relative path such as `sheets/seatwave-simple.csv` is looked for from it. `rootDir` is the folder that holds `settings.gradle.kts`, the project folder. So `./gradlew -q estimate --args='sheets/seatwave-simple.csv'` finds the sheet. I tested the line in a scratch copy: I ran Gradle from `/tmp` with `-p` pointing at the project, and the estimator found `sheets/seatwave-simple.csv`. With the `workingDir` line deleted, the same command also worked, because the default working directory of a `JavaExec` task is the project folder. In this one-project build the line therefore makes the behavior explicit rather than changing it. It would matter in a build with several projects, where the project folder and the root folder differ.

The two tasks are `estimate` and `probe`. `./gradlew tasks --group application` lists them (output below).

### The commands you type

- `./gradlew check` runs the full gate. `./` means "the file `gradlew` in this folder".
- `./gradlew spotlessApply` rewrites source files into the formatter's layout. Run it before `check` if `spotlessCheck` complains.
- `-q` (`--quiet`) hides Gradle's own messages, so only the program's output shows.
- `--args='...'` passes the text after it to the program's `main` method as arguments. Gradle splits the text at spaces into separate arguments. `./gradlew -q probe --args='probe.properties build/probe'` therefore gives `ProbeCli` two arguments.
- Programs run with the project folder as the working directory (the `workingDir` line above).
- When a program calls `System.exit(1)`, Gradle reports `Execution failed for task ':estimate'` and exits with 1 itself.

## Try it yourself

1. List the tasks that run the programs:

```
./gradlew tasks --group application
```

   Expected output (the first lines):

```
Application tasks
-----------------
estimate - Runs io.plainround.seatwave.estimates.estimate.EstimateCli.
probe - Runs io.plainround.seatwave.estimates.probe.ProbeCli.
```

2. Run the whole gate (the first run downloads libraries, so it takes longer):

```
./gradlew clean check
```

   It prints one `PASSED` line per test (119 in all), runs `jacocoTestCoverageVerification`, `spotlessCheck` and ends with `BUILD SUCCESSFUL in 2s` and `8 actionable tasks: 8 executed`. Run `./gradlew check` a second time and every task shows `UP-TO-DATE`.

3. Run a program quietly:

```
./gradlew -q estimate --args='sheets/seatwave-simple.csv' | head -3
```

   Expected output:

```
INPUTS (a complete sheet: save these lines as a .csv file to reproduce this estimate)
quantity,value,unit,source
daily_active_users,20000,users,Module 1: 20_000 visits a day
```

4. Run a program that fails on purpose and look at the exit status:

```
./gradlew -q estimate --args='sheets/lab1-unit-slips.csv'; echo "exit=$?"
```

   The program prints four `REJECTED ...` lines and `SHEET REJECTED: fix every line above; nothing was estimated.`. Gradle then prints `Execution failed for task ':estimate'` and `finished with non-zero exit value 1`, and the last line is `exit=1`.

5. See the version Gradle chose for each library:

```
./gradlew -q dependencies --configuration testRuntimeClasspath
```

   The tree starts with `+--- com.github.sviperll:result4j:1.2.0` and shows `org.junit.jupiter:junit-jupiter -> 6.1.3`.

## Mistakes people make

- **Running `gradle` instead of `./gradlew`.** The installed Gradle may be a different version. The wrapper always starts 9.9.1 (`gradle-wrapper.md`).
- **Editing `gradle.lockfile` by hand.** The file says manual edits can break the build. Regenerate it (`dependency-locking.md`).
- **Adding a repository in `build.gradle.kts`.** `FAIL_ON_PROJECT_REPOS` in the settings file stops it on purpose.
- **Ignoring a compiler warning.** `-Werror` makes that impossible here.
- **Thinking `test` is enough.** `./gradlew test` skips the formatter check and the coverage floor. Only `check` runs all three.
- **Putting `jacocoTestCoverageVerification` before the tests.** The `dependsOn(tasks.test)` line prevents it.
- **Looking for the XML report in the wrong place.** It is `build/reports/jacoco/test/jacocoTestReport.xml`, written only because of the `xml.required = true` line.
- **Forgetting that the build file runs.** It is a program. Typing mistakes show up as Kotlin errors, not as friendly messages.

## Check yourself

1. *What is a task?* One named unit of work, such as `compileJava`, `test` or `check`. Gradle runs a task only after the tasks it depends on.
2. *What does `testImplementation(platform(libs.junit.bom))` do?* It loads a list of matching versions for the JUnit family, so the individual JUnit libraries need no version of their own.
3. *Why does `-Werror` matter?* It turns every compiler warning into an error, so no warning can stay unnoticed.
4. *Which line makes the coverage floor part of `check`?* `dependsOn(tasks.jacocoTestCoverageVerification)` inside `tasks.check`.
5. *What does `xml.required = true` add?* A machine-readable coverage report, `jacocoTestReport.xml`, next to the HTML one.

## Further reading

- Gradle 9.9.1 user guide: <https://docs.gradle.org/9.9.1/userguide/userguide.html>
- Build file basics: <https://docs.gradle.org/9.9.1/userguide/part1_gradle_init.html>
- Java toolchains: <https://docs.gradle.org/9.9.1/userguide/toolchains.html>
- Kotlin DSL primer: <https://docs.gradle.org/9.9.1/userguide/kotlin_dsl.html>
- The Java plugin and its `check` task: <https://docs.gradle.org/9.9.1/userguide/java_plugin.html>
- The JaCoCo plugin and its reports: <https://docs.gradle.org/9.9.1/userguide/jacoco_plugin.html>
