package biblemulticonverter.format;

import java.io.IOException;
import java.io.Writer;

import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.format.AbstractStructuredHTMLVisitor.StructuredHTMLState;

/**
 * Helper class for converting {@link FormattedText} to structured HTML. Unlike
 * {@link AbstractHTMLVisitor}, this also implements headlines and line breaks
 * (including tables and paragraph styles using inline CSS). State is tracked in
 * a StructuredHTMLState object that may be passed to subsequent visitors or
 * "closed".
 */
public abstract class AbstractStructuredHTMLVisitor extends AbstractHTMLVisitor {

	protected final StructuredHTMLState state;

	protected AbstractStructuredHTMLVisitor(StructuredHTMLState state) {
		super(state.getWriter(), "");
		this.state = state;
	}

	@Override
	protected void prepareForInlineOutput(boolean endTag) throws IOException {
		if (!endTag) {
			state.ensureOpen();
		} else if (suffixStack.size() == 2) {
			state.closeHeadline();
		}
	}

	public static void startHeadline(StructuredHTMLState state) throws IOException {
		state.closeAll();
		state.openState = StructuredHTMLOpenState.Headline;
	}

	@Override
	public Visitor<IOException> visitHeadline(int depth) throws IOException {
		if (suffixStack.size() == 1) {
			startHeadline(state);
		}
		writer.write("<h" + (depth < 6 ? depth : 6) + ">");
		pushSuffix("</h" + (depth < 6 ? depth : 6) + ">");
		return this;
	}

	public static void visitLineBreak(AbstractHTMLVisitor v, StructuredHTMLState state, ExtendedLineBreakKind kind, int indent) throws IOException {
		if (kind == ExtendedLineBreakKind.TABLE_ROW_FIRST_CELL || kind == ExtendedLineBreakKind.TABLE_ROW_NEXT_CELL) {
			if (kind == ExtendedLineBreakKind.TABLE_ROW_FIRST_CELL && state.openState == StructuredHTMLOpenState.TableCell) {
				v.writer.write("</td></tr><tr>");
				state.colNumber = 1;
			} else if (state.openState == StructuredHTMLOpenState.TableCell) {
				v.writer.write("</td>");
			} else {
				state.closeAll();
				v.writer.write("<table><tr>");
				state.openState = StructuredHTMLOpenState.TableCell;
				state.colNumber = 1;
			}
			if (state.colNumber == 1 && kind == ExtendedLineBreakKind.TABLE_ROW_NEXT_CELL) {
				System.out.println("WARNING: Table cell without table row start");
				state.colNumber++; // for roundtrip formats
			}
			v.writer.write("<td class=\"col" + state.colNumber + "\"");
			state.colNumber++;
			if (indent > 0) {
				v.writer.write(" colspan=\"" + indent + "\"");
				state.colNumber += indent - 1;
			} else if (indent == ExtendedLineBreakKind.INDENT_CENTER) {
				v.writer.write(" style=\"text-align: center;\"");
			} else if (indent == ExtendedLineBreakKind.INDENT_RIGHT_JUSTIFIED) {
				v.writer.write(" style=\"text-align: right;\"");
			}
			v.writer.write(">");
		} else {
			state.closeAll();
			state.openState = StructuredHTMLOpenState.Para;
			v.writer.write("<p class=\"para-" + Character.toLowerCase(kind.getCode()) + "\"");
			if (indent > 0) {
				v.writer.write(" style=\"text-indent: " + indent + "em;\"");
			} else if (indent == ExtendedLineBreakKind.INDENT_CENTER) {
				v.writer.write(" style=\"text-align: center;\"");
			} else if (indent == ExtendedLineBreakKind.INDENT_RIGHT_JUSTIFIED) {
				v.writer.write(" style=\"text-align: right;\"");
			}
			v.writer.write(">");
		}
	}

	@Override
	public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws IOException {
		if (suffixStack.size() == 1 && kind != ExtendedLineBreakKind.NEWLINE) {
			visitLineBreak(this, state, kind, indent);
		} else {
			prepareForInlineOutput(false);
			if (!kind.isSameParagraph()) {
				writer.write("<br><br>");
			} else {
				writer.write("<br>");
			}
			if (indent > 0) {
				writer.write("<span class=\"indent\">");
				for (int i = 0; i < indent; i++) {
					writer.write("&nbsp;&nbsp;&nbsp;");
				}
				writer.write("</span>");
			}
		}
	}

	public static class StructuredHTMLState {
		private final Writer writer;
		private StructuredHTMLOpenState openState = StructuredHTMLOpenState.None;
		private int colNumber = 0;

		public StructuredHTMLState(Writer writer) {
			this.writer = writer;
		}

		public Writer getWriter() {
			return writer;
		}

		public void ensureOpen() throws IOException {
			if (openState == StructuredHTMLOpenState.None) {
				writer.write("<p>");
				openState = StructuredHTMLOpenState.Para;
			}
		}

		public void closeHeadline() {
			if (openState == StructuredHTMLOpenState.Headline) {
				openState = StructuredHTMLOpenState.None;
			}
		}

		public void closeAll() throws IOException {
			switch (openState) {
			case Para:
				writer.write("</p>");
				break;
			case TableCell:
				writer.write("</td></tr></table>");
				break;
			case None:
			case Headline:
				break;
			}
			openState = StructuredHTMLOpenState.None;
		}
	}

	private static enum StructuredHTMLOpenState {
		None, TableCell, Para, Headline
	}
}
