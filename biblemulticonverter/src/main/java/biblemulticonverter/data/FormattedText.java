package biblemulticonverter.data;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents formatted text, that may contain headlines, footnotes, etc.
 */
public class FormattedText {

	/**
	 * Text used to be used at the beginning of a footnote that should actually
	 * be a list of cross references.
	 */
	@Deprecated
	public static String XREF_MARKER = "\u2118 ";

	private List<Headline> headlines = new ArrayList<Headline>(0);
	private List<FormattedElement> elements = new ArrayList<FormattedElement>(5);
	private boolean finished = false;

	public Visitor<RuntimeException> getAppendVisitor() {
		if (finished)
			throw new IllegalStateException();
		return new AppendVisitor(this);
	}

	public <T extends Throwable> void accept(Visitor<T> visitor) throws T {
		if (visitor == null)
			return;
		String elementTypes = null;
		while (true) {
			int depth = visitor.visitElementTypes(elementTypes);
			if (depth <= 0)
				break;
			elementTypes = getElementTypes(depth);
		}
		for (Headline headline : headlines) {
			headline.acceptThis(visitor);
		}
		visitor.visitStart();
		for (FormattedElement element : elements)
			element.acceptThis(visitor);
		if (visitor.visitEnd())
			accept(visitor);
	}

	public List<Headline> getHeadlines() {
		return new ArrayList<Headline>(headlines);
	}

	public List<FormattedText> splitContent(boolean includeHeadlines, boolean innerContent) {
		List<FormattedText> result = new ArrayList<FormattedText>();
		if (includeHeadlines) {
			for (Headline h : headlines) {
				FormattedText t = new FormattedText();
				if (innerContent) {
					h.accept(t.getAppendVisitor());
				} else {
					h.acceptThis(t.getAppendVisitor());
				}
				result.add(t);
			}
		}
		for (FormattedElement e : elements) {
			FormattedText t = new FormattedText();
			if (innerContent) {
				if (e instanceof FormattedText)
					((FormattedText) e).accept(t.getAppendVisitor());
			} else {
				e.acceptThis(t.getAppendVisitor());
			}
			result.add(t);
		}
		return result;
	}

	public void validate(Bible bible, BookID book, String location, List<String> danglingReferences, Map<String, Set<String>> dictionaryEntries, Map<String, Set<FormattedText.ValidationCategory>> validationCategories, Set<String> internalAnchors, Set<String> internalLinks) {
		if (!finished)
			ValidationCategory.NOT_FINISHED.throwOrRecord(location, validationCategories, location);
		try {
			accept(new ValidatingVisitor(bible, book, danglingReferences, dictionaryEntries, location, validationCategories, internalAnchors, internalLinks, this instanceof Verse ? ValidationContext.VERSE : ValidationContext.NORMAL_TEXT));
		} catch (RuntimeException ex) {
			throw new RuntimeException("Validation error at " + location, ex);
		}
	}

	public void trimWhitespace() {
		if (finished)
			throw new IllegalStateException();
		if (Boolean.getBoolean("biblemulticonverter.keepwhitespace"))
			return;
		boolean trimmed = false;
		for (int i = 0; i < elements.size(); i++) {
			if (elements.get(i) instanceof FormattedText)
				((FormattedText) elements.get(i)).trimWhitespace();
			if (!(elements.get(i) instanceof Text))
				continue;
			if (i > 0 && elements.get(i - 1) instanceof Text) {
				elements.set(i, new Text((((Text) elements.get(i - 1)).text + ((Text) elements.get(i)).text).replace("  ", " ")));
				elements.remove(i - 1);
				i -= 2;
				continue;
			}
			Text text = (Text) elements.get(i);
			if (text.text.startsWith(" ")) {
				boolean trim;
				if (i == 0) {
					trim = true;
				} else {
					FormattedElement prev = elements.get(i - 1);
					trim = (prev instanceof LineBreak || prev instanceof Headline);
				}
				if (trim) {
					trimmed = true;
					if (text.text.length() == 1) {
						elements.remove(i);
						i--;
						continue;
					} else {
						elements.set(i, new Text(text.text.substring(1)));
					}
				}
			}
			if (text.text.endsWith(" ")) {
				boolean trim;
				if (i == elements.size() - 1) {
					trim = true;
				} else {
					FormattedElement next = elements.get(i + 1);
					trim = (next instanceof LineBreak || next instanceof Headline);
				}
				if (trim) {
					trimmed = true;
					if (text.text.length() == 1) {
						elements.remove(i);
						i--;
					} else {
						elements.set(i, new Text(text.text.substring(0, text.text.length() - 1)));
					}
				}
			}
		}
		if (trimmed)
			trimWhitespace();
	}

	/**
	 * Return the types of elements inside this formatted text as String, useful
	 * for regex matching.
	 */
	public String getElementTypes(int depth) {
		StringBuilder sb = new StringBuilder();
		accept(new ElementTypeVisitor(sb, depth, ""));
		return sb.toString();
	}

	public void removeLastElement() {
		if (finished)
			throw new IllegalStateException();
		elements.remove(elements.size() - 1);
	}

