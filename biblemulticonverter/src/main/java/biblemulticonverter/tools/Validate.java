package biblemulticonverter.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.Verse;
import biblemulticonverter.format.ExportFormat;

public class Validate implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Usage: Validate [PrintSpecialVerseSummary|PrintHeadlines]",
			"Validate bible for inconsistencies",
			"",
			"Use this module to find inconsistencies, or XREFs that refer to nonexistant verses.",
			"With an extra argument 'PrintSpecialVerseSummary', additionally print a summary of special",
			"(non-numeric or reordered) verses. With an extra argument 'PrintHeadlines' print all",
			"headlines and their depths, separated by verse ranges between them, for validating",
			"headline depths."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {

		List<String> danglingReferences = new ArrayList<>();
		bible.validate(danglingReferences);
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
