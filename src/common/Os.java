package common;

public class Os {
	public static String name() {
		return System.getProperty("os.name").toLowerCase();
	}

	public static String version() {
		return System.getProperty("os.version").toLowerCase();
	}

	public static String arch() {
		return System.getProperty("os.arch").toLowerCase();
	}

	public static boolean isWindows() {
		return name().contains("windows");
	}

	public static boolean isLinux() {
		return name().contains("linux");
	}

	public static boolean isMacos() {
		return name().contains("mac");
	}
}
