package biblemulticonverter.logos.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import biblemulticonverter.data.Utils;

/**
 * Test that all WIVU patterns are valid and can be handled by RoundtripHTML.
 */
public class WIVUConversionTest {

	private static final boolean RUN_ALL_PATTERNS = Boolean.getBoolean("biblemulticonverter.unittest.runallpatterns");

	private static final String[] PATTERN_PARTS = {
			"B=C",
			"B=D",
			"B=T",
			"B=R",
			"B=R[d]",
			"B=A[acgo]",
			"B=A[acgo][bcfmx][dpsx][acd]",
			"B=N[cgtx]",
			"B=N[cgtx][bcfmx][dpsx][acd]",
			"B=Np",
			"B=Np[mflt]",
			"B=P[dfipr]",
			"B=P[dfipr][123x][bcfm][dps]",
			"B=S[dhnp]",
			"B=S[dhnp][123x][bcfm][dps]",
			"B=T[acdeijmnor]",
			"H=V[DHKLMNOPQcfhijklmopqrtuvwyz][pqiwhjvrsauc]",
			"A=V[GHLMOPQabcefhilmopqrstuvwz][pqiwhjvrsauc]",
			"H=V[DHKLMNOPQcfhijklmopqrtuvwyz][pqiwhjvrsauc][123][bcfm][dps]",
			"A=V[GHLMOPQabcefhilmopqrstuvwz][pqiwhjvrsauc][123][bcfm][dps]",
			"H=V[DHKLMNOPQcfhijklmopqrtuvwyz][pqiwhjvrsauc][ac]",
			"A=V[GHLMOPQabcefhilmopqrstuvwz][pqiwhjvrsauc][ac]",
			"H=V[DHKLMNOPQcfhijklmopqrtuvwyz][pqiwhjvrsauc][bfm]",
			"A=V[GHLMOPQabcefhilmopqrstuvwz][pqiwhjvrsauc][bfm]",
			"H=V[DHKLMNOPQcfhijklmopqrtuvwyz][pqiwhjvrsauc][bcfm][dps][acd]",
			"A=V[GHLMOPQabcefhilmopqrstuvwz][pqiwhjvrsauc][bcfm][dps][acd]",
	};

	protected static List<String> computePatterns() {
		List<String> result = new ArrayList<>();
		List<String> partSample = Arrays.stream(PATTERN_PARTS).filter(p -> p.length() < 10).collect(Collectors.toList());

		for (String part : PATTERN_PARTS) {
			// length 1
			if (isValid("H", part)) {
				result.add("H" + part.substring(2));
			}
			if (isValid("A", part)) {
				result.add("A" + part.substring(2));
			}
			if (RUN_ALL_PATTERNS) {
				// length 2
				for (String part2 : partSample) {
					if (isValid("H", part) && isValid("A", part)) {
						result.add("H" + part.substring(2) + "/" + part2.substring(2));
					}
					if (isValid("A", part) && isValid("H", part)) {
						result.add("A" + part2.substring(2) + "/" + part.substring(2));
					}
				}
				// a sample of longer ones
				if (isValid("H", part)) {
					result.add("HC//" + part.substring(2) + "/D");
					result.add("H" + part.substring(2) + "/Nt/D/C");
				}
				if (isValid("A", part)) {
					result.add("ARd/R/" + part.substring(2));
				}
			} else if (isValid("H", part)) {
				result.add("HC//" + part.substring(2));
			}
		}
		return result;
	}

	private static boolean isValid(String lang, String part) {
		return part.startsWith("B=") || part.startsWith(lang + "=");
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
			for (String suffix : expandPattern(pattern.substring(endPos + 1))) {
				for (int i = pos + 1; i < endPos; i++) {
					result.add(pattern.substring(0, pos) + pattern.charAt(i) + suffix);
				}
			}
		}
		return result;
	}

	/**
	 * Test that all WIVU morphology tags created from the patterns are accepted
	 * by the regular expression.
	 */
	@Test
	public void testPatternValidity() {
		for (String pattern : computePatterns()) {
			Assert.assertTrue("Mismatch: " + pattern, pattern.matches("([A-Za-z1-3/]++|\\[[A-Za-z1-3]++\\])*+"));
			for (String expansion : expandPattern(pattern)) {
				Assert.assertTrue("Not accepted: " + expansion, expansion.matches(Utils.WIVU_REGEX));
				Assert.assertFalse("WIVU may not be RMAC: " + expansion, expansion.matches(Utils.RMAC_REGEX));
			}
		}
	}

	/**
	 * Test that all WIVU morpology tags that are accepted by the regular
	 * expression are covered by the patterns
	 */
	@Test
	public void testPatternCoverage() {
		String regex = Utils.WIVU_PART_REGEX;
		List<String> patternParts = new ArrayList<>(Arrays.asList(PATTERN_PARTS));
		for (int i = 0; i < patternParts.size() - 1; i++) {
			String p1 = patternParts.get(i);
			String p2 = patternParts.get(i + 1);
			if (p1.startsWith("H=") && p2.startsWith("A=")) {
				int pos0 = p1.indexOf('[');
				int pos1 = p1.indexOf(']');
				int pos2 = p2.indexOf(']');
				if (p1.substring(pos1).equals(p2.substring(pos2)) && p1.substring(1, pos0 + 1).equals(p2.substring(1, pos0 + 1))) {
					String range1 = p1.substring(pos0 + 1, pos1);
					String range2 = p2.substring(pos0 + 1, pos2);
					StringBuilder rangeB = new StringBuilder(range1.length() + range2.length());
					int i1 = 0, i2 = 0;
					while (i1 < range1.length() && i2 < range2.length()) {
						if (range1.charAt(i1) == range2.charAt(i2)) {
							rangeB.append(range1.charAt(i1)); i1++; i2++;
						} else if (range1.charAt(i1) < range2.charAt(i2)) {
							rangeB.append(range1.charAt(i1)); i1++;
						} else if (range1.charAt(i1) > range2.charAt(i2)) {
							rangeB.append(range2.charAt(i2)); i2++;
						}
					}
					rangeB.append(range1.substring(i1) + range2.substring(i2)); // one is empty
					String pb = "B=" + p1.substring(2, pos0 + 1) + rangeB.toString() + p1.substring(pos1);
					patternParts.set(i, pb);
					patternParts.set(i + 1, pb);
				}
			}
		}
		for (int i = 0; i < patternParts.size(); i++) {
			Assert.assertTrue("Starts with B=: "+patternParts.get(i), patternParts.get(i).startsWith("B="));
			patternParts.set(i, patternParts.get(i).substring(2));
		}
		Set<String> expandedPatterns = RMACPatternCompletenessTest.expandToPatterns(regex);
		Set<String> expectedPatterns = new HashSet<>(patternParts);
		Assert.assertEquals(expandedPatterns, expectedPatterns);
	}
}

