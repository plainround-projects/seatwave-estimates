package io.plainround.seatwave.estimates.estimate;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One data line of an estimation sheet, read but not yet checked against the input rules.
 *
 * @param lineNumber the line in the file, counted from 1, so a problem can point at it
 * @param quantity the name in the first column, for example "peak_factor"
 * @param value the number in the second column
 * @param unit the text of the third column, for example "ratio"
 * @param source where the number comes from: a measurement, a document or an assumption
 */
public record SheetEntry(
    int lineNumber, String quantity, BigDecimal value, String unit, String source) {

  /**
   * Creates an entry.
   *
   * @throws NullPointerException if any text or the value is null
   * @throws IllegalArgumentException if the line number is below 1
   */
  public SheetEntry {
    Objects.requireNonNull(quantity, "quantity");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(source, "source");
    if (lineNumber < 1) {
      throw new IllegalArgumentException("line numbers start at 1, but was " + lineNumber);
    }
  }

  /**
   * Returns the same entry with another value, for the sensitivity check.
   *
   * @param newValue the value to put in place of the current one
   * @return a copy that differs only in its value
   */
  public SheetEntry withValue(BigDecimal newValue) {
    return new SheetEntry(lineNumber, quantity, newValue, unit, source);
  }
}
