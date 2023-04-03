package biblemulticonverter.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Visitor;

/**
 * Helper class for converting {@link FormattedText} to formats that cannot
 * include CSS formatting. Existing CSS formatting will be replaced by similar
 * formatting instructions. After replacement, an abstract method is called to
 * inform the visitor so it can fix its suffix stack and/or log the event.
 */
public abstract class AbstractNoCSSVisitor<T extends Throwable> implements Visitor<T> {

	private static final Map<String, FormattingInstructionKind> CSS_MAPPINGS = new HashMap<>();

	static {
		CSS_MAPPINGS.put("font-weight:bold", FormattingInstructionKind.BOLD);
		CSS_MAPPINGS.put("font-style:italic", FormattingInstructionKind.ITALIC);
		CSS_MAPPINGS.put("text-decoration:underline", FormattingInstructionKind.UNDERLINE);
		CSS_MAPPINGS.put("color:blue", FormattingInstructionKind.LINK);
		CSS_MAPPINGS.put("vertical-align:sub", FormattingInstructionKind.SUBSCRIPT);
		CSS_MAPPINGS.put(" vertical-align:super", FormattingInstructionKind.SUPERSCRIPT);
		CSS_MAPPINGS.put("text-decoration:line-through", FormattingInstructionKind.STRIKE_THROUGH);
		CSS_MAPPINGS.put("font-variant:small-caps", FormattingInstructionKind.DIVINE_NAME);
		CSS_MAPPINGS.put("color:red", FormattingInstructionKind.WORDS_OF_JESUS);
	}

	/**
	 * Determine formatting instructions (filling the argument) and return the
	 * remaining CSS.
	 */
	public static String determineFormattingInstructions(String css, List<FormattingInstructionKind> result) {
		StringBuilder remainingCSS = new StringBuilder();
		for (String declaration : css.split(";")) {
			String cleanedCSS = declaration.toLowerCase().replace(" ", "");
			FormattingInstructionKind kind = CSS_MAPPINGS.get(cleanedCSS);
			if (kind != null) {
				result.add(kind);
			} else if (!cleanedCSS.isEmpty()) {
				remainingCSS.append(declaration + "; ");
			}
		}
		return remainingCSS.toString().trim();
	}

	/**
	 * Can be called to fixup a suffix stack after replacing a CSS formatting by
	 * multiple formatting instructions (which may have pushed multiple suffixes
	 * on the stack).
	 */
	protected static void fixupSuffixStack(int replacements, List<String> suffixStack) {
		if (replacements != 1) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < replacements; i++) {
				sb.append(suffixStack.remove(suffixStack.size() - 1));
			}
			suffixStack.add(sb.toString());
		}
	}

	@Override
	public Visitor<T> visitCSSFormatting(String css) throws T {
		List<FormattingInstructionKind> formattings = new ArrayList<>();
		String remainingCSS = determineFormattingInstructions(css, formattings);
		Visitor<T> v = this;
		for (FormattingInstructionKind kind : formattings) {
			v = v.visitFormattingInstruction(kind);
		}
		return visitChangedCSSFormatting(remainingCSS, v, formattings.size());
	}

	protected abstract Visitor<T> visitChangedCSSFormatting(String remainingCSS, Visitor<T> resultingVisitor, int replacements);
}
