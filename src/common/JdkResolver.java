package common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import config.Config;

public class JdkResolver {
	private static final Path miseHome = Path.of(System.getProperty("user.home"))
		.resolve(".local", "share", "mise", "installs", "java");
	private static final Path miseHomeWindows = Path.of(System.getProperty("user.home"))
		.resolve("AppData", "Local", "mise", "installs", "java");
	private static final Path sdkmanHome = Path.of(System.getProperty("user.home"))
		.resolve(".sdkman", "candidates", "java");

	private static Path jdkHome = null;
	private static Path java = null;
	private static Path javac = null;
	private static Path nativeImage = null;
	private static Path graalJava = null;
	private static Path graalHome = null;

	// TODO: When stable values drop, simplify this
	public static Path java() {
		if (java == null) {
			var home = jdkHome();
			if (home != null) {
				java = home.resolve("bin", "java");
			} else {
				java = Path.of("java");
			}
		}

		return java;
	}

	public static Path javac() {
		if (javac == null) {
			var home = jdkHome();

			if (home != null) {
				javac = home.resolve("bin", "javac");
			} else {
				javac = Path.of("javac");
			}
		}

		return javac;
	}

	public static Path nativeImage() {
		if (nativeImage == null) {
			var home = graalvmHome();

			if (home != null) {
				if (Os.isWindows()) {
					nativeImage = home.resolve("bin", "native-image.cmd");
				} else {
					nativeImage = home.resolve("bin", "native-image");
				}
			} else {
				nativeImage = Path.of("native-image");
			}
		}

		return nativeImage;
	}

	public static Path graalJava() {
		if (graalJava == null) {
			var home = graalvmHome();

			if (home != null) {
				graalJava = home.resolve("bin", "java");
			} else {
				graalJava = Path.of("java");
			}
		}

		return graalJava;
	}

	public static Path graalvmHome() {
		if (graalHome == null) {
			Path path;
			if (Config.graalVersion() != null) {
				path = jdkHome(new Jdk(Config.graalVersion(), Distro.graal));
			} else if (Config.jdkVersion() != null) {
				path = jdkHome(new Jdk(Config.jdkVersion(), Distro.graal));
			} else {
				return null;
			}

			if (Files.exists(path) && Files.isDirectory(path)) {
				graalHome = path.toAbsolutePath();
			} else {
				graalHome = Path.of("");
			}
		}

		return graalHome;
	}

	public static Path jdkHome() {
		if (jdkHome == null) {
			var jdk = resolveRequiredJdkVersion();
			jdkHome = jdkHome(jdk);
		}

		return jdkHome;
	}

	private static Jdk resolveRequiredJdkVersion() {
		var jdk = resolveSdkmanrcVersion();
		if (jdk != null) {
			return jdk;
		}

		return Jdk.parse(Config.jdkVersion());
	}

	private static Path jdkHome(Jdk requiredJdk) {
		if (requiredJdk == null) {
			return javaHomeEnv();
		}

		if (Os.isMacos()) {
			return resolveJdk(
				requiredJdk,
				List.of(
					JdkResolver::resolveJavaHome,
					JdkResolver::resolveGraalvmHome,
					JdkResolver::resolveSdkman,
					JdkResolver::resolveMise
				)
			).orElseThrow(
				() -> new IllegalArgumentException(
					"Jdk version " + requiredJdk.toString() + " is not installed"
				)
			);
		}

		if (Os.isLinux()) {
			return resolveJdk(
				requiredJdk,
				List.of(
					JdkResolver::resolveJavaHome,
					JdkResolver::resolveGraalvmHome,
					JdkResolver::resolveSdkman,
					JdkResolver::resolveMise
				)
			).orElseThrow(
				() -> new IllegalArgumentException(
					"Jdk version " + requiredJdk.toString() + " is not installed"
				)
			);
		}

		if (Os.isWindows()) {
			return resolveJdk(
				requiredJdk,
				List.of(
					JdkResolver::resolveJavaHome,
					JdkResolver::resolveGraalvmHome,
					JdkResolver::resolveMiseWindows
				)
			).orElseThrow(
				() -> new IllegalArgumentException(
					"Jdk version " + requiredJdk.toString() + " is not installed"
				)
			);
		}

		return javaHomeEnv();
	}

