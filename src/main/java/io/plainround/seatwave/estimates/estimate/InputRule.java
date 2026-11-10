package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One rule per input a sheet may give: its name, the one unit it must be written in, and its range.
 *
 * <p>A sheet must write each input in exactly its unit. "500 KB" where bytes are expected is
 * rejected, not converted, because KB is ambiguous (1,000 or 1,024) and the slip is what this
 * module teaches to catch.
 */
public enum InputRule {
  /** People who use the system on a typical day. */
  DAILY_ACTIVE_USERS("daily_active_users", Unit.USERS, Range.POSITIVE),
  /** Requests one daily user makes in a day. */
  REQUESTS_PER_USER_PER_DAY(
      "requests_per_user_per_day", Unit.REQUESTS_PER_USER_PER_DAY, Range.POSITIVE),
  /** Peak rate divided by the average rate of the whole day. */
  PEAK_FACTOR("peak_factor", Unit.RATIO, Range.AT_LEAST_ONE),
  /**
   * The share of requests that change data. It is an input instead of the read share because
   * doubling a small write share is a fair test, while doubling a read share of 85 percent is
   * impossible.
   */
  WRITE_SHARE("write_share", Unit.PERCENT, Range.SHARE),
  /** Average size of one request as it arrives, headers included. */
  REQUEST_BYTES("request_bytes", Unit.BYTES, Range.POSITIVE),
  /** Average size of one response as it leaves, headers included. */
  RESPONSE_BYTES("response_bytes", Unit.BYTES, Range.POSITIVE),
  /** How far ahead the storage estimate looks. */
  HORIZON_YEARS("horizon_years", Unit.YEARS, Range.POSITIVE),
  /** Every key the cache could ever hold. */
  CACHE_KEYS("cache_keys", Unit.KEYS, Range.NON_NEGATIVE),
  /** The share of keys that receives most reads. */
  CACHE_HOT_SHARE("cache_hot_share", Unit.PERCENT, Range.SHARE),
  /** Size of one cached value. */
  CACHE_ENTRY_BYTES("cache_entry_bytes", Unit.BYTES, Range.POSITIVE),
  /** Memory used per byte of value: keys, object headers and the table itself. */
  CACHE_OVERHEAD("cache_overhead", Unit.RATIO, Range.AT_LEAST_ONE),
  /** The measured rate at which one server's p99 starts to jump. */
  KNEE_PER_SERVER("knee_per_server", Unit.REQUESTS_PER_SECOND, Range.POSITIVE),
  /** The share of the knee a server is planned to run at. */
  UTILIZATION_TARGET("utilization_target", Unit.PERCENT, Range.POSITIVE_SHARE),
  /** Extra capacity above the estimated peak, for spikes the estimate missed. */
  HEADROOM("headroom", Unit.PERCENT, Range.NON_NEGATIVE),
  /** Servers added on top, so one can fail or be deployed without losing capacity. */
  SPARE_SERVERS("spare_servers", Unit.SERVERS, Range.WHOLE_NUMBER),
  /** On-demand price of one server. */
  SERVER_PRICE("server_price", Unit.USD_PER_HOUR, Range.NON_NEGATIVE),
  /** Price of one decimal gigabyte sent to the internet. */
  EGRESS_PRICE("egress_price", Unit.USD_PER_GB, Range.NON_NEGATIVE),
  /** Price of keeping one decimal gigabyte for a month. */
  STORAGE_PRICE("storage_price", Unit.USD_PER_GB_MONTH, Range.NON_NEGATIVE);

  private final String key;
  private final Unit unit;
  private final Range range;

  InputRule(String key, Unit unit, Range range) {
    this.key = key;
    this.unit = unit;
    this.range = range;
  }

  /**
   * Finds the rule for a quantity name written in a sheet.
   *
   * @param key the name, for example "peak_factor"
   * @return the rule, or empty when the name is not an input
   */
  public static Optional<InputRule> withKey(String key) {
    return Arrays.stream(values()).filter(rule -> rule.key.equals(key)).findFirst();
  }

  /**
   * Returns the name a sheet uses.
   *
   * @return for example "daily_active_users"
   */
  public String key() {
    return key;
  }

  /**
   * Returns the one unit the input must be written in.
   *
   * @return the unit
   */
  public Unit unit() {
    return unit;
  }

  Range range() {
    return range;
  }

  /**
   * Checks a quantity against this rule.
   *
   * @param quantity the value to check
   * @return the same quantity
   * @throws IllegalArgumentException if the unit is wrong or the value is out of range
   */
  Quantity requireValid(Quantity quantity) {
    Objects.requireNonNull(quantity, key);
    if (quantity.unit() != unit || !range.contains(quantity.amount())) {
      throw new IllegalArgumentException(
          key + " must be " + range.description() + " " + unit.symbol() + ", but was " + quantity);
    }
    return quantity;
  }
}
