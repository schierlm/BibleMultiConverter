package biblemulticonverter.format.paratext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.BookID;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Text;
import biblemulticonverter.format.paratext.ParatextCharacterContent.VerseEnd;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.Version;
import biblemulticonverter.format.paratext.utilities.TagParser;

/**
 * Represents a bible book in a Paratext format.
 */
public class ParatextBook {

	private final ParatextID id;
	private final String bibleName;
	private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
	private final List<ParatextBookContentPart> content = new ArrayList<>();

	public ParatextBook(ParatextID id, String bibleName) {
		this.id = id;
		this.bibleName = bibleName;
	}

	public ParatextID getId() {
		return id;
	}

	public String getBibleName() {
		return bibleName;
	}

	public LinkedHashMap<String, String> getAttributes() {
		return attributes;
	}

	public List<ParatextBookContentPart> getContent() {
		return content;
	}

	/**
	 * Finds the last instance of the given {@link ParatextBookContentPart} type in the content of this book.
	 *
	 * @return the last instance of the given type or null if not found.
	 */
	public <T extends ParatextBookContentPart> T findLastBookContent(Class<T> type) {
		ListIterator<ParatextBookContentPart> iterator = content.listIterator(content.size());
		while (iterator.hasPrevious()) {
			ParatextBookContentPart part = iterator.previous();
			if (type.isInstance(part)) {
				return (T) part;
			}
		}
		return null;
	}

	/**
	 * Finds all instances of the given {@link ParatextCharacterContentPart} type in this {@link
	 * ParatextBook} and nested {@link ParatextCharacterContentContainer ParatextCharaterContentContainers} if any.
	 *
	 * @return list of all found instances or null if no instances where found.
	 */
	public <T extends ParatextCharacterContentPart> List<T> findAllCharacterContent(Class<T> type) {
		List<T> result = null;
		for (ParatextBookContentPart part : content) {
			if (part instanceof ParatextCharacterContentContainer) {
				ParatextCharacterContentContainer content = ((ParatextCharacterContentContainer) part);
				List<T> nestedResults = content.findAll(type);
				if (nestedResults != null) {
					if (result == null) {
						result = nestedResults;
					} else {
						result.addAll(nestedResults);
					}
				}
			}
		}
		return result;
	}

	/**
	 * Finds the last instance of the given {@link ParatextCharacterContentPart} type in the content of this book.
	 *
	 * @return the last instance of the given type or null if not found.
	 */
	public <T extends ParatextCharacterContentPart> T findLastCharacterContent(Class<T> type) {
		ListIterator<ParatextBookContentPart> iterator = content.listIterator(content.size());
		while (iterator.hasPrevious()) {
			ParatextBookContentPart part = iterator.previous();
			if (part instanceof ParatextCharacterContentContainer) {
				final ParatextCharacterContentContainer content = ((ParatextCharacterContentContainer) part);
				final T nestedPart = content.findLastContent(type);
				if (nestedPart != null) {
					return nestedPart;
				}
			}
		}
		return null;
	}

	public <T extends Throwable> void accept(ParatextBookContentVisitor<T> v) throws T {
		for (ParatextBookContentPart p : content) {
			p.acceptThis(v);
		}
	}

	/**
	 * Before converting to non-Paratext format, remove (unformatted) whitespace
	 * at the end of a verse. Also move whitespace at the end of character
	 * content outside of it. This can be introduced by importing USX2 bibles.
	 * The whitespace should remain when converting to other Paratext formats,
	 * therefore only strip it when converting to non-Paratext formats.
	 */
	public void fixTrailingWhitespace() {
		boolean seenVerseEnd = false;
		for (int i = content.size() - 1; i >= 0; i--) {
			if (content.get(i) instanceof ParatextCharacterContent) {
				ParatextCharacterContent cc = (ParatextCharacterContent) content.get(i);
				fixTrailingWhitespace(cc);
				for (int j = cc.getContent().size() - 1; j >= 0; j--) {
					if (seenVerseEnd && cc.getContent().get(j) instanceof Text) {
						Text oldText = (Text) cc.getContent().get(j);
						if (oldText.getChars().matches(" +")) {
							cc.getContent().remove(j);
						} else {
							cc.getContent().set(j, Text.from(oldText.getChars().replaceFirst(" +$", "")));
						}
					}
					seenVerseEnd = j < cc.getContent().size() && (cc.getContent().get(j) instanceof VerseEnd);
				}
			} else {
				seenVerseEnd = content.get(i) instanceof TableCellStart;
			}
		}
	}

	private void fixTrailingWhitespace(ParatextCharacterContentContainer cc) {
		for (int i = 0; i < cc.getContent().size(); i++) {
			if (cc.getContent().get(i) instanceof ParatextCharacterContentContainer) {
				fixTrailingWhitespace((ParatextCharacterContentContainer) cc.getContent().get(i));
			}
			if (cc.getContent().get(i) instanceof AutoClosingFormatting) {
				String suffix = extractTrailingWhitespace((AutoClosingFormatting) cc.getContent().get(i));
				if (!suffix.isEmpty()) {
					cc.getContent().add(i + 1, Text.from(suffix));
				}
			}
		}
	}

