package biblemulticonverter.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VerseRange;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.format.ExportFormat;

public abstract class AbstractVersificationDetector implements ExportFormat {

	protected abstract VersificationScheme[] loadSchemes() throws IOException;

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		VersificationScheme[] schemes = loadSchemes();
		Set<String> totalVerses = new HashSet<String>();
		boolean includeXref = exportArgs.length > 0 && exportArgs[0].equals("-xref");
		Set<BookID> usedBooks = EnumSet.noneOf(BookID.class);
		Map<BookID,String> bookAbbrMap = new EnumMap<>(BookID.class);
		XrefCountVisitor xcv = includeXref ? new XrefCountVisitor(schemes, totalVerses, usedBooks) : null;
		int usedArgCount = (includeXref ? 1 : 0);
		boolean limitBooks = exportArgs.length > usedArgCount && exportArgs[usedArgCount].equals("-limitBooks");

		// fill missing verses
		for (Book book : bible.getBooks()) {
			usedBooks.add(book.getId());
			bookAbbrMap.put(book.getId(), book.getAbbr());
			for (int cc = 0; cc < book.getChapters().size(); cc++) {
				Chapter chapter = book.getChapters().get(cc);
				if (includeXref && chapter.getProlog() != null) {
					chapter.getProlog().accept(xcv);
				}
				if (useVerseRanges()) {
					for (VerseRange vr : chapter.createVerseRanges(false)) {
						int cnumber = vr.getChapter() == 0 ? cc + 1 : vr.getChapter();
						countVerse(schemes, totalVerses, book.getAbbr(), book.getId(), cnumber, vr.getMinVerse());
						countVerse(schemes, totalVerses, book.getAbbr(), book.getId(), cnumber, vr.getMaxVerse());
					}
				} else {
					for (VirtualVerse v : chapter.createVirtualVerses(useTitleAsVerseZero(), !ignoreHeadlines())) {
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

		// limit to used books
		if (limitBooks) {
			usedArgCount++;
			for(VersificationScheme scheme: schemes) {
				scheme.limitToBooks(usedBooks);
			}
		}

		// sort them
		Arrays.sort(schemes);

		// print them
		System.out.print("Best match:  ");
		String verboseSchemasProperty = System.getProperty("versificationdetector.verboseschemes", "");
		Set<String> verboseSchemes = verboseSchemasProperty == null ? null : new HashSet<>(Arrays.asList(verboseSchemasProperty.split(", *")));
		printScheme(schemes[0], totalVerses, verboseSchemes, bookAbbrMap);

		System.out.println();
		System.out.println("Other options:");
		for (int i = 1; i < Math.min(11, schemes.length); i++) {
			printScheme(schemes[i], totalVerses, verboseSchemes, bookAbbrMap);
			if (schemes[i].missingChapters.size() > schemes[0].missingChapters.size() + 2 ||
					schemes[i].missingVerses.size() > schemes[0].missingVerses.size() + 5)
				break;
		}

		// print selected schemes
		if (exportArgs.length > usedArgCount) {
			System.out.println();
			System.out.println("Selected schemes:");
			for (int i = usedArgCount; i < exportArgs.length; i++) {
				boolean found = false;
				for (VersificationScheme scheme : schemes) {
					if (scheme.getName().equals(exportArgs[i])) {
						printScheme(scheme, totalVerses, verboseSchemes, bookAbbrMap);
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

	protected boolean ignoreHeadlines() {
		return false;
	}

	protected boolean useVerseRanges() {
		return false;
	}

	protected boolean useTitleAsVerseZero() {
		return false;
	}

	private void printScheme(VersificationScheme scheme, Set<String> totalVerses, Set<String> verboseSchemes, Map<BookID, String> bookAbbrMap) {
		if (scheme.getMissingChapters().size() > 0)
			System.out.println(scheme.getName() + " (Missing chapters+verses: " + scheme.getMissingChapters().size() + "+" + scheme.getMissingVerses().size() + " " + scheme.getMissingChapters() + " " + scheme.getMissingVerses());
		else if (scheme.getMissingVerses().size() > 0)
			System.out.println(scheme.getName() + " (Missing verses: " + scheme.getMissingVerses().size() + " " + scheme.getMissingVerses() + ")");
		else
			System.out.println(scheme.getName() + " (All verses covered, and " + (scheme.getVerseCount() - totalVerses.size()) + " more)");
		if (verboseSchemes != null && (verboseSchemes.contains(scheme.getName()) || verboseSchemes.contains("all"))) {
			Set<String> coveredVerses = new HashSet<>();
			for (Entry<BookID, BitSet[]> entry : scheme.coveredBooks.entrySet()) {
				String bookAbbr = bookAbbrMap.getOrDefault(entry.getKey(), entry.getKey().getOsisID());
				for (int cnum = 1; cnum <= entry.getValue().length; cnum++) {
					BitSet verses = entry.getValue()[cnum - 1];
					for (int vnum = verses.nextSetBit(0); vnum != -1; vnum = verses.nextSetBit(vnum + 1)) {
						coveredVerses.add(bookAbbr + " " + cnum + ":" + vnum);
					}
				}
			}
			List<String> bookAbbrOrder = new ArrayList<>(bookAbbrMap.values());
			printVerbose("\t-", totalVerses, coveredVerses, bookAbbrOrder);
			printVerbose("\t+", coveredVerses, totalVerses, bookAbbrOrder);
		}
	}

	private void printVerbose(String prefix, Set<String> verseList, Set<String> removeList, List<String> bookAbbrOrder) {
		List<String> list = new ArrayList<>(verseList);
		list.removeAll(removeList);
		Map<String, int[]> sortKey = new HashMap<>();
		Pattern ptn = Pattern.compile("(" + Utils.BOOK_ABBR_REGEX + ") ([0-9]+):([0-9]+)");
		for (String verse : list) {
			Matcher m = ptn.matcher(verse);
			if (!m.matches())
				throw new RuntimeException("Invalid verse reference: " + verse);
			String abbr = m.group(1);
			int aidx = bookAbbrOrder.indexOf(abbr);
			if (aidx == 0) {
				aidx = bookAbbrOrder.size();
				bookAbbrOrder.add(abbr);
			}
			sortKey.put(verse, new int[] { aidx, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)) });
		}
		list.sort(Comparator.comparing(v -> sortKey.get(v), Comparator.<int[], Integer> comparing(a -> a[0]).thenComparing(a -> a[1]).thenComparing(a -> a[2])));
		for (String verse : list) {
			System.out.println(prefix + verse);
		}
	}

	public static final class VersificationScheme implements Comparable<VersificationScheme> {
		private final String name;
		private final Map<BookID, BitSet[]> coveredBooks;
		private int verseCount;
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

		private void limitToBooks(Collection<BookID> books) {
			Set<BookID> booksToRemove = EnumSet.noneOf(BookID.class);
			booksToRemove.addAll(coveredBooks.keySet());
			booksToRemove.removeAll(books);
			for(BookID book : booksToRemove) {
				BitSet[] chapters = coveredBooks.remove(book);
				for (BitSet chapter : chapters) {
					verseCount -= chapter.cardinality();
				}
			}
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
		private Set<BookID> usedBooks;

		public XrefCountVisitor(VersificationScheme[] schemes, Set<String> totalVerses, Set<BookID> usedBooks) {
			super(null);
			this.schemes = schemes;
			this.totalVerses = totalVerses;
			this.usedBooks = usedBooks;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			usedBooks.add(book);
			try {
				countVerse(schemes, totalVerses, bookAbbr, book, firstChapter, Integer.parseInt(firstVerse));
				countVerse(schemes, totalVerses, bookAbbr, book, lastChapter, Integer.parseInt(lastVerse));
			} catch (NumberFormatException ex) {
			}
			return this;
		}
	}
}
