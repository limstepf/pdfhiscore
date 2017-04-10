package ch.unibe.scg.pdfhiscore;

import java.util.BitSet;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

/**
 * Simple error listener.
 */
public class ErrorListener implements ANTLRErrorListener {

	protected boolean fail = false;

	/**
	 * Creates a new error listener.
	 */
	public ErrorListener() {

	}

	/**
	 * Checks whether an expression was invalid.
	 *
	 * @return {@code true} if the expression was invalid, {@code false}
	 * otherwise.
	 */
	public boolean isFail() {
		return this.fail;
	}

	protected void setFail(boolean fail) {
		this.fail = fail;
	}

	@Override
	public void syntaxError(Recognizer<?, ?> rcgnzr, Object o, int i, int i1, String string, RecognitionException re) {
		setFail(true);
	}

	@Override
	public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean bln, BitSet bitset, ATNConfigSet atncs) {
		setFail(true);
	}

	@Override
	public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitset, ATNConfigSet atncs) {
		setFail(true);
	}

	@Override
	public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atncs) {
		setFail(true);
	}

}