	private String extractTrailingWhitespace(AutoClosingFormatting container) {
		if(container.getContent().isEmpty()) {
			System.out.println("WARNING: Trying to extract whitespace from empty container tag");
			return "";
		}
		ParatextCharacterContentPart last = container.getContent().get(container.getContent().size() - 1);
		if (last instanceof AutoClosingFormatting) {
			return extractTrailingWhitespace((AutoClosingFormatting) last);
		} else if (last instanceof Text) {
			String oldText = ((Text) last).getChars();
			String newText = oldText.replaceFirst(" +$", "");
			if (newText.isEmpty()) {
				container.getContent().remove(container.getContent().size()-1);
				return oldText;
			}
			if (!newText.equals(oldText)) {
				container.getContent().set(container.getContent().size() - 1, Text.from(newText));
				return oldText.substring(newText.length());
			}
		}
		return "";
	}

	/**
	 * One of {@link ChapterStart}, {@link ChapterEnd}, {@link ParagraphStart}, {@link TableCellStart} or
	 * {@link ParatextCharacterContent}.
	 */
	public static interface ParatextBookContentPart {
		public <T extends Throwable> void acceptThis(ParatextBookContentVisitor<T> v) throws T;
	}

	public static class ChapterStart implements ParatextBookContentPart {
		private final ChapterIdentifier location;

		public ChapterStart(ChapterIdentifier location) {
			this.location = location;
		}

		public int getChapter() {
			return location.chapter;
		}

		public ChapterIdentifier getLocation() {
			return location;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextBookContentVisitor<T> v) throws T {
			v.visitChapterStart(location);
		}
	}

	public static class ChapterEnd implements ParatextBookContentPart {
		private final ChapterIdentifier location;

		public ChapterEnd(ChapterIdentifier location) {
			this.location = location;
		}

		public ChapterIdentifier getLocation() {
			return location;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextBookContentVisitor<T> v) throws T {
			v.visitChapterEnd(location);
		}
	}

	public static class ParagraphStart implements ParatextBookContentPart {
		private final ParagraphKind kind;

		public ParagraphStart(ParagraphKind kind) {
			this.kind = kind;
		}

		public ParagraphKind getKind() {
			return kind;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextBookContentVisitor<T> v) throws T {
			v.visitParagraphStart(kind);
		}
	}

	public static enum ParagraphKindCategory {
		TEXT, HEADLINE, TABLE_ROW, VERTICAL_WHITE_SPACE, SKIP
	}

	public static enum ParagraphKind {

		//@formatter:off

		INTRO_MAJOR_TITLE(Version.V1, true, ParagraphKindCategory.HEADLINE, "imt", 1, true, null),
		INTRO_MAJOR_TITLE_1(Version.V1, true, ParagraphKindCategory.HEADLINE, "imt1", 1, true, null),
		INTRO_MAJOR_TITLE_2(Version.V1, true, ParagraphKindCategory.HEADLINE, "imt2", 1, true, null),
		INTRO_MAJOR_TITLE_3(Version.V1, true, ParagraphKindCategory.HEADLINE, "imt3", 1, true, null),
		INTRO_MAJOR_TITLE_4(Version.V1, true, ParagraphKindCategory.HEADLINE, "imt4", 1, true, null),
		INTRO_SECTION(Version.V1, true, ParagraphKindCategory.HEADLINE, "is", 2, false, null),
		INTRO_SECTION_1(Version.V1, true, ParagraphKindCategory.HEADLINE, "is1", 2, false, null),
		INTRO_SECTION_2(Version.V1, true, ParagraphKindCategory.HEADLINE, "is2", 3, false, null),
		INTRO_SECTION_3(Version.V1, true, ParagraphKindCategory.HEADLINE, "is3", 4, false, null),
		INTRO_SECTION_4(Version.V1, true, ParagraphKindCategory.HEADLINE, "is4", 5, false, null),
		INTRO_SECTION_5(Version.V1, true, ParagraphKindCategory.HEADLINE, "is5", 6, false, null),
		INTRO_SECTION_6(Version.V1, true, ParagraphKindCategory.HEADLINE, "is6", 7, false, null),
		INTRO_SECTION_7(Version.V1, true, ParagraphKindCategory.HEADLINE, "is7", 8, false, null),
		INTRO_SECTION_8(Version.V1, true, ParagraphKindCategory.HEADLINE, "is8", 9, false, null),

