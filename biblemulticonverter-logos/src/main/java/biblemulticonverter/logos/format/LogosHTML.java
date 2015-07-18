package biblemulticonverter.logos.format;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;
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
		LOGOS_BOOKS.put(BookID.BOOK_1Esd, "1Esd");
		LOGOS_BOOKS.put(BookID.BOOK_2Esd, "2Esd");
	}

	private int footnoteCounter = 0;
	private int footnoteNumber = 0;
	private int grammarCounter = 0;
	private String lineSeparator;

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String versemap = exportArgs.length == 1 ? "Bible" : exportArgs[1];
		if (!versemap.matches("Bible[A-Z0-9]*")) {
			System.out.println("Invalid versification: " + versemap);
			return;
		}
		VersificationScheme scheme = new LogosVersificationDetector().loadScheme(versemap);
		if (scheme == null) {
			System.out.println("Invalid versification: " + versemap);
			return;
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
				try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(exportArgs[2]), StandardCharsets.UTF_8))) {
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
					for(String key : mb.getKeys())
						template = template.replace("${"+key+"}", mb.getValue(key));
				}
				bw.write(template);
			} else {
				bw.write("<h1>" + title.replace("&", "&amp").replace("<", "&lt;").replace(">", "&gt;") + "</h1>\n");
			}

			StringWriter footnotes = new StringWriter();
			for (Book book : bible.getBooks()) {
				BitSet[] chapterVerses = scheme.getCoveredBooks().get(book.getId());
				String babbr = LOGOS_BOOKS.get(book.getId());
				if (babbr == null && book.getId().getZefID() < 0 && book.getChapters().size() == 1) {
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
						chapter.getProlog().accept(new LogosVisitor(bw, "", footnotes, false, versemap, scheme));
						bw.write("\n<br/>\n");
						continue;
					}
				}
				if (babbr == null) {
					System.out.println("WARNING: Skipping book " + book.getId());
					continue;
				}
				bw.write("<h2>[[@" + versemap + ":" + babbr + "]]" + book.getLongName() + " (" + book.getAbbr() + ")</h2>\n");
				int cnumber = 0;
				for (Chapter chapter : book.getChapters()) {
					cnumber++;
					String chapterRef = "@" + versemap + ":" + babbr + " " + cnumber;
					boolean writeChapterNumber = false;
					int usedHeadlines = 2;
					if (book.getChapters().size() > 1) {
						if (noChapterHeadings) {
							writeChapterNumber = true;
						} else {
							usedHeadlines = 3;
							bw.write("<h3>[[" + chapterRef + "]]{{~ " + book.getAbbr() + " " + cnumber + " }}</h3>\n");
						}
					}
					footnoteNumber = 0;
					if (chapter.getProlog() != null) {
						chapter.getProlog().accept(new LogosVisitor(bw, "", footnotes, book.getId().isNT(), versemap, scheme));
						bw.write("\n<br/>\n");
					}
					BitSet allowedVerses = chapterVerses != null && cnumber <= chapterVerses.length ? chapterVerses[cnumber - 1] : null;
					if (allowedVerses != null && allowedVerses.isEmpty())
						allowedVerses = null;
					for (VirtualVerse vv : chapter.createVirtualVerses(allowedVerses)) {
						for (Headline hh : vv.getHeadlines()) {
							int depth = hh.getDepth() + usedHeadlines < 6 ? hh.getDepth() + usedHeadlines : 6;
							bw.write("<h" + depth + ">");
							hh.accept(new LogosVisitor(bw, "", footnotes, book.getId().isNT(), versemap, scheme));
							bw.write("</h" + depth + ">\n");
						}

						if (vv.getVerses().size() == 0)
							continue;

						int vnumber = vv.getNumber();
						bw.write(verseSeparator);
						if (writeChapterNumber) {
							bw.write("<b style=\"font-size: 20pt\">[[" + chapterRef + "]]" + cnumber + "</b>"+verseSeparator);
							writeChapterNumber = false;
						}
						bw.write("[[" + chapterRef + ":" + vnumber + "]]");
						boolean first = true;
						for (Verse v : vv.getVerses()) {
							if (!first)
								bw.write(verseSeparator);
							if (!first && v.getNumber().matches("[0-9]+,[0-9]+")) {
								bw.write("[[" + "@" + versemap + ":" + babbr + " " + v.getNumber().replace(',', ':') + "]]");
							}
							first = false;
							bw.write("<b>" + v.getNumber() + "</b> {{field-on:bible}}");
							v.accept(new LogosVisitor(bw, "", footnotes, book.getId().isNT(), versemap, scheme));
							bw.write("{{field-off:bible}}\n");
						}
					}
				}
			}
			bw.write(footnotes.toString());
			bw.write("</body></html>");
		}
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

		protected LogosVisitor(Writer writer, String suffix, StringWriter footnoteWriter, boolean nt, String versemap, VersificationScheme scheme) {
			super(writer, suffix);
			this.footnoteWriter = footnoteWriter;
			this.nt = nt;
			this.versemap = versemap;
			this.scheme = scheme;
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
			return super.visitEnd();
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			int level = depth < 3 ? depth + 3 : 6;
			writer.write("<h" + level + ">");
			pushSuffix("</h" + level + ">\n");
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
			return new LogosVisitor(footnoteWriter, "</DIV>\n", null, nt, versemap, scheme);
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			if (!isVerseCovered(book, firstChapter, firstVerse) || !isVerseCovered(book, lastChapter, lastVerse)) {
				writer.write("<sup>");
				pushSuffix("</sup>");
				return this;
			}
			writer.write("<sup>[[ ");
			String ref = firstChapter + ":" + firstVerse;
			if (firstChapter != lastChapter) {
				ref += "-" + lastChapter + ":" + lastVerse;
			} else if (!firstVerse.equals(lastVerse)) {
				ref += "-" + lastVerse;
			}
			pushSuffix(" &gt;&gt; " + versemap + ":" + LOGOS_BOOKS.get(book) + " " + ref + "]]</sup>");
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
			for (int i = 0; i < strongs.length; i++) {
				links.add(type + strongs[i]);
				if (rmac != null)
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
