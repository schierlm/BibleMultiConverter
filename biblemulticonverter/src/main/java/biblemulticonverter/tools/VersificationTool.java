package biblemulticonverter.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.Main;
import biblemulticonverter.ModuleRegistry.Module;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.versification.VersificationFormat;

public class VersificationTool implements Tool {

	public static final String[] HELP_TEXT = {
			"Usage: Versification <dbfile> <subcommand> <args>",
			"",
			"Change versification databases or query information from them.",
			"Versification databases can be used to detect versification of a Bible",
			"or to convert a Bible to a different versification.",
			"",
			"Subcommands: ",
			"- list [<versification|mapping> [...]]",
			"- import <format> [<args>]",
			"- export <format> <outFile> [<versification|mapping> [...]]",
			"- rename <versification> <newName>",
			"- remove [<versification|mapping> [...]]",
			"- join <mapping> [<mapping> [...]]",
			"- compare {<versification1> <versification2>|<mapping1> <mapping2>}"
	};

	@Override
	public void run(String... args) throws Exception {
		File dbFile = new File(args[0]);
		VersificationSet vs = dbFile.exists() ? new VersificationSet(dbFile) : new VersificationSet();
		boolean save = false;
		switch (args[1]) {
		case "list":
			if (args.length == 2) {
				for (Versification v : vs.getVersifications())
					printInfo(v);
				Map<String, Integer> mappingCounts = new HashMap<>();
				for (VersificationMapping m : vs.getMappings()) {
					String namePrefix = m.getFrom().getName() + "/" + m.getTo().getName() + "/";
					printInfo(namePrefix + countOccurrence(mappingCounts, namePrefix), m);
				}
			} else {
				for (int i = 2; i < args.length; i++) {
					if (args[i].contains("/"))
						printInfo(args[i], vs.findMapping(args[i]));
					else
						printInfo(vs.findVersification(args[i]));
				}
			}
			break;
		case "import":
			Module<VersificationFormat> importModule = Main.versificationFormats.get(args[2]);
			importModule.getImplementationClass().newInstance().doImport(vs, Arrays.copyOfRange(args, 3, args.length));
			save = true;
			break;
		case "export":
			Module<VersificationFormat> exportModule = Main.versificationFormats.get(args[2]);
			List<Versification> vv = args.length == 4 ? vs.getVersifications() : new ArrayList<Versification>();
			List<VersificationMapping> mm = args.length == 4 ? vs.getMappings() : new ArrayList<VersificationMapping>();
			for (int i = 4; i < args.length; i++) {
				if (args[i].contains("/"))
					mm.add(vs.findMapping(args[i]));
				else
					vv.add(vs.findVersification(args[i]));
			}
			exportModule.getImplementationClass().newInstance().doExport(new File(args[3]), vv, mm);
			break;
		case "rename":
			vs.findVersification(args[2]).setName(args[3]);
			save = true;
			break;
		case "remove":
			List<Versification> vvv = new ArrayList<>();
			List<VersificationMapping> mmm = new ArrayList<>();
			for (int i = 2; i < args.length; i++) {
				if (args[i].contains("/"))
					mmm.add(vs.findMapping(args[i]));
				else
					vvv.add(vs.findVersification(args[i]));
			}
			for (Versification v : vvv) {
				vs.getVersifications().remove(v);
			}
			for (VersificationMapping m : mmm)
				vs.getMappings().remove(m);
			save = true;
			break;
		case "join":
			VersificationMapping m1 = vs.findMapping(args[2]);
			for (int i = 3; i < args.length; i++) {
				VersificationMapping m2 = vs.findMapping(args[i]);
				if (m1.getTo() != m2.getFrom())
					throw new IllegalStateException("Cannot join, versification mismatch: " + m1.getTo().getName() + " != " + m2.getFrom().getName());
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
				m1 = VersificationMapping.build(m1.getFrom(), m2.getTo(), map);
			}
			vs.getMappings().add(m1);
			save = true;
			break;
		case "compare":
			if (args[2].contains("/")) {
				VersificationMapping vm1 = vs.findMapping(args[2]);
				VersificationMapping vm2 = vs.findMapping(args[3]);
				if (vm1.getFrom() != vm2.getFrom() || vm1.getTo() != vm2.getTo())
					throw new IllegalArgumentException("Versification mappings need to have same source/destination versification to be compared");
				EnumMap<MappingVerseCompareVariant, Integer> variants = new EnumMap<>(MappingVerseCompareVariant.class);
				for (int i = 0; i < vm1.getFrom().getVerseCount(); i++) {
					Reference r = vm1.getFrom().getReference(i);
					MappingVerseCompareVariant cv = MappingVerseCompareVariant.get(vm1.getMapping(r), vm2.getMapping(r));
					variants.put(cv, variants.containsKey(cv) ? variants.get(cv) + 1 : 1);
				}
				for (Map.Entry<MappingVerseCompareVariant, Integer> entry : variants.entrySet()) {
					System.out.println(entry.getKey().toString() + ": " + entry.getValue());
				}
			} else {
				Versification v1 = vs.findVersification(args[2]);
				Set<Reference> v1r = new HashSet<>();
				for (int i = 0; i < v1.getVerseCount(); i++)
					v1r.add(v1.getReference(i));
				Versification v2 = vs.findVersification(args[3]);
				Set<Reference> v2r = new HashSet<>();
				for (int i = 0; i < v2.getVerseCount(); i++)
					v2r.add(v2.getReference(i));
				if (v1.getVerseCount() == v2.getVerseCount() && v1r.equals(v2r)) {
					boolean sameOrder = true;
					for (int i = 0; i < v1.getVerseCount(); i++) {
						if (!v1.getReference(i).equals(v2.getReference(i))) {
							sameOrder = false;
							break;
						}
					}
					System.out.println("Versifications contain same verses" + (sameOrder ? " in same order" : ""));
				} else if (v1r.containsAll(v2r)) {
					System.out.println("Right versification is a subset of left versification");
				} else if (v2r.containsAll(v1r)) {
					System.out.println("Left versification is a subset of right versification");
				} else {
					Set<Reference> intersect = new HashSet<Reference>();
					intersect.addAll(v1r);
					intersect.retainAll(v2r);
					if (intersect.isEmpty())
						System.out.println("Versifications are disjoint");
					else
						System.out.println("Versifications have " + intersect.size() + " verses in common");
				}
			}
			break;
		default:
			System.out.println("Unsupported command: " + args[1]);
		}
		if (save) {
			try (Writer w = new OutputStreamWriter(new FileOutputStream(dbFile), StandardCharsets.UTF_8)) {
				vs.saveTo(w);
			}
		}
	}

