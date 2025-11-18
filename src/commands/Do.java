package commands;

import static common.DependencyResolution.mavenDeps;

import java.util.List;
import java.util.Map;

import common.CliCommand;
import config.Config;
import config.ConfigDoc.ConfDependency;
import config.ConfigDoc.Script;
import mixins.CommandExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "do")
public class Do implements Runnable {
	@Mixin
	CommandExecutor executor;

	@Parameters(arity = "1..*")
	List<String> args;

	public void run() {
		Map<String, Script> scripts = Config.getScripts();
		var scriptName = args.get(0);
		var script = scripts.get(scriptName);
		if (script != null) {
			if (script.args.get(0).startsWith("!")) {
				script.args.set(0, script.args.get(0).substring(1));
				executor.executeBlocking(script.args);
			} else {
				var command = new CliCommand.Java();
				var classpath = mavenDeps().add(new ConfDependency(script.args.get(0)))
					.add(script.dependencies)
					.classpath();
				command.classpath(classpath);
				command.args(script.args.subList(1, script.args.size()));
				command.args(args);

				executor.executeBlocking(command.get());
			}
		} else {
			var command = new CliCommand.Java();
			var classpath = mavenDeps().add(new ConfDependency(args.remove(0))).classpath();
			command.classpath(classpath);
			command.args(args);

			executor.executeBlocking(command.get());
		}
	}
}
