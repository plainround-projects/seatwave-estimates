package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tries to build every record of the estimate in an invalid state, and expects a refusal. */
class RecordGuardsTest {

  private final Inputs production = Sheets.inputsOf(Sheets.PRODUCTION);

  @Test
  void refusesInputsWithAValueInTheWrongUnit() {
    Map<InputRule, Quantity> values = new EnumMap<>(production.values());
    values.put(InputRule.RESPONSE_BYTES, Quantity.of("38212", Unit.GIGABYTES));

    assertThatThrownBy(() -> new Inputs(values, production.stores()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("response_bytes must be greater than 0 bytes");
  }

  @Test
  void refusesInputsWithAValueOutOfRange() {
    Map<InputRule, Quantity> values = new EnumMap<>(production.values());
    values.put(InputRule.UTILIZATION_TARGET, Quantity.of("0", Unit.PERCENT));

    assertThatThrownBy(() -> new Inputs(values, production.stores()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesInputsWithAMissingValue() {
    Map<InputRule, Quantity> values = new EnumMap<>(production.values());
    values.remove(InputRule.PEAK_FACTOR);

    assertThatThrownBy(() -> new Inputs(values, production.stores()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("peak_factor");
  }

  @Test
  void refusesInputsWithoutStoresOrWithTwoOfOneName() {
    Store bookings = production.stores().getFirst();

    assertThatThrownBy(() -> new Inputs(production.values(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Inputs(production.values(), List.of(bookings, bookings)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesAStoreWithABadNameOrAMissingField() {
    Store bookings = production.stores().getFirst();
    Map<StoreField, Quantity> fields = new EnumMap<>(bookings.fields());
    fields.remove(StoreField.RETENTION_DAYS);

    assertThatThrownBy(() -> new Store("Bookings!", bookings.fields()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Store("bookings", fields))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void refusesAStoreFieldInTheWrongUnit() {
    Store bookings = production.stores().getFirst();
    Map<StoreField, Quantity> fields = new EnumMap<>(bookings.fields());
    fields.put(StoreField.REPLICATION_FACTOR, Quantity.of("3", Unit.RATIO));

    assertThatThrownBy(() -> new Store("bookings", fields))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bookings.replication_factor must be 1 or more copies");
  }

  @Test
  void keepsInputsUnchangedWhenTheCallersMapChanges() {
    Map<InputRule, Quantity> values = new EnumMap<>(production.values());
    Inputs inputs = new Inputs(values, production.stores());

    values.put(InputRule.PEAK_FACTOR, Quantity.of("99", Unit.RATIO));

    assertThat(inputs.get(InputRule.PEAK_FACTOR).amount()).isEqualByComparingTo("26");
  }

  @Test
  void refusesAnEstimateWithTwoStepsOfOneIdAndAnUnknownId() {
    Step step =
        new Step("rate.peak", "RATE", "Peak", "x", Quantity.of("1", Unit.REQUESTS_PER_SECOND));

    assertThatThrownBy(() -> new Estimate(List.of(step, step)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Estimate(List.of(step)).result("rate.average"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesASheetEntryWithoutALineNumberOrASource() {
    assertThatThrownBy(() -> new SheetEntry(0, "peak_factor", BigDecimal.ONE, "ratio", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SheetEntry(1, "peak_factor", BigDecimal.ONE, "ratio", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void refusesAStepOrARowWithMissingParts() {
    assertThatThrownBy(() -> new Step("id", "RATE", "label", "formula", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new SensitivityRow("q", "doubled", "step", null, false))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void findsRulesAndFieldsByTheirSheetNames() {
    assertThat(InputRule.withKey("peak_factor")).contains(InputRule.PEAK_FACTOR);
    assertThat(InputRule.withKey("peak")).isEmpty();
    assertThat(StoreField.withKey("retention_days")).contains(StoreField.RETENTION_DAYS);
    assertThat(InputRule.HEADROOM.unit()).isEqualTo(Unit.PERCENT);
    assertThat(StoreField.ITEMS_PER_DAY.unit()).isEqualTo(Unit.ITEMS_PER_DAY);
  }
}
