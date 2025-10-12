package tui;

import java.util.List;

import org.jline.keymap.BindingReader;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;

public class Renderer extends Element {
	private BindingReader reader;
	private Element focusedEl;

	public Renderer(Terminal terminal) {
		super("root", terminal);
		this.reader = new BindingReader(terminal.reader());
	}

	public void display() {
		var attrs = terminal.getAttributes();
		try {
			terminal.enterRawMode();
			super.clear();

			while (true) {
				super.render();
				focusedEl.updateCursor();

				String op = reader.readBinding(focusedEl.keys());
				if ("exit".equals(op)) {
					break;
				}
				focusedEl.handleInput(op, reader);
			}
		} finally {
			terminal.setAttributes(attrs);
		}
	}

	@Override
	protected List<AttributedString> lines() {
		return null;
	}

	@Override
	public void addChild(Element element) {
		super.addChild(element);

		if (this.focusedEl == null && element.focusable()) {
			this.focusedEl = element;
		}
	}

	public void focus(Element el) {
		this.focusedEl = el;
	}
}