	private static Path graalvmHomeEnv() {
		var javaHome = System.getenv("GRAALVM_HOME");
		if (javaHome != null && !javaHome.isBlank()) {
			return Path.of(javaHome);
		} else {
			return null;
		}
	}

	private static Path javaHomeEnv() {
		var javaHome = System.getenv("JAVA_HOME");
		if (javaHome != null && !javaHome.isBlank()) {
			return Path.of(javaHome);
		} else {
			return null;
		}
	}

	private static Jdk resolveSdkmanrcVersion() {
		var rcPath = Path.of(".skdmanrc");
		if (!Files.exists(rcPath)) {
			return null;
		}

		try (var lines = Files.lines(rcPath)) {
			return lines.filter(l -> l.startsWith("java="))
				.map(l -> l.substring("java=".length() + 1).trim())
				.map(Jdk::parse)
				.findAny()
				.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private static Optional<Path> resolveJdk(Jdk jdk, List<Function<Jdk, Optional<Path>>> resolvers) {
		return resolvers.stream().parallel().flatMap(r -> r.apply(jdk).stream()).findAny();
	}

	private static Optional<Path> resolveMiseWindows(Jdk jdk) {
		return findJdkFromDir(miseHomeWindows, jdk);
	}

	private static Optional<Path> resolveMise(Jdk jdk) {
		return findJdkFromDir(miseHome, jdk);
	}

	private static Optional<Path> resolveSdkman(Jdk jdk) {
		return findJdkFromDir(sdkmanHome, jdk);
	}

	private static Optional<Path> resolveJavaHome(Jdk jdk) {
		var homePath = javaHomeEnv();

		return jdk.isHomePath(homePath)
			? Optional.of(homePath)
			: Optional.empty();
	}

	private static Optional<Path> resolveGraalvmHome(Jdk jdk) {
		if (jdk.distro != Distro.graal) {
			return Optional.empty();
		}
		var homePath = graalvmHomeEnv();

		return jdk.isHomePath(homePath)
			? Optional.of(homePath)
			: Optional.empty();
	}

	private static Optional<Path> findJdkFromDir(Path installDir, Jdk jdk) {
		try (var content = Files.list(installDir)) {
			return content.parallel().filter(Files::isDirectory).filter(jdk::isHomePath).findAny();
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	public static enum Distro {
		openjdk,
		temurin,
		corretto,
		graal,
		zulu;

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
				case "zulu":
				case "azul":
					return zulu;
				default:
					throw new IllegalArgumentException(
						"Your distro is not recommended (check out https://whichjdk.com/), use one of "
							+ Arrays.toString(Distro.values())
					);
			}
		}

		public boolean isRelease(String release) {
			switch (this) {
				// make sure graal is above openjdk, because they have the same implementor value
				case graal:
					return release.contains("GRAALVM_VERSION");
				case openjdk:
					return release.contains("IMPLEMENTOR=\"Oracle Corporation\"");
				case temurin:
					return release.contains("Adoptium") || release.contains("Eclipse");
				case corretto:
					return release.contains("Corretto");
				case zulu:
					return release.contains("Azul");
			}

			return false;
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
			var version = split[0].trim();
			if (split.length == 1) {
				return new Jdk(version, null);
			}

			var distro = Distro.parse(split[1]);

			return new Jdk(version, distro);
		}

		public boolean isHomePath(Path path) {
			if (path == null) {
				return false;
			}

			try {
				var release = Files.readString(path.resolve("release"));
				var versionLine = "JAVA_VERSION=\"%s\"".formatted(version);
				return release.contains(versionLine) && (distro == null || distro.isRelease(release));
			} catch (IOException e) {
				return false;
			}
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
