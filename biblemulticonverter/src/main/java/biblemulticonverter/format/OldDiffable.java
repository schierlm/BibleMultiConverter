package biblemulticonverter.format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;

public class OldDiffable implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export bibles to Diffable format of v0.0.8 or v0.0.7 (or older).",
			"",
			"Usage: OldDiffable [-older] <OutputFile>",
			"",
			"When -older switch is not given, export to v0.0.8 format"
	};

	private static final String MAGIC = "BibleMultiConverter-1.0 Title: ";

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		boolean olderFormat = exportArgs[0].equals("-older");
		File exportFile = new File(exportArgs[olderFormat ? 1 : 0]);
		try (Writer w = new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
			doExport(bible, w, olderFormat);
		}
	}

	private void doExport(Bible bible, Writer w, boolean olderFormat) throws IOException {
		w.write(MAGIC + bible.getName() + "\n");
		for (Book book : bible.getBooks()) {
			w.write(book.getAbbr() + " = " + book.getId().getOsisID() + "\t" + book.getShortName() + "\t" + book.getLongName() + "\n");
			int chapterNumber = 0;
			for (Chapter ch : book.getChapters()) {
				chapterNumber++;
				if (ch.getProlog() != null) {
					ch.getProlog().accept(new OldDiffableVisitor(w, book.getAbbr() + " " + chapterNumber + " ", olderFormat));
				}
				for (Verse v : ch.getVerses()) {
					v.accept(new OldDiffableVisitor(w, book.getAbbr() + " " + chapterNumber + ":" + v.getNumber() + " ", olderFormat));
				}
			}
		}
	}

	private static class OldDiffableVisitor implements Visitor<IOException> {
		private final Writer w;
		private final String linePrefix;
		private final OldDiffableVisitor childVisitor;
		private boolean startNewLine = false, inMainContent = false;
		private boolean olderFormat;

		private OldDiffableVisitor(Writer w, String linePrefix, boolean olderFormat) throws IOException {
			this.w = w;
			this.linePrefix = linePrefix;
			this.olderFormat = olderFormat;
			childVisitor = linePrefix == null ? this : new OldDiffableVisitor(w, null, olderFormat);
			if (linePrefix != null)
				w.write(linePrefix);
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			return 0;
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			if (inMainContent)
				startNewLine = true;
			checkLine();
			if (linePrefix != null)
				startNewLine = true;
			w.write("<h" + depth + ">");
			return childVisitor;
		}

		@Override
		public void visitStart() throws IOException {
			if (linePrefix != null)
				inMainContent = true;
		}

		@Override
		public void visitText(String text) throws IOException {
			checkLine();
			w.write(text.replace("<", "<<>"));
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			checkLine();
			w.write("<fn>");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			checkLine();
			w.write("<xref abbr=\"" + bookAbbr + "\" id=\"" + book.getOsisID() + "\" chapters=\"" + firstChapter + ":" + lastChapter + "\" verses=\"" + firstVerse + ":" + lastVerse + "\">");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			checkLine();
			w.write("<" + kind.getCode() + ">");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			checkLine();
			w.write("<css style=\"" + css + "\">");
			return childVisitor;
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			checkLine();
			w.write("<vs/>");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws IOException {
			checkLine();
			w.write("<br kind=\"" + kind.name() + "\"/>");
			if (linePrefix != null)
				startNewLine = true;
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			checkLine();
			w.write("<grammar strong=\"");
			if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					if (i > 0)
						w.write(',');
					w.write("" + strongs[i]);
				}
			}
			if (strongsPrefixes != null) {
				if (olderFormat) {
					System.out.println("WARNING: Dropping strongs prefixes for older format");
				} else {
					w.write("\" strongpfx=\"");
					w.write(strongsPrefixes);
				}
			}
			w.write("\" rmac=\"");
			if (rmac != null) {
				for (int i = 0; i < rmac.length; i++) {
					if (i > 0)
						w.write(',');
					w.write("" + rmac[i]);
				}
			}
			w.write("\" idx=\"");
			if (sourceIndices != null) {
				for (int i = 0; i < sourceIndices.length; i++) {
					if (i > 0)
						w.write(',');
					w.write("" + sourceIndices[i]);
				}
			}
			w.write("\">");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			checkLine();
			w.write("<dict dictionary=\"" + dictionary + "\" entry=\"" + entry + "\">");
			return childVisitor;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
			checkLine();
			int marker = 1;
			while (raw.contains("</raw:" + marker + ">")) {
				marker = (int) (Math.random() * 1000000);
			}
			w.write("<raw:" + marker + " mode=\"" + mode.name() + "\">" + raw + "</raw:" + marker + ">");
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			w.write("<var vars=\"");
			for (int i = 0; i < variations.length; i++) {
				if (i > 0)
					w.write(",");
				w.write(variations[i]);
			}
			w.write("\">");
			return childVisitor;
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			checkLine();
			w.write("<extra prio=\"" + prio.name() + "\" category=\"" + category + "\" key=\"" + key + "\" value=\"" + value + "\">");
			return childVisitor;
		}

		@Override
		public boolean visitEnd() throws IOException {
			if (linePrefix == null)
				w.write("</>");
			else
				w.write("\n");
			return false;
		}

		private void checkLine() throws IOException {
			if (startNewLine) {
				startNewLine = false;
				w.write('\n');
				w.write(linePrefix);
			}
		}
	}
}
