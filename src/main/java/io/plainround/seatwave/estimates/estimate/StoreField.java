package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The five numbers every kind of stored data needs. A sheet writes each one as {@code
 * store.<name>.<field>}, for example {@code store.bookings.bytes_per_item}.
 */
public enum StoreField {
  /** New records or files written per day. */
  ITEMS_PER_DAY("items_per_day", Unit.ITEMS_PER_DAY, Range.NON_NEGATIVE),
  /** Size of one record or file. */
  BYTES_PER_ITEM("bytes_per_item", Unit.BYTES, Range.POSITIVE),
  /** How long one item is kept before it is deleted. */
  RETENTION_DAYS("retention_days", Unit.DAYS, Range.POSITIVE),
  /** Copies kept of every byte. */
  REPLICATION_FACTOR("replication_factor", Unit.COPIES, Range.AT_LEAST_ONE),
  /** Bytes stored per byte of data, for indexes and page overhead. */
  INDEX_OVERHEAD("index_overhead", Unit.RATIO, Range.AT_LEAST_ONE);

  private final String key;
  private final Unit unit;
  private final Range range;

  StoreField(String key, Unit unit, Range range) {
    this.key = key;
    this.unit = unit;
    this.range = range;
  }

  /**
   * Finds the field a sheet names after the store name.
   *
   * @param key for example "retention_days"
   * @return the field, or empty when there is none with that name
   */
  public static Optional<StoreField> withKey(String key) {
    return Arrays.stream(values()).filter(field -> field.key.equals(key)).findFirst();
  }

  /**
   * Returns the name a sheet uses after the store name.
   *
   * @return for example "items_per_day"
   */
  public String key() {
    return key;
  }

  /**
   * Returns the one unit the field must be written in.
   *
   * @return the unit
   */
  public Unit unit() {
    return unit;
  }

  Range range() {
    return range;
  }

  Quantity requireValid(String storeName, Quantity quantity) {
    Objects.requireNonNull(quantity, key);
    if (quantity.unit() != unit || !range.contains(quantity.amount())) {
      throw new IllegalArgumentException(
          storeName
              + "."
              + key
              + " must be "
              + range.description()
              + " "
              + unit.symbol()
              + ", but was "
              + quantity);
    }
    return quantity;
  }
}
