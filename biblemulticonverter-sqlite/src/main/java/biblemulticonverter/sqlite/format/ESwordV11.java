package biblemulticonverter.sqlite.format;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

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
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.format.AbstractHTMLVisitor;
import biblemulticonverter.format.ExportFormat;
import biblemulticonverter.format.StrippedDiffable;
import biblemulticonverter.sqlite.SQLiteModuleRegistry;

public class ESwordV11 implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export format for new (version 11) E-Sword modules",
			"",
			"Usage: ESwordV11 <outfile>",
	};

	private static BookID[] BOOK_ORDER = new BookID[78];
	private static Map<BookID, String> BOOK_ABBR_MAP = new EnumMap<>(BookID.class);

	static {
		for (int i = 1; i <= 66; i++) {
			BOOK_ORDER[i - 1] = BookID.fromZefId(i);
		}
		BookID[] extra = new BookID[] {
				BookID.BOOK_Tob, BookID.BOOK_Jdt, BookID.BOOK_Wis, BookID.BOOK_Sir, BookID.BOOK_Bar,
				BookID.BOOK_1Macc, BookID.BOOK_2Macc, BookID.BOOK_1Esd, BookID.BOOK_2Esd,
				BookID.BOOK_3Macc, BookID.BOOK_4Macc, BookID.BOOK_PrMan
		};
		System.arraycopy(extra, 0, BOOK_ORDER, 66, extra.length);
		for (int i = 0; i < BOOK_ORDER.length; i++) {
			BOOK_ABBR_MAP.put(BOOK_ORDER[i], BOOK_ORDER[i].getEnglishName());
		}
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		new StrippedDiffable() {
			public void mergeIntroductionPrologs(Bible bible) {
				super.mergeIntroductionPrologs(bible);
			};
		}.mergeIntroductionPrologs(bible);

		String outfile = exportArgs[0];
		if (!outfile.endsWith(".bbli"))
			outfile += ".bbli";
		boolean hasStrongs = false, hasOT = false, hasNT = false, hasDC = false;
		for (Book bk : bible.getBooks()) {
			int idx = Arrays.asList(BOOK_ORDER).indexOf(bk.getId());
			if (idx == -1)
				continue;
			if (bk.getId().isDeuterocanonical())
				hasDC = true;
			else if (bk.getId().isNT())
				hasNT = true;
			else
				hasOT = true;
		}
		for (Book bk : bible.getBooks()) {
			for (Chapter ch : bk.getChapters()) {
				for (Verse vv : ch.getVerses()) {
					String elementTypes = vv.getElementTypes(Integer.MAX_VALUE);
					if (elementTypes.contains("g")) {
						hasStrongs = true;
						break;
					}
				}
			}
		}
		new File(outfile).delete();
		SqlJetDb db = SQLiteModuleRegistry.openDB(new File(outfile), true);
		db.getOptions().setAutovacuum(true);
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		db.getOptions().setUserVersion(0);
		db.createTable("CREATE TABLE Details (Title NVARCHAR (100), Abbreviation NVARCHAR (50), Information TEXT, Version INT, OldTestament BOOL, NewTestament BOOL, Apocrypha BOOL, Strongs BOOL, RightToLeft BOOL)");
		db.createTable("CREATE TABLE Bible (Book INT, Chapter INT, Verse INT, Scripture TEXT)");
		db.createTable("CREATE TABLE Notes (Book INT, Chapter INT, Verse INT, ID TEXT, Note TEXT)");
		db.createIndex("CREATE INDEX BookChapterVerseIndex ON Bible (Book, Chapter, Verse)");
		db.createIndex("CREATE INDEX BookChapterVerseIDIndex ON Notes (Book, Chapter, Verse, ID)");
		ISqlJetTable detailsTable = db.getTable("Details");
		detailsTable.insert(bible.getName(), System.getProperty("esword.abbreviation", bible.getName().substring(0, 1)), System.getProperty("esword.information", "<p></p>"), 4, hasOT ? 1 : 0, hasNT ? 1 : 0, hasDC ? 1 : 0, hasStrongs ? 1 : 0, 0);
		ISqlJetTable bibleTable = db.getTable("Bible");
		ISqlJetTable notesTable = db.getTable("Notes");

		for (Book bk : bible.getBooks()) {
			int bnumber = Arrays.asList(BOOK_ORDER).indexOf(bk.getId());
			if (bnumber == -1) {
				System.out.println("WARNING: Skipping unsupported book " + bk.getId());
				continue;
			}
			bnumber++;
			for (int cnumber = 1; cnumber <= bk.getChapters().size(); cnumber++) {
				Chapter ch = bk.getChapters().get(cnumber - 1);
				FormattedText prolog = ch.getProlog();
				for (VirtualVerse vv : ch.createVirtualVerses(false, false)) {
					int vnumber = vv.getNumber();
					StringWriter sw = new StringWriter();
					List<StringWriter> footnotes = new ArrayList<>();
					if (prolog != null) {
						StringWriter pw = new StringWriter();
						prolog.accept(new ESwordVisitor(pw, null, bk.getId().isNT()));
						notesTable.insert(bnumber, cnumber, vnumber, "N0", pw.toString());
						sw.write("<not>N0</not> ");
						prolog = null;
					}
					boolean first = true;
					for (Verse v : vv.getVerses()) {
						if (!first || !v.getNumber().equals("" + vnumber)) {
							sw.write(" <b>(" + v.getNumber() + ")</b> ");
						}
						v.accept(new ESwordVisitor(sw, footnotes, bk.getId().isNT()));
						first = false;
					}
					bibleTable.insert(bnumber, cnumber, vnumber, sw.toString());
					for (int i = 0; i < footnotes.size(); i++) {
						notesTable.insert(bnumber, cnumber, vnumber, "N" + (i + 1), footnotes.get(i).toString());
					}
				}
			}
		}
		db.commit();
		db.close();
	}

	private static class ESwordVisitor extends AbstractHTMLVisitor {
		private final List<StringWriter> footnotes;
		private final boolean nt;

		public ESwordVisitor(StringWriter writer, List<StringWriter> footnotes, boolean nt) {
			super(writer, "");
			this.footnotes = footnotes;
			this.nt = nt;
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			int hd = Math.min(depth + 1, 6);
			writer.write("<h" + hd + ">");
			pushSuffix("</h" + hd + ">");
			return this;
		}

		@Override
		public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
			Visitor<IOException> result = visitFootnote0();
			if (ofCrossReferences)
				result.visitText(FormattedText.XREF_MARKER);
			return result;
		}

		public Visitor<IOException> visitFootnote0() throws IOException {
			if (footnotes == null) {
				writer.write("[<i>");
				pushSuffix("</i>]");
				return this;
			}
			StringWriter fw = new StringWriter();
			footnotes.add(fw);
			writer.write("<not>N" + (footnotes.size()) + "</not> ");
			return new ESwordVisitor(fw, null, nt);
		}

		@Override
		public Visitor<IOException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws IOException {
			String firstAbbr = BOOK_ABBR_MAP.get(firstBook);
			if (firstAbbr == null)
				firstAbbr = firstBookAbbr;
			String lastAbbr = BOOK_ABBR_MAP.get(lastBook);
			if (lastAbbr == null)
				lastAbbr = lastBookAbbr;
			writer.write("<ref>" + firstAbbr + " " + firstChapter + ":" + firstVerse + "</ref>");
			if ((lastBook != firstBook || lastChapter != firstChapter || !lastVerse.equals(firstVerse)))
				writer.write("-<ref>" + lastAbbr + " " + lastChapter + ":" + lastVerse + "</ref>");
			return null;
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			String startTag, endTag;
			if (kind.getHtmlTag() != null) {
				startTag = "<" + kind.getHtmlTag() + ">";
				endTag = "</" + kind.getHtmlTag() + ">";
			} else if (kind == FormattingInstructionKind.LINK || kind == FormattingInstructionKind.FOOTNOTE_LINK) {
				startTag = "<blu>";
				endTag = "</blu>";
			} else if (kind == FormattingInstructionKind.WORDS_OF_JESUS) {
				startTag = "<red>";
				endTag = "</red>";
			} else {
				startTag = createFormattingInstructionStartTag(kind);
				endTag = "</span>";
			}
			writer.write(startTag);
			pushSuffix(endTag);
			return this;
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			writer.write("<font color=#808080>/</font>");
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind lbk, int indent) throws IOException {
			LineBreakKind kind = lbk.toLineBreakKind(indent);
			writer.write(kind == LineBreakKind.PARAGRAPH ? "<p>" : "<br />");
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws IOException {
			StringBuilder newSuffix = new StringBuilder();
			if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					newSuffix.append("<num>" + Utils.formatStrongs(nt, i, strongsPrefixes, strongs, strongsSuffixes, "") + "</num> ");
				}
			}
			if (rmac != null) {
				for (String r : rmac) {
					newSuffix.append("<tvm>" + r + "</tvm> ");
				}
			}
			pushSuffix(newSuffix.toString());
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			System.out.println("WARNING: Skipped unsupported dictionary entry");
			pushSuffix("");
			return this;
		}
		@Override
		public Visitor<IOException> visitSpeaker(String labelOrStrongs) throws IOException {
			return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "speaker", labelOrStrongs);
		}

	}
}
