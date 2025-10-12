package common;

import java.util.List;
import java.util.stream.Stream;

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
	public static Stream<String> resolvePaths(Scope... scopes) {
		return resolve(scopes).map(a -> a.getFile().getAbsolutePath());
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
			List<Dependency> dependencies = confDependencies
				.map(d -> new Dependency(new DefaultArtifact(d.coords()), d.scope()))
				.toList();

			CollectRequest collectRequest = new CollectRequest().setDependencies(dependencies)
				.setRepositories(ctx.remoteRepositories());
			DependencyRequest req = new DependencyRequest().setCollectRequest(collectRequest);

			return system.resolveDependencies(ctx.repositorySystemSession(), req)
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
			var session = ctx.repositorySystemSession();
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
}
