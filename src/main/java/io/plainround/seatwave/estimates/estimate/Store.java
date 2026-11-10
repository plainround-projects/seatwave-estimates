package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One kind of stored data, such as bookings or log lines, with all five of its numbers.
 *
 * <p>The constructor refuses a store with a missing field, a field in the wrong unit or a value out
 * of range, so a half-described store cannot reach the estimator. Instances are immutable.
 *
 * @param name a short lowercase name, for example "bookings"
 * @param fields every {@link StoreField} with its value
 */
public record Store(String name, Map<StoreField, Quantity> fields) {

  /** Lowercase letters, digits and underscores, starting with a letter, at most 32 characters. */
  static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");

  /**
   * Creates a store.
   *
   * @throws IllegalArgumentException if the name is not allowed or a field is missing or invalid
   */
  public Store {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(fields, "fields");
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("a store name must match " + NAME + ", but was " + name);
    }
    Map<StoreField, Quantity> checked = new EnumMap<>(StoreField.class);
    for (StoreField field : StoreField.values()) {
      checked.put(field, field.requireValid(name, fields.get(field)));
    }
    fields = Map.copyOf(checked);
  }

  /**
   * Returns one field.
   *
   * @param field the field wanted
   * @return its value; never null, because the constructor requires every field
   */
  public Quantity get(StoreField field) {
    return fields.get(field);
  }
}
