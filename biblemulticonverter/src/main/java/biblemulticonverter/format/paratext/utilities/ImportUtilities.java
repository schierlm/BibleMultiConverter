package biblemulticonverter.format.paratext.utilities;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;

import biblemulticonverter.format.paratext.ParatextBook;
import biblemulticonverter.format.paratext.ParatextBook.Figure;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKindCategory;
import biblemulticonverter.format.paratext.ParatextBook.VerseEnd;
import biblemulticonverter.format.paratext.ParatextBook.VerseStart;
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
	public static void closeOpenVerse(ParatextBook result, ParatextBook.VerseStart openVerse) throws IOException {
		if (openVerse != null) {
			// Search for a ParatextCharacterContent (container) that is:
			// - Not empty (prefer a container that is already written to to close the verse in)
			// - Not directly after a ParagraphKindCategory that is not text, or in other words not in a container that
			//   belongs to a header.
			// - Not before the corresponding openVerse
			List<ParatextBook.ParatextBookContentPart> bookParts = result.getContent();
			if (!bookParts.isEmpty() && bookParts.get(bookParts.size() - 1) == openVerse) {
				// the open verse is an empty verse at the end of a
				// chapter/book. Provide a place to put the verse end.
				bookParts.add(new ParatextCharacterContent());
			}
			ListIterator<ParatextBook.ParatextBookContentPart> bookPartsIterator = bookParts.listIterator(bookParts.size());
			boolean didAddVerseEndMilestone = false, verseStartFound = false, paraSwitchFound = false;
			ParatextCharacterContent lastSuitableContentContainer = null;
			while (bookPartsIterator.hasPrevious()) {
				ParatextBook.ParatextBookContentPart bookPart = bookPartsIterator.previous();
				if (bookPart instanceof ParatextBook.ParagraphStart) {
					ParagraphKind kind = ((ParatextBook.ParagraphStart) bookPart).getKind();
					ParagraphKindCategory category = kind.getCategory();
					if (category == ParatextBook.ParagraphKindCategory.TEXT && !kind.name().startsWith("INTRO_") && lastSuitableContentContainer != null) {
						bookParts.add(bookParts.indexOf(lastSuitableContentContainer) + 1, new ParatextBook.VerseEnd(openVerse.getLocation()));
						didAddVerseEndMilestone = true;
						break;
					} else if (verseStartFound) {
						if (category != ParatextBook.ParagraphKindCategory.TEXT) {
							System.out.println("WARNING: Verse end marker not inserted for verse marker outside normal text: "+openVerse.getLocation());
							return;
						}
						break;
					}
				} else if (bookPart instanceof ParatextBook.TableCellStart || bookPart instanceof ParatextBook.ChapterStart) {
					if (lastSuitableContentContainer != null) {
						bookParts.add(bookParts.indexOf(lastSuitableContentContainer) + 1, new ParatextBook.VerseEnd(openVerse.getLocation()));
						didAddVerseEndMilestone = true;
						break;
					} else if (verseStartFound) {
						break;
					}
				} else if (bookPart instanceof ParatextCharacterContent) {
					ParatextCharacterContent content = (ParatextCharacterContent) bookPart;
					if (!content.getContent().isEmpty() && (!verseStartFound || paraSwitchFound)) {
						// Only put the verse end milestone in a ParatextCharacterContentContainer that is not empty.
						lastSuitableContentContainer = content;
						if (verseStartFound) {
							lastSuitableContentContainer = null;
						}
					} else if (content.getContent().isEmpty() && bookPartsIterator.hasPrevious()) {
						int prevIdx = bookPartsIterator.previousIndex();
						ParatextBook.ParatextBookContentPart prevPart = bookParts.get(prevIdx);
						if (prevPart == openVerse) {
							// Empty containers are okay if they are just behind the verse start (i.e. an empty verse)
							lastSuitableContentContainer = content;
						} else if (prevIdx > 0 && bookParts.get(prevIdx-1) == openVerse && prevPart instanceof ParatextBook.ParagraphStart) {
							// Empty containers are also okay if they are after a paragraph start behind the verse start (i.e. a verse only consisting of a paragraph break)
							lastSuitableContentContainer = content;
						}
					}
				} else if (bookPart == openVerse) {
					verseStartFound = true;
				}
				if (verseStartFound && !(bookPart instanceof VerseStart || bookPart instanceof VerseEnd || bookPart instanceof ParatextCharacterContent || bookPart instanceof Figure)) {
					paraSwitchFound = true;
				}
			}
			if (!didAddVerseEndMilestone) {
				throw new IOException("Could not insert verse end of " + openVerse.getVerseNumber() + " [" + openVerse.getLocation() + "], because no suitable location could be found.");
			}
		}
	}
}
