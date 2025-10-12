package tui;

import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;

public class Container extends Element {
	public Container(Terminal terminal) {
		this(null, terminal);
		super.focusable = false;
	}

	public Container(String id, Terminal terminal) {
		super(id, terminal);
	}

	@Override
	protected List<AttributedString> lines() {
		return null;
	}
}
