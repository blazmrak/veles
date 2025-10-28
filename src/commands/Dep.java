package commands;

import static common.DependencyResolution.mavenDeps;
import static config.Config.junitVersion;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.Files.writeString;

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
				|| exists(Path.of(".dep.nocomp"))
				|| exists(Path.of(".dep.testcomp"))
				|| exists(Path.of(".dep.test"))) {
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
		var compileDeps = mavenDeps().add(Scope.COMPILE, Scope.PROVIDED).classpath();
		compileDeps.jars.forEach(this::linkLib);
		var annotationProcessors = mavenDeps().add(Scope.PROCESSOR).classpath();
		annotationProcessors.jars.forEach(this::linkLib);
		var compileFile = new StringBuilder();
		compileFile.append("-d\n");
		compileFile.append(Config.outputClassesDir().toString() + "\n");
		if (compileDeps.hasDeps()) {
			compileFile.append("-cp\n");
			compileFile.append(compileDeps + "\n");
		}
		if (annotationProcessors.hasDeps()) {
			compileFile.append("--processor-path\n");
			compileFile.append(annotationProcessors + "\n");
			compileFile.append("-proc:full");
		}
		try {
			writeString(Path.of(".dep.compile"), compileFile.toString());
		} catch (Exception e) {
		}

		// save runtime time file
		var runtimeDeps = mavenDeps().add(Scope.COMPILE, Scope.RUNTIME)
			.classpath()
			.add(Config.outputClassesDir());
		var runtimeFile = new StringBuilder();
		runtimeFile.append("-cp\n");
		runtimeFile.append(runtimeDeps + "\n");
		try {
			writeString(Path.of(".dep.runtime"), runtimeFile.toString());
		} catch (Exception e) {
		}

		// save nocomp time file
		var nocompDeps = mavenDeps().add(Scope.COMPILE, Scope.PROVIDED, Scope.RUNTIME).classpath();
		nocompDeps.jars.forEach(this::linkLib);
		var nocompFile = new StringBuilder();
		if (nocompDeps.hasDeps()) {
			nocompFile.append("-cp\n");
			nocompFile.append(nocompDeps + "\n");
		}
		if (nocompFile.length() > 0) {
			try {
				writeString(Path.of(".dep.nocomp"), nocompFile.toString());
			} catch (Exception e) {
			}
		}

		if (Files.exists(Config.testDir())) {
			// save testcomp file
			var testcompDeps = mavenDeps().add(Scope.COMPILE, Scope.PROVIDED, Scope.TEST)
				.add(ConfDependency.parse("!org.junit.jupiter:junit-jupiter-api:" + junitVersion()))
				.add(ConfDependency.parse("!org.junit.jupiter:junit-jupiter-params:" + junitVersion()))
				.classpath()
				.add(Config.outputClassesDir());
			var testcompFile = new StringBuilder();
			testcompFile.append("-cp\n");
			testcompFile.append(testcompDeps + "\n");
			testcompFile.append("-d\n");
			testcompFile.append(Config.outputTestClassesDir() + "\n");
			try {
				writeString(Path.of(".dep.testcomp"), testcompFile.toString());
			} catch (Exception e) {
			}

			// save test file
			var testDeps = mavenDeps().add(Scope.COMPILE, Scope.RUNTIME, Scope.TEST)
				.add(ConfDependency.parse("!org.junit.platform:junit-platform-console:" + junitVersion()))
				.add(ConfDependency.parse("!org.junit.jupiter:junit-jupiter-engine:" + junitVersion()))
				.add(ConfDependency.parse("!org.junit.jupiter:junit-jupiter-params:" + junitVersion()))
				.classpath()
				.add(Config.outputClassesDir())
				.add(Config.outputTestClassesDir())
				.toString();
			var testFile = new StringBuilder();
			testFile.append("-cp\n");
			testFile.append(testDeps + "\n");
			try {
				writeString(Path.of(".dep.test"), testFile.toString());
			} catch (Exception e) {
			}
		}
	}

	private String linkLib(String path) {
		var target = Path.of(path);
		var link = depsPath.resolve(target.getFileName());
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
