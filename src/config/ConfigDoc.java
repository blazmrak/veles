package config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.eclipse.aether.artifact.Artifact;

import config.ConfigDoc.ConfDependency.Scope;

public class ConfigDoc {
	public String artifact;
	public Settings settings = new Settings();
	public List<ConfDependency> dependencies = new ArrayList<>();

	public static ConfigDoc parse(Object obj) {
		var target = new ConfigDoc();
		if (obj != null && obj instanceof Map m) {
			var artifactVal = m.get("artifact");
			if (artifactVal != null && artifactVal instanceof String val) {
				target.artifact = val;
			}
			var settingsVal = m.get("settings");
			if (settingsVal != null && settingsVal instanceof Map val) {
				target.settings = Settings.parse(val);
			}
			var dependenciesVal = m.get("dependencies");
			if (dependenciesVal != null && dependenciesVal instanceof List val) {
				for (var i : val) {
					if (i instanceof String coords) {
						var dep = ConfDependency.parse(coords);
						if (dep == null) {
							continue;
						} else {
							target.dependencies.add(dep);
						}
					}
				}
			}
		}

		return target;
	}

	public String toString() {
		return "{" + "artifact: " + artifact + ", " + "settings: " + settings + ", " + "dependencies: "
			+ dependencies + "}";
	}

	public static class Settings {
		public String jdk;
		public Project project = new Project();
		public Compiler compiler = new Compiler();
		public Format format = new Format();
		public Native _native = new Native();

		public static Settings parse(Object obj) {
			var target = new Settings();
			if (obj != null && obj instanceof Map m) {
				var projectVal = m.get("project");
				if (projectVal != null && projectVal instanceof Map project) {
					target.project = Project.parse(project);
				}
				var compilerVal = m.get("compiler");
				if (compilerVal != null && compilerVal instanceof Map compiler) {
					target.compiler = Compiler.parse(compiler);
				}
				var nativeVal = m.get("native");
				if (nativeVal != null && nativeVal instanceof Map _native) {
					target._native = Native.parse(_native);
				}
				var formatVal = m.get("format");
				if (formatVal != null && formatVal instanceof Map format) {
					target.format = Format.parse(format);
				}
				var jdkVal = m.get("jdk");
				if (jdkVal != null && jdkVal instanceof String val) {
					target.jdk = val;
				} else if (jdkVal != null && jdkVal instanceof Number val) {
					target.jdk = val.toString();
				}
			}

			return target;
		}

		public String toString() {
			return "{" + "jdk: " + jdk + ", project: " + project + ", compiler: " + compiler + "}";
		}

		public static class Format {
			public Formatter formatter;
			public int lineWidth = 100;
			public FormatIndent indent = FormatIndent.tab;

			public static Format parse(Object obj) {
				var target = new Format();
				if (obj != null && obj instanceof Map m) {
					var formatterVal = m.get("formatter");
					if (formatterVal != null && formatterVal instanceof Map val) {
						target.formatter = Formatter.parse(val);
					}
					var lineWidthVal = m.get("lineWidth");
					if (lineWidthVal != null && lineWidthVal instanceof Integer val) {
						target.lineWidth = val;
					}
					var indentVal = m.get("indent");
					if (indentVal != null && indentVal instanceof String val) {
						target.indent = FormatIndent.valueOf(val);
					}
				}

				return target;
			}

			public String toString() {
				return "{" + "formatter: " + formatter + ", " + "lineWidth: " + lineWidth + "}";
			}
		}

		public enum FormatIndent {
			tab,
			space
		}

		public static class Project {
			public String src;
			public String test;

			public static Project parse(Object obj) {
				var target = new Project();
				if (obj != null && obj instanceof Map m) {
					var srcVal = m.get("src");
					if (srcVal != null && srcVal instanceof String val) {
						target.src = val;
					}
					var testVal = m.get("test");
					if (testVal != null && testVal instanceof String val) {
						target.test = val;
					}
				}

				return target;
			}

