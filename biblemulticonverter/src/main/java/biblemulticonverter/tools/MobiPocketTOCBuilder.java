package biblemulticonverter.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.tools.MobiPocketBXR.BookInfo;

public class MobiPocketTOCBuilder implements Tool {

	public static final String[] HELP_TEXT = {
			"Usage: MobiPocketTOCBuilder <directory> [<bxrName>...]",
			"",
			"Create a MobiPocket TOC from BXR files created while exporting Bibles to MobiPocket format.",
			"<directory> is used for looking up the .bxr files (file name without .bxr extension)",
			"and to store the output files."
	};

	private static final String TITLE = "Die Bibel";

	@Override
	public void run(String... args) throws Exception {
		File directory = new File(args[0]);
		MobiPocketBXR[] crossrefs = new MobiPocketBXR[args.length - 1];
		Map<String, String> bookMap = new HashMap<String, String>();
		List<String> fullRefList = new ArrayList<String>();
		for (int i = 0; i < args.length - 1; i++) {
			String name = args[i + 1];
			if (name.length() > 22) {
				name = name.substring(0, 7) + "-" + name.substring(name.length() - 14);
				System.out.println(args[i] + " => " + name);
			}
			crossrefs[i] = new MobiPocketBXR(name, new File(directory, args[i + 1] + ".bxr"));
			for (BookInfo bi : crossrefs[i].books) {
				if (!bookMap.containsKey(bi.ref))
				{
					if (i != 0)
						System.out.println(crossrefs[i].name + ": " + bi.ref + "->" + bi.book);
					bookMap.put(bi.ref, bi.book);
					fullRefList.add(bi.ref);
				}
			}
		}
		if (fullRefList.contains("Einl")) {
			fullRefList.remove("Einl");
			fullRefList.add(0, "Einl");
		}

		try (final BufferedWriter opfw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, "BibleNavigation.opf")), "UTF-8"))) {
			opfw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n" +
					"<package unique-identifier=\"uid\">\r\n" +
					"  <metadata>\r\n" +
					"    <dc-metadata xmlns:dc=\"http://purl.org/metadata/dublin_core\" xmlns:oebpackage=\"http://openebook.org/namespaces/oeb-package/1.0/\">\r\n" +
					"      <dc:Title>" + TITLE + "</dc:Title>\r\n" +
					"      <dc:Language>de</dc:Language>\r\n" +
					"    </dc-metadata>\r\n" +
					"    <x-metadata>\r\n" +
					"      <DatabaseName>BibleNavigation</DatabaseName>\r\n" +
					"      <output encoding=\"Windows-1252\"></output>\r\n" +
					"    </x-metadata>\r\n" +
					"  </metadata>\r\n" +
					"  <manifest>\r\n" +
					"    <item id=\"onlyfile\" media-type=\"text/x-oeb1-document\" href=\"BibleNavigation.html\"></item>\r\n" +
					"  </manifest>\r\n" +
					"  <spine>\r\n" +
					"    <itemref idref=\"onlyfile\" />\r\n" +
					"  </spine>\r\n" +
					"  <tours></tours>\r\n" +
					"  <guide></guide>\r\n" +
					"</package>\r\n" +
					"");
		}

		try (final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, "BibleNavigation.html")), "UTF-8"))) {
			bw.write("<html><head>");
			bw.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">");
			bw.write("<style type=\"text/css\">body {font-family: Verdana, Arial, Helvetica, sans-serif}</style>");
			bw.write("<title>" + TITLE + "</title></head><body>");
			bw.newLine();
			bw.write("<h1>" + TITLE + "</h1>");
			bw.newLine();
			bw.write("<center><p><a href=\"#AT\">AT</a> - <a href=\"#NT\">NT</a></p></center>");
			bw.newLine();
			bw.write("<ul>");
			for (MobiPocketBXR crossref : crossrefs) {
				bw.write("<li><a href=\"oeb:redirect?title=Bible" + crossref.name + "\">" + crossref.title + "</a></li>");
				bw.newLine();
			}
			bw.write("</ul>");
			bw.newLine();
			bw.write("<mbp:pagebreak>");
			bw.newLine();
			bw.write("<h2><a name=\"AT\">AT</a></h2>");
			bw.newLine();
			bw.write("<p>");
			for (int i = 0; i < fullRefList.size(); i++) {
				String ref = fullRefList.get(i);
				String book = bookMap.get(ref);
				if (book.equals("EinlNT")) {
					bw.write("</p>");
					bw.newLine();
					bw.write("<mbp:pagebreak>");
					bw.newLine();
					bw.write("<h2><a name=\"NT\">NT</a></h2>");
					bw.newLine();
					bw.write("<p>");
				} else if (i != 0) {
					bw.write(" - ");
				}
				bw.write("<a href=\"#m" + ref + "\">" + book + "</a>");
			}
			bw.write("</p>");
			bw.newLine();
			bw.write("<mbp:pagebreak>");
			bw.newLine();
			String backlink = "AT";
			for (int i = 0; i < fullRefList.size(); i++) {
				String ref = fullRefList.get(i);
				String book = bookMap.get(ref);
				if (book.equals("EinlNT"))
					backlink = "NT";
				bw.write("<h2><a name=\"m" + ref + "\">" + book + "</a></h2>");
				bw.newLine();
				bw.write("<p><a href=\"#" + backlink + "\">zurück</a></p>");
				bw.newLine();
				bw.write("<p><a href=\"#b" + ref + "\">Einleitung</a></p>");
				bw.newLine();
				bw.write("<p>");
				int maxChapter = -1;
				for (MobiPocketBXR crossref : crossrefs) {
					BookInfo bi = getBook(crossref, ref);
					if (bi != null && bi.chapterCount > maxChapter)
						maxChapter = bi.chapterCount;
				}
				for (int j = 1; j <= maxChapter; j++) {
					if (j != 1)
						bw.write(" - ");
					bw.write("<a href=\"#b" + ref + "c" + j + "\">" + j + "</a>");
				}
				bw.write("</p>");
				bw.newLine();
				bw.write("<mbp:pagebreak>");
				bw.newLine();
				for (int j = 0; j <= maxChapter; j++) {
					String cref = "c" + j;
					if (j == 0) {
						cref = "";
						bw.write("<h2><a name=\"b" + ref + cref + "\" external=\"yes\">" + book + " (Einleitung)</a></h2>");
					} else {
						bw.write("<h2><a name=\"b" + ref + cref + "\" external=\"yes\">" + book + " " + j + "</a></h2>");
					}
					bw.newLine();
					bw.write("<p><a href=\"#m" + ref + "\">zurück</a></p>");
					bw.newLine();
					bw.write("<ul>");
					for (MobiPocketBXR crossref : crossrefs) {
						BookInfo bi = getBook(crossref, ref);
						if (bi == null || bi.chapterCount < j)
							continue;
						bw.write("<li><a href=\"oeb:redirect?title=Bible" + crossref.name + "#b" + ref + cref + "\">" + crossref.title + "</a></li>");
						bw.newLine();
					}
					bw.write("</ul>");
					bw.write("<mbp:pagebreak>");
					bw.newLine();
				}
			}
		}
	}

	private static BookInfo getBook(MobiPocketBXR bxr, String ref) {
		for (BookInfo bi : bxr.books) {
			if (bi.ref.equals(ref))
				return bi;
		}
		return null;
	}
}