		INTRO_PARAGRAPH_P(Version.V1, true, ParagraphKindCategory.TEXT, "ip", 0, false, null),
		INTRO_PARAGRAPH_M(Version.V1, true, ParagraphKindCategory.TEXT, "im", 0, false, null),
		INTRO_PARAGRAPH_PI(Version.V1, true, ParagraphKindCategory.TEXT, "ipi", 0, false, null),
		INTRO_PARAGRAPH_MI(Version.V1, true, ParagraphKindCategory.TEXT, "imi", 0, false, null),
		INTRO_PARAGRAPH_PQ(Version.V1, true, ParagraphKindCategory.TEXT, "ipq", 0, false, null),
		INTRO_PARAGRAPH_MQ(Version.V1, true, ParagraphKindCategory.TEXT, "imq", 0, false, null),
		INTRO_PARAGRAPH_PR(Version.V1, true, ParagraphKindCategory.TEXT, "ipr", 0, false, null),
		INTRO_PARAGRAPH_Q(Version.V1, true, ParagraphKindCategory.TEXT, "iq", 0, false, null),
		INTRO_PARAGRAPH_Q1(Version.V1, true, ParagraphKindCategory.TEXT, "iq1", 0, false, null),
		INTRO_PARAGRAPH_Q2(Version.V1, true, ParagraphKindCategory.TEXT, "iq2", 0, false, null),
		INTRO_PARAGRAPH_Q3(Version.V1, true, ParagraphKindCategory.TEXT, "iq3", 0, false, null),
		INTRO_PARAGRAPH_Q4(Version.V1, true, ParagraphKindCategory.TEXT, "iq4", 0, false, null),
		INTRO_BLANK_LINE(Version.V1, true, ParagraphKindCategory.VERTICAL_WHITE_SPACE, "ib", 0, false, null),
		INTRO_PARAGRAPH_LI(Version.V1, true, ParagraphKindCategory.TEXT, "ili", 0, false, null),
		INTRO_PARAGRAPH_LI1(Version.V1, true, ParagraphKindCategory.TEXT, "ili1", 0, false, null),
		INTRO_PARAGRAPH_LI2(Version.V1, true, ParagraphKindCategory.TEXT, "ili2", 0, false, null),
		INTRO_PARAGRAPH_LI3(Version.V1, true, ParagraphKindCategory.TEXT, "ili3", 0, false, null),
		INTRO_PARAGRAPH_LI4(Version.V1, true, ParagraphKindCategory.TEXT, "ili4", 0, false, null),
		INTRO_OUTLINE_TITLE(Version.V1, true, ParagraphKindCategory.HEADLINE, "iot", 7, false, null),
		INTRO_OUTLINE(Version.V1, true, ParagraphKindCategory.TEXT, "io", 0, false, null),
		INTRO_OUTLINE_1(Version.V1, true, ParagraphKindCategory.TEXT, "io1", 0, false, null),
		INTRO_OUTLINE_2(Version.V1, true, ParagraphKindCategory.TEXT, "io2", 0, false, null),
		INTRO_OUTLINE_3(Version.V1, true, ParagraphKindCategory.TEXT, "io3", 0, false, null),
		INTRO_OUTLINE_4(Version.V1, true, ParagraphKindCategory.TEXT, "io4", 0, false, null),
		INTRO_EXPLANATORY(Version.V1, true, ParagraphKindCategory.TEXT, "iex", 0, false, null),

		INTRO_MAJOR_TITLE_ENDING(Version.V1, true, ParagraphKindCategory.HEADLINE, "imte", 9, true, null),
		INTRO_MAJOR_TITLE_ENDING_1(Version.V1, true, ParagraphKindCategory.HEADLINE, "imte1", 9, true, null),
		INTRO_MAJOR_TITLE_ENDING_2(Version.V1, true, ParagraphKindCategory.HEADLINE, "imte2", 9, true, null),
		INTRO_MAJOR_TITLE_ENDING_3(Version.V1, true, ParagraphKindCategory.HEADLINE, "imte3", 9, true, null),
		INTRO_MAJOR_TITLE_ENDING_4(Version.V1, true, ParagraphKindCategory.HEADLINE, "imte4", 9, true, null),
		INTRO_END(Version.V1, true, ParagraphKindCategory.SKIP, "ie", 0, false, null),

		CHAPTER_DESCRIPTION(Version.V1, true, ParagraphKindCategory.TEXT, "cd", 0, false, null),

		MAJOR_TITLE(Version.V1, false, ParagraphKindCategory.HEADLINE, "mt", 1, true, null),
		MAJOR_TITLE_1(Version.V1, false, ParagraphKindCategory.HEADLINE, "mt1", 1, true, null),
		MAJOR_TITLE_2(Version.V1, false, ParagraphKindCategory.HEADLINE, "mt2", 1, true, null),
		MAJOR_TITLE_3(Version.V1, false, ParagraphKindCategory.HEADLINE, "mt3", 1, true, null),
		MAJOR_TITLE_4(Version.V1, false, ParagraphKindCategory.HEADLINE, "mt4", 1, true, null),
		MAJOR_TITLE_ENDING(Version.V1, false, ParagraphKindCategory.HEADLINE, "mte", 9, true, null),
		MAJOR_TITLE_ENDING_1(Version.V1, false, ParagraphKindCategory.HEADLINE, "mte1", 9, true, null),
		MAJOR_TITLE_ENDING_2(Version.V1, false, ParagraphKindCategory.HEADLINE, "mte2", 9, true, null),
		MAJOR_TITLE_ENDING_3(Version.V1, false, ParagraphKindCategory.HEADLINE, "mte3", 9, true, null),
		MAJOR_TITLE_ENDING_4(Version.V1, false, ParagraphKindCategory.HEADLINE, "mte4", 9, true, null),
		MAJOR_SECTION(Version.V1, false, ParagraphKindCategory.HEADLINE, "ms", 2, false, null),
		MAJOR_SECTION_1(Version.V1, false, ParagraphKindCategory.HEADLINE, "ms1", 2, false, null),
		MAJOR_SECTION_2(Version.V1, false, ParagraphKindCategory.HEADLINE, "ms2", 3, false, null),
		MAJOR_SECTION_3(Version.V1, false, ParagraphKindCategory.HEADLINE, "ms3", 4, false, null),
		MAJOR_SECTION_4(Version.V1, false, ParagraphKindCategory.HEADLINE, "ms4", 5, false, null),
		MAJOR_SECTION_5(Version.V1, false, ParagraphKindCategory.HEADLINE, "ms5", 6, false, null),
		MAJOR_SECTION_REFERENCE(Version.V1, false, ParagraphKindCategory.HEADLINE, "mr", 0, true, FormattingInstructionKind.ITALIC),
		SECTION(Version.V1, false, ParagraphKindCategory.HEADLINE, "s", 5, false, null),
		SECTION_1(Version.V1, false, ParagraphKindCategory.HEADLINE, "s1", 5, false, null),
		SECTION_2(Version.V1, false, ParagraphKindCategory.HEADLINE, "s2", 6, false, null),
		SECTION_3(Version.V1, false, ParagraphKindCategory.HEADLINE, "s3", 7, false, null),
		SECTION_4(Version.V1, false, ParagraphKindCategory.HEADLINE, "s4", 8, false, null),
		SECTION_5(Version.V1, false, ParagraphKindCategory.HEADLINE, "s5", 9, false, null),
		SECTION_REFERENCE(Version.V2, false, ParagraphKindCategory.HEADLINE, "sr", 0, true, FormattingInstructionKind.ITALIC),
		PARALLEL_PASSAGE_REFERENCE(Version.V1, false, ParagraphKindCategory.HEADLINE, "r", 9, false, FormattingInstructionKind.ITALIC),
		DESCRIPTIVE_TITLE(Version.V1, false, ParagraphKindCategory.TEXT, "d", 0, false, null),
		HEBREW_NOTE(Version.V3, false, ParagraphKindCategory.HEADLINE, "qd", 0, false, FormattingInstructionKind.ITALIC),

