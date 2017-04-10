package ch.unibe.scg.pdfhiscore;

import ch.unibe.scg.pdfhiscore.HistogramQueryLanguageParser.ProgContext;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.TokenStream;

/**
 * A histogram query is a list of valid Histogram Query Language (HQL)
 * expressions.
 */
public class HistogramQuery {

	private final static double DEFAULT_WEIGHT = 1.0;
	private final static char COMMENT_CHAR = '#';
	private final static char SPLIT_LINE_CHAR = '\\';
	private final ArrayList<ProgContext> contexts;
	private final ArrayList<String> expressions;
	private final ArrayList<Double> weights;
	private final Set<String> words;

	/**
	 * Creates a new histogram query.
	 *
	 * @param file the histogram query file containing the HQL expressions.
	 * @throws java.io.IOException
	 */
	public HistogramQuery(File file) throws IOException, IllegalArgumentException {
		this(Files.lines(file.toPath()));
	}

	/**
	 * Creates a new histogram query.
	 *
	 * @param expressions the HQL expressions.
	 * @throws java.io.IOException
	 */
	public HistogramQuery(String... expressions) throws IOException, IllegalArgumentException {
		this(Arrays.asList(expressions));
	}

	/**
	 * Creates a new histogram query.
	 *
	 * @param expressions the HQL expressions.
	 * @throws java.io.IOException
	 */
	public HistogramQuery(List<String> expressions) throws IOException, IllegalArgumentException {
		this(expressions.stream());
	}

	/**
	 * Creates a new histogram query.
	 *
	 * @param stream the stream of HQL expressions.
	 * @throws java.io.IOException
	 */
	public HistogramQuery(Stream<String> stream) throws IOException, IllegalArgumentException {
		this.contexts = new ArrayList<>();
		this.expressions = new ArrayList<>();
		this.weights = new ArrayList<>();
		this.words = new HashSet<>();
		final LineBuffer buffer = new LineBuffer();

		stream.forEach((s) -> {
			s = s.toLowerCase().trim();
			if (s.isEmpty()) {
				return;
			}
			if (isSplitLine(s)) {
				buffer.put(s);
				return;
			}
			if (isExpression(s)) {
				buffer.merge(s);
				if (buffer.isValid()) {
					final String expr = buffer.getExpression();
					try {
						final TokenStream tokenStream = getTokenStream(expr);
						final ErrorListener errorListener = new ErrorListener();
						final ProgContext context = getProgContext(tokenStream, errorListener);
						if (errorListener.isFail()) {
							throw new IOException();
						}
						pickWordTokens(tokenStream);
						contexts.add(context);
						expressions.add(expr);
						weights.add(buffer.getWeight());
					} catch (IOException ex) {
						throw new IllegalArgumentException(
								"invalid syntax: \"" + expr + "\"",
								ex
						);
					}
				}
			}
		});
	}

	private boolean isSplitLine(String line) {
		return line.charAt(line.length() - 1) == SPLIT_LINE_CHAR;
	}

	private boolean isExpression(String line) {
		return line.charAt(0) != COMMENT_CHAR;
	}

	/**
	 * Simple line buffer to merge split lines. Lines that will be continued end
	 * with '\' and call {@code put()} to buffer them. Final/terminal lines call
	 * {@code merge()}. Now, that we have a complete line in the buffer, we also
	 * look for optional options at the start of the expression and parse them.
	 * Finally the expression and weight can be retireved after each call to
	 * {@code merge()}.
	 */
	private static class LineBuffer {

		private String buffer;

		public void put(String line) {
			if (buffer == null) {
				buffer = strip(line);
			} else {
				buffer = buffer + strip(line);
			}
		}

		private String strip(String line) {
			return line.substring(0, line.length() - 1);
		}

		public void merge(String line) {
			if (buffer == null) {
				buffer = line;
			} else {
				buffer = buffer + line;
			}
			parse();
			buffer = null;
		}

		// optional options block at the start of an expression in the form of:
		// [var=val, val, ..., var=val]
		private String expression;
		private Double weight;

		public boolean isValid() {
			return expression != null;
		}

		public String getExpression() {
			return expression;
		}

		public Double getWeight() {
			return weight;
		}

		private void parse() {
			expression = null;
			weight = null;

			final int start = buffer.indexOf('[');
			final int end = buffer.indexOf(']');
			if (start == 0 && end > start) {
				final String options = buffer.substring(start + 1, end);
				for (String opt : options.split(",")) {
					final String[] args = opt.split("=");
					if (args.length > 1) {
						final String var = args[0].trim();
						final String val = args[1].trim();
						parseOpt(var, val);
					} else {
						final String val = opt.trim();
						parseOpt(val);
					}
				}
				expression = buffer.substring(end + 1).trim();
			} else {
				expression = buffer;
			}
			if (weight == null) {
				weight = DEFAULT_WEIGHT;
			}
		}

		private void parseOpt(String var, String val) {
			if ("weight".equals(var)) {
				try {
					weight = Double.parseDouble(val);
				} catch (NumberFormatException ex) {
					// ignore
				}
			}
		}

		private void parseOpt(String val) {

		}

	}

	/**
	 * Returns the number of Histogram Query Language (HQL) expressions.
	 *
	 * @return the number of Histogram Query Language (HQL) expressions.
	 */
	public int getNumExpressions() {
		return expressions.size();
	}

	/**
	 * Returns the Histogram Query Language (HQL) expressions.
	 *
	 * @return the Histogram Query Language (HQL) expressions.
	 */
	public List<String> getExpressions() {
		return expressions;
	}

