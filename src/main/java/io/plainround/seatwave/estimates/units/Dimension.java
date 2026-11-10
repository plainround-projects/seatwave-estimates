package io.plainround.seatwave.estimates.units;

/**
 * What a unit measures. Two units can be converted into each other only when they share a
 * dimension, so a byte count can become terabytes but never requests per second.
 */
public enum Dimension {
  /** People who use the system. */
  USERS,
  /** Requests one user makes in one day. */
  REQUESTS_PER_USER,
  /** Requests per unit of time. */
  REQUEST_RATE,
  /** Plain numbers: ratios, percents and change factors. */
  DIMENSIONLESS,
  /** An amount of stored or sent data. */
  INFORMATION,
  /** Data added per day. */
  INFORMATION_PER_DAY,
  /** Data sent per second. */
  INFORMATION_RATE,
  /** Data sent per month. */
  INFORMATION_PER_MONTH,
  /** Records or files written per day. */
  ITEMS_PER_DAY,
  /** A span of calendar time. */
  TIME_SPAN,
  /** How many copies of each byte are kept. */
  COPIES,
  /** Entries in a cache. */
  KEYS,
  /** Machines. */
  SERVERS,
  /** Money per hour of running time. */
  MONEY_PER_HOUR,
  /** Money per month. */
  MONEY_PER_MONTH,
  /** Money per gigabyte sent. */
  PRICE_PER_GIGABYTE,
  /** Money per gigabyte stored for a month. */
  PRICE_PER_GIGABYTE_MONTH,
  /** A measured duration of one operation. */
  DURATION,
  /** Measurements taken. */
  SAMPLES
}
