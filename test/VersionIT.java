import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayNameGenerator.IndicativeSentences.SentenceFragment;
import org.junit.jupiter.api.IndicativeSentencesGeneration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import utils.ExecArgumentProvider;
import utils.Fast;
import utils.Executor.ProcessSandbox;

@SentenceFragment("Version flag")
@IndicativeSentencesGeneration
public class VersionIT {

	@SentenceFragment("prints the version")
	@ParameterizedTest
	@ArgumentsSource(ExecArgumentProvider.class)
	@Fast
	public void test(ProcessSandbox process) throws IOException {
		var result = process.execute("--version");
		assertThat(result.out().strip()).isEqualTo(Files.readString(Path.of("src", "VERSION")).strip());
	}
}
