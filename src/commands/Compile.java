package commands;

import static common.DependencyResolution.mavenDeps;
import static common.DependencyResolution.resolvePaths;
import static config.Config.jacocoVersion;
import static config.Config.junitVersion;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import common.CliCommand;
import common.DependencyResolution;
import common.FilesUtil;
import common.JdkResolver;
import common.Paths;
import common.Zip;
import config.Config;
import config.ConfigDoc.ConfDependency;
import config.ConfigDoc.ConfDependency.Scope;
import config.ConfigDoc.Gav;
import mixins.CommandExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "compile", description = { "Compile .java files" })
public class Compile implements Runnable {
	@Mixin
	CommandExecutor executor;

	@Option(names = { "-c", "--full-clean" }, description = { "Delete the target directory" })
	boolean fullClean;

	@Option(names = { "-C", "--skip-clean" }, description = { "Clean the classes output" })
	boolean skipClean;

	@Option(names = { "-S", "--skip-compile" }, description = { "Skip compilation step" })
	boolean skipCompile;

	@Option(names = { "-t", "--unit-test" }, description = { "Run unit tests" })
	boolean doUnit;

	@Option(names = { "-T", "--unit-quick" }, description = { "Skip unit tests tagged with 'slow'" })
	boolean doUnitQuick;

	@Option(names = { "-j", "--jar" }, description = { "Package classes as a .jar file" })
	boolean doJar;

	@Option(names = { "-n", "--native" }, description = { "Package classes as a native executable" })
	boolean doNative;

	@Option(
		names = { "-r", "--native-reach" },
		description = { "Used when native compilation is acting up" }
	)
	boolean doReach;

	@Option(names = { "-z", "--zip" }, description = { "Package libs and .jar into a .zip" })
	boolean doZip;

	@Option(
		names = { "-d", "--docker" },
		description = { "Package libs and .jar into a docker image" }
	)
	boolean doDocker;

	@Option(names = { "-u", "--uber" }, description = { "Package libs into a single .jar" })
	boolean doUber;

	@Option(names = { "-U", "--exploded" }, description = { "Unzip the uber jar" })
	boolean doExploded;

	@Option(names = { "-i", "--integration-test" }, description = { "Run integration tests" })
	boolean doIntegration;

	@Option(
		names = { "-I", "--integration-quick" },
		description = { "Skip integrationt tests tagged with 'slow'" }
	)
	boolean doIntegrationQuick;

	@Option(names = { "-O", "--only" }, description = { "Run only the tests that have 'only' tag" })
	boolean runOnly;

	@Option(
		names = { "-f", "--filter" },
		description = { "Equivalent to JUnit --include-methodname, used for filtering tests" },
		arity = "1..*"
	)
	List<String> filterPatterns = Collections.emptyList();

	@Option(names = { "--cover" }, description = { "Produce coverage report" })
	boolean doCover;

	@Option(names = { "-e", "--entrypoint" }, description = { "Entrypoint for the java program" })
	String entrypoint;

	@Option(names = { "-X", "--ignore-depfiles" }, description = { "Ignore .dep files" })
	boolean ignoreDepfiles;

	@Parameters
	List<String> args = Collections.emptyList();

	public void run() {
		if (!skipClean) {
			clean();
		}

		if (!skipCompile) {
			compile();
		}

		if (doUnit) {
			testCompile();
			unitTestRun();
		}

		if (doReach) {
			reach();
		}

		if (doJar || doZip || doDocker || doUber) {
			jar();
		}

		if (doZip) {
			zip();
		}

		if (doDocker) {
			docker();
		}

		if (doUber || doExploded) {
			uber();
		}

		if (doExploded) {
			exploded();
		}

		if (doNative) {
			_native();
		}

		if (doIntegration) {
			if (!doUnit) {
				testCompile();
			}
			integrationTestRun();
		}

		if (doCover) {
			generateCoverageReport();
		}
	}

	private void generateCoverageReport() {
		var jacocoCli = DependencyResolution
			.getArtifact(new Gav("org.jacoco:org.jacoco.cli:" + jacocoVersion()), "nodeps")
			.getFile()
			.getAbsolutePath();
		var command = new CliCommand.Java().option("-jar", jacocoCli)
			.add("report")
			.add(Config.outputDir().resolve("test-reports", "jacoco.exec").toString())
			.option("--classfiles", Config.outputClassesDir().toString())
			.option("--sourcefiles", Config.sourceDir().toString())
			.option("--html", Config.outputDir().resolve("test-reports", "coverage").toString())
			.option("--xml", Config.outputDir().resolve("test-reports", "coverage.xml").toString())
			.get();

		executor.executeBlocking(command);
	}

