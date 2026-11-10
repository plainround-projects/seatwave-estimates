package io.plainround.seatwave.estimates.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.Test;

class QuantityTest {

  // A fixed seed makes every run generate the same amounts, so a failure can be replayed.
  private static final long SEED = 20_261_110L;
  private static final int GENERATED_AMOUNTS = 200;

  @Test
  void convertsOneGigabitPerSecondIntoBytesPerSecond() {
    Quantity oneGigabit = Quantity.of("1", Unit.GIGABITS_PER_SECOND);

    Quantity bytes = oneGigabit.to(Unit.BYTES_PER_SECOND);

    assertThat(bytes.amount()).isEqualByComparingTo("125000000");
  }

  @Test
  void convertsBytesPerSecondIntoGigabitsByMultiplyingByEight() {
    Quantity bytes = Quantity.of("1149898148", Unit.BYTES_PER_SECOND);

    Quantity gigabits = bytes.to(Unit.GIGABITS_PER_SECOND);

    assertThat(gigabits.amount()).isEqualByComparingTo("9.199185184");
  }

  @Test
  void convertsOneMillionRequestsPerDayIntoAboutTwelvePerSecond() {
    Quantity perDay = Quantity.of("1000000", Unit.REQUESTS_PER_DAY);

    Quantity perSecond = perDay.to(Unit.REQUESTS_PER_SECOND);

    assertThat(perSecond.toString()).isEqualTo("11.57 requests/s");
  }

  @Test
  void convertsGigabytesIntoGibibytes() {
    Quantity decimal = Quantity.of("1073741824", Unit.BYTES);

    assertThat(decimal.to(Unit.GIBIBYTES).amount()).isEqualByComparingTo("1");
    assertThat(decimal.to(Unit.GIGABYTES).amount()).isEqualByComparingTo("1.073741824");
  }

  @Test
  void convertsYearsIntoDaysOf365() {
    assertThat(Quantity.of("5", Unit.YEARS).to(Unit.DAYS).amount()).isEqualByComparingTo("1825");
  }

  @Test
  void convertsPercentIntoAFraction() {
    assertThat(Quantity.of("85", Unit.PERCENT).to(Unit.RATIO).amount())
        .isEqualByComparingTo("0.85");
  }

  @Test
  void refusesToConvertBytesIntoRequests() {
    Quantity bytes = Quantity.of("1", Unit.BYTES);

    assertThatThrownBy(() -> bytes.to(Unit.REQUESTS_PER_SECOND))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot convert bytes into requests/s");
  }

  @Test
  void refusesToAddBitsPerSecondToBytesPerSecond() {
    Quantity bytes = Quantity.of("125000000", Unit.BYTES_PER_SECOND);
    Quantity bits = Quantity.of("1", Unit.GIGABITS_PER_SECOND);

    assertThatThrownBy(() -> bytes.plus(bits))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot add Gbps and bytes/s");
  }

  @Test
  void refusesToAddPerDayToPerSecond() {
    Quantity perSecond = Quantity.of("12", Unit.REQUESTS_PER_SECOND);
    Quantity perDay = Quantity.of("1000000", Unit.REQUESTS_PER_DAY);

    assertThatThrownBy(() -> perSecond.plus(perDay))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot add requests/day and requests/s");
  }

  @Test
  void refusesToAddGigabytesToBytesWithoutAConversion() {
    Quantity bytes = Quantity.of("1000000000", Unit.BYTES);
    Quantity gigabytes = Quantity.of("1", Unit.GIGABYTES);

    assertThatThrownBy(() -> bytes.plus(gigabytes)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void addsQuantitiesOfTheSameUnit() {
    Quantity sum =
        Quantity.of("822", Unit.USD_PER_MONTH).plus(Quantity.of("10317", Unit.USD_PER_MONTH));

    assertThat(sum).isEqualTo(Quantity.of("11139", Unit.USD_PER_MONTH));
  }

  @Test
  void refusesToCompareDifferentUnits() {
    Quantity days = Quantity.of("90", Unit.DAYS);
    Quantity years = Quantity.of("5", Unit.YEARS);

    assertThatThrownBy(() -> days.timesAsMany(years)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesToCompareWithZero() {
    Quantity zero = Quantity.of("0", Unit.SERVERS);

    assertThatThrownBy(() -> Quantity.of("4", Unit.SERVERS).timesAsMany(zero))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void comparesInTimes() {
    Quantity ratio = Quantity.of("66", Unit.SERVERS).timesAsMany(Quantity.of("33", Unit.SERVERS));

    assertThat(ratio).isEqualTo(new Quantity(new BigDecimal("2"), Unit.TIMES));
  }

  @Test
  void scalesAndKeepsTheUnit() {
    Quantity scaled = Quantity.of("1157", Unit.REQUESTS_PER_SECOND).scaledBy(new BigDecimal("26"));

    assertThat(scaled).isEqualTo(Quantity.of("30082", Unit.REQUESTS_PER_SECOND));
  }

  @Test
  void rejectsANegativeAmount() {
    assertThatThrownBy(() -> Quantity.of("-1", Unit.BYTES))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAMissingAmountOrUnit() {
    assertThatThrownBy(() -> new Quantity(null, Unit.BYTES))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Quantity(BigDecimal.ONE, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void printsLargeNumbersWithoutDecimalsAndWithGrouping() {
    assertThat(Quantity.of("30092.59", Unit.REQUESTS_PER_SECOND).toString())
        .isEqualTo("30,093 requests/s");
  }

  @Test
  void printsMiddleNumbersWithAtMostTwoDecimals() {
    assertThat(Quantity.of("9.1991", Unit.GIGABITS_PER_SECOND).toString()).isEqualTo("9.2 Gbps");
  }

  @Test
  void printsSmallNumbersWithFourSignificantDigits() {
    assertThat(Quantity.of("0.2016", Unit.USD_PER_HOUR).toString()).isEqualTo("0.2016 USD/hour");
    assertThat(Quantity.of("0.000123456", Unit.TERABYTES).toString()).isEqualTo("0.0001235 TB");
  }

  @Test
  void printsMoneyOfADollarOrMoreWithCents() {
    assertThat(Quantity.of("147.168", Unit.USD_PER_MONTH).toString()).isEqualTo("147.17 USD/month");
    assertThat(Quantity.of("822", Unit.USD_PER_MONTH).toString()).isEqualTo("822.00 USD/month");
    assertThat(Quantity.of("13.3056", Unit.USD_PER_HOUR).toString()).isEqualTo("13.31 USD/hour");
  }

  @Test
  void printsZeroWithItsUnit() {
    assertThat(Quantity.of("0", Unit.SERVERS).toString()).isEqualTo("0 servers");
  }

  @Test
  void everyPrintedQuantityEndsWithItsUnitSymbol() {
    List<Double> amounts =
        Instancio.ofList(Double.class)
            .size(GENERATED_AMOUNTS)
            .generate(Select.all(Double.class), generator -> generator.doubles().range(0.0, 1e15))
            .withSeed(SEED)
            .create();

    for (Unit unit : Unit.values()) {
      for (Double amount : amounts) {
        String printed = new Quantity(BigDecimal.valueOf(amount), unit).toString();

        assertThat(printed).endsWith(" " + unit.symbol());
      }
    }
  }
}
