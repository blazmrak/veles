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

/**
 * Provides executable CLI commands for each of the compiled artifacts or all possible compiled
 * artifacts if running with CI=true.
 */
public class ExecArgumentProvider implements ArgumentsProvider {
	private static final String runtimeClasspath = mavenDeps().add(Scope.COMPILE, Scope.RUNTIME)
		.classpath()
		.add(Config.outputClassesDir().toAbsolutePath())
		.toString();

	@Override
	public Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters,
		ExtensionContext context) throws Exception {
		var list = new ArrayList<ProcessSandbox>();
		var isCI = "true".equals(System.getenv("CI"));

		if (isCI || Files.exists(Config.outputJavaJarPath().toAbsolutePath())) {
			list.add(
				new ProcessSandbox(
					"jar",
					new CliCommand.Java().jar(Config.outputJavaJarPath().toAbsolutePath())
				)
			);
		}

		if (isCI || Files.exists(Config.outputJavaUberJarPath().toAbsolutePath())) {
			list.add(
				new ProcessSandbox(
					"uber",
					new CliCommand.Java().jar(Config.outputJavaUberJarPath().toAbsolutePath())
				)
			);
		}

		var nativeName = Os.isWindows()
			? Config.outputNativeExecutableName() + ".exe"
			: Config.outputNativeExecutableName();
		var nativePath = Config.outputDir().resolve(nativeName).toAbsolutePath();
		if (isCI || Files.exists(nativePath)) {
			list.add(new ProcessSandbox("native", new CliCommand(nativePath.toString())));
		}

		if (isCI || Files.exists(Config.outputClassesDir().toAbsolutePath())) {
			list.add(
				new ProcessSandbox("classes", new CliCommand.Java().classpath(runtimeClasspath).args("App"))
			);
		}

		if (isCI || Files.exists(Config.outputExplodedDir().toAbsolutePath())) {
			list.add(
				new ProcessSandbox(
					"exploded",
					new CliCommand.Java().classpath(Config.outputExplodedDir().toAbsolutePath()).args("App")
				)
			);
		}

		return list.stream().map(Arguments::of);
	}

}
