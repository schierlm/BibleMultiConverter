package biblemulticonverter.format.paratext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentPart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;

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
	 * One of {@link VerseStart}, {@link FootnoteXref},
	 * {@link AutoClosingFormatting}, {@link Reference} or {@link Text}.
	 */
	public static interface ParatextCharacterContentPart {
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T;
	}

	public static interface ParatextCharacterContentVisitor<T extends Throwable> {
		public void visitVerseStart(String verseNumber) throws T;

		public ParatextCharacterContentVisitor<T> visitFootnoteXref(FootnoteXrefKind kind, String caller) throws T;

		public ParatextCharacterContentVisitor<T> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws T;

		public void visitReference(Reference reference) throws T;

		public void visitText(String text) throws T;

		public void visitEnd() throws T;
	}

	public static class ParatextCharacterAppendVisitor implements ParatextCharacterContentVisitor<RuntimeException> {

		private ParatextCharacterContentContainer parent;

		public ParatextCharacterAppendVisitor(ParatextCharacterContentContainer parent) {
			this.parent = parent;
		}

		@Override
		public void visitVerseStart(String verseNumber) throws RuntimeException {
			parent.getContent().add(new VerseStart(verseNumber));
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitFootnoteXref(FootnoteXrefKind kind, String caller) throws RuntimeException {
			return addAndVisit(new FootnoteXref(kind, caller));
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws RuntimeException {
			AutoClosingFormatting acf = new AutoClosingFormatting(kind, false);
			acf.getAttributes().putAll(attributes);
			return addAndVisit(acf);
		}

		@Override
		public void visitReference(Reference reference) throws RuntimeException {
			parent.getContent().add(reference);
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			parent.getContent().add(new Text(text));
		}

		@Override
		public void visitEnd() throws RuntimeException {
		}

		private <P extends ParatextCharacterContentPart & ParatextCharacterContentContainer> ParatextCharacterContentVisitor<RuntimeException> addAndVisit(P part) {
			parent.getContent().add(part);
			return new ParatextCharacterAppendVisitor(part);
		}
	}

	public static class VerseStart implements ParatextCharacterContentPart {
		private final String verseNumber;

		public VerseStart(String verseNumber) {
			this.verseNumber = verseNumber;
		}

		public String getVerseNumber() {
			return verseNumber;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T {
			visitor.visitVerseStart(verseNumber);
		}
	}

	public static class FootnoteXref implements ParatextCharacterContentPart, ParatextCharacterContentContainer {

		private FootnoteXrefKind kind;
		private final String caller;
		private final List<ParatextCharacterContentPart> content = new ArrayList<>(5);

		public FootnoteXref(FootnoteXrefKind kind, String caller) {
			this.kind = kind;
			this.caller = caller;
		}

		public String getCaller() {
			return caller;
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
			accept(visitor.visitFootnoteXref(kind, caller));
		}
	}

	public static enum FootnoteXrefKind {
		FOOTNOTE("f"), ENDNOTE("fe"), XREF("x");

		private String tag;

		private FootnoteXrefKind(String tag) {
			this.tag = tag;
		}

		public String getTag() {
			return tag;
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
		private final boolean wasNested;
		private final List<ParatextCharacterContentPart> content = new ArrayList<>(5);
		private final Map<String, String> attributes = new HashMap<>(3);

		public AutoClosingFormatting(AutoClosingFormattingKind kind, boolean wasNested) {
			this.kind = kind;
			this.wasNested = wasNested;
		}

		public AutoClosingFormattingKind getKind() {
			return kind;
		}

		public String getUsedTag() {
			return (wasNested ? "+" : "") + kind.getTag();
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

		INTRODUCTION_OUTLINE_REFERENCE_RANGE("ior", FormattingInstructionKind.ITALIC),
		INTRODUCTION_QUOTED_TEXT("iqt", FormattingInstructionKind.ITALIC),

		QUOTATION_REFERENCE("rq", FormattingInstructionKind.ITALIC),
		VERSE_PRESENTATION("vp", FormattingInstructionKind.BOLD), // only very rudimentary
		SELAH("qs", FormattingInstructionKind.ITALIC, FormattingInstructionKind.DIVINE_NAME),
		ACROSTIC_CHARACTER("qac",  FormattingInstructionKind.BOLD,  FormattingInstructionKind.ITALIC),

		LIST_TOTAL("litl"),
		LIST_KEY("lik", FormattingInstructionKind.ITALIC),
		LIST_VALUE("liv"),

		FOOTNOTE_REFERENCE("fr", FormattingInstructionKind.BOLD),
		FOOTNOTE_QUOTATION("fq", FormattingInstructionKind.ITALIC),
		FOOTNOTE_QUOTATION_ALT("fqa", FormattingInstructionKind.ITALIC),
		FOOTNOTE_KEYWORD("fk", FormattingInstructionKind.DIVINE_NAME),
		FOOTNOTE_LABEL("fl"),
		FOOTNOTE_PARAGRAPH("fp", null, null, LineBreakKind.PARAGRAPH, null),
		FOOTNOTE_VERSE_NUMBER("fv", FormattingInstructionKind.SUPERSCRIPT),
		FOOTNOTE_TEXT("ft"),
		FOOTNOTE_DEUTEROCANONICAL_CONTENT("fdc", KeepIf.DC),

		XREF_ORIGIN("xo", FormattingInstructionKind.BOLD),
		XREF_KEYWORD("xk", FormattingInstructionKind.DIVINE_NAME),
		XREF_QUOTATION("xq", FormattingInstructionKind.ITALIC),
		XREF_TARGET("xt"),
		XREF_OT_CONTENT("xot", KeepIf.OT),
		XREF_NT_CONTENT("xnt", KeepIf.NT),
		XREF_DEUTEROCANONICAL_CONTENT("xdc", KeepIf.DC),

		ADDITION("add", FormattingInstructionKind.ITALIC),
		BOOK_TITLE("bk", FormattingInstructionKind.ITALIC),
		DEUTEROCANONICAL_CONTENT("dc", KeepIf.DC),
		KEYWORD("k"),
		LITURGICAL("lit", FormattingInstructionKind.BOLD),
		NAME_OF_DEITY("nd", FormattingInstructionKind.DIVINE_NAME, true),
		ORDINAL("ord", FormattingInstructionKind.SUPERSCRIPT),
		PROPER_NAME("pn"),
		PROPER_NAME_GEOGRAPHIC("png"),
		ADDED_PROPER_NAME("addpn", FormattingInstructionKind.ITALIC),
		QUOTED_TEXT("qt", FormattingInstructionKind.ITALIC),
		SIGNATURE("sig", FormattingInstructionKind.ITALIC),
		SECONDARY_LANGUAGE_SOURCE("sls", FormattingInstructionKind.ITALIC),
		TRANSLITERATED("tl", FormattingInstructionKind.ITALIC),
		WORDS_OF_JESUS("wj", FormattingInstructionKind.WORDS_OF_JESUS, true),

		EMPHASIS("em", FormattingInstructionKind.ITALIC),
		BOLD("bd", FormattingInstructionKind.BOLD, true),
		ITALIC("it", FormattingInstructionKind.ITALIC, true),
		BOLD_ITALIC("bdit", FormattingInstructionKind.BOLD, FormattingInstructionKind.ITALIC),
		NORMAL("no", "font-style: normal; font-weight: normal;"),
		SMALL_CAPS("sc", FormattingInstructionKind.DIVINE_NAME),

		INDEX_ENTRY("ndx"),
		PRONUNCIATION("pro"),
		WORDLIST("w"),
		GREEK_WORD("wg"),
		HEBREW_WORD("wh"),

		PAGE_BREAK("pb", null, null, LineBreakKind.PARAGRAPH, null);

		//@formatter:on

		private static final String[] WORDLIST_ENTRY_ATTRIBUTES = { "lemma", "strong" };

		private final String tag;
		private final FormattingInstructionKind format;
		private final String css;
		private final LineBreakKind lbk;
		private final KeepIf keepIf;

		private AutoClosingFormattingKind(String tag) {
			this(tag, (KeepIf) null);
		}

		private AutoClosingFormattingKind(String tag, KeepIf keepIf) {
			this(tag, null, "-bmc-usfm-tag: " + tag + ";", null, keepIf);
		}

		private AutoClosingFormattingKind(String tag, FormattingInstructionKind... extraStyles) {
			this(tag, null, buildCSS(extraStyles) + "-bmc-usfm-tag: " + tag + ";", null, null);
		}

		private AutoClosingFormattingKind(String tag, FormattingInstructionKind kind, boolean primary) {
			this(tag, kind, kind.getCss(), null, null);
			if (!primary)
				throw new IllegalStateException();
		}

		private static String buildCSS(FormattingInstructionKind[] extraStyles) {
			StringBuilder result = new StringBuilder();
			for (FormattingInstructionKind k : extraStyles) {
				result.append(k.getCss() + " ");
			}
			return result.toString();
		}

		private AutoClosingFormattingKind(String tag, String extraCss) {
			this(tag, null, extraCss + " -bmc-usfm-tag: " + tag + ";", null, null);
		}

		private AutoClosingFormattingKind(String tag, FormattingInstructionKind format, String css, LineBreakKind lbk, KeepIf keepIf) {
			this.tag = tag;
			this.format = format;
			this.css = css;
			this.lbk = lbk;
			this.keepIf = keepIf;
		}

		public String getTag() {
			return tag;
		}

		public FormattingInstructionKind getFormat() {
			return format;
		}

		public LineBreakKind getLineBreakKind() {
			return lbk;
		}

		public String getCss() {
			return css;
		}

		public KeepIf getKeepIf() {
			return keepIf;
		}

		public String[] getDefaultAttributes() {
			return this == WORDLIST ? WORDLIST_ENTRY_ATTRIBUTES : null;
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

	public static class Reference implements ParatextCharacterContentPart {
		private final ParatextID book;
		private final int firstChapter;
		private final int firstVerse;
		private final int lastChapter;
		private final int lastVerse;
		private String content;

		public Reference(ParatextID book, int firstChapter, int firstVerse, int lastChapter, int lastVerse, String content) {
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

		public int getFirstVerse() {
			return firstVerse;
		}

		public int getLastChapter() {
			return lastChapter;
		}

		public int getLastVerse() {
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
	}

	public static class Text implements ParatextCharacterContentPart {
		private String text;

		public Text(String chars) {
			this.text = chars;
		}

		public String getChars() {
			return text;
		}

		@Override
		public <T extends Throwable> void acceptThis(ParatextCharacterContentVisitor<T> visitor) throws T {
			visitor.visitText(text);
		}
	}
}
