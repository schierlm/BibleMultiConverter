package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
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
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;

public class Accordance implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export format for Accordance",
	};

	private static Map<BookID, String> BOOK_NAME_MAP = new EnumMap<>(BookID.class);

	static {
		BOOK_NAME_MAP.put(BookID.BOOK_Gen, "Gen.");
		BOOK_NAME_MAP.put(BookID.BOOK_Exod, "Ex.");
		BOOK_NAME_MAP.put(BookID.BOOK_Lev, "Lev.");
		BOOK_NAME_MAP.put(BookID.BOOK_Num, "Num.");
		BOOK_NAME_MAP.put(BookID.BOOK_Deut, "Deut.");
		BOOK_NAME_MAP.put(BookID.BOOK_Josh, "Josh.");
		BOOK_NAME_MAP.put(BookID.BOOK_Judg, "Judg.");
		BOOK_NAME_MAP.put(BookID.BOOK_Ruth, "Ruth");
		BOOK_NAME_MAP.put(BookID.BOOK_1Sam, "1Sam.");
		BOOK_NAME_MAP.put(BookID.BOOK_2Sam, "2Sam.");
		BOOK_NAME_MAP.put(BookID.BOOK_1Kgs, "1Kings");
		BOOK_NAME_MAP.put(BookID.BOOK_2Kgs, "2Kings");
		BOOK_NAME_MAP.put(BookID.BOOK_1Chr, "1Chr.");
		BOOK_NAME_MAP.put(BookID.BOOK_2Chr, "2Chr.");
		BOOK_NAME_MAP.put(BookID.BOOK_Ezra, "Ezra");
		BOOK_NAME_MAP.put(BookID.BOOK_Neh, "Neh.");
		BOOK_NAME_MAP.put(BookID.BOOK_Esth, "Esth.");
		BOOK_NAME_MAP.put(BookID.BOOK_Job, "Job");
		BOOK_NAME_MAP.put(BookID.BOOK_Ps, "Psa.");
		BOOK_NAME_MAP.put(BookID.BOOK_Prov, "Prov.");
		BOOK_NAME_MAP.put(BookID.BOOK_Eccl, "Eccl.");
		BOOK_NAME_MAP.put(BookID.BOOK_Song, "Song");
		BOOK_NAME_MAP.put(BookID.BOOK_Isa, "Is.");
		BOOK_NAME_MAP.put(BookID.BOOK_Jer, "Jer.");
		BOOK_NAME_MAP.put(BookID.BOOK_Lam, "Lam.");
		BOOK_NAME_MAP.put(BookID.BOOK_Ezek, "Ezek.");
		BOOK_NAME_MAP.put(BookID.BOOK_Dan, "Dan.");
		BOOK_NAME_MAP.put(BookID.BOOK_Hos, "Hos.");
		BOOK_NAME_MAP.put(BookID.BOOK_Joel, "Joel");
		BOOK_NAME_MAP.put(BookID.BOOK_Amos, "Amos");
		BOOK_NAME_MAP.put(BookID.BOOK_Obad, "Obad.");
		BOOK_NAME_MAP.put(BookID.BOOK_Jonah, "Jonah");
		BOOK_NAME_MAP.put(BookID.BOOK_Mic, "Mic.");
		BOOK_NAME_MAP.put(BookID.BOOK_Nah, "Nah.");
		BOOK_NAME_MAP.put(BookID.BOOK_Hab, "Hab.");
		BOOK_NAME_MAP.put(BookID.BOOK_Zeph, "Zeph.");
		BOOK_NAME_MAP.put(BookID.BOOK_Hag, "Hag.");
		BOOK_NAME_MAP.put(BookID.BOOK_Zech, "Zech.");
		BOOK_NAME_MAP.put(BookID.BOOK_Mal, "Mal.");
		BOOK_NAME_MAP.put(BookID.BOOK_Tob, "Tob.");
		BOOK_NAME_MAP.put(BookID.BOOK_Jdt, "Judith");
		BOOK_NAME_MAP.put(BookID.BOOK_Wis, "Wis.");
		BOOK_NAME_MAP.put(BookID.BOOK_Sir, "Sir.");
		BOOK_NAME_MAP.put(BookID.BOOK_Bar, "Bar.");
		BOOK_NAME_MAP.put(BookID.BOOK_1Macc, "1Mac.");
		BOOK_NAME_MAP.put(BookID.BOOK_2Macc, "2Mac.");
		BOOK_NAME_MAP.put(BookID.BOOK_1Esd, "1Esdr.");
		BOOK_NAME_MAP.put(BookID.BOOK_PrMan, "Man.");
		BOOK_NAME_MAP.put(BookID.BOOK_3Macc, "3Mac.");
		BOOK_NAME_MAP.put(BookID.BOOK_2Esd, "2Esdr.");
		BOOK_NAME_MAP.put(BookID.BOOK_4Macc, "4Mac.");
		BOOK_NAME_MAP.put(BookID.BOOK_Matt, "Matt.");
		BOOK_NAME_MAP.put(BookID.BOOK_Mark, "Mark");
		BOOK_NAME_MAP.put(BookID.BOOK_Luke, "Luke");
		BOOK_NAME_MAP.put(BookID.BOOK_John, "John");
		BOOK_NAME_MAP.put(BookID.BOOK_Acts, "Acts");
		BOOK_NAME_MAP.put(BookID.BOOK_Rom, "Rom.");
		BOOK_NAME_MAP.put(BookID.BOOK_1Cor, "1Cor.");
		BOOK_NAME_MAP.put(BookID.BOOK_2Cor, "2Cor.");
		BOOK_NAME_MAP.put(BookID.BOOK_Gal, "Gal.");
		BOOK_NAME_MAP.put(BookID.BOOK_Eph, "Eph.");
		BOOK_NAME_MAP.put(BookID.BOOK_Phil, "Phil.");
		BOOK_NAME_MAP.put(BookID.BOOK_Col, "Col.");
		BOOK_NAME_MAP.put(BookID.BOOK_1Thess, "1Th.");
		BOOK_NAME_MAP.put(BookID.BOOK_2Thess, "2Th.");
		BOOK_NAME_MAP.put(BookID.BOOK_1Tim, "1Tim.");
		BOOK_NAME_MAP.put(BookID.BOOK_2Tim, "2Tim.");
		BOOK_NAME_MAP.put(BookID.BOOK_Titus, "Titus");
		BOOK_NAME_MAP.put(BookID.BOOK_Phlm, "Philem.");
		BOOK_NAME_MAP.put(BookID.BOOK_Heb, "Heb.");
		BOOK_NAME_MAP.put(BookID.BOOK_Jas, "James");
		BOOK_NAME_MAP.put(BookID.BOOK_1Pet, "1Pet.");
		BOOK_NAME_MAP.put(BookID.BOOK_2Pet, "2Pet.");
		BOOK_NAME_MAP.put(BookID.BOOK_1John, "1John");
		BOOK_NAME_MAP.put(BookID.BOOK_2John, "2John");
		BOOK_NAME_MAP.put(BookID.BOOK_3John, "3John");
		BOOK_NAME_MAP.put(BookID.BOOK_Jude, "Jude");
		BOOK_NAME_MAP.put(BookID.BOOK_Rev, "Rev.");
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		boolean paraMarker = false;
		for (Book book : bible.getBooks()) {
			if (!BOOK_NAME_MAP.containsKey(book.getId())) {
				continue;
			}
			for (Chapter chapter : book.getChapters()) {
				for (Verse v : chapter.getVerses()) {
					if (v.getElementTypes(Integer.MAX_VALUE).contains("b")) {
						paraMarker = true;
						break;
					}
				}
				if (paraMarker)
					break;
			}
			if (paraMarker)
				break;
		}
		File mainFile = new File(exportArgs[0] + ".txt");
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mainFile), StandardCharsets.UTF_8));
				BufferedWriter bnw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[0] + "-booknames.txt"), StandardCharsets.UTF_8))) {
			for (Book book : bible.getBooks()) {
				String bookName = BOOK_NAME_MAP.get(book.getId());
				if (bookName == null) {
					System.out.println("WARNING: Skipping book " + book.getAbbr());
					continue;
				}
				bnw.write(bookName + "\t" + book.getAbbr() + "\n");
				bw.write(bookName + " ");
				int cnumber = 0;
				for (Chapter chapter : book.getChapters()) {
					cnumber++;
					bw.write(cnumber + ":");
					List<VirtualVerse> vvs = chapter.createVirtualVerses();
					if (vvs.isEmpty()) {
						bw.write("1 " + (paraMarker ? "¶" : "") + "\n");
						paraMarker = false;
					}
					for (VirtualVerse vv : vvs) {
						bw.write(vv.getNumber() + " " + (paraMarker ? "¶ " : ""));
						paraMarker = false;
						StringBuilder sb = new StringBuilder();
						for (Verse v : vv.getVerses()) {
							if (!v.getNumber().equals("" + vv.getNumber())) {
								sb.append(" <b>(" + v.getNumber() + ")</b> ");
							}
							v.accept(new AccordanceVisitor(sb));
						}
						String verseText = sb.toString().replaceAll("  +", " ").trim();
						if (verseText.endsWith(" ¶")) {
							verseText = verseText.substring(0, verseText.length() - 2);
							paraMarker = true;
						}
						bw.write(verseText + "\n");
					}
				}
			}
		}
		if (mainFile.length() > 0) {
			try (RandomAccessFile raf = new RandomAccessFile(mainFile, "rw")) {
				raf.setLength(mainFile.length() - 1);
			}
		}
	}

	private static class AccordanceVisitor implements Visitor<RuntimeException> {

		protected final StringBuilder sb;
		protected final List<String> suffixStack = new ArrayList<String>();

		public AccordanceVisitor(StringBuilder sb) {
			this.sb = sb;
			pushSuffix("");
		}

		private void pushSuffix(String suffix) {
			suffixStack.add(suffix);
		}

		@Override
		public void visitVerseSeparator() {
			sb.append("/");
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
			sb.append(text.replace("<", "〈").replace(">", "〉"));
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) {
			pushSuffix("");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) {
			String prefix, suffix;
			switch (kind) {
			case BOLD:
				prefix = "<b>";
				suffix = "</b>";
				break;
			case DIVINE_NAME:
				prefix = "<c>";
				suffix = "</c>";
				break;
			case ITALIC:
				prefix = "<i>";
				suffix = "</i>";
				break;
			case SUBSCRIPT:
				prefix = "<sub>";
				suffix = "</sub>";
				break;
			case SUPERSCRIPT:
				prefix = "<sup>";
				suffix = "</sup>";
				break;
			case UNDERLINE:
				prefix = "<u>";
				suffix = "</u>";
				break;
			case WORDS_OF_JESUS:
				prefix = "<color=red>";
				suffix = "</color>";
				break;
			default:
				prefix = suffix = "";
				break;
			}
			sb.append(prefix);
			pushSuffix(suffix);
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) {
			throw new RuntimeException("Headlines not supported");
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() {
			return null;
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) {
			pushSuffix("");
			return this;
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) {
			switch (kind) {
			case NEWLINE:
			case NEWLINE_WITH_INDENT:
				sb.append("<br>");
				return;
			case PARAGRAPH:
				sb.append(" ¶ ");
				break;
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
		public void visitRawHTML(RawHTMLMode mode, String raw) {
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) {
			throw new UnsupportedOperationException("Variation text not supported");
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
			sb.append(suffixStack.remove(suffixStack.size() - 1));
			return false;
		}
	}
}
