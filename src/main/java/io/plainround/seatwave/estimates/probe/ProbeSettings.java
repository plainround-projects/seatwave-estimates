package io.plainround.seatwave.estimates.probe;

import com.github.sviperll.result4j.Result;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * How much the latency probe measures. Every value comes from {@code probe.properties}; there are
 * no defaults in code, so a missing key stops the probe before it measures anything.
 *
 * @param samples timed samples kept for each operation
 * @param warmupSamples samples run and thrown away first, so the JIT compiler has compiled the loop
 * @param readsPerSample array reads timed together in one sample
 * @param memoryArrayBytes size of the array that must come from main memory
 * @param cacheArrayBytes size of the array small enough to stay in the CPU's first-level cache
 * @param diskFileBytes size of the temporary file read and written
 * @param diskBlockBytes size of one read or write
 * @param diskWriteSamples timed writes, fewer than reads because each one waits for the device
 * @param socketTimeout the longest any socket call may block
 * @param randomSeed the seed that makes every run visit the same positions
 */
public record ProbeSettings(
    int samples,
    int warmupSamples,
    int readsPerSample,
    int memoryArrayBytes,
    int cacheArrayBytes,
    int diskFileBytes,
    int diskBlockBytes,
    int diskWriteSamples,
    Duration socketTimeout,
    long randomSeed) {

  /**
   * Creates the settings.
   *
   * @throws IllegalArgumentException if a count or size is below 1, an array or file cannot hold
   *     one block, or the timeout is not positive
   */
  public ProbeSettings {
    Objects.requireNonNull(socketTimeout, "socketTimeout");
    int[] mustBePositive = {
      samples,
      warmupSamples,
      readsPerSample,
      memoryArrayBytes,
      cacheArrayBytes,
      diskFileBytes,
      diskBlockBytes,
      diskWriteSamples
    };
    for (int value : mustBePositive) {
      if (value < 1) {
        throw new IllegalArgumentException("every count and size must be 1 or more: " + value);
      }
    }
    if (diskFileBytes < diskBlockBytes || cacheArrayBytes < Integer.BYTES) {
      throw new IllegalArgumentException("the file and the arrays must hold at least one block");
    }
    if (socketTimeout.isNegative() || socketTimeout.isZero()) {
      throw new IllegalArgumentException("the socket timeout must be positive: " + socketTimeout);
    }
  }

  /**
   * Reads and checks the settings.
   *
   * @param properties the loaded {@code probe.properties}
   * @return the settings, or one line per missing or invalid key
   */
  public static Result<ProbeSettings, List<String>> from(Properties properties) {
    Objects.requireNonNull(properties, "properties");
    List<String> problems = new ArrayList<>();
    int samples = positiveInt(properties, "samples", problems);
    int warmup = positiveInt(properties, "warmup.samples", problems);
    int reads = positiveInt(properties, "memory.reads.per.sample", problems);
    int memory = positiveInt(properties, "memory.array.bytes", problems);
    int cache = positiveInt(properties, "cache.array.bytes", problems);
    int file = positiveInt(properties, "disk.file.bytes", problems);
    int block = positiveInt(properties, "disk.block.bytes", problems);
    int writes = positiveInt(properties, "disk.write.samples", problems);
    int timeoutMillis = positiveInt(properties, "socket.timeout.millis", problems);
    long seed = positiveInt(properties, "random.seed", problems);
    if (!problems.isEmpty()) {
      return Result.error(List.copyOf(problems));
    }
    try {
      return Result.success(
          new ProbeSettings(
              samples,
              warmup,
              reads,
              memory,
              cache,
              file,
              block,
              writes,
              Duration.ofMillis(timeoutMillis),
              seed));
    } catch (IllegalArgumentException inconsistent) {
      // Each key was valid alone, but together they break a rule, such as a file smaller than a
      // block.
      return Result.error(List.of(inconsistent.getMessage()));
    }
  }

  private static int positiveInt(Properties properties, String key, List<String> problems) {
    String text = properties.getProperty(key);
    if (text == null || text.isBlank()) {
      problems.add(key + " is missing");
      return 0;
    }
    try {
      int value = Integer.parseInt(text.strip());
      if (value < 1) {
        problems.add(key + " must be 1 or more, but was " + value);
      }
      return value;
    } catch (NumberFormatException notANumber) {
      // A typo is an expected failure of a config file, so it becomes a problem line, not a crash.
      problems.add(
          key + " must be a whole number up to " + Integer.MAX_VALUE + ", but was " + text);
      return 0;
    }
  }
}