		SPEAKER_TITLE(Version.V1, false, ParagraphKindCategory.HEADLINE, "sp", 9, false, FormattingInstructionKind.BOLD),
		SEMANTIC_DIVISION(Version.V3, false, ParagraphKindCategory.VERTICAL_WHITE_SPACE, "sd", 0, false, null),
		SEMANTIC_DIVISION_1(Version.V3, false, ParagraphKindCategory.VERTICAL_WHITE_SPACE, "sd1", 0, false, null),
		SEMANTIC_DIVISION_2(Version.V3, false, ParagraphKindCategory.VERTICAL_WHITE_SPACE, "sd2", 0, false, null),
		SEMANTIC_DIVISION_3(Version.V3, false, ParagraphKindCategory.VERTICAL_WHITE_SPACE, "sd3", 0, false, null),
		SEMANTIC_DIVISION_4(Version.V3, false, ParagraphKindCategory.VERTICAL_WHITE_SPACE, "sd4", 0, false, null),

		PARAGRAPH_P(Version.V1, false, ParagraphKindCategory.TEXT, "p", 0, false, null),
		PARAGRAPH_P1(Version.V1, false, ParagraphKindCategory.TEXT, "p1", 0, false, null),
		PARAGRAPH_P2(Version.V1, false, ParagraphKindCategory.TEXT, "p2", 0, false, null),
		PARAGRAPH_M(Version.V1, false, ParagraphKindCategory.TEXT, "m", 0, false, null),
		PARAGRAPH_PO(Version.V3, false, ParagraphKindCategory.TEXT, "po", 0, false, null),
		PARAGRAPH_PMO(Version.V2, false, ParagraphKindCategory.TEXT, "pmo", 0, false, null),
		PARAGRAPH_PM(Version.V2, false, ParagraphKindCategory.TEXT, "pm", 0, false, null),
		PARAGRAPH_PMC(Version.V2, false, ParagraphKindCategory.TEXT, "pmc", 0, false, null),
		PARAGRAPH_PMR(Version.V2, false, ParagraphKindCategory.TEXT, "pmr", 0, false, null),
		PARAGRAPH_PI(Version.V1, false, ParagraphKindCategory.TEXT, "pi", 0, false, null),
		PARAGRAPH_PI1(Version.V1, false, ParagraphKindCategory.TEXT, "pi1", 0, false, null),
		PARAGRAPH_PI2(Version.V1, false, ParagraphKindCategory.TEXT, "pi2", 0, false, null),
		PARAGRAPH_PI3(Version.V1, false, ParagraphKindCategory.TEXT, "pi3", 0, false, null),
		PARAGRAPH_PI4(Version.V1, false, ParagraphKindCategory.TEXT, "pi4", 0, false, null),
		PARAGRAPH_MI(Version.V1, false, ParagraphKindCategory.TEXT, "mi", 0, false, null),
		NO_BREAK_AT_START_OF_CHAPTER(Version.V1, false, ParagraphKindCategory.TEXT, "nb", 0, false, null),
		PARAGRAPH_CLOSING(Version.V1, false, ParagraphKindCategory.TEXT, "cls", 0, false, null),
		PARAGRAPH_LH(Version.V3, false, ParagraphKindCategory.TEXT, "lh", 0, false, null),
		PARAGRAPH_LI(Version.V1, false, ParagraphKindCategory.TEXT, "li", 0, false, null),
		PARAGRAPH_LI1(Version.V1, false, ParagraphKindCategory.TEXT, "li1", 0, false, null),
		PARAGRAPH_LI2(Version.V1, false, ParagraphKindCategory.TEXT, "li2", 0, false, null),
		PARAGRAPH_LI3(Version.V1, false, ParagraphKindCategory.TEXT, "li3", 0, false, null),
		PARAGRAPH_LI4(Version.V1, false, ParagraphKindCategory.TEXT, "li4", 0, false, null),
		PARAGRAPH_LIM(Version.V3, false, ParagraphKindCategory.TEXT, "lim", 0, false, null),
		PARAGRAPH_LIM1(Version.V3, false, ParagraphKindCategory.TEXT, "lim1", 0, false, null),
		PARAGRAPH_LIM2(Version.V3, false, ParagraphKindCategory.TEXT, "lim2", 0, false, null),
		PARAGRAPH_LIM3(Version.V3, false, ParagraphKindCategory.TEXT, "lim3", 0, false, null),
		PARAGRAPH_LIM4(Version.V3, false, ParagraphKindCategory.TEXT, "lim4", 0, false, null),
		PARAGRAPH_LF(Version.V3, false, ParagraphKindCategory.TEXT, "lf", 0, false, null),
		PARAGRAPH_CENTERED(Version.V1, false, ParagraphKindCategory.TEXT, "pc", 0, false, null),

