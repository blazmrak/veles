package commands;

import static common.DependencyResolution.mavenDeps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import common.JdkResolver;
import config.Config;
import config.ConfigDoc.ConfDependency.Scope;
import mixins.CommandExecutor;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start", description = "Run the compiled output")
public class Start implements Runnable {
	@Mixin
	CommandExecutor executor;

	@Option(names = { "-j", "--jar" }, description = { "Run compiled JAR file" })
	public boolean doJar;

	@Option(names = { "-n", "--native" }, description = { "Run compiled native executable" })
	public boolean doNative;

	@Option(names = { "-u", "--uber" }, description = { "Run compiled uber JAR" })
	public boolean doUber;

	@Option(names = { "-U", "--exploded" }, description = { "Run the exploded directory" })
	public boolean doExploded;

	@Option(names = { "-X", "--ignore-depfiles" }, description = { "Ignore .dep files" })
	boolean ignoreDepfiles;

	@ArgGroup(exclusive = true)
	public AOTGroup aot = new AOTGroup();

	public static class AOTGroup {
		@Option(
			names = { "-t", "--train" },
			description = { "Training run to generate AOT cache" },
			required = true
		)
		public boolean train;

		@Option(
			names = { "-a", "--aot" },
			description = { "Start with AOT" },
			arity = "0..1",
			required = true
		)
		public String useAotCache;
	}

	@Option(
		names = { "-e", "--entrypoint" },
		description = "Class to use as an entrypoint to the program"
	)
	public String entrypoint;

	@Parameters
	public List<String> args = Collections.emptyList();

	@Override
	public void run() {
		var entrypoint = Config.getEntrypoint(this.entrypoint);

		var command = new ArrayList<String>();
		if (!doNative) {
			command.add(JdkResolver.java().toString());
			if (Config.isPreviewEnabled()) {
				command.add("--enable-preview");
			}
			if (doJar || doUber) {
				var aotCacheOutput = Config.outputDir()
					.resolve(
						(doUber
							? Config.outputJavaUberJarName()
							: Config.outputJavaJarName()) + ".aot"
					);
				if (aot.train) {
					command.add("-XX:AOTCacheOutput=" + aotCacheOutput);
				}
				if (aot.useAotCache != null) {
					if (aot.useAotCache.isBlank()) {
						aot.useAotCache = aotCacheOutput.toString();
					}
					command.add("-XX:AOTCache=" + aot.useAotCache);
					command.add("-XX:AOTMode=on");
				}

				command.add("-jar");
				if (doJar) {
					command.add(Config.outputDir().resolve(Config.outputJavaJarName()).toString());
				} else {
					command.add(Config.outputDir().resolve(Config.outputJavaUberJarName()).toString());
				}
			} else {
				if (doExploded) {
					command.add("-cp");
					command.add(Config.outputExplodedDir().toString());
				} else if (Files.exists(Path.of(".dep.runtime")) && !ignoreDepfiles) {
					command.add("@.dep.runtime");
				} else {
					command.add("-cp");
					command.add(
						mavenDeps().add(Scope.COMPILE, Scope.RUNTIME)
							.classpath()
							.add(Config.outputClassesDir())
							.toString()
					);
				}
				command.add(entrypoint.canonicalName());
			}
		} else {
			command.add(Config.outputDir().resolve(Config.outputNativeExecutableName()).toString());
		}

		if (args != null) {
			command.addAll(args);
		}

		executor.executeBlocking(command);
	}
}
