package common;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

import config.Config;

public class Paths {
	public static Optional<Path> findFirst(String... paths) {
		for (var pathStr : paths) {
			var path = Path.of(pathStr);
			if (Files.exists(path)) {
				return Optional.of(path);
			}
		}

		return Optional.empty();
	}

	public static Stream<Path> allTestFiles() {
		try {
			return Files.walk(Config.testDir()).filter(f -> f.getFileName().toString().endsWith(".java"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Stream<Path> allSourceFiles() {
		return allNonBuildVisible().filter(f -> f.startsWith(Config.sourceDir()))
			.filter(f -> f.toString().endsWith(".java"));
	}

	public static Stream<Path> allResourceFiles() {
		return allNonBuildVisible().filter(f -> f.startsWith(Config.sourceDir()))
			.filter(f -> !f.toString().endsWith(".java"));
	}

	/// @return A stream of files in the project, skipping `target` directory and hidden files and
	///         directories ///
	public static Stream<Path> allNonBuildVisible() {
		var files = new ArrayList<Path>();

		try {
			Path root = Path.of(".");
			Path target = root.resolve("target");
			Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
					throws IOException {
					if (root.equals(dir)) {
						return FileVisitResult.CONTINUE;
					} else if (target.equals(dir) || Files.isHidden(dir)) {
						return FileVisitResult.SKIP_SUBTREE;
					} else {
						return FileVisitResult.CONTINUE;
					}
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (!Files.isHidden(file)) {
						files.add(file);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return files.stream();
	}

	public static String tryReadFile(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void tryWriteFile(Path path, String value) {
		try {
			Files.writeString(path, value);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void watch(Path path, Consumer<List<WatchEvent<Path>>> onChangeHandler) {
		new DebouncedWatcher(path, onChangeHandler).watch();
	}

	private static class DebouncedWatcher {
		private final Path path;
		private final Consumer<List<WatchEvent<Path>>> onChangeHandler;
		private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
		private final ScheduledExecutorService executorService;

		public DebouncedWatcher(Path path, Consumer<List<WatchEvent<Path>>> onChangeHandler) {
			this.path = path;
			this.onChangeHandler = onChangeHandler;
			this.executorService = Executors.newScheduledThreadPool(0);
		}

		@SuppressWarnings("unchecked")
		public void watch() {
			var watcher = initWatcher();

			ScheduledFuture<?> task = null;
			final List<WatchEvent<Path>> events = new ArrayList<WatchEvent<Path>>();
			while (true) {
				WatchKey key;
				try {
					key = watcher.take();
				} catch (InterruptedException e) {
					return;
				}

				lock.writeLock().lock();
				try {
					for (WatchEvent<?> event : key.pollEvents()) {
						WatchEvent.Kind<?> kind = event.kind();

						if (kind == OVERFLOW) {
							continue;
						}

						if (event.context() instanceof Path) {
							events.add((WatchEvent<Path>) event);
						}
					}
				} finally {
					lock.writeLock().unlock();
				}

				if (task != null) {
					task.cancel(false);
				}

				task = executorService.schedule(() -> {
					lock.writeLock().lock();
					try {
						onChangeHandler.accept(events);
					} catch (Exception e) {
					} finally {
						events.clear();
						lock.writeLock().unlock();
					}
				}, 10, TimeUnit.MILLISECONDS);

				boolean valid = key.reset();
				if (!valid) {
					break;
				}
			}
		}

		private WatchService initWatcher() {
			try {
				var watcher = FileSystems.getDefault().newWatchService();

				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
						throws IOException {
						dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

						return FileVisitResult.CONTINUE;
					}
				});

				return watcher;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static void ensureDirExists(Path path) {
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
