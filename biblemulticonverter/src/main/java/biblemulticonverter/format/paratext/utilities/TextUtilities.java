package biblemulticonverter.format.paratext.utilities;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtilities {

	private static final Pattern WHITESPACE_NORMALIZATION_PATTERN = Pattern.compile("[\\p{Cc}\\p{Z}]+");
	private static final Pattern XML_INDENTION_MATCHER = Pattern.compile("[\\n\\r]+ {2,}");
	private static final Pattern NEW_LINE_MATCHER = Pattern.compile("[\\n\\r]+");

	/**
	 * Normalises whitespace according to: https://ubsicap.github.io/usfm/about/syntax.html#whitespace-normalization
	 *
	 * @param text the input text
	 * @return the input text with normalized whitespaces.
	 */
	public static String whitespaceNormalization(String text) {
		// First detect XML indention, which can occur in mixed content XML, and completly remove the indention,
		// Since it is undesired to transform indention to spaces.
		String result = XML_INDENTION_MATCHER.matcher(text).replaceAll("");

		// Then normalize any leftover whitespace to one space.
		result = WHITESPACE_NORMALIZATION_PATTERN.matcher(result).replaceAll(" ");
		return result;
	}

	/**
	 * Normalizes whitespace usage in USFM content, by replacing any sequence of control characters and whitespace with
	 * a single space.
	 *
	 * @param preserveSpacesAtEndOfLines when set to true the normalization will preserve single spaces that might be
	 *                                   present at the end of a line, which would otherwise be removed/ignored.
	 */
	public static String usfmWhitespaceNormalization(String text, boolean preserveSpacesAtEndOfLines) {
		if (!preserveSpacesAtEndOfLines) {
			return WHITESPACE_NORMALIZATION_PATTERN.matcher(text).replaceAll(" ").trim();
		}
		Matcher matcher = WHITESPACE_NORMALIZATION_PATTERN.matcher(text);
		StringBuffer buffer = new StringBuffer();
		while (matcher.find()) {
			final String match = matcher.group();
			// If the match starts with a space, but also contains a new line, replace it with 2 spaces instead of 1 to
			// preserve the initial space. The parser normally ignores spaces that occur before a new marker, and if
			// this whitespace sits before another marker it would therefore disappear.
			//
			// Example:
			// USFM (notice the space after `earth.`)
			// \v 1 ... heavens and the earth. 
			// \v 2 The earth was formless and empty...
			//
			// XML: (notice the space after `earth.`)
			// <verse number="1" style="v" sid="GEN 1:1" />... created the heavens and the earth. <verse eid="GEN 1:1" />
			// <verse number="2" style="v" sid="GEN 1:2" />The earth was formless and empty...<verse eid="GEN 1:2" />
			//
			// For USFM without adding two spaces, the resulting text would be normalized to:
			// \v 1 ... heavens and the earth. \v 2 The earth was formless and empty...
			//
			// The USFM parser will then see the space between `earth.` and `\v` as separator and not as part of the
			// verse, therefore importing the text as `created the heavens and the earth.`
			//
			// When exporting to USX again the resulting XML would be:
			//
			// <verse number="1" style="v" sid="GEN 1:1" />... created the heavens and the earth.<verse eid="GEN 1:1" />
			// <verse number="2" style="v" sid="GEN 1:2" />The earth was formless and empty...<verse eid="GEN 1:2" />
			//
			// Notice how there is no longer a space between `earth.` and `<verse eid="GEN 1:1" />`. This means when
			// some information has been lost in the process of converting from USX to USX (or USFM to USX).
			if (match.startsWith(" ") && NEW_LINE_MATCHER.matcher(match).find()) {
				matcher.appendReplacement(buffer, "  ");
			} else {
				matcher.appendReplacement(buffer, " ");
			}
		}
		matcher.appendTail(buffer);

		// Trim left over spaces at the start (never trim end to avoid accidental removal of a single space at the end
		// of a verse/book)
		return trimSpacesAtStart(buffer.toString());
	}

	private static String trimSpacesAtStart(String text) {
		return text.replaceAll("^ +", "");
	}
}
