package io.plainround.seatwave.estimates.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UnitTest {

  @Test
  void findsAUnitByItsSymbol() {
    assertThat(Unit.withSymbol("Gbps")).contains(Unit.GIGABITS_PER_SECOND);
  }

  @Test
  void findsNoUnitForAnAmbiguousSymbol() {
    // KB is not a unit here on purpose: it can mean 1,000 or 1,024 bytes.
    assertThat(Unit.withSymbol("KB")).isEmpty();
    assertThat(Unit.withSymbol("bits")).isEmpty();
  }

  @Test
  void givesEveryUnitADistinctSymbol() {
    long distinct = Arrays.stream(Unit.values()).map(Unit::symbol).distinct().count();

    assertThat(distinct).isEqualTo(Unit.values().length);
  }

  @Test
  void keepsEightBitsInEveryByteOfAGigabit() {
    assertThat(Unit.GIGABITS_PER_SECOND.sizeInBaseUnits()).isEqualByComparingTo("125000000");
    assertThat(Unit.GIGABITS_PER_SECOND.dimension()).isEqualTo(Unit.BYTES_PER_SECOND.dimension());
  }
}
