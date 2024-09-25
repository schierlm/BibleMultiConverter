package biblemulticonverter.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
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
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.format.ExportFormat;

public class VersificationCountsDetector implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Detect what versification most closely matches a module, looking on chapter/verse counts only",
			"",
			"Usage: VersificationCountsDetector <dbfile> [-nochapter|-lowchapter] [-xref] [-limitBooks] [schemes]",
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		// parse parameters
		VersificationSet vs = new VersificationSet(new File(exportArgs[0]));
		ChapterMode chapterMode = (exportArgs.length > 1 && exportArgs[1].equals("-nochapter")) ? ChapterMode.IGNORE : //
				(exportArgs.length > 1 && exportArgs[1].equals("-lowchapter")) ? ChapterMode.LOW_PRIO : ChapterMode.HIGH_PRIO;
		boolean includeXref = false, limitBooks = false;
		int exportArgsIdx = (chapterMode != ChapterMode.HIGH_PRIO ? 2 : 1);
		if (exportArgs.length > exportArgsIdx && exportArgs[exportArgsIdx].equals("-xref")) {
			includeXref = true;
			exportArgsIdx++;
		}
		if (exportArgs.length > exportArgsIdx && exportArgs[exportArgsIdx].equals("-limitBooks")) {
			limitBooks = true;
			exportArgsIdx++;
		}

		// count bible verses
		Map<BookID, int[]> bibleCounts = new EnumMap<>(BookID.class);
		Set<String> totalVerses = new HashSet<>();
		XrefCountVisitor xcv = includeXref ? new XrefCountVisitor(bibleCounts, totalVerses) : null;
		for (Book book : bible.getBooks()) {
			for (int cc = 0; cc < book.getChapters().size(); cc++) {
				Chapter chapter = book.getChapters().get(cc);
				if (includeXref && chapter.getProlog() != null) {
					chapter.getProlog().accept(xcv);
				}
				for (Verse v : chapter.getVerses()) {
					countVerse(bibleCounts, totalVerses, book.getAbbr(), book.getId(), cc + 1, v.getNumber());
				}
				if (includeXref) {
					for (Verse vv : chapter.getVerses()) {
						vv.accept(xcv);
					}
				}
			}
		}
		int totalVerseCount = (int) totalVerses.stream().filter(x -> !x.endsWith(":*")).count();
		if (chapterMode == ChapterMode.IGNORE) {
			for (int[] info : bibleCounts.values())
				info[0] = 0;
		}

		// count versification schema verses
		VersificationCounts[] counts = new VersificationCounts[vs.getVersifications().size()];
		for (int i = 0; i < counts.length; i++) {
			Versification vn = vs.getVersifications().get(i);
			Map<BookID, int[]> vsCounts = new EnumMap<>(BookID.class);
			Set<String> foundChapters = new HashSet<>();
			for (int j = 0; j < vn.getVerseCount(); j++) {
				Reference r = vn.getReference(j);
				if (limitBooks && !bibleCounts.containsKey(r.getBook()))
					continue;
				int[] cCounts = vsCounts.computeIfAbsent(r.getBook(), x -> new int[2]);
				if (foundChapters.add(r.getBook().name() + " " + r.getChapter()))
					cCounts[0]++;
				cCounts[1]++;
			}
			counts[i] = new VersificationCounts(vn.getName(), vsCounts, chapterMode, bibleCounts);
		}

		// sort them
		Arrays.sort(counts);

		// print them
		System.out.print("Best match:  ");
		String verboseSchemasProperty = System.getProperty("versificationdetector.verboseschemes", "");
		Set<String> verboseSchemes = verboseSchemasProperty == null ? null : new HashSet<>(Arrays.asList(verboseSchemasProperty.split(", *")));
		printScheme(counts[0], totalVerseCount, verboseSchemes);
		System.out.println();
		System.out.println("Other options:");
		for (int i = 1; i < Math.min(11, counts.length); i++) {
			printScheme(counts[i], totalVerseCount, verboseSchemes);
		}

		// print selected schemes
		if (exportArgs.length > exportArgsIdx) {
			System.out.println();
			System.out.println("Selected schemes:");
			for (int i = exportArgsIdx; i < exportArgs.length; i++) {
				boolean found = false;
				for (VersificationCounts c : counts) {
					if (c.getName().equals(exportArgs[i])) {
						printScheme(c, totalVerseCount, verboseSchemes);
						found = true;
						break;
					}
				}
				if (!found)
					System.out.println(exportArgs[i] + " (Unknown scheme)");
			}
		}
	}

	protected void countVerse(Map<BookID, int[]> bibleCounts, Set<String> totalVerses, String bookAbbr, BookID bookID, int cnum, String vnum) {
		if (vnum.equals("*"))
			return;
		if (!totalVerses.add(bookAbbr + " " + cnum + ":" + vnum))
			return;
		int[] bookCounts = bibleCounts.computeIfAbsent(bookID, x -> new int[2]);
		bookCounts[1]++;
		if (totalVerses.add(bookAbbr + " " + cnum + ":*"))
			bookCounts[0]++;
	}

	private void printScheme(VersificationCounts counts, int totalVerseCount, Set<String> verboseSchemes) {
		List<String> missingInfo = new ArrayList<String>();
		if (counts.missingBooks > 0 || counts.missingChapters > 0 || counts.missingVerses > 0) {
			for (BookID bid : counts.bibleCounts.keySet()) {
				int[] bInfo = counts.bibleCounts.get(bid);
				int[] cInfo = counts.coveredCounts.get(bid);
				if (cInfo != null && (bInfo[0] > cInfo[0] || bInfo[1] > cInfo[1])) {
					missingInfo.add(bid.getOsisID() + "(" + (bInfo[0] == cInfo[0] ? "" : String.format("%+dc", cInfo[0] - bInfo[0])) +
							(cInfo[0] != bInfo[0] && cInfo[1] != bInfo[1] ? "," : "") +
							(bInfo[1] == cInfo[1] ? "" : String.format("%+dv", cInfo[1] - bInfo[1])) + ")");
				}
			}
		}
		if (counts.missingBooks > 0) {
			Set<BookID> missingBooks = EnumSet.noneOf(BookID.class);
			missingBooks.addAll(counts.bibleCounts.keySet());
			missingBooks.removeAll(counts.coveredCounts.keySet());
			System.out.println(counts.getName() + " (Missing books+chapters+verses: " + counts.missingBooks + "+" + counts.missingChapters + "+" + counts.missingVerses + " " + missingBooks + " " + missingInfo);
		} else if (counts.missingChapters > 0) {
			System.out.println(counts.getName() + " (Missing chapters+verses: " + counts.missingChapters + "+" + counts.missingVerses + " " + missingInfo);
		} else if (counts.missingVerses > 0) {
			System.out.println(counts.getName() + " (Missing verses: " + counts.missingVerses + " " + missingInfo + ")");
		} else {
			System.out.println(counts.getName() + " (All verses covered, and " + (counts.verseCount - totalVerseCount) + " more)");
		}
		if (verboseSchemes != null && (verboseSchemes.contains(counts.getName()) || verboseSchemes.contains("all"))) {
			EnumSet<BookID> books = EnumSet.noneOf(BookID.class);
			books.addAll(counts.bibleCounts.keySet());
			books.addAll(counts.coveredCounts.keySet());
			int[] noVerses = new int[2];
			for (BookID bid : books) {
				int[] bInfo = counts.bibleCounts.getOrDefault(bid, noVerses);
				int[] cInfo = counts.coveredCounts.getOrDefault(bid, noVerses);
				System.out.println("\t" + bid.getOsisID() + ": " + cInfo[0] + "c[" + String.format("%+d", cInfo[0] - bInfo[0]) + "], " + cInfo[1] + "v[" + String.format("%+d", cInfo[1] - bInfo[1]) + "]");
			}
		}
	}

	public static final class VersificationCounts implements Comparable<VersificationCounts> {

		private final String name;
		private final Map<BookID, int[]> coveredCounts, bibleCounts;
		private final ChapterMode chapterMode;
		private final int verseCount, missingBooks, missingChapters, missingVerses;

		public VersificationCounts(String name, Map<BookID, int[]> coveredCounts, ChapterMode chapterMode, Map<BookID, int[]> bibleCounts) {
			this.name = name;
			this.coveredCounts = coveredCounts;
			this.bibleCounts = bibleCounts;
			this.chapterMode = chapterMode;
			int verseCount = 0;
			for (int[] bookInfo : coveredCounts.values()) {
				verseCount += bookInfo[1];
				if (chapterMode == ChapterMode.IGNORE)
					bookInfo[0] = 0;
			}
			this.verseCount = verseCount;

			int missingBooks = 0, missingChapters = 0, missingVerses = 0;
			for (Map.Entry<BookID, int[]> bibleBooks : bibleCounts.entrySet()) {
				int[] bInfo = bibleBooks.getValue();
				int[] cInfo = coveredCounts.get(bibleBooks.getKey());
				if (cInfo == null) {
					missingBooks++;
				} else {
					if (cInfo[0] < bInfo[0])
						missingChapters += bInfo[0] - cInfo[0];
					if (cInfo[1] < bInfo[1])
						missingVerses += bInfo[1] - cInfo[1];
				}
			}
			this.missingBooks = missingBooks;
			this.missingChapters = missingChapters;
			this.missingVerses = missingVerses;
		}

		public String getName() {
			return name;
		}

		@Override
		public int compareTo(VersificationCounts other) {
			int result = Integer.compare(missingBooks, other.missingBooks);
			if (result == 0 && chapterMode == ChapterMode.HIGH_PRIO)
				result = Integer.compare(missingChapters, other.missingChapters);
			if (result == 0)
				result = Integer.compare(missingVerses, other.missingVerses);
			if (result == 0 && chapterMode == ChapterMode.LOW_PRIO)
				result = Integer.compare(missingChapters, other.missingChapters);
			if (result == 0)
				result = Integer.compare(verseCount, other.verseCount);
			return result;
		}
	}

	private static enum ChapterMode {
		IGNORE, LOW_PRIO, HIGH_PRIO
	}

	private class XrefCountVisitor extends FormattedText.VisitorAdapter<RuntimeException> {
		private final Map<BookID, int[]> bibleCounts;
		private final Set<String> totalVerses;

		public XrefCountVisitor(Map<BookID, int[]> bibleCounts, Set<String> totalVerses) {
			super(null);
			this.bibleCounts = bibleCounts;
			this.totalVerses = totalVerses;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws RuntimeException {
			countVerse(bibleCounts, totalVerses, firstBookAbbr, firstBook, firstChapter, firstVerse);
			countVerse(bibleCounts, totalVerses, lastBookAbbr, lastBook, lastChapter, lastVerse);
			if (firstBook == lastBook && firstChapter == lastChapter) {
				try {
					int fv = Integer.parseInt(firstVerse);
					int lv = Integer.parseInt(lastVerse);
					for (int v = fv + 1; v < lv; v++) {
						countVerse(bibleCounts, totalVerses, firstBookAbbr, firstBook, lastChapter, "" + v);
					}
				} catch (NumberFormatException ex) {
				}
			}
			return this;
		}
	}
}