	/**
	 * Call this when the content of this object is complete. After calling this
	 * method, changes to the content are impossible. Note that this
	 * implementation takes measures (like share common objects) to reduce
	 * memory consumption; therefore, call this method if you are sure you do
	 * not have to change the contents again.
	 */
	public void finished() {
		if (finished)
			throw new IllegalStateException();
		finished = true;
		if (elements.size() == 0) {
			elements = Collections.emptyList();
		} else {
			for (FormattedElement e : elements) {
				if (e instanceof FormattedText)
					((FormattedText) e).finished();
			}
			((ArrayList<FormattedElement>) elements).trimToSize();
		}
		if (headlines.size() == 0) {
			headlines = Collections.emptyList();
		} else {
			for (Headline h : headlines)
				h.finished();
			((ArrayList<Headline>) headlines).trimToSize();
		}
	}

	private static interface FormattedElement {
		public abstract <T extends Throwable> void acceptThis(Visitor<T> v) throws T;
	}

	private static abstract class FormattedTextElement extends FormattedText implements FormattedElement {
	}

	private static class Text implements FormattedElement {
		private final String text;

		private Text(String text) {
			this.text = Utils.validateString("text", text, " | ?" + Utils.NORMALIZED_WHITESPACE_REGEX + " ?");
		}

		@Override
		public <T extends Throwable> void acceptThis(Visitor<T> v) throws T {
			v.visitText(text);
		}
	}

	public static class Headline extends FormattedTextElement {
		private final int depth;

		public Headline(int depth) {
			this.depth = Utils.validateNumber("depth", depth, 1, 9);
		}

