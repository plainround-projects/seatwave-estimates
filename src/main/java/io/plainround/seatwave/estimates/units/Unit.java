package io.plainround.seatwave.estimates.units;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * Every unit a number in this program may carry, with the dimension it measures and its size in the
 * base unit of that dimension.
 *
 * <p>The symbol is what a sheet must write in its unit column and what every printed number ends
 * with. Kilo, mega, giga and tera are decimal (1 GB is 10^9 bytes); the one binary unit, GiB, says
 * so in its symbol. The enum is immutable and safe to share between threads.
 */
public enum Unit {
  /** People. */
  USERS("users", Dimension.USERS, "1"),
  /** Requests one user makes in one day. */
  REQUESTS_PER_USER_PER_DAY("requests/user/day", Dimension.REQUESTS_PER_USER, "1"),
  /** The base unit of request rates. */
  REQUESTS_PER_DAY("requests/day", Dimension.REQUEST_RATE, "1"),
  /** One request per second is 86,400 requests per day: 24 h x 60 min x 60 s. */
  REQUESTS_PER_SECOND("requests/s", Dimension.REQUEST_RATE, "86400"),
  /** A plain ratio, such as a peak factor or an overhead multiplier. */
  RATIO("ratio", Dimension.DIMENSIONLESS, "1"),
  /** One percent is one hundredth. */
  PERCENT("percent", Dimension.DIMENSIONLESS, "0.01"),
  /** How many times larger one result became than another. */
  TIMES("times", Dimension.DIMENSIONLESS, "1"),
  /** Read requests for each write request. */
  READS_PER_WRITE("reads/write", Dimension.DIMENSIONLESS, "1"),
  /** The base unit of information in this program. */
  BYTES("bytes", Dimension.INFORMATION, "1"),
  /** Decimal gigabyte: 10^9 bytes. */
  GIGABYTES("GB", Dimension.INFORMATION, "1000000000"),
  /** Decimal terabyte: 10^12 bytes. */
  TERABYTES("TB", Dimension.INFORMATION, "1000000000000"),
  /** Binary gibibyte: 2^30 bytes, the unit memory is sold in. */
  GIBIBYTES("GiB", Dimension.INFORMATION, "1073741824"),
  /** The base unit of data added per day. */
  BYTES_PER_DAY("bytes/day", Dimension.INFORMATION_PER_DAY, "1"),
  /** Decimal gigabytes added per day. */
  GIGABYTES_PER_DAY("GB/day", Dimension.INFORMATION_PER_DAY, "1000000000"),
  /** The base unit of data rates. */
  BYTES_PER_SECOND("bytes/s", Dimension.INFORMATION_RATE, "1"),
  /** One gigabit per second is 10^9 bits, which is 125,000,000 bytes, every second. */
  GIGABITS_PER_SECOND("Gbps", Dimension.INFORMATION_RATE, "125000000"),
  /** The base unit of data sent per month. */
  BYTES_PER_MONTH("bytes/month", Dimension.INFORMATION_PER_MONTH, "1"),
  /** Decimal gigabytes per month, the unit egress is priced in. */
  GIGABYTES_PER_MONTH("GB/month", Dimension.INFORMATION_PER_MONTH, "1000000000"),
  /** Decimal terabytes per month. */
  TERABYTES_PER_MONTH("TB/month", Dimension.INFORMATION_PER_MONTH, "1000000000000"),
  /** Records or files written per day. */
  ITEMS_PER_DAY("items/day", Dimension.ITEMS_PER_DAY, "1"),
  /** The base unit of calendar time. */
  DAYS("days", Dimension.TIME_SPAN, "1"),
  /** A 365-day year, as in Module 2. */
  YEARS("years", Dimension.TIME_SPAN, "365"),
  /** Copies of each stored byte. */
  COPIES("copies", Dimension.COPIES, "1"),
  /** Cache entries. */
  KEYS("keys", Dimension.KEYS, "1"),
  /** Machines. */
  SERVERS("servers", Dimension.SERVERS, "1"),
  /** US dollars per hour. */
  USD_PER_HOUR("USD/hour", Dimension.MONEY_PER_HOUR, "1"),
  /** US dollars per month. */
  USD_PER_MONTH("USD/month", Dimension.MONEY_PER_MONTH, "1"),
  /** US dollars per decimal gigabyte sent. */
  USD_PER_GB("USD/GB", Dimension.PRICE_PER_GIGABYTE, "1"),
  /** US dollars per decimal gigabyte kept for one month. */
  USD_PER_GB_MONTH("USD/GB-month", Dimension.PRICE_PER_GIGABYTE_MONTH, "1"),
  /** The base unit of measured durations. */
  NANOSECONDS("ns", Dimension.DURATION, "1"),
  /** 1,000 nanoseconds. */
  MICROSECONDS("µs", Dimension.DURATION, "1000"),
  /** 1,000,000 nanoseconds. */
  MILLISECONDS("ms", Dimension.DURATION, "1000000"),
  /** Measurements. */
  SAMPLES("samples", Dimension.SAMPLES, "1");

  private final String symbol;
  private final Dimension dimension;
  private final BigDecimal sizeInBaseUnits;

  Unit(String symbol, Dimension dimension, String sizeInBaseUnits) {
    this.symbol = symbol;
    this.dimension = dimension;
    // Written as text so the size is an exact decimal, not the nearest double.
    this.sizeInBaseUnits = new BigDecimal(sizeInBaseUnits);
  }

  /**
   * Finds the unit a sheet names.
   *
   * @param symbol the text of a unit column, for example "bytes"
   * @return the unit with exactly that symbol, or empty when no unit has it
   */
  public static Optional<Unit> withSymbol(String symbol) {
    return Arrays.stream(values()).filter(unit -> unit.symbol.equals(symbol)).findFirst();
  }

  /**
   * Returns the text that follows every number in this unit.
   *
   * @return the symbol, for example "requests/s"
   */
  public String symbol() {
    return symbol;
  }

  /**
   * Returns what this unit measures.
   *
   * @return the dimension
   */
  public Dimension dimension() {
    return dimension;
  }

  /**
   * Returns how many base units of the dimension one of this unit holds.
   *
   * @return for example 1000000000 for GB, whose base unit is the byte
   */
  public BigDecimal sizeInBaseUnits() {
    return sizeInBaseUnits;
  }
}
