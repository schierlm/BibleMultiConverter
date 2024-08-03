package biblemulticonverter.format.paratext;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import biblemulticonverter.format.paratext.ParatextBook.ChapterStart;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextBook.PeripheralStart;
import biblemulticonverter.format.paratext.ParatextBook.Remark;
import biblemulticonverter.format.paratext.ParatextBook.SidebarEnd;
import biblemulticonverter.format.paratext.ParatextBook.SidebarStart;
import biblemulticonverter.format.paratext.ParatextBook.TableCellStart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.CustomMarkup;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Figure;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Milestone;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.SpecialSpace;
import biblemulticonverter.format.paratext.ParatextCharacterContent.VerseStart;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import biblemulticonverter.format.paratext.model.Version;
import biblemulticonverter.format.paratext.utilities.ImportUtilities;
import biblemulticonverter.format.paratext.utilities.StandardExportWarningMessages;
import biblemulticonverter.format.paratext.utilities.TextUtilities;

/**
 * Importer and exporter for USFM.
 */
public class USFM extends AbstractParatextFormat {

	public static final String[] HELP_TEXT = {
			"Bible format used by Paratext",
			"",
			"Usage (export): USFM <outdir> <filenamepattern>",
			"",
			"Point the importer to a directory that contains the .usfm files.",
			"",
			"When exporting, you need to give a file name pattern. You can use # for ",
			"the book number and * for the book name."
	};

	public static final Set<String> KNOWN_CHARACTER_TAGS = new HashSet<>(Arrays.asList("f", "fe", "x", "ef"));

	public static final Map<String, ParagraphKind> PARAGRAPH_TAGS = ParagraphKind.allTags();
	public static final Map<String, FootnoteXrefKind> FOOTNOTE_XREF_TAGS = FootnoteXrefKind.allTags();
	public static final Map<String, AutoClosingFormattingKind> AUTO_CLOSING_TAGS = AutoClosingFormattingKind.allTags();

	private final boolean preserveSpacesAtEndOfLines;

	public USFM() {
		this(Boolean.getBoolean("biblemulticonverter.paratext.usfm.preserveSpacesAtEndOfLines"));
	}

