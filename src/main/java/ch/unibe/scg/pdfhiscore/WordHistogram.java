package ch.unibe.scg.pdfhiscore;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simple word histogram.
 */
public class WordHistogram {

	private final HashMap<String, Integer> histogram;

	/**
	 * Creates a new word histogram.
	 */
	public WordHistogram() {
		this.histogram = new HashMap<>();
	}

	/**
	 * Inserts a word.
	 *
	 * @param word the word to be inserted/counted.
	 */
	public void insert(String word) {
		if (histogram.containsKey(word)) {
			histogram.put(word, histogram.get(word) + 1);
		} else {
			histogram.put(word, 1);
		}
	}

	/**
	 * Returns the (single) word histogram.
	 *
	 * @return the (single) word histogram.
	 */
	public Map<String, Integer> getHistogram() {
		return this.histogram;
	}

	/**
	 * Returns the sorted (single) word histogram.
	 *
	 * @return the sorted (single) word histogram.
	 */
	public Map<String, Integer> getSortedHistogram() {
		return sortByValue(histogram);
	}

	/**
	 * Sorts a map by its value in descending order.
	 *
	 * @param <K> type of the key.
	 * @param <V> type of the value.
	 * @param map the map to be sorted.
	 * @return the sorted map in descending order.
	 */
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
		return map.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
				.collect(Collectors.toMap(
								Map.Entry::getKey,
								Map.Entry::getValue,
								(e1, e2) -> e1,
								LinkedHashMap::new
						));
	}

}
