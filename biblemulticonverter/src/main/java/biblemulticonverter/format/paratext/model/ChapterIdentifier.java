package biblemulticonverter.format.paratext.model;

import biblemulticonverter.format.paratext.ParatextBook;
import biblemulticonverter.format.paratext.utilities.LocationParser;

/**
 * Represents a USX SID (Start Identifier) or EID (End Identifier) for a chapter.
 */
public class ChapterIdentifier {

	public final ParatextBook.ParatextID book;
	public final int chapter;

	public ChapterIdentifier(ParatextBook.ParatextID book, int chapter) throws IllegalArgumentException {
		this.book = book;
		this.chapter = chapter;
	}

	/**
	 * @return a VerseLocation if the rawLocation was parsed successfully, null if not.
	 */
	public static ChapterIdentifier fromLocationString(String rawLocation) {
		// Should not really matter if true or false is used for `allowAbbreviatedVerseRanges`, since only
		// Format.CHAPTER is allowed in this method, but to stay consistent with VerseLocation true seems to most
		// appropriate.
		LocationParser parser = new LocationParser();
		if (parser.parse(rawLocation) && parser.getFormat() == LocationParser.Format.CHAPTER) {
			return new ChapterIdentifier(parser.getStartBook(), parser.getStartChapter());
		} else {
			return null;
		}
	}

	@Override
	public String toString() {
		return book.getIdentifier() + " " + chapter;
	}
}
