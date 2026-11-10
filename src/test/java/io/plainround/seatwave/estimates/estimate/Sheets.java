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

  static final Path PRODUCTION = Path.of("sheets", "seatwave-production.csv");
  static final Path SIMPLE = Path.of("sheets", "seatwave-simple.csv");
  static final Path PHOTO_SHARING = Path.of("sheets", "photo-sharing.csv");
  static final Path PHOTO_SHARING_GIVEN = Path.of("sheets", "photo-sharing-given.csv");
  static final Path LAB1_UNIT_SLIPS = Path.of("sheets", "lab1-unit-slips.csv");

  private Sheets() {}

  static List<String> linesOf(Path sheet) {
    try {
      return Files.readAllLines(sheet, StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  static List<SheetEntry> entriesOf(Path sheet) {
    return entries(CsvSheet.parse(linesOf(sheet)));
  }

  static List<SheetEntry> entries(Result<List<SheetEntry>, List<SheetProblem>> parsed) {
    return switch (parsed) {
      case Result.Success<List<SheetEntry>, List<SheetProblem>>(List<SheetEntry> entries) ->
          entries;
      case Result.Error<List<SheetEntry>, List<SheetProblem>>(List<SheetProblem> problems) ->
          fail("expected entries but got " + problems);
    };
  }

  static List<SheetProblem> parseProblems(Result<List<SheetEntry>, List<SheetProblem>> parsed) {
    return switch (parsed) {
      case Result.Success<List<SheetEntry>, List<SheetProblem>>(List<SheetEntry> entries) ->
          fail("expected problems but got " + entries);
      case Result.Error<List<SheetEntry>, List<SheetProblem>>(List<SheetProblem> problems) ->
          problems;
    };
  }

  static Inputs inputsOf(Path sheet) {
    return inputs(entriesOf(sheet));
  }

  static Inputs inputs(List<SheetEntry> entries) {
    return switch (InputsReader.read(entries)) {
      case Result.Success<Inputs, List<SheetProblem>>(Inputs inputs) -> inputs;
      case Result.Error<Inputs, List<SheetProblem>>(List<SheetProblem> problems) ->
          fail("expected inputs but got " + problems);
    };
  }

  static List<SheetProblem> inputProblems(List<SheetEntry> entries) {
    return switch (InputsReader.read(entries)) {
      case Result.Success<Inputs, List<SheetProblem>>(Inputs inputs) ->
          fail("expected problems but got " + inputs);
      case Result.Error<Inputs, List<SheetProblem>>(List<SheetProblem> problems) -> problems;
    };
  }

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

  static List<SheetEntry> productionWithout(String quantity) {
    return entriesOf(PRODUCTION).stream()
        .filter(entry -> !entry.quantity().equals(quantity))
        .toList();
  }
}
