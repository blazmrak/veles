package commands.widgets;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class Fuzz {

	public static <T> List<SearchResult<T>> search(Collection<T> items, Scorer<T> scorer) {
		return search(items, new FuzzOptions<T>().scorer(scorer));
	}

	public static <T> List<SearchResult<T>> search(Collection<T> items, FuzzOptions<T> opts) {
		return items.stream()
			.map(opts.scorer)
			.filter(s -> s.similarity() >= opts.similarityCutoff)
			.sorted(Comparator.comparing(SearchResult<T>::similarity).reversed())
			.limit(opts.limit)
			.toList();
	}

	public record SearchResult<T>(T item, SearchScorer matrix, int queryLength, int targetLength) {
		public String explain() {
			return "%f, %f (%s)"
				.formatted(distanceToTarget(), substringSimilarity(), matrix.substringLengths());
		}

		public double similarity() {
			if (queryLength > targetLength) {
				return 0;
			}

			return (distanceToTarget() + substringSimilarity()) / 2;
		}

		private double distanceToTarget() {
			return (double) (targetLength - matrix.distance()) / (double) targetLength;
		}

		private double substringSimilarity() {
			return matrix.substringLengths()
				.stream()
				.filter(i -> i > 1)
				.mapToDouble(i -> (double) i)
				.sum() / (double) queryLength;
		}
	}

	public interface Scorer<T> extends Function<T, SearchResult<T>> {
	}

	public static class FuzzOptions<T> {
		public double similarityCutoff = 0.2;
		public int limit = 10;
		public Scorer<T> scorer;

		public FuzzOptions<T> scorer(Scorer<T> scorer) {
			this.scorer = scorer;
			return this;
		}

		public FuzzOptions<T> limit(int limit) {
			this.limit = limit;
			return this;
		}

		public FuzzOptions<T> similarityCutoff(double similarityCutoff) {
			this.similarityCutoff = similarityCutoff;
			return this;
		}
	}
}
