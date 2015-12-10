package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

public class OnLineBible implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export format for importing into OnLine Bible",
			"",
			"Usage: OnLineBible <outfile> [<namesfile>] [IncludeStrongs]",
			"",
			"Put <namesfile> as NewBkNms.Lst into the note control directory of the Bible notes set."
	};

	private static final BookMeta[] BOOK_META = new BookMeta[] {
			new BookMeta("Ge", BookID.BOOK_Gen, 31, 25, 24, 26, 32, 22, 24, 22, 29, 32, 32, 20, 18, 24, 21, 16, 27, 33, 38, 18, 34, 24, 20, 67, 34, 35, 46, 22, 35, 43, 55, 32, 20, 31, 29, 43, 36, 30, 23, 23, 57, 38, 34, 34, 28, 34, 31, 22, 33, 26),
			new BookMeta("Ex", BookID.BOOK_Exod, 22, 25, 22, 31, 23, 30, 25, 32, 35, 29, 10, 51, 22, 31, 27, 36, 16, 27, 25, 26, 36, 31, 33, 18, 40, 37, 21, 43, 46, 38, 18, 35, 23, 35, 35, 38, 29, 31, 43, 38),
			new BookMeta("Le", BookID.BOOK_Lev, 17, 16, 17, 35, 19, 30, 38, 36, 24, 20, 47, 8, 59, 57, 33, 34, 16, 30, 37, 27, 24, 33, 44, 23, 55, 46, 34),
			new BookMeta("Nu", BookID.BOOK_Num, 54, 34, 51, 49, 31, 27, 89, 26, 23, 36, 35, 16, 33, 45, 41, 50, 13, 32, 22, 29, 35, 41, 30, 25, 18, 65, 23, 31, 40, 16, 54, 42, 56, 29, 34, 13),
			new BookMeta("De", BookID.BOOK_Deut, 46, 37, 29, 49, 33, 25, 26, 20, 29, 22, 32, 32, 18, 29, 23, 22, 20, 22, 21, 20, 23, 30, 25, 22, 19, 19, 26, 68, 29, 20, 30, 52, 29, 12),
			new BookMeta("Jos", BookID.BOOK_Josh, 18, 24, 17, 24, 15, 27, 26, 35, 27, 43, 23, 24, 33, 15, 63, 10, 18, 28, 51, 9, 45, 34, 16, 33),
			new BookMeta("Jud", BookID.BOOK_Judg, 36, 23, 31, 24, 31, 40, 25, 35, 57, 18, 40, 15, 25, 20, 20, 31, 13, 31, 30, 48, 25),
			new BookMeta("Ru", BookID.BOOK_Ruth, 22, 23, 18, 22),
			new BookMeta("1Sa", BookID.BOOK_1Sam, 28, 36, 21, 22, 12, 21, 17, 22, 27, 27, 15, 25, 23, 52, 35, 23, 58, 30, 24, 42, 15, 23, 29, 22, 44, 25, 12, 25, 11, 31, 13),
			new BookMeta("2Sa", BookID.BOOK_2Sam, 27, 32, 39, 12, 25, 23, 29, 18, 13, 19, 27, 31, 39, 33, 37, 23, 29, 33, 43, 26, 22, 51, 39, 25),
			new BookMeta("1Ki", BookID.BOOK_1Kgs, 53, 46, 28, 34, 18, 38, 51, 66, 28, 29, 43, 33, 34, 31, 34, 34, 24, 46, 21, 43, 29, 53),
			new BookMeta("2Ki", BookID.BOOK_2Kgs, 18, 25, 27, 44, 27, 33, 20, 29, 37, 36, 21, 21, 25, 29, 38, 20, 41, 37, 37, 21, 26, 20, 37, 20, 30),
			new BookMeta("1Ch", BookID.BOOK_1Chr, 54, 55, 24, 43, 26, 81, 40, 40, 44, 14, 47, 40, 14, 17, 29, 43, 27, 17, 19, 8, 30, 19, 32, 31, 31, 32, 34, 21, 30),
			new BookMeta("2Ch", BookID.BOOK_2Chr, 17, 18, 17, 22, 14, 42, 22, 18, 31, 19, 23, 16, 22, 15, 19, 14, 19, 34, 11, 37, 20, 12, 21, 27, 28, 23, 9, 27, 36, 27, 21, 33, 25, 33, 27, 23),
			new BookMeta("Ezr", BookID.BOOK_Ezra, 11, 70, 13, 24, 17, 22, 28, 36, 15, 44),
			new BookMeta("Ne", BookID.BOOK_Neh, 11, 20, 32, 23, 19, 19, 73, 18, 38, 39, 36, 47, 31),
			new BookMeta("Es", BookID.BOOK_Esth, 22, 23, 15, 17, 14, 14, 10, 17, 32, 3),
			new BookMeta("Job", BookID.BOOK_Job, 22, 13, 26, 21, 27, 30, 21, 22, 35, 22, 20, 25, 28, 22, 35, 22, 16, 21, 29, 29, 34, 30, 17, 25, 6, 14, 23, 28, 25, 31, 40, 22, 33, 37, 16, 33, 24, 41, 30, 24, 34, 17),
			new BookMeta("Ps", BookID.BOOK_Ps, 6, 12, 8, 8, 12, 10, 17, 9, 20, 18, 7, 8, 6, 7, 5, 11, 15, 50, 14, 9, 13, 31, 6, 10, 22, 12, 14, 9, 11, 12, 24, 11, 22, 22, 28, 12, 40, 22, 13, 17, 13, 11, 5, 26, 17, 11, 9, 14, 20, 23, 19, 9, 6, 7, 23, 13, 11, 11, 17, 12, 8, 12, 11, 10, 13, 20, 7, 35, 36, 5, 24, 20, 28, 23, 10, 12, 20, 72, 13, 19, 16, 8, 18, 12, 13, 17, 7, 18, 52, 17, 16, 15, 5, 23, 11, 13, 12, 9, 9, 5, 8, 28, 22, 35, 45, 48, 43, 13, 31, 7, 10, 10, 9, 8, 18, 19, 2, 29, 176, 7, 8, 9, 4, 8, 5, 6, 5, 6, 8, 8, 3, 18, 3, 3, 21, 26, 9, 8, 24, 13, 10, 7, 12, 15, 21, 10, 20, 14, 9, 6),
			new BookMeta("Pr", BookID.BOOK_Prov, 33, 22, 35, 27, 23, 35, 27, 36, 18, 32, 31, 28, 25, 35, 33, 33, 28, 24, 29, 30, 31, 29, 35, 34, 28, 28, 27, 28, 27, 33, 31),
			new BookMeta("Ec", BookID.BOOK_Eccl, 18, 26, 22, 16, 20, 12, 29, 17, 18, 20, 10, 14),
			new BookMeta("So", BookID.BOOK_Song, 17, 17, 11, 16, 16, 13, 13, 14),
			new BookMeta("Isa", BookID.BOOK_Isa, 31, 22, 26, 6, 30, 13, 25, 22, 21, 34, 16, 6, 22, 32, 9, 14, 14, 7, 25, 6, 17, 25, 18, 23, 12, 21, 13, 29, 24, 33, 9, 20, 24, 17, 10, 22, 38, 22, 8, 31, 29, 25, 28, 28, 25, 13, 15, 22, 26, 11, 23, 15, 12, 17, 13, 12, 21, 14, 21, 22, 11, 12, 19, 12, 25, 24),
			new BookMeta("Jer", BookID.BOOK_Jer, 19, 37, 25, 31, 31, 30, 34, 22, 26, 25, 23, 17, 27, 22, 21, 21, 27, 23, 15, 18, 14, 30, 40, 10, 38, 24, 22, 17, 32, 24, 40, 44, 26, 22, 19, 32, 21, 28, 18, 16, 18, 22, 13, 30, 5, 28, 7, 47, 39, 46, 64, 34),
			new BookMeta("La", BookID.BOOK_Lam, 22, 22, 66, 22, 22),
			new BookMeta("Eze", BookID.BOOK_Ezek, 28, 10, 27, 17, 17, 14, 27, 18, 11, 22, 25, 28, 23, 23, 8, 63, 24, 32, 14, 49, 32, 31, 49, 27, 17, 21, 36, 26, 21, 26, 18, 32, 33, 31, 15, 38, 28, 23, 29, 49, 26, 20, 27, 31, 25, 24, 23, 35),
			new BookMeta("Da", BookID.BOOK_Dan, 21, 49, 30, 37, 31, 28, 28, 27, 27, 21, 45, 13),
			new BookMeta("Ho", BookID.BOOK_Hos, 11, 23, 5, 19, 15, 11, 16, 14, 17, 15, 12, 14, 16, 9),
			new BookMeta("Joe", BookID.BOOK_Joel, 20, 32, 21),
			new BookMeta("Am", BookID.BOOK_Amos, 15, 16, 15, 13, 27, 14, 17, 14, 15),
			new BookMeta("Ob", BookID.BOOK_Obad, 21),
			new BookMeta("Jon", BookID.BOOK_Jonah, 17, 10, 10, 11),
			new BookMeta("Mic", BookID.BOOK_Mic, 16, 13, 12, 13, 15, 16, 20),
			new BookMeta("Na", BookID.BOOK_Nah, 15, 13, 19),
			new BookMeta("Hab", BookID.BOOK_Hab, 17, 20, 19),
			new BookMeta("Zep", BookID.BOOK_Zeph, 18, 15, 20),
			new BookMeta("Hag", BookID.BOOK_Hag, 15, 23),
			new BookMeta("Zec", BookID.BOOK_Zech, 21, 13, 10, 14, 11, 15, 14, 23, 17, 12, 17, 14, 9, 21),
			new BookMeta("Mal", BookID.BOOK_Mal, 14, 17, 18, 6),
			new BookMeta("Mt", BookID.BOOK_Matt, 25, 23, 17, 25, 48, 34, 29, 34, 38, 42, 30, 50, 58, 36, 39, 28, 27, 35, 30, 34, 46, 46, 39, 51, 46, 75, 66, 20),
			new BookMeta("Mr", BookID.BOOK_Mark, 45, 28, 35, 41, 43, 56, 37, 38, 50, 52, 33, 44, 37, 72, 47, 20),
			new BookMeta("Lu", BookID.BOOK_Luke, 80, 52, 38, 44, 39, 49, 50, 56, 62, 42, 54, 59, 35, 35, 32, 31, 37, 43, 48, 47, 38, 71, 56, 53),
			new BookMeta("Joh", BookID.BOOK_John, 51, 25, 36, 54, 47, 71, 53, 59, 41, 42, 57, 50, 38, 31, 27, 33, 26, 40, 42, 31, 25),
			new BookMeta("Ac", BookID.BOOK_Acts, 26, 47, 26, 37, 42, 15, 60, 40, 43, 48, 30, 25, 52, 28, 41, 40, 34, 28, 41, 38, 40, 30, 35, 27, 27, 32, 44, 31),
			new BookMeta("Ro", BookID.BOOK_Rom, 32, 29, 31, 25, 21, 23, 25, 39, 33, 21, 36, 21, 14, 23, 33, 27),
			new BookMeta("1Co", BookID.BOOK_1Cor, 31, 16, 23, 21, 13, 20, 40, 13, 27, 33, 34, 31, 13, 40, 58, 24),
			new BookMeta("2Co", BookID.BOOK_2Cor, 24, 17, 18, 18, 21, 18, 16, 24, 15, 18, 33, 21, 14),
			new BookMeta("Ga", BookID.BOOK_Gal, 24, 21, 29, 31, 26, 18),
			new BookMeta("Eph", BookID.BOOK_Eph, 23, 22, 21, 32, 33, 24),
			new BookMeta("Php", BookID.BOOK_Phil, 30, 30, 21, 23),
			new BookMeta("Col", BookID.BOOK_Col, 29, 23, 25, 18),
			new BookMeta("1Th", BookID.BOOK_1Thess, 10, 20, 13, 18, 28),
			new BookMeta("2Th", BookID.BOOK_2Thess, 12, 17, 18),
			new BookMeta("1Ti", BookID.BOOK_1Tim, 20, 15, 16, 16, 25, 21),
			new BookMeta("2Ti", BookID.BOOK_2Tim, 18, 26, 17, 22),
			new BookMeta("Tit", BookID.BOOK_Titus, 16, 15, 15),
			new BookMeta("Phm", BookID.BOOK_Phlm, 25),
			new BookMeta("Heb", BookID.BOOK_Heb, 14, 18, 19, 16, 14, 20, 28, 13, 28, 39, 40, 29, 25),
			new BookMeta("Jas", BookID.BOOK_Jas, 27, 26, 18, 17, 20),
			new BookMeta("1Pe", BookID.BOOK_1Pet, 25, 25, 22, 19, 14),
			new BookMeta("2Pe", BookID.BOOK_2Pet, 21, 22, 18),
			new BookMeta("1Jo", BookID.BOOK_1John, 10, 29, 24, 21, 21),
			new BookMeta("2Jo", BookID.BOOK_2John, 13),
			new BookMeta("3Jo", BookID.BOOK_3John, 14),
			new BookMeta("Jude", BookID.BOOK_Jude, 25),
			new BookMeta("Re", BookID.BOOK_Rev, 20, 29, 22, 11, 14, 17, 17, 13, 21, 11, 19, 17, 18, 20, 8, 21, 18, 24, 21, 15, 27, 21),
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String outFile = exportArgs[0], namesFile = null;
		;
		boolean includeStrongs = false;
		if (exportArgs.length > 1) {
			if (exportArgs[1].equals("IncludeStrongs")) {
				includeStrongs = true;
			} else {
				namesFile = exportArgs[1];
			}
			if (exportArgs.length > 2 && exportArgs[2].equals("IncludeStrongs")) {
				includeStrongs = true;
			}
		}

		Set<BookID> supportedBooks = EnumSet.noneOf(BookID.class);
		for (BookMeta bm : BOOK_META) {
			supportedBooks.add(bm.id);
		}
		Map<BookID, Book> bookMap = new EnumMap<>(BookID.class);
		for (Book book : bible.getBooks()) {
			if (supportedBooks.contains(book.getId()))
				bookMap.put(book.getId(), book);
			else
				System.out.println("WARNING: Skipping book " + book.getAbbr());
		}

		if (namesFile != null) {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(namesFile))) {
				for (BookMeta bm : BOOK_META) {
					Book bk = bookMap.get(bm.id);
					if (bk != null) {
						bw.write(bk.getShortName() + " " + bm.abbr);
						bw.newLine();
					}
				}
			}
		}

		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
			for (BookMeta bm : BOOK_META) {
				String prefix = "";
				if (bm.id == BookID.BOOK_Matt && includeStrongs) {
					prefix = "0 ";
				}
				Book bk = bookMap.remove(bm.id);
				for (int i = 0; i < bm.versification.length; i++) {
					Chapter ch = bk != null && i < bk.getChapters().size() ? bk.getChapters().get(i) : null;
					int maxVerse = bm.versification[i];
					BitSet allowedNumbers = new BitSet(maxVerse + 1);
					allowedNumbers.set(1, maxVerse + 1);
					List<VirtualVerse> vvs = ch == null ? null : ch.createVirtualVerses(null);
					for (int vnum = 1; vnum <= bm.versification[i]; vnum++) {

						bw.write("$$$ " + bm.abbr + " " + (i + 1) + ":" + vnum + " ");
						bw.newLine();
						StringBuilder text = new StringBuilder(prefix);
						if (vvs != null) {
							for (VirtualVerse vv : vvs) {
								if (vv.getNumber() == vnum) {
									for (Headline h : vv.getHeadlines()) {
										text.append(" {\\$");
										h.accept(new OnLineBibleVisitor(text, includeStrongs));
										text.append("\\$} ");
									}
									for (Verse v : vv.getVerses()) {
										if (!v.getNumber().equals("" + vv.getNumber())) {
											text.append("\\\\(" + v.getNumber() + ")\\\\ ");
										}
										v.accept(new OnLineBibleVisitor(text, includeStrongs));
									}
								}
							}
						}
						if (text.length() > 0) {
							bw.write(text.toString().replaceAll("  +", " "));
							bw.newLine();
						}
						prefix = "";
					}
				}
			}
		}
		if (!bookMap.isEmpty())
			throw new IllegalStateException("Remaining books: " + bookMap.keySet());
	}

	private static class BookMeta {

		private final String abbr;
		private final BookID id;
		private final int[] versification;

		public BookMeta(String abbr, BookID id, int... versification) {
			this.abbr = abbr;
			this.id = id;
			this.versification = versification;
		}
	}

	private static class OnLineBibleVisitor implements Visitor<RuntimeException> {

		private final StringBuilder content;
		private final boolean includeStrongs;
		private final String suffix;

		public OnLineBibleVisitor(StringBuilder content, boolean includeStrongs) {
			this(content, includeStrongs, "");
		}

		private OnLineBibleVisitor(StringBuilder content, boolean includeStrongs, String suffix) {
			this.content = content;
			this.includeStrongs = includeStrongs;
			this.suffix = suffix;
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			throw new RuntimeException("Headlines should have been exported before");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			content.append("\\&");
		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			if (includeStrongs)
				text = text.replaceAll("([0-9]+)", "#$1");
			content.append(text);
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
			content.append(" {");
			return new OnLineBibleVisitor(content, includeStrongs, "} ");
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			return new OnLineBibleVisitor(content, includeStrongs);
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			String tag = "";
			switch (kind) {
			case BOLD:
				tag = "\\\\";
				break;
			case ITALIC:
				tag = "\\@";
				break;
			case UNDERLINE:
				tag = "\\%";
				break;
			default:
				break;
			}
			content.append(tag);
			return new OnLineBibleVisitor(content, includeStrongs, tag);
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			return new OnLineBibleVisitor(content, includeStrongs);
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			visitText("/");
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			StringBuilder suffix = new StringBuilder();
			if (includeStrongs) {
				suffix.append(" ");
				for (int strong : strongs) {
					suffix.append(strong).append(" ");
				}
			}
			return new OnLineBibleVisitor(content, includeStrongs, suffix.toString());
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			return new OnLineBibleVisitor(content, includeStrongs);
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			throw new RuntimeException("Variations not supported");
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			return prio.handleVisitor(category, new OnLineBibleVisitor(content, includeStrongs));
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			content.append(suffix);
			return false;
		}
	}
}
