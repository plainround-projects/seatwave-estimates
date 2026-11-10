package io.plainround.seatwave.estimates.probe;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Command-line entry point of the latency probe.
 *
 * <p>It reads {@code probe.properties}, prints the machine it runs on, measures each operation and
 * prints the table. Exit status 2 means bad settings; 1 means a measurement failed.
 */
public final class ProbeCli {

  private static final int EXIT_MEASUREMENT_FAILED = 1;
  // Exit status 2 is the shell convention for "the command was used incorrectly".
  private static final int EXIT_BAD_USAGE = 2;
  private static final long BYTES_PER_MEBIBYTE = 1L << 20;

  private ProbeCli() {}

  /**
   * Runs the probe.
   *
   * @param arguments the path to the settings file, then the folder for the temporary file
   */
  public static void main(String[] arguments) {
    if (arguments.length != 2) {
      System.err.println("usage: probe <probe.properties> <folder for the temporary file>");
      System.exit(EXIT_BAD_USAGE);
    }
    switch (ProbeSettings.from(load(Path.of(arguments[0])))) {
      case Result.Success<ProbeSettings, List<String>>(ProbeSettings settings) ->
          run(settings, Path.of(arguments[1]));
      case Result.Error<ProbeSettings, List<String>>(List<String> problems) -> {
        problems.forEach(problem -> System.err.println("invalid setting: " + problem));
        System.exit(EXIT_BAD_USAGE);
      }
    }
  }

  private static void run(ProbeSettings settings, Path folder) {
    Runtime runtime = Runtime.getRuntime();
    System.out.printf(
        Locale.ROOT,
        "Machine: %s %s on %s, %d logical processors, Java %s, heap limit %d MiB%n",
        System.getProperty("os.name"),
        System.getProperty("os.version"),
        System.getProperty("os.arch"),
        runtime.availableProcessors(),
        System.getProperty("java.version"),
        runtime.maxMemory() / BYTES_PER_MEBIBYTE);
    try {
      Map<ProbeOperation, LatencySamples> measured = new LinkedHashMap<>();
      measured.put(ProbeOperation.TIMER_STEP, MemoryProbe.timerStep(settings));
      measured.put(
          ProbeOperation.CACHE_READ, MemoryProbe.arrayReads(settings, settings.cacheArrayBytes()));
      measured.put(
          ProbeOperation.MEMORY_READ,
          MemoryProbe.arrayReads(settings, settings.memoryArrayBytes()));
      DiskProbe.DiskResults disk = DiskProbe.measure(settings, folder);
      measured.put(ProbeOperation.DISK_READ, disk.reads());
      measured.put(ProbeOperation.DISK_WRITE_FORCED, disk.forcedWrites());
      measured.put(ProbeOperation.LOOPBACK_ROUND_TRIP, LoopbackProbe.roundTrips(settings));
      System.out.print(ProbeReport.render(measured));
      System.out.println("These numbers describe this machine only. Yours will differ.");
    } catch (IOException measurementFailed) {
      System.err.println("a measurement failed: " + measurementFailed);
      System.exit(EXIT_MEASUREMENT_FAILED);
    }
  }

  private static Properties load(Path path) {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      properties.load(reader);
      return properties;
    } catch (IOException unreadable) {
      // A missing settings file is bad usage: say which file and why, then stop before measuring.
      System.err.println("cannot read " + path + ": " + unreadable.getMessage());
      System.exit(EXIT_BAD_USAGE);
      throw new IllegalStateException("System.exit returned", unreadable);
    }
  }
}
