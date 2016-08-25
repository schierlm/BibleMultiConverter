package biblemulticonverter.neue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.MetadataBook.MetadataBookKey;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.format.ImportFormat;

public class NeUeParser implements ImportFormat {

	public static final String[] HELP_TEXT = {
			"Parse HTML from Neue Evangelistische Übersetzung",
	};

	private static BookMetadata[] METADATA = {
			new BookMetadata("1mo", "1Mo", BookID.BOOK_Gen),
			new BookMetadata("2mo", "2Mo", BookID.BOOK_Exod),
			new BookMetadata("3mo", "3Mo", BookID.BOOK_Lev),
			new BookMetadata("4mo", "4Mo", BookID.BOOK_Num),
			new BookMetadata("5mo", "5Mo", BookID.BOOK_Deut),
			new BookMetadata("jos", "Jos", BookID.BOOK_Josh),
			new BookMetadata("ri", "Ri", BookID.BOOK_Judg),
			new BookMetadata("rut", "Rut", BookID.BOOK_Ruth),
			new BookMetadata("1sam", "1Sam", BookID.BOOK_1Sam),
			new BookMetadata("2sam", "2Sam", BookID.BOOK_2Sam),
			new BookMetadata("1koe", "1Koe", BookID.BOOK_1Kgs),
			new BookMetadata("2koe", "2Koe", BookID.BOOK_2Kgs),
			new BookMetadata("1chr", "1Chr", BookID.BOOK_1Chr),
			new BookMetadata("2chr", "2Chr", BookID.BOOK_2Chr),
			new BookMetadata("esra", "Esra", BookID.BOOK_Ezra),
			new BookMetadata("neh", "Neh", BookID.BOOK_Neh),
			new BookMetadata("est", "Est", BookID.BOOK_Esth),
			new BookMetadata("hiob", "Hiob", BookID.BOOK_Job),
			new BookMetadata("ps", "Ps", BookID.BOOK_Ps),
			new BookMetadata("spr", "Spr", BookID.BOOK_Prov),
			new BookMetadata("pred", "Pred", BookID.BOOK_Eccl),
			new BookMetadata("hl", "Hl", BookID.BOOK_Song),
			new BookMetadata("jes", "Jes", BookID.BOOK_Isa),
			new BookMetadata("jer", "Jer", BookID.BOOK_Jer),
			new BookMetadata("kla", "Kla", BookID.BOOK_Lam),
			new BookMetadata("hes", "Hes", BookID.BOOK_Ezek),
			new BookMetadata("dan", "Dan", BookID.BOOK_Dan),
			new BookMetadata("hos", "Hos", BookID.BOOK_Hos),
			new BookMetadata("joel", "Joel", BookID.BOOK_Joel),
			new BookMetadata("amos", "Amos", BookID.BOOK_Amos),
			new BookMetadata("obadja", "Obadja", BookID.BOOK_Obad),
			new BookMetadata("jona", "Jona", BookID.BOOK_Jonah),
			new BookMetadata("mi", "Mi", BookID.BOOK_Mic),
			new BookMetadata("nah", "Nah", BookID.BOOK_Nah),
			new BookMetadata("hab", "Hab", BookID.BOOK_Hab),
			new BookMetadata("zef", "Zef", BookID.BOOK_Zeph),
			new BookMetadata("hag", "Hag", BookID.BOOK_Hag),
			new BookMetadata("sach", "Sach", BookID.BOOK_Zech),
			new BookMetadata("mal", "Mal", BookID.BOOK_Mal),
			new BookMetadata("mt", "Mt", BookID.BOOK_Matt),
			new BookMetadata("mk", "Mk", BookID.BOOK_Mark),
			new BookMetadata("lk", "Lk", BookID.BOOK_Luke),
			new BookMetadata("jo", "Jo", BookID.BOOK_John),
			new BookMetadata("apg", "Apg", BookID.BOOK_Acts),
			new BookMetadata("roe", "Roe", BookID.BOOK_Rom),
			new BookMetadata("1kor", "1Kor", BookID.BOOK_1Cor),
			new BookMetadata("2kor", "2Kor", BookID.BOOK_2Cor),
			new BookMetadata("gal", "Gal", BookID.BOOK_Gal),
			new BookMetadata("eph", "Eph", BookID.BOOK_Eph),
			new BookMetadata("phil", "Phil", BookID.BOOK_Phil),
			new BookMetadata("kol", "Kol", BookID.BOOK_Col),
			new BookMetadata("1thes", "1Thes", BookID.BOOK_1Thess),
			new BookMetadata("2thes", "2Thes", BookID.BOOK_2Thess),
			new BookMetadata("1tim", "1Tim", BookID.BOOK_1Tim),
			new BookMetadata("2tim", "2Tim", BookID.BOOK_2Tim),
			new BookMetadata("tit", "Tit", BookID.BOOK_Titus),
			new BookMetadata("phm", "Phm", BookID.BOOK_Phlm),
			new BookMetadata("hebr", "Hebr", BookID.BOOK_Heb),
			new BookMetadata("jak", "Jak", BookID.BOOK_Jas),
			new BookMetadata("1pt", "1Pt", BookID.BOOK_1Pet),
			new BookMetadata("2pt", "2Pt", BookID.BOOK_2Pet),
			new BookMetadata("1jo", "1Jo", BookID.BOOK_1John),
			new BookMetadata("2jo", "2Jo", BookID.BOOK_2John),
			new BookMetadata("3jo", "3Jo", BookID.BOOK_3John),
			new BookMetadata("jud", "Jud", BookID.BOOK_Jude),
			new BookMetadata("off", "Off", BookID.BOOK_Rev),
	};