		// PARAGRAPH_RIGHT was added in USFM/USX 1.0, deprecated in USFM/USX 2.0, but it has been restored in USFM/USX 3.0.
		PARAGRAPH_RIGHT(Version.V1, false, ParagraphKindCategory.TEXT, "pr", 0, false, null),
		PARAGRAPH_HANGING(Version.V1, false, ParagraphKindCategory.TEXT, "ph", 0, false, null),
		PARAGRAPH_HANGING1(Version.V1, false, ParagraphKindCategory.TEXT, "ph1", 0, false, null),
		PARAGRAPH_HANGING2(Version.V1, false, ParagraphKindCategory.TEXT, "ph2", 0, false, null),
		PARAGRAPH_HANGING3(Version.V1, false, ParagraphKindCategory.TEXT, "ph3", 0, false, null),
		PARAGRAPH_HANGING4(Version.V1, false, ParagraphKindCategory.TEXT, "ph4", 0, false, null),
		PARAGRAPH_LITURGICAL(Version.V1, false, ParagraphKindCategory.TEXT, "lit", 0, false, null),
		BLANK_LINE(Version.V1, false, ParagraphKindCategory.VERTICAL_WHITE_SPACE, "b", 0, false, null),
		PAGE_BREAK(Version.V1, false, ParagraphKindCategory.SKIP, "pb", 0, false, null),
		PARAGRAPH_Q(Version.V1, false, ParagraphKindCategory.TEXT, "q", 0, false, null),
		PARAGRAPH_Q1(Version.V1, false, ParagraphKindCategory.TEXT, "q1", 0, false, null),
		PARAGRAPH_Q2(Version.V1, false, ParagraphKindCategory.TEXT, "q2", 0, false, null),
		PARAGRAPH_Q3(Version.V1, false, ParagraphKindCategory.TEXT, "q3", 0, false, null),
		PARAGRAPH_Q4(Version.V1, false, ParagraphKindCategory.TEXT, "q4", 0, false, null),
		PARAGRAPH_QR(Version.V1, false, ParagraphKindCategory.TEXT, "qr", 0, false, null),
		PARAGRAPH_QC(Version.V1, false, ParagraphKindCategory.TEXT, "qc", 0, false, null),
		PARAGRAPH_QA(Version.V1, true, ParagraphKindCategory.HEADLINE, "qa", 9, false, FormattingInstructionKind.ITALIC),
		PARAGRAPH_QM(Version.V2, false, ParagraphKindCategory.TEXT, "qm", 0, false, null),
		PARAGRAPH_QM1(Version.V2, false, ParagraphKindCategory.TEXT, "qm1", 0, false, null),
		PARAGRAPH_QM2(Version.V2, false, ParagraphKindCategory.TEXT, "qm2", 0, false, null),
		PARAGRAPH_QM3(Version.V2, false, ParagraphKindCategory.TEXT, "qm3", 0, false, null),
		PARAGRAPH_QM4(Version.V2, false, ParagraphKindCategory.TEXT, "qm4", 0, false, null),

		TABLE_ROW(Version.V1, false, ParagraphKindCategory.TABLE_ROW, "tr", 0, false, null),

		PERIPHERALS(Version.V1, false, ParagraphKindCategory.HEADLINE, "periph", 1, false, null);

		//@formatter:on

		private final Version since;
		/**
		 * The value of a numbered marker, if the marker is not numbered this value will be -1.
		 */
		private final int number;
		private final String baseTag;
		private final boolean prolog;
		private final ParagraphKindCategory category;
		private final String tag;
		private final int headlineDepth;
		private final boolean joinHeadlines;
		private final FormattingInstructionKind extraFormatting;

		private ParagraphKind(Version since, boolean prolog, ParagraphKindCategory category, String tag, int headlineDepth, boolean joinHeadlines, FormattingInstructionKind extraFormatting) {
			this.since = since;
			this.prolog = prolog;
			this.category = category;
			this.tag = tag;
			this.headlineDepth = headlineDepth;
			this.joinHeadlines = joinHeadlines;
			this.extraFormatting = extraFormatting;
			TagParser parser = new TagParser();
			parser.parse(tag);
			this.number = parser.getNumber();
			this.baseTag = parser.getTag();
		}

		public Version getVersion() {
			return since;
		}

		public boolean isProlog() {
			return prolog;
		}

		public ParagraphKindCategory getCategory() {
			return category;
		}

		public String getTag() {
			return tag;
		}

		/**
		 * True if this ParagraphKind has the same base tag as the given ParagraphKind. A base tag is the
		 * ParagraphKind's tag without the number part.
		 */
		public boolean isSameBase(ParagraphKind kind) {
			return baseTag.equals(kind.baseTag);
		}

		/**
		 * Returns this tags number.
		 *
		 * @return the tags number or -1 if the tag does not have a number.
		 */
		public int getNumber() {
			return number;
		}

