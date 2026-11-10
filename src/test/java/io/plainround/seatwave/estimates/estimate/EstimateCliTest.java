package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EstimateCliTest {

  @Test
  void explainsEveryKindOfSheetProblemWithItsLine() {
    assertThat(EstimateCli.explain(new SheetProblem.WrongHeader("a,b")))
        .isEqualTo("header: expected 'quantity,value,unit,source', found 'a,b'");
    assertThat(EstimateCli.explain(new SheetProblem.WrongColumnCount(4))).startsWith("line 4:");
    assertThat(EstimateCli.explain(new SheetProblem.NotANumber(5, "lots")))
        .isEqualTo("line 5: 'lots' is not a plain decimal number");
    assertThat(EstimateCli.explain(new SheetProblem.MissingSource(6))).contains("source");
    assertThat(EstimateCli.explain(new SheetProblem.UnknownQuantity(7, "peak")))
        .isEqualTo("line 7: 'peak' is not an input");
    assertThat(
            EstimateCli.explain(new SheetProblem.WrongUnit(9, "response_bytes", "bytes", "bits")))
        .isEqualTo("line 9: response_bytes must be in bytes, not bits");
    assertThat(EstimateCli.explain(new SheetProblem.OutOfRange(7, "write_share", "from 0 to 100")))
        .isEqualTo("line 7: write_share must be from 0 to 100");
    assertThat(EstimateCli.explain(new SheetProblem.Duplicate(99, "peak_factor", 6)))
        .isEqualTo("line 99: peak_factor was already given on line 6");
    assertThat(EstimateCli.explain(new SheetProblem.Missing("headroom")))
        .isEqualTo("missing input: headroom");
    assertThat(EstimateCli.explain(new SheetProblem.NoStore())).contains("store.");
  }
}
