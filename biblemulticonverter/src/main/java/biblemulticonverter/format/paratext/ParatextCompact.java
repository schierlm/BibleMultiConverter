package biblemulticonverter.format.paratext;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import biblemulticonverter.format.paratext.ParatextBook.ChapterEnd;
import biblemulticonverter.format.paratext.ParatextBook.ChapterStart;
import biblemulticonverter.format.paratext.ParatextBook.Figure;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentPart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextBook.PeripheralStart;
import biblemulticonverter.format.paratext.ParatextBook.SidebarEnd;
import biblemulticonverter.format.paratext.ParatextBook.SidebarStart;
import biblemulticonverter.format.paratext.ParatextBook.TableCellStart;
import biblemulticonverter.format.paratext.ParatextBook.VerseEnd;
import biblemulticonverter.format.paratext.ParatextBook.VerseStart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.CustomMarkup;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Milestone;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.SpecialSpace;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Text;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;

/**
 * Simple importer and exporter that dumps the internal Paratext format to
 * compact plain text.
 */
public class ParatextCompact extends AbstractParatextFormat {

	public static final String[] HELP_TEXT = {
			"Dump a Paratext bible to compact plain text",
			"",
			"Usage (export): ParatextDump <OutputFile>",
			"",
			"Point the importer to .txt files, not to directories!",
	};

	public ParatextCompact() {
		super("ParatextCompact");
	}

	private static final AutoClosingFormattingKind[] uppercaseAK = new AutoClosingFormattingKind[26];

	private static final ParagraphKind[] uppercasePK = new ParagraphKind[26];

	private static <T> void fillShortcut(char key, T[] array, T value) {
		if (array[key - 'A'] != null)
			throw new IllegalStateException("Duplicate assignment for " + key);
		array[key - 'A'] = value;
	}

	static {
		fillShortcut('A', uppercasePK, ParagraphKind.PARAGRAPH_QA);
		fillShortcut('E', uppercasePK, ParagraphKind.PARAGRAPH_CENTERED);
		fillShortcut('F', uppercasePK, ParagraphKind.MAJOR_SECTION);
		fillShortcut('G', uppercasePK, ParagraphKind.MAJOR_TITLE);
		fillShortcut('H', uppercasePK, ParagraphKind.PARAGRAPH_HANGING);
		fillShortcut('I', uppercasePK, ParagraphKind.INTRO_PARAGRAPH_P);
		fillShortcut('J', uppercasePK, ParagraphKind.CHAPTER_DESCRIPTION);
		fillShortcut('K', uppercasePK, ParagraphKind.PAGE_BREAK);
		fillShortcut('L', uppercasePK, ParagraphKind.PARAGRAPH_LI);
		fillShortcut('N', uppercasePK, ParagraphKind.NO_BREAK_AT_START_OF_CHAPTER);
		fillShortcut('O', uppercasePK, ParagraphKind.INTRO_SECTION);
		fillShortcut('T', uppercasePK, ParagraphKind.TABLE_ROW);
		fillShortcut('U', uppercasePK, ParagraphKind.INTRO_PARAGRAPH_Q);
		fillShortcut('W', uppercasePK, ParagraphKind.PARAGRAPH_RIGHT);
		fillShortcut('X', uppercasePK, ParagraphKind.PARAGRAPH_PI);
		fillShortcut('Y', uppercasePK, ParagraphKind.PARAGRAPH_MI);
		fillShortcut('Z', uppercasePK, ParagraphKind.MAJOR_SECTION_REFERENCE);

		fillShortcut('A', uppercaseAK, AutoClosingFormattingKind.DEUTEROCANONICAL_CONTENT);
		fillShortcut('B', uppercaseAK, AutoClosingFormattingKind.BOLD);
		fillShortcut('C', uppercaseAK, AutoClosingFormattingKind.SMALL_CAPS);
		fillShortcut('D', uppercaseAK, AutoClosingFormattingKind.NAME_OF_DEITY);
		fillShortcut('E', uppercaseAK, AutoClosingFormattingKind.QUOTED_TEXT);
		fillShortcut('F', uppercaseAK, AutoClosingFormattingKind.QUOTATION_REFERENCE);
		fillShortcut('G', uppercaseAK, AutoClosingFormattingKind.GREEK_WORD);
		fillShortcut('H', uppercaseAK, AutoClosingFormattingKind.HEBREW_WORD);
		fillShortcut('I', uppercaseAK, AutoClosingFormattingKind.ITALIC);
		fillShortcut('J', uppercaseAK, AutoClosingFormattingKind.XREF_QUOTATION);
		fillShortcut('L', uppercaseAK, AutoClosingFormattingKind.TRANSLITERATED);
		fillShortcut('M', uppercaseAK, AutoClosingFormattingKind.FOOTNOTE_MARK);
		fillShortcut('N', uppercaseAK, AutoClosingFormattingKind.NORMAL);
		fillShortcut('O', uppercaseAK, AutoClosingFormattingKind.XREF_ORIGIN);
		fillShortcut('P', uppercaseAK, AutoClosingFormattingKind.PROPER_NAME);
		fillShortcut('Q', uppercaseAK, AutoClosingFormattingKind.FOOTNOTE_QUOTATION);
		fillShortcut('R', uppercaseAK, AutoClosingFormattingKind.FOOTNOTE_REFERENCE);
		fillShortcut('S', uppercaseAK, AutoClosingFormattingKind.SELAH);
		fillShortcut('T', uppercaseAK, AutoClosingFormattingKind.FOOTNOTE_TEXT);
		fillShortcut('U', uppercaseAK, AutoClosingFormattingKind.XREF_TARGET_REFERENCES);
		fillShortcut('V', uppercaseAK, AutoClosingFormattingKind.FOOTNOTE_VERSE_NUMBER);
		fillShortcut('X', uppercaseAK, AutoClosingFormattingKind.XREF_KEYWORD);
		fillShortcut('Y', uppercaseAK, AutoClosingFormattingKind.FOOTNOTE_KEYWORD);
		fillShortcut('Z', uppercaseAK, AutoClosingFormattingKind.QUOTED_BOOK_TITLE);

		for (ParagraphKind pk : ParagraphKind.values()) {
			if (pk.getTag().length() == 1) {
				fillShortcut(pk.getTag().toUpperCase().charAt(0), uppercasePK, pk);
			}
		}
		for (AutoClosingFormattingKind ak : AutoClosingFormattingKind.values()) {
			if (ak.getTag().length() == 1) {
				fillShortcut(ak.getTag().toUpperCase().charAt(0), uppercaseAK, ak);
			}
		}
	}

