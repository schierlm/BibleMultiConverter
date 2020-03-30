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
import biblemulticonverter.data.StandardVersification;
import biblemulticonverter.data.VirtualVerse;

/**
 * Importer and exporter for TheWord.
 */
public class TheWord implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Bible format used by theWord",
	};

	private static final BookID[] BOOK_ORDER = new BookID[66];

	private static final Set<BookID> COVERED_BOOKS = EnumSet.noneOf(BookID.class);

	static {
		for (int i = 0; i < BOOK_ORDER.length; i++) {
			BOOK_ORDER[i] = BookID.fromZefId(i + 1);
			COVERED_BOOKS.add(BOOK_ORDER[i]);
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
			for (BookID bid : BOOK_ORDER) {
				if ((bid.isNT() && !hasNT) || (!bid.isNT() && !hasOT))
					continue;
				Book bk = new Book(bid.getOsisID(), bid, bid.getEnglishName(), bid.getEnglishName());
				int[] verseCount = StandardVersification.KJV.getVerseCount(bid);
				for (int cnumber = 1; cnumber <= verseCount.length; cnumber++) {
					Chapter ch = new Chapter();
					boolean hasVerses = false;
					int maxVerse = verseCount[cnumber - 1];
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
							Matcher m = Pattern.compile("(<FI>[^<>]*<Fi>|<FO>[^<>]*<Fo>|[^<> ]*)((<W[GH][0-9]+>)+)").matcher(line.replaceFirst("^(<W[GH][0-9]+x>)+", ""));
							StringBuffer sb = new StringBuffer();
							while (m.find()) {
								String word = m.group(1);
								String tags = m.group(2);
								m.appendReplacement(sb, "");
								sb.append("<S%" + tags.substring(2, tags.length() - 1).replaceAll("><W", "%") + ">");
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
					char[] strongNumberPrefixes = new char[strongs.length];
					int[] strongNumbers = new int[strongs.length];
					try {
						for (int i = 0; i < strongs.length; i++) {
							strongNumberPrefixes[i] = strongs[i].charAt(0);
							strongNumbers[i] = Integer.parseInt(strongs[i].substring(1));
						}
						pos = parseLine(visitor.visitGrammarInformation(strongNumberPrefixes, strongNumbers, null, null), line, closePos + 1, "<s%>");
						continue;
					} catch (NumberFormatException ex) {
						// malformed Strongs tag
					}
				}
			} else if (line.startsWith("<XWG", pos) || line.startsWith("<XWH", pos)) {
				int closePos = line.indexOf('>', pos);
				try {
					int number = Integer.parseInt(line.substring(pos + 4, closePos));
					visitor.visitGrammarInformation(new char[] {line.charAt(pos+3)}, new int[] { number }, null, null);
					pos = closePos + 1;
					continue;
				} catch (NumberFormatException ex) {
					System.out.println("WARNING: Invalid Strong number in tag " + line.substring(pos, closePos + 1));
					warningCount++;
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
			for (BookID bid : BOOK_ORDER) {
				if ((bid.isNT() && !hasNT) || (!bid.isNT() && !hasOT))
					continue;
				TheWordVisitor twv = bid.isNT() ? twvn : twvo;
				Book bk = foundBooks.get(bid);
				int[] verseCount = StandardVersification.KJV.getVerseCount(bid);
				for (int cnumber = 1; cnumber <= verseCount.length; cnumber++) {
					Chapter ch = bk != null && cnumber <= bk.getChapters().size() ? bk.getChapters().get(cnumber - 1) : null;
					int maxVerse = verseCount[cnumber - 1];
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
			if (category.equals("mybiblezone") && key.equals("rawtag") && value.equals("m")) {
				bw.write("<WT");
				suffixStack.add(">");
				return this;
			}
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
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			StringBuilder suffix = new StringBuilder();
			if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					String prefix = nt ? "G" : "H";
					if (strongsPrefixes != null) {
						prefix = ("GH".indexOf(strongsPrefixes[i]) != -1 ? "" : prefix) + strongsPrefixes[i];
					}
					suffix.append("<W").append(prefix).append(strongs[i]).append('>');
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