	private void exploded() {
		Zip.unzip(
			Config.outputDir().resolve(Config.outputJavaUberJarName()),
			Config.outputDir().resolve("exploded")
		);
	}

	private void testCompile() {
		var testPath = Config.testDir();
		if (!Files.exists(testPath)) {
			return;
		}

		copyResources(testPath, Config.outputTestClassesDir());

		var command = new ArrayList<String>();
		command.add(JdkResolver.javac().toString());
		command.add("--source-path");
		command.add(testPath.toString());
		if (Config.getRelease() != 0) {
			command.add("--release");
			command.add(String.valueOf(Config.getRelease()));
		}
		if (Config.isPreviewEnabled()) {
			command.add("--enable-preview");
		}

		if (Files.exists(Path.of(".dep.testcomp")) && !ignoreDepfiles) {
			command.add("@.dep.testcomp");
		} else {
			var compileTestDeps = mavenDeps().add(Scope.COMPILE, Scope.PROVIDED, Scope.TEST)
				.add(ConfDependency.parse("!org.junit.jupiter:junit-jupiter-api:" + junitVersion()))
				.add(ConfDependency.parse("!org.junit.jupiter:junit-jupiter-params:" + junitVersion()))
				.classpath()
				.add(Config.outputClassesDir());

			command.add("-cp");
			command.add(compileTestDeps.toString());

			var processors = mavenDeps().add(Scope.PROCESSOR).classpath().toString();
			if (!processors.isBlank()) {
				command.add("--processor-path");
				command.add(processors);
				command.add("-s");
				command.add(Config.outputTestGeneratedDir().toString());
			}

			command.add("-d");
			command.add(Config.outputTestClassesDir().toString());
		}

		try (var files = Paths.allTestFiles()) {
			files.map(Path::toString).forEach(command::add);
		}

		var res = executor.executeBlocking(command);
		if (res != 0) {
			System.exit(res);
		}
	}

	private void unitTestRun() {
		var command = testCommand();
		command.add("--reports-dir");
		command.add(Config.outputDir().resolve("test-reports", "junit-unit").toString());
		if (doUnitQuick) {
			command.add("--exclude-tag");
			command.add("slow");
		}

		var res = executor.executeBlocking(command);
		if (res != 0) {
			System.exit(res);
		}
	}

	private void integrationTestRun() {
		var command = testCommand();
		command.add("--include-classname");
		command.add(".*IT$");
		command.add("--reports-dir");
		command.add(Config.outputDir().resolve("test-reports", "junit-integration").toString());
		if (doIntegrationQuick) {
			command.add("--exclude-tag");
			command.add("slow");
		}

		var res = executor.executeBlocking(command);
		if (res != 0) {
			System.exit(res);
		}
	}

	private List<String> testCommand() {
		var command = new ArrayList<String>();
		command.add(JdkResolver.java().toString());
		if (doCover) {
			var agent = DependencyResolution
				.getArtifact(new Gav("org.jacoco:org.jacoco.agent:" + jacocoVersion()), "runtime")
				.getFile()
				.getAbsolutePath();
			command.add(
				"-javaagent:%s=destfile=%s"
					.formatted(agent, Config.outputDir().resolve("test-reports", "jacoco.exec"))
			);
		}
		if (Config.isPreviewEnabled()) {
			command.add("--enable-preview");
		}

		if (Files.exists(Path.of(".dep.test")) && !ignoreDepfiles) {
			command.add("@.dep.test");
		} else {
			var classpath = mavenDeps().add(Scope.COMPILE, Scope.RUNTIME, Scope.TEST)
				.add(ConfDependency.parse("!org.junit.platform:junit-platform-console:" + junitVersion()))
				.add(ConfDependency.parse("!org.junit.jupiter:junit-jupiter-engine:" + junitVersion()))
				.add(ConfDependency.parse("!org.junit.jupiter:junit-jupiter-params:" + junitVersion()))
				.classpath()
				.add(Config.outputClassesDir())
				.add(Config.outputTestClassesDir())
				.toString();

			command.add("-cp");
			command.add(classpath);
		}

		command.add("org.junit.platform.console.ConsoleLauncher");
		command.add("execute");
		command.add("--scan-class-path");
		command.add("--disable-banner");
		command.add("--fail-if-no-tests");
		if (runOnly) {
			command.add("--include-tag");
			command.add("only");
		}
		filterPatterns.forEach(pattern -> {
			command.add("--include-methodname");
			command.add(pattern);
		});
		command.add("--config=junit.platform.reporting.open.xml.enabled=true");
		command.add("--config=junit.jupiter.execution.parallel.enabled=true");
		command.add("--config=junit.jupiter.execution.parallel.mode.default=concurrent");

		return command;
	}