		public int getHeadlineDepth() {
			return headlineDepth;
		}

		public boolean isJoinHeadlines() {
			return joinHeadlines;
		}

		public FormattingInstructionKind getExtraFormatting() {
			return extraFormatting;
		}

		/**
		 * Returns the ParagraphKind for this ParagraphKind's tag but with the given number variation.
		 *
		 * @throws IllegalArgumentException if there is no ParagraphKind with this ParagraphKind's tag and the given
		 *                                  number variation.
		 */
		public ParagraphKind getWithNumber(int number) {
			ParagraphKind kind = getFromTag(baseTag + (number > 0 ? number : ""));
			if (kind == null) {
				throw new IllegalArgumentException("ParagraphKind " + this + " does not have a variation with number " + number);
			}
			return kind;
		}

		/**
		 * Gets the ParagraphKind that represents the given tag.
		 *
		 * @param tag the tag to get the ParagraphKind for, this may be a tag without number part ({@code q}), or with
		 *            number part ({@code q1}).
		 * @return the ParagraphKind that represents the given tag or null if the given tag is not a known
		 * ParagraphKind.
		 */
		public static ParagraphKind getFromTag(String tag) {
			for (ParagraphKind kind : values()) {
				if (kind.tag.equals(tag)) {
					return kind;
				}
			}
			return null;
		}

		public static Map<String, ParagraphKind> allTags() {
			Map<String, ParagraphKind> result = new HashMap<>();
			for (ParagraphKind kind : values()) {
				result.put(kind.tag, kind);
			}
			return result;
		}

		public static Set<ParagraphKind> allForVersion(Version version) {
			Set<ParagraphKind> result = EnumSet.noneOf(ParagraphKind.class);
			for (ParagraphKind kind : values()) {
				if (kind.since.isLowerOrEqualTo(version)) {
					result.add(kind);
				}
			}
			return result;
		}
	}

	public static class TableCellStart implements ParatextBookContentPart {
		private final String tag;

		public TableCellStart(String tag) {
			this.tag = tag;
		}

		public String getTag() {
			return tag;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextBookContentVisitor<T> v) throws T {
			v.visitTableCellStart(tag);
		}
	}

	public static interface ParatextBookContentVisitor<T extends Throwable> {
		public void visitChapterStart(ChapterIdentifier chapter) throws T;

		public void visitChapterEnd(ChapterIdentifier chapter) throws T;

		public void visitParagraphStart(ParagraphKind kind) throws T;

		public void visitTableCellStart(String tag) throws T;

		public void visitParatextCharacterContent(ParatextCharacterContent content) throws T;
	}

	/**
	 * A container that can include character content.
	 */
	public static interface ParatextCharacterContentContainer {
		public List<ParatextCharacterContentPart> getContent();

		/**
		 * Finds all instances of the given {@link ParatextCharacterContentPart} type in this {@link
		 * ParatextCharacterContentContainer} and nested ParatextCharacterContentContainers if any.
		 *
		 * @return list of all found instances or null if no instances where found.
		 */
		public default <T extends ParatextCharacterContentPart> List<T> findAll(Class<T> type) {
			List<T> result = null;
			for (ParatextCharacterContentPart part : getContent()) {

				// First search for `type` in nested containers
				if (part instanceof ParatextCharacterContentContainer) {
					List<T> nestedResults = ((ParatextCharacterContentContainer) part).findAll(type);
					if (nestedResults != null) {
						if (result == null) {
							result = nestedResults;
						} else {
							result.addAll(nestedResults);
						}
					}
				}
				// "part" can be both the `type` being searched for as well as a ParatextCharacterContentContainer,
				// hence two if-statements and not an if-else.
				if (type.isInstance(part)) {
					if (result == null) {
						result = new ArrayList<>();
					}
					result.add((T) part);
				}
			}
			return result;
		}

		/**
		 * Finds the last instance of the given ParatextCharacterContentPart type in the content of this
		 * {@link ParatextCharacterContentContainer}.
		 *
		 * @return the last instance of the given type or null if not found.
		 */
		public default <T extends ParatextCharacterContentPart> T findLastContent(Class<T> type) {
			final List<ParatextCharacterContentPart> content = getContent();
			final ListIterator<ParatextCharacterContentPart> iterator = content.listIterator(content.size());
			while (iterator.hasPrevious()) {
				ParatextCharacterContentPart part = iterator.previous();

				// First search for `type` in nested containers
				if (part instanceof ParatextCharacterContentContainer) {
					T nestedPart = ((ParatextCharacterContentContainer) part).findLastContent(type);
					if (nestedPart != null) {
						return nestedPart;
					}
				}
				// Not found in a nested container, check if this part is the `type` being searched for.
				if (type.isInstance(part)) {
					return (T) part;
				}
			}
			return null;
		}

		public default <T extends Throwable> void accept(ParatextCharacterContentVisitor<T> visitor) throws T {
			if (visitor == null)
				return;
			for (ParatextCharacterContentPart part : getContent()) {
				part.acceptThis(visitor);
			}
			visitor.visitEnd();
		}
	}

	public static enum ParatextID {

		// @formatter:off

		ID_FRT("A0", "FRT", "Front Matter", BookID.INTRODUCTION),
		ID_INT("A7", "INT", "Introduction Matter", BookID.INTRODUCTION_OT),

