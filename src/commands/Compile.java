package commands;

import static common.DependencyResolution.resolvePaths;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import common.FilesUtil;
import common.Paths;
import common.Zip;
import config.Config;
import config.ConfigDoc.ConfDependency.Scope;
import mixins.CommandExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "compile", description = { "Compile .java files" })
public class Compile implements Runnable {
	@Mixin
	CommandExecutor executor;

	@Option(names = { "-C", "--skip-clean" }, description = { "Clean the classes output" })
	boolean skipClean;

	@Option(names = { "-S", "--skip-compile" }, description = { "Skip compilation step" })
	boolean skipCompile;

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
	}

	private void exploded() {
		Zip.unzip(
			Config.outputDir().resolve(Config.outputJavaUberJarName()),
			Config.outputDir().resolve("exploded")
		);
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
		try (var files = Files.walk(libs)) {
			files.forEach(f -> {
				if (Files.isSymbolicLink(f)) {
					try {
						var p = Files.readSymbolicLink(f);
						Files.copy(p, f, StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			});
		} catch (Exception e) {
			throw new RuntimeException("Failed to derefrence libs", e);
		}

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
		var classpath = resolvePaths(Scope.COMPILE, Scope.PROVIDED, Scope.RUNTIME).collect(joining(":"))
			+ ":" + Config.outputClassesDir();
		var traceCmd = new ArrayList<String>();
		traceCmd.add(getGraalBin() + "java");
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
				getGraalBin() + "native-image",
				"-jar",
				Config.outputDir().resolve(Config.outputJavaUberJarName()).toString(),
				"-o",
				Config.outputDir().resolve(Config.outputNativeExecutableName()).toString(),
				Config.getEntrypoint().canonicalName()
			);
		} else {
			command = List.of(
				getGraalBin() + "native-image",
				"-cp",
				resolvePaths(Scope.COMPILE, Scope.PROVIDED, Scope.RUNTIME).collect(joining(":")) + ":"
					+ Config.outputClassesDir(),
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
		deleteDir(Config.outputClassesDir());
	}

	/**
	 * Clean the output directory and compile the source files using javac.
	 */
	private void compile() {
		copyResources(Config.sourceDir(entrypoint), Config.outputClassesDir());

		var command = new ArrayList<String>();
		command.add("javac");
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
			var classpath = resolvePaths(Scope.COMPILE, Scope.PROVIDED).collect(joining(":"));
			if (!classpath.isBlank()) {
				command.add("-cp");
				command.add(classpath);
			}

			var processors = resolvePaths(Scope.PROCESSOR).collect(joining(":"));
			if (!processors.isBlank()) {
				command.add("--processor-path");
				command.add(processors);
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

	private String getGraalBin() {
		String homeEnv = System.getenv("GRAALVM_HOME");
		Path path;
		if (homeEnv != null) {
			path = Path.of(homeEnv, "bin");
		} else {
			path = Path.of(
				System.getenv("HOME"),
				".sdkman",
				"candidates",
				"java",
				Config.graalVersion() + "-graal",
				"bin"
			);
		}

		if (Files.exists(path) && Files.isDirectory(path)) {
			return path.toAbsolutePath().toString() + "/";
		}

		return "";
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
