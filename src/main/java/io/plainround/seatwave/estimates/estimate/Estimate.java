package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The result of one estimate: every step of the arithmetic, in the order it was done.
 *
 * <p>Instances are immutable and safe to share between threads.
 *
 * @param steps the steps, each with a distinct id
 */
public record Estimate(List<Step> steps) {

  /**
   * Creates an estimate.
   *
   * @throws IllegalArgumentException if two steps share an id
   */
  public Estimate {
    Objects.requireNonNull(steps, "steps");
    steps = List.copyOf(steps);
    Set<String> ids = new HashSet<>();
    for (Step step : steps) {
      if (!ids.add(step.id())) {
        throw new IllegalArgumentException("two steps have the id " + step.id());
      }
    }
  }

  /**
   * Finds the result of one step.
   *
   * @param id the step's id, for example "servers.peak"
   * @return the result
   * @throws IllegalArgumentException if no step has that id
   */
  public Quantity result(String id) {
    return steps.stream()
        .filter(step -> step.id().equals(id))
        .findFirst()
        .map(Step::result)
        .orElseThrow(() -> new IllegalArgumentException("no step has the id " + id));
  }
}
