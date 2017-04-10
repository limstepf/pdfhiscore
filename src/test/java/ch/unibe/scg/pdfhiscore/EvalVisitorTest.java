package ch.unibe.scg.pdfhiscore;

import ch.unibe.scg.pdfhiscore.HistogramQueryLanguageParser.ProgContext;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.TokenStream;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Histogram Query Language (HQL) grammar and evaluation tests.
 */
public class EvalVisitorTest {

	public static final String[] validExpressions = {
		/*  0: */"w1",
		/*  1: */ "w2 w3 w4 w5",
		/*  2: */ "\"s1 s2\"",
		/*  3: */ "'s3 s4'",
		/*  4: */ "> w6 10",
		/*  5: */ "&& w7 w8",
		/*  6: */ "|| w9 w10",
		/*  7: */ "&& w11 w12 w13 w14",
		/*  8: */ "&& w15 \"sa sb\" (> w16 5) (|| w17 w18 w19)",
		/*  9: */ "|| (&& \"s5 s6\" w20) \"s7 s8\"",
		/* 10: */ "&& (&& w21 w22) w23 (|| w24 w25)",
		/* 11: */ "&& w26 (|| w27 (> w28 10))",
		/* 12: */ "(> w29 w30 w31 30)",
		/* 13: */ "> w29 w30 w31 30",
		/* 14: */ "(|| xxx yyy zzz)",
		/* 15: */ "!!(|| xxx yyy zzz)",
		/* 16: */ "(&& !xxx !yyy zzz)",
		/* 17: */ "!vvv",
		/* 18: */ "!(&& vvv vvv)"
	};

	public static final String[] invalidExpressions = {
		/*  0: */"",
		/*  1: */ "> w1 w2",
		/*  2: */ "&&",
		/*  3: */ "|| w3 && w4",
		/*  4: */ "! || w1 w2"
	};

	@Test
	public void testValidExpressions() throws IOException {
		for (String s : validExpressions) {
			assertTrue("valid expr.: " + s, isValidString(s));
		}
	}

	@Test
	public void testInvalidExpressions() throws IOException {
		for (String s : invalidExpressions) {
			assertFalse("invalid expr.: " + s, isValidString(s));
		}
	}

	@Test
	public void testSatisfiedExpressions() throws IOException {
		// word histogram to satisfy all valid expressions
		final Map<String, Integer> map = new HashMap<>();
		map.put("w1", 1);
		map.put("w2", 1);
		map.put("w3", 1);
		map.put("w4", 1);
		map.put("w5", 1);
		map.put("w6", 11);
		map.put("w7", 1);
		map.put("w8", 1);
		map.put("w10", 1);
		map.put("w11", 1);
		map.put("w12", 1);
		map.put("w13", 1);
		map.put("w14", 1);
		map.put("w15", 1);
		map.put("w16", 6);
		map.put("w18", 1);
		map.put("w20", 1);
		map.put("w21", 1);
		map.put("w22", 1);
		map.put("w23", 1);
		map.put("w25", 1);
		map.put("w26", 1);
		map.put("w28", 11);
		map.put("s1 s2", 1);
		map.put("s3 s4", 1);
		map.put("sa sb", 1);
		map.put("s5 s6", 1);
		map.put("w29", 11);
		map.put("w30", 11);
		map.put("w31", 11);
		map.put("zzz", 1);

		final EvalVisitor visitor = new EvalVisitor(map);

		for (String s : validExpressions) {
			assertTrue("satisfied expr.: " + s, isSatisfiedExpression(visitor, s));
		}
	}

	@Test
	public void testUnsatisfiedExpressions() throws IOException {
		// word histogram to make sure all valid expressions are not satisfied
		final Map<String, Integer> map = new HashMap<>();
		map.put("w3", 1);
		map.put("w5", 1);
		map.put("w6", 3);
		map.put("w8", 1);
		map.put("w12", 1);
		map.put("w13", 1);
		map.put("w14", 1);
		map.put("w15", 1);
		map.put("sa sb", 1);
		map.put("w16", 3);
		map.put("s5 s6", 1);
		map.put("w21", 1);
		map.put("w22", 1);
		map.put("w23", 1);
		map.put("w26", 1);
		map.put("w28", 9);
		map.put("w29", 1);
		map.put("w30", 1);
		map.put("w31", 1);
		map.put("vvv", 1);

		final EvalVisitor visitor = new EvalVisitor(map);

		for (String s : validExpressions) {
			assertFalse("unsatisfied expr.: " + s, isSatisfiedExpression(visitor, s));
		}
	}

	protected boolean isSatisfiedExpression(EvalVisitor visitor, String string) throws IOException {
		final ErrorListener errorListener = new ErrorListener();
		final ProgContext context = parseString(string, errorListener);
		assertFalse("valid expr.: " + string, errorListener.isFail());
		return context.accept(visitor);
	}

	protected boolean isSatisfiedExpression(Map<String, Integer> map, String string) throws IOException {
		return isSatisfiedExpression(new EvalVisitor(map), string);
	}

	protected boolean isValidString(String string) throws IOException {
		final ErrorListener errorListener = new ErrorListener();
		final ProgContext context = parseString(string, errorListener);
		return !errorListener.isFail();
	}

	protected ProgContext parseString(String string, ANTLRErrorListener errorListener) throws IOException {
		final CharStream inputCharStream = new ANTLRInputStream(new StringReader(string));
		final TokenSource tokenSource = new HistogramQueryLanguageLexer(inputCharStream);
		final TokenStream tokenStream = new CommonTokenStream(tokenSource);
		final HistogramQueryLanguageParser parser = new HistogramQueryLanguageParser(tokenStream);
		parser.addErrorListener(errorListener);
		return parser.prog();
	}

}
