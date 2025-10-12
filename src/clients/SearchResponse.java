package clients;

import java.util.List;

import io.avaje.jsonb.Json;

@Json
public class SearchResponse {
	public Response response;

	public static class Response {
		public List<Docs> docs;

		public static class Docs {
			public String id;
			public String g;
			public String a;
			public String p;
			public String latestVersion;
		}
	}
}
