package ch.unibe.scg.pdfhiscore;

import ch.unibe.scg.pdfhiscore.HistogramQuery.HistogramQueryResult;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * PDF Hi(stogram) Score. Builds a word and a compound word histogram to be
 * queried by Histogram Query Language (HQL) expressions.
 */
public class Main {

	public static final String FILE_EXTENSION = "pdfhiscore.yaml";
	public static final String DEFAULT_CONFIG = "summary+reports";

	/**
	 * Config.
	 */
	public static class Config {

		public final Date queryDate;
		public final boolean writeSummary;
		public final boolean writeReports;
		public final boolean explainQuery;
		public final boolean fullHistograms;
		public final int fullHistogramMinCount;
		public final boolean verbosePDFBox;

		/**
		 * Creates a new config.
		 *
		 * @param cfg the config string.
		 */
		public Config(String cfg) {
			this.queryDate = new Date();
			this.writeSummary = cfg.contains("summary");
			this.writeReports = cfg.contains("reports");
			this.explainQuery = cfg.contains("explain");
			this.fullHistograms = cfg.contains("histograms");
			this.fullHistogramMinCount = 2;
			this.verbosePDFBox = cfg.contains("verbose");
		}

		@Override
		public String toString() {
			return String.format(
					"  summary: %b\n  reports: %b\n  explain: %b\n  full-histograms: %b\n  histogram-mincount: %d\n  verbose: %b",
					writeSummary,
					writeReports,
					explainQuery,
					fullHistograms,
					fullHistogramMinCount,
					verbosePDFBox
			);
		}

	}

