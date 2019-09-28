package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.StandardVersification;
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
			"",
			"Usage: Accordance <outfile> [<element>=<formatting> [...]] [lineending|encoding|verseschema=<value>]",
			"",
			"Supported elements: H*, H1-H9, FN, VN, XREF, STRONG, MORPH, DICT, GRAMMAR, PL,",
			"                    B, I, U, L, F, S, P, D, T, W",
			"Every supported element is also supported with prefix PL: (when in prolog).",
			"",
			"Supported formattings:",
			" -                         Do not include element content",
			" +                         Include content unformatted",
			" <format>[+<format>[+...]] Include content with given format",
			" <formatting>#<formatting> Include a (footnote) number with first formatting, ",
			"                           move element content to end of verse with second formatting",
			"",
			"Supported formats: PARENS, BRACKETS, BRACES, BR, BOLD, SMALL_CAPS, ITALIC, SUB, SUP, UNDERLINE",
			"as well as colors: BLACK, GRAY, WHITE, CHOCOLATE, BURGUNDY, RED, ORANGE, BROWN,",
			"                   YELLOW, CYAN, TURQUOISE, GREEN, OLIVE, FOREST, TEAL, SAPPHIRE,",
			"                   BLUE, NAVY, PURPLE, LAVENDER, MAGENTA",
			"",
			"Other supported options:",
			" lineending=cr|lf         Use CR or LF as line ending",
			" encoding=macroman,utf-8  Try first macroman, then UTF-8 as encoding",
			" verseschema=fillone      Fill missing verses starting from verse 1",
			" verseschema=fillzero     In some psalms, fill from verse 0",
			" verseschema=restrictkjv  Restrict allowed verses to KJV schema"
	};

	public static Map<BookID, String> BOOK_NAME_MAP = new EnumMap<>(BookID.class);

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

	private static Set<String> SUPPORTED_ELEMENTS = new HashSet<>(Arrays.asList(
			"B", "I", "U", "L", "F", "S", "P", "D", "T", "W", "VN", "XREF", "STRONG", "MORPH", "DICT", "GRAMMAR",
			"FN", "H1", "H2", "H3", "H4", "H5", "H6", "H7", "H8", "H9", "PL", "PL:XREF",
			"PL:B", "PL:I", "PL:U", "PL:L", "PL:F", "PL:S", "PL:P", "PL:D", "PL:T", "PL:W",
			"PL:H1", "PL:H2", "PL:H3", "PL:H4", "PL:H5", "PL:H6", "PL:H7", "PL:H8", "PL:H9"));

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
		Map<String, String[]> formatRules = new HashMap<>();
		Set<String> unformattedElements = new HashSet<>();
		parseFormatRule("VN=BOLD", formatRules);
		for (String rule : Arrays.asList("B=BOLD", "D=SMALL_CAPS", "I=ITALIC", "S=SUB", "P=SUP", "U=UNDERLINE", "W=RED")) {
			parseFormatRule(rule, formatRules);
			parseFormatRule("PL:" + rule, formatRules);
		}
		String lineEnding = "\n";
		String[] encodings = null;
		int verseSchema = -1;
		BitSet psalmSet = null;
		for (int i = 1; i < exportArgs.length; i++) {
			if (exportArgs[i].toLowerCase().equals("lineending=cr")) {
				lineEnding = "\r";
			} else if (exportArgs[i].toLowerCase().equals("lineending=lf")) {
				lineEnding = "\n";
			} else if (exportArgs[i].toLowerCase().equals("verseschema=fillone")) {
				verseSchema = 1;
			} else if (exportArgs[i].toLowerCase().equals("verseschema=fillzero")) {
				verseSchema = 0;
				psalmSet = new BitSet(151);
				psalmSet.set(3, 9 + 1);
				psalmSet.set(11, 32 + 1);
				psalmSet.set(34, 42 + 1);
				psalmSet.set(44, 70 + 1);
				psalmSet.set(72, 90 + 1);
				psalmSet.set(92);
				psalmSet.set(98);
				psalmSet.set(100, 103 + 1);
				psalmSet.set(108, 110 + 1);
				psalmSet.set(120, 134 + 1);
				psalmSet.set(138, 145 + 1);
			} else if (exportArgs[i].toLowerCase().equals("verseschema=restrictkjv")) {
				verseSchema = -2;
			} else if (exportArgs[i].toLowerCase().startsWith("encoding=")) {
				encodings = exportArgs[i].substring(9).split(",");
			} else {
				parseFormatRule(exportArgs[i], formatRules);
			}
		}
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mainFile), StandardCharsets.UTF_8));
				BufferedWriter bnw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[0] + "-booknames.txt"), StandardCharsets.UTF_8))) {
			for (Book book : bible.getBooks()) {
				String bookName = BOOK_NAME_MAP.get(book.getId());
				if (bookName == null) {
					System.out.println("WARNING: Skipping book " + book.getAbbr());
					continue;
				}
				bnw.write(bookName + "\t" + book.getAbbr() + lineEnding);
				bw.write(bookName + " ");
				int cnumber = 0;
				for (Chapter chapter : book.getChapters()) {
					FormattedText prolog = chapter.getProlog();
					cnumber++;
					bw.write(cnumber + ":");
					List<VirtualVerse> vvs;
					if (verseSchema == -2) {
						int[] verseCounts = StandardVersification.KJV.getVerseCount(book.getId());
						int minVerse = book.getId() == BookID.BOOK_Ps && psalmSet.get(cnumber) ? 0 : 1;
						int maxVerse = verseCounts != null && cnumber <= verseCounts.length ? verseCounts[cnumber - 1] : -1;
						BitSet verseBits = maxVerse == -1 ? null : new BitSet(maxVerse + 1);
						if (verseBits != null)
							verseBits.set(minVerse, maxVerse + 1);
						vvs = chapter.createVirtualVerses(true, verseBits, false);
					} else {
						vvs = chapter.createVirtualVerses(true, false);
					}
					if (vvs.isEmpty()) {
						if (verseSchema == 0 && book.getId() == BookID.BOOK_Ps && psalmSet.get(cnumber)) {
							bw.write("0");
						} else {
							bw.write("1");
						}
						if (verseSchema < 0)
							bw.write(" " + (paraMarker ? "¶" : "") + lineEnding);
						else
							bw.write(" " + (paraMarker ? "¶ " : "") + "-" + lineEnding);
						paraMarker = false;
					}
					int nextFillVerse = verseSchema < 0 ? 99999 : verseSchema;
					if (nextFillVerse == 0 && !(book.getId() == BookID.BOOK_Ps && psalmSet.get(cnumber)))
						nextFillVerse = 1;
					for (VirtualVerse vv : vvs) {
						while (nextFillVerse < vv.getNumber()) {
							bw.write(nextFillVerse + " " + (paraMarker ? "¶ " : "") + "-" + lineEnding);
							nextFillVerse++;
						}
						bw.write(vv.getNumber() + " " + (paraMarker ? "¶ " : ""));
						if (nextFillVerse == vv.getNumber())
							nextFillVerse++;
						paraMarker = false;
						AccordanceVisitor av = new AccordanceVisitor(formatRules, unformattedElements);
						av.start();
						if (prolog != null) {
							AccordanceVisitor plv = av.startProlog();
							if (plv != null) {
								prolog.accept(plv);
							}
							prolog = null;
						}
						if (!vv.getHeadlines().isEmpty())
							throw new IllegalStateException();
						av.visitEnd();
						boolean firstVerse = true;
						for (Verse v : vv.getVerses()) {
							av.start();
							if (!firstVerse || !v.getNumber().equals(vv.getNumber() == 0 ? "1/t" : "" + vv.getNumber())) {
								av.visitText(" ");
								Visitor<RuntimeException> nv = av.visitElement("VN", DEFAULT_VERSENO);
								if (nv != null) {
									nv.visitText(v.getNumber());
									nv.visitEnd();
								}
								av.visitText(" ");
							}
							v.accept(av);
							firstVerse = false;
						}
						String verseText = av.getContent().replaceAll("  +", " ").trim();
						if (verseText.endsWith(" ¶")) {
							verseText = verseText.substring(0, verseText.length() - 2);
							paraMarker = true;
						}
						bw.write(verseText + lineEnding);
					}
				}
			}
		}
		if (mainFile.length() > 0) {
			try (RandomAccessFile raf = new RandomAccessFile(mainFile, "rw")) {
				raf.setLength(mainFile.length() - 1);
			}
		}
		if (encodings != null) {
			String content = new String(Files.readAllBytes(mainFile.toPath()), StandardCharsets.UTF_8);
			byte[] bytes = null;
			for (String encoding : encodings) {
				byte[] trying = content.getBytes(encoding);
				if (new String(trying, encoding).equals(content)) {
					bytes = trying;
					break;
				}
			}
			if (bytes == null) {
				System.out.println("WARNING: All encoding could not losslessly encode the bible, using " + encodings[encodings.length - 1] + " anyway");
				bytes = content.getBytes(encodings[encodings.length - 1]);
			}
			try (FileOutputStream fos = new FileOutputStream(mainFile)) {
				fos.write(bytes);
			}
		}
		if (!unformattedElements.isEmpty())
			System.out.println("WARNING: No formatting specified for elements: " + unformattedElements);
	}

	private void parseFormatRule(String rule, Map<String, String[]> formatRules) {
		String[] parts = rule.toUpperCase().split("=");
		if (parts.length != 2)
			throw new RuntimeException("Unsupported format rule: " + rule);
		if (parts[0].equals("H*") || parts[0].equals("PL:H*")) {
			for (int i = 1; i <= 9; i++)
				parseFormatRule(parts[0].replace('*', (char) ('0' + i)) + "=" + parts[1], formatRules);
			return;
		}
		if (!SUPPORTED_ELEMENTS.contains(parts[0]))
			throw new RuntimeException("Unsupported element in format rule: " + rule);
		if (parts[1].equals("-")) {
			formatRules.put(parts[0], new String[0]);
		} else if (parts[1].contains("#")) {
			String[] formats = parts[1].split("#");
			if (formats.length != 2)
				throw new RuntimeException("Unsupported formatting in format rule: " + rule);
			String[] f1 = parseFormats(formats[0]), f2 = parseFormats(formats[1]);
			formatRules.put(parts[0], new String[] { f1[0], f1[1], f2[0], f2[1] });
		} else {
			formatRules.put(parts[0], parseFormats(parts[1]));
		}
	}

	private String[] parseFormats(String formats) {
		if (formats.equals("+"))
			return new String[] { "", "" };
		StringBuilder prefix = new StringBuilder(), suffix = new StringBuilder();
		for (String format : formats.split("\\+")) {
			Format fmt = Format.valueOf(format);
			prefix.append(fmt.prefix);
			suffix.insert(0, fmt.suffix);
		}
		return new String[] { prefix.toString(), suffix.toString() };
	}

	private static class AccordanceVisitor implements Visitor<RuntimeException> {

		private StringBuilder sb = new StringBuilder(), fnb = new StringBuilder();
		private boolean inFootnote = false;
		private int footnoteNumber = 0;
		private final List<String> suffixStack = new ArrayList<String>();
		private final Map<String, String[]> formatRules;
		private final Set<String> unspecifiedFormattings;
		private String elementPrefix = "";

		public AccordanceVisitor(Map<String, String[]> formatRules, Set<String> unspecifiedFormattings) {
			this.formatRules = formatRules;
			this.unspecifiedFormattings = unspecifiedFormattings;
		}

		public void start() {
			if (inFootnote || !suffixStack.isEmpty())
				throw new IllegalStateException();
			pushSuffix("");
		}

		public AccordanceVisitor startProlog() {
			if (!elementPrefix.isEmpty())
				throw new IllegalStateException();
			AccordanceVisitor next = visitElement("PL", DEFAULT_SKIP);
			if (next != null) {
				pushSuffix("\0\0\1");
				elementPrefix = "PL:";
			}
			return next;
		}

		private String getContent() {
			if (inFootnote || !suffixStack.isEmpty())
				throw new IllegalStateException();
			return sb.toString() + fnb.toString();
		}

		private void pushSuffix(String suffix) {
			suffixStack.add(suffix);
		}

		private String[] getElementRule(String elementType, String[] defaultRule) {
			String[] rule = formatRules.get(elementPrefix + elementType);
			if (rule == null) {
				unspecifiedFormattings.add(elementPrefix + elementType);
				rule = defaultRule;
			}
			return rule;
		}

		private AccordanceVisitor visitElement(String elementType, String[] defaultRule) {
			String[] rule = getElementRule(elementType, defaultRule);
			if (rule.length == 0) {
				return null;
			} else if (rule.length == 2) {
				sb.append(rule[0]);
				pushSuffix(rule[1]);
				return this;
			} else if (rule.length == 4) {
				if (inFootnote)
					throw new IllegalStateException("Footnote inside footnote");
				inFootnote = true;
				footnoteNumber++;
				sb.append(rule[0] + footnoteNumber);
				fnb.append(" " + rule[2] + footnoteNumber + " ");
				pushSuffix(rule[1] + "\0" + rule[3]);
				StringBuilder tmp = sb;
				sb = fnb;
				fnb = tmp;
				pushSuffix("\0\0\2");
				return this;
			} else {
				throw new IllegalStateException();
			}
		}

		private void appendRule(String[] suffixes, String[] rule, String text) {
			if (rule.length == 2) {
				suffixes[0] += " " + rule[0] + text + rule[1];
			} else if (rule.length == 4) {
				footnoteNumber++;
				suffixes[0] += " " + rule[0] + footnoteNumber + rule[1];
				suffixes[1] += " " + rule[2] + footnoteNumber + " " + text + rule[3];
			} else {
				throw new IllegalStateException();
			}
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
			return visitElement("CSS:" + css, DEFAULT_KEEP);
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) {
			return visitElement(("" + kind.getCode()).toUpperCase(), DEFAULT_KEEP);
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) {
			return visitElement("H" + depth, DEFAULT_SKIP);
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() {
			return visitElement("FN", DEFAULT_SKIP);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) {
			return visitElement("XREF", DEFAULT_KEEP);
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
			Visitor<RuntimeException> next = visitElement("GRAMMAR", DEFAULT_KEEP);
			if (next == null)
				return null;
			String[] suffixes = { suffixStack.remove(suffixStack.size() - 1), "" };
			if (strongs != null) {
				String[] rule = getElementRule("STRONG", DEFAULT_SKIP);
				if (rule.length > 0) {
					StringBuilder sb = new StringBuilder();
					for (int strong : strongs) {
						sb.append(" G").append(strong);
					}
					appendRule(suffixes, rule, sb.toString().trim());
				}
			}
			if (rmac != null) {
				String[] rule = getElementRule("MORPH", DEFAULT_SKIP);
				if (rule.length > 0) {
					StringBuilder sb = new StringBuilder();
					for (String morph : rmac) {
						sb.append(" ").append(morph);
					}
					appendRule(suffixes, rule, sb.toString().trim());
				}
			}
			if (!suffixes[1].isEmpty())
				suffixes[0] += "\0" + suffixes[1];
			pushSuffix(suffixes[0]);
			return next;
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) {
			return visitElement("DICT", DEFAULT_KEEP);
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) {
			unspecifiedFormattings.add(elementPrefix + "RAWHTML");
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
			String suffix = suffixStack.remove(suffixStack.size() - 1);
			if (suffix.equals("\0\0\1")) {
				elementPrefix = "";
				suffix = suffixStack.remove(suffixStack.size() - 1);
			}
			if (suffix.equals("\0\0\2")) {
				if (!inFootnote)
					throw new IllegalStateException();
				inFootnote = false;
				StringBuilder tmp = sb;
				sb = fnb;
				fnb = tmp;
				suffix = suffixStack.remove(suffixStack.size() - 1);
			}
			if (suffix.contains("\0")) {
				String[] sparts = suffix.split("\0");
				if (sparts.length != 2)
					throw new IllegalStateException();
				sb.append(sparts[0]);
				fnb.append(sparts[1]);
			} else {
				sb.append(suffix);
			}
			return false;
		}
	}

	private static final String[] DEFAULT_KEEP = { "", "" }, DEFAULT_SKIP = {}, DEFAULT_VERSENO = { "<color=teal>(", ")</color>" };

	private static enum Format {
		PARENS("(", ")"), BRACKETS("[", "]"), BRACES("{", "}"), BR("<br>", ""), //
		BOLD("<b>", "</b>"), SMALL_CAPS("<c>", "</c>"), ITALIC("<i>", "</i>"), SUB("<sub>", "</sub>"), SUP("<sup>", "</sup>"), UNDERLINE("<u>", "</u>"), //

		BLACK("<color=black>", "</color>"), GRAY("<color=gray>", "</color>"), WHITE("<color=white>", "</color>"), CHOCOLATE("<color=chocolate>", "</color>"), //
		BURGUNDY("<color=burgundy>", "</color>"), RED("<color=red>", "</color>"), ORANGE("<color=orange>", "</color>"), BROWN("<color=brown>", "</color>"), //
		YELLOW("<color=yellow>", "</color>"), CYAN("<color=cyan>", "</color>"), TURQUOISE("<color=turquoise>", "</color>"), GREEN("<color=green>", "</color>"), //
		OLIVE("<color=olive>", "</color>"), FOREST("<color=forest>", "</color>"), TEAL("<color=teal>", "</color>"), SAPPHIRE("<color=sapphire>", "</color>"), //
		BLUE("<color=blue>", "</color>"), NAVY("<color=navy>", "</color>"), PURPLE("<color=purple>", "</color>"), LAVENDER("<color=lavender>", "</color>"), //
		MAGENTA("<color=magenta>", "</color>");

		private final String prefix, suffix;

		private Format(String prefix, String suffix) {
			this.prefix = prefix;
			this.suffix = suffix;
		}
	}
}
