package biblemulticonverter.format.paratext.model;

import biblemulticonverter.format.paratext.ParatextBook;
import biblemulticonverter.format.paratext.utilities.LocationParser;

/**
 * Represents a USX SID (Start Identifier) or EID (End Identifier) for a verse.
 */
public class VerseIdentifier extends ChapterIdentifier {

	public final String startVerse;
	public final String endVerse;

	/**
	 * @see #VerseIdentifier(ParatextBook.ParatextID, int, String, String)
	 */
	public VerseIdentifier(ParatextBook.ParatextID book, int chapter, String verse) throws IllegalArgumentException {
		this(book, chapter, verse, null);
	}

	/**
	 * @throws IllegalArgumentException if the provided verse or endVerse String is not a valid verse ID.
	 */
	public VerseIdentifier(ParatextBook.ParatextID book, int chapter, String verse, String endVerse) throws IllegalArgumentException {
		super(book, chapter);
		if (!LocationParser.isValidVerseId(verse)) {
			throw new IllegalArgumentException("Provided verse String is not a valid verse ID: " + verse);
		} else if (endVerse != null && !LocationParser.isValidVerseId(endVerse)) {
			throw new IllegalArgumentException("Provided endVerse String is not a valid verse ID: " + endVerse);
		}
		this.startVerse = verse;
		this.endVerse = endVerse;
	}
	
	/**
	 * Returns this identifiers verse, this may be a range if endVerse is not null.
	 */
	public String verse() {
		return startVerse + (endVerse != null ? "-" + endVerse : "");
	}

	/**
	 * @return a VerseLocation if the location was parsed successfully, null if not.
	 * @throws IllegalArgumentException if the given location is not a valid verse location identifier.
	 */
	public static VerseIdentifier fromStringOrThrow(String location) throws IllegalArgumentException {
		LocationParser parser = new LocationParser(true);
		if (parser.parse(location)) {
			if (parser.getFormat() == LocationParser.Format.VERSE) {
				return new VerseIdentifier(parser.getStartBook(), parser.getStartChapter(), parser.getStartVerse());
			} else if (parser.getFormat() == LocationParser.Format.VERSE_RANGE && parser.getStartChapter() == parser.getEndChapter()) {
				// Only allow verse ranges that do not span multiple chapters
				return new VerseIdentifier(parser.getStartBook(), parser.getStartChapter(), parser.getStartVerse(), parser.getEndVerse());
			}
		}
		throw new IllegalArgumentException("Given location string is not a valid verse location identifier: " + location);
	}

	/**
	 * @return a VerseLocation if the location was parsed successfully, null if not.
	 * @throws IllegalArgumentException if the given location is not a valid verse location identifier.
	 */
	public static VerseIdentifier fromVerseNumberRangeOrThrow(ParatextBook.ParatextID book, int chapter, String verseNumberRange) throws IllegalArgumentException {
		return fromStringOrThrow(book.getIdentifier() + " " + chapter + ":" + verseNumberRange); 
	}

	@Override
	public String toString() {
		return super.toString() + ":" + startVerse + (endVerse != null ? "-" + endVerse : "");
	}
}
