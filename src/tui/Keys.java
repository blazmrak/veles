package tui;

import java.util.HashMap;
import java.util.Map;

public class Keys {
	private static Map<Key, String> keys = new HashMap<>();

	static {
		keys.put(Key.BACKSPACE, "\177");
		keys.put(Key.ENTER, "\r");
		keys.put(Key.LEFT, "\r");
		keys.put(Key.RIGHT, "\r");
		keys.put(Key.UP, "\033[A");
		keys.put(Key.DOWN, "\033[B");
		keys.put(Key.A, "a");
		keys.put(Key.F, "f");
		keys.put(Key.C, "c");
		keys.put(Key.ESC, "\033");
	}

	public enum Key {
		BACKSPACE,
		ENTER,
		ESC,
		LEFT,
		RIGHT,
		UP,
		DOWN,
		A,
		F,
		C
	}

	public static String get(Key position) {
		return keys.get(position);
	}
}
