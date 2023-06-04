package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.StandardVersification;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.versification.AccordanceReferenceList;

public class Accordance implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Bible format for Accordance",
			"",
			"Usage: Accordance <outfile> [<element>=<formatting> [...]] [lineending|encoding|verseref|verseschema=<value>]",
			"",
			"Supported elements: H*, H1-H9, FN, VN, XREF, STRONG, MORPH, DICT, GRAMMAR, PL,",
			"                    B, I, U, L, F, S, P, D, T, W",
			"Every supported element is also supported with prefix PL: (when in prolog).",
			"The prefix CSS: can be used to style CSS rules.",
			"Metadata elements:  BIBN, BKSN, BKLN (Bible name, book short/long name)",
			"",
			"Supported formattings:",
			" -                         Do not include element content",
			" +                         Include content unformatted",
			" <format>[+<format>[+...]] Include content with given format",
			" <formatting>#<formatting> Include a (footnote) number with first formatting, ",
			"                           move element content to end of verse with second formatting",
			"",
			"Supported formats: PARENS, BRACKETS, BRACES, BR_START, PARA_START, BR_END, PARA_END,",
			"                   FORCEBREAK_START, FORCEBREAK_END, NOBREAK,",
			"                   BOLD, SMALL_CAPS, ITALIC, SUB, SUP, UNDERLINE",
			"as well as colors: BLACK, GRAY, WHITE, CHOCOLATE, BURGUNDY, RED, ORANGE, BROWN,",
			"                   YELLOW, CYAN, TURQUOISE, GREEN, OLIVE, FOREST, TEAL, SAPPHIRE,",
			"                   BLUE, NAVY, PURPLE, LAVENDER, MAGENTA, CERULEAN",
			"",
			"Other supported options:",
			" lineending=cr|lf         Use CR or LF as line ending",
			" encoding=macroman,utf-8  Try first macroman, then UTF-8 as encoding",
			" verseref=short           Output the shortest possible verse refs (only verse if needed)",
			" verseref=medium          Output medium verse refs (always include chapter)",
			" verseref=full            Output full verse refs (always include book and chapter)",
			" verseschemashift=<nbr>   Shift chapter boundaries in verse schema up to given maximum of verses",
			" verseschema=fillone      Fill missing verses starting from verse 1",
			" verseschema=fillzero     In some psalms, fill from verse 0",
			" verseschema=restrictkjv  Restrict allowed verses to KJV schema",
			" verseschema=<name>@<db>  Use verse schema from database to restrict and fill verses",
			" verseschema=#<name>@<db> Use verse counts from verse schema from database to restrict and fill verses"
	};

	public static Map<BookID, String> BOOK_NAME_MAP = new EnumMap<>(BookID.class);
	private static Map<Format, String> CSS_COLORS = new EnumMap<>(Format.class);

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
		BOOK_NAME_MAP.put(BookID.BOOK_4Ezra, "4Ezra");
		BOOK_NAME_MAP.put(BookID.BOOK_3Macc, "3Mac.");
		BOOK_NAME_MAP.put(BookID.BOOK_4Macc, "4Mac.");
		BOOK_NAME_MAP.put(BookID.BOOK_Sus, "Sus.");
		BOOK_NAME_MAP.put(BookID.BOOK_Bel, "Bel");
		BOOK_NAME_MAP.put(BookID.BOOK_EpJer, "Let.");
		BOOK_NAME_MAP.put(BookID.BOOK_PssSol, "Sol.");
		BOOK_NAME_MAP.put(BookID.BOOK_1En, "Enoch");
		BOOK_NAME_MAP.put(BookID.BOOK_Odes, "Ode.");
		BOOK_NAME_MAP.put(BookID.BOOK_EpLao, "Laod.");
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

		CSS_COLORS.put(Format.BLACK, "black");
		CSS_COLORS.put(Format.GRAY, "dimgray");
		CSS_COLORS.put(Format.WHITE, "white");
		CSS_COLORS.put(Format.CHOCOLATE, "saddlebrown");
		CSS_COLORS.put(Format.BURGUNDY, "maroon");
		CSS_COLORS.put(Format.RED, "red");
		CSS_COLORS.put(Format.ORANGE, "orange");
		CSS_COLORS.put(Format.BROWN, "sandybrown");
		CSS_COLORS.put(Format.YELLOW, "yellow");
		CSS_COLORS.put(Format.CYAN, "cyan");
		CSS_COLORS.put(Format.TURQUOISE, "turquoise");
		CSS_COLORS.put(Format.GREEN, "green");
		CSS_COLORS.put(Format.OLIVE, "olivedrab");
		CSS_COLORS.put(Format.FOREST, "forestgreen");
		CSS_COLORS.put(Format.TEAL, "teal");
		CSS_COLORS.put(Format.SAPPHIRE, "deepskyblue");
		CSS_COLORS.put(Format.BLUE, "blue");
		CSS_COLORS.put(Format.NAVY, "navy");
		CSS_COLORS.put(Format.PURPLE, "purple");
		CSS_COLORS.put(Format.LAVENDER, "blueviolet");
		CSS_COLORS.put(Format.MAGENTA, "magenta");
		CSS_COLORS.put(Format.CERULEAN, "darkcyan");
	}

	private static Set<String> SUPPORTED_ELEMENTS = new HashSet<>(Arrays.asList(
			"B", "I", "U", "L", "F", "S", "P", "D", "T", "W", "VN", "XREF", "STRONG", "MORPH", "DICT", "GRAMMAR",
			"FN", "H1", "H2", "H3", "H4", "H5", "H6", "H7", "H8", "H9", "BIBN", "BKSN", "BKLN", "PL", "PL:XREF",
			"PL:B", "PL:I", "PL:U", "PL:L", "PL:F", "PL:S", "PL:P", "PL:D", "PL:T", "PL:W", "PL:FN",
			"PL:H1", "PL:H2", "PL:H3", "PL:H4", "PL:H5", "PL:H6", "PL:H7", "PL:H8", "PL:H9"));

	@Override
	public Bible doImport(File inputFile) throws Exception {
		// probe charset
		String line, charset = "US-ASCII";
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.ISO_8859_1))) {
			while ((line = br.readLine()) != null) {
				if (line.matches("[\0-~]*"))
					continue;
				byte[] origBytes = line.getBytes(StandardCharsets.ISO_8859_1);
				byte[] testUTF8 = new String(origBytes, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
				if (Arrays.equals(testUTF8, origBytes)) {
					charset = "UTF-8";
				} else {
					charset = "MacRoman";
				}
				break;
			}
		}
		System.out.println("Detected charset: " + charset);

		// do the real parsing
		Bible bible = new Bible("Imported From Accordance");
		Map<BookID, Book> bibleBooks = new EnumMap<>(BookID.class);
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), charset))) {
			BookID bookID = null;
			Verse v = null;
			int chapterNumber = -1, verseNumber = -1;
			while ((line = br.readLine()) != null) {
				String rest = line.replaceAll("(?U)\\p{Cntrl}", " ").replaceAll("  +", " ").trim();
				if (rest.isEmpty())
					continue;
				int pos = rest.indexOf(' ');
				if (!rest.matches("[0-9]+[: ].*") && pos != -1) {
					bookID = AccordanceReferenceList.BOOK_NAME_MAP.get(rest.substring(0, pos));
					if (bookID == null)
						bookID = AccordanceReferenceList.BOOK_NAME_MAP.get(rest.substring(0, pos) + ".");
					if (bookID == null)
						throw new IOException("Unsupported verse reference/book name: " + line);
					chapterNumber = verseNumber = -1;
					rest = rest.substring(pos + 1).trim();
					pos = rest.indexOf(' ');
				}
				if (rest.matches("[0-9]+ .*")) {
					verseNumber = Integer.parseInt(rest.substring(0, pos));
				} else if (rest.matches("[0-9]+:[0-9]+ .*")) {
					String[] numberParts = rest.substring(0, pos).split(":");
					chapterNumber = Integer.parseInt(numberParts[0]);
					verseNumber = Integer.parseInt(numberParts[1]);
				} else {
					throw new IOException("Unsupported verse reference: " + line);
				}
				rest = rest.substring(pos + 1).trim();
				if (rest.startsWith("¶")) {
					if (v != null)
						v.getAppendVisitor().visitLineBreak(LineBreakKind.PARAGRAPH);
					rest = rest.substring(1).trim();
				}
				if (bookID == null || chapterNumber == -1)
					throw new IOException("Incomplete first line verse reference: " + line);
				Book bk = bibleBooks.get(bookID);
				if (bk == null) {
					bk = new Book(bookID.getOsisID(), bookID, bookID.getEnglishName(), bookID.getEnglishName());
					bibleBooks.put(bookID, bk);
					bible.getBooks().add(bk);
				}
				v = new Verse(verseNumber == 0 ? "1/t" : "" + verseNumber);
				int cn = chapterNumber;
				if (cn == 0) {
					v = new Verse(verseNumber + "/p");
					cn = 1;
				}
				while (bk.getChapters().size() < cn)
					bk.getChapters().add(new Chapter());
				if (bk.getChapters().get(cn-1).getVerseIndex(v.getNumber()) != -1)
					System.out.println("WARNING: Duplicate verse "+bookID.getOsisID()+" "+chapterNumber+":"+verseNumber);
				bk.getChapters().get(cn - 1).getVerses().add(v);
				try {
					int parsed = parseText(rest, 0, v.getAppendVisitor());
					if (parsed != rest.length())
						throw new IOException("Unparsed tags: " + rest.substring(parsed));
				} catch (IOException ex) {
					System.err.println();
					System.err.println("Parse error in "+bookID.getOsisID()+" "+chapterNumber+":"+verseNumber);
					System.err.println("Line: "+line);
					System.err.println();
					throw ex;
				}
			}
		}
		for (Book bk : bible.getBooks()) {
			for (Chapter ch : bk.getChapters()) {
				for (Verse v : ch.getVerses()) {
					v.finished();
				}
			}
		}
		return bible;
	}

	private int parseText(String text, int start, Visitor<RuntimeException> vv) throws IOException {
		while (start < text.length()) {
			if (text.charAt(start) != '<') {
				int pos1 = text.indexOf('<', start);
				int pos2 = text.indexOf('¶', start);
				if (pos1 == -1)
					pos1 = text.length();
				while (pos2 != -1 && pos2 < pos1) {
					vv.visitText(text.substring(start, pos2).replaceAll(" +$", ""));
					vv.visitLineBreak(LineBreakKind.PARAGRAPH);
					start = pos2 + 1;
					while (start < text.length() && text.charAt(start) == ' ')
						start++;
					pos2 = text.indexOf('¶', start);
				}
				vv.visitText(text.substring(start, pos1));
				start = pos1;
				continue;
			}
			if (text.startsWith("</", start))
				return start;
			int pos = text.indexOf('>', start);
			if (pos == -1) {
				throw new IOException("Unclosed tag: " + text.substring(start));
			}
			String tag = text.substring(start + 1, pos).toUpperCase();
			start = pos + 1;
			if (tag.startsWith("COLOR=")) {
				String cssName;
				try {
					cssName = CSS_COLORS.get(Format.valueOf(tag.substring(6)));
					if (cssName == null)
						throw new IllegalArgumentException();
				} catch (IllegalArgumentException ex) {
					throw new IOException("Unsupported tag: " + text.substring(start - tag.length() - 2));
				}
				start = parseText(text, start, vv.visitCSSFormatting("-accordance-color:" + tag.substring(6).toLowerCase() + "; color:" + cssName));
				if (!text.substring(start).toUpperCase().startsWith("</COLOR>"))
					throw new IOException("Unclosed COLOR tag: " + text.substring(start));
				start += 8;
			} else if (tag.equals("BR")) {
				vv.visitLineBreak(LineBreakKind.NEWLINE);
			} else {
				FormattingInstructionKind kind;
				switch (tag) {
				case "B":
					kind = FormattingInstructionKind.BOLD;
					break;
				case "C":
					kind = FormattingInstructionKind.DIVINE_NAME;
					break;
				case "I":
					kind = FormattingInstructionKind.ITALIC;
					break;
				case "SUB":
					kind = FormattingInstructionKind.SUBSCRIPT;
					break;
				case "SUP":
					kind = FormattingInstructionKind.SUPERSCRIPT;
					break;
				case "U":
					kind = FormattingInstructionKind.UNDERLINE;
					break;
				default:
					throw new IOException("Unsupported tag: " + text.substring(start - tag.length() - 2));
				}
				start = parseText(text, start, vv.visitFormattingInstruction(kind));
				if (!text.substring(start).toUpperCase().startsWith("</" + tag + ">"))
					throw new IOException("Unclosed " + tag + " tag: " + text.substring(start));
				start += tag.length() + 3;
			}
		}
		return start;
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
		File booknameFile = new File(exportArgs[0] + "-booknames.txt");
		Map<String, String[]> formatRules = new HashMap<>();
		Set<String> unformattedElements = new HashSet<>();
		parseFormatRule("VN=CERULEAN", formatRules);
		parseFormatRule("BIBN=-", formatRules);
		parseFormatRule("BKSN=-", formatRules);
		parseFormatRule("BKLN=-", formatRules);
		for (String rule : Arrays.asList("B=BOLD", "D=SMALL_CAPS", "I=ITALIC", "S=SUB", "P=SUP", "U=UNDERLINE", "W=RED")) {
			parseFormatRule(rule, formatRules);
			parseFormatRule("PL:" + rule, formatRules);
		}
		for (Map.Entry<Format, String> cssColor : CSS_COLORS.entrySet()) {
			parseFormatRule("CSS:-ACCORDANCE-COLOR:" + cssColor.getKey().name() + ";_COLOR:" + cssColor.getValue().toUpperCase() + "=" + cssColor.getKey().name(), formatRules);
			parseFormatRule("PL:CSS:-ACCORDANCE-COLOR:" + cssColor.getKey().name() + ";_COLOR:" + cssColor.getValue().toUpperCase() + "=" + cssColor.getKey().name(), formatRules);
		}
		String lineEnding = "\n";
		String[] encodings = null;
		int verseSchema = -1, verseRef = 0, verseSchemaShift = 0, verseSchemaShiftLogging = 0;
		Versification versification = null;
		BitSet psalmSet = null;
		for (int i = 1; i < exportArgs.length; i++) {
			if (exportArgs[i].toLowerCase().equals("lineending=cr")) {
				lineEnding = "\r";
			} else if (exportArgs[i].toLowerCase().equals("lineending=lf")) {
				lineEnding = "\n";
			} else if (exportArgs[i].toLowerCase().equals("verseref=short")) {
				verseRef = 0;
			} else if (exportArgs[i].toLowerCase().equals("verseref=medium")) {
				verseRef = 1;
			} else if (exportArgs[i].toLowerCase().equals("verseref=full")) {
				verseRef = 2;
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
			} else if (exportArgs[i].toLowerCase().startsWith("verseschemashift=")) {
				String maxCount = exportArgs[i].substring(17);
				while (maxCount.startsWith("+")) {
					verseSchemaShiftLogging++;
					maxCount = maxCount.substring(1);
				}
				verseSchemaShift = Integer.parseInt(maxCount);
			} else if (exportArgs[i].toLowerCase().startsWith("verseschema=#")) {
				String[] params = exportArgs[i].substring(13).split("@", 2);
				versification = new VersificationSet(new File(params[1])).findVersification(params[0]);
				verseSchema = 3;
			} else if (exportArgs[i].toLowerCase().startsWith("verseschema=")) {
				String[] params = exportArgs[i].substring(12).split("@", 2);
				versification = new VersificationSet(new File(params[1])).findVersification(params[0]);
				verseSchema = 2;
			} else if (exportArgs[i].toLowerCase().startsWith("encoding=")) {
				encodings = exportArgs[i].substring(9).split(",");
			} else {
				parseFormatRule(exportArgs[i], formatRules);
			}
		}
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mainFile), StandardCharsets.UTF_8));
				BufferedWriter bnw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(booknameFile), StandardCharsets.UTF_8))) {
			String bibleName = bible.getName();
			for (Book book : bible.getBooks()) {
				String bookName = BOOK_NAME_MAP.get(book.getId());
				String shortName = book.getShortName(), longName = book.getLongName();
				if (bookName == null) {
					System.out.println("WARNING: Skipping book " + book.getAbbr());
					continue;
				}
				int cnumber = 0;
				List<Chapter> chapters = book.getChapters();
				List<List<Integer>> allReferences = new ArrayList<>();
				if (verseSchema == 2 || verseSchema == 3) {
					for (int i = 0; i < versification.getVerseCount(); i++) {
						Reference r = versification.getReference(i);
						if (r.getBook() != book.getId())
							continue;
						int chapter = r.getChapter();
						String verse = r.getVerse();
						if (chapter == 1 && verse.endsWith("/p")) {
							chapter = 0;
							verse = verse.substring(0, verse.length() - 2);
						}
						if (verse.equals("1/t"))
							verse = "0";
						if (!verse.matches("[0-9]+"))
							throw new IOException("Unsupported verse reference in versification: " + verse);
						while (chapter >= allReferences.size())
							allReferences.add(new ArrayList<>());
						allReferences.get(chapter).add(Integer.parseInt(verse));
					}
					if (allReferences.isEmpty()) {
						System.out.println("WARNING: Skipping book " + book.getAbbr() + " as it is not contained in versification");
						continue;
					}
					if (!allReferences.get(0).isEmpty()) {
						// we have chapter zero here, so we need to split
						// verses!
						cnumber--;
						chapters = new ArrayList<>(chapters);
						Chapter chapter0 = new Chapter();
						Chapter chapter1 = new Chapter();
						Chapter origChapter = chapters.remove(0);
						for (Verse v : origChapter.getVerses()) {
							if (v.getNumber().endsWith("/p")) {
								Verse vv = new Verse(v.getNumber().substring(0, v.getNumber().length() - 2));
								v.accept(vv.getAppendVisitor());
								vv.finished();
								chapter0.getVerses().add(vv);
							} else {
								chapter1.getVerses().add(v);
							}
						}
						if (chapter0.getVerses().isEmpty())
							chapter1.setProlog(origChapter.getProlog());
						else
							chapter0.setProlog(origChapter.getProlog());
						chapters.add(0, chapter1);
						chapters.add(0, chapter0);
					}
					int allowedChapters = allReferences.size() - cnumber - 1;
					if (chapters.size() > allowedChapters) {
						if (cnumber == 0)
							chapters = new ArrayList<>(chapters);
						Chapter lastAllowedChapter = new Chapter();
						lastAllowedChapter.setProlog(chapters.get(allowedChapters - 1).getProlog());
						lastAllowedChapter.getVerses().addAll(chapters.get(allowedChapters - 1).getVerses());
						for (int ch = allowedChapters; ch < chapters.size(); ch++) {
							int cnum = ch + cnumber + 1;
							for (Verse v : chapters.get(ch).getVerses()) {
								if (v.getNumber().contains(",")) {
									lastAllowedChapter.getVerses().add(v);
								} else {
									Verse vv = new Verse(cnum + "," + v.getNumber());
									v.accept(vv.getAppendVisitor());
									vv.finished();
									lastAllowedChapter.getVerses().add(vv);
								}
							}
						}
						while (chapters.size() >= allowedChapters)
							chapters.remove(chapters.size() - 1);
						chapters.add(lastAllowedChapter);
					}
					if (verseSchemaShift > 0) {
						// determine how many verses can be removed / added
						int[] removableVerses = new int[allReferences.size()];
						for (int rnum = 0; rnum < allReferences.size(); rnum++) {
							List<Integer> refs = allReferences.get(rnum);
							if (refs.isEmpty())
								continue;
							int cnum = rnum - cnumber - 1;
							if (cnum >= chapters.size() || chapters.get(cnum).getVerses().isEmpty()) {
								int removableRefs = refs.size() - 1;
								if (rnum == allReferences.size() - 1) {
									// last chapter may be empty
									removableRefs++;
								}
								removableVerses[rnum] = Math.min(verseSchemaShift, removableRefs);
								continue;
							}
							List<Verse> verses = chapters.get(cnum).getVerses();
							int normalVvCount = countVirtualVerses(verseSchema, verses, refs);
							// try removing
							List<Integer> reducedRefs = new ArrayList<>(refs);
							for (int i = 0; i < verseSchemaShift; i++) {
								reducedRefs.remove(reducedRefs.size() - 1);
								if (reducedRefs.isEmpty() || countVirtualVerses(verseSchema, verses, reducedRefs) < normalVvCount)
									break;
								removableVerses[rnum]++;
							}
							// try adding
							if (removableVerses[rnum] == 0) {
								int nextVerseNum = refs.get(refs.size() - 1)+1;
								List<Integer> augmentedRefs = new ArrayList<>(refs);
								for (int i = 0; i < verseSchemaShift; i++) {
									while(augmentedRefs.contains(nextVerseNum)) nextVerseNum++;
									augmentedRefs.add(nextVerseNum);
									nextVerseNum++;
								}
								int maxVvCount = countVirtualVerses(verseSchema, verses, augmentedRefs);
								if (maxVvCount > normalVvCount) {
									nextVerseNum = refs.get(refs.size() - 1)+1;
									augmentedRefs = new ArrayList<>(refs);
									for (int i = 0; i < verseSchemaShift; i++) {
										while(augmentedRefs.contains(nextVerseNum)) nextVerseNum++;
										augmentedRefs.add(nextVerseNum);
										nextVerseNum++;
										removableVerses[rnum]--;
										if (countVirtualVerses(verseSchema, verses, augmentedRefs) == maxVvCount) {
											break;
										}
									}
								}
							}
						}
						// normalize so that the sum is zero
						int removableSum = Arrays.stream(removableVerses).sum();
						for (int i = removableVerses.length - 1; i >= 0; i--) {
							if (removableSum == 0)
								break;
							if (removableVerses[i] > 0 && removableSum > 0) {
								int maxFix = Math.min(removableVerses[i], removableSum);
								removableVerses[i] -= maxFix;
								removableSum -= maxFix;
							} else if (removableVerses[i] < 0 && removableSum < 0) {
								int maxFix = Math.min(-removableVerses[i], -removableSum);
								removableVerses[i] += maxFix;
								removableSum += maxFix;
							}
						}
						if (removableSum != 0)
							throw new IllegalStateException("Unable to normalize removable verses");
						for (int i = 0; i < removableVerses.length; i++) {
							if (removableVerses[i] == 0)
								continue;
							List<Integer> refs = allReferences.get(i);
							if (verseSchemaShiftLogging > 0) {
								System.out.println("INFO: Changing chapter " + book.getAbbr() + " " + i + " from " + refs.size() + " to " + (refs.size() - removableVerses[i]));
								if (verseSchemaShiftLogging > 1)
									System.out.println("\t Old value: " + refs);
							}
							if (removableVerses[i] > 0) {
								refs.subList(refs.size() - removableVerses[i], refs.size()).clear();
							} else if (removableVerses[i] < 0) {
								int nextVerseNum = refs.get(refs.size() - 1)+1;
								for (int j = 0; j < -removableVerses[i]; j++) {
									while(refs.contains(nextVerseNum)) nextVerseNum++;
									refs.add(nextVerseNum);
									nextVerseNum++;
								}
							}
							if (verseSchemaShiftLogging > 1) {
								System.out.println("\t New value: " + refs);
							}
						}
					}
					if (verseSchema == 3) {
						chapters = new ArrayList<>(chapters);
						for (int i = 0; i < chapters.size() - 1; i++) {
							Chapter ch = new Chapter();
							ch.setProlog(chapters.get(i).getProlog());
							ch.getVerses().addAll(chapters.get(i).getVerses());
							chapters.set(i, ch);
						}
						int holeCount = allReferences.stream().mapToInt(refs -> refs.size()).sum() - chapters.stream().mapToInt(ch -> ch.getVerses().size()).sum();
						int chapterToRead = 0;
						for (int i = 0; i < chapters.size() - 1; i++) {
							if (chapterToRead < i + 1)
								chapterToRead = i + 1;
							while (chapterToRead < chapters.size() && chapters.get(chapterToRead).getProlog() == null && chapters.get(chapterToRead).getVerses().isEmpty()) {
								chapterToRead++;
							}
							List<Verse> verses = chapters.get(i).getVerses();
							List<Integer> refs = allReferences.get(i + cnumber + 1);
							int holesHere = refs.size() - verses.size();
							holeCount -= holesHere;
							if (chapters.get(chapterToRead).getProlog() != null) {
								// cannot shuffle verses
								continue;
							}
							if (holesHere < 0 && holeCount >= -holesHere) {
								holeCount += holesHere;
								int cnum = i + cnumber + 1;
								for (int j = 0; j < -holesHere; j++) {
									Verse v = verses.remove(verses.size() - 1);
									if (v.getNumber().contains(",")) {
										chapters.get(i + 1).getVerses().add(0, v);
									} else {
										Verse vv = new Verse(cnum + "," + v.getNumber());
										v.accept(vv.getAppendVisitor());
										vv.finished();
										chapters.get(i + 1).getVerses().add(0, vv);
									}
									chapterToRead = 0;
								}
							} else if (holesHere > 0 && holeCount < 0) {
								for (int j = 0; j < holesHere; j++) {
									while (chapterToRead < chapters.size() && chapters.get(chapterToRead).getProlog() == null && chapters.get(chapterToRead).getVerses().isEmpty()) {
										chapterToRead++;
									}
									if (chapters.get(chapterToRead).getProlog() != null)
										break;
									holeCount++;
									Verse v = chapters.get(chapterToRead).getVerses().remove(0);
									int cnum = chapterToRead + cnumber + 1;
									if (v.getNumber().contains(",")) {
										verses.add(v);
									} else {
										Verse vv = new Verse(cnum + "," + v.getNumber());
										v.accept(vv.getAppendVisitor());
										vv.finished();
										verses.add(vv);
									}
								}
							}
						}
					}
				}
				bnw.write(bookName.replace(".", "") + "\t" + book.getAbbr().replace(".", "") + lineEnding);
				if (verseRef != 2)
					bw.write(bookName + " ");
				for (Chapter chapter : chapters) {
					FormattedText prolog = chapter.getProlog();
					cnumber++;
					List<VirtualVerse> vvs;
					Map<String, String> verseNumberMap = null;
					int nextFillVerse = verseSchema < 0 ? 99999 : verseSchema, lastFillVerse = -1;
					if (nextFillVerse == 0 && !(book.getId() == BookID.BOOK_Ps && psalmSet.get(cnumber)))
						nextFillVerse = 1;
					if (verseSchema == -2) {
						int[] verseCounts = StandardVersification.KJV.getVerseCount(book.getId());
						int minVerse = book.getId() == BookID.BOOK_Ps && psalmSet.get(cnumber) ? 0 : 1;
						int maxVerse = verseCounts != null && cnumber <= verseCounts.length ? verseCounts[cnumber - 1] : -1;
						BitSet verseBits = maxVerse == -1 ? null : new BitSet(maxVerse + 1);
						if (verseBits != null)
							verseBits.set(minVerse, maxVerse + 1);
						vvs = chapter.createVirtualVerses(true, verseBits, false);
					} else if (verseSchema == 2) {
						Chapter dummyChapter = new Chapter();
						verseNumberMap = new HashMap<>();
						if (cnumber >= allReferences.size() || allReferences.get(cnumber).isEmpty()) {
							System.out.println("WARNING: Skipping export of " + book.getAbbr() + " " + cnumber + " as it is not contained in versification!");
							continue;
						}
						List<Integer> references = allReferences.get(cnumber);
						for (int i = 0; i < references.size(); i++) {
							verseNumberMap.put("" + (i + 1), "" + references.get(i));
						}
						int nextExtraVerse = 1, maxSeenRealVerse = -2;
						for (Verse v : chapter.getVerses()) {
							String vnumber = v.getNumber().equals("1/t") ? "0" : v.getNumber();
							int idx;
							try {
								int vnum = Integer.parseInt(vnumber);
								maxSeenRealVerse = Math.max(vnum, maxSeenRealVerse);
								idx = references.indexOf(vnum);
							} catch (NumberFormatException ex) {
								idx = -1;
							}
							String newNum = "" + (idx + 1);
							if (idx == -1) {
								if (vnumber.startsWith("" + (maxSeenRealVerse + 1)) && references.contains(maxSeenRealVerse + 1)) {
									maxSeenRealVerse++;
									Verse dv = new Verse("" + maxSeenRealVerse);
									dv.finished();
									dummyChapter.getVerses().add(dv);
								}
								newNum = nextExtraVerse + "x";
								nextExtraVerse++;
								verseNumberMap.put(newNum, vnumber);
							}
							Verse vv = new Verse(newNum);
							v.accept(vv.getAppendVisitor());
							vv.finished();
							dummyChapter.getVerses().add(vv);
						}
						nextFillVerse = 1;
						lastFillVerse = references.size();
						BitSet verseBits = new BitSet(lastFillVerse + 1);
						verseBits.set(1, lastFillVerse + 1);
						vvs = dummyChapter.createVirtualVerses(false, verseBits, false);
					} else if (verseSchema == 3) {
						verseNumberMap = new HashMap<>();
						if (cnumber >= allReferences.size() || allReferences.get(cnumber).isEmpty()) {
							System.out.println("WARNING: Skipping export of " + book.getAbbr() + " " + cnumber + " as it is not contained in versification!");
							continue;
						}
						List<Integer> references = allReferences.get(cnumber);
						Chapter emptyChapter = new Chapter();
						for (int i = 0; i < references.size(); i++) {
							emptyChapter.getVerses().add(new Verse("" + (i + 1)));
							verseNumberMap.put("" + (i + 1), "" + references.get(i));
						}
						vvs = emptyChapter.createVirtualVerses(false, false);
						while (vvs.size() > chapter.getVerses().size())
							vvs.remove(vvs.size() - 1);
						int nextExtraVerse = 1;
						for (int i = 0; i < chapter.getVerses().size(); i++) {
							Verse v = chapter.getVerses().get(i);
							VirtualVerse viv;
							if (i < vvs.size()) {
								viv = vvs.get(i);
								viv.getVerses().clear();
							} else {
								viv = vvs.get(vvs.size() - 1);
							}
							String vnumber = v.getNumber().equals("1/t") ? "0" : v.getNumber();
							int idx;
							try {
								int vnum = Integer.parseInt(vnumber);
								idx = references.indexOf(vnum);
							} catch (NumberFormatException ex) {
								idx = -1;
							}
							String newNum = "" + (idx + 1);
							if (idx == -1) {
								newNum = nextExtraVerse + "x";
								nextExtraVerse++;
								verseNumberMap.put(newNum, vnumber);
							}
							Verse vv = new Verse(newNum);
							v.accept(vv.getAppendVisitor());
							vv.finished();
							viv.getVerses().add(vv);
						}
						nextFillVerse = 1;
						lastFillVerse = references.size();
					} else {
						vvs = chapter.createVirtualVerses(true, false);
					}
					if (verseRef == 0)
						bw.write(cnumber + ":");
					if (vvs.isEmpty()) {
						if (verseRef == 2)
							bw.write(bookName + " ");
						if (verseRef != 0)
							bw.write(cnumber + ":");
						if (verseSchema < 0) {
							bw.write("1 " + (paraMarker ? "¶" : "") + lineEnding);
						} else {
							bw.write(mapBack(verseNumberMap, "" + nextFillVerse) + " " + (paraMarker ? "¶ " : "") + "-" + lineEnding);
							nextFillVerse++;
						}
						paraMarker = false;
					}
					for (VirtualVerse vv : vvs) {
						while (nextFillVerse < vv.getNumber()) {
							if (verseRef == 2)
								bw.write(bookName + " ");
							if (verseRef != 0)
								bw.write(cnumber + ":");
							bw.write(mapBack(verseNumberMap, "" + nextFillVerse) + " " + (paraMarker ? "¶ " : "") + "-" + lineEnding);
							nextFillVerse++;
						}
						if (verseRef == 2)
							bw.write(bookName + " ");
						if (verseRef != 0)
							bw.write(cnumber + ":");
						bw.write(mapBack(verseNumberMap, "" + vv.getNumber()) + " " + (paraMarker ? "¶ " : ""));
						if (nextFillVerse == vv.getNumber())
							nextFillVerse++;
						paraMarker = false;
						AccordanceVisitor av = new AccordanceVisitor(formatRules, unformattedElements, book.getId().isNT());
						av.start();
						if (bibleName != null) {
							Visitor<RuntimeException> nv = av.visitElement("BIBN", DEFAULT_SKIP);
							if (nv != null) {
								nv.visitText(bibleName);
								nv.visitEnd();
							}
							bibleName = null;
						}
						if (shortName != null) {
							Visitor<RuntimeException> nv = av.visitElement("BKSN", DEFAULT_SKIP);
							if (nv != null) {
								nv.visitText(shortName);
								nv.visitEnd();
							}
							shortName = null;
						}
						if (longName != null) {
							Visitor<RuntimeException> nv = av.visitElement("BKLN", DEFAULT_SKIP);
							if (nv != null) {
								nv.visitText(longName);
								nv.visitEnd();
							}
							longName = null;
						}
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
									nv.visitText(mapBack(verseNumberMap, v.getNumber()));
									nv.visitEnd();
								}
								av.visitText(" ");
							}
							v.accept(av);
							firstVerse = false;
						}
						String verseText = av.getContent().replaceAll("  +", " ").trim();
						if (verseText.endsWith("¶")) {
							verseText = verseText.substring(0, verseText.length() - 1);
							paraMarker = true;
						}
						bw.write(verseText + lineEnding);
					}
					while (nextFillVerse <= lastFillVerse) {
						if (verseRef == 2)
							bw.write(bookName + " ");
						if (verseRef != 0)
							bw.write(cnumber + ":");
						bw.write(mapBack(verseNumberMap, "" + nextFillVerse) + " " + (paraMarker ? "¶ " : "") + "-" + lineEnding);
						nextFillVerse++;
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
			String booknameContent = new String(Files.readAllBytes(booknameFile.toPath()), StandardCharsets.UTF_8);
			byte[] bytes = null, booknameBytes = null;
			;
			for (String encoding : encodings) {
				byte[] trying = content.getBytes(encoding);
				byte[] booknameTrying = booknameContent.getBytes(encoding);
				if (new String(trying, encoding).equals(content) && new String(booknameTrying, encoding).equals(booknameContent)) {
					bytes = trying;
					booknameBytes = booknameTrying;
					break;
				}
			}
			if (bytes == null) {
				System.out.println("WARNING: All encoding could not losslessly encode the bible, using " + encodings[encodings.length - 1] + " anyway");
				bytes = content.getBytes(encodings[encodings.length - 1]);
				booknameBytes = booknameContent.getBytes(encodings[encodings.length - 1]);
			}
			try (FileOutputStream fos = new FileOutputStream(mainFile)) {
				fos.write(bytes);
			}
			try (FileOutputStream fos = new FileOutputStream(booknameFile)) {
				fos.write(booknameBytes);
			}
		}
		if (!unformattedElements.isEmpty())
			System.out.println("WARNING: No formatting specified for elements: " + unformattedElements);
	}

	private String mapBack(Map<String, String> verseNumberMap, String verseNumber) {
		if (verseNumberMap == null)
			return verseNumber;
		return verseNumberMap.get(verseNumber).toString();
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
		if (!parts[0].startsWith("CSS:") && !parts[0].startsWith("PL:CSS:") && !SUPPORTED_ELEMENTS.contains(parts[0]))
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

	private int countVirtualVerses(int verseSchema, List<Verse> verses, List<Integer> refs) {
		if (verseSchema == 2) {
			Chapter dummyChapter = new Chapter();
			int nextExtraVerse = 1, maxSeenRealVerse = -2;
			for (Verse v : verses) {
				String vnumber = v.getNumber().equals("1/t") ? "0" : v.getNumber();
				int idx;
				try {
					int vnum = Integer.parseInt(vnumber);
					maxSeenRealVerse = Math.max(vnum, maxSeenRealVerse);
					idx = refs.indexOf(vnum);
				} catch (NumberFormatException ex) {
					idx = -1;
				}
				String newNum = "" + (idx + 1);
				if (idx == -1) {
					if (vnumber.startsWith("" + (maxSeenRealVerse + 1)) && refs.contains(maxSeenRealVerse + 1)) {
						maxSeenRealVerse++;
						Verse dv = new Verse("" + maxSeenRealVerse);
						dv.finished();
						dummyChapter.getVerses().add(dv);
					}
					newNum = nextExtraVerse + "x";
					nextExtraVerse++;
				}
				Verse vv = new Verse(newNum);
				v.accept(vv.getAppendVisitor());
				vv.finished();
				dummyChapter.getVerses().add(vv);
			}
			int lastFillVerse = refs.size();
			BitSet verseBits = new BitSet(lastFillVerse + 1);
			verseBits.set(1, lastFillVerse + 1);
			return dummyChapter.createVirtualVerses(false, verseBits, false).size();
		} else if (verseSchema == 3) {
			return Math.min(refs.size(), verses.size());
		} else {
			throw new IllegalStateException("Virtual verses cannot be counted without a versemap to be adjusted");
		}
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private static class AccordanceVisitor implements Visitor<RuntimeException> {

		private TagReorderStringBuilder sb = new TagReorderStringBuilder(PendingLineBreak.NOBREAK), fnb = new TagReorderStringBuilder(PendingLineBreak.NONE);
		private boolean inFootnote = false;
		private int footnoteNumber = 0;
		private final List<String> suffixStack = new ArrayList<String>();
		private final Map<String, String[]> formatRules;
		private final Set<String> unspecifiedFormattings;
		private final boolean nt;
		private String elementPrefix = "";

		public AccordanceVisitor(Map<String, String[]> formatRules, Set<String> unspecifiedFormattings, boolean nt) {
			this.formatRules = formatRules;
			this.unspecifiedFormattings = unspecifiedFormattings;
			this.nt = nt;
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
			return sb.getContent() + fnb.getContent() + sb.pendingLineBreak.merge(fnb.pendingLineBreak).getText();
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
				TagReorderStringBuilder tmp = sb;
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
			sb.appendText("/");
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
			sb.appendText(text.replace("<", "〈").replace(">", "〉").replace("\u00A0", " "));
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) {
			return visitElement("CSS:" + css.replace(" ", "_").toUpperCase(), DEFAULT_KEEP);
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
				sb.append("<para>");
				break;
			}
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
			Visitor<RuntimeException> next = visitElement("GRAMMAR", DEFAULT_KEEP);
			if (next == null)
				return null;
			String[] suffixes = { suffixStack.remove(suffixStack.size() - 1), "" };
			if (strongs != null) {
				String[] rule = getElementRule("STRONG", DEFAULT_SKIP);
				if (rule.length > 0) {
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < strongs.length; i++) {
						sb.append(" ").append(strongsPrefixes != null ? strongsPrefixes[i] : nt ? 'G' : 'H').append(strongs[i]);
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
				TagReorderStringBuilder tmp = sb;
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

	private static class TagReorderStringBuilder {
		private final StringBuilder sb = new StringBuilder();
		private final List<ShadowTag> shadowTagStack = new ArrayList<>();
		private PendingLineBreak pendingLineBreak;

		public TagReorderStringBuilder(PendingLineBreak pendingLineBreak) {
			this.pendingLineBreak = pendingLineBreak;
		}

		public void appendText(String text) {
			if (text.isEmpty())
				return;
			sb.append(pendingLineBreak.getText());
			pendingLineBreak = PendingLineBreak.NONE;
			for (ShadowTag t : shadowTagStack) {
				if (t.printed)
					continue;
				sb.append(t.getOpenTag());
				t.printed = true;
			}
			sb.append(text);
		}

		public void append(String textWithTags) {
			int start = 0, pos = textWithTags.indexOf('<');
			while (pos != -1) {
				appendText(textWithTags.substring(start, pos));
				int endPos = textWithTags.indexOf('>', pos);
				if (endPos == -1)
					throw new RuntimeException("Incomplete tag in " + textWithTags);
				String[] tagInfo = textWithTags.substring(pos + 1, endPos).split("=", 2);
				if (tagInfo.length == 1 && tagInfo[0].equals("br")) {
					pendingLineBreak = pendingLineBreak.merge(PendingLineBreak.BR);
				} else if (tagInfo.length == 1 && tagInfo[0].equals("para")) {
					pendingLineBreak = pendingLineBreak.merge(PendingLineBreak.PARAGRAPH);
				} else if (tagInfo.length == 1 && tagInfo[0].equals("nobreak")) {
					pendingLineBreak = pendingLineBreak.merge(PendingLineBreak.NOBREAK);
				} else if (tagInfo.length == 1 && tagInfo[0].equals("forcebreak")) {
					sb.append(pendingLineBreak.getText());
					pendingLineBreak = PendingLineBreak.NONE;
				} else if (tagInfo.length == 1 && tagInfo[0].startsWith("/")) {
					ShadowTag lastTag = shadowTagStack.remove(shadowTagStack.size() - 1);
					if (!lastTag.name.equals(tagInfo[0].substring(1)))
						throw new RuntimeException("Closing tag <" + tagInfo[0] + "> does not match expected tag " + lastTag.name);
					if (lastTag.shadowed)
						throw new IllegalStateException("Closing a tag that was not unshadowed");
					sb.append(lastTag.getCloseTagIfPrinted());
					if (lastTag.type == ShadowTagType.SHADOWING) {
						int shadowedIndex = -1;
						for (int i = shadowTagStack.size() - 1; i >= 0; i--) {
							ShadowTag tag = shadowTagStack.get(i);
							sb.append(tag.getCloseTagIfPrinted());
							tag.printed = false;
							if (tag.shadowed && tag.name.equals(lastTag.name)) {
								tag.shadowed = false;
								shadowedIndex = i;
								break;
							}
						}
						if (shadowedIndex == -1)
							throw new IllegalStateException("Shadow stack mismatch");
					}
				} else {
					int shadowCandidateIndex = -1;
					for (int i = shadowTagStack.size() - 1; i >= 0; i--) {
						ShadowTag tag = shadowTagStack.get(i);
						if (tag.type != ShadowTagType.REDUNDANT && !tag.shadowed && tag.name.equals(tagInfo[0])) {
							shadowCandidateIndex = i;
							break;
						}
					}
					ShadowTagType newType = ShadowTagType.NORMAL;
					String tagValue = tagInfo.length == 2 ? tagInfo[1] : "";
					if (shadowCandidateIndex != -1) {
						ShadowTag candidate = shadowTagStack.get(shadowCandidateIndex);
						if (candidate.value.equals(tagValue)) {
							newType = ShadowTagType.REDUNDANT;
						} else {
							newType = ShadowTagType.SHADOWING;
							for (int i = shadowTagStack.size() - 1; i >= shadowCandidateIndex; i--) {
								ShadowTag tag = shadowTagStack.get(i);
								sb.append(shadowTagStack.get(i).getCloseTagIfPrinted());
								tag.printed = false;
							}
							candidate.shadowed = true;
						}
					}
					ShadowTag newTag = new ShadowTag(newType, tagInfo[0], tagValue);
					shadowTagStack.add(newTag);
				}
				start = endPos + 1;
				pos = textWithTags.indexOf('<', start);
			}
			appendText(textWithTags.substring(start));
		}

		public String getContent() {
			if (!shadowTagStack.isEmpty())
				throw new IllegalStateException();
			return sb.toString();
		}
	}

	private static class ShadowTag {
		private final ShadowTagType type;
		private final String name;
		private final String value;
		private boolean shadowed = false, printed = false;

		private ShadowTag(ShadowTagType type, String name, String value) {
			this.type = type;
			this.name = name;
			this.value = value;
		}

		private String getOpenTag() {
			if (shadowed || type == ShadowTagType.REDUNDANT)
				return "";
			return "<" + name + (value.isEmpty() ? "" : "=" + value) + ">";
		}

		private String getCloseTagIfPrinted() {
			if (shadowed || !printed || type == ShadowTagType.REDUNDANT)
				return "";
			return "</" + name + ">";
		}
	}

	private static enum ShadowTagType {
		NORMAL, SHADOWING, REDUNDANT
	}

	private static enum PendingLineBreak {
		NONE(""), BR("<br>"), PARAGRAPH("¶"), NOBREAK("");

		private String text;

		private PendingLineBreak(String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}

		public PendingLineBreak merge(PendingLineBreak other) {
			return values()[Math.max(ordinal(), other.ordinal())];
		}
	}

	private static final String[] DEFAULT_KEEP = { "", "" }, DEFAULT_SKIP = {}, DEFAULT_VERSENO = { "<color=cerulean>(", ")</color>" };

	private static enum Format {
		PARENS("(", ")"), BRACKETS("[", "]"), BRACES("{", "}"), NOBREAK("<nobreak>", "<nobreak>"), //
		BR_START("<br>", ""), PARA_START("<para>", ""), BR_END("", "<br>"), PARA_END("", "<para>"), //
		FORCEBREAK_START("<forcebreak>", ""), FORCEBREAK_END("", "<forcebreak>"), //
		BOLD("<b>", "</b>"), SMALL_CAPS("<c>", "</c>"), ITALIC("<i>", "</i>"), SUB("<sub>", "</sub>"), SUP("<sup>", "</sup>"), UNDERLINE("<u>", "</u>"), //

		BLACK("<color=black>", "</color>"), GRAY("<color=gray>", "</color>"), WHITE("<color=white>", "</color>"), CHOCOLATE("<color=chocolate>", "</color>"), //
		BURGUNDY("<color=burgundy>", "</color>"), RED("<color=red>", "</color>"), ORANGE("<color=orange>", "</color>"), BROWN("<color=brown>", "</color>"), //
		YELLOW("<color=yellow>", "</color>"), CYAN("<color=cyan>", "</color>"), TURQUOISE("<color=turquoise>", "</color>"), GREEN("<color=green>", "</color>"), //
		OLIVE("<color=olive>", "</color>"), FOREST("<color=forest>", "</color>"), TEAL("<color=teal>", "</color>"), SAPPHIRE("<color=sapphire>", "</color>"), //
		BLUE("<color=blue>", "</color>"), NAVY("<color=navy>", "</color>"), PURPLE("<color=purple>", "</color>"), LAVENDER("<color=lavender>", "</color>"), //
		MAGENTA("<color=magenta>", "</color>"), CERULEAN("<color=cerulean>", "</color>");

		private final String prefix, suffix;

		private Format(String prefix, String suffix) {
			this.prefix = prefix;
			this.suffix = suffix;
		}
	}
}
