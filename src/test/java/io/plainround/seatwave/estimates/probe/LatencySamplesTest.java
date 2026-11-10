package io.plainround.seatwave.estimates.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LatencySamplesTest {

  private static final BigDecimal P50 = new BigDecimal("50");
  private static final BigDecimal P99 = new BigDecimal("99");

  @Test
  void readsPercentilesByNearestRank() {
    long[] hundredSamples = new long[100];
    for (int sampleIndex = 0; sampleIndex < hundredSamples.length; sampleIndex++) {
      hundredSamples[sampleIndex] = sampleIndex + 1;
    }

    LatencySamples samples = new LatencySamples(hundredSamples, 1);

    assertThat(samples.percentile(P50).amount()).isEqualByComparingTo("50");
    assertThat(samples.percentile(P99).amount()).isEqualByComparingTo("99");
    assertThat(samples.percentile(new BigDecimal("100")).amount()).isEqualByComparingTo("100");
  }

  @Test
  void letsOneSlowSampleInAHundredShowOnlyInTheTail() {
    long[] mostlyFast = new long[100];
    Arrays.fill(mostlyFast, 100L);
    mostlyFast[0] = 1_000_000L;

    LatencySamples samples = new LatencySamples(mostlyFast, 1);

    assertThat(samples.percentile(P99).amount()).isEqualByComparingTo("100");
    assertThat(samples.percentile(new BigDecimal("100")).amount()).isEqualByComparingTo("1000000");
  }

  @Test
  void dividesEachSampleByTheOperationsItTimed() {
    LatencySamples samples = new LatencySamples(new long[] {100_000L}, 1_000);

    assertThat(samples.percentile(P50).toString()).isEqualTo("100 ns");
  }

  @Test
  void copiesTheArraySoLaterChangesHaveNoEffect() {
    long[] raw = {10L, 20L, 30L};
    LatencySamples samples = new LatencySamples(raw, 1);

    raw[0] = 1_000L;

    assertThat(samples.percentile(new BigDecimal("1")).amount()).isEqualByComparingTo("10");
    assertThat(samples.count().toString()).isEqualTo("3 samples");
  }

  @Test
  void rejectsNoSamplesANegativeSampleOrZeroOperations() {
    assertThatThrownBy(() -> new LatencySamples(new long[0], 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LatencySamples(new long[] {-1L}, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LatencySamples(new long[] {1L}, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAPercentileOutsideItsRange() {
    LatencySamples samples = new LatencySamples(new long[] {1L}, 1);

    assertThatThrownBy(() -> samples.percentile(BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> samples.percentile(new BigDecimal("100.1")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
