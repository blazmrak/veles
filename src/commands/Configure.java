package commands;

import java.io.IOException;

import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;
import org.jline.terminal.Terminal;

import commands.widgets.ConfigAutocompleteWidget;
import picocli.CommandLine.Command;

@Command(name = "config", description = "Configure Veles")
public class Configure implements Runnable {
	public void run() {
		try {
			var terminal = TerminalBuilder.builder().system(true).build();
			var attrs = terminal.getAttributes();
			terminal.handle(Terminal.Signal.INT, _ -> {
				terminal.puts(Capability.clear_screen);
				terminal.setAttributes(attrs);
				terminal.writer().println("^C");
				terminal.flush();
				System.exit(130);
			});
			try {
				var result = new ConfigAutocompleteWidget(terminal).prompt();
				result.setting.update(result.value);
			} catch (Exception e) {
				terminal.setAttributes(attrs);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