		// Old Testament
		ID_GEN("01", "GEN", "Genesis", BookID.BOOK_Gen),
		ID_EXO("02", "EXO", "Exodus", BookID.BOOK_Exod),
		ID_LEV("03", "LEV", "Leviticus", BookID.BOOK_Lev),
		ID_NUM("04", "NUM", "Numbers", BookID.BOOK_Num),
		ID_DEU("05", "DEU", "Deuteronomy", BookID.BOOK_Deut),
		ID_JOS("06", "JOS", "Joshua", BookID.BOOK_Josh),
		ID_JDG("07", "JDG", "Judges", BookID.BOOK_Judg),
		ID_RUT("08", "RUT", "Ruth", BookID.BOOK_Ruth),
		ID_1SA("09", "1SA", "1 Samuel", BookID.BOOK_1Sam),
		ID_2SA("10", "2SA", "2 Samuel", BookID.BOOK_2Sam),
		ID_1KI("11", "1KI", "1 Kings", BookID.BOOK_1Kgs),
		ID_2KI("12", "2KI", "2 Kings", BookID.BOOK_2Kgs),
		ID_1CH("13", "1CH", "1 Chronicles", BookID.BOOK_1Chr),
		ID_2CH("14", "2CH", "2 Chronicles", BookID.BOOK_2Chr),
		ID_EZR("15", "EZR", "Ezra", BookID.BOOK_Ezra),
		ID_NEH("16", "NEH", "Nehemiah", BookID.BOOK_Neh),
		ID_EST("17", "EST", "Esther (Hebrew)", BookID.BOOK_Esth),
		ID_JOB("18", "JOB", "Job", BookID.BOOK_Job),
		ID_PSA("19", "PSA", "Psalms", BookID.BOOK_Ps),
		ID_PRO("20", "PRO", "Proverbs", BookID.BOOK_Prov),
		ID_ECC("21", "ECC", "Ecclesiastes", BookID.BOOK_Eccl),
		ID_SNG("22", "SNG", "Song of Songs", BookID.BOOK_Song),
		ID_ISA("23", "ISA", "Isaiah", BookID.BOOK_Isa),
		ID_JER("24", "JER", "Jeremiah", BookID.BOOK_Jer),
		ID_LAM("25", "LAM", "Lamentations", BookID.BOOK_Lam),
		ID_EZK("26", "EZK", "Ezekiel", BookID.BOOK_Ezek),
		ID_DAN("27", "DAN", "Daniel (Hebrew)", BookID.BOOK_Dan),
		ID_HOS("28", "HOS", "Hosea", BookID.BOOK_Hos),
		ID_JOL("29", "JOL", "Joel", BookID.BOOK_Joel),
		ID_AMO("30", "AMO", "Amos", BookID.BOOK_Amos),
		ID_OBA("31", "OBA", "Obadiah", BookID.BOOK_Obad),
		ID_JON("32", "JON", "Jonah", BookID.BOOK_Jonah),
		ID_MIC("33", "MIC", "Micah", BookID.BOOK_Mic),
		ID_NAM("34", "NAM", "Nahum", BookID.BOOK_Nah),
		ID_HAB("35", "HAB", "Habakkuk", BookID.BOOK_Hab),
		ID_ZEP("36", "ZEP", "Zephaniah", BookID.BOOK_Zeph),
		ID_HAG("37", "HAG", "Haggai", BookID.BOOK_Hag),
		ID_ZEC("38", "ZEC", "Zechariah", BookID.BOOK_Zech),
		ID_MAL("39", "MAL", "Malachi", BookID.BOOK_Mal),

		// New Testament
		ID_MAT("41", "MAT", "Matthew", BookID.BOOK_Matt),
		ID_MRK("42", "MRK", "Mark", BookID.BOOK_Mark),
		ID_LUK("43", "LUK", "Luke", BookID.BOOK_Luke),
		ID_JHN("44", "JHN", "John", BookID.BOOK_John),
		ID_ACT("45", "ACT", "Acts", BookID.BOOK_Acts),
		ID_ROM("46", "ROM", "Romans", BookID.BOOK_Rom),
		ID_1CO("47", "1CO", "1 Corinthians", BookID.BOOK_1Cor),
		ID_2CO("48", "2CO", "2 Corinthians", BookID.BOOK_2Cor),
		ID_GAL("49", "GAL", "Galatians", BookID.BOOK_Gal),
		ID_EPH("50", "EPH", "Ephesians", BookID.BOOK_Eph),
		ID_PHP("51", "PHP", "Philippians", BookID.BOOK_Phil),
		ID_COL("52", "COL", "Colossians", BookID.BOOK_Col),
		ID_1TH("53", "1TH", "1 Thessalonians", BookID.BOOK_1Thess),
		ID_2TH("54", "2TH", "2 Thessalonians", BookID.BOOK_2Thess),
		ID_1TI("55", "1TI", "1 Timothy", BookID.BOOK_1Tim),
		ID_2TI("56", "2TI", "2 Timothy", BookID.BOOK_2Tim),
		ID_TIT("57", "TIT", "Titus", BookID.BOOK_Titus),
		ID_PHM("58", "PHM", "Philemon", BookID.BOOK_Phlm),
		ID_HEB("59", "HEB", "Hebrews", BookID.BOOK_Heb),
		ID_JAS("60", "JAS", "James", BookID.BOOK_Jas),
		ID_1PE("61", "1PE", "1 Peter", BookID.BOOK_1Pet),
		ID_2PE("62", "2PE", "2 Peter", BookID.BOOK_2Pet),
		ID_1JN("63", "1JN", "1 John", BookID.BOOK_1John),
		ID_2JN("64", "2JN", "2 John", BookID.BOOK_2John),
		ID_3JN("65", "3JN", "3 John", BookID.BOOK_3John),
		ID_JUD("66", "JUD", "Jude", BookID.BOOK_Jude),
		ID_REV("67", "REV", "Revelation", BookID.BOOK_Rev),

