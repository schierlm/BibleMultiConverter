package biblemulticonverter.logos.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Utils;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.tools.Tool;

public class LogosWordNumberTool implements Tool {
	public static final String[] HELP_TEXT = {
			"Create or change word number databases for building WordNumber: datatype links",
			"",
			"Usage: LogosWordNumberTool <dbfile> <subcommand> <args>",
			"",
			"Subcommands:",
			"- import <csvfile> <name> <txtfile>",
			"- importapparatus <csvfile> <txtfile> <editionAbbr> [...]",
			"- updatewordlock <wordlockfile> <csvfile> <name> [...]",
			"- exportmappeddb <mappeddbfile> <csvfile> <name> [<wordlockfile>]",
			"- mapaugmentidxdb <srcaugmentdbfile> <destaugmentdbfile> <srccsvfile> <srcname> <destcsvfile> <destname> [LockWords]"
	};

	public void run(String... args) throws Exception {
		System.out.println("Loading " + args[0]);
		Map<String, List<String>> db = loadDatabase(new File(args[0]));
		String saveName = null;
		switch (args[1]) {
		case "import": {
			Map<BookID, List<List<List<String[]>>>> mapping = loadMapping(args[2], args[3], false);
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(args[4]), StandardCharsets.UTF_8))) {
				String line = br.readLine();
				if (!line.startsWith("PREFIX\t"))
					throw new IOException("Invalid word number file: " + line);
				String prefix = line.substring(7);
				while ((line = br.readLine()) != null) {
					if (line.isEmpty())
						continue;
					String[] parts = line.split("\t");
					if (parts.length != 2)
						throw new IOException("Invalid word number file line: " + line);
					BookID bid = BookID.fromOsisId(parts[0]);
					List<String> nums = new ArrayList<>();
					for (String range : parts[1].split(" ")) {
						if (range.startsWith("*")) {
							int count = range.equals("*") ? 1 : Integer.parseInt(range.substring(1));
							for (int i = 0; i < count; i++) {
								nums.add("*");
							}
							continue;
						}
						int pos = range.indexOf('-');
						if (pos == -1) {
							nums.add(prefix + range);
						} else {
							int from = Integer.parseInt(range.substring(0, pos));
							int to = Integer.parseInt(range.substring(pos + 1));
							for (int i = from; i <= to; i++) {
								nums.add(prefix + i);
							}
						}
					}
					List<String[]> refs = mapping.remove(bid).stream().flatMap(x -> x.stream()).flatMap(x -> x.stream()).collect(Collectors.toList());
					if (refs.size() != nums.size())
						throw new IOException("Edition has " + refs.size() + " words in " + bid + ", yet word number file has " + nums.size());
					for (int i = 0; i < refs.size(); i++) {
						String num = nums.get(i);
						if (num.equals("*"))
							continue;
						String[] ref = refs.get(i);
						if (addEntry(db, num, ref)) {
							saveName = args[0];
						}
					}
				}
			}
			break;
		}
		case "importlist": {
			Map<BookID, List<List<List<String[]>>>> mapping = loadMapping(args[2], args[3], false);
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(args[4]), StandardCharsets.UTF_8))) {
				String line;
				Pattern cellPattern = Utils.compilePattern("([A-Za-z0-9]{3})\\.([0-9]+)\\.([0-9]+)#([0-9]+)");
				while ((line = br.readLine()) != null) {
					String[] parts = line.split("\t");
					if (parts.length != 2)
						throw new IOException("Invalid word number list line: " + line);
					Matcher m = cellPattern.matcher(parts[0]);
					if (!m.matches())
						throw new IOException(parts[0]);
					BookID bid = ParatextID.fromIdentifier(m.group(1).toUpperCase()).getId();
					String[] ref = mapping.get(bid).get(Integer.parseInt(m.group(2)) - 1).get(Integer.parseInt(m.group(3)) - 1).get(Integer.parseInt(m.group(4)) - 1);
					String num = parts[1];
					if (addEntry(db, num, ref)) {
						saveName = args[0];
					}
				}
			}
			break;
		}
		case "importapparatus": {
			String[] abbrs = new String[args.length - 4];
			Map<String, String[]> apparatusRows = new HashMap<>();
			@SuppressWarnings("unchecked")
			Map<String, String>[] wordNumUpdates = new HashMap[abbrs.length];
			Map<String, Integer> abbrShortIndex = new HashMap<>();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(args[2]), StandardCharsets.UTF_8))) {
				String line = br.readLine();
				List<String> headers = Arrays.asList(line.split("\t"));
				int[] abbrCols = new int[abbrs.length];
				for (int i = 0; i < abbrs.length; i++) {
					abbrs[i] = args[4 + i];
					abbrCols[i] = headers.indexOf(abbrs[i] + ":TAGNT-Idx");
					if (abbrCols[i] == -1)
						throw new IOException("Unsupported edition: " + args[4 + i]);
					wordNumUpdates[i] = new HashMap<>();
					abbrShortIndex.put(abbrs[i].substring(0, 2), i);
				}
				Pattern cellPattern = Utils.compilePattern("([A-Za-z0-9]{3})\\.([0-9]+)\\.([0-9]+)#([0-9]+(:[0-9AB]+(\\([12+]\\))?)?)");
				while ((line = br.readLine()) != null) {
					String[] parts = line.split("\t");
					String refBase = Matcher.quoteReplacement(parts[0]) + "#";
					String cellRef = parts[1].replaceFirst("^@", refBase);
					Matcher m = cellPattern.matcher(cellRef);
					if (!m.matches())
						throw new IOException(cellRef);
					BookID bid = ParatextID.fromIdentifier(m.group(1).toUpperCase()).getId();
					String key = bid.getOsisID() + " " + m.group(2) + ":" + m.group(3) + "#" + m.group(4);
					String[] value = new String[abbrs.length * 2];
					for (int i = 0; i < abbrs.length; i++) {
						if (parts[abbrCols[i]].length() == 0) {
							value[i * 2] = "";
						} else {
							cellRef = parts[abbrCols[i]].replaceFirst("^@", refBase);
							m = cellPattern.matcher(cellRef);
							if (!m.matches())
								throw new IOException(cellRef);
							bid = ParatextID.fromIdentifier(m.group(1).toUpperCase()).getId();
							value[i * 2] = bid.getOsisID() + " " + m.group(2) + ":" + m.group(3) + "#" + m.group(4);
						}
					}
					apparatusRows.put(key, value);
				}
			}
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(args[3]), StandardCharsets.UTF_8))) {
				String line = br.readLine();
				if (!line.startsWith("PREFIX\t"))
					throw new IOException("Invalid word number file: " + line);
				String prefix = line.substring(7);
				while ((line = br.readLine()) != null) {
					String[] parts = line.split("\t");
					if (parts.length != 2)
						throw new IOException(line);
					int vg = 1, vgw = 1;
					for (String word : parts[1].split(" ")) {
						if (word.equals("/")) {
							vg++;
							vgw = 1;
						} else if (word.equals("?")) {
							vgw++;
						} else {
							String[] wordparts = word.split("\\|");
							String[] row = apparatusRows.get(parts[0] + "#" + vg + ":" + vgw);
							if (row == null)
								throw new IOException(parts[0] + "#" + vg + ":" + vgw);
							for (int i = 0; i < abbrs.length; i++) {
								if (!row[i * 2].isEmpty()) {
									row[i * 2 + 1] = handleApparatusEntry(wordNumUpdates[i], prefix, wordparts[0]);
								}
							}
							for (int i = 1; i < wordparts.length; i++) {
								String[] kv = wordparts[i].split("=");
								if (kv.length != 2)
									throw new IOException(wordparts[i]);
								String[] partrow = row;
								if (kv[0].length() > 2) {
									partrow = apparatusRows.get(parts[0] + "#" + vg + ":" + vgw + "(" + kv[0].substring(2) + ")");
									if (partrow == null)
										throw new IOException(parts[0] + "#" + vg + ":" + vgw);
									kv[0] = kv[0].substring(0, 2);
								}
								Integer idx = abbrShortIndex.get(kv[0]);
								if (idx != null) {
									if (partrow[idx * 2].isEmpty()) {
										throw new IOException(wordparts[i]);
									} else {
										partrow[idx * 2 + 1] = handleApparatusEntry(wordNumUpdates[idx], prefix, kv[1]);
									}
								}
							}
							vgw++;
						}
					}
				}
			}
			Map<String, String> firstEditionWordsPos = new HashMap<>();
			for (Map.Entry<String, String[]> row : apparatusRows.entrySet()) {
				for (int i = 0; i < abbrs.length; i++) {
					String num = row.getValue()[i * 2 + 1];
					if (num == null)
						continue;
					String[] ref = row.getValue()[i * 2].split("#");
					if (num.startsWith("=")) {
						num = num.substring(1);
					} else {
						String newNum = wordNumUpdates[i].remove(num);
						if (newNum != null) {
							num = newNum;
						}
					}
					if (addEntry(db, num, ref)) {
						saveName = args[0];
					}
				}
				if (!row.getValue()[0].isEmpty()) {
					String[] pos = row.getValue()[0].split("#");
					String wn = db.get(pos[0]).get(Integer.parseInt(pos[1]) - 1);
					firstEditionWordsPos.put(wn, row.getKey());
				}
			}
			Set<String> wordNumUpdateKeys = new HashSet<>();
			for (int i = 0; i < abbrs.length; i++) {
				wordNumUpdateKeys.addAll(wordNumUpdates[i].keySet());
			}
			for (String key : wordNumUpdateKeys) {
				// find out which word we need in first edition
				String lookupNum = key;
				if (wordNumUpdates[0].containsKey(key)) {
					lookupNum = wordNumUpdates[0].get(key);
				}
				String rowLabel = firstEditionWordsPos.get(lookupNum);
				if (rowLabel == null)
					throw new IOException(lookupNum);
				String[] row = apparatusRows.get(rowLabel);
				for (int i = 0; i < abbrs.length; i++) {
					String num = wordNumUpdates[i].getOrDefault(key, key);
					String[] ref = row[i * 2].split("#");
					if (addEntry(db, num, ref)) {
						saveName = args[0];
					}
				}
			}
			break;
		}
		case "updatewordlock": {
			System.out.println("Loading " + args[2]);
			Map<String, List<String>> wordlock = loadDatabase(new File(args[2]));
			Pattern cellPattern = Utils.compilePattern("([A-Za-z0-9]{3})\\.([0-9]+)\\.([0-9]+)#([0-9]+)");
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(args[3]), StandardCharsets.UTF_8))) {
				int[] columns = new int[args.length - 4];
				String line = br.readLine();
				String[] headers = line.split("\t");
				for (int i = 4; i < args.length; i++) {
					int colidx = Arrays.asList(headers).indexOf(args[i] + ":Idx");
					if (colidx == -1 || !Arrays.asList(args[i] + ":Greek", args[i] + ":Hebrew").contains(headers[colidx + 1]))
						throw new IOException("Edition " + args[i] + " not found in file " + args[3]);
					columns[i - 4] = colidx;
				}
				while ((line = br.readLine()) != null) {
					String[] fields = line.split("\t");
					String wordnum = null;
					for (int i = 0; i < columns.length; i++) {
						if (fields[columns[i]].isEmpty())
							continue;
						String hebgk = fields[columns[i] + 1];
						if (hebgk.isEmpty())
							hebgk = fields[2];
						hebgk = normalize(hebgk);
						if (wordnum == null) {
							String col = fields[1];
							if (col.startsWith("@")) {
								col = fields[0] + "#" + col.substring(1);
							}
							Matcher m = cellPattern.matcher(col);
							if (!m.matches())
								throw new IOException("Unsupported mapping value: " + col);
							BookID bid = ParatextID.fromIdentifier(m.group(1).toUpperCase()).getId();
							int num = Integer.parseInt(m.group(4));
							List<String> wndata = db.get(bid.getOsisID() + " " + Integer.parseInt(m.group(2)) + ":" + Integer.parseInt(m.group(3)));
							if (wndata == null || wndata.size() < num) {
								throw new IOException("Trying to wordlock against an edition with incomplete word numbers");
							}
							List<String> words = wordlock.computeIfAbsent(wndata.get(num - 1), x -> new ArrayList<>());
							if (!words.contains(hebgk)) {
								words.add(hebgk);
							}
						}
					}
				}
			}
			for (List<String> words : wordlock.values()) {
				Collections.sort(words);
			}
			saveName = args[2];
			db.clear();
			db.putAll(wordlock);
			break;
		}
		case "exportmappeddb": {
			Map<String, List<String>> wordlock = args.length == 5 ? null : loadDatabase(new File(args[5]));
			Map<BookID, List<List<List<String[]>>>> mapping = loadMapping(args[3], args[4], wordlock != null);
			Map<String, List<String>> mappedDb = new HashMap<>();
			for (Entry<BookID, List<List<List<String[]>>>> bookEntry : mapping.entrySet()) {

				for (int cn = 1; cn <= bookEntry.getValue().size(); cn++) {
					List<List<String[]>> chapter = bookEntry.getValue().get(cn - 1);
					for (int vn = 1; vn <= chapter.size(); vn++) {
						List<String> row = new ArrayList<>();
						List<String[]> verse = chapter.get(vn - 1);
						for (int num = 1; num <= verse.size(); num++) {
							String[] pos = verse.get(num - 1);
							List<String> wns = db.get(pos[0]);
							int idx = Integer.parseInt(pos[1]);
							String wn = "";
							if (wns != null && idx <= wns.size()) {
								wn = wns.get(idx - 1);
								if (wordlock != null && !wn.isEmpty()) {
									String hebgk = pos[2];
									if (!wordlock.get(wn).contains(hebgk)) {
										wn = "";
									}
								}
							}
							row.add(wn);
						}
						mappedDb.put(bookEntry.getKey().getOsisID() + " " + cn + ":" + vn, row);
					}
				}
			}
			saveName = args[2];
			db.clear();
			db.putAll(mappedDb);
			break;
		}
		case "mapaugmentidxdb": {
			boolean useWordLock = false;
			if (args.length > 8) {
				if (!args[8].equals("LockWords"))
					throw new RuntimeException("Unsupported LockWords argument");
				useWordLock = true;
			}
			Map<BookID, List<List<List<String[]>>>> sourceMapping = loadMapping(args[4], args[5], useWordLock);
			Map<String, String> destMapping = loadReverseMapping(args[6], args[7], useWordLock);
			Properties oldDB = new Properties();
			try (FileInputStream in = new FileInputStream(args[2])) {
				oldDB.load(in);
			}
			Properties newDB = new Properties();
			for (Map.Entry<String, String> entry : ((Map<String, String>) (Map) oldDB).entrySet()) {
				String oldKey = entry.getKey(), suffix = "";
				if (oldKey.endsWith("+") || oldKey.endsWith("@")) {
					suffix = oldKey.substring(oldKey.length() - 1);
					oldKey = oldKey.substring(0, oldKey.length() - 1);
					Matcher m = Utils.compilePattern("([A-Za-z0-9-]+)\\.([0-9]+)\\.([0-9]+)@([0-9]+)").matcher(oldKey);
					if (!m.matches()) {
						System.out.println("WARNING: Skipping unsupported database key: " + oldKey);
						continue;
					}
					String[] mapping;
					try {
						mapping = sourceMapping.get(BookID.fromOsisId(m.group(1))).get(Integer.parseInt(m.group(2)) - 1).get(Integer.parseInt(m.group(3)) - 1).get(Integer.parseInt(m.group(4)) - 1);
					} catch (IndexOutOfBoundsException | NullPointerException ex) {
						System.out.println("WARNING: Skipping database key that is not in source mapping: " + oldKey);
						continue;
					}
					String newKey = destMapping.get(mapping[0] + "#" + mapping[1] + "#" + mapping[2]);
					if (newKey == null) {
						System.out.println("Skipping " + oldKey);
						continue;
					}
					newDB.setProperty(newKey + suffix, entry.getValue());
				}
			}
			try (FileOutputStream out = new FileOutputStream(args[3])) {
				newDB.store(out, "AugmentGrammar database");
			}
			break;
		}
		default:
			System.out.println("Unsupported command: " + args[1]);
		}
		if (saveName != null) {
			System.out.println("Saving " + saveName);
			try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(saveName), StandardCharsets.UTF_8))) {
				List<String> keys = new ArrayList<>(db.keySet());
				Collections.sort(keys);
				for (String key : keys) {
					bw.write(key + "=" + String.join(",", db.get(key)));
					bw.newLine();
				}
			}
		}
	}

	private String handleApparatusEntry(Map<String, String> wordNumUpdates, String prefix, String entry) {
		if (entry.contains("/")) {
			String[] fromto = entry.split("/");
			if (fromto.length != 2)
				throw new RuntimeException(entry);
			if (wordNumUpdates.containsKey(prefix + fromto[0]))
				throw new RuntimeException("Existing value for " + entry + ": " + wordNumUpdates.get(prefix + fromto[0]));
			wordNumUpdates.put(prefix + fromto[0], prefix + fromto[1]);
			return "=" + prefix + fromto[0];
		} else {
			return prefix + entry;
		}
	}

	private boolean addEntry(Map<String, List<String>> db, String num, String[] ref) throws IOException {
		List<String> row = db.computeIfAbsent(ref[0], x -> new ArrayList<>());
		int refidx = Integer.parseInt(ref[1]);
		while (row.size() < refidx)
			row.add("");
		String oldnum = row.get(refidx - 1);
		if (oldnum.equals(num))
			return false;
		if (!oldnum.isEmpty())
			throw new IOException("Trying to assign " + num + " to " + ref[0] + "#" + refidx + ", which is " + oldnum);
		row.set(refidx - 1, num);
		return true;
	}

	private Map<BookID, List<List<List<String[]>>>> loadMapping(String csvfile, String name, boolean includeWords) throws IOException {
		Map<BookID, List<List<List<String[]>>>> result = new EnumMap<>(BookID.class);
		Pattern cellPattern = Utils.compilePattern("([A-Za-z0-9]{3})\\.([0-9]+)\\.([0-9]+)#([0-9]+)");
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(csvfile), StandardCharsets.UTF_8))) {
			String line = br.readLine();
			int colidx = Arrays.asList(line.split("\t")).indexOf(name + ":Idx");
			if (colidx == -1)
				throw new IOException("Edition " + name + " not found in file " + csvfile);
			if (includeWords && !Arrays.asList(name + ":Hebrew", name + ":Greek").contains(line.split("\t")[colidx + 1])) {
				throw new IOException("Edition " + name + " has no Greek/Hebrew words, which are required for wordlock");
			}
			while ((line = br.readLine()) != null) {
				String[] fields = line.split("\t");
				String col = fields[colidx];
				if (col.isEmpty())
					continue;
				if (col.startsWith("@")) {
					col = fields[0] + "#" + col.substring(1);
				}
				Matcher m = cellPattern.matcher(col);
				if (!m.matches())
					throw new IOException("Unsupported mapping value: " + col);
				BookID bid = ParatextID.fromIdentifier(m.group(1).toUpperCase()).getId();
				int ch = Integer.parseInt(m.group(2)), vs = Integer.parseInt(m.group(3));
				int num = Integer.parseInt(m.group(4));
				String value = fields[1];
				if (value.startsWith("@")) {
					value = fields[0] + "#" + fields[1].substring(1);
				}

				List<List<List<String[]>>> chapters = result.computeIfAbsent(bid, x -> new ArrayList<>());
				while (chapters.size() < ch) {
					chapters.add(new ArrayList<>());
				}
				List<List<String[]>> verses = chapters.get(ch - 1);
				while (verses.size() < vs) {
					verses.add(new ArrayList<>());
				}
				List<String[]> words = verses.get(vs - 1);
				while (words.size() < num) {
					words.add(null);
				}
				Matcher mm = cellPattern.matcher(value);
				if (!mm.matches())
					throw new IOException("Unsupported mapping value: " + col);
				BookID mbid = ParatextID.fromIdentifier(mm.group(1).toUpperCase()).getId();
				String ref = mbid.getOsisID() + " " + Integer.parseInt(mm.group(2)) + ":" + Integer.parseInt(mm.group(3));
				String hebgk = null;
				if (includeWords) {
					hebgk = fields[colidx + 1];
					if (hebgk.isEmpty())
						hebgk = fields[2];
					hebgk = normalize(hebgk);
				}
				words.set(num - 1, new String[] { ref, mm.group(4), hebgk });
			}
		}
		return result;
	}

	private Map<String, String> loadReverseMapping(String csvfile, String name, boolean includeWords) throws IOException {
		Map<String, String> result = new HashMap<>();
		Pattern cellPattern = Utils.compilePattern("([A-Za-z0-9]{3})\\.([0-9]+\\.[0-9]+)#([0-9]+)");
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(csvfile), StandardCharsets.UTF_8))) {
			String line = br.readLine();
			int colidx = Arrays.asList(line.split("\t")).indexOf(name + ":Idx");
			if (colidx == -1)
				throw new IOException("Edition " + name + " not found in file " + csvfile);
			if (includeWords && !Arrays.asList(name + ":Hebrew", name + ":Greek").contains(line.split("\t")[colidx + 1])) {
				throw new IOException("Edition " + name + " has no Greek/Hebrew words, which are required for wordlock");
			}
			while ((line = br.readLine()) != null) {
				String[] fields = line.split("\t");
				String col = fields[colidx];
				if (col.isEmpty())
					continue;
				if (col.startsWith("@")) {
					col = fields[0] + "#" + col.substring(1);
				}
				Matcher m = cellPattern.matcher(col);
				if (!m.matches())
					throw new IOException("Unsupported mapping value: " + col);
				BookID bid = ParatextID.fromIdentifier(m.group(1).toUpperCase()).getId();
				String target = bid.getOsisID() + "." + m.group(2) + "@" + m.group(3);
				String value = fields[1];
				if (value.startsWith("@")) {
					value = fields[0] + "#" + fields[1].substring(1);
				}
				Matcher mm = cellPattern.matcher(value);
				if (!mm.matches())
					throw new IOException("Unsupported mapping value: " + col);
				BookID mbid = ParatextID.fromIdentifier(mm.group(1).toUpperCase()).getId();
				String hebgk = null;
				if (includeWords) {
					hebgk = fields[colidx + 1];
					if (hebgk.isEmpty())
						hebgk = fields[2];
					hebgk = normalize(hebgk);
				}
				result.put(mbid.getOsisID() + " " + mm.group(2).replace(".", ":") + "#" + mm.group(3) + "#" + hebgk, target);
			}
		}
		return result;
	}

	private static String normalize(String text) {
		return Normalizer.normalize(text, Form.NFKD).replaceAll("[^\\p{L}]++", "").replaceAll("[\\p{Lm}]++", "").toLowerCase().trim();
	}

	public static Map<String, List<String>> loadDatabase(File file) throws IOException {
		Map<String, List<String>> result = new HashMap<>();
		if (file.exists()) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					int pos = line.indexOf('=');
					List<String> vals = new ArrayList<>(Arrays.asList(line.substring(pos + 1).split(",")));
					result.put(line.substring(0, pos), vals);
				}
			}
		}
		return result;
	}
}
