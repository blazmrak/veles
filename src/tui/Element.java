package tui;

import static java.util.Objects.requireNonNullElseGet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp.Capability;

public abstract class Element {
	private Position position;
	private Dimensions dimensions;
	private boolean focused;
	protected boolean focusable;

	protected Terminal terminal;
	protected Position cursor;
	private List<AttributedString> lines;
	protected List<Element> children = new ArrayList<>();

	protected String id;
	private KeyMap<String> keys = new KeyMap<String>();
	private Map<String, Consumer<BindingReader>> handlers = new HashMap<>();

	protected Element(String id, Terminal terminal) {
		this(id, terminal, null, null, null);
	}

	protected Element(String id, Terminal terminal, Position position, Dimensions dimensions,
		Position cursor) {
		this.id = id;
		this.terminal = terminal;
		this.position = requireNonNullElseGet(position, () -> new Position(0, 0));
		this.dimensions = requireNonNullElseGet(
			dimensions,
			() -> new Dimensions(terminal.getWidth(), terminal.getHeight())
		);
		this.cursor = requireNonNullElseGet(cursor, () -> new Position(0, 0));

		this.keys.setNomatch("nomatch");
	}

	public final void render() {
		render(true);
	}

	public final synchronized void render(boolean flush) {
		lines = lines();
		if (lines != null) {
			for (int i = 0; i < lines.size(); i++) {
				terminal.puts(Capability.cursor_address, position.y + i, position.x);
				var line = lines.get(i);
				if (line.length() > dimensions.width) {
					terminal.writer().write(line.substring(0, dimensions.width).toAnsi());
				} else {
					terminal.writer().write(line.toAnsi());
					terminal.writer().write(" ".repeat(dimensions.width - line.length()));
				}
			}

			for (int i = lines.size(); i < dimensions.height - lines.size(); i++) {
				terminal.puts(Capability.cursor_address, position.y + i, position.x);
				terminal.writer().write(" ".repeat(dimensions.width));
			}
		}

		for (var child : children) {
			child.render(false);
		}

		// terminal.puts(Capability.cursor_address, initialCursorPos.getY(),
		// initialCursorPos.getX());

		if (flush) {
			terminal.writer().flush();
		}
	}

	public final Optional<Element> find(String id) {
		if (Objects.equals(this.id, id)) {
			return Optional.of(this);
		}

		for (var child : children) {
			var found = child.find(id);
			if (found.isPresent()) {
				return found;
			}
		}

		return Optional.empty();
	}

	public final <T> Optional<T> find(String id, Class<T> clazz) {
		if (Objects.equals(this.id, id)) {
			if (clazz.isInstance(this)) {
				return Optional.of(clazz.cast(this));
			}
		}

		for (var child : children) {
			var found = child.find(id);
			if (found.isPresent() && clazz.isInstance(found.get())) {
				return Optional.of(clazz.cast(found.get()));
			}
		}

		return Optional.empty();
	}

	public final void updateCursor() {
		terminal.puts(
			Capability.cursor_address,
			Math.min(position.y + dimensions.height, position.y + cursor.y),
			Math.min(position.x + cursor.x, position.x + dimensions.width)
		);
		terminal.flush();
	}

	public final void handleInput(String op, BindingReader reader) {
		var handler = this.handlers.get(op);
		if (handler == null) {
			return;
		}

		handler.accept(reader);
	}

	public final void handle(String op, String key, Consumer<BindingReader> handler) {
		if (!"nomatch".equals(op)) {
			this.keys.bind(op, key);
		}

		this.handlers.put(op, handler);
	}

	public void focus() {
		this.focused = true;
	}

	public void addChild(Element element) {
		this.children.add(element);
	}

	public void removeChild(Element element) {
		this.children.remove(element);
	}

	public void move(int x, int y) {
		this.position = new Position(x, y);
	}

	public void resize(int width, int height) {
		this.dimensions = new Dimensions(width, height);
	}

	public boolean focusable() {
		return focusable;
	}

	public KeyMap<String> keys() {
		return this.keys;
	}

	public Dimensions dimensions() {
		return this.dimensions;
	}

	public Position position() {
		return this.position;
	}

	abstract protected List<AttributedString> lines();

	protected void clear() {
		for (int i = 0; i < dimensions.height; i++) {
			terminal.puts(Capability.cursor_address, position.y + i, position.x);
			terminal.writer().write(" ".repeat(dimensions.width));
		}

		terminal.writer().flush();
	}

	public static class Position {
		public int x;
		public int y;

		public Position() {
		}

		public Position(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	public static class Dimensions {
		public int width;
		public int height;

		public Dimensions() {
		}

		public Dimensions(int width, int height) {
			this.width = width;
			this.height = height;
		}
	}
}
