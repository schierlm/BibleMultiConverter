package biblemulticonverter.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A range of verses, that belong to a single chapter, but may be re-ordered. It
 * is defined by an optional chapter number, minimum and maximum verse number,
 * and the verses in this range. Useful for export formats that support
 * re-ordered verses and verse ranges (and headlines in the middle of verses),
 * but do not support verses that cover non-adjacent or split verses. For
 * example, verses <tt>1, 6, 2.4, 3a, 5, 3b, 7-9, 11, 10</tt> get merged into
 * the ranges <tt>1, 6, 2-5, 7-9, 11, 10</tt>.
 */
public class VerseRange {

	private final int chapter; // 0 if not given
	private final int minVerse, maxVerse;
	private final List<Verse> verses;

	protected VerseRange(int chapter, int minVerse, int maxVerse, Verse verse) {
		this.chapter = Utils.validateNumber("chapter", chapter, 0, Integer.MAX_VALUE);
		this.minVerse = Utils.validateNumber("minVerse", minVerse, 1, Integer.MAX_VALUE);
		this.maxVerse = Utils.validateNumber("maxVerse", maxVerse, minVerse, Integer.MAX_VALUE);
		verses = Collections.singletonList(verse);
	}

	protected VerseRange(VerseRange first, VerseRange second) {
		int chapter, minVerse, maxVerse;
		if (first.chapter == second.chapter) {
			chapter = first.chapter;
			minVerse = Math.min(first.minVerse, second.minVerse);
			maxVerse = Math.max(first.maxVerse, second.maxVerse);
		} else if (first.chapter == 0) {
			chapter = second.chapter;
			minVerse = second.minVerse;
			maxVerse = second.maxVerse;
		} else {
			chapter = first.chapter;
			minVerse = first.minVerse;
			maxVerse = second.maxVerse;
		}
		this.chapter = Utils.validateNumber("chapter", chapter, 0, Integer.MAX_VALUE);
		this.minVerse = Utils.validateNumber("minVerse", minVerse, 1, Integer.MAX_VALUE);
		this.maxVerse = Utils.validateNumber("maxVerse", maxVerse, minVerse, Integer.MAX_VALUE);
		List<Verse> verses = new ArrayList<>(first.verses);
		verses.addAll(second.verses);
		this.verses = Collections.unmodifiableList(verses);
	}

	protected boolean overlaps(VerseRange other) {
		return chapter == other.chapter && maxVerse >= other.minVerse && other.maxVerse >= minVerse;
	}

	public void validate(Bible bible, BookID book, String bookAbbr, int cnumber, List<String> danglingReferences, Map<String,Set<String>> dictionaryEntries) {
		String location = bookAbbr + " " + cnumber + ":[" + (chapter == 0 ? "" : chapter + ",") + minVerse + "-" + maxVerse + "]";
		Set<String> verseNumbers = new HashSet<String>();
		for (Verse verse : verses) {
			if (!verseNumbers.add(verse.getNumber()))
				throw new IllegalStateException("Duplicate verse number");
			verse.validate(bible, book, location + verse.getNumber(), danglingReferences, dictionaryEntries);
		}
	}

	public int getChapter() {
		return chapter;
	}

	public int getMinVerse() {
		return minVerse;
	}

	public int getMaxVerse() {
		return maxVerse;
	}

	public List<Verse> getVerses() {
		return Collections.unmodifiableList(verses);
	}
}
