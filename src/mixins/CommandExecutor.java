package mixins;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

public class CommandExecutor {
	@Spec
	CommandSpec spec;
	private boolean quiet;
	private Function<ProcessBuilder, Process> processBuilderDecorator = (builder) -> {
		try {
			return builder.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	};

	@ArgGroup(heading = "Common:\n", order = 1000)
	public CommonOptions opts = new CommonOptions();

	public int executeBlocking(List<String> command) {
		try {
			var process = execute(command);
			if (process == null) {
				return 0;
			}

			return process.waitFor();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public Process execute(List<String> command) {
		if (opts.verbose || opts.dryRun) {
			spec.commandLine().getOut().println();
			spec.commandLine().getOut().println(prettyFormatCommand(command));
		}

		if (opts.dryRun) {
			return null;
		}

		try {
			ProcessBuilder builder = new ProcessBuilder().command(command);

			if (!quiet) {
				builder.inheritIO();
			}

			return this.processBuilderDecorator.apply(builder);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public void beforeStart(Function<ProcessBuilder, Process> processBuilderDecorator) {
		this.processBuilderDecorator = processBuilderDecorator;
	}

	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}

	private String prettyFormatCommand(List<String> command) {
		return command.stream().collect(Collectors.joining(" "));
	}
}
