package commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import common.DependencyResolution;
import common.Paths;
import config.Config;
import config.ConfigDoc.Settings.Formatter;
import mixins.CommandExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "format")
public class Format implements Runnable {
	@Mixin
	CommandExecutor executor;

	@Option(names = { "-p", "--palantir" })
	boolean usePalantir;

	@Option(names = { "-s", "--show" })
	boolean show;

	@Option(names = { "-c", "--check" })
	boolean failIfChanged;

	@Parameters
	List<String> args;

	public void run() {
		var formatter = Config.getFormatter();
		if (formatter != null) {
			switch (formatter) {
				case ECLIPSE -> formatEclipse(formatter);
				case PALANTIR -> formatPalantir(formatter);
			}
		} else if (usePalantir) {
			formatPalantir(Formatter.PALANTIR);
		} else {
			formatEclipse(Formatter.ECLIPSE);
		}
	}

	private void formatPalantir(Formatter formatter) {
		// fetch formatter from maven central
		var gav = formatter.getGav();
		var artifact = DependencyResolution.getArtifact(gav, "nativeImage-linux-glibc_x86-64", "bin");

		var file = artifact.getFile();
		if (!file.canExecute()) {
			System.out.println("""
				Formatter is not executable, run:

				chmod +x %s
				""".formatted(file.getAbsolutePath()));
			return;
		}

		var command = new ArrayList<String>();
		command.add(file.getAbsolutePath());
		if (executor.opts.dryRun) {
			command.add("-n");
		} else {
			command.add("-i");
		}
		if (failIfChanged) {
			command.add("--set-exit-if-changed");
		}
		Paths.allSourceFiles().map(Path::toString).forEach(command::add);

		var status = executor.executeBlocking(command);
		if (status != 0) {
			throw new RuntimeException("Formatting failed with exit status: " + status);
		}
	}

	record FormatResult(Path path, String file) {
		public String toString() {
			if (file == null)
				return path.toString();

			var sep = "-".repeat(path.toString().length()) + "\n";

			return sep + path + ":\n" + sep + file;
		}
	}

	private void formatEclipse(Formatter fmt) {
		var options = new Lsp().readFormatterSettings();
		options.put("org.eclipse.jdt.core.formatter.lineSplit", String.valueOf(Config.getLineWidth()));
		options.put(
			"org.eclipse.jdt.core.formatter.comment.line_length",
			String.valueOf(Config.getLineWidth())
		);
		options.put("org.eclipse.jdt.core.formatter.tabulation.char", Config.getIndent().toString());

		var files = args == null || args.isEmpty()
			? Paths.allSourceFiles()
			: args.stream().map(Path::of);

		var changes = files.parallel().map(sourcePath -> {
			String source = Paths.tryReadFile(sourcePath);
			Document doc = new Document(source);
			var changedBytes = formatCode(doc, options);
			if (changedBytes == 0) {
				return null;
			}

			if (!executor.opts.dryRun && !failIfChanged) {
				try {
					Files.writeString(sourcePath, doc.get());
				} catch (Exception e) {
					System.out.println("Failed to write " + sourcePath + ": " + e.getMessage());
					return null;
				}
			}

			return show
				? new FormatResult(sourcePath, doc.get())
				: new FormatResult(sourcePath, null);
		}).filter(s -> s != null).toList();

		if (failIfChanged) {
			if (changes.size() > 0) {
				System.out.println("The following files are not formatted:");
				changes.forEach(System.out::println);
				System.out.println("\nCheck failed!");
				System.exit(1);
			}
		} else if (executor.opts.dryRun) {
			if (changes.size() > 0) {
				System.out.println("Files that will be formatted:");
				changes.forEach(System.out::println);
			} else {
				System.out.println("Nothing to format");
			}
		} else {
			if (changes.size() > 0) {
				System.out.println("Formatted:");
				changes.forEach(System.out::println);
			}
		}
	}

	private int formatCode(Document doc, Map<String, String> options) {
		var formatter = ToolFactory.createCodeFormatter(options);
		TextEdit edit = formatter.format(
			CodeFormatter.K_COMPILATION_UNIT,
			doc.get(),
			0,
			doc.get().length(),
			0,
			System.lineSeparator()
		);
		try {
			edit.apply(doc);
			return edit.getLength();
		} catch (MalformedTreeException | BadLocationException e) {
			throw new RuntimeException(e);
		}
	}
}
