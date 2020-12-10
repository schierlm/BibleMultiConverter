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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.format.paratext.ParatextBook.ChapterStart;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextBook.TableCellStart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.VerseStart;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import biblemulticonverter.format.paratext.model.Version;
import biblemulticonverter.format.paratext.utilities.ImportUtilities;
import biblemulticonverter.format.paratext.utilities.StandardExportLogMessages;
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

	public static final Set<String> KNOWN_CHARACTER_TAGS = new HashSet<>(Arrays.asList("f", "fe", "x"));

	private static final Set<ParagraphKind> USFM_2_PARAGRAPH_KINDS = ParagraphKind.allForVersion(Version.V2_2);
	private static final Set<AutoClosingFormattingKind> USFM_2_AUTO_CLOSING_FORMATTING_KINDS = AutoClosingFormattingKind.allForVersion(Version.V2_2);

	public static final Map<String, ParagraphKind> PARAGRAPH_TAGS = ParagraphKind.allTags();
	public static final Map<String, FootnoteXrefKind> FOOTNOTE_XREF_TAGS = FootnoteXrefKind.allTags();
	public static final Map<String, AutoClosingFormattingKind> AUTO_CLOSING_TAGS = AutoClosingFormattingKind.allTags();

	private final StandardExportLogMessages logger = new StandardExportLogMessages("USFM 2");

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		return doImportBook(inputFile, StandardCharsets.UTF_8);
	}

	private ParatextBook doImportBook(File inputFile, Charset charset) throws Exception {
		KNOWN_CHARACTER_TAGS.addAll(AUTO_CLOSING_TAGS.keySet());
		if (!inputFile.getName().toLowerCase().endsWith(".usfm") && !inputFile.getName().toLowerCase().endsWith(".sfm"))
			return null;
		String data = TextUtilities.whitespaceNormalization(new String(Files.readAllBytes(inputFile.toPath()), charset)) + "\\$EOF$";
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

		VerseStart openVerse = null;
		ChapterStart openChapter = null;

		while (startPos < finalPos) {
			if (data.charAt(startPos) != '\\')
				throw new IllegalStateException();
			int pos = data.indexOf('\\', startPos + 1);
			String textPart = data.substring(startPos + 1, pos);
			startPos = pos;
			pos = Math.min(textPart.length(), 1 + Math.min((textPart + " ").indexOf(' '), (textPart + "*").indexOf('*')));
			String tag = textPart.substring(0, pos).trim().toLowerCase();
			textPart = textPart.substring(pos);
			if (textPart.endsWith(" ")) {
				String nextTag = data.substring(startPos + 1, Math.min(data.length(), startPos + 10)) + " *\\";
				pos = Math.min(nextTag.indexOf('\\'), Math.min(nextTag.indexOf(' '), nextTag.indexOf('*')));
				if (!KNOWN_CHARACTER_TAGS.contains(nextTag.substring(0, pos))) {
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
				AutoClosingFormatting nextContainer = new AutoClosingFormatting(AUTO_CLOSING_TAGS.get(tag), tag.startsWith("+"));
				containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
				containerStack.add(nextContainer);
				if (nextContainer.getKind().getDefaultAttributes() != null && data.startsWith("\\" + tag + "*", startPos) && textPart.contains("|")) {
					String[] defaultAttributes = nextContainer.getKind().getDefaultAttributes();
					String[] parts = textPart.split("\\|");
					for (int i = 1; i < parts.length; i++) {
						if (parts[i].contains("=")) {
							String attList = parts[i];
							while (attList.contains("=")) {
								pos = attList.indexOf('=');
								String key = attList.substring(0, pos).trim();
								attList = attList.substring(pos + 1).trim();
								if (attList.startsWith("\"")) {
									pos = attList.indexOf('"', 1);
									nextContainer.getAttributes().put(key, attList.substring(1, pos));
									attList = attList.substring(pos + 1).trim();
								} else {
									nextContainer.getAttributes().put(key, attList);
									attList = "";
								}
							}
						} else if (i - 1 < defaultAttributes.length) {
							nextContainer.getAttributes().put(defaultAttributes[i - 1], parts[i]);
						}
					}
					textPart = parts[0];
					if (textPart.endsWith(" ")) {
						textPart = textPart.substring(0, textPart.length() - 1);
					}
				}
			} else if (tag.equals("v")) {
				ImportUtilities.closeOpenVerse(result, openVerse);

				String[] parts = textPart.split(" ", 2);
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
			} else if (tag.matches("t[hc]r?[0-9]+")) {
				result.getContent().add(new TableCellStart(tag));
				closeCharacterAttributes = true;
			} else if (FOOTNOTE_XREF_TAGS.containsKey(tag)) {
				String[] parts = textPart.split(" ", 2);
				FootnoteXref nextContainer = new FootnoteXref(FOOTNOTE_XREF_TAGS.get(tag), parts[0]);
				containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
				containerStack.add(nextContainer);
				textPart = parts.length == 1 ? "" : parts[1];
			} else if (tag.equals("id")) {
				System.out.println("WARNING: Skipping duplicate \\id tag");
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
				} else {
					result.getAttributes().put(tag, textPart);
				}
				textPart = "";
			} else if (BOOK_HEADER_ATTRIBUTE_TAGS.contains(tag)) {
				result.getAttributes().put(tag, textPart);
				textPart = "";
			} else {
				System.out.println("WARNING: Skipping unknown tag \\" + tag);
			}
			if (closeCharacterAttributes) {
				containerStack.clear();
			}

			textPart = textPart.replace(" // ", " ").replace("~", "\u00A0");
			ParatextCharacterContent.Text text = ParatextCharacterContent.Text.from(textPart);
			if (text != null) {
				if (containerStack.isEmpty()) {
					ParatextCharacterContent container = new ParatextCharacterContent();
					containerStack.add(container);
					result.getContent().add(container);
				}
				containerStack.get(containerStack.size() - 1).getContent().add(text);
			}
		}
		ImportUtilities.closeOpenVerse(result, openVerse);
		ImportUtilities.closeOpenChapter(result, openChapter);
		return result;
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
			bw.write("\\id " + book.getId().getIdentifier() + " " + escape(book.getBibleName(), false));
			bw.write("\n\\ide UTF-8");
			for (Map.Entry<String, String> attr : book.getAttributes().entrySet()) {
				// Never write a charset other than the charset we are using to write this file
				if (!attr.getKey().equals("ide")) {
					bw.write("\n\\" + attr.getKey() + " " + escape(attr.getValue(), false));
				}
			}
			book.accept(new ParatextBookContentVisitor<IOException>() {

				private USFMExportContext context = new USFMExportContext(logger);

				@Override
				public void visitChapterStart(ChapterIdentifier location) throws IOException {
					bw.write("\n\\c " + location.chapter);
					context.needSpace = true;
				}

				@Override
				public void visitChapterEnd(ChapterIdentifier location) throws IOException {
					// Chapter end marker is not supported in USFM 2.
				}

				@Override
				public void visitParagraphStart(ParagraphKind kind) throws IOException {
					if (USFM_2_PARAGRAPH_KINDS.contains(kind)) {
						bw.write("\n\\" + kind.getTag());
						context.needSpace = true;
					} else {
						visitUnsupportedParagraphStart(kind);
					}
				}

				private void visitUnsupportedParagraphStart(ParagraphKind kind) throws IOException {
					if (kind == ParagraphKind.HEBREW_NOTE) {
						// According to documentation this is very similar to `d` (ParagraphKind.DESCRIPTIVE_TITLE)
						logger.logReplaceWarning(kind, ParagraphKind.DESCRIPTIVE_TITLE);
						visitParagraphStart(ParagraphKind.DESCRIPTIVE_TITLE);
					} else if (kind.isSameBase(ParagraphKind.SEMANTIC_DIVISION)) {
						// TODO maybe add more than 1 blank line?
						logger.logReplaceWarning(kind, ParagraphKind.BLANK_LINE);
						visitParagraphStart(ParagraphKind.BLANK_LINE);
					} else if (kind == ParagraphKind.PARAGRAPH_PO || kind == ParagraphKind.PARAGRAPH_LH || kind == ParagraphKind.PARAGRAPH_LF) {
						logger.logReplaceWarning(kind, ParagraphKind.PARAGRAPH_P);
						visitParagraphStart(ParagraphKind.PARAGRAPH_P);
					} else if (kind.getTag().startsWith(ParagraphKind.PARAGRAPH_LIM.getTag())) {
						// Documentation is not entirely clear on what the exact difference is between `lim#` and `li#`
						// one is "embedded" the other is not: https://ubsicap.github.io/usfm/lists/index.html#lim
						// The assumption is made here that `lim#` is directly replaceable with `li#`
						ParagraphKind replacement = ParagraphKind.PARAGRAPH_LI.getWithNumber(kind.getNumber());
						logger.logReplaceWarning(kind, replacement);
						visitParagraphStart(replacement);
					} else {
						throw new RuntimeException("Could not export to USFM 2 because an unhandled paragraph type `" + kind + "` from a newer USFM/USX version was found.");
					}
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
				public void visitParatextCharacterContent(ParatextCharacterContent content) throws IOException {
					content.accept(new USFMCharacterContentVisitor(bw, context));
				}
			});
		}
	}

	private static String escape(String text, boolean escapePipe) {
		if (escapePipe)
			text = text.replace("|", "\uFF5C");
		return text.replace("\\", "\uFE68");
	}

	private static class USFMExportContext {
		StandardExportLogMessages logger;
		boolean needSpace = false;

		public USFMExportContext(StandardExportLogMessages logger) {
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
		public ParatextCharacterContentVisitor<IOException> visitFootnoteXref(FootnoteXrefKind kind, String caller) throws IOException {
			if (context.needSpace)
				bw.write(" ");
			bw.write("\\" + kind.getTag() + " " + caller);
			context.needSpace = true;
			pushSuffix(kind.getTag());
			return this;
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws IOException {
			if (USFM_2_AUTO_CLOSING_FORMATTING_KINDS.contains(kind)) {
				if (context.needSpace)
					bw.write(" ");
				AutoClosingFormattingKind lastTag = getLastTag();
				String thisTag = (lastTag != null ? "+" : "") + kind.getTag();
				bw.write("\\" + thisTag);
				context.needSpace = true;
				if (attributes.isEmpty()) {
					pushSuffix(thisTag);
				} else {
					// TODO it can happen that newer attributes are unintentionally exported
					StringBuilder attrs = new StringBuilder("");
					for (Map.Entry<String, String> entry : attributes.entrySet()) {
						if (attrs.length() > 0)
							attrs.append(" ");
						attrs.append(entry.getKey() + "=\"" + entry.getValue() + "\"");
					}
					pushSuffix(thisTag + "\t|" + attrs.toString());
				}
			} else {
				return visitUnsupportedAutoClosingFormatting(kind, attributes);
			}
			return this;
		}

		private ParatextCharacterContentVisitor<IOException> visitUnsupportedAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws IOException {
			if (kind == AutoClosingFormattingKind.LIST_TOTAL || kind == AutoClosingFormattingKind.LIST_KEY || kind.isSameBase(AutoClosingFormattingKind.LIST_VALUE)) {
				// It should not be too much of an issue to just skip these list tags
				// E.g.
				// \li1 \lik Reuben\lik* \liv1 Eliezer son of Zichri\liv1*
				// Wil become:
				// \li1 Reuben Eliezer son of Zichri
				context.logger.logSkippedWarning(kind);
				return new USFMCharacterContentVisitor(bw, context);
			} else if (kind == AutoClosingFormattingKind.FOOTNOTE_WITNESS_LIST) {
				// The Footnote witness list is just extra markup found within a footnote, however according to
				// documentation found here: https://ubsicap.github.io/usfm/v3.0.rc1/notes_basic/fnotes.html
				// Each element within a footnote must start with it's appropriate tag. So we can't just skip this tag
				// since it could contain text. It would be better to turn this into a text entry `ft`.
				context.logger.logReplaceWarning(kind, AutoClosingFormattingKind.FOOTNOTE_TEXT);
				return visitAutoClosingFormatting(AutoClosingFormattingKind.FOOTNOTE_TEXT, attributes);
			} else if (kind == AutoClosingFormattingKind.XREF_PUBLISHED_ORIGIN) {
				// Published cross reference origin texts do not exist in USFM 2.x
				// There is not really a nice way to downgrade these, we cannot put the `xop` tag into `xo` because it
				// might not follow the usual `<chapter><separator><verse>` pattern.
				// TODO, maybe we can just write the contents to the parent target, just like FOOTNOTE_WITNESS_LIST?
				context.logger.logRemovedWarning(kind);
				return null;
			} else if (kind == AutoClosingFormattingKind.XREF_TARGET_REFERENCES_TEXT) {
				// "Target reference(s) extra / added text" does not exist in USFM 2.x
				// We should be able to get away with just adding the raw content directly `target`.
				context.logger.logSkippedWarning(kind);
				return new USFMCharacterContentVisitor(bw, context);
			} else if (kind == AutoClosingFormattingKind.SUPERSCRIPT) {
				// There is not really a good way to represent superscript in USFM 2.x
				// To avoid losing data, we skip the tag and just add the content directly to `target`.
				// TODO, maybe we can use `sc` (Small caps) instead?
				context.logger.logSkippedWarning(kind, "This might lead to text that is not separated by whitespace," +
						"since the previous text and superscript text may not have had been separated by whitespace.");
				return new USFMCharacterContentVisitor(bw, context);
			} else if (kind == AutoClosingFormattingKind.ARAMAIC_WORD) {
				// There is not really a good way to represent Aramaic words in USFM 2.x
				// To avoid losing data, we skip the tag and just add the content directly to `target`.
				context.logger.logSkippedWarning(kind);
				return new USFMCharacterContentVisitor(bw, context);
			} else if (kind == AutoClosingFormattingKind.PROPER_NAME_GEOGRAPHIC) {
				// This marker just gives geographic names a different presentation, thus can easily be skipped without
				// too much loss.
				context.logger.logSkippedWarning(kind);
				return new USFMCharacterContentVisitor(bw, context);
			} else {
				throw new RuntimeException("Could not export to USFM 2 because an unhandled char type `" + kind + "` from a newer USFM/USX version was found.");
			}
		}

		@Override
		public void visitReference(Reference reference) throws IOException {
			visitText(reference.getContent());
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
			bw.write(escape(text, lastTag != null && lastTag.getDefaultAttributes() != null));
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
}
