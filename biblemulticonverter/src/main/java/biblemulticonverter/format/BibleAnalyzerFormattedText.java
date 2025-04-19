package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.HyperlinkType;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;

public class BibleAnalyzerFormattedText implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Formatted Text Export format for Bible Analyzer",
			"",
			"Usage: BibleAnalyzerFormattedText <OutputFile>",
			"",
			"This format supports both bibles and dictionaries.",
			"",
			"It can be converted by the official module conversion tools, and is therefore",
			"preferred if another program uses the same input format, or if you do not want to use",
			"undocumented file formats. On the other hand, it does not support prologs, headlines,",
			"footnotes or toggleable grammar information - if you want those, you have to use the",
			"BibleAnalyzerDatabase export format."
	};

	private static final BibleAnalyzerBookInfo[] BOOK_INFO = {
			new BibleAnalyzerBookInfo("Gen", BookID.BOOK_Gen, 50),
			new BibleAnalyzerBookInfo("Exo", BookID.BOOK_Exod, 40),
			new BibleAnalyzerBookInfo("Lev", BookID.BOOK_Lev, 27),
			new BibleAnalyzerBookInfo("Num", BookID.BOOK_Num, 36),
			new BibleAnalyzerBookInfo("Deu", BookID.BOOK_Deut, 34),
			new BibleAnalyzerBookInfo("Jos", BookID.BOOK_Josh, 24),
			new BibleAnalyzerBookInfo("Jdg", BookID.BOOK_Judg, 21),
			new BibleAnalyzerBookInfo("Rth", BookID.BOOK_Ruth, 4),
			new BibleAnalyzerBookInfo("1Sa", BookID.BOOK_1Sam, 31),
			new BibleAnalyzerBookInfo("2Sa", BookID.BOOK_2Sam, 24),
			new BibleAnalyzerBookInfo("1Ki", BookID.BOOK_1Kgs, 22),
			new BibleAnalyzerBookInfo("2Ki", BookID.BOOK_2Kgs, 25),
			new BibleAnalyzerBookInfo("1Ch", BookID.BOOK_1Chr, 29),
			new BibleAnalyzerBookInfo("2Ch", BookID.BOOK_2Chr, 36),
			new BibleAnalyzerBookInfo("Ezr", BookID.BOOK_Ezra, 10),
			new BibleAnalyzerBookInfo("Neh", BookID.BOOK_Neh, 13),
			new BibleAnalyzerBookInfo("Est", BookID.BOOK_Esth, 10),
			new BibleAnalyzerBookInfo("Job", BookID.BOOK_Job, 42),
			new BibleAnalyzerBookInfo("Psa", BookID.BOOK_Ps, 150),
			new BibleAnalyzerBookInfo("Pro", BookID.BOOK_Prov, 31),
			new BibleAnalyzerBookInfo("Ecc", BookID.BOOK_Eccl, 12),
			new BibleAnalyzerBookInfo("Son", BookID.BOOK_Song, 8),
			new BibleAnalyzerBookInfo("Isa", BookID.BOOK_Isa, 66),
			new BibleAnalyzerBookInfo("Jer", BookID.BOOK_Jer, 52),
			new BibleAnalyzerBookInfo("Lam", BookID.BOOK_Lam, 5),
			new BibleAnalyzerBookInfo("Eze", BookID.BOOK_Ezek, 48),
			new BibleAnalyzerBookInfo("Dan", BookID.BOOK_Dan, 12),
			new BibleAnalyzerBookInfo("Hos", BookID.BOOK_Hos, 14),
			new BibleAnalyzerBookInfo("Joe", BookID.BOOK_Joel, 3),
			new BibleAnalyzerBookInfo("Amo", BookID.BOOK_Amos, 9),
			new BibleAnalyzerBookInfo("Oba", BookID.BOOK_Obad, 1),
			new BibleAnalyzerBookInfo("Jon", BookID.BOOK_Jonah, 4),
			new BibleAnalyzerBookInfo("Mic", BookID.BOOK_Mic, 7),
			new BibleAnalyzerBookInfo("Nah", BookID.BOOK_Nah, 3),
			new BibleAnalyzerBookInfo("Hab", BookID.BOOK_Hab, 3),
			new BibleAnalyzerBookInfo("Zep", BookID.BOOK_Zeph, 3),
			new BibleAnalyzerBookInfo("Hag", BookID.BOOK_Hag, 2),
			new BibleAnalyzerBookInfo("Zec", BookID.BOOK_Zech, 14),
			new BibleAnalyzerBookInfo("Mal", BookID.BOOK_Mal, 4),
			new BibleAnalyzerBookInfo("Mat", BookID.BOOK_Matt, 28),
			new BibleAnalyzerBookInfo("Mar", BookID.BOOK_Mark, 16),
			new BibleAnalyzerBookInfo("Luk", BookID.BOOK_Luke, 24),
			new BibleAnalyzerBookInfo("Joh", BookID.BOOK_John, 21),
			new BibleAnalyzerBookInfo("Act", BookID.BOOK_Acts, 28),
			new BibleAnalyzerBookInfo("Rom", BookID.BOOK_Rom, 16),
			new BibleAnalyzerBookInfo("1Co", BookID.BOOK_1Cor, 16),
			new BibleAnalyzerBookInfo("2Co", BookID.BOOK_2Cor, 13),
			new BibleAnalyzerBookInfo("Gal", BookID.BOOK_Gal, 6),
			new BibleAnalyzerBookInfo("Eph", BookID.BOOK_Eph, 6),
			new BibleAnalyzerBookInfo("Phi", BookID.BOOK_Phil, 4),
			new BibleAnalyzerBookInfo("Col", BookID.BOOK_Col, 4),
			new BibleAnalyzerBookInfo("1Th", BookID.BOOK_1Thess, 5),
			new BibleAnalyzerBookInfo("2Th", BookID.BOOK_2Thess, 3),
			new BibleAnalyzerBookInfo("1Ti", BookID.BOOK_1Tim, 6),
			new BibleAnalyzerBookInfo("2Ti", BookID.BOOK_2Tim, 4),
			new BibleAnalyzerBookInfo("Tit", BookID.BOOK_Titus, 3),
			new BibleAnalyzerBookInfo("Phm", BookID.BOOK_Phlm, 1),
			new BibleAnalyzerBookInfo("Heb", BookID.BOOK_Heb, 13),
			new BibleAnalyzerBookInfo("Jam", BookID.BOOK_Jas, 5),
			new BibleAnalyzerBookInfo("1Pe", BookID.BOOK_1Pet, 5),
			new BibleAnalyzerBookInfo("2Pe", BookID.BOOK_2Pet, 3),
			new BibleAnalyzerBookInfo("1Jo", BookID.BOOK_1John, 5),
			new BibleAnalyzerBookInfo("2Jo", BookID.BOOK_2John, 1),
			new BibleAnalyzerBookInfo("3Jo", BookID.BOOK_3John, 1),
			new BibleAnalyzerBookInfo("Jud", BookID.BOOK_Jude, 1),
			new BibleAnalyzerBookInfo("Rev", BookID.BOOK_Rev, 22),
	};

	private static final Map<BookID, BibleAnalyzerBookInfo> BOOK_INFO_BY_ID = new EnumMap<>(BookID.class);

	static {
		for (BibleAnalyzerBookInfo bi : BOOK_INFO) {
			BOOK_INFO_BY_ID.put(bi.id, bi);
		}
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String basename = new File(exportArgs[0]).getName().replaceAll("\\..*", "");
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[0]), StandardCharsets.UTF_8))) {
			exportBible(basename, bw, bible, false, false);
		}
	}

	protected final void exportBible(String basename, Writer w, Bible bible, boolean hasStrongs, boolean hasRMAC) throws Exception {
		for (Book book : bible.getBooks()) {
			if (book.getId() == BookID.DICTIONARY_ENTRY) {
				Writer dw = startDictionaryEntry(basename, book, w);
				book.getChapters().get(0).getProlog().accept(new BibleAnalyzerVisitor(dw));
				finishDictionaryEntry(dw);
				continue;
			}
			BibleAnalyzerBookInfo info = BOOK_INFO_BY_ID.get(book.getId());
			if (info == null) {
				System.out.println("WARNING: Skipping book " + book.getAbbr());
				continue;
			}
			List<Chapter> chapters = book.getChapters();
			if (chapters.size() > info.chapterCount) {
				System.out.println("WARNING: Merging chapter " + info.chapterCount + " to " + chapters.size() + " into " + book.getAbbr() + " " + info.chapterCount);
				chapters = new ArrayList<>(chapters.subList(0, info.chapterCount + 1));
				Chapter targetChapter = chapters.get(info.chapterCount - 1);
				for (int cn = info.chapterCount + 1; cn <= book.getChapters().size(); cn++) {
					Chapter sourceChapter = book.getChapters().get(cn - 1);
					if (sourceChapter.getProlog() != null)
						System.out.println("WARNING: Prolog for " + book.getAbbr() + " " + cn + " is lost");
					for (Verse v : sourceChapter.getVerses()) {
						String vn = v.getNumber();
						if (!vn.contains(","))
							vn = cn + "," + vn;
						Verse vv = new Verse(vn);
						v.accept(vv.getAppendVisitor());
						vv.finished();
						targetChapter.getVerses().add(vv);
					}
				}
			}
			int cnumber = 0;
			for (Chapter chapter : chapters) {
				cnumber++;
				int[] nextFootnote = { 1 };
				handleProlog(info.abbr, cnumber, chapter.getProlog());
				for (VirtualVerse vv : chapter.createVirtualVerses()) {
					Writer[] vw = startVerse(w, info.abbr, cnumber, vv);
					boolean firstVerse = true;
					for (Verse v : vv.getVerses()) {
						if (!firstVerse || !v.getNumber().equals("" + vv.getNumber())) {
							vw[0].append(" <b>(" + v.getNumber() + ")</b> ");
						}
						v.accept(new BibleAnalyzerVisitor(vw[0], vw[1], nextFootnote, book.getId().isNT(), "", hasStrongs, hasRMAC));
						firstVerse = false;
					}
					finishVerse(vw);
				}
				finishChapter();
			}
		}
	}

	protected Writer startDictionaryEntry(String basename, Book book, Writer w) throws Exception {
		if (!book.getAbbr().equals(book.getShortName())) {
			w.write(book.getAbbr() + "\t\u2192 <a href=\"/dict/" + basename + "/" + book.getShortName() + "\">" + book.getShortName() + "</a>\r\n");
		}
		w.write(book.getShortName() + "\t");
		return w;
	}

	protected void finishDictionaryEntry(Writer dw) throws Exception {
		dw.write("\r\n");
	}

	protected void handleProlog(String abbr, int cnumber, FormattedText prolog) throws Exception {
	}

	protected Writer[] startVerse(Writer w, String abbr, int cnumber, VirtualVerse vv) throws Exception {
		w.write(abbr + " " + cnumber + ":" + vv.getNumber() + "\t");
		return new Writer[] { w, null };
	}

	protected void finishVerse(Writer[] vw) throws Exception {
		vw[0].write("\r\n");
	}

	protected void finishChapter() throws Exception {
	}

	private static class BibleAnalyzerBookInfo {
		private final String abbr;
		private final BookID id;
		private final int chapterCount;

		public BibleAnalyzerBookInfo(String abbr, BookID id, int chapterCount) {
			this.abbr = abbr;
			this.id = id;
			this.chapterCount = chapterCount;
		}
	}

	protected static class BibleAnalyzerVisitor extends AbstractHTMLVisitor {

		private final Writer footnoteWriter;
		private final int[] nextFootnote;
		private final boolean nt;
		private final boolean hasStrongs;
		private final boolean hasRMAC;

		public BibleAnalyzerVisitor(Writer writer) {
			this(writer, null, null, false, "", false, false);
		}

		private BibleAnalyzerVisitor(Writer writer, Writer footnoteWriter, int[] nextFootnote, boolean nt, String suffix, boolean hasStrongs, boolean hasRMAC) {
			super(writer, suffix);
			this.footnoteWriter = footnoteWriter;
			this.nextFootnote = nextFootnote;
			this.hasStrongs = hasStrongs;
			this.hasRMAC = hasRMAC;
			this.nt = nt;
		}

		@Override
		public void visitText(String text) throws IOException {
			text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("[", "&#91;").replace("]", "&#93;");
			if (hasRMAC) {
				text = text.replace("{", "&#123;").replace("}", "&#125;");
			}
			writer.write(text);
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			if (kind == FormattingInstructionKind.WORDS_OF_JESUS) {
				writer.write("<r>");
				pushSuffix("</r>");
				return this;
			}
			return super.visitFormattingInstruction(kind);
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			// only for prologs or dictionary entries!
			writer.write("<h" + (depth < 6 ? depth : 6) + ">");
			pushSuffix("</h" + (depth < 6 ? depth : 6) + ">");
			return this;
		}

		public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
			Visitor<IOException> result = visitFootnote0();
			if (ofCrossReferences)
				result.visitText(FormattedText.XREF_MARKER);
			return result;
		}

		public Visitor<IOException> visitFootnote0() throws IOException {
			if (footnoteWriter != null) {
				writer.write("<fn>" + nextFootnote[0] + "</fn>");
				footnoteWriter.write("<fn>" + nextFootnote[0] + " ");
				nextFootnote[0]++;
				return new BibleAnalyzerVisitor(footnoteWriter, null, null, nt, "</fn>", false, false);
			} else {
				return null;
			}
		}

		@Override
		public Visitor<IOException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws IOException {
			if (firstBook == lastBook  && !lastVerse.equals("*")) {
				return visitCrossReference0(firstBookAbbr, firstBook, firstChapter, firstVerse, lastChapter, lastVerse);
			} else {
				return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "cross", "reference");
			}
		}

		public Visitor<IOException> visitCrossReference0(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			BibleAnalyzerBookInfo info = BOOK_INFO_BY_ID.get(book);
			if (info != null && firstVerse.matches("[0-9]+")) {
				writer.write("<a href=\"bible://" + info.abbr + " " + firstChapter + ":" + firstVerse + "\">");
				pushSuffix("</a>");
			} else {
				pushSuffix("");
			}
			return this;
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind lbk, int indent) throws IOException {
			LineBreakKind kind = lbk.toLineBreakKind(indent);
			switch (kind) {
			case NEWLINE:
				writer.write("<br>");
				return;
			case NEWLINE_WITH_INDENT:
				writer.write("<br><span class=\"indent\">&nbsp;&nbsp;&nbsp;</span>");
				return;
			case PARAGRAPH:
				writer.write("<br><br>");
				break;
			}
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws IOException {
			StringBuilder suffix = new StringBuilder();
			if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					suffix.append("[" + (hasStrongs ? Utils.formatStrongs(nt, i, strongsPrefixes, strongs, strongsSuffixes, "") : "" + strongs[i]) + "]");
				}
			}
			if (rmac != null && hasRMAC) {
				for (int i = 0; i < rmac.length; i++) {
					suffix.append("{" + rmac[i] + "}");
				}
			}
			pushSuffix(suffix.toString());
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			writer.write("<a href=\"dict/" + dictionary + "/" + entry + "\">");
			pushSuffix("</a>");
			return this;
		}
		@Override
		public Visitor<IOException> visitSpeaker(String labelOrStrongs) throws IOException {
			return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "speaker", labelOrStrongs);
		}
	}
}
