package biblemulticonverter.logos.format;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import biblemulticonverter.Main;
import biblemulticonverter.ModuleRegistry.Module;
import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification;
import biblemulticonverter.format.ExportFormat;

public class AugmentLogosLinks implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Add values of Logos links to Extra Attributes or Grammar Attributes of a Bible",
			"",
			"Usage: AugmentLogosLinks [-xattr] [<prefix>=<category>:<key>:<value>...] -- <ExportFormat> [<ExportArgs>...]",
			"",
			"Use -xattr to fill Extra Attributes instead of Grammar Attributes (for compatibility with older versions of BibleMultiConverter).",
			"Useful, for example, to fill '[osisgrammar:lemma:]lemma' or 'osisgrammar:morph:louwnida' before exporting to OSIS."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		boolean xattr = false;
		if (exportArgs[0].equals("-xattr")) {
			xattr = true;
		}
		int formatArg = xattr ? 1 : 0;
		LogosLinksGenerator linksGenerator = new LogosLinksGenerator();
		List<String[]> mappingRules = new ArrayList<>();
		while (!exportArgs[formatArg].equals("--")) {
			if (xattr) {
				int pos = exportArgs[formatArg].indexOf("=");
				String[] parts = exportArgs[formatArg].substring(pos + 1).split(":");
				if (parts.length != 3)
					throw new IOException("Invalid rule: " + exportArgs[formatArg]);
				mappingRules.add(new String[] { exportArgs[formatArg].substring(0, pos), parts[0], parts[1], parts[2] });
			} else {
				mappingRules.add(exportArgs[formatArg].split("=", 2));
			}
			formatArg++;
		}
		formatArg++;

		for (Book book : bible.getBooks()) {
			int cnum = 0;
			for (Chapter chapter : book.getChapters()) {
				cnum++;
				List<Verse> verses = chapter.getVerses();
				for (int i = 0; i < verses.size(); i++) {
					Verse v1 = verses.get(i);
					Verse v2 = new Verse(v1.getNumber());
					v1.accept(new AugmentLogosLinksVisitor(v2.getAppendVisitor(), new Versification.Reference(book.getId(), cnum, v1.getNumber()), mappingRules, xattr, linksGenerator));
					v2.finished();
					verses.set(i, v2);
				}
			}
		}
		Module<ExportFormat> exportModule = Main.exportFormats.get(exportArgs[formatArg]);
		ExportFormat exportFormat = exportModule.getImplementationClass().newInstance();
		exportFormat.doExport(bible, Arrays.copyOfRange(exportArgs, formatArg + 1, exportArgs.length));
	}

	private static class AugmentLogosLinksVisitor extends FormattedText.VisitorAdapter<RuntimeException> {

		private final Versification.Reference reference;
		private final List<String[]> mappingRules;
		private final boolean xattr;
		private final LogosLinksGenerator linksGenerator;

		private AugmentLogosLinksVisitor(Visitor<RuntimeException> next, Versification.Reference reference, List<String[]> mappingRules, boolean xattr, LogosLinksGenerator linksGenerator) throws RuntimeException {
			super(next);
			this.reference = reference;
			this.mappingRules = mappingRules;
			this.xattr = xattr;
			this.linksGenerator = linksGenerator;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return new AugmentLogosLinksVisitor(childVisitor, reference, mappingRules, xattr, linksGenerator);
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws RuntimeException {
			List<String> links = linksGenerator.generateLinks(reference.getBook().isNT(), reference, strongsPrefixes, strongs, strongsSuffixes, rmac, sourceVerses, sourceIndices, attributeKeys, attributeValues);
			if (!xattr) {
				List<String> attrKeys = new ArrayList<>(Arrays.asList(attributeKeys));
				List<String> attrVals = new ArrayList<>(Arrays.asList(attributeValues));
				for (String[] rule : mappingRules) {
					for (String link : links) {
						if (link.startsWith(rule[0])) {
							attrKeys.add(rule[1]);
							attrVals.add(link.substring(rule[0].length()));
						}
					}
				}
				attributeKeys = attrKeys.toArray(new String[attrKeys.size()]);
				attributeValues = attrVals.toArray(new String[attrVals.size()]);
			}
			Visitor<RuntimeException> vv = getVisitor().visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceVerses, sourceIndices, attributeKeys, attributeValues);
			if (xattr) {
				for (String[] rule : mappingRules) {
					for (String link : links) {
						if (link.startsWith(rule[0])) {
							vv.visitExtraAttribute(ExtraAttributePriority.SKIP, rule[1], rule[2], rule[3]).visitText(link.substring(rule[0].length()));
						}
					}
				}
			}
			return wrapChildVisitor(vv);
		}
	}
}
