package biblemulticonverter.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
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
		int totalVerseCount = 0;

		// fill missing verses
		BitSet[] emptyBitSetArray = new BitSet[0];
		for (Book book : bible.getBooks()) {
			for (int cc = 0; cc < book.getChapters().size(); cc++) {
				Chapter chapter = book.getChapters().get(cc);
				for (VirtualVerse v : chapter.createVirtualVerses()) {
					totalVerseCount++;
					for (VersificationScheme scheme : schemes) {
						BitSet[] bookInfo = scheme.getCoveredBooks().get(book.getId());
						if (bookInfo == null)
							bookInfo = emptyBitSetArray;
						if (cc >= bookInfo.length) {
							if (!scheme.getMissingChapters().contains(book.getAbbr() + " " + (cc + 1)))
								scheme.getMissingChapters().add(book.getAbbr() + " " + (cc + 1));
						} else if (!bookInfo[cc].get(v.getNumber())) {
							scheme.getMissingVerses().add(book.getAbbr() + " " + (cc + 1) + ":" + v.getNumber());
						}
					}
				}
			}
		}

		// sort them
		Arrays.sort(schemes);

		// print them
		System.out.print("Best match:  ");
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
		if (exportArgs.length > 0) {
			System.out.println();
			System.out.println("Selected schemes:");
			for (int i = 0; i < exportArgs.length; i++) {
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

	private void printScheme(VersificationScheme scheme, int totalVerseCount) {
		if (scheme.getMissingChapters().size() > 0)
			System.out.println(scheme.getName() + " (Missing chapters+verses: " + scheme.getMissingChapters().size() + "+" + scheme.getMissingVerses() + " " + scheme.getMissingChapters() + " " + scheme.getMissingVerses());
		if (scheme.getMissingVerses().size() > 0)
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
}
