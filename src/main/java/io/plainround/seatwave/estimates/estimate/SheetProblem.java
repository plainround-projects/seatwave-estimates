package io.plainround.seatwave.estimates.estimate;

/**
 * Why a sheet cannot be used. These are expected failures of input typed by a person, so they are
 * returned as values. The interface is sealed, so a switch over it must handle every reason.
 */
public sealed interface SheetProblem {

  /**
   * The first line that is not a comment is not the expected header.
   *
   * @param found the line that was found
   */
  record WrongHeader(String found) implements SheetProblem {}

  /**
   * A line does not have four columns.
   *
   * @param lineNumber the line in the file
   */
  record WrongColumnCount(int lineNumber) implements SheetProblem {}

  /**
   * A value is not a plain decimal number.
   *
   * @param lineNumber the line in the file
   * @param text the value as it was written
   */
  record NotANumber(int lineNumber, String text) implements SheetProblem {}

  /**
   * A line has no source, so nobody can check where its number comes from.
   *
   * @param lineNumber the line in the file
   */
  record MissingSource(int lineNumber) implements SheetProblem {}

  /**
   * The quantity name is not an input of the estimator.
   *
   * @param lineNumber the line in the file
   * @param quantity the name as it was written
   */
  record UnknownQuantity(int lineNumber, String quantity) implements SheetProblem {}

  /**
   * The unit is not the one the input must be written in.
   *
   * @param lineNumber the line in the file
   * @param quantity the input
   * @param expected the unit the input requires
   * @param found the unit the sheet wrote
   */
  record WrongUnit(int lineNumber, String quantity, String expected, String found)
      implements SheetProblem {}

  /**
   * The value is outside the range of the input.
   *
   * @param lineNumber the line in the file
   * @param quantity the input
   * @param rule the range in words
   */
  record OutOfRange(int lineNumber, String quantity, String rule) implements SheetProblem {}

  /**
   * The same quantity is given twice.
   *
   * @param lineNumber the later line
   * @param quantity the input
   * @param firstLine the line that gave it first
   */
  record Duplicate(int lineNumber, String quantity, int firstLine) implements SheetProblem {}

  /**
   * An input the estimator needs is not in the sheet.
   *
   * @param quantity the missing name
   */
  record Missing(String quantity) implements SheetProblem {}

  /** The sheet describes no stored data at all. */
  record NoStore() implements SheetProblem {}
}
