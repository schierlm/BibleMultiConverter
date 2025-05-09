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
	private static final String RMAC_DECLINED = "[NARCDTKIXQFSP](-[123]?[NVGDA][SP][MFN]?)?|[NA]-[NVGDA][SP][MFN]-([PLT]|[PL]G|LI)|A-[NVGDA][SP][MFN]-NUI|S-[123][SP][NVGDA][SP][MFN]";
	private static final String RMAC_VERBS = "V-([PIFARLX]|2[PFARL])[AMPEDONQX][ISOMNP](-([123][SP]|[NGDAV][SPD][MFN]))?";
	public static final String RMAC_REGEX = "(" + RMAC_UNDECLINED + "|" + RMAC_DECLINED + ")(-(S|C|ABB|I|N|K|ATT|ARAM|HEB))?|" + RMAC_VERBS + "(-ATT|-ARAM|-HEB)?";

	public static final String WIVU_PART_REGEX = "C|D|Np[mflt]?|(A[acgo]|N[cgtx])(|[bcfmx][dpsx][acd])|(P[dfipr]|S[dhnp])(|[123x][bcfm][dps])|R[d]?|T[acdeijmnor]?|V[DGHKLMNOPQabcefhijklmopqrstuvwyz][pqiwhjvrsauc](|[bfm]|[123][bcfm][dps]|[bcfm][dps][acd]|[ac])";
	public static final String WIVU_REGEX = "[HA](" + WIVU_PART_REGEX + ")(//?(" + WIVU_PART_REGEX + "))*";

	public static final String MORPH_REGEX = RMAC_REGEX + "|" + WIVU_REGEX;

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

	public static int parseStrongs(String value, char assumedPrefix, char[] prefixSuffixHolder) {
		String strongssuffix = System.getProperty("biblemulticonverter.strongssuffix", "");
		if (!value.matches("[A-Z].*") && assumedPrefix >= 'A' && assumedPrefix <= 'Z')
			value = assumedPrefix + value;
		if (strongssuffix.equals("cut") && value.matches("[A-Z]0*[1-9][0-9]*[a-zA-Z]")) {
			value = value.substring(0, value.length() - 1);
		} else if (strongssuffix.equals("xy") && value.matches("[GH]0*[1-9][0-9]*[a-zA-Z]")) {
			int longNumber = Integer.parseInt(value.substring(1, value.length() - 1)) * 100;
			char suffix = value.charAt(value.length() - 1);
			if (suffix >= 'a' && suffix <= 'z') {
				longNumber += suffix - 'a' + 1;
			} else if (suffix >= 'A' && suffix <= 'Z') {
				longNumber += suffix - 'A' + 31;
			} else {
				throw new IllegalStateException();
			}
			value = (char) (value.charAt(0) - 'G' + 'X') + "" + longNumber;
		}
		if (value.matches("[A-Z]0*[1-9][0-9]*[a-zA-Z]") && prefixSuffixHolder != null) {
			prefixSuffixHolder[1] = value.charAt(value.length() - 1);
			value = value.substring(0, value.length() - 1);
		} else if (prefixSuffixHolder != null) {
			prefixSuffixHolder[1] = ' ';
		}
		if (value.matches("0*[1-9][0-9]*") && assumedPrefix != '\0') {
			if (prefixSuffixHolder != null)
				prefixSuffixHolder[0] = assumedPrefix;
			return Integer.parseInt(value);
		} else if (value.matches("[A-Z]0*[1-9][0-9]*")) {
			if (prefixSuffixHolder != null)
				prefixSuffixHolder[0] = value.charAt(0);
			return Integer.parseInt(value.substring(1));
		}
		return -1;
	}

	public static String formatStrongs(boolean nt, int index, char[] prefixes, int[] numbers, char[] suffixes, String suffixSeparator) {
		return formatStrongs(nt, prefixes == null ? '\0' : prefixes[index], numbers[index], suffixes == null ? ' ' : suffixes[index], suffixSeparator);
	}

	public static String formatStrongs(boolean nt, char prefix, int number, char suffix, String suffixSeparator) {
		if ((prefix == 'X' || prefix == 'Y') && suffix == ' ' && System.getProperty("biblemulticonverter.strongssuffix", "").equals("xy")) {
			int suffixNum = number % 100;
			char suffixChar = '\0';
			number = number / 100;
			prefix = (char) (prefix - 'X' + 'G');
			if (suffixNum >= 1 && suffixNum <= 26) {
				suffixChar = (char) (suffixNum - 1 + 'a');
			} else if (suffixNum >= 31 && suffixNum <= 56) {
				suffixChar = (char) (suffixNum - 31 + 'A');
			}
			if (suffixChar != '\0') {
				return prefix + "" + number + suffixSeparator + suffixChar;
			}
		}
		return (prefix != '\0' ? "" + prefix : nt ? "G" : "H") + number + (suffix == ' ' ? "" : suffixSeparator + suffix);
	}
}
