package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import common.CliCommand;

public class Executor {

	public static int exec(CliCommand command) throws InterruptedException, IOException {
		Process p = new ProcessBuilder(command.get()).start();
		return p.waitFor();
	}

	public static class ProcessSandbox {
		public final CliCommand command;
		public final String name;
		private final ProcessBuilder pb;
		private final Path output;

		public ProcessSandbox(String name, CliCommand command) {
			this.name = name;
			this.command = command;
			try {
				output = Files.createTempFile("output", ".txt");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			pb = new ProcessBuilder(command.get()).redirectOutput(output.toFile())
				.redirectError(output.toFile());
		}

		public void execute() {
			try {
				var res = pb.start().waitFor();
				if (res != 0) {
					throw new RuntimeException(Files.readString(output));
				}
			} catch (InterruptedException | IOException e) {
				throw new RuntimeException(e);
			}
		}

		public String output() {
			try {
				return Files.readString(this.output);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
