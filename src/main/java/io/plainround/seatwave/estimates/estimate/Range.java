package io.plainround.seatwave.estimates.estimate;

import java.math.BigDecimal;

/**
 * The values an input may take. Each range is a rule written once and used by both the sheet
 * reader, which reports a broken rule, and the input records, which refuse to exist with one.
 */
enum Range {
  /** Greater than zero. */
  POSITIVE("greater than 0"),
  /** Zero or more. */
  NON_NEGATIVE("0 or more"),
  /** One or more: a multiplier that can only add, such as a replication factor. */
  AT_LEAST_ONE("1 or more"),
  /** A share from 0 to 100 percent. */
  SHARE("from 0 to 100"),
  /** A share above 0 and at most 100 percent. */
  POSITIVE_SHARE("above 0 and at most 100"),
  /** A whole number, zero or more. */
  WHOLE_NUMBER("a whole number, 0 or more");

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final String description;

  Range(String description) {
    this.description = description;
  }

  /**
   * Tells whether a value obeys the rule.
   *
   * @param value the value to check
   * @return true when the value is inside the range
   */
  boolean contains(BigDecimal value) {
    return switch (this) {
      case POSITIVE -> value.signum() > 0;
      case NON_NEGATIVE -> value.signum() >= 0;
      case AT_LEAST_ONE -> value.compareTo(BigDecimal.ONE) >= 0;
      case SHARE -> value.signum() >= 0 && value.compareTo(HUNDRED) <= 0;
      case POSITIVE_SHARE -> value.signum() > 0 && value.compareTo(HUNDRED) <= 0;
      case WHOLE_NUMBER -> value.signum() >= 0 && value.stripTrailingZeros().scale() <= 0;
    };
  }

  /**
   * Describes the rule in words.
   *
   * @return for example "from 0 to 100"
   */
  String description() {
    return description;
  }
}
