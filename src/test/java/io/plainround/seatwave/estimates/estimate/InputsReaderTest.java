package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputsReaderTest {

  @Test
  void acceptsTheProductionSheet() {
    Inputs inputs = Sheets.inputsOf(Sheets.PRODUCTION);

    assertThat(inputs.get(InputRule.DAILY_ACTIVE_USERS).toString()).isEqualTo("5,000,000 users");
    assertThat(inputs.stores())
        .extracting(Store::name)
        .containsExactly("bookings", "seats", "logs", "posters");
  }

  @Test
  void rejectsAResponseSizeWrittenInBits() {
    List<SheetEntry> entries = Sheets.productionWith("response_bytes", "305696", "bits");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.WrongUnit(9, "response_bytes", "bytes", "bits"));
  }

  @Test
  void rejectsASizeWrittenInKilobytesBecauseKilobyteIsAmbiguous() {
    List<SheetEntry> entries = Sheets.productionWith("store.bookings.bytes_per_item", "2", "KB");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(
            new SheetProblem.WrongUnit(12, "store.bookings.bytes_per_item", "bytes", "KB"));
  }

  @Test
  void rejectsARateWrittenPerDayWhereOnePerSecondIsExpected() {
    List<SheetEntry> entries = Sheets.productionWith("knee_per_server", "69120000", "requests/day");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(
            new SheetProblem.WrongUnit(35, "knee_per_server", "requests/s", "requests/day"));
  }

  @Test
  void rejectsAShareAboveOneHundredPercent() {
    List<SheetEntry> entries = Sheets.productionWith("write_share", "170", "percent");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.OutOfRange(7, "write_share", "from 0 to 100"));
  }

  @Test
  void rejectsAPeakFactorBelowOne() {
    List<SheetEntry> entries = Sheets.productionWith("peak_factor", "0.5", "ratio");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.OutOfRange(6, "peak_factor", "1 or more"));
  }

  @Test
  void rejectsZeroUsersAndAHalfSpareServer() {
    List<SheetEntry> entries =
        Sheets.productionWith("daily_active_users", "0", "users").stream()
            .map(entry ->
                entry.quantity().equals("spare_servers")
                    ? entry.withValue(new BigDecimal("0.5"))
                    : entry)
            .toList();

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(
            new SheetProblem.OutOfRange(4, "daily_active_users", "greater than 0"),
            new SheetProblem.OutOfRange(38, "spare_servers", "a whole number, 0 or more"));
  }

  @Test
  void rejectsAnUnknownQuantity() {
    List<SheetEntry> entries = new ArrayList<>(Sheets.entriesOf(Sheets.PRODUCTION));
    entries.add(new SheetEntry(99, "peak_factr", BigDecimal.TEN, "ratio", "typo"));
    entries.add(new SheetEntry(100, "store.bookings.colour", BigDecimal.ONE, "ratio", "x"));
    entries.add(
        new SheetEntry(101, "store.Bookings.items_per_day", BigDecimal.ONE, "items/day", "x"));
    entries.add(new SheetEntry(102, "store..items_per_day", BigDecimal.ONE, "items/day", "x"));

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(
            new SheetProblem.UnknownQuantity(99, "peak_factr"),
            new SheetProblem.UnknownQuantity(100, "store.bookings.colour"),
            new SheetProblem.UnknownQuantity(101, "store.Bookings.items_per_day"),
            new SheetProblem.UnknownQuantity(102, "store..items_per_day"));
  }

  @Test
  void rejectsTheSameQuantityTwice() {
    List<SheetEntry> entries = new ArrayList<>(Sheets.entriesOf(Sheets.PRODUCTION));
    entries.add(new SheetEntry(99, "peak_factor", BigDecimal.TEN, "ratio", "second guess"));

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.Duplicate(99, "peak_factor", 6));
  }

  @Test
  void rejectsAMissingInput() {
    List<SheetEntry> entries = Sheets.productionWithout("headroom");

    assertThat(Sheets.inputProblems(entries)).containsExactly(new SheetProblem.Missing("headroom"));
  }

  @Test
  void rejectsAStoreWithAMissingField() {
    List<SheetEntry> entries = Sheets.productionWithout("store.logs.retention_days");

    assertThat(Sheets.inputProblems(entries))
        .containsExactly(new SheetProblem.Missing("store.logs.retention_days"));
  }

  @Test
  void rejectsASheetWithNoStore() {
    List<SheetEntry> entries =
        Sheets.entriesOf(Sheets.PRODUCTION).stream()
            .filter(entry -> !entry.quantity().startsWith("store."))
            .toList();

    assertThat(Sheets.inputProblems(entries)).containsExactly(new SheetProblem.NoStore());
  }

  @Test
  void rejectsAnEmptyListWithEveryMissingInput() {
    List<SheetProblem> problems = Sheets.inputProblems(List.of());

    assertThat(problems).hasSize(InputRule.values().length + 1);
  }
}
