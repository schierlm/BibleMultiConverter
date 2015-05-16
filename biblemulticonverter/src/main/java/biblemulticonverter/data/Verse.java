package biblemulticonverter.data;

/**
 * A single verse, consisting of a verse number and formatted text.
 */
public class Verse extends FormattedText {

	public final String number;

	public Verse(String number) {
		this.number = Utils.validateString("number", number, Utils.VERSE_REGEX);
	}

	public String getNumber() {
		return number;
	}
}
