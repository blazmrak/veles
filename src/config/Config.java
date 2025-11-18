package config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import common.Paths;
import config.ConfigDoc.ConfDependency;
import config.ConfigDoc.ConfDependency.Scope;
import config.ConfigDoc.Gav;
import config.ConfigDoc.Script;
import config.ConfigDoc.Settings.FormatIndent;
import config.ConfigDoc.Settings.Formatter;

public class Config {
	private static Pattern mainMethodPattern = Pattern
		.compile("^\\s*(?:public\\s+)?(?:static\\s+)?void\\s+main\\s*\\([^)]*\\)", Pattern.MULTILINE);
	private static Yaml yaml;
	private static ConfigDoc config;
	private static Path sourceDir;
	private static Path testDir = Path.of("test");
	private static Entrypoint entrypoint;
	private static Gav gav;

	static {
		DumperOptions representerOptions = new DumperOptions();
		representerOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		representerOptions.setPrettyFlow(true);
		representerOptions.setIndicatorIndent(2);
		representerOptions.setIndentWithIndicator(true);
		representerOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

		yaml = new Yaml(
			new Constructor(new LoaderOptions()),
			new VelesRepresenter(representerOptions),
			representerOptions
		);

		try {
			Map<String, Object> configYaml = yaml.load(Files.readString(Path.of("veles.yaml")));
			config = parse(configYaml);
		} catch (NoSuchFileException e) {
			config = defaultConfig();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static String junitVersion() {
		return config.settings.test.junitVersion;
	}

	public static String jacocoVersion() {
		return config.settings.test.jacocoVersion;
	}

	public static String graalVersion() {
		return config.settings._native.graalVersion;
	}

	public static String jdkVersion() {
		return config.settings.jdk;
	}

	public static Path sourceDir() {
		if (sourceDir == null) {
			sourceDir = Config.getEntrypoint(null).sourceDir();
			if (sourceDir.startsWith(Path.of("."))) {
				sourceDir = sourceDir.subpath(1, sourceDir.getNameCount());
			}
		}

		return sourceDir;
	}

	public static Path testDir() {
		return testDir;
	}

	public static Path sourceDir(String mainClass) {
		if (sourceDir == null) {
			sourceDir = Config.getEntrypoint(mainClass).sourceDir();
		}

		return sourceDir;
	}

	public static Path outputDir() {
		return Path.of("target");
	}

	public static Path outputTestClassesDir() {
		return outputDir().resolve("test-classes");
	}

	public static Path outputClassesDir() {
		return outputDir().resolve("classes");
	}

	public static Path outputGeneratedDir() {
		return outputDir().resolve("generated-sources", "annotations");
	}

	public static Path outputTestGeneratedDir() {
		return outputDir().resolve("test-generated-sources", "annotations");
	}

	public static Path outputExplodedDir() {
		return outputDir().resolve("exploded");
	}

	public static String outputNativeExecutableName() {
		return getArtifactId();
	}

	public static String outputJavaJarName() {
		return outputNativeExecutableName() + ".jar";
	}

	public static Path outputJavaJarPath() {
		return outputDir().resolve(outputJavaJarName());
	}

	public static String outputJavaUberJarName() {
		return outputNativeExecutableName() + "-uber.jar";
	}

	public static Path outputJavaUberJarPath() {
		return outputDir().resolve(outputJavaUberJarName());
	}

	public static boolean isPreviewEnabled() {
		return config.settings.compiler.enablePreview;
	}

	@SuppressWarnings("unchecked")
	public static void addDependency(ConfDependency dep) {
		updateConfig(config -> {
			config.putIfAbsent("dependencies", new ArrayList<String>());
			config.compute("dependencies", (_, deps) -> {
				if (deps instanceof List d) {
					// search if existing dependency can be updated
					for (int i = 0; i < d.size(); i++) {
						var coords = d.get(i);
						if (coords instanceof String c) {
							var confdep = ConfDependency.parse(c);
							if (dep.isSamePackage(confdep)) {
								d.set(i, dep.toString());
								return d;
							}
						}
					}

					// if not, add it to the end
					d.add(dep.toString());
					return d;
				} else {
					throw new RuntimeException(
						"Bad configuration: veles.yaml -> dependencies property should be a List"
					);
				}
			});
		});
	}

	private static Map<String, Object> readConfig() {
		Map<String, Object> config = new LinkedHashMap<>();
		try {
			config = yaml.load(Files.readString(Path.of("veles.yaml")));
		} catch (NoSuchFileException e) {
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return config;
	}

	private static void renderConfig(Map<String, Object> config) {
		String dump = yaml.dump(config)
			.replaceAll("(?m)(^\\w.*:)", "\n$1") // separate top level blocks with a new line
			.substring(1); // remove the first new line

		try {
			Files.writeString(Path.of("veles.yaml"), dump);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void updateConfig(Consumer<Map<String, Object>> visitor) {
		var config = readConfig();
		visitor.accept(config);
		renderConfig(config);
	}

	public static String getGroupId() {
		return getGav().getGroupId();
	}

	public static String getArtifactId() {
		return getGav().getArtifactId();
	}

	public static String getVersion() {
		return getGav().getVersion();
	}

	public static Gav getGav() {
		if (gav == null) {
			gav = config.artifact != null
				? new Gav(config.artifact)
				: new Gav(Path.of(".").toAbsolutePath().getParent().getFileName() + ":1.0");
		}

		return gav;
	}

	public static Formatter getFormatter() {
		return config.settings.format.formatter;
	}

	public record Entrypoint(String canonicalName, Path filePath) {
		public Path sourceDir() {
			var classPath = Path.of("", canonicalName.split("\\."));
			return filePath.subpath(0, filePath.getNameCount() - classPath.getNameCount());
		}
	}

	public static Entrypoint getEntrypoint() {
		return getEntrypoint(null);
	}

	public static Entrypoint getEntrypoint(String mainClass) {
		if (entrypoint == null) {
			entrypoint = searchEntrypoint(mainClass);
		}

		return entrypoint;
	}

	public static FormatIndent getIndent() {
		return config.settings.format.indent;
	}

	public static int getLineWidth() {
		return config.settings.format.lineWidth;
	}

	private static Entrypoint searchEntrypoint(String mainClass) {
		var name = mainClass != null
			? mainClass
			: config.settings.compiler.entrypoint;
		var classPath = name.contains(".")
			? name.split("\\.")
			: new String[] { name };
		classPath[classPath.length - 1] += ".java";
		var entrypointFilePath = Path.of("", classPath);

		var autodetectedSourceFile = Paths.allNonBuildVisible()
			.parallel()
			.filter(p -> p.endsWith(entrypointFilePath))
			.findAny();

		var filePath = autodetectedSourceFile.orElseGet(() -> {
			if (mainClass == null) {
				return findFileWithMain()
					.orElseThrow(() -> new RuntimeException("Couldn't find any file with a main method"));
			}

			throw new RuntimeException("Source file " + entrypointFilePath + " not found");
		});

		String firstLine = null;
		try (var file = new BufferedReader(new FileReader(filePath.toString()), 1024)) {
			firstLine = file.readLine();
		} catch (IOException e) {
			// noop
		}

		var fileName = filePath.getFileName().toString();
		var className = fileName.substring(0, fileName.length() - ".java".length());
		if (firstLine != null && firstLine.startsWith("package")) {
			var pkg = firstLine.trim().substring(8, firstLine.length() - 1);
			return new Entrypoint(pkg + "." + className, filePath);
		} else {
			return new Entrypoint(className, filePath);
		}
	}

	private static Optional<Path> findFileWithMain() {
		return Paths.allNonBuildVisible()
			.filter(p -> p.toString().endsWith(".java"))
			.parallel()
			.filter(p -> {
				try {
					var file = Files.readString(p);
					return mainMethodPattern.matcher(file).find();
				} catch (Exception e) {
					return false;
				}
			})
			.findAny();
	}

	public static int getRelease() {
		return config.settings.compiler.release;
	}

	public static Stream<ConfDependency> getRuntimeDependencies() {
		return config.dependencies.stream().filter(d -> d.scope == Scope.RUNTIME);
	}

	public static Stream<ConfDependency> getCompileDependencies() {
		return config.dependencies.stream().filter(d -> d.scope == Scope.COMPILE);
	}

	public static Stream<ConfDependency> getProvidedDependencies() {
		return config.dependencies.stream().filter(d -> d.scope == Scope.PROVIDED);
	}

	public static Stream<ConfDependency> getTestDependencies() {
		return config.dependencies.stream().filter(d -> d.scope == Scope.TEST);
	}

	public static Stream<ConfDependency> getAllDependencies() {
		return config.dependencies.stream();
	}

	private static ConfigDoc parse(Map<String, Object> yaml) {
		return ConfigDoc.parse(yaml);
	}

	private static ConfigDoc defaultConfig() {
		return new ConfigDoc();
	}

	public static Map<String, Script> getScripts() {
		return config.scripts;
	}

	private static class VelesRepresenter extends Representer {
		public VelesRepresenter(DumperOptions options) {
			super(options);

			this.representers.put(Formatter.class, data -> {
				Formatter f = (Formatter) data;
				Map<String, Object> map = new LinkedHashMap<>();
				map.put("type", f.getType());
				map.put("version", f.getGav().getVersion());
				return super.representMapping(Tag.MAP, map, options.getDefaultFlowStyle());
			});
		}
	}
}
