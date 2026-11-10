package io.plainround.seatwave.estimates.estimate;

import com.github.sviperll.result4j.Result;
import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Doubles each input in turn and reports the result that moved most.
 *
 * <p>Every formula of the estimator is a product or a sum, so doubling one input can move a result
 * by at most a small factor. A result that moves more than {@link #FLAG_FACTOR} times means a
 * formula grows faster than its inputs, which in an estimate is almost always a mistake. Stateless
 * and safe to use from any thread.
 */
public final class Sensitivity {

  /** The prompt's rule: a result that changes more than ten times when one input doubles. */
  public static final BigDecimal FLAG_FACTOR = new BigDecimal("10");

  private static final BigDecimal TWO = new BigDecimal("2");
  private static final MathContext MOVEMENT_DIGITS = new MathContext(6);

  private Sensitivity() {}

  /**
   * Runs the check.
   *
   * @param entries the sheet entries the estimate came from
   * @param estimator the formula to test; the real one is {@code Estimator::estimate}
   * @return one row per entry, in sheet order
   * @throws IllegalArgumentException if the entries themselves do not make valid inputs
   */
  public static List<SensitivityRow> check(
      List<SheetEntry> entries, Function<Inputs, Estimate> estimator) {
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(estimator, "estimator");
    Estimate baseline =
        estimator.apply(
            inputsFrom(entries)
                .orElseThrow(() -> new IllegalArgumentException("the sheet is not valid")));
    List<SensitivityRow> rows = new ArrayList<>();
    for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
      rows.add(vary(entries, entryIndex, baseline, estimator));
    }
    return List.copyOf(rows);
  }

  private static SensitivityRow vary(
      List<SheetEntry> entries,
      int entryIndex,
      Estimate baseline,
      Function<Inputs, Estimate> estimator) {
    SheetEntry entry = entries.get(entryIndex);
    BigDecimal doubled = entry.value().multiply(TWO);
    Optional<Inputs> changed = inputsFrom(replaced(entries, entryIndex, doubled));
    String change = "doubled";
    if (changed.isEmpty()) {
      // A share of 85 percent cannot double, so it is halved: the test still moves it by 2 times.
      changed =
          inputsFrom(replaced(entries, entryIndex, entry.value().divide(TWO, Quantity.PRECISION)));
      change = "halved";
    }
    // Every range allows either the double or the half of a valid value, so this cannot be empty.
    Inputs varied =
        changed.orElseThrow(() -> new IllegalStateException("cannot vary " + entry.quantity()));
    return compare(entry.quantity(), change, baseline, estimator.apply(varied));
  }

  private static SensitivityRow compare(
      String quantity, String change, Estimate baseline, Estimate changed) {
    Map<String, Quantity> changedById =
        changed.steps().stream().collect(Collectors.toMap(Step::id, Step::result));
    String mostMoved = "no result moved";
    Quantity largest = new Quantity(BigDecimal.ONE, Unit.TIMES);
    for (Step step : baseline.steps()) {
      Quantity after = changedById.get(step.id());
      // A zero has no ratio; such a step is skipped rather than counted as an infinite move.
      if (after == null || after.amount().signum() == 0 || step.result().amount().signum() == 0) {
        continue;
      }
      Quantity movement = movementBetween(step.result(), after);
      if (movement.amount().compareTo(largest.amount()) > 0) {
        largest = movement;
        mostMoved = step.label();
      }
    }
    boolean isFlagged = largest.amount().compareTo(FLAG_FACTOR) > 0;
    return new SensitivityRow(quantity, change, mostMoved, largest, isFlagged);
  }

  private static Quantity movementBetween(Quantity before, Quantity after) {
    Quantity grown = after.timesAsMany(before);
    // Shrinking to a tenth is as suspicious as growing ten times, so both count the same way.
    Quantity movement =
        grown.amount().compareTo(BigDecimal.ONE) >= 0 ? grown : before.timesAsMany(after);
    // Rounding hides the last-digit noise of division, so equal moves tie and the first one wins.
    return new Quantity(movement.amount().round(MOVEMENT_DIGITS), Unit.TIMES);
  }

  private static List<SheetEntry> replaced(
      List<SheetEntry> entries, int entryIndex, BigDecimal value) {
    List<SheetEntry> copy = new ArrayList<>(entries);
    copy.set(entryIndex, entries.get(entryIndex).withValue(value));
    return copy;
  }

  private static Optional<Inputs> inputsFrom(List<SheetEntry> entries) {
    return switch (InputsReader.read(entries)) {
      case Result.Success<Inputs, List<SheetProblem>>(Inputs inputs) -> Optional.of(inputs);
      case Result.Error<Inputs, List<SheetProblem>> _ -> Optional.empty();
    };
  }
}
