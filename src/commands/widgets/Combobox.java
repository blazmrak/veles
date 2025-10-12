package commands.widgets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp.Capability;

class Combobox<T> {
	private final Terminal terminal;
	private final BindingReader reader;
	private final KeyMap<String> keys = new KeyMap<>();
	private final Map<String, ComboboxHandler<T>> eventHandlers = new HashMap<>();

	private int selectedIndex = 0;
	private List<T> matches = new ArrayList<>();
	private StringBuilder qb = new StringBuilder();
	private Function<String, List<T>> filter;
	private Function<T, String> itemRenderer;
	private String prefix = "Search: ";

	Combobox(Terminal terminal) {
		this.terminal = terminal;
		reader = new BindingReader(terminal.reader());
		this.itemRenderer = T::toString;

		// Define key bindings
		keys.setNomatch("nomatch"); // Any printable char
		handle("up", "\033[A", this::selectionUp); // Arrow up
		handle("down", "\033[B", this::selectionDown); // Arrow down
		handle("backspace", "\177", this::delete);
		handle("enter", "\r", this::select);
		handle("nomatch", "\u0003", this::update); // Ctrl-C
	}

	public ComboboxResult<T> prompt() {
		this.matches = this.filter.apply(this.qb.toString());

		ComboboxResult<T> result = null;
		while (result == null) {
			this.render();
			result = this.waitInput();
		}

		return result;
	}

	public void handle(String eventName, String key, ComboboxHandler<T> handler) {
		this.keys.bind(eventName, key);
		this.eventHandlers.put(eventName, handler);
	}

	@SuppressWarnings("unchecked")
	private ComboboxResult<T> waitInput() {
		String op = reader.readBinding(keys);
		var handler = this.eventHandlers.get(op);

		if (handler == null) {
			return null;
		}

		ComboboxResult<T> selection = null;
		for (var action : handler.apply(new InputEvent<>(reader, qb, selectedIndex, matches))) {
			switch (action) {
				case UpdateInput u -> {
					this.qb = u.sb();
				}
				case @SuppressWarnings("rawtypes") ReturnSelection s -> {
					selection = new ComboboxResult<>(qb.toString(), (T) s.selected);
				}
				case SelectedIndex s -> {
					this.selectedIndex = s.selectedIndex();
				}
				case UpdateCompletions _ -> {
					this.matches = this.filter.apply(this.qb.toString());
				}
			}
		}

		return selection;
	}

	public void withPrefix(String prefix) {
		this.prefix = prefix;
	}

	public void filterCompletions(Function<String, List<T>> filter) {
		this.filter = filter;
	}

	public void renderItem(Function<T, String> itemRenderer) {
		this.itemRenderer = itemRenderer;
	}

	public void render() {
		terminal.puts(Capability.clear_screen);
		terminal.writer().println(this.prefix + qb);
		terminal.writer().println("----------------");
		for (int i = 0; i < matches.size(); i++) {
			if (i == selectedIndex) {
				terminal.writer().println("> " + itemRenderer.apply(matches.get(i)));
			} else {
				terminal.writer().println("   " + itemRenderer.apply(matches.get(i)));
			}
		}

		terminal.puts(Capability.cursor_address, 0, this.prefix.length() + qb.length());
		terminal.flush();
	}

	private List<ComboboxAction> selectionUp(InputEvent<T> e) {
		if (e.selectedIndex() > 0)
			return List.of(new SelectedIndex(e.selectedIndex() - 1));

		return Collections.emptyList();
	}

	private List<ComboboxAction> selectionDown(InputEvent<T> e) {
		if (e.selectedIndex() < e.matches().size() - 1)
			return List.of(new SelectedIndex(e.selectedIndex() + 1));

		return Collections.emptyList();
	}

	private List<ComboboxAction> update(InputEvent<T> e) {
		var ch = e.reader().getLastBinding().charAt(0);
		e.query().append(ch);

		return List.of(new UpdateInput(e.query()), new UpdateCompletions());
	}

	private List<ComboboxAction> delete(InputEvent<T> e) {
		if (e.query().length() > 0) {
			var newSb = e.query().deleteCharAt(e.query().length() - 1);
			return List.of(new UpdateInput(newSb), new UpdateCompletions());
		}

		return Collections.emptyList();
	}

	private List<ComboboxAction> select(InputEvent<T> e) {
		if (e.matches().size() > 0) {
			return List.of(new ReturnSelection<>(e.matches().get(e.selectedIndex())));
		}

		return Collections.emptyList();
	}

	public record ComboboxResult<T>(String query, T selection) {
	}

	public record InputEvent<T>(
		BindingReader reader, StringBuilder query, int selectedIndex, List<T> matches
	) {
	}

	public sealed interface ComboboxAction permits SelectedIndex, UpdateCompletions, ReturnSelection, UpdateInput {
	}

	public record SelectedIndex(int selectedIndex) implements ComboboxAction {
	}

	public record UpdateCompletions() implements ComboboxAction {
	}

	public record ReturnSelection<T>(T selected) implements ComboboxAction {
	}

	public record UpdateInput(StringBuilder sb) implements ComboboxAction {
	}

	public interface ComboboxHandler<T> extends Function<InputEvent<T>, List<ComboboxAction>> {
	}
}
