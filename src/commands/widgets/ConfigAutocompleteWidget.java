package commands.widgets;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jline.builtins.Completers.Completer;
import org.jline.reader.LineReader.Option;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp.Capability;

import commands.widgets.Fuzz.FuzzOptions;
import commands.widgets.Fuzz.SearchResult;
import config.Config;
import config.ConfigDoc.Gav;

public class ConfigAutocompleteWidget {
	private final Terminal terminal;
	private final List<Setting> settings;

	public ConfigAutocompleteWidget(Terminal terminal) {
		this.terminal = terminal;
		this.settings = initSettings();
	}

	private List<Setting> initSettings() {
		return List.of(
			new Setting(
				"artifact",
				() -> Config.getGav().toString(),
				(value) -> Config.updateConfig(config -> {
					config.put("artifact", new Gav(value).toString());
				}),
				"In form of [groupId:]<artifactId>:<version>",
				null
			)
		);
	}

	public ConfigValue prompt() {
		Setting setting;
		try {
			terminal.enterRawMode();

			var settingCombobox = new Combobox<SearchResult<Setting>>(terminal);
			settingCombobox.withPrefix("Setting: ");
			settingCombobox.filterCompletions(this::fuzzySearchOptions);
			settingCombobox.renderItem(r -> r.item().key + ": " + r.item().description);

			setting = settingCombobox.prompt().selection().item();
			terminal.puts(Capability.clear_screen);
			terminal.flush();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		try {
			var valueInput = LineReaderBuilder.builder()
				.terminal(terminal)
				.completer(setting.completer)
				.option(Option.DISABLE_EVENT_EXPANSION, true)
				.build();
			var value = valueInput
				.readLine("Setting: " + setting.key + " = ", null, (Character) null, setting.getValue());

			terminal.puts(Capability.clear_screen);
			terminal.flush();

			return new ConfigValue(setting, value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static class ConfigValue {
		public final Setting setting;
		public final String value;

		public ConfigValue(Setting setting, String value) {
			this.setting = setting;
			this.value = value;
		}
	}

	public static class Setting {
		public final String key;
		public final String description;
		private Supplier<String> defaultValueSupplier;
		private Consumer<String> valueUpdater;
		public final Completer completer;

		public Setting(String key, Supplier<String> defaultValueSupplier, Consumer<String> valueUpdater,
			String description, Completer completer) {
			this.key = key;
			this.defaultValueSupplier = defaultValueSupplier;
			this.valueUpdater = valueUpdater;
			this.description = description;
			this.completer = completer;
		}

		public String getValue() {
			return defaultValueSupplier.get();
		}

		public void update(String value) {
			this.valueUpdater.accept(value);
		}

	}

	private List<SearchResult<Setting>> fuzzySearchOptions(String query) {
		return Fuzz.search(
			settings,
			new FuzzOptions<Setting>().similarityCutoff(0).limit(20).scorer((setting) -> {
				if (query == null || query.isBlank())
					return new SearchResult<>(setting, SearchScorer.perfect(), 1, setting.key.length());
				var scorer = SearchScorer.calculate(query, setting.key);
				return new SearchResult<>(setting, scorer, query.length(), setting.key.length());
			})
		);
	}
}
