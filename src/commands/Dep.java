package commands;

import static common.DependencyResolution.resolve;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.Files.writeString;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.jline.terminal.TerminalBuilder;

import commands.widgets.MavenSearchWidget;
import config.Config;
import config.ConfigDoc.ConfDependency;
import config.ConfigDoc.ConfDependency.Scope;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "dep")
public class Dep implements Runnable {
	private final Path depsPath = Path.of("target", "deps");

	@Option(names = { "-m", "--materialize" })
	boolean materialize;

	@Parameters
	List<String> args;

	public void run() {
		try {
			var terminal = TerminalBuilder.builder().build();
			var gav = new MavenSearchWidget(terminal).search();
			Config.addDependency(ConfDependency.parse(gav.toString()));
			if (exists(Path.of(".dep.compile"))
				|| exists(Path.of(".dep.runtime"))
				|| exists(Path.of(".dep.nocomp"))) {
				this.save(materialize);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Saves dependencies into a file to use with JDK via @ option. It generates .dep files in root
	 * directory and saves links to dependencies inside target/deps. These links can be materialized
	 * using the "--materialize" flag.
	 * 
	 * @throws IOException
	 *
	 * @example java @.dep.nocomp Script.java
	 */
	@Command(name = "save", description = "Saves dependencies for use with JDK via @ option")
	public void save(@Option(names = { "-m", "--materialize" }) boolean materialize)
		throws IOException {
		this.materialize = materialize;
		Files.createDirectories(depsPath);

		// save compile time file
		var compileDeps = resolve(Scope.COMPILE, Scope.PROVIDED).map(this::linkLib)
			.collect(joining(":"));
		var annotationProcessors = resolve(Scope.PROCESSOR).map(this::linkLib).collect(joining(":"));
		var compileFile = new StringBuilder();
		compileFile.append("-d\n");
		compileFile.append("target/classes\n");
		if (compileDeps.length() > 0) {
			compileFile.append("-cp\n");
			compileFile.append(compileDeps + "\n");
		}
		if (annotationProcessors.length() > 0) {
			compileFile.append("--processor-path\n");
			compileFile.append(annotationProcessors + "\n");
			compileFile.append("-proc:full");
		}
		try {
			writeString(Path.of(".dep.compile"), compileFile.toString());
		} catch (Exception e) {
		}

		// save runtime time file
		var runtimeDeps = resolve(Scope.COMPILE, Scope.PROVIDED).map(this::linkLib)
			.collect(joining(":")) + ":" + Config.outputClassesDir().toString();
		var runtimeFile = new StringBuilder();
		if (runtimeDeps.length() > 0) {
			runtimeFile.append("-cp\n");
			runtimeFile.append(runtimeDeps + "\n");
		}
		if (runtimeFile.length() > 0) {
			try {
				writeString(Path.of(".dep.runtime"), runtimeFile.toString());
			} catch (Exception e) {
			}
		}

		// save nocomp time file
		var nocompDeps = resolve(Scope.COMPILE, Scope.PROVIDED, Scope.RUNTIME).map(this::linkLib)
			.collect(joining(":"));
		var nocompFile = new StringBuilder();
		if (runtimeDeps.length() > 0) {
			nocompFile.append("-cp\n");
			nocompFile.append(nocompDeps + "\n");
		}
		if (nocompFile.length() > 0) {
			try {
				writeString(Path.of(".dep.nocomp"), nocompFile.toString());
			} catch (Exception e) {
			}
		}
	}

	private String linkLib(Artifact artifact) {
		Path target = Path.of(artifact.getFile().getAbsolutePath());
		Path link = depsPath.resolve(target.getFileName());
		try {
			if (!materialize) {
				if (exists(link) && !isSymbolicLink(link)) {
					Files.delete(link);
				}
				if (!exists(link)) {
					Files.createSymbolicLink(link, target);
				}
			} else {
				if (exists(link) && isSymbolicLink(link)) {
					Files.delete(link);
				}
				if (!exists(link)) {
					Files.copy(target, link);
				}
			}
			return link.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
