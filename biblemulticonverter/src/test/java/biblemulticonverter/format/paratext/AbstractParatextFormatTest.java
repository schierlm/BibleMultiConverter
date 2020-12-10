package biblemulticonverter.format.paratext;

import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AbstractParatextFormatTest {

	@Test
	public void test_on_export_internal_verse_numbers_are_transformed_to_paratext_verse_numbers() {
		// Dummy paratext book
		Book book = new BookBuilder(BookID.BOOK_1Cor).
				addChapter()
				.addVerse("5").endVerse()
				.addVerse("5/7").endVerse()
				.create();

		final ParatextBook paratextBook = new ParatextFormat().exportToParatextBook(book, "test");

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

		final ParatextBook paratextBook = new ParatextFormat().exportToParatextBook(book, "test");

		final List<ParatextCharacterContent.Reference> resultReferences = new ArrayList<>();
		ParatextCharacterContent.Reference reference = null;
		while ((reference = paratextBook.findLastCharacterContent(ParatextCharacterContent.Reference.class, reference)) != null) {
			resultReferences.add(reference);
		}
		Collections.reverse(resultReferences);

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
