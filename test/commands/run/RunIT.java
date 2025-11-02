package commands.run;

import static java.lang.String.join;
import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayNameGenerator.IndicativeSentences.SentenceFragment;
import org.junit.jupiter.api.IndicativeSentencesGeneration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import utils.ExecArgumentProvider;
import utils.Fast;
import utils.Executor.ProcessSandbox;

@SentenceFragment("Run command")
@IndicativeSentencesGeneration
public class RunIT {
	private static Path codeDir = Path.of("test", "commands", "run", ".test-code");

	@SentenceFragment("shows help")
	@ParameterizedTest
	@ArgumentsSource(ExecArgumentProvider.class)
	@Fast
	public void help(ProcessSandbox sandbox) {
		sandbox.directory = codeDir;

		var result = sandbox.execute("--help");

		assertThat(result.failed()).isFalse();
	}

	@SentenceFragment("executes java command")
	@ParameterizedTest
	@ArgumentsSource(ExecArgumentProvider.class)
	public void executes(ProcessSandbox sandbox) {
		sandbox.directory = codeDir;

		var result = sandbox.execute("run");

		assertFalse(result.failed(), result.err());
		assertEquals("Works!" + lineSeparator(), result.out());
	}

	@SentenceFragment("forwards the arguments")
	@ParameterizedTest
	@ArgumentsSource(ExecArgumentProvider.class)
	public void forwardArgs(ProcessSandbox sandbox) {
		sandbox.directory = codeDir;

		var result = sandbox.execute("run", "arg1", "arg2");

		assertFalse(result.failed(), result.err());
		assertEquals(join(lineSeparator(), "Works!arg1,arg2", ""), result.out());
	}

	@SentenceFragment("--dry-run prints the command and doesn't execute it")
	@ParameterizedTest
	@ArgumentsSource(ExecArgumentProvider.class)
	public void dryRun(ProcessSandbox sandbox) {
		sandbox.directory = codeDir;

		var result = sandbox.execute("run", "-N");

		assertThat(result.failed()).isFalse();
		assertThat(result.err()).isEmpty();
		assertThat(result.out()).matches(join(lineSeparator(), "", ".*java Main\\.java", "$"));
	}
}
