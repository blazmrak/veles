package commands;

import static common.DependencyResolution.getDocumentation;
import static common.DependencyResolution.getSources;
import static common.DependencyResolution.resolve;
import static common.DependencyResolution.resolvePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.aether.artifact.Artifact;

import common.Paths;
import config.Config;
import config.ConfigDoc.ConfDependency.Scope;
import config.ConfigDoc.Gav;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "lsp", description = { "Generate files for JdtLS language server" })
public class Lsp implements Runnable {
	private static final Path APT_PREFS_PATH = Path.of(".settings", "org.eclipse.jdt.apt.core.prefs");
	private static final Path CORE_PREFS_PATH = Path.of(".settings", "org.eclipse.jdt.core.prefs");

	@Option(names = { "-d", "--documentation" })
	boolean pullDocumentation;

	public void run() {
		var entries = resolve(Scope.COMPILE, Scope.RUNTIME, Scope.PROVIDED).map(artifact -> {
			var gav = new Gav(artifact);
			Artifact sources = null;
			Artifact documentation = null;
			sources = getSources(gav, !pullDocumentation);
			documentation = getDocumentation(gav, !pullDocumentation);
			return new ClassPathEntry(artifact, sources, documentation);
		});
		updateProject();
		updateClassPath(entries);
		updateFactoryPath(resolvePaths(Scope.PROCESSOR));
		updateEditorConfig();

		configureSettings();
	}