	private static String e(String original) {
		return original.replace("\\", "\\/");
	}

	private static String u(String original) {
		return original.replace("\\/", "\\");
	}

	@Override
	protected List<ParatextBook> doImportAllBooks(File inputFile) throws Exception {
		List<ParatextBook> result = new ArrayList<ParatextBook>();
		Map<String, ParagraphKind> paraMap = new HashMap<>();
		for (int i = 0; i < uppercasePK.length; i++) {
			if (uppercasePK[i] == null)
				continue;
			paraMap.put("" + (char) ('A' + i), uppercasePK[i]);
			for (int j = 1; j < 9; j++) {
				ParagraphKind sub = ParagraphKind.getFromTag(uppercasePK[i].getTag() + "" + j);
				if (sub == null)
					break;
				paraMap.put(j + "" + (char) ('A' + i), sub);
			}
		}
		Map<String, AutoClosingFormattingKind> formattingMap = new HashMap<>();
		for (int i = 0; i < uppercaseAK.length; i++) {
			formattingMap.put("" + (char) ('A' + i), uppercaseAK[i]);
		}
		for (AutoClosingFormattingKind ak : AutoClosingFormattingKind.values()) {
			String prefix = "", tag = ak.getTag();
			formattingMap.put(prefix + tag.substring(0, tag.length() - 1) + tag.substring(tag.length() - 1).toUpperCase(), ak);
		}
		for (ParagraphKind pk : ParagraphKind.values()) {
			String prefix = "", tag = pk.getTag();
			if (pk.getTag().matches(".*[0-9]")) {
				prefix = tag.substring(tag.length() - 1);
				tag = tag.substring(0, tag.length() - 1);
			}
			paraMap.put(prefix + tag.substring(0, tag.length() - 1) + tag.substring(tag.length() - 1).toUpperCase(), pk);
		}
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
			String line = br.readLine();
			if (!line.startsWith("ParatextCompactV1.0:"))
				throw new IOException("Invalid file magic: " + line);
			int offset = "ParatextCompactV1.0:".length();
			while (line != null && offset < line.length()) {
				int[] chapverse = new int[] { 0, -100 };
				ChapterIdentifier openChapter = null;
				VerseIdentifier openVerse = null;
				int pos = line.indexOf("\\>", offset);
				String[] parts = line.substring(offset, pos).split(":", 2);
				offset = pos + 2;
				ParatextBook book = new ParatextBook(ParatextID.fromIdentifier(parts[0]), u(parts[1]));
				result.add(book);
				while (line.startsWith("\\!", offset)) {
					offset += 2;
					pos = line.indexOf("\\>", offset);
					parts = line.substring(offset, pos).split(" ", 2);
					offset = pos + 2;
					book.getAttributes().put(parts[0], u(parts[1]));
				}
				importTableCellOrCharContent(book, line, offset, formattingMap);
				while ((line = br.readLine()) != null) {
					offset = 0;
					if (line.equals("*")) {
						line = br.readLine();
						break;
					} else if (line.startsWith("C") || line.startsWith(":")) {
						boolean open = true;
						if (openChapter != null)
							book.getContent().add(new ChapterEnd(openChapter));
						openChapter = null;
						if (line.startsWith("C")) {
							chapverse[0]++;
							offset = 1;
						} else if (line.startsWith(":*")) {
							open = false;
							offset = 2;
						} else {
							pos = line.indexOf(":", 1);
							chapverse[0] = Integer.parseInt(line.substring(1, pos));
							offset = pos + 1;
						}
						if (open) {
							chapverse[1] = 0;
							openChapter = new ChapterIdentifier(book.getId(), chapverse[0]);
							book.getContent().add(new ChapterStart(openChapter));
						}
					} else if (line.startsWith("V") || line.startsWith("+")) {
						if (openVerse != null)
							book.getContent().add(new VerseEnd(openVerse));
						openVerse = null;
						String verseNum;
						if (line.startsWith("V")) {
							chapverse[1]++;
							verseNum = "" + chapverse[1];
							offset = 1;
						} else if (line.startsWith("+*")) {
							verseNum = null;
							offset = 2;
						} else {
							pos = line.indexOf(":", 1);
							verseNum = line.substring(1, pos);
							offset = pos + 1;
							chapverse[1] = verseNum.matches("[0-9]+") ? Integer.parseInt(verseNum) : -100;
						}
						if (verseNum != null) {
							openVerse = VerseIdentifier.fromStringOrThrow(openChapter + ":" + verseNum);
							book.getContent().add(new VerseStart(openVerse, verseNum));
						}
					} else if (line.startsWith("#")) {
						pos = line.indexOf("\\>");
						book.getContent().add(new ParatextBook.Remark(u(line.substring(1, pos))));
						offset = pos + 2;
					} else if (line.startsWith("<:")) {
						pos = line.indexOf("\\>");
						book.getContent().add(new SidebarStart(pos == 2 ? new String[0] : line.substring(2, pos).split(" ")));
						offset = pos + 2;
					} else if (line.startsWith(">")) {
						book.getContent().add(new SidebarEnd());
						offset = 1;
					} else if (line.startsWith("@")) {
						pos = line.indexOf("\\>");
						String[] titleAndId = line.substring(1, pos).split("\\\\\\|");
						book.getContent().add(new PeripheralStart(u(titleAndId[0]), titleAndId.length == 1 ? null : titleAndId[1]));
						offset = pos + 2;
					} else if (line.startsWith("$")) {
						pos = line.indexOf("\\>");
						Figure f = new Figure(u(line.substring(1, pos)));
						offset = pos + 2;
						while (line.startsWith("!", offset)) {
							pos = line.indexOf("\\>", offset);
							String[] keyVal = line.substring(offset + 1, pos).split(" ", 2);
							f.getAttributes().put(keyVal[0], u(keyVal[1]));
							offset = pos + 2;
						}
						if (!line.startsWith("*", offset))
							throw new IOException("Invalid figure end: " + line.substring(offset));
						offset++;
						book.getContent().add(f);
					} else {
						int kindLen = 1;
						while (!(line.charAt(kindLen - 1) >= 'A' && line.charAt(kindLen - 1) <= 'Z'))
							kindLen++;
						book.getContent().add(new ParagraphStart(paraMap.get(line.substring(0, kindLen))));
						offset = kindLen;
					}
					importTableCellOrCharContent(book, line, offset, formattingMap);
				}
			}
		}
		return result;
	}

	private void importTableCellOrCharContent(ParatextBook book, String line, int offset, Map<String, AutoClosingFormattingKind> formattingMap) throws IOException {
		if (offset == line.length())
			return;
		int pos = line.indexOf("\\#", offset), endPos;
		while (pos != -1) {
			if (pos != offset) {
				ParatextCharacterContent pcc = new ParatextCharacterContent();
				book.getContent().add(pcc);
				endPos = importCharContent(pcc.getContent(), line, offset, formattingMap, '#');
				if (endPos != pos) {
					throw new IOException("Invalid character content: " + line.substring(endPos));
				}
			}
			endPos = pos + 3;
			while (!(line.charAt(endPos - 1) >= 'A' && line.charAt(endPos - 1) <= 'Z'))
				endPos++;
			book.getContent().add(new TableCellStart(line.substring(endPos - 1, endPos).toLowerCase() + line.substring(pos + 2, endPos - 1)));
			offset = endPos;
			pos = line.indexOf("\\#", offset);
		}
		if (offset != line.length()) {
			ParatextCharacterContent pcc = new ParatextCharacterContent();
			book.getContent().add(pcc);
			endPos = importCharContent(pcc.getContent(), line, offset, formattingMap, '\0');
			if (endPos != line.length()) {
				throw new IOException("Invalid character content: " + line.substring(endPos));
			}
		}
	}

	private int importCharContent(List<ParatextCharacterContentPart> target, String line, int offset, Map<String, AutoClosingFormattingKind> formattingMap, char terminator) throws IOException {
		int pos = line.indexOf("\\", offset);
		while (pos != -1) {
			Text text = Text.from(u(line.substring(offset, pos)));
			if (text != null) {
				target.add(text);
			}
			ParatextCharacterContentContainer parent = null;
			if (line.charAt(pos + 1) == terminator) {
				return pos;
			} else if (line.startsWith("_", pos + 1)) {
				offset = pos + 2;
				pos = offset + 1;
				while (!(line.charAt(pos - 1) >= 'A' && line.charAt(pos - 1) <= 'Z'))
					pos++;
				FootnoteXrefKind fk = Objects.requireNonNull(USFM.FOOTNOTE_XREF_TAGS.get(line.substring(pos - 1, pos).toLowerCase() + line.substring(offset, pos - 1)));
				offset = pos;
				String caller = "+";
				if (line.startsWith("+", offset)) {
					pos = line.indexOf("\\>", offset);
					caller = u(line.substring(offset + 1, pos));
					offset = pos + 2;
				}
				List<String> categories = new ArrayList<>();
				while (line.startsWith("!", offset)) {
					pos = line.indexOf("\\>", offset);
					categories.add(u(line.substring(offset + 1, pos)));
					offset = pos + 2;
				}
				if (!line.startsWith("*", offset))
					throw new IOException("Invalid footnote content: " + line.substring(offset));
				offset++;
				FootnoteXref fx = new FootnoteXref(fk, caller, categories.toArray(new String[categories.size()]));
				target.add(fx);
				parent = fx;
			} else if (line.startsWith("@", pos + 1)) {
				offset = pos + 2;
				pos = line.indexOf(" ", offset);
				final Milestone m = new Milestone(Objects.requireNonNull(line.substring(offset, pos)));
				target.add(m);
				offset = pos + 1;
				while (line.startsWith("!", offset)) {
					pos = line.indexOf(" ", offset);
					String key = line.substring(offset + 1, pos);
					offset = pos + 1;
					pos = line.indexOf("\\>", offset);
					m.getAttributes().put(key, u(line.substring(offset, pos)));
					offset = pos + 2;
				}
				if (!line.startsWith("*", offset))
					throw new IOException("Invalid milestone content: " + line.substring(offset));
				offset++;
			} else if (line.startsWith("'", pos + 1)) {
				offset = pos + 2;
				pos = line.indexOf("/", offset);
				String location = line.substring(offset, pos);
				offset = pos + 1;
				pos = line.indexOf("\\>", offset);
				target.add(Reference.parse(location, u(line.substring(offset, pos))));
				offset = pos + 2;
			} else if (line.startsWith("%", pos + 1)) {
				offset = pos + 2;
				pos = line.indexOf("\\>", offset);
				boolean ending = line.charAt(pos - 1) == '*';
				target.add(new CustomMarkup(line.substring(offset, pos - (ending ? 1 : 0)), ending));
				offset = pos + 2;
			} else if (line.startsWith("~", pos + 1)) {
				target.add(new SpecialSpace(true, false));
				offset = pos + 2;
			} else if (line.startsWith("?", pos + 1)) {
				target.add(new SpecialSpace(false, true));
				offset = pos + 2;
			} else {
				offset = pos + 1;
				pos = offset + 1;
				while (!(line.charAt(pos - 1) >= 'A' && line.charAt(pos - 1) <= 'Z'))
					pos++;
				AutoClosingFormatting acf = new AutoClosingFormatting(Objects.requireNonNull(formattingMap.get(line.substring(offset, pos))));
				target.add(acf);
				offset = pos;
				while (line.startsWith("\\!", offset)) {
					pos = line.indexOf(" ", offset);
					String key = line.substring(offset + 2, pos);
					offset = pos + 1;
					pos = line.indexOf("\\>", offset);
					acf.getAttributes().put(key, u(line.substring(offset, pos)));
					offset = pos + 2;
				}
				parent = acf;
			}
			if (parent != null) {
				pos = importCharContent(parent.getContent(), line, offset, formattingMap, '*');
				if (!line.startsWith("\\*", pos))
					throw new IOException("Invalid content after end of character content: " + line.substring(pos));
				offset = pos + 2;
			}
			pos = line.indexOf("\\", offset);
		}
		Text text = Text.from(u(line.substring(offset)));
		if (text != null) {
			target.add(text);
		}
		if (terminator != '\0')
			throw new IOException("Terminator \\" + terminator + " not found");
		return line.length();
	}

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void doExportBooks(List<ParatextBook> books, String... exportArgs) throws Exception {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[0]), StandardCharsets.UTF_8))) {
			bw.write("ParatextCompactV1.0:");
			for (ParatextBook book : books) {
				writeBook(bw, book);
			}
		}
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
			bw.write("ParatextCompactV1.0:");
			writeBook(bw, book);
		}
	}

	private static final Map<AutoClosingFormattingKind, String> formattingTags = new EnumMap<>(AutoClosingFormattingKind.class);
	private static final Map<ParagraphKind, String> paragraphTags = new EnumMap<>(ParagraphKind.class);

	private void writeBook(BufferedWriter bw, ParatextBook book) throws IOException {
		bw.write(book.getId().getIdentifier() + ":" + e(book.getBibleName()) + "\\>");
		for (Map.Entry<String, String> bookattr : book.getAttributes().entrySet()) {
			bw.write("\\!" + bookattr.getKey() + " " + e(bookattr.getValue()) + "\\>");
		}
		int[] chapverse = new int[] { 0, -100 };
		synchronized (ParatextCompact.class) {
			if (paragraphTags.isEmpty()) {
				for (int i = 0; i < uppercasePK.length; i++) {
					if (uppercasePK[i] == null)
						continue;
					paragraphTags.put(uppercasePK[i], "" + (char) ('A' + i));
					for (int j = 1; j < 9; j++) {
						ParagraphKind sub = ParagraphKind.getFromTag(uppercasePK[i].getTag() + "" + j);
						if (sub == null)
							break;
						paragraphTags.put(sub, j + "" + (char) ('A' + i));
					}
				}
				for (ParagraphKind pk : ParagraphKind.values()) {
					if (paragraphTags.containsKey(pk))
						continue;
					String prefix = "", tag = pk.getTag();
					if (pk.getTag().matches(".*[0-9]")) {
						prefix = tag.substring(tag.length() - 1);
						tag = tag.substring(0, tag.length() - 1);
					}
					paragraphTags.put(pk, prefix + tag.substring(0, tag.length() - 1) + tag.substring(tag.length() - 1).toUpperCase());
				}
			}
			if (formattingTags.isEmpty()) {
				for (int i = 0; i < uppercaseAK.length; i++) {
					formattingTags.put(uppercaseAK[i], "" + (char) ('A' + i));
				}
				for (AutoClosingFormattingKind ak : AutoClosingFormattingKind.values()) {
					if (formattingTags.containsKey(ak))
						continue;
					String prefix = "", tag = ak.getTag();
					formattingTags.put(ak, prefix + tag.substring(0, tag.length() - 1) + tag.substring(tag.length() - 1).toUpperCase());
				}
			}
		}
		ParatextBook bb = new ParatextBook(book.getId(), book.getBibleName());
		List<ParatextBookContentPart> content = bb.getContent();
		content.addAll(book.getContent());
		for (int i = 0; i < content.size() - 1; i++) {
			if ((content.get(i) instanceof ChapterEnd && content.get(i + 1) instanceof ChapterStart) || (content.get(i) instanceof VerseEnd && content.get(i + 1) instanceof VerseStart)) {
				content.remove(i);
				i--;
			}
		}
		bb.accept(new ParatextBookContentVisitor<IOException>() {

			@Override
			public void visitChapterStart(ChapterIdentifier location) throws IOException {
				if (chapverse[0] == location.chapter - 1) {
					bw.write("\nC");
				} else {
					bw.write("\n:" + location.chapter + ":");
				}
				chapverse[0] = location.chapter;
				chapverse[1] = 0;
			}

			@Override
			public void visitChapterEnd(ChapterIdentifier location) throws IOException {
				bw.write("\n:*");
			}

			@Override
			public void visitRemark(String content) throws IOException {
				bw.write("\n#" + e(content) + "\\>");
			}

			@Override
			public void visitParagraphStart(ParagraphKind kind) throws IOException {
				bw.write("\n" + paragraphTags.get(kind));
			}

			@Override
			public void visitTableCellStart(String tag) throws IOException {
				bw.write("\\#" + tag.substring(1) + tag.substring(0, 1).toUpperCase());
			}

			@Override
			public void visitSidebarStart(String[] categories) throws IOException {
				bw.write("\n<:" + String.join(" ", categories) + "\\>");
			}

			@Override
			public void visitSidebarEnd() throws IOException {
				bw.write("\n>");
			}

			@Override
			public void visitPeripheralStart(String title, String id) throws IOException {
				bw.write("\n@" + e(title) + (id == null ? "" : "\\|" + id) + "\\>");
			}

			@Override
			public void visitVerseStart(VerseIdentifier location, String verseNumber) throws IOException {
				if (String.valueOf(chapverse[1] + 1).equals(verseNumber)) {
					bw.write("\nV");
					chapverse[1]++;
				} else {
					bw.write("\n+" + verseNumber + ":");
					chapverse[1] = verseNumber.matches("[0-9]+") ? Integer.parseInt(verseNumber) : -100;
				}
			}

			@Override
			public void visitVerseEnd(VerseIdentifier location) throws IOException {
				bw.write("\n+*");
			}

			@Override
			public void visitFigure(String caption, Map<String, String> attributes) throws IOException {
				bw.write("\n$" + e(caption) + "\\>");
				for (Map.Entry<String, String> attr : attributes.entrySet()) {
					bw.write("!" + attr.getKey() + " " + e(attr.getValue()) + "\\>");
				}
				bw.write("*");
			}

			@Override
			public void visitParatextCharacterContent(ParatextCharacterContent content) throws IOException {
				content.accept(new ParatextDumpCharacterContentVisitor(bw, ""));
			}
		});
		bw.write("\n*\n");
	}

	public class ParatextDumpCharacterContentVisitor implements ParatextCharacterContentVisitor<IOException> {

		private final BufferedWriter bw;
		private final String suffix;

		public ParatextDumpCharacterContentVisitor(BufferedWriter bw, String suffix) {
			this.bw = bw;
			this.suffix = suffix;
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) throws IOException {
			bw.write("\\_" + kind.getTag().substring(1) + kind.getTag().substring(0, 1).toUpperCase());
			if (!caller.equals("+")) {
				bw.write("+" + e(caller) + "\\>");
			}
			for (String cat : categories) {
				bw.write("!" + e(cat) + "\\>");
			}
			bw.write("*");
			return new ParatextDumpCharacterContentVisitor(bw, "\\*");
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws IOException {
			bw.write("\\" + formattingTags.get(kind));
			for (Map.Entry<String, String> attr : attributes.entrySet()) {
				bw.write("\\!" + attr.getKey() + " " + e(attr.getValue()) + "\\>");
			}
			return new ParatextDumpCharacterContentVisitor(bw, "\\*");
		}

		@Override
		public void visitMilestone(String tag, Map<String, String> attributes) throws IOException {
			bw.write("\\@" + tag + " ");
			for (Map.Entry<String, String> attr : attributes.entrySet()) {
				bw.write("!" + attr.getKey() + " " + e(attr.getValue()) + "\\>");
			}
			bw.write("*");
		}

		@Override
		public void visitReference(Reference reference) throws IOException {
			bw.write("\\'" + reference.toString() + "/" + e(reference.getContent()) + "\\>");
		}

		@Override
		public void visitCustomMarkup(String tag, boolean ending) throws IOException {
			bw.write("\\%" + tag + (ending ? "*" : "") + "\\>");
		}

		@Override
		public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws IOException {
			bw.write(nonBreakSpace ? "\\~" : "\\?");
		}

		@Override
		public void visitText(String text) throws IOException {
			bw.write(e(text));
		}

		@Override
		public void visitEnd() throws IOException {
			bw.write(suffix);
		}
	}
}
