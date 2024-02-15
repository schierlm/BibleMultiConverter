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

	private static final String[] LOGOS_PATTERNS = {
			"J[ADGNV?][PS?][FMN?][COS?]", // Adjective "J[ADGNV?][DPS?][FMN?][COPS?]"
			"B[CIKNS?]", // Adverb "B[CEIKNPSX?]"
			"D[ADGNV?][PS?][FMN?]", // Article "D[ADGNV?][DPS?][FMN?]"
			"I", // Interjection
			"C", // Conjunction C[[ACADALAMANAPARATAZLALCLDLILKLMLNLTLXSCSE??]]
			"T[CIN?]", // Particle "T[CEIKNPSX?]"
			"P", // Preposition
			"R[CDFIKPRSX?][123?][ADGNV?][PS?][FMN?]", // Pronoun "R[CDFIKNPRSX?][123?][ADGNV?][DPS?][FMN?][AP?]"
			"N[ADGNV?][PS?][FMN?][COS?]", // Noun "N[ADGNV?][DPS?][FMN?][COPS?]"
			"X[FLNOP]", // Indeclinable
			"V[AFILPR?][AMPU?][IMNOPS][123][PS]", "V[AFILPRT?][AMPU?][IMNOPS]?[DPS?][ADGNV?][FMN?]", // Verb "V[AFILPRT?][AMPU?][IMNOPS][123?][DPS?][ADGNV?][FMN?]"
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
		} else if (pattern.startsWith("[[", pos)) {
			int endPos = pattern.indexOf("]]", pos);
			for (String suffix : expandPattern(pattern.substring(endPos + 2))) {
				for (int i = pos + 2; i < endPos; i += 2) {
					result.add(pattern.substring(0, pos) + pattern.substring(i, i + 2) + suffix);
				}
			}
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
		Set<String> logosExpansions = new HashSet<>(), unusedExpansions = new HashSet<>();
		for (String logosPattern : LOGOS_PATTERNS) {
			for (String expanded : expandPattern(logosPattern)) {
				if (!expanded.contains("?")) {
					unusedExpansions.add(expanded);
				} else {
					expanded = expanded.replaceAll("\\?+$", "");
				}
				logosExpansions.add(expanded);
			}
		}
		for (String pattern : computePatterns()) {
			for (String expansion : expandPattern(pattern)) {
				String logosExpansionList = LogosHTML.convertMorphology(expansion);
				for(String logosExpansion : logosExpansionList.split(":")) {
					Assert.assertTrue("Generated invalid Logos expansion: " + logosExpansion + " (" + (logosExpansion == logosExpansionList ? "" : "Part of " + logosExpansionList + ", ") + "from: " + expansion + ")", logosExpansions.contains(logosExpansion));
					unusedExpansions.remove(logosExpansion);
				}
			}
		}
		Assert.assertTrue("Never generated logos expansions: "+unusedExpansions, unusedExpansions.isEmpty());
	}


	private void testLogosWord(String rmac, String logos) {
		Assert.assertTrue("Invalid RMAC: "+rmac, rmac.matches(Utils.RMAC_REGEX));
		Assert.assertEquals(logos, LogosHTML.convertMorphology(rmac));
	}

	@Test
	public void testLogosRegressions() {
		testLogosWord("N-GSM-P", "NGSM:XP");
		testLogosWord("V-XXM-2P", "V??M2P");
		testLogosWord("Q", "R");
		testLogosWord("N-NPN-C", "NNPNC");
	}

}