		public int getDepth() {
			return depth;
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitHeadline(depth));
		}
	}

	private static class Footnote extends FormattedTextElement {

		private boolean ofCrossReferences;

		public Footnote(boolean ofCrossReferences) {
			this.ofCrossReferences = ofCrossReferences;
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitFootnote(ofCrossReferences));
		}
	}

	private static class CrossReference extends FormattedTextElement {
		private String firstBookAbbr;
		private BookID firstBook;
		private int firstChapter;
		private String firstVerse;
		private String lastBookAbbr;
		private BookID lastBook;
		private int lastChapter;
		private String lastVerse;

		private CrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) {
			this.firstBookAbbr = Utils.validateString("firstBookAbbr", firstBookAbbr, Utils.BOOK_ABBR_REGEX);
			this.firstBook = Utils.validateNonNull("firstBook", firstBook);
			this.firstChapter = Utils.validateNumber("firstChapter", firstChapter, 1, Integer.MAX_VALUE);
			this.firstVerse = Utils.validateString("firstVerse", firstVerse, Utils.VERSE_REGEX);
			this.lastBookAbbr = Utils.validateString("lastBookAbbr", lastBookAbbr, Utils.BOOK_ABBR_REGEX);
			this.lastBook = Utils.validateNonNull("lastBook",        lastBook);
			if (firstChapter == 1 && lastChapter == -1 && lastVerse.equals("*")) {
				this.lastChapter = -1;
			} else {
				this.lastChapter = Utils.validateNumber("lastChapter", lastChapter, firstChapter, Integer.MAX_VALUE);
			}
			if (firstVerse.equals("1") && lastVerse.equals("*")) {
				this.lastVerse = "*";
			} else {
				this.lastVerse = Utils.validateString("lastVerse", lastVerse, Utils.VERSE_REGEX);
			}
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitCrossReference(firstBookAbbr, firstBook, firstChapter, firstVerse, lastBookAbbr, lastBook, lastChapter, lastVerse));
		}
	}

	private static class FormattingInstruction extends FormattedTextElement {
		private final FormattingInstructionKind kind;

		private FormattingInstruction(FormattingInstructionKind kind) {
			this.kind = Utils.validateNonNull("kind", kind);
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitFormattingInstruction(kind));
		}
	}

	private static class CSSFormatting extends FormattedTextElement {
		private final String css;

		private CSSFormatting(String css) {
			this.css = Utils.validateString("css", css, "[^\r\n\t\"<>&]*+");
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitCSSFormatting(css));
		}
	}

	private static class VerseSeparator implements FormattedElement {
		@Override
		public <T extends Throwable> void acceptThis(Visitor<T> v) throws T {
			v.visitVerseSeparator();
		}
	}

	private static class LineBreak implements FormattedElement {
		private final ExtendedLineBreakKind kind;
		private final int indent;

		private LineBreak(ExtendedLineBreakKind kind, int indent) {
			this.kind = Utils.validateNonNull("kind", kind);
			this.indent = Utils.validateNumber("indent", indent, -2, 9);
		}

		@Override
		public <T extends Throwable> void acceptThis(Visitor<T> v) throws T {
			v.visitLineBreak(kind, indent);
		}
	}

	private static class GrammarInformation extends FormattedTextElement {
		private final char[] strongsPrefixes;
		private final int[] strongs;
		private final char[] strongsSuffixes;
		private final String[] rmac;
		private final int[] sourceIndices;
		private final String[] attributeKeys;
		private final String[] attributeValues;

		private GrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
			this.strongsPrefixes = strongsPrefixes;
			this.strongs = strongs;
			this.strongsSuffixes = strongsSuffixes;
			this.rmac = rmac;
			this.sourceIndices = sourceIndices;
			this.attributeKeys = attributeKeys;
			this.attributeValues = attributeValues;

			if (strongs == null && rmac == null && sourceIndices == null && attributeKeys == null) {
				throw new IllegalArgumentException("At least one grammar information type is required!");
			}
			if (strongsPrefixes != null) {
				if (strongs == null)
					throw new IllegalArgumentException("Prefixes without Strongs numbers are not supported!");
				if (strongsPrefixes.length != strongs.length)
					throw new IllegalArgumentException("Prefixes need to be same length as Strongs numbers");
				for(char prefix : strongsPrefixes) {
					if (prefix < 'A' || prefix > 'Z')
						throw new IllegalArgumentException("Invalid Strongs prefix letter: "+prefix);
				}
			}
			if (strongs != null) {
				if (strongs.length == 0) {
					throw new IllegalArgumentException("Strongs may not be empty");
				}
				for (int strong : strongs) {
					if (strong <= 0)
						throw new IllegalArgumentException("Strongs must be positive: " + strong);
				}
			}
			if (strongsSuffixes != null) {
				if (strongs == null)
					throw new IllegalArgumentException("Suffixes without Strongs numbers are not supported!");
				if (strongsSuffixes.length != strongs.length)
					throw new IllegalArgumentException("Suffixes need to be same length as Strongs numbers");
				for(char suffix : strongsSuffixes) {
					if ((suffix != ' ') && (suffix < 'A' || suffix > 'Z') && (suffix < 'a' || suffix > 'z'))
						throw new IllegalArgumentException("Invalid Strongs suffix letter: " + suffix);
				}
			}
			if (rmac != null) {
				if (rmac.length == 0)
					throw new IllegalArgumentException("RMAC may not be empty");
				for (String entry : rmac) {
					Utils.validateString("morph", entry, Utils.MORPH_REGEX);
				}
			}
			if (sourceIndices != null) {
				if (sourceIndices.length == 0)
					throw new IllegalArgumentException("Source indices may not be empty");
				for (int idx : sourceIndices) {
					if (idx <= 0 || idx > 100)
						throw new IllegalArgumentException("Source index out of range: " + idx);
				}
			}
			if (attributeKeys != null || attributeValues != null) {
				if (attributeKeys == null || attributeValues == null)
					throw new IllegalArgumentException("Attribute keys and values must both be present");
				if (attributeKeys.length != attributeValues.length)
					throw new IllegalArgumentException("Attribute keys and values must have same length");
				if (attributeKeys.length == 0)
					throw new IllegalArgumentException("Attributes may not be empty");
				for (int i = 0; i < attributeKeys.length; i++) {
					Utils.validateString("attributeKey", attributeKeys[i], "[a-z0-9_:-]++");
					Utils.validateString("attributeValue", attributeValues[i], "[^ \r\n\t]*+");
				}
			}
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceIndices, attributeKeys, attributeValues));
		}
	}

	private static class DictionaryEntry extends FormattedTextElement {
		private final String dictionary;
		private final String entry;

		private DictionaryEntry(String dictionary, String entry) {
			this.dictionary = Utils.validateString("dictionary", dictionary, "[A-Za-z0-9]+");
			if (dictionary.equals("strongs") || dictionary.equals("rmac"))
				throw new IllegalArgumentException("Please use Grammar information for Strongs and/or RMAC");
			this.entry = Utils.validateString("entry", entry, "[A-Za-z0-9-]+");
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitDictionaryEntry(dictionary, entry));
		}
	}

	private static class Speaker extends FormattedTextElement {
		private final String labelOrStrongs;

		private Speaker(String labelOrStrongs) {
			this.labelOrStrongs = Utils.validateString("labelOrStrongs", labelOrStrongs, "[A-Za-z0-9_:-]+");
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitSpeaker(labelOrStrongs));
		}
	}

	private static class Hyperlink extends FormattedTextElement {
		private final HyperlinkType type;
		private final String target;

		private Hyperlink(HyperlinkType type, String target) {
			this.type = Utils.validateNonNull("type", type);
			this.target = Utils.validateString("target", target, "[^\r\n\t\"<>&]*+");
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitHyperlink(type, target));
		}
	}

	private static class RawHTML implements FormattedElement {

		private final RawHTMLMode mode;
		private final String raw;

		private RawHTML(RawHTMLMode mode, String raw) {
			this.mode = Utils.validateNonNull("mode", mode);
			this.raw = Utils.validateString("raw", raw, Utils.NORMALIZED_WHITESPACE_REGEX);
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			visitor.visitRawHTML(mode, raw);
		}
	}

	private static class VariationText extends FormattedTextElement {
		private final String[] variations;

		private VariationText(String[] variations) {
			this.variations = Utils.validateNonNull("variations", variations);
			if (variations.length == 0)
				throw new IllegalArgumentException("Variations is empty");
			for (String variation : variations) {
				Utils.validateString("variation", variation, "[A-Za-z0-9-]+");
			}
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitVariationText(variations));
		}
	}

	private static class ExtraAttribute extends FormattedTextElement {
		private final ExtraAttributePriority prio;
		private final String category;
		private final String key;
		private final String value;

		private ExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) {
			this.prio = Utils.validateNonNull("prio", prio);
			this.category = Utils.validateString("category", category, "[a-z0-9]+");
			this.key = Utils.validateString("key", key, "[a-z0-9-]+");
			this.value = Utils.validateString("value", value, "[A-Za-z0-9-]+");
		}

		public <T extends Throwable> void acceptThis(Visitor<T> visitor) throws T {
			accept(visitor.visitExtraAttribute(prio, category, key, value));
		}
	}

	public static enum FormattingInstructionKind {
		BOLD('b', "b", "font-weight: bold;"),
		ITALIC('i', "i", "font-style: italic;"),
		UNDERLINE('u', "u", "text-decoration: underline;"),
		LINK('l', null, "color: blue;"),
		// has to contain a footnote and may be used to render a link text for
		// it (covered range)
		FOOTNOTE_LINK('f', null, "color: blue;"),
		SUBSCRIPT('s', "sub", "font-size: .83em; vertical-align: sub;"),
		SUPERSCRIPT('p', "sup", "font-size: .83em; vertical-align: super;"),
		DIVINE_NAME('d', null, "font-variant: small-caps;"),
		STRIKE_THROUGH('t', null, "text-decoration: line-through;"),
		ADDITION('a', null, "font-style: italic; -bmc-usfm-tag: add;"),
		PSALM_DESCRIPTIVE_TITLE('m', null, "font-style: italic; -bmc-usfm-tag: d;"),
		WORDS_OF_JESUS('w', null, "color: red;");

		private final char code;
		private final String htmlTag;
		private final String css;

		private FormattingInstructionKind(char code, String htmlTag, String css) {
			if (code < 'a' || code > 'z')
				throw new IllegalStateException("Invalid code: " + code);
			this.code = code;
			this.htmlTag = htmlTag;
			this.css = css;
		}

		public char getCode() {
			return code;
		}

		public String getHtmlTag() {
			return htmlTag;
		}

		public String getCss() {
			return css;
		}

		public static FormattingInstructionKind fromChar(char c) {
			for (FormattingInstructionKind k : values()) {
				if (k.code == c)
					return k;
			}
			throw new IllegalArgumentException("Char: " + c);
		}
	}

	public static enum ExtendedLineBreakKind {

		PARAGRAPH('P', false), NO_FIRST_LINE_INDENT('M', false), HANGING_INDENT('H', false), PAGE_BREAK('G', false), BLANK_LINE('B', false), SEMANTIC_DIVISION('D', false),
		TABLE_ROW_FIRST_CELL('R', false), TABLE_ROW_NEXT_CELL('C', false),
		NEWLINE('N', true), POETIC_LINE('L', true), SAME_LINE_IF_POSSIBLE('S', true);

		public static int INDENT_CENTER = -1;
		public static int INDENT_RIGHT_JUSTIFIED = -2;
		private char code;
		private boolean sameParagraph;

		private ExtendedLineBreakKind(char code, boolean sameParagraph) {
			this.code = code;
			this.sameParagraph = sameParagraph;
		}

		public char getCode() {
			return code;
		}

		public boolean isSameParagraph() {
			return sameParagraph;
		}

		public boolean isClosingTable() {
			return this != NEWLINE && this != TABLE_ROW_FIRST_CELL && this != TABLE_ROW_NEXT_CELL;
		}

		@Deprecated
		public LineBreakKind toLineBreakKind(int indent) {
			if (this == TABLE_ROW_NEXT_CELL)
				return LineBreakKind.NEWLINE_WITH_INDENT;
			if (!sameParagraph)
				return LineBreakKind.PARAGRAPH;
			return indent == 0 ? LineBreakKind.NEWLINE : LineBreakKind.NEWLINE_WITH_INDENT;
		}

		public static ExtendedLineBreakKind fromChar(char c) {
			for (ExtendedLineBreakKind k : values()) {
				if (k.code == c)
					return k;
			}
			throw new IllegalArgumentException("Char: " + c);
		}
	}

	@Deprecated
	public static enum LineBreakKind {
		PARAGRAPH, NEWLINE, NEWLINE_WITH_INDENT;
	}

	public static enum RawHTMLMode {
		ONLINE, OFFLINE, BOTH;
	}

	public static enum HyperlinkType {
		ANCHOR, INTERNAL_LINK, EXTERNAL_LINK, IMAGE;
	}

	public static enum ExtraAttributePriority {
		KEEP_CONTENT, SKIP, ERROR;

		public <T extends Throwable> Visitor<T> handleVisitor(String category, Visitor<T> visitor) throws T {
			switch (this) {
			case ERROR:
				throw new IllegalArgumentException("Unhandled extra attribute of category " + category);
			case KEEP_CONTENT:
				return visitor;
			case SKIP:
				return null;
			default:
				throw new IllegalStateException("Unsupported priority: " + this);
			}
		}
	}

	public static interface Visitor<T extends Throwable> {

		/**
		 * @param elementTypes
		 *            Element types (initially {@code null})
		 * @return desired depth of element types, or 0 if sufficient
		 */
		public int visitElementTypes(String elementTypes) throws T;

		public Visitor<T> visitHeadline(int depth) throws T;

		public void visitStart() throws T;

		public void visitText(String text) throws T;

		public Visitor<T> visitFootnote(boolean ofCrossReferences) throws T;

		public Visitor<T> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws T;

		public Visitor<T> visitFormattingInstruction(FormattingInstructionKind kind) throws T;

		public Visitor<T> visitCSSFormatting(String css) throws T;

		public void visitVerseSeparator() throws T;

		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws T;

		public Visitor<T> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws T;

		public Visitor<T> visitDictionaryEntry(String dictionary, String entry) throws T;

		public void visitRawHTML(RawHTMLMode mode, String raw) throws T;

		public Visitor<T> visitSpeaker(String labelOrStrongs) throws T;

		public Visitor<T> visitHyperlink(HyperlinkType type, String target) throws T;

		public Visitor<T> visitVariationText(String[] variations) throws T;

		public Visitor<T> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws T;

		/**
		 * @return whether to visit the element again
		 */
		public boolean visitEnd() throws T;
	}

	public static class VisitorAdapter<T extends Throwable> implements Visitor<T> {

		private Visitor<T> next;

		public VisitorAdapter(Visitor<T> next) throws T {
			this.next = next;
		}

		protected Visitor<T> getVisitor() throws T {
			return next;
		}

		protected Visitor<T> wrapChildVisitor(Visitor<T> childVisitor) throws T {
			return childVisitor;
		}

		protected void beforeVisit() throws T {
		}

		@Override
		public int visitElementTypes(String elementTypes) throws T {
			return getVisitor() == null ? 0 : getVisitor().visitElementTypes(elementTypes);
		}

		@Override
		public Visitor<T> visitHeadline(int depth) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitHeadline(depth));
		}

		@Override
		public void visitStart() throws T {
			if (getVisitor() != null)
				getVisitor().visitStart();
		}

		@Override
		public void visitText(String text) throws T {
			beforeVisit();
			if (getVisitor() != null)
				getVisitor().visitText(text);
		}

		@Override
		public Visitor<T> visitFootnote(boolean ofCrossReferences) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitFootnote(ofCrossReferences));
		}

		@Override
		public Visitor<T> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitCrossReference(firstBookAbbr, firstBook, firstChapter, firstVerse, lastBookAbbr, lastBook, lastChapter, lastVerse));
		}

		@Override
		public Visitor<T> visitFormattingInstruction(FormattingInstructionKind kind) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitFormattingInstruction(kind));
		}

		@Override
		public Visitor<T> visitCSSFormatting(String css) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitCSSFormatting(css));
		}

		@Override
		public void visitVerseSeparator() throws T {
			beforeVisit();
			if (getVisitor() != null)
				getVisitor().visitVerseSeparator();
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws T {
			beforeVisit();
			if (getVisitor() != null)
				getVisitor().visitLineBreak(kind, indent);
		}

		@Override
		public Visitor<T> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceIndices, attributeKeys, attributeValues));
		}

		@Override
		public Visitor<T> visitDictionaryEntry(String dictionary, String entry) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitDictionaryEntry(dictionary, entry));
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws T {
			beforeVisit();
			if (getVisitor() != null)
				getVisitor().visitRawHTML(mode, raw);
		}

		@Override
		public Visitor<T> visitSpeaker(String labelOrStrongs) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitSpeaker(labelOrStrongs));
		}

		@Override
		public Visitor<T> visitHyperlink(HyperlinkType type, String target) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitHyperlink(type, target));
		}

		@Override
		public Visitor<T> visitVariationText(String[] variations) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitVariationText(variations));
		}

		@Override
		public Visitor<T> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws T {
			beforeVisit();
			return wrapChildVisitor(getVisitor() == null ? null : getVisitor().visitExtraAttribute(prio, category, key, value));
		}

		@Override
		public boolean visitEnd() throws T {
			return getVisitor() == null ? false : getVisitor().visitEnd();
		}
	}

	private static class AppendVisitor implements Visitor<RuntimeException> {
		private FormattedText target;

		public AppendVisitor(FormattedText target) {
			this.target = target;
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) {
			Headline h = new Headline(depth);
			if (target.elements.size() == 0)
				target.headlines.add(h);
			else
				target.elements.add(h);
			return new AppendVisitor(h);
		}

		@Override
		public void visitStart() {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			if (text.length() == 0)
				return;
			if (target.elements.size() > 0 && target.elements.get(target.elements.size() - 1) instanceof Text) {
				Text oldText = (Text) target.elements.remove(target.elements.size() - 1);
				if (oldText.text.endsWith(" ") && text.startsWith(" "))
					text = text.substring(1);
				text = oldText.text + text;
			}
			target.elements.add(new Text(text));
		}

		@Override
		public Visitor<RuntimeException> visitFootnote(boolean ofCrossReferences) throws RuntimeException {
			return addAndVisit(new Footnote(ofCrossReferences));
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws RuntimeException {
			return addAndVisit(new CrossReference(firstBookAbbr, firstBook, firstChapter, firstVerse, lastBookAbbr, lastBook, lastChapter, lastVerse));
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			return addAndVisit(new FormattingInstruction(kind));
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			return addAndVisit(new CSSFormatting(css));
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			target.elements.add(new VerseSeparator());
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws RuntimeException {
			target.elements.add(new LineBreak(kind, indent));
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
			return addAndVisit(new GrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceIndices, attributeKeys, attributeValues));
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			return addAndVisit(new DictionaryEntry(dictionary, entry));
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			target.elements.add(new RawHTML(mode, raw));
		}

		@Override
		public Visitor<RuntimeException> visitSpeaker(String labelOrStrongs) throws RuntimeException {
			return addAndVisit(new Speaker(labelOrStrongs));
		}

		@Override
		public Visitor<RuntimeException> visitHyperlink(HyperlinkType type, String target) throws RuntimeException {
			return addAndVisit(new Hyperlink(type, target));
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			return addAndVisit(new VariationText(variations));
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			return addAndVisit(new ExtraAttribute(prio, category, key, value));
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			return false;
		}

		private Visitor<RuntimeException> addAndVisit(FormattedTextElement elem) {
			target.elements.add(elem);
			return new AppendVisitor(elem);
		}
	}

	private static class ValidatingVisitor implements Visitor<RuntimeException> {

		private static boolean IGNORE_WHITESPACE_ISSUES = Boolean.getBoolean("biblemulticonverter.validate.ignore.whitespace");
		private static boolean IGNORE_EMPTY_ELEMENTS = Boolean.getBoolean("biblemulticonverter.validate.ignore.empty");

		private final Bible bible;
		private final BookID book;
		private final List<String> danglingReferences;
		private final Map<String, Set<String>> dictionaryEntries;
		private final String location;
		private final Map<String, Set<ValidationCategory>> validationCategories;
		private final Set<String> internalAnchors;
		private final Set<String> internalLinks;
		private final ValidationContext context;

		private int lastHeadlineDepth = 0;
		private boolean leadingWhitespaceAllowed = false;
		private boolean trailingWhitespaceFound = false;
		private boolean isEmpty = true;

		private ValidatingVisitor(Bible bible, BookID book, List<String> danglingReferences, Map<String, Set<String>> dictionaryEntries, String location, Map<String, Set<FormattedText.ValidationCategory>> validationCategories, Set<String> internalAnchors, Set<String> internalLinks, ValidationContext context) {
			this.bible = bible;
			this.book = book;
			this.danglingReferences = danglingReferences;
			this.dictionaryEntries = dictionaryEntries;
			this.location = location;
			this.validationCategories = validationCategories;
			this.internalAnchors = internalAnchors;
			this.internalLinks = internalLinks;
			this.context = context;
		}

		private ValidatingVisitor createValidatingVisitor(ValidationContext context) {
			return new ValidatingVisitor(bible, book, danglingReferences, dictionaryEntries, location, validationCategories, internalAnchors, internalLinks, context);
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			if (context != ValidationContext.VERSE)
				violation(ValidationCategory.INVALID_SEPARATOR_LOCATION, "");
			visitInlineElement();
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			isEmpty = false;
			lastHeadlineDepth = 0;
			if (text.startsWith(" ")) {
				if (trailingWhitespaceFound && !IGNORE_WHITESPACE_ISSUES)
					violation(ValidationCategory.WHITESPACE_ADJACENT, "");
				if (!leadingWhitespaceAllowed && !IGNORE_WHITESPACE_ISSUES)
					violation(ValidationCategory.WHITESPACE_AT_BEGINNING, "");
			}
			trailingWhitespaceFound = text.endsWith(" ");
		}

		@Override
		public void visitStart() {
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws RuntimeException {
			if (trailingWhitespaceFound && !IGNORE_WHITESPACE_ISSUES)
				violation(ValidationCategory.WHITESPACE_BEFORE_BREAK, "");
			if (context.ordinal() >= ValidationContext.HEADLINE.ordinal() && context != ValidationContext.FOOTNOTE)
				violation(ValidationCategory.INVALID_LINE_BREAK_LOCATION, "");
			leadingWhitespaceAllowed = false;
			isEmpty = false;
			lastHeadlineDepth = 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) {
			if (context.ordinal() >= ValidationContext.HEADLINE.ordinal())
				violation(ValidationCategory.NESTED_HEADLINE, "");
			if (depth <= lastHeadlineDepth)
				violation(ValidationCategory.INVALID_HEADLINE_DEPTH_ORDER, depth + " after " + lastHeadlineDepth);
			if (trailingWhitespaceFound && !IGNORE_WHITESPACE_ISSUES)
				violation(ValidationCategory.WHITESPACE_BEFORE_HEADLINE, "");
			leadingWhitespaceAllowed = false;
			lastHeadlineDepth = depth == 9 ? 8 : depth;
			isEmpty = false;
			return createValidatingVisitor(ValidationContext.HEADLINE);
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			visitInlineElement();
			return createValidatingVisitor(context);
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			visitInlineElement();
			return createValidatingVisitor(context);
		}

		@Override
		public Visitor<RuntimeException> visitFootnote(boolean ofCrossReferences) throws RuntimeException {
			if (context.ordinal() >= ValidationContext.FOOTNOTE.ordinal())
				violation(ValidationCategory.NESTED_FOOTNOTE, "");
			visitInlineElement();
			return createValidatingVisitor(ValidationContext.FOOTNOTE);
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			isEmpty = false;
			if (prio == ExtraAttributePriority.KEEP_CONTENT) {
				visitInlineElement();
				return createValidatingVisitor(context);
			} else if (prio == ExtraAttributePriority.ERROR) {
				// no idea; therefore be as lax as possible
				trailingWhitespaceFound = false;
				leadingWhitespaceAllowed = true;
				lastHeadlineDepth = 0;
			}
			return null;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			visitInlineElement();
		}

		@Override
		public Visitor<RuntimeException> visitSpeaker(String labelOrStrongs) throws RuntimeException {
			visitInlineElement();
			return createValidatingVisitor(context);
		}

		@Override
		public Visitor<RuntimeException> visitHyperlink(HyperlinkType type, String target) throws RuntimeException {
			visitInlineElement();
			switch(type) {
			case ANCHOR:
				if (!internalAnchors.add(target)) {
					violation(ValidationCategory.DUPLICATE_ANCHOR, target);
				}
				break;
			case IMAGE:
				if (target.matches("[A-Za-z0-9.-]+"))
					break;
				// fall-through
			case EXTERNAL_LINK:
				try {
					new URL(target);
				} catch (MalformedURLException ex) {
					violation(ValidationCategory.MALFORMED_HYPERLINK, target);
				}
				break;
			case INTERNAL_LINK:
				if (!target.startsWith("#"))
					violation(ValidationCategory.MALFORMED_HYPERLINK, target);
				internalLinks.add(target.substring(1));
				break;
			default:
				break;
			}
			ValidatingVisitor vvv =  createValidatingVisitor(context);
			if (type == HyperlinkType.ANCHOR)
				vvv.isEmpty = false;
			return vvv;
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			visitInlineElement();
			return createValidatingVisitor(context);
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
			if (context.ordinal() >= ValidationContext.LINK.ordinal())
				violation(ValidationCategory.NESTED_LINK, "");
			visitInlineElement();
			if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					validateDictionaryEntry("strongs", (strongsPrefixes != null ? "" + strongsPrefixes[i] : book.isNT() ? "G" : "H") + strongs[i] + (strongsSuffixes != null && strongsSuffixes[i] != ' ' ? "" + strongsSuffixes[i] : ""));
				}
			}
			return createValidatingVisitor(ValidationContext.LINK);
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			if (context.ordinal() >= ValidationContext.LINK.ordinal())
				violation(ValidationCategory.NESTED_LINK, "");
			visitInlineElement();
			validateDictionaryEntry(dictionary, entry);
			return createValidatingVisitor(ValidationContext.LINK);
		}

		private void validateDictionaryEntry(String dictionary, String entry) {
			if (dictionaryEntries != null && danglingReferences != null) {
				Set<String> entries = dictionaryEntries.get(dictionary);
				if (entries == null) {
					if (!danglingReferences.contains("[" + dictionary + "]"))
						danglingReferences.add("[" + dictionary + "]");
				} else if (!entries.contains(entry)) {
					danglingReferences.add("[" + dictionary + "]:" + entry);
				}
			}
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String firstBookAbbr, BookID firstBookID, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBookID, int lastChapter, String lastVerse) throws RuntimeException {
			if (context.ordinal() >= ValidationContext.XREF.ordinal())
				violation(ValidationCategory.NESTED_XREF, "");
			if (context == ValidationContext.VERSE)
				violation(ValidationCategory.INVALID_XREF_LOCATION, "");
			visitInlineElement();
			Book firstBook = bible.getBook(firstBookAbbr, firstBookID);
			Book lastBook = bible.getBook(lastBookAbbr, lastBookID);
			int firstIndex = firstBook == null || firstBook.getChapters().size() < firstChapter ? -1 : firstBook.getChapters().get(firstChapter - 1).getVerseIndex(firstVerse);
			int lastIndex = lastBook == null || lastBook.getChapters().size() < lastChapter ? -1 : lastVerse.equals("*") ? 999 : lastBook.getChapters().get(lastChapter - 1).getVerseIndex(lastVerse);
			if (firstIndex == -1 && danglingReferences != null) {
				danglingReferences.add(firstBookAbbr + "(" + firstBookID.getOsisID() + ") " + firstChapter + ":" + firstVerse);
			}
			if (lastIndex == -1 && danglingReferences != null) {
				danglingReferences.add(lastBookAbbr + "(" + lastBookID.getOsisID() + ") " + lastChapter + ":" + lastVerse);
			}
			if (firstIndex != -1 && lastIndex != -1 && firstChapter == lastChapter && firstBookID == lastBookID) {
				if (firstIndex > lastIndex)
					violation(ValidationCategory.MALFORMED_XREF, "");
			}
			return createValidatingVisitor(ValidationContext.XREF);
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			if (trailingWhitespaceFound && !IGNORE_WHITESPACE_ISSUES)
				violation(ValidationCategory.WHITESPACE_AT_END, "");
			if (isEmpty && !IGNORE_EMPTY_ELEMENTS)
				violation(ValidationCategory.EMPTY_ELEMENT, "");
			return false;
		}

		private void visitInlineElement() {
			isEmpty = false;
			leadingWhitespaceAllowed = true;
			trailingWhitespaceFound = false;
			lastHeadlineDepth = 0;
		}

		private void violation(ValidationCategory category, String arg) {
			category.throwOrRecord(location, validationCategories, arg);
		}
	}

	private static class ElementTypeVisitor implements Visitor<RuntimeException> {

		private final StringBuilder sb;
		private final int depth;
		private final String suffix;

		public ElementTypeVisitor(StringBuilder sb, int depth, String suffix) {
			this.sb = sb;
			this.depth = depth;
			this.suffix = suffix;
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) {
			return visitNestedType('h');
		}

		@Override
		public void visitStart() {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			sb.append('t');
		}

		@Override
		public Visitor<RuntimeException> visitFootnote(boolean ofCrossReferences) throws RuntimeException {
			return visitNestedType('f');
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws RuntimeException {
			return visitNestedType('x');
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			return visitNestedType('F');
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			return visitNestedType('c');
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			sb.append("/");
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws RuntimeException {
			sb.append('b');
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
			return visitNestedType('g');
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			return visitNestedType('d');
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			sb.append('H');
		}

		@Override
		public Visitor<RuntimeException> visitSpeaker(String labelOrStrongs) throws RuntimeException {
			return visitNestedType('s');
		}

		@Override
		public Visitor<RuntimeException> visitHyperlink(HyperlinkType type, String target) throws RuntimeException {
			return visitNestedType('l');
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			return visitNestedType('o');
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			return visitNestedType('X');
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			sb.append(suffix);
			return false;
		}

		private ElementTypeVisitor visitNestedType(char ch) {
			sb.append(ch);
			if (depth > 1) {
				sb.append('<');
				return new ElementTypeVisitor(sb, depth - 1, ">");
			} else {
				return null;
			}
		}
	}

	private static enum ValidationContext {
		VERSE,
		NORMAL_TEXT,
		HEADLINE,
		FOOTNOTE,
		XREF,
		LINK,
	}

	public static enum ValidationCategory {
		BIBLE_WITHOUT_BOOKS("Bible does not have books"),
		ONLY_METADATA_BOOK("Bible has only metadata book"),
		MALFORMED_DICTIONARY_ENTRY("Malformed dictionary entry: "),
		MALFORMED_HYPERLINK("Malformed hyperlink: "),
		AMBIGUOUS_BOOK_ID("Ambiguous book id "),
		DUPLICATE_BOOK_REFERENCE("Duplicate book reference in "),
		DUPLICATE_ANCHOR("Duplicate link anchor: "),
		PROLOG_VALIDATION_FAILED(""),
		BOOK_WITHOUT_CHAPTERS("Book has no chapters: "),
		LAST_CHAPTER_WITHOUT_CONTENT("Last chapter has neither prolog nor verses: "),
		DUPLICATE_VERSE("Duplicate verse number "),
		OVERLAPPING_VERSE_RANGES("Overlapping verse ranges: "),
		NOT_FINISHED("Formatted text not marked as finished (this may dramatically increase memory usage): "),
		EMPTY_ELEMENT("Element is empty"),
		MALFORMED_XREF("First xref verse is after last xref verse"),
		MISSING_INTERNAL_ANCHOR("Anchor missing for internal link: "),

		INVALID_VIRTUAL_VERSE_ORDER("Invalid order of virtual verses: "),
		INVALID_HEADLINE_DEPTH_ORDER("Invalid headline depth order: "),
		INVALID_SEPARATOR_LOCATION("Verse separators are only allowed in verses!"),
		INVALID_XREF_LOCATION("cross references may only appear inside footnotes, prologs or headlines"),
		INVALID_LINE_BREAK_LOCATION("Line breaks only allowed in block context or footnotes"),

		WHITESPACE_ADJACENT("Whitespace adjacent to whitespace found"),
		WHITESPACE_AT_BEGINNING("No whitespace allowed at beginning or after line breaks or headlines"),
		WHITESPACE_BEFORE_BREAK("No whitespace allowed before line breaks"),
		WHITESPACE_AT_END("No whitespace allowed at end of element"),
		WHITESPACE_BEFORE_HEADLINE("No whitespace allowed before headlines"),

		NESTED_HEADLINE("Invalid nested headline"),
		NESTED_FOOTNOTE("Invalid nested footnote"),
		NESTED_LINK("Invalid nested link"),
		NESTED_XREF("Invalid nested cross reference");

		private String description;

		private ValidationCategory(String description) {
			this.description = description;
		}

		public void throwOrRecord(String location, Map<String, Set<FormattedText.ValidationCategory>> validationCategories, String arg) {
			if (Boolean.getBoolean("biblemulticonverter.validate.ignore."+name()))
				return;
			if (validationCategories == null)
				throw new IllegalStateException(description + arg);
			validationCategories.computeIfAbsent(location, (x) -> EnumSet.noneOf(ValidationCategory.class)).add(this);
		}
	}
}
