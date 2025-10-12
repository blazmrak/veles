package commands;

import java.io.IOException;
import java.util.List;

import org.jline.terminal.TerminalBuilder;

import commands.widgets.MavenSearchWidget;
import config.Config;
import config.ConfigDoc.ConfDependency;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "dep")
public class Dep implements Runnable {

	@Parameters
	List<String> args;

	public void run() {
		try {
			var terminal = TerminalBuilder.builder().build();
			var gav = new MavenSearchWidget(terminal).search();
			Config.addDependency(ConfDependency.parse(gav.toString()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
