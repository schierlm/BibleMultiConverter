package biblemulticonverter.sqlite.format;

import java.io.File;
import java.io.StringWriter;
import java.io.Writer;

import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.format.BibleAnalyzerFormattedText;

public class BibleAnalyzerDatabase extends BibleAnalyzerFormattedText {

	public static final String[] HELP_TEXT = {
			"Database Export format for Bible Analyzer",
			"",
			"This format only supports bibles, not dictionaries.",
			"",
			"It directly writes the SQLite database consumed by Bible Analyzer, and therefore",
			"supports more features than the BibleAnalyzerFormattedText format; since the internal",
			"format is not documented and has been reverse engineered, the risk is higher that these",
			"modules cease to work in a future version of Bible Analyzer."
	};

	private ISqlJetTable bibleTable, footnoteTable, headingTable;
	private int bibleID, footnoteID, headingID;
	private String headingRef, verseRef;
	private StringWriter headingWriter;
	private boolean headingEmpty;

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File outfile = new File(exportArgs[0]);
		String basename = outfile.getName().replaceAll("\\..*", "");
		boolean hasStrongs = false, hasRMAC = false;
		for (Book bk : bible.getBooks()) {
			for (Chapter ch : bk.getChapters()) {
				for (Verse vv : ch.getVerses()) {
					String elementTypes = vv.getElementTypes(Integer.MAX_VALUE);
					if (elementTypes.contains("g")) {
						final boolean[] hasGrammar = { hasStrongs, hasRMAC };
						vv.accept(new VisitorAdapter<RuntimeException>(null) {
							@Override
							protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) {
								return this;
							}

							@Override
							public Visitor<RuntimeException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) {
								if (strongs != null)
									hasGrammar[0] = true;
								if (rmac != null)
									hasGrammar[1] = true;
								return super.visitGrammarInformation(strongs, rmac, sourceIndices);
							}
						});
						hasStrongs = hasGrammar[0];
						hasRMAC = hasGrammar[1];
					}
				}
			}
		}
		outfile.delete();
		SqlJetDb db = SqlJetDb.open(outfile, true);
		db.getOptions().setAutovacuum(true);
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		db.getOptions().setUserVersion(0);

		db.createTable("CREATE TABLE bible (id INTEGER PRIMARY KEY, ref, verse)");
		db.createIndex("CREATE INDEX refsidx ON bible (ref)");
		db.createTable("CREATE TABLE footnote (id INTEGER PRIMARY KEY, ref, note)");
		db.createTable("CREATE TABLE heading (id INTEGER PRIMARY KEY, ref, head)");
		db.createTable("CREATE TABLE images (fName, imageData)");
		db.createTable("CREATE TABLE option (strongs, interlinear, encrypt, apocrypha)");
		db.createTable("CREATE TABLE title (abbr, desc, info)");
		db.createTable("CREATE TABLE wordlist (word, freq)");
		db.createIndex("CREATE INDEX wordlistidx ON wordlist (word)");
		db.getTable("option").insert(hasStrongs ? 1 : 0, hasRMAC ? 1 : 0, 0, 0);
		db.getTable("title").insert(basename, bible.getName(), bible.getName());
		bibleTable = db.getTable("bible");
		footnoteTable = db.getTable("footnote");
		headingTable = db.getTable("heading");
		bibleID = footnoteID = headingID = 1;
		exportBible(basename, null, bible, hasStrongs, hasRMAC);
		db.commit();
		db.close();
	}

	@Override
	protected Writer startDictionaryEntry(String basename, Book book, Writer w) throws Exception {
		throw new RuntimeException("Dictionaries are not supported!");
	}

	@Override
	protected void finishDictionaryEntry(Writer dw) throws Exception {
		throw new RuntimeException("Dictionaries are not supported!");
	}

	@Override
	protected void handleProlog(String abbr, int cnumber, FormattedText prolog) throws Exception {
		headingRef = abbr + " " + cnumber + ":";
		headingWriter = new StringWriter();
		headingEmpty = true;
		if (prolog != null) {
			headingWriter.write("<span style=\"color:black\">");
			prolog.accept(new BibleAnalyzerVisitor(headingWriter));
			headingWriter.write("</span>");
			headingEmpty = false;
		}
	}

	@Override
	protected Writer[] startVerse(Writer w, String abbr, int cnumber, VirtualVerse vv) throws Exception {
		for (Headline h : vv.getHeadlines()) {
			if (!headingEmpty)
				headingWriter.write("<br>");
			else
				headingEmpty = false;
			headingWriter.write(vv.getNumber() + " ");
			h.accept(new BibleAnalyzerVisitor(headingWriter));
		}
		verseRef = abbr + " " + cnumber + ":" + vv.getNumber();
		StringWriter verseWriter = new StringWriter();
		StringWriter footnoteWriter = new StringWriter();
		return new Writer[] { verseWriter, footnoteWriter };
	}

	@Override
	protected void finishVerse(Writer[] vw) throws Exception {
		bibleTable.insert(bibleID, verseRef, vw[0].toString());
		bibleID++;
		if (!vw[1].toString().isEmpty()) {
			footnoteTable.insert(footnoteID, verseRef, vw[1].toString());
			footnoteID++;
		}
		verseRef = null;
	}

	@Override
	protected void finishChapter() throws Exception {
		if (!headingEmpty) {
			headingTable.insert(headingID, headingRef, headingWriter.toString());
			headingID++;
		}
		headingRef = null;
		headingWriter = null;
	}
}
