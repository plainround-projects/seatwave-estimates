package io.plainround.seatwave.estimates.probe;

import static org.assertj.core.api.Assertions.assertThat;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProbeReportTest {

  @Test
  void printsEachDurationInItsLargestReadableUnit() {
    assertThat(ProbeReport.readable(Quantity.of("98.5", Unit.NANOSECONDS)).toString())
        .isEqualTo("98.5 ns");
    assertThat(ProbeReport.readable(Quantity.of("12300", Unit.NANOSECONDS)).toString())
        .isEqualTo("12.3 µs");
    assertThat(ProbeReport.readable(Quantity.of("4100000", Unit.NANOSECONDS)).toString())
        .isEqualTo("4.1 ms");
  }

  @Test
  void marksAGapOfMoreThanTenTimesInEitherDirection() {
    Map<ProbeOperation, LatencySamples> measured = new LinkedHashMap<>();
    measured.put(ProbeOperation.MEMORY_READ, new LatencySamples(new long[] {120L}, 1));
    measured.put(ProbeOperation.DISK_READ, new LatencySamples(new long[] {3_000L}, 1));
    measured.put(ProbeOperation.LOOPBACK_ROUND_TRIP, new LatencySamples(new long[] {40_000L}, 1));

    String report = ProbeReport.render(measured);

    assertThat(report.lines().filter(line -> line.contains("explain it"))).hasSize(2);
    assertThat(report).contains("1.2 times").contains("0.02 times").contains("0.08 times");
  }

  @Test
  void comparesNothingWhereTheTableHasNoLine() {
    Map<ProbeOperation, LatencySamples> measured = new LinkedHashMap<>();
    measured.put(ProbeOperation.DISK_WRITE_FORCED, new LatencySamples(new long[] {50_000L}, 1));

    assertThat(ProbeReport.render(measured)).contains("not compared").contains("none in the table");
  }

  @Test
  void saysWhenADurationIsBelowTheTimersResolution() {
    Map<ProbeOperation, LatencySamples> measured = new LinkedHashMap<>();
    measured.put(ProbeOperation.CACHE_READ, new LatencySamples(new long[] {0L}, 1));

    assertThat(ProbeReport.render(measured)).contains("below the timer's resolution");
  }

  @Test
  void keepsThePublishedValuesOfTheTable() {
    assertThat(ProbeOperation.MEMORY_READ.textbook())
        .contains(Quantity.of("100", Unit.NANOSECONDS));
    assertThat(ProbeOperation.TIMER_STEP.textbook()).isEmpty();
    assertThat(ProbeOperation.DISK_READ.label()).isEqualTo("random 4 KiB file read");
  }
}