		// Apocrypha
		ID_TOB("68", "TOB", "Tobit", BookID.BOOK_Tob),
		ID_JDT("69", "JDT", "Judith", BookID.BOOK_Jdt),
		ID_ESG("70", "ESG", "Esther Greek", BookID.BOOK_EsthGr),
		ID_WIS("71", "WIS", "Wisdom of Solomon", BookID.BOOK_Wis),
		ID_SIR("72", "SIR", "Sirach", BookID.BOOK_Sir),
		ID_BAR("73", "BAR", "Baruch", BookID.BOOK_Bar),
		ID_LJE("74", "LJE", "Letter of Jeremiah", BookID.BOOK_EpJer),
		ID_S3Y("75", "S3Y", "Song of the 3 Young Men", BookID.BOOK_PrAzar),
		ID_SUS("76", "SUS", "Susanna", BookID.BOOK_Sus),
		ID_BEL("77", "BEL", "Bel and the Dragon", BookID.BOOK_Bel),
		ID_1MA("78", "1MA", "1 Maccabees", BookID.BOOK_1Macc),
		ID_2MA("79", "2MA", "2 Maccabees", BookID.BOOK_2Macc),
		ID_3MA("80", "3MA", "3 Maccabees", BookID.BOOK_3Macc),
		ID_4MA("81", "4MA", "4 Maccabees", BookID.BOOK_4Macc),
		ID_1ES("82", "1ES", "1 Esdras (Greek)", BookID.BOOK_1Esd),
		ID_2ES("83", "2ES", "2 Esdras (Latin)", BookID.BOOK_2Esd),
		ID_MAN("84", "MAN", "Prayer of Manasseh", BookID.BOOK_PrMan),
		ID_PS2("85", "PS2", "Psalm 151", BookID.BOOK_AddPs),
		ID_ODA("86", "ODA", "Odae/Odes", BookID.BOOK_Odes),
		ID_PSS("87", "PSS", "Psalms of Solomon", BookID.BOOK_PssSol),
		ID_EZA("A4", "EZA", "Ezra Apocalypse", BookID.BOOK_4Ezra),
		ID_5EZ("A5", "5EZ", "5 Ezra", BookID.BOOK_5Ezra),
		ID_6EZ("A6", "6EZ", "6 Ezra", BookID.BOOK_6Ezra),
		ID_DAG("B2", "DAG", "Daniel Greek", BookID.BOOK_DanGr),
		ID_PS3("B3", "PS3", "Psalms 152-155", BookID.BOOK_5ApocSyrPss),
		ID_2BA("B4", "2BA", "2 Baruch (Apocalypse)", BookID.BOOK_2Bar),
		ID_LBA("B5", "LBA", "Letter of Baruch", BookID.BOOK_EpBar),
		ID_JUB("B6", "JUB", "Jubilees", BookID.BOOK_Jub),
		ID_ENO("B7", "ENO", "Enoch", BookID.BOOK_1En),
		ID_1MQ("B8", "1MQ", "1 Meqabyan/Mekabis", BookID.BOOK_1Meq),
		ID_2MQ("B9", "2MQ", "2 Meqabyan/Mekabis", BookID.BOOK_2Meq),
		ID_3MQ("C0", "3MQ", "3 Meqabyan/Mekabis", BookID.BOOK_3Meq),
		ID_REP("C1", "REP", "Reproof", BookID.BOOK_Rep),
		ID_4BA("C2", "4BA", "4 Baruch", BookID.BOOK_4Bar),
		ID_LAO("C3", "LAO", "Letter to the Laodiceans", BookID.BOOK_EpLao),

		// Appendices
		ID_BAK("A1", "BAK", "Back Matter", BookID.APPENDIX),
		ID_OTH("A2", "OTH", "Other Matter", BookID.APPENDIX_OTHER),
		ID_CNC("A8", "CNC", "Concordance", BookID.APPENDIX_CONCORDANCE),
		ID_GLO("A9", "GLO", "Glossary / Wordlist", BookID.APPENDIX_GLOSSARY),
		ID_TDX("B0", "TDX", "Topical Index", BookID.APPENDIX_TOPICAL),
		ID_NDX("B1", "NDX", "Names Index", BookID.APPENDIX_NAMES);

		// @formatter:on

		private final String number;
		private final String identifier;
		private final String englishName;
		private final BookID id;

		private ParatextID(String number, String identifier, String englishName, BookID id) {
			this.number = number;
			this.identifier = identifier;
			this.englishName = englishName;
			this.id = id;
		}

		public String getNumber() {
			return number;
		}

		public String getIdentifier() {
			return identifier;
		}

		public String getEnglishName() {
			return englishName;
		}

		public BookID getId() {
			return id;
		}

		public static ParatextID fromIdentifier(String identifier) {
			for (ParatextID id : values()) {
				if (id.identifier.equals(identifier))
					return id;
			}
			return null;
		}

		public static ParatextID fromBookID(BookID bid) {
			for (ParatextID id : values()) {
				if (id.id == bid)
					return id;
			}
			return null;
		}
	}
}
