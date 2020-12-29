package biblemulticonverter.logos.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.EnumMap;

import biblemulticonverter.data.BookID;
import biblemulticonverter.tools.AbstractVersificationDetector;
import biblemulticonverter.tools.AbstractVersificationDetector.VersificationScheme;

public class LogosVersificationDetector extends AbstractVersificationDetector {

	public VersificationScheme loadScheme(String name) throws IOException {
		for (VersificationScheme scheme : loadSchemes()) {
			if (scheme.getName().equals(name))
				return scheme;
		}
		return null;
	}

	@Override
	protected VersificationScheme[] loadSchemes() throws IOException {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(LogosVersificationDetector.class.getResourceAsStream("/logos-versemap.dat"), StandardCharsets.ISO_8859_1))) {
			// parse header
			String line = br.readLine();
			String[] fields = line.split(" ");
			VersificationScheme[] result = new VersificationScheme[fields.length];
			for (int i = 0; i < result.length; i++) {
				result[i] = new VersificationScheme(fields[i], new EnumMap<BookID, BitSet[]>(BookID.class));
			}
			// parse content
			BookID book = null;
			while ((line = br.readLine()) != null) {
				fields = line.split(" ");
				int idx = 0;
				if (!fields[0].matches("[0-9,-]+")) {
					book = BookID.fromOsisId(fields[0]);
					idx++;
				}
				BitSet affected = readBits(fields[idx]);
				idx++;
				BitSet[] chapters = new BitSet[fields.length - idx];
				for (int i = 0; i < chapters.length; i++) {
					chapters[i] = readBits(fields[i + idx]);
				}
				for (int i = affected.nextSetBit(0); i >= 0; i = affected.nextSetBit(i + 1)) {
					result[i].getCoveredBooks().put(book, chapters);
				}
			}

			// refresh metadata
			for (int i = 0; i < result.length; i++) {
				result[i] = new VersificationScheme(result[i].getName(), result[i].getCoveredBooks());
			}
			return result;
		}
	}

	public static BitSet readBits(String word) {
		BitSet result = new BitSet();
		if (!word.equals("-")) {
			for (String range : word.split(",")) {
				String[] bounds = range.split("-");
				if (bounds.length == 1)
					result.set(Integer.parseInt(bounds[0]));
				else
					result.set(Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]) + 1);
			}
		}
		return result;
	}

	@Override
	protected boolean useVerseRanges() {
		return true;
	}
}