	/**
	 * Main method.
	 *
	 * @param args the command line arguments.
	 */
	public static void main(String[] args) {

		// parse command line arguments
		final CommandLineArguments cla = new CommandLineArguments(Main.class, args);
		final CommandLineArguments.Argument dirArg = cla.add(
				"The directory to recursively find and process all PDF files.",
				"<directory>",
				"d", "directory"
		);
		final CommandLineArguments.Argument fileArg = cla.add(
				"The PDF file to process.",
				"<file>",
				"f", "file"
		);
		final CommandLineArguments.Argument queryArg = cla.add(
				"The histogram query containing Histogram Query Language (HQL) expressions.",
				"<file>",
				"q", "query"
		);
		final CommandLineArguments.Argument configArg = cla.add(
				"The config string (\"summary\", \"reports\", \"explain\", \"histograms\", \"verbose\").",
				"<string> (DEFAULT = \"" + DEFAULT_CONFIG + "\")",
				"c", "config"
		);
		final CommandLineArguments.Argument searchArg = cla.add(
				"A Histogram Query Language (HQL) expression to search the given files.",
				"<string>",
				"s", "search"
		);
		final CommandLineArguments.Argument usageArg = cla.add(
				"Print the usage of this program.",
				"",
				"u", "usage"
		);

		if (args.length == 0 || usageArg.isSet()) {
			cla.printUsage();
			kthxbai();
		}

		// read config
		final String configString = configArg.isEmpty()
				? DEFAULT_CONFIG
				: configArg.getString().toLowerCase();
		final Config cfg = new Config(configString);
		if (searchArg.isEmpty()) {
			System.out.println("config:");
			System.out.println(cfg);
		}

		if (!cfg.verbosePDFBox) {
			final String[] loggers = {
				"org.apache.pdfbox.util.PDFStreamEngine",
				"org.apache.pdfbox.pdmodel.font.PDFont",
				"org.apache.pdfbox.pdmodel.font.PDSimpleFont",
				"org.apache.pdfbox.pdmodel.font.PDType0Font",
				"org.apache.pdfbox.pdmodel.font.FontManager",
				"org.apache.pdfbox.pdfparser.PDFObjectStreamParser"
			};
			for (String id : loggers) {
				final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(id);
				logger.setLevel(Level.SEVERE);
			}
		}

		// assemble pdf files
		final File[] files;
		if (!fileArg.isEmpty()) {
			final File file = new File(fileArg.getValue());
			if (file.exists()) {
				files = new File[]{file};
			} else {
				files = new File[0];
			}
		} else if (!dirArg.isEmpty()) {
			final File dir = new File(dirArg.getValue());
			File[] tmpFiles = null;
			if (dir.isDirectory()) {
				final FileFinder finder = new FileFinder("*.pdf");
				try {
					finder.walkFileTree(dir);
					tmpFiles = new File[finder.getNumMatches()];
					int i = 0;
					for (File file : finder.getFiles()) {
						tmpFiles[i++] = file;
					}
				} catch (IOException ex) {
					System.err.println("ERROR: failure searching for PDF files in: " + dir);
					ex.printStackTrace(System.err);
					tmpFiles = null;
				}
			}
			files = (tmpFiles == null) ? new File[0] : tmpFiles;
		} else {
			files = new File[0];
		}

		// this is just a simple search query
		if (!searchArg.isEmpty()) {
			final String expression = searchArg.getString();
			System.out.println("search-query: " + escapeYamlString(expression));
			System.out.println("num-documents: " + files.length);

			// turn off PDFBox's chatter to stderr since it's really distracting here...
			final PrintStream stderr = System.err;
			System.setErr(new PrintStream(new OutputStream() {
				@Override
				public void write(int i) throws IOException {

				}
			}));

			int hits = 0;
			try {
				final HistogramQuery query = new HistogramQuery(expression);
				for (int i = 0; i < files.length; i++) {
					final File file = files[i];
					final PDFTextAnalysis analysis = new PDFTextAnalysis(file);
					final HistogramQueryResult result = query.eval(analysis);
					if (result.getTotalScore() > 0) {
						hits++;
						if (hits == 1) {
							System.out.println("search-results:");
						}
						System.out.println("  - name: '" + file.getName() + "'");
						System.out.println("    path: '" + file.getCanonicalPath() + "'");
						System.out.println("    num-pages: " + analysis.getPDFInfo().get("num-pages"));
						System.out.println("    query-matches: {");
						final Map<String, Integer> qhits = getWordHitsMap(analysis, query);
						final int h = qhits.size();
						int j = 0;
						for (Map.Entry<String, Integer> m : qhits.entrySet()) {
							j++;
							System.out.println(String.format(
									"      '%s': %d%s",
									m.getKey(),
									m.getValue(),
									(j == h) ? "" : ","
							));
						}
						System.out.println("    }");
					}
				}
				System.out.println("document-matches: " + hits);
			} catch (IOException ex) {
				System.err.println("ERROR: invalid hisogram query expression: " + expression);
				ex.printStackTrace(System.err);
			} catch (IllegalArgumentException ex) {
				System.err.println("ERROR: invalid histogram query expression");
				ex.printStackTrace(System.err);
			}

			// 'silent' kthxbai
			System.setErr(stderr);
			System.exit(0);
		}

		// read query file
		final File queryFile;
		final HistogramQuery query;
		if (!queryArg.isEmpty()) {
			queryFile = new File(queryArg.getValue());
			if (queryFile.exists()) {
				System.out.println("\nreading histogram query file: \"" + queryFile + "\"...");
				HistogramQuery q = null;
				try {
					q = new HistogramQuery(queryFile);
					System.out.println("histogram query:");
					System.out.println(String.format(
							"  num. expressions: %d\n  num. query words: %d\n  min. score: %f\n  max. score: %f",
							q.getNumExpressions(),
							q.getWords().size(),
							q.getMinScore(),
							q.getMaxScore()
					));

					System.out.println("\nhistogram query expressions:");
					for (int i = 0, n = q.getNumExpressions(); i < n; i++) {
						System.out.println(String.format(
								"%s -- [weight=%.2f]",
								q.getExpressions().get(i),
								q.getWeights().get(i)
						));
					}
					System.out.print("\n");
				} catch (IOException ex) {
					System.err.println("ERROR: failed to read hisogram query file: " + queryFile);
					ex.printStackTrace(System.err);
					kthxbai();
				} catch (IllegalArgumentException ex) {
					System.err.println("ERROR: invalid histogram query expression");
					ex.printStackTrace(System.err);
					kthxbai();
				}
				query = q;
			} else {
				query = null;
			}
		} else {
			queryFile = null;
			query = null;
		}

		// run for all files assembled...
		final HistogramQueryResult[] queryResults = new HistogramQueryResult[files.length];
		final Map<String, Integer> wordHits = new HashMap<>();
		final int[] expressionHits = new int[(query == null) ? 0 : query.getNumExpressions()];
		final ArrayList<Integer> textExtractionFailures = new ArrayList<>();

		for (int i = 0; i < files.length; i++) {
			final File file = files[i];
			System.out.println(String.format(
					"processing file (%d/%d):\n  \"%s\"...",
					i + 1,
					files.length,
					file
			));
			final File out = getOutputFile(file);
			try (Writer writer = newFileWriter(out)) {
				final PDFTextAnalysis analysis = new PDFTextAnalysis(file);
				if (analysis.getWordCount() == 0) {
					textExtractionFailures.add(i);
				}
				if (query != null) {
					final HistogramQueryResult result = query.eval(analysis);
					if (cfg.writeSummary) {
						evalWordHits(wordHits, analysis, query);
						evalExpressionHits(expressionHits, query, result);
					}
					if (cfg.writeReports) {
						System.out.println(String.format("  writing report to: \"%s\"...", out));
						writeReport(file, analysis, query, result, cfg, writer);
					}
					queryResults[i] = result;
				}
			} catch (IOException ex) {
				System.err.println("ERROR: failure during PDF text analysis");
				ex.printStackTrace(System.err);
			}
		}
		System.out.print("\n");

		// write summary
		if (query != null) {
			final File out = getOutputFile(queryFile);
			if (cfg.writeSummary) {
				System.out.println(String.format("writing summary to: \"%s\"...", out));
				try (Writer writer = newFileWriter(out)) {
					writeSummary(files, query, queryResults, textExtractionFailures, wordHits, expressionHits, cfg, writer);
				} catch (IOException ex) {
					System.err.println("ERROR: failed to write the summary to: " + out);
					ex.printStackTrace(System.err);
				}
			}
		}

		kthxbai();
	}

