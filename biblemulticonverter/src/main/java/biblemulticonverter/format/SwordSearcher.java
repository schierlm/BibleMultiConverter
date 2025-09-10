package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.StandardVersification;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.VirtualVerse;

public class SwordSearcher implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export format for SwordSearcher",
			"",
			"Usage: SwordSearcher <outfile> [<marker>]",
			"",
			"The exported file can be read by SwordSearcher Forge."
	};

	private static final Map<BookID, String> BOOK_ABBREV_MAP = new EnumMap<>(BookID.class);

	static {
		BOOK_ABBREV_MAP.put(BookID.BOOK_Gen, "Ge");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Exod, "Ex");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Lev, "Le");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Num, "Nu");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Deut, "De");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Josh, "Jos");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Judg, "Jg");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Ruth, "Ru");
		BOOK_ABBREV_MAP.put(BookID.BOOK_1Sam, "1Sa");
		BOOK_ABBREV_MAP.put(BookID.BOOK_2Sam, "2Sa");
		BOOK_ABBREV_MAP.put(BookID.BOOK_1Kgs, "1Ki");
		BOOK_ABBREV_MAP.put(BookID.BOOK_2Kgs, "2Ki");
		BOOK_ABBREV_MAP.put(BookID.BOOK_1Chr, "1Ch");
		BOOK_ABBREV_MAP.put(BookID.BOOK_2Chr, "2Ch");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Ezra, "Ezr");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Neh, "Ne");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Esth, "Es");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Job, "Job");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Ps, "Ps");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Prov, "Pr");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Eccl, "Ec");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Song, "So");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Isa, "Isa");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Jer, "Jer");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Lam, "La");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Ezek, "Eze");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Dan, "Da");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Hos, "Ho");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Joel, "Joe");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Amos, "Am");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Obad, "Ob");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Jonah, "Jon");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Mic, "Mic");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Nah, "Na");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Hab, "Hab");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Zeph, "Zep");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Hag, "Hag");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Zech, "Zec");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Mal, "Mal");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Matt, "Mt");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Mark, "Mr");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Luke, "Lu");
		BOOK_ABBREV_MAP.put(BookID.BOOK_John, "Joh");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Acts, "Ac");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Rom, "Ro");
		BOOK_ABBREV_MAP.put(BookID.BOOK_1Cor, "1Co");
		BOOK_ABBREV_MAP.put(BookID.BOOK_2Cor, "2Co");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Gal, "Ga");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Eph, "Eph");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Phil, "Php");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Col, "Col");
		BOOK_ABBREV_MAP.put(BookID.BOOK_1Thess, "1Th");
		BOOK_ABBREV_MAP.put(BookID.BOOK_2Thess, "2Th");
		BOOK_ABBREV_MAP.put(BookID.BOOK_1Tim, "1Ti");
		BOOK_ABBREV_MAP.put(BookID.BOOK_2Tim, "2Ti");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Titus, "Tit");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Phlm, "Phm");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Heb, "Heb");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Jas, "Jas");
		BOOK_ABBREV_MAP.put(BookID.BOOK_1Pet, "1Pe");
		BOOK_ABBREV_MAP.put(BookID.BOOK_2Pet, "2Pe");
		BOOK_ABBREV_MAP.put(BookID.BOOK_1John, "1Jo");
		BOOK_ABBREV_MAP.put(BookID.BOOK_2John, "2Jo");
		BOOK_ABBREV_MAP.put(BookID.BOOK_3John, "3Jo");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Jude, "Jude");
		BOOK_ABBREV_MAP.put(BookID.BOOK_Rev, "Re");
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		IncludedFeatures features = new IncludedFeatures();
		for (Book book : bible.getBooks()) {
			if (!BOOK_ABBREV_MAP.containsKey(book.getId())) {
				continue;
			}
			for (Chapter chapter : book.getChapters()) {
				for (Verse vv : chapter.getVerses()) {
					vv.accept(new VisitorAdapter<RuntimeException>(null) {
						@Override
						protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) {
							return this;
						}

						@Override
						public Visitor<RuntimeException> visitFootnote(boolean ofCrossReferences) {
							features.hasFootnotes = true;
							return null;
						}

						@Override
						public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) {
							if (kind == FormattingInstructionKind.ITALIC || kind == FormattingInstructionKind.BOLD || kind == FormattingInstructionKind.ADDITION || kind == FormattingInstructionKind.PSALM_DESCRIPTIVE_TITLE) {
								features.hasItalics = true;
								return null;
							} else if (kind == FormattingInstructionKind.WORDS_OF_JESUS) {
								features.hasRedLetter = true;
								return null;
							}
							return this;
						}

						@Override
						public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
							return null;
						}
					});
				}
			}
		}

		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(exportArgs[0])), StandardCharsets.UTF_8))) {
			bw.write("\uFEFF; User Bible\n; TITLE: " + bible.getName() + "\n; ABBREVIATION: BMC\n" + features.getHeader() + "\n");
			for (Book book : bible.getBooks()) {
				String abbr = BOOK_ABBREV_MAP.get(book.getId());
				if (abbr == null) {
					System.out.println("WARNING: Skipping book " + book.getAbbr());
					continue;
				}
				int cnumber = 0;
				int[] verseCounts = StandardVersification.KJV.getVerseCount(book.getId());
				for (Chapter chapter : book.getChapters()) {
					cnumber++;
					if (cnumber > verseCounts.length) {
						System.out.println("WARNING: Skipping chapter " + book.getAbbr() + " " + cnumber);
						continue;
					}
					int maxVerse = verseCounts[cnumber - 1];
					BitSet allowedNumbers = new BitSet(maxVerse + 1);
					allowedNumbers.set(1, maxVerse + 1);
					for (VirtualVerse vv : chapter.createVirtualVerses(false, allowedNumbers, false)) {
						bw.write("$$ " + abbr + " " + cnumber + ":" + vv.getNumber() + "\n");
						boolean firstVerse = true;
						for (Verse v : vv.getVerses()) {
							if (!firstVerse || !v.getNumber().equals("" + vv.getNumber())) {
								bw.write(" (" + v.getNumber() + ") ");
							}
							v.accept(new SwordSearcherVisitor(bw, features, true, ""));
							firstVerse = false;
						}
						bw.write('\n');
					}
				}
			}
		}
	}

	private static class IncludedFeatures {
		private boolean hasItalics, hasFootnotes, hasRedLetter;

		private String escapeText(String text) {
			text = text.replace("&", "&amp").replace("<", "&lt").replace(">", "&gt");
			if (hasItalics)
				text = text.replace("[", "&#91").replace("]", "&#93");
			if (hasFootnotes)
				text = text.replace("{", "&#123").replace("}", "&#125");
			if (hasRedLetter)
				text = text.replace("/", "&#47");
			return text;
		}

		private String getHeader() {
			return (hasItalics ? "; HAS ITALICS\n" : "") +
					(hasFootnotes ? "; HAS FOOTNOTES\n" : "") +
					(hasRedLetter ? "; HAS REDLETTER\n" : "");
		}
	}

	private static class SwordSearcherVisitor extends AbstractNoCSSVisitor<IOException> {

		private final BufferedWriter bw;
		private final IncludedFeatures features;
		private final boolean allowFormatting;
		private final String suffix;

		public SwordSearcherVisitor(BufferedWriter bw, IncludedFeatures features, boolean allowFormatting, String suffix) {
			this.bw = bw;
			this.features = features;
			this.allowFormatting = allowFormatting;
			this.suffix = suffix;
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			return 0;
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			return null;
		}

		@Override
		public void visitStart() throws IOException {
		}

		@Override
		public void visitText(String text) throws IOException {
			bw.write(features.escapeText(text));
		}

		@Override
		public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
			Visitor<IOException> result = visitFootnote0();
			if (result != null && ofCrossReferences)
				result.visitText(FormattedText.XREF_MARKER);
			return result;
		}

		public Visitor<IOException> visitFootnote0() throws IOException {
			if (!allowFormatting && suffix.equals("]")) {
				bw.write("]{");
				return new SwordSearcherVisitor(bw, features, false, "}[");
			}
			if (!allowFormatting) {
				System.out.println("WARNING: Skipping footnote within formatted text");
				return null;
			}
			bw.write("{");
			return new SwordSearcherVisitor(bw, features, false, "}");
		}

		@Override
		public Visitor<IOException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws IOException {
			return new SwordSearcherVisitor(bw, features, allowFormatting, "");
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			if (allowFormatting && (kind == FormattingInstructionKind.BOLD || kind == FormattingInstructionKind.ITALIC || kind == FormattingInstructionKind.ADDITION || kind == FormattingInstructionKind.PSALM_DESCRIPTIVE_TITLE)) {
				bw.write("[");
				return new SwordSearcherVisitor(bw, features, false, "]");
			}
			if (allowFormatting && (kind == FormattingInstructionKind.WORDS_OF_JESUS)) {
				bw.write("+r/");
				return new SwordSearcherVisitor(bw, features, false, "-r/");
			}
			return new SwordSearcherVisitor(bw, features, allowFormatting, "");
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			List<FormattingInstructionKind> formattings = new ArrayList<>();
			determineFormattingInstructions(css, formattings);
			StringBuilder suffix = new StringBuilder();
			for (FormattingInstructionKind kind : formattings) {
				SwordSearcherVisitor v = (SwordSearcherVisitor) visitFormattingInstruction(kind);
				suffix.append(v.suffix);
			}
			return new SwordSearcherVisitor(bw, features, allowFormatting, suffix.toString());
		}

		@Override
		protected Visitor<IOException> visitChangedCSSFormatting(String remainingCSS, Visitor<IOException> resultingVisitor, int replacements) {
			throw new IllegalStateException();
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			visitText("/");
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind lbk, int indent) throws IOException {
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws IOException {
			return new SwordSearcherVisitor(bw, features, allowFormatting, "");
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			return new SwordSearcherVisitor(bw, features, allowFormatting, "");
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			throw new RuntimeException("Variations are not supported");
		}

		@Override
		public Visitor<IOException> visitSpeaker(String labelOrStrongs) throws IOException {
			return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "speaker", labelOrStrongs);
		}

		@Override
		public Visitor<IOException> visitHyperlink(HyperlinkType type, String target) throws IOException {
			return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "hyperlink", type.toString());
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			return prio.handleVisitor(category, new SwordSearcherVisitor(bw, features, allowFormatting, ""));
		}

		@Override
		public boolean visitEnd() throws IOException {
			bw.write(suffix);
			return false;
		}
	}
}
