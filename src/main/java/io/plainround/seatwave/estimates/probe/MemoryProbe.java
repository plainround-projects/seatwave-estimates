package io.plainround.seatwave.estimates.probe;

import java.util.Objects;
import java.util.Random;

/**
 * Times the timer itself and array reads, from cache and from main memory.
 *
 * <p>An array read is timed by pointer chasing: each element holds the index of the next one to
 * read, in a random single cycle through the whole array. Each read must wait for the one before,
 * so the CPU cannot prefetch or overlap them, and the time per read is the true latency. Stateless
 * and safe to use from any thread; each call allocates its own array.
 */
public final class MemoryProbe {

  private MemoryProbe() {}

  /**
   * Measures the smallest step System.nanoTime can show on this machine.
   *
   * @param settings how many samples to take
   * @return the difference between two back-to-back calls, one per sample
   */
  public static LatencySamples timerStep(ProbeSettings settings) {
    Objects.requireNonNull(settings, "settings");
    long[] samples = new long[settings.samples()];
    for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
      long start = System.nanoTime();
      samples[sampleIndex] = System.nanoTime() - start;
    }
    return new LatencySamples(samples, 1);
  }

  /**
   * Measures dependent reads from an array of the given size.
   *
   * @param settings counts and the random seed
   * @param arrayBytes the size of the array; small sizes stay in cache, large ones do not
   * @return one sample per batch of {@code readsPerSample} reads
   */
  public static LatencySamples arrayReads(ProbeSettings settings, int arrayBytes) {
    Objects.requireNonNull(settings, "settings");
    int[] nextIndex = singleCycle(arrayBytes / Integer.BYTES, new Random(settings.randomSeed()));
    int position = 0;
    // Warm-up: the first batches run interpreted and fault the array's pages in; they are
    // discarded.
    for (int warmupIndex = 0; warmupIndex < settings.warmupSamples(); warmupIndex++) {
      position = chase(nextIndex, position, settings.readsPerSample());
    }
    long[] samples = new long[settings.samples()];
    for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
      long start = System.nanoTime();
      position = chase(nextIndex, position, settings.readsPerSample());
      samples[sampleIndex] = System.nanoTime() - start;
    }
    // Using the final position keeps the compiler from removing the reads as dead code.
    if (position < 0 || position >= nextIndex.length) {
      throw new IllegalStateException("the chase left the array at " + position);
    }
    return new LatencySamples(samples, settings.readsPerSample());
  }

  private static int chase(int[] nextIndex, int start, int reads) {
    int position = start;
    for (int readIndex = 0; readIndex < reads; readIndex++) {
      position = nextIndex[position];
    }
    return position;
  }

  // Sattolo's algorithm: a random permutation that is one single cycle, so the chase visits every
  // element before it repeats and never gets stuck in a small loop that would stay in cache.
  private static int[] singleCycle(int length, Random random) {
    int[] nextIndex = new int[length];
    for (int index = 0; index < length; index++) {
      nextIndex[index] = index;
    }
    for (int index = length - 1; index > 0; index--) {
      int swapWith = random.nextInt(index);
      int held = nextIndex[index];
      nextIndex[index] = nextIndex[swapWith];
      nextIndex[swapWith] = held;
    }
    return nextIndex;
  }
}
