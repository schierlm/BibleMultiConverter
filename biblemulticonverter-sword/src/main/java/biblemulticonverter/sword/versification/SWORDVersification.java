package biblemulticonverter.sword.versification;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseKey;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.VersificationsMapper;
import org.crosswire.jsword.versification.system.SystemKJV;
import org.crosswire.jsword.versification.system.Versifications;

import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.sword.BookMapping;
import biblemulticonverter.sword.tools.SWORDVersificationDetector;
import biblemulticonverter.versification.VersificationFormat;

public class SWORDVersification implements VersificationFormat {

	public static final String[] HELP_TEXT = {
			"Versification format for versifications included in the SWORD project."
	};

	List<String> missingMappings = Arrays.asList("Catholic", "Catholic2", "KJVA", "LXX", "Orthodox");

	@Override
	public void doImport(VersificationSet versifications, String... importArgs) throws Exception {
		org.crosswire.jsword.versification.Versification v11n_kjv = Versifications.instance().getVersification(SystemKJV.V11N_NAME);
		Map<BookID, BibleBook> reverseBookMapping = new EnumMap<>(BookID.class);
		for (Map.Entry<BibleBook, BookID> entry : BookMapping.MAPPING.entrySet()) {
			reverseBookMapping.put(entry.getValue(), entry.getKey());
		}
		for (String versificationName : SWORDVersificationDetector.ALL_V11N_NAMES) {
			System.out.println("--- " + versificationName + " ---");
			org.crosswire.jsword.versification.Versification v11n = Versifications.instance().getVersification(versificationName);
			LinkedHashMap<BookID, int[]> verseCounts = new LinkedHashMap<>();
			for (Iterator<BibleBook> it = v11n.getBookIterator(); it.hasNext();) {
				BibleBook bb = (BibleBook) it.next();
				int[] chapters = new int[v11n.getLastChapter(bb)];
				if (chapters.length == 0)
					continue;
				verseCounts.put(BookMapping.MAPPING.get(bb), chapters);
				for (int j = 1; j <= v11n.getLastChapter(bb); j++) {
					chapters[j - 1] = v11n.getLastVerse(bb, j);
				}
			}
			versifications.add(Arrays.asList(Versification.fromVerseCounts("SWORD_" + versificationName, null, null, verseCounts)), null);
			if (!versificationName.equals(SystemKJV.V11N_NAME) && !missingMappings.contains(versificationName)) {
				buildMapping(versifications, SystemKJV.V11N_NAME, v11n_kjv, versificationName, v11n, reverseBookMapping);
				buildMapping(versifications, versificationName, v11n, SystemKJV.V11N_NAME, v11n_kjv, reverseBookMapping);
			}
		}
	}

	private void buildMapping(VersificationSet versifications, String fromName, org.crosswire.jsword.versification.Versification fromV11n, String toName, org.crosswire.jsword.versification.Versification toV11n, Map<BookID, BibleBook> reverseBookMapping) {
		System.out.println("\t" + fromName + " -> " + toName);
		Versification from = versifications.findVersification("SWORD_" + fromName);
		Versification to = versifications.findVersification("SWORD_" + toName);
		Map<Reference, List<Reference>> map = new HashMap<>();
		for (int i = 0; i < from.getVerseCount(); i++) {
			Reference ref = from.getReference(i);
			Verse v = new Verse(fromV11n, reverseBookMapping.get(ref.getBook()), ref.getChapter(), Integer.parseInt(ref.getVerse()));
			VerseKey<?> vk = VersificationsMapper.instance().mapVerse(v, toV11n);
			List<Reference> refs = new ArrayList<>();
			for (String verse : vk.getOsisID().split(" ")) {
				if (verse.isEmpty())
					continue;
				String[] verseParts = verse.split("\\.");
				BookID book = BookID.fromOsisId(verseParts[0]);
				int chapter = Integer.parseInt(verseParts[1]);
				int verseNum = Integer.parseInt(verseParts[2]);
				if (verseNum == 0) verseNum = 1;
				refs.add(new Reference(book, chapter, "" + verseNum));
			}
			if (!refs.isEmpty())
				map.put(ref, refs);
		}
		versifications.add(null, Arrays.asList(VersificationMapping.build(from, to, map)));
	}

	@Override
	public boolean isExportSupported() {
		return false;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
	}
}
