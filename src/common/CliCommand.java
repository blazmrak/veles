package common;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import common.DependencyResolution.Classpath;

public class CliCommand {
	protected final List<String> command = new ArrayList<>();

	public CliCommand(String command) {
		this.command.add(command);
	}

	public CliCommand option(String option, String value) {
		if (value == null || value.isBlank()) {
			return this;
		}

		command.add(option);
		command.add(value);
		return this;
	}

	public CliCommand booleanOption(String option) {
		return booleanOption(option, true);
	}

	public CliCommand booleanOption(String option, Boolean value) {
		if (value != true) {
			return this;
		}

		command.add(option);
		return this;
	}

	public CliCommand add(String args) {
		this.command.add(args);
		return this;
	}

	public CliCommand args(String... args) {
		Collections.addAll(command, args);
		return this;
	}

	public CliCommand args(List<String> args) {
		command.addAll(args);
		return this;
	}

	public List<String> get() {
		return command;
	}

	public static String[] parseArgs(String args) throws IOException {
		var tok = new StreamTokenizer(new StringReader(args));
		tok.resetSyntax();
		tok.wordChars('!', '~');

		List<String> result = new ArrayList<>();
		while (tok.nextToken() != StreamTokenizer.TT_EOF) {
			var token = tok.sval != null
				? tok.sval
				: String.valueOf((char) tok.ttype);
			token = token.strip();
			if (!token.isBlank()) {
				result.add(token);
			}
		}

		return result.toArray(new String[0]);
	}

	public static class Javac extends CliCommand {
		public Javac() {
			super(JdkResolver.java().toString());
		}

		public Javac classpath(Classpath cp) {
			super.option("-cp", cp.toString());
			return this;
		}

		public Javac classpath(String cp) {
			super.option("-cp", cp);
			return this;
		}

		public Javac file(Path file) {
			super.add("@" + file);
			return this;
		}
	}

	public static class Java extends CliCommand {
		public Java() {
			super(JdkResolver.java().toString());
		}

		public Java classpath(Classpath cp) {
			return classpath(cp.toString());
		}

		public Java classpath(Path cp) {
			return classpath(cp.toString());
		}

		public Java classpath(String cp) {
			super.option("-cp", cp);
			return this;
		}

		public Java file(Path file) {
			return file(file.toString());
		}

		public Java file(String file) {
			super.add("@" + file);
			return this;
		}

		public Java jar(String file) {
			super.option("-jar", file);
			return this;
		}

		public Java jar(Path file) {
			return jar(file.toString());
		}

		public Java enablePreview(Boolean bool) {
			super.booleanOption("--enable-preview", bool);
			return this;
		}
	}
}
