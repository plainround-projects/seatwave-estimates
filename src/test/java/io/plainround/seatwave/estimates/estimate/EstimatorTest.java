package io.plainround.seatwave.estimates.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstimatorTest {

  private final Estimate production = Estimator.estimate(Sheets.inputsOf(Sheets.PRODUCTION));
  private final Estimate simple = Estimator.estimate(Sheets.inputsOf(Sheets.SIMPLE));

  @Test
  void turnsFiveMillionUsersIntoAboutTwelveHundredRequestsPerSecondOnAverage() {
    assertThat(production.result("rate.per_day").toString()).isEqualTo("100,000,000 requests/day");
    assertThat(production.result("rate.average").toString()).isEqualTo("1,157 requests/s");
  }

  @Test
  void multipliesTheAverageByThePeakFactor() {
    assertThat(production.result("rate.peak").toString()).isEqualTo("30,093 requests/s");
  }

  @Test
  void splitsThePeakIntoReadsAndWrites() {
    assertThat(production.result("rate.reads").toString()).isEqualTo("25,579 requests/s");
    assertThat(production.result("rate.writes").toString()).isEqualTo("4,514 requests/s");
    assertThat(production.result("rate.read_write").toString()).isEqualTo("5.67 reads/write");
  }

  @Test
  void leavesOutTheReadWriteRatioWhenNothingIsWritten() {
    List<SheetEntry> entries = Sheets.productionWith("write_share", "0", "percent");

    Estimate allReads = Estimator.estimate(Sheets.inputs(entries));

    assertThatThrownBy(() -> allReads.result("rate.read_write"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(allReads.result("rate.writes").amount()).isEqualByComparingTo("0");
  }

  @Test
  void addsUpNewDataPerDayAcrossStores() {
    assertThat(production.result("size.bookings").toString()).isEqualTo("1 GB/day");
    assertThat(production.result("size.total").toString()).isEqualTo("101 GB/day");
  }

  @Test
  void multipliesStorageByCopiesAndIndexOverhead() {
    assertThat(production.result("storage.bookings.raw").toString()).isEqualTo("1.83 TB");
    assertThat(production.result("storage.bookings.stored").toString()).isEqualTo("8.21 TB");
  }

  @Test
  void keepsLogsOnlyForTheirRetention() {
    assertThat(production.result("storage.logs.days").toString()).isEqualTo("90 days");
    assertThat(production.result("storage.logs.year").toString()).isEqualTo("36.5 TB");
    assertThat(production.result("storage.logs.stored").toString()).isEqualTo("27 TB");
  }

  @Test
  void capsRetentionAtTheHorizon() {
    assertThat(production.result("storage.bookings.days").toString()).isEqualTo("1,825 days");
  }

  @Test
  void addsUpStorageAndPricesIt() {
    assertThat(production.result("storage.total").toString()).isEqualTo("35.72 TB");
    assertThat(production.result("storage.cost").toString()).isEqualTo("821.60 USD/month");
  }

  @Test
  void convertsPeakEgressFromBytesIntoBits() {
    assertThat(production.result("bandwidth.egress_peak").unit()).isEqualTo(Unit.BYTES_PER_SECOND);
    assertThat(production.result("bandwidth.egress_peak_gbps").toString()).isEqualTo("9.2 Gbps");
  }

  @Test
  void estimatesMonthlyEgressAndItsCost() {
    assertThat(production.result("bandwidth.egress_month").toString()).isEqualTo("115 TB/month");
    assertThat(production.result("bandwidth.cost").toString()).isEqualTo("10,317.24 USD/month");
  }

  @Test
  void sizesTheFleetAtTheUtilizationTargetWithHeadroomAndASpare() {
    assertThat(production.result("servers.safe_rate").toString()).isEqualTo("560 requests/s");
    assertThat(production.result("servers.peak").toString()).isEqualTo("66 servers");
    assertThat(production.result("servers.average").toString()).isEqualTo("4 servers");
  }

  @Test
  void pricesTheFleetWithTheModuleOneMonth() {
    assertThat(production.result("servers.peak_hour_cost").toString()).isEqualTo("13.31 USD/hour");
    assertThat(production.result("servers.average_month_cost").amount())
        .isEqualByComparingTo("588.672");
  }

  @Test
  void sizesTheCacheFromTheHotSetNotFromEveryKey() {
    assertThat(production.result("cache.hot_keys").toString()).isEqualTo("20,000 keys");
    assertThat(production.result("cache.hot_memory").toString()).isEqualTo("0.925 GB");
    assertThat(production.result("cache.hot_memory_gib").toString()).isEqualTo("0.8615 GiB");
    assertThat(production.result("cache.full_memory").toString()).isEqualTo("18.5 GB");
  }

  @Test
  void addsTheMonthlyCostOfEachLine() {
    assertThat(production.result("cost.month").toString()).isEqualTo("11,727.51 USD/month");
  }

  @Test
  void needsOneServerForTheSimpleSeatWave() {
    assertThat(simple.result("rate.average").toString()).isEqualTo("4.63 requests/s");
    assertThat(simple.result("rate.peak").toString()).isEqualTo("13.89 requests/s");
    assertThat(simple.result("servers.peak").toString()).isEqualTo("1 servers");
  }

  @Test
  void givesTheSameEstimateForTheSameInputsEveryTime() {
    Estimate again = Estimator.estimate(Sheets.inputsOf(Sheets.PRODUCTION));

    assertThat(again).isEqualTo(production);
  }

  @Test
  void namesEveryStepOnceAndGivesEveryResultAUnit() {
    assertThat(production.steps()).allSatisfy(step -> assertThat(step.result().unit()).isNotNull());
    assertThat(production.steps()).extracting(Step::id).doesNotHaveDuplicates();
  }

  @Test
  void neverStoresLessThanOneCopy() {
    for (Store store : Sheets.inputsOf(Sheets.PRODUCTION).stores()) {
      BigDecimal raw = production.result("storage." + store.name() + ".raw").amount();
      BigDecimal stored = production.result("storage." + store.name() + ".stored").amount();

      assertThat(stored).isGreaterThanOrEqualTo(raw);
    }
  }
}
