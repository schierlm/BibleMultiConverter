package biblemulticonverter.sword.tools;

import java.io.IOException;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.Iterator;

import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.SystemCatholic;
import org.crosswire.jsword.versification.system.SystemCatholic2;
import org.crosswire.jsword.versification.system.SystemGerman;
import org.crosswire.jsword.versification.system.SystemKJV;
import org.crosswire.jsword.versification.system.SystemKJVA;
import org.crosswire.jsword.versification.system.SystemLXX;
import org.crosswire.jsword.versification.system.SystemLeningrad;
import org.crosswire.jsword.versification.system.SystemLuther;
import org.crosswire.jsword.versification.system.SystemMT;
import org.crosswire.jsword.versification.system.SystemNRSV;
import org.crosswire.jsword.versification.system.SystemNRSVA;
import org.crosswire.jsword.versification.system.SystemOrthodox;
import org.crosswire.jsword.versification.system.SystemSynodal;
import org.crosswire.jsword.versification.system.SystemSynodalProt;
import org.crosswire.jsword.versification.system.SystemVulg;
import org.crosswire.jsword.versification.system.Versifications;

import biblemulticonverter.data.BookID;
import biblemulticonverter.sword.BookMapping;
import biblemulticonverter.tools.AbstractVersificationDetector;

public class SWORDVersificationDetector extends AbstractVersificationDetector {

	private static final String[] ALL_V11N_NAMES = {
			SystemKJV.V11N_NAME,
			SystemCatholic.V11N_NAME,
			SystemCatholic2.V11N_NAME,
			SystemGerman.V11N_NAME,
			SystemKJVA.V11N_NAME,
			SystemLeningrad.V11N_NAME, // same as MT
			SystemLuther.V11N_NAME,
			SystemLXX.V11N_NAME,
			SystemMT.V11N_NAME,
			SystemNRSV.V11N_NAME,
			SystemNRSVA.V11N_NAME,
			SystemOrthodox.V11N_NAME,
			SystemSynodal.V11N_NAME,
			SystemSynodalProt.V11N_NAME,
			SystemVulg.V11N_NAME,
	};

	@Override
	protected VersificationScheme[] loadSchemes() throws IOException {
			// parse header
			VersificationScheme[] result = new VersificationScheme[ALL_V11N_NAMES.length];
			for (int i = 0; i < result.length; i++) {
				EnumMap<BookID, BitSet[]> coveredBooks = new EnumMap<BookID, BitSet[]>(BookID.class);
				Versification v11n = Versifications.instance().getVersification(ALL_V11N_NAMES[i]);
				for(Iterator<BibleBook> it =v11n.getBookIterator(); it.hasNext();) {
					BibleBook bb = (BibleBook)it.next();
					BitSet[] chapters = new BitSet[v11n.getLastChapter(bb)];
					coveredBooks.put(BookMapping.MAPPING.get(bb), chapters);
					for(int j=1; j <= v11n.getLastChapter(bb); j++) {
						chapters[j-1] = new BitSet();
						chapters[j-1].set(1, v11n.getLastVerse(bb, j)+1);
					}
				}
				result[i] = new VersificationScheme(ALL_V11N_NAMES[i], coveredBooks);
			}
			return result;
	}
	@Override
	protected boolean useVerseRanges() {
		return false;
	}
}