	private void updateProject() {
		try {
			Files.writeString(Path.of(".project"), """
				<?xml version="1.0" encoding="UTF-8"?>
				<projectDescription>
				  <name>%s</name>
				  <comment></comment>
				  <projects>
				  </projects>
				  <buildSpec>
				    <buildCommand>
				      <name>org.eclipse.jdt.core.javabuilder</name>
				      <arguments>
				      </arguments>
				    </buildCommand>
				  </buildSpec>
				  <natures>
				    <nature>org.eclipse.jdt.core.javanature</nature>
				  </natures>
				  <filteredResources>
				    <filter>
				      <id>1756271453664</id>
				      <name></name>
				      <type>30</type>
				      <matcher>
				        <id>org.eclipse.core.resources.regexFilterMatcher</id>
				        <arguments>node_modules|\\.git|__CREATED_BY_JAVA_LANGUAGE_SERVER__</arguments>
				      </matcher>
				    </filter>
				  </filteredResources>
				</projectDescription>
				""".formatted(Config.getArtifactId()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void configureSettings() {
		Paths.ensureDirExists(Path.of(".settings"));
		updateCorePreferences();
		updateAptPrefs();
	}

	public Map<String, String> readFormatterSettings() {
		try {
			return readFormatterSettingsFromFile();
		} catch (Throwable e) {
			configureSettings();
			return readFormatterSettingsFromFile();
		}
	}

	private Map<String, String> readFormatterSettingsFromFile() {
		return parsePrefs(CORE_PREFS_PATH).entrySet()
			.stream()
			.filter(e -> !e.getKey().startsWith("#") && e.getKey().contains("formatter"))
			.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
	}

	private void updateEditorConfig() {
		if (!Files.exists(Path.of(".editorconfig"))) {
			write(Path.of(".editorconfig"), """
				root = true

				[*]
				charset = utf-8
				insert_final_newline = true
				end_of_line = lf
				indent_style = %s
				indent_size = 2
				max_line_length = %d

				[*.{yaml,yml}]
				indent_style = space
				indent_size = 2
				trim_trailing_whitespace = true
				insert_final_newline = true
				charset = utf-8

				[*.md]
				trim_trailing_whitespace = false
				indent_style = space
				indent_size = 2
				""".formatted(Config.getIndent(), Config.getLineWidth()));
		}
	}

	private void updateCorePreferences() {
		var currentPrefs = parsePrefs(CORE_PREFS_PATH);
		var defaultPrefs = defaultCorePrefs();

		currentPrefs.forEach((k, v) -> {
			defaultPrefs.put(k, v);
		});

		defaultPrefs.put("org.eclipse.jdt.core.compiler.release", String.valueOf(Config.getRelease()));
		defaultPrefs
			.put("org.eclipse.jdt.core.formatter.lineSplit", String.valueOf(Config.getLineWidth()));
		defaultPrefs.put(
			"org.eclipse.jdt.core.formatter.comment.line_length",
			String.valueOf(Config.getLineWidth())
		);
		defaultPrefs
			.put("org.eclipse.jdt.core.formatter.tabulation.char", Config.getIndent().toString());

		var newPrefs = defaultPrefs.entrySet()
			.stream()
			.map(s -> s.getKey() + "=" + s.getValue())
			.collect(Collectors.joining("\n"));

		write(CORE_PREFS_PATH, newPrefs);
	}

	private void updateAptPrefs() {
		var currentPrefs = parsePrefs(APT_PREFS_PATH);
		var defaultPrefs = new LinkedHashMap<String, String>();
		defaultPrefs.put("eclipse.preferences.version", "1");
		defaultPrefs.put("org.eclipse.jdt.apt.aptEnabled", "true");
		defaultPrefs.put("org.eclipse.jdt.apt.genSrcDir", "target/generated-sources/annotations");
		defaultPrefs
			.put("org.eclipse.jdt.apt.genTestSrcDir", "target/generated-test-sources/test-annotations");
		defaultPrefs.put("org.eclipse.jdt.apt.reconcileEnabled", "true");
		currentPrefs.forEach((k, v) -> {
			defaultPrefs.put(k, v);
		});

		var newPrefs = defaultPrefs.entrySet()
			.stream()
			.map(s -> s.getKey() + "=" + s.getValue())
			.collect(Collectors.joining("\n"));

		write(APT_PREFS_PATH, newPrefs);
	}

	private record ClassPathEntry(Artifact binary, Artifact sources, Artifact documentation) {
		public String xml() {
			if (sources == null) {
				return """
					\t<classpathentry kind="lib\" path=\"%s\"/>"""
					.formatted(binary.getFile().getAbsolutePath());
			} else if (documentation == null) {
				return new StringBuilder("""
					\t<classpathentry kind="lib" path="%s" sourcepath="%s"/>
					""".formatted(binary.getFile().getAbsolutePath(), sources.getFile().getAbsolutePath()))
					.toString();
			} else {

				return """
					\t<classpathentry kind="lib" path="%s" sourcepath="%s">
					\t\t<attributes>
					\t\t\t<attribute name="javadoc_location" value="%s:file:%s!/"/>
					\t\t</attributes>
					\t</classpathentry>
					""".formatted(
					binary.getFile().getAbsolutePath(),
					sources.getFile().getAbsolutePath(),
					documentation.getExtension(),
					documentation.getFile().getAbsolutePath()
				);
			}
		}
	}

	private void updateClassPath(Stream<ClassPathEntry> paths) {
		var classpaths = paths.map(ClassPathEntry::xml).collect(Collectors.joining(""));
		var classpathFile = String.format("""
			<classpath>
			  <classpathentry kind="src" path="%s"/>
			  <classpathentry kind="src" path="%s">
			    <attributes>
			      <attribute name="optional" value="true"/>
			    </attributes>
			  </classpathentry>
			  <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>

			%s

			  <classpathentry kind="output" path="%s"/>
			</classpath>
			""", Config.sourceDir(), Config.outputGeneratedDir(), classpaths, Config.outputClassesDir());

		write(Path.of(".classpath"), classpathFile);
	}

	private void updateFactoryPath(Stream<String> paths) {
		var factorypaths = paths.map(
			"""
				\t<factorypathentry kind="EXTJAR" id="%s" enabled="true" runInBatchMode="false"/>"""::formatted
		).collect(Collectors.joining("\n"));
		var factorypathFile = String.format("""
			<factorypath>
			%s
			</factorypath>
			""", factorypaths);

		write(Path.of(".factorypath"), factorypathFile);
	}

	private Map<String, String> defaultCorePrefs() {
		var newMap = new LinkedHashMap<String, String>();
		newMap.put("eclipse.preferences.version", "1");
		if (Config.getRelease() > 0) {
			newMap.put("org.eclipse.jdt.core.compiler.release", String.valueOf(Config.getRelease()));
		}
		newMap.put(
			"org.eclipse.jdt.core.compiler.problem.enablePreviewFeatures",
			Config.isPreviewEnabled()
				? "enabled"
				: "disabled"
		);
		newMap.put("org.eclipse.jdt.core.compiler.problem.forbiddenReference", "warning");
		newMap.put("org.eclipse.jdt.core.compiler.problem.reportPreviewFeatures", "ignore");
		newMap.put("org.eclipse.jdt.core.compiler.processAnnotations", "enabled");

		newMap.put("org.eclipse.jdt.core.formatter.profile", "veles");
		newMap.put("org.eclipse.jdt.core.formatter.profile.version", "12");
		newMap.put("org.eclipse.jdt.core.formatter.use_on_save", "enabled");
		newMap.put("org.eclipse.jdt.core.formatter.tabulation.size", "2");
		newMap.put("org.eclipse.jdt.core.formatter.indentation.size", "2");

		newMap.put("org.eclipse.jdt.core.formatter.continuation_indentation", "1");
		newMap
			.put("org.eclipse.jdt.core.formatter.continuation_indentation_for_array_initializer", "1");

		newMap.put("org.eclipse.jdt.core.formatter.brace_position_for_type_declaration", "end_of_line");
		newMap
			.put("org.eclipse.jdt.core.formatter.brace_position_for_method_declaration", "end_of_line");
		newMap.put(
			"org.eclipse.jdt.core.formatter.brace_position_for_constructor_declaration",
			"end_of_line"
		);
		newMap.put("org.eclipse.jdt.core.formatter.brace_position_for_block", "end_of_line");
		newMap.put(
			"org.eclipse.jdt.core.formatter.brace_position_for_anonymous_type_declaration",
			"end_of_line"
		);
		newMap.put("org.eclipse.jdt.core.formatter.brace_position_for_lambda_body", "end_of_line");

		newMap.put(
			"org.eclipse.jdt.core.formatter.parentheses_positions_in_method_declaration",
			"separate_lines_if_wrapped"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.parentheses_positions_in_method_invocation",
			"separate_lines_if_wrapped"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.parentheses_positions_in_record_declaration",
			"separate_lines_if_wrapped"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.parentheses_positions_in_lambda_declaration",
			"separate_lines_if_wrapped"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.parentheses_positions_in_annotation",
			"separate_lines_if_wrapped"
		);

		newMap.put("org.eclipse.jdt.core.formatter.alignment_for_arguments_in_annotation", "48");
		newMap.put("org.eclipse.jdt.core.formatter.alignment_for_arguments_in_method_invocation", "48");
		newMap
			.put("org.eclipse.jdt.core.formatter.alignment_for_arguments_in_allocation_expression", "48");
		newMap.put(
			"org.eclipse.jdt.core.formatter.alignment_for_arguments_in_explicit_constructor_call",
			"48"
		);
		newMap.put("org.eclipse.jdt.core.formatter.alignment_for_selector_in_method_invocation", "80");
		newMap.put(
			"org.eclipse.jdt.core.formatter.alignment_for_permitted_types_in_type_declaration",
			"16"
		);
		newMap.put("org.eclipse.jdt.core.formatter.alignment_for_logical_operator", "48");
		newMap.put("org.eclipse.jdt.core.formatter.alignment_for_conditional_expression", "49");
		newMap.put("org.eclipse.jdt.core.formatter.alignment_for_conditional_expression_chain", "32");
		newMap.put("org.eclipse.jdt.core.formatter.alignment_for_enum_constants", "49");

		newMap.put("org.eclipse.jdt.core.formatter.join_wrapped_lines", "true");
		newMap.put("org.eclipse.jdt.core.formatter.join_lines_in_comments", "true");

		newMap.put("org.eclipse.jdt.core.formatter.blank_lines_before_package", "0");
		newMap.put("org.eclipse.jdt.core.formatter.blank_lines_after_package", "1");
		newMap.put("org.eclipse.jdt.core.formatter.blank_lines_before_imports", "1");
		newMap.put("org.eclipse.jdt.core.formatter.blank_lines_after_imports", "1");
		newMap.put("org.eclipse.jdt.core.formatter.blank_lines_before_class", "1");
		newMap.put("org.eclipse.jdt.core.formatter.blank_lines_before_method", "1");
		newMap.put("org.eclipse.jdt.core.formatter.blank_lines_before_field", "0");

		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_before_opening_paren_in_method_invocation",
			"do not insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_before_opening_paren_in_method_declaration",
			"do not insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_before_opening_brace_in_type_declaration",
			"insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_before_opening_brace_in_array_initializer",
			"insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_after_opening_brace_in_array_initializer",
			"insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_before_opening_brace_in_method_declaration",
			"insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_before_comma_in_method_invocation_arguments",
			"do not insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_before_opening_paren_in_annotation",
			"do not insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_before_closing_brace_in_array_initializer",
			"insert"
		);

		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_after_comma_in_method_invocation_arguments",
			"insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_after_comma_in_method_declaration_parameters",
			"insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_after_comma_in_array_initializer",
			"insert"
		);
		newMap.put(
			"org.eclipse.jdt.core.formatter.insert_space_after_comma_in_allocation_expression",
			"insert"
		);
		newMap
			.put("org.eclipse.jdt.core.formatter.insert_space_after_at_in_annotation", "do not insert");
		newMap.put("org.eclipse.jdt.core.formatter.insert_space_after_comma_in_annotation", "insert");
		newMap.put("org.eclipse.jdt.core.formatter.insert_space_before_colon_in_case", "do not insert");
		newMap
			.put("org.eclipse.jdt.core.formatter.insert_space_before_colon_in_default", "do not insert");

		newMap.put("org.eclipse.jdt.core.formatter.keep_guardian_clause_on_one_line", "true");

		newMap.put("org.eclipse.jdt.core.formatter.comment.format_line_comments", "true");
		newMap.put("org.eclipse.jdt.core.formatter.comment.format_block_comments", "true");
		newMap.put("org.eclipse.jdt.core.formatter.comment.format_javadoc_comments", "true");
		newMap.put("org.eclipse.jdt.core.formatter.comment.insert_new_line_for_parameter", "false");
		newMap
			.put("org.eclipse.jdt.core.formatter.comment.clear_blank_lines_in_javadoc_comment", "false");
		newMap.put("org.eclipse.jdt.core.formatter.comment.indent_parameter_description", "true");
		newMap.put("org.eclipse.jdt.core.formatter.comment.indent_root_tags", "true");

		return newMap;
	}

	private Map<String, String> parsePrefs(Path path) {
		var file = read(path);
		if (file == null) {
			return Map.of();
		}

		var map = new LinkedHashMap<String, String>();
		for (var line : file.split("\n")) {
			if (line.isBlank()) {
				continue;
			}

			var split = line.split("=");
			if (split.length < 2) {
				continue;
			}

			map.put(split[0], split[1]);
		}

		return map;
	}

	private void write(Path path, String file) {
		try {
			Files.writeString(path, file);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String read(Path path) {
		try {
			return Files.readString(path);
		} catch (Exception e) {
			return null;
		}
	}
}
