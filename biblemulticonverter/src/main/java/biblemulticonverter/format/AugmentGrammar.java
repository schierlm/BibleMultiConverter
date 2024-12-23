package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.stream.Collectors;

import biblemulticonverter.Main;
import biblemulticonverter.ModuleRegistry.Module;
import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
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
			"Usage: AugmentGrammar augment[:<modes>] <dbfile> <ExportFormat> [<ExportArgs>...]",
			"Usage: AugmentGrammar addsourceindex <ExportFormat> [<ExportArgs>...]",
			"",
			"Supported modes (separate multiple modes by comma):",
			"   S:  Read Strongs and augment Morphology",
			"   I:  Read Indices and augment Morphology",
			"   SI: Read Strongs and augment Indices",
			"   IS: Read Indices and augment Strongs",
			"   SA:  Read Strongs and augment Attributes",
			"   IA:  Read Indices and augment Attributes",
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
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(exportArgs[1]))) {
				runOperation(bible, new GrammarOperation() {

					private int counter = 0;
					private Reference lastReference = null;

					@Override
					public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
						if (!reference.equals(lastReference)) {
							counter = 0;
							lastReference = reference;
						}
						counter++;
						String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, strongsSuffixes, humanStrongs);
						String[] fullAttr = buildFullAttributes(false, null, attributeKeys, attributeValues);
						int max = Math.max(Math.max(fullStrongs == null ? 0 : fullStrongs.length, rmac == null ? 0 : rmac.length), Math.max(sourceIndices == null ? 0 : sourceIndices.length, fullAttr == null ? 0 : fullAttr.length));
						try {
							for (int i = 0; i < max; i++) {
								bw.write(reference.toString() + "\t" + counter + "\t" + (i + 1) + "\t" + (fullStrongs != null && i < fullStrongs.length ? fullStrongs[i] : "") + "\t" + (rmac != null && i < rmac.length ? rmac[i] : "") + "\t" + (sourceIndices != null && i < sourceIndices.length ? sourceIndices[i] : "") + "\t" + (fullAttr != null && i < fullAttr.length ? fullAttr[i] : ""));
								bw.newLine();
							}
						} catch (IOException ex) {
							throw new RuntimeException(ex);
						}
						return next.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceIndices, attributeKeys, attributeValues);
					}
				});
			}
		} else if (exportArgs[0].equals("dumpwords")) {
			boolean humanStrongs = exportArgs.length > 2 && exportArgs[2].equals("humanStrongs");
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(exportArgs[1]))) {
				runOperation(bible, new GrammarOperation() {

					private int counter = 0;
					private Reference lastReference = null;

					@Override
					public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
						if (!reference.equals(lastReference)) {
							counter = 0;
							lastReference = reference;
						}
						counter++;
						String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, strongsSuffixes, humanStrongs);
						String[] fullAttr = buildFullAttributes(false, null, attributeKeys, attributeValues);
						final StringBuilder sb = new StringBuilder(reference.toString() + "\t" + counter + "\t" + (fullStrongs != null ? String.join("+", fullStrongs) : "") + "\t" + (rmac != null ? String.join("+", rmac) : "") + "\t" + (sourceIndices != null ? Arrays.toString(sourceIndices).replace(", ", "+").replace("]", "").substring(1) : "") + "\t" + (fullAttr != null ? String.join("+", fullAttr) : "") + "\t");
						return new VisitorAdapter<RuntimeException>(next.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceIndices, attributeKeys, attributeValues)) {
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
			runOperation(bible, new GrammarOperation() {

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

				@Override
				public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, strongsSuffixes, false);
					String[] idxStrings = null;
					if (sourceIndices != null) {
						idxStrings = new String[sourceIndices.length];
						for (int i = 0; i < sourceIndices.length; i++) {
							idxStrings[i] = String.valueOf(sourceIndices[i]);
						}
					}
					for (Mode mode : modes) {
						switch (mode) {
						case STRONGS2MORPH:
							analyze(reference, '*', fullStrongs, "", rmac);
							break;
						case INDEX2MORPH:
							analyze(reference, '@', idxStrings, "", rmac);
							break;
						case INDEX2STRONGS:
							analyze(reference, '*', fullStrongs, "@", idxStrings);
							break;
						case STRONGS2INDEX:
							analyze(reference, '@', idxStrings, "@", fullStrongs);
							break;
						case STRONGS2ATTR:
							analyze(reference, '*', fullStrongs, "+", buildFullAttributes(true, fullStrongs, attributeKeys, attributeValues));
							break;
						case INDEX2ATTR:
							analyze(reference, '@', idxStrings, "+", buildFullAttributes(true, idxStrings, attributeKeys, attributeValues));
							break;
						}
					}
					return next.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceIndices, attributeKeys, attributeValues);
				}
			});
			try (FileOutputStream out = new FileOutputStream(exportArgs[1])) {
				props.store(out, "AugmentGrammar database");
			}
		} else if (exportArgs[0].equals("augment")) {
			Properties props = new Properties();
			try (FileInputStream in = new FileInputStream(exportArgs[1])) {
				props.load(in);
			}
			runOperation(bible, new GrammarOperation() {
				@Override
				public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, strongsSuffixes, false);
					String keyPrefix = reference.getBook().getOsisID() + "." + reference.getChapter() + "." + reference.getVerse();
					for (Mode mode : modes) {
						switch (mode) {
						case INDEX2MORPH:
							if (sourceIndices != null && rmac == null) {
								rmac = new String[sourceIndices.length];
								for (int i = 0; i < sourceIndices.length; i++) {
									rmac[i] = props.getProperty(keyPrefix + "@" + sourceIndices[i], "*");
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
									String value = props.getProperty(keyPrefix + "@" + sourceIndices[i] + "@", "*");
									if (value.equals("*")) {
										strongsPrefixes = null;
										strongs = null;
										strongsSuffixes = null;
										break;
									}
									strongsPrefixes[i] = value.charAt(0);
									if (value.matches("[A-Z][0-9]+[a-zA-Z]")) {
										strongs[i] = Integer.parseInt(value.substring(1, value.length()-1));
										strongsSuffixes[i] = value.charAt(value.length()-1);
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
						case STRONGS2INDEX:
							if (fullStrongs != null && sourceIndices == null) {
								sourceIndices = new int[fullStrongs.length];
								for (int i = 0; i < fullStrongs.length; i++) {
									String value = props.getProperty(keyPrefix + "*" + fullStrongs[i] + "@", "*");
									if (rmac[i].equals("*")) {
										sourceIndices = null;
										break;
									}
									sourceIndices[i] = Integer.parseInt(value);
								}
							}
							break;
						case INDEX2ATTR:
							if (sourceIndices != null && attributeKeys == null) {
								String[] attrPairs = new String[sourceIndices.length];
								for (int i = 0; i < sourceIndices.length; i++) {
									attrPairs[i] = props.getProperty(keyPrefix + "@" + sourceIndices[i] + "+", "*");
									if (attrPairs[i].equals("*")) {
										attrPairs = null;
										break;
									}
								}
								if (attrPairs != null) {
									attributeKeys = new String[parseAttrPairs(attrPairs, null, null)];
									attributeValues = new String[attributeKeys.length];
									parseAttrPairs(attrPairs, attributeKeys, attributeValues);
								}
							}
							break;
						case STRONGS2ATTR:
							if (fullStrongs != null && attributeKeys == null) {
								String[] attrPairs = new String[fullStrongs.length];
								for (int i = 0; i < fullStrongs.length; i++) {
									attrPairs[i] = props.getProperty(keyPrefix + "*" + fullStrongs[i] + "+", "*");
									if (attrPairs[i].equals("*")) {
										attrPairs = null;
										break;
									}
								}
								if (attrPairs != null) {
									attributeKeys = new String[parseAttrPairs(attrPairs, null, null)];
									attributeValues = new String[attributeKeys.length];
									parseAttrPairs(attrPairs, attributeKeys, attributeValues);
								}
							}
							break;
						}
					}
					return next.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceIndices, attributeKeys, attributeValues);
				}
			});
			Module<ExportFormat> exportModule = Main.exportFormats.get(exportArgs[2]);
			ExportFormat exportFormat = exportModule.getImplementationClass().newInstance();
			exportFormat.doExport(bible, Arrays.copyOfRange(exportArgs, 3, exportArgs.length));

		} else if (exportArgs[0].equals("addsourceindex")) {
			runOperation(bible, new GrammarOperation() {
				private int counter = 0;
				private Reference lastReference = null;

				@Override
				public Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					if (!reference.equals(lastReference)) {
						counter = 0;
						lastReference = reference;
					}
					counter++;
					if (sourceIndices != null)
						throw new RuntimeException(reference + " already has source index!");
					return next.visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, new int[] { counter }, attributeKeys, attributeValues);
				}
			});
			Module<ExportFormat> exportModule = Main.exportFormats.get(exportArgs[1]);
			ExportFormat exportFormat = exportModule.getImplementationClass().newInstance();
			exportFormat.doExport(bible, Arrays.copyOfRange(exportArgs, 2, exportArgs.length));
		} else {
			System.out.println("Supported operations: dump, dumpwords, analyze, augment, addsourceindex");
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
	private static int parseAttrPairs(String[] attrPairs, String[] attributeKeys, String[] attributeValues) {
		int count = 0;
		for(String attrPair : attrPairs) {
			for(String part: attrPair.split(" ")) {
				if (part.isEmpty())
					continue;
				String[] kv = part.split("=", 2);
				if (attributeKeys != null) {
					attributeKeys[count] = kv[0];
					attributeKeys[count] = kv[1];
				}
				count++;
			}
		}
		return count;
	}

	protected <T extends Throwable> void runOperation(Bible bible, GrammarOperation operation) throws T {
		for (Book book : bible.getBooks()) {
			int cnum = 0;
			for (Chapter chapter : book.getChapters()) {
				cnum++;
				List<Verse> verses = chapter.getVerses();
				for (int i = 0; i < verses.size(); i++) {
					Verse v1 = verses.get(i);
					Verse v2 = new Verse(v1.getNumber());
					v1.accept(new GrammarOperationVisitor(v2.getAppendVisitor(), new Versification.Reference(book.getId(), cnum, v1.getNumber()), operation));
					v2.finished();
					verses.set(i, v2);
				}
			}
		}
	}

	private static enum Mode {
		STRONGS2MORPH("S"), INDEX2MORPH("I"), STRONGS2INDEX("SI"), INDEX2STRONGS("IS"), STRONGS2ATTR("SA"), INDEX2ATTR("IA");

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
		public abstract Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues);
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
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws RuntimeException {
			return wrapChildVisitor(operation.handleGrammar(reference, getVisitor(), strongsPrefixes, strongs, strongsSuffixes, rmac, sourceIndices, attributeKeys, attributeValues));
		}
	}
}
