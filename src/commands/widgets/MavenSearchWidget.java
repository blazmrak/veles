package commands.widgets;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp.Capability;

import clients.MavenCentralClient;
import clients.SearchResponse;
import commands.widgets.Fuzz.SearchResult;
import common.LocalMavenRepository;
import config.ConfigDoc.ConfDependency;
import config.ConfigDoc.ConfDependency.Scope;
import config.ConfigDoc.Gav;

public class MavenSearchWidget {
	private final Terminal terminal;
	private final Attributes attrs;
	private final Set<Gav> packages;
	private final MavenCentralClient mavenCentralClient = new MavenCentralClient();

	public MavenSearchWidget(Terminal terminal) {
		this.terminal = terminal;
		this.attrs = terminal.getAttributes();
		this.packages = LocalMavenRepository.packages()
			.stream()
			.filter(p -> !p.getArtifactId().contains("-parent"))
			.collect(Collectors.toSet());
	}

	public ConfDependency search() {
		terminal.enterRawMode();

		var artifactCombobox = new Combobox<SearchResult<Gav>>(terminal);
		artifactCombobox.filterCompletions(this::fuzzySearchArtifacts);
		artifactCombobox.renderItem(r -> r.item().toString());
		artifactCombobox.withPrefix("Search: ");
		artifactCombobox.handle("search_remote", KeyMap.ctrl('u'), (g) -> {
			try {
				var query = g.query().toString();
				artifactCombobox.withPrefix("Querying central: ");
				artifactCombobox.render();
				SearchResponse result = mavenCentralClient.search(query);
				result.response.docs.forEach(d -> {
					packages.add(new Gav(d.g, d.a, null));
				});

				artifactCombobox.withPrefix("Search: ");
				return List.of(new Combobox.UpdateCompletions());
			} catch (Exception e) {
				artifactCombobox.withPrefix("Failed - " + e.getMessage() + ": ");
				return List.of();
			}
		});

		var artifactGav = artifactCombobox.prompt();

		var versions = new MavenCentralClient().fetchVersions(artifactGav.selection().item());
		var versionsCombobox = new Combobox<Gav>(terminal);
		versionsCombobox.withPrefix(artifactGav.selection().item().toString() + ":");
		versionsCombobox.renderItem(item -> item.getVersion());
		versionsCombobox.filterCompletions(
			query -> versions.stream().filter(v -> v.getVersion().startsWith(query)).limit(10).toList()
		);

		var gav = versionsCombobox.prompt().selection();

		var scopes = Scope.all();
		var scopeCombobox = new Combobox<Scope>(terminal);
		scopeCombobox.withPrefix(gav.toString());
		scopeCombobox.renderItem(s -> s.toString().toLowerCase());
		scopeCombobox.filterCompletions(
			query -> scopes.stream().filter(s -> s.toString().toLowerCase().startsWith(query)).toList()
		);

		var scope = scopeCombobox.prompt().selection();

		terminal.setAttributes(attrs);
		terminal.puts(Capability.clear_screen);
		terminal.flush();

		return new ConfDependency(gav, scope);
	}

	private List<SearchResult<Gav>> fuzzySearchArtifacts(String query) {
		return Fuzz.search(packages, (gav) -> {
			var scorer = SearchScorer.calculate(query, gav.getArtifactId());
			return new SearchResult<>(gav, scorer, query.length(), gav.getArtifactId().length());
		});
	}
}
