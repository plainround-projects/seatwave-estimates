package io.plainround.seatwave.estimates.probe;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Objects;

/**
 * The measured durations of one operation, kept sorted so any percentile can be read.
 *
 * <p>Each sample may time several operations in a row, because {@code System.nanoTime} cannot see a
 * single 1-nanosecond read: its own resolution is coarser. A sample of 1,000 reads that took
 * 100,000 ns counts as 100 ns per read. Instances are immutable and safe to share between threads.
 */
public final class LatencySamples {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final long[] sortedSampleNanos;
  private final int operationsPerSample;

  /**
   * Creates the samples.
   *
   * @param sampleNanos the duration of each sample in nanoseconds; copied, so later changes to the
   *     array have no effect
   * @param operationsPerSample how many operations each sample timed
   * @throws IllegalArgumentException if there are no samples, a sample is negative, or the count of
   *     operations is below 1
   */
  public LatencySamples(long[] sampleNanos, int operationsPerSample) {
    Objects.requireNonNull(sampleNanos, "sampleNanos");
    if (sampleNanos.length == 0 || operationsPerSample < 1) {
      throw new IllegalArgumentException("need at least one sample of at least one operation");
    }
    long[] copy = sampleNanos.clone();
    Arrays.sort(copy);
    if (copy[0] < 0) {
      throw new IllegalArgumentException("a duration is never negative, but was " + copy[0]);
    }
    this.sortedSampleNanos = copy;
    this.operationsPerSample = operationsPerSample;
  }

  /**
   * Returns the number of samples.
   *
   * @return the count, in {@link Unit#SAMPLES}
   */
  public Quantity count() {
    return new Quantity(BigDecimal.valueOf(sortedSampleNanos.length), Unit.SAMPLES);
  }

  /**
   * Reads a percentile by the nearest-rank method: the smallest sample that at least that share of
   * samples does not exceed.
   *
   * @param percent the percentile, above 0 and at most 100, for example 99 for p99
   * @return the duration of one operation at that percentile, in nanoseconds
   * @throws IllegalArgumentException if the percent is outside its range
   */
  public Quantity percentile(BigDecimal percent) {
    Objects.requireNonNull(percent, "percent");
    if (percent.signum() <= 0 || percent.compareTo(HUNDRED) > 0) {
      throw new IllegalArgumentException("a percentile is above 0 and at most 100: " + percent);
    }
    // Nearest rank: rank = ceiling(percent / 100 x count), counted from 1.
    BigDecimal rank =
        percent
            .multiply(BigDecimal.valueOf(sortedSampleNanos.length))
            .divide(HUNDRED, 0, RoundingMode.CEILING);
    long sampleNanos = sortedSampleNanos[rank.intValueExact() - 1];
    BigDecimal perOperation =
        BigDecimal.valueOf(sampleNanos)
            .divide(BigDecimal.valueOf(operationsPerSample), Quantity.PRECISION);
    return new Quantity(perOperation, Unit.NANOSECONDS);
  }
}
