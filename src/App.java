import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import commands.Compile;
import commands.Dep;
import commands.Dev;
import commands.Format;
import commands.Init;
import commands.Lsp;
import commands.Run;
import commands.Start;
import common.VersionProvider;
import picocli.AutoComplete.GenerateCompletion;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
	name = "veles",
	description = { "Simple JDK wrapper" },
	mixinStandardHelpOptions = true,
	versionProvider = VersionProvider.class,
	subcommands = { Init.class, Dev.class, Run.class, Compile.class, Start.class, Dep.class,
		Lsp.class, Format.class, GenerateCompletion.class }
)
public class App {
	public static void main(String[] args) {
		Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		logger.setLevel(Level.INFO);

		new CommandLine(new App()).execute(args);
	}
}
