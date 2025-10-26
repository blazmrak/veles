package common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import config.Config;

public class JdkResolver {
	private static final Path miseHome = Path.of(System.getProperty("user.home"))
		.resolve(".local", "share", "mise", "installs", "java");
	private static final Path miseHomeWindows = Path.of(System.getProperty("user.home"))
		.resolve("AppData", "Local", "mise", "installs", "java");
	private static final Path sdkmanHome = Path.of(System.getProperty("user.home"))
		.resolve(".sdkman", "candidates", "java");

	public static Path java() {
		var home = jdkHome();

		if (home != null) {
			return home.resolve("bin", "java");
		}
		return Path.of("java");
	}

	public static Path javac() {
		var home = jdkHome();

		if (home != null) {
			return home.resolve("bin", "javac");
		}
		return Path.of("javac");
	}

	public static Path nativeImage() {
		var home = graalvmHome();

		if (home != null) {
			return home.resolve("bin", "native-image");
		}
		return Path.of("native-image");
	}

	public static Path graalJava() {
		var home = graalvmHome();

		if (home != null) {
			return home.resolve("bin", "java");
		}
		return Path.of("java");
	}

	public static Path graalvmHome() {
		String homeEnv = System.getenv("GRAALVM_HOME");
		Path path;
		if (homeEnv != null && !homeEnv.isBlank()) {
			path = Path.of(homeEnv, "bin");
		} else {
			path = jdkHome(Jdk.parse(Config.graalVersion() + "-graal"));
		}

		if (Files.exists(path) && Files.isDirectory(path)) {
			return path.toAbsolutePath();
		}

		return Path.of("");
	}

	public static Path jdkHome() {
		var homePath = resolveSdkmanrc();
		if (homePath != null) {
			return homePath;
		}

		return jdkHome(Jdk.parse(Config.jdkVersion()));
	}

	private static Path jdkHome(Jdk jdk) {
		var javaHome = System.getenv("JAVA_HOME");
		if (jdk == null) {
			if (javaHome != null && !javaHome.isBlank()) {
				return Path.of(javaHome);
			} else {
				return null;
			}
		}

		if (Os.isMacos()) {
			var sdkPath = resolveSdkman(jdk);
			if (sdkPath != null) {
				return sdkPath;
			}
			var misePath = resolveMise(jdk);
			if (misePath != null) {
				return misePath;
			} else {
				throw new IllegalArgumentException("Jdk version " + jdk.toString() + " is not installed");
			}
		}

		if (Os.isLinux()) {
			var sdkPath = resolveSdkman(jdk);
			if (sdkPath != null) {
				return sdkPath;
			}
			var misePath = resolveMise(jdk);
			if (misePath != null) {
				return misePath;
			} else {
				throw new IllegalArgumentException("Jdk version " + jdk.toString() + " is not installed");
			}
		}

		if (Os.isWindows()) {
			var misePath = resolveMiseWindows(jdk);
			if (misePath != null) {
				return misePath;
			} else {
				throw new IllegalArgumentException("Jdk version " + jdk.toString() + " is not installed");
			}
		}

		if (javaHome != null && !javaHome.isBlank()) {
			return Path.of(javaHome);
		} else {
			return null;
		}
	}

	private static Path resolveSdkmanrc() {
		var rcPath = Path.of(".skdmanrc");
		if (!Files.exists(rcPath)) {
			return null;
		}

		try (var lines = Files.lines(rcPath)) {
			return lines.filter(l -> l.startsWith("java="))
				.map(l -> l.substring("java=".length() + 1).trim())
				.map(SdkmanId::jdkHome)
				.findAny()
				.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private static Path resolveMiseWindows(Jdk jdk) {
		var path = miseHomeWindows.resolve(jdk.toMise());
		if (!Files.exists(path)) {
			return null;
		}

		return path;
	}

	private static Path resolveMise(Jdk jdk) {
		var path = miseHome.resolve(jdk.toMise());
		if (!Files.exists(path)) {
			return null;
		}

		return path;
	}

	private static Path resolveSdkman(Jdk jdk) {
		var path = sdkmanHome.resolve(jdk.toSdkman());
		if (!Files.exists(path)) {
			return null;
		}

		return path;
	}

	public static enum Distro {
		openjdk,
		temurin,
		corretto,
		graal,
		unknown;

		public static Distro parse(String distro) {
			switch (distro) {
				case "tem":
				case "temurin":
				case "eclipse":
					return temurin;
				case "amzn":
				case "cor":
				case "corretto":
					return corretto;
				case "graal":
				case "graalvm":
					return graal;
				case "openjdk":
				case "oracle":
				case "open":
					return openjdk;
				default:
					return unknown;
			}
		}

		public String toMise() {
			switch (this) {
				case openjdk:
					return "oracle";
				case temurin:
					return "tem";
				case corretto:
					return "amzn";
				case graal:
					return "graal";
				default:
					return null;
			}
		}

		public String toSdkman() {
			switch (this) {
				case openjdk:
					return "oracle";
				case temurin:
					return "tem";
				case corretto:
					return "amzn";
				case graal:
					return "graal";
				default:
					return null;
			}
		}
	}

	public static class Jdk {
		public final String version;
		public final Distro distro;

		public Jdk(String version, Distro distro) {
			this.version = version;
			this.distro = distro;
		}

		public static Jdk parse(String id) {
			if (id == null) {
				return null;
			}

			var split = id.split("-");
			if (split.length != 2) {
				throw new IllegalArgumentException("Jdk version should be specified as <version>-<distro>");
			}
			var version = split[0].trim();
			var distro = Distro.parse(split[1]);
			if (distro == Distro.unknown) {
				version += "-" + split[1];
			}

			return new Jdk(version, distro);
		}

		public String toMise() {
			if (distro == Distro.unknown) {
				return version;
			}

			return version + "-" + distro.toMise();
		}

		public String toSdkman() {
			if (distro == Distro.unknown) {
				return version;
			}

			return version + "-" + distro.toSdkman();
		}

		@Override
		public String toString() {
			return version + "-" + distro;
		}
	}

	public static class SdkmanId {
		private static final Path sdkHome = Path
			.of(System.getProperty("user.home"), ".sdkman", "candidates", "java");

		public static Path jdkHome(String id) {
			return sdkHome.resolve(id);
		}
	}
}
