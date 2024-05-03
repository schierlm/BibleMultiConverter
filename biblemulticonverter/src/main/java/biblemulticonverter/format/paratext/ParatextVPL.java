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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import biblemulticonverter.data.Utils;
import biblemulticonverter.format.paratext.ParatextBook.ChapterEnd;
import biblemulticonverter.format.paratext.ParatextBook.ChapterStart;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentPart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextBook.TableCellStart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Text;
import biblemulticonverter.format.paratext.ParatextCharacterContent.VerseEnd;
import biblemulticonverter.format.paratext.ParatextCharacterContent.VerseStart;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;

/**
 * Simple importer and exporter that writes a diffable VPL-inspired plain text
 * file with Paratext tags.
 */
public class ParatextVPL extends AbstractParatextFormat {

	public static final String[] HELP_TEXT = {
			"VPL inspired format using Paratext tags",
			"",
			"Usage (export): ParatextDump <OutputFile>",
			"",
			"Point the importer to .txt files, not to directories!",
	};

	private static final String MAGIC = "BibleMultiConverterParatext-1.0";

	public ParatextVPL() {
		super("ParatextVPL");
	}

	@Override
	protected List<ParatextBook> doImportAllBooks(File inputFile) throws Exception {
		List<ParatextBook> result = new ArrayList<ParatextBook>();
		ParatextBook currentBook = null;
		ChapterIdentifier currentChapter = null;
		VerseIdentifier currentVerse = null;
		ParatextCharacterContentContainer currentContainer = null;
		Map<String, ParagraphKind> allParagraphKinds = ParagraphKind.allTags();
		Map<String, FootnoteXrefKind> allFootnoteKinds = FootnoteXrefKind.allTags();
		Map<String, AutoClosingFormattingKind> allFormattingKinds = AutoClosingFormattingKind.allTags();
		List<ParatextCharacterContentContainer> containerStack = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
			String line = br.readLine();
			if (!line.equals(MAGIC))
				throw new IOException("Invalid header line: " + line);
			while ((line = br.readLine()) != null) {
				if (line.length() == 0 || line.startsWith("#"))
					continue;
				try {
					String[] parts = line.split(" ", 2);
					if (parts.length != 2) {
						parts = new String[] { line, "" };
					}
					if (parts[0].startsWith("=") && parts[0].endsWith("=")) {
						if (currentChapter != null || currentVerse != null) {
							throw new IOException("Chapter/verse still open when starting book " + parts[0]);
						}
						result.add(currentBook = new ParatextBook(ParatextID.fromIdentifier(parts[0].substring(1, parts[0].length() - 1)), parts[1]));
					} else if (parts[0].startsWith("[#") && parts[0].endsWith("]")) {
						currentBook.getAttributes().put(parts[0].substring(2, parts[0].length() - 1), parts[1]);
					} else {
						if (parts[0].startsWith("[") && parts[0].endsWith("]")) {
							if (!containerStack.isEmpty()) {
								throw new RuntimeException("Paragraph nested in other content: " + line);
							}
							if (currentContainer != null)
								currentContainer = null;
							String tag = parts[0].substring(1, parts[0].length() - 1);
							if (Utils.compilePattern(TableCellStart.TABLE_CELL_TAG_REGEX).matcher(tag).matches()) {
								currentBook.getContent().add(new TableCellStart(tag));
							} else {
								currentBook.getContent().add(new ParagraphStart(Objects.requireNonNull(allParagraphKinds.get(tag))));
							}
						} else {
							String[] subparts = parts[0].split("\\.");
							if (subparts.length != 2 && subparts.length != 3) {
								throw new IOException("Unsupported line prefix: " + parts[0]);
							}
							ChapterIdentifier cid = new ChapterIdentifier(ParatextID.fromIdentifier(subparts[0]), Integer.parseInt(subparts[1]));
							if (subparts.length == 2) {
								if (currentVerse != null) {
									throw new IOException("Verse still open when starting chapter " + parts[0]);
								}
								if (currentChapter != null) {
									currentBook.getContent().add(new ParatextBook.ChapterEnd(currentChapter));
								}
								currentChapter = cid;
								currentBook.getContent().add(new ChapterStart(currentChapter));
							} else if (subparts.length == 3) {
								if (currentChapter == null || !currentChapter.toString().equals(cid.toString())) {
									throw new IOException("Verse" + parts[0] + " inside unrelated chapter " + currentChapter);
								}
								if (currentContainer == null) {
									ParatextCharacterContent pcc = new ParatextCharacterContent();
									currentContainer = pcc;
									currentBook.getContent().add(pcc);
								}
								if (currentVerse != null) {
									currentContainer.getContent().add(new ParatextCharacterContent.VerseEnd(currentVerse));
								}
								currentVerse = VerseIdentifier.fromStringOrThrow(currentChapter + ":" + subparts[2]);
								currentContainer.getContent().add(new VerseStart(currentVerse, subparts[2]));
							}
						}
						if (!parts[1].isEmpty()) {
							if (currentContainer == null) {
								ParatextCharacterContent pcc = new ParatextCharacterContent();
								currentContainer = pcc;
								currentBook.getContent().add(pcc);
							}
							final String rest = parts[1];
							int startPos = 0, pos = rest.indexOf('<');
							while (pos != -1) {
								Text t = Text.from(rest.substring(startPos, pos));
								if (t != null) {
									currentContainer.getContent().add(t);
								}
								if (rest.startsWith("<<>", pos)) {
									currentContainer.getContent().add(Text.from("<"));
									startPos = pos + 3;
								} else if (rest.startsWith("</>", pos)) {
									if (!containerStack.isEmpty()) {
										currentContainer = containerStack.remove(containerStack.size() - 1);
									} else if (currentVerse != null) {
										currentContainer.getContent().add(new ParatextCharacterContent.VerseEnd(currentVerse));
										currentVerse = null;
									} else if (currentChapter != null) {
										currentContainer = null;
										currentBook.getContent().add(new ParatextBook.ChapterEnd(currentChapter));
										currentChapter = null;
									} else {
										throw new IOException("Closing tag but nothing is open");
									}
									startPos = pos + 3;
								} else {
									int endPos = rest.indexOf('>', pos);
									if (endPos == -1)
										throw new IOException("Unclosed tag: " + rest.substring(pos));
									String tag = rest.substring(pos + 1, endPos);
									startPos = endPos + 1;
									Map<String, String> args = null;
									if (tag.contains(" ")) {
										int pos2 = tag.indexOf(" ");
										String argRest = tag.substring(pos2 + 1).trim();
										tag = tag.substring(0, pos2);
										args = new HashMap<>();
										while (!argRest.isEmpty()) {
											pos2 = argRest.indexOf("=\"");
											String key = argRest.substring(0, pos2);
											argRest = argRest.substring(pos2 + 2);
											pos2 = argRest.indexOf("\"");
											String value = argRest.substring(0, pos2);
											argRest = argRest.substring(pos2 + 1).trim();
											args.put(key, value);
										}
									}
									AutoClosingFormattingKind formatting = allFormattingKinds.get(tag);
									if (formatting != null) {
										AutoClosingFormatting acf = new AutoClosingFormatting(formatting);
										if (args != null) {
											acf.getAttributes().putAll(args);
											args = null;
										}
										currentContainer.getContent().add(acf);
										containerStack.add(currentContainer);
										currentContainer = acf;
									} else if (tag.equals("ref")) {
										pos = rest.indexOf('<', startPos);
										if (!rest.startsWith("</>", pos)) {
											throw new IOException("Unsupported reference content: " + rest.substring(startPos));
										}
										currentContainer.getContent().add(Reference.parse(args.remove("target"), rest.substring(startPos, pos)));
										startPos = pos + 3;
									} else {
										FootnoteXrefKind footnote = allFootnoteKinds.get(tag);
										if (footnote != null) {
											FootnoteXref fx = new FootnoteXref(footnote, args.remove("caller"));
											currentContainer.getContent().add(fx);
											containerStack.add(currentContainer);
											currentContainer = fx;
										} else {
											throw new IOException("Unsupported tag: " + tag);
										}
									}
									if (args != null && !args.isEmpty()) {
										throw new IOException("Unsupported tag arguments: " + args);
									}
								}
								pos = rest.indexOf('<', startPos);
							}
							Text t = Text.from(rest.substring(startPos));
							if (t != null) {
								currentContainer.getContent().add(t);
							}
						}
					}
				} catch (Exception ex) {
					throw new IOException("Error while parsing line: " + line, ex);
				}
			}
		}
		return result;
	}

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void doExportBooks(List<ParatextBook> books, String... exportArgs) throws Exception {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[0]), StandardCharsets.UTF_8))) {
			bw.write(MAGIC + "\n");
			for (ParatextBook book : books) {
				writeBook(bw, book);
			}
		}
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
			bw.write("BibleMultiConverterParatext-1.0\n");
			writeBook(bw, book);
		}
	}

	private void writeBook(BufferedWriter bw, ParatextBook book) throws IOException {
		String id = book.getId().getIdentifier();
		bw.write("=" + id + "= " + book.getBibleName());
		for (Map.Entry<String, String> bookattr : book.getAttributes().entrySet()) {
			bw.write("\n[#" + bookattr.getKey() + "] " + bookattr.getValue());
		}
		ParatextBook bb = new ParatextBook(book.getId(), book.getBibleName());
		List<ParatextBookContentPart> content = bb.getContent();
		content.addAll(book.getContent());
		for (int i = 0; i < content.size() - 1; i++) {
			if (content.get(i) instanceof ChapterEnd && content.get(i + 1) instanceof ChapterStart) {
				content.remove(i);
				i--;
			}
		}
		bb.accept(new ParatextBookContentVisitor<IOException>() {
			int currchapter = -1;

			@Override
			public void visitChapterStart(ChapterIdentifier location) throws IOException {
				currchapter = location.chapter;
				bw.write("\n" + id + "." + location.chapter + " ");
			}

			@Override
			public void visitChapterEnd(ChapterIdentifier location) throws IOException {
				bw.write("</>");
			}

			@Override
			public void visitParagraphStart(ParagraphKind kind) throws IOException {
				bw.write("\n[" + kind.getTag() + "] ");
			}

			@Override
			public void visitTableCellStart(String tag) throws IOException {
				bw.write("\n[" + tag + "] ");
			}

			@Override
			public void visitParatextCharacterContent(ParatextCharacterContent content) throws IOException {
				ParatextCharacterContent cc = new ParatextCharacterContent();
				cc.getContent().addAll(content.getContent());
				for (int i = 0; i < cc.getContent().size() - 1; i++) {
					if (cc.getContent().get(i) instanceof VerseEnd && cc.getContent().get(i + 1) instanceof VerseStart) {
						cc.getContent().remove(i);
						i--;
					}
				}
				cc.accept(new ParatextVPLCharacterContentVisitor(id + "." + currchapter + ".", bw, ""));
			}
		});
		bw.write('\n');
	}

	public class ParatextVPLCharacterContentVisitor implements ParatextCharacterContentVisitor<IOException> {

		private final String bookChapter;
		private final BufferedWriter bw;
		private final String suffix;

		public ParatextVPLCharacterContentVisitor(String bookChapter, BufferedWriter bw, String suffix) {
			this.bookChapter = bookChapter;
			this.bw = bw;
			this.suffix = suffix;
		}

		@Override
		public void visitVerseStart(VerseIdentifier location, String verseNumber) throws IOException {
			bw.write("\n" + bookChapter + verseNumber + " ");
		}

		@Override
		public void visitVerseEnd(VerseIdentifier location) throws IOException {
			bw.write("</>");
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitFootnoteXref(FootnoteXrefKind kind, String caller) throws IOException {
			bw.write("<" + kind.getTag() + " caller=\"" + caller + "\">");
			return new ParatextVPLCharacterContentVisitor(bookChapter, bw, "</>");
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws IOException {
			bw.write("<" + kind.getTag());
			for (Map.Entry<String, String> attr : attributes.entrySet()) {
				bw.write(" " + attr.getKey() + "=\"" + attr.getValue() + "\"");
			}
			bw.write('>');
			return new ParatextVPLCharacterContentVisitor(bookChapter, bw, "</>");
		}

		@Override
		public void visitReference(Reference reference) throws IOException {
			bw.write("<ref target=\"" + reference.toString() + "\">");
			visitText(reference.getContent());
			bw.write("</>");
		}

		@Override
		public void visitText(String text) throws IOException {
			bw.write(text.replace("<", "<<>"));
		}

		@Override
		public void visitEnd() throws IOException {
			bw.write(suffix);
		}
	}
}
