package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import java.util.Objects;

/**
 * What happened to the estimate when one input was doubled, or halved when doubling would leave its
 * range.
 *
 * @param quantity the input that was changed
 * @param change "doubled" or "halved"
 * @param mostMovedStep the label of the result that moved most
 * @param movement how many times larger or smaller that result became, always 1 or more
 * @param isFlagged true when the movement is above the flag factor: a sign of a broken formula
 */
public record SensitivityRow(
    String quantity, String change, String mostMovedStep, Quantity movement, boolean isFlagged) {

  /**
   * Creates a row.
   *
   * @throws NullPointerException if any text or the movement is null
   */
  public SensitivityRow {
    Objects.requireNonNull(quantity, "quantity");
    Objects.requireNonNull(change, "change");
    Objects.requireNonNull(mostMovedStep, "mostMovedStep");
    Objects.requireNonNull(movement, "movement");
  }
}
