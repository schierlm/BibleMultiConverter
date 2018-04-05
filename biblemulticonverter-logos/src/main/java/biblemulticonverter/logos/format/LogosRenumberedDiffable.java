package biblemulticonverter.logos.format;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.format.Diffable;
import biblemulticonverter.format.ExportFormat;
import biblemulticonverter.logos.tools.LogosVersificationDetector;
import biblemulticonverter.tools.AbstractVersificationDetector.VersificationScheme;

public class LogosRenumberedDiffable implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Renumber named verses for Logos before exporting as Diffable.",
			"",
			"Usage: LogosRenumberedDiffable <OutputFile> [<versemap>]",
			"",
			"If you want to export named verses, run your bible through this",
			"module before trying the LogosVersificationDetector. If a versemap",
			"is given, only replace verses that exist in this versemap."
	};

	private final Map<String, Integer> verseTransforms = new HashMap<>();

	public static String transformLogosVerse(String verse) {
		if (verse.matches("[0-9]+([a-z])\\1"))
			verse = verse.replaceFirst("([0-9]+)([a-z])\\2", "$1-$2");
		else if (verse.matches("[0-9]+a[b-z]"))
			verse = verse.replaceFirst("([0-9]+)a([b-z])", "$1/$2");
		else if (!verse.matches("[0-9]+[a-z]")) {
			String[] replacements = { "1/t", "2-t", "3-t", "2/t", "3/t", "4/t", "1/p", "1-p", "2-p", "1/s" };
			verse = replacements[Arrays.asList(LogosHTML.NAMED_VERSES).indexOf(verse)];
		}
		return verse;
	}

	public LogosRenumberedDiffable() {
		for (int i = 0; i < LogosHTML.NAMED_VERSES.length; i++) {
			String verse = transformLogosVerse(LogosHTML.NAMED_VERSES[i]);
			if (verseTransforms.containsKey(verse))
				throw new IllegalStateException(verse);
			verseTransforms.put(verse, i + 1000);
		}
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String outputFile = exportArgs[0];
		VersificationScheme scheme = null;
		if (exportArgs.length == 2) {
			if (!exportArgs[1].matches("Bible[A-Z0-9]*")) {
				System.out.println("Invalid versification: " + exportArgs[1]);
				return;
			}
			scheme = new LogosVersificationDetector().loadScheme(exportArgs[1]);
			if (scheme == null) {
				System.out.println("Invalid versification: " + exportArgs[1]);
				return;
			}
		}
		for (Book book : bible.getBooks()) {
			int cnumber = 0;
			for (Chapter chap : book.getChapters()) {
				cnumber++;
				if (chap.getProlog() != null) {
					FormattedText newProlog = new FormattedText();
					chap.getProlog().accept(new MapXrefVisitor(newProlog.getAppendVisitor(), scheme));
					newProlog.finished();
					chap.setProlog(newProlog);
				}
				for (int j = 0; j < chap.getVerses().size(); j++) {
					Verse v = chap.getVerses().get(j);
					Verse nv = new Verse(mapVerse(book.getAbbr(), book.getId(), cnumber, v.getNumber(), scheme, ""));
					v.accept(new MapXrefVisitor(nv.getAppendVisitor(), scheme));
					nv.finished();
					chap.getVerses().set(j, nv);
				}
			}
		}
		new Diffable().doExport(bible, new String[] { outputFile });
	}

	private String mapVerse(String bookAbbr, BookID book, int chapter, String verse, VersificationScheme scheme, String context) {
		Integer newVerse = verseTransforms.get(verse);
		if (newVerse == null) {
			if (!verse.matches("[0-9]+"))
				System.out.println(context + "UNKNOWN " + bookAbbr + " " + chapter + ":" + verse);
			return verse;
		}
		if (scheme != null) {
			BitSet[] bookVerses = scheme.getCoveredBooks().get(book);
			BitSet chapterVerses = bookVerses != null && chapter <= bookVerses.length ? bookVerses[chapter - 1] : null;
			if (chapterVerses == null || !chapterVerses.get(newVerse)) {
				System.out.println(context + "NOTMAPPED " + bookAbbr + " " + chapter + ":" + verse);
				return verse;
			}
		}
		System.out.println(context + "MAPPED " + bookAbbr + " " + chapter + ":" + verse + " -> " + newVerse);
		return "" + (int) newVerse;
	}

	private class MapXrefVisitor extends FormattedText.VisitorAdapter<RuntimeException> {

		private final VersificationScheme scheme;

		private MapXrefVisitor(Visitor<RuntimeException> next, VersificationScheme scheme) throws RuntimeException {
			super(next);
			this.scheme = scheme;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return new MapXrefVisitor(childVisitor, scheme);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			return super.visitCrossReference(bookAbbr, book,
					firstChapter, mapVerse(bookAbbr, book, firstChapter, firstVerse, scheme, "XREF "),
					lastChapter, mapVerse(bookAbbr, book, lastChapter, lastVerse, scheme, "XREF "));
		}
	}
}
