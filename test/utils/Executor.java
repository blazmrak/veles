package utils;

import static utils.Executor.ProcessSandbox.ExecutionResult;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import common.CliCommand;
import picocli.CommandLine;

public class Executor {

	public static int exec(CliCommand command) throws InterruptedException, IOException {
		Process p = new ProcessBuilder(command.get()).start();
		return p.waitFor();
	}

	public static class CommandSandbox {
		private final Runnable command;

		public CommandSandbox(Runnable command) {
			this.command = command;
		}

		public ExecutionResult execute(String... args) {
			var cmd = new CommandLine(this.command);

			var out = new StringWriter();
			var err = new StringWriter();
			cmd.setOut(new PrintWriter(out));
			cmd.setErr(new PrintWriter(err));

			var dryArgs = Arrays.copyOf(args, args.length + 1);
			dryArgs[dryArgs.length - 1] = "-N";
			var exitCode = cmd.execute(dryArgs);

			return new ExecutionResult(
				new CliCommand("noop").args(args),
				exitCode,
				out.toString(),
				err.toString()
			);
		}
	}

	public static class ProcessSandbox {
		private final CliCommand command;
		public final String name;
		public Path directory = Path.of(".");
		private final Path out;
		private final Path err;

		public ProcessSandbox(String name, CliCommand command) {
			this.name = name;
			this.command = command;
			try {
				out = Files.createTempFile("out", ".txt");
				err = Files.createTempFile("err", ".txt");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		public ExecutionResult execute(String... args) {
			try {
				command.args(args);

				var exitCode = new ProcessBuilder().command(command.get())
					.directory(directory.toFile())
					.redirectOutput(out.toFile())
					.redirectError(err.toFile())
					.start()
					.waitFor();
				return new ExecutionResult(command, exitCode, Files.readString(out), Files.readString(err));
			} catch (InterruptedException | IOException e) {
				throw new RuntimeException(e);
			}
		}

		public String output() {
			try {
				return Files.readString(this.out);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String toString() {
			return name;
		}

		public static record ExecutionResult(CliCommand command, int code, String out, String err) {
			@Override
			public String toString() {
				return """
					EXIT: %d
					OUT:
					%s
					---------------
					ERR:
					%s
					""".formatted(code, out, err);
			}

			public boolean failed() {
				return code != 0;
			}
		}
	}
}
