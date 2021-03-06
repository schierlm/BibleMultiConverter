package biblemulticonverter.format.paratext;

import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;

import static org.junit.Assert.assertEquals;

public class AbstractParatextFormatTest {

	@Test
	public void test_on_export_internal_verse_numbers_are_transformed_to_paratext_verse_numbers() {
		// Dummy paratext book
		Book book = new BookBuilder(BookID.BOOK_1Cor).
				addChapter()
				.addVerse("5").endVerse()
				.addVerse("11/13").endVerse()
				.addVerse("4.6.9").endVerse()
				.addVerse("2G").endVerse()
				.addVerse("13-15a").endVerse()
				.addVerse("10/12G").endVerse()
				.addVerse("1-4.7").endVerse()
				.addVerse("1.4-7").endVerse()
				.addVerse("41,6").endVerse()
				.create();

		final ParatextBook paratextBook = new TestParatextFormat().exportToParatextBook(book, "test");
		
		final List<ParatextCharacterContent.VerseStart> actualVerses = paratextBook.findAllCharacterContent(ParatextCharacterContent.VerseStart.class);
		assertEquals("5", actualVerses.get(0).getVerseNumber());
		assertEquals("11", actualVerses.get(1).getVerseNumber());
		assertEquals("4", actualVerses.get(2).getVerseNumber());
		assertEquals("2", actualVerses.get(3).getVerseNumber());
		assertEquals("13-15a", actualVerses.get(4).getVerseNumber());
		assertEquals("10", actualVerses.get(5).getVerseNumber());
		assertEquals("1-4", actualVerses.get(6).getVerseNumber());
		assertEquals("1", actualVerses.get(7).getVerseNumber());
		assertEquals("6", actualVerses.get(8).getVerseNumber());
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
		addDummyVerse(characterContent, new VerseIdentifier(paratextBook.getId(), 1, "6b", "7a"), "6b-7a");
		paratextBook.getContent().add(new ParatextBook.ChapterEnd(new ChapterIdentifier(paratextBook.getId(), 1)));

		AbstractParatextFormat format = new TestParatextFormat();

		Map<ParatextBook.ParatextID, String> abbrs = new EnumMap<>(ParatextBook.ParatextID.class);
		abbrs.put(ParatextBook.ParatextID.ID_1CO, "1co");

		Book book = format.importParatextBook(paratextBook, abbrs);

		Chapter chapter = book.getChapters().get(0);
		assertEquals("5", chapter.getVerses().get(0).getNumber());
		assertEquals("6b", chapter.getVerses().get(1).getNumber());
	}

	@Test
	public void test_on_export_internal_cross_references_are_transformed_to_paratext_cross_references() {
		// Dummy paratext book
		final Book book = new BookBuilder(BookID.BOOK_1Cor)
				.addChapter()
				.addVerse("5")
				.addCrossReference(BookID.BOOK_1Kgs, 1, "1", 999, "999")
				.addCrossReference(BookID.BOOK_1Kgs, 1, "1", 1, "999")
				.addCrossReference(BookID.BOOK_1Kgs, 6, "1", 6, "999")
				.addCrossReference(BookID.BOOK_1Kgs, 6, "5", 6, "999")
				.addCrossReference(BookID.BOOK_1Kgs, 11, "6a", 11, "6a")
				.addCrossReference(BookID.BOOK_1Kgs, 5, "1", 11, "999")
				.endVerse()
				.create();

		final ParatextBook paratextBook = new TestParatextFormat().exportToParatextBook(book, "test");

		final List<ParatextCharacterContent.Reference> resultReferences = paratextBook.findAllCharacterContent(ParatextCharacterContent.Reference.class);
		assertEqualsReference(ParatextBook.ParatextID.ID_1KI, -1, null, -1, null, resultReferences.get(0));
		assertEqualsReference(ParatextBook.ParatextID.ID_1KI, 1, null, -1, null, resultReferences.get(1));
		assertEqualsReference(ParatextBook.ParatextID.ID_1KI, 6, null, -1, null, resultReferences.get(2));
		assertEqualsReference(ParatextBook.ParatextID.ID_1KI, 6, "5", 6, "999", resultReferences.get(3));
		assertEqualsReference(ParatextBook.ParatextID.ID_1KI, 11, "6a", -1, null, resultReferences.get(4));
		assertEqualsReference(ParatextBook.ParatextID.ID_1KI, 5, null, 11, null, resultReferences.get(5));
	}

	private void assertEqualsReference(ParatextBook.ParatextID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse, ParatextCharacterContent.Reference actual) {
		assertEquals(book, actual.getBook());
		assertEquals(firstChapter, actual.getFirstChapter());
		assertEquals(lastChapter, actual.getLastChapter());
		assertEquals(firstVerse, actual.getFirstVerse());
		assertEquals(lastVerse, actual.getLastVerse());
	}

	private void addDummyVerse(ParatextCharacterContent content, VerseIdentifier identifier, String number) {
		content.getContent().add(
				new ParatextCharacterContent.VerseStart(identifier, number)
		);
		content.getContent().add(ParatextCharacterContent.Text.from("Lorem Ipsum"));
		content.getContent().add(new ParatextCharacterContent.VerseEnd(identifier));
	}

	private static class TestParatextFormat extends AbstractParatextFormat {

		public TestParatextFormat() {
			super("TestParatextFormat");
		}

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
