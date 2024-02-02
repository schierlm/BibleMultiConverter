package biblemulticonverter.logos.format;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import biblemulticonverter.data.Utils;

/**
 * Test that all RMAC patterns are valid and can be handled by LogosHTML and
 * RoundtripHTML.
 */
public class RMACConversionTest {

	private static final String[] PATTERN_PREFIXES = {
			"ADV", "CONJ", "COND", "PRT", "PREP", "INJ",
			"ARAM", "HEB", "N-PRI", "A-NUI", "N-LI", "N-OI",
			"V-[PIFARLX][AMPEDONQX][ISOMNP]",
			"V-2[PFARL][AMPEDONQX][ISOMNP]",
			"V-[PIFARLX][AMPEDONQX][ISOMNP]-[NGDAV][SPD][MFN]",
			"V-2[PFARL][AMPEDONQX][ISOMNP]-[NGDAV][SPD][MFN]",
			"V-[PIFARLX][AMPEDONQX][ISOMNP]-[123][SP]",
			"V-2[PFARL][AMPEDONQX][ISOMNP]-[123][SP]",
			"A-[NVGDA][SP][MFN]-NUI",
			"S-[123][SP][NVGDA][SP][MFN]",
			"[NARCDTKIXQFSP]",
			"[NARCDTKIXQFSP]-[123][NVGDA][SP][MFN]",
			"[NARCDTKIXQFSP]-[NVGDA][SP][MFN]",
			"[NARCDTKIXQFSP]-[123][NVGDA][SP]",
			"[NARCDTKIXQFSP]-[NVGDA][SP]",
			"[NA]-[NVGDA][SP][MFN]-[PLT]",
			"[NA]-[NVGDA][SP][MFN]-[PL]G",
			"[NA]-[NVGDA][SP][MFN]-LI",
	};

	protected static List<String> computePatterns() {
		List<String> result = new ArrayList<>();
		for (String prefix : PATTERN_PREFIXES) {
			result.add(prefix);
			result.add(prefix + "-ATT");
			result.add(prefix + "-ARAM");
			result.add(prefix + "-HEB");
			if (!prefix.startsWith("V")) {
				result.add(prefix + "-ABB");
				result.add(prefix + "-S");
				result.add(prefix + "-C");
				result.add(prefix + "-I");
				result.add(prefix + "-N");
				result.add(prefix + "-K");
			}
		}
		return result;
	}

	protected static List<String> expandPattern(String pattern) {
		int pos = pattern.indexOf('[');
		List<String> result = new ArrayList<>();
		if (pos == -1) {
			result.add(pattern);
		} else {
			int endPos = pattern.indexOf(']', pos);
			for(String suffix : expandPattern(pattern.substring(endPos + 1))) {
				for(int i= pos + 1; i < endPos; i++) {
					result.add(pattern.substring(0, pos) + pattern.charAt(i) + suffix);
				}
			}
		}
		return result;
	}

	/**
	 * Test that all RMAC created from the patterns are accepted by the regular
	 * expression.
	 */
	@Test
	public void testPatternValidity() {
		for (String pattern : computePatterns()) {
			Assert.assertTrue("Mismatch: " + pattern, pattern.matches("([A-Z1-3-]++|\\[[A-Z1-3]++\\])*+"));
			for (String expansion : expandPattern(pattern)) {
				Assert.assertTrue("Not accepted: " + expansion, expansion.matches(Utils.RMAC_REGEX));
			}
		}
	}

	/**
	 * Test that all RMAC created from the patterns can be converted to
	 * LogosHTML
	 */
	@Test
	public void testLogosConversion() {
		for (String pattern : computePatterns()) {
			for (String expansion : expandPattern(pattern)) {
				LogosHTML.convertMorphology(expansion);
			}
		}
	}
}
