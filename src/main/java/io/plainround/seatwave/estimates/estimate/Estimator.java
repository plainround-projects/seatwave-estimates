package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns inputs into the five-line estimate (rate, size, storage, bandwidth, servers) plus the cache
 * memory, keeping every step of the arithmetic.
 *
 * <p>Every result is computed from the inputs alone, with no clock and no randomness, so the same
 * inputs always give the same estimate. One instance computes one estimate and is confined to the
 * call that created it; use {@link #estimate(Inputs)} from any thread.
 */
public final class Estimator {

  /** A traffic month is 30 days, as in Module 2. */
  static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");

  /** A machine is billed for 730 hours a month, as in Module 1 ($0.2016 x 730 = $147.17). */
  static final BigDecimal BILLING_HOURS_PER_MONTH = new BigDecimal("730");

  private static final String RATE = "RATE";
  private static final String SIZE = "SIZE";
  private static final String STORAGE = "STORAGE";
  private static final String BANDWIDTH = "BANDWIDTH";
  private static final String SERVERS = "SERVERS";
  private static final String CACHE = "CACHE";
  private static final String COST = "COST";
  private static final String TIMES = " x ";

  private final Inputs inputs;
  private final List<Step> steps = new ArrayList<>();

  private Estimator(Inputs inputs) {
    this.inputs = inputs;
  }

  /**
   * Computes the estimate.
   *
   * @param inputs checked inputs
   * @return every step, in the order rate, size, storage, bandwidth, servers, cache, cost
   * @throws NullPointerException if the inputs are null
   */
  public static Estimate estimate(Inputs inputs) {
    Estimator estimator = new Estimator(Objects.requireNonNull(inputs, "inputs"));
    estimator.estimateRate();
    estimator.estimateSize();
    estimator.estimateStorage();
    estimator.estimateBandwidth();
    estimator.estimateServers();
    estimator.estimateCache();
    estimator.estimateCost();
    return new Estimate(estimator.steps);
  }

  private void estimateRate() {
    Quantity users = input(InputRule.DAILY_ACTIVE_USERS);
    Quantity perUser = input(InputRule.REQUESTS_PER_USER_PER_DAY);
    Quantity perDay = product(Unit.REQUESTS_PER_DAY, users, perUser);
    add("rate.per_day", RATE, "Requests per day", users + TIMES + perUser, perDay);
    // The unit conversion divides by 86,400, the seconds in 24 x 60 x 60.
    Quantity average = perDay.to(Unit.REQUESTS_PER_SECOND);
    add("rate.average", RATE, "Average rate", perDay + " / 86,400 s/day", average);
    Quantity peakFactor = input(InputRule.PEAK_FACTOR);
    Quantity peak = average.scaledBy(peakFactor.amount());
    add("rate.peak", RATE, "Peak rate", average + TIMES + peakFactor, peak);
    Quantity writeShare = input(InputRule.WRITE_SHARE);
    BigDecimal writeFraction = writeShare.to(Unit.RATIO).amount();
    BigDecimal readFraction = BigDecimal.ONE.subtract(writeFraction);
    Quantity readShare = new Quantity(readFraction, Unit.RATIO).to(Unit.PERCENT);
    add("rate.reads", RATE, "Reads at peak", peak + TIMES + readShare, peak.scaledBy(readFraction));
    add(
        "rate.writes",
        RATE,
        "Writes at peak",
        peak + TIMES + writeShare,
        peak.scaledBy(writeFraction));
    if (writeFraction.signum() > 0) {
      Quantity readsPerWrite =
          new Quantity(
              readFraction.divide(writeFraction, Quantity.PRECISION), Unit.READS_PER_WRITE);
      add(
          "rate.read_write",
          RATE,
          "Read-write ratio",
          readShare + " / " + writeShare,
          readsPerWrite);
    }
  }

  private void estimateSize() {
    Quantity total = new Quantity(BigDecimal.ZERO, Unit.BYTES_PER_DAY);
    for (Store store : inputs.stores()) {
      Quantity perDay = newBytesPerDay(store);
      add(
          "size." + store.name(),
          SIZE,
          "New " + store.name() + " data per day",
          store.get(StoreField.ITEMS_PER_DAY) + TIMES + store.get(StoreField.BYTES_PER_ITEM),
          perDay.to(Unit.GIGABYTES_PER_DAY));
      total = total.plus(perDay);
    }
    add(
        "size.total",
        SIZE,
        "New data per day, all stores",
        "sum of the lines above",
        total.to(Unit.GIGABYTES_PER_DAY));
  }

  private void estimateStorage() {
    Quantity horizonDays = input(InputRule.HORIZON_YEARS).to(Unit.DAYS);
    Quantity total = new Quantity(BigDecimal.ZERO, Unit.BYTES);
    for (Store store : inputs.stores()) {
      total = total.plus(storedBytes(store, horizonDays));
    }
    add(
        "storage.total",
        STORAGE,
        "Stored at the horizon, all stores",
        "sum of the stored lines above",
        total.to(Unit.TERABYTES));
    Quantity price = input(InputRule.STORAGE_PRICE);
    Quantity gigabytes = total.to(Unit.GIGABYTES);
    add(
        "storage.cost",
        STORAGE,
        "Storage cost per month at the horizon",
        gigabytes + TIMES + price,
        product(Unit.USD_PER_MONTH, gigabytes, price));
  }

  private Quantity storedBytes(Store store, Quantity horizonDays) {
    String name = store.name();
    Quantity perDay = newBytesPerDay(store);
    Quantity perYear =
        new Quantity(perDay.amount().multiply(Unit.YEARS.sizeInBaseUnits()), Unit.BYTES);
    add(
        "storage." + name + ".year",
        STORAGE,
        name + ": one year of data",
        perDay + " x 365 days",
        perYear.to(Unit.TERABYTES));
    Quantity retention = store.get(StoreField.RETENTION_DAYS);
    Quantity keptDays =
        retention.amount().compareTo(horizonDays.amount()) < 0 ? retention : horizonDays;
    add(
        "storage." + name + ".days",
        STORAGE,
        name + ": days kept",
        "the smaller of " + retention + " and " + horizonDays,
        keptDays);
    Quantity raw = product(Unit.BYTES, perDay, keptDays);
    add(
        "storage." + name + ".raw",
        STORAGE,
        name + ": one copy",
        perDay + TIMES + keptDays,
        raw.to(Unit.TERABYTES));
    Quantity copies = store.get(StoreField.REPLICATION_FACTOR);
    Quantity overhead = store.get(StoreField.INDEX_OVERHEAD);
    Quantity stored = product(Unit.BYTES, raw, copies, overhead);
    add(
        "storage." + name + ".stored",
        STORAGE,
        name + ": stored with copies and indexes",
        raw.to(Unit.TERABYTES) + TIMES + copies + TIMES + overhead,
        stored.to(Unit.TERABYTES));
    return stored;
  }

  private void estimateBandwidth() {
    Quantity peak = result("rate.peak");
    Quantity responseBytes = input(InputRule.RESPONSE_BYTES);
    Quantity egress = product(Unit.BYTES_PER_SECOND, peak, responseBytes);
    add("bandwidth.egress_peak", BANDWIDTH, "Egress at peak", peak + TIMES + responseBytes, egress);
    // Bytes to bits: the unit conversion multiplies by 8 and divides by 10^9.
    add(
        "bandwidth.egress_peak_gbps",
        BANDWIDTH,
        "Egress at peak, in bits",
        egress + " x 8 bits/byte",
        egress.to(Unit.GIGABITS_PER_SECOND));
    Quantity requestBytes = input(InputRule.REQUEST_BYTES);
    Quantity ingress = product(Unit.BYTES_PER_SECOND, peak, requestBytes);
    add(
        "bandwidth.ingress_peak_gbps",
        BANDWIDTH,
        "Ingress at peak, in bits",
        peak + TIMES + requestBytes + " x 8 bits/byte",
        ingress.to(Unit.GIGABITS_PER_SECOND));
    Quantity perDay = result("rate.per_day");
    Quantity month = new Quantity(DAYS_PER_MONTH, Unit.DAYS);
    Quantity monthly = product(Unit.BYTES_PER_MONTH, perDay, responseBytes, month);
    add(
        "bandwidth.egress_month",
        BANDWIDTH,
        "Egress per 30-day month",
        perDay + TIMES + responseBytes + TIMES + month,
        monthly.to(Unit.TERABYTES_PER_MONTH));
    Quantity gigabytes = monthly.to(Unit.GIGABYTES_PER_MONTH);
    Quantity price = input(InputRule.EGRESS_PRICE);
    add(
        "bandwidth.cost",
        BANDWIDTH,
        "Egress cost per month",
        gigabytes + TIMES + price,
        product(Unit.USD_PER_MONTH, gigabytes, price));
  }

  private void estimateServers() {
    Quantity knee = input(InputRule.KNEE_PER_SERVER);
    Quantity utilization = input(InputRule.UTILIZATION_TARGET);
    Quantity safe = knee.scaledBy(utilization.to(Unit.RATIO).amount());
    add("servers.safe_rate", SERVERS, "Safe rate per server", knee + TIMES + utilization, safe);
    Quantity headroom = input(InputRule.HEADROOM);
    BigDecimal withHeadroom = BigDecimal.ONE.add(headroom.to(Unit.RATIO).amount());
    Quantity peakServers = fleetFor("peak", result("rate.peak"), safe, withHeadroom);
    Quantity averageServers = fleetFor("average", result("rate.average"), safe, withHeadroom);
    Quantity price = input(InputRule.SERVER_PRICE);
    Quantity peakHour = product(Unit.USD_PER_HOUR, peakServers, price);
    add(
        "servers.peak_hour_cost",
        SERVERS,
        "Peak fleet cost per hour",
        peakServers + TIMES + price,
        peakHour);
    add(
        "servers.peak_month_cost",
        SERVERS,
        "Peak fleet cost per month, if kept all month",
        peakHour + " x 730 hours",
        monthOf(peakHour));
    Quantity averageMonth = monthOf(product(Unit.USD_PER_HOUR, averageServers, price));
    add(
        "servers.average_month_cost",
        SERVERS,
        "Average fleet cost per month",
        averageServers + TIMES + price + " x 730 hours",
        averageMonth);
  }

  private Quantity fleetFor(String load, Quantity rate, Quantity safe, BigDecimal withHeadroom) {
    Quantity planned = rate.scaledBy(withHeadroom);
    add(
        "servers." + load + "_planned",
        SERVERS,
        "Planned " + load + " rate with headroom",
        rate + " x " + withHeadroom.toPlainString() + " (1 + headroom)",
        planned);
    // Round up: 53.7 servers means 54, because 53 would run above the utilization target.
    BigDecimal needed =
        planned
            .amount()
            .divide(safe.amount(), Quantity.PRECISION)
            .setScale(0, RoundingMode.CEILING);
    Quantity spare = input(InputRule.SPARE_SERVERS);
    Quantity servers = new Quantity(needed, Unit.SERVERS).plus(spare);
    add(
        "servers." + load,
        SERVERS,
        "Servers for the " + load + " rate",
        "ceiling of " + planned + " / " + safe + ", + " + spare + " (spare)",
        servers);
    return servers;
  }

  private void estimateCache() {
    Quantity keys = input(InputRule.CACHE_KEYS);
    Quantity hotShare = input(InputRule.CACHE_HOT_SHARE);
    Quantity hotKeys = keys.scaledBy(hotShare.to(Unit.RATIO).amount());
    add("cache.hot_keys", CACHE, "Hot keys", keys + TIMES + hotShare, hotKeys);
    Quantity entry = input(InputRule.CACHE_ENTRY_BYTES);
    Quantity overhead = input(InputRule.CACHE_OVERHEAD);
    Quantity hot = product(Unit.BYTES, hotKeys, entry, overhead);
    add(
        "cache.hot_memory",
        CACHE,
        "Memory for the hot set",
        hotKeys + TIMES + entry + TIMES + overhead,
        hot.to(Unit.GIGABYTES));
    add(
        "cache.hot_memory_gib",
        CACHE,
        "Memory for the hot set, in binary units",
        hot.to(Unit.GIGABYTES) + " / 1.073741824",
        hot.to(Unit.GIBIBYTES));
    Quantity full = product(Unit.BYTES, keys, entry, overhead);
    add(
        "cache.full_memory",
        CACHE,
        "Memory to cache every key, for comparison",
        keys + TIMES + entry + TIMES + overhead,
        full.to(Unit.GIGABYTES));
  }

  private void estimateCost() {
    Quantity storage = result("storage.cost");
    Quantity egress = result("bandwidth.cost");
    Quantity servers = result("servers.average_month_cost");
    add(
        "cost.month",
        COST,
        "Monthly cost: storage, egress and the average fleet",
        storage + " + " + egress + " + " + servers,
        storage.plus(egress).plus(servers));
  }

  private static Quantity monthOf(Quantity perHour) {
    return new Quantity(
        perHour.amount().multiply(BILLING_HOURS_PER_MONTH, Quantity.PRECISION), Unit.USD_PER_MONTH);
  }

  private Quantity newBytesPerDay(Store store) {
    return product(
        Unit.BYTES_PER_DAY,
        store.get(StoreField.ITEMS_PER_DAY),
        store.get(StoreField.BYTES_PER_ITEM));
  }

  private Quantity input(InputRule rule) {
    return inputs.get(rule);
  }

  private Quantity result(String id) {
    return new Estimate(steps).result(id);
  }

  private void add(String id, String section, String label, String formula, Quantity result) {
    steps.add(new Step(id, section, label, formula, result));
  }

  // A product of quantities has a new unit, which the caller names: users x requests/user/day is
  // requests/day. Naming it at every call keeps each formula's unit visible where it is written.
  private static Quantity product(Unit resultUnit, Quantity... factors) {
    BigDecimal amount = BigDecimal.ONE;
    for (Quantity factor : factors) {
      amount = amount.multiply(factor.amount(), Quantity.PRECISION);
    }
    return new Quantity(amount, resultUnit);
  }
}
