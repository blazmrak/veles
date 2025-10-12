package common;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

import config.ConfigDoc.Gav;

public class LocalMavenRepository {

	public static Set<Gav> packages() {
		var packages = new HashSet<Gav>();
		try {
			var localMaven = Path.of(System.getProperty("user.home"), ".m2", "repository");
			Files.walkFileTree(localMaven, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
					throws IOException {
					if (Character.isDigit(dir.getFileName().toString().charAt(0))) {
						var artifactDir = dir.getParent();
						var artifact = artifactDir.getFileName().toString();
						var group = localMaven.relativize(artifactDir.getParent())
							.toString()
							.replace(FileSystems.getDefault().getSeparator(), ".");
						packages.add(new Gav(group, artifact, null));
						return FileVisitResult.SKIP_SUBTREE;
					}

					return FileVisitResult.CONTINUE;
				}
			});

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return packages;
	}
}
