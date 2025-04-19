package biblemulticonverter.logos.format;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.format.LaridianPocketBible;

public class LaridianPocketBibleExtendedInterlinear extends LaridianPocketBible {

	public static final String[] HELP_TEXT = new String[LaridianPocketBible.HELP_TEXT.length + 2];

	static {
		HELP_TEXT[0] = "Export to Laridian Pocket Bible with Interlinear fields from Logos links";
		for (int i = 1; i < LaridianPocketBible.HELP_TEXT.length; i++) {
			HELP_TEXT[i] = LaridianPocketBible.HELP_TEXT[i];
		}
		HELP_TEXT[HELP_TEXT.length - 2] = "Additionally, any Logos link prefix can be used as interlinear type, by prefixing it with 'LogosLink:'.";
		HELP_TEXT[HELP_TEXT.length - 1] = "You will need to edit the generate meta tag in the head, though.";
	};

	@Override
	protected InterlinearType parseInterlinearType(int index, String type) {
		if (type.startsWith("LogosLink:")) {
			return new LogosLinkInterlinearType(index, type.substring(10));
		}
		return super.parseInterlinearType(index, type);
	}

	public static class LogosLinkInterlinearType extends InterlinearType<List<String>> {

		private static final InterlinearTypePrecalculator<List<String>> LOGOS_LINK_PRECALCULATOR = new InterlinearTypePrecalculator<List<String>>() {
			private final LogosLinksGenerator generator;
			{
				try {
					generator = new LogosLinksGenerator();
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			}

			public java.util.List<String> precalculate(boolean nt, Reference verseReference, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
				return generator.generateLinks(nt, verseReference, strongsPrefixes, strongs, strongsSuffixes, rmac, sourceIndices, attributeKeys, attributeValues);
			}
		};

		private final String linkPrefix;

		protected LogosLinkInterlinearType(int index, String linkPrefix) {
			super(LOGOS_LINK_PRECALCULATOR, "extra" + (index + 1), "Name for LogosLink:" + linkPrefix + " (edit me), , , , , , sync:\\\\, yes");
			this.linkPrefix = linkPrefix;
		}

		@Override
		protected List<String> determineValues(List<String> links, boolean nt, Reference verseReference, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
			return links.stream().filter(l -> l.startsWith(linkPrefix)).map(l -> l.substring(linkPrefix.length())).collect(Collectors.toList());
		}
	}
}
