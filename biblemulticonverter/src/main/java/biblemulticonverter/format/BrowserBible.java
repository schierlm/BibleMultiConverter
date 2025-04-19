package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.HyperlinkType;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;

public class BrowserBible implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export format for The Browser Bible 3 by Digital Bible Society",
			"",
			"Usage: BrowserBible <outdir>",
			"",
			"The output directory will contain info.json, verses.txt and biblemulticonverter.js.",
			"Copy biblemulticonverter.js into /tools/generators/ directory and your output",
			"directory into /input/, then rebuild the texts. You may need to edit info.json.",
			"",
			"You may also optionally add a UTF-8 formatted about.html into your output",
			"directory, which will be shown in case you click the little (i) icon."
	};

	private static final Map<BookID, String> BOOK_NAMES = new EnumMap<>(BookID.class);

	static {
		BOOK_NAMES.put(BookID.METADATA, "FR");
		BOOK_NAMES.put(BookID.INTRODUCTION, "IN");
		BOOK_NAMES.put(BookID.BOOK_Gen, "GN");
		BOOK_NAMES.put(BookID.BOOK_Exod, "EX");
		BOOK_NAMES.put(BookID.BOOK_Lev, "LV");
		BOOK_NAMES.put(BookID.BOOK_Num, "NU");
		BOOK_NAMES.put(BookID.BOOK_Deut, "DT");
		BOOK_NAMES.put(BookID.BOOK_Josh, "JS");
		BOOK_NAMES.put(BookID.BOOK_Judg, "JG");
		BOOK_NAMES.put(BookID.BOOK_Ruth, "RT");
		BOOK_NAMES.put(BookID.BOOK_1Sam, "S1");
		BOOK_NAMES.put(BookID.BOOK_2Sam, "S2");
		BOOK_NAMES.put(BookID.BOOK_1Kgs, "K1");
		BOOK_NAMES.put(BookID.BOOK_2Kgs, "K2");
		BOOK_NAMES.put(BookID.BOOK_1Chr, "R1");
		BOOK_NAMES.put(BookID.BOOK_2Chr, "R2");
		BOOK_NAMES.put(BookID.BOOK_Ezra, "ER");
		BOOK_NAMES.put(BookID.BOOK_Neh, "NH");
		BOOK_NAMES.put(BookID.BOOK_Esth, "ET");
		BOOK_NAMES.put(BookID.BOOK_Job, "JB");
		BOOK_NAMES.put(BookID.BOOK_Ps, "PS");
		BOOK_NAMES.put(BookID.BOOK_Prov, "PR");
		BOOK_NAMES.put(BookID.BOOK_Eccl, "EC");
		BOOK_NAMES.put(BookID.BOOK_Song, "SS");
		BOOK_NAMES.put(BookID.BOOK_Isa, "IS");
		BOOK_NAMES.put(BookID.BOOK_Jer, "JR");
		BOOK_NAMES.put(BookID.BOOK_Lam, "LM");
		BOOK_NAMES.put(BookID.BOOK_Ezek, "EK");
		BOOK_NAMES.put(BookID.BOOK_Dan, "DN");
		BOOK_NAMES.put(BookID.BOOK_Hos, "HS");
		BOOK_NAMES.put(BookID.BOOK_Joel, "JL");
		BOOK_NAMES.put(BookID.BOOK_Amos, "AM");
		BOOK_NAMES.put(BookID.BOOK_Obad, "OB");
		BOOK_NAMES.put(BookID.BOOK_Jonah, "JH");
		BOOK_NAMES.put(BookID.BOOK_Mic, "MC");
		BOOK_NAMES.put(BookID.BOOK_Nah, "NM");
		BOOK_NAMES.put(BookID.BOOK_Hab, "HK");
		BOOK_NAMES.put(BookID.BOOK_Zeph, "ZP");
		BOOK_NAMES.put(BookID.BOOK_Hag, "HG");
		BOOK_NAMES.put(BookID.BOOK_Zech, "ZC");
		BOOK_NAMES.put(BookID.BOOK_Mal, "ML");
		BOOK_NAMES.put(BookID.BOOK_Tob, "TB");
		BOOK_NAMES.put(BookID.BOOK_Jdt, "JT");
		BOOK_NAMES.put(BookID.BOOK_EsthGr, "EG");
		BOOK_NAMES.put(BookID.BOOK_AddEsth, "AE");
		BOOK_NAMES.put(BookID.BOOK_Wis, "WS");
		BOOK_NAMES.put(BookID.BOOK_Sir, "SR");
		BOOK_NAMES.put(BookID.BOOK_Bar, "BR");
		BOOK_NAMES.put(BookID.BOOK_EpJer, "LJ");
		BOOK_NAMES.put(BookID.BOOK_PrAzar, "PA");
		BOOK_NAMES.put(BookID.BOOK_Sus, "SN");
		BOOK_NAMES.put(BookID.BOOK_Bel, "BL");
		BOOK_NAMES.put(BookID.BOOK_1Macc, "M1");
		BOOK_NAMES.put(BookID.BOOK_2Macc, "M2");
		BOOK_NAMES.put(BookID.BOOK_1Esd, "E1");
		BOOK_NAMES.put(BookID.BOOK_PrMan, "PN");
		BOOK_NAMES.put(BookID.BOOK_AddPs, "PX");
		BOOK_NAMES.put(BookID.BOOK_3Macc, "M3");
		BOOK_NAMES.put(BookID.BOOK_2Esd, "E2");
		BOOK_NAMES.put(BookID.BOOK_4Macc, "M4");
		BOOK_NAMES.put(BookID.BOOK_Odes, "OS");
		BOOK_NAMES.put(BookID.BOOK_PssSol, "SP");
		BOOK_NAMES.put(BookID.BOOK_EpLao, "LL");
		BOOK_NAMES.put(BookID.BOOK_1En, "N1");
		BOOK_NAMES.put(BookID.BOOK_AddDan, "AD");
		BOOK_NAMES.put(BookID.BOOK_Jub, "JE");
		BOOK_NAMES.put(BookID.BOOK_DanGr, "DG");
		BOOK_NAMES.put(BookID.BOOK_Matt, "MT");
		BOOK_NAMES.put(BookID.BOOK_Mark, "MK");
		BOOK_NAMES.put(BookID.BOOK_Luke, "LK");
		BOOK_NAMES.put(BookID.BOOK_John, "JN");
		BOOK_NAMES.put(BookID.BOOK_Acts, "AC");
		BOOK_NAMES.put(BookID.BOOK_Rom, "RM");
		BOOK_NAMES.put(BookID.BOOK_1Cor, "C1");
		BOOK_NAMES.put(BookID.BOOK_2Cor, "C2");
		BOOK_NAMES.put(BookID.BOOK_Gal, "GL");
		BOOK_NAMES.put(BookID.BOOK_Eph, "EP");
		BOOK_NAMES.put(BookID.BOOK_Phil, "PP");
		BOOK_NAMES.put(BookID.BOOK_Col, "CL");
		BOOK_NAMES.put(BookID.BOOK_1Thess, "H1");
		BOOK_NAMES.put(BookID.BOOK_2Thess, "H2");
		BOOK_NAMES.put(BookID.BOOK_1Tim, "T1");
		BOOK_NAMES.put(BookID.BOOK_2Tim, "T2");
		BOOK_NAMES.put(BookID.BOOK_Titus, "TT");
		BOOK_NAMES.put(BookID.BOOK_Phlm, "PM");
		BOOK_NAMES.put(BookID.BOOK_Heb, "HB");
		BOOK_NAMES.put(BookID.BOOK_Jas, "JM");
		BOOK_NAMES.put(BookID.BOOK_1Pet, "P1");
		BOOK_NAMES.put(BookID.BOOK_2Pet, "P2");
		BOOK_NAMES.put(BookID.BOOK_1John, "J1");
		BOOK_NAMES.put(BookID.BOOK_2John, "J2");
		BOOK_NAMES.put(BookID.BOOK_3John, "J3");
		BOOK_NAMES.put(BookID.BOOK_Jude, "JD");
		BOOK_NAMES.put(BookID.BOOK_Rev, "RV");
		BOOK_NAMES.put(BookID.APPENDIX, "BK");
		BOOK_NAMES.put(BookID.INTRODUCTION_OT, "XA");
		BOOK_NAMES.put(BookID.INTRODUCTION_NT, "XB");
		BOOK_NAMES.put(BookID.APPENDIX_OTHER, "OH");
		// BOOK_NAMES.put(BookID.BOOK_XXC, "XC");
		// BOOK_NAMES.put(BookID.BOOK_XXD, "XD");
		// BOOK_NAMES.put(BookID.BOOK_XXE, "XE");
		// BOOK_NAMES.put(BookID.BOOK_XXF, "XF");
		// BOOK_NAMES.put(BookID.BOOK_XXG, "XG");
		BOOK_NAMES.put(BookID.APPENDIX_GLOSSARY, "GS");
		BOOK_NAMES.put(BookID.APPENDIX_CONCORDANCE, "CN");
		BOOK_NAMES.put(BookID.APPENDIX_TOPICAL, "TX");
		BOOK_NAMES.put(BookID.APPENDIX_NAMES, "NX");
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File directory = new File(exportArgs[0]);
		if (!directory.exists())
			directory.mkdirs();
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, "info.json")), StandardCharsets.UTF_8))) {
			bw.write("{\n" +
					" \"name\": \"" + bible.getName().replace("\\", "\\\\").replace("\"", "\\\"") + "\",\n" +
					" \"nameEnglish\": \"\",\n" +
					" \"langName\": \"Deutsch\",\n" +
					" \"langNameEnglish\": \"German\",\n" +
					" \"abbr\": \"XXX\",\n" +
					" \"id\": \"deu_xxx\",\n" +
					" \"lang\": \"deu\",\n" +
					" \"generator\": \"biblemulticonverter\",\n" +
					" \"dir\": \"ltr\"\n" +
					"}");
		}
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, "biblemulticonverter.js")), StandardCharsets.UTF_8))) {
			Reader r = new InputStreamReader(RoundtripHTML.class.getResourceAsStream("/BrowserBible/biblemulticonverter.js"), StandardCharsets.UTF_8);
			char[] buf = new char[4096];
			int len;
			while ((len = r.read(buf)) != -1) {
				bw.write(buf, 0, len);
			}
		}
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, "verses.txt")), StandardCharsets.UTF_8))) {
			for (Book book : bible.getBooks()) {
				String bookAbbr = BOOK_NAMES.get(book.getId());
				if (bookAbbr == null) {
					System.out.println("WARNING: Skipping book " + book.getAbbr());
					continue;
				}
				int cnumber = 0;
				String bookHeading = "<div class=\"mt\">" + book.getLongName() + "</div>";
				for (Chapter chapter : book.getChapters()) {
					int[] nextFootnote = new int[] { 1 };
					cnumber++;
					if (chapter.getProlog() != null) {
						bw.write(bookAbbr + "\t" + book.getShortName() + "\t" + cnumber + "\t\t" + bookHeading + "\t");
						bookHeading = "";
						StringWriter fnw = new StringWriter();
						chapter.getProlog().accept(new BrowserBibleVisitor(bw, fnw, nextFootnote, book.getId().isNT(), ""));
						bw.write("\t" + fnw.toString() + "\n");
					}
					for (VirtualVerse vv : chapter.createVirtualVerses()) {
						String vnumber = "" + vv.getNumber();
						boolean autoNumber = vnumber.equals(vv.getVerses().get(0).getNumber());
						if (!autoNumber)
							vnumber = "!" + vnumber;
						bw.write(bookAbbr + "\t" + book.getShortName() + "\t" + cnumber + "\t" + vnumber + "\t" + bookHeading);
						bookHeading = "";
						StringWriter fnw = new StringWriter();
						BrowserBibleVisitor bbv = new BrowserBibleVisitor(bw, fnw, nextFootnote, book.getId().isNT(), "");
						for (Headline hl : vv.getHeadlines()) {
							hl.accept(bbv.visitHeadline(hl.getDepth()));
						}
						bw.write("\t");
						for (Verse v : vv.getVerses()) {
							if (!autoNumber) {
								bw.write("<span class=\"verse-num\">" + v.getNumber() + "&nbsp;</span>");
							}
							autoNumber = false;
							bbv.nextVerse();
							v.accept(bbv);
						}
						bbv.visitEnd();
						bw.write("\t" + fnw.toString() + "\n");
					}
				}
			}
		}
	}

	private static class BrowserBibleVisitor extends AbstractHTMLVisitor {

		private final Writer footnoteWriter;
		private final int[] nextFootnote;
		private final boolean nt;

		protected BrowserBibleVisitor(Writer writer, Writer footnoteWriter, int[] nextFootnote, boolean nt, String suffix) {
			super(writer, suffix);
			this.footnoteWriter = footnoteWriter;
			this.nextFootnote = nextFootnote;
			this.nt = nt;
		}

		protected void nextVerse() {
			pushSuffix("");
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			writer.write("<div class=\"s s" + depth + "\">");
			pushSuffix("</div>");
			return this;
		}

		@Override
		protected String getNextFootnoteTarget() {
			return "#footnote-" + nextFootnote[0];
		}

		@Override
		public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
			Visitor<IOException> result = visitFootnote0();
			if (ofCrossReferences)
				result.visitText(FormattedText.XREF_MARKER);
			return result;
		}

		public Visitor<IOException> visitFootnote0() throws IOException {
			writer.write("<span class=\"note\" id=\"note-" + nextFootnote[0] + "\"><a class=\"key\" href=\"#footnote-" + nextFootnote[0] + "\">" + nextFootnote[0] + "</a></span>");
			footnoteWriter.write("<span class=\"footnote\" id=\"footnote-" + nextFootnote[0] + "\"><span class=\"key\">" + nextFootnote[0] + "</span><a class=\"backref\" href=\"#note-" + nextFootnote[0] + "\">#</a><span class=\"text\">");
			nextFootnote[0]++;
			return new BrowserBibleVisitor(footnoteWriter, null, null, nt, "</span></span>");
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
			String bookName = BOOK_NAMES.get(book);
			if (bookName != null && firstVerse.matches("[0-9]+")) {
				writer.write("<span class=\"bibleref\" data-id=\"" + bookName + firstChapter + "_" + firstVerse + "\">");
				pushSuffix("</span>");
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
				if (footnoteWriter == null)
					writer.write("<br><br>");
				else
					writer.write("</div><div class=\"p\">");
				break;
			}
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws IOException {
			writer.write("<l");
			if (strongs != null) {
				writer.write(" s=\"");
				for (int i = 0; i < strongs.length; i++) {
					if (i > 0)
						writer.write(" ");
					writer.write(Utils.formatStrongs(nt, i, strongsPrefixes, strongs, strongsSuffixes, ""));
				}
				writer.write("\"");
			}
			if (rmac != null) {
				writer.write(" m=\"");
				for (int i = 0; i < rmac.length; i++) {
					if (i > 0)
						writer.write(" ");
					writer.write(rmac[i]);
				}
				writer.write("\"");
			}
			writer.write(">");
			pushSuffix("</l>");
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			// no dictionary support yet :-(
			pushSuffix("");
			return this;
		}
		@Override
		public Visitor<IOException> visitSpeaker(String labelOrStrongs) throws IOException {
			return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "speaker", labelOrStrongs);
		}
	}
}
