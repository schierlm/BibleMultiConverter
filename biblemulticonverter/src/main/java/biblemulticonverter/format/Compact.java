package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.HyperlinkType;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.schema.haggai.PARAGRAPH;

public class Compact implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"A text-format that is small and well-compressible.",
			"",
			"Usage (export): Compact <OutputFile>",
			"",
			"Basically, every verse will be put into its own line, similar to VPL.",
			"But book, chapter, and verse information is omitted in case it can be inferred from the previous verse.",
			"Also, formatting tags are cut as soon as they are unique (resulting in lots of 'unclosed' angle brackets)",
			"",
			"Use this format for transmission or storage of modules, not for editing. The parser is not very fault-tolerant."
	};

	private static final String MAGIC = "BiMuCo-1.0:";

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File exportFile = new File(exportArgs[0]);
		try (Writer w = new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
			doExport(bible, w);
		}
	}

	protected void doExport(Bible bible, Writer w) throws IOException {
		w.write(MAGIC + bible.getName() + "\n");
		for (Book book : bible.getBooks()) {
			w.write("=" + book.getAbbr() + "\t" + book.getId().getOsisID() + "\t" + book.getShortName() + "\t" + book.getLongName() + "\n");
			boolean firstChapter = true;
			int verseNum;
			for (Chapter ch : book.getChapters()) {
				if (firstChapter)
					firstChapter = false;
				else
					w.write('+');
				if (ch.getProlog() != null) {
					w.write("0 ");
					ch.getProlog().accept(new CompactVisitor(w, false));
					w.write('\n');
				}
				verseNum = 1;
				for (Verse v : ch.getVerses()) {
					if (v.getNumber().equals("" + verseNum)) {
						StringWriter sw = new StringWriter();
						v.accept(new CompactVisitor(sw, false));
						String s = sw.toString();
						if (!s.matches("[A-Za-z].*"))
							w.write(' ');
						w.write(s);
					} else {
						w.write(v.getNumber() + " ");
						v.accept(new CompactVisitor(w, false));
						Matcher m = Utils.compilePattern("([0-9]+).*").matcher(v.getNumber());
						if (!m.matches()) {
							throw new IllegalStateException();
						}
						verseNum = Integer.parseInt(m.group(1));
					}
					verseNum++;
					w.write('\n');
				}
			}
		}
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
			return doImport(br);
		}
	}

	protected Bible doImport(BufferedReader br) throws IOException {
		String line = br.readLine();
		if (!line.startsWith(MAGIC))
			throw new IOException("Invalid header line: " + line);
		Bible result = new Bible(line.substring(MAGIC.length()));
		Book currentBook = null;
		Chapter currentChapter = null;
		int nextVerseNum = -1;
		while ((line = br.readLine()) != null) {
			if (line.length() == 0)
				throw new IOException("Empty line");
			char start = line.charAt(0);
			while (start == '+') {
				if (currentChapter == null)
					currentBook.getChapters().add(new Chapter());
				currentChapter = null;
				nextVerseNum = -1;
				line = line.substring(1);
				if (line.length() == 0)
					throw new IOException("Empty line");
				start = line.charAt(0);
			}
			if (start == '=') {
				String[] fields = line.substring(1).split("\t", -1);
				if (fields.length != 4)
					throw new IOException("Unsupported chapter heading: " + line);
				currentBook = new Book(fields[0], BookID.fromOsisId(fields[1]), fields[2], fields[3]);
				result.getBooks().add(currentBook);
				currentChapter = null;
				nextVerseNum = -1;
			} else if (start == '0' && line.startsWith("0 ")) {
				if (currentChapter == null) {
					currentChapter = new Chapter();
					currentBook.getChapters().add(currentChapter);
					nextVerseNum = 1;
				}
				if (currentChapter.getProlog() != null)
					throw new IOException("More than one prolog for the same chapter");
				FormattedText prolog = new FormattedText();
				parseCompact(prolog.getAppendVisitor(), line.substring(2));
				currentChapter.setProlog(prolog);
				prolog.finished();
			} else if (start >= '1' && start <= '9' && line.contains(" ")) {
				if (currentChapter == null) {
					currentChapter = new Chapter();
					currentBook.getChapters().add(currentChapter);
				}
				int pos = line.indexOf(' ');
				Verse v = new Verse(line.substring(0, pos));
				parseCompact(v.getAppendVisitor(), line.substring(pos + 1));
				v.finished();
				currentChapter.getVerses().add(v);
				Matcher m = Utils.compilePattern("([0-9]+).*").matcher(v.getNumber());
				if (!m.matches()) {
					throw new IllegalStateException();
				}
				nextVerseNum = Integer.parseInt(m.group(1)) + 1;
			} else if (start == ' ' || (start >= 'A' && start <= 'Z') || (start >= 'a' && start <= 'z')) {
				if (currentChapter == null) {
					currentChapter = new Chapter();
					currentBook.getChapters().add(currentChapter);
					nextVerseNum = 1;
				}
				if (start == ' ')
					line = line.substring(1);
				Verse v = new Verse("" + nextVerseNum);
				parseCompact(v.getAppendVisitor(), line);
				v.finished();
				currentChapter.getVerses().add(v);
				nextVerseNum++;
			} else {
				throw new IOException("Unsupported line: " + line);
			}
		}
		return result;
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return true;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private void parseCompact(Visitor<RuntimeException> visitor, String line) throws IOException {
		int lastPos = 0, pos = line.indexOf('<');
		List<Visitor<RuntimeException>> visitorStack = new ArrayList<Visitor<RuntimeException>>();
		String[] args;
		while (pos != -1) {
			if (pos > lastPos) {
				visitor.visitText(line.substring(lastPos, pos));
			}
			char ch = line.charAt(pos + 1);
			lastPos = pos + 2;
			if (ch >= 'a' && ch <= 'z') {
				visitorStack.add(visitor);
				visitor = visitor.visitFormattingInstruction(FormattingInstructionKind.fromChar(ch));
			} else {
				switch (ch) {
				case '/':
					visitor = visitorStack.remove(visitorStack.size() - 1);
					break;
				case '<':
					visitor.visitText("<");
					break;
				case 'H':
					visitorStack.add(visitor);
					visitor = visitor.visitHeadline(line.charAt(pos + 2) - '0');
					lastPos++;
					break;
				case 'F':
					visitorStack.add(visitor);
					boolean ofCrossReferences = false;
					if (Diffable.parseXrefMarkers && line.startsWith(FormattedText.XREF_MARKER, lastPos)) {
						lastPos += FormattedText.XREF_MARKER.length();
						ofCrossReferences = true;
					}
					visitor = visitor.visitFootnote(ofCrossReferences);
					break;
				case 'Y':
					visitorStack.add(visitor);
					visitor = visitor.visitFootnote(true);
					break;
				case 'C':
					lastPos = line.indexOf('>', pos) + 1;
					visitorStack.add(visitor);
					visitor = visitor.visitCSSFormatting(line.substring(pos + 2, lastPos - 1));
					break;
				case 'V':
					visitor.visitVerseSeparator();
					break;
				case 'B':
					int indent = 0;
					if (line.charAt(lastPos) >= 'a' && line.charAt(lastPos) <= 'x') {
						indent = line.charAt(lastPos) - 'a' + 1;
						lastPos++;
					} else if (line.charAt(lastPos) >= 'y' && line.charAt(lastPos) <= 'z') {
						indent = line.charAt(lastPos) - 'z' - 1;
						lastPos++;
					}
					if (line.charAt(lastPos) == '0' + LineBreakKind.PARAGRAPH.ordinal()) {
						visitor.visitLineBreak(ExtendedLineBreakKind.PARAGRAPH, 0);
					} else if (line.charAt(lastPos) == '0' + LineBreakKind.NEWLINE.ordinal()) {
						visitor.visitLineBreak(ExtendedLineBreakKind.NEWLINE, 0);
					} else if (line.charAt(lastPos) == '0' + LineBreakKind.NEWLINE_WITH_INDENT.ordinal()) {
						visitor.visitLineBreak(ExtendedLineBreakKind.NEWLINE, 1);
					} else {
						visitor.visitLineBreak(ExtendedLineBreakKind.fromChar(line.charAt(lastPos)), indent);
					}
					lastPos++;
					break;
				case 'G':
					lastPos = line.indexOf('>', pos) + 1;
					args = line.substring(pos + 2, lastPos - 1).split(" ");
					char[] strongsPrefixes = new char[args.length];
					char[] strongsSuffixes = null;
					int[] strongs = new int[args.length];
					String[] rmacs = new String[args.length];
					int[] idxs = new int[args.length];
					String[] attributeKeys = new String[args.length];
					String[] attributeValues = new String[args.length];
					int scount = 0, rcount = 0, icount = 0, acount = 0;
					for (int i = 0; i < args.length; i++) {
						if (args[i].equals("@")) {
							strongsSuffixes = new char[args.length];
							Arrays.fill(strongsSuffixes, ' ');
							continue;
						} else if (args[i].startsWith("+")) {
							String[] parts = args[i].substring(1).split("=", 2);
							attributeKeys[acount] = parts[0];
							attributeValues[acount] = parts[1].replace("&g", ">").replace("&a", "&");
							acount++;
							continue;
						}
						String[] parts = args[i].split(":");
						String s = parts[0];
						if (Diffable.parseStrongsSuffix && !s.isEmpty()) {
							char[] prefixSuffixHolder = new char[2];
							strongs[i] = Utils.parseStrongs(s, '?', prefixSuffixHolder);
							if (prefixSuffixHolder[0] != '?') {
								strongsPrefixes[i] = prefixSuffixHolder[0];
							}
							if (prefixSuffixHolder[1] != ' ') {
								if (strongsSuffixes == null) {
									strongsSuffixes = new char[args.length];
									Arrays.fill(strongsSuffixes, ' ');
								}
								strongsSuffixes[i] = prefixSuffixHolder[1];
							}
						} else {
							if (s.matches("[A-Z]?[0-9]+[a-zA-Z]")) {
								if (strongsSuffixes == null) {
									strongsSuffixes = new char[args.length];
									Arrays.fill(strongsSuffixes, ' ');
								}
								strongsSuffixes[i] = s.charAt(s.length() - 1);
								s = s.substring(0, s.length() - 1);
							}
							if (s.matches("[A-Z][0-9]+")) {
								strongsPrefixes[i] = s.charAt(0);
								s = s.substring(1);
							}
							strongs[i] = s.isEmpty() ? -1 : Integer.parseInt(s);
						}
						if (parts.length > 1 && !parts[1].isEmpty())
							rmacs[i] = parts[1];
						else
							rmacs[i] = null;
						if (parts.length > 2)
							idxs[i] = Integer.parseInt(parts[2]);
						else
							idxs[i] = -1;
						if (strongs[i] != -1)
							scount = i + 1;
						if (rmacs[i] != null)
							rcount = i + 1;
						if (idxs[i] != -1)
							icount = i + 1;
					}
					strongsPrefixes = scount == 0 || strongsPrefixes[0] == 0 ? null : Arrays.copyOf(strongsPrefixes, scount);
					strongsSuffixes = scount == 0 || strongsSuffixes == null ? null : Arrays.copyOf(strongsSuffixes, scount);
					strongs = scount == 0 ? null : Arrays.copyOf(strongs, scount);
					rmacs = rcount == 0 ? null : Arrays.copyOf(rmacs, rcount);
					idxs = icount == 0 ? null : Arrays.copyOf(idxs, icount);
					attributeKeys = acount == 0 ? null : Arrays.copyOf(attributeKeys, acount);
					attributeValues = acount == 0 ? null : Arrays.copyOf(attributeValues, acount);
					visitorStack.add(visitor);
					visitor = visitor.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmacs, idxs, attributeKeys, attributeValues);
					break;
				case 'D':
					lastPos = line.indexOf('>', pos) + 1;
					args = line.substring(pos + 2, lastPos - 1).split(" ");
					visitorStack.add(visitor);
					visitor = visitor.visitDictionaryEntry(args[0], args[1]);
					break;
				case 'O':
					lastPos = line.indexOf('>', pos) + 1;
					args = line.substring(pos + 2, lastPos - 1).split(",");
					visitorStack.add(visitor);
					visitor = visitor.visitVariationText(args);
					break;
				case 'R':
					lastPos = line.indexOf(line.charAt(pos + 3), pos + 4) + 1;
					visitor.visitRawHTML(RawHTMLMode.values()[line.charAt(pos + 2) - '0'], line.substring(pos + 4, lastPos - 1));
					break;
				case 'S':
					lastPos = line.indexOf('>', pos) + 1;
					visitorStack.add(visitor);
					visitor = visitor.visitSpeaker(line.substring(pos + 2, lastPos - 1));
					break;
				case 'L':
					lastPos = line.indexOf('>', pos) + 1;
					visitorStack.add(visitor);
					visitor = visitor.visitHyperlink(HyperlinkType.values()[line.charAt(pos + 2) - '0'], line.substring(pos + 3, lastPos - 1));
					break;
				case 'E':
					lastPos = line.indexOf('>', pos) + 1;
					args = line.substring(pos + 3, lastPos - 1).split(" ");
					visitorStack.add(visitor);
					visitor = visitor.visitExtraAttribute(ExtraAttributePriority.values()[line.charAt(pos + 2) - '0'], args[0], args[1], args[2]);
					break;
				case 'X':
					lastPos = line.indexOf('>', pos) + 1;
					args = line.substring(pos + 2, lastPos - 1).split(" ");
					visitorStack.add(visitor);
					String firstBookAbbr, lastBookAbbr;
					BookID firstBookID, lastBookID;
					if (args[0].contains(":")) {
						String[] parts = args[0].split(":", 2);
						firstBookAbbr = parts[0];
						lastBookAbbr = parts[1];
						parts = args[1].split(":", 2);
						firstBookID = BookID.fromOsisId(parts[0]);
						lastBookID = BookID.fromOsisId(parts[1]);
					} else {
						firstBookAbbr = lastBookAbbr = args[0];
						firstBookID = lastBookID = BookID.fromOsisId(args[1]);
					}
					visitor = visitor.visitCrossReference(firstBookAbbr, firstBookID, Integer.parseInt(args[2]), args[3], lastBookAbbr, lastBookID, Integer.parseInt(args[4]), args[5]);
					break;
				default:
					throw new IOException("Unknown tag: " + ch + " in " + line);
				}
			}
			pos = line.indexOf('<', lastPos);
		}
		if (lastPos < line.length())
			visitor.visitText(line.substring(lastPos));
		if (visitorStack.size() > 0)
			throw new RuntimeException("Unclosed tags: " + line);
	}

	private static class CompactVisitor implements Visitor<IOException> {

		private final Writer w;
		private final boolean writeSuffix;
		private final CompactVisitor childVisitor;

		private CompactVisitor(Writer w, boolean writeSuffix) {
			this.w = w;
			this.writeSuffix = writeSuffix;
			childVisitor = writeSuffix ? this : new CompactVisitor(w, true);
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			return 0;
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			w.write("<H" + depth);
			return childVisitor;
		}

		@Override
		public void visitStart() throws IOException {
		}

		@Override
		public void visitText(String text) throws IOException {
			w.write(text.replace("<", "<<"));
		}

		@Override
		public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
			w.write(ofCrossReferences ? "<Y" : "<F");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws IOException {
			String bookID = firstBook.getOsisID();
			if (firstBook != lastBook) {
				bookID += ":" + lastBook.getOsisID();
				firstBookAbbr += ":" + lastBookAbbr;
			}
			w.write("<X" + firstBookAbbr + " " + bookID + " " + firstChapter + " " + firstVerse + " " + lastChapter + " " + lastVerse + ">");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			w.write("<" + kind.getCode());
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			w.write("<C" + css + ">");
			return childVisitor;
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			w.write("<V");
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws IOException {
			if (kind == ExtendedLineBreakKind.PARAGRAPH && indent == 0) {
				w.write("<B" + LineBreakKind.PARAGRAPH.ordinal());
			} else if (kind == ExtendedLineBreakKind.NEWLINE && indent == 0) {
				w.write("<B" + LineBreakKind.NEWLINE.ordinal());
			} else if (kind == ExtendedLineBreakKind.NEWLINE && indent == 1) {
				w.write("<B" + LineBreakKind.NEWLINE_WITH_INDENT.ordinal());
			} else {
				w.write("<B");
				if (indent < 0) {
					w.write('z' + indent + 1);
				} else if (indent > 0) {
					w.write('a' + indent - 1);
				}
				w.write(kind.getCode());
			}
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws IOException {
			w.write("<G");
			int max = Math.max(Math.max(strongs == null ? 0 : strongs.length, rmac == null ? 0 : rmac.length), sourceIndices == null ? 0 : sourceIndices.length);
			boolean hasSuffix = false;
			for (int i = 0; i < max; i++) {
				w.write(i > 0 ? " " : "");
				boolean r = rmac != null && i < rmac.length;
				boolean si = sourceIndices != null && i < sourceIndices.length;
				if (Diffable.writeStrongsSuffix && strongsPrefixes != null && strongs != null) {
					w.write(Utils.formatStrongs(false, i, strongsPrefixes, strongs, strongsSuffixes, ""));
				} else {
					if (strongsPrefixes != null && i < strongsPrefixes.length)
						w.write("" + strongsPrefixes[i]);
					if (strongs != null && i < strongs.length)
						w.write("" + strongs[i]);
					if (strongsSuffixes != null && i < strongsSuffixes.length && strongsSuffixes[i] != ' ') {
						w.write("" + strongsSuffixes[i]);
						hasSuffix = true;
					}
				}
				if (r || si) {
					w.write(":");
					if (r)
						w.write(rmac[i]);
					if (si)
						w.write(":" + sourceIndices[i]);
				}
			}
			if (strongsSuffixes != null && !hasSuffix) {
				w.write(" @");
			}
			if (attributeKeys != null) {
				for (int i = 0; i < attributeKeys.length; i++) {
					w.write(" +" + attributeKeys[i] + "=" + attributeValues[i].replace("&", "&a").replace(">", "&g"));
				}
			}
			w.write(">");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			w.write("<D" + dictionary + " " + entry + ">");
			return childVisitor;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
			char marker = '!';
			while (raw.indexOf(marker) != -1)
				marker++;
			w.write("<R" + mode.ordinal() + marker + raw + marker);
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			w.write("<O");
			for (int i = 0; i < variations.length; i++) {
				if (i != 0)
					w.write(',');
				w.write(variations[i]);
			}
			w.write(">");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitSpeaker(String labelOrStrongs) throws IOException {
			w.write("<S" + labelOrStrongs + ">");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitHyperlink(HyperlinkType type, String target) throws IOException {
			w.write("<L" + type.ordinal() + target + ">");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			w.write("<E" + prio.ordinal() + category + " " + key + " " + value + ">");
			return childVisitor;
		}

		@Override
		public boolean visitEnd() throws IOException {
			if (writeSuffix)
				w.write("</");
			return false;
		}
	}
}
