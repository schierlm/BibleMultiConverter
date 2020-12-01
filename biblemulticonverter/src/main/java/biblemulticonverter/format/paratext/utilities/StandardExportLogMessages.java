package biblemulticonverter.format.paratext.utilities;

import biblemulticonverter.format.paratext.ParatextBook;
import biblemulticonverter.format.paratext.ParatextCharacterContent;

/**
 * A simple class that provides some standard log messages that can be used to make log messages consistent across
 * different exporters.
 */
public class StandardExportLogMessages {

	private final String targetFormat;

	/**
	 * @param targetFormat the target format that this logger should use in its messages, e.g. "USFM 2" or "USX 3"
	 */
	public StandardExportLogMessages(String targetFormat) {
		this.targetFormat = targetFormat;
	}

	/**
	 * Log a message to indicate that a specific paragraph style marker was replaced with another paragraph style marker
	 * during the export.
	 *
	 * @param original    the paragraph style maker that was replaced.
	 * @param replacement the paragraph style marker that was used as replacement.
	 */
	public void logReplaceWarning(ParatextBook.ParagraphKind original, ParatextBook.ParagraphKind replacement) {
		System.out.println("WARNING: Replaced paragraph style marker `" + original.getTag() + "` with `" +
				replacement.getTag() + "`, because this paragraph style cannot be represented in " + targetFormat +
				".");
	}

	/**
	 * Log a message to indicate that a specific char style marker was replaced with another char style marker during
	 * the export.
	 *
	 * @param original    the char style maker that was replaced.
	 * @param replacement the char style marker that was used as replacement.
	 */
	public void logReplaceWarning(ParatextCharacterContent.AutoClosingFormattingKind original, ParatextCharacterContent.AutoClosingFormattingKind replacement) {
		System.out.println("WARNING: Replaced char style marker `" + original.getTag() + "` with `" +
				replacement.getTag() + "`, because this char style cannot be represented in " + targetFormat + ".");
	}

	/**
	 * @see #logSkippedWarning(ParatextCharacterContent.AutoClosingFormattingKind, String)
	 */
	public void logSkippedWarning(ParatextCharacterContent.AutoClosingFormattingKind autoClosingFormatting) {
		logSkippedWarning(autoClosingFormatting, null);
	}

	/**
	 * Log a message to indicate that a specific char style marker was skipped and not added to the export output.
	 *
	 * @param autoClosingFormatting the char style maker that was skipped.
	 * @param extraExplanation      an optional explanation of why the char style marker was skipped, may be null.
	 */
	public void logSkippedWarning(ParatextCharacterContent.AutoClosingFormattingKind autoClosingFormatting, String extraExplanation) {
		System.out.println("WARNING: Skipped char style marker `" + autoClosingFormatting.getTag() + "`," +
				"because this char style cannot be represented in " + targetFormat + ", instead its contents have been been added"
				+ "without this marker." + (extraExplanation != null ? " " + extraExplanation : ""));
	}

	/**
	 * Log a message to indicate that a specific char style marker was removed and not added to the export output,
	 * including it's contents.
	 *
	 * @param autoClosingFormatting the car style marker that was removed
	 */
	public void logRemovedWarning(ParatextCharacterContent.AutoClosingFormattingKind autoClosingFormatting) {
		System.out.println("WARNING: Removed char style marker `" + autoClosingFormatting.getTag() + "`," +
				"because this char style cannot be represented in " + targetFormat + ".");
	}
}