	/**
	 * @param preserveSpacesAtEndOfLines when set to true the USFM normalization will preserve single spaces that might
	 *                                   be present at the end of a line, which would otherwise be removed/ignored.
	 */
	public USFM(boolean preserveSpacesAtEndOfLines) {
		super("USFM");
		this.preserveSpacesAtEndOfLines = preserveSpacesAtEndOfLines;
	}

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		return doImportBook(inputFile, StandardCharsets.UTF_8);
	}

	private ParatextBook doImportBook(File inputFile, Charset charset) throws Exception {
		KNOWN_CHARACTER_TAGS.addAll(AUTO_CLOSING_TAGS.keySet());
		if (!inputFile.getName().toLowerCase().endsWith(".usfm") && !inputFile.getName().toLowerCase().endsWith(".sfm"))
			return null;
		String data = TextUtilities.usfmWhitespaceNormalization(new String(Files.readAllBytes(inputFile.toPath()), charset), preserveSpacesAtEndOfLines) + "\\$EOF$";
		if (!data.startsWith("\\id ")) {
			System.out.println("WARNING: Skipping malformed file " + inputFile);
			return null;
		}
		int startPos = data.indexOf("\\", 2);
		int finalPos = data.length() - "\\$EOF$".length();
		String[] idParts = data.substring(4, startPos).trim().split(" ", 2);
		ParatextID id = ParatextID.fromIdentifier(idParts[0].toUpperCase());
		if (id == null) {
			System.out.println("WARNING: Skipping book with unknown ID: " + idParts[0]);
			return null;
		}
		ParatextBook result = new ParatextBook(id, idParts.length == 1 ? "" : idParts[1]);
		List<ParatextCharacterContentContainer> containerStack = new ArrayList<>();
		boolean ignoreAutoClosingTags = Boolean.getBoolean("biblemulticonverter.usfm.ignoreautoclosingtags");
		String verseSuffixLetters = System.getProperty("biblemulticonverter.usfm.versesuffixletters", "");
		int escapePos = verseSuffixLetters.indexOf("\\u");
		while (escapePos != -1) {
			verseSuffixLetters = verseSuffixLetters.substring(0, escapePos) + (char) Integer.parseInt(verseSuffixLetters.substring(escapePos + 2, escapePos + 6), 16) + verseSuffixLetters.substring(escapePos + 6);
			escapePos = verseSuffixLetters.indexOf("\\u");
		}
		Map<Character, Character> verseSuffixMap = new HashMap<>();
		for (int i = 0; i < verseSuffixLetters.length(); i++) {
			if (i >= 26)
				throw new IllegalStateException("More than 26 verse suffix letters defined");
			verseSuffixMap.put(verseSuffixLetters.charAt(i), (char)('a'+i));
		}

		VerseStart openVerse = null;
		ChapterStart openChapter = null;
		boolean parseAttributes = false;

		while (startPos < finalPos) {
			if (data.charAt(startPos) != '\\')
				throw new IllegalStateException();
			int pos = data.indexOf('\\', startPos + 1);
			String textPart = data.substring(startPos + 1, pos);
			startPos = pos;
			pos = Math.min(textPart.length(), 1 + Math.min((textPart + " ").indexOf(' '), (textPart + "*").indexOf('*')));
			String origTag = textPart.substring(0, pos).trim();
			String tag = origTag.toLowerCase();
			textPart = textPart.substring(pos);
			if (textPart.endsWith(" ")) {
				String nextTag = data.substring(startPos + 1, Math.min(data.length(), startPos + 10)) + " *\\";
				pos = Math.min(nextTag.indexOf('\\'), Math.min(nextTag.indexOf(' '), nextTag.indexOf('*')));
				if (!KNOWN_CHARACTER_TAGS.contains(nextTag.substring(0, pos)) && !nextTag.startsWith("z")) {
					textPart = textPart.substring(0, textPart.length() - 1);
				}
			}
			if (containerStack.isEmpty() && (AUTO_CLOSING_TAGS.containsKey(tag) || tag.equals("v") || FOOTNOTE_XREF_TAGS.containsKey(tag))) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				result.getContent().add(container);
				containerStack.add(container);
			}
			boolean closeCharacterAttributes = false;
			if (PARAGRAPH_TAGS.containsKey(tag)) {

				ParagraphKind kind = PARAGRAPH_TAGS.get(tag);
				//if (kind.getCategory() != ParatextBook.ParagraphKindCategory.TEXT) {
				// Close any open verse
				//	openVerse = closeOpenVerse(result, openVerse, false);
				//}
				result.getContent().add(new ParagraphStart(kind));
				closeCharacterAttributes = true;
			} else if (tag.startsWith("z") && tag.endsWith("*")) {
				// special case to avoid unwinding container stack
				containerStack.get(containerStack.size() - 1).getContent().add(new CustomMarkup(origTag.substring(0, origTag.length() - 1), true));
			} else if (tag.endsWith("*")) {
				String rawTag = tag.substring(0, tag.length() - 1);
				while (!containerStack.isEmpty() && containerStack.get(containerStack.size() - 1) instanceof AutoClosingFormatting) {
					AutoClosingFormatting acc = (AutoClosingFormatting) containerStack.get(containerStack.size() - 1);
					if (acc.getUsedTag().equals(rawTag))
						break;
					containerStack.remove(containerStack.size() - 1);
				}
				boolean found = false;
				if (AUTO_CLOSING_TAGS.containsKey(rawTag)) {
					if (!containerStack.isEmpty() && containerStack.get(containerStack.size() - 1) instanceof AutoClosingFormatting) {
						AutoClosingFormatting acc = (AutoClosingFormatting) containerStack.get(containerStack.size() - 1);
						found = acc.getUsedTag().equals(rawTag);
					}
				} else if (FOOTNOTE_XREF_TAGS.containsKey(rawTag)) {
					if (!containerStack.isEmpty() && containerStack.get(containerStack.size() - 1) instanceof FootnoteXref) {
						FootnoteXref fx = (FootnoteXref) containerStack.get(containerStack.size() - 1);
						found = fx.getKind().getTag().equals(rawTag);
					}
				} else {
					System.out.println("WARNING: Skipping unknown end tag \\" + tag);
				}
				if (found) {
					containerStack.remove(containerStack.size() - 1);
				} else {
					System.out.println("WARNING: Skipping mismatched end tag \\" + tag);
				}
			} else if (AUTO_CLOSING_TAGS.containsKey(tag)) {
				if (!tag.startsWith("+") && !ignoreAutoClosingTags) {
					while (!containerStack.isEmpty() && containerStack.get(containerStack.size() - 1) instanceof AutoClosingFormatting) {
						containerStack.remove(containerStack.size() - 1);
					}
				}
				AutoClosingFormatting nextContainer = new AutoClosingFormatting(AUTO_CLOSING_TAGS.get(tag));
				containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
				if (tag.startsWith("+")) {
					nextContainer = new NestedAutoClosingFormatting(nextContainer);
				}
				containerStack.add(nextContainer);
			} else if (tag.equals("fig")) {
				if (!ignoreAutoClosingTags) {
					while (!containerStack.isEmpty() && containerStack.get(containerStack.size() - 1) instanceof AutoClosingFormatting) {
						containerStack.remove(containerStack.size() - 1);
					}
				}
				Figure fig;
				String[] legacyParts = textPart.split("\\|");
				if (legacyParts.length == 7) {
					 fig = new Figure(legacyParts[5].trim());
					 for (int i=0; i<5; i++) {
						 if (!legacyParts[i].trim().isEmpty())
							 fig.getAttributes().put(Figure.FIGURE_PROVIDED_ATTRIBUTES[i], legacyParts[i].trim());
					 }
					 if (!legacyParts[6].trim().isEmpty())
						 fig.getAttributes().put("ref", legacyParts[6].trim());
				} else {
					pos = textPart.lastIndexOf("|");
					fig = new Figure(textPart.substring(0, pos).trim());
					parseAttributeList(textPart.substring(pos + 1).trim(), fig.getAttributes());
				}
				containerStack.get(containerStack.size() - 1).getContent().add(fig);
				textPart = "";
				if (data.startsWith("\\fig*", startPos)) {
					pos = data.indexOf('\\', startPos + 5);
					textPart = data.substring(startPos + 5, pos);
					startPos = pos;
					if (textPart.endsWith(" ")) {
						String nextTag = data.substring(startPos + 1, Math.min(data.length(), startPos + 10)) + " *\\";
						pos = Math.min(nextTag.indexOf('\\'), Math.min(nextTag.indexOf(' '), nextTag.indexOf('*')));
						if (!KNOWN_CHARACTER_TAGS.contains(nextTag.substring(0, pos)) && !nextTag.startsWith("z")) {
							textPart = textPart.substring(0, textPart.length() - 1);
						}
					}
				} else if (data.startsWith("\\+", startPos)) {
					System.out.println("WARNING: Nested tags inside figure are not supported!");
				}
			} else if (tag.equals("v")) {
				ImportUtilities.closeOpenVerse(result, openVerse);

				String[] parts = textPart.split(" ", 2);

				// remove RIGHT-TO-LEFT mark which is often present in combined verse numbers of Arabic Bibles
				parts[0] = parts[0].replace("\u200F", "");

				// replace verse suffix
				if (!parts[0].isEmpty() && verseSuffixMap.containsKey(parts[0].charAt(parts[0].length()-1))) {
					parts[0] = parts[0].substring(0, parts[0].length()-1) + verseSuffixMap.get(parts[0].charAt(parts[0].length()-1));
				}

				ChapterStart chapter = result.findLastBookContent(ChapterStart.class);
				if (chapter == null) {
					throw new IllegalStateException("Verse \\v found before chapter start milestone");
				}

				// A verse number in USFM 2 may be in the format 6-7, 6a or even 6-7a.
				// Attempt to parse these numbers by first adding the book and chapter and then parsing it as a whole.
				VerseIdentifier location = VerseIdentifier.fromStringOrThrow(openChapter.getLocation() + ":" + parts[0]);

				openVerse = new VerseStart(location, parts[0]);

				containerStack.get(containerStack.size() - 1).getContent().add(openVerse);
				textPart = parts.length == 1 ? "" : parts[1];
			} else if (tag.equals("c")) {

				ImportUtilities.closeOpenVerse(result, openVerse);
				openVerse = null;

				// There is not really a good way to accurately determine where the end of a chapter should be placed
				// based on USFM 2 content. Maybe a title above this chapter marker was already intended to be part of
				// this chapter. This is basically a best guess. This should not really matter when converting from
				// USFM 2 to USX 2 or USFX (which is based on USFM 2), however when up-converting to USX 3 this might
				// lead to unexpected results.
				ImportUtilities.closeOpenChapter(result, openChapter);

				String[] parts = textPart.split(" ", 2);
				if (!parts[0].matches("[0-9]+"))
					throw new NumberFormatException("Invalid chapter number in \\c " + textPart);
				openChapter = new ChapterStart(new ChapterIdentifier(id, Integer.parseInt(parts[0])));
				result.getContent().add(openChapter);
				closeCharacterAttributes = true;
				textPart = parts.length == 1 ? "" : parts[1];
			} else if (tag.matches(TableCellStart.TABLE_CELL_TAG_REGEX)) {
				result.getContent().add(new TableCellStart(tag));
				closeCharacterAttributes = true;
			} else if (FOOTNOTE_XREF_TAGS.containsKey(tag)) {
				String[] parts = textPart.split(" ", 2);
				List<String> categories = new ArrayList<>();
				while (data.startsWith("\\cat ", startPos)) {
					pos = data.indexOf("\\", startPos + 5);
					categories.add(data.substring(startPos + 5, pos).trim());
					if (data.startsWith("\\cat* ", pos)) {
						pos += 6;
					} else if (data.startsWith("\\cat*", pos)) {
						pos += 5;
					}
					startPos = pos;
				}
				FootnoteXref nextContainer = new FootnoteXref(FOOTNOTE_XREF_TAGS.get(tag), parts[0], categories.toArray(new String[0]));
				containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
				containerStack.add(nextContainer);
				textPart = parts.length == 1 ? "" : parts[1];
			} else if (tag.equals("id")) {
				System.out.println("WARNING: Skipping duplicate \\id tag");
				textPart = "";
			} else if (tag.equals("usfm")) {
				parseAttributes = true;
				if (textPart.startsWith("1.") || textPart.startsWith("2.")) {
					System.out.println("WARNING: \\usfm tag declares version " + textPart + " but has been added in version 3.0");
					parseAttributes = false;
				} else if (!textPart.equals("3.0") && !textPart.startsWith("3.0.")) {
					System.out.println("WARNING: \\usfm tag declares version " + textPart + " which is not supported yet");
				}
				textPart = "";
			} else if (tag.equals("ide")) {
				Charset correctCharset;
				try {
					if (textPart.matches("[0-9]+ - .*")) {
						int codepage = Integer.parseInt(textPart.replaceAll(" - .*", ""));
						correctCharset = codepage == 65001 ? StandardCharsets.UTF_8 : Charset.forName("windows-" + codepage);
					} else {
						correctCharset = Charset.forName(textPart);
					}
				} catch (UnsupportedCharsetException | IllegalCharsetNameException ex) {
					System.out.println("WARNING: Unknown charset " + textPart + " specified, falling back to ISO-8859-1");
					correctCharset = StandardCharsets.ISO_8859_1;
				}
				if (!correctCharset.equals(charset)) {
					if (!charset.equals(StandardCharsets.UTF_8)) {
						throw new IOException("Two charsets specified: " + charset + " and " + correctCharset);
					}
					return doImportBook(inputFile, correctCharset);
				}
				textPart = "";
			} else if (tag.equals("esb")) {
				List<String> categories = new ArrayList<>();
				while (data.startsWith("\\cat ", startPos)) {
					pos = data.indexOf("\\", startPos + 5);
					categories.add(data.substring(startPos + 5, pos).trim());
					if (data.startsWith("\\cat* ", pos)) {
						pos += 6;
					} else if (data.startsWith("\\cat*", pos)) {
						pos += 5;
					}
					startPos = pos;
				}
				result.getContent().add(new SidebarStart(categories.toArray(new String[0])));
				textPart = "";
			} else if (tag.equals("esbe")) {
				result.getContent().add(new SidebarEnd());
				textPart = "";
			} else if (tag.equals("periph")) {
				ImportUtilities.closeOpenChapter(result, openChapter);
				openChapter = null;
				if (textPart.contains("|id=\"") && textPart.endsWith("\"")) {
					String[] parts = textPart.substring(0, textPart.length()-1).split(Pattern.quote("|id=\""), 2);
					result.getContent().add(new PeripheralStart(parts[0], parts[1]));
				} else {
					result.getContent().add(new PeripheralStart(textPart, null));
				}
				closeCharacterAttributes = true;
				textPart = "";
			} else if (BOOK_HEADER_ATTRIBUTE_TAGS.contains(tag)) {
				result.getAttributes().put(tag, textPart);
				textPart = "";
			} else if (tag.equals("rem")) {
				if (!result.getContent().isEmpty()) {
					result.getContent().add(new Remark(textPart));
					closeCharacterAttributes = true;
				} else if (result.getAttributes().containsKey(tag)) {
					int number = 2;
					while (result.getAttributes().containsKey(tag + "@" + number))
						number++;
					result.getAttributes().put(tag + "@" + number, textPart);
				} else {
					result.getAttributes().put(tag, textPart);
				}
				textPart = "";
			} else if ((tag.startsWith("z") || tag.matches("qt[1-5]?(-[se])?|ts?(\\-[se])?")) && data.startsWith("\\*", startPos)) {
				Milestone milestone = new Milestone(tag.startsWith("z") ? origTag : tag);
				containerStack.get(containerStack.size() - 1).getContent().add(milestone);
				if (textPart.startsWith("|")) {
					String attList = textPart.substring(1).trim();
					parseAttributeList(attList, milestone.getAttributes());
				} else {
					System.out.println("WARNING: Skipping unsupported milestone content: "+textPart);
				}
				pos = data.indexOf('\\', startPos + 2);
				textPart = data.substring(startPos + 2, pos);
				startPos = pos;
				if (textPart.endsWith(" ")) {
					String nextTag = data.substring(startPos + 1, Math.min(data.length(), startPos + 10)) + " *\\";
					pos = Math.min(nextTag.indexOf('\\'), Math.min(nextTag.indexOf(' '), nextTag.indexOf('*')));
					if (!KNOWN_CHARACTER_TAGS.contains(nextTag.substring(0, pos)) && !nextTag.startsWith("z")) {
						textPart = textPart.substring(0, textPart.length() - 1);
					}
				}
			} else if (tag.startsWith("z")) {
				containerStack.get(containerStack.size() - 1).getContent().add(new CustomMarkup(origTag, false));
			} else {
				System.out.println("WARNING: Skipping unknown tag \\" + tag);
			}
			if (closeCharacterAttributes) {
				containerStack.clear();
			}

			if (!containerStack.isEmpty() && textPart.contains("|") && containerStack.get(containerStack.size() - 1) instanceof AutoClosingFormatting) {
				AutoClosingFormatting nextContainer = (AutoClosingFormatting) containerStack.get(containerStack.size() - 1);
				String nextTag = data.substring(startPos, Math.min(startPos+10, data.length()));
				if ((nextContainer.getKind() == AutoClosingFormattingKind.WORDLIST || parseAttributes) && (nextContainer.getKind().getDefaultAttribute() != null || textPart.matches(".*\\|[^|]*?=[^|]*")) && nextTag.matches("\\\\[^+].*|\\\\[^ *]+\\*.*")) {
					String defaultAttribute = nextContainer.getKind().getDefaultAttribute();
					pos = textPart.lastIndexOf("|");
					String attList = textPart.substring(pos+1).trim();
					textPart = textPart.substring(0, pos);
					if (textPart.endsWith(" ")) {
						textPart = textPart.substring(0, textPart.length() - 1);
					}
					if (attList.contains("=")) {
						parseAttributeList(attList, nextContainer.getAttributes());
					} else {
						nextContainer.getAttributes().put(defaultAttribute, attList);
					}
				}
			}

			List<ParatextCharacterContentPart> texts = new ArrayList<>();
			while (true) {
				int pos1 = textPart.indexOf("~"), pos2 = textPart.indexOf(" // ");
				ParatextCharacterContent.SpecialSpace space;
				if (pos2 != -1 && (pos1 == -1 || pos2 < pos1)) {
					pos1 = pos2;
					pos2 = pos1 + 4;
					space = new SpecialSpace(false, true);
				} else if (pos1 != -1) {
					pos2 = pos1 + 1;
					space = new SpecialSpace(true, false);
				} else {
					break;
				}
				ParatextCharacterContent.Text prefix = ParatextCharacterContent.Text.from(textPart.substring(0, pos1));
				if (prefix != null)
					texts.add(prefix);
				texts.add(space);
				textPart = textPart.substring(pos2);
			}
			ParatextCharacterContent.Text text = ParatextCharacterContent.Text.from(textPart);
			if (text != null) {
				texts.add(text);
			}
			if (!texts.isEmpty()) {
				if (containerStack.isEmpty()) {
					ParatextCharacterContent container = new ParatextCharacterContent();
					containerStack.add(container);
					result.getContent().add(container);
				}
				containerStack.get(containerStack.size() - 1).getContent().addAll(texts);
			}
		}
		ImportUtilities.closeOpenVerse(result, openVerse);
		ImportUtilities.closeOpenChapter(result, openChapter);
		return result;
	}

	private void parseAttributeList(String attList, Map<String, String> target) {
		while (attList.contains("=")) {
			int pos = attList.indexOf('=');
			String key = attList.substring(0, pos).trim();
			attList = attList.substring(pos + 1).trim();
			if (attList.startsWith("\"")) {
				pos = attList.indexOf('"', 1);
				target.put(key, attList.substring(1, pos));
				attList = attList.substring(pos + 1).trim();
			} else {
				target.put(key, attList);
				attList = "";
			}
		}
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
			bw.write("\\id " + book.getId().getIdentifier() + " " + escape(book.getBibleName(), false));
			Version minRequiredVersion  = getMinRequiredVersion(book);
			if (Version.V3.isLowerOrEqualTo(minRequiredVersion)) {
				bw.write("\n\\usfm "+minRequiredVersion.toString());
			}
			bw.write("\n\\ide UTF-8");
			for (Map.Entry<String, String> attr : book.getAttributes().entrySet()) {
				bw.write("\n\\" + attr.getKey().replaceFirst("@[0-9]+$", "") + " " + escape(attr.getValue(), false));
			}
			book.accept(new ParatextBookContentVisitor<IOException>() {

				private final USFMExportContext context = new USFMExportContext(warningLogger);

				@Override
				public void visitChapterStart(ChapterIdentifier location) throws IOException {
					bw.write("\n\\c " + location.chapter);
					context.needSpace = true;
				}

				@Override
				public void visitChapterEnd(ChapterIdentifier location) throws IOException {
					// Chapter end marker is not supported in USFM.
				}

				@Override
				public void visitRemark(String content) throws IOException {
					bw.write("\n\\rem "+content);
					context.needSpace = false;
				}

				@Override
				public void visitParagraphStart(ParagraphKind kind) throws IOException {
					bw.write("\n\\" + kind.getTag());
					context.needSpace = true;
				}

				@Override
				public void visitTableCellStart(String tag) throws IOException {
					if (context.needSpace) {
						bw.write(" ");
					}
					bw.write("\\" + tag);
					context.needSpace = true;
				}

				@Override
				public void visitSidebarStart(String[] categories) throws IOException {
					bw.write("\n\\esb");
					context.needSpace = true;
					for (String cat : categories) {
						if (context.needSpace)
							bw.write(' ');
						context.needSpace = false;
						bw.write("\\cat " + escape(cat, true) + "\\cat*");
					}
				}

				@Override
				public void visitSidebarEnd() throws IOException {
					bw.write("\n\\esbe");
					context.needSpace = true;
				}

				@Override
				public void visitPeripheralStart(String title, String id) throws IOException {
					bw.write("\n\\periph "+title);
					if (id != null) {
						bw.write("|id=\""+id+"\"");
					}
				}

				@Override
				public void visitParatextCharacterContent(ParatextCharacterContent content) throws IOException {
					content.accept(new USFMCharacterContentVisitor(bw, context));
				}

			});
			bw.write('\n');
		}
	}

	private static class VersionHolder {
		Version version = Version.V1;

		void pushVersion(Version v) {
			if (!v.isLowerOrEqualTo(version)) version = v;
		}
	}

	public Version getMinRequiredVersion(ParatextBook book) {
		VersionHolder vh = new VersionHolder();
		for (Map.Entry<String, String> attr : book.getAttributes().entrySet()) {
			vh.pushVersion(ParatextBook.getMinVersionForAttribute(attr.getKey()));
		}

		book.accept(new ParatextBookContentVisitor<RuntimeException>() {

			@Override
			public void visitChapterStart(ChapterIdentifier location) {
			}

			@Override
			public void visitChapterEnd(ChapterIdentifier location) {
			}

			@Override
			public void visitRemark(String content) {
			}

			@Override
			public void visitParagraphStart(ParagraphKind kind) {
				vh.pushVersion(kind.getVersion());
			}

			@Override
			public void visitTableCellStart(String tag) {
			}

			@Override
			public void visitSidebarStart(String[] categories) throws RuntimeException {
				vh.pushVersion(Version.V2_1);
			}

			@Override
			public void visitSidebarEnd() throws RuntimeException {
				vh.pushVersion(Version.V2_1);
			}

			@Override
			public void visitPeripheralStart(String title, String id) throws RuntimeException {
				if (id != null)
					vh.pushVersion(Version.V3);
			}

			@Override
			public void visitParatextCharacterContent(ParatextCharacterContent content) {
				content.accept(new ParatextCharacterContentVisitor<RuntimeException>() {

					@Override
					public void visitVerseStart(VerseIdentifier location, String verseNumber) throws RuntimeException {
					}

					@Override
					public ParatextCharacterContentVisitor<RuntimeException> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) throws RuntimeException {
						if (kind == FootnoteXrefKind.STUDY_EXTENDED_FOOTNOTE) {
							vh.pushVersion(Version.V2_1);
						} else if (kind == FootnoteXrefKind.STUDY_EXTENDED_XREF) {
							vh.pushVersion(Version.V2_3);
						}
						return this;
					}

					@Override
					public ParatextCharacterContentVisitor<RuntimeException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws RuntimeException {
						vh.pushVersion(kind.getVersion());
						if (kind == AutoClosingFormattingKind.WORDLIST) {
							if (attributes.size() > 1 || (attributes.size() == 1 && !attributes.containsKey("lemma"))) {
								vh.pushVersion(Version.V3);
							}
						} else {
							if (!attributes.isEmpty())
								vh.pushVersion(Version.V3);
						}
						return this;
					}

					@Override
					public void visitFigure(String caption, Map<String, String> attributes) throws RuntimeException {
						Set<String> attrKeys = new HashSet<>(attributes.keySet());
						attrKeys.removeAll(Arrays.asList(Figure.FIGURE_PROVIDED_ATTRIBUTES));
						if (!attrKeys.isEmpty())
							vh.pushVersion(Version.V3);
					}

					@Override
					public void visitMilestone(String tag, Map<String, String> attributes) throws RuntimeException {
						vh.pushVersion(Version.V3);
					}

					@Override
					public void visitReference(Reference reference) throws RuntimeException {
					}

					@Override
					public void visitCustomMarkup(String tag, boolean ending) throws RuntimeException {
					}

					@Override
					public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws RuntimeException {
					}

					@Override
					public void visitText(String text) throws RuntimeException {
					}

					@Override
					public void visitEnd() throws RuntimeException {
					}

					@Override
					public void visitVerseEnd(VerseIdentifier verseLocation) throws RuntimeException {
					}
				});
			}
		});
		return vh.version;
	}

	private static String escape(String text, boolean escapePipe) {
		if (escapePipe)
			text = text.replace("|", "\uFF5C");
		return text.replace("\\", "\uFE68");
	}

	private static class USFMExportContext {
		StandardExportWarningMessages logger;
		boolean needSpace = false;

		public USFMExportContext(StandardExportWarningMessages logger) {
			this.logger = logger;
		}
	}

	private static class USFMCharacterContentVisitor implements ParatextCharacterContentVisitor<IOException> {

		private final BufferedWriter bw;
		private final USFMExportContext context;
		private final List<String> suffixStack = new ArrayList<>();

		public USFMCharacterContentVisitor(BufferedWriter bw, USFMExportContext context) {
			this.bw = bw;
			this.context = context;
			pushSuffix("");
		}

		private void pushSuffix(String suffix) {
			suffixStack.add(suffix);
		}

		@Override
		public void visitVerseStart(VerseIdentifier location, String verseNumber) throws IOException {
			bw.write("\n\\v " + verseNumber);
			context.needSpace = true;
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) throws IOException {
			if (context.needSpace) {
				bw.write(" ");
			}
			final String normalizedCaller;
			if(caller == null) {
				context.logger.logFootnoteCallerMissingWarning('+');
				normalizedCaller = "+";
			} else {
				normalizedCaller = caller;
			}
			bw.write("\\" + kind.getTag() + " " + normalizedCaller);
			context.needSpace = true;
			for (String cat : categories) {
				if (context.needSpace)
					bw.write(' ');
				context.needSpace = false;
				bw.write("\\cat " + escape(cat, true) + "\\cat*");
			}
			pushSuffix(kind.getTag());
			return this;
		}

		@Override
		public void visitCustomMarkup(String tag, boolean ending) throws IOException {
			if (context.needSpace) {
				bw.write(" ");
			}
			bw.write("\\" + tag + (ending ? "*" : ""));
			context.needSpace = !ending;
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws IOException {
			if (context.needSpace)
				bw.write(" ");
			AutoClosingFormattingKind lastTag = getLastTag();
			String thisTag = (lastTag != null ? "+" : "") + kind.getTag();
			bw.write("\\" + thisTag);
			context.needSpace = true;
			if (attributes.isEmpty()) {
				pushSuffix(thisTag);
			} else {
				StringBuilder attrs = new StringBuilder("");
				List<String> attributeKeys = new ArrayList<>(attributes.keySet());
				List<String> orderedAttributeKeys = new ArrayList<>();
				if (kind.getProvidedAttributes() != null) {
					orderedAttributeKeys.addAll(Arrays.asList(kind.getProvidedAttributes()));
				}
				orderedAttributeKeys.removeAll(Arrays.asList(AutoClosingFormattingKind.LINKING_ATTRIBUTES));
				orderedAttributeKeys.addAll(Arrays.asList(AutoClosingFormattingKind.LINKING_ATTRIBUTES));
				orderedAttributeKeys.retainAll(attributeKeys);
				attributeKeys.removeAll(orderedAttributeKeys);
				attributeKeys.sort(Comparator.naturalOrder());
				orderedAttributeKeys.addAll(attributeKeys);
				for (String key : orderedAttributeKeys) {
					if (attrs.length() > 0)
						attrs.append(" ");
					attrs.append(key + "=\"" + attributes.get(key) + "\"");
				}
				pushSuffix(thisTag + "\t|" + attrs.toString());
			}
			return this;
		}

		@Override
		public void visitFigure(String caption, Map<String, String> attributes) throws IOException {
			if (context.needSpace)
				bw.write(" ");
			bw.write("\\fig ");
			context.needSpace = false;
			visitText(caption);
			if (!attributes.isEmpty()) {
				StringBuilder attrs = new StringBuilder("");
				List<String> attributeKeys = new ArrayList<>(attributes.keySet());
				List<String> orderedAttributeKeys = new ArrayList<>();
				orderedAttributeKeys.addAll(Arrays.asList(Figure.FIGURE_PROVIDED_ATTRIBUTES));
				orderedAttributeKeys.retainAll(attributeKeys);
				attributeKeys.removeAll(orderedAttributeKeys);
				attributeKeys.sort(Comparator.naturalOrder());
				orderedAttributeKeys.addAll(attributeKeys);
				for (String key : orderedAttributeKeys) {
					if (attrs.length() > 0)
						attrs.append(" ");
					attrs.append(key + "=\"" + attributes.get(key) + "\"");
				}
				bw.write("|" + attrs.toString());
			}
			bw.write("\\fig*");
		}

		@Override
		public void visitMilestone(String tag, Map<String, String> attributes) throws IOException {
			if (context.needSpace)
				bw.write(" ");
			bw.write("\\" + tag+" ");
			if (!attributes.isEmpty()) {
				StringBuilder attrs = new StringBuilder("");
				List<String> orderedAttributeKeys = new ArrayList<>(attributes.keySet());
				orderedAttributeKeys.sort(Comparator.naturalOrder());
				for (String key : orderedAttributeKeys) {
					if (attrs.length() > 0)
						attrs.append(" ");
					attrs.append(key + "=\"" + attributes.get(key) + "\"");
				}
				bw.write("|" + attrs.toString());
			}
			bw.write("\\*");
			context.needSpace = false;
		}

		@Override
		public void visitReference(Reference reference) throws IOException {
			visitText(reference.getContent());
		}

		@Override
		public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws IOException {
			visitText(nonBreakSpace ? "~" : " // ");
		}

		@Override
		public void visitText(String text) throws IOException {
			if (context.needSpace)
				bw.write(" ");
			// context.needSpace = text.endsWith(" ");
			// if (context.needSpace) {
			// 	text = text.substring(0, text.length() - 1);
			// }
			AutoClosingFormattingKind lastTag = getLastTag();
			bw.write(escape(text, lastTag != null && (lastTag.getProvidedAttributes() != null)));
			context.needSpace = false;
		}

		@Override
		public void visitEnd() throws IOException {
			String suffix = suffixStack.remove(suffixStack.size() - 1);
			if (!suffix.isEmpty()) {
				if (context.needSpace)
					bw.write(" ");
				if (suffix.contains("\t")) {
					String[] parts = suffix.split("\t", 2);
					bw.write(parts[1]);
					suffix = parts[0];
				}
				bw.write("\\" + suffix + "*");
				context.needSpace = false;
			}
		}

		@Override
		public void visitVerseEnd(VerseIdentifier verse) throws IOException {
			// USFM 2.x does not support verse end milestones, hence we don't add them.
		}

		private AutoClosingFormattingKind getLastTag() {
			if (suffixStack.isEmpty())
				return null;
			String suffix = suffixStack.get(suffixStack.size() - 1);
			if (suffix.isEmpty())
				return null;
			if (suffix.contains("\t"))
				suffix = suffix.split("\t", 2)[0];
			return AUTO_CLOSING_TAGS.get(suffix);
		}
	}

	public static class NestedAutoClosingFormatting extends AutoClosingFormatting {
		private final AutoClosingFormatting f;

		public NestedAutoClosingFormatting(AutoClosingFormatting f) {
			super(f.getKind());
			this.f = f;
		}

		public String getUsedTag() {
			return "+"+f.getUsedTag();
		}

		public Map<String, String> getAttributes() {
			return f.getAttributes();
		}

		@Override
		public List<ParatextCharacterContentPart> getContent() {
			return f.getContent();
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T {
			throw new UnsupportedOperationException();
		}
	}
}
