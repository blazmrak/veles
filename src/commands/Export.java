package commands;

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import common.FilesUtil;
import common.Paths;
import config.Config;
import config.ConfigDoc.ConfDependency.Scope;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export the project to Maven")
public class Export implements Runnable {
	public void run() {
		Paths.ensureDirExists(Path.of("export"));

		generatePomXml();
		copySourceFiles();
		copyResourceFiles();
		copyTestFiles();
	}

	private void generatePomXml() {
		var pom = """
			  <?xml version="1.0" encoding="UTF-8"?>
			  <project xmlns="http://maven.apache.org/POM/4.0.0"
			     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
			     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
			    <modelVersion>4.0.0</modelVersion>
			""";
		var groupId = Config.getGroupId();
		if (groupId != null) {
			pom += "  <groupId>%s</groupId>\n".formatted(groupId);
		}
		var artifactId = Config.getArtifactId();
		if (artifactId != null) {
			pom += "  <artifactId>%s</artifactId>\n".formatted(artifactId);
		}
		var version = Config.getVersion();
		if (version != null) {
			pom += "  <version>%s</version>\n".formatted(version);
		}

		pom += "\n  <dependencies>\n";
		pom += Config.getAllDependencies()
			.filter(d -> d.scope != Scope.POM && d.scope != Scope.PROCESSOR)
			.map(d -> {
				return """
					    <dependency>
					      <groupId>%s</groupId>
					      <artifactId>%s</artifactId>
					      <version>%s</version>
					      <scope>%s</scope>
					    </dependency>
					""".formatted(
					d.gav.getGroupId(),
					d.gav.getArtifactId(),
					d.gav.getVersion(),
					d.scope.toString().toLowerCase()
				);
			})
			.collect(joining())
			.replace("     <scope>compile</scope>\n", "");
		pom += "  </dependencies>\n";
		pom += "</project>";

		try {
			Files.writeString(Path.of("export", "pom.xml"), pom);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void copySourceFiles() {
		var exportSourceDir = Path.of("export", "src", "main", "java");
		FilesUtil
			.copyDir(Config.sourceDir(), exportSourceDir, path -> path.toString().endsWith(".java"));
	}

	private void copyResourceFiles() {
		var exportSourceDir = Path.of("export", "src", "main", "resources");
		FilesUtil
			.copyDir(Config.sourceDir(), exportSourceDir, path -> !path.toString().endsWith(".java"));
	}

	private void copyTestFiles() {
	}
}
