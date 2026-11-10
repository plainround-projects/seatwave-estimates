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

  private final List<SheetEntry> production = Sheets.entriesOf(Sheets.PRODUCTION);

  @Test
  void flagsNothingForTheRealEstimator() {
    List<SensitivityRow> rows = Sensitivity.check(production, Estimator::estimate);

    assertThat(EstimateReport.hasFlag(rows)).isFalse();
    assertThat(rows).hasSize(production.size());
  }

  @Test
  void movesThePeakRateTwiceWhenUsersDouble() {
    SensitivityRow users = varied(Sensitivity.check(production, Estimator::estimate), 0);

    assertThat(users.quantity()).isEqualTo("daily_active_users");
    assertThat(users.change()).isEqualTo("doubled");
    assertThat(users.movement().amount()).isEqualByComparingTo("2");
    assertThat(users.mostMovedStep()).isEqualTo("Requests per day");
  }

  @Test
  void halvesAShareThatCannotDouble() {
    List<SheetEntry> entries = Sheets.productionWith("utilization_target", "70", "percent");

    SensitivityRow utilization = varied(Sensitivity.check(entries, Estimator::estimate), 32);

    assertThat(utilization.quantity()).isEqualTo("utilization_target");
    assertThat(utilization.change()).isEqualTo("halved");
  }

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

  @Test
  void skipsResultsThatAreZero() {
    List<SheetEntry> entries = Sheets.productionWith("write_share", "0", "percent");

    List<SensitivityRow> rows = Sensitivity.check(entries, Estimator::estimate);

    assertThat(EstimateReport.hasFlag(rows)).isFalse();
  }

  @Test
  void rejectsASheetThatIsNotValid() {
    List<SheetEntry> broken = Sheets.productionWithout("peak_factor");

    assertThatThrownBy(() -> Sensitivity.check(broken, Estimator::estimate))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static SensitivityRow varied(List<SensitivityRow> rows, int index) {
    return rows.get(index);
  }
}
