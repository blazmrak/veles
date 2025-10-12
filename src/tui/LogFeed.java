package tui;

import java.util.ArrayList;
import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;

public class LogFeed extends Element {
	private List<String> logs = new ArrayList<>();
	private int maxSize = 20;

	public LogFeed(Terminal terminal) {
		this(null, terminal);
	}

	public LogFeed(String id, Terminal terminal) {
		super(id, terminal);
	}

	public void append(String logLine) {
		if (logs.size() > maxSize) {
			logs.remove(0);
		}

		logs.add(logLine);
	}

	@Override
	protected List<AttributedString> lines() {
		return logs.subList(Math.max(0, logs.size() - dimensions().height), logs.size())
			.stream()
			.map(AttributedString::fromAnsi)
			.toList();
	}
}
