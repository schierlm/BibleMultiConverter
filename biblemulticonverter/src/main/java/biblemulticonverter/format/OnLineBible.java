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
import biblemulticonverter.data.StandardVersification;
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
			new BookMeta("Ge", BookID.BOOK_Gen),
			new BookMeta("Ex", BookID.BOOK_Exod),
			new BookMeta("Le", BookID.BOOK_Lev),
			new BookMeta("Nu", BookID.BOOK_Num),
			new BookMeta("De", BookID.BOOK_Deut),
			new BookMeta("Jos", BookID.BOOK_Josh),
			new BookMeta("Jud", BookID.BOOK_Judg),
			new BookMeta("Ru", BookID.BOOK_Ruth),
			new BookMeta("1Sa", BookID.BOOK_1Sam),
			new BookMeta("2Sa", BookID.BOOK_2Sam),
			new BookMeta("1Ki", BookID.BOOK_1Kgs),
			new BookMeta("2Ki", BookID.BOOK_2Kgs),
			new BookMeta("1Ch", BookID.BOOK_1Chr),
			new BookMeta("2Ch", BookID.BOOK_2Chr),
			new BookMeta("Ezr", BookID.BOOK_Ezra),
			new BookMeta("Ne", BookID.BOOK_Neh),
			new BookMeta("Es", BookID.BOOK_Esth),
			new BookMeta("Job", BookID.BOOK_Job),
			new BookMeta("Ps", BookID.BOOK_Ps),
			new BookMeta("Pr", BookID.BOOK_Prov),
			new BookMeta("Ec", BookID.BOOK_Eccl),
			new BookMeta("So", BookID.BOOK_Song),
			new BookMeta("Isa", BookID.BOOK_Isa),
			new BookMeta("Jer", BookID.BOOK_Jer),
			new BookMeta("La", BookID.BOOK_Lam),
			new BookMeta("Eze", BookID.BOOK_Ezek),
			new BookMeta("Da", BookID.BOOK_Dan),
			new BookMeta("Ho", BookID.BOOK_Hos),
			new BookMeta("Joe", BookID.BOOK_Joel),
			new BookMeta("Am", BookID.BOOK_Amos),
			new BookMeta("Ob", BookID.BOOK_Obad),
			new BookMeta("Jon", BookID.BOOK_Jonah),
			new BookMeta("Mic", BookID.BOOK_Mic),
			new BookMeta("Na", BookID.BOOK_Nah),
			new BookMeta("Hab", BookID.BOOK_Hab),
			new BookMeta("Zep", BookID.BOOK_Zeph),
			new BookMeta("Hag", BookID.BOOK_Hag),
			new BookMeta("Zec", BookID.BOOK_Zech),
			new BookMeta("Mal", BookID.BOOK_Mal),
			new BookMeta("Mt", BookID.BOOK_Matt),
			new BookMeta("Mr", BookID.BOOK_Mark),
			new BookMeta("Lu", BookID.BOOK_Luke),
			new BookMeta("Joh", BookID.BOOK_John),
			new BookMeta("Ac", BookID.BOOK_Acts),
			new BookMeta("Ro", BookID.BOOK_Rom),
			new BookMeta("1Co", BookID.BOOK_1Cor),
			new BookMeta("2Co", BookID.BOOK_2Cor),
			new BookMeta("Ga", BookID.BOOK_Gal),
			new BookMeta("Eph", BookID.BOOK_Eph),
			new BookMeta("Php", BookID.BOOK_Phil),
			new BookMeta("Col", BookID.BOOK_Col),
			new BookMeta("1Th", BookID.BOOK_1Thess),
			new BookMeta("2Th", BookID.BOOK_2Thess),
			new BookMeta("1Ti", BookID.BOOK_1Tim),
			new BookMeta("2Ti", BookID.BOOK_2Tim),
			new BookMeta("Tit", BookID.BOOK_Titus),
			new BookMeta("Phm", BookID.BOOK_Phlm),
			new BookMeta("Heb", BookID.BOOK_Heb),
			new BookMeta("Jas", BookID.BOOK_Jas),
			new BookMeta("1Pe", BookID.BOOK_1Pet),
			new BookMeta("2Pe", BookID.BOOK_2Pet),
			new BookMeta("1Jo", BookID.BOOK_1John),
			new BookMeta("2Jo", BookID.BOOK_2John),
			new BookMeta("3Jo", BookID.BOOK_3John),
			new BookMeta("Jude", BookID.BOOK_Jude),
			new BookMeta("Re", BookID.BOOK_Rev),
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
				int[] verseCount = StandardVersification.KJV.getVerseCount(bm.id);
				for (int i = 0; i < verseCount.length; i++) {
					Chapter ch = bk != null && i < bk.getChapters().size() ? bk.getChapters().get(i) : null;
					int maxVerse = verseCount[i];
					BitSet allowedNumbers = new BitSet(maxVerse + 1);
					allowedNumbers.set(1, maxVerse + 1);
					List<VirtualVerse> vvs = ch == null ? null : ch.createVirtualVerses(null);
					for (int vnum = 1; vnum <= verseCount[i]; vnum++) {

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

		public BookMeta(String abbr, BookID id) {
			this.abbr = abbr;
			this.id = id;
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
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			StringBuilder suffix = new StringBuilder();
			if (strongs != null && includeStrongs) {
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
