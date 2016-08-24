package biblemulticonverter.logos.format;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VerseRange;
import biblemulticonverter.format.AbstractHTMLVisitor;
import biblemulticonverter.format.ExportFormat;
import biblemulticonverter.logos.tools.LogosVersificationDetector;
import biblemulticonverter.tools.AbstractVersificationDetector.VersificationScheme;

public class LogosHTML implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"HTML Export format for Logos Bible Software",
			"",
			"Usage: LogosHTML <outfile> [<versemap> [<template> [-inline|-nochapter]]]",
			"",
			"Open the resulting HTML file in LibreOffice 4.4 (as Writer, not as Writer/Web), and",
			"save as MS Office 2007 .docx format. The resulting file can be imported in Logos",
			"as a personal book.",
			"In case no verse map is given, the default verse map, 'Bible', is used.",
			"Use a template in case you want to add a header text (like copyright) automatically.",
			"Use the -inline option to add more than one verse on the same line, or the -nochapter",
			"option to additionally not write headlines for chapters (if the book has a different",
			"headline structure). In that case you can give the template as '-' to use none."
	};

	private static Map<BookID, String> LOGOS_BOOKS = new EnumMap<>(BookID.class);
	private static final BitSet[] ALL_CHAPTER_VERSES = new BitSet[100];

	static {
		LOGOS_BOOKS.put(BookID.BOOK_Gen, "Ge");
		LOGOS_BOOKS.put(BookID.BOOK_Exod, "Ex");
		LOGOS_BOOKS.put(BookID.BOOK_Lev, "Le");
		LOGOS_BOOKS.put(BookID.BOOK_Num, "Nu");
		LOGOS_BOOKS.put(BookID.BOOK_Deut, "Dt");
		LOGOS_BOOKS.put(BookID.BOOK_Josh, "Jos");
		LOGOS_BOOKS.put(BookID.BOOK_Judg, "Jdg");
		LOGOS_BOOKS.put(BookID.BOOK_Ruth, "Ru");
		LOGOS_BOOKS.put(BookID.BOOK_1Sam, "1Sa");
		LOGOS_BOOKS.put(BookID.BOOK_2Sam, "2Sa");
		LOGOS_BOOKS.put(BookID.BOOK_1Kgs, "1Ki");
		LOGOS_BOOKS.put(BookID.BOOK_2Kgs, "2Ki");
		LOGOS_BOOKS.put(BookID.BOOK_1Chr, "1Ch");
		LOGOS_BOOKS.put(BookID.BOOK_2Chr, "2Ch");
		LOGOS_BOOKS.put(BookID.BOOK_Ezra, "Ezr");
		LOGOS_BOOKS.put(BookID.BOOK_Neh, "Ne");
		LOGOS_BOOKS.put(BookID.BOOK_Esth, "Es");
		LOGOS_BOOKS.put(BookID.BOOK_Job, "Job");
		LOGOS_BOOKS.put(BookID.BOOK_Ps, "Ps");
		LOGOS_BOOKS.put(BookID.BOOK_Prov, "Pr");
		LOGOS_BOOKS.put(BookID.BOOK_Eccl, "Ec");
		LOGOS_BOOKS.put(BookID.BOOK_Song, "So");
		LOGOS_BOOKS.put(BookID.BOOK_Isa, "Is");
		LOGOS_BOOKS.put(BookID.BOOK_Jer, "Je");
		LOGOS_BOOKS.put(BookID.BOOK_Lam, "La");
		LOGOS_BOOKS.put(BookID.BOOK_Ezek, "Eze");
		LOGOS_BOOKS.put(BookID.BOOK_Dan, "Da");
		LOGOS_BOOKS.put(BookID.BOOK_Hos, "Ho");
		LOGOS_BOOKS.put(BookID.BOOK_Joel, "Joe");
		LOGOS_BOOKS.put(BookID.BOOK_Amos, "Am");
		LOGOS_BOOKS.put(BookID.BOOK_Obad, "Ob");
		LOGOS_BOOKS.put(BookID.BOOK_Jonah, "Jon");
		LOGOS_BOOKS.put(BookID.BOOK_Mic, "Mic");
		LOGOS_BOOKS.put(BookID.BOOK_Nah, "Na");
		LOGOS_BOOKS.put(BookID.BOOK_Hab, "Hab");
		LOGOS_BOOKS.put(BookID.BOOK_Zeph, "Zep");
		LOGOS_BOOKS.put(BookID.BOOK_Hag, "Hag");
		LOGOS_BOOKS.put(BookID.BOOK_Zech, "Zec");
		LOGOS_BOOKS.put(BookID.BOOK_Mal, "Mal");
		LOGOS_BOOKS.put(BookID.BOOK_Matt, "Mt");
		LOGOS_BOOKS.put(BookID.BOOK_Mark, "Mk");
		LOGOS_BOOKS.put(BookID.BOOK_Luke, "Lk");
		LOGOS_BOOKS.put(BookID.BOOK_John, "Jn");
		LOGOS_BOOKS.put(BookID.BOOK_Acts, "Ac");
		LOGOS_BOOKS.put(BookID.BOOK_Rom, "Ro");
		LOGOS_BOOKS.put(BookID.BOOK_1Cor, "1Co");
		LOGOS_BOOKS.put(BookID.BOOK_2Cor, "2Co");
		LOGOS_BOOKS.put(BookID.BOOK_Gal, "Ga");
		LOGOS_BOOKS.put(BookID.BOOK_Eph, "Eph");
		LOGOS_BOOKS.put(BookID.BOOK_Phil, "Php");
		LOGOS_BOOKS.put(BookID.BOOK_Col, "Col");
		LOGOS_BOOKS.put(BookID.BOOK_1Thess, "1Th");
		LOGOS_BOOKS.put(BookID.BOOK_2Thess, "2Th");
		LOGOS_BOOKS.put(BookID.BOOK_1Tim, "1Ti");
		LOGOS_BOOKS.put(BookID.BOOK_2Tim, "2Ti");
		LOGOS_BOOKS.put(BookID.BOOK_Titus, "Tt");
		LOGOS_BOOKS.put(BookID.BOOK_Phlm, "Phm");
		LOGOS_BOOKS.put(BookID.BOOK_Heb, "Heb");
		LOGOS_BOOKS.put(BookID.BOOK_Jas, "Jas");
		LOGOS_BOOKS.put(BookID.BOOK_1Pet, "1Pe");
		LOGOS_BOOKS.put(BookID.BOOK_2Pet, "2Pe");
		LOGOS_BOOKS.put(BookID.BOOK_1John, "1Jn");
		LOGOS_BOOKS.put(BookID.BOOK_2John, "2Jn");
		LOGOS_BOOKS.put(BookID.BOOK_3John, "3Jn");
		LOGOS_BOOKS.put(BookID.BOOK_Jude, "Jud");
		LOGOS_BOOKS.put(BookID.BOOK_Rev, "Re");
		LOGOS_BOOKS.put(BookID.BOOK_Jdt, "Jdt");
		LOGOS_BOOKS.put(BookID.BOOK_Wis, "Wis");
		LOGOS_BOOKS.put(BookID.BOOK_Tob, "Tob");
		LOGOS_BOOKS.put(BookID.BOOK_Sir, "Sir");
		LOGOS_BOOKS.put(BookID.BOOK_Bar, "Bar");
		LOGOS_BOOKS.put(BookID.BOOK_1Macc, "1Mac");
		LOGOS_BOOKS.put(BookID.BOOK_2Macc, "2Mac");
		LOGOS_BOOKS.put(BookID.BOOK_PrMan, "PrMan");
		LOGOS_BOOKS.put(BookID.BOOK_3Macc, "3Mac");
		LOGOS_BOOKS.put(BookID.BOOK_4Macc, "4Mac");
		LOGOS_BOOKS.put(BookID.BOOK_EpJer, "LetJer");
		LOGOS_BOOKS.put(BookID.BOOK_Sus, "Sus");
		LOGOS_BOOKS.put(BookID.BOOK_1Esd, "1Esd");
		LOGOS_BOOKS.put(BookID.BOOK_2Esd, "2Esd");
		LOGOS_BOOKS.put(BookID.BOOK_PrAzar, "Pr Az");
		LOGOS_BOOKS.put(BookID.BOOK_EsthGr, "EsG");
		LOGOS_BOOKS.put(BookID.BOOK_PssSol, "PssSol");
		BitSet allVerses = new BitSet();
		allVerses.set(1, 1000);
		Arrays.fill(ALL_CHAPTER_VERSES, allVerses);
	}

	public static final String[] NAMED_VERSES = {
			"title", "title 2", "title A", "title B", "title C", "prologue", "prologue 1", "prologue 2", "subscript",
			"1a", "1b", "1c", "1d", "1e", "1f", "1g", "1h", "1i", "1k", "1l", "1m", "1n", "1o", "1p", "1q", "1r", "1s",
			"2a", "2b", "2c", "2d", "2e", "2f", "2g", "2h", "2i", "2k", "2l", "2m", "2n", "2o", "2p",
			"3a", "3b", "3c", "3d", "3e", "3f", "3g", "3h", "3i", "3k", "3l", "4a", "4ab", "4b", "4c", "5a", "5b", "5c", "5d", "5e",
			"6a", "6ab", "6ac", "6b", "6c", "6d", "6e", "6f", "6g", "6h", "6i", "6j", "7a", "7b", "7c", "8a", "8b", "8c", "9a", "9b", "9c", "9d", "9e",
			"10a", "10b", "10c", "11a", "11b", "11c", "12a", "12aa", "12b", "12bb", "12c", "12cc", "12d", "12e", "12f", "12g", "12h", "12i", "12j", "12k", "12l", "12m",
			"12n", "12nn", "12o", "12p", "12q", "12r", "12s", "12t", "12u", "12v", "12x", "12y", "12z", "13a", "13b", "13c", "13d", "13e", "13f", "13g", "13h", "13q",
			"14a", "14b", "14c", "14d", "14q", "15a", "15b", "15c", "15d", "15e", "15f", "15g", "15h", "15i", "15q", "16a", "16b", "16c", "16q",
			"17a", "17aa", "17b", "17bb", "17c", "17cc", "17d", "17dd", "17e", "17ee", "17f", "17ff", "17g", "17gg", "17h", "17hh", "17i", "17ii", "17k", "17kk", "17l", "17m",
			"17n", "17o", "17p", "17q", "17r", "17s", "17t", "17u", "17v", "17w", "17x", "17y", "17z", "18a", "18b", "18c", "18d", "18e", "18f", "18g", "18h", "18i", "18q",
			"19a", "19b", "19c", "19d", "19q", "20a", "20b", "20c", "20d", "20e", "20f", "20g", "20h", "20i", "20j", "20k", "20l", "20q", "21a", "21b", "21c",
			"22a", "22b", "22c", "22d", "22e", "23a", "23ab", "23b", "23c", "24a", "24b", "24c", "24d", "24e", "24f", "24g", "24h", "24i", "24k", "24l", "24m",
			"24n", "24o", "24p", "24q", "24r", "24s", "24t", "24u", "24x", "24y", "24z", "25a", "25b", "26a", "26b", "27a", "27b",
			"28a", "28b", "28c", "28d", "28e", "28f", "28g", "28h", "29a", "29b", "30a", "30b", "30c", "31a", "31b", "32a", "33a", "33b", "34a", "34b", "34c",
			"35a", "35b", "35c", "35d", "35e", "35f", "35g", "35h", "35i", "35k", "35l", "35m", "35n", "35o",
			"36a", "36b", "36p", "37a", "37b", "37c", "37p", "38a", "38b", "38p", "39a", "39p", "40a", "40ab", "40b", "40c", "40p",
			"41a", "41p", "42a", "42b", "42c", "42d", "42p", "43a", "43b", "43p", "44a", "44p", "45a", "45p",
			"46a", "46b", "46c", "46d", "46e", "46f", "46g", "46h", "46i", "46k", "46l", "46p", "47a", "47p", "48a", "48b", "48p", "49a", "49p",
			"50a", "50p", "51a", "51b", "51p", "52a", "52p", "53a", "53p", "54a", "54p", "55a", "55p", "56a", "56b", "56p", "57a", "57p", "58a", "58p", "59a", "59p",
			"60a", "60p", "61a", "61p", "62a", "62b", "62c", "62p", "63a", "63p", "64a", "64p", "65a", "65p", "66a", "66p", "67p", "68p", "69a", "69b", "69p",
			"70a", "70p", "71a", "71p", "72a", "72b", "72p", "73a", "73p", "74a", "74p", "75a", "75p", "76a", "76p", "77a", "77p", "78a", "78p", "79a", "79p",
			"80a", "80p", "81a", "81p", "82a", "82p", "83a", "83p", "84a", "84p", "85a", "85p", "86a", "86p", "87a", "87p", "88a", "88p", "88w", "88x", "88y", "88z",
			"89a", "89p", "90a", "90p", "91p", "92p", "93p", "94p", "95p", "96p", "97p", "98p", "99p", "100p", "101p", "102p", "103p", "104p", "105p"
	};

	private int footnoteCounter = 0;
	private int footnoteNumber = 0;
	private int grammarCounter = 0;
	private String lineSeparator;

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String versemap = exportArgs.length == 1 ? "Bible" : exportArgs[1];
		Properties bookDatatypeMap = null;
		VersificationScheme scheme;
		if (versemap.startsWith("author:")) {
			bookDatatypeMap = new Properties();
			try(InputStream in = new FileInputStream(versemap.substring(7))) {
				bookDatatypeMap.load(in);
			}
			versemap = null;
			scheme = new LogosVersificationDetector().loadScheme("Bible");
		} else {
			if (!versemap.matches("Bible[A-Z0-9]*")) {
				System.out.println("Invalid versification: " + versemap);
				return;
			}
			scheme = new LogosVersificationDetector().loadScheme(versemap);
			if (scheme == null) {
				System.out.println("Invalid versification: " + versemap);
				return;
			}
		}
		footnoteCounter = 0;
		grammarCounter = 0;
		String title = bible.getName();
		String verseSeparator = "<br />";
		lineSeparator = "<br />";
		boolean noChapterHeadings = false;
		if (exportArgs.length > 3 && exportArgs[3].equals("-inline")) {
			verseSeparator = " ";
			lineSeparator = "<br />&nbsp;&nbsp;&nbsp;&nbsp; ";
		} else if (exportArgs.length > 3 && exportArgs[3].equals("-nochapter")) {
			verseSeparator = " ";
			lineSeparator = "<br />&nbsp;&nbsp;&nbsp;&nbsp; ";
			noChapterHeadings = true;
		}
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(exportArgs[0])), StandardCharsets.UTF_8))) {
			bw.write("<html><head>\n" +
					"<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" />\n" +
					"<style>" +
					"body, h1, h2, h3, h4, h5, h6 { font-family: \"Times New Roman\";}\n" +
					"a { color: black; text-decoration: none;}\n" +
					"a.sdfootnotesym, a.sdendnotesym { font-style: italic;}\n" +
					"h1 {font-size: 24pt;}\n" +
					"h2 {font-size: 22pt;}\n" +
					"h3 {font-size: 20pt;}\n" +
					"h4 {font-size: 18pt;}\n" +
					"h5 {font-size: 16pt;}\n" +
					"h6 {font-size: 14pt;}\n" +
					"</style>\n" +
					"</head><body lang=\"de-DE\">\n");

			if (exportArgs.length > 2 && !exportArgs[2].equals("-")) {
				StringWriter sw = new StringWriter();
				try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(exportArgs[2]), StandardCharsets.UTF_8))) {
					char[] buffer = new char[4096];
					int len;
					while ((len = br.read(buffer)) != -1)
						sw.write(buffer, 0, len);
				}
				String template = sw.toString();
				template = template.replace("${name}", bible.getName());
				MetadataBook mb = bible.getMetadataBook();
				if (mb != null) {
					bible.getBooks().remove(0);
					for (String key : mb.getKeys())
						template = template.replace("${" + key + "}", mb.getValue(key));
				}
				bw.write(template);
			} else {
				bw.write("<h1>" + title.replace("&", "&amp").replace("<", "&lt;").replace(">", "&gt;") + "</h1>\n");
			}

			StringWriter footnotes = new StringWriter();
			for (Book book : bible.getBooks()) {
				BitSet[] chapterVerses;
				String milestone;
				if (bookDatatypeMap != null) {
					milestone = bookDatatypeMap.getProperty(book.getId().getOsisID());
					chapterVerses = milestone == null ? null : ALL_CHAPTER_VERSES;
				} else {
					chapterVerses = scheme.getCoveredBooks().get(book.getId());
					String babbr = LOGOS_BOOKS.get(book.getId());
					milestone = babbr == null ? null : (versemap + ":" + babbr+" %c:%v");
				}
				if (milestone == null && book.getId().getZefID() < 0 && book.getChapters().size() == 1) {
					Chapter chapter = book.getChapters().get(0);
					if (chapter.getVerses().size() == 0 && chapter.getProlog() != null) {
						// prolog only book
						bw.write("<h2>");
						if (book.getId() == BookID.DICTIONARY_ENTRY) {
							if (bible.getName().toUpperCase().contains("STRONG") && book.getShortName().matches("[GH][1-9][0-9]*")) {
								bw.write("[[@" + (book.getShortName().startsWith("G") ? "Greek" : "Hebrew") + "Strongs:" + book.getShortName() + "]]");
							} else {
								bw.write("[[@Headword:" + book.getAbbr() + "]]");
								if (!book.getAbbr().equals(book.getShortName())) {
									bw.write("[[@Headword:" + book.getShortName() + "]]");
								}
								if (!book.getLongName().equals(book.getShortName())) {
									bw.write("[[@Headword:" + book.getLongName() + "]]");
								}
							}
						}
						bw.write(book.getLongName() + "</h2>\n");
						footnoteNumber = 0;
						chapter.getProlog().accept(new LogosVisitor(bw, "", footnotes, false, versemap, scheme, null, 2));
						bw.write("\n<br/>\n");
						continue;
					}
				}
				if (milestone == null) {
					System.out.println("WARNING: Skipping book " + book.getId());
					continue;
				}
				bw.write("<h2>[[@" + formatMilestone(milestone, "", "") + "]]" + book.getLongName() + " (" + book.getAbbr() + ")</h2>\n");
				int cnumber = 0;
				for (Chapter chapter : book.getChapters()) {
					cnumber++;
					if (!chapter.getVerses().isEmpty() && chapter.getVerses().get(0).getNumber().endsWith(".p")) {
						Chapter prologue = new Chapter();
						BitSet prologueVerses = new BitSet(100);
						prologueVerses.set(1, 100);
						while (!chapter.getVerses().isEmpty() && chapter.getVerses().get(0).getNumber().endsWith(".p")) {
							Verse v = chapter.getVerses().remove(0);
							Verse vv = new Verse(v.getNumber().replace(".p", ""));
							v.accept(vv.getAppendVisitor());
							vv.finished();
							prologue.getVerses().add(vv);
						}
						exportChapter(milestone, "Prologue", prologue, versemap, scheme, verseSeparator, noChapterHeadings, bw, footnotes, book, chapterVerses, prologueVerses);
					}
					BitSet thisChapterVerses = chapterVerses != null && cnumber <= chapterVerses.length ? chapterVerses[cnumber - 1] : null;
					exportChapter(milestone, ""+cnumber, chapter, versemap, scheme, verseSeparator, noChapterHeadings, bw, footnotes, book, chapterVerses, thisChapterVerses);
				}
			}
			bw.write(footnotes.toString());
			bw.write("</body></html>");
		}
	}

	protected void exportChapter(String milestone, String cname, Chapter chapter, String versemap, VersificationScheme scheme, String verseSeparator, boolean noChapterHeadings, BufferedWriter bw, StringWriter footnotes, Book book, BitSet[] chapterVerses, BitSet thisChapterVerses) throws IOException {
		String chapterRef = "@" + formatMilestone(milestone, cname, "");
		boolean writeChapterNumber = false;
		int usedHeadlines = 2;
		if (book.getChapters().size() > 1) {
			if (noChapterHeadings) {
				writeChapterNumber = true;
			} else {
				usedHeadlines = 3;
				bw.write("<h3>[[" + chapterRef + "]]{{~ " + book.getAbbr() + " " + cname + " }}</h3>\n");
			}
		}
		footnoteNumber = 0;
		if (chapter.getProlog() != null) {
			chapter.getProlog().accept(new LogosVisitor(bw, "", footnotes, book.getId().isNT(), versemap, scheme, null, usedHeadlines));
			bw.write("\n<br/>\n");
		}
		for (VerseRange vr : chapter.createVerseRanges()) {
			String versePrefix = "";
			if (writeChapterNumber) {
				versePrefix = "<b style=\"font-size: 20pt\">[[" + chapterRef + "]]" + cname + "</b>" + verseSeparator;
				writeChapterNumber = false;
			}
			BitSet allowedVerses = thisChapterVerses;
			String vcname = cname;
			if (vr.getChapter() != 0) {
				allowedVerses = chapterVerses != null && vr.getChapter() <= chapterVerses.length ? chapterVerses[vr.getChapter() - 1] : null;
				vcname = "" + vr.getChapter();
			}
			boolean printMilestone = allowedVerses != null && !allowedVerses.isEmpty();
			if (printMilestone) {
				int minVerse = vr.getMinVerse(), maxVerse = vr.getMaxVerse();
				while (maxVerse >= minVerse && !allowedVerses.get(maxVerse))
					maxVerse--;
				while (minVerse <= maxVerse && !allowedVerses.get(minVerse))
					minVerse++;
				if (minVerse == maxVerse) {
					if (minVerse >= 1000 && minVerse < 1000 + NAMED_VERSES.length) {
						versePrefix += "[[@" + formatMilestone(milestone, vcname, NAMED_VERSES[minVerse - 1000]) + "]]";
					} else {
						versePrefix += "[[@" + formatMilestone(milestone, vcname, ""+minVerse) + "]]";
					}
				} else if (minVerse < maxVerse) {
					versePrefix += "[[@" + formatMilestone(milestone, vcname, minVerse + "-" + maxVerse) + "]]";
				}
			}

			for (Verse v : vr.getVerses()) {
				bw.write(verseSeparator);
				v.accept(new LogosVisitor(bw, "", footnotes, book.getId().isNT(), versemap, scheme, versePrefix + "<b>" + v.getNumber() + "</b> ", usedHeadlines));
				versePrefix = "";
				bw.write("\n");
			}
		}
	}

	private static String formatMilestone(String milestone, String chapter, String verse) {
		return milestone.replace("%c", chapter).replace("%v", verse).replaceAll("[ .,:;]+$", "");
	}

	private static final Pattern NO_GREEK_OR_HEBREW = Pattern.compile("[\\P{IsGreek}&&\\P{IsHebrew}]*+");
	private static final Pattern FIND_HEBREW = Pattern.compile("[\\p{IsHebrew}]++([\\p{IsCommon}]++[\\p{IsHebrew}]++)*+");
	private static final Pattern FIND_GREEK = Pattern.compile("[\\p{IsGreek}]++([\\p{IsCommon}]++[\\p{IsGreek}]++)*+");

	private static String tagForeign(String str) {
		// fast path for when every character is neither greek nor hebrew
		if (NO_GREEK_OR_HEBREW.matcher(str).matches())
			return str;
		// do the tagging
		return tagOne(tagOne(str, FIND_HEBREW, "he-IL"), FIND_GREEK, "el-GR");
	}

	private static String tagOne(String str, Pattern pattern, String languageCode) {
		Matcher m = pattern.matcher(str);
		StringBuffer result = new StringBuffer(str.length());
		while (m.find()) {
			m.appendReplacement(result, "<span lang=\"" + languageCode + "\">$0</span>");
		}
		m.appendTail(result);
		return result.toString();
	}

	private static String convertMorphology(String rmac) {
		Matcher m = Utils.compilePattern("([NARCDTKIXQFSP])(-([123]?)([NVGDA][SP][MFN]?))?(-(S|C|ABB|I|N|K|ATT))?").matcher(rmac);
		if (m.matches()) {
			char type = m.group(1).charAt(0);
			String person = m.group(3);
			if (person == null)
				person = "X";
			String flags = m.group(4);
			if (flags == null) {
				flags = "";
			}
			String suffix = m.group(6);
			String cops;
			if (suffix != null && (suffix.equals("C") || suffix.equals("S"))) {
				cops = suffix;
			} else {
				cops = "O";
			}
			switch (type) {
			case 'N': // @N[ADGNV][DPS][FMN][COPS]
				return "N" + flags.substring(0, 3) + cops;
			case 'A': // @J[ADGNV][DPS][FMN][COPS]
				if (flags.equals("") && cops != null)
					flags = "XXX";
				return "J" + flags.substring(0, 3) + cops;
			case 'T': // @D[ADGNV][DPS][FMN]
				return "D" + flags;
			case 'Q': // correlative or interrogative -> RK or RI
				return "RX" + person + flags;
			case 'R': // @R[CDFIKNPRSX][123][ADGNV][DPS][FMN][AP]
			case 'X':
			case 'F':
			case 'S':
			case 'P':
			case 'K':
			case 'I':
			case 'C':
			case 'D':
				return "R" + type + person + flags;
			}
		} else if (rmac.startsWith("V-")) { // @V[AFILPRT][AMPU][IMNOPS][123][DPS][ADGNV][FMN]
			Matcher mm = Utils.compilePattern("V-2?([PIFARLX])([AMPEDON][ISOMNP])(-([123][SP])|-([NGDAV][SPD][MFN]))?(-ATT)?").matcher(rmac);
			if (!mm.matches())
				throw new RuntimeException(rmac);
			String tense = mm.group(1);
			String flags = mm.group(2);
			String optflags1 = mm.group(4);
			String optflags2 = mm.group(5);
			String opt = "";
			if (optflags1 != null) {
				opt = optflags1;
			} else if (optflags2 != null) {
				opt = "XX" + optflags2.charAt(1) + "" + optflags2.charAt(0) + "" + optflags2.charAt(2);
			}
			char voice = flags.charAt(0);
			if ("EDON".contains("" + voice)) {
				voice = "UMPU".charAt("EDON".indexOf(voice));
			}
			return "V" + tense + voice + "" + flags.charAt(1) + opt;
		} else {
			switch (rmac) {
			case "ADV": // @B[CEIKNPSX]
				return "B";
			case "ADV-S":
			case "ADV-I":
			case "ADV-N":
			case "ADV-C":
			case "ADV-K":
				return "B" + rmac.substring(4, 5);
			case "CONJ": // @C[AC AD AL AM AN AP AR AT AZ LA LC LD LI LK LM LN
							// LT LX SC SE]
			case "CONJ-N":
			case "CONJ-K":
				return "C";
			case "COND": // @T[CEIKNPSX]
				return "TC";
			case "PRT":
				return "T";
			case "PRT-N":
				return "TN";
			case "PRT-I":
				return "TI";
			case "PREP":
				return "P";
			case "INJ": // @I
				return "I";
			case "ARAM": // @X[FLNOP]
			case "HEB":
				return "XF";
			case "N-PRI":
				return "XP";
			case "A-NUI":
				return "XN";
			case "N-LI":
				return "XL";
			case "N-OI":
				return "XO";
			}
		}

		throw new RuntimeException(rmac);
	}

	private class LogosVisitor extends AbstractHTMLVisitor {

		private StringWriter footnoteWriter;
		private boolean nt;
		private String versemap;
		VersificationScheme scheme;
		private boolean grammarFlag;
		private final String fieldPrefix;
		private boolean fieldOn = false;
		private final int usedHeadlines;

		protected LogosVisitor(Writer writer, String suffix, StringWriter footnoteWriter, boolean nt, String versemap, VersificationScheme scheme, String fieldPrefix, int usedHeadlines) {
			super(writer, suffix);
			this.footnoteWriter = footnoteWriter;
			this.nt = nt;
			this.versemap = versemap;
			this.scheme = scheme;
			this.fieldPrefix = fieldPrefix;
			this.usedHeadlines = usedHeadlines;
		}

		@Override
		public void visitStart() throws IOException {
			if (fieldPrefix != null && suffixStack.size() == 1) {
				writer.write(fieldPrefix);
				if (versemap != null) {
					fieldOn = true;
					writer.write("{{field-on:bible}}");
				}
			}
		}

		@Override
		public void visitText(String text) throws IOException {
			grammarFlag = false;
			text = text.replace("&", "&amp").replace("<", "&lt;").replace(">", "&gt;");
			writer.write(tagForeign(text));
		}

		@Override
		public boolean visitEnd() throws IOException {
			if (suffixStack.get(suffixStack.size() - 1).equals("\1")) {
				suffixStack.set(suffixStack.size() - 1, "");
				grammarFlag = true;
			}
			if (fieldPrefix != null && suffixStack.size() == 1 && versemap != null) {
				writer.write("{{field-off:bible}}");
				fieldOn = false;
			}
			return super.visitEnd();
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			int level = depth + usedHeadlines < 6 ? depth + usedHeadlines : 6;
			String suffix = "</h" + level + ">\n";
			if (fieldOn) {
				writer.write("{{field-off:bible}}");
				suffix += "{{field-on:bible}}";
			}
			writer.write("<h" + level + ">");
			pushSuffix(suffix);
			return this;
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			if (footnoteWriter == null)
				throw new IllegalStateException("Footnote inside footnote not supported");
			footnoteCounter++;
			footnoteNumber++;

			footnoteWriter.write("<DIV ID=\"sdfootnote" + footnoteCounter + "\">");
			writer.write("<A CLASS=\"sdfootnoteanc\" HREF=\"#sdfootnote" + footnoteCounter + "sym\" sdfixed><sup>" + footnoteNumber + "</sup></A>");
			return new LogosVisitor(footnoteWriter, "</DIV>\n", null, nt, versemap, scheme, null, usedHeadlines);
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			if (!isVerseCovered(book, firstChapter, firstVerse) || !isVerseCovered(book, lastChapter, lastVerse)) {
				writer.write("<sup>");
				pushSuffix("</sup>");
				return this;
			}
			writer.write("[[ ");
			String ref = firstChapter + ":" + firstVerse;
			if (firstChapter != lastChapter) {
				ref += "-" + lastChapter + ":" + lastVerse;
			} else if (!firstVerse.equals(lastVerse)) {
				ref += "-" + lastVerse;
			}
			pushSuffix(" &gt;&gt; " + (versemap == null ? "Bible" : versemap) + ":" + LOGOS_BOOKS.get(book) + " " + ref + "]]");
			return this;
		}

		private boolean isVerseCovered(BookID book, int chapter, String verse) {
			BitSet[] chapterVerses = scheme.getCoveredBooks().get(book);
			if (chapterVerses == null || chapter > chapterVerses.length)
				return false;
			BitSet allowedVerses = chapterVerses[chapter - 1];
			try {
				int verseNum = Integer.parseInt(verse);
				return allowedVerses.get(verseNum);
			} catch (NumberFormatException ex) {
				return false;
			}
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws IOException {
			grammarFlag = false;
			writer.write(lineSeparator);
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			String link;
			if (dictionary.equals("strong")) {
				link = (entry.startsWith("H") ? "Hebrew" : "Greek") + "Strongs:" + entry;
			} else {
				// "logosres:pbb:d77fa70df6cd42f9890343c2779e6680;hw=Foo" is not
				// feasible as the UUID is assigned when initially creating the
				// book...
				link = "Headword:" + entry;
			}
			writer.write("[[ ");
			pushSuffix(" &gt;&gt; " + link + "]]</sup>");
			return this;
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			List<String> links = new ArrayList<String>();
			String type = nt ? "GreekStrongs:G" : "HebrewStrongs:H";
			int max = Math.max(strongs == null ? 0 : strongs.length, rmac == null ? 0 : rmac.length);
			for (int i = 0; i < max; i++) {
				if (strongs != null && i < strongs.length)
					links.add(type + strongs[i]);
				if (rmac != null && i < rmac.length)
					links.add("LogosMorphGr:" + convertMorphology(rmac[i]));
			}
			if (links.size() == 0) {
				pushSuffix("");
			} else {
				// LibreOffice seems to have a problem when there are too many
				// hyperlinks.
				// Therefore, only export hyperlinks for 10 grammar elements and
				// use dummy elements
				// for postprocessing later.
				grammarCounter++;
				String firstLink = links.remove(0);
				String prefix = "<a href=\"" + firstLink + "\">";
				StringBuilder suffix = new StringBuilder("</a>");
				for (String link : links) {
					suffix.append("<a href=\"" + link + "\">\u2295</a>");
				}
				boolean stackProblem = suffixStack.size() > 1;
				if (suffixStack.size() == 2 && suffixStack.get(1).equals("</span>")) {
					stackProblem = false;
				}
				if (grammarCounter < 10 || stackProblem) {
					writer.write(prefix);
					pushSuffix(suffix.toString());
				} else {
					if (grammarFlag) {
						// avoid </strike><strike> in HTML, which would be
						// ignored by LibreOffice
						System.out.println("WARNING: Added whitespace between adjacent grammar tags");
						writer.write(" ");
					}
					links.add(0, firstLink);
					StringBuilder altPrefix = new StringBuilder("<strike>|");
					for (String link : links) {
						altPrefix.append(link + "|");
					}
					altPrefix.append("|");
					pushSuffix("\1");
					return new GrammarHackVisitor(this, writer, prefix, suffix.toString(), altPrefix.toString(), "</strike>");
				}
			}
			return this;
		}
	}

	private static class GrammarHackVisitor extends VisitorAdapter<IOException> {

		private final Writer writer;
		private final String prefix;
		private String suffix;
		private final String altPrefix;
		private final String altSuffix;

		public GrammarHackVisitor(Visitor<IOException> nextVisitor, Writer writer, String prefix, String suffix, String altPrefix, String altSuffix) throws IOException {
			super(nextVisitor);
			this.writer = writer;
			this.prefix = prefix;
			this.suffix = suffix;
			this.altPrefix = altPrefix;
			this.altSuffix = altSuffix;
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			if (elementTypes == null)
				return 1;
			if (elementTypes.equals("t")) {
				// only text, let's use our alternative Strings
				writer.write(altPrefix);
				suffix = altSuffix;
			} else {
				writer.write(prefix);
			}
			return 0;
		}

		@Override
		public boolean visitEnd() throws IOException {
			writer.write(suffix);
			return super.visitEnd();
		}

	}
}
