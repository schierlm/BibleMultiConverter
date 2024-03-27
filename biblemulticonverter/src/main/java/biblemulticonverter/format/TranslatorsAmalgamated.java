package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;

public class TranslatorsAmalgamated implements ImportFormat {

	public static final String[] HELP_TEXT = {
			"Importer for Translators Amalgamated Hebrew OT / Greek NT",
			"",
			"Usage: TranslatorsAmalgamated <directory>",
			"",
			"Download Translators Amalgamated Bibles <https://github.com/STEPBible/STEPBible-Data/>.",
			"Note that this importer does not yet import all information from TAHOT/TAGNT."
	};

	private static String[] FILE_NAMES_OT = {
			"TAHOT Gen-Deu - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
			"TAHOT Jos-Est - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
			"TAHOT Job-Sng - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
			"TAHOT Isa-Mal - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
	};

	private static String[] FILE_NAMES_NT = {
			"TAGNT Mat-Jhn - Translators Amalgamated Greek NT - STEPBible.org CC-BY.txt",
			"TAGNT Act-Rev - Translators Amalgamated Greek NT - STEPBible.org CC-BY.txt",
	};

	@Override
	public Bible doImport(File directory) throws Exception {
		Bible bible = new Bible("Translators Amalgamated Hebrew OT / Greek NT");
		Map<String, Book> startedBooks = new HashMap<>();
		for (String fileName : FILE_NAMES_OT) {
			File file = new File(directory, fileName);
			Verse currVerse = null;
			String currVersePrefix = null;
			Visitor<RuntimeException> currVerseVisitor = null;
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.startsWith("#"))
						break;
				}
				while ((line = br.readLine()) != null) {
					if (line.trim().isEmpty() || line.startsWith("#"))
						continue;
					String[] fields = line.split("\t", -1);
					if (fields.length < 12) {
						System.out.println(line);
						throw new RuntimeException("Invalid field count: " + fields.length);
					}
					if (fields[0].equals("Eng (Heb) Ref & Type")) {
						if (currVerse != null)
							currVerse.finished();
						currVerse = null;
						currVersePrefix = null;
						currVerseVisitor = null;
						continue;
					}
					// 0: Eng (Heb) Ref & Type [type and alternate versification
					// not yet done]
					// 1: Hebrew
					// 2: Transliteration
					// 3: Translation
					// 4: dStrongs
					// 5: Grammar
					// 6: Meaning Variants [not yet done]
					// 7: Spelling Variants [not yet done]
					// 8: Root dStrong+Instance [not yet done]
					// 9: Alternative Strongs+Instance [not yet done]
					// 10: Conjoin word [not yet done]
					// 11: Expanded Strong tags [not yet done]
					try {
						fields[0] = fields[0].replaceFirst("\\(.*\\)#", "#");
						if (currVersePrefix == null) {
							currVersePrefix = fields[0].split("#")[0];
							String[] parts = currVersePrefix.replaceFirst("\\(.*\\)$", "").split("\\.");
							if (parts.length != 3)
								throw new RuntimeException("Invalid verse prefix " + currVersePrefix);
							Book book = startedBooks.get(parts[0]);
							if (book == null) {
								BookID bid = ParatextID.fromIdentifier(parts[0].toUpperCase()).getId();
								book = new Book(parts[0], bid, bid.getEnglishName(), bid.getEnglishName());
								startedBooks.put(parts[0], book);
								bible.getBooks().add(book);
							}
							int chapter = Integer.parseInt(parts[1]);
							while (book.getChapters().size() < chapter)
								book.getChapters().add(new Chapter());
							String verseNum = parts[2];
							if (book.getId() == BookID.BOOK_Ps && verseNum.equals("0"))
								verseNum = "1/t";
							currVerse = new Verse(verseNum);
							book.getChapters().get(chapter - 1).getVerses().add(currVerse);
							currVerseVisitor = currVerse.getAppendVisitor();
						} else {
							currVerseVisitor.visitText(" ");
						}
						if (!fields[0].startsWith(currVersePrefix)) {
							throw new RuntimeException(fields[0] + " inside " + currVersePrefix);
						}
						Visitor<RuntimeException> v = currVerseVisitor;
						if (fields[4].isEmpty() && fields[5].isEmpty()) {
							System.out.println("WARNING: Word without strongs/morph in " + currVersePrefix);
						} else {
							String[] strongs = fields[4].replaceAll("[{}/\\\\+]+", " ").replaceAll("  +", " ").trim().split(" ");
							int[] strongNum = new int[strongs.length];
							char[] strongPfx = new char[strongs.length];
							for (int i = 0; i < strongs.length; i++) {
								char[] prefixHolder = new char[1];
								int number = Utils.parseStrongs(strongs[i], 'H', prefixHolder);
								if (number == -1 && strongs[i].matches(".*[a-zA-Z]")) {
									System.out.println("WARNING: Stripping suffix of Strongs " + strongs[i]);
									number = Utils.parseStrongs(strongs[i].substring(0, strongs[i].length() - 1), 'H', prefixHolder);
								}
								if (number == -1) {
									throw new RuntimeException("Invalid Strongs number: " + strongs[i]);
								}
								strongPfx[i] = prefixHolder[0];
								strongNum[i] = number;
							}
							String[] wivu = new String[] { fields[5] };
							if (!wivu[0].matches(Utils.WIVU_REGEX)) {
								if (wivu[0].startsWith("Hc/")) {
									wivu[0] = "HC/" + wivu[0].substring(3);
								}
							}
							if (!wivu[0].matches(Utils.WIVU_REGEX)) {
								System.out.println("WARNING: Skipping WIVU: " + wivu[0]);
								wivu = null;
							}
							v = v.visitGrammarInformation(strongPfx, strongNum, wivu, null);
							v.visitExtraAttribute(ExtraAttributePriority.SKIP, "tahot", "english", "transliteration").visitText(fields[2]);
							v.visitExtraAttribute(ExtraAttributePriority.SKIP, "tahot", "english", "translation").visitText(fields[3].replaceAll("  +", " "));
						}
						v.visitText(fields[1]);
						v.visitEnd();
					} catch (Exception ex) {
						throw new RuntimeException("While parsing " + currVersePrefix, ex);
					}
				}
				currVerse.finished();
			}
		}
		for (String fileName : FILE_NAMES_NT) {
			File file = new File(directory, fileName);
			Verse currVerse = null;
			String currVersePrefix = null;
			List<WordItem> items = new ArrayList<>();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.startsWith("#"))
						break;
				}
				while ((line = br.readLine()) != null) {
					if (line.trim().isEmpty() || line.startsWith("#"))
						continue;
					String[] fields = line.split("\t", -1);
					if (fields.length < 13)
						throw new RuntimeException("Invalid field count: " + fields.length);
					if (fields[0].equals("Word & Type")) {
						try {
							if (currVerse != null)
								writeGreek(currVerse, items, currVersePrefix);
						} catch (Exception ex) {
							throw new RuntimeException("While writing " + currVersePrefix, ex);
						}
						currVerse = null;
						currVersePrefix = null;
						items.clear();
						continue;
					}
					// 0: Word & Type [type and alternate versification not yet
					// done]
					// 1: Greek
					// 2: English translation
					// 3: dStrongs = Grammar
					// 4: Dictionary form = Gloss
					// 5: editions
					// 6: Meaning variants [not yet done]
					// 7: Spelling variants [not yet done]
					// 8: Spanish translation [not yet done]
					// 9: Sub-meaning [not yet done]
					// 10: Conjoin word [not yet done]
					// 11: sStrong+Instance [not yet done]
					// 12: Alt Strongs [not yet done]

					fields[0] = fields[0].replaceFirst("[({\\[][A-Za-z0-9.]+[)}\\]]#", "#");
					if (currVersePrefix == null) {
						currVersePrefix = fields[0].split("#")[0];
						String[] parts = currVersePrefix.split("\\.");
						if (parts.length != 3)
							throw new RuntimeException("Invalid verse prefix " + currVersePrefix);
						Book book = startedBooks.get(parts[0]);
						if (book == null) {
							BookID bid = ParatextID.fromIdentifier(parts[0].toUpperCase()).getId();
							book = new Book(parts[0], bid, bid.getEnglishName(), bid.getEnglishName());
							startedBooks.put(parts[0], book);
							bible.getBooks().add(book);
						}
						int chapter = Integer.parseInt(parts[1]);
						while (book.getChapters().size() < chapter)
							book.getChapters().add(new Chapter());
						currVerse = new Verse(parts[2]);
						book.getChapters().get(chapter - 1).getVerses().add(currVerse);
					}
					if (!fields[0].startsWith(currVersePrefix)) {
						throw new RuntimeException(fields[0] + " inside " + currVersePrefix);
					}
					try {
						fields[0] = fields[0].replaceFirst("\\([A-Za-z0-9.]+\\)#", "#");
						String[] strongMorphCombined = fields[3].split(" \\+ ");
						String[] strong = new String[strongMorphCombined.length];
						String[] morph = new String[strongMorphCombined.length];
						for (int i = 0; i < strongMorphCombined.length; i++) {
							String[] parts = strongMorphCombined[i].split("=");
							strong[i] = parts[0];
							morph[i] = parts[1];
							if (morph[i].equals("ADV-T"))
								morph[i] = "ADV";
							if (morph[i].startsWith("B-"))
								morph[i] = "F-" + morph[i].substring(2);
						}

						String[] lemmaGloss = fields[4].split("=");
						WordItem wi = new WordItem();
						wi.greek = fields[1].replaceAll("  +", " ");
						wi.attributes.add(new String[] { "osisgrammar", "lemma", "lemma", lemmaGloss[0] });
						wi.attributes.add(new String[] { "tahot", "english", "translation", fields[2] });
						wi.attributes.add(new String[] { "tahot", "english", "gloss", lemmaGloss[1] });
						wi.strongs = strong;
						wi.rmac = morph;

						String[] editions = fields[5].split("\\+");
						wi.editions = EnumSet.noneOf(Edition.class);
						for (String edition : editions) {
							edition = edition.replace("«", "»-");
							if (edition.contains("»")) {
								String[] parts = edition.split("»");
								Edition edi = getEdition(parts[0]);
								WordItem wii = new WordItem(wi, edi);
								wii.shift = parts[1].contains(".") ? 0 : Integer.parseInt(parts[1]);
								int index = items.size();
								while (wii.shift < 0 && index > 0) {
									if (items.get(index - 1).editions.contains(edi)) {
										wii.shift++;
									}
									index--;
								}
								if (wii.shift < 0) {
									System.out.println("WARNING: Shifted word left out of verse: " + currVersePrefix);
									wii.shift = 0;
								}
								items.add(index, wii);
							} else {
								wi.editions.add(getEdition(edition));
							}
						}
						if (!wi.editions.isEmpty())
							items.add(wi);
					} catch (Exception ex) {
						throw new RuntimeException("While parsing " + currVersePrefix, ex);
					}
				}
				writeGreek(currVerse, items, currVersePrefix);
			}
		}
		return bible;
	}

	private Edition getEdition(String name) {
		if (name.matches(".*\\.[0-9]+:")) {
			name = name.substring(0, name.lastIndexOf("."));
		}
		return Edition.OtherEdition.contains(name) ? Edition.Other : Edition.valueOf(name);
	}

	private void writeGreek(Verse verse, List<WordItem> items, String currVersePrefix) {
		Visitor<RuntimeException> vv = verse.getAppendVisitor();
		boolean first = true;
		for (int i = 0; i < items.size(); i++) {
			WordItem wi = items.get(i);
			if (wi.shift > 0) {
				int j = i;
				while (wi.shift > 0 && j < items.size() - 1) {
					WordItem next = items.get(j + 1);
					items.set(j, next);
					j++;
					if (next.editions.containsAll(wi.editions))
						wi.shift--;
				}
				if (wi.shift > 0) {
					System.out.println("WARNING: Shifted word right out of verse: " + currVersePrefix);
					wi.shift = 0;
				}
				items.set(j, wi);
				i--;
			}
		}
		for (WordItem wi : items) {
			if (!first)
				vv.visitText(" ");
			first = false;
			int[] strongNum = new int[wi.strongs.length];
			char[] strongPfx = new char[wi.strongs.length];
			for (int i = 0; i < wi.strongs.length; i++) {
				char[] prefixHolder = new char[1];
				int number = Utils.parseStrongs(wi.strongs[i], 'G', prefixHolder);
				if (number == -1 && wi.strongs[i].matches(".*[a-zA-Z]")) {
					System.out.println("WARNING: Stripping suffix of Strongs " + wi.strongs[i]);
					number = Utils.parseStrongs(wi.strongs[i].substring(0, wi.strongs[i].length() - 1), 'G', prefixHolder);
				}
				if (number == -1) {
					throw new RuntimeException("Invalid Strongs number: " + wi.strongs[i]);
				}
				strongPfx[i] = prefixHolder[0];
				strongNum[i] = number;
			}

			Visitor<RuntimeException> v = vv;
			if (wi.editions != null) {
				v = v.visitVariationText(wi.editions.stream().map(Edition::toString).collect(Collectors.toList()).toArray(new String[0]));
			}
			v = v.visitGrammarInformation(strongPfx, strongNum, wi.rmac, null);
			for (String[] attr : wi.attributes) {
				v.visitExtraAttribute(ExtraAttributePriority.SKIP, attr[0], attr[1], attr[2]).visitText(attr[3].replaceAll("  +", " "));
			}
			if (wi.greek.matches(".* \\(.*\\)$")) {
				int pos = wi.greek.lastIndexOf(" (");
				v.visitExtraAttribute(ExtraAttributePriority.SKIP, "tagnt", "english", "transliteration").visitText(wi.greek.substring(pos + 2, wi.greek.length() - 1));
				wi.greek = wi.greek.substring(0, pos);
			}
			boolean addPara = false;
			if (wi.greek.endsWith("¶")) {
				addPara = true;
				wi.greek = wi.greek.substring(0, wi.greek.length() - 1);
			}
			v.visitText(wi.greek);
			v.visitEnd();
			if (addPara) {
				vv.visitLineBreak(LineBreakKind.PARAGRAPH);
				first = true;
			}
		}
		verse.finished();
	}

	private static class WordItem {
		String greek;
		final List<String[]> attributes = new ArrayList<>();
		EnumSet<Edition> editions;
		String[] strongs;
		String[] rmac;
		int shift = 0;

		public WordItem() {
		}

		public WordItem(WordItem toCopy, Edition edition) {
			this.greek = toCopy.greek;
			this.attributes.addAll(toCopy.attributes);
			this.editions = EnumSet.of(edition);
			this.strongs = toCopy.strongs;
			this.rmac = toCopy.rmac;
		}
	}

	private static enum Edition {
		NA28, NA27, Tyn, SBL, WH, Treg, TR, Byz, Other;

		public static Set<String> OtherEdition = new HashSet<>(Arrays.asList("01", "02", "03", "032", "04", "05", "06", "Byz0", "Coptic", "KJV", "Latin", "NIV", "P66", "P66*", "Syriac"));
	}
}
