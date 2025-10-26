package commands;

import static common.DependencyResolution.resolvePaths;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jline.terminal.TerminalBuilder;

import commands.widgets.MavenSearchWidget;
import config.Config;
import config.ConfigDoc.ConfDependency;
import config.ConfigDoc.ConfDependency.Scope;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "dep")
public class Dep implements Runnable {

	@Parameters
	List<String> args;

	public void run() {
		try {
			var terminal = TerminalBuilder.builder().build();
			var gav = new MavenSearchWidget(terminal).search();
			Config.addDependency(ConfDependency.parse(gav.toString()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Saves dependencies into a file to use with JDK via @ option.
	 *
	 * @example java @.dep.nocomp Script.java
	 */
	@Command(name = "save", description = "Saves dependencies for use with JDK via @ option")
	public void save() {
		var compileDeps = resolvePaths(Scope.COMPILE, Scope.PROVIDED).collect(joining(":"));
		var runtimeDeps = resolvePaths(Scope.COMPILE, Scope.RUNTIME).collect(joining(":"))
			+ ":target/classes";
		var nocompDeps = resolvePaths(Scope.COMPILE, Scope.PROVIDED, Scope.RUNTIME)
			.collect(joining(":"));
		var annotationProcessors = resolvePaths(Scope.PROCESSOR).collect(joining(":"));

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
			Files.writeString(Path.of(".dep.compile"), compileFile.toString());
		} catch (Exception e) {
		}

		var runtimeFile = new StringBuilder();
		if (runtimeDeps.length() > 0) {
			runtimeFile.append("-cp\n");
			runtimeFile.append(runtimeDeps + "\n");
		}
		if (runtimeFile.length() > 0) {
			try {
				Files.writeString(Path.of(".dep.runtime"), runtimeFile.toString());
			} catch (Exception e) {
			}
		}

		var nocompFile = new StringBuilder();
		if (runtimeDeps.length() > 0) {
			nocompFile.append("-cp\n");
			nocompFile.append(nocompDeps + "\n");
		}
		if (nocompFile.length() > 0) {
			try {
				Files.writeString(Path.of(".dep.nocomp"), nocompFile.toString());
			} catch (Exception e) {
			}
		}
	}
}
