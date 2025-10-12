package tui;

import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;

public class Hr extends Element {
	private int width;

	public Hr(Terminal terminal) {
		super(null, terminal, null, new Dimensions(terminal.getWidth(), 1), null);
		this.width = terminal.getWidth();
	}

	@Override
	protected List<AttributedString> lines() {
		return List.of(new AttributedString("-".repeat(width)));
	}
}
