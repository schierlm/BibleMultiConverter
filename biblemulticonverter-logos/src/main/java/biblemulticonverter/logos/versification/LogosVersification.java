package biblemulticonverter.logos.versification;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.logos.format.LogosHTML;
import biblemulticonverter.logos.format.LogosRenumberedDiffable;
import biblemulticonverter.logos.tools.LogosVersificationDetector;
import biblemulticonverter.versification.VersificationFormat;

public class LogosVersification implements VersificationFormat {

	public static final String[] HELP_TEXT = {
			"Versification format for versifications supported by Logos.",
			"",
			"Note that these versifications do not take verse order into account.",
			"They also do not include versification mappings."
	};

	@Override
	public void doImport(VersificationSet versifications, String... importArgs) throws Exception {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(LogosVersificationDetector.class.getResourceAsStream("/logos-versemap.dat"), StandardCharsets.ISO_8859_1))) {
			// parse header
			String line = br.readLine();
			String[] versificationNames = line.split(" ");
			VersificationRefList[] versificationRefLists = new VersificationRefList[versificationNames.length];
			for (int i = 0; i < versificationRefLists.length; i++) {
				versificationRefLists[i] = new VersificationRefList();
			}

			// parse content
			BookID book = null;
			String[] fields;
			while ((line = br.readLine()) != null) {
				fields = line.split(" ");
				int idx = 0;
				if (!fields[0].matches("[0-9,-]+")) {
					book = BookID.fromOsisId(fields[0]);
					idx++;
				}
				BitSet affected = LogosVersificationDetector.readBits(fields[idx]);
				idx++;
				List<Reference> affectedRefs = new ArrayList<>();
				for (int ch = 1; idx < fields.length; ch++, idx++) {
					BitSet chapterBits = LogosVersificationDetector.readBits(fields[idx]);
					for (int i = chapterBits.nextSetBit(0); i >= 0; i = chapterBits.nextSetBit(i + 1)) {
						String v = "" + i;
						if (i >= 1000) {
							v = LogosRenumberedDiffable.transformLogosVerse(LogosHTML.NAMED_VERSES[i - 1000]);
						}
						affectedRefs.add(new Reference(book, ch, v));
					}
				}
				Reference[] affectedRefsArray = affectedRefs.toArray(new Reference[affectedRefs.size()]);
				for (int i = affected.nextSetBit(0); i >= 0; i = affected.nextSetBit(i + 1)) {
					versificationRefLists[i].add(affectedRefsArray);
				}
			}

			// generate versifications
			for (int i = 0; i < versificationNames.length; i++) {
				versifications.add(Arrays.asList(Versification.fromReferenceList("Logos_" + versificationNames[i], null, null, versificationRefLists[i].expand())), null);
			}
		}
	}

	@Override
	public boolean isExportSupported() {
		return false;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
	}

	private static class VersificationRefList extends ArrayList<Reference[]> {
		private List<Reference> expand() {
			List<Reference> result = new ArrayList<>();
			for (Reference[] subList : this) {
				result.addAll(Arrays.asList(subList));
			}
			return result;
		}
	}
}
