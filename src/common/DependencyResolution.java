package common;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

import config.Config;
import config.ConfigDoc.ConfDependency;
import config.ConfigDoc.ConfDependency.Scope;
import config.ConfigDoc.Gav;
import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtimes;

public class DependencyResolution {
	public static ResolutionList mavenDeps() {
		return new ResolutionList();
	}

	public static Stream<String> resolvePaths(Scope... scopes) {
		return resolve(scopes).map(a -> a.getFile().getAbsolutePath());
	}

	public static Stream<Artifact> resolve(ResolutionList resolutionList) {
		return resolve(resolutionList.stream());
	}

	public static Stream<Artifact> resolve(Scope... scopes) {
		if (scopes.length == 0) {
			return Stream.empty();
		}

		Stream<ConfDependency> dependencies = Config.getAllDependencies().filter(d -> {
			for (var scope : scopes) {
				if (d.scope == scope)
					return true;
			}
			return false;
		});

		return resolve(dependencies);
	}

	private static Stream<Artifact> resolve(Stream<ConfDependency> confDependencies) {
		var contextOverrides = ContextOverrides.create().withUserSettings(true).build();
		try (Context ctx = Runtimes.INSTANCE.getRuntime().create(contextOverrides)) {
			var system = ctx.repositorySystem();
			var session = (DefaultRepositorySystemSession) ctx.repositorySystemSession();
			session.setSystemProperty("aether.dependencyCollector.impl", "bf");

			List<Dependency> dependencies = confDependencies
				.map(d -> new Dependency(new DefaultArtifact(d.coords()), d.scope()))
				.toList();

			CollectRequest collectRequest = new CollectRequest().setDependencies(dependencies)
				.setRepositories(ctx.remoteRepositories());
			DependencyRequest req = new DependencyRequest().setCollectRequest(collectRequest);

			return system.resolveDependencies(session, req)
				.getArtifactResults()
				.stream()
				.map(ArtifactResult::getArtifact);
		} catch (DependencyResolutionException e) {
			throw new RuntimeException(e);
		}
	}

	public static Stream<String> resolve(String coords) {
		var contextOverrides = ContextOverrides.create().withUserSettings(true).build();
		try (Context ctx = Runtimes.INSTANCE.getRuntime().create(contextOverrides)) {
			var system = ctx.repositorySystem();
			var session = (DefaultRepositorySystemSession) ctx.repositorySystemSession();
			session.setSystemProperty("aether.dependencyCollector.impl", "bf");

			var dependency = new Dependency(new DefaultArtifact(coords), "compile");

			CollectRequest collectRequest = new CollectRequest().addDependency(dependency)
				.setRepositories(ctx.remoteRepositories());
			DependencyRequest req = new DependencyRequest().setCollectRequest(collectRequest);

			return system.resolveDependencies(session, req)
				.getArtifactResults()
				.stream()
				.map(r -> r.getArtifact().getFile().getAbsolutePath());
		} catch (DependencyResolutionException e) {
			throw new RuntimeException(e);
		}
	}

	public static Artifact getSources(Gav gav, boolean offline) {
		try {
			return getArtifact(gav, "sources", offline);
		} catch (Exception e) {
			return null;
		}
	}

	public static Artifact getDocumentation(Gav gav, boolean offline) {
		try {
			return getArtifact(gav, "javadoc", offline);
		} catch (Exception e) {
			return null;
		}
	}

	public static Artifact getArtifact(Gav gav, String classifier) {
		return getArtifact(gav, classifier, "jar", false);
	}

	private static Artifact getArtifact(Gav gav, String classifier, boolean offline) {
		return getArtifact(gav, classifier, "jar", offline);
	}

	public static Artifact getArtifact(Gav gav, String classifier, String extension) {
		return getArtifact(gav, classifier, extension, false);
	}

	private static Artifact getArtifact(Gav gav, String classifier, String extension,
		boolean offline) {
		return getArtifact(
			new DefaultArtifact(
				gav.getGroupId(),
				gav.getArtifactId(),
				classifier,
				extension,
				gav.getVersion()
			),
			offline
		);
	}

	private static Artifact getArtifact(Artifact artifact, boolean offline) {
		var contextOverrides = ContextOverrides.create()
			.offline(offline)
			.withUserSettings(true)
			.build();
		try (Context ctx = Runtimes.INSTANCE.getRuntime().create(contextOverrides)) {
			var system = ctx.repositorySystem();
			var session = ctx.repositorySystemSession();

			var req = new ArtifactRequest().setArtifact(artifact)
				.setRepositories(ctx.remoteRepositories());

			var result = system.resolveArtifact(session, req);
			return result.getArtifact();
		} catch (ArtifactResolutionException e) {
			throw new RuntimeException(e);
		}
	}

	public static class ResolutionList {
		private final Set<ConfDependency> dependencies = new HashSet<>();

		private ResolutionList() {
		}

		public ResolutionList add(Scope... scopes) {
			dependencies.addAll(Config.getAllDependencies().filter(d -> {
				for (var scope : scopes) {
					if (d.scope == scope)
						return true;
				}
				return false;
			}).toList());

			return this;
		}

		public ResolutionList add(Scope scope) {
			dependencies.addAll(Config.getAllDependencies().filter(d -> d.scope == scope).toList());
			return this;
		}

		public ResolutionList add(ConfDependency dependency) {
			dependencies.add(dependency);
			return this;
		}

		public ResolutionList add(ConfDependency... dependencies) {
			Collections.addAll(this.dependencies, dependencies);
			return this;
		}

		public ResolutionList add(Collection<ConfDependency> dependencies) {
			this.dependencies.addAll(dependencies);
			return this;
		}

		public ResolutionList add(String dependency) {
			add(ConfDependency.parse(dependency));
			return this;
		}

		public Stream<ConfDependency> stream() {
			return this.dependencies.stream();
		}

		public Stream<Artifact> resolve() {
			return DependencyResolution.resolve(this.dependencies.stream());
		}

		public Classpath classpath() {
			return new Classpath().add(this.resolve().map(p -> p.getFile().getAbsolutePath().toString()));
		}
	}

	public static class Classpath {
		public final List<String> jars = new ArrayList<>();

		public Classpath add(Stream<String> paths) {
			paths.forEach(jars::add);
			return this;
		}

		public Classpath add(Path path) {
			this.jars.add(path.toString());
			return this;
		}

		public Classpath add(Classpath path) {
			this.jars.addAll(path.jars);
			return this;
		}

		public Classpath add(String path) {
			this.jars.add(path);
			return this;
		}

		public boolean hasDeps() {
			return !jars.isEmpty();
		}

		@Override
		public String toString() {
			if (Os.isWindows()) {
				return String.join(";", jars);
			} else {
				return String.join(":", jars);
			}
		}
	}
}
