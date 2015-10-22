package biblemulticonverter.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.Verse;
import biblemulticonverter.format.Diffable;
import biblemulticonverter.format.ExportFormat;

public class Validate implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Validate bible for inconsistencies",
			"",
			"Usage: Validate [PrintSpecialVerseSummary|PrintHeadlines]",
			"       Validate IncludeExternalRefs [<ref> [...]]",
			"",
			"Use this module to find inconsistencies, or XREFs that refer to nonexistant verses.",
			"With an extra argument 'PrintSpecialVerseSummary', additionally print a summary of special",
			"(non-numeric or reordered) verses. With an extra argument 'PrintHeadlines' print all",
			"headlines and their depths, separated by verse ranges between them, for validating",
			"headline depths.",
			"With extra argeument 'IncludeExternalRefs', external references to Strongs and dictionaries",
			"are validated, too. Refs can be 'B<DiffableBibleFile>' to validate a dictionary's xrefs against",
			"another bible, or 'S<StrongEntryList>' to validate Strongs against an entry list file,",
			"'L<DictName>=<DictEntryList>' to validate dictionary references for a given dictionary, or",
			"'L<DicName>' to validate dictionary references for itself, or last but not least",
			"'X<ExportEntryList>' to export the entries of currently validated dictionary."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		Map<String, Set<String>> dictionaryEntries = null;
		if (exportArgs.length > 0 && exportArgs[0].equals("IncludeExternalRefs")) {
			dictionaryEntries = new HashMap<String, Set<String>>();
			for (int i = 1; i < exportArgs.length; i++) {
				String ref = exportArgs[i];
				if (ref.startsWith("S"))
					ref = "Lstrongs=" + ref.substring(1);
				if (ref.startsWith("B")) {
					for (Book baseBook : new Diffable().doImport(new File(ref.substring(1))).getBooks()) {
						if (baseBook.getId().getZefID() < 0)
							continue;
						Book book = null;
						for (Book oldBook : bible.getBooks()) {
							if (oldBook.getId().equals(baseBook.getId())) {
								book = oldBook;
								break;
							}
						}
						if (book == null) {
							book = new Book(baseBook.getAbbr(), baseBook.getId(), "$BBL$" + baseBook.getAbbr(), "$BBL$" + baseBook.getAbbr());
							bible.getBooks().add(book);
						}
						for (int ch = 0; ch < baseBook.getChapters().size(); ch++) {
							for (Verse v : baseBook.getChapters().get(ch).getVerses()) {
								while (book.getChapters().size() <= ch)
									book.getChapters().add(new Chapter());
								Chapter c = book.getChapters().get(ch);
								boolean verseFound = false;
								for (Verse vv : c.getVerses()) {
									if (vv.getNumber().equals(v.getNumber())) {
										verseFound = true;
										break;
									}
								}
								if (!verseFound) {
									Verse vv = new Verse(v.getNumber());
									vv.getAppendVisitor().visitText("X");
									vv.finished();
									c.getVerses().add(vv);
								}
							}
						}
					}
				} else if (ref.startsWith("L")) {
					if (ref.contains("=")) {
						int pos = ref.indexOf('=');
						Set<String> entryList = new HashSet<>();
						try (BufferedReader br = new BufferedReader(new FileReader(ref.substring(pos + 1)))) {
							String line;
							while ((line = br.readLine()) != null) {
								entryList.add(line);
							}
						}
						dictionaryEntries.put(ref.substring(1, pos), entryList);
					} else {
						Set<String> entryList = new HashSet<>();
						for (Book bk : bible.getBooks()) {
							if (bk.getId().equals(BookID.DICTIONARY_ENTRY)) {
								entryList.add(bk.getAbbr());
							}
						}
						dictionaryEntries.put(ref.substring(1), entryList);
					}
				} else if (ref.startsWith("X")) {
					try (BufferedWriter bw = new BufferedWriter(new FileWriter(ref.substring(1)))) {
						for (Book bk : bible.getBooks()) {
							if (bk.getId().equals(BookID.DICTIONARY_ENTRY)) {
								bw.write(bk.getAbbr());
								bw.newLine();
							}
						}
					}
				} else {
					System.out.println("WARNING: external reference: " + ref);
				}
			}
			exportArgs = new String[0];
		}
		List<String> danglingReferences = new ArrayList<>();
		bible.validate(danglingReferences, dictionaryEntries);
		if (danglingReferences.size() > 0) {
			System.out.println("Dangling references: ");
			for (String reference : danglingReferences) {
				System.out.println("\t" + reference);
			}
			System.out.println();
		}
		if (exportArgs.length == 1 && exportArgs[0].equals("PrintSpecialVerseSummary")) {
			System.out.println("Special verse numbers:");
			printSpecialVerseNumbers(bible);
		} else if (exportArgs.length == 1 && exportArgs[0].equals("PrintHeadlines")) {
			System.out.println("Headlines:");
			printHeadlines(bible);
		} else if (exportArgs.length > 0) {
			System.out.println("WARNING: Unsupported arguments: " + Arrays.toString(exportArgs));
		}
	}

	private void printSpecialVerseNumbers(Bible bible) throws IOException {
		boolean lastPrinted = false;
		for (Book b : bible.getBooks()) {
			int chapter = 0;
			for (Chapter c : b.getChapters()) {
				chapter++;
				Verse lastVerse = null;
				for (Verse v : c.getVerses()) {
					boolean print;
					if (lastVerse == null) {
						print = !v.getNumber().equals("1");
					} else {
						try {
							int last = Integer.parseInt(lastVerse.getNumber());
							print = !v.getNumber().equals("" + (last + 1));
						} catch (NumberFormatException ex) {
							print = true;
						}
					}
					if (print) {
						if (!lastPrinted && lastVerse != null) {
							printShortVerse(b, chapter, lastVerse);
						}
						printShortVerse(b, chapter, v);
					} else if (lastPrinted) {
						System.out.println("\t====");
					}
					lastPrinted = print;
					lastVerse = v;
				}
			}
		}
	}

	private void printShortVerse(Book b, int chapter, Verse v) throws IOException {
		ShortVerseVisitor svv = new ShortVerseVisitor();
		v.accept(svv);
		svv.print("\t" + b.getAbbr() + " " + chapter + ":" + v.getNumber() + "  ");
	}

	private void printHeadlines(Bible bible) throws IOException {
		HeadlineSummaryVisitor hsv = new HeadlineSummaryVisitor();
		for (Book b : bible.getBooks()) {
			int cnumber = 0;
			for (Chapter c : b.getChapters()) {
				cnumber++;
				if (c.getProlog() != null) {
					hsv.setCurrentVerse(b.getAbbr() + " " + cnumber + " (Prolog)");
					c.getProlog().accept(hsv);
				}
				for (Verse v : c.getVerses()) {
					hsv.setCurrentVerse(b.getAbbr() + " " + cnumber + ":" + v.getNumber());
					v.accept(hsv);
				}
			}
		}
		hsv.printVerseRange();
	}

	private static class ShortVerseVisitor extends VisitorAdapter<RuntimeException> {

		private StringBuilder sb;
		private List<ShortVerseVisitor> headlines = new ArrayList<>();

		public ShortVerseVisitor() {
			super(null);
			sb = new StringBuilder();
		}

		public void print(String prefix) {
			for (ShortVerseVisitor headline : headlines) {
				headline.print("\t#  ");
			}
			if (sb.length() > 55)
				sb.replace(25, sb.length() - 25, "[...]");
			System.out.println(prefix + sb.toString());
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			ShortVerseVisitor svv = new ShortVerseVisitor();
			headlines.add(svv);
			return svv;
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			sb.append('/');
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			sb.append(text);
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(biblemulticonverter.data.FormattedText.FormattingInstructionKind kind) throws RuntimeException {
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			return prio.handleVisitor(category, this);
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			return this;
		}
	}

	private static class HeadlineSummaryVisitor extends VisitorAdapter<RuntimeException> {

		private String firstVerse, currentVerse, lastVerse;

		public HeadlineSummaryVisitor() {
			super(null);
			firstVerse = lastVerse = currentVerse = null;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			printVerseRange();
			System.out.print(depth + "\t");
			return new VisitorAdapter<RuntimeException>(null) {
				@Override
				public void visitText(String text) throws RuntimeException {
					System.out.print(text);
				}

				@Override
				public boolean visitEnd() throws RuntimeException {
					System.out.println();
					return false;
				}
			};
		}

		@Override
		protected void beforeVisit() throws RuntimeException {
			if (firstVerse == null)
				firstVerse = currentVerse;
			lastVerse = currentVerse;
		}

		public void setCurrentVerse(String currentVerse) {
			this.currentVerse = currentVerse;
		}

		public void printVerseRange() {
			if (lastVerse != null) {
				if (!lastVerse.equals(firstVerse))
					lastVerse = firstVerse + " - " + lastVerse;
				System.out.println("\t\t[" + lastVerse + "]");
			}
			lastVerse = firstVerse = null;
		}
	}
}
