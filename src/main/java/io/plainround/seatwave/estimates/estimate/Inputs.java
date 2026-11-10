package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Every input of one estimate, each in its one allowed unit and inside its range.
 *
 * <p>The constructor checks every rule of {@link InputRule}, so an {@code Inputs} with a response
 * size in bits, a missing peak factor or a read share of 170 percent cannot exist. Instances are
 * immutable and safe to share between threads.
 *
 * @param values one value for every {@link InputRule}
 * @param stores the kinds of stored data, at least one, with distinct names
 */
public record Inputs(Map<InputRule, Quantity> values, List<Store> stores) {

  /**
   * Creates the inputs.
   *
   * @throws IllegalArgumentException if a value is missing or invalid, there is no store, or two
   *     stores share a name
   */
  public Inputs {
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(stores, "stores");
    Map<InputRule, Quantity> checked = new EnumMap<>(InputRule.class);
    for (InputRule rule : InputRule.values()) {
      checked.put(rule, rule.requireValid(values.get(rule)));
    }
    values = Map.copyOf(checked);
    stores = List.copyOf(stores);
    if (stores.isEmpty()) {
      throw new IllegalArgumentException("an estimate needs at least one store");
    }
    Set<String> names = new HashSet<>();
    for (Store store : stores) {
      if (!names.add(store.name())) {
        throw new IllegalArgumentException("two stores are named " + store.name());
      }
    }
  }

  /**
   * Returns one input.
   *
   * @param rule the input wanted
   * @return its value; never null, because the constructor requires every input
   */
  public Quantity get(InputRule rule) {
    return values.get(rule);
  }
}
