package commands;

import static org.jline.keymap.KeyMap.alt;
import static tui.Keys.Key.A;
import static tui.Keys.Key.C;
import static tui.Keys.Key.F;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import mixins.CommandExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tui.Container;
import tui.Hr;
import tui.Input;
import tui.Keys;
import tui.LogFeed;
import tui.Renderer;
import tui.Tabs;

@Command(name = "dev", description = "Compile and run in one step")
public class Dev implements Runnable {
	@Option(names = { "--quick", "-q" })
	boolean quick;

	@Option(names = { "--jar", "-j" })
	boolean doJar;

	@Mixin
	CommandExecutor executor;

	public void run() {
		executor.setQuiet(true);
		try {
			Terminal terminal = TerminalBuilder.builder().system(true).build();

			var renderer = new Renderer(terminal);
			var dev = new DevControls(terminal, executor, quick, doJar);

			renderer.addChild(dev);
			renderer.focus(dev.input);
			renderer.display();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class DevControls extends Container {
		private Input input;
		private Tabs tabs;
		private Process currentProcess;
		private ExecutorService logAppender;
		private ExecutorService runner;
		private CommandExecutor executor;
		private boolean quick;
		private boolean doJar;

		public DevControls(Terminal terminal, CommandExecutor executor, boolean quick, boolean doJar) {
			super(terminal);
			this.executor = executor;
			this.quick = quick;
			this.doJar = doJar;

			tabs = initTabs();
			tabs.move(0, 2);

			input = initInput(tabs);

			var hr = new Hr(terminal);
			hr.move(0, 1);

			addChild(input);
			addChild(hr);
			addChild(tabs);
		}

		private Tabs initTabs() {
			var tabs = new Tabs("tabs", terminal);

			return tabs;
		}

		private Input initInput(Tabs tabs) {
			var input = new Input(null, terminal);
			input.handle("prev", alt(Keys.get(A)), (_) -> {
				tabs.prev();
			});
			input.handle("next", alt(Keys.get(F)), (_) -> {
				tabs.next();
			});
			input.handle("close", alt(Keys.get(C)), (_) -> {
				tabs.removeTab();
				stopManagedProcess();
			});
			input.onChange(this.handleOnChange(tabs));

			return input;
		}

		private Consumer<Input> handleOnChange(Tabs tabs) {
			return (el) -> {
				var logFeed = new LogFeed(terminal);
				logFeed.resize(terminal.getWidth(), terminal.getHeight());
				tabs.addTab(logFeed);

				executor.beforeStart((builder) -> {
					stopManagedProcess();
					try {
						currentProcess = builder.start();
					} catch (IOException e) {
						logFeed.append(e.getMessage());
						throw new RuntimeException(e);
					}

					logAppender = Executors.newVirtualThreadPerTaskExecutor();
					logAppender
						.execute(new LogFeedWriter(tabs, logFeed, input, currentProcess.getInputStream()));
					logAppender
						.execute(new LogFeedWriter(tabs, logFeed, input, currentProcess.getErrorStream()));

					return currentProcess;
				});

				runWithArgs(el.value(), logFeed);
			};
		}

		private void runWithArgs(String args, LogFeed logFeed) {
			if (runner != null) {
				runner.shutdownNow();
			}
			runner = Executors.newSingleThreadExecutor();
			if (quick) {
				runner.submit(() -> {
					var run = new Run();
					run.executor = executor;
					run.args = List.of(args.split(" "));
					run.run();
				});
			} else {
				runner.submit(() -> {
					logFeed.append("--------------------");
					logFeed.append("Compiling...");
					logFeed.append("--------------------");
					logFeed.append("");

					var compile = new Compile();
					compile.executor = executor;
					compile.doJar = doJar;
					compile.run();

					logFeed.append("");
					logFeed.append("--------------------");
					logFeed.append("Starting...");
					logFeed.append("--------------------");
					logFeed.append("");

					var run = new Start();
					run.executor = executor;
					run.target.jar = doJar;
					run.args = List.of(args.split(" "));
					run.run();
				});
			}
		}

		private void stopManagedProcess() {
			if (currentProcess != null) {
				currentProcess.destroy();
				logAppender.shutdownNow();
				currentProcess = null;
				logAppender = null;
			}
		}

		private static class LogFeedWriter implements Runnable {
			private Tabs tabs;
			private LogFeed logFeed;
			private Input input;
			private InputStream inputStream;

			public LogFeedWriter(Tabs tabs, LogFeed logFeed, Input input, InputStream inputStream) {
				this.tabs = tabs;
				this.input = input;
				this.logFeed = logFeed;
				this.inputStream = inputStream;
			}

			public void run() {
				try (var reader = new InputStreamReader(inputStream)) {
					var output = new char[8192];
					var readBytes = 0;
					var leftover = new StringBuilder();
					while ((readBytes = reader.read(output, 0, output.length)) != -1) {
						leftover.append(output, 0, readBytes);
						int newLineIndex = 0;
						int oldNewLineIndex = 0;
						while ((newLineIndex = leftover.indexOf("\n", newLineIndex + 1)) != -1) {
							logFeed.append(leftover.substring(oldNewLineIndex, newLineIndex));
							oldNewLineIndex = newLineIndex;
						}
						leftover.delete(0, oldNewLineIndex + 1);

						if (tabs.selected() == logFeed) {
							logFeed.render();
							input.updateCursor();
						}
					}
				} catch (Exception e) {
				}
			}
		}
	}
}
