package io.plainround.seatwave.estimates.probe;

import java.time.Duration;

/** Small settings, so the probes run in milliseconds inside a unit test. */
final class ProbeSettingsFixtures {

  static final int SAMPLES = 50;
  static final int WARMUP = 5;
  static final int READS_PER_SAMPLE = 10;
  static final int MEMORY_BYTES = 1 << 16;
  static final int CACHE_BYTES = 1 << 10;
  static final int FILE_BYTES = 1 << 16;
  static final int BLOCK_BYTES = 1 << 12;
  static final int WRITES = 5;
  static final Duration TIMEOUT = Duration.ofSeconds(5);
  static final long SEED = 20_261_110L;

  private ProbeSettingsFixtures() {}

  static ProbeSettings small() {
    return new ProbeSettings(
        SAMPLES,
        WARMUP,
        READS_PER_SAMPLE,
        MEMORY_BYTES,
        CACHE_BYTES,
        FILE_BYTES,
        BLOCK_BYTES,
        WRITES,
        TIMEOUT,
        SEED);
  }
}
