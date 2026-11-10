package io.plainround.seatwave.estimates.estimate;

import io.plainround.seatwave.estimates.units.Quantity;
import io.plainround.seatwave.estimates.units.Unit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders an estimate as text: the inputs as a sheet, every step, the five-line estimate and the
 * sensitivity check.
 *
 * <p>Every number in the text comes from a {@code Quantity}, so every number carries its unit. The
 * inputs block is itself a valid sheet, so anyone can reproduce the estimate from the printout.
 * Stateless and safe to use from any thread.
 */
public final class EstimateReport {

  private static final String NEW_LINE = System.lineSeparator();
  private static final String INDENT = "  ";
  private static final Quantity FLAG = new Quantity(Sensitivity.FLAG_FACTOR, Unit.TIMES);

  private EstimateReport() {}

  /**
   * Renders the report.
   *
   * @param entries the sheet entries the estimate came from
   * @param estimate the estimate
   * @param sensitivity the rows of the sensitivity check
   * @return the report, ending with a line break
   */
  public static String render(
      List<SheetEntry> entries, Estimate estimate, List<SensitivityRow> sensitivity) {
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(estimate, "estimate");
    Objects.requireNonNull(sensitivity, "sensitivity");
    StringBuilder text = new StringBuilder();
    text.append(
            "INPUTS (a complete sheet: save these lines as a .csv file to reproduce this estimate)")
        .append(NEW_LINE);
    CsvSheet.render(entries).forEach(line -> text.append(line).append(NEW_LINE));
    text.append(NEW_LINE).append("STEPS").append(NEW_LINE);
    appendSteps(text, estimate);
    text.append(NEW_LINE).append("THE FIVE-LINE ESTIMATE").append(NEW_LINE);
    appendFiveLines(text, estimate);
    text.append(NEW_LINE)
        .append("SENSITIVITY (each input doubled, or halved when doubling leaves its range)")
        .append(NEW_LINE);
    appendSensitivity(text, sensitivity);
    return text.toString();
  }

  /**
   * Tells whether any row of a sensitivity check was flagged.
   *
   * @param sensitivity the rows
   * @return true when at least one result moved more than the flag factor
   */
  public static boolean hasFlag(List<SensitivityRow> sensitivity) {
    return sensitivity.stream().anyMatch(SensitivityRow::isFlagged);
  }

  private static void appendSteps(StringBuilder text, Estimate estimate) {
    String section = "";
    for (Step step : estimate.steps()) {
      if (!step.section().equals(section)) {
        section = step.section();
        text.append(section).append(NEW_LINE);
      }
      text.append(INDENT)
          .append(step.label())
          .append(": ")
          .append(step.formula())
          .append(" = ")
          .append(step.result())
          .append(NEW_LINE);
    }
  }

  private static void appendFiveLines(StringBuilder text, Estimate estimate) {
    line(
        text,
        "1. RATE      ",
        "average " + estimate.result("rate.average") + "; peak " + estimate.result("rate.peak"));
    line(text, "2. SIZE      ", estimate.result("size.total") + " of new data");
    line(
        text,
        "3. STORAGE   ",
        estimate.result("storage.total")
            + " stored at the horizon; "
            + estimate.result("storage.cost"));
    line(
        text,
        "4. BANDWIDTH ",
        "peak egress "
            + estimate.result("bandwidth.egress_peak_gbps")
            + "; "
            + estimate.result("bandwidth.egress_month")
            + "; "
            + estimate.result("bandwidth.cost"));
    line(
        text,
        "5. SERVERS   ",
        estimate.result("servers.peak")
            + " at peak, "
            + estimate.result("servers.average")
            + " at the average rate");
    line(
        text,
        "+  CACHE     ",
        estimate.result("cache.hot_memory")
            + " for the hot set, against "
            + estimate.result("cache.full_memory")
            + " for every key");
    line(
        text,
        "=  COST      ",
        estimate.result("cost.month")
            + " at the average fleet; the peak fleet costs "
            + estimate.result("servers.peak_hour_cost"));
  }

  private static void line(StringBuilder text, String heading, String content) {
    text.append(INDENT).append(heading).append(content).append(NEW_LINE);
  }

  private static void appendSensitivity(StringBuilder text, List<SensitivityRow> sensitivity) {
    for (SensitivityRow row : sensitivity) {
      text.append(INDENT)
          .append(
              String.format(
                  Locale.ROOT,
                  "%-34s %-8s %-12s %s%s",
                  row.quantity(),
                  row.change(),
                  row.movement(),
                  row.mostMovedStep(),
                  row.isFlagged() ? "   <-- FLAGGED" : ""))
          .append(NEW_LINE);
    }
    text.append(
            hasFlag(sensitivity)
                ? "FLAGGED: a result moved more than "
                    + FLAG
                    + " when one input doubled. Check that formula before using the estimate."
                : "No result moved more than " + FLAG + " when any one input doubled.")
        .append(NEW_LINE);
  }
}
