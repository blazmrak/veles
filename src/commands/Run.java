package commands;

import static common.DependencyResolution.mavenDeps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import common.JdkResolver;
import common.Paths;
import config.Config;
import config.ConfigDoc.ConfDependency.Scope;
import mixins.CommandExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
	name = "run",
	description = "Run .java files directly leveraging JEP-458",
	mixinStandardHelpOptions = true
)
public class Run implements Runnable {
	@Mixin
	CommandExecutor executor;

	@Option(
		names = { "-w", "--watch" },
		description = "Watch source files for changes and rerun the program"
	)
	boolean watch;

	@Option(
		names = { "-e", "--entrypoint" },
		description = "Class to use as an entrypoint to the program"
	)
	String entrypoint;

	/**
	 * Use this if your native executable has issues. This will run GraalVM JVM using
	 * `native-image-agent` with given arguments, which will merge the reachability metadata with
	 * existing metadata inside `META-INF/native-image/<artifactId>`.
	 *
	 * This metadata is then used by the `native-image` GraalVM utility to generate the native
	 * executable.
	 */
	@Option(
		names = { "-r", "--native-reach" },
		description = { "Use when native compilation is acting up" }
	)
	public boolean doReach;

	@Option(names = { "-X", "--ignore-depfiles" }, description = { "Ignore .dep files" })
	boolean ignoreDepfiles;

	@Parameters
	List<String> args = Collections.emptyList();

	public void run() {
		var entrypoint = Config.getEntrypoint(this.entrypoint);
		var command = new ArrayList<String>();
		if (doReach) {
			command.add(JdkResolver.graalJava().toString());
			command.add(
				"-agentlib:native-image-agent=" + "config-merge-dir="
					+ entrypoint.sourceDir().resolve("META-INF", "native-image", Config.getArtifactId())
			);
		} else {
			command.add(JdkResolver.java().toString());
		}
		if (Files.exists(Path.of(".dep.nocomp")) && !ignoreDepfiles) {
			command.add("@.dep.nocomp");
		} else {
			var classpath = mavenDeps().add(Scope.COMPILE, Scope.RUNTIME, Scope.PROVIDED)
				.classpath()
				.toString();
			if (!classpath.isBlank()) {
				command.add("-cp");
				command.add(classpath);
			}
		}
		if (Config.isPreviewEnabled()) {
			command.add("--enable-preview");
		}
		command.add(entrypoint.filePath().toString());
		command.addAll(args);

		if (!watch) {
			var code = executor.executeBlocking(command);
			if (code != 0) {
				System.exit(code);
			}
			return;
		}

		Paths.watch(Config.sourceDir(), new RunWatchHandler(executor, command));
	}

	public static class RunWatchHandler implements Consumer<List<WatchEvent<Path>>> {
		private final CommandExecutor executor;
		private final List<String> command;
		private Process managedProcess;

		public RunWatchHandler(CommandExecutor executor, List<String> command) {
			this.executor = executor;
			this.command = command;
			managedProcess = executor.execute(command);
		}

		@Override
		public void accept(List<WatchEvent<Path>> events) {
			if (managedProcess.isAlive()) {
				managedProcess.destroy();
			}

			managedProcess = executor.execute(command);
		}
	}
}
