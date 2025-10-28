package utils;

import static common.DependencyResolution.mavenDeps;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.ParameterDeclarations;

import common.CliCommand;
import common.Os;
import config.Config;
import config.ConfigDoc.ConfDependency.Scope;
import utils.Executor.ProcessSandbox;

public class ExecArgumentProvider implements ArgumentsProvider {
	private static final String runtimeClasspath = mavenDeps().add(Scope.COMPILE, Scope.RUNTIME)
		.classpath()
		.add(Config.outputClassesDir())
		.toString();

	@Override
	public Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters,
		ExtensionContext context) throws Exception {
		var list = new ArrayList<ProcessSandbox>();
		var isCI = "true".equals(System.getenv("CI"));

		if (isCI || Files.exists(Config.outputJavaJarPath())) {
			list.add(new ProcessSandbox("jar", new CliCommand.Java().jar(Config.outputJavaJarPath())));
		}

		if (isCI || Files.exists(Config.outputJavaUberJarPath())) {
			list
				.add(new ProcessSandbox("uber", new CliCommand.Java().jar(Config.outputJavaUberJarPath())));
		}

		var nativeName = Os.isWindows()
			? Config.outputNativeExecutableName() + ".exe"
			: Config.outputNativeExecutableName();
		var nativePath = Config.outputDir().resolve(nativeName);
		if (isCI || Files.exists(nativePath)) {
			list.add(new ProcessSandbox("native", new CliCommand(nativePath.toString())));
		}

		if (isCI || Files.exists(Config.outputClassesDir())) {
			list.add(
				new ProcessSandbox("classes", new CliCommand.Java().classpath(runtimeClasspath).args("App"))
			);
		}

		if (isCI || Files.exists(Config.outputExplodedDir())) {
			list.add(
				new ProcessSandbox(
					"exploded",
					new CliCommand.Java().classpath(Config.outputExplodedDir()).args("App")
				)
			);
		}

		return list.stream().map(Arguments::of);
	}

}