	public static void kthxbai() {
		System.out.println("\nkthxbai.");
		System.exit(0);
	}

	public static File getOutputFile(File file) {
		final String filename = file.getName();
		final int n = filename.lastIndexOf('.');
		final File parent = file.getParentFile();
		return new File(
				parent,
				filename.substring(0, n + 1) + FILE_EXTENSION
		);
	}

	public static String escapeYamlString(String value) {
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		} else if (!value.contains("'")) {
			return "'" + value + "'";
		} else {
			return "'" + value.replaceAll("'", "''") + "'";
		}
	}

	public static void writeReport(File file, PDFTextAnalysis analysis, HistogramQuery query, HistogramQueryResult result, Config cfg, Writer writer) throws IOException {
		final Map<String, Object> data = newMap();
		data.put("query-date", cfg.queryDate);
		data.put("file-info", getFileInfo(file));
		data.put("pdf-info", analysis.getPDFInfo());

		data.put("total-score", result.getTotalScore());
		data.put("total-score-normalized", result.getTotalScoreNormalized());
		data.put("total-score-cut-normalized", result.getTotalScoreCutNormalized());
		data.put("query-matches", getKeyWordHistogram(query, analysis));
		if (cfg.explainQuery) {
			data.put("expression-scores", getExpressionScores(query, result));
		}
		data.put("single-word-count", analysis.getWordCount());
		if (cfg.fullHistograms) {
			data.put(
					"single-word-histogram",
					getSortedHistogram(analysis.getHistogram(), cfg.fullHistogramMinCount)
			);
			data.put(
					"compound-word-histogram",
					getSortedHistogram(analysis.getCompoundHistogram(), cfg.fullHistogramMinCount)
			);
		}

		writeYaml(data, writer);
	}

	public static void writeSummary(File[] files, HistogramQuery query, HistogramQueryResult[] queryResults, ArrayList<Integer> textExtractionFailures, Map<String, Integer> wordHits, int[] expressionHits, Config cfg, Writer writer) {
		final Map<String, Object> data = newMap();
		final int jobSize = queryResults.length;
		data.put("query-date", cfg.queryDate);
		data.put("query-job-size", jobSize);
		data.put("query-num-expressions", query.getNumExpressions());
		data.put("query", getExpressionSummary(query, jobSize, expressionHits));
		data.put("query-matches", getWordSummary(query, jobSize, wordHits));
		data.put("pdf-text-extraction-failures", getExtractionFailureSummary(files, textExtractionFailures));
		data.put("query-min-score", query.getMinScore());
		data.put("query-max-score", query.getMaxScore());

		putDescriptiveStatistics(queryResults, data);

		final Map<String, Object> fdata = newMap();
		for (int i = 0, n = files.length; i < n; i++) {
			final File file = files[i];
			final HistogramQueryResult result = queryResults[i];
			fdata.put(file.getName(), new Double[]{
				result.getTotalScore(),
				result.getTotalScoreNormalized(),
				result.getTotalScoreCutNormalized()
			});
		}
		data.put("scores", fdata);

		writeYaml(data, writer);
	}

	public static List<String> getExtractionFailureSummary(File[] files, ArrayList<Integer> textExtractionFailures) {
		final List<String> data = new ArrayList<>();
		for (Integer index : textExtractionFailures) {
			data.add(files[index].toString());
		}
		return data;
	}

	public static Map<String, Object> getExpressionSummary(HistogramQuery query, int jobSize, int[] expressionHits) {
		final Map<String, Object> data = newMap();
		for (int i = 0, n = query.getNumExpressions(); i < n; i++) {
			final Map<String, Object> d = newMap();
			final double rel = expressionHits[i] / (double) jobSize;
			d.put("abs-frequency", expressionHits[i]);
			d.put("rel-frequency", rel);
			data.put(query.getExpressions().get(i), d);
		}
		return data;
	}

	public static Map<String, Object> getWordSummary(HistogramQuery query, int jobSize, Map<String, Integer> wordHits) {
		final Map<String, Object> data = newMap();
		for (String word : query.getWords()) {
			final int abs = wordHits.containsKey(word) ? wordHits.get(word) : 0;
			final double rel = abs / (double) jobSize;
			final Map<String, Object> d = newMap();
			d.put("abs-frequency", abs);
			d.put("rel-frequency", rel);
			data.put(word, d);
		}
		return data;
	}

	public static Map<String, Integer> getWordHitsMap(PDFTextAnalysis analysis, HistogramQuery query) {
		final Map<String, Integer> data = new HashMap<>();
		for (String word : query.getWords()) {
			final WordHistogram hist = HistogramQuery.isCompoundWord(word)
					? analysis.getCompoundHistogram()
					: analysis.getHistogram();
			if (hist.getHistogram().containsKey(word)) {
				data.put(word, hist.getHistogram().get(word));
			}
		}
		return data;
	}

	public static void evalWordHits(Map<String, Integer> wordHits, PDFTextAnalysis analysis, HistogramQuery query) {
		for (String word : query.getWords()) {
			final WordHistogram hist = HistogramQuery.isCompoundWord(word)
					? analysis.getCompoundHistogram()
					: analysis.getHistogram();
			if (hist.getHistogram().containsKey(word)) {
				wordHits.put(word, wordHits.containsKey(word) ? wordHits.get(word) + 1 : 1);
			}
		}
	}

	public static void evalExpressionHits(int[] expressionHits, HistogramQuery query, HistogramQueryResult result) {
		final double[] scores = result.getExpressionScores();
		for (int i = 0, n = query.getNumExpressions(); i < n; i++) {
			if (scores[i] != 0) {
				expressionHits[i]++;
			}
		}
	}

	public static void putDescriptiveStatistics(HistogramQueryResult[] queryResults, Map<String, Object> data) {
		final Variance populationVariance = new Variance(false);
		final DescriptiveStatistics[] scores = new DescriptiveStatistics[3];
		for (int i = 0; i < 3; i++) {
			scores[i] = new DescriptiveStatistics();
			scores[i].setVarianceImpl(populationVariance);
		}

		for (int i = 0, n = queryResults.length; i < n; i++) {
			final HistogramQueryResult result = queryResults[i];
			if (result != null) {
				scores[0].addValue(result.getTotalScore());
				scores[1].addValue(result.getTotalScoreNormalized());
				scores[2].addValue(result.getTotalScoreCutNormalized());
			}
		}

		final String[] keys = new String[]{
			"score",
			"score-normalized",
			"score-cut-normalized"
		};

		for (int i = 0; i < 3; i++) {
			final Map<String, Object> sdata = newMap();
			sdata.put("N", scores[i].getN());
			sdata.put("min", scores[i].getMin());
			sdata.put("max", scores[i].getMax());
			sdata.put("median", scores[i].getPercentile(50));
			sdata.put("mean", scores[i].getMean());
			sdata.put("standard-deviation", scores[i].getStandardDeviation());
			sdata.put("variance", scores[i].getVariance());
			sdata.put("skewness", scores[i].getSkewness());
			sdata.put("kurtosis", scores[i].getKurtosis());
			data.put(keys[i], sdata);
		}
	}

	public static Writer newFileWriter(File file) throws UnsupportedEncodingException, FileNotFoundException {
		return new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(file),
				StandardCharsets.UTF_8
		));
	}

	public static Map<String, Object> newMap() {
		return new LinkedHashMap<>();
	}

	public static void writeYaml(Map<String, Object> data, Writer writer) {
		final DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
		options.setPrettyFlow(true);
		final Yaml yaml = new Yaml(options);
		yaml.dump(data, writer);
	}

	public static Map<String, Object> getFileInfo(File file) throws IOException {
		final Map<String, Object> data = newMap();
		data.put("name", file.getName());
		data.put("path", file.getCanonicalPath());
		data.put("size", file.length());
		data.put("last-modified", new Date(file.lastModified()));
		return data;
	}

	public static Map<String, Object> getSortedHistogram(WordHistogram histogram, int minCount) {
		final Map<String, Object> data = newMap();
		for (Map.Entry<String, Integer> e : histogram.getSortedHistogram().entrySet()) {
			if (e.getValue() < minCount) {
				break;
			}
			data.put(e.getKey(), e.getValue());
		}
		return data;
	}

	public static Map<String, Integer> getKeyWordHistogram(HistogramQuery query, PDFTextAnalysis analysis) {
		final Map<String, Integer> data = new HashMap<>();
		for (String word : query.getWords()) {
			if (HistogramQuery.isCompoundWord(word)) {
				if (analysis.getCompoundHistogram().getHistogram().containsKey(word)) {
					final int count = analysis.getCompoundHistogram().getHistogram().get(word);
					data.put(word, count);
				}
			} else {
				if (analysis.getHistogram().getHistogram().containsKey(word)) {
					final int count = analysis.getHistogram().getHistogram().get(word);
					data.put(word, count);
				}
			}
		}
		return WordHistogram.sortByValue(data);
	}

	public static Map<String, Object> getExpressionScores(HistogramQuery query, HistogramQueryResult result) {
		final Map<String, Object> data = newMap();
		for (int i = 0, n = query.getNumExpressions(); i < n; i++) {
			data.put(query.getExpressions().get(i), result.getExpressionScores()[i]);
		}
		return data;
	}

}
