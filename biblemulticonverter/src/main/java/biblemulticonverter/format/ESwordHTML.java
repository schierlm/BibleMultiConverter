package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.Map;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.HyperlinkType;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.VirtualVerse;

public class ESwordHTML implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"HTML Export format for E-Sword",
			"",
			"Usage: ESwordHTML <outfile> [<marker>]",
			"",
			"This export format will export two files, <basename>.bblx.HTM and <basename>.cmtx.HTM.",
			"They can be imported in E-Sword ToolTip Tool NT (2.51) and exported as .bblx or .cmtx,",
			"respectively. The bible is exported as two files, as the .bblx format does not support",
			"footnotes or Strongs references, so if these exist in the bible, they will be exported",
			"into a matching commentary file.",
			"",
			"As ToolTipTool NT sometimes introduces fake newlines when importing from HTML, a marker can",
			"be written to the end of every line (which should not exist elsewhere in the file), so that",
			"it can later be removed after converting to RTF (using ESwordRTFPostprocessor Tool)."
	};

	private static final ESwordBookInfo[] BOOK_INFO = {
			new ESwordBookInfo(BookID.BOOK_Gen, "Genesis", 31, 25, 24, 26, 32, 22, 24, 22, 29, 32, 32, 20, 18, 24, 21, 16, 27, 33, 38, 18, 34, 24, 20, 67, 34, 35, 46, 22, 35, 43, 55, 32, 20, 31, 29, 43, 36, 30, 23, 23, 57, 38, 34, 34, 28, 34, 31, 22, 33, 26),
			new ESwordBookInfo(BookID.BOOK_Exod, "Exodus", 22, 25, 22, 31, 23, 30, 25, 32, 35, 29, 10, 51, 22, 31, 27, 36, 16, 27, 25, 26, 36, 31, 33, 18, 40, 37, 21, 43, 46, 38, 18, 35, 23, 35, 35, 38, 29, 31, 43, 38),
			new ESwordBookInfo(BookID.BOOK_Lev, "Leviticus", 17, 16, 17, 35, 19, 30, 38, 36, 24, 20, 47, 8, 59, 57, 33, 34, 16, 30, 37, 27, 24, 33, 44, 23, 55, 46, 34),
			new ESwordBookInfo(BookID.BOOK_Num, "Numbers", 54, 34, 51, 49, 31, 27, 89, 26, 23, 36, 35, 16, 33, 45, 41, 50, 13, 32, 22, 29, 35, 41, 30, 25, 18, 65, 23, 31, 40, 16, 54, 42, 56, 29, 34, 13),
			new ESwordBookInfo(BookID.BOOK_Deut, "Deuteronomy", 46, 37, 29, 49, 33, 25, 26, 20, 29, 22, 32, 32, 18, 29, 23, 22, 20, 22, 21, 20, 23, 30, 25, 22, 19, 19, 26, 68, 29, 20, 30, 52, 29, 12),
			new ESwordBookInfo(BookID.BOOK_Josh, "Joshua", 18, 24, 17, 24, 15, 27, 26, 35, 27, 43, 23, 24, 33, 15, 63, 10, 18, 28, 51, 9, 45, 34, 16, 33),
			new ESwordBookInfo(BookID.BOOK_Judg, "Judges", 36, 23, 31, 24, 31, 40, 25, 35, 57, 18, 40, 15, 25, 20, 20, 31, 13, 31, 30, 48, 25),
			new ESwordBookInfo(BookID.BOOK_Ruth, "Ruth", 22, 23, 18, 22),
			new ESwordBookInfo(BookID.BOOK_1Sam, "1 Samuel", 28, 36, 21, 22, 12, 21, 17, 22, 27, 27, 15, 25, 23, 52, 35, 23, 58, 30, 24, 42, 15, 23, 29, 22, 44, 25, 12, 25, 11, 31, 13),
			new ESwordBookInfo(BookID.BOOK_2Sam, "2 Samuel", 27, 32, 39, 12, 25, 23, 29, 18, 13, 19, 27, 31, 39, 33, 37, 23, 29, 33, 43, 26, 22, 51, 39, 25),
			new ESwordBookInfo(BookID.BOOK_1Kgs, "1 Kings", 53, 46, 28, 34, 18, 38, 51, 66, 28, 29, 43, 33, 34, 31, 34, 34, 24, 46, 21, 43, 29, 53),
			new ESwordBookInfo(BookID.BOOK_2Kgs, "2 Kings", 18, 25, 27, 44, 27, 33, 20, 29, 37, 36, 21, 21, 25, 29, 38, 20, 41, 37, 37, 21, 26, 20, 37, 20, 30),
			new ESwordBookInfo(BookID.BOOK_1Chr, "1 Chronicles", 54, 55, 24, 43, 26, 81, 40, 40, 44, 14, 47, 40, 14, 17, 29, 43, 27, 17, 19, 8, 30, 19, 32, 31, 31, 32, 34, 21, 30),
			new ESwordBookInfo(BookID.BOOK_2Chr, "2 Chronicles", 17, 18, 17, 22, 14, 42, 22, 18, 31, 19, 23, 16, 22, 15, 19, 14, 19, 34, 11, 37, 20, 12, 21, 27, 28, 23, 9, 27, 36, 27, 21, 33, 25, 33, 27, 23),
			new ESwordBookInfo(BookID.BOOK_Ezra, "Ezra", 11, 70, 13, 24, 17, 22, 28, 36, 15, 44),
			new ESwordBookInfo(BookID.BOOK_Neh, "Nehemiah", 11, 20, 32, 23, 19, 19, 73, 18, 38, 39, 36, 47, 31),
			new ESwordBookInfo(BookID.BOOK_Esth, "Esther", 22, 23, 15, 17, 14, 14, 10, 17, 32, 3),
			new ESwordBookInfo(BookID.BOOK_Job, "Job", 22, 13, 26, 21, 27, 30, 21, 22, 35, 22, 20, 25, 28, 22, 35, 22, 16, 21, 29, 29, 34, 30, 17, 25, 6, 14, 23, 28, 25, 31, 40, 22, 33, 37, 16, 33, 24, 41, 30, 24, 34, 17),
			new ESwordBookInfo(BookID.BOOK_Ps, "Psalm", 6, 12, 8, 8, 12, 10, 17, 9, 20, 18, 7, 8, 6, 7, 5, 11, 15, 50, 14, 9, 13, 31, 6, 10, 22, 12, 14, 9, 11, 12, 24, 11, 22, 22, 28, 12, 40, 22, 13, 17, 13, 11, 5, 26, 17, 11, 9, 14, 20, 23, 19, 9, 6, 7, 23, 13, 11, 11, 17, 12, 8, 12, 11, 10, 13, 20, 7, 35, 36, 5, 24, 20, 28, 23, 10, 12, 20, 72, 13, 19, 16, 8, 18, 12, 13, 17, 7, 18, 52, 17, 16, 15, 5, 23, 11, 13, 12, 9, 9, 5, 8, 28, 22, 35, 45, 48, 43, 13, 31, 7, 10, 10, 9, 8, 18, 19, 2, 29, 176, 7, 8, 9, 4, 8, 5, 6, 5, 6, 8, 8, 3, 18, 3, 3, 21, 26, 9, 8, 24, 13, 10, 7, 12, 15, 21, 10, 20, 14, 9, 6, 7),
			new ESwordBookInfo(BookID.BOOK_Prov, "Proverbs", 33, 22, 35, 27, 23, 35, 27, 36, 18, 32, 31, 28, 25, 35, 33, 33, 28, 24, 29, 30, 31, 29, 35, 34, 28, 28, 27, 28, 27, 33, 31),
			new ESwordBookInfo(BookID.BOOK_Eccl, "Ecclesiastes", 18, 26, 22, 16, 20, 12, 29, 17, 18, 20, 10, 14),
			new ESwordBookInfo(BookID.BOOK_Song, "Song of Solomon", 17, 17, 11, 16, 16, 13, 13, 14),
			new ESwordBookInfo(BookID.BOOK_Isa, "Isaiah", 31, 22, 26, 6, 30, 13, 25, 22, 21, 34, 16, 6, 22, 32, 9, 14, 14, 7, 25, 6, 17, 25, 18, 23, 12, 21, 13, 29, 24, 33, 9, 20, 24, 17, 10, 22, 38, 22, 8, 31, 29, 25, 28, 28, 25, 13, 15, 22, 26, 11, 23, 15, 12, 17, 13, 12, 21, 14, 21, 22, 11, 12, 19, 12, 25, 24),
			new ESwordBookInfo(BookID.BOOK_Jer, "Jeremiah", 19, 37, 25, 31, 31, 30, 34, 22, 26, 25, 23, 17, 27, 22, 21, 21, 27, 23, 15, 18, 14, 30, 40, 10, 38, 24, 22, 17, 32, 24, 40, 44, 26, 22, 19, 32, 21, 28, 18, 16, 18, 22, 13, 30, 5, 28, 7, 47, 39, 46, 64, 34),
			new ESwordBookInfo(BookID.BOOK_Lam, "Lamentations", 22, 22, 66, 22, 22),
			new ESwordBookInfo(BookID.BOOK_Ezek, "Ezekiel", 28, 10, 27, 17, 17, 14, 27, 18, 11, 22, 25, 28, 23, 23, 8, 63, 24, 32, 14, 49, 32, 31, 49, 27, 17, 21, 36, 26, 21, 26, 18, 32, 33, 31, 15, 38, 28, 23, 29, 49, 26, 20, 27, 31, 25, 24, 23, 35),
			new ESwordBookInfo(BookID.BOOK_Dan, "Daniel", 21, 49, 30, 37, 31, 28, 28, 27, 27, 21, 45, 13),
			new ESwordBookInfo(BookID.BOOK_Hos, "Hosea", 11, 23, 5, 19, 15, 11, 16, 14, 17, 15, 12, 14, 16, 9),
			new ESwordBookInfo(BookID.BOOK_Joel, "Joel", 20, 32, 21),
			new ESwordBookInfo(BookID.BOOK_Amos, "Amos", 15, 16, 15, 13, 27, 14, 17, 14, 15),
			new ESwordBookInfo(BookID.BOOK_Obad, "Obadiah", 21),
			new ESwordBookInfo(BookID.BOOK_Jonah, "Jonah", 17, 10, 10, 11),
			new ESwordBookInfo(BookID.BOOK_Mic, "Micah", 16, 13, 12, 13, 15, 16, 20),
			new ESwordBookInfo(BookID.BOOK_Nah, "Nahum", 15, 13, 19),
			new ESwordBookInfo(BookID.BOOK_Hab, "Habakkuk", 17, 20, 19),
			new ESwordBookInfo(BookID.BOOK_Zeph, "Zephaniah", 18, 15, 20),
			new ESwordBookInfo(BookID.BOOK_Hag, "Haggai", 15, 23),
			new ESwordBookInfo(BookID.BOOK_Zech, "Zechariah", 21, 13, 10, 14, 11, 15, 14, 23, 17, 12, 17, 14, 9, 21),
			new ESwordBookInfo(BookID.BOOK_Mal, "Malachi", 14, 17, 18, 6),
			new ESwordBookInfo(BookID.BOOK_Matt, "Matthew", 25, 23, 17, 25, 48, 34, 29, 34, 38, 42, 30, 50, 58, 36, 39, 28, 27, 35, 30, 34, 46, 46, 39, 51, 46, 75, 66, 20),
			new ESwordBookInfo(BookID.BOOK_Mark, "Mark", 45, 28, 35, 41, 43, 56, 37, 38, 50, 52, 33, 44, 37, 72, 47, 20),
			new ESwordBookInfo(BookID.BOOK_Luke, "Luke", 80, 52, 38, 44, 39, 49, 50, 56, 62, 42, 54, 59, 35, 35, 32, 31, 37, 43, 48, 47, 38, 71, 56, 53),
			new ESwordBookInfo(BookID.BOOK_John, "John", 51, 25, 36, 54, 47, 71, 53, 59, 41, 42, 57, 50, 38, 31, 27, 33, 26, 40, 42, 31, 25),
			new ESwordBookInfo(BookID.BOOK_Acts, "Acts", 26, 47, 26, 37, 42, 15, 60, 40, 43, 48, 30, 25, 52, 28, 41, 40, 34, 28, 41, 38, 40, 30, 35, 27, 27, 32, 44, 31),
			new ESwordBookInfo(BookID.BOOK_Rom, "Romans", 32, 29, 31, 25, 21, 23, 25, 39, 33, 21, 36, 21, 14, 23, 33, 27),
			new ESwordBookInfo(BookID.BOOK_1Cor, "1 Corinthians", 31, 16, 23, 21, 13, 20, 40, 13, 27, 33, 34, 31, 13, 40, 58, 24),
			new ESwordBookInfo(BookID.BOOK_2Cor, "2 Corinthians", 24, 17, 18, 18, 21, 18, 16, 24, 15, 18, 33, 21, 14),
			new ESwordBookInfo(BookID.BOOK_Gal, "Galatians", 24, 21, 29, 31, 26, 18),
			new ESwordBookInfo(BookID.BOOK_Eph, "Ephesians", 23, 22, 21, 32, 33, 24),
			new ESwordBookInfo(BookID.BOOK_Phil, "Philippians", 30, 30, 21, 23),
			new ESwordBookInfo(BookID.BOOK_Col, "Colossians", 29, 23, 25, 18),
			new ESwordBookInfo(BookID.BOOK_1Thess, "1 Thessalonians", 10, 20, 13, 18, 28),
			new ESwordBookInfo(BookID.BOOK_2Thess, "2 Thessalonians", 12, 17, 18),
			new ESwordBookInfo(BookID.BOOK_1Tim, "1 Timothy", 20, 15, 16, 16, 25, 21),
			new ESwordBookInfo(BookID.BOOK_2Tim, "2 Timothy", 18, 26, 17, 22),
			new ESwordBookInfo(BookID.BOOK_Titus, "Titus", 16, 15, 15),
			new ESwordBookInfo(BookID.BOOK_Phlm, "Philemon", 25),
			new ESwordBookInfo(BookID.BOOK_Heb, "Hebrews", 14, 18, 19, 16, 14, 20, 28, 13, 28, 39, 40, 29, 25),
			new ESwordBookInfo(BookID.BOOK_Jas, "James", 27, 26, 18, 17, 20),
			new ESwordBookInfo(BookID.BOOK_1Pet, "1 Peter", 25, 25, 22, 19, 14),
			new ESwordBookInfo(BookID.BOOK_2Pet, "2 Peter", 21, 22, 18),
			new ESwordBookInfo(BookID.BOOK_1John, "1 John", 10, 29, 24, 21, 21),
			new ESwordBookInfo(BookID.BOOK_2John, "2 John", 13),
			new ESwordBookInfo(BookID.BOOK_3John, "3 John", 14),
			new ESwordBookInfo(BookID.BOOK_Jude, "Jude", 25),
			new ESwordBookInfo(BookID.BOOK_Rev, "Revelation", 20, 29, 22, 11, 14, 17, 17, 13, 21, 11, 19, 17, 18, 20, 8, 21, 18, 24, 21, 15, 27, 21),
			new ESwordBookInfo(BookID.BOOK_Jdt, "Judith", 16, 28, 10, 15, 24, 21, 32, 36, 14, 23, 23, 20, 20, 19, 13, 25),
			new ESwordBookInfo(BookID.BOOK_Wis, "Wisdom", 16, 24, 19, 20, 23, 25, 30, 21, 18, 21, 26, 27, 19, 31, 19, 29, 21, 25, 22),
			new ESwordBookInfo(BookID.BOOK_Tob, "Tobit", 22, 14, 17, 42, 21, 17, 18, 21, 6, 12, 19, 22, 18, 15),
			new ESwordBookInfo(BookID.BOOK_Sir, "Sirach", 30, 18, 31, 31, 15, 37, 36, 19, 18, 31, 34, 18, 26, 27, 20, 30, 32, 33, 30, 31, 28, 27, 27, 34, 26, 29, 30, 26, 28, 25, 31, 24, 31, 26, 20, 26, 31, 34, 35, 30, 23, 25, 33, 23, 26, 20, 25, 25, 16, 29, 30),
			new ESwordBookInfo(BookID.BOOK_Bar, "Baruch", 21, 35, 37, 37, 9, 73),
			new ESwordBookInfo(BookID.BOOK_1Macc, "1Maccabees", 64, 70, 60, 61, 68, 63, 50, 32, 73, 89, 74, 53, 53, 49, 41, 24),
			new ESwordBookInfo(BookID.BOOK_2Macc, "2Maccabees", 36, 32, 40, 50, 27, 31, 42, 36, 29, 38, 38, 45, 26, 46, 39),
			new ESwordBookInfo(BookID.BOOK_PrMan, "Prayer of Manasseh", 15),
			new ESwordBookInfo(BookID.BOOK_3Macc, "3Maccabees", 29, 33, 30, 21, 51, 41, 23),
			new ESwordBookInfo(BookID.BOOK_4Macc, "4Maccabees", 35, 24, 21, 26, 38, 35, 23, 29, 32, 21, 27, 19, 27, 20, 32, 25, 24, 24),
			new ESwordBookInfo(BookID.BOOK_1Esd, "1 Esdras", 58, 30, 24, 63, 73, 34, 15, 96, 55),
			new ESwordBookInfo(BookID.BOOK_2Esd, "2 Esdras", 40, 48, 36, 52, 56, 59, 70, 63, 47, 59, 46, 51, 58, 48, 63, 78),
	};

	private static final Map<BookID, ESwordBookInfo> BOOK_INFO_BY_ID = new EnumMap<>(BookID.class);

	static {
		for (ESwordBookInfo ebi : BOOK_INFO) {
			BOOK_INFO_BY_ID.put(ebi.id, ebi);
		}
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		new StrippedDiffable().mergeIntroductionPrologs(bible);
		String filename = exportArgs[0];
		String marker = exportArgs.length == 1 ? "" : exportArgs[1];
		String title = bible.getName();

		try (BufferedWriter bblx = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(filename + ".bblx.HTM")), StandardCharsets.UTF_8));
				BufferedWriter cmtx = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(filename + ".cmtx.HTM")), StandardCharsets.UTF_8))) {

			bblx.write("<html><head>\n" +
					"<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" />\n" +
					"<style>\n" +
					"p{margin-top:0pt;margin-bottom:0pt;}\n" +
					"b.headline{font-size:14pt;}\n" +
					"sup.str{color:#008000;}\n" +
					".xref {color:#008000;font-weight:bold;text-decoration:underline;}\n" +
					"</style>\n" +
					"</head><body>\n" +
					"<p>#define description=" + title + marker + "</p>\n" +
					"<p>#define abbreviation=ChangeMe" + marker + "</p>\n" +
					"<p>#define comments=Exported by BibleMultiConverter" + marker + "</p>\n" +
					"<p>#define version=1" + marker + "</p>\n" +
					"<p>#define strong=0" + marker + "</p>\n" +
					"<p>#define right2left=0" + marker + "</p>\n" +
					"<p>#define ot=1" + marker + "</p>\n" +
					"<p>#define nt=1" + marker + "</p>\n" +
					"<p>#define font=DEFAULT" + marker + "</p>\n" +
					"<p>#define apocrypha=1" + marker + "</p>\n" +
					"<p><span style=\"background-color:#C80000;\">\u00F7</span>" + marker + "</p>\n");

			cmtx.write("<html><head>\n" +
					"<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" />\n" +
					"<style>\n" +
					"p{margin-top:0pt;margin-bottom:0pt;}\n" +
					"p.spc{margin-top:10pt;margin-bottom:0pt;}\n" +
					"p.prologend{border-width:1px;border-top-style:none;border-right-style:none;border-bottom-style:solid;border-left-style:none;border-color:black}\n" +
					"b.headline{font-size:14pt;}\n" +
					"sup.str{color:#008000;}\n" +
					"</style></head><body>\n" +
					"<p>#define description=" + title + " (Kommentar)" + marker + "</p>\n" +
					"<p>#define abbreviation=ChangeMe" + marker + "</p>\n" +
					"<p>#define comments=Exported by BibleMultiConverter" + marker + "</p>\n" +
					"<p>#define version=1" + marker + "</p>\r\n");

			for (Book book : bible.getBooks()) {
				ESwordBookInfo info = BOOK_INFO_BY_ID.get(book.getId());
				if (info == null) {
					System.out.println("WARNING: Skipping book " + book.getAbbr());
					continue;
				}
				String bname = info.name;
				int cnumber = 0;
				for (Chapter chapter : book.getChapters()) {
					cnumber++;
					if (cnumber > info.versification.length) {
						System.out.println("WARNING: Skipping chapter " + book.getAbbr() + " " + cnumber);
						continue;
					}
					int maxVerse = info.versification[cnumber - 1];
					BitSet allowedNumbers = new BitSet(maxVerse + 1);
					allowedNumbers.set(1, maxVerse + 1);
					FormattedText prolog = chapter.getProlog();
					for (VirtualVerse vv : chapter.createVirtualVerses(allowedNumbers)) {
						int vnumber = vv.getNumber();
						String vref = bname + " " + cnumber + ":" + vnumber;
						StringBuilder parsedVerse = new StringBuilder();
						StringBuilder parsedCommentary = new StringBuilder();
						for (Headline hl : vv.getHeadlines()) {
							parsedVerse.append("<b class=\"headline\">");
							hl.accept(new ESwordVisitor(parsedVerse, marker, book.getId().isNT(), "", "", null, null));
							parsedVerse.append("</b><br />");
						}
						boolean firstVerse = true;
						for (Verse v : vv.getVerses()) {
							if (!firstVerse || !v.getNumber().equals("" + vnumber)) {
								parsedVerse.append("<b>(" + v.getNumber() + ")</b>");
							}
							StringBuilder comments = new StringBuilder();
							if (prolog != null) {
								prolog.accept(new ESwordVisitor(comments, marker, book.getId().isNT(), "", "", "<i>", "</i>"));
								comments.append(marker + "</p>\n<!--keep--><p class=\"prologend\">&nbsp;" + marker + "</p>\n<p class=\"spc\">");
							}
							v.accept(new ESwordVisitor(parsedVerse, marker, book.getId().isNT(), "", "", null, null));
							v.accept(new ESwordVisitor(comments, marker, book.getId().isNT(), "<b>", "</b>", "", ""));
							if (comments.toString().contains("<!--keep-->"))
								parsedCommentary.append(comments.toString());
							firstVerse = false;
						}
						if (parsedVerse.length() == 0)
							parsedVerse.append("-");
						bblx.write("<p>" + vref + " " + parsedVerse.toString() + marker + "</p>\n");
						if (parsedCommentary.length() > 0)
							cmtx.write("<p><span style=\"background-color:#FF0000;\">\u00F7</span>" + vref + marker + "</p>\n<p>" + parsedCommentary.toString() + marker + "</p>\n");
						prolog = null;
					}
				}
			}
			bblx.write("</body></html>");
			cmtx.write("</body></html>");
		}
	}

	private static class ESwordBookInfo {
		private final BookID id;
		private final String name;
		private final int[] versification;

		public ESwordBookInfo(BookID id, String name, int... versification) {
			super();
			this.id = id;
			this.name = name;
			this.versification = versification;
		}
	}

	private static class ESwordVisitor implements Visitor<RuntimeException> {
		private final String suffix;
		private final String marker;
		private final boolean nt;
		private final String textPrefix;
		private final String textSuffix;
		private final String footnotePrefix;
		private final String footnoteSuffix;
		private final StringBuilder target;

		public ESwordVisitor(StringBuilder target, String marker, boolean nt, String textPrefix, String textSuffix, String footnotePrefix, String footnoteSuffix) {
			this.suffix = "";
			this.target = target;
			this.marker = marker;
			this.nt = nt;
			this.textPrefix = textPrefix;
			this.textSuffix = textSuffix;
			this.footnotePrefix = footnotePrefix;
			this.footnoteSuffix = footnoteSuffix;
		}

		public ESwordVisitor(String suffix, ESwordVisitor toCopy) {
			this.suffix = suffix;
			this.target = toCopy.target;
			this.marker = toCopy.marker;
			this.nt = toCopy.nt;
			this.textPrefix = toCopy.textPrefix;
			this.textSuffix = toCopy.textSuffix;
			this.footnotePrefix = toCopy.footnotePrefix;
			this.footnoteSuffix = toCopy.footnoteSuffix;
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			target.append("<b class=\"headline\">");
			return new ESwordVisitor("</b><br/>", this);
		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("{", "(").replace("}", ")").replaceAll("[ \t\r\n]+", " ");
			target.append(textPrefix + text + textSuffix);
		}

		@Override
		public Visitor<RuntimeException> visitFootnote(boolean ofCrossReferences) throws RuntimeException {
			Visitor<RuntimeException> result = visitFootnote0();
			if (ofCrossReferences)
				result.visitText(FormattedText.XREF_MARKER);
			return result;
		}

		public Visitor<RuntimeException> visitFootnote0() throws RuntimeException {
			if (footnotePrefix == null)
				return null;
			target.append(marker + "</p>\n<p><!--keep-->" + footnotePrefix);
			String suffix = footnoteSuffix + marker + "</p>\n<p class=\"spc\">";
			ESwordVisitor base = new ESwordVisitor(target, marker, nt, "", "", null, null);
			return new ESwordVisitor(suffix, base);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) {
			if (firstBook == lastBook  && !lastVerse.equals("*")) {
				return visitCrossReference0(firstBookAbbr, firstBook, firstChapter, firstVerse, lastChapter, lastVerse);
			} else {
				return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "cross", "reference");
			}
		}

		public Visitor<RuntimeException> visitCrossReference0(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			ESwordBookInfo info = BOOK_INFO_BY_ID.get(book);
			String bookName = info == null ? bookAbbr : info.name;
			target.append(textPrefix);
			target.append("<span class=\"xref\">" + bookName + "_" + firstChapter + ":" + firstVerse + "</span>");
			if (lastChapter != firstChapter || !lastVerse.equals(firstVerse))
				target.append("-<span class=\"xref\">" + bookName + "_" + lastChapter + ":" + lastVerse + "</span>");
			target.append(textSuffix);
			return null;
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			return visitCSSFormatting(kind.getCss());
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			target.append(textPrefix + "<span style=\"" + css + "\">");
			ESwordVisitor base = new ESwordVisitor(target, marker, nt, "", "", null, null);
			return new ESwordVisitor("</span>" + textSuffix, base);
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			Visitor<RuntimeException> v = visitCSSFormatting("color: #808080;");
			v.visitText("/");
			v.visitEnd();
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind lbk, int indent) {
			target.append("<br />");
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
			StringBuilder newSuffix = new StringBuilder();
			if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					newSuffix.append("<sup class=\"str\">" + Utils.formatStrongs(nt, i, strongsPrefixes, strongs, strongsSuffixes, "") + "</sup> ");
				}
			}
			return new ESwordVisitor(newSuffix.toString(), this);
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			return new ESwordVisitor("", this);
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			if (mode != RawHTMLMode.ONLINE && footnotePrefix != null)
				target.append(footnotePrefix + raw + footnoteSuffix);
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			throw new RuntimeException("Variations are not supported");
		}

		@Override
		public Visitor<RuntimeException> visitSpeaker(String labelOrStrongs) {
			return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "speaker", labelOrStrongs);
		}

		@Override
		public Visitor<RuntimeException> visitHyperlink(HyperlinkType type, String target) {
			return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "hyperlink", type.toString());
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			return prio.handleVisitor(category, new ESwordVisitor("", this));
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			target.append(suffix);
			return false;
		}
	}
}
