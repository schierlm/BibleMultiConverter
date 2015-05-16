package biblemulticonverter.data;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * Utility functions.
 */
public class Utils {

	private static final ConcurrentMap<String, Pattern> patternCache = new ConcurrentHashMap<String, Pattern>();

	public static final String NORMALIZED_WHITESPACE_REGEX = "\\S++( \\S++)*+";

	public static final String BOOK_ABBR_REGEX = "[A-Z0-9][A-Z0-9a-z.äöü]+";
	public static final String VERSE_REGEX = "[1-9][0-9,/.-]*[a-zG]?";

	private static final String RMAC_UNDECLINED = "ADV|CONJ|COND|PRT|PREP|INJ|ARAM|HEB|N-PRI|A-NUI|N-LI|N-OI";
	private static final String RMAC_DECLINED = "[NARCDTKIXQFSP](-[123]?[NVGDA][SP][MFN]?)?";
	private static final String RMAC_VERBS = "V-([PIFARLX]|2[FARL])[AMPEDON][ISOMNP](-([123][SP]|[NGDAV][SPD][MFN]))?";
	public static final String RMAC_REGEX = "(" + RMAC_UNDECLINED + "|" + RMAC_DECLINED + ")(-(S|C|ABB|I|N|K|ATT))?|" + RMAC_VERBS + "(-ATT)?";

	public static int validateNumber(String name, int value, int min, int max) {
		if (value < min || value > max)
			throw new IllegalArgumentException(name + " is invalid: " + value);
		return value;
	}

	public static String validateString(String name, String value, String regex) {
		if (!compilePattern(regex).matcher(value).matches())
			throw new IllegalArgumentException(name + " is invalid: " + value);
		return value;
	}

	public static <T> T validateNonNull(String name, T value) {
		if (value == null)
			throw new IllegalArgumentException(name + " is null");
		return value;
	}

	public static Pattern compilePattern(String regex) {
		Pattern result = patternCache.get(regex);
		if (result == null) {
			result = Pattern.compile(regex);
			patternCache.putIfAbsent(regex, result);
		}
		return result;
	}
}
