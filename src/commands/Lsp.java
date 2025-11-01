package commands;

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import common.MavenPom;
import common.Paths;
import config.Config;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "lsp", description = { "Generate files for JdtLS language server" })
public class Lsp implements Runnable {
	private static final Path CORE_PREFS_PATH = Path.of(".settings", "org.eclipse.jdt.core.prefs");
	private static final Path FORMAT_XML_PATH = Path.of(".settings", "format.xml");

	@Option(names = { "-d", "--documentation" })
	boolean pullDocumentation;

	public void run() {
		updateEditorConfig();
		updateFormatterSettings();
		MavenPom.generatePomXml();
	}

	public Map<String, String> readFormatterSettings() {
		var settings = readFormatterXml();
		if (settings == null) {
			updateFormatterSettings();
			return readFormatterXml();
		} else {
			return readFormatterXml();
		}
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

	private void updateFormatterSettings() {
		Paths.ensureDirExists(Path.of(".settings"));
		updateFormatterXml();
		updateJdtlsPrefs();
	}

	private void updateFormatterXml() {
		var currentSettings = readFormatterXml();
		if (currentSettings == null) {
			currentSettings = parsePrefs(CORE_PREFS_PATH);
		}
		var defaultPrefs = defaultFormatterPrefs();

		currentSettings.forEach((k, v) -> {
			if (k.contains("formatter")) {
				defaultPrefs.put(k, v);
			}
		});

		defaultPrefs
			.put("org.eclipse.jdt.core.formatter.lineSplit", String.valueOf(Config.getLineWidth()));
		defaultPrefs.put(
			"org.eclipse.jdt.core.formatter.comment.line_length",
			String.valueOf(Config.getLineWidth())
		);
		defaultPrefs
			.put("org.eclipse.jdt.core.formatter.tabulation.char", Config.getIndent().toString());

		var format = """
			<?xml version="1.0" encoding="UTF-8"?>
			<profiles version="12">
				<profile kind="CodeFormatterProfile" name="Default" version="12">
			%s
				</profile>
			</profiles>
			""".formatted(
			defaultPrefs.entrySet()
				.stream()
				.map(s -> "\t\t<setting id=\"" + s.getKey() + "\" value=\"" + s.getValue() + "\"/>")
				.collect(joining("\n"))
		);

		try {
			Files.writeString(FORMAT_XML_PATH, format);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Map<String, String> readFormatterXml() {
		try {
			var dbFactory = DocumentBuilderFactory.newInstance();
			var dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(FORMAT_XML_PATH.toFile());
			doc.getDocumentElement().normalize();
			NodeList list = doc.getElementsByTagName("setting");
			var result = new HashMap<String, String>();
			for (var i = 0; i < list.getLength(); i++) {
				switch (list.item(i)) {
					case Element el -> {
						var id = el.getAttribute("id");
						var value = el.getAttribute("value");
						result.put(id, value);
					}
					default -> {
					}
				}
			}
			return result;
		} catch (SAXException | IOException | ParserConfigurationException e) {
			return null;
		}
	}

	private void updateJdtlsPrefs() {
		var currentPrefs = parsePrefs(CORE_PREFS_PATH);
		var defaultPrefs = defaultCorePrefs();

		currentPrefs.forEach((k, v) -> {
			defaultPrefs.put(k, v);
		});

		readFormatterXml().forEach((k, v) -> {
			defaultPrefs.put(k, v);
		});

		defaultPrefs.put(
			"org.eclipse.jdt.core.compiler.problem.enablePreviewFeatures",
			Config.isPreviewEnabled()
				? "enabled"
				: "disabled"
		);

		if (Config.getRelease() == 0) {
			defaultPrefs.remove("org.eclipse.jdt.core.compiler.source");
			defaultPrefs.remove("org.eclipse.jdt.core.compiler.target");
		}

		var newPrefs = defaultPrefs.entrySet()
			.stream()
			.map(s -> s.getKey() + "=" + s.getValue())
			.collect(Collectors.joining("\n"));

		write(CORE_PREFS_PATH, newPrefs);
	}

	private Map<String, String> defaultCorePrefs() {
		var newMap = new LinkedHashMap<String, String>();
		newMap.put("eclipse.preferences.version", "1");
		if (Config.getRelease() > 0) {
			newMap.put("org.eclipse.jdt.core.compiler.source", String.valueOf(Config.getRelease()));
			newMap.put("org.eclipse.jdt.core.compiler.target", String.valueOf(Config.getRelease()));
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

		return newMap;
	}

	private Map<String, String> defaultFormatterPrefs() {
		var newMap = new LinkedHashMap<String, String>();
		newMap.put("org.eclipse.jdt.core.formatter.lineSplit", String.valueOf(Config.getLineWidth()));
		newMap.put("org.eclipse.jdt.core.formatter.profile", "veles");
		newMap.put("org.eclipse.jdt.core.formatter.profile.version", "12");
		newMap.put("org.eclipse.jdt.core.formatter.use_on_save", "enabled");
		newMap.put("org.eclipse.jdt.core.formatter.tabulation.char", "tab");
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

		newMap.put(
			"org.eclipse.jdt.core.formatter.comment.line_length",
			String.valueOf(Config.getLineWidth())
		);
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
