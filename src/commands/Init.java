package commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import mixins.CommandExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "init", description = "Get off the ground faster")
public class Init implements Runnable {
	@Mixin
	CommandExecutor executor;

	public void run() {
		var gitPath = Path.of(".git");
		if (!Files.exists(gitPath) || !Files.isDirectory(gitPath)) {
			try {
				executor.executeBlocking(List.of("git", "init"));
				Files.writeString(Path.of(".gitignore"), """
					target
					""");
				executor.executeBlocking(List.of("git", "add", "."));
				executor.executeBlocking(List.of("git", "commit", "-m", "Initial commit"));
			} catch (Exception e) {
			}
		}

		var velesPath = Path.of("veles.yaml");
		if (Files.exists(velesPath)) {
			return;
		}
	}
}