			@Override
			public String toString() {
				return "{" + "src: " + src + ", " + "test: " + test + "}";
			}
		}

		public static class Compiler {
			public int release;
			public String entrypoint = "App";
			public boolean enablePreview = false;

			public static Compiler parse(Object obj) {
				var target = new Compiler();
				if (obj != null && obj instanceof Map m) {
					var releaseVal = m.get("release");
					if (releaseVal != null && releaseVal instanceof Integer val) {
						target.release = val;
					}
					var entrypointVal = m.get("entrypoint");
					if (entrypointVal != null && entrypointVal instanceof String val) {
						target.entrypoint = val;
					}
					var enablePreviewVal = m.get("enablePreview");
					if (enablePreviewVal != null && enablePreviewVal instanceof Boolean val) {
						target.enablePreview = val;
					}
				}

				return target;
			}

			public String toString() {
				return "{release: " + release + ", entrypoint: " + entrypoint + ", enablePreview: "
					+ enablePreview + "}";
			}
		}

		public static class Native {
			public String graalVersion = "25";

			public static Native parse(Object obj) {
				var target = new Native();
				if (obj != null && obj instanceof Map m) {
					var graalVersionVal = m.get("graalVersion");
					if (graalVersionVal != null && graalVersionVal instanceof String val) {
						target.graalVersion = val;
					} else if (graalVersionVal != null && graalVersionVal instanceof Number val) {
						target.graalVersion = val.toString();
					}
				}

				return target;
			}

			public String toString() {
				return "{" + "graalVersion: " + graalVersion + "}";
			}
		}

		public static enum Formatter {
			ECLIPSE(new Gav("org.eclipse.jdt:org.eclipse.jdt.core:3.32.0")),
			PALANTIR(new Gav("com.palantir.javaformat:palantir-java-format-native:2.74.0"));

			private Gav coords;

			private Formatter(Gav coords) {
				this.coords = coords;
			}

			public static Formatter fromString(String type) {
				switch (type) {
					case "eclipse":
						return ECLIPSE;
					case "palantir":
						return PALANTIR;
				}

				throw new IllegalArgumentException(
					"Formatter definition should be one of: [eclipse, palantir] but was '" + type + "'"
				);
			}

			public static Formatter parse(Map<?, ?> map) {
				Formatter formatter = null;
				var type = map.get("type");
				if (type == null) {
					formatter = Formatter.PALANTIR;
				} else if (type instanceof String t) {
					formatter = Formatter.fromString(t);
				} else {
					throw new IllegalArgumentException("formatter type should be a string");
				}

				var version = map.get("version");
				if (version instanceof String s) {
					formatter.setVersion(s);
				}
				return formatter;
			}

			public void setVersion(String version) {
				this.coords = this.coords.withVersion(version);
			}

			public String getType() {
				switch (this) {
					case ECLIPSE:
						return "eclipse";
					case PALANTIR:
						return "palantir";
					default:
						throw new RuntimeException("Missing type");
				}
			}

			public Gav getGav() {
				return this.coords;
			}

			public String toString() {
				return this.coords.toString();
			}
		}
	}

	public static class ConfDependency {
		public Gav gav;
		public Scope scope;
		private String jarName;

		public ConfDependency(Gav gav, Scope scope) {
			this.gav = gav;
			this.scope = scope;
		}

		public static ConfDependency parse(String dep) {
			if (dep == null) {
				return null;
			}

			var scope = Scope.parse(dep);
			if (scope != Scope.COMPILE) {
				dep = dep.substring(1);
			}

			return new ConfDependency(new Gav(dep), scope);
		}

		public String jarName() {
			if (jarName == null) {
				jarName = gav.artifactId + ".jar";
			}

			return jarName;
		}

		public String jarPath() {
			return "libs/" + scope.toString().toLowerCase() + "/" + jarName();
		}

		public String coords() {
			return gav.toString();
		}

