package common;

public class Os {
	private static final String os = System.getProperty("os.name").toLowerCase();
	public static final String version = System.getProperty("os.version").toLowerCase();
	public static final String arch = System.getProperty("os.arch").toLowerCase();

	public static boolean isWindows() {
		return os.contains("windows");
	}

	public static boolean isLinux() {
		return os.contains("linux");
	}

	public static boolean isMacos() {
		return os.contains("mac");
	}
}
