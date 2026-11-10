package io.plainround.seatwave.estimates.probe;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs each probe with small settings. Durations depend on the machine, so these tests check only
 * what is true everywhere: the count of samples, and that every duration is a real one.
 */
class ProbesTest {

  private static final BigDecimal P99 = new BigDecimal("99");
  private final ProbeSettings settings = ProbeSettingsFixtures.small();

  @Test
  void measuresTheTimerStep() {
    LatencySamples samples = MemoryProbe.timerStep(settings);

    assertThat(samples.count().amount()).isEqualByComparingTo("50");
  }

  @Test
  void measuresArrayReadsFromBothArraySizes() {
    LatencySamples cache = MemoryProbe.arrayReads(settings, settings.cacheArrayBytes());
    LatencySamples memory = MemoryProbe.arrayReads(settings, settings.memoryArrayBytes());

    assertThat(cache.count().amount()).isEqualByComparingTo("50");
    assertThat(memory.percentile(P99).amount().signum()).isPositive();
  }

  @Test
  void measuresDiskReadsAndWritesAndDeletesItsFile(@TempDir Path folder) throws IOException {
    DiskProbe.DiskResults results = DiskProbe.measure(settings, folder);

    assertThat(results.reads().count().amount()).isEqualByComparingTo("50");
    assertThat(results.forcedWrites().count().amount()).isEqualByComparingTo("5");
    try (Stream<Path> left = Files.list(folder)) {
      assertThat(left).isEmpty();
    }
  }

  @Test
  void measuresRoundTripsOnLoopbackOnly() throws IOException {
    LatencySamples samples = LoopbackProbe.roundTrips(settings);

    assertThat(samples.count().amount()).isEqualByComparingTo("50");
    assertThat(samples.percentile(P99).amount().signum()).isPositive();
  }
}
