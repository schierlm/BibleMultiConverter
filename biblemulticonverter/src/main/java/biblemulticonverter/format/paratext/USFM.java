package biblemulticonverter.format.paratext;

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


	public static final Map<String, ParagraphKind> PARAGRAPH_TAGS = ParagraphKind.allTags();
	public static final Map<String, FootnoteXrefKind> FOOTNOTE_XREF_TAGS = FootnoteXrefKind.allTags();
	public static final Map<String, AutoClosingFormattingKind> AUTO_CLOSING_TAGS = AutoClosingFormattingKind.allTags();

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		return doImportBook(inputFile, StandardCharsets.UTF_8);
	}

	private ParatextBook doImportBook(File inputFile, Charset charset) throws Exception {
		KNOWN_CHARACTER_TAGS.addAll(AUTO_CLOSING_TAGS.keySet());
		if (!inputFile.getName().toLowerCase().endsWith(".usfm") && !inputFile.getName().toLowerCase().endsWith(".sfm"))
			return null;
		String data = new String(Files.readAllBytes(inputFile.toPath()), charset).replaceAll("[\\p{Cc}\\p{Z}]+", " ").trim() + "\\$EOF$";
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
				result.getContent().add(new ParagraphStart(PARAGRAPH_TAGS.get(tag)));
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
				String[] parts = textPart.split(" ", 2);

				ChapterStart chapter = result.findLastBookContent(ChapterStart.class);
				if (chapter == null) {
					throw new IllegalStateException("Verse \\v found before chapter start milestone");
				}

				// A verse number in USFM 2 may be in the format 6-7, 6a or even 6-7a.
				// Attempt to parse these numbers by first adding the book and chapter and then parsing it as a whole.
				VerseIdentifier location = VerseIdentifier.fromStringOrThrow(chapter.getLocation() + ":" + parts[0]);

				containerStack.get(containerStack.size() - 1).getContent().add(new VerseStart(location, parts[0]));
				textPart = parts.length == 1 ? "" : parts[1];
			} else if (tag.equals("c")) {
				String[] parts = textPart.split(" ", 2);
				if (!parts[0].matches("[0-9]+"))
					throw new NumberFormatException("Invalid chapter number in \\c " + textPart);
				result.getContent().add(new ChapterStart(new ChapterIdentifier(id, Integer.parseInt(parts[0]))));
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
			if (!textPart.isEmpty()) {
				if (containerStack.isEmpty()) {
					ParatextCharacterContent container = new ParatextCharacterContent();
					containerStack.add(container);
					result.getContent().add(container);
				}
				textPart = textPart.replace(" // ", " ").replace("~", "\u00A0");
				containerStack.get(containerStack.size() - 1).getContent().add(new ParatextCharacterContent.Text(textPart));
			}
		}
		return result;
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
			bw.write("\\id " + book.getId().getIdentifier() + " " + escape(book.getBibleName(), false));
			for (Map.Entry<String, String> attr : book.getAttributes().entrySet()) {
				bw.write("\n\\" + attr.getKey() + " " + escape(attr.getValue(), false));
			}
			book.accept(new ParatextBookContentVisitor<IOException>() {

				private USFMExportContext context = new USFMExportContext();

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
		boolean needSpace = false;
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
				for (Map.Entry<String, String> entry : attributes.entrySet()) {
					if (attrs.length() > 0)
						attrs.append(" ");
					attrs.append(entry.getKey() + "=\"" + entry.getValue() + "\"");
				}
				pushSuffix(thisTag + "\t|" + attrs.toString());
			}
			return this;
		}

		@Override
		public void visitReference(Reference reference) throws IOException {
			visitText(reference.getContent());
		}

		@Override
		public void visitText(String text) throws IOException {
			if (context.needSpace)
				bw.write(" ");
			context.needSpace = text.endsWith(" ");
			if (context.needSpace) {
				text = text.substring(0, text.length() - 1);
			}
			AutoClosingFormattingKind lastTag = getLastTag();
			bw.write(escape(text, lastTag != null && lastTag.getDefaultAttributes() != null));
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
