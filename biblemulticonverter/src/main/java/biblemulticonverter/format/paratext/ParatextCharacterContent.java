package biblemulticonverter.format.paratext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentPart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import biblemulticonverter.format.paratext.model.Version;
import biblemulticonverter.format.paratext.utilities.LocationParser;
import biblemulticonverter.format.paratext.utilities.TagParser;
import biblemulticonverter.format.paratext.utilities.TextUtilities;

public class ParatextCharacterContent implements ParatextBookContentPart, ParatextCharacterContentContainer {

	private final List<ParatextCharacterContentPart> content = new ArrayList<>(5);

	public ParatextCharacterContent() {
	}

	public List<ParatextCharacterContentPart> getContent() {
		return content;
	}

	@Override
	public <T extends Throwable> void acceptThis(ParatextBookContentVisitor<T> v) throws T {
		v.visitParatextCharacterContent(this);
	}

	/**
	 * One of {@link FootnoteXref}, {@link AutoClosingFormatting}, {@link Milestone}, {@link Reference},
	 * {@link CustomMarkup}, {@link SpecialSpace} or {@link Text}.
	 */
	public static interface ParatextCharacterContentPart {
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T;
	}

	// TODO try to move VerseStart, VerseEnd and Figure into ParatextBook?
	public static interface ParatextCharacterContentVisitor<T extends Throwable> {

		public ParatextCharacterContentVisitor<T> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) throws T;

		public ParatextCharacterContentVisitor<T> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws T;

		public void visitMilestone(String tag, Map<String,String> attributes) throws T;

		public void visitReference(Reference reference) throws T;

		public void visitCustomMarkup(String tag, boolean ending) throws T;

		public void visitText(String text) throws T;

		public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws T;

