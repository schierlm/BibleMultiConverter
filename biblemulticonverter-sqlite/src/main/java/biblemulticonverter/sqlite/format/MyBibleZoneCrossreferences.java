package biblemulticonverter.sqlite.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.Verse;
import biblemulticonverter.format.ExportFormat;
import biblemulticonverter.sqlite.SQLiteModuleRegistry;

public class MyBibleZoneCrossreferences implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"MyBible.zone (Bible Reader for Android) Crossreferences.",
			"",
			"Usage: MyBibleZone <moduleName>.crossreferences.SQLite3 [<propertyfile>]",
			"",
			"Property file can be used for overriding values in the info table.",
			"",
			"Cross references are generated from footnotes that start with Xref",
			"marker. Bold cross references override the verses the cross reference",
			"is for, normal cross references give the target."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String outfile = exportArgs[0];
		if (!outfile.endsWith(".crossreferences.SQLite3"))
			outfile += ".crossreferences.SQLite3";
		new File(outfile).delete();
		SqlJetDb db = SQLiteModuleRegistry.openDB(new File(outfile), true);
		db.getOptions().setAutovacuum(true);
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		db.getOptions().setUserVersion(0);
		db.createTable("CREATE TABLE info (name TEXT, value TEXT)");
		db.createTable("CREATE TABLE cross_references (book NUMERIC, chapter NUMERIC, verse NUMERIC, verse_end NUMERIC, book_to NUMERIC, chapter_to NUMERIC, verse_to_start NUMERIC, verse_to_end NUMERIC, votes NUMERIC)");
		db.createIndex("CREATE INDEX book_and_chapter ON cross_references(book, chapter)");
		Map<String, String> infoValues = new LinkedHashMap<>();
		MetadataBook mb = bible.getMetadataBook();
		if (mb == null)
			mb = new MetadataBook();
		infoValues.put("language", "xx");
		infoValues.put("description", bible.getName());
		infoValues.put("detailed_info", "");
		infoValues.put("russian_numbering", "false");
		infoValues.put("requires_reverse_processing", "false");
		for (String mbkey : mb.getKeys()) {
			if (mbkey.startsWith("MyBible.zone@")) {
				infoValues.put(mbkey.substring(13).replace('.', '_'), mb.getValue(mbkey));
			} else {
				infoValues.put("detailed_info", infoValues.get("detailed_info") + "\r\n<br><b>" + mbkey + ":</b>" + mb.getValue(mbkey));
			}
		}
		if (exportArgs.length > 1) {
			Properties props = new Properties();
			FileInputStream in = new FileInputStream(exportArgs[1]);
			props.load(in);
			in.close();
			for (Object key : props.keySet()) {
				String template = props.getProperty(key.toString());
				template = template.replace("${name}", bible.getName());
				for (String mbkey : mb.getKeys())
					template = template.replace("${" + mbkey + "}", mb.getValue(mbkey));
				infoValues.put(key.toString(), template);
			}
		}
		ISqlJetTable infoTable = db.getTable("info");
		ISqlJetTable crossReferencesTable = db.getTable("cross_references");
		for (Map.Entry<String, String> entry : infoValues.entrySet()) {
			infoTable.insert(entry.getKey(), entry.getValue());
		}
		for (Book bk : bible.getBooks()) {
			if (!MyBibleZone.BOOK_NUMBERS.containsKey(bk.getId()))
				continue;
			int bn = MyBibleZone.BOOK_NUMBERS.get(bk.getId());
			for (int cn = 1; cn <= bk.getChapters().size(); cn++) {
				Chapter ch = bk.getChapters().get(cn - 1);
				for (Verse v : ch.getVerses()) {
					int vn = numericVerse(v.getNumber());
					v.accept(new XrefVerseVisitor(new int[] { bn, cn, vn, 0 }, bible, crossReferencesTable));
				}
			}
		}
		db.commit();
		db.close();
	}

	private static int numericVerse(String number) {
		return Integer.parseInt(number.replaceAll("[^0-9].*", ""));
	}

	private static int lastChapterVerse(Bible bible, BookID book, int cn) {
		for (Book bk : bible.getBooks()) {
			if (bk.getId() == book) {
				Chapter ch = bk.getChapters().get(cn - 1);
				Verse v = ch.getVerses().get(ch.getVerses().size() - 1);
				return numericVerse(v.getNumber());
			}
		}
		throw new RuntimeException("Dangling Xref? " + book + " " + cn);
	}

	private static class XrefVerseVisitor extends VisitorAdapter<IOException> {

		private final int[] thisRef;
		private final ISqlJetTable crossReferencesTable;
		private final Bible bible;

		public XrefVerseVisitor(int[] thisRef, Bible bible, ISqlJetTable crossReferencesTable) throws IOException {
			super(null);
			this.thisRef = thisRef;
			this.bible = bible;
			this.crossReferencesTable = crossReferencesTable;
		}

		@Override
		protected Visitor<IOException> wrapChildVisitor(Visitor<IOException> visitor) throws IOException {
			return this;
		}

		@Override
		public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
			return new XrefFootnoteVisitor(thisRef, bible, crossReferencesTable, ofCrossReferences);
		}

		@Override
		public Visitor<IOException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws IOException {
			Visitor<IOException> fnv = new XrefFootnoteVisitor(thisRef, bible, crossReferencesTable, true);
			return fnv.visitCrossReference(firstBookAbbr, firstBook, firstChapter, firstVerse, lastBookAbbr, lastBook, lastChapter, lastVerse);
		}
	}

	private static class XrefFootnoteVisitor extends VisitorAdapter<IOException> {

		private final int[] thisRef;
		private final Bible bible;
		private final ISqlJetTable crossReferencesTable;
		private final List<int[]> currRefs = new ArrayList<>();
		private VisitorMode mode = VisitorMode.UNKNOWN;

		public XrefFootnoteVisitor(int[] thisRef, Bible bible, ISqlJetTable crossReferencesTable, boolean ofCrossReferences) throws IOException {
			super(null);
			this.thisRef = thisRef;
			this.bible = bible;
			this.crossReferencesTable = crossReferencesTable;
			this.mode = ofCrossReferences ? VisitorMode.XREF_FOOTNOTE : VisitorMode.UNKNOWN;
		}

		@Override
		protected void beforeVisit() throws IOException {
			if (mode == VisitorMode.UNKNOWN)
				mode = VisitorMode.OTHER_FOOTNOTE;
			if (mode == VisitorMode.OTHER_FOOTNOTE)
				return;
			throw new IOException("Unsupported element in Xref footnote");
		}

		@Override
		public void visitText(String text) throws IOException {
			if (mode == VisitorMode.UNKNOWN && text.startsWith(FormattedText.XREF_MARKER)) {
				mode = VisitorMode.XREF_FOOTNOTE;
				text = text.substring(2);
			} else if (mode == VisitorMode.UNKNOWN) {
				mode = VisitorMode.OTHER_FOOTNOTE;
			}
			if (mode == VisitorMode.OTHER_FOOTNOTE)
				return;
			if (!text.matches("[:./,; ]*"))
				System.out.println("WARNING: Unsupported text inside xref footnote: " + text);
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			if (mode == VisitorMode.UNKNOWN)
				mode = VisitorMode.OTHER_FOOTNOTE;
			if (mode == VisitorMode.OTHER_FOOTNOTE)
				return null;
			if (kind == FormattingInstructionKind.BOLD && mode == VisitorMode.XREF_FOOTNOTE) {
				currRefs.clear();
				mode = VisitorMode.XREF_FOOTNOTE_BOLD;
				return this;
			} else {
				System.out.println("WARNING: Unsupported formatting inside xref footnote: " + kind);
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
			List<int[]> ranges = new ArrayList<>();
			if (!MyBibleZone.BOOK_NUMBERS.containsKey(book)) {
				System.out.println("WARNING: Cross reference to unsupported book " + book);
				return null;
			}
			int bn = MyBibleZone.BOOK_NUMBERS.get(book);
			int fv = numericVerse(firstVerse);
			int lv = numericVerse(lastVerse);
			if (firstChapter == lastChapter) {
				ranges.add(new int[] { bn, firstChapter, fv, lv });
			} else {
				ranges.add(new int[] { bn, firstChapter, fv, lastChapterVerse(bible, book, firstChapter) });
				for (int c = firstChapter + 1; c < lastChapter; c++) {
					ranges.add(new int[] { bn, c, 1, lastChapterVerse(bible, book, c) });
				}
				ranges.add(new int[] { bn, lastChapter, 1, lv });
			}
			switch (mode) {
			case UNKNOWN:
				mode = VisitorMode.XREF_FOOTNOTE;
				System.out.println("WARNING: Foonote starting with cross reference without marker");
				// fall through
			case XREF_FOOTNOTE:
				List<int[]> refs = currRefs;
				if (refs.isEmpty())
					refs = Collections.singletonList(thisRef);
				try {
					for (int[] ref : refs) {
						if (ref[3] == ref[2])
							ref[3] = 0;
						for (int[] range : ranges) {
							if (range[3] == range[2])
								range[3] = 0;
							crossReferencesTable.insert(ref[0], ref[1], ref[2], ref[3], range[0], range[1], range[2], range[3], 1);
						}
					}
				} catch (SqlJetException ex) {
					throw new IOException(ex);
				}
				break;
			case XREF_FOOTNOTE_BOLD:
				currRefs.addAll(ranges);
				break;
			case OTHER_FOOTNOTE:
				break;
			default:
				throw new IOException("Cross reference in invalid mode: " + mode);
			}
			return null;
		}

		public boolean visitEnd() {
			if (mode == VisitorMode.XREF_FOOTNOTE_BOLD)
				mode = VisitorMode.XREF_FOOTNOTE;
			else
				mode = VisitorMode.END;
			return false;
		}
	}

	private static enum VisitorMode {
		UNKNOWN, OTHER_FOOTNOTE, XREF_FOOTNOTE, XREF_FOOTNOTE_BOLD, END
	}
}