	/**
	 * Generate a minimal Docker file if necessary and build the docker image using the version
	 * specified in the config file.
	 */
	private void docker() {
		var dockerfilePath = Path.of("Dockerfile");
		var jarName = Config.outputJavaJarName();
		if (!Files.exists(dockerfilePath)) {
			try {
				Files.writeString(dockerfilePath, """
					FROM eclipse-temurin:%s-jdk AS jlink

					WORKDIR /app

					COPY ./target/libs ./libs
					COPY ./target/%2$s ./%2$s
					COPY ./target/%2$s.aot* ./%2$s.aot

					ENTRYPOINT ["./custom-java/bin/java", "-XX:AOTCache=./target/%2$s.aot", "-jar", "app.jar"]
					""".formatted(jarName));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		// materialize libs, because docker cannot copy from outside the build context
		var libs = Config.outputDir().resolve("libs");
		FilesUtil.materializeAllInside(libs);

		var command = new ArrayList<String>();
		command.add("docker");
		command.add("build");
		command.add("-t");
		var artifact = Objects.requireNonNullElse(Config.getArtifactId(), "veles-generated");
		command.add(artifact + ":latest");
		var version = Config.getVersion();
		if (version != null) {
			command.add("-t");
			command.add(artifact + ":" + version);
		}
		command.add(".");

		executor.executeBlocking(command);
	}

	/**
	 * Use this if your native executable has issues. This will run GraalVM JVM using
	 * `native-image-agent` with given arguments, which will merge the reachability metadata with
	 * existing metadata inside `META-INF/native-image/<artifactId>`.
	 *
	 * This metadata is then used by the `native-image` utility to generate the native executable.
	 */
	private void reach() {
		var reachabilityDir = Config.getArtifactId().isBlank()
			? "veles-generated"
			: Config.getArtifactId();
		var classpath = mavenDeps().add(Scope.COMPILE, Scope.PROVIDED, Scope.RUNTIME)
			.classpath()
			.add(Config.outputClassesDir())
			.toString();

		var traceCmd = new ArrayList<String>();
		traceCmd.add(JdkResolver.graalJava().toString());
		traceCmd.add(
			"-agentlib:native-image-agent=" + "config-merge-dir=" + Config.sourceDir(entrypoint)
				+ "/META-INF/native-image/" + reachabilityDir
		);
		traceCmd.add("-cp");
		traceCmd.add(classpath);
		traceCmd.add(Config.getEntrypoint(entrypoint).canonicalName());
		traceCmd.addAll(args);
		executor.executeBlocking(traceCmd);

		Paths.ensureDirExists(Config.outputClassesDir().resolve("META-INF", "native-image"));

		FilesUtil.copyDir(
			Config.sourceDir(entrypoint).resolve("META-INF", "native-image"),
			Config.outputClassesDir().resolve("META-INF", "native-image")
		);
	}

	/**
	 * Generates the native image. You need to have GraalVM installed via SDKMAN or set GRAALVM_HOME
	 * environment variable, that points to the the directory where GraalVM is installed.
	 */
	private void _native() {
		List<String> command = null;
		if (doUber) {
			command = List.of(
				JdkResolver.nativeImage().toString(),
				"-jar",
				Config.outputDir().resolve(Config.outputJavaUberJarName()).toString(),
				"-o",
				Config.outputDir().resolve(Config.outputNativeExecutableName()).toString()
			);
		} else {
			command = List.of(
				JdkResolver.nativeImage().toString(),
				"-cp",
				mavenDeps().add(Scope.COMPILE, Scope.PROVIDED, Scope.RUNTIME)
					.classpath()
					.add(Config.outputClassesDir())
					.toString(),
				"-o",
				Config.outputDir().resolve(Config.outputNativeExecutableName()).toString(),
				Config.getEntrypoint().canonicalName()
			);
		}

		executor.executeBlocking(command);
	}

	/**
	 * Zip the lean jar together with the `libs/` folder. Probably better to just use uber jar
	 * instead.
	 */
	private void zip() {
		try (var zip = new Zip(Config.outputDir().resolve("app.zip").toString())) {
			File appJar = Config.outputDir().resolve(Config.outputJavaJarName()).toFile();
			zip.add("app.jar", appJar);

			File libsDir = Config.outputDir().resolve("libs").toFile();
			if (!libsDir.exists() || !libsDir.isDirectory()) {
				return;
			}

			for (File libJar : libsDir.listFiles()) {
				if (!libJar.isFile()) {
					continue;
				}

				zip.add("libs/" + libJar.getName(), libJar);
			}
		}
	}

	/**
	 * Generate a Manifest.txt and use `jar` command to generate the lean jar file
	 */
	private void jar() {
		try {
			var entrypoint = Config.getEntrypoint();
			Files.writeString(
				Config.outputClassesDir().resolve("Manifest.txt"),
				String.format(
					"""
						Main-Class: %s
						Class-Path: %s
						""",
					entrypoint.canonicalName(),
					symlinkLibs(
						Config.outputDir().resolve("libs"),
						resolvePaths(Scope.COMPILE, Scope.RUNTIME)
					).collect(joining(" \n  "))
				)
			);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}

		var command = List.of(
			"jar",
			"-c",
			"-f",
			Config.outputDir().resolve(Config.outputJavaJarName()).toString(),
			"-m",
			Config.outputClassesDir().resolve("Manifest.txt").toString(),
			"-C",
			Config.outputClassesDir().toString(),
			"."
		);

		executor.executeBlocking(command);
	}

	private static void deleteDir(Path dest) {
		try (var files = Files.walk(dest)) {
			files.forEach((path) -> {
				try {
					Files.delete(path);
				} catch (IOException e) {
					new RuntimeException(e);
				}
			});
		} catch (IOException e) {
			new RuntimeException(e);
		}
	}

	private static void copyResources(Path source, Path target) {
		FilesUtil.copyNonBuild(source, target, path -> !path.toString().endsWith(".java"));
	}

	private void clean() {
		if (fullClean) {
			deleteDir(Config.outputDir());
		} else {
			deleteDir(Config.outputClassesDir());
			if (doUnit || doIntegration) {
				deleteDir(Config.outputTestClassesDir());
				deleteDir(Config.outputDir().resolve("test-reports"));
			}
		}
	}

	/**
	 * Clean the output directory and compile the source files using javac.
	 */
	private void compile() {
		copyResources(Config.sourceDir(entrypoint), Config.outputClassesDir());

		var command = new ArrayList<String>();
		command.add(JdkResolver.javac().toString());
		command.add("--source-path");
		command.add(Config.sourceDir(entrypoint).toString());
		if (Config.getRelease() != 0) {
			command.add("--release");
			command.add(String.valueOf(Config.getRelease()));
		}
		if (Config.isPreviewEnabled()) {
			command.add("--enable-preview");
		}

		if (Files.exists(Path.of(".dep.compile")) && !ignoreDepfiles) {
			command.add("@.dep.compile");
		} else {
			var classpath = mavenDeps().add(Scope.COMPILE, Scope.PROVIDED).classpath();
			if (classpath.hasDeps()) {
				command.add("-cp");
				command.add(classpath.toString());
			}

			var processors = mavenDeps().add(Scope.PROCESSOR).classpath();
			if (processors.hasDeps()) {
				command.add("--processor-path");
				command.add(processors.toString());
				command.add("-s");
				command.add(Config.outputGeneratedDir().toString());
			}

			command.add("-d");
			command.add(Config.outputClassesDir().toString());
		}

		Paths.allSourceFiles().map(Path::toString).forEach(command::add);

		executor.executeBlocking(command);
	}

	/**
	 * All the hard work has been done before, all we need to do is to just unzip each library .jar
	 * and zip it as our own.
	 */
	private void uber() {
		try (var zip = new Zip(Config.outputDir().resolve(Config.outputJavaUberJarName()).toString())) {
			zip.add("Manifest.txt", "Main-class: %s\n".formatted(Config.getEntrypoint().canonicalName()));
			zip.concat(Config.outputDir().resolve(Config.outputJavaJarName()));
			resolvePaths(Scope.COMPILE, Scope.RUNTIME).map(Path::of).forEach(zip::concat);
		}
	}

	private Stream<String> symlinkLibs(Path outputDir, Stream<String> libPaths) {
		Paths.ensureDirExists(outputDir);

		// Ew
		return libPaths.map((path) -> {
			var filename = Path.of(path).getFileName();
			try {
				var symlinkPath = outputDir.resolve(filename);
				Files.deleteIfExists(symlinkPath);
				Files.createSymbolicLink(symlinkPath, Path.of(path));
			} catch (IOException e) {
				throw new RuntimeException("Could not symlink libs", e);
			}

			return Path.of(outputDir.toString().substring(Config.outputDir().toString().length() + 1))
				.resolve(filename)
				.toString();
		});
	}

}
