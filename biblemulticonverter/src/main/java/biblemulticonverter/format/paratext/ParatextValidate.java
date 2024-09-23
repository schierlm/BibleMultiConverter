package biblemulticonverter.format.paratext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKindCategory;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Milestone;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;

/**
 * Exporter that validates a Paratext bible.
 */
public class ParatextValidate extends AbstractParatextFormat {

	public static final String[] HELP_TEXT = {
			"Validate a Paratext bible"
	};

	public ParatextValidate() {
		super("ParatextValidate");
	}

	@Override
	protected List<ParatextBook> doImportAllBooks(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void doExportBooks(List<ParatextBook> books, String... exportArgs) throws Exception {
		if (validateBooks(books) > 0) {
			System.out.println("*** VALIDATION FAILED ***");
		}
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws Exception {
		if (validateBook(book) > 0) {
			System.out.println("*** VALIDATION FAILED ***");
		}
	}

	protected int validateBooks(List<ParatextBook> books) throws Exception {
		int errorCount = 0;
		for (ParatextBook book : books) {
			errorCount += validateBook(book);
		}
		return errorCount;
	}

	protected int validateBook(ParatextBook book) throws Exception {
		ValidateBookVisitor v = new ValidateBookVisitor(book.getId());
		book.accept(v);
		return v.getErrorCount();
	}

	private static class ValidateBookVisitor implements ParatextBookContentVisitor<RuntimeException> {

		private final List<String> openMilestones = new ArrayList<>();
		boolean expectContent = false, foundContent = false;
		private Reference currentReference;
		private ChapterIdentifier openChapter = null;
		private VerseIdentifier openVerse = null, lastClosedVerse = null;
		private ParagraphKind openParagraph = null;
		private int errorCount = 0;

		public ValidateBookVisitor(ParatextID id) {
			currentReference = Reference.book(id, "");
		}

		public int getErrorCount() {
			checkContent(false);
			if (openVerse != null) {
				violation("Verse open at end of book");
			}
			if (openChapter != null) {
				violation("Chapter open at end of book");
			}
			for (String ms : openMilestones) {
				violation("Milestone not closed: " + ms);
			}
			return errorCount;
		}

		protected void violation(String message) {
			String ref = String.valueOf(currentReference);
			if (currentReference != null && currentReference.getFirstVerse() == null && lastClosedVerse != null) {
				ref += " (after " + lastClosedVerse + ")";
			}
			System.out.println("[" + ref + "] " + message);
			errorCount++;
		}

		private void checkContent(boolean newExpectContent) {
			if (expectContent && !foundContent) {
				violation("Text content expected but not found in paragraph type " + openParagraph);
			}
			foundContent = false;
			expectContent = newExpectContent;
		}

		@Override
		public void visitChapterStart(ChapterIdentifier location) {
			if (openVerse != null) {
				violation("Verse still open when opening chapter " + location);
			}
			if (openChapter != null) {
				violation("Chapter still open when opening chapter " + location);
			}
			openChapter = location;
			openVerse = null;
			lastClosedVerse = null;
			checkContent(false);
			currentReference = Reference.chapter(currentReference.getBook(), location.chapter, "");
			openParagraph = null;
		}

		@Override
		public void visitChapterEnd(ChapterIdentifier location) {
			if (openVerse != null) {
				violation("Verse still open when closing chapter " + location);
			}
			if (openChapter == null) {
				violation("No chapter open when closing chapter " + location);
			} else if (openChapter.chapter != location.chapter || openChapter.book != location.book) {
				violation("Other chapter open: " + openChapter + ", when closing chapter " + location);
			}
			openChapter = null;
			openVerse = null;
			checkContent(false);
			currentReference = Reference.book(currentReference.getBook(), "");
			openParagraph = null;
		}

		@Override
		public void visitRemark(String content) {
			checkContent(false);
			openParagraph = null;
		}

		@Override
		public void visitParagraphStart(ParagraphKind kind) {
			checkContent(kind.getCategory() != ParagraphKindCategory.VERTICAL_WHITE_SPACE && kind.getCategory() != ParagraphKindCategory.SKIP);
			openParagraph = kind;
		}

		@Override
		public void visitTableCellStart(String tag) {
			if (!expectContent) {
				violation("Paragraph expected before table cell start");
			}
			foundContent = true;
			if (openParagraph != ParagraphKind.TABLE_ROW) {
				violation("Table cell found in paragraph type" + openParagraph);
			}
		}

		@Override
		public void visitSidebarStart(String[] categories) throws RuntimeException {
			checkContent(false);
			openParagraph = null;
		}

		@Override
		public void visitSidebarEnd() throws RuntimeException {
			checkContent(false);
			openParagraph = null;
		}

		public void visitPeripheralStart(String title, String id) throws RuntimeException {
			checkContent(false);
			openChapter = null;
			openVerse = null;
			openParagraph = null;
			currentReference = Reference.book(currentReference.getBook(), "");
		};

		@Override
		public void visitVerseStart(VerseIdentifier location, String verseNumber) throws RuntimeException {
			if (!expectContent) {
				violation("Paragraph expected before start of verse " + location);
			}
			if (openVerse != null) {
				violation("Verse already open when starting verse " + location);
			}
			if (openChapter == null) {
				violation("No chapter open when starting verse " + location);
			}
			if (openParagraph == null) {
				violation("No paragraph open when starting verse " + location);
			} else if (openParagraph != ParagraphKind.SPEAKER_TITLE && (openParagraph.getCategory() != ParagraphKindCategory.TEXT || openParagraph.name().startsWith("INTRO_"))) {
				violation("Paragraph of type " + openParagraph + " not suitable to start verse " + location);
			}
			openVerse = location;
			currentReference = Reference.verse(currentReference.getBook(), currentReference.getFirstChapter(), verseNumber, "");
		}

		@Override
		public void visitVerseEnd(VerseIdentifier verseLocation) throws RuntimeException {
			if (!expectContent) {
				violation("Paragraph expected before end of verse " + verseLocation);
			}
			if (openVerse != verseLocation) {
				violation("Other verse open: " + openVerse + ", when closing verse " + verseLocation);
			}
			if (openChapter == null) {
				violation("No chapter open when closing verse " + verseLocation);
			}
			if (openParagraph == null) {
				violation("No paragraph open when starting verse " + verseLocation);
			} else if (openParagraph.getCategory() != ParagraphKindCategory.TEXT || openParagraph.name().startsWith("INTRO_")) {
				violation("Paragraph of type " + openParagraph + " not suitable to close verse " + verseLocation);
			}
			openVerse = null;
			lastClosedVerse = verseLocation;
			currentReference = Reference.chapter(currentReference.getBook(), currentReference.getFirstChapter(), "");
		}

		@Override
		public void visitFigure(String caption, Map<String, String> attributes) throws RuntimeException {
			if (!expectContent) {
				violation("Paragraph expected before figure");
			}
			foundContent = true;
		}

		@Override
		public void visitParatextCharacterContent(ParatextCharacterContent content) {
			if (!expectContent) {
				boolean isRealContent = false;
				for(ParatextCharacterContentPart c : content.getContent()) {
					if (openChapter != null && openVerse == null && c instanceof AutoClosingFormatting &&  ((AutoClosingFormatting) c).getKind() == AutoClosingFormattingKind.ALTERNATE_CHAPTER) {
						// \\ca tag before verse start is allowed
						continue;
					}
					if (c instanceof Milestone) {
						// milestones are allowed everywhere
						continue;
					}
					isRealContent = true;
					break;
				}
				if (isRealContent) {
					violation("Paragraph expected before character content");
				}
			}
			foundContent = true;
			content.accept(new ValidateCharacterVisitor(this, openMilestones));
		}
	}

	private static class ValidateCharacterVisitor implements ParatextCharacterContentVisitor<RuntimeException> {
		private final ValidateBookVisitor vbv;
		private final List<String> openMilestones;

		public ValidateCharacterVisitor(ValidateBookVisitor vbv, List<String> openMilestones) {
			this.vbv = vbv;
			this.openMilestones = openMilestones;
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) throws RuntimeException {
			return this;
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws RuntimeException {
			return this;
		}

		@Override
		public void visitMilestone(String tag, Map<String, String> attributes) throws RuntimeException {
			if (tag.endsWith("-s")) {
				openMilestones.add(tag.substring(0, tag.length() - 2));
			} else if (tag.endsWith("-e")) {
				String subtag = tag.substring(0, tag.length() - 2);
				int pos = openMilestones.lastIndexOf(subtag);
				if (pos == -1) {
					vbv.violation("Milestone " + subtag + " closed but not open");
				} else {
					openMilestones.remove(pos);
				}
			}
		}

		@Override
		public void visitReference(Reference reference) throws RuntimeException {
		}

		@Override
		public void visitCustomMarkup(String tag, boolean ending) throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
		}

		@Override
		public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws RuntimeException {
		}

		@Override
		public void visitEnd() throws RuntimeException {
		}
	}
}
