package biblemulticonverter.logos.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;

/** Generator for Logos links. */
public class LogosLinksGenerator {

	private Map<String, List<ExtraLinkRule>> extraLinkRules;

	public LogosLinksGenerator() throws IOException {
		extraLinkRules = new HashMap<>();
		for (String extraLinkFile : System.getProperty("biblemulticonverter.logos.extralinkfiles", "").split(File.pathSeparator)) {
			if (extraLinkFile.isEmpty())
				continue;
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(extraLinkFile), StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					String[] fields = line.split("[\t,;]");
					if (fields.length < 3) {
						System.out.println("WARNING: ExtraLinkFile " + extraLinkFile + " incomplete line: " + line);
						continue;
					}
					if (!fields[0].matches("[A-Z][1-9][0-9]*")) {
						System.out.println("WARNING: ExtraLinkFile " + extraLinkFile + " skipping invalid Strong number: " + line);
						continue;
					}
					List<ExtraLinkCondition> conditions = null;
					if (fields[1].length() > 1) {
						conditions = new ArrayList<>();
						for (String cond : fields[1].split(" ")) {
							ExtraLinkCondition c = ExtraLinkCondition.parse(cond.split("\\+"));
							if (c == null) {
								System.out.println("WARNING: ExtraLinkFile " + extraLinkFile + " skipping invalid condition " + cond + " in: " + line);
							} else {
								conditions.add(c);
							}
						}
						if (conditions.isEmpty()) {
							System.out.println("WARNING: ExtraLinkFile " + extraLinkFile + " skipping line without valid conditions: " + line);
						}
					}
					boolean skipStrongs = false;
					List<String> links = new ArrayList<String>();
					for (int i = 2; i < fields.length; i++) {
						if (fields[i].contains(":") && !fields[i].contains(" ")) {
							links.add(fields[i]);
						} else if (fields[i].equals("-")) {
							skipStrongs = true;
						} else {
							System.out.println("WARNING: ExtraLinkFile " + extraLinkFile + " skipping invalid link " + fields[i] + " in: " + line);
						}
					}
					if (links.isEmpty()) {
						System.out.println("WARNING: ExtraLinkFile " + extraLinkFile + " skipping line with no links: " + line);
						continue;
					}
					extraLinkRules.computeIfAbsent(fields[0], x -> new ArrayList<>()).add(new ExtraLinkRule(conditions, links, skipStrongs));
				}
			}
		}
	}

	public List<String> generateLinks(boolean nt, Versification.Reference verseReference, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
		List<String> links = new ArrayList<String>();
		String[] expandedStrongs = strongs == null ? null : new String[strongs.length];
		if (strongs != null) {
			for (int i = 0; i < strongs.length; i++) {
				expandedStrongs[i] = Utils.formatStrongs(nt, i, strongsPrefixes, strongs);
			}
		}
		int max = Math.max(strongs == null ? 0 : strongs.length, rmac == null ? 0 : rmac.length);
		for (int i = 0; i < max; i++) {
			if (strongs != null && i < strongs.length) {
				boolean skipStrongs = false;
				for (ExtraLinkRule r : extraLinkRules.getOrDefault(expandedStrongs[i], Collections.emptyList())) {
					if (r.conditions != null) {
						boolean conditionFound = false;
						for (ExtraLinkCondition cond : r.conditions) {
							if (cond.matches(verseReference, expandedStrongs, rmac)) {
								conditionFound = true;
								break;
							}
						}
						if (!conditionFound)
							continue;
					}
					skipStrongs |= r.skipStrongs;
					links.addAll(r.links);
				}
				if (!skipStrongs) {
					boolean useNT = nt;
					if (expandedStrongs[i].charAt(0) == 'G')
						useNT = true;
					else if (expandedStrongs[i].charAt(0) == 'H')
						useNT = false;
					String type = useNT ? "GreekStrongs:" : "HebrewStrongs:";
					links.add(type + expandedStrongs[i]);
				}
			}
			if (rmac != null && i < rmac.length) {
				if (rmac[i].matches(Utils.RMAC_REGEX)) {
					for (String morph : LogosHTML.convertMorphology(rmac[i]).split(":")) {
						links.add("LogosMorphGr:" + morph);
					}
				} else if (rmac[i].matches(Utils.WIVU_REGEX)) {
					String prefix = rmac[i].startsWith("A") ? "WIVUMorphAram:" : "WIVUMorphHeb:";
					for (String morph : rmac[i].substring(1).split("/+")) {
						links.add(prefix + morph);
					}
				}
			}
		}
		return links;
	}

	private static class ExtraLinkRule {
		private final List<ExtraLinkCondition> conditions;
		private final List<String> links;
		private final boolean skipStrongs;

		public ExtraLinkRule(List<ExtraLinkCondition> conditions, List<String> links, boolean skipStrongs) {
			super();
			this.conditions = conditions;
			this.links = links;
			this.skipStrongs = skipStrongs;
		}
	}

	private static class ExtraLinkCondition {
		private final Versification.Reference verseNumber;
		private final List<String> strongNumbers;
		private final List<String> rmacNumbers;

		public static ExtraLinkCondition parse(String[] conditions) {
			Reference verseNumber = null;
			final List<String> strongNumbers = new ArrayList<>();
			final List<String> rmacNumbers = new ArrayList<>();
			for (String cond : conditions) {
				try {
					if (cond.matches(".*\\.[0-9]+\\." + Utils.VERSE_REGEX)) {
						String[] parts = cond.split("\\.", 3);
						if (verseNumber != null)
							throw new IllegalArgumentException("More than one verse reference");
						verseNumber = new Versification.Reference(BookID.fromOsisId(parts[0]), Integer.parseInt(parts[1]), parts[2]);
					} else if (cond.matches("[A-Z][1-9][0-9]*")) {
						strongNumbers.add(cond);
					} else if (cond.matches(Utils.MORPH_REGEX)) {
						rmacNumbers.add(cond);
					} else {
						throw new IllegalArgumentException("Unsupported condition format");
					}
				} catch (IllegalArgumentException ex) {
					System.out.println("WARNING: Invalid extra link condition " + cond + ": " + ex.toString());
					return null;
				}
			}
			return new ExtraLinkCondition(verseNumber, strongNumbers, rmacNumbers);
		}

		private ExtraLinkCondition(Reference verseNumber, List<String> strongNumbers, List<String> rmacNumbers) {
			super();
			this.verseNumber = verseNumber;
			this.strongNumbers = strongNumbers;
			this.rmacNumbers = rmacNumbers;
		}

		public boolean matches(Reference verseNum, String[] strongs, String[] rmacs) {
			if (verseNumber != null && !verseNumber.equals(verseNum))
				return false;
			for (String strong : strongNumbers) {
				if (strongs == null || !Arrays.asList(strongs).contains(strong))
					return false;
			}
			for (String rmac : rmacNumbers) {
				if (rmacs == null || !Arrays.asList(rmacs).contains(rmac)) {
					return false;
				}
			}
			return true;
		}
	}
}
