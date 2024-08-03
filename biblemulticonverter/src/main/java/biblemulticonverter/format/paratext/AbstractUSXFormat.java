package biblemulticonverter.format.paratext;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;

public abstract class AbstractUSXFormat<ParaStyle extends Enum<ParaStyle>, CharStyle extends Enum<CharStyle>> extends AbstractParatextFormat {

	protected Map<ParatextBook.ParagraphKind, ParaStyle> PARA_KIND_MAP = new EnumMap<>(ParatextBook.ParagraphKind.class);
	protected Map<ParaStyle, ParatextBook.ParagraphKind> PARA_STYLE_MAP;
	protected Set<ParaStyle> PARA_STYLE_UNSUPPORTED;
	private final StyleWrapper<ParaStyle> paraStyleWrapper;
	protected String verseSeparatorText = System.getProperty("biblemulticonverter.paratext.usx.verseseparatortext");

	protected Map<ParatextCharacterContent.AutoClosingFormattingKind, CharStyle> CHAR_KIND_MAP = new EnumMap<>(ParatextCharacterContent.AutoClosingFormattingKind.class);
	protected Map<CharStyle, ParatextCharacterContent.AutoClosingFormattingKind> CHAR_STYLE_MAP;
	protected Set<CharStyle> CHAR_STYLE_UNSUPPORTED;
	private final StyleWrapper<CharStyle> charStyleWrapper;

	public AbstractUSXFormat(String formatName, StyleWrapper<ParaStyle> paraStyleWrapper, StyleWrapper<CharStyle> charStyleWrapper) {
		super(formatName);
		this.paraStyleWrapper = paraStyleWrapper;
		PARA_STYLE_MAP = new EnumMap<>(paraStyleWrapper.getStyleClass());
		PARA_STYLE_UNSUPPORTED = EnumSet.copyOf(paraStyleWrapper.getUnsupportedStyles());

		this.charStyleWrapper = charStyleWrapper;
		CHAR_STYLE_MAP = new EnumMap<>(charStyleWrapper.getStyleClass());
		CHAR_STYLE_UNSUPPORTED = charStyleWrapper.getUnsupportedStyles().isEmpty() ? EnumSet.noneOf(charStyleWrapper.getStyleClass()) : EnumSet.copyOf(charStyleWrapper.getUnsupportedStyles());

		prepareParaMaps();
		prepareCharMaps();
	}

	private void prepareParaMaps() {
		// Checks if every ParaGraphKind is mapped to a ParaStyle
		Map<String, ParatextBook.ParagraphKind> paraTags = ParatextBook.ParagraphKind.allTags();
		for (ParaStyle style : paraStyleWrapper.values()) {
			if (!(PARA_STYLE_UNSUPPORTED.contains(style) || paraStyleWrapper.tag(style).equals("rem") || paraStyleWrapper.tag(style).equals("periph") || paraStyleWrapper.tag(style).equals("usfm") || BOOK_HEADER_ATTRIBUTE_TAGS.contains(paraStyleWrapper.tag(style)))) {
				if (!paraTags.containsKey(paraStyleWrapper.tag(style))) {
					throw new RuntimeException("Found unhandled ParaStyle " + style);
				} else {
					ParatextBook.ParagraphKind kind = Objects.requireNonNull(paraTags.get(paraStyleWrapper.tag(style)));
					PARA_STYLE_MAP.put(style, kind);
					PARA_KIND_MAP.put(kind, style);
				}
			}
		}
		setupCustomParaMappings();
		assertParaKindMapIsComplete();
	}

	private void prepareCharMaps() {
		Map<String, ParatextCharacterContent.AutoClosingFormattingKind> charTags = ParatextCharacterContent.AutoClosingFormattingKind.allTags();
		for (CharStyle style : charStyleWrapper.values()) {
			if (!CHAR_STYLE_UNSUPPORTED.contains(style)) {
				if (!charTags.containsKey(charStyleWrapper.tag(style))) {
					throw new RuntimeException("Found unhandled CharStyle " + style);
				} else {
					ParatextCharacterContent.AutoClosingFormattingKind kind = Objects.requireNonNull(charTags.get(charStyleWrapper.tag(style)));
					CHAR_STYLE_MAP.put(style, kind);
					CHAR_KIND_MAP.put(kind, style);
				}
			}
		}
		setupCustomCharMappings();
		assertAutoClosingFormattingKindMapIsComplete();
	}

	/**
	 * Some ParagraphKinds cannot directly map to a schema type, because they are not known in the schema. In most cases
	 * this means the schema does not support these tags and some form of downgrading/skipping/replacing needs to
	 * happen. An easy way to do this is to map the unsupported ParagraphKinds to other types that are known in the
	 * schema, those mappings should be made here.
	 */
	protected void setupCustomParaMappings() {

	}

	protected void setupCustomCharMappings() {

	}

	private void assertParaKindMapIsComplete() {
		// These are part of the internal model for ParagraphKind's but are in USX not seen as paragraphs.
		Set<ParatextBook.ParagraphKind> skipInMapping = EnumSet.of(ParatextBook.ParagraphKind.TABLE_ROW);

		for (ParatextBook.ParagraphKind kind : ParatextBook.ParagraphKind.values()) {
			if (!PARA_KIND_MAP.containsKey(kind) && !skipInMapping.contains(kind) && isParagraphKindSupported(kind)) {
				throw new RuntimeException("Not all ParagraphKinds have been mapped to ParaStyles, missing mapping for: " + kind);
			}
		}
	}

	private void assertAutoClosingFormattingKindMapIsComplete() {
		for (ParatextCharacterContent.AutoClosingFormattingKind kind : ParatextCharacterContent.AutoClosingFormattingKind.values()) {
			if (!CHAR_KIND_MAP.containsKey(kind) && isAutoClosingFormattingKindSupported(kind)) {
				throw new RuntimeException("Not all AutoClosingFormattingKinds have been mapped to CharStyles, missing mapping for: " + kind);
			}
		}
	}

	abstract boolean isParagraphKindSupported(ParatextBook.ParagraphKind kind);

	abstract boolean isAutoClosingFormattingKindSupported(ParatextCharacterContent.AutoClosingFormattingKind kind);

	abstract static class StyleWrapper<Style extends Enum<Style>> {

		abstract Class<Style> getStyleClass();

		abstract Set<Style> getUnsupportedStyles();

		abstract Style[] values();

		abstract String tag(Style style);
	}
}
