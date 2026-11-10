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

  // A step line ends with "= <number> <unit>", so the last token after the number is the unit.
  private static final Pattern STEP_RESULT = Pattern.compile(".* = [0-9][0-9,.]* (\\S+)$");

  private final List<SheetEntry> entries = Sheets.entriesOf(Sheets.PRODUCTION);
  private final Estimate estimate = Estimator.estimate(Sheets.inputs(entries));
  private final String report =
      EstimateReport.render(entries, estimate, Sensitivity.check(entries, Estimator::estimate));

  @Test
  void reproducesTheSameEstimateFromThePrintedInputs() {
    List<String> printedSheet = report.lines().skip(1).takeWhile(line -> !line.isBlank()).toList();

    Estimate fromPrintout =
        Estimator.estimate(Sheets.inputs(Sheets.entries(CsvSheet.parse(printedSheet))));

    assertThat(fromPrintout).isEqualTo(estimate);
  }

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

  @Test
  void neverPrintsBitsAndBytesInOneResult() {
    assertThat(report.lines().filter(line -> line.endsWith("Gbps")))
        .allSatisfy(line -> assertThat(line).doesNotEndWith("bytes/s"));
    assertThat(estimate.result("bandwidth.egress_peak_gbps").unit())
        .isNotEqualTo(estimate.result("bandwidth.egress_peak").unit());
  }

  @Test
  void printsTheFiveLinesInOrder() {
    int rate = report.indexOf("1. RATE");
    int size = report.indexOf("2. SIZE");
    int storage = report.indexOf("3. STORAGE");
    int bandwidth = report.indexOf("4. BANDWIDTH");
    int servers = report.indexOf("5. SERVERS");

    assertThat(List.of(rate, size, storage, bandwidth, servers)).isSorted().doesNotContain(-1);
  }

  @Test
  void saysWhenNothingWasFlagged() {
    assertThat(report).contains("No result moved more than 10 times when any one input doubled.");
  }

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
}
