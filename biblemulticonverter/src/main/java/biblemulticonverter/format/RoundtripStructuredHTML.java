package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.format.AbstractStructuredHTMLVisitor.StructuredHTMLState;

public class RoundtripStructuredHTML extends RoundtripHTML {

	public static final String[] HELP_TEXT = Arrays.copyOf(RoundtripHTML.HELP_TEXT, RoundtripHTML.HELP_TEXT.length);

	static {
		HELP_TEXT[0] = "Roundtrip HTML Export using structured paragraphs";
		HELP_TEXT[2] = "Usage (export): RoundtripStructuredHTML <OutputDirectory>";
	};

	protected void exportChapter(Chapter ch, BufferedWriter bw, Properties xrefMap) throws IOException {
		List<StringWriter> footnotes = new ArrayList<StringWriter>();
		List<StructuredHTMLState> footnoteStates = new ArrayList<StructuredHTMLState>();
		StructuredHTMLState state = new StructuredHTMLState(bw);
		bw.write("<div class=\"biblehtmlcontent structured\">\n");
		if (ch.getProlog() != null) {
			bw.write("<!-- start content: prolog -->\n");
			ch.getProlog().accept(new RoundtripStructuredHTMLVisitor(state, footnotes, footnoteStates, "", xrefMap));
			bw.write("\n");
			bw.write("<!-- end content: prolog -->\n");
		}
		if (ch.getVerses().size() > 0) {
			for (Verse v : ch.getVerses()) {
				bw.write("<!-- start content: verse " + v.getNumber() + " -->");
				v.accept(new RoundtripStructuredHTMLVisitor(state, footnotes, footnoteStates, "<span class=\"vn\" id=\"v" + v.getNumber() + "\">" + v.getNumber() + "</span> ", xrefMap));
				bw.write("<!-- end content: verse -->\n");
			}
		}
		bw.write("<!-- start content: suffix -->");
		state.closeAll();
		bw.write("<!-- end content: suffix -->\n");
		bw.write("</div>\n");
		for (StructuredHTMLState footnoteState : footnoteStates) {
			footnoteState.closeAll();
		}
		if (footnotes.size() > 0) {
			bw.write("<div class=\"biblehtmlcontent footnotes\">\n");
			for (StringWriter footnote : footnotes) {
				bw.write(footnote.toString() + "</div>\n");
			}
			bw.write("</div>\n");
		}
	}

	protected void parseChapter(Chapter ch, BufferedReader br, List<FormattedText.Visitor<RuntimeException>> footnotes) throws IOException {
		String line;
		ParseState state = new ParseState(ParseState.State.NONE);
		while ((line = br.readLine()) != null) {
			if (line.equals("<!-- start content: prolog -->")) {
				line = br.readLine();
				FormattedText prolog = new FormattedText();
				parseStructuredLine(state, prolog.getAppendVisitor(), line, footnotes);
				ch.setProlog(prolog);
				line = br.readLine();
				if (!line.equals("<!-- end content: prolog -->"))
					throw new IOException(line);
			} else if (line.startsWith("<!-- start content: verse ")) {
				if (!line.endsWith("<!-- end content: verse -->"))
					throw new IOException(line);
				line = line.substring(26, line.length() - 27);
				int pos = line.indexOf(" -->");
				String vnum = line.substring(0, pos);
				Verse v = new Verse(vnum);
				line = line.substring(pos + 4).replace("<span class=\"vn\" id=\"v" + vnum + "\">", "<span class=\"vn\">");
				parseStructuredLine(state, v.getAppendVisitor(), line, footnotes);
				ch.getVerses().add(v);
			} else if (line.equals("<div class=\"biblehtmlcontent footnotes\">")) {
				for (int i = 0; i < footnotes.size(); i++) {
					line = br.readLine();
					String prefix = "<div class=\"fn\"><p><sup class=\"fnt\"><a name=\"fn" + (i + 1) + "\" href=\"#fnm" + (i + 1) + "\">" + (i + 1) + "</a></sup> ";
					if (!line.startsWith(prefix) || !line.endsWith("</div>"))
						throw new IOException(line);
					line = line.substring(prefix.length(), line.length() - 6);
					ParseState fnState = new ParseState(ParseState.State.PARAGRAPH);
					parseStructuredLine(fnState, footnotes.get(i), line, null);
					if (fnState.state != ParseState.State.NONE)
						throw new IOException(line);
				}
				line = br.readLine();
				if (!line.equals("</div>"))
					throw new IOException(line);
			} else if (line.startsWith("<!-- start content: suffix -->") && line.endsWith("<!-- end content: suffix -->")) {
				String suffix = line.substring(30, line.length() - 28);
				if (suffix.equals("</p>") && state.state == ParseState.State.PARAGRAPH) {
					state.state = ParseState.State.NONE;
				} else if (suffix.equals("</td></tr></table>") && state.state == ParseState.State.TABLECELL) {
					state.state = ParseState.State.NONE;
				} else if (suffix.isEmpty() && state.state == ParseState.State.NONE) {
					// all fine
				} else {
					throw new IOException(suffix);
				}
			} else if (line.startsWith("<!-- start ")) {
				throw new IOException(line);
			}
		}
		if (state.state != ParseState.State.NONE)
			throw new IOException(state.toString());
	}

