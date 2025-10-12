package commands;

import static common.DependencyResolution.resolvePaths;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import common.Paths;
import config.Config;
import config.ConfigDoc.ConfDependency.Scope;
import mixins.CommandExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "run", description = "Run .java files directly leveraging JEP-458")
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

	@Parameters
	List<String> args = Collections.emptyList();

	public void run() {
		var command = new ArrayList<String>();
		command.add("java");
		var classpath = resolvePaths(Scope.COMPILE, Scope.RUNTIME, Scope.PROVIDED)
			.collect(Collectors.joining(":"));
		if (!classpath.isBlank()) {
			command.add("-cp");
			command.add(classpath);
		}
		if (Config.isPreviewEnabled()) {
			command.add("--enable-preview");
		}
		command.add(Config.getEntrypoint(this.entrypoint).filePath().toString());
		command.addAll(args);

		if (!watch) {
			executor.executeBlocking(command);
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
