package biblemulticonverter.logos.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.Main;
import biblemulticonverter.ModuleRegistry.Module;
import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification;
import biblemulticonverter.format.ExportFormat;

public class ReplaceStrongs implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Replace Strongs numbers by pattern or from other Logos links (e.g. GreekGK).",
			"",
			"Usage: ReplaceStrongs <pattern>=<replacement> [...] -- <ExportFormat> [<ExportArgs>...]",
			"",
			"Patterns can be either Strongs numbers or (full) Logos links, replacements are Strongs numbers.",
			"",
			"Placeholders in patterns are $ (prefix letter), # (number), ! (optional suffix letter).",
			"For example, $#!=$#! will keep all Strongs numbers, while G#=Q# will change e.g. G123 to Q123."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		int formatArg = 0;
		LogosLinksGenerator linksGenerator = new LogosLinksGenerator();
		List<PatternRule> rules = new ArrayList<>();
		while (!exportArgs[formatArg].equals("--")) {
			rules.add(new PatternRule(exportArgs[formatArg]));
			formatArg++;
		}
		formatArg++;

		for (Book book : bible.getBooks()) {
			int cnum = 0;
			for (Chapter chapter : book.getChapters()) {
				cnum++;
				List<Verse> verses = chapter.getVerses();
				for (int i = 0; i < verses.size(); i++) {
					Verse v1 = verses.get(i);
					Verse v2 = new Verse(v1.getNumber());
					v1.accept(new ReplaceStrongsVisitor(v2.getAppendVisitor(), new Versification.Reference(book.getId(), cnum, v1.getNumber()), linksGenerator, rules));
					v2.finished();
					verses.set(i, v2);
				}
			}
		}

		Module<ExportFormat> exportModule = Main.exportFormats.get(exportArgs[formatArg]);
		ExportFormat exportFormat = exportModule.getImplementationClass().newInstance();
		exportFormat.doExport(bible, Arrays.copyOfRange(exportArgs, formatArg + 1, exportArgs.length));
	}

	private static class PatternRule {
		final Pattern pattern;
		final String replacement;

		public PatternRule(String rule) {
			String[] parts = rule.split("=");
			if (parts.length != 2)
				throw new RuntimeException("Invalid pattern rule: " + rule);
			StringBuilder ptn = new StringBuilder(), rpl = new StringBuilder();
			for (String placeholder : Arrays.asList("$", "#", "!")) {
				if (parts[1].contains(placeholder) && !parts[0].contains(placeholder)) {
					throw new RuntimeException("Invalid pattern rule - cannot replace " + placeholder + " as not contained in pattern: " + rule);
				}
			}
			pattern = Pattern.compile("^" + replacePlaceholders(parts[0], Pattern::quote, "(?<P>[A-Z])", "(?<N>[0-9]+)", "(?<S>[a-zA-Z]?)") + "$");
			replacement = replacePlaceholders(parts[1], Matcher::quoteReplacement, "${P}", "${N}", "${S}");
		}

		private static String replacePlaceholders(String pattern, Function<String, String> quoteFunction, String replacement4Prefix, String replacement4Number, String replacement4Suffix) {
			StringBuilder result = new StringBuilder();
			Matcher m = Utils.compilePattern("[$#!]").matcher(pattern);
			int lastPos = 0;
			while (m.find()) {
				result.append(quoteFunction.apply(pattern.substring(lastPos, m.start())));
				switch (m.group().charAt(0)) {
				case '$':
					result.append(replacement4Prefix);
					break;
				case '#':
					result.append(replacement4Number);
					break;
				case '!':
					result.append(replacement4Suffix);
					break;
				default:
					throw new IllegalStateException(m.group());
				}
				lastPos = m.end();
			}
			result.append(quoteFunction.apply(pattern.substring(lastPos)));
			return result.toString();
		}
	}

	private static class ReplaceStrongsVisitor extends FormattedText.VisitorAdapter<RuntimeException> {

		private final Versification.Reference reference;
		private final LogosLinksGenerator linksGenerator;
		private final List<PatternRule> rules;

		private ReplaceStrongsVisitor(Visitor<RuntimeException> next, Versification.Reference reference, LogosLinksGenerator linksGenerator, List<PatternRule> rules) throws RuntimeException {
			super(next);
			this.reference = reference;
			this.linksGenerator = linksGenerator;
			this.rules = rules;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return new ReplaceStrongsVisitor(childVisitor, reference, linksGenerator, rules);
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
			if (strongs != null) {
				List<String> newStrongs = new ArrayList<>();
				char[] oneStrongsPrefix = new char[1];
				int[] oneStrongs = new int[1];
				char[] oneStrongsSuffix = new char[1];
				for (int i = 0; i < strongs.length; i++) {
					oneStrongs[0] = strongs[i];
					oneStrongsPrefix[0] = strongsPrefixes == null ? '?' : strongsPrefixes[i];
					oneStrongsSuffix[0] = strongsSuffixes == null ? '?' : strongsSuffixes[i];
					List<String> links = linksGenerator.generateLinks(reference.getBook().isNT(), reference, strongsPrefixes == null ? null : oneStrongsPrefix, oneStrongs, strongsSuffixes == null ? null : oneStrongsSuffix, rmac, sourceVerses, sourceIndices, attributeKeys, attributeValues);
					links.add(Utils.formatStrongs(reference.getBook().isNT(), strongsPrefixes == null ? '\0' : strongsPrefixes[i], strongs[i], strongsSuffixes == null ? ' ' : strongsSuffixes[i], ""));
					outer: for (PatternRule rule : rules) {
						for (String link : links) {
							Matcher m = rule.pattern.matcher(link);
							if (m.matches()) {
								m.reset();
								String newStrong = m.replaceFirst(rule.replacement);
								if (newStrong.matches("[A-Z]?0*[1-9][0-9]*[a-zA-Z]?")) {
									newStrongs.add(newStrong);
								} else {
									System.out.println("WARNING: Skipping malformed replaced Strong number: " + newStrong);
								}
								break outer;
							}
						}
					}
				}

				if (newStrongs.isEmpty()) {
					strongs = null;
					strongsPrefixes = null;
					strongsSuffixes = null;
				} else {
					strongs = new int[newStrongs.size()];
					strongsPrefixes = new char[newStrongs.size()];
					strongsSuffixes = new char[newStrongs.size()];
					char[] prefixSuffixHolder = new char[2];
					for (int i = 0; i < strongs.length; i++) {
						String newStrong = newStrongs.get(i);
						if (newStrong.matches("[A-Z]-*")) {
							strongs[i] = Utils.parseStrongs(newStrong, '\0', prefixSuffixHolder);
							strongsPrefixes[i] = prefixSuffixHolder[0];
						} else {
							strongs[i] = Utils.parseStrongs(newStrong, 'X', prefixSuffixHolder);
							strongsPrefixes[i] = ' ';
						}
						strongsSuffixes[i] = prefixSuffixHolder[1];
					}
					if (new String(strongsPrefixes).trim().isEmpty()) {
						strongsPrefixes = null;
					} else {
						for (int i = 0; i < strongsPrefixes.length; i++) {
							if (strongsPrefixes[i] == ' ') {
								strongsPrefixes[i] = reference.getBook().isNT() ? 'G' : 'H';
							}
						}
					}
					if (new String(strongsSuffixes).trim().isEmpty()) {
						strongsSuffixes = null;
					}
				}
			}
			if (strongs == null && rmac == null && sourceIndices == null && attributeKeys == null) {
				return this;
			}
			return wrapChildVisitor(getVisitor().visitGrammarInformation(strongsPrefixes, strongs, strongsSuffixes, rmac, sourceVerses, sourceIndices, attributeKeys, attributeValues));
		}
	}
}
