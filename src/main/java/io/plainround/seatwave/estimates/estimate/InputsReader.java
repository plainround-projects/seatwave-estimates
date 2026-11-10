package io.plainround.seatwave.estimates.estimate;

import com.github.sviperll.result4j.Result;
import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Turns sheet entries into checked {@link Inputs}, collecting every problem instead of stopping at
 * the first, so one run shows all the work left.
 *
 * <p>It uses the same rules as the records' constructors, so each rule is written once. Stateless
 * and safe to use from any thread.
 */
public final class InputsReader {

  private static final String STORE_PREFIX = "store.";
  private static final char NAME_SEPARATOR = '.';

  private InputsReader() {}

  /**
   * Checks entries against the input rules.
   *
   * @param entries the entries of one sheet
   * @return the inputs, or every problem found
   */
  public static Result<Inputs, List<SheetProblem>> read(List<SheetEntry> entries) {
    Objects.requireNonNull(entries, "entries");
    List<SheetProblem> problems = new ArrayList<>();
    Map<String, Integer> firstLineOf = new HashMap<>();
    Map<InputRule, Quantity> values = new EnumMap<>(InputRule.class);
    Map<String, Map<StoreField, Quantity>> stores = new LinkedHashMap<>();
    for (SheetEntry entry : entries) {
      Integer firstLine = firstLineOf.putIfAbsent(entry.quantity(), entry.lineNumber());
      if (firstLine != null) {
        problems.add(new SheetProblem.Duplicate(entry.lineNumber(), entry.quantity(), firstLine));
        continue;
      }
      readEntry(entry, values, stores).ifPresent(problems::add);
    }
    addMissing(firstLineOf.keySet(), stores.keySet(), problems);
    if (!problems.isEmpty()) {
      return Result.error(List.copyOf(problems));
    }
    List<Store> storeList = new ArrayList<>();
    stores.forEach((name, fields) -> storeList.add(new Store(name, fields)));
    return Result.success(new Inputs(values, storeList));
  }

  private static Optional<SheetProblem> readEntry(
      SheetEntry entry,
      Map<InputRule, Quantity> values,
      Map<String, Map<StoreField, Quantity>> stores) {
    Optional<InputRule> rule = InputRule.withKey(entry.quantity());
    if (rule.isPresent()) {
      Optional<SheetProblem> problem = check(entry, rule.get().unit(), rule.get().range());
      if (problem.isEmpty()) {
        values.put(rule.get(), new Quantity(entry.value(), rule.get().unit()));
      }
      return problem;
    }
    return readStoreEntry(entry, stores);
  }

  private static Optional<SheetProblem> readStoreEntry(
      SheetEntry entry, Map<String, Map<StoreField, Quantity>> stores) {
    String quantity = entry.quantity();
    int separator = quantity.lastIndexOf(NAME_SEPARATOR);
    if (!quantity.startsWith(STORE_PREFIX) || separator <= STORE_PREFIX.length()) {
      return Optional.of(new SheetProblem.UnknownQuantity(entry.lineNumber(), quantity));
    }
    String storeName = quantity.substring(STORE_PREFIX.length(), separator);
    Optional<StoreField> field = StoreField.withKey(quantity.substring(separator + 1));
    if (field.isEmpty() || !Store.NAME.matcher(storeName).matches()) {
      return Optional.of(new SheetProblem.UnknownQuantity(entry.lineNumber(), quantity));
    }
    Optional<SheetProblem> problem = check(entry, field.get().unit(), field.get().range());
    if (problem.isEmpty()) {
      stores
          .computeIfAbsent(storeName, name -> new EnumMap<>(StoreField.class))
          .put(field.get(), new Quantity(entry.value(), field.get().unit()));
    }
    return problem;
  }

  private static Optional<SheetProblem> check(SheetEntry entry, Unit unit, Range range) {
    if (!unit.symbol().equals(entry.unit())) {
      return Optional.of(
          new SheetProblem.WrongUnit(
              entry.lineNumber(), entry.quantity(), unit.symbol(), entry.unit()));
    }
    if (!range.contains(entry.value())) {
      return Optional.of(
          new SheetProblem.OutOfRange(entry.lineNumber(), entry.quantity(), range.description()));
    }
    return Optional.empty();
  }

  // An input counts as given when any line names it, even a rejected one, so a wrong unit is
  // reported once, as a wrong unit, and not a second time as missing.
  private static void addMissing(
      Set<String> givenNames, Set<String> storeNames, List<SheetProblem> problems) {
    for (InputRule rule : InputRule.values()) {
      if (!givenNames.contains(rule.key())) {
        problems.add(new SheetProblem.Missing(rule.key()));
      }
    }
    boolean hasStoreLine = givenNames.stream().anyMatch(name -> name.startsWith(STORE_PREFIX));
    if (!hasStoreLine) {
      problems.add(new SheetProblem.NoStore());
    }
    for (String storeName : storeNames) {
      for (StoreField field : StoreField.values()) {
        String name = STORE_PREFIX + storeName + NAME_SEPARATOR + field.key();
        if (!givenNames.contains(name)) {
          problems.add(new SheetProblem.Missing(name));
        }
      }
    }
  }
}
