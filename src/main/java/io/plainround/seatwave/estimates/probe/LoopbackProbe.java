package io.plainround.seatwave.estimates.probe;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Times a TCP round trip to an echo server that this class starts on the loopback address.
 *
 * <p>No byte leaves the machine: the server listens only on the loopback address, on a port the
 * operating system picks, and lives only for the duration of one call. Every socket call is bounded
 * by the configured timeout. Stateless; each call has its own server.
 */
public final class LoopbackProbe {

  private static final int ONE_WAITING_CONNECTION = 1;
  private static final int PING = 1;

  private LoopbackProbe() {}

  /**
   * Measures round trips of one byte each way.
   *
   * @param settings counts and the socket timeout
   * @return one sample per round trip
   * @throws IOException if a socket fails or a call times out
   */
  public static LatencySamples roundTrips(ProbeSettings settings) throws IOException {
    Objects.requireNonNull(settings, "settings");
    int timeoutMillis = Math.toIntExact(settings.socketTimeout().toMillis());
    // Port 0 asks the operating system for any free port, so two runs never collide.
    try (ServerSocket server =
            new ServerSocket(0, ONE_WAITING_CONNECTION, InetAddress.getLoopbackAddress());
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      server.setSoTimeout(timeoutMillis);
      Future<Void> echo = executor.submit(() -> echoOneClient(server, timeoutMillis));
      LatencySamples samples = timeClient(server, settings, timeoutMillis);
      awaitEcho(echo, settings);
      return samples;
    }
  }

  private static LatencySamples timeClient(
      ServerSocket server, ProbeSettings settings, int timeoutMillis) throws IOException {
    try (Socket client = new Socket()) {
      client.connect(server.getLocalSocketAddress(), timeoutMillis);
      // Without TCP_NODELAY the kernel may hold a 1-byte write back to batch it, adding delay.
      client.setTcpNoDelay(true);
      client.setSoTimeout(timeoutMillis);
      OutputStream out = client.getOutputStream();
      InputStream in = client.getInputStream();
      for (int warmupIndex = 0; warmupIndex < settings.warmupSamples(); warmupIndex++) {
        roundTrip(out, in);
      }
      long[] samples = new long[settings.samples()];
      for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
        long start = System.nanoTime();
        roundTrip(out, in);
        samples[sampleIndex] = System.nanoTime() - start;
      }
      return new LatencySamples(samples, 1);
    }
  }

  private static void roundTrip(OutputStream out, InputStream in) throws IOException {
    out.write(PING);
    out.flush();
    if (in.read() != PING) {
      throw new IOException("the echo server answered with the wrong byte or closed early");
    }
  }

  private static Void echoOneClient(ServerSocket server, int timeoutMillis) throws IOException {
    try (Socket connection = server.accept()) {
      connection.setTcpNoDelay(true);
      connection.setSoTimeout(timeoutMillis);
      InputStream in = connection.getInputStream();
      OutputStream out = connection.getOutputStream();
      // The client closing its socket ends the stream with -1, which ends the echo.
      for (int received = in.read(); received >= 0; received = in.read()) {
        out.write(received);
        out.flush();
      }
    }
    return null;
  }

  private static void awaitEcho(Future<Void> echo, ProbeSettings settings) throws IOException {
    try {
      echo.get(settings.socketTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException failed) {
      throw new IOException("the echo server failed", failed.getCause());
    } catch (TimeoutException tooSlow) {
      echo.cancel(true);
      throw new IOException("the echo server did not stop within the socket timeout", tooSlow);
    } catch (InterruptedException interrupted) {
      // Keep the interrupt visible to the caller and stop measuring.
      Thread.currentThread().interrupt();
      InterruptedIOException stopped = new InterruptedIOException("interrupted while measuring");
      stopped.initCause(interrupted);
      throw stopped;
    }
  }
}
