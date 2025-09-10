package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import biblemulticonverter.Main;
import biblemulticonverter.ModuleRegistry.Module;
import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;

public class AugmentGrammar implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Analyze used Grammar information in one bible and augment another one from that data.",
			"",
			"Usage: AugmentGrammar dump <csvfile> [humanStrongs]",
			"Usage: AugmentGrammar dumpwords <csvfile> [humanStrongs]",
			"Usage: AugmentGrammar analyze[:<modes>] <dbfile>",
			"Usage: AugmentGrammar analyzestrongdic <dbfile> [<keyPattern>]",
			"Usage: AugmentGrammar augment[:<modes>] <dbfile> <ExportFormat> [<ExportArgs>...]",
			"Usage: AugmentGrammar addsourceindex <ExportFormat> [<ExportArgs>...]",
			"Usage: AugmentGrammar addtextattr <ExportFormat> [<ExportArgs>...]",
			"Usage: AugmentGrammar addattrprefix <prefix> <ExportFormat> [<ExportArgs>...]",
			"",
			"Supported modes (separate multiple modes by comma):",
			"   S:  Read Strongs and augment Morphology",
			"   I:  Read Indices and augment Morphology",
			"   SI: Read Strongs and augment Indices",
			"   SLI: Read Strongs that exist more than once as list and augment Indices",
			"   IS: Read Indices and augment Strongs",
			"   SA:  Read Strongs and augment Attributes",
			"   IA:  Read Indices and augment Attributes",
			"   AI:  Read Absolute Indices and augment Morphology",
			"   AIS: Read Absolute Indices and augment Strongs",
			"   AIA:  Read Absolute Indices and augment Attributes",
			"",
			"Database can only be used to augment using a mode if the mode was also present when analyzing."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		List<Mode> modes = new ArrayList<>();
		modes.add(Mode.STRONGS2MORPH);
		if (exportArgs[0].startsWith("analyze:") || exportArgs[0].startsWith("augment:")) {
			String[] parts = exportArgs[0].split(":");
			exportArgs[0] = parts[0];
			modes.clear();
			for (String code : parts[1].split(",")) {
				modes.add(Mode.fromCode(code));
			}
		}
		if (exportArgs[0].equals("dump")) {
			boolean humanStrongs = exportArgs.length > 2 && exportArgs[2].equals("humanStrongs");
			try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[1]), StandardCharsets.UTF_8))) {
				runOperation(bible, null, false, new GrammarOperation() {

					private int counter = 0;
					private Reference lastReference = null;

					@Override
					public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
						if (!reference.equals(lastReference)) {
							counter = 0;
							lastReference = reference;
						}
						counter++;
						String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, strongsSuffixes, humanStrongs);
						String[] fullAttr = buildFullAttributes(false, null, attributeKeys, attributeValues);
						String[] fullSourceIndices = buildFullSourceIndices(sourceVerses, sourceIndices);
						int max = Math.max(Math.max(fullStrongs == null ? 0 : fullStrongs.length, rmac == null ? 0 : rmac.length), Math.max(sourceIndices == null ? 0 : sourceIndices.length, fullAttr == null ? 0 : fullAttr.length));
						try {
							for (int i = 0; i < max; i++) {
								bw.write(reference.toString() + "\t" + counter + "\t" + (i + 1) + "\t" + (fullStrongs != null && i < fullStrongs.length ? fullStrongs[i] : "") + "\t" + (rmac != null && i < rmac.length ? rmac[i] : "") + "\t" + (fullSourceIndices != null && i < fullSourceIndices.length ? fullSourceIndices[i] : "") + "\t" + (fullAttr != null && i < fullAttr.length ? fullAttr[i] : ""));
								bw.newLine();
							}
						} catch (IOException ex) {
							throw new RuntimeException(ex);
						}
						return null;
					}
				});
			}
		} else if (exportArgs[0].equals("dumpwords")) {
			boolean humanStrongs = exportArgs.length > 2 && exportArgs[2].equals("humanStrongs");
			try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[1]), StandardCharsets.UTF_8))) {
				runOperation(bible, null, false, new GrammarOperation() {

					private int counter = 0;
					private Reference lastReference = null;

					@Override
					public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
						if (!reference.equals(lastReference)) {
							counter = 0;
							lastReference = reference;
						}
						counter++;
						String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, strongsSuffixes, humanStrongs);
						String[] fullAttr = buildFullAttributes(false, null, attributeKeys, attributeValues);
						String[] sourceIndexNames = null;
						if (sourceIndices != null) {
							sourceIndexNames =  new String[sourceIndices.length];
							for(int i=0; i<sourceIndices.length; i++) {
								sourceIndexNames[i] = (sourceVerses != null && sourceVerses[i] != null ? sourceVerses[i].toString() : "") + sourceIndices[i];
							}
						}

						final StringBuilder sb = new StringBuilder(reference.toString() + "\t" + counter + "\t" + (fullStrongs != null ? String.join("+", fullStrongs) : "") + "\t" + (rmac != null ? String.join("+", rmac) : "") + "\t" + (sourceIndices != null ? Arrays.toString(sourceIndexNames).replace(", ", "+").replace("]", "").substring(1) : "") + "\t" + (fullAttr != null ? String.join("+", fullAttr) : "") + "\t");
						return new VisitorAdapter<RuntimeException>(null) {
							@Override
							public void visitText(String text) throws RuntimeException {
								sb.append(text);
							}

							public boolean visitEnd() {
								try {
									bw.write(sb.toString());
									bw.newLine();
								} catch (IOException ex) {
									throw new RuntimeException(ex);
								}
								return super.visitEnd();
							}
						};
					}
				});
			}
		} else if (exportArgs[0].equals("analyze")) {
			Properties props = new Properties();
			if (new File(exportArgs[1]).exists()) {
				try (FileInputStream in = new FileInputStream(exportArgs[1])) {
					props.load(in);
				}
			}
			Map<String, List<String>> strongsLists = new HashMap<>();
			runOperation(bible, null, false, new GrammarOperation() {

				private final String normalSeparator = System.getProperty("biblemulticonverter.concatattributes.separator");
				private final String firstSeparator = System.getProperty("biblemulticonverter.concatattributes.firstseparator", normalSeparator);
				private final String partialPattern = System.getProperty("biblemulticonverter.concatattributes.partialpattern", "${value}");

				Set<String> seenStrongRefs = new HashSet<>();

				private void analyze(Reference reference, char separator, String[] srcVals, String suffix, String[] dstVals) {
					if (srcVals != null && dstVals != null && srcVals.length == dstVals.length) {
						for (int i = 0; i < srcVals.length; i++) {
							String key = reference.getBook().getOsisID() + "." + reference.getChapter() + "." + reference.getVerse() + separator + srcVals[i] + suffix;
							String oldVal = props.getProperty(key);
							if (oldVal == null)
								props.setProperty(key, dstVals[i]);
							else if (!oldVal.equals(dstVals[i]))
								props.setProperty(key, "*");
						}
					}
				}

				private void analyzeAbs(Reference reference, char separator, Versification.Reference[] sourceVerses, int[] sourceIndices, String suffix, String[] dstVals) {
					if (sourceIndices != null && dstVals != null && sourceIndices.length == dstVals.length) {
						for (int i = 0; i < sourceIndices.length; i++) {
							analyze(sourceVerses != null && sourceVerses[i] != null ? sourceVerses[i] : reference, separator, new String[] { String.valueOf(sourceIndices[i]) }, suffix, new String[] { dstVals[i] });
						}
					}
				}

				private void analyzeAttr(Versification.Reference reference, char separator, String[] srcVals, String[] attributeKeys, String[] attributeValues) {
					if (attributeKeys == null || srcVals == null)
						return;
					if (normalSeparator != null) {
						Map<String, String> newAttr = new LinkedHashMap<>();
						for (int i = 0; i < attributeKeys.length; i++) {
							String k = attributeKeys[i];
							String val = newAttr.get(k);
							newAttr.put(k, (val == null ? "" : val + " ") + attributeValues[i]);
						}
						for (int i = 0; i < srcVals.length; i++) {
							String usedSeparator = i == 0 ? firstSeparator : normalSeparator;
							String usedPattern = srcVals.length == 1 ? "${value}" : partialPattern.replace("${index}", "" + (i + 1)).replace("${count}", "" + srcVals.length);
							String key = reference.getBook().getOsisID() + "." + reference.getChapter() + "." + reference.getVerse() + separator + srcVals[i] + "+";
							String oldVal = props.getProperty(key, "").trim();
							Set<String> usedAttrKeys = new HashSet<String>();
							for (String oldPart : oldVal.split(" ")) {
								if (oldPart.isEmpty())
									continue;
								String[] kv = oldPart.split("=", 2);
								usedAttrKeys.add(kv[0]);
							}
							StringBuilder sb = new StringBuilder(oldVal.isEmpty() ? "" : oldVal + " ");
							for (Map.Entry<String, String> newEntry : newAttr.entrySet()) {
								if (!usedSeparator.isEmpty() && usedAttrKeys.contains(newEntry.getKey())) {
									for (String separatorWord : usedSeparator.split(" ")) {
										sb.append(newEntry.getKey() + "=" + separatorWord + " ");
									}
								}
								String newValue = usedPattern.replace("${value}", newEntry.getValue());
								for (String valueWord : newValue.split(" ")) {
									sb.append(newEntry.getKey() + "=" + valueWord + " ");
								}
							}
							props.setProperty(key, sb.toString().trim());
						}
					} else {
						analyze(reference, separator, srcVals, "+", buildFullAttributes(true, srcVals, attributeKeys, attributeValues));
					}
				}

				@Override
				public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, strongsSuffixes, false);
					String[] idxStrings = buildFullSourceIndices(sourceVerses, sourceIndices);
					for (Mode mode : modes) {
						switch (mode) {
						case STRONGS2MORPH:
							analyze(reference, '*', fullStrongs, "", rmac);
							break;
						case INDEX2MORPH:
							analyze(reference, '@', idxStrings, "", rmac);
							break;
						case INDEX2STRONGS:
							analyze(reference, '@', idxStrings, "@", fullStrongs);
							break;
						case STRONGSLIST2INDEX:
							if (idxStrings != null && fullStrongs != null && idxStrings.length == fullStrongs.length) {
								for (int i = 0; i < idxStrings.length; i++) {
									String strongRefKey = getStrongRefKey(reference, fullStrongs[i], attributeKeys, attributeValues);
									if (strongRefKey == null || seenStrongRefs.add(strongRefKey)) {
										String key = reference.getBook().getOsisID() + "." + reference.getChapter() + "." + reference.getVerse() + '*' + fullStrongs[i] + "@L";
										strongsLists.computeIfAbsent(key, x -> new ArrayList<>()).add(idxStrings[i]);
									}
								}
							}
							break;
						case STRONGS2INDEX:
							analyze(reference, '*', fullStrongs, "@", idxStrings);
							break;
						case STRONGS2ATTR:
							analyzeAttr(reference, '*', fullStrongs, attributeKeys, attributeValues);
							break;
						case INDEX2ATTR:
							analyzeAttr(reference, '@', idxStrings, attributeKeys, attributeValues);
							break;
						case ABSOLUTEINDEX2MORPH:
							analyzeAbs(reference, '@', sourceVerses, sourceIndices, "", rmac);
							break;
						case ABSOLUTEINDEX2STRONGS:
							analyzeAbs(reference, '@', sourceVerses, sourceIndices, "@", fullStrongs);
							break;
						case ABSOLUTEINDEX2ATTR:
							if (normalSeparator != null && sourceIndices != null && attributeKeys != null) {
								for (int i = 0; i < sourceIndices.length; i++) {
									analyzeAttr(sourceVerses != null && sourceVerses[i] != null ? sourceVerses[i] : reference, '@', new String[] { String.valueOf(sourceIndices[i]) }, attributeKeys, attributeValues);
								}
							} else {
								analyzeAbs(reference, '@', sourceVerses, sourceIndices, "+", buildFullAttributes(true, idxStrings, attributeKeys, attributeValues));
							}
							break;
						}
					}
					return null;
				}
			});
			for (Entry<String, List<String>> entry : strongsLists.entrySet()) {
				if (entry.getValue().size() == 1)
					continue;
				String key = entry.getKey();
				String value = String.join(",", entry.getValue());
				String oldVal = props.getProperty(key);
				if (oldVal == null)
					props.setProperty(key, value);
				else if (!oldVal.equals(value))
					props.setProperty(key, "*");
			}
			try (FileOutputStream out = new FileOutputStream(exportArgs[1])) {
				props.store(out, "AugmentGrammar database");
			}
		} else if (exportArgs[0].equals("analyzestrongdic")) {
			Properties props = new Properties();
			if (new File(exportArgs[1]).exists()) {
				try (FileInputStream in = new FileInputStream(exportArgs[1])) {
					props.load(in);
				}
			}
			final Pattern keyPattern = Utils.compilePattern(exportArgs.length > 2 ? exportArgs[2] : ".*");
			Set<String> foundAttributes = new HashSet<>(), multiWordAttributes = new HashSet<>();
			for (final Book book : bible.getBooks()) {
				Map<String, ExtractTextVisitor> extractedAttributes = new HashMap<>();
				for (Chapter chapter : book.getChapters()) {
					chapter.getProlog().accept(new VisitorAdapter<RuntimeException>(null) {
						@Override
						protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
							return this;
						}

						public FormattedText.Visitor<RuntimeException> visitExtraAttribute(biblemulticonverter.data.FormattedText.ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
							if (category.equals("strongdic") && key.equals("attribute") && keyPattern.matcher(value).matches()) {
								ExtractTextVisitor ev = new ExtractTextVisitor();
								extractedAttributes.put(value, ev);
								return ev;
							} else {
								return this;
							}
						};
					});
				}
				StringBuilder attrVal = new StringBuilder();
				for (Map.Entry<String, ExtractTextVisitor> attr : extractedAttributes.entrySet()) {
					String key = "strong:" + attr.getKey().toLowerCase();
					String[] vals = attr.getValue().getExtractedText().split(" ");
					foundAttributes.add(key);
					if (vals.length > 1)
						multiWordAttributes.add(key);
					for (int i = 0; i < vals.length; i++) {
						attrVal.append(key + "=" + vals[i] + " ");
					}
				}
				props.setProperty("ANY*" + book.getAbbr() + "+", attrVal.toString().trim());
			}
			if (!foundAttributes.isEmpty()) {
				System.out.println("Found single-word attributes:");
				for (String attr : foundAttributes) {
					if (!multiWordAttributes.contains(attr)) {
						System.out.println("\t" + attr);
					}
				}
				System.out.println("Found multi-word attributes:");
				for (String attr : multiWordAttributes) {
					System.out.println("\t" + attr);
				}
			}
			try (FileOutputStream out = new FileOutputStream(exportArgs[1])) {
				props.store(out, "AugmentGrammar database");
			}
		} else if (exportArgs[0].equals("augment")) {
			Properties props = new Properties();
			try (FileInputStream in = new FileInputStream(exportArgs[1])) {
				props.load(in);
			}
			final Map<String, int[]> strongsCounters = new HashMap<>();
			GrammarOperation prepare = null;
			if (modes.contains(Mode.STRONGSLIST2INDEX)) {
				prepare = new GrammarOperation() {

					Set<String> seenStrongRefs = new HashSet<>();

					@Override
					public void reset() {
						strongsCounters.clear();
						seenStrongRefs.clear();
					}

					@Override
					public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
						String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, strongsSuffixes, false);
						if (fullStrongs != null) {
							for (String entry : fullStrongs) {
								String strongRefKey = getStrongRefKey(reference, entry, attributeKeys, attributeValues);
								if (strongRefKey == null || seenStrongRefs.add(strongRefKey)) {
									strongsCounters.computeIfAbsent(entry, x -> new int[2])[0]++;
								}
							}
						}
						return null;
					}
				};
			}
			runOperation(bible, prepare, true, new GrammarOperation() {

				private final String normalSeparator = System.getProperty("biblemulticonverter.concatattributes.separator");
				private final String firstSeparator = System.getProperty("biblemulticonverter.concatattributes.firstseparator", normalSeparator);
				private final String ellipsisSeparator = System.getProperty("biblemulticonverter.concatattributes.ellipsisseparator", normalSeparator);

				Map<String, Integer> seenStrongRefIndexValues = new HashMap<>();
				Map<String, Versification.Reference> seenStrongRefVerseValues = new HashMap<>();

				private void augmentAttr(String[][] attrKeyVals, Properties props, String keyPrefix, String separator, String[] srcVals, boolean[] useEllipsis) {
					if (attrKeyVals[0] == null || normalSeparator != null) {
						List<String[]> attrPairs = new ArrayList<>();
						Set<String> usedAttrKeys = new HashSet<>();
						if (attrKeyVals[0] != null) {
							for (int i = 0; i < attrKeyVals[0].length; i++) {
								attrPairs.add(new String[] { attrKeyVals[0][i], attrKeyVals[1][i] });
								usedAttrKeys.add(attrKeyVals[0][i]);
							}
						}
						for (int i = 0; i < srcVals.length; i++) {
							String attrString = props.getProperty(keyPrefix + separator + srcVals[i] + "+", props.getProperty("ANY" + separator + srcVals[i] + "+", "*"));
							String usedSeparator = i == 0 ? firstSeparator : useEllipsis[i] ? ellipsisSeparator : normalSeparator;
							if (!attrString.equals("*")) {
								Set<String> separatorNeededKeys = usedSeparator == null ? null : new HashSet<>(usedAttrKeys);
								for (String part : attrString.split(" ")) {
									if (part.isEmpty())
										continue;
									String[] kv = part.split("=", 2);
									if (usedSeparator != null && !usedSeparator.isEmpty() && separatorNeededKeys.remove(kv[0])) {
										for (String separatorWord : usedSeparator.split(" ")) {
											attrPairs.add(new String[] { kv[0], separatorWord });
										}
									}
									attrPairs.add(kv);
									usedAttrKeys.add(kv[0]);
								}
							} else if (usedSeparator == null) {
								attrPairs.clear();
								break;
							}
						}
						if (!attrPairs.isEmpty()) {
							attrKeyVals[0] = new String[attrPairs.size()];
							attrKeyVals[1] = new String[attrPairs.size()];
							for (int i = 0; i < attrPairs.size(); i++) {
								String[] kv = attrPairs.get(i);
								attrKeyVals[0][i] = kv[0];
								attrKeyVals[1][i] = kv[1];
							}
						}
					}
				}

				private void parseSourceIndexVerse(String value, int[] sourceIndices, Reference[] sourceVerses, int i) {
					if (value.contains("@")) {
						String[] parts = value.split("[ :@]");
						if (parts.length != 4)
							throw new IllegalArgumentException("Invalid source index value: " + value);
						sourceVerses[i] = new Versification.Reference(BookID.fromOsisId(parts[0]), Integer.parseInt(parts[1]), parts[2]);
						value = parts[3];
					}
					sourceIndices[i] = Integer.parseInt(value);
				}

				@Override
				public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, strongsSuffixes, false);
					String[] fullSourceIndices = buildFullSourceIndices(sourceVerses, sourceIndices);
					String keyPrefix = reference.getBook().getOsisID() + "." + reference.getChapter() + "." + reference.getVerse();
					for (Mode mode : modes) {
						switch (mode) {
						case INDEX2MORPH:
							if (sourceIndices != null && rmac == null) {
								rmac = new String[sourceIndices.length];
								for (int i = 0; i < sourceIndices.length; i++) {
									rmac[i] = props.getProperty(keyPrefix + "@" + fullSourceIndices[i], "*");
									if (rmac[i].equals("*")) {
										rmac = null;
										break;
									}
								}
							}
							break;
						case STRONGS2MORPH:
							if (fullStrongs != null && rmac == null) {
								rmac = new String[fullStrongs.length];
								for (int i = 0; i < fullStrongs.length; i++) {
									rmac[i] = props.getProperty(keyPrefix + "*" + fullStrongs[i], "*");
									if (rmac[i].equals("*")) {
										rmac = null;
										break;
									}
								}
							}
							break;
						case INDEX2STRONGS:
							if (sourceIndices != null && strongs == null) {
								strongsPrefixes = new char[sourceIndices.length];
								strongs = new int[sourceIndices.length];
								strongsSuffixes = new char[sourceIndices.length];
								for (int i = 0; i < sourceIndices.length; i++) {
									String value = props.getProperty(keyPrefix + "@" + fullSourceIndices[i] + "@", "*");
									if (value.equals("*")) {
										strongsPrefixes = null;
										strongs = null;
										strongsSuffixes = null;
										break;
									}
									strongsPrefixes[i] = value.charAt(0);
									if (value.matches("[A-Z][0-9]+[a-zA-Z]")) {
										strongs[i] = Integer.parseInt(value.substring(1, value.length() - 1));
										strongsSuffixes[i] = value.charAt(value.length() - 1);
									} else {
										strongs[i] = Integer.parseInt(value.substring(1));
										strongsSuffixes[i] = ' ';
									}
								}
								if (strongsSuffixes != null && new String(strongsSuffixes).trim().isEmpty()) {
									strongsSuffixes = null;
								}
							}
							break;
						case STRONGSLIST2INDEX:
							if (fullStrongs != null && sourceIndices == null) {
								sourceIndices = new int[fullStrongs.length];
								sourceVerses = new Versification.Reference[fullStrongs.length];
								for (int i = 0; i < fullStrongs.length; i++) {
									String strongRefKey = getStrongRefKey(reference, fullStrongs[i], attributeKeys, attributeValues);
									if (strongRefKey != null) {
										Integer value = seenStrongRefIndexValues.get(strongRefKey);
										if (value != null) {
											sourceIndices[i] = value;
											sourceVerses[i] = seenStrongRefVerseValues.get(strongRefKey);
											continue;
										}
									}
									String value = props.getProperty(keyPrefix + "*" + fullStrongs[i] + "@L", "*");
									String[] parts = value.equals("*") ? null : value.split(",");
									int[] cnt = strongsCounters.get(fullStrongs[i]);
									if (value.equals("*") || parts.length != cnt[0]) {
										sourceIndices = null;
										sourceVerses = null;
										break;
									}
									parseSourceIndexVerse(parts[cnt[1]], sourceIndices, sourceVerses, i);
									cnt[1]++;
									if (strongRefKey != null) {
										seenStrongRefIndexValues.put(strongRefKey, sourceIndices[i]);
										if (sourceVerses[i] != null) {
											seenStrongRefVerseValues.put(strongRefKey, sourceVerses[i]);
										}
									}
								}
								if (sourceVerses != null && Arrays.stream(sourceVerses).allMatch(r -> r == null)) {
									sourceVerses = null;
								}
							}
							break;
						case STRONGS2INDEX:
							if (fullStrongs != null && sourceIndices == null) {
								sourceIndices = new int[fullStrongs.length];
								sourceVerses = new Versification.Reference[fullStrongs.length];
								for (int i = 0; i < fullStrongs.length; i++) {
									String value = props.getProperty(keyPrefix + "*" + fullStrongs[i] + "@", "*");
									if (value.equals("*")) {
										sourceIndices = null;
										sourceVerses = null;
										break;
									}
									parseSourceIndexVerse(value, sourceIndices, sourceVerses, i);
								}
								if (sourceVerses != null && Arrays.stream(sourceVerses).allMatch(r -> r == null)) {
									sourceVerses = null;
								}
							}
							break;
						case INDEX2ATTR:
							if (sourceIndices != null) {
								boolean[] useEllipsis = new boolean[sourceIndices.length];
								for (int i = 0; i < sourceIndices.length; i++) {
									useEllipsis[i] = i > 0 && sourceIndices[i - 1] != sourceIndices[i] - 1;
									if (i > 0 && sourceVerses != null) {
										if (sourceVerses[i] == null ^ sourceVerses[i - 1] == null) {
											useEllipsis[i] = true;
										} else if (sourceVerses[i] != null && sourceVerses[i - i] != null && !sourceVerses[i].equals(sourceVerses[i - 1])) {
											useEllipsis[i] = true;
										}
									}
								}
								String[][] attrKeyVals = new String[][] { attributeKeys, attributeValues };
								augmentAttr(attrKeyVals, props, keyPrefix, "@", fullSourceIndices, useEllipsis);
								attributeKeys = attrKeyVals[0];
								attributeValues = attrKeyVals[1];
							}
							break;
						case STRONGS2ATTR:
							if (fullStrongs != null) {
								String[][] attrKeyVals = new String[][] { attributeKeys, attributeValues };
								augmentAttr(attrKeyVals, props, keyPrefix, "*", fullStrongs, new boolean[fullStrongs.length]);
								attributeKeys = attrKeyVals[0];
								attributeValues = attrKeyVals[1];
							}
							break;
						case ABSOLUTEINDEX2MORPH:
							if (sourceIndices != null && rmac == null) {
								rmac = new String[sourceIndices.length];
								for (int i = 0; i < sourceIndices.length; i++) {
									Versification.Reference absReference = sourceVerses != null && sourceVerses[i] != null ? sourceVerses[i] : reference;
									String absKeyPrefix = absReference.getBook().getOsisID() + "." + absReference.getChapter() + "." + absReference.getVerse();
									rmac[i] = props.getProperty(absKeyPrefix + "@" + sourceIndices[i], "*");
									if (rmac[i].equals("*")) {
										rmac = null;
										break;
									}
								}
							}
							break;
						case ABSOLUTEINDEX2STRONGS:
							if (sourceIndices != null && strongs == null) {
								strongsPrefixes = new char[sourceIndices.length];
								strongs = new int[sourceIndices.length];
								strongsSuffixes = new char[sourceIndices.length];
								for (int i = 0; i < sourceIndices.length; i++) {
									Versification.Reference absReference = sourceVerses != null && sourceVerses[i] != null ? sourceVerses[i] : reference;
									String absKeyPrefix = absReference.getBook().getOsisID() + "." + absReference.getChapter() + "." + absReference.getVerse();
									String value = props.getProperty(absKeyPrefix + "@" + sourceIndices[i] + "@", "*");
									if (value.equals("*")) {
										strongsPrefixes = null;
										strongs = null;
										strongsSuffixes = null;
										break;
									}
									strongsPrefixes[i] = value.charAt(0);
									if (value.matches("[A-Z][0-9]+[a-zA-Z]")) {
										strongs[i] = Integer.parseInt(value.substring(1, value.length() - 1));
										strongsSuffixes[i] = value.charAt(value.length() - 1);
									} else {
										strongs[i] = Integer.parseInt(value.substring(1));
										strongsSuffixes[i] = ' ';
									}
								}
								if (strongsSuffixes != null && new String(strongsSuffixes).trim().isEmpty()) {
									strongsSuffixes = null;
								}
							}
							break;
						case ABSOLUTEINDEX2ATTR:
							if (sourceIndices != null) {
								boolean[] useEllipsis = new boolean[sourceIndices.length];
								String[][] attrKeyVals = new String[][] { attributeKeys, attributeValues };
								for (int i = 0; i < sourceIndices.length; i++) {
									Versification.Reference absReference = sourceVerses != null && sourceVerses[i] != null ? sourceVerses[i] : reference;
									String absKeyPrefix = absReference.getBook().getOsisID() + "." + absReference.getChapter() + "." + absReference.getVerse();
									useEllipsis[i] = i > 0 && sourceIndices[i - 1] != sourceIndices[i] - 1;
									if (i > 0 && sourceVerses != null) {
										if (sourceVerses[i] == null ^ sourceVerses[i - 1] == null) {
											useEllipsis[i] = true;
										} else if (sourceVerses[i] != null && sourceVerses[i - i] != null && !sourceVerses[i].equals(sourceVerses[i - 1])) {
											useEllipsis[i] = true;
										}
									}
									augmentAttr(attrKeyVals, props, absKeyPrefix, "@", new String[] {String.valueOf(sourceIndices[i])}, new boolean[] {useEllipsis[i]});
								}
								attributeKeys = attrKeyVals[0];
								attributeValues = attrKeyVals[1];
							}
							break;
						}
					}
					return next.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceVerses, sourceIndices, attributeKeys, attributeValues);
				}
			});
			Module<ExportFormat> exportModule = Main.exportFormats.get(exportArgs[2]);
			ExportFormat exportFormat = exportModule.getImplementationClass().newInstance();
			exportFormat.doExport(bible, Arrays.copyOfRange(exportArgs, 3, exportArgs.length));

		} else if (exportArgs[0].equals("addsourceindex")) {
			runOperation(bible, null, true, new GrammarOperation() {
				private int counter = 0;
				private Reference lastReference = null;

				@Override
				public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					if (!reference.equals(lastReference)) {
						counter = 0;
						lastReference = reference;
					}
					counter++;
					if (sourceIndices != null)
						throw new RuntimeException(reference + " already has source index!");
					return next.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, null, new int[] { counter }, attributeKeys, attributeValues);
				}
			});
			Module<ExportFormat> exportModule = Main.exportFormats.get(exportArgs[1]);
			ExportFormat exportFormat = exportModule.getImplementationClass().newInstance();
			exportFormat.doExport(bible, Arrays.copyOfRange(exportArgs, 2, exportArgs.length));
		} else if (exportArgs[0].equals("addtextattr")) {
			runOperation(bible, null, true, new GrammarOperation() {
				int globalCounter = 0;

				@Override
				public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					globalCounter++;
					if (attributeKeys == null) {
						attributeKeys = new String[] { "text" };
						attributeValues = new String[] { "##" + globalCounter + "##" };
					} else {
						if (attributeKeys != null && Arrays.asList(attributeKeys).contains("text")) {
							throw new RuntimeException(reference + " already has text attribute!");
						}
						attributeKeys = Arrays.copyOf(attributeKeys, attributeKeys.length + 1);
						attributeKeys[attributeKeys.length - 1] = "text";
						attributeValues = Arrays.copyOf(attributeValues, attributeValues.length + 1);
						attributeValues[attributeValues.length - 1] = "##" + globalCounter + "##";
					}
					return next.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceVerses, sourceIndices, attributeKeys, attributeValues);
				}
			});
			Map<String, ExtractTextVisitor> extractedTexts = new HashMap<>();
			runOperation(bible, null, false, new GrammarOperation() {
				@Override
				public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					if (!attributeKeys[attributeKeys.length - 1].equals("text")) {
						throw new IllegalStateException();
					}
					String value = attributeValues[attributeValues.length - 1];
					ExtractTextVisitor ev = new ExtractTextVisitor();
					extractedTexts.put(value, ev);
					return ev;
				}
			});
			runOperation(bible, null, true, new GrammarOperation() {
				@Override
				public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					if (!attributeKeys[attributeKeys.length - 1].equals("text")) {
						throw new IllegalStateException();
					}
					String value = extractedTexts.get(attributeValues[attributeValues.length - 1]).getExtractedText().trim();
					String[] words = value.isEmpty() ? new String[0] : value.split(" ");
					attributeKeys = Arrays.copyOf(attributeKeys, attributeKeys.length - 1 + words.length);
					attributeValues = Arrays.copyOf(attributeValues, attributeValues.length - 1 + words.length);
					for (int i = 0; i < words.length; i++) {
						attributeKeys[attributeKeys.length - words.length + i] = "text";
						attributeValues[attributeValues.length - words.length + i] = words[i];
					}
					return next.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceVerses, sourceIndices, attributeKeys, attributeValues);
				}
			});
			Module<ExportFormat> exportModule = Main.exportFormats.get(exportArgs[1]);
			ExportFormat exportFormat = exportModule.getImplementationClass().newInstance();
			exportFormat.doExport(bible, Arrays.copyOfRange(exportArgs, 2, exportArgs.length));
		} else if (exportArgs[0].equals("addattrprefix")) {
			String prefix = exportArgs[1];
			runOperation(bible, null, true, new GrammarOperation() {
				private int counter = 0;
				private Reference lastReference = null;

				@Override
				public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					if (!reference.equals(lastReference)) {
						counter = 0;
						lastReference = reference;
					}
					counter++;
					List<String[]> newAttr = new ArrayList<>();
					newAttr.add(new String[] { prefix + ":idx", "" + counter });
					if (strongs != null) {
						for (String strong : buildFullStrongs(reference, strongsPrefixes, strongs, strongsSuffixes, false)) {
							newAttr.add(new String[] { prefix + ":strong", strong });
						}
					}
					if (rmac != null) {
						for (String morph : rmac) {
							newAttr.add(new String[] { prefix + ":morph", morph });
						}
					}
					String[] newAttributeKeys = new String[attributeKeys.length + newAttr.size()];
					String[] newAttributeValues = Arrays.copyOf(attributeValues, attributeValues.length + newAttr.size());
					for (int i = 0; i < attributeKeys.length; i++) {
						newAttributeKeys[i] = prefix + ":" + attributeKeys[i];
					}
					for (int i = 0; i < newAttr.size(); i++) {
						String[] kv = newAttr.get(i);
						newAttributeKeys[attributeKeys.length + i] = kv[0];
						newAttributeValues[attributeKeys.length + i] = kv[1];
					}
					return next.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceVerses, sourceIndices, newAttributeKeys, newAttributeValues);
				}
			});
			Module<ExportFormat> exportModule = Main.exportFormats.get(exportArgs[2]);
			ExportFormat exportFormat = exportModule.getImplementationClass().newInstance();
			exportFormat.doExport(bible, Arrays.copyOfRange(exportArgs, 3, exportArgs.length));
		} else {
			System.out.println("Supported operations: dump, dumpwords, analyze, analyzestrongdic, augment, addsourceindex, addtextattr, addattrprefix");
		}
	}

	private static String[] buildFullStrongs(Versification.Reference reference, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, boolean humanFormat) {
		if (strongs == null)
			return null;
		if (strongsPrefixes == null) {
			strongsPrefixes = new char[strongs.length];
			Arrays.fill(strongsPrefixes, reference.getBook().isNT() ? 'G' : 'H');
		}
		String[] result = new String[strongs.length];
		for (int i = 0; i < strongs.length; i++) {
			result[i] = humanFormat ? Utils.formatStrongs(reference.getBook().isNT(), strongsPrefixes[i], strongs[i], strongsSuffixes == null ? ' ' : strongsSuffixes[i], "") : strongsPrefixes[i] + "" + strongs[i] + "" + (strongsSuffixes == null || strongsSuffixes[i] == ' ' ? "" : "" + strongsSuffixes[i]);
		}
		return result;
	}

	private static String[] buildFullAttributes(boolean matchLength, String[] arrayToMatch, String[] attributeKeys, String[] attributeValues) {
		if (attributeKeys != null && (!matchLength || arrayToMatch != null)) {
			Map<String, List<String>> attributes = new HashMap<>();
			for (int i = 0; i < attributeKeys.length; i++) {
				List<String> values = attributes.computeIfAbsent(attributeKeys[i], x -> new ArrayList<>());
				values.add(attributeValues[i]);
			}
			List<String> keys = attributes.keySet().stream().sorted().filter(k -> !matchLength || attributes.get(k).size() == arrayToMatch.length).collect(Collectors.toList());
			int max = keys.stream().map(k -> attributes.get(k).size()).max(Comparator.naturalOrder()).orElse(0);
			if (max == 0)
				return null;
			String[] result = new String[max];
			for (int i = 0; i < result.length; i++) {
				StringBuilder sb = new StringBuilder();
				for (String key : keys) {
					List<String> vals = attributes.get(key);
					if (i < vals.size()) {
						sb.append(key).append("=").append(vals.get(i)).append(" ");
					}
				}
				result[i] = sb.toString().trim();
			}
			return result;
		}
		return null;
	}

	private static String[] buildFullSourceIndices(Versification.Reference[] sourceVerses, int[] sourceIndices) {
		if (sourceIndices == null)
			return null;
		String[] result = new String[sourceIndices.length];
		for (int i = 0; i < sourceIndices.length; i++) {
			result[i] = (sourceVerses != null && sourceVerses[i] != null ? sourceVerses[i].toString() + "@" : "") + sourceIndices[i];
		}
		return result;
	}

	private static String getStrongRefKey(Versification.Reference reference, String fullStrongs, String[] attributeKeys, String[] attributeValues) {
		String refKey = null;
		if (attributeKeys != null) {
			for (int i = 0; i < attributeKeys.length; i++) {
				if (attributeKeys[i].equals("strong:ref") && !attributeValues[i].isEmpty()) {
					refKey = fullStrongs + ":" + attributeValues[i] + "@" + reference.getBook().getOsisID() + "." + reference.getChapter() + "." + reference.getVerse();
					break;
				}
			}
		}
		return refKey;
	}

	protected <T extends Throwable> void runOperation(Bible bible, GrammarOperation prepare, boolean useResult, GrammarOperation operation) throws T {
		for (Book book : bible.getBooks()) {
			int cnum = 0;
			for (Chapter chapter : book.getChapters()) {
				cnum++;
				List<Verse> verses = chapter.getVerses();
				for (int i = 0; i < verses.size(); i++) {
					Verse v1 = verses.get(i);
					if (prepare != null) {
						prepare.reset();
						v1.accept(new GrammarOperationVisitor(null, new Versification.Reference(book.getId(), cnum, v1.getNumber()), prepare));
					}
					operation.reset();
					if (useResult) {
						Verse v2 = new Verse(v1.getNumber());
						v1.accept(new GrammarOperationVisitor(v2.getAppendVisitor(), new Versification.Reference(book.getId(), cnum, v1.getNumber()), operation));
						v2.finished();
						verses.set(i, v2);
					} else {
						v1.accept(new GrammarOperationVisitor(null, new Versification.Reference(book.getId(), cnum, v1.getNumber()), operation));
					}
				}
			}
		}
	}

	private static enum Mode {
		STRONGS2MORPH("S"), INDEX2MORPH("I"), STRONGS2INDEX("SI"), STRONGSLIST2INDEX("SLI"), INDEX2STRONGS("IS"), STRONGS2ATTR("SA"), INDEX2ATTR("IA"), ABSOLUTEINDEX2MORPH("AI"), ABSOLUTEINDEX2STRONGS("AIS"), ABSOLUTEINDEX2ATTR("AIA");

		private final String code;

		private Mode(String code) {
			this.code = code;
		}

		private static Mode fromCode(String code) {
			for (Mode mode : values()) {
				if (mode.code.equals(code))
					return mode;
			}
			throw new NoSuchElementException("Unknown mode: " + code);
		}
	}

	private static interface GrammarOperation {
		public default void reset() {
		}

		public abstract Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues);
	}

	private static class GrammarOperationVisitor extends FormattedText.VisitorAdapter<RuntimeException> {

		private final Versification.Reference reference;
		private final GrammarOperation operation;

		private GrammarOperationVisitor(Visitor<RuntimeException> next, Versification.Reference reference, GrammarOperation operation) throws RuntimeException {
			super(next);
			this.reference = reference;
			this.operation = operation;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return new GrammarOperationVisitor(childVisitor, reference, operation);
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws RuntimeException {
			return wrapChildVisitor(operation.handleGrammar(reference, getVisitor(), strongsPrefixes, strongs, strongsSuffixes, rmac, sourceVerses, sourceIndices, attributeKeys, attributeValues));
		}
	}

	private static class ExtractTextVisitor extends FormattedText.VisitorAdapter<RuntimeException> {

		StringBuilder sb = new StringBuilder();

		public ExtractTextVisitor() throws RuntimeException {
			super(null);
		}

		public String getExtractedText() {
			return sb.toString();
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitFootnote(boolean ofCrossReferences) throws RuntimeException {
			return null;
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws RuntimeException {
			visitText(" ");
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			visitText("/");
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			sb.append(text);
		}
	}
}
