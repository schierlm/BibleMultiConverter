package biblemulticonverter.format.paratext.utilities;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.format.paratext.ParatextBook;

/**
 * Parses USX location's such as used in the Reference loc attribute, or verse/chapter sid and eid attributes.
 */
public class LocationParser {

	private static final Pattern LOCATION_PATTERN = Pattern.compile("^([A-Z1-4]{3})(?:-([A-Z1-4]{3})|(?: ([0-9]+)(?:(?:-([0-9]+))|(?::([a-z0-9]+)(?:-([a-z0-9]+)(?::([a-z0-9]+))?)?))?))?$");

	private static final Pattern CHAPTER_ID_PATTERN = Pattern.compile("^[0-9]+$");
	private static final Pattern VERSE_ID_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)?$");

	private final boolean allowAbbreviatedVerseRanges;

	private ParatextBook.ParatextID startBook = null;
	private ParatextBook.ParatextID endBook = null;
	private int startChapter = -1;
	private int endChapter = -1;
	private String startVerse = null;
	private String endVerse = null;

	private Format format = null;

	/**
	 * @param allowAbbreviatedVerseRanges if true this parser also parses abbreviated verse ranges with formats like:
	 *                                    MAT 3:5-6 instead of only accepting MAT 3:5-3:6. In the USX ref element
	 *                                    abbreviated verse ranges are not allowed, however they are allowed in verse
	 *                                    SID and EID identifiers.
	 */
	public LocationParser(boolean allowAbbreviatedVerseRanges) {
		this.allowAbbreviatedVerseRanges = allowAbbreviatedVerseRanges;
	}

	private void reset() {
		startBook = null;
		endBook = null;
		startChapter = -1;
		startVerse = null;
		endChapter = -1;
		endVerse = null;
		format = null;
	}

	public boolean parse(String rawLocation) {
		reset();
		Matcher matcher = LOCATION_PATTERN.matcher(rawLocation);
		boolean isValid = matcher.matches();
		if (!isValid) {
			// Quickly return is matcher is unable to match
			return false;
		}

		String startBookIdentifier = matcher.group(1);
		startBook = ParatextBook.ParatextID.fromIdentifier(startBookIdentifier);
		if (startBook == null) {
			// Unable to parse start book, return.
			reset();
			return false;
		}

		String endBookIdentifier = matcher.group(2);
		if (endBookIdentifier != null) {
			endBook = ParatextBook.ParatextID.fromIdentifier(endBookIdentifier);
			if (endBook == null) {
				// Unable to parse end book, return.
				reset();
				return false;
			}
		}

		if (endBook != null) {
			// End book found, this must be a book range
			format = Format.BOOK_RANGE;
		} else if (matcher.group(3) == null) {
			// Start chapter is not found as well as an end book, this must be a single book
			format = Format.BOOK;
		} else {
			startChapter = Integer.parseInt(matcher.group(3));
			String chapter = matcher.group(4);
			String verse = matcher.group(5);

			if (chapter == null && verse == null) {
				// Nothing after startChapter (no dash or double colon followed by a chapter or verse number)
				format = Format.CHAPTER;
			} else if (chapter != null) {
				// Found endChapter
				format = Format.CHAPTER_RANGE;
				endChapter = Integer.parseInt(chapter);
			} else {
				// Found startVerse
				startVerse = verse;
				if (matcher.group(6) == null) {
					// Nothing found after startVerse
					format = Format.VERSE;
				} else if (matcher.group(7) == null) {
					if (allowAbbreviatedVerseRanges) {
						// Only one number found after the dash and startVerse
						endChapter = startChapter;
						endVerse = matcher.group(6);
						format = Format.VERSE_RANGE;
					} else {
						reset();
						return false;
					}
				} else {
					// Two numbers found after the dash and startVerse
					endChapter = Integer.parseInt(matcher.group(6));
					endVerse = matcher.group(7);
					format = Format.VERSE_RANGE;
				}
			}
		}
		return true;
	}

	public Format getFormat() {
		return format;
	}

	public ParatextBook.ParatextID getStartBook() {
		return startBook;
	}

	public ParatextBook.ParatextID getEndBook() {
		return endBook;
	}

	public int getStartChapter() {
		return startChapter;
	}

	public int getEndChapter() {
		return endChapter;
	}

	public String getStartVerse() {
		return startVerse;
	}

	public String getEndVerse() {
		return endVerse;
	}

	/**
	 * Get whether the given verse id is a valid Paratext verse id.
	 *
	 * @param id              the id to validate.
	 * @param allowVerseRange whether or not to accept verse ranges as in 6-8 or 5-7a etc.
	 * @return true if the given id is deemed valid, false if not.
	 */
	public static boolean isValidVerseId(String id, boolean allowVerseRange) {
		Matcher matcher = VERSE_ID_PATTERN.matcher(id);
		if (matcher.matches()) {
			if (matcher.group(1) != null && !allowVerseRange) {
				return false;
			} else {
				return true;
			}
		}
		return false;
	}

	public static boolean isValidChapterId(String id) {
		return CHAPTER_ID_PATTERN.matcher(id).matches();
	}

	public enum Format {
		/**
		 * E.g. MAT
		 */
		BOOK,
		/**
		 * E.g. MAT-LUK
		 */
		BOOK_RANGE,
		/**
		 * E.g. MAT 3
		 */
		CHAPTER,
		/**
		 * E.g. MAT 3-4
		 */
		CHAPTER_RANGE,
		/**
		 * E.g. MAT 3:5
		 */
		VERSE,
		/**
		 * E.g. MAT 3:5-4:6
		 * <p>
		 * Verse ranges like MAT 3:4-6 are converted to this format and turned into: MAT 3:4-3:6. Users of this parser
		 * can decide for themselves to format the resulting parts in either of the two forms (if needed).
		 */
		VERSE_RANGE
	}
}
