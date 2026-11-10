package io.plainround.seatwave.estimates.units;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * A number together with its unit. It is the only way this program holds or prints a number.
 *
 * <p>Two rules make unit slips impossible to print. First, {@link #plus} and {@link #timesAsMany}
 * refuse two different units, even two of one dimension such as bytes and GB, so every conversion
 * is written out with {@link #to}. Second, {@link #toString} always ends with the unit symbol.
 * Instances are immutable and safe to share between threads.
 *
 * @param amount the number, never negative
 * @param unit the unit the number is counted in
 */
public record Quantity(BigDecimal amount, Unit unit) {

  /** Sixteen significant digits: far more than any estimate input has, and exact for money. */
  public static final MathContext PRECISION = MathContext.DECIMAL64;

  // Numbers at or above this are printed without decimals: 30,093 requests/s, not 30,092.59.
  private static final BigDecimal WHOLE_NUMBER_FROM = new BigDecimal("100");
  // Numbers between 1 and 100 keep two decimals: 9.2 Gbps, 1.5 ratio.
  private static final int DECIMALS_BELOW_WHOLE = 2;
  private static final int CENTS = 2;
  // Numbers below 1 keep four significant digits, so a price of 0.2016 USD/hour prints in full.
  private static final MathContext SMALL_NUMBER_DIGITS = new MathContext(4, RoundingMode.HALF_UP);

  /**
   * Creates a quantity.
   *
   * @throws NullPointerException if the amount or the unit is null
   * @throws IllegalArgumentException if the amount is negative
   */
  public Quantity {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(unit, "unit");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("an estimate is never negative, but was " + amount);
    }
  }

  /**
   * Creates a quantity from decimal text.
   *
   * @param amount the number as text, for example "37000"
   * @param unit the unit
   * @return the quantity
   */
  public static Quantity of(String amount, Unit unit) {
    return new Quantity(new BigDecimal(amount), unit);
  }

  /**
   * Converts this quantity into another unit of the same dimension.
   *
   * @param target the unit wanted, for example {@link Unit#GIGABITS_PER_SECOND}
   * @return the same amount of the same thing, counted in the target unit
   * @throws IllegalArgumentException if the target measures something else
   */
  public Quantity to(Unit target) {
    Objects.requireNonNull(target, "target");
    if (target.dimension() != unit.dimension()) {
      throw new IllegalArgumentException(
          "cannot convert " + unit.symbol() + " into " + target.symbol());
    }
    BigDecimal inBaseUnits = amount.multiply(unit.sizeInBaseUnits(), PRECISION);
    return new Quantity(inBaseUnits.divide(target.sizeInBaseUnits(), PRECISION), target);
  }

  /**
   * Adds a quantity in exactly the same unit.
   *
   * @param other the quantity to add
   * @return the sum, in this unit
   * @throws IllegalArgumentException if the units differ, for example bytes and bits per second
   */
  public Quantity plus(Quantity other) {
    requireSameUnit(other, "add");
    return new Quantity(amount.add(other.amount, PRECISION), unit);
  }

  /**
   * Multiplies by a plain number and keeps the unit.
   *
   * @param factor the multiplier, for example a peak factor of 26
   * @return the scaled quantity, in this unit
   */
  public Quantity scaledBy(BigDecimal factor) {
    Objects.requireNonNull(factor, "factor");
    return new Quantity(amount.multiply(factor, PRECISION), unit);
  }

  /**
   * Tells how many times larger this quantity is than another in exactly the same unit.
   *
   * @param other the quantity to compare with; it must not be zero
   * @return this amount divided by the other amount, in {@link Unit#TIMES}
   * @throws IllegalArgumentException if the units differ or the other amount is zero
   */
  public Quantity timesAsMany(Quantity other) {
    requireSameUnit(other, "compare");
    if (other.amount.signum() == 0) {
      throw new IllegalArgumentException("cannot compare with zero " + unit.symbol());
    }
    return new Quantity(amount.divide(other.amount, PRECISION), Unit.TIMES);
  }

  /**
   * Formats the quantity for a person: grouped digits, a sensible number of decimals, then the
   * unit.
   *
   * @return for example "30,093 requests/s", "9.2 Gbps" or "147.17 USD/month"
   */
  @Override
  public String toString() {
    BigDecimal rounded = rounded();
    int decimals = isWholeMoney() ? CENTS : Math.max(0, rounded.stripTrailingZeros().scale());
    return String.format(Locale.ROOT, "%,." + decimals + "f", rounded) + " " + unit.symbol();
  }

  // Money of a dollar or more keeps its cents, so the Module 1 price reads 147.17, not 147.
  private boolean isWholeMoney() {
    boolean isMoney =
        unit.dimension() == Dimension.MONEY_PER_HOUR
            || unit.dimension() == Dimension.MONEY_PER_MONTH;
    return isMoney && amount.compareTo(BigDecimal.ONE) >= 0;
  }

  private BigDecimal rounded() {
    if (isWholeMoney()) {
      return amount.setScale(CENTS, RoundingMode.HALF_UP);
    }
    if (amount.compareTo(WHOLE_NUMBER_FROM) >= 0) {
      return amount.setScale(0, RoundingMode.HALF_UP);
    }
    if (amount.compareTo(BigDecimal.ONE) >= 0) {
      return amount.setScale(DECIMALS_BELOW_WHOLE, RoundingMode.HALF_UP);
    }
    return amount.round(SMALL_NUMBER_DIGITS);
  }

  private void requireSameUnit(Quantity other, String operation) {
    Objects.requireNonNull(other, "other");
    if (other.unit != unit) {
      // A mix of units is a bug in the formula, so it fails loudly instead of returning a value.
      throw new IllegalArgumentException(
          "cannot " + operation + " " + other.unit.symbol() + " and " + unit.symbol());
    }
  }
}
