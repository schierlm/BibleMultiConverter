package biblemulticonverter.format;

import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;

public class VersificationMappedDiffable implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export like Diffable, but change the Versification first.",
			"",
			"Usage: VersificationMappedDiffable <OutputFile> <mappings.bmcv> <from>/<to> [<option> [...]]",
			"",
			"Supported options:",
			"DropUnmapped  - unmapped verses get dropped (and cross references unlinked) instead of",
			"                keeping them",
			"ShowNumbers   - Add bold verse numbers of original verses in parentheses",
			"AddTags       - Add tags (Extra Attributes) to automatically identify the mapping later",
			"",
			"Note that currently there are no tools that can read and parse the tags.",
			"If a book contains prologs, they will go to the chapter that will contain the first verse " +
					"of the original chapter."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String outputFile = exportArgs[0];
		VersificationSet vs = new VersificationSet(new File(exportArgs[1]));
		VersificationMapping vm = vs.findMapping(exportArgs[2]);
		boolean dropUnmapped = false, showNumbers = false, addTags = false;
		;
		for (int i = 3; i < exportArgs.length; i++) {
			if (exportArgs[i].equals("DropUnmapped"))
				dropUnmapped = true;
			else if (exportArgs[i].equals("ShowNumbers"))
				showNumbers = true;
			else if (exportArgs[i].equals("AddTags"))
				addTags = true;
			else
				throw new IllegalArgumentException("Unsupported option: " + exportArgs[i]);
		}
		Map<BookID, String> abbrMap = new EnumMap<>(BookID.class);
		for (Book book : bible.getBooks()) {
			abbrMap.put(book.getId(), book.getAbbr());
		}
		Bible newBible = new Bible(bible.getName());
		Map<BookID, Book> newBooks = new EnumMap<>(BookID.class);
		for (Book book : bible.getBooks()) {
			if (book.getId().getZefID() < 1) {
				// metadata book, introduction or appendix
				newBible.getBooks().add(book);
				continue;
			}
			int cnumber = 0;
			for (Chapter chap : book.getChapters()) {
				cnumber++;
				if (chap.getProlog() != null && chap.getVerses().isEmpty()) {
					System.out.println("WARNING: Prolog for " + book.getAbbr() + " " + cnumber + " got lost as chapter contains no verses.");
				}
				for (int j = 0; j < chap.getVerses().size(); j++) {
					Verse oldVerse = chap.getVerses().get(j);
					Reference ref = new Reference(book.getId(), cnumber, oldVerse.getNumber()), newRef;
					List<Reference> newRefs = vm.getMapping(ref);
					if ((newRefs == null || newRefs.isEmpty()) && dropUnmapped) {
						if (j == 0 && chap.getProlog() != null) {
							System.out.println("WARNING: Prolog for " + book.getAbbr() + " " + cnumber + " got lost as first verse of it is unmapped.");
						}
						continue;
					}
					if (newRefs == null || newRefs.contains(ref) || newRefs.isEmpty())
						newRef = ref;
					else
						newRef = newRefs.get(0);
					if (!newBooks.containsKey(newRef.getBook())) {
						Book newBook = null;
						for (Book oldBook : bible.getBooks()) {
							if (oldBook.getId() == newRef.getBook()) {
								newBook = new Book(oldBook.getAbbr(), newRef.getBook(), oldBook.getShortName(), oldBook.getLongName());
								break;
							}
						}
						if (newBook == null)
							newBook = new Book(newRef.getBook().getOsisID(), newRef.getBook(), newRef.getBook().getEnglishName(), newRef.getBook().getEnglishName());
						newBooks.put(newRef.getBook(), newBook);
						newBible.getBooks().add(newBook);
					}
					Book newBook = newBooks.get(newRef.getBook());
					while (newBook.getChapters().size() < newRef.getChapter())
						newBook.getChapters().add(new Chapter());
					Chapter newChapter = newBook.getChapters().get(newRef.getChapter() - 1);
					if (j == 0 && chap.getProlog() != null) {
						FormattedText newProlog = new FormattedText();
						if (newChapter.getProlog() != null) {
							newChapter.getProlog().accept(newProlog.getAppendVisitor());
							newProlog.getAppendVisitor().visitLineBreak(LineBreakKind.PARAGRAPH);
						}
						chap.getProlog().accept(new MapXrefVisitor(newProlog.getAppendVisitor(), vm, dropUnmapped, abbrMap));
						newProlog.finished();
						newChapter.setProlog(newProlog);
					}
					Verse newVerse = null;
					for (Verse v : newChapter.getVerses()) {
						if (v.getNumber().equals(newRef.getVerse())) {
							newVerse = v;
							break;
						}
					}
					boolean needSpace = true;
					if (newVerse == null) {
						newVerse = new Verse(newRef.getVerse());
						newChapter.getVerses().add(newVerse);
						needSpace = false;
					}
					if (needSpace || !ref.equals(newRef)) {
						Visitor<RuntimeException> v = newVerse.getAppendVisitor();
						if (addTags)
							v = v.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "v11n", "origverse", ref.getBook().getOsisID() + "--" + ref.getChapter() + "--" + ref.getVerse());
						if (needSpace)
							v.visitText(" ");
						if (showNumbers) {
							String verseNumber;
							if (!ref.getBook().equals(newRef.getBook())) {
								verseNumber = ref.getBook().getOsisID() + " " + ref.getChapter() + ":" + ref.getVerse();
							} else if (ref.getChapter() != newRef.getChapter()) {
								verseNumber = ref.getChapter() + ":" + ref.getVerse();
							} else {
								verseNumber = ref.getVerse();
							}
							v.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText("(" + verseNumber + ")");
							v.visitText(" ");
						}
					}
					oldVerse.accept(new MapXrefVisitor(newVerse.getAppendVisitor(), vm, dropUnmapped, abbrMap));
				}
			}
		}
		for (Book bk : newBible.getBooks()) {
			for (Chapter ch : bk.getChapters()) {
				for (Verse v : ch.getVerses()) {
					v.finished();
				}
			}
		}
		bible = newBible;
		new Diffable().doExport(bible, new String[] { outputFile });
	}

	private static class MapXrefVisitor extends VisitorAdapter<RuntimeException> {

		private final Visitor<RuntimeException> next;
		private final VersificationMapping vm;
		private final boolean dropUnmapped;
		private final Map<BookID, String> abbrMap;

		private MapXrefVisitor(Visitor<RuntimeException> next, VersificationMapping vm, boolean dropUnmapped, Map<BookID, String> abbrMap) {
			super(next);
			this.next = next;
			this.vm = vm;
			this.dropUnmapped = dropUnmapped;
			this.abbrMap = abbrMap;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return new MapXrefVisitor(childVisitor, vm, dropUnmapped, abbrMap);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			Reference ref = new Reference(book, firstChapter, firstVerse), newRef;
			List<Reference> newRefs = vm.getMapping(ref);
			if ((newRefs == null || newRefs.isEmpty()) && dropUnmapped) {
				return next;
			}
			if (newRefs == null || newRefs.contains(ref) || newRefs.isEmpty())
				newRef = ref;
			else
				newRef = newRefs.get(0);
			String newAbbr;
			if (book == newRef.getBook())
				newAbbr = bookAbbr;
			else if (abbrMap.containsKey(newRef.getBook()))
				newAbbr = abbrMap.get(newRef.getBook());
			else
				newAbbr = newRef.getBook().getOsisID();
			return next.visitCrossReference(newAbbr, newRef.getBook(), newRef.getChapter(), newRef.getVerse(), newRef.getChapter(), newRef.getVerse());
		}
	}
}
