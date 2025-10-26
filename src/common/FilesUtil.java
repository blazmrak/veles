package common;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FilesUtil {
	public static void materializeAllInside(Path dir) {
		try (var files = Files.walk(dir)) {
			files.forEach(FilesUtil::materializeLink);
		} catch (Exception e) {
			throw new RuntimeException("Failed to derefrence libs", e);
		}
	}

	private static void materializeLink(Path file) {
		if (Files.isSymbolicLink(file)) {
			try {
				var p = Files.readSymbolicLink(file);
				Files.copy(p, file, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static void copyDir(Path source, Path target) {
		copyDir(source, target, _ -> true);
	}

	public static void copyDir(Path source, Path target, Predicate<Path> predicate) {
		try (var files = Files.walk(source)) {
			files.filter(predicate).forEach(path -> {
				try {
					Path relative = source.relativize(path);
					Path dest = target.resolve(relative);
					if (Files.isDirectory(path)) {
						Files.createDirectories(dest);
					} else {
						Files.createDirectories(dest.getParent());
						Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Stream<Path> copyNonBuild(Path source, Path target, Predicate<Path> predicate) {
		var files = new ArrayList<Path>();

		try {
			Path root = Path.of(".");
			Path output = root.resolve("target");
			Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
					throws IOException {
					if (root.equals(dir)) {
						return FileVisitResult.CONTINUE;
					} else if (output.equals(dir) || Files.isHidden(dir)) {
						return FileVisitResult.SKIP_SUBTREE;
					} else {
						return FileVisitResult.CONTINUE;
					}
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					// TODO: improve formatting here
					if (!Files.isHidden(file)
						&& !"veles.yaml".equals(file.toString())
						&& predicate.test(file)) {
						Path relative = source.relativize(file);
						Path dest = target.resolve(relative);
						Files.createDirectories(dest.getParent());
						Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return files.stream();
	}
}
