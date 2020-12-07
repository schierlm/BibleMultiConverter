package biblemulticonverter.format.paratext;

import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.Verse;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import org.junit.Test;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AbstractParatextFormatTest {

	@Test
	public void test_on_export_internal_verse_numbers_are_transformed_to_paratext_verse_numbers() {
		// Dummy paratext book
		Book book = new Book("1co", BookID.BOOK_1Cor, "1 Cor", "1 Corinthians");
		Chapter chapter = new Chapter();
		book.getChapters().add(chapter);
		addDummyVerse(chapter, "5");
		addDummyVerse(chapter, "5/7");

		AbstractParatextFormat format = new ParatextFormat();

		ParatextBook paratextBook = format.exportToParatextBook(book, "test");
		ParatextCharacterContent.VerseStart verse2 = paratextBook.findLastCharacterContent(ParatextCharacterContent.VerseStart.class);

		ParatextCharacterContent.VerseStart verse1 = paratextBook.findLastCharacterContent(ParatextCharacterContent.VerseStart.class, verse2);

		assertEquals("5", verse1.getVerseNumber());
		assertEquals("5-7", verse2.getVerseNumber());
	}

	@Test
	public void test_on_import_paratext_verse_numbers_are_transformed_to_internal_verse_numbers() {
		// Dummy paratext book
		ParatextBook paratextBook = new ParatextBook(ParatextBook.ParatextID.ID_1CO, null);
		paratextBook.getContent().add(new ParatextBook.ChapterStart(new ChapterIdentifier(paratextBook.getId(), 1)));
		paratextBook.getContent().add(new ParatextBook.ParagraphStart(ParatextBook.ParagraphKind.PARAGRAPH_P));
		ParatextCharacterContent characterContent = new ParatextCharacterContent();
		paratextBook.getContent().add(characterContent);

		// Normal verse number
		addDummyVerse(characterContent, new VerseIdentifier(paratextBook.getId(), 1, "5", null), "5");

		// Paratext only supported verse number
		addDummyVerse(characterContent, new VerseIdentifier(paratextBook.getId(), 1, "6b", "7b"), "6b-7b");
		paratextBook.getContent().add(new ParatextBook.ChapterEnd(new ChapterIdentifier(paratextBook.getId(), 1)));

		AbstractParatextFormat format = new ParatextFormat();

		Map<ParatextBook.ParatextID, String> abbrs = new EnumMap<>(ParatextBook.ParatextID.class);
		abbrs.put(ParatextBook.ParatextID.ID_1CO, "1co");

		Book book = format.importParatextBook(paratextBook, abbrs);

		Chapter chapter = book.getChapters().get(0);
		assertEquals("5", chapter.getVerses().get(0).getNumber());
		assertEquals("6b", chapter.getVerses().get(1).getNumber());
	}

	private void addDummyVerse(Chapter chapter, String number) {
		Verse verse = new Verse(number);
		verse.getAppendVisitor().visitText("Lorem Ipsum");
		verse.getAppendVisitor().visitEnd();
		chapter.getVerses().add(verse);
	}

	private void addDummyVerse(ParatextCharacterContent content, VerseIdentifier identifier, String number) {
		content.getContent().add(
				new ParatextCharacterContent.VerseStart(identifier, number)
		);
		content.getContent().add(new ParatextCharacterContent.Text("Lorem Ipsum"));
		content.getContent().add(new ParatextCharacterContent.VerseEnd(identifier));
	}

	private static class ParatextFormat extends AbstractParatextFormat {

		@Override
		protected ParatextBook doImportBook(File inputFile) throws Exception {
			throw new RuntimeException();
		}

		@Override
		protected void doExportBook(ParatextBook book, File outFile) throws Exception {
			throw new RuntimeException();
		}
	}
}
