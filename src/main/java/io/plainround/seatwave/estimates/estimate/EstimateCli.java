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
