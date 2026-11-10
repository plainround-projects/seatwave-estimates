package io.plainround.seatwave.estimates.probe;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Renders the measured latencies next to the published orders of magnitude.
 *
 * <p>Every duration is a {@link Quantity}, printed in the largest unit that keeps it at 1 or more.
 * A measured p50 that differs from the published value by more than ten times in either direction
 * is marked, because Lab 2 asks for each such gap to be explained. Stateless and safe to use from
 * any thread.
 */
public final class ProbeReport {

  /** A gap above this many times between measured and published values is marked. */
  static final BigDecimal GAP_FACTOR = new BigDecimal("10");

  private static final BigDecimal P50 = new BigDecimal("50");
  private static final BigDecimal P99 = new BigDecimal("99");
  private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
  private static final String ROW_FORMAT = "%-32s %-14s %-12s %-12s %-12s %-31s %s%n";

  private ProbeReport() {}

  /**
   * Renders the latency table.
   *
   * @param measured the samples of every operation that was measured, in report order
   * @return one header line, then one line per operation
   */
  public static String render(Map<ProbeOperation, LatencySamples> measured) {
    Objects.requireNonNull(measured, "measured");
    StringBuilder text = new StringBuilder();
    text.append(
        String.format(
            Locale.ROOT,
            ROW_FORMAT,
            "Operation",
            "Samples",
            "p50",
            "p99",
            "Published",
            "Published line",
            "p50 / published"));
    measured.forEach((operation, samples) -> text.append(row(operation, samples)));
    return text.toString();
  }

  /**
   * Prints a duration in the largest unit that keeps the number at 1 or more.
   *
   * @param duration a duration in any duration unit
   * @return for example "98.54 ns", "12.3 µs" or "4.1 ms"
   */
  static Quantity readable(Quantity duration) {
    Quantity nanos = duration.to(Unit.NANOSECONDS);
    if (nanos.amount().compareTo(ONE_THOUSAND) < 0) {
      return nanos;
    }
    Quantity micros = nanos.to(Unit.MICROSECONDS);
    return micros.amount().compareTo(ONE_THOUSAND) < 0 ? micros : nanos.to(Unit.MILLISECONDS);
  }

  private static String row(ProbeOperation operation, LatencySamples samples) {
    Quantity median = samples.percentile(P50);
    Optional<Quantity> published = operation.textbook();
    String comparison = published.map(value -> gap(median, value)).orElse("not compared");
    return String.format(
        Locale.ROOT,
        ROW_FORMAT,
        operation.label(),
        samples.count(),
        readable(median),
        readable(samples.percentile(P99)),
        published.map(ProbeReport::readable).map(Quantity::toString).orElse("-"),
        operation.textbookLabel(),
        comparison);
  }

  private static String gap(Quantity measured, Quantity published) {
    if (measured.amount().signum() == 0) {
      return "below the timer's resolution";
    }
    Quantity ratio = measured.timesAsMany(published);
    boolean isFar =
        ratio.amount().compareTo(GAP_FACTOR) > 0
            || published.timesAsMany(measured).amount().compareTo(GAP_FACTOR) > 0;
    return ratio + (isFar ? "   <-- gap over 10 times: explain it" : "");
  }
}
