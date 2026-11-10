package io.plainround.seatwave.estimates.probe;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.Optional;

/**
 * What the probe times, and the published order of magnitude each one is compared with.
 *
 * <p>The published values are from the "Latency Numbers Every Programmer Should Know" table
 * credited to Jeff Dean and Peter Norvig, as read on 10 November 2026. They describe hardware of
 * about 2012 and are orders of magnitude, not specifications.
 */
public enum ProbeOperation {
  /** Two back-to-back calls of System.nanoTime: the finest step the timer can show. */
  TIMER_STEP("timer: two nanoTime calls", null, "none in the table"),
  /** A dependent read from an array small enough to stay in the first-level cache. */
  CACHE_READ("array read, cache-sized array", "0.5", "L1 cache reference"),
  /** A dependent read from an array far larger than every CPU cache. */
  MEMORY_READ("array read, memory-sized array", "100", "main memory reference"),
  /** A random 4 KiB read through the operating system from a file the probe wrote. */
  DISK_READ("random 4 KiB file read", "150000", "read 4K randomly from SSD"),
  /** A 4 KiB write followed by a force to the storage device. */
  DISK_WRITE_FORCED("4 KiB write + force (fsync)", null, "none in the table"),
  /** One byte to an echo server on the loopback address and one byte back. */
  LOOPBACK_ROUND_TRIP("TCP round trip on loopback", "500000", "round trip in one data center");

  private final String label;
  private final Quantity textbook;
  private final String textbookLabel;

  ProbeOperation(String label, String textbookNanos, String textbookLabel) {
    this.label = label;
    // A missing published value is kept as null inside the enum and exposed only as an Optional.
    this.textbook = textbookNanos == null ? null : Quantity.of(textbookNanos, Unit.NANOSECONDS);
    this.textbookLabel = textbookLabel;
  }

  /**
   * Returns the name printed in the report.
   *
   * @return for example "array read, memory-sized array"
   */
  public String label() {
    return label;
  }

  /**
   * Returns the published order of magnitude.
   *
   * @return the published duration, or empty when the table has no matching line
   */
  public Optional<Quantity> textbook() {
    return Optional.ofNullable(textbook);
  }

  /**
   * Returns the line of the published table this operation is compared with.
   *
   * @return for example "main memory reference"
   */
  public String textbookLabel() {
    return textbookLabel;
  }
}
