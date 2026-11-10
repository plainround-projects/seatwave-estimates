package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShippedSheetsTest {

  @Test
  void acceptsTheSimpleAndProductionSheets() {
    assertThat(Sheets.inputsOf(Sheets.SIMPLE).stores()).hasSize(2);
    assertThat(Sheets.inputsOf(Sheets.PRODUCTION).stores()).hasSize(4);
  }

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

  @Test
  void flagsNoResultInAnyShippedSheet() {
    for (Path sheet :
        List.of(
            Sheets.SIMPLE, Sheets.PRODUCTION, Sheets.PHOTO_SHARING, Sheets.PHOTO_SHARING_GIVEN)) {
      List<SensitivityRow> rows = Sensitivity.check(Sheets.entriesOf(sheet), Estimator::estimate);

      assertThat(EstimateReport.hasFlag(rows)).as(sheet.toString()).isFalse();
    }
  }
}
