package io.plainround.seatwave.estimates.probe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Random;

/**
 * Times random block reads and forced block writes on a temporary file.
 *
 * <p>The reads go through the operating system's page cache. The probe has just written the file,
 * so most reads are served from memory, not from the device; the report says so. A forced write
 * must reach the storage device before {@code FileChannel.force} returns, so it measures the device
 * and the file system. Stateless; each call creates and deletes its own file.
 */
public final class DiskProbe {

  private DiskProbe() {}

  /**
   * Runs both disk measurements on one temporary file in the given folder.
   *
   * @param settings sizes, counts and the random seed
   * @param folder where the temporary file is created; it is deleted before the method returns
   * @return the reads and the forced writes
   * @throws IOException if the file cannot be created, written, read or deleted
   */
  public static DiskResults measure(ProbeSettings settings, Path folder) throws IOException {
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(folder, "folder");
    Path file = Files.createTempFile(folder, "latency-probe-", ".bin");
    try (FileChannel channel =
        FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
      Random random = new Random(settings.randomSeed());
      fill(channel, settings, random);
      LatencySamples reads = timeReads(channel, settings, random);
      LatencySamples writes = timeForcedWrites(channel, settings, random);
      return new DiskResults(reads, writes);
    } finally {
      Files.deleteIfExists(file);
    }
  }

  /**
   * The two disk measurements.
   *
   * @param reads random block reads
   * @param forcedWrites block writes, each forced to the device
   */
  public record DiskResults(LatencySamples reads, LatencySamples forcedWrites) {}

  private static void fill(FileChannel channel, ProbeSettings settings, Random random)
      throws IOException {
    ByteBuffer block = ByteBuffer.allocate(settings.diskBlockBytes());
    for (long offset = 0; offset < settings.diskFileBytes(); offset += settings.diskBlockBytes()) {
      random.nextBytes(block.array());
      block.clear();
      writeFully(channel, block, offset);
    }
    channel.force(true);
  }

  private static LatencySamples timeReads(
      FileChannel channel, ProbeSettings settings, Random random) throws IOException {
    ByteBuffer block = ByteBuffer.allocate(settings.diskBlockBytes());
    for (int warmupIndex = 0; warmupIndex < settings.warmupSamples(); warmupIndex++) {
      readFully(channel, block, randomOffset(settings, random));
    }
    long[] samples = new long[settings.samples()];
    for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
      long offset = randomOffset(settings, random);
      long start = System.nanoTime();
      readFully(channel, block, offset);
      samples[sampleIndex] = System.nanoTime() - start;
    }
    return new LatencySamples(samples, 1);
  }

  private static LatencySamples timeForcedWrites(
      FileChannel channel, ProbeSettings settings, Random random) throws IOException {
    ByteBuffer block = ByteBuffer.allocate(settings.diskBlockBytes());
    random.nextBytes(block.array());
    long[] samples = new long[settings.diskWriteSamples()];
    for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
      long offset = randomOffset(settings, random);
      block.clear();
      long start = System.nanoTime();
      writeFully(channel, block, offset);
      // force(false) asks only for the data, not the file's metadata: what a database log needs.
      channel.force(false);
      samples[sampleIndex] = System.nanoTime() - start;
    }
    return new LatencySamples(samples, 1);
  }

  private static long randomOffset(ProbeSettings settings, Random random) {
    int blocks = settings.diskFileBytes() / settings.diskBlockBytes();
    return (long) random.nextInt(blocks) * settings.diskBlockBytes();
  }

  // A positional read or write may move fewer bytes than asked, so both loop until the block is
  // done.
  private static void readFully(FileChannel channel, ByteBuffer block, long offset)
      throws IOException {
    block.clear();
    while (block.hasRemaining()) {
      int read = channel.read(block, offset + block.position());
      if (read < 0) {
        throw new IOException("the probe file ended before offset " + offset);
      }
    }
  }

  private static void writeFully(FileChannel channel, ByteBuffer block, long offset)
      throws IOException {
    while (block.hasRemaining()) {
      channel.write(block, offset + block.position());
    }
  }
}