	private <T> int countOccurrence(Map<T, Integer> map, T object) {
		int result = map.containsKey(object) ? map.get(object) + 1 : 1;
		map.put(object, result);
		return result;
	}

	private void printInfo(Versification v) {
		System.out.println(v.getName() + ": " +
				(v.getDescription() == null ? "(No description)" : v.getDescription()) +
				" (" + v.getVerseCount() + " verses)");
		if (v.getAliases() != null)
			for (String a : v.getAliases())
				System.out.println("\tAlias: " + a);
	}

	private void printInfo(String label, VersificationMapping m) {
		System.out.println(label + ": " + m.getRuleCount() + " rules");
		Map<Reference, Integer> occurrenceFrom = new HashMap<>();
		Map<Reference, Integer> occurrenceTo = new HashMap<>();
		for (int i = 0; i < m.getFrom().getVerseCount(); i++) {
			Reference r = m.getFrom().getReference(i);
			for (Reference rr : m.getMapping(r)) {
				countOccurrence(occurrenceFrom, r);
				countOccurrence(occurrenceTo, rr);
			}
		}

		System.out.println("\t" + m.getFrom().getName() + ": " + mappedVerseInfo(occurrenceFrom) + " of " + m.getFrom().getVerseCount() + " verses");
		System.out.println("\t" + m.getTo().getName() + ": " + mappedVerseInfo(occurrenceTo) + " of " + m.getTo().getVerseCount() + " verses");
	}

	private String mappedVerseInfo(Map<Reference, Integer> occurrenceCounts) {
		int max = 0, sum = 0;
		for (int v : occurrenceCounts.values())
			max = Math.max(max, v);
		int[] groupCounters = new int[max];
		for (int v : occurrenceCounts.values())
			groupCounters[v - 1]++;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < groupCounters.length; i++) {
			sb.append(i > 0 ? "+" : "").append(groupCounters[i]);
			sum += groupCounters[i];
		}
		return sum + " (" + sb.toString() + ")";
	}

	private static enum MappingVerseCompareVariant {
		BOTH_UNMAPPED, LEFT_UNMAPPED, RIGHT_UNMAPPED, SAME_SINGLE_VERSE, SAME_ORDER, SAME_VERSES, LEFT_SUBSET, RIGHT_SUBSET, INTERSECTING, DISJOINT;

		private static MappingVerseCompareVariant get(List<Reference> left, List<Reference> right) {
			if (left.isEmpty() && right.isEmpty())
				return BOTH_UNMAPPED;
			if (left.isEmpty())
				return LEFT_UNMAPPED;
			if (right.isEmpty())
				return RIGHT_UNMAPPED;
			if (left.equals(right)) {
				if (left.size() == 1)
					return SAME_SINGLE_VERSE;
				else
					return SAME_ORDER;
			}
			if (left.size() == 1 && right.size() == 1)
				return DISJOINT;
			Set<Reference> leftSet = new HashSet<>(left);
			Set<Reference> rightSet = new HashSet<>(right);
			if (leftSet.equals(rightSet))
				return SAME_VERSES;
			if (leftSet.containsAll(rightSet))
				return RIGHT_SUBSET;
			if (rightSet.containsAll(leftSet))
				return LEFT_SUBSET;
			rightSet.retainAll(leftSet);
			return rightSet.isEmpty() ? DISJOINT : INTERSECTING;
		}
	}
}
