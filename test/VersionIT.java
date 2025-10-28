import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayNameGenerator.IndicativeSentences.SentenceFragment;
import org.junit.jupiter.api.IndicativeSentencesGeneration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import utils.ExecArgumentProvider;
import utils.Executor.ProcessSandbox;

@SentenceFragment("Version flag")
@IndicativeSentencesGeneration
public class VersionIT {

	@SentenceFragment("prints the version")
	@ParameterizedTest
	@ArgumentsSource(ExecArgumentProvider.class)
	public void test(ProcessSandbox process) {
		process.command.add("--version");
		process.execute();
		if ("true".equals(System.getenv("CI"))) {
			assertNotEquals("local\n", process.output());
		} else {
			assertEquals("local\n", process.output());
		}
	}
}
