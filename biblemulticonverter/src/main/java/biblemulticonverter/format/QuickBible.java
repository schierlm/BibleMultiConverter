package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;

public class QuickBible implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export format for QuickBible (Bible for Android)",
	};

	private static final BookID[] EXTRA_BOOKS = {
			BookID.BOOK_1Esd, BookID.BOOK_2Esd, BookID.BOOK_Tob, BookID.BOOK_Jdt,
			BookID.BOOK_1Macc, BookID.BOOK_2Macc, BookID.BOOK_3Macc, BookID.BOOK_4Macc,
			null, // Hebrew Psalms
			BookID.BOOK_Odes, BookID.BOOK_Wis, BookID.BOOK_Sir, BookID.BOOK_PssSol,
			BookID.BOOK_EpJer, BookID.BOOK_Bar, BookID.BOOK_Sus, BookID.BOOK_PrAzar,
			BookID.BOOK_Bel, BookID.BOOK_PrMan, BookID.BOOK_AddEsth, BookID.BOOK_AddPs,
			BookID.BOOK_EpLao, BookID.BOOK_AddDan
	};

	private static final Map<BookID, Integer> BOOK_MAP = new EnumMap<>(BookID.class);

	static {
		for (int i = 1; i <= 66; i++) {
			BOOK_MAP.put(BookID.fromZefId(i), i);
		}
		for (int i = 0; i < EXTRA_BOOKS.length; i++) {
			if (EXTRA_BOOKS[i] == null)
				continue;
			BOOK_MAP.put(EXTRA_BOOKS[i], i + 67);
		}
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		StringBuilder verseSection = new StringBuilder(), pericopeSection = new StringBuilder();
		StringBuilder footnoteSection = new StringBuilder(), xrefSection = new StringBuilder();
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[0]), StandardCharsets.UTF_8))) {
			bw.write("info\tlongName\t" + bible.getName() + "\n");
			for (Book book : bible.getBooks()) {
				Integer bNumber = BOOK_MAP.get(book.getId());
				if (bNumber == null) {
					System.out.println("WARNING: Skipping book " + book.getAbbr());
					continue;
				}
				bw.write("book_name\t" + bNumber + "\t" + book.getShortName() + "\t" + book.getAbbr() + "\n");
				int cNumber = 0;
				for (Chapter chapter : book.getChapters()) {
					cNumber++;
					if (chapter.getVerses().isEmpty()) {
						verseSection.append("verse\t" + bNumber + "\t" + cNumber + "\t1\t\n");
					}
					int vNumber = 0;
					for (VirtualVerse vv : chapter.createVirtualVerses()) {
						vNumber++;
						while (vNumber < vv.getNumber()) {
							verseSection.append("verse\t" + bNumber + "\t" + cNumber + "\t" + vNumber + "\t\n");
							vNumber++;
						}
						if (vNumber != vv.getNumber())
							throw new RuntimeException("Expected verse " + vNumber + ", but got " + vv.getNumber());

						for (Headline h : vv.getHeadlines()) {
							pericopeSection.append("pericope\t" + bNumber + "\t" + cNumber + "\t" + vNumber + "\t");
							if (!h.getElementTypes(1).equals("t")) {
								pericopeSection.append("@@");
							}
							h.accept(new QuickBibleVisitor(pericopeSection, true, false, "\n", null, null, null));
						}
						verseSection.append("verse\t" + bNumber + "\t" + cNumber + "\t" + vNumber + "\t");
						boolean hasFormatting = false;
						for (Verse v : vv.getVerses()) {
							if (!v.getNumber().equals("" + vv.getNumber()) || !v.getElementTypes(1).equals("t")) {
								hasFormatting = true;
								break;
							}
						}
						if (hasFormatting)
							verseSection.append("@@");
						StringBuilder verseBuilder = new StringBuilder();
						List<StringBuilder> footnotes = new ArrayList<>();
						List<List<StringBuilder>> footnoteXrefs = new ArrayList<>();
						for (Verse v : vv.getVerses()) {
							if (!v.getNumber().equals("" + vv.getNumber())) {
								verseBuilder.append(" @9(" + v.getNumber() + ")@7 ");
							}
							v.accept(new QuickBibleVisitor(verseBuilder, true, true, "", footnotes, footnoteXrefs, null));
						}
						int xrefCounter = 0;
						for (int i = 0; i < footnotes.size(); i++) {
							int fn = i + 1;
							int tagPos = verseBuilder.indexOf("@<f" + fn + "@>@/");
							List<StringBuilder> xrefs = footnoteXrefs.get(i);
							String fnt = footnotes.get(i).toString();
							for (int j = 0; j < xrefs.size(); j++) {
								xrefCounter++;
								String xrefTag = "@<x" + xrefCounter + "@>@/";
								verseBuilder.insert(tagPos, xrefTag);
								tagPos += xrefTag.length();
								String[] parts = xrefs.get(j).toString().split("@!");
								xrefSection.append("xref\t" + bNumber + "\t" + cNumber + "\t" + vNumber + "\t" + xrefCounter + "\t" + parts[0] + parts[1] + "@/\n");
								fnt = fnt.replace("@!" + j + "@!", parts[1]);
							}
							footnoteSection.append("footnote\t" + bNumber + "\t" + cNumber + "\t" + vNumber + "\t" + fn + "\t" + fnt + "\n");
						}
						verseSection.append(verseBuilder.toString());
						verseSection.append("\n");
					}
				}
			}
			bw.write(verseSection.toString());
			bw.write(pericopeSection.toString());
			bw.write(footnoteSection.toString());
			bw.write(xrefSection.toString());
		}
	}

	private static class QuickBibleVisitor implements Visitor<RuntimeException> {

		private final StringBuilder mainBuilder;
		private final boolean allowItalic;
		private final boolean allowWOJ;
		private final List<StringBuilder> footnotes;
		private final List<List<StringBuilder>> footnoteXrefs;
		private final List<String> suffixStack = new ArrayList<String>();
		private List<StringBuilder> insideXrefs;

		public QuickBibleVisitor(StringBuilder mainBuilder, boolean allowItalic, boolean allowWOJ, String suffix, List<StringBuilder> footnotes, List<List<StringBuilder>> footnoteXrefs, List<StringBuilder> insideXrefs) {
			this.mainBuilder = mainBuilder;
			this.allowItalic = allowItalic;
			this.allowWOJ = allowWOJ;
			this.footnotes = footnotes;
			this.footnoteXrefs = footnoteXrefs;
			this.insideXrefs = insideXrefs;
			pushSuffix(suffix);
		}

		private void pushSuffix(String suffix) {
			suffixStack.add(suffix);
		}

		@Override
		public int visitElementTypes(String elementTypes) {
			return 0;
		}

		@Override
		public void visitStart() {
		}

		@Override
		public void visitText(String text) {
			mainBuilder.append(text.replace('@', '\uFE6B'));
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) {
			switch (kind) {
			case WORDS_OF_JESUS:
				if (allowWOJ) {
					mainBuilder.append("@6");
					return new QuickBibleVisitor(mainBuilder, allowItalic, false, "@5", footnotes, footnoteXrefs, insideXrefs);
				}
				break;

			case BOLD:
			case ITALIC:
			case UNDERLINE:
			case LINK:
			case FOOTNOTE_LINK:
				if (allowItalic) {
					mainBuilder.append("@9");
					return new QuickBibleVisitor(mainBuilder, false, allowWOJ, "@7", footnotes, footnoteXrefs, insideXrefs);
				}
				break;

			case DIVINE_NAME:
			case STRIKE_THROUGH:
			case SUBSCRIPT:
			case SUPERSCRIPT:
				break;
			}
			pushSuffix("");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) {
			throw new RuntimeException("Headlines do not exist in VirtualVerses!");
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() {
			if (footnotes == null)
				return null;
			StringBuilder fnb = new StringBuilder("@@");
			List<StringBuilder> xr = new ArrayList<>();
			footnotes.add(fnb);
			footnoteXrefs.add(xr);
			mainBuilder.append("@<f" + footnotes.size() + "@>@/");
			return new QuickBibleVisitor(fnb, true, false, "", null, null, xr);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) {
			if (insideXrefs == null) {
				pushSuffix("");
				return this;
			}
			StringBuilder xrb = new StringBuilder("@<to:" + book.getOsisID() + "." + firstChapter + "." + firstVerse);
			if (lastChapter != firstChapter || !lastVerse.equals(firstVerse)) {
				xrb.append("-" + book.getOsisID() + "." + lastChapter + "." + lastVerse);
			}
			xrb.append("@>@!");
			mainBuilder.append("@!" + insideXrefs.size() + "@!");
			insideXrefs.add(xrb);
			return new QuickBibleVisitor(xrb, false, false, "", null, null, null);
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) {
			switch (kind) {
			case NEWLINE:
				mainBuilder.append("@0");
				return;
			case NEWLINE_WITH_INDENT:
				mainBuilder.append("@1");
				return;
			case PARAGRAPH:
				mainBuilder.append("@^");
				return;
			}
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) {
			pushSuffix("");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) {
			pushSuffix("");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) {
			pushSuffix("");
			return this;
		}

		@Override
		public void visitVerseSeparator() {
			if (allowItalic)
				mainBuilder.append("@9/@7");
			else
				mainBuilder.append("/");
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) {
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) {
			throw new RuntimeException("Variations are not supported");
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) {
			Visitor<RuntimeException> next = prio.handleVisitor(category, this);
			if (next != null)
				pushSuffix("");
			return next;
		}

		@Override
		public boolean visitEnd() {
			mainBuilder.append(suffixStack.remove(suffixStack.size() - 1));
			return false;
		}
	}
}
