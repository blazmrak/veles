package commands.widgets;

import java.util.ArrayList;
import java.util.List;

public record SearchScorer(int[][] matrix, List<Integer> substringLengths) {
	public int distance() {
		return matrix[matrix.length - 1][matrix[0].length - 1];
	}

	public static SearchScorer perfect() {
		return new SearchScorer(new int[][] { new int[] { 0 } }, List.of());
	}

	public static SearchScorer calculate(String query, String target) {
		var matrix = levenshteinDistance(query, target);
		var lengths = consecutiveSubstrings(query, target);

		return new SearchScorer(matrix, lengths);
	}

	private static List<Integer> consecutiveSubstrings(String query, String target) {
		List<Integer> lengths = new ArrayList<>();
		var max = 0;
		var pos = 0;
		for (var i = 0; i < query.length();) {
			for (var j = pos; j < target.length(); j++) {
				var subLen = 0;
				for (var k = 0; j + k < target.length()
					&& i + k < query.length()
					&& target.charAt(j + k) == query.charAt(i + k); k++) {
					subLen++;
				}
				if (subLen > max) {
					max = subLen;
					pos = j + subLen;
				}
			}

			if (max > 0) {
				lengths.add(max);
				i += max;
				max = 0;
			} else {
				i++;
			}
		}

		return lengths;
	}

	private static int[][] levenshteinDistance(String query, String target) {
		var matrix = new int[query.length() + 1][target.length() + 1];
		// init matrix
		for (int i = 0; i < matrix.length; i++) {
			matrix[i][0] = i;
		}

		for (int i = 0; i < matrix[0].length; i++) {
			matrix[0][i] = i;
		}

		// algorithm
		for (var i = 1; i < query.length() + 1; i++) {
			for (var j = 1; j < target.length() + 1; j++) {
				if (query.charAt(i - 1) == target.charAt(j - 1)) {
					matrix[i][j] = matrix[i - 1][j - 1];
				} else {
					matrix[i][j] = min(matrix[i - 1][j - 1], matrix[i - 1][j], matrix[i][j - 1]) + 1;
				}
			}
		}

		return matrix;
	}

	private static int min(int... vals) {
		var min = Integer.MAX_VALUE;

		for (int i = 0; i < vals.length; i++) {
			if (vals[i] < min) {
				min = vals[i];
			}
		}

		return min;
	}
}
