package common;

import java.io.File;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import config.Config;
import config.ConfigDoc.ConfDependency;
import config.ConfigDoc.ConfDependency.Scope;

public class MavenPom {

	public static void generatePomXml() {
		var factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
		var doc = builder.newDocument();
		var project = doc.createElement("project");
		doc.appendChild(project);
		project.setAttribute("xmlns", "http://maven.apache.org/POM/4.0.0");
		project.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		project.setAttribute(
			"xmlns:schemaLocation",
			"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
		);

		appendMetadata(doc, project);
		appendDependencyManagement(doc, project);
		appendDependencies(doc, project);
		var build = append(doc, project, "build");
		appendSources(doc, build);
		appendTestSources(doc, build);
		var plugins = append(doc, build, "plugins");
		appendCompilerPlugin(doc, plugins);
		appendSurefirePlugin(doc, plugins);
		appendJacocoPlugin(doc, plugins);
		appendEclipseFormatterPlugin(doc, plugins);

		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.transform(new DOMSource(doc), new StreamResult(new File("pom.xml")));
		} catch (TransformerException | TransformerFactoryConfigurationError e) {
			throw new RuntimeException(e);
		}
	}

	private static void appendJacocoPlugin(Document doc, Node plugins) {
		var plugin = append(doc, plugins, "plugin");
		appendGav(doc, plugin, "org.jacoco", "jacoco-maven-plugin", Config.jacocoVersion());
		var executions = append(doc, plugin, "executions");

		var execution1 = append(doc, executions, "execution");
		append(doc, execution1, "id", "prepare-agent");
		var goals1 = append(doc, execution1, "goals");
		append(doc, goals1, "goal", "prepare-agent");

		var execution2 = append(doc, executions, "execution");
		append(doc, execution2, "id", "report");
		append(doc, execution2, "phase", "verify");
		var goals2 = append(doc, execution2, "goals");
		append(doc, goals2, "goal", "report");
	}

	private static void appendDependencyManagement(Document doc, Element project) {
		var dependencyManagement = append(doc, project, "dependencyManagement");
		var dependencies = append(doc, dependencyManagement, "dependencies");
		Stream
			.concat(
				Config.getAllDependencies(),
				Stream.of(ConfDependency.parse("#org.junit:junit-bom:" + Config.junitVersion()))
			)
			.filter(d -> d.scope == Scope.POM)
			.forEach(d -> {
				var dep = append(doc, dependencies, "dependency");
				appendGav(
					doc,
					dep,
					d.gav.getGroupId(),
					d.gav.getArtifactId(),
					d.gav.getVersion(),
					"import",
					"pom"
				);
			});
	}

	private static void appendSurefirePlugin(Document doc, Node plugins) {
		var plugin = append(doc, plugins, "plugin");
		appendGav(doc, plugin, "org.apache.maven.plugins", "maven-surefire-plugin", null);
		var configuration = append(doc, plugin, "configuration");

		var includes = append(doc, configuration, "includes");
		append(doc, includes, "include", "**/*Test.java");
	}

	private static void appendEclipseFormatterPlugin(Document doc, Node plugins) {
		var plugin = append(doc, plugins, "plugin");
		appendGav(doc, plugin, "com.diffplug.spotless", "spotless-maven-plugin", "3.0.0");

		var configuration = append(doc, plugin, "configuration");
		var java = append(doc, configuration, "java");
		var eclipse = append(doc, java, "eclipse");
		append(doc, eclipse, "file", "${project.basedir}/.settings/format.xml");

		var executions = append(doc, plugin, "executions");
		var execution = append(doc, executions, "execution");
		var goals = append(doc, execution, "goals");
		append(doc, goals, "goal", "check");
		append(doc, goals, "goal", "apply");
	}

	private static Node append(Document doc, Node el, String name) {
		return el.appendChild(doc.createElement(name));
	}

	private static void append(Document doc, Node el, String name, String value) {
		append(doc, el, name).setTextContent(value);
	}

	private static void appendGav(Document doc, Node parent, String groupId, String artifactId,
		String version) {
		appendGav(doc, parent, groupId, artifactId, version, null, null);
	}

	private static void appendGav(Document doc, Node parent, String groupId, String artifactId,
		String version, String scope, String type) {
		if (groupId != null) {
			append(doc, parent, "groupId", groupId);
		}
		append(doc, parent, "artifactId", artifactId);
		if (version != null) {
			append(doc, parent, "version", version);
		}
		if (scope != null && !"compile".equals(scope)) {
			append(doc, parent, "scope", scope);
		}
		if (type != null) {
			append(doc, parent, "type", type);
		}
	}

	private static void appendCompilerPlugin(Document doc, Node plugins) {
		var plugin = append(doc, plugins, "plugin");
		appendGav(doc, plugin, "org.apache.maven.plugins", "maven-compiler-plugin", null);
		var configuration = append(doc, plugin, "configuration");
		if (Config.getRelease() > 0) {
			configuration.appendChild(doc.createElement("source"))
				.setTextContent(Config.getRelease() + "");
			configuration.appendChild(doc.createElement("target"))
				.setTextContent(Config.getRelease() + "");
		} else {
			append(doc, configuration, "source", "25");
			append(doc, configuration, "target", "25");
		}
		var processorPath = append(doc, configuration, "annotationProcessorPaths");
		Config.getAllDependencies().filter(d -> d.scope == Scope.PROCESSOR).forEach(d -> {
			appendGav(
				doc,
				processorPath.appendChild(doc.createElement("path")),
				d.gav.getGroupId(),
				d.gav.getArtifactId(),
				d.gav.getVersion()
			);
		});

		var includes = append(doc, configuration, "includes");
		append(doc, includes, "include", "**/*.java");
		var excludes = append(doc, configuration, "excludes");
		append(doc, excludes, "exclude", "**/*Test.java");
		append(doc, excludes, "exclude", "**/*IT.java");

		var executions = append(doc, plugin, "executions");
		var execution = append(doc, executions, "execution");
		append(doc, execution, "id", "compile-tests");
		append(doc, execution, "phase", "test-compile");
		execution.appendChild(doc.createElement("goals"))
			.appendChild(doc.createElement("goal"))
			.setTextContent("testCompile");
		var execConfiguration = append(doc, execution, "configuration");
		var execIncludes = append(doc, execConfiguration, "includes");
		append(doc, execIncludes, "include", "**/*Test.java");
		append(doc, execIncludes, "include", "**/*IT.java");
	}

	private static void appendTestSources(Document doc, Node build) {
		append(doc, build, "testSourceDirectory", Config.testDir().toString());
		var resources = append(doc, build, "testResources");
		var resource = append(doc, resources, "testResource");
		append(doc, resource, "directory", Config.testDir().toString());
		var excludes = append(doc, resource, "excludes");
		append(doc, excludes, "exclude", ".*");
		append(doc, excludes, "exclude", "**/*.java");
		append(doc, excludes, "exclude", "target/**");
		append(doc, excludes, "exclude", "pom.xml");
		append(doc, excludes, "exclude", "veles.yaml");
	}

	private static void appendSources(Document doc, Node build) {
		append(doc, build, "sourceDirectory", Config.sourceDir().toString());
		var resources = append(doc, build, "resources");
		var resource = append(doc, resources, "resource");
		append(doc, resource, "directory", Config.sourceDir().toString());
		var excludes = append(doc, resource, "excludes");
		append(doc, excludes, "exclude", ".*");
		append(doc, excludes, "exclude", "**/*.java");
		append(doc, excludes, "exclude", "target/**");
		append(doc, excludes, "exclude", "pom.xml");
		append(doc, excludes, "exclude", "veles.yaml");
	}

	private static void appendMetadata(Document doc, Element project) {
		append(doc, project, "modelVersion", "4.0.0");
		String groupId = Config.getGroupId();
		if (groupId == null) {
			groupId = "app." + Config.getArtifactId();
		}
		append(doc, project, "groupId", groupId);
		var artifactId = Config.getArtifactId();
		append(doc, project, "artifactId", artifactId);
		var version = Config.getVersion();
		append(doc, project, "version", version);
	}

	private static void appendDependencies(Document doc, Element project) {
		var dependencies = append(doc, project, "dependencies");
		Stream
			.concat(
				Config.getAllDependencies(),
				Stream.of(
					ConfDependency.parse("!org.junit.jupiter:junit-jupiter-api"),
					ConfDependency.parse("!org.junit.jupiter:junit-jupiter-params")
				)
			)
			.filter(d -> d.scope != Scope.POM && d.scope != Scope.PROCESSOR)
			.forEach(d -> {
				appendGav(
					doc,
					dependencies.appendChild(doc.createElement("dependency")),
					d.gav.getGroupId(),
					d.gav.getArtifactId(),
					d.gav.getVersion(),
					d.scope.toString().toLowerCase(),
					null
				);
			});
	}
}
