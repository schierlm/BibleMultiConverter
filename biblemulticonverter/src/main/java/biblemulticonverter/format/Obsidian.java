package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;

public class Obsidian implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export to Markdown for Obsidian (one chapter per file)",
			"",
			"Some features (like Strongs numbers or CSS formatting) can only be exported when",
			"inline HTML is allowed via --html option.",
			"",
			"Usage: Obsidian <OutputDirectory> [-html]"
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File outDir = new File(exportArgs[0]);
		outDir.mkdirs();
		boolean inlineHTML = exportArgs.length == 2 && exportArgs[1].equals("-html");
		for (Book book : bible.getBooks()) {
			File bookDir = new File(outDir, book.getShortName());
			bookDir.mkdir();
			for (int cn = 1; cn <= book.getChapters().size(); cn++) {
				Chapter ch = book.getChapters().get(cn - 1);
				AtomicInteger footnoteCounter = new AtomicInteger();
				try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(bookDir, book.getAbbr() + " " + cn + ".md")), StandardCharsets.UTF_8))) {
					bw.write("# " + book.getShortName() + " " + cn);
					bw.write("\n");
					bw.write("\n");
					if (ch.getProlog() != null) {
						acceptWithFootnotes(bw, footnoteCounter, ch.getProlog(), inlineHTML);
						bw.write("\n");
					}
					for (VirtualVerse vv : ch.createVirtualVerses()) {
						for (Headline hl : vv.getHeadlines()) {
							bw.write("#####".substring(0, 1 + Math.max(hl.getDepth(), 4)));
							bw.write(' ');
							hl.accept(new ObsidianVisitor(bw, null, null, inlineHTML, ""));
							bw.write("\n");
						}
						for (Verse v : vv.getVerses()) {
							bw.write("###### " + v.getNumber());
							bw.write("\n");
							acceptWithFootnotes(bw, footnoteCounter, v, inlineHTML);
							bw.write("\n");
						}
					}
				}
			}
		}
	}

	private void acceptWithFootnotes(BufferedWriter bw, AtomicInteger footnoteCounter, FormattedText ft, boolean inlineHTML) throws IOException {
		StringWriter fn = new StringWriter();
		ft.accept(new ObsidianVisitor(bw, footnoteCounter, fn, inlineHTML, ""));
		bw.write("\n");
		String fns = fn.toString();
		if (!fns.isEmpty()) {
			bw.write(fns);
			bw.write("\n");
		}
	}

	/** Escape Markdown */
	private static String escape(String raw) {
		for (char ch : Arrays.asList('\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '!')) {
			raw = raw.replace("" + ch, "\\" + ch);
		}
		return raw;
	}

	private static class ObsidianVisitor extends AbstractNoCSSVisitor<IOException> {

		private final Writer writer;
		private final Writer footnoteWriter;
		private final AtomicInteger footnoteCounter;
		private final boolean inlineHTML;
		private final List<String> suffixStack = new ArrayList<String>();

		protected ObsidianVisitor(Writer writer, AtomicInteger footnoteCounter, Writer footnoteWriter, boolean inlineHTML, String suffix) {
			this.writer = writer;
			this.footnoteCounter = footnoteCounter;
			this.footnoteWriter = footnoteWriter;
			this.inlineHTML = inlineHTML;
			pushSuffix(suffix);
		}

		protected void pushSuffix(String suffix) {
			suffixStack.add(suffix);
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			return 0;
		}

		@Override
		public void visitStart() throws IOException {
		}

		@Override
		public void visitText(String text) throws IOException {
			writer.write(escape(text));
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			// for prolog only (in verses, already parsed by virtual verse)
			writer.write("#####".substring(0, 1 + Math.max(depth, 4)));
			writer.write(' ');
			pushSuffix("\n");
			return this;
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			if (footnoteWriter == null) {
				System.out.println("WARNING: Nested footnotes are not supported");
				return null;
			}
			int footnote = footnoteCounter.incrementAndGet();
			writer.write("[^" + footnote + "]");
			footnoteWriter.write("[^" + footnote + "]: ");
			return new ObsidianVisitor(footnoteWriter, null, null, inlineHTML, "\n");
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			System.out.println("WARNING: Cross references are not supported");
			pushSuffix("");
			return this;
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws IOException {
			if (footnoteWriter == null) {
				visitText(" ");
			} else {
				switch (kind) {
				case NEWLINE:
					writer.write("\n");
					break;
				case NEWLINE_WITH_INDENT:
					writer.write("\n    ");
					break;
				case PARAGRAPH:
					writer.write("\n\n");
					break;
				}
			}
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			String suffix;
			if (strongs == null && rmac == null) {
				suffix = "";
			} else if (!inlineHTML) {
				System.out.println("WARNING: Skipping grammar information (Inline HTML is disabled)");
				suffix = "";
			} else {
				StringBuilder suffixBuilder = new StringBuilder();
				if (strongs != null) {
					for (int i = 0; i < strongs.length; i++) {
						suffixBuilder.append(" <sup><font color=green>" + (strongsPrefixes != null ? Utils.formatStrongs(false, i, strongsPrefixes, strongs) : "" + strongs[i]) + "</font></sup>");
					}
				}
				if (rmac != null) {
					for (String r : rmac) {
						suffixBuilder.append(" <sup><font color=darkmagenta>" + r + "</font></sup>");
					}
				}
				suffix = suffixBuilder.append(' ').toString();
			}
			pushSuffix(suffix);
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			System.out.println("WARNING: Dictionary entries are not supported");
			pushSuffix("");
			return this;
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			String marker;
			switch (kind) {
			case BOLD:
				marker = "**";
				break;
			case ITALIC:
				marker = "*";
				break;
			default:
				return !inlineHTML ? visitChangedCSSFormatting(kind.getCss(), this, 0) : visitCSSFormatting(kind.getCss());
			}
			writer.append(marker);
			pushSuffix(marker);
			return this;
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			if (!inlineHTML) {
				return super.visitCSSFormatting(css);
			}
			writer.write("<span style=\"" + css + "\">");
			pushSuffix("</span>");
			return this;
		}

		@Override
		protected Visitor<IOException> visitChangedCSSFormatting(String remainingCSS, Visitor<IOException> resultingVisitor, int replacements) {
			if (!remainingCSS.isEmpty())
				System.out.println("WARNING: Skipping formatting (Inline HTML is disabled)");
			fixupSuffixStack(replacements, suffixStack);
			return resultingVisitor;
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			visitText("/");
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
			if (mode == RawHTMLMode.ONLINE) {
				System.out.println("WARNING: Skipping Online Raw HTML (Online Raw HTML is not supported)");
			} else if (inlineHTML) {
				writer.write(raw);
			} else {
				System.out.println("WARNING: Skipping Raw HTML (Inline HTML is disabled)");
			}
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			throw new RuntimeException("Variations not supported");
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			Visitor<IOException> next = prio.handleVisitor(category, this);
			if (next != null)
				pushSuffix("");
			return next;
		}

		@Override
		public boolean visitEnd() throws IOException {
			writer.write(suffixStack.remove(suffixStack.size() - 1));
			return false;
		}
	}
}
