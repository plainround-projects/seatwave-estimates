package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import java.util.Objects;

/**
 * One line of visible arithmetic: what is computed, how, and the result with its unit.
 *
 * @param id a stable name, for example "rate.peak", so a result can be found and compared
 * @param section the line of the five-line estimate it belongs to, for example "RATE"
 * @param label what the result is, in words
 * @param formula the arithmetic with every operand and its unit
 * @param result the result; a {@link Quantity}, so it always carries a unit
 */
public record Step(String id, String section, String label, String formula, Quantity result) {

  /**
   * Creates a step.
   *
   * @throws NullPointerException if any part is null
   */
  public Step {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(section, "section");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(formula, "formula");
    Objects.requireNonNull(result, "result");
  }
}
