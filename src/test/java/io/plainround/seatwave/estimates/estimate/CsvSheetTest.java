package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class CsvSheetTest {

  private static final String HEADER = CsvSheet.HEADER;

  @Test
  void readsOneLineWithItsLineNumber() {
    List<SheetEntry> entries =
        Sheets.entries(
            CsvSheet.parse(List.of("# comment", HEADER, "peak_factor,26,ratio,the sale")));

    assertThat(entries).hasSize(1);
    assertThat(entries.getFirst().lineNumber()).isEqualTo(3);
    assertThat(entries.getFirst().quantity()).isEqualTo("peak_factor");
    assertThat(entries.getFirst().value()).isEqualByComparingTo("26");
    assertThat(entries.getFirst().unit()).isEqualTo("ratio");
  }

  @Test
  void skipsBlankLinesAndComments() {
    List<SheetEntry> entries =
        Sheets.entries(
            CsvSheet.parse(List.of("", "# a", HEADER, "", "# b", "peak_factor,2,ratio,x")));

    assertThat(entries).hasSize(1);
  }

  @Test
  void readsUnderscoresAsDigitGroups() {
    List<SheetEntry> entries =
        Sheets.entries(CsvSheet.parse(List.of(HEADER, "daily_active_users,5_000_000,users,x")));

    assertThat(entries.getFirst().value()).isEqualByComparingTo("5000000");
  }

  @Test
  void keepsCommasInsideAQuotedSource() {
    List<SheetEntry> entries =
        Sheets.entries(
            CsvSheet.parse(
                List.of(HEADER, "peak_factor,26,ratio,\"1,000,000 fans, \"\"the sale\"\"\"")));

    assertThat(entries.getFirst().source()).isEqualTo("1,000,000 fans, \"the sale\"");
  }

  @Test
  void writesWhatItReadsSoASheetSurvivesARoundTrip() {
    List<SheetEntry> entries = Sheets.entriesOf(Sheets.PRODUCTION);

    List<SheetEntry> again = Sheets.entries(CsvSheet.parse(CsvSheet.render(entries)));

    assertThat(again)
        .extracting(SheetEntry::quantity, SheetEntry::value, SheetEntry::unit, SheetEntry::source)
        .containsExactlyElementsOf(
            entries.stream()
                .map(entry ->
                    Tuple.tuple(entry.quantity(), entry.value(), entry.unit(), entry.source()))
                .toList());
  }

  @Test
  void rejectsASheetWithoutTheHeader() {
    List<SheetProblem> problems =
        Sheets.parseProblems(CsvSheet.parse(List.of("name,value", "peak_factor,26,ratio,x")));

    assertThat(problems).containsExactly(new SheetProblem.WrongHeader("name,value"));
  }

  @Test
  void rejectsAnEmptyFile() {
    assertThat(Sheets.parseProblems(CsvSheet.parse(List.of("# only a comment"))))
        .containsExactly(new SheetProblem.WrongHeader(""));
  }

  @Test
  void rejectsALineWithTooFewColumns() {
    assertThat(Sheets.parseProblems(CsvSheet.parse(List.of(HEADER, "peak_factor,26"))))
        .containsExactly(new SheetProblem.WrongColumnCount(2));
  }

  @Test
  void rejectsAValueThatIsNotAPlainNumber() {
    List<String> lines =
        List.of(
            HEADER,
            "peak_factor,lots,ratio,x",
            "write_share,-5,percent,x",
            "request_bytes,3e2,bytes,x",
            "response_bytes,38 212,bytes,x");

    assertThat(Sheets.parseProblems(CsvSheet.parse(lines)))
        .containsExactly(
            new SheetProblem.NotANumber(2, "lots"),
            new SheetProblem.NotANumber(3, "-5"),
            new SheetProblem.NotANumber(4, "3e2"),
            new SheetProblem.NotANumber(5, "38 212"));
  }

  @Test
  void rejectsALineWithNoSource() {
    assertThat(Sheets.parseProblems(CsvSheet.parse(List.of(HEADER, "peak_factor,26,ratio,  "))))
        .containsExactly(new SheetProblem.MissingSource(2));
  }

  @Test
  void reportsEveryBadLineNotOnlyTheFirst() {
    List<String> lines = List.of(HEADER, "a,1", "b,x,ratio,y", "c,1,ratio,");

    assertThat(Sheets.parseProblems(CsvSheet.parse(lines))).hasSize(3);
  }
}