	private static String[] JESUS_CHRONIK = {
			"00 jugend", "01 unterwegs", "02 entschluss",
			"03 monate", "04 woche", "05 auferst"
	};

	@Override
	public Bible doImport(File inputDirectory) throws Exception {
		Bible bible = new Bible("NeÜ bibel.heute (Neue evangelistische Übersetzung)");
		MetadataBook metadata = new MetadataBook();

		metadata.setValue(MetadataBookKey.description, "Neue evangelistische Übersetzung (NeÜ), eine Übertragung der Bibel ins heutige Deutsch.");
		metadata.setValue(MetadataBookKey.rights, "Copyright (c) Karl-Heinz Vanheiden, Ahornweg 3, 07926 Gefell. Sofern keine anderslautende schriftliche Genehmigung des Rechteinhabers vorliegt, darf dieses Werk zu privaten und gemeindlichen Zwecken verwendet, aber nicht verändert oder weitergegeben werden. " +
				"Eine Weitergabe auf körperlichen Datenträgern (Papier, CD, DVD, Stick o.ä.) bedarf zusätzlich einer Genehmigung der Christlichen Verlagsgesellschaft Dillenburg (http://cv-dillenburg.de/).");
		metadata.setValue(MetadataBookKey.source, "http://www.derbibelvertrauen.de/");
		metadata.setValue(MetadataBookKey.publisher, "Karl-Heinz Vanheiden");
		metadata.setValue(MetadataBookKey.language, "GER");
		bible.getBooks().add(metadata.getBook());

		String mainFile = "NeUe.htm";
		if (!new File(inputDirectory, mainFile).exists())
			mainFile = "index.htm";

		try (BufferedReader br = createReader(inputDirectory, mainFile)) {
			String line = br.readLine().trim();
			while (!line.startsWith("<p class=\"u3\">")) {
				if (line.contains("Textstand: ")) {
					line = line.substring(line.indexOf("Textstand: ") + 11);
					line = line.substring(0, line.indexOf('<'));
					metadata.setValue(MetadataBookKey.version, line);
					metadata.setValue(MetadataBookKey.date, new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
					metadata.setValue(MetadataBookKey.revision, line.replaceAll("[^0-9]+", ""));
					metadata.finished();
				}
				line = br.readLine().trim();
			}
			Pattern tocPattern = Pattern.compile("<a href=\"([^\"]+)\">([^<>]+)</a>&nbsp;&nbsp;(?:</p>)?");
			int bookIndex = 0, jcIndex = 0;
			while (!line.startsWith("<a name=\"vorwort\">")) {
				Matcher m = tocPattern.matcher(line);
				if (m.matches()) {
					String url = m.group(1);
					String shortName = replaceEntities(m.group(2));
					if (url.endsWith(".html#bb")) {
						String filename = url.substring(0, url.length() - 8);
						BookMetadata bm = METADATA[bookIndex];
						if (!bm.filename.equals(filename))
							throw new IOException(filename + "/" + bm.filename);
						bm.shortname = shortName;
						bookIndex++;
					} else if (url.startsWith("0")) {
						if (!url.equals(JESUS_CHRONIK[jcIndex] + ".html"))
							throw new IOException(url + "/" + JESUS_CHRONIK[jcIndex]);
						jcIndex++;
					} else {
						throw new IOException(url);
					}
				} else if (line.length() != 0 && !line.startsWith("<p class=\"u3\">") && !line.startsWith("///") && !line.equals("<p>&nbsp;</p>")) {
					throw new IOException(line);
				}
				line = br.readLine().trim();
			}
			if (bookIndex != METADATA.length)
				throw new IOException(bookIndex + " != " + METADATA.length);
			if (jcIndex == 0) JESUS_CHRONIK = new String[0];
			if (jcIndex != JESUS_CHRONIK.length)
				throw new IOException(jcIndex + " != " + JESUS_CHRONIK.length);

			// Vorwort
			Book vorwort = new Book("Vorwort", BookID.INTRODUCTION, "Vorwort", "Vorwort des Übersetzers");
			bible.getBooks().add(vorwort);
			Visitor<RuntimeException> vv = getPrologVisitor(vorwort);

			boolean needParagraph = false;
			while (!line.startsWith("<div align=\"right\">")) {
				line = line.replaceAll("<a name=\"[a-z]+\"></a>", "");

				if (line.startsWith("<h2>")) {
					if (!vorwort.getLongName().equals(replaceEntities(cutAffix(line, "<h2>", "</h2>"))))
						throw new IOException(replaceEntities(cutAffix(line, "<h2>", "</h2>")));
				} else if (line.startsWith("<h4>")) {
					parseFormattedText(vv.visitHeadline(1), cutAffix(line, "<h4>", "</h4>"), null, null);
					needParagraph = false;
				} else if (line.startsWith("<div class=\"fn\">")) {
					if (needParagraph)
						vv.visitLineBreak(LineBreakKind.PARAGRAPH);
					needParagraph = true;
					parseFormattedText(vv.visitFormattingInstruction(FormattingInstructionKind.ITALIC), cutAffix(line, "<div class=\"fn\">", "</div>"), null, null);
				} else if (line.startsWith("<p>")) {
					if (needParagraph)
						vv.visitLineBreak(LineBreakKind.PARAGRAPH);
					needParagraph = true;
					if (line.endsWith("<br />"))
						line += br.readLine().trim();
					parseFormattedText(vv, cutAffix(line, "<p>", "</p>"), null, null);
				} else {
					throw new IOException(line);
				}
				line = skipLines(br, "<p>&nbsp;</p>");
			}
			vorwort.getChapters().get(0).getProlog().finished();
		}

		for (BookMetadata bm : METADATA) {
			if (!new File(inputDirectory, bm.filename + ".html").exists()) {
				System.out.println("*** Skipping " + bm.filename + " - file not found ***");
				continue;
			}
			try (BufferedReader br = createReader(inputDirectory, bm.filename + ".html")) {
				String line = br.readLine().trim();
				line = skipLines(br, "<html>", "<head>", "<title>", "<meta ", "<link ", "</head>", "<body>", "<p class=\"u3\">", "<a href=\"", "\\\\\\");
				if (!line.equals("<p><a name=\"bb\">&nbsp;</a></p>"))
					throw new IOException(line);
				line = skipLines(br);
				Book bk = new Book(bm.abbr, bm.id, bm.shortname, replaceEntities(cutAffix(line, "<h1>", "</h1>")));
				bible.getBooks().add(bk);
				line = skipLines(br, "<p class=\"u3\">", "<a href=\"#", "</p>");
				FormattedText prolog = new FormattedText();
				prolog.getAppendVisitor().visitHeadline(1).visitText(replaceEntities(cutAffix(line, "<p class=\"u0\">", "</p>")));
				line = skipLines(br);
				boolean firstProlog = true;
				while (line.startsWith("<div class=\"e\">") && line.endsWith("</div>")) {
					if (firstProlog) {
						firstProlog = false;
					} else {
						prolog.getAppendVisitor().visitLineBreak(LineBreakKind.PARAGRAPH);
					}
					parseFormattedText(prolog.getAppendVisitor(), cutAffix(line, "<div class=\"e\">", "</div>"), bm, null);
					line = skipLines(br);
				}
				if (firstProlog)
					throw new IOException(line);
				prolog.getAppendVisitor().visitLineBreak(LineBreakKind.PARAGRAPH);
				parseFormattedText(prolog.getAppendVisitor().visitFormattingInstruction(FormattingInstructionKind.BOLD).visitFormattingInstruction(FormattingInstructionKind.ITALIC), cutAffix(line, "<p class=\"u1\">", "</p>"), bm, null);
				prolog.finished();
				line = skipLines(br);
				if (!line.startsWith("<h"))
					throw new IOException(line);
				char minHeadline = line.charAt(2);
				List<Headline> headlines = new ArrayList<>();
				boolean inParagraph = false;
				Chapter currentChapter = null;
				Verse currentVerse = null;
				List<Visitor<RuntimeException>> footnotes = new ArrayList<>();
				List<String> footnoteVerses = new ArrayList<>();
				while (!line.equals("<hr>")) {
					if (line.startsWith("<p>&nbsp;</p>")) {
						line = line.substring(13).trim();
						if (line.length() == 0)
							line = skipLines(br);
						continue;
					}
					String restLine = null;
					List<Visitor<RuntimeException>> newFootnotes = new ArrayList<>();
					while (line.matches("<[a-z0-9]+ (class=\"[^\"]+\" )?id=\"[a-z0-9]+\"[> ].*"))
						line = line.replaceFirst(" id=\"[a-z0-9]+\"", "");
					if (line.startsWith("<p class=\"poet\">")) {
						line = "<p>" + line.substring(16);
					}
					if (line.matches(".*</p>.+")) {
						int pos = line.indexOf("</p>");
						restLine = line.substring(pos + 4).trim();
						line = line.substring(0, pos + 4);
					}
					if (!inParagraph && line.startsWith("<p>")) {
						inParagraph = true;
						line = line.substring(3).trim();
						if (line.length() == 0) {
							line = skipLines(br);
							continue;
						}
					}
					if (line.indexOf("<span class=\"vers\">", 1) != -1) {
						int pos = line.indexOf("<span class=\"vers\">", 1);
						restLine = line.substring(pos) + (restLine == null ? "" : restLine);
						line = line.substring(0, pos).trim();
					}
					if (line.indexOf("<p class=\"poet\">", 1) != -1) {
						int pos = line.indexOf("<p class=\"poet\">", 1);
						restLine = line.substring(pos) + (restLine == null ? "" : restLine);
						line = line.substring(0, pos).trim();
					}
					while (line.endsWith("&nbsp;"))
						line = line.substring(0, line.length() - 6);
					if (!inParagraph && (line.startsWith("<h2>") || line.startsWith("<h3>") || line.startsWith("<h4>"))) {
						Headline hl = new Headline(line.charAt(2) - minHeadline + 1);
						String headline = cutAffix(line, line.substring(0, 4), "</" + line.substring(1, 4));
						if (headline.contains("*"))
							throw new IOException(headline);
						hl.getAppendVisitor().visitText(replaceEntities(headline));
						headlines.add(hl);
					} else if (inParagraph && line.startsWith("<span class=\"vers\">")) {
						int pos = line.indexOf("</span>");
						if (pos == -1)
							throw new IOException(line);
						String vs = line.substring(19, pos).trim();
						if (vs.matches("[0-9]+(,[0-9]+)?")) {
							currentVerse = new Verse(vs);
						} else {
							throw new IOException(vs);
						}
						line = line.substring(pos + 7);
						if (line.endsWith("</p>")) {
							inParagraph = false;
							line = line.substring(0, line.length() - 4);
						}
						line = line.trim();
						for (Headline h : headlines) {
							h.accept(currentVerse.getAppendVisitor().visitHeadline(h.getDepth()));
						}
						headlines.clear();
						parseFormattedText(currentVerse.getAppendVisitor(), line, bm, newFootnotes);
						if (!inParagraph)
							currentVerse.getAppendVisitor().visitLineBreak(LineBreakKind.PARAGRAPH);
						currentChapter.getVerses().add(currentVerse);
					} else if (inParagraph && line.startsWith("<a href=\"#top\"><span class=\"kap\">")) {
						int chap = Integer.parseInt(cutAffix(line, "<a href=\"#top\"><span class=\"kap\">", "</span></a>"));
						currentChapter = new Chapter();
						currentVerse = null;
						bk.getChapters().add(currentChapter);
						if (chap != bk.getChapters().size())
							throw new IOException(chap + "/" + bk.getChapters().size());
						if (prolog != null) {
							currentChapter.setProlog(prolog);
							prolog = null;
						}
					} else if (!inParagraph && line.startsWith("<div class=\"fn\">")) {
						String content = cutAffix(line, "<div class=\"fn\">", "</div>");
						if (footnoteVerses.size() == 0)
							throw new IOException(line);
						String prefix = footnoteVerses.remove(0) + ":";
						if (!content.startsWith(prefix)) {
							throw new IOException(prefix + " / " + content);
						}
						parseFormattedText(footnotes.remove(0), content.substring(prefix.length()).trim(), bm, null);
					} else if (inParagraph && !line.isEmpty() && (!line.startsWith("<") && !line.startsWith("&nbsp;") || line.startsWith("<span class=\"u2\">"))) {
						if (line.endsWith("</p>")) {
							inParagraph = false;
							line = line.substring(0, line.length() - 4);
						}
						line = line.trim();
						parseFormattedText(currentVerse.getAppendVisitor(), line, bm, newFootnotes);
						if (!inParagraph)
							currentVerse.getAppendVisitor().visitLineBreak(LineBreakKind.PARAGRAPH);
					} else {
						System.err.println("Next line: " + br.readLine());
						throw new IOException(line);
					}
					if (!newFootnotes.isEmpty()) {
						footnotes.addAll(newFootnotes);
						for (int i = 0; i < newFootnotes.size(); i++) {
							if (currentVerse.getNumber().contains(",")) {
								footnoteVerses.add(currentVerse.getNumber());
							} else {
								footnoteVerses.add(bk.getChapters().size() + "," + currentVerse.getNumber());
							}
						}
					}
					if (restLine != null)
						line = restLine;
					else
						line = skipLines(br);
				}
				if (!headlines.isEmpty())
					throw new IOException("" + headlines.size());
				if (!footnotes.isEmpty() || !footnoteVerses.isEmpty())
					throw new IOException(footnotes.size() + "/" + footnoteVerses.size());
				for (Chapter ch : bk.getChapters()) {
					for (Verse vv : ch.getVerses()) {
						vv.trimWhitespace();
						vv.finished();
					}
				}
			}
		}

		// Anhang
		Book anhang = new Book("Anhang", BookID.APPENDIX, "Anhang", "Anhang");
		bible.getBooks().add(anhang);
		Visitor<RuntimeException> vv = getPrologVisitor(anhang);
		vv.visitHeadline(1).visitText("Ausblick auf die ganze Bibel");
		try (BufferedReader br = createReader(inputDirectory, "bibel.html")) {
			String line = br.readLine().trim();
			while (!line.startsWith("<a name=\"at\">")) {
				line = br.readLine().trim();
			}
			while (!line.equals("</body>")) {
				line = line.replaceAll("<a name=\"[a-z]+\"></a>", "");
				line = line.replaceAll("> +<", "><");
				line = line.replace("<td valign=\"top\"><br /><br /><a href", "<td valign=\"top\"><a href");
				if (line.startsWith("<h2>")) {
					parseFormattedText(vv.visitHeadline(2), cutAffix(line, "<h2>", "</h2>"), null, null);
				} else if (line.startsWith("<a href=\"#top\"><h2>")) {
					parseFormattedText(vv.visitHeadline(2), cutAffix(line, "<a href=\"#top\"><h2>", "</h2></a>"), null, null);
				} else if (line.startsWith("<h3>")) {
					parseFormattedText(vv.visitHeadline(3), cutAffix(line, "<h3>", "</h3>"), null, null);
				} else if (line.startsWith("<a href=\"#top\"><h3>")) {
					parseFormattedText(vv.visitHeadline(3), cutAffix(line, "<a href=\"#top\"><h3>", "</h3></a>"), null, null);
				} else if (line.startsWith("<td valign=\"top\"><a href=\"")) {
					String[] parts = cutAffix(line, "<td valign=\"top\"><a href=\"", "</a></td>").split(".html\">", 2);
					line = br.readLine().trim().replaceAll("> +<", "><").replace("html#u", "html");
					if (line.contains("<td><br /><br /><a href")) {
						vv.visitLineBreak(LineBreakKind.PARAGRAPH);
						line = line.replace("<td><br /><br /><a href", "<td><a href");
					}
					String title = cutAffix(line, "<td><a href=\"" + parts[0] + ".html\">", "</a><br />");
					Visitor<RuntimeException> bold = vv.visitFormattingInstruction(FormattingInstructionKind.BOLD);
					BookMetadata m = null;
					for (BookMetadata bm : METADATA) {
						if (bm.filename.equals(parts[0])) {
							m = bm;
							break;
						}
					}
					bold.visitCrossReference(m.abbr, m.id, 1, "1", 1, "1").visitText(replaceEntities(parts[1].replace("-", "")));
					bold.visitText(" " + replaceEntities(title));
					vv.visitLineBreak(LineBreakKind.NEWLINE);

					line = br.readLine().trim();
					while (!line.endsWith("</td>"))
						line += " " + br.readLine().trim();
					vv.visitText(replaceEntities(cutAffix(line, "", "</td>")));
					vv.visitLineBreak(LineBreakKind.PARAGRAPH);
					line = br.readLine().trim();
					if (!line.equals("</tr>"))
						throw new IOException(line);
				} else {
					throw new IOException(line);
				}
				line = skipLines(br, "<table border=\"0\" width=\"350\">", "<colgroup>",
						"<p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p>",
						"</colgroup>", "<col ", "<tr>", "</table>");
			}
		}

		// Hesekiels Tempel
		vv.visitHeadline(1).visitText("Hesekiels Tempel");
		Visitor<RuntimeException> vvv = vv.visitFormattingInstruction(FormattingInstructionKind.LINK);
		vvv.visitRawHTML(RawHTMLMode.OFFLINE, "<a href=\"http://www.alt.kh-vanheiden.de/NeUe/Bibeltexte/Hesekiels%20Tempel.gif\" target=\"_blank\">");
		vvv.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText("Rekonstruktionszeichnung");
		vvv.visitRawHTML(RawHTMLMode.OFFLINE, "</a>");
		vv.visitRawHTML(RawHTMLMode.ONLINE, "<br /><img src=\"http://www.alt.kh-vanheiden.de/NeUe/Bibeltexte/Hesekiels%20Tempel.gif\" width=\"640\" height=\"635\">");

		// Jesus-Chronik
		if (JESUS_CHRONIK.length > 0)
			vv.visitHeadline(1).visitText("Die Jesus-Chronik");
		for (String name : JESUS_CHRONIK) {
			if (!new File(inputDirectory, name + ".html").exists()) {
				System.out.println("*** Skipping " + name + " - file not found ***");
				continue;
			}
			try (BufferedReader br = createReader(inputDirectory, name + ".html")) {
				String line = skipLines(br, "<html>", "<head>",
						"<title> Die Jesus-Biografie</title>",
						"<link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\">",
						"</head>", "<body>");

				List<Visitor<RuntimeException>> footnoteList = new ArrayList<>();
				List<String> footnotePrefixes = new ArrayList<>();
				while (!line.startsWith("</body>")) {
					line = line.replaceAll("<a name=\"[a-z]+\"></a>", "");
					if (line.startsWith("<h2>")) {
						parseFormattedText(vv.visitHeadline(2), cutAffix(line, "<h2>", "</h2>"), null, null);
					} else if (line.startsWith("<div class=\"fn\">")) {
						while (!line.endsWith("</div>"))
							line += " " + br.readLine().trim();
						String[] fns = cutAffix(line, "<div class=\"fn\">", "</div>").split("<br />");
						for (String fn : fns) {
							fn = fn.trim();
							String pfx = footnotePrefixes.remove(0);
							Visitor<RuntimeException> fnv = footnoteList.remove(0);
							if (!fn.startsWith(pfx))
								throw new IOException(pfx + " / " + fn);
							parseFormattedText(fnv, cutAffix(fn, pfx, ""), null, null);
						}
					} else if (line.startsWith("<p><div class=\"rot\">")) {
						String text = cutAffix(line, "<p><div class=\"rot\">", "<!--/DATE--></div></p>").replace("<!--DATE-->", "");
						parseFormattedText(vv.visitFormattingInstruction(FormattingInstructionKind.ITALIC), text, null, null);
						vv.visitLineBreak(LineBreakKind.PARAGRAPH);
					} else if (line.startsWith("<p><b>") && line.contains("</b><br />")) {
						int pos = line.indexOf("</b><br />");
						parseJesusChronikText(vv.visitHeadline(3), line.substring(6, pos), footnotePrefixes, footnoteList);
						String xref =  cutAffix(line.substring(pos),"</b><br />", "</p>");
						if (!xref.isEmpty())
							parseJesusChronikText(vv.visitFormattingInstruction(FormattingInstructionKind.ITALIC),xref, footnotePrefixes, footnoteList);
						vv.visitLineBreak(LineBreakKind.PARAGRAPH);
					} else if (line.startsWith("<p>")) {
						parseJesusChronikText(vv, cutAffix(line, "<p>", "</p>"), footnotePrefixes, footnoteList);
						vv.visitLineBreak(LineBreakKind.PARAGRAPH);
					} else if (line.startsWith("&copy;")) {
						while (!line.endsWith("</div>"))
							line += " " + br.readLine().trim();
						parseFormattedText(vv, cutAffix(line, "", "</div>"), null, null);
						vv.visitLineBreak(LineBreakKind.PARAGRAPH);
					} else if (line.startsWith("<div class=\"e\">")) {
						while (!line.endsWith("</div>"))
							line += " " + br.readLine().trim();
						parseFormattedText(vv.visitFormattingInstruction(FormattingInstructionKind.ITALIC), cutAffix(line, "<div class=\"e\">", "</div>"), null, null);
						vv.visitLineBreak(LineBreakKind.PARAGRAPH);
					} else {
						throw new IOException(line);
					}
					line = skipLines(br);
				}
				if (!footnoteList.isEmpty() || !footnotePrefixes.isEmpty())
					throw new IOException(footnoteList.size() + " / " + footnotePrefixes.size());
			}
		}
		anhang.getChapters().get(0).getProlog().trimWhitespace();
		anhang.getChapters().get(0).getProlog().finished();

		return bible;
	}


	private Visitor<RuntimeException> getPrologVisitor(Book book) {
		FormattedText prolog = new FormattedText();
		book.getChapters().add(new Chapter());
		book.getChapters().get(0).setProlog(prolog);
		return prolog.getAppendVisitor();
	}

	private String cutAffix(String line, String prefix, String suffix) throws IOException {
		if (!line.startsWith(prefix) || !line.endsWith(suffix))
			throw new IOException(line);
		return line.substring(prefix.length(), line.length() - suffix.length()).trim();
	}

	private void parseFormattedText(Visitor<RuntimeException> v, String html, BookMetadata bm, List<Visitor<RuntimeException>> footnoteList) throws IOException {
		if (parseFormattedText(0, v, html, bm, footnoteList) != html.length())
			throw new IOException(html);
	}

	private int parseFormattedText(int pos, Visitor<RuntimeException> v, String html, BookMetadata bm, List<Visitor<RuntimeException>> footnoteList) throws IOException {
		int tagPos = html.indexOf('<', pos);
		while (tagPos != -1) {
			parseText(v, replaceEntities(html.substring(pos, tagPos)), footnoteList);
			if (html.startsWith("</", tagPos)) {
				return tagPos;
			} else if (html.startsWith("<b>", tagPos)) {
				pos = parseFormattedText(tagPos + 3, v.visitFormattingInstruction(FormattingInstructionKind.BOLD), html, bm, footnoteList);
				if (!html.startsWith("</b>", pos))
					throw new IOException(html.substring(pos));
				pos += 4;
			} else if (html.startsWith("<i>", tagPos)) {
				pos = parseFormattedText(tagPos + 3, v.visitFormattingInstruction(FormattingInstructionKind.ITALIC), html, bm, footnoteList);
				if (!html.startsWith("</i>", pos))
					throw new IOException(html.substring(pos));
				pos += 4;
			} else if (html.startsWith("<u>", tagPos)) {
				pos = parseFormattedText(tagPos + 3, v.visitFormattingInstruction(FormattingInstructionKind.UNDERLINE), html, bm, footnoteList);
				if (!html.startsWith("</u>", pos))
					throw new IOException(html.substring(pos));
				pos += 4;
			} else if (html.startsWith("<em>", tagPos)) {
				pos = parseFormattedText(tagPos + 4, v.visitFormattingInstruction(FormattingInstructionKind.ITALIC), html, bm, footnoteList);
				if (!html.startsWith("</em>", pos))
					throw new IOException(html.substring(pos));
				pos += 5;
			} else if (html.startsWith("<span class=\"u2\">", tagPos)) {
				pos = parseFormattedText(tagPos + 17, v.visitFormattingInstruction(FormattingInstructionKind.BOLD), html, bm, footnoteList);
				if (!html.startsWith("</span>", pos))
					throw new IOException(html.substring(pos));
				pos += 7;
			} else if (html.startsWith("<span class=\"i\">", tagPos)) {
				pos = parseFormattedText(tagPos + 16, v.visitFormattingInstruction(FormattingInstructionKind.ITALIC), html, bm, footnoteList);
				if (!html.startsWith("</span>", pos))
					throw new IOException(html.substring(pos));
				pos += 7;
			} else if (html.startsWith("<a href=\"", tagPos)) {
				int pos1 = html.indexOf("\">", tagPos);
				int pos2 = html.indexOf("</a>", pos1);
				if (pos1 == -1 || pos2 == -1)
					throw new IOException(html.substring(tagPos));
				String filename;
				String chapter, toChapter;
				String text = replaceEntities(html.substring(pos1 + 2, pos2).trim());
				String verse, toVerse;
				String linkTarget = html.substring(tagPos + 9, pos1);
				if (linkTarget.startsWith("NeUe.htm") || linkTarget.startsWith("index.htm") || linkTarget.endsWith("derbibelvertrauen.de")) {
					filename = null;
					verse = chapter = toVerse = toChapter = null;
				} else if (linkTarget.endsWith(".html")) {
					verse = chapter = toVerse = toChapter = "1";
					filename = linkTarget;
				} else {
					Matcher target = Utils.compilePattern("([0-9a-z]+\\.html)?#([0-9]+)").matcher(linkTarget);
					Matcher desc = Utils.compilePattern("[^<>]*?([0-9]+(?:[-+][0-9]+)?)(, ?[0-9]+(?:-[0-9]+)?)?(?:\\.[0-9.-]*)?(?: – [0-9]+,[0-9]+)?").matcher(text);
					if (!target.matches() || !desc.matches())
						throw new IOException(html.substring(tagPos));
					filename = target.group(1);
					chapter = target.group(2);
					toChapter = chapter;
					String chapter2 = desc.group(1);
					if (chapter2.contains("-")) {
						String[] chapters = chapter2.split("-");
						chapter2 = chapters[0];
						toChapter = chapters[1];
					} else if (chapter2.contains("+")) {
						chapter2 = chapter2.substring(0, chapter2.indexOf("+"));
					}
					verse = desc.group(2);
					if (!chapter.equals(chapter2)) {
						throw new IOException(target.group() + " / " + desc.group() + " / " + chapter + " != " + chapter2);
					}
					if (verse == null) {
						verse = "1";
						toVerse = "1";
					} else if (verse.contains("-")) {
						String[] verses = verse.substring(1).trim().split("-");
						verse = verses[0];
						toVerse = verses[1];
					} else {
						toVerse = verse = verse.substring(1).trim();
					}
				}
				if (verse == null) {
					v.visitFormattingInstruction(FormattingInstructionKind.LINK).visitText(text);
				} else {
					BookMetadata m = null;
					if (filename == null) {
						m = bm;
					} else {
						for (BookMetadata mm : METADATA) {
							if (filename.equals(mm.filename + ".html")) {
								m = mm;
								break;
							}
						}
					}
					if (m == null)
						throw new IOException(filename);
					if (footnoteList != null) {
						throw new IOException("Cross reference in verse: " + html);
					}
					v.visitCrossReference(m.abbr, m.id, Integer.parseInt(chapter), verse, Integer.parseInt(toChapter), toVerse).visitText(text);
				}
				pos = pos2 + 4;
			} else if (html.startsWith("<br />", tagPos)) {
				v.visitLineBreak(LineBreakKind.NEWLINE);
				pos = tagPos + 6;
			} else if (html.startsWith("<a name=\"", tagPos)) {
				if (!html.substring(tagPos).matches("<a name=\"[a-z0-9]+\"></a>"))
					throw new IOException(html.substring(tagPos));
				pos = html.indexOf("</a>", tagPos) + 4;
			} else {
				throw new IOException(html.substring(tagPos));
			}
			tagPos = html.indexOf('<', pos);
		}
		parseText(v, replaceEntities(html.substring(pos)), footnoteList);
		return html.length();
	}

	private void parseJesusChronikText(Visitor<RuntimeException> vv, String text, List<String> footnotePrefixes, List<Visitor<RuntimeException>> footnoteList) throws IOException {
		if (text.contains("<span class=\"f\">")) {
			StringBuffer sb = new StringBuffer();
			Matcher m = Pattern.compile("<span class=\"f\">(\\([a-z]\\))</span>").matcher(text);
			while (m.find()) {
				footnotePrefixes.add(m.group(1));
				m.appendReplacement(sb, "*");
			}
			m.appendTail(sb);
			text = sb.toString();
		}
		parseFormattedText(vv, text, null, footnoteList);
	}

	private void parseText(Visitor<RuntimeException> v, String text, List<Visitor<RuntimeException>> footnoteList) throws IOException {
		if (footnoteList == null || (!text.contains("*") && !text.contains(" / "))) {
			v.visitText(text);
			return;
		}
		while (true) {
			int pos = text.indexOf('*');
			int pos2 = text.indexOf(" / ");
			if (pos2 != -1 && (pos == -1 || pos2 < pos))
				pos = pos2;
			if (pos == -1)
				break;
			v.visitText(text.substring(0, pos));
			if (text.startsWith("*", pos)) {
				footnoteList.add(v.visitFootnote());
				text = text.substring(pos + 1);
			} else if (text.startsWith(" / ", pos)) {
				v.visitText(" ");
				v.visitVerseSeparator();
				v.visitText(" ");
				text = text.substring(pos + 3);
			} else {
				throw new IOException(text.substring(pos));
			}
		}
		v.visitText(text);
	}

	/**
	 * Skip lines that are empty or start with one of the prefixes
	 */
	private String skipLines(BufferedReader br, String... prefixes) throws IOException {
		String line = br.readLine().trim();
		while (true) {
			boolean skip = line.length() == 0;
			for (String pfx : prefixes) {
				if (skip)
					break;
				skip = line.startsWith(pfx);
			}
			if (!skip)
				break;
			line = br.readLine().trim();
		}
		return line.replace("<img src=\"note.png\" width=\"10\" height=\"10\">", "\u266A");
	}

	private static String replaceEntities(String text) throws IOException {
		if (text.contains("<") || text.contains(">") || text.contains("\0"))
			throw new IOException(text);
		text = text.replace('\t', ' ').replaceAll("  +", " ");
		if (!text.contains("&"))
			return text;
		text = text.replace("&amp;", "\0").replace("&lt;", "<").replace("&gt;", ">");
		text = text.replace("&Auml;", "Ä").replace("&Ouml;", "Ö").replace("&Uuml;", "Ü");
		text = text.replace("&auml;", "ä").replace("&ouml;", "ö").replace("&uuml;", "ü");
		text = text.replace("&szlig;", "ß").replace("&iuml;", "ï").replace("&euml;", "ë");
		text = text.replace("&Aacute;", "Á").replace("&iacute;", "í").replace("&aacute;", "á");
		text = text.replace("&eacute;", "é").replace("&copy;", "©");
		if (text.contains("&"))
			throw new IOException(text);
		return text.replace("\0", "&");
	}

	private BufferedReader createReader(File directory, String filename) throws IOException {
		System.out.println("=== " + filename + " ===");
		return new BufferedReader(new InputStreamReader(new FileInputStream(new File(directory, filename)), "windows-1252"));
	}

	private static class BookMetadata {
		private final String abbr;
		private final BookID id;
		private final String filename;
		private String shortname;

		public BookMetadata(String filename, String abbr, BookID id) {
			this.filename = filename;
			this.abbr = abbr;
			this.id = id;
		}
	}
}
