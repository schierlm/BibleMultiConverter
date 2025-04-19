package biblemulticonverter.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.FormattedText.Headline;

/**
 * A virtual verse, consisting of a list of headlines and a list of "real"
 * verses belonging to an integer verse number. Useful for export formats that
 * do not support headlines within verses and/or non-numeric verse numbers.
 *
 */
public class VirtualVerse {

	private final int number;
	private final List<Headline> headlines = new ArrayList<Headline>();
	private final List<Verse> verses = new ArrayList<Verse>();

	protected VirtualVerse(int number) {
		this.number = Utils.validateNumber("number", number, 0, Integer.MAX_VALUE);
	}

	public void validate(Bible bible, BookID book, String bookAbbr, int cnumber, List<String> danglingReferences, Map<String, Set<String>> dictionaryEntries, Map<String, Set<FormattedText.ValidationCategory>> validationCategories, Set<String> internalAnchors, Set<String> internalLinks) {
		int lastHeadlineDepth = 0;
		String locationBase = bookAbbr + " " + cnumber + ":";
		String location = locationBase + "v" + getNumber();
		for (Verse verse : verses) {
			if (validationCategories != null && validationCategories.containsKey(locationBase + verse.getNumber()))
				return;
		}
		for (Headline headline : headlines) {
			if (headline.getDepth() <= lastHeadlineDepth)
				FormattedText.ValidationCategory.INVALID_HEADLINE_DEPTH_ORDER.throwOrRecord(location + ":Headline", validationCategories, headline.getDepth() + " after " + lastHeadlineDepth);
			lastHeadlineDepth = headline.getDepth() == 9 ? 8 : headline.getDepth();
			headline.validate(bible, book, location + ":Headline", danglingReferences, dictionaryEntries, validationCategories, internalAnchors, internalLinks);
		}
		Set<String> verseNumbers = new HashSet<String>();
		for (Verse verse : verses) {
			if (!verseNumbers.add(verse.getNumber()))
				FormattedText.ValidationCategory.DUPLICATE_VERSE.throwOrRecord(location, validationCategories, bookAbbr + " " + cnumber + ":"+verse.getNumber());
			verse.validate(bible, book, location + ":" + verse.getNumber(), danglingReferences, dictionaryEntries, validationCategories, internalAnchors, internalLinks);
		}
	}

	public int getNumber() {
		return number;
	}

	public List<Headline> getHeadlines() {
		return headlines;
	}

	public List<Verse> getVerses() {
		return verses;
	}
}
