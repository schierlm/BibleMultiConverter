package biblemulticonverter.logos.format;

import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import biblemulticonverter.data.Utils;

/**
 * Test that all RMAC that are accepted by the regular expression are covered by
 * the patterns
 */
public class RMACPatternCompletenessTest {

	@Test
	public void testPatternCoverage() {
		String regex = Utils.RMAC_REGEX;
		Set<String> expandedPatterns = expandToPatterns(regex);
		Set<String> expectedPatterns = new HashSet<>(RMACConversionTest.computePatterns());
		Assert.assertEquals(expandedPatterns, expectedPatterns);
	}

	protected static Set<String> expandToPatterns(String regex) {
		ParsePosition p = new ParsePosition(0);
		Set<String> result = expandList(regex, p);
		if (p.getIndex() != regex.length())
			throw new RuntimeException("Unsupported character in regex: " + regex.substring(p.getIndex()));
		return result;
	}

	private static Set<String> expandList(String regex, ParsePosition p) {
		Set<String> result = new HashSet<>();
		while (p.getIndex() != regex.length() && regex.charAt(p.getIndex()) != ')') {
			result.addAll(expandAlternative(regex, p));
		}
		return result;
	}

	private static Set<String> expandAlternative(String regex, ParsePosition pos) {
		List<Set<String>> parts = new ArrayList<>();
		String lookAt = regex.substring(pos.getIndex());
		int p = pos.getIndex();
		while (p < regex.length()) {
			if (regex.startsWith("[", p)) {
				int endPos = regex.indexOf("]", p);
				Set<String> s = new HashSet<>();
				s.add(regex.substring(p, endPos + 1));
				p = endPos + 1;
				if (regex.startsWith("?", p)) {
					s.add("");
					p++;
				}
				parts.add(s);
			} else if (regex.startsWith("(", p)) {
				pos.setIndex(p+1);
				Set<String> s = expandList(regex, pos);
				p = pos.getIndex();
				if (!regex.startsWith(")", p))
					throw new RuntimeException("Missing parenthesis: "+regex.substring(p));
				p++;
				if (regex.startsWith("?", p)) {
					s.add("");
					p++;
				}
				parts.add(s);
			} else if (regex.startsWith(")", p)) {
				break; // keep parenthesis!
			} else if (regex.startsWith("|", p)) {
				p++; // consume pipe!
				break;
			} else {
				int ps = p;
				while (regex.charAt(p) == '-' || regex.charAt(p) == '/' || (regex.charAt(p) >= 'A' && regex.charAt(p) <= 'Z') || (regex.charAt(p) >= 'a' && regex.charAt(p) <= 'z') || (regex.charAt(p) >= '0' && regex.charAt(p) <= '9')) {
					p++;
				}
				if (p == ps) {
					throw new RuntimeException("Unsupported character in regex: " + regex.substring(p));
				}
				Set<String> s = new HashSet<>();
				s.add(regex.substring(ps, p));
				parts.add(s);
			}
		}
		pos.setIndex(p);
		while (parts.size() > 1) {
			Set<String> s1 = parts.remove(0);
			Set<String> s2 = parts.get(0);
			Set<String> j = new HashSet<>();
			for (String e1 : s1) {
				for (String e2 : s2) {
					j.add(e1 + e2);
				}
			}
			parts.set(0, j);
		}
		return parts.isEmpty() ? new HashSet<>(Arrays.asList("")) : parts.get(0);
	}
}
