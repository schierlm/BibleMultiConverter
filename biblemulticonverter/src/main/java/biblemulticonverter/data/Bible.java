package biblemulticonverter.data;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a complete bible. A bible has a name and a list of books.
 */
public class Bible {

	private final String name;
	private final List<Book> books;

	public Bible(String name) {
		this.name = Utils.validateString("name", name, Utils.NORMALIZED_WHITESPACE_REGEX);
		this.books = new ArrayList<Book>();
	}

	public MetadataBook getMetadataBook() {
		if (books.size() > 0 && books.get(0).getId() == BookID.METADATA) {
			return new MetadataBook(books.get(0));
		} else {
			return null;
		}
	}

	public String getName() {
		return name;
	}

	public List<Book> getBooks() {
		return books;
	}

	public void validate(List<String> danglingReferences) {
		validate(danglingReferences, null);
	}

	public void validate(List<String> danglingReferences, Map<String, Set<String>> dictionaryEntries) {
		Set<BookID> bookIDs = EnumSet.noneOf(BookID.class);
		Set<String> bookAbbrs = new HashSet<String>();
		Set<String> bookShortNames = new HashSet<String>();
		Set<String> bookLongNames = new HashSet<String>();
		if (books.size() == 0)
			throw new IllegalStateException("Bible does not have books");
		for (Book book : books) {
			book.validate(this, danglingReferences, dictionaryEntries);
			if (book.getId() == BookID.METADATA) {
				if (books.size() == 1)
					throw new IllegalStateException("Bible has only metadata book");
				getMetadataBook().validate();
			}
			if (book.getId() == BookID.DICTIONARY_ENTRY) {
				if (book.getChapters().size() != 1 || book.getChapters().get(0).getProlog() == null || !book.getChapters().get(0).getVerses().isEmpty()) {
					throw new IllegalStateException("Malformed dictionary entry: " + book.getAbbr());
				}
			} else if (!bookIDs.add(book.getId())) {
				throw new IllegalStateException("Ambiguous book id " + book.getId());
			}
			if (!bookAbbrs.add(book.getAbbr()) || !bookShortNames.add(book.getShortName()) || !bookLongNames.add(book.getLongName()))
				throw new IllegalStateException("Duplicate book reference in " + book.getId());
		}
	}

	protected Book getBook(String bookAbbr, BookID bookID) {
		for (Book book : books) {
			if (book.getAbbr().equals(bookAbbr) && book.getId() == bookID)
				return book;
			if (book.getAbbr().equals(bookAbbr) || book.getId() == bookID)
				throw new IllegalStateException("Partial match of xref book");
		}
		return null;
	}
}
