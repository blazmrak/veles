package clients;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import config.ConfigDoc.Gav;
import io.avaje.jsonb.Jsonb;

public class MavenCentralClient {
	private final HttpClient client;
	private final Jsonb jsonb;

	public MavenCentralClient() {
		this.client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(Redirect.NEVER)
			.build();
		this.jsonb = Jsonb.builder().build();
	}

	public SearchResponse search(String query) {
		try {
			var req = HttpRequest.newBuilder()
				.uri(
					new URI(
						"https://search.maven.org/solrsearch/select?wt=json&rows=100&q="
							+ URLEncoder.encode(query, StandardCharsets.UTF_8)
					)
				)
				.GET()
				.timeout(Duration.ofSeconds(5))
				.build();

			var response = client.send(req, BodyHandlers.ofInputStream());

			var respType = jsonb.type(SearchResponse.class);

			return respType.fromJson(response.body());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public List<Gav> fetchVersions(Gav gav) {
		try {
			URI uri = new URI(
				"https://repo.maven.apache.org/maven2/%s/%s/"
					.formatted(gav.getGroupId().replaceAll("\\.", "/"), gav.getArtifactId())
			);

			var req = HttpRequest.newBuilder().uri(uri).GET().timeout(Duration.ofSeconds(5)).build();

			var response = client.send(req, BodyHandlers.ofString());

			List<Gav> result = new ArrayList<>();
			String body = response.body();
			int verIndex = body.indexOf("<a") + 1; // skip first because it points to ..
			while ((verIndex = body.indexOf("<a", verIndex)) != -1) {
				var start = body.indexOf(">", verIndex) + 1;
				var end = body.indexOf("</a", verIndex);
				verIndex++;

				var version = body.substring(start, end);

				if (!Character.isDigit(version.charAt(0))) {
					continue;
				} else if (version.endsWith("/")) {
					version = version.substring(0, version.length() - 1);
				}

				result.add(gav.withVersion(version));
			}

			result.sort((g1, g2) -> g2.comparableVersion().compareTo(g1.comparableVersion()));

			return result;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
