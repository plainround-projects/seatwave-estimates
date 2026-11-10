package io.plainround.seatwave.estimates.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ProbeSettingsTest {

  @Test
  void acceptsTheShippedSettingsFile() throws IOException {
    ProbeSettings settings = settingsOf(shipped());

    assertThat(settings.samples()).isEqualTo(2_000);
    assertThat(settings.memoryArrayBytes()).isEqualTo(268_435_456);
    assertThat(settings.socketTimeout()).isEqualTo(Duration.ofSeconds(2));
  }

  @Test
  void reportsEveryMissingKey() {
    List<String> problems = problemsOf(new Properties());

    assertThat(problems).hasSize(10).contains("samples is missing", "random.seed is missing");
  }

  @Test
  void reportsAValueThatIsNotAWholeNumberOrIsBelowOne() throws IOException {
    Properties properties = shipped();
    properties.setProperty("samples", "lots");
    properties.setProperty("disk.write.samples", "0");

    assertThat(problemsOf(properties))
        .containsExactly(
            "samples must be a whole number up to 2147483647, but was lots",
            "disk.write.samples must be 1 or more, but was 0");
  }

  @Test
  void reportsValuesThatAreValidAloneButNotTogether() throws IOException {
    Properties properties = shipped();
    properties.setProperty("disk.file.bytes", "1024");

    assertThat(problemsOf(properties))
        .containsExactly("the file and the arrays must hold at least one block");
  }

  @Test
  void refusesSettingsBuiltInCodeWithAZeroCountOrTimeout() {
    assertThatThrownBy(() ->
            new ProbeSettings(
                0, 1, 1, 1 << 16, 1 << 10, 1 << 16, 1 << 12, 1, Duration.ofSeconds(1), 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new ProbeSettings(1, 1, 1, 1 << 16, 1 << 10, 1 << 16, 1 << 12, 1, Duration.ZERO, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Properties shipped() throws IOException {
    Properties properties = new Properties();
    try (Reader reader =
        Files.newBufferedReader(Path.of("probe.properties"), StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    return properties;
  }

  private static ProbeSettings settingsOf(Properties properties) {
    return switch (ProbeSettings.from(properties)) {
      case Result.Success<ProbeSettings, List<String>>(ProbeSettings settings) -> settings;
      case Result.Error<ProbeSettings, List<String>>(List<String> problems) ->
          fail("expected settings but got " + problems);
    };
  }

  private static List<String> problemsOf(Properties properties) {
    return switch (ProbeSettings.from(properties)) {
      case Result.Success<ProbeSettings, List<String>>(ProbeSettings settings) ->
          fail("expected problems but got " + settings);
      case Result.Error<ProbeSettings, List<String>>(List<String> problems) -> problems;
    };
  }
}
