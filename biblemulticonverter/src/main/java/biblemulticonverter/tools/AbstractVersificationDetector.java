package biblemulticonverter.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VerseRange;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.format.ExportFormat;

public abstract class AbstractVersificationDetector implements ExportFormat {

	protected abstract VersificationScheme[] loadSchemes() throws IOException;

	public VersificationScheme loadScheme(String name) throws IOException {
		for (VersificationScheme scheme : loadSchemes()) {
			if (scheme.getName().equals(name))
				return scheme;
		}
		return null;
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		VersificationScheme[] schemes = loadSchemes();
		Set<String> totalVerses = new HashSet<String>();
		boolean includeXref = exportArgs.length > 0 && exportArgs[0].equals("-xref");
		XrefCountVisitor xcv = includeXref ? new XrefCountVisitor(schemes, totalVerses) : null;

		// fill missing verses
		for (Book book : bible.getBooks()) {
			for (int cc = 0; cc < book.getChapters().size(); cc++) {
				Chapter chapter = book.getChapters().get(cc);
				if (includeXref && chapter.getProlog() != null) {
					chapter.getProlog().accept(xcv);
				}
				if (useVerseRanges()) {
					for (VerseRange vr : chapter.createVerseRanges()) {
						int cnumber = vr.getChapter() == 0 ? cc + 1 : vr.getChapter();
						countVerse(schemes, totalVerses, book.getAbbr(), book.getId(), cnumber, vr.getMinVerse());
						countVerse(schemes, totalVerses, book.getAbbr(), book.getId(), cnumber, vr.getMaxVerse());
					}
				} else {
					for (VirtualVerse v : chapter.createVirtualVerses()) {
						countVerse(schemes, totalVerses, book.getAbbr(), book.getId(), cc + 1, v.getNumber());
					}
				}
				if (includeXref) {
					for (Verse vv : chapter.getVerses()) {
						vv.accept(xcv);
					}
				}
			}
		}

		// sort them
		Arrays.sort(schemes);

		// print them
		System.out.print("Best match:  ");
		int totalVerseCount = totalVerses.size();
		printScheme(schemes[0], totalVerseCount);

		System.out.println();
		System.out.println("Other options:");
		for (int i = 1; i < Math.min(11, schemes.length); i++) {
			printScheme(schemes[i], totalVerseCount);
			if (schemes[i].missingChapters.size() > schemes[0].missingChapters.size() + 2 ||
					schemes[i].missingVerses.size() > schemes[0].missingVerses.size() + 5)
				break;
		}

		// print selected schemes
		if (exportArgs.length > (includeXref ? 1 : 0)) {
			System.out.println();
			System.out.println("Selected schemes:");
			for (int i = includeXref ? 1 : 0; i < exportArgs.length; i++) {
				boolean found = false;
				for (VersificationScheme scheme : schemes) {
					if (scheme.getName().equals(exportArgs[i])) {
						printScheme(scheme, totalVerseCount);
						found = true;
						break;
					}
				}
				if (!found)
					System.out.println(exportArgs[i] + " (Unknown scheme)");
			}
		}
	}

	protected void countVerse(VersificationScheme[] schemes, Set<String> totalVerses, String bookAbbr, BookID bookID, int cnum, int vnum) {
		totalVerses.add(bookAbbr + " " + cnum + ":" + vnum);
		for (VersificationScheme scheme : schemes) {
			BitSet[] bookInfo = scheme.getCoveredBooks().get(bookID);
			if (bookInfo == null)
				bookInfo = new BitSet[0];
			if (cnum - 1 >= bookInfo.length) {
				if (!scheme.getMissingChapters().contains(bookAbbr + " " + cnum))
					scheme.getMissingChapters().add(bookAbbr + " " + cnum);
			} else if (!bookInfo[cnum - 1].get(vnum)) {
				scheme.getMissingVerses().add(bookAbbr + " " + cnum + ":" + vnum);
			}
		}
	}

	protected boolean useVerseRanges() {
		return false;
	}

	private void printScheme(VersificationScheme scheme, int totalVerseCount) {
		if (scheme.getMissingChapters().size() > 0)
			System.out.println(scheme.getName() + " (Missing chapters+verses: " + scheme.getMissingChapters().size() + "+" + scheme.getMissingVerses().size() + " " + scheme.getMissingChapters() + " " + scheme.getMissingVerses());
		else if (scheme.getMissingVerses().size() > 0)
			System.out.println(scheme.getName() + " (Missing verses: " + scheme.getMissingVerses().size() + " " + scheme.getMissingVerses() + ")");
		else
			System.out.println(scheme.getName() + " (All verses covered, and " + (scheme.getVerseCount() - totalVerseCount) + " more)");
	}

	public static final class VersificationScheme implements Comparable<VersificationScheme> {
		private final String name;
		private final Map<BookID, BitSet[]> coveredBooks;
		private final int verseCount;
		private final List<String> missingChapters = new ArrayList<String>();
		private final List<String> missingVerses = new ArrayList<String>();

		public VersificationScheme(String name, Map<BookID, BitSet[]> coveredBooks) {
			this.name = name;
			this.coveredBooks = coveredBooks;
			int verseCount = 0;
			for (BitSet[] chapters : coveredBooks.values()) {
				for (BitSet chapter : chapters) {
					verseCount += chapter.cardinality();
				}
			}
			this.verseCount = verseCount;
		}

		public List<String> getMissingChapters() {
			return missingChapters;
		}

		public List<String> getMissingVerses() {
			return missingVerses;
		}

		public Map<BookID, BitSet[]> getCoveredBooks() {
			return coveredBooks;
		}

		public String getName() {
			return name;
		}

		public int getVerseCount() {
			return verseCount;
		}

		@Override
		public int compareTo(VersificationScheme other) {
			int result = Integer.compare(missingChapters.size(), other.missingChapters.size());
			if (result == 0)
				result = Integer.compare(missingVerses.size(), other.missingVerses.size());
			if (result == 0)
				result = Integer.compare(verseCount, other.verseCount);
			return result;
		}
	}

	private class XrefCountVisitor extends FormattedText.VisitorAdapter<RuntimeException> {
		private final VersificationScheme[] schemes;
		private final Set<String> totalVerses;

		public XrefCountVisitor(VersificationScheme[] schemes, Set<String> totalVerses) {
			super(null);
			this.schemes = schemes;
			this.totalVerses = totalVerses;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			try {
				countVerse(schemes, totalVerses, bookAbbr, book, firstChapter, Integer.parseInt(firstVerse));
				countVerse(schemes, totalVerses, bookAbbr, book, lastChapter, Integer.parseInt(lastVerse));
			} catch (NumberFormatException ex) {
			}
			return this;
		}
	}
}