		public void visitEnd() throws T;
	}

	public static class FootnoteXref implements ParatextCharacterContentPart, ParatextCharacterContentContainer {

		private FootnoteXrefKind kind;
		private final String caller;
		private final List<ParatextCharacterContentPart> content = new ArrayList<>(5);
		private final String[] categories;

		public FootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) {
			this.kind = kind;
			this.caller = caller;
			this.categories = categories;
		}

		public String getCaller() {
			return caller;
		}

		public String[] getCategories() {
			return categories;
		}

		public FootnoteXrefKind getKind() {
			return kind;
		}

		public void setKind(FootnoteXrefKind kind) {
			this.kind = kind;
		}

		@Override
		public List<ParatextCharacterContentPart> getContent() {
			return content;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T {
			accept(visitor.visitFootnoteXref(kind, caller, categories));
		}
	}

	public static enum FootnoteXrefKind {
		FOOTNOTE("f"), ENDNOTE("fe"), XREF("x"), STUDY_EXTENDED_FOOTNOTE("ef"), STUDY_EXTENDED_XREF("ex");

		private String tag;

		private FootnoteXrefKind(String tag) {
			this.tag = tag;
		}

		public String getTag() {
			return tag;
		}

		public boolean isXref() {
			return this == XREF || this == STUDY_EXTENDED_XREF;
		}

		public static Map<String, FootnoteXrefKind> allTags() {
			Map<String, FootnoteXrefKind> result = new HashMap<>();
			for (FootnoteXrefKind kind : values()) {
				result.put(kind.tag, kind);
			}
			return result;
		}
	}

	public static class AutoClosingFormatting implements ParatextCharacterContentPart, ParatextCharacterContentContainer {
		private final AutoClosingFormattingKind kind;
		private final List<ParatextCharacterContentPart> content = new ArrayList<>(5);
		private final Map<String, String> attributes = new HashMap<>(3);

		public AutoClosingFormatting(AutoClosingFormattingKind kind) {
			this.kind = kind;
		}

		public AutoClosingFormattingKind getKind() {
			return kind;
		}

		public String getUsedTag() {
			return kind.getTag();
		}

		public Map<String, String> getAttributes() {
			return attributes;
		}

		@Override
		public List<ParatextCharacterContentPart> getContent() {
			return content;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T {
			accept(visitor.visitAutoClosingFormatting(kind, attributes));
		}
	}

	public static enum AutoClosingFormattingKind {

		//@formatter:off

		INTRODUCTION_OUTLINE_REFERENCE_RANGE(Version.V1, "ior", FormattingInstructionKind.ITALIC),
		INTRODUCTION_QUOTED_TEXT(Version.V2_2, "iqt", FormattingInstructionKind.ITALIC),

		QUOTATION_REFERENCE(Version.V1, "rq", null, "font-style: italic;-bmc-usfm-tag: rq;", ExtendedLineBreakKind.NEWLINE, ExtendedLineBreakKind.INDENT_RIGHT_JUSTIFIED, ExtendedLineBreakKind.NEWLINE, 0, null, null),
		SELAH(Version.V1, "qs", FormattingInstructionKind.ITALIC, FormattingInstructionKind.DIVINE_NAME),
		ACROSTIC_CHARACTER(Version.V1, "qac", FormattingInstructionKind.BOLD, FormattingInstructionKind.ITALIC),

		LIST_TOTAL(Version.V3, "litl"),
		LIST_KEY(Version.V3, "lik", FormattingInstructionKind.ITALIC),
		LIST_VALUE(Version.V3, "liv"),
		LIST_VALUE_1(Version.V3, "liv1"),
		LIST_VALUE_2(Version.V3, "liv2"),
		LIST_VALUE_3(Version.V3, "liv3"),
		LIST_VALUE_4(Version.V3, "liv4"),
		LIST_VALUE_5(Version.V3, "liv5"),

		FOOTNOTE_REFERENCE(Version.V1, "fr", FormattingInstructionKind.BOLD),
		FOOTNOTE_QUOTATION(Version.V1, "fq", FormattingInstructionKind.ITALIC),
		FOOTNOTE_QUOTATION_ALT(Version.V1, "fqa", FormattingInstructionKind.ITALIC),
		FOOTNOTE_KEYWORD(Version.V1, "fk", FormattingInstructionKind.DIVINE_NAME),
		FOOTNOTE_LABEL(Version.V2_0_3, "fl"),
		FOOTNOTE_PARAGRAPH(Version.V2_0_3, "fp", null, null, ExtendedLineBreakKind.PARAGRAPH, 0, null, 0, null, null),
		FOOTNOTE_WITNESS_LIST(Version.V3, "fw"),
		FOOTNOTE_VERSE_NUMBER(Version.V1, "fv", FormattingInstructionKind.SUPERSCRIPT),
		FOOTNOTE_TEXT(Version.V1, "ft"),
		FOOTNOTE_MARK(Version.V1, "fm", FormattingInstructionKind.SUPERSCRIPT),
		EXTENDED_FOOTNOTE_MARK(Version.V3, "efm", FormattingInstructionKind.SUPERSCRIPT),

		// In version 3.0 this marker has been deprecated, it should be easy to replace with dc and remove it completely
		// here.
		// https://ubsicap.github.io/usfm/master/notes_basic/fnotes.html#fdc-fdc
		FOOTNOTE_DEUTEROCANONICAL_CONTENT(Version.V1, "fdc", KeepIf.DC),

		XREF_ORIGIN(Version.V1, "xo", FormattingInstructionKind.BOLD),
		XREF_PUBLISHED_ORIGIN(Version.V3, "xop", FormattingInstructionKind.BOLD),
		XREF_KEYWORD(Version.V1, "xk", FormattingInstructionKind.DIVINE_NAME),
		XREF_QUOTATION(Version.V1, "xq", FormattingInstructionKind.ITALIC),

		// In version 3.0 one new attribute was introduced for this tag: "link-href" see:
		// https://ubsicap.github.io/usx/usx3.0.3/notes.html#xt
		XREF_TARGET_REFERENCES(Version.V1, "xt"),
		XREF_TARGET_REFERENCES_TEXT(Version.V3, "xta"),
		XREF_OT_CONTENT(Version.V2_2, "xot", KeepIf.OT),
		XREF_NT_CONTENT(Version.V2_2, "xnt", KeepIf.NT),

		// In version 3.0 this marker has been deprecated, it should be easy to replace with dc and remove it completely
		// here.
		// https://ubsicap.github.io/usfm/master/notes_basic/fnotes.html#fdc-fdc
		XREF_DEUTEROCANONICAL_CONTENT(Version.V1, "xdc", KeepIf.DC),

		ADDITION(Version.V1, "add", FormattingInstructionKind.ITALIC),
		QUOTED_BOOK_TITLE(Version.V1, "bk", FormattingInstructionKind.ITALIC),
		DEUTEROCANONICAL_CONTENT(Version.V1, "dc", KeepIf.DC),
		KEYWORD(Version.V1, "k"),
		NAME_OF_DEITY(Version.V1, "nd", FormattingInstructionKind.DIVINE_NAME, true),
		ORDINAL(Version.V1, "ord", FormattingInstructionKind.SUPERSCRIPT),
		PROPER_NAME(Version.V1, "pn"),
		PROPER_NAME_GEOGRAPHIC(Version.V3, "png"),

		// In version 3.0 this tag has been deprecated
		// https://ubsicap.github.io/usfm/characters/index.html#addpn-addpn
		ADDED_PROPER_NAME(Version.V2, "addpn", FormattingInstructionKind.ITALIC),
		QUOTED_TEXT(Version.V1, "qt", FormattingInstructionKind.ITALIC),
		SIGNATURE(Version.V1, "sig", FormattingInstructionKind.ITALIC),
		SECONDARY_LANGUAGE_SOURCE(Version.V1, "sls", FormattingInstructionKind.ITALIC),
		TRANSLITERATED(Version.V1, "tl", FormattingInstructionKind.ITALIC),
		WORDS_OF_JESUS(Version.V2, "wj", FormattingInstructionKind.WORDS_OF_JESUS, true),

		EMPHASIS(Version.V2, "em", FormattingInstructionKind.ITALIC),
		BOLD(Version.V1, "bd", FormattingInstructionKind.BOLD, true),
		ITALIC(Version.V1, "it", FormattingInstructionKind.ITALIC, true),
		BOLD_ITALIC(Version.V1, "bdit", FormattingInstructionKind.BOLD, FormattingInstructionKind.ITALIC),
		NORMAL(Version.V1, "no", "font-style: normal; font-weight: normal;"),
		SMALL_CAPS(Version.V1, "sc", FormattingInstructionKind.DIVINE_NAME),
		SUPERSCRIPT(Version.V3, "sup", FormattingInstructionKind.SUPERSCRIPT),

		INDEX_ENTRY(Version.V1, "ndx"),

		// In version 3.0 this tag has been deprecated, rb should be used instead, see:
		// https://ubsicap.github.io/usx/usx3.0.3/charstyles.html#pro
		PRONUNCIATION(Version.V2, "pro"),
		RUBY(Version.V3, "rb"),

		LINK(Version.V3, "jmp"),

		// In version 3.0 one new attributes have been added to this tag, see:
		// https://ubsicap.github.io/usx/usx3.0.3/charstyles.html#w
		WORDLIST(Version.V1, "w"),
		GREEK_WORD(Version.V1, "wg"),
		HEBREW_WORD(Version.V1, "wh"),
		ARAMAIC_WORD(Version.V3, "wa"),

		STUDY_CONTENT_CATEGORY(Version.V2_1, "cat", "font-weight: bold; background-color: blue;"),

		// Chapters and Verses presentation / alternative numbers
		ALTERNATE_CHAPTER(Version.V1, "ca", null, null, null, 0, null, 0, null, "altchapternumber"),
		ALTERNATE_VERSE(Version.V1, "va", null, null, null, 0, null, 0, null, "altversenumber"),
		PUBLISHED_VERSE(Version.V1, "vp", null, null, null, 0, null, 0, null, "versenumber");

		//@formatter:on

		private static final String[] WORDLIST_PROVIDED_ATTRIBUTES = {"lemma", "strong"};
		private static final String[] XREF_TARGET_REFERENCES_PROVIDED_ATTRIBUTES = {"link-href"};
		private static final String[] RUBY_PROVIDED_ATTRIBUTES = {"gloss"};
		public static final String[] LINKING_ATTRIBUTES = {"link-href", "link-title", "link-id"};

		private final Version since;
		/**
		 * The value of a numbered marker, if the marker is not numbered this value will be -1.
		 */
		private final int number;
		private final String baseTag;
		private final String tag;
		private final FormattingInstructionKind format;
		private final String css;
		private final ExtendedLineBreakKind lbk, lbkEnd;
		private final int lbkIndent, lbkEndIndent;
		private final KeepIf keepIf;
		private final String extraAttribute;

		private AutoClosingFormattingKind(Version since, String tag) {
			this(since, tag, (KeepIf) null);
		}

		private AutoClosingFormattingKind(Version since, String tag, KeepIf keepIf) {
			this(since, tag, null, "-bmc-usfm-tag: " + tag + ";", null, 0, null, 0, keepIf, null);
		}

		private AutoClosingFormattingKind(Version since, String tag, FormattingInstructionKind... extraStyles) {
			this(since, tag, null, buildCSS(extraStyles) + "-bmc-usfm-tag: " + tag + ";", null, 0, null, 0, null, null);
		}

		private AutoClosingFormattingKind(Version since, String tag, FormattingInstructionKind kind, boolean primary) {
			this(since, tag, kind, kind.getCss(), null, 0, null, 0, null, null);
			if (!primary)
				throw new IllegalStateException();
		}

		private AutoClosingFormattingKind(Version since, String tag, String extraCss) {
			this(since, tag, null, extraCss + " -bmc-usfm-tag: " + tag + ";", null, 0, null, 0, null, null);
		}

		private AutoClosingFormattingKind(Version since, String tag, FormattingInstructionKind format, String css, ExtendedLineBreakKind lbk, int lbkIndent, ExtendedLineBreakKind lbkEnd, int lbkEndIndent, KeepIf keepIf, String extraAttribute) {
			this.since = since;
			this.tag = tag;
			this.format = format;
			this.css = css;
			this.lbk = lbk;
			this.lbkIndent = lbkIndent;
			this.lbkEnd = lbkEnd;
			this.lbkEndIndent = lbkEndIndent;
			this.keepIf = keepIf;
			this.extraAttribute = extraAttribute;
			TagParser parser = new TagParser();
			parser.parse(tag);
			this.number = parser.getNumber();
			this.baseTag = parser.getTag();
		}

		private static String buildCSS(FormattingInstructionKind[] extraStyles) {
			StringBuilder result = new StringBuilder();
			for (FormattingInstructionKind k : extraStyles) {
				result.append(k.getCss() + " ");
			}
			return result.toString();
		}

		public String getTag() {
			return tag;
		}

		public FormattingInstructionKind getFormat() {
			return format;
		}

		public ExtendedLineBreakKind getLineBreakKind() {
			return lbk;
		}

		public int getIndent() {
			return lbkIndent;
		}

		public ExtendedLineBreakKind getEndLineBreakKind() {
			return lbkEnd;
		}

		public int getEndIndent() {
			return lbkEndIndent;
		}

		public String getCss() {
			return css;
		}

		public KeepIf getKeepIf() {
			return keepIf;
		}

		public String getExtraAttribute() {
			return extraAttribute;
		}

		public String getDefaultAttribute() {
			switch (this) {
			case WORDLIST:
				return "lemma";
			case XREF_TARGET_REFERENCES:
				return "link-href";
			case RUBY:
				return "gloss";
			default:
				return null;
			}
		}

		public String[] getProvidedAttributes() {
			switch (this) {
			case WORDLIST:
				return WORDLIST_PROVIDED_ATTRIBUTES;
			case XREF_TARGET_REFERENCES:
				return XREF_TARGET_REFERENCES_PROVIDED_ATTRIBUTES;
			case RUBY:
				return RUBY_PROVIDED_ATTRIBUTES;
			default:
				return null;
			}
		}

		/**
		 * True if this AutoClosingFormattingKind has the same baseTag as the given AutoClosingFormattingKind.
		 */
		public boolean isSameBase(AutoClosingFormattingKind kind) {
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

		public Version getVersion() {
			return since;
		}

		public static Set<AutoClosingFormattingKind> allForVersion(Version version) {
			Set<AutoClosingFormattingKind> result = EnumSet.noneOf(AutoClosingFormattingKind.class);
			for (AutoClosingFormattingKind kind : values()) {
				if (kind.since.isLowerOrEqualTo(version)) {
					result.add(kind);
				}
			}
			return result;
		}

		public static Map<String, AutoClosingFormattingKind> allTags() {
			Map<String, AutoClosingFormattingKind> result = new HashMap<>();
			for (AutoClosingFormattingKind kind : values()) {
				result.put(kind.tag, kind);
				result.put("+" + kind.tag, kind);
			}
			return result;
		}

		public static Map<String, AutoClosingFormattingKind> allCSS() {
			Map<String, AutoClosingFormattingKind> result = new HashMap<>();
			for (AutoClosingFormattingKind kind : values()) {
				if (kind.css != null)
					result.put(kind.css, kind);
			}
			return result;
		}
	}

	public static enum KeepIf {
		OT, NT, DC
	}

	public static class Milestone implements ParatextCharacterContentPart {

		private final String tag;
		private final Map<String, String> attributes = new HashMap<>(3);

		public Milestone(String tag) {
			this.tag = tag;
		}

		public String getTag() {
			return tag;
		}

		public Map<String, String> getAttributes() {
			return attributes;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T {
			visitor.visitMilestone(tag, attributes);
		}
	}

	public static class Reference implements ParatextCharacterContentPart {

		private final ParatextID book;
		private final int firstChapter;
		private final String firstVerse;
		private final int lastChapter;
		private final String lastVerse;
		private String content;

		private Reference(ParatextID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse, String content) {
			this.book = book;
			this.firstChapter = firstChapter;
			this.firstVerse = firstVerse;
			this.lastChapter = lastChapter;
			this.lastVerse = lastVerse;
			this.content = content;
		}

		public ParatextID getBook() {
			return book;
		}

		public int getFirstChapter() {
			return firstChapter;
		}

		public String getFirstVerse() {
			return firstVerse;
		}

		public int getLastChapter() {
			return lastChapter;
		}

		public String getLastVerse() {
			return lastVerse;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T {
			visitor.visitReference(this);
		}

		/**
		 * Create a new Reference by parsing the given location and using the given content. The location must be be
		 * parsable by {@link LocationParser}
		 *
		 * @param location a raw location string that can be parsed using {@link LocationParser}. All
		 *                 {@link LocationParser.Format} types are allowed except
		 *                 {@link LocationParser.Format#BOOK_RANGE}, since Reference does not support book ranges (they
		 *                 should be encoded as "first reference only",
		 *                 https://ubsicap.github.io/usx/v3.0.0/elements.html#ref).
		 * @param content  the content for the Reference
		 * @return a new Reference for the given location and with the given content.
		 * @throws IllegalArgumentException if the given location cannot be parsed or is of the location matches the
		 *                                  {@link LocationParser.Format#BOOK_RANGE} format.
		 */
		public static Reference parse(String location, String content) {
			LocationParser parser = new LocationParser(true);
			if (!parser.parse(location)) {
				throw new IllegalArgumentException("Found invalid reference location: " + location);
			}
			switch (parser.getFormat()) {
				case BOOK:
					return Reference.book(parser.getStartBook(), content);
				case CHAPTER:
					return Reference.chapter(parser.getStartBook(), parser.getStartChapter(), content);
				case CHAPTER_RANGE:
					return Reference.chapterRange(parser.getStartBook(), parser.getStartChapter(), parser.getEndChapter(), content);
				case VERSE:
					return Reference.verse(parser.getStartBook(), parser.getStartChapter(), parser.getStartVerse(), content);
				case VERSE_RANGE:
					return Reference.verseRange(parser.getStartBook(), parser.getStartChapter(), parser.getStartVerse(), parser.getEndChapter(), parser.getEndVerse(), content);
				case BOOK_RANGE:
					throw new IllegalArgumentException("Found book range (" + location + "), book ranges are not supported to be in references");
			}
			throw new RuntimeException("Unimplement format found: " + parser.getFormat());
		}

		/**
		 * E.g. MAT
		 */
		public static Reference book(ParatextID book, String content) {
			return new Reference(book, -1, null, -1, null, content);
		}

		/**
		 * E.g. MAT 3
		 */
		public static Reference chapter(ParatextID book, int firstChapter, String content) {
			return new Reference(book, firstChapter, null, -1, null, content);
		}

		/**
		 * E.g. MAT 3:1
		 */
		public static Reference verse(ParatextID book, int firstChapter, String firstVerse, String content) {
			return new Reference(book, firstChapter, firstVerse, -1, null, content);
		}

		/**
		 * E.g. MAT 3-4
		 */
		public static Reference chapterRange(ParatextID book, int firstChapter, int lastChapter, String content) {
			if (firstChapter > lastChapter)
				throw new IllegalArgumentException("Invalid chapter range: " + firstChapter + "-" + lastChapter);
			return new Reference(book, firstChapter, null, lastChapter, null, content);
		}

		/**
		 * E.g. MAT 3:4-5:2
		 */
		public static Reference verseRange(ParatextID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse, String content) {
			if (firstChapter > lastChapter)
				throw new IllegalArgumentException("Invalid chapter range: " + firstChapter + "-" + lastChapter);
			return new Reference(book, firstChapter, firstVerse, lastChapter, lastVerse, content);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			// Book
			builder.append(book.getIdentifier());

			if (firstChapter != -1) {

				// Single chapter
				builder.append(" ");
				builder.append(firstChapter);

				if (firstVerse != null) {

					// Single verse
					builder.append(":");
					builder.append(firstVerse);
					if (lastChapter != -1 && lastVerse != null) {
						// Verse range
						builder.append("-");
						builder.append(lastChapter);
						builder.append(":");
						builder.append(lastVerse);
					}
				} else if (lastChapter != -1) {
					// Chapter range
					builder.append("-");
					builder.append(lastChapter);
				}
			}
			return builder.toString();
		}
	}

	public static class CustomMarkup implements ParatextCharacterContentPart {

		private final String tag;
		private final boolean ending;

		public CustomMarkup(String tag, boolean ending) {
			this.tag = tag;
			this.ending = ending;
		}

		public String getTag() {
			return tag;
		}

		public boolean isEnding() {
			return ending;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T {
			visitor.visitCustomMarkup(tag, ending);
		}
	}

	public static class SpecialSpace implements ParatextCharacterContentPart {

		private final boolean nonBreakSpace, optionalLineBreak;

		public SpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) {
			if (nonBreakSpace == optionalLineBreak)
				throw new IllegalArgumentException("Invalid SpecialSpace options");
			this.nonBreakSpace = nonBreakSpace;
			this.optionalLineBreak = optionalLineBreak;
		}

		public boolean isNonBreakSpace() {
			return nonBreakSpace;
		}
		public boolean isOptionalLineBreak() {
			return optionalLineBreak;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T {
			visitor.visitSpecialSpace(nonBreakSpace, optionalLineBreak);
		}
	}

	public static class Text implements ParatextCharacterContentPart {
		private String text;

		private Text(String chars) {
			this.text = chars;
		}

		public String getChars() {
			return text;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T {
			visitor.visitText(text);
		}

		public static Text from(String chars) {
			final String text = TextUtilities.whitespaceNormalization(chars);
			if (text.isEmpty()) {
				return null;
			} else {
				return new Text(text);
			}
		}
	}
}