		public String scope() {
			if (scope == Scope.PROCESSOR) {
				return "provided";
			} else {
				return scope.toString().toLowerCase();
			}
		}

		@Override
		public String toString() {
			return scope.getPrefix() + gav.toString();
		}

		public boolean isSamePackage(ConfDependency other) {
			if (other == null) {
				return false;
			}

			if (other == this) {
				return true;
			}

			return Objects.equals(gav.groupId, other.gav.groupId)
				&& Objects.equals(gav.artifactId, other.gav.artifactId);
		}

		public static enum Scope {
			PROCESSOR("@"),
			COMPILE,
			POM("#"),
			RUNTIME("+"),
			TEST("!"),
			PROVIDED("-");

			private String prefix;

			private Scope() {
			}

			private Scope(String prefix) {
				this.prefix = prefix;
			}

			public static Scope parse(String coord) {
				if (coord.startsWith("@")) {
					return Scope.PROCESSOR;
				} else if (coord.startsWith("-")) {
					return Scope.PROVIDED;
				} else if (coord.startsWith("+")) {
					return Scope.RUNTIME;
				} else if (coord.startsWith("!")) {
					return Scope.TEST;
				} else if (coord.startsWith("#")) {
					return Scope.POM;
				} else {
					return Scope.COMPILE;
				}
			}

			public static List<Scope> all() {
				return List.of(COMPILE, TEST, PROCESSOR, POM, RUNTIME, PROVIDED);
			}

			public String getPrefix() {
				if (this.prefix == null) {
					return "";
				}

				return String.valueOf(this.prefix);
			}

		}
	}

	public static class Gav {
		private String groupId;
		private String artifactId;
		private String version;
		private ComparableVersion comparableVersion;
		private String coords;

		public Gav(String groupId, String artifactId, String version) {
			this.groupId = groupId;
			this.artifactId = artifactId;
			this.version = version;
			if (version != null) {
				this.comparableVersion = new ComparableVersion(version);
			}
		}

		public Gav(Artifact artifact) {
			this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
		}

		public Gav(String gav) {
			var gavSplit = gav.split(":");
			if (gavSplit.length == 2) {
				if (!gavSplit[0].isBlank()) {
					artifactId = gavSplit[0];
				}
				if (!gavSplit[1].isBlank()) {
					version = gavSplit[1];
				}
			} else if (gavSplit.length >= 3) {
				if (!gavSplit[0].isBlank()) {
					groupId = gavSplit[0];
				}
				if (!gavSplit[1].isBlank()) {
					artifactId = gavSplit[1];
				}
				if (!gavSplit[2].isBlank()) {
					version = gavSplit[2];
					comparableVersion = new ComparableVersion(version);
				}
			} else {
				throw new IllegalArgumentException("Invalid GAV: '" + gav + "'");
			}
		}

		public ConfDependency withScope(Scope scope) {
			return new ConfDependency(this, scope);
		}

		public Gav withVersion(String version) {
			return new Gav(groupId, artifactId, version);
		}

		public String getGroupId() {
			return groupId;
		}

		public String getArtifactId() {
			return artifactId;
		}

		public String getVersion() {
			return version;
		}

		public ComparableVersion comparableVersion() {
			return comparableVersion;
		}

		@Override
		public int hashCode() {
			return Objects.hash(groupId, artifactId, version);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}

			if (obj instanceof Gav other) {
				return Objects.equals(groupId, other.groupId)
					&& Objects.equals(artifactId, other.artifactId)
					&& Objects.equals(version, other.version);
			}

			return false;
		}

		@Override
		public String toString() {
			if (coords == null) {
				var sb = new StringBuilder();
				if (groupId != null)
					sb.append(groupId);

				if (artifactId != null) {
					if (sb.length() > 0) {
						sb.append(":");
					}
					sb.append(artifactId);
				}

				if (version != null) {
					if (sb.length() > 0) {
						sb.append(":");
					}
					sb.append(version);
				}
				coords = sb.toString();
			}

			return coords;
		}
	}
}