	/**
	 * Returns the weights of the Histogram Query Language (HQL) expressions.
	 *
	 * @return the weights of the Histogram Query Language (HQL) expressions.
	 */
	public List<Double> getWeights() {
		return weights;
	}

	/**
	 * Returns a set of all words used by this query. This includes single
	 * words, as well as compound words (single- and double-quoted).
	 *
	 * @return a set of all words used by this query.
	 */
	public Set<String> getWords() {
		return words;
	}

	/**
	 * Returns the maximum score if all expressions are satisfied. Expressions
	 * with a negative weight are ignored.
	 *
	 * @return the maximum score.
	 */
	public double getMaxScore() {
		double total = 0;
		// ignore negative weights, since the "good" thing to do would be to not
		// satisfy the expression
		for (Double s : getWeights()) {
			if (s > 0) {
				total += s;
			}
		}
		return total;
	}

	/**
	 * Returns the minimum score. Usually 0, but can be negative if there are
	 * expressions with a negative weight.
	 *
	 * @return the minimum score.
	 */
	public double getMinScore() {
		double total = 0;
		// assume case where only expressions with negative weight are satisfied
		for (Double s : getWeights()) {
			if (s < 0) {
				total += s;
			}
		}
		return total;
	}

	/**
	 * Evaluates PDF word histograms using this histogram query.
	 *
	 * @param analysis the PDF text analysis.
	 * @return the achieved score (between 0 and {@code getMaxScore()}.
	 */
	public HistogramQueryResult eval(PDFTextAnalysis analysis) {
		return eval(
				analysis.getHistogram().getHistogram(),
				analysis.getCompoundHistogram().getHistogram()
		);
	}

	/**
	 * Evaluates the (single) word histogram using this histogram query.
	 *
	 * @param histogram the (single) word histogram.
	 * @return the achieved score (between 0 and {@code getMaxScore()}.
	 */
	public HistogramQueryResult eval(Map<String, Integer> histogram) {
		return eval(histogram, null);
	}

	/**
	 * Evaluates word histograms (single and compound) using this histogram
	 * query.
	 *
	 * @param histogram the single word histogram.
	 * @param compoundHistogram the compound word histogram.
	 * @return the achieved score (between 0 and {@code getMaxScore()}.
	 */
	public HistogramQueryResult eval(Map<String, Integer> histogram, Map<String, Integer> compoundHistogram) {
		final HistogramQueryResult result = new HistogramQueryResult(this);
		final EvalVisitor visitor = new EvalVisitor(histogram, compoundHistogram);

		for (int i = 0; i < getNumExpressions(); i++) {
			final ProgContext context = contexts.get(i);
			result.setScore(
					i,
					context.accept(visitor) ? getWeights().get(i) : 0
			);
		}
		return result;
	}

	protected final TokenStream getTokenStream(String expression) throws IOException {
		final CharStream inputCharStream = new ANTLRInputStream(new StringReader(expression));
		final TokenSource tokenSource = new HistogramQueryLanguageLexer(inputCharStream);
		return new CommonTokenStream(tokenSource);
	}

	protected final ProgContext getProgContext(TokenStream tokenStream, ANTLRErrorListener errorListener) {
		final HistogramQueryLanguageParser parser = new HistogramQueryLanguageParser(tokenStream);
		parser.addErrorListener(errorListener);
		final ProgContext context = parser.prog();
		return context;
	}

	private void pickWordTokens(TokenStream tokenStream) {
		for (int i = 0, n = tokenStream.size(); i < n; i++) {
			final Token token = tokenStream.get(i);
			switch (token.getType()) {
				case HistogramQueryLanguageParser.SQWORD:
				case HistogramQueryLanguageParser.DQWORD:
				case HistogramQueryLanguageParser.WORD:
					words.add(stripQuotes(token.getText()));
					break;
			}
		}
	}

	public static String stripQuotes(String word) {
		return word.replaceAll("[\"']", "");
	}

	public static boolean isCompoundWord(String word) {
		return word.contains(" ");
	}

	/**
	 * A histogram query result.
	 */
	public static class HistogramQueryResult {

		private final double[] score;
		private double totalScore;
		private final double minScore;
		private final double maxScore;

		/**
		 * Creates a new histogram query result.
		 *
		 * @param query the histogram query.
		 */
		public HistogramQueryResult(HistogramQuery query) {
			this.score = new double[query.getNumExpressions()];
			this.minScore = query.getMinScore();
			this.maxScore = query.getMaxScore();
		}

		protected void setScore(int index, double score) {
			this.score[index] = score;
			totalScore += score;
		}

		/**
		 * Returns the individual expression scores.
		 *
		 * @return the individual expression scores.
		 */
		public double[] getExpressionScores() {
			return score;
		}

		/**
		 * Returns the total score. The total score is the sum of all expression
		 * scores.
		 *
		 * @return the total score.
		 */
		public double getTotalScore() {
			return totalScore;
		}

		/**
		 * Returns the total score normalized to the range {@literal 0..1}.
		 *
		 * @return the total score normalized to the range {@literal 0..1}.
		 */
		public double getTotalScoreNormalized() {
			final double range = maxScore - minScore;
			return (minScore + totalScore) / range;
		}

		/**
		 * Returns the total score normalized to the range {@literal 0..1}, with
		 * negative scores cut to 0.
		 *
		 * @return the total score normalized to the range {@literal 0..1}, with
		 * negative scores cut to 0.
		 */
		public double getTotalScoreCutNormalized() {
			if (totalScore < 0) {
				return 0;
			}
			return totalScore / maxScore;
		}

	}

}
