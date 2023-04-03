package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;

public class BibleWorks implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Plain text import and export format for BibleWorks",
			"",
			"Usage (export): BibleWorks <OutputFile>"
	};

	private static Map<BookID, String> BOOK_NAME_MAP = new EnumMap<>(BookID.class);
	private static Map<String, BookID> REVERSE_BOOK_MAP = new HashMap<>();

	static {
		// https://www.bibleworks.com/bw9help/bwh42_Abbreviations.htm#BookNameAbbreviations
		BOOK_NAME_MAP.put(BookID.BOOK_Gen, "Gen");
		BOOK_NAME_MAP.put(BookID.BOOK_Exod, "Exo");
		BOOK_NAME_MAP.put(BookID.BOOK_Lev, "Lev");
		BOOK_NAME_MAP.put(BookID.BOOK_Num, "Num");
		BOOK_NAME_MAP.put(BookID.BOOK_Deut, "Deu");
		BOOK_NAME_MAP.put(BookID.BOOK_Josh, "Jos");
		BOOK_NAME_MAP.put(BookID.BOOK_Judg, "Jdg");
		BOOK_NAME_MAP.put(BookID.BOOK_Ruth, "Rut");
		BOOK_NAME_MAP.put(BookID.BOOK_1Sam, "1Sa");
		BOOK_NAME_MAP.put(BookID.BOOK_2Sam, "2Sa");
		BOOK_NAME_MAP.put(BookID.BOOK_1Kgs, "1Ki");
		BOOK_NAME_MAP.put(BookID.BOOK_2Kgs, "2Ki");
		BOOK_NAME_MAP.put(BookID.BOOK_1Chr, "1Ch");
		BOOK_NAME_MAP.put(BookID.BOOK_2Chr, "2Ch");
		BOOK_NAME_MAP.put(BookID.BOOK_Ezra, "Ezr");
		BOOK_NAME_MAP.put(BookID.BOOK_Neh, "Neh");
		BOOK_NAME_MAP.put(BookID.BOOK_Esth, "Est");
		BOOK_NAME_MAP.put(BookID.BOOK_Job, "Job");
		BOOK_NAME_MAP.put(BookID.BOOK_Ps, "Psa");
		BOOK_NAME_MAP.put(BookID.BOOK_Prov, "Pro");
		BOOK_NAME_MAP.put(BookID.BOOK_Eccl, "Ecc");
		BOOK_NAME_MAP.put(BookID.BOOK_Song, "Sol");
		BOOK_NAME_MAP.put(BookID.BOOK_Isa, "Isa");
		BOOK_NAME_MAP.put(BookID.BOOK_Jer, "Jer");
		BOOK_NAME_MAP.put(BookID.BOOK_Lam, "Lam");
		BOOK_NAME_MAP.put(BookID.BOOK_Ezek, "Eze");
		BOOK_NAME_MAP.put(BookID.BOOK_Dan, "Dan");
		BOOK_NAME_MAP.put(BookID.BOOK_Hos, "Hos");
		BOOK_NAME_MAP.put(BookID.BOOK_Joel, "Joe");
		BOOK_NAME_MAP.put(BookID.BOOK_Amos, "Amo");
		BOOK_NAME_MAP.put(BookID.BOOK_Obad, "Oba");
		BOOK_NAME_MAP.put(BookID.BOOK_Jonah, "Jon");
		BOOK_NAME_MAP.put(BookID.BOOK_Mic, "Mic");
		BOOK_NAME_MAP.put(BookID.BOOK_Nah, "Nah");
		BOOK_NAME_MAP.put(BookID.BOOK_Hab, "Hab");
		BOOK_NAME_MAP.put(BookID.BOOK_Zeph, "Zep");
		BOOK_NAME_MAP.put(BookID.BOOK_Hag, "Hag");
		BOOK_NAME_MAP.put(BookID.BOOK_Zech, "Zec");
		BOOK_NAME_MAP.put(BookID.BOOK_Mal, "Mal");
		BOOK_NAME_MAP.put(BookID.BOOK_Matt, "Mat");
		BOOK_NAME_MAP.put(BookID.BOOK_Mark, "Mar");
		BOOK_NAME_MAP.put(BookID.BOOK_Luke, "Luk");
		BOOK_NAME_MAP.put(BookID.BOOK_John, "Joh");
		BOOK_NAME_MAP.put(BookID.BOOK_Acts, "Act");
		BOOK_NAME_MAP.put(BookID.BOOK_Rom, "Rom");
		BOOK_NAME_MAP.put(BookID.BOOK_1Cor, "1Co");
		BOOK_NAME_MAP.put(BookID.BOOK_2Cor, "2Co");
		BOOK_NAME_MAP.put(BookID.BOOK_Gal, "Gal");
		BOOK_NAME_MAP.put(BookID.BOOK_Eph, "Eph");
		BOOK_NAME_MAP.put(BookID.BOOK_Phil, "Phi");
		BOOK_NAME_MAP.put(BookID.BOOK_Col, "Col");
		BOOK_NAME_MAP.put(BookID.BOOK_1Thess, "1Th");
		BOOK_NAME_MAP.put(BookID.BOOK_2Thess, "2Th");
		BOOK_NAME_MAP.put(BookID.BOOK_1Tim, "1Ti");
		BOOK_NAME_MAP.put(BookID.BOOK_2Tim, "2Ti");
		BOOK_NAME_MAP.put(BookID.BOOK_Titus, "Tit");
		BOOK_NAME_MAP.put(BookID.BOOK_Phlm, "Phm");
		BOOK_NAME_MAP.put(BookID.BOOK_Heb, "Heb");
		BOOK_NAME_MAP.put(BookID.BOOK_Jas, "Jam");
		BOOK_NAME_MAP.put(BookID.BOOK_1Pet, "1Pe");
		BOOK_NAME_MAP.put(BookID.BOOK_2Pet, "2Pe");
		BOOK_NAME_MAP.put(BookID.BOOK_1John, "1Jo");
		BOOK_NAME_MAP.put(BookID.BOOK_2John, "2Jo");
		BOOK_NAME_MAP.put(BookID.BOOK_3John, "3Jo");
		BOOK_NAME_MAP.put(BookID.BOOK_Jude, "Jud");
		BOOK_NAME_MAP.put(BookID.BOOK_Rev, "Rev");
		BOOK_NAME_MAP.put(BookID.BOOK_Jdt, "Jdt");
		BOOK_NAME_MAP.put(BookID.BOOK_Wis, "Wis");
		BOOK_NAME_MAP.put(BookID.BOOK_Tob, "Tob");
		BOOK_NAME_MAP.put(BookID.BOOK_Sir, "Sir");
		BOOK_NAME_MAP.put(BookID.BOOK_Bar, "Bar");
		BOOK_NAME_MAP.put(BookID.BOOK_1Macc, "1Ma");
		BOOK_NAME_MAP.put(BookID.BOOK_2Macc, "2Ma");
		BOOK_NAME_MAP.put(BookID.BOOK_PrMan, "Prm");
		BOOK_NAME_MAP.put(BookID.BOOK_3Macc, "3Ma");
		BOOK_NAME_MAP.put(BookID.BOOK_4Macc, "4Ma");
		BOOK_NAME_MAP.put(BookID.BOOK_EpJer, "Epj");
		BOOK_NAME_MAP.put(BookID.BOOK_1Esd, "1Es");
		BOOK_NAME_MAP.put(BookID.BOOK_2Esd, "4Es");
		BOOK_NAME_MAP.put(BookID.BOOK_Odes, "Ode");
		BOOK_NAME_MAP.put(BookID.BOOK_PssSol, "Pss");
		BOOK_NAME_MAP.put(BookID.BOOK_EpLao, "Lao");
		BOOK_NAME_MAP.put(BookID.BOOK_1En, "1En");
		BOOK_NAME_MAP.put(BookID.BOOK_Sus, "Sus");
		BOOK_NAME_MAP.put(BookID.BOOK_Bel, "Bel");
		BOOK_NAME_MAP.put(BookID.BOOK_AddPs, "Psx");
		BOOK_NAME_MAP.put(BookID.BOOK_PrAzar, "Pra");
		BOOK_NAME_MAP.put(BookID.BOOK_EsthGr, "Esg");
		BOOK_NAME_MAP.put(BookID.BOOK_DanGr, "Dng");
		BOOK_NAME_MAP.put(BookID.BOOK_Jub, "Jub");

		// NOT SUPPORTED:
		// Prologue to Sirach: Sip
		// Joshua (A): Jsa
		// Judges (A): Jda
		// Tobit (S): Tbs
		// Susanna (TH): Sut
		// Daniel (TH): Dat
		// Bel and the Dragon (TH): Bet

		for (Map.Entry<BookID, String> e : BOOK_NAME_MAP.entrySet()) {
			REVERSE_BOOK_MAP.put(e.getValue().toUpperCase(), e.getKey());
		}
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
			return doImport(br);
		}
	}

	protected Bible doImport(BufferedReader br) throws IOException {
		String line;
		Bible result = new Bible("Imported from BibleWorks");
		Map<BookID, Book> books = new EnumMap<>(BookID.class);
		Set<String> unsupportedBooks = new HashSet<>();
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(" +", 3);
			if (parts.length != 3 || !parts[1].matches("[1-9][0-9]*:[1-9][0-9]*"))
				throw new IOException("Malformed line: " + line);
			BookID bid = REVERSE_BOOK_MAP.get(parts[0].toUpperCase());
			if (bid == null) {
				if (unsupportedBooks.add(parts[0].toUpperCase()))
					System.out.println("WARNING: Skipping unsupported book " + parts[0]);
				continue;
			}
			Book bk = books.get(bid);
			if (bk == null) {
				bk = new Book(bid.getOsisID(), bid, bid.getEnglishName(), bid.getEnglishName());
				result.getBooks().add(bk);
				books.put(bid, bk);
			}
			String[] chapverse = parts[1].split(":");
			int chapter = Integer.parseInt(chapverse[0]);
			Verse v = new Verse(chapverse[1]);
			while (bk.getChapters().size() < chapter)
				bk.getChapters().add(new Chapter());
			bk.getChapters().get(chapter - 1).getVerses().add(v);
			String rest = parts[2].trim();
			if (rest.endsWith("}") && (rest.contains("<N") || rest.contains("<R"))) {
				int pos = rest.lastIndexOf("{");
				if (pos == -1)
					throw new RuntimeException("Endnotes without start marker: " + rest);
				String endnotes = rest.substring(pos + 1, rest.length() - 1).trim();
				rest = rest.substring(0, pos);
				Map<String, Visitor<RuntimeException>> footnoteMap = new HashMap<>();
				parseText(rest, v.getAppendVisitor(), footnoteMap);
				Visitor<RuntimeException> rx = null;
				if (footnoteMap.size() == 1 && footnoteMap.containsKey("Rx")) {
					rx = footnoteMap.get("Rx");
					if (endnotes.startsWith("<p><p><b>")) {
						endnotes = "<p><rsup>x</rsup> " + endnotes;
					}
				}
				boolean para = endnotes.startsWith("<p>");
				boolean tags = endnotes.startsWith("<");
				Pattern regex = Pattern.compile(para ? "^<p>(<[nr]sup>|\\()(.|[0-9]+)(</[nr]sup>|\\))(.*?)(<p>(?:<[nr]sup>|\\().*)?$" : tags ? "^(<[nr]sup>)(.|[0-9]+)(</[nr]sup>)(.*?)(<[nr]sup>.*)?$" : "^(\\()([0-9]+|[a-z])(\\))(.*?)(\\((?:[0-9]+|[a-z])\\).*)?$");
				while (endnotes != null) {
					Matcher m = regex.matcher(endnotes);
					if (!m.matches())
						throw new IOException("Unsupported endnote: " + endnotes);
					String firstNoteTag = m.group(1).trim();
					String firstNoteMark = m.group(2).trim();
					String firstNoteEndTag = m.group(3).trim();
					String firstNoteText = m.group(4).trim();
					endnotes = m.group(5);
					String key, noteType;
					if (firstNoteTag.equals("<nsup>") && firstNoteEndTag.equals("</nsup>")) {
						noteType = key = "N" + firstNoteMark;
					} else if (firstNoteTag.equals("<rsup>") && firstNoteEndTag.equals("</rsup>")) {
						noteType = key = "R" + firstNoteMark;
					} else if (!tags && firstNoteTag.equals("(") && firstNoteEndTag.equals(")") && firstNoteMark.matches("[a-z]")) {
						noteType = "r" + firstNoteMark;
						key = "R" + firstNoteMark;
					} else if (firstNoteTag.equals("(") && firstNoteEndTag.equals(")")) {
						noteType = "n" + firstNoteMark;
						key = "N" + firstNoteMark;
					} else {
						throw new IOException("Unsupported endnote (unbalanced tags): " + endnotes);
					}
					Visitor<RuntimeException> vv = footnoteMap.remove(key);
					if (vv == null)
						vv = rx;
					if (vv == null)
						throw new RuntimeException("No such footnote " + key + ": " + firstNoteText);
					if (noteType.startsWith("R") || noteType.startsWith("r"))
						vv.visitText(FormattedText.XREF_MARKER);
					vv.visitExtraAttribute(ExtraAttributePriority.SKIP, "bibleworks", "notetype",
							(para ? "P" : "") + noteType.replace('*', '-'));
					pos = parseNoteTags(firstNoteText, 0, vv);
					if (pos != firstNoteText.length())
						throw new IOException("Unbalanced tag: " + firstNoteText.substring(pos));
				}
				if (!footnoteMap.isEmpty())
					throw new IOException("Missing footnote text for " + footnoteMap.keySet() + " in " + parts[0] + " " + parts[1]);
			} else {
				parseText(rest, v.getAppendVisitor(), null);
			}
			v.finished();
		}

		return result;
	}

	private void parseText(String text, Visitor<RuntimeException> vv, Map<String, Visitor<RuntimeException>> footnoteMap) throws IOException {
		int pos, ppos, spos = 0;
		while ((pos = text.indexOf("{", spos)) != -1) {
			int epos = text.indexOf("}", pos);
			if (epos == -1)
				throw new IOException("Unclosed note: " + text.substring(pos));
			String part = text.substring(spos, pos);
			parseBracketsAndTags(part, vv, footnoteMap);
			part = text.substring(pos + 1, epos).trim();
			Visitor<RuntimeException> vvv = vv.visitFootnote();
			vvv.visitExtraAttribute(ExtraAttributePriority.SKIP, "bibleworks", "notetype", "T");
			ppos = parseNoteTags(part, 0, vvv);
			if (ppos != part.length())
				throw new IOException("Unbalanced tag: " + part.substring(ppos));
			spos = epos + 1;
		}
		parseBracketsAndTags(text.substring(spos), vv, footnoteMap);
	}

	private void parseBracketsAndTags(String text, Visitor<RuntimeException> vv, Map<String, Visitor<RuntimeException>> footnoteMap) throws IOException {
		int pos, spos = 0;
		while ((pos = text.indexOf("[", spos)) != -1) {
			if (text.startsWith("[[", pos)) {
				parseTags(text.substring(spos, pos), vv, footnoteMap);
				vv.visitText("[");
				spos = pos + 2;
				continue;
			}
			int epos = text.indexOf("]", pos);
			while(epos != -1 && text.startsWith("]]", epos)) {
				epos = text.indexOf("]", epos + 2);
			}
			if (epos == -1) {
				System.out.println("WARNING: Unclosed italic bracket: " + text.substring(pos));
				break;
			}
			parseTags(text.substring(spos, pos), vv, footnoteMap);
			parseTags(text.substring(pos + 1, epos).replace("[[", "["), vv.visitFormattingInstruction(FormattingInstructionKind.ITALIC), footnoteMap);
			spos = epos + 1;
		}
		parseTags(text.substring(spos), vv, footnoteMap);
	}

	private void parseTags(String text, Visitor<RuntimeException> vv, Map<String, Visitor<RuntimeException>> footnoteMap) throws IOException {
		Matcher m = Pattern.compile("@|<[NR](?:[a-z*]|[0-9]+)>|<[0-9]+>").matcher(text);
		int start = 0;
		while (m.find()) {
			final int spos = m.start(), epos = m.end();
			String preText = text.substring(start, spos).replaceAll("  +", " ").replace("]]", "]"), tag = epos == spos + 1 ? "" : text.substring(spos + 1, epos - 1);
			start = epos;
			int wPos = preText.replaceFirst(" $", "").lastIndexOf(" ") + 1;
			if (m.group().equals("@")) {
				vv.visitText(preText.substring(0, wPos));
				int pos = text.indexOf(" ", start);
				if (pos == -1)
					pos = text.length();
				String morph = text.substring(start, pos);
				String safeMorph = morph.replace('!', 'X').replace('/', 'S').replace('+', 'P').replace('^', 'C').replace('&', 'A').replace('(', 'O').replace(')', 'L');
				String[] rmac = morph2RMAC(morph);
				Visitor<RuntimeException> vvv = vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "bibleworks", "morph", safeMorph);
				if (rmac != null)
					vvv = vvv.visitGrammarInformation(null, null, rmac, null);
				vvv.visitText(preText.substring(wPos));
				start = pos;
			} else if (tag.matches("[0-9]+")) {
				vv.visitText(preText.substring(0, wPos));
				vv.visitGrammarInformation(null, new int[] { Integer.parseInt(tag) }, null, null).visitText(preText.substring(wPos));
			} else if (tag.matches("[NR][0-9a-z*]+")) {
				vv.visitText(preText);
				if (footnoteMap == null)
					throw new IOException("No footnote supported here: " + text.substring(spos));
				footnoteMap.put(tag, vv.visitFootnote());
			} else {
				throw new IllegalStateException();
			}
		}
		vv.visitText(text.substring(start).replaceAll("  +", " ").replace("]]", "]"));
	}

	private int parseNoteTags(String text, int start, Visitor<RuntimeException> vv) throws IOException {
		int pos;
		while ((pos = text.indexOf("<", start)) != -1) {
			vv.visitText(text.substring(start, pos).replaceAll("  +", " "));
			if (text.startsWith("</", pos)) {
				return pos;
			} else {
				int epos = text.indexOf(">", pos);
				if (epos == -1)
					throw new IOException("Unclosed tag: " + text.substring(pos));
				String tag = text.substring(pos + 1, epos);
				start = epos + 1;
				Visitor<RuntimeException> nv;
				if (tag.equals("p")) {
					vv.visitLineBreak(LineBreakKind.PARAGRAPH);
					nv = null;
				} else if (tag.equals("b")) {
					nv = vv.visitFormattingInstruction(FormattingInstructionKind.BOLD);
				} else if (tag.equals("i")) {
					nv = vv.visitFormattingInstruction(FormattingInstructionKind.ITALIC);
				} else if (tag.equals("sub")) {
					nv = vv.visitFormattingInstruction(FormattingInstructionKind.SUBSCRIPT);
				} else if (tag.equals("sup")) {
					nv = vv.visitFormattingInstruction(FormattingInstructionKind.SUPERSCRIPT);
				} else if (tag.equals("gb")) {
					nv = vv.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "bibleworks", "notetag", tag);
				} else if (Arrays.asList("h", "g", "ee", "sym", "cyr", "wing").contains(tag)) {
					nv = vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "bibleworks", "notetag", tag);
				} else {
					nv = null;
					vv.visitText("<");
					start = pos + 1;
				}
				if (nv != null) {
					start = parseNoteTags(text, start, nv);
					if (!text.startsWith("</" + tag + ">", start))
						throw new IOException("Unclosed " + tag + " tag: " + text.substring(start));
					start += tag.length() + 3;
				}
			}
		}
		vv.visitText(text.substring(start).replaceAll("  +", " "));
		return text.length();
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File exportFile = new File(exportArgs[0]);
		try (Writer w = new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
			doExport(bible, w);
		}
	}

	protected void doExport(Bible bible, Writer w) throws IOException {
		for (Book bb : bible.getBooks()) {
			String bookName = BOOK_NAME_MAP.get(bb.getId());
			if (bookName == null) {
				System.out.println("WARNING: Skipping unsupported book " + bb.getAbbr() + " (" + bb.getId().getOsisID() + ")");
				continue;
			}
			for (int c = 1; c <= bb.getChapters().size(); c++) {
				for (VirtualVerse vv : bb.getChapters().get(c - 1).createVirtualVerses()) {
					w.write(bookName + " " + c + ":" + vv.getNumber() + "  ");
					BibleWorksVerseVisitor vi = new BibleWorksVerseVisitor();
					boolean firstVerse = true;
					for (Verse v : vv.getVerses()) {
						vi.visitNextVerse(firstVerse && v.getNumber().equals("" + vv.getNumber()) ? "" : v.getNumber());
						v.accept(vi);
						firstVerse = false;
					}
					vi.writeTo(w);
					w.write("\n");
				}
			}
		}
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private static Set<String> skippedFeatures = new HashSet<>();

	private static void skipFeature(String featureName) {
		if (skippedFeatures.add(featureName)) {
			System.out.println("WARNING: Skipped unsupported feature: " + featureName);
		}
	}

	private static String[] morph2RMAC(String morph) {
		// https://www.bibleworks.com/bw9help/bwh43_Codes.htm#AnOverviewofMorphCodingSchemes
		// we only implement Friberg here
		String[] morphs = morph.replaceAll("^[/!&^()+]+", "").split("[/!&^()+]+");
		String[] rmacs = new String[morphs.length];
		for (int i = 0; i < morphs.length; i++) {
			String m = morphs[i].toUpperCase().replaceAll("^-+", ""), r;
			if (m.matches("N-[NGDAV][MFN-][123-][SP]")) {
				r = "N-" + m.substring(4, 5).replace("-", "") + m.substring(2, 3) + m.substring(5, 6) + m.substring(3, 4).replace("-", "");
			} else if (m.matches("NP[NGDAV][MFN-][123-][SP]")) {
				r = "P-" + m.substring(4, 5).replace("-", "") + m.substring(2, 3) + m.substring(5, 6) + m.substring(3, 4).replace("-", "");
			} else if (m.matches("V[ISOMNPR][PIFARL][AMPEDON]")) {
				r = "V-" + m.substring(2, 4) + m.substring(1, 2).replace('R', 'P');
			} else if (m.matches("V[ISOMNPR][PIFARL][AMPEDON]--[123][SP]")) {
				r = "V-" + m.substring(2, 4) + m.substring(1, 2).replace('R', 'P') + "-" + m.substring(6, 8);
			} else if (m.matches("V[ISOMNPR][PIFARL][AMPEDON][NGDAV][MFN][123-][SP]")) {
				r = "V-" + m.substring(2, 4) + m.substring(1, 2).replace('R', 'P') + "-" + m.substring(4, 5) + m.substring(7, 8) + m.substring(5, 6);
			} else if (m.matches("AP[RITD-][NGDAV][MFN-][12-][SP]")) {
				r = m.substring(2, 3).replace('-', 'P').replace('I', 'X').replace('T', 'I') + "-" + m.substring(5, 6).replace("-", "") + m.substring(3, 4) + m.substring(6, 7) + m.substring(4, 5).replace("-", "");
			} else if (m.matches("AP[CO][NGDAV][MFN-][12-][SP]")) {
				r = "A-NUI";
			} else if (m.matches("AB([CORITDMS-]([NGDAV-][MFN-][12-][SP])?)?")) {
				r = "ADV";
			} else if (m.matches("A-[CORITDMS-][NGDAV][MFN-][12-][SP]")) {
				r = "A-" + m.substring(5, 6).replace("-", "") + m.substring(3, 4) + m.substring(6, 7) + m.substring(4, 5).replace("-", "");
			} else if (m.matches("D[NGDAV][MFN][SP]")) {
				r = "T-" + m.substring(1, 2) + m.substring(3, 4) + m.substring(2, 3);
			} else if (m.matches("P[GDA]")) {
				r = "PREP";
			} else if (m.matches("C[CHS]")) {
				r = "CONJ";
			} else if (m.matches("Q[NSTV]")) {
				r = "PRT";
			} else {
				System.out.println("WARNING: Unable to convert morph: " + morph + " (part " + m + ")");
				return null;
			}
			rmacs[i] = r;
		}
		return rmacs;
	}

	private static String rmac2Morph(String rmac) {
		// back conversion is not implemented (yet)
		return null;
	}

	private static class BibleWorksVerseVisitor implements Visitor<RuntimeException> {

		private final List<StringBuilder> contentParts = new ArrayList<>();
		private final List<String> suffixStack = new ArrayList<>();
		private final StringBuilder endnotes = new StringBuilder();
		private int nextFootnote = 1;
		private char nextXref = 'a';

		private static String fixBrackets(String text) {
			// In case an escaped bracket (marked with \1) is following an italics bracket
			// of same type, add a zero-width NBSP in between to avoid detecting them in wrong order.
			int pos = text.indexOf('\1');
			while(pos != -1) {
				String replacement = "";
				if (pos > 0 && text.charAt(pos-1) == text.charAt(pos+1)) {
					replacement = "\uFEFF";
				}
				text = text.substring(0, pos) + replacement + text.substring(pos+1);
				pos = text.indexOf('\1');
			}
			return text;
		}

		private StringBuilder getCurrentPart() {
			if (contentParts.isEmpty())
				contentParts.add(new StringBuilder());
			return contentParts.get(contentParts.size() - 1);
		}

		private void visitNextVerse(String number) {
			if (!suffixStack.isEmpty())
				throw new IllegalStateException("Invalid tag nesting");
			suffixStack.add("");
			if (!number.isEmpty())
				getCurrentPart().append(" [" + number + "] ");
		}

		private void writeTo(Writer w) throws IOException {
			if (!suffixStack.isEmpty())
				throw new IllegalStateException("Invalid tag nesting");
			for (StringBuilder cp : contentParts) {
				w.write(fixBrackets(cp.toString()));
			}
			if (endnotes.length() > 0)
				w.write("{ " + fixBrackets(endnotes.toString()) + "}");
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			throw new IllegalStateException("virtual verses do not have headlines");
		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			if (text.startsWith("[") || text.startsWith("]"))
				text = "\1" + text;
			getCurrentPart().append(text.replace("[", "[[").replace("]", "]]"));
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
			StringBuilder footnoteMark = new StringBuilder();
			contentParts.add(footnoteMark);
			contentParts.add(new StringBuilder());
			return new BibleWorksFootnoteVisitor(footnoteMark, this);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			skipFeature("Cross reference");
			suffixStack.add("");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			if (kind == FormattingInstructionKind.ITALIC) {
				getCurrentPart().append("[");
				suffixStack.add("]");
			} else if (kind == FormattingInstructionKind.DIVINE_NAME) {
				getCurrentPart().append("\\");
				suffixStack.add("\\");
			} else {
				skipFeature("Formatting " + kind);
				suffixStack.add("");
			}
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			skipFeature("CSS formatting");
			suffixStack.add("");
			return this;
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			visitText("/");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			skipFeature("Line break");
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			String suffix = "";
			if (rmac != null && !suffixStack.get(suffixStack.size() - 1).startsWith("@")) {
				for (String r : rmac) {
					String morph = rmac2Morph(r);
					if (morph != null)
						suffix += "@" + morph;
				}
			}
			if (strongs != null) {
				for (int s : strongs) {
					suffix += "<" + s + ">";
				}
			}
			suffixStack.add(suffix);
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			skipFeature("Dictionary entry");
			suffixStack.add("");
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			skipFeature("Raw HTML");
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			throw new RuntimeException("Variations not supported");
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			if (category.equals("bibleworks") && key.equals("morph")) {
				String morph = value.replace('X', '!').replace('S', '/').replace('P', '+').replace('C', '^').replace('A', '&').replace('O', '(').replace('L', ')');
				suffixStack.add("@" + morph);
				return this;
			}
			skipFeature("Extra attribute");
			if (prio == ExtraAttributePriority.KEEP_CONTENT)
				suffixStack.add("");
			return prio.handleVisitor(category, this);
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			getCurrentPart().append(suffixStack.remove(suffixStack.size() - 1));
			return false;
		}
	}

	private static class BibleWorksFootnoteVisitor implements Visitor<RuntimeException> {

		private final StringBuilder footnoteMark, currentNotes = new StringBuilder();
		private final BibleWorksVerseVisitor verseVisitor;
		private final List<String> suffixStack = new ArrayList<>();
		private Boolean isXref = null;
		private String noteMark = null;
		private boolean isTranslatorsNote = false, withParagraph = true, withTags = true;

		private BibleWorksFootnoteVisitor(StringBuilder footnoteMark, BibleWorksVerseVisitor verseVisitor) {
			this.footnoteMark = footnoteMark;
			this.verseVisitor = verseVisitor;
			suffixStack.add("");
			// to detect incompletely parsed footnotes
			footnoteMark.append("???");
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			skipFeature("Headline in footnote");
			return null;
		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			if (text.startsWith(FormattedText.XREF_MARKER) && isXref == null) {
				isXref = true;
				text = text.substring(FormattedText.XREF_MARKER.length());
			} else if (isXref == null) {
				isXref = false;
			}
			if (text.startsWith("[") || text.startsWith("]"))
				text = "\1" + text;
			currentNotes.append(text.replace("[", "[[").replace("]", "]]"));
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
			skipFeature("Footnote in footnote");
			return null;
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			skipFeature("Cross reference in footnote");
			suffixStack.add("");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			String tag = null;
			switch (kind) {
			case BOLD:
				tag = "b";
				break;
			case ITALIC:
				tag = "i";
				break;
			case SUBSCRIPT:
				tag = "sub";
				break;
			case SUPERSCRIPT:
				tag = "sup";
				break;
			default:
				break;
			}
			if (tag == null) {
				skipFeature("Formatting " + kind + " in footnote");
				suffixStack.add("");
			} else {
				currentNotes.append("<" + tag + ">");
				suffixStack.add("</" + tag + ">");
			}
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			skipFeature("CSS formatting in footnote");
			suffixStack.add("");
			return this;
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			visitText("/");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			currentNotes.append("<p>");
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			skipFeature("Grammar information in footnote");
			suffixStack.add("");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			skipFeature("Dictionary entry in footnote");
			suffixStack.add("");
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			skipFeature("Raw HTML in footnote");
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			throw new RuntimeException("Variations not supported");
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			if (category.equals("bibleworks")) {
				if (key.equals("notetype")) {
					if (noteMark != null)
						throw new RuntimeException("More than one footnote type in the same footnote");
					if (isXref == null)
						isXref = false;
					if (value.startsWith("P")) {
						withParagraph = true;
						value = value.substring(1);
					} else {
						withParagraph = false;
					}
					if (value.equals("T")) {
						isTranslatorsNote = true;
						noteMark = "";
					} else if (value.startsWith(isXref ? "r" : "n")) {
						withTags = false;
					} else if (!value.startsWith(isXref ? "R" : "N")) {
						throw new RuntimeException("Invalid footnote type: " + value);
					}
					noteMark = value.substring(1).replace('-', '*');
					return null;
				} else if (key.equals("notetag")) {
					currentNotes.append("<" + value + ">");
					suffixStack.add("</" + value + ">");
					return this;
				}
			}
			skipFeature("Extra attribute in footnote");
			if (prio == ExtraAttributePriority.KEEP_CONTENT)
				suffixStack.add("");
			return prio.handleVisitor(category, this);
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			currentNotes.append(suffixStack.remove(suffixStack.size() - 1));
			if (suffixStack.isEmpty()) {
				footnoteMark.setLength(0);
				if (isTranslatorsNote) {
					footnoteMark.append("{ " + currentNotes.toString() + " }");
				} else {
					if (withParagraph) {
						verseVisitor.endnotes.append("<p>");
					}
					String tag = "nsup", notePrefix = "N";
					if (isXref != null && isXref) {
						tag = "rsup";
						notePrefix = "R";
						if (noteMark == null) {
							noteMark = "" + verseVisitor.nextXref;
							verseVisitor.nextXref++;
						}
					} else {
						if (noteMark == null) {
							noteMark = "" + verseVisitor.nextFootnote;
							verseVisitor.nextFootnote++;
						}
					}
					verseVisitor.endnotes.append(withTags ? "<" + tag + ">" : "(");
					verseVisitor.endnotes.append(noteMark);
					verseVisitor.endnotes.append(withTags ? "</" + tag + ">" : ")");
					verseVisitor.endnotes.append(" ");
					verseVisitor.endnotes.append(currentNotes.toString());
					verseVisitor.endnotes.append(" ");
					footnoteMark.append("<" + notePrefix + noteMark + ">");
				}
			}
			return false;
		}
	}
}
