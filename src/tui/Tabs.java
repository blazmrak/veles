package tui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;

public class Tabs extends Element {
	private final List<Element> tabs = new ArrayList<>();
	private int selectedTab = -1;
	private int maxTabs = 5;

	public Tabs(String id, Terminal terminal) {
		super(id, terminal);
	}

	@Override
	protected List<AttributedString> lines() {
		return List.of(
			new AttributedString(
				IntStream.range(0, tabs.size())
					.mapToObj(
						i -> i == selectedTab
							? "** tab " + i + " **"
							: "tab " + i
					)
					.collect(Collectors.joining(" | "))
			)
		);
	}

	public void addTab(Element element) {
		if (tabs.size() > maxTabs) {
			removeTab(0);
		}

		element.move(position().x, position().y + 1);
		tabs.add(element);
		selectTab(tabs.size() - 1);
	}

	public <T extends Element> T selected(Class<T> clazz) {
		return clazz.cast(selected());
	}

	public Element selected() {
		return tabs.get(selectedTab);
	}

	public void removeTab() {
		removeTab(selectedTab);
	}

	public void removeTab(int index) {
		if (tabs.size() <= 1 || index > tabs.size() || index < 0) {
			return;
		}

		tabs.remove(index);
		if (index <= selectedTab) {
			prev();
		}
	}

	public void prev() {
		if (selectedTab > 0) {
			selectedTab--;
			replaceChild();
		}
	}

	public void next() {
		if (selectedTab < tabs.size() - 1) {
			selectedTab++;
			replaceChild();
		}
	}

	public void selectTab(int tab) {
		if (0 <= tab && tab < tabs.size()) {
			selectedTab = tab;
			replaceChild();
		}
	}

	private void replaceChild() {
		replaceChild(selectedTab);
	}

	private void replaceChild(int index) {
		if (children.size() == 0) {
			addChild(tabs.get(index));
		} else {
			children.set(0, tabs.get(index));
		}
	}
}
