package io.plainround.seatwave.estimates.estimate;

import com.github.sviperll.result4j.Result;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads and writes the estimation sheet: a CSV file with the columns quantity, value, unit and
 * source, which any spreadsheet program opens.
 *
 * <p>Lines that start with {@code #} and blank lines are comments. The source column is last, so it
 * may contain commas; like any spreadsheet export, a source with a comma is written in double
 * quotes. A value is a plain decimal number; underscores may group digits, as in Java, so 5_000_000
 * is five million. Stateless and safe to use from any thread.
 */
public final class CsvSheet {

  /** The header every sheet starts with, after any comments. */
  public static final String HEADER = "quantity,value,unit,source";

  private static final String COMMENT_PREFIX = "#";
  private static final String COLUMN_SEPARATOR = ",";
  private static final int COLUMNS = 4;
  private static final Pattern PLAIN_DECIMAL = Pattern.compile("[0-9]+(\\.[0-9]+)?");
  private static final String DIGIT_GROUP_SEPARATOR = "_";
  private static final String QUOTE = "\"";

  private CsvSheet() {}

  /**
   * Reads the lines of a sheet.
   *
   * @param lines the file's lines, in order
   * @return every data line as an entry, or every problem found; one bad line rejects the sheet
   * @throws NullPointerException if the list or a line is null
   */
  public static Result<List<SheetEntry>, List<SheetProblem>> parse(List<String> lines) {
    Objects.requireNonNull(lines, "lines");
    List<SheetEntry> entries = new ArrayList<>();
    List<SheetProblem> problems = new ArrayList<>();
    boolean isHeaderSeen = false;
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      String line = lines.get(lineIndex).strip();
      if (line.isEmpty() || line.startsWith(COMMENT_PREFIX)) {
        continue;
      }
      if (!isHeaderSeen) {
        isHeaderSeen = true;
        if (!HEADER.equals(line)) {
          return Result.error(List.of(new SheetProblem.WrongHeader(line)));
        }
        continue;
      }
      readLine(lineIndex + 1, line, entries, problems);
    }
    if (!isHeaderSeen) {
      return Result.error(List.of(new SheetProblem.WrongHeader("")));
    }
    return problems.isEmpty() ? Result.success(List.copyOf(entries)) : Result.error(problems);
  }

  /**
   * Writes entries back as sheet lines, so a printed estimate can be read in again unchanged.
   *
   * @param entries the entries to write
   * @return the header followed by one line per entry
   */
  public static List<String> render(List<SheetEntry> entries) {
    List<String> lines = new ArrayList<>();
    lines.add(HEADER);
    for (SheetEntry entry : entries) {
      lines.add(
          String.join(
              COLUMN_SEPARATOR,
              entry.quantity(),
              entry.value().toPlainString(),
              entry.unit(),
              quoted(entry.source())));
    }
    return List.copyOf(lines);
  }

  // A spreadsheet writes a cell that holds a comma inside double quotes, and doubles any quote.
  private static String unquoted(String cell) {
    boolean isQuoted = cell.length() >= 2 && cell.startsWith(QUOTE) && cell.endsWith(QUOTE);
    return isQuoted ? cell.substring(1, cell.length() - 1).replace(QUOTE + QUOTE, QUOTE) : cell;
  }

  private static String quoted(String cell) {
    boolean needsQuotes = cell.contains(COLUMN_SEPARATOR) || cell.contains(QUOTE);
    return needsQuotes ? QUOTE + cell.replace(QUOTE, QUOTE + QUOTE) + QUOTE : cell;
  }

  private static void readLine(
      int lineNumber, String line, List<SheetEntry> entries, List<SheetProblem> problems) {
    // The limit keeps every comma after the third one inside the source column.
    String[] columns = line.split(COLUMN_SEPARATOR, COLUMNS);
    if (columns.length != COLUMNS) {
      problems.add(new SheetProblem.WrongColumnCount(lineNumber));
      return;
    }
    String valueText = columns[1].strip().replace(DIGIT_GROUP_SEPARATOR, "");
    if (!PLAIN_DECIMAL.matcher(valueText).matches()) {
      problems.add(new SheetProblem.NotANumber(lineNumber, columns[1].strip()));
      return;
    }
    String source = unquoted(columns[3].strip());
    if (source.isEmpty()) {
      problems.add(new SheetProblem.MissingSource(lineNumber));
      return;
    }
    entries.add(
        new SheetEntry(
            lineNumber, columns[0].strip(), new BigDecimal(valueText), columns[2].strip(), source));
  }
}
