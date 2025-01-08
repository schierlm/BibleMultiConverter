package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;

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
				runOperation(bible, new GrammarOperation() {

					private int counter = 0;
					private Reference lastReference = null;

					@Override
					public Visitor<RuntimeException> handleGrammar(Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
						if (!reference.equals(lastReference)) {
							counter = 0;
							lastReference = reference;
						}
						counter++;
						String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, humanStrongs);
						int max = Math.max(Math.max(fullStrongs == null ? 0 : fullStrongs.length, rmac == null ? 0 : rmac.length), sourceIndices == null ? 0 : sourceIndices.length);
						try {
							for (int i = 0; i < max; i++) {
								bw.write(reference.toString() + "\t" + counter + "\t" + (i + 1) + "\t" + (fullStrongs != null && i < fullStrongs.length ? fullStrongs[i] : "") + "\t" + (rmac != null && i < rmac.length ? rmac[i] : "") + "\t" + (sourceIndices != null && i < sourceIndices.length ? sourceIndices[i] : ""));
								bw.newLine();
							}
						} catch (IOException ex) {
							throw new RuntimeException(ex);
						}
						return next.visitGrammarInformation(strongsPrefixes, strongs, rmac, sourceIndices);
					}
				});
			}
		} else if (exportArgs[0].equals("dumpwords")) {
			boolean humanStrongs = exportArgs.length > 2 && exportArgs[2].equals("humanStrongs");
			try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[1]), StandardCharsets.UTF_8))) {
				runOperation(bible, new GrammarOperation() {

					private int counter = 0;
					private Reference lastReference = null;

					@Override
					public Visitor<RuntimeException> handleGrammar(Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
						if (!reference.equals(lastReference)) {
							counter = 0;
							lastReference = reference;
						}
						counter++;
						String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, humanStrongs);
						final StringBuilder sb = new StringBuilder(reference.toString() + "\t" + counter + "\t" + (fullStrongs != null ? String.join("+", fullStrongs) : "") + "\t" + (rmac != null ? String.join("+", rmac) : "") + "\t" + (sourceIndices != null ? Arrays.toString(sourceIndices).replace(", ", "+").replace("]", "").substring(1) : "") + "\t");
						return new VisitorAdapter<RuntimeException>(next.visitGrammarInformation(strongsPrefixes, strongs, rmac, sourceIndices)) {
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
				public Visitor<RuntimeException> handleGrammar(Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
					String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, false);
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
						}
					}
					return next.visitGrammarInformation(strongsPrefixes, strongs, rmac, sourceIndices);
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
				public Visitor<RuntimeException> handleGrammar(Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
					String[] fullStrongs = buildFullStrongs(reference, strongsPrefixes, strongs, false);
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
								for (int i = 0; i < sourceIndices.length; i++) {
									String value = props.getProperty(keyPrefix + "@" + sourceIndices[i] + "@", "*");
									if (value.equals("*")) {
										strongsPrefixes = null;
										strongs = null;
										break;
									}
									strongsPrefixes[i] = value.charAt(0);
									strongs[i] = Integer.parseInt(value.substring(1));
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
						}
					}
					return next.visitGrammarInformation(strongsPrefixes, strongs, rmac, sourceIndices);
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
				public Visitor<RuntimeException> handleGrammar(Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
					if (!reference.equals(lastReference)) {
						counter = 0;
						lastReference = reference;
					}
					counter++;
					if (sourceIndices != null)
						throw new RuntimeException(reference + " already has source index!");
					return next.visitGrammarInformation(strongsPrefixes, strongs, rmac, new int[] { counter });
				}
			});
			Module<ExportFormat> exportModule = Main.exportFormats.get(exportArgs[1]);
			ExportFormat exportFormat = exportModule.getImplementationClass().newInstance();
			exportFormat.doExport(bible, Arrays.copyOfRange(exportArgs, 2, exportArgs.length));
		} else {
			System.out.println("Supported operations: dump, dumpwords, analyze, augment, addsourceindex");
		}
	}

	private static String[] buildFullStrongs(Versification.Reference reference, char[] strongsPrefixes, int[] strongs, boolean humanFormat) {
		if (strongs == null)
			return null;
		if (strongsPrefixes == null) {
			strongsPrefixes = new char[strongs.length];
			Arrays.fill(strongsPrefixes, reference.getBook().isNT() ? 'G' : 'H');
		}
		String[] result = new String[strongs.length];
		for (int i = 0; i < strongs.length; i++) {
			result[i] = humanFormat ? Utils.formatStrongs(reference.getBook().isNT(), strongsPrefixes[i], strongs[i]): strongsPrefixes[i] + "" + strongs[i];
		}
		return result;
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
		STRONGS2MORPH("S"), INDEX2MORPH("I"), STRONGS2INDEX("SI"), INDEX2STRONGS("IS");

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
		public abstract Visitor<RuntimeException> handleGrammar(Versification.Reference reference, Visitor<RuntimeException> next, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices);
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
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			return wrapChildVisitor(operation.handleGrammar(reference, getVisitor(), strongsPrefixes, strongs, rmac, sourceIndices));
		}
	}
}
