package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;

/**
 * Importer and exporter for TheWord.
 */
public class TheWord implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Bible format used by theWord",
	};

	private static final TheWordBookInfo[] BOOK_INFO = {
			new TheWordBookInfo(BookID.BOOK_Gen, 31, 25, 24, 26, 32, 22, 24, 22, 29, 32, 32, 20, 18, 24, 21, 16, 27, 33, 38, 18, 34, 24, 20, 67, 34, 35, 46, 22, 35, 43, 55, 32, 20, 31, 29, 43, 36, 30, 23, 23, 57, 38, 34, 34, 28, 34, 31, 22, 33, 26),
			new TheWordBookInfo(BookID.BOOK_Exod, 22, 25, 22, 31, 23, 30, 25, 32, 35, 29, 10, 51, 22, 31, 27, 36, 16, 27, 25, 26, 36, 31, 33, 18, 40, 37, 21, 43, 46, 38, 18, 35, 23, 35, 35, 38, 29, 31, 43, 38),
			new TheWordBookInfo(BookID.BOOK_Lev, 17, 16, 17, 35, 19, 30, 38, 36, 24, 20, 47, 8, 59, 57, 33, 34, 16, 30, 37, 27, 24, 33, 44, 23, 55, 46, 34),
			new TheWordBookInfo(BookID.BOOK_Num, 54, 34, 51, 49, 31, 27, 89, 26, 23, 36, 35, 16, 33, 45, 41, 50, 13, 32, 22, 29, 35, 41, 30, 25, 18, 65, 23, 31, 40, 16, 54, 42, 56, 29, 34, 13),
			new TheWordBookInfo(BookID.BOOK_Deut, 46, 37, 29, 49, 33, 25, 26, 20, 29, 22, 32, 32, 18, 29, 23, 22, 20, 22, 21, 20, 23, 30, 25, 22, 19, 19, 26, 68, 29, 20, 30, 52, 29, 12),
			new TheWordBookInfo(BookID.BOOK_Josh, 18, 24, 17, 24, 15, 27, 26, 35, 27, 43, 23, 24, 33, 15, 63, 10, 18, 28, 51, 9, 45, 34, 16, 33),
			new TheWordBookInfo(BookID.BOOK_Judg, 36, 23, 31, 24, 31, 40, 25, 35, 57, 18, 40, 15, 25, 20, 20, 31, 13, 31, 30, 48, 25),
			new TheWordBookInfo(BookID.BOOK_Ruth, 22, 23, 18, 22),
			new TheWordBookInfo(BookID.BOOK_1Sam, 28, 36, 21, 22, 12, 21, 17, 22, 27, 27, 15, 25, 23, 52, 35, 23, 58, 30, 24, 42, 15, 23, 29, 22, 44, 25, 12, 25, 11, 31, 13),
			new TheWordBookInfo(BookID.BOOK_2Sam, 27, 32, 39, 12, 25, 23, 29, 18, 13, 19, 27, 31, 39, 33, 37, 23, 29, 33, 43, 26, 22, 51, 39, 25),
			new TheWordBookInfo(BookID.BOOK_1Kgs, 53, 46, 28, 34, 18, 38, 51, 66, 28, 29, 43, 33, 34, 31, 34, 34, 24, 46, 21, 43, 29, 53),
			new TheWordBookInfo(BookID.BOOK_2Kgs, 18, 25, 27, 44, 27, 33, 20, 29, 37, 36, 21, 21, 25, 29, 38, 20, 41, 37, 37, 21, 26, 20, 37, 20, 30),
			new TheWordBookInfo(BookID.BOOK_1Chr, 54, 55, 24, 43, 26, 81, 40, 40, 44, 14, 47, 40, 14, 17, 29, 43, 27, 17, 19, 8, 30, 19, 32, 31, 31, 32, 34, 21, 30),
			new TheWordBookInfo(BookID.BOOK_2Chr, 17, 18, 17, 22, 14, 42, 22, 18, 31, 19, 23, 16, 22, 15, 19, 14, 19, 34, 11, 37, 20, 12, 21, 27, 28, 23, 9, 27, 36, 27, 21, 33, 25, 33, 27, 23),
			new TheWordBookInfo(BookID.BOOK_Ezra, 11, 70, 13, 24, 17, 22, 28, 36, 15, 44),
			new TheWordBookInfo(BookID.BOOK_Neh, 11, 20, 32, 23, 19, 19, 73, 18, 38, 39, 36, 47, 31),
			new TheWordBookInfo(BookID.BOOK_Esth, 22, 23, 15, 17, 14, 14, 10, 17, 32, 3),
			new TheWordBookInfo(BookID.BOOK_Job, 22, 13, 26, 21, 27, 30, 21, 22, 35, 22, 20, 25, 28, 22, 35, 22, 16, 21, 29, 29, 34, 30, 17, 25, 6, 14, 23, 28, 25, 31, 40, 22, 33, 37, 16, 33, 24, 41, 30, 24, 34, 17),
			new TheWordBookInfo(BookID.BOOK_Ps, 6, 12, 8, 8, 12, 10, 17, 9, 20, 18, 7, 8, 6, 7, 5, 11, 15, 50, 14, 9, 13, 31, 6, 10, 22, 12, 14, 9, 11, 12, 24, 11, 22, 22, 28, 12, 40, 22, 13, 17, 13, 11, 5, 26, 17, 11, 9, 14, 20, 23, 19, 9, 6, 7, 23, 13, 11, 11, 17, 12, 8, 12, 11, 10, 13, 20, 7, 35, 36, 5, 24, 20, 28, 23, 10, 12, 20, 72, 13, 19, 16, 8, 18, 12, 13, 17, 7, 18, 52, 17, 16, 15, 5, 23, 11, 13, 12, 9, 9, 5, 8, 28, 22, 35, 45, 48, 43, 13, 31, 7, 10, 10, 9, 8, 18, 19, 2, 29, 176, 7, 8, 9, 4, 8, 5, 6, 5, 6, 8, 8, 3, 18, 3, 3, 21, 26, 9, 8, 24, 13, 10, 7, 12, 15, 21, 10, 20, 14, 9, 6),
			new TheWordBookInfo(BookID.BOOK_Prov, 33, 22, 35, 27, 23, 35, 27, 36, 18, 32, 31, 28, 25, 35, 33, 33, 28, 24, 29, 30, 31, 29, 35, 34, 28, 28, 27, 28, 27, 33, 31),
			new TheWordBookInfo(BookID.BOOK_Eccl, 18, 26, 22, 16, 20, 12, 29, 17, 18, 20, 10, 14),
			new TheWordBookInfo(BookID.BOOK_Song, 17, 17, 11, 16, 16, 13, 13, 14),
			new TheWordBookInfo(BookID.BOOK_Isa, 31, 22, 26, 6, 30, 13, 25, 22, 21, 34, 16, 6, 22, 32, 9, 14, 14, 7, 25, 6, 17, 25, 18, 23, 12, 21, 13, 29, 24, 33, 9, 20, 24, 17, 10, 22, 38, 22, 8, 31, 29, 25, 28, 28, 25, 13, 15, 22, 26, 11, 23, 15, 12, 17, 13, 12, 21, 14, 21, 22, 11, 12, 19, 12, 25, 24),
			new TheWordBookInfo(BookID.BOOK_Jer, 19, 37, 25, 31, 31, 30, 34, 22, 26, 25, 23, 17, 27, 22, 21, 21, 27, 23, 15, 18, 14, 30, 40, 10, 38, 24, 22, 17, 32, 24, 40, 44, 26, 22, 19, 32, 21, 28, 18, 16, 18, 22, 13, 30, 5, 28, 7, 47, 39, 46, 64, 34),
			new TheWordBookInfo(BookID.BOOK_Lam, 22, 22, 66, 22, 22),
			new TheWordBookInfo(BookID.BOOK_Ezek, 28, 10, 27, 17, 17, 14, 27, 18, 11, 22, 25, 28, 23, 23, 8, 63, 24, 32, 14, 49, 32, 31, 49, 27, 17, 21, 36, 26, 21, 26, 18, 32, 33, 31, 15, 38, 28, 23, 29, 49, 26, 20, 27, 31, 25, 24, 23, 35),
			new TheWordBookInfo(BookID.BOOK_Dan, 21, 49, 30, 37, 31, 28, 28, 27, 27, 21, 45, 13),
			new TheWordBookInfo(BookID.BOOK_Hos, 11, 23, 5, 19, 15, 11, 16, 14, 17, 15, 12, 14, 16, 9),
			new TheWordBookInfo(BookID.BOOK_Joel, 20, 32, 21),
			new TheWordBookInfo(BookID.BOOK_Amos, 15, 16, 15, 13, 27, 14, 17, 14, 15),
			new TheWordBookInfo(BookID.BOOK_Obad, 21),
			new TheWordBookInfo(BookID.BOOK_Jonah, 17, 10, 10, 11),
			new TheWordBookInfo(BookID.BOOK_Mic, 16, 13, 12, 13, 15, 16, 20),
			new TheWordBookInfo(BookID.BOOK_Nah, 15, 13, 19),
			new TheWordBookInfo(BookID.BOOK_Hab, 17, 20, 19),
			new TheWordBookInfo(BookID.BOOK_Zeph, 18, 15, 20),
			new TheWordBookInfo(BookID.BOOK_Hag, 15, 23),
			new TheWordBookInfo(BookID.BOOK_Zech, 21, 13, 10, 14, 11, 15, 14, 23, 17, 12, 17, 14, 9, 21),
			new TheWordBookInfo(BookID.BOOK_Mal, 14, 17, 18, 6),
			new TheWordBookInfo(BookID.BOOK_Matt, 25, 23, 17, 25, 48, 34, 29, 34, 38, 42, 30, 50, 58, 36, 39, 28, 27, 35, 30, 34, 46, 46, 39, 51, 46, 75, 66, 20),
			new TheWordBookInfo(BookID.BOOK_Mark, 45, 28, 35, 41, 43, 56, 37, 38, 50, 52, 33, 44, 37, 72, 47, 20),
			new TheWordBookInfo(BookID.BOOK_Luke, 80, 52, 38, 44, 39, 49, 50, 56, 62, 42, 54, 59, 35, 35, 32, 31, 37, 43, 48, 47, 38, 71, 56, 53),
			new TheWordBookInfo(BookID.BOOK_John, 51, 25, 36, 54, 47, 71, 53, 59, 41, 42, 57, 50, 38, 31, 27, 33, 26, 40, 42, 31, 25),
			new TheWordBookInfo(BookID.BOOK_Acts, 26, 47, 26, 37, 42, 15, 60, 40, 43, 48, 30, 25, 52, 28, 41, 40, 34, 28, 41, 38, 40, 30, 35, 27, 27, 32, 44, 31),
			new TheWordBookInfo(BookID.BOOK_Rom, 32, 29, 31, 25, 21, 23, 25, 39, 33, 21, 36, 21, 14, 23, 33, 27),
			new TheWordBookInfo(BookID.BOOK_1Cor, 31, 16, 23, 21, 13, 20, 40, 13, 27, 33, 34, 31, 13, 40, 58, 24),
			new TheWordBookInfo(BookID.BOOK_2Cor, 24, 17, 18, 18, 21, 18, 16, 24, 15, 18, 33, 21, 14),
			new TheWordBookInfo(BookID.BOOK_Gal, 24, 21, 29, 31, 26, 18),
			new TheWordBookInfo(BookID.BOOK_Eph, 23, 22, 21, 32, 33, 24),
			new TheWordBookInfo(BookID.BOOK_Phil, 30, 30, 21, 23),
			new TheWordBookInfo(BookID.BOOK_Col, 29, 23, 25, 18),
			new TheWordBookInfo(BookID.BOOK_1Thess, 10, 20, 13, 18, 28),
			new TheWordBookInfo(BookID.BOOK_2Thess, 12, 17, 18),
			new TheWordBookInfo(BookID.BOOK_1Tim, 20, 15, 16, 16, 25, 21),
			new TheWordBookInfo(BookID.BOOK_2Tim, 18, 26, 17, 22),
			new TheWordBookInfo(BookID.BOOK_Titus, 16, 15, 15),
			new TheWordBookInfo(BookID.BOOK_Phlm, 25),
			new TheWordBookInfo(BookID.BOOK_Heb, 14, 18, 19, 16, 14, 20, 28, 13, 28, 39, 40, 29, 25),
			new TheWordBookInfo(BookID.BOOK_Jas, 27, 26, 18, 17, 20),
			new TheWordBookInfo(BookID.BOOK_1Pet, 25, 25, 22, 19, 14),
			new TheWordBookInfo(BookID.BOOK_2Pet, 21, 22, 18),
			new TheWordBookInfo(BookID.BOOK_1John, 10, 29, 24, 21, 21),
			new TheWordBookInfo(BookID.BOOK_2John, 13),
			new TheWordBookInfo(BookID.BOOK_3John, 14),
			new TheWordBookInfo(BookID.BOOK_Jude, 25),
			new TheWordBookInfo(BookID.BOOK_Rev, 20, 29, 22, 11, 14, 17, 17, 13, 21, 11, 19, 17, 18, 20, 8, 21, 18, 24, 21, 15, 27, 21),
	};

	private static final Set<BookID> COVERED_BOOKS = EnumSet.noneOf(BookID.class);

	static {
		for (TheWordBookInfo tbi : BOOK_INFO) {
			COVERED_BOOKS.add(tbi.id);
		}
	}

	private int warningCount = 0;

	@Override
	public Bible doImport(File inputFile) throws Exception {
		warningCount = 0;
		Bible result = new Bible("Imported From theWord");
		boolean hasOT = true, hasNT = true;
		if (inputFile.getName().toLowerCase().endsWith(".ot")) {
			hasNT = false;
		} else if (inputFile.getName().toLowerCase().endsWith(".nt")) {
			hasOT = false;
		}
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "UTF-8"))) {
			for (TheWordBookInfo bi : BOOK_INFO) {
				if ((bi.id.isNT() && !hasNT) || (!bi.id.isNT() && !hasOT))
					continue;
				Book bk = new Book(bi.id.getOsisID(), bi.id, bi.id.getEnglishName(), bi.id.getEnglishName());
				for (int cnumber = 1; cnumber <= bi.versification.length; cnumber++) {
					Chapter ch = new Chapter();
					boolean hasVerses = false;
					int maxVerse = bi.versification[cnumber - 1];
					for (int vnumber = 1; vnumber <= maxVerse; vnumber++) {
						String line = br.readLine();
						if (line.startsWith("\uFEFF"))
							line = line.substring(1);
						if (line.equals("- - -") || line.trim().length() == 0)
							continue;
						line = line.replaceAll("  +", " ").trim();
						hasVerses = true;
						Verse v = new Verse("" + vnumber);
						if (line.contains("<WH") || line.contains("<WG")) {
							Matcher m = Pattern.compile("(<FI>[^<> ]*<Fi>|[^<> ]*)((<W[GH][0-9]+>)+)").matcher(line.replaceFirst("^(<W[GH][0-9]+x>)+", ""));
							StringBuffer sb = new StringBuffer();
							while (m.find()) {
								String word = m.group(1);
								String tags = m.group(2);
								m.appendReplacement(sb, "");
								sb.append("<S%" + tags.substring(3, tags.length() - 1).replaceAll("><W[GH]", "%") + ">");
								sb.append(word);
								sb.append("<s%>");
							}
							m.appendTail(sb);
							line = sb.toString();
						}
						int pos = parseLine(v.getAppendVisitor(), line, 0, null);
						if (pos != line.length())
							v.getAppendVisitor().visitText(line.substring(pos));
						v.finished();
						ch.getVerses().add(v);
					}
					if (hasVerses) {
						while (bk.getChapters().size() < cnumber - 1) {
							bk.getChapters().add(new Chapter());
						}
						if (bk.getChapters().size() != cnumber - 1)
							throw new RuntimeException();
						bk.getChapters().add(ch);
					}
				}
				if (bk.getChapters().size() > 0)
					result.getBooks().add(bk);
			}
		}
		return result;
	}

	private int parseLine(Visitor<RuntimeException> visitor, String line, int pos, String endTag) {
		Visitor<RuntimeException> garbageVisitor = new FormattedText().getAppendVisitor();
		while (pos < line.length()) {
			if (line.charAt(pos) != '<') {
				int endPos = line.indexOf('<', pos);
				if (endPos == -1)
					endPos = line.length();
				visitor.visitText(line.substring(pos, endPos).replaceAll("[\r\n\t ]+", " "));
				pos = endPos;
				continue;
			}
			if (endTag != null && line.startsWith(endTag, pos))
				break;
			if (pos + 2 < line.length() && line.charAt(pos + 2) == '>' && "bius".indexOf(line.charAt(pos + 1)) != -1) {
				String newEndTag = "</" + line.charAt(pos + 1) + ">";
				if (parseLine(garbageVisitor, line, pos + 3, newEndTag) != -1) {
					FormattingInstructionKind kind;
					switch (line.charAt(pos + 1)) {
					case 'b':
						kind = FormattingInstructionKind.BOLD;
						break;
					case 'i':
						kind = FormattingInstructionKind.ITALIC;
						break;
					case 'u':
						kind = FormattingInstructionKind.UNDERLINE;
						break;
					case 's':
						kind = FormattingInstructionKind.STRIKE_THROUGH;
						break;
					default:
						throw new RuntimeException("Cannot happen");
					}
					pos = parseLine(visitor.visitFormattingInstruction(kind), line, pos + 3, newEndTag);
					continue;
				}
			} else if (line.startsWith("<sub>", pos) || line.startsWith("<sup>", pos)) {
				String newEndTag = "</" + line.substring(pos + 1, pos + 5);
				if (parseLine(garbageVisitor, line, pos + 5, newEndTag) != -1) {
					FormattingInstructionKind kind = line.charAt(pos + 3) == 'p' ? FormattingInstructionKind.SUPERSCRIPT : FormattingInstructionKind.SUBSCRIPT;
					pos = parseLine(visitor.visitFormattingInstruction(kind), line, pos + 5, newEndTag);
					continue;
				}
			} else if (line.startsWith("<FR>", pos)) {
				if (parseLine(garbageVisitor, line, pos + 4, "<Fr>") != -1) {
					pos = parseLine(visitor.visitFormattingInstruction(FormattingInstructionKind.WORDS_OF_JESUS), line, pos + 4, "<Fr>");
					continue;
				}
			} else if (line.startsWith("<FO>", pos)) {
				if (parseLine(garbageVisitor, line, pos + 4, "<Fo>") != -1) {
					pos = parseLine(visitor.visitFormattingInstruction(FormattingInstructionKind.LINK), line, pos + 4, "<Fo>");
					continue;
				}
			} else if (line.startsWith("<font color=\"gray\">/</font>", pos)) {
				visitor.visitVerseSeparator();
				pos += 27;
				continue;
			} else if (line.startsWith("<CL>", pos)) {
				visitor.visitLineBreak(LineBreakKind.NEWLINE);
				pos += 4;
				continue;
			} else if (line.startsWith("<CM>", pos)) {
				visitor.visitLineBreak(LineBreakKind.PARAGRAPH);
				pos += 4;
				continue;
			} else if (line.startsWith("<CI><PI>", pos)) {
				visitor.visitLineBreak(LineBreakKind.NEWLINE_WITH_INDENT);
				pos += 8;
				continue;
			} else if (line.startsWith("<TS", pos) && pos + 3 < line.length()) {
				char next = line.charAt(pos + 3);
				int depth, len;
				if (next == '>') {
					depth = 1;
					len = 4;
				} else if (pos + 4 < line.length() && line.charAt(pos + 4) == '>' && next >= '1' && next <= '3') {
					depth = next - '0';
					len = 5;
				} else {
					depth = len = 0;
				}
				String end = "<Ts>", altEnd = len == 5 ? "<Ts" + next + ">" : "<Ts>";
				if (line.indexOf(altEnd, pos) != -1 && (line.indexOf(end, pos) == -1 || line.indexOf(altEnd, pos) < line.indexOf(end, pos)))
					end = altEnd;
				if (len > 0 && parseLine(garbageVisitor, line, pos + len, end) != -1) {
					pos = parseLine(visitor.visitHeadline(depth), line, pos + len, end);
					continue;
				}
			} else if (line.startsWith("<RF", pos)) {
				int closePos = line.indexOf('>', pos);
				if (parseLine(garbageVisitor, line, closePos + 1, "<Rf>") != -1) {
					pos = parseLine(visitor.visitFootnote(), line, closePos + 1, "<Rf>");
					continue;
				}
			} else if (line.startsWith("<FI>", pos)) {
				if (parseLine(garbageVisitor, line, pos + 4, "<Fi>") != -1) {
					pos = parseLine(visitor.visitFormattingInstruction(FormattingInstructionKind.ITALIC), line, pos + 4, "<Fi>");
					continue;
				}
			} else if (line.startsWith("<S%", pos)) {
				int closePos = line.indexOf('>', pos);
				if (parseLine(garbageVisitor, line, closePos + 1, "<s%>") != -1) {
					String[] strongs = line.substring(pos + 3, closePos).split("%");
					int[] strongNumbers = new int[strongs.length];
					try {
						for (int i = 0; i < strongs.length; i++) {
							strongNumbers[i] = Integer.parseInt(strongs[i]);
						}
						pos = parseLine(visitor.visitGrammarInformation(strongNumbers, null, null), line, closePos + 1, "<s%>");
						continue;
					} catch (NumberFormatException ex) {
						// malformed Strongs tag
					}
				}
			} else if (line.startsWith("<WT", pos)) {
				// TODO parse morph information
			} else if (line.startsWith("<RX", pos)) {
				// TODO parse cross references
			} else if (line.startsWith("<CI>", pos) || line.startsWith("<PF", pos) || line.startsWith("<PI", pos)) {
				// extra formatting not supported by BMC
			} else if (warningCount < 100) {
				System.out.println("WARNING: Skipping unknown tag " + line.substring(pos, Math.min(pos + 20, line.length())));
				warningCount++;
			}

			// the tag is not supported (yet), skip the first character
			visitor.visitText("<");
			pos++;
		}

		if (endTag != null) {
			if (line.startsWith(endTag, pos))
				pos += endTag.length();
			else
				pos = -1;
		}
		return pos;
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		boolean hasOT = false, hasNT = false;
		Map<BookID, Book> foundBooks = new EnumMap<>(BookID.class);
		for (Book bk : bible.getBooks()) {
			if (!COVERED_BOOKS.contains(bk.getId()))
				continue;
			foundBooks.put(bk.getId(), bk);
			if (bk.getId().isNT())
				hasNT = true;
			else
				hasOT = true;
		}
		if (!hasOT && !hasNT) {
			System.out.println("WARNING: Unable to export, no supported book is covered!");
			return;
		}
		File file = new File(exportArgs[0] + "." + (hasOT && hasNT ? "ont" : hasOT ? "ot" : "nt"));

		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
			bw.write("\uFEFF");
			TheWordVisitor twvo = hasOT ? new TheWordVisitor(bw, false) : null;
			TheWordVisitor twvn = hasNT ? new TheWordVisitor(bw, true) : null;
			for (TheWordBookInfo bi : BOOK_INFO) {
				if ((bi.id.isNT() && !hasNT) || (!bi.id.isNT() && !hasOT))
					continue;
				TheWordVisitor twv = bi.id.isNT() ? twvn : twvo;
				Book bk = foundBooks.get(bi.id);
				for (int cnumber = 1; cnumber <= bi.versification.length; cnumber++) {
					Chapter ch = bk != null && cnumber <= bk.getChapters().size() ? bk.getChapters().get(cnumber - 1) : null;
					int maxVerse = bi.versification[cnumber - 1];
					int nextVerse = 1;
					if (ch != null) {
						BitSet allowedNumbers = new BitSet(maxVerse + 1);
						allowedNumbers.set(1, maxVerse + 1);
						for (VirtualVerse vv : ch.createVirtualVerses(allowedNumbers)) {
							while (vv.getNumber() > nextVerse) {
								bw.write("- - -\r\n");
								nextVerse++;
							}
							if (vv.getNumber() != nextVerse)
								throw new RuntimeException("Verse to write :" + vv.getNumber() + ", but next verse slot in file: " + nextVerse);
							for (Headline h : vv.getHeadlines()) {
								bw.write("<TS" + (h.getDepth() < 3 ? h.getDepth() : 3) + ">");
								h.accept(twv);
								twv.reset();
								bw.write("<Ts>");
							}
							for (Verse v : vv.getVerses()) {
								if (!v.getNumber().equals("" + vv.getNumber())) {
									bw.write(" (" + v.getNumber() + ")");
								}
								v.accept(twv);
								twv.reset();
							}
							bw.write("\r\n");
							nextVerse++;
						}
					}
					if (nextVerse > maxVerse + 1)
						throw new RuntimeException(nextVerse + "/" + (maxVerse + 1));
					for (int i = 0; i <= maxVerse - nextVerse; i++) {
						bw.write("- - -\r\n");
					}
				}
			}
			bw.write("\r\nabout=Converted by BibleMultiConverter\r\n");
		}
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return false;
	}

	private static class TheWordBookInfo {
		private final BookID id;
		private final int[] versification;

		public TheWordBookInfo(BookID id, int... versification) {
			super();
			this.id = id;
			this.versification = versification;
		}
	}

	private static class TheWordVisitor implements Visitor<IOException> {

		private BufferedWriter bw;
		protected final List<String> suffixStack = new ArrayList<String>();
		private boolean nt;

		private TheWordVisitor(BufferedWriter bw, boolean nt) {
			this.bw = bw;
			this.nt = nt;
			suffixStack.add("");
		}

		public void reset() {
			if (!suffixStack.isEmpty())
				throw new RuntimeException("Suffixes remaining!");
			suffixStack.add("");
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			bw.write("<font color=\"gray\">/</font>");
		}

		@Override
		public void visitText(String text) throws IOException {
			// there seems to be no escaping syntax; let's hope for the best
			bw.write(text);
		}

		@Override
		public void visitStart() throws IOException {
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws IOException {
			switch (kind) {
			case NEWLINE:
				bw.write("<CL>");
				break;
			case PARAGRAPH:
				bw.write("<CM>");
				break;
			case NEWLINE_WITH_INDENT:
				bw.write("<CI><PI>");
				break;
			}
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			return 0;
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			System.out.println("WARNING: Skipping headline where no headlines allowed");
			return null;
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			if (kind.getHtmlTag() != null) {
				bw.write("<" + kind.getHtmlTag() + ">");
				suffixStack.add("</" + kind.getHtmlTag() + ">");
				return this;
			}
			String start = "", end = "";
			switch (kind) {
			case STRIKE_THROUGH:
				start = "<s>";
				end = "</s>";
				break;
			case LINK:
			case FOOTNOTE_LINK:
				start = "<font color=blue>";
				end = "</font>";
				break;
			case WORDS_OF_JESUS:
				start = "<FR>";
				end = "<Fr>";
				break;
			default:
				break;
			}
			bw.write(start);
			suffixStack.add(end);
			return this;
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			bw.write("<RF>");
			suffixStack.add("<Rf>");
			return this;
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			Visitor<IOException> result = prio.handleVisitor(category, this);
			if (result != null)
				suffixStack.add("");
			return result;
		}

		@Override
		public boolean visitEnd() throws IOException {
			bw.write(suffixStack.remove(suffixStack.size() - 1));
			return false;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			suffixStack.add("");
			return this;
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			StringBuilder suffix = new StringBuilder();
			if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					suffix.append("<W").append(nt ? 'G' : 'H').append(strongs[i]).append('>');
				}
			}
			if (rmac != null) {
				suffix.append("<WT");
				for (String r : rmac) {
					suffix.append(r);
					suffix.append(' ');
				}
				suffix.setLength(suffix.length() - 1);
				suffix.append(">");
			}
			suffixStack.add(suffix.toString());
			return this;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			try {
				Integer.parseInt(firstVerse);
				Integer.parseInt(lastVerse);
			} catch (NumberFormatException ex) {
				System.out.println("WARNING: Skipping xref of non-numeric verse numbers: " + firstVerse + "-" + lastVerse);
				return null;
			}
			if (firstChapter != lastChapter) {
				System.out.println("WARNING: Skipping xref that spans more than one chapter");
				return null;
			}
			String verse = firstVerse + (firstVerse.equals(lastVerse) ? "" : "-" + lastVerse);
			bw.write("<RX " + book.getZefID() + "." + firstChapter + "." + verse + ">");
			return null;
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			suffixStack.add("");
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			suffixStack.add("");
			return this;
		}
	}
}