	protected void parseStructuredLine(ParseState state, Visitor<RuntimeException> visitor, String line, List<Visitor<RuntimeException>> footnotes) throws IOException {
		int pos = 0;
		while (pos != line.length()) {
			if (state.state == ParseState.State.NONE) {
				if (line.startsWith("<table><tr><td class=\"col", pos)) {
					pos += 11; // leave td to be parsed later
					state.state = ParseState.State.TABLEROW;
				} else if (line.startsWith("<p>", pos)) {
					pos = parseLine(visitor, line, pos + 3, footnotes);
					state.state = ParseState.State.PARAGRAPH;
				} else if (line.startsWith("<p class=\"para-", pos) && line.startsWith("\"", pos + 16)) {
					ExtendedLineBreakKind ekind = ExtendedLineBreakKind.fromChar(Character.toUpperCase(line.charAt(pos + 15)));
					int indent = 0;
					pos += 17;
					if (line.startsWith(" style=\"text-indent: ", pos)) {
						pos += 21;
						int ePos = line.indexOf("em;\"", pos);
						indent = Integer.parseInt(line.substring(pos, ePos));
						pos = ePos + 4;
					} else if (line.startsWith(" style=\"text-align: center;\"", pos)) {
						indent = ExtendedLineBreakKind.INDENT_CENTER;
						pos += 28;
					} else if (line.startsWith(" style=\"text-align: right;\"", pos)) {
						indent = ExtendedLineBreakKind.INDENT_RIGHT_JUSTIFIED;
						pos += 27;
					}
					if (!line.startsWith(">", pos))
						throw new IOException(line);
					visitor.visitLineBreak(ekind, indent);
					pos = parseLine(visitor, line, pos + 1, footnotes);
					state.state = ParseState.State.PARAGRAPH;
				} else if (line.startsWith("<h", pos)) {
					int ePos = line.indexOf("</h", pos);
					if (!line.startsWith(">", ePos + 4))
						throw new IOException();
					String part = line.substring(pos, ePos + 5);
					pos = parseLine(visitor, part, 0, footnotes);
					if (pos != part.length())
						throw new IOException(part.substring(pos));
					pos = ePos + 5;
				} else {
					throw new IOException(line.substring(pos));
				}
			} else if (state.state == ParseState.State.PARAGRAPH) {
				if (line.startsWith("</p>", pos)) {
					pos += 4;
					state.state = ParseState.State.NONE;
				} else if (line.startsWith("</", pos)) {
					throw new IOException(line);
				} else {
					pos = parseLine(visitor, line, pos, footnotes);
				}
			} else if (state.state == ParseState.State.TABLEROW) {
				if (line.startsWith("</tr></table>", pos)) {
					pos += 13;
					state.state = ParseState.State.NONE;
				} else if (line.startsWith("</tr><tr>", pos)) {
					pos += 9;
				} else if (line.startsWith("<td class=\"col", pos)) {
					pos += 14;
					int ePos = line.indexOf('"', pos);
					int colnum = Integer.parseInt(line.substring(pos, ePos)), indent = 0;
					pos = ePos + 1;
					if (line.startsWith(" colspan=\"", pos)) {
						pos += 10;
						ePos = line.indexOf('"', pos);
						indent = Integer.parseInt(line.substring(pos, ePos));
						pos = ePos + 1;
					} else if (line.startsWith(" style=\"text-align: center;\"", pos)) {
						indent = ExtendedLineBreakKind.INDENT_CENTER;
						pos += 28;
					} else if (line.startsWith(" style=\"text-align: right;\"", pos)) {
						indent = ExtendedLineBreakKind.INDENT_RIGHT_JUSTIFIED;
						pos += 27;
					}
					if (!line.startsWith(">", pos))
						throw new IOException(line);
					visitor.visitLineBreak(colnum == 1 ? ExtendedLineBreakKind.TABLE_ROW_FIRST_CELL : ExtendedLineBreakKind.TABLE_ROW_NEXT_CELL, indent);
					pos = parseLine(visitor, line, pos + 1, footnotes);
					state.state = ParseState.State.TABLECELL;
				} else {
					throw new IOException(line);
				}
			} else if (state.state == ParseState.State.TABLECELL) {
				if (line.startsWith("</td>", pos)) {
					pos += 5;
					state.state = ParseState.State.TABLEROW;
				} else if (line.startsWith("</", pos)) {
					throw new IOException(line);
				} else {
					pos = parseLine(visitor, line, pos, footnotes);
				}
			} else {
				throw new IOException(state.state.toString());
			}
		}
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return true;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private static class ParseState {
		private State state = State.NONE;

		private ParseState(State state) {
			this.state = state;
		}

		private static enum State {
			NONE, TABLEROW, TABLECELL, PARAGRAPH
		}
	}

	private static class RoundtripStructuredHTMLVisitor extends AbstractRoundtripHTMLVisitor {

		private final StructuredHTMLState state;
		private final String prefix;
		private final List<StringWriter> footnotes;
		private final List<StructuredHTMLState> footnoteStates;

		private RoundtripStructuredHTMLVisitor(StructuredHTMLState state, List<StringWriter> footnotes, List<StructuredHTMLState> footnoteStates, String prefix, Properties xrefMap) {
			super(state.getWriter(), "", xrefMap);
			this.state = state;
			this.prefix = prefix;
			this.footnotes = footnotes;
			this.footnoteStates = footnoteStates;
		}

		@Override
		protected void prepareForInlineOutput(boolean endTag) throws IOException {
			if (!endTag) {
				state.ensureOpen();
			} else if (suffixStack.size() == 2) {
				state.closeHeadline();
			}
		}

		@Override
		public void visitStart() throws IOException {
			if (suffixStack.size() == 1) {
				prepareForInlineOutput(false);
				writer.write(prefix);
			}
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			if (suffixStack.size() == 1) {
				AbstractStructuredHTMLVisitor.startHeadline(state);
			}
			return super.visitHeadline(depth);
		}

		@Override
		protected String getNextFootnoteTarget() {
			return "#fn" + (footnotes.size() + 1);
		}

		@Override
		public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
			StringWriter fnw = new StringWriter();
			StructuredHTMLState fns = new StructuredHTMLState(fnw);
			footnotes.add(fnw);
			footnoteStates.add(fns);
			int cnt = footnotes.size();
			state.ensureOpen();
			writer.write(ofCrossReferences ? "<sup class=\"fxm\">" : "<sup class=\"fnm\">");
			writer.write("<a name=\"fnm" + cnt + "\" href=\"#fn" + cnt + "\">" + cnt + "</a></sup>");
			fnw.write("<div class=\"fn\">");
			fns.ensureOpen();
			fnw.write("<sup class=\"fnt\"><a name=\"fn" + cnt + "\" href=\"#fnm" + cnt + "\">" + cnt + "</a></sup> ");
			return new RoundtripStructuredHTMLVisitor(fns, null, null, "", xrefMap);
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws IOException {
			if (suffixStack.size() == 1 && kind != ExtendedLineBreakKind.NEWLINE) {
				AbstractStructuredHTMLVisitor.visitLineBreak(this, state, kind, indent);
			} else {
				super.visitLineBreak(kind, indent);
			}
		}
	}
}
