package biblemulticonverter.format.paratext.utilities;

import java.util.regex.Pattern;

public class TextUtilities {

	private static final Pattern WHITESPACE_NORMALIZATION_PATTERN = Pattern.compile("[\\p{Cc}\\p{Z}]+");

	/**
	 * Normalises whitespace according to: https://ubsicap.github.io/usfm/about/syntax.html#whitespace-normalization
	 *
	 * @param text the input text
	 * @return the input text with normalized whitespaces.
	 */
	public static String whitespaceNormalization(String text) {
		return WHITESPACE_NORMALIZATION_PATTERN.matcher(text).replaceAll(" ").trim();
	}
}
