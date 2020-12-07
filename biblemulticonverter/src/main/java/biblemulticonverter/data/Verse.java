package biblemulticonverter.data;

import java.util.regex.Pattern;

/**
 * A single verse, consisting of a verse number and formatted text.
 */
public class Verse extends FormattedText {

	private final static Pattern VALID_VERSE_PATTERN = Pattern.compile(Utils.VERSE_REGEX);
	private final String number;

	public Verse(String number) {
		this.number = Utils.validateString("number", number, Utils.VERSE_REGEX);
	}

	public String getNumber() {
		return number;
	}
	
	public static boolean isValidNumber(String number) {
		return VALID_VERSE_PATTERN.matcher(number).matches();
	}
}
