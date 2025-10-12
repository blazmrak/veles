package tui;

import static tui.Keys.Key.BACKSPACE;
import static tui.Keys.Key.ENTER;
import static tui.Keys.Key.ESC;

import java.util.List;
import java.util.function.Consumer;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;

public class Input extends Element {
	private StringBuilder value;
	private Consumer<Input> onChangeHandler;

	public Input(String id, Terminal terminal) {
		super(
			id,
			terminal,
			null,
			new Dimensions(terminal.getSize().getColumns(), 1),
			new Position(2, 0)
		);

		this.value = new StringBuilder();
		super.handle("nomatch", null, (reader) -> {
			value.append(reader.getLastBinding());
			cursor.x += reader.getLastBinding().length();
		});
		super.handle("enter", Keys.get(ENTER), (_) -> {
			if (onChangeHandler != null) {
				onChangeHandler.accept(this);
			}
		});
		super.handle("backspace", Keys.get(BACKSPACE), (_) -> {
			if (value.length() > 0) {
				value.deleteCharAt(value.length() - 1);
				cursor.x--;
			}
		});
		super.keys().bind("exit", Keys.get(ESC));
	}

	@Override
	protected List<AttributedString> lines() {
		return List.of(new AttributedString("> " + value.toString()));
	}

	public void onChange(Consumer<Input> onChangeHandler) {
		this.onChangeHandler = onChangeHandler;
	}

	public String value() {
		return this.value.toString();
	}
}
