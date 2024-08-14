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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import biblemulticonverter.format.paratext.ParatextBook.ChapterStart;
import biblemulticonverter.format.paratext.ParatextBook.Figure;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
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
 * Simple importer and exporter that dumps the internal Paratext format to a diffable plain text.
 */
public class ParatextDump extends AbstractParatextFormat {

	public static final String[] HELP_TEXT = {
			"Dump a Paratext bible to diffable plain text",
			"",
			"Usage (export): ParatextDump <OutputFile>",
			"",
			"Point the importer to .txt files, not to directories!",
	};

	public ParatextDump() {
		super("ParatextDump");
	}

	@Override
	protected List<ParatextBook> doImportAllBooks(File inputFile) throws Exception {
		List<ParatextBook> result = new ArrayList<ParatextBook>();
		ParatextBook currentBook = null;
		Map<String, ParagraphKind> allParagraphKinds = ParagraphKind.allTags();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\t", 3);
				switch (parts[0]) {
					case "BOOK":
						result.add(currentBook = new ParatextBook(ParatextID.fromIdentifier(parts[1]), parts[2]));
						break;
					case "BOOKATTR":
						currentBook.getAttributes().put(parts[1], parts[2]);
						break;
					case "CHAPTER":
						currentBook.getContent().add(new ChapterStart(new ChapterIdentifier(currentBook.getId(), Integer.parseInt(parts[1]))));
						break;
					case "CHAPTER-END":
						currentBook.getContent().add(new ParatextBook.ChapterEnd(ChapterIdentifier.fromLocationString(parts[1])));
						break;
					case "REMARK":
						currentBook.getContent().add(new ParatextBook.Remark(parts[1]));
						break;
					case "PARAGRAPH":
						currentBook.getContent().add(new ParagraphStart(Objects.requireNonNull(allParagraphKinds.get(parts[1]))));
						break;
					case "TABLECELL":
						currentBook.getContent().add(new TableCellStart(parts[1]));
						break;
					case "SIDEBAR":
						currentBook.getContent().add(new SidebarStart(parts[1].isEmpty() ? new String[0] : parts[1].split(" ")));
						break;
					case "SIDEBAR-END":
						currentBook.getContent().add(new SidebarEnd());
						break;
					case "PERIPHERAL":
						currentBook.getContent().add(new PeripheralStart(parts[1], parts.length == 2 ? null : parts[2]));
						break;
					case "VERSE":
						currentBook.getContent().add(new VerseStart(VerseIdentifier.fromStringOrThrow(parts[1]), parts[2]));
						break;
					case "VERSE-END":
						currentBook.getContent().add(new VerseEnd(VerseIdentifier.fromStringOrThrow(parts[2])));
						break;
					case "FIGURE":
						Figure f = new Figure(Objects.requireNonNull(parts[1]));
						currentBook.getContent().add(f);
						while (!(line = br.readLine()).equals("FIGUREEND")) {
							if (line.startsWith("ATTRIBUTE\t")) {
								parts = line.split("\t", 3);
								f.getAttributes().put(parts[1], parts[2]);
							} else {
								throw new RuntimeException("Unsupported figure line: "+line);
							}
						}
						break;
					case "CHARCONTENTSTART":
						ParatextCharacterContent cc = new ParatextCharacterContent();
						currentBook.getContent().add(cc);
						while (!(line = br.readLine()).equals("CHARCONTENTEND")) {
							importCharContent(cc.getContent(), br, line);
						}
						break;
					default:
						throw new IOException(line);
				}
			}
		}
		return result;
	}

	private void importCharContent(List<ParatextCharacterContentPart> target, BufferedReader br, String line) throws IOException {
		String[] parts = line.split("\t", 3);
		switch (parts[0]) {
			case "FOOTNOTE":
				String[] callerAndAttributes = parts[2].split(" ");
				FootnoteXref fx = new FootnoteXref(Objects.requireNonNull(FootnoteXrefKind.allTags().get(parts[1])), callerAndAttributes[0], Arrays.copyOfRange(callerAndAttributes, 1, callerAndAttributes.length));
				target.add(fx);
				while (!(line = br.readLine()).equals("FOOTNOTEEND")) {
					importCharContent(fx.getContent(), br, line);
				}
				break;
			case "CHARFORMAT":
				AutoClosingFormatting acf = new AutoClosingFormatting(Objects.requireNonNull(AutoClosingFormattingKind.allTags().get(parts[1])));
				target.add(acf);
				while (!(line = br.readLine()).equals("CHARFORMATEND")) {
					if (line.startsWith("ATTRIBUTE\t")) {
						parts = line.split("\t", 3);
						acf.getAttributes().put(parts[1], parts[2]);
					} else {
						importCharContent(acf.getContent(), br, line);
					}
				}
				break;
			case "MILESTONE":
				final Milestone m = new Milestone(Objects.requireNonNull(parts[1]));
				target.add(m);
				while (!(line = br.readLine()).equals("MILESTONEEND")) {
					if (line.startsWith("ATTRIBUTE\t")) {
						parts = line.split("\t", 3);
						m.getAttributes().put(parts[1], parts[2]);
					} else {
						throw new RuntimeException("Unsupported milestone line: "+line);
					}
				}
				break;
			case "REFERENCE":
				target.add(Reference.parse(parts[1], parts[2]));
				break;
			case "CUSTOM-MARKUP":
				target.add(new CustomMarkup(parts[1], Boolean.parseBoolean(parts[2])));
				break;
			case "SPACE":
				target.add(new SpecialSpace(Boolean.parseBoolean(parts[1]), Boolean.parseBoolean(parts[2])));
				break;
			case "TEXT":
				Text text = Text.from(parts[2]);
				if(text != null) {
					target.add(text);
				}
				break;
			default:
				throw new IOException(line);
		}
	}

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void doExportBooks(List<ParatextBook> books, String... exportArgs) throws Exception {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[0]), StandardCharsets.UTF_8))) {
			for (ParatextBook book : books) {
				writeBook(bw, book);
			}
		}
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
			writeBook(bw, book);
		}
	}

	protected void writeBook(BufferedWriter bw, ParatextBook book) throws IOException {
		bw.write("BOOK\t" + book.getId().getIdentifier() + "\t" + book.getBibleName() + "\n");
		for (Map.Entry<String, String> bookattr : book.getAttributes().entrySet()) {
			bw.write("BOOKATTR\t" + bookattr.getKey() + "\t" + bookattr.getValue() + "\n");
		}
		book.accept(new ParatextBookContentVisitor<IOException>() {

			@Override
			public void visitChapterStart(ChapterIdentifier location) throws IOException {
				bw.write("CHAPTER\t" + location.chapter + "\n");
			}

			@Override
			public void visitChapterEnd(ChapterIdentifier location) throws IOException {
				bw.write("CHAPTER-END\t" + location + "\n");
			}

			@Override
			public void visitRemark(String content) throws IOException {
				bw.write("REMARK\t" + content + "\n");
			}

			@Override
			public void visitParagraphStart(ParagraphKind kind) throws IOException {
				bw.write("PARAGRAPH\t" + kind.getTag() + "\n");
			}

			@Override
			public void visitTableCellStart(String tag) throws IOException {
				bw.write("TABLECELL\t" + tag + "\n");
			}

			@Override
			public void visitSidebarStart(String[] categories) throws IOException {
				bw.write("SIDEBAR\t"+String.join(" ", categories)+"\n");
			}

			@Override
			public void visitSidebarEnd() throws IOException {
				bw.write("SIDEBAR-END\n");
			}

			@Override
			public void visitPeripheralStart(String title, String id) throws IOException {
				bw.write("PERIPHERAL\t"+title+(id == null ? "" : "\t"+id)+"\n");
			}

			@Override
			public void visitVerseStart(VerseIdentifier location, String verseNumber) throws IOException {
				bw.write("VERSE\t" + location + "\t" + verseNumber + "\n");
			}

			@Override
			public void visitVerseEnd(VerseIdentifier location) throws IOException {
				bw.write("VERSE-END\t\t" + location + "\n");
			}

			@Override
			public void visitFigure(String caption, Map<String, String> attributes) throws IOException {
				bw.write("FIGURE\t"+caption+"\n");
				for (Map.Entry<String, String> attr : attributes.entrySet()) {
					bw.write("ATTRIBUTE\t" + attr.getKey() + "\t" + attr.getValue() + "\n");
				}
				bw.write("FIGUREEND\n");
			}

			@Override
			public void visitParatextCharacterContent(ParatextCharacterContent content) throws IOException {
				bw.write("CHARCONTENTSTART\n");
				content.accept(new ParatextDumpCharacterContentVisitor(bw, "CHARCONTENTEND\n"));
			}
		});
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
			bw.write("FOOTNOTE\t" + kind.getTag() + "\t" + caller);
			for (String cat : categories) {
				bw.write(" " + cat);
			}
			bw.write("\n");
			return new ParatextDumpCharacterContentVisitor(bw, "FOOTNOTEEND\n");
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws IOException {
			bw.write("CHARFORMAT\t" + kind.getTag() + "\n");
			for (Map.Entry<String, String> attr : attributes.entrySet()) {
				bw.write("ATTRIBUTE\t" + attr.getKey() + "\t" + attr.getValue() + "\n");
			}
			return new ParatextDumpCharacterContentVisitor(bw, "CHARFORMATEND\n");
		}

		@Override
		public void visitMilestone(String tag, Map<String, String> attributes) throws IOException {
			bw.write("MILESTONE\t"+tag+"\n");
			for (Map.Entry<String, String> attr : attributes.entrySet()) {
				bw.write("ATTRIBUTE\t" + attr.getKey() + "\t" + attr.getValue() + "\n");
			}
			bw.write("MILESTONEEND\n");
		}

		@Override
		public void visitReference(Reference reference) throws IOException {
			bw.write("REFERENCE\t" + reference.toString() + "\t" + reference.getContent() + "\n");
		}

		@Override
		public void visitCustomMarkup(String tag, boolean ending) throws IOException {
			bw.write("CUSTOM-MARKUP\t" + tag + "\t" + ending + "\n");
		}

		@Override
		public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws IOException {
			bw.write("SPACE\t" + nonBreakSpace + "\t" + optionalLineBreak + "\n");
		}

		@Override
		public void visitText(String text) throws IOException {
			bw.write("TEXT\t\t" + text + "\n");
		}

		@Override
		public void visitEnd() throws IOException {
			bw.write(suffix);
		}
	}
}
