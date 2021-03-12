package biblemulticonverter.format.paratext.utilities;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;

import biblemulticonverter.format.paratext.ParatextBook;
import biblemulticonverter.format.paratext.ParatextCharacterContent;

public class ImportUtilities {

	/**
	 * Adds a ChapterEnd to the given ParatextBook. The location of the ChapterEnd in the ParatextBook is always at the
	 * end of whatever the book already contains.
	 */
	public static void closeOpenChapter(ParatextBook result, ParatextBook.ChapterStart openChapter) {
		if (openChapter != null) {
			result.getContent().add(new ParatextBook.ChapterEnd(openChapter.getLocation()));
		}
	}

	/**
	 * Adds a VerseEnd to the given ParatextBook. The VerseEnd is inserted somewhere near the end of the content that
	 * the ParatextBook might already contain, it is basically a "best guess".
	 */
	public static void closeOpenVerse(ParatextBook result, ParatextCharacterContent.VerseStart openVerse) throws IOException {
		if (openVerse != null) {
			// Search for a ParatextCharacterContent (container) that is:
			// - Not empty (prefer a container that is already written to to close the verse in)
			// - Not directly after a ParagraphKindCategory that is not text, or in other words not in a container that
			//   belongs to a header.
			List<ParatextBook.ParatextBookContentPart> bookParts = result.getContent();
			ListIterator<ParatextBook.ParatextBookContentPart> bookPartsIterator = bookParts.listIterator(bookParts.size());
			boolean didAddVerseEndMilestone = false;
			ParatextCharacterContent lastSuitableContentContainer = null;
			while (bookPartsIterator.hasPrevious()) {
				ParatextBook.ParatextBookContentPart bookPart = bookPartsIterator.previous();
				if (bookPart instanceof ParatextBook.ParagraphStart) {
					if (((ParatextBook.ParagraphStart) bookPart).getKind().getCategory() == ParatextBook.ParagraphKindCategory.TEXT && lastSuitableContentContainer != null) {
						lastSuitableContentContainer.getContent().add(new ParatextCharacterContent.VerseEnd(openVerse.getLocation()));
						didAddVerseEndMilestone = true;
						break;
					}
				} else if (bookPart instanceof ParatextCharacterContent) {
					ParatextCharacterContent content = (ParatextCharacterContent) bookPart;
					if (!content.getContent().isEmpty()) {
						// Only put the verse end milestone in a ParatextCharacterContentContainer that is not empty.
						lastSuitableContentContainer = content;
					}
				}
			}
			if (!didAddVerseEndMilestone) {
				throw new IOException("Could not insert verse end, because no suitable location could be found.");
			}
		}
	}
}
