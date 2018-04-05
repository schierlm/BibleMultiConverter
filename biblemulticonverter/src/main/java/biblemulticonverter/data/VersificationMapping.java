package biblemulticonverter.data;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import biblemulticonverter.data.Versification.Reference;

/**
 * Represents a mapping between different {@link Versifications}. This makes it
 * possible to find out where the same text is in different versifications.
 */
public final class VersificationMapping {

	public static VersificationMapping build(Versification from, Versification to, Map<Reference, List<Reference>> mappings) {
		TreeMap<Integer, Rule> rules = new TreeMap<Integer, Rule>();
		int[][] fullMap = new int[from.getVerseCount()][];
		for (Map.Entry<Reference, List<Reference>> mapping : mappings.entrySet()) {
			if (mapping.getValue().size() == 0)
				throw new IllegalArgumentException("Empty mapping specified for " + mapping.getKey());
			int fromIndex = from.getIndexForReference(mapping.getKey());
			if (fromIndex == -1)
				throw new IllegalArgumentException("Mapping for verse " + mapping.getKey() + " that does not exist in source mapping " + from.getName());
			if (fullMap[fromIndex] != null)
				throw new IllegalArgumentException("More than one mapping for " + mapping.getKey());
			int toIndexStart = to.getIndexForReference(mapping.getValue().get(0));
			if (toIndexStart == -1)
				throw new IllegalArgumentException("Mapping to verse " + mapping.getValue().get(0) + " that does not exist in destination mapping " + to.getName());
			boolean consecutive = true;
			for (int i = 1; i < mapping.getValue().size(); i++) {
				int toIndex = to.getIndexForReference(mapping.getValue().get(i));
				if (toIndex == -1)
					throw new IllegalArgumentException("Mapping to verse " + mapping.getValue().get(i) + " that does not exist in destination mapping " + to.getName());
				if (toIndex != toIndexStart + i)
					consecutive = false;
			}
			if (consecutive) {
				fullMap[fromIndex] = new int[] { toIndexStart, toIndexStart + mapping.getValue().size() - 1 };
			} else {
				fullMap[fromIndex] = new int[0];
				int[] toIndexExtra = new int[mapping.getValue().size() - 1];
				for (int i = 1; i < mapping.getValue().size(); i++) {
					toIndexExtra[i - 1] = to.getIndexForReference(mapping.getValue().get(i));
				}
				rules.put(fromIndex, new DisjointRule(fromIndex, toIndexStart, toIndexExtra));
			}
		}
		for (int i = 0; i < fullMap.length; i++) {
			if (fullMap[i] == null || fullMap[i].length == 0)
				continue;
			int start = i;
			while (i + 1 < fullMap.length && fullMap[i + 1] != null && fullMap[i + 1].length != 0) {
				if (fullMap[i + 1][0] == fullMap[i][0] + 1 && fullMap[i + 1][1] == fullMap[i][1] + 1) {
					i++;
				} else {
					break;
				}
			}
			int count = i - start + 1;
			rules.put(start, new Rule(start, fullMap[start][0], fullMap[start][1], count));
		}
		return new VersificationMapping(from, to, rules);
	}

	public static VersificationMapping join(VersificationMapping m1, VersificationMapping m2) {
		Map<Reference, List<Reference>> map = new HashMap<>();
		for (int j = 0; j < m1.getFrom().getVerseCount(); j++) {
			Reference r1 = m1.getFrom().getReference(j);
			List<Reference> r3 = new ArrayList<>();
			for (Reference r2 : m1.getMapping(r1)) {
				r3.addAll(m2.getMapping(r2));
			}
			for (int k = 0; k < r3.size() - 1; k++) {
				if (r3.get(k).equals(r3.get(k + 1))) {
					r3.remove(k);
					k--;
				}
			}
			if (!r3.isEmpty())
				map.put(r1, r3);
		}
		return VersificationMapping.build(m1.getFrom(), m2.getTo(), map);
	}

	public static VersificationMapping findBestMapping(Versification fromVersification, Versification toVersification, List<VersificationMapping> candidates) {
		Map<Reference, List<Reference>> map = new HashMap<>();
		for (int i = 0; i < fromVersification.getVerseCount(); i++) {
			Reference r = fromVersification.getReference(i);
			List<Reference> bestMapping = null;
			for (VersificationMapping candidate : candidates) {
				List<Reference> thisMapping = candidate.getMapping(r);
				if (thisMapping.isEmpty())
					thisMapping = null;
				if (bestMapping == null) {
					bestMapping = thisMapping;
				} else if (thisMapping != null) {
					bestMapping.retainAll(thisMapping);
					if (bestMapping.isEmpty())
						return null;
				}
			}
			if (bestMapping != null) {
				map.put(r, bestMapping);
			}
		}
		return VersificationMapping.build(fromVersification, toVersification, map);
	}

