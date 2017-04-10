package ch.unibe.scg.pdfhiscore;

import static ch.unibe.scg.pdfhiscore.HistogramQuery.isCompoundWord;
import static ch.unibe.scg.pdfhiscore.HistogramQuery.stripQuotes;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Histogram Query Language (HQL) evaluation visitor.
 */
public class EvalVisitor implements HistogramQueryLanguageVisitor<Boolean> {

	/**
	 * Known operators.
	 */
	public enum Operator {

		/**
		 * Logical AND operator.
		 */
		AND("&&"),
		/**
		 * Logical OR operator.
		 */
		OR("||"),
		/**
		 * Logical NOT operator.
		 */
		NOT("!"),
		/**
		 * Greater than operator. Evaluates to {@code true} if the word(s) occur
		 * more than the stated value.
		 */
		GT(">");

		private final String symbol;

		Operator(String symbol) {
			this.symbol = symbol;
		}

		@Override
		public String toString() {
			return this.symbol;
		}

		public boolean equals(String symbol) {
			return this.symbol.equals(symbol);
		}
	}

	private final Map<String, Integer> histogram;
	private final Map<String, Integer> compoundHistogram;

	/**
	 * Creates a new evaluation visitor.
	 *
	 * @param histogram the word histogram.
	 */
	public EvalVisitor(Map<String, Integer> histogram) {
		this(histogram, null);
	}

	/**
	 * Creates a new evaluation visitor.
	 *
	 * @param histogram the single word histogram.
	 * @param compoundHistogram the compound word histogram.
	 */
	public EvalVisitor(Map<String, Integer> histogram, Map<String, Integer> compoundHistogram) {
		this.histogram = histogram;
		this.compoundHistogram = compoundHistogram;
	}

	@Override
	public Boolean visit(ParseTree arg0) {
		return false;
	}

	@Override
	public Boolean visitChildren(RuleNode arg0) {
		return false;
	}

	@Override
	public Boolean visitErrorNode(ErrorNode arg0) {
		return false;
	}

	@Override
	public Boolean visitTerminal(TerminalNode arg0) {
		final String op = arg0.getText();
		if (Operator.OR.equals(op)) {
			return false;
		}
		return true;
	}

	@Override
	public Boolean visitProg(HistogramQueryLanguageParser.ProgContext ctx) {
		final String op = ctx.getChild(0).getText();

		if (Operator.AND.equals(op)) {
			return parseAND(ctx.children);
		} else if (Operator.OR.equals(op)) {
			return parseOR(ctx.children);
		} else if (Operator.NOT.equals(op)) {
			throw new IllegalStateException(
					"unexpected NOT operator in `prog`"
			);
		} else if (Operator.GT.equals(op)) {
			final int n = ctx.children.size() - 1;
			return parseGT(
					ctx.children.subList(1, n),
					ctx.children.get(n).getText()
			);
		} else {
			return parseAND(ctx.children);
		}
	}

	@Override
	public Boolean visitExpr(HistogramQueryLanguageParser.ExprContext ctx) {
		if (ctx.AND() != null) {
			return parseAND(
					// strip open and closing parentheses
					ctx.children.subList(1, ctx.children.size() - 1)
			);
		} else if (ctx.OR() != null) {
			return parseOR(
					// strip open and closing parentheses
					ctx.children.subList(1, ctx.children.size() - 1)
			);
		} else if (ctx.NOT() != null) {
			// check for opening parenthesis
			final int idx = (ctx.children.size() > 2) ? 2 : 1;
			return !ctx.children.get(idx).accept(this);
		} else if (ctx.GT() != null) {
			// ...and also strip open and closing parentheses (besides > and INT)
			final int n = ctx.children.size() - 2;
			return parseGT(
					ctx.children.subList(2, n),
					ctx.children.get(n).getText()
			);
		}

		return ctx.getChild(0).accept(this);
	}

	@Override
	public Boolean visitValue(HistogramQueryLanguageParser.ValueContext ctx) {
		return eval(ctx.getChild(0).getText());
	}

	// check for simple existence
	protected boolean eval(String word) {
		if (compoundHistogram != null) {
			if (isCompoundWord(word)) {
				return compoundHistogram.containsKey(stripQuotes(word));
			}
		}
		return histogram.containsKey(stripQuotes(word));
	}

	// check for minimum count
	protected boolean eval(String word, int minExclusive) {
		word = stripQuotes(word);

		if (compoundHistogram != null) {
			if (isCompoundWord(word)) {
				if (!compoundHistogram.containsKey(word)) {
					return false;
				}
				return compoundHistogram.get(word) > minExclusive;
			}
		}

		if (!histogram.containsKey(word)) {
			return false;
		}
		return histogram.get(word) > minExclusive;
	}

	// add up count of multiple words
	protected int count(List<ParseTree> children) {
		int sum = 0;
		for (ParseTree tree : children) {
			final String word = stripQuotes(tree.getText());
			if (compoundHistogram != null) {
				if (isCompoundWord(word)) {
					if (compoundHistogram.containsKey(word)) {
						sum += compoundHistogram.get(word);
					}
					continue;
				}
			}
			if (histogram.containsKey(word)) {
				sum += histogram.get(word);
			}
		}
		return sum;
	}

	protected boolean parseAND(List<ParseTree> children) {
		boolean ret = true;
		for (ParseTree tree : children) {
			ret = ret && tree.accept(this);
		}
		return ret;
	}

	protected boolean parseOR(List<ParseTree> children) {
		for (ParseTree tree : children) {
			if (tree.accept(this)) {
				return true;
			}
		}
		return false;
	}

	protected boolean parseGT(List<ParseTree> children, String val) {
		final int minExclusive = Integer.parseInt(val);
		return count(children) > minExclusive;
	}

}
