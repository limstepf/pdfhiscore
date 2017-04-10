package ch.unibe.scg.pdfhiscore;

import java.io.File;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * PDF text analysis.
 */
public class PDFTextAnalysis {

	private final Map<String, Object> pdfInfo;
	private final WordHistogram hist;
	private final WordHistogram compoundHist;
	private int wordCount;

	/**
	 * Creates a new PDF text analysis.
	 *
	 * @param file the PDF file.
	 */
	public PDFTextAnalysis(File file) {
		this.hist = new WordHistogram();
		this.compoundHist = new WordHistogram();
		this.pdfInfo = new LinkedHashMap<>();

		try (PDDocument document = PDDocument.load(file)) {
			getPDFInfo(document, pdfInfo);
			final PDFTextStripper textStripper = new PDFTextStripper();
			textStripper.setSortByPosition(true);
			parseText(textStripper.getText(document));
		} catch (Exception ex) {
			System.err.println("ERROR: failed to parse PDF file: " + file);
			ex.printStackTrace(System.err);
		}
	}

	/**
	 * Returns the number of (single) words extracted from the PDF.
	 *
	 * @return the number of (single) words extracted from the PDF.
	 */
	public int getWordCount() {
		return wordCount;
	}

	/**
	 * Returns the PDF information.
	 *
	 * @return the PDF information.
	 */
	public Map<String, Object> getPDFInfo() {
		return pdfInfo;
	}

	/**
	 * Returns the (single) word histogram.
	 *
	 * @return the (single) word histogram.
	 */
	public WordHistogram getHistogram() {
		return hist;
	}

	/**
	 * Returns the compound word histogram. A compound word is composed of two
	 * (single) words.
	 *
	 * @return the compound word histogram.
	 */
	public WordHistogram getCompoundHistogram() {
		return compoundHist;
	}

	private void parseText(String text) {
		String last = "";
		for (String line : text.split("\\\\r?\\\\n")) {
			for (String word : text.split(" ")) {
				// remove trash chars
				word = word.toLowerCase().replaceAll(
						"[\\.|,|=|:|;|!|\\?|\\(|\\)|\\r]",
						""
				).trim();
				// also reject numbers and similar trash
				if (!word.isEmpty() && word.matches(".*[a-zA-Z].*")) {
					hist.insert(word);
					wordCount++;
					if (!last.isEmpty()) {
						final String c = last + " " + word;
						compoundHist.insert(c);
					}
					last = word;
				}
			}
		}
	}

	private void getPDFInfo(PDDocument document, Map<String, Object> map) {
		final PDDocumentInformation info = document.getDocumentInformation();
		map.put("subject", info.getSubject());
		map.put("title", info.getTitle());
		map.put("author", info.getAuthor());
		map.put("creator", info.getCreator());
		map.put("producer", info.getProducer());
		map.put("keywords", info.getKeywords());
		final Calendar creationDate = info.getCreationDate();
		map.put("creation-date", creationDate == null ? "-" : creationDate.getTime());
		final Calendar modificationDate = info.getModificationDate();
		map.put("modification-date", modificationDate == null ? "-" : modificationDate.getTime());
		map.put("num-pages", String.format("%d", document.getNumberOfPages()));
	}

}
