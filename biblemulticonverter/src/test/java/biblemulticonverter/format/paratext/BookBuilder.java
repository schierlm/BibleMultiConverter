package biblemulticonverter.format.paratext;

import java.util.HashSet;
import java.util.Set;

import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.Verse;

public class BookBuilder {

	private final Book book;
	private final Set<VerseBuilder> openVerseBuilders = new HashSet<>();
	private Chapter currentChapter = null;

	public BookBuilder(BookID bookID) {
		this.book = new Book(bookID.getThreeLetterCode(), bookID, bookID.getOsisID(), bookID.getEnglishName());
	}

	public BookBuilder addChapter() {
		currentChapter = new Chapter();
		book.getChapters().add(currentChapter);
		return this;
	}

	public VerseBuilder addVerse(String number) {
		if (currentChapter == null) {
			addChapter();

		}
		VerseBuilder builder = new VerseBuilder(number);
		openVerseBuilders.add(builder);
		return builder;
	}

	public Book create() {
		for (VerseBuilder builder : openVerseBuilders) {
			builder.currentVerse.getAppendVisitor().visitEnd();
		}
		return book;
	}

	public class VerseBuilder {
		private Verse currentVerse = null;

		private VerseBuilder(String number) {
			currentVerse = new Verse(number);
			currentChapter.getVerses().add(currentVerse);
		}

		public VerseBuilder addCrossReference(BookID bookID, int firstChapter, String firstVerse, int lastChapter, String lastVerse) {
			String bookAbbr = bookID.getThreeLetterCode();
			currentVerse.getAppendVisitor().visitCrossReference(bookAbbr, bookID, firstChapter, firstVerse, bookAbbr, bookID, lastChapter, lastVerse);
			return this;
		}

		public BookBuilder endVerse() {
			currentVerse.getAppendVisitor().visitEnd();
			openVerseBuilders.remove(this);
			return BookBuilder.this;
		}
	}
}
