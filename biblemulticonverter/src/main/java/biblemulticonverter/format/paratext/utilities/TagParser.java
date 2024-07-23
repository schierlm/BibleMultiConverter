package biblemulticonverter.format.paratext.utilities;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser that can be used to parse USX/USFM element/marker tags, such as: {@code p, q1, v, c}.
 */
public class TagParser {

	private static final Pattern NUMBER_MATCHER = Pattern.compile("^([a-z]+)([1-9]*)$");

	private String baseTag = null;
	private int number = -1;

	private void reset() {
		baseTag = null;
		number = -1;
	}

	public boolean parse(String tag) {
		reset();
		Matcher matcher = NUMBER_MATCHER.matcher(tag);
		boolean isValid = matcher.matches();
		if (isValid) {
			baseTag = matcher.group(1);
			String rawNumber = matcher.group(2);
			if (rawNumber != null && !rawNumber.isEmpty()) {
				number = Integer.parseInt(rawNumber);
			} else {
				number = -1;
			}
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Returns the parsed number.
	 *
	 * @return the tags number or -1 if the tag does not have a number or no tag has been parsed yet, or the parsed tag
	 * was not valid.
	 */
	public int getNumber() {
		return number;
	}

	public String getTag() {
		return baseTag;
	}
}