	protected static VersificationMapping fromRules(Versification from, Versification to, List<String> ruleDefinitions) {
		TreeMap<Integer, Rule> rules = new TreeMap<Integer, Rule>();
		for (String def : ruleDefinitions) {
			String[] parts = def.split("=", 2);
			int count = 1;
			if (parts[0].contains("+")) {
				String[] subParts = parts[0].split("\\+", 2);
				count = Integer.parseInt(subParts[1]);
				parts[0] = subParts[0];
			}
			int fromIndex = Integer.parseInt(parts[0]);
			if (parts[1].contains(" ")) {
				String[] subParts = parts[1].split(" ");
				int[] toIndexExtra = new int[subParts.length - 1];
				for (int i = 0; i < toIndexExtra.length; i++) {
					toIndexExtra[i] = Integer.parseInt(subParts[i + 1]);
				}
				rules.put(fromIndex, new DisjointRule(fromIndex, Integer.parseInt(subParts[0]), toIndexExtra));
			} else if (parts[1].contains("-")) {
				String[] subParts = parts[1].split("-", 2);
				rules.put(fromIndex, new Rule(fromIndex, Integer.parseInt(subParts[0]), Integer.parseInt(subParts[1]), count));
			} else {
				int toIndex = Integer.parseInt(parts[1]);
				rules.put(fromIndex, new Rule(fromIndex, toIndex, toIndex, count));
			}
		}
		return new VersificationMapping(from, to, rules);
	}

	private final Versification from;
	private final Versification to;
	private final TreeMap<Integer, Rule> rules;

	private VersificationMapping(Versification from, Versification to, TreeMap<Integer, Rule> rules) {
		this.from = from;
		this.to = to;
		this.rules = rules;
	}

	public Versification getFrom() {
		return from;
	}

	public Versification getTo() {
		return to;
	}

	public int getRuleCount() {
		return rules.size();
	}

	/**
	 * Find a mapping for a given verse reference
	 *
	 * @param fromReference
	 *            Verse reference to map
	 * @return {@code null} if the verse reference is not part of the source
	 *         versification; an empty {@link List} if the verse cannot be
	 *         mapped to the target versification; or a list of references it
	 *         can be mapped to.
	 */
	public List<Reference> getMapping(Reference fromReference) {
		int fromIndex = from.getIndexForReference(fromReference);
		if (fromIndex == -1)
			return null;
		final List<Reference> result = new ArrayList<>();
		Map.Entry<Integer, Rule> rule = rules.floorEntry(fromIndex);
		if (rule == null || fromIndex >= rule.getValue().fromIndex + rule.getValue().verseCount)
			return result;
		Rule r = rule.getValue();
		for (int toIndex = r.toIndexFrom; toIndex <= r.toIndexTo; toIndex++) {
			result.add(to.getReference(toIndex + (fromIndex - r.fromIndex)));
		}
		if (r instanceof DisjointRule) {
			DisjointRule dr = (DisjointRule) r;
			for (int i = 0; i < dr.toIndexExtra.length; i++) {
				result.add(to.getReference(dr.toIndexExtra[i]));
			}
		}
		return result;
	}

	protected void dumpRules(Writer w) throws IOException {
		for (Rule r : rules.values()) {
			w.write(" " + r.fromIndex);
			if (r.verseCount > 1) {
				w.write("+" + r.verseCount);
			}
			w.write("=" + r.toIndexFrom);
			if (r.toIndexFrom != r.toIndexTo) {
				w.write("-" + r.toIndexTo);
			}
			if (r instanceof DisjointRule) {
				for (int i = 0; i < ((DisjointRule) r).toIndexExtra.length; i++) {
					w.write(" " + ((DisjointRule) r).toIndexExtra[i]);
				}
			}
			w.write('\n');
		}
	}

	private static class Rule {

		private final int fromIndex, toIndexFrom, toIndexTo, verseCount;

		private Rule(int fromIndex, int toIndexFrom, int toIndexTo, int verseCount) {
			super();
			this.fromIndex = fromIndex;
			this.toIndexFrom = toIndexFrom;
			this.toIndexTo = toIndexTo;
			this.verseCount = verseCount;
		}
	}

	/**
	 * Special rule for disjoint toIndices. Yes I know this violates Liskov
	 * Substitution principle, but I do not want to have an extra reference to
	 * an int[] in every "normal" rule, and this class is a private inner class
	 * anyway.
	 */
	private static class DisjointRule extends Rule {

		private final int[] toIndexExtra;

		private DisjointRule(int fromIndex, int toIndex, int[] toIndexExtra) {
			super(fromIndex, toIndex, toIndex, 1);
			this.toIndexExtra = toIndexExtra;
		}
	}
}
