package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;

public class HeatMapHTML implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Create HTML page that shows which verses contain certain features.",
			"",
			"Usage: HeatMapHTML <outfile> <title> <categorization> [+ <categorization> [...]]",
			"Categorization format: <title> <color1> <name1> <regexp1> [<color2> <name2> <regexp2> [...]] <defaultColor> <defaultName>",
			"",
			"This export format will create a HTML page which graphically shows existence of",
			"words/features across the books/chapters of a bible.",
			"",
			"Multiple categorizations can be given, separated by an argument that only consists",
			"of a '+' sign. Each categorization will assign a color (like #FF0000) and name to a verse whose",
			"Diffable export string (without the verse number) matches the given regexp. When",
			"more than one regexp matches, the first one wins. When none matches, default",
			"color and title are assigned."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		for (int i = 2; i < exportArgs.length; i++) {
			if (exportArgs[i].startsWith("@@")) {
				List<String> spliceList = Files.readAllLines(new File(exportArgs[i].substring(2)).toPath(), StandardCharsets.UTF_8);
				String[] newExportArgs = new String[exportArgs.length - 1 + spliceList.size()];
				System.arraycopy(exportArgs, 0, newExportArgs, 0, i);
				System.arraycopy(spliceList.toArray(new String[spliceList.size()]), 0, newExportArgs, i, spliceList.size());
				System.arraycopy(exportArgs, i + 1, newExportArgs, i + spliceList.size(), exportArgs.length - i - 1);
				exportArgs = newExportArgs;
			}
		}
		if (exportArgs.length < 8) {
			System.out.println("Usage: HeatMapHTML <outfile> <title> <categorization> [+ <categorization> [...]]");
			System.out.println("Categorization format: <title> <color1> <name1> <regexp1> [<color2> <name2> <regexp2> [...]] <defaultColor> <defaultName>");
			System.out.println("Each categorization can also be given as @@<filename>, where the file contains one parameter per line (to simplify escaping).");
			return;
		}
		File outputFile = new File(exportArgs[0]);
		String title = exportArgs[1];
		List<Categorization> categorizationList = new ArrayList<>();
		int firstArg = 2;
		for (int i = 8; i < exportArgs.length; i += 3) {
			if (exportArgs[i].equals("+")) {
				categorizationList.add(parseCategorization(exportArgs, firstArg, i));
				firstArg = i + 1;
				i += 4;
			}
		}
		if ((exportArgs.length - firstArg) % 3 != 0) {
			System.out.println("Categorization format: <title> <color1> <name1> <regexp1> [<color2> <name2> <regexp2> [...]] <defaultColor> <defaultName>");
			return;
		}
		categorizationList.add(parseCategorization(exportArgs, firstArg, exportArgs.length));
		Categorization[] categorizations = categorizationList.toArray(new Categorization[categorizationList.size()]);
		StringWriter sw = new StringWriter();
		new Diffable().doExport(bible, sw);
		String[] lines = sw.toString().split("\n");
		int nextLine = skipNonVerses(lines, 1);
		Map<BookID, int[][][]> rawDataPerBook = new EnumMap<>(BookID.class);
		for (Book book : bible.getBooks()) {
			boolean containsVerses = false;
			int[][][] rawData = new int[book.getChapters().size()][][];
			for (int i = 0; i < book.getChapters().size(); i++) {
				List<Verse> verses = book.getChapters().get(i).getVerses();
				if (verses.isEmpty())
					continue;
				containsVerses = true;
				rawData[i] = new int[categorizations.length][verses.size()];
				for (int j = 0; j < verses.size(); j++) {
					Verse v = verses.get(j);
					String prefix = book.getAbbr() + " " + (i + 1) + ":" + v.getNumber() + " ";
					int firstLine = nextLine;
					if (nextLine == lines.length)
						throw new RuntimeException(prefix);
					while (nextLine < lines.length && lines[nextLine].startsWith(prefix))
						nextLine++;
					if (nextLine == firstLine)
						throw new RuntimeException(prefix + "|" + lines[nextLine]);
					for (int k = 0; k < categorizations.length; k++) {
						int value = categorizations[k].regexes.length;
						for (int r = 0; r < categorizations[k].regexes.length; r++) {
							for (int l = firstLine; l < nextLine; l++) {
								if (categorizations[k].regexes[r].matcher(lines[l]).find()) {
									value = r;
									break;
								}
							}
							if (value == r)
								break;
						}
						rawData[i][k][j] = value;
					}
					nextLine = skipNonVerses(lines, nextLine);
				}
			}
			if (containsVerses) {
				rawDataPerBook.put(book.getId(), rawData);
			}
		}
		if (nextLine != lines.length)
			throw new RuntimeException(lines[nextLine]);
		build(outputFile, bible, title, categorizations, rawDataPerBook);
	}

	private Categorization parseCategorization(String[] exportArgs, int firstArg, int lastArg) {
		if ((lastArg - firstArg) % 3 != 0)
			throw new IllegalArgumentException();
		int colorcount = (lastArg - firstArg) / 3;
		String[] colors = new String[colorcount], names = new String[colorcount];
		Pattern[] regexes = new Pattern[colorcount - 1];
		for (int i = 0; i < colorcount; i++) {
			colors[i] = exportArgs[firstArg + 3 * i + 1];
			if (!colors[i].matches("#[0-9a-fA-F]{6}"))
				throw new IllegalArgumentException("Invalid HTML color: " + colors[i]);
			names[i] = exportArgs[firstArg + 3 * i + 2];
			if (i != colorcount - 1)
				regexes[i] = Pattern.compile(exportArgs[firstArg + 3 * i + 3]);
		}
		return new Categorization(exportArgs[firstArg], colors, names, regexes);
	}

	private int skipNonVerses(String[] lines, int line) {
		while (line < lines.length && !lines[line].matches(Utils.BOOK_ABBR_REGEX + " [0-9]+:" + Utils.VERSE_REGEX + " .*"))
			line++;
		return line;
	}

	private void build(File outputFile, Bible bible, String title, Categorization[] categorizations, Map<BookID, int[][][]> rawDataPerBook) throws Exception {
		List<Section> otBooks = new ArrayList<>(), ntBooks = new ArrayList<>();
		for (Book book : bible.getBooks()) {
			int[][][] rawData = rawDataPerBook.get(book.getId());
			if (rawData == null)
				continue;
			List<Section> bookList = book.getId().isNT() ? ntBooks : otBooks;
			int chapCount = book.getChapters().size();
			if (chapCount == 1) {
				if (!book.getChapters().get(0).getVerses().isEmpty()) {
					String[] verseNumbers = new String[book.getChapters().get(0).getVerses().size()];
					for (int i = 0; i < verseNumbers.length; i++) {
						verseNumbers[i] = book.getChapters().get(0).getVerses().get(i).getNumber();
					}
					bookList.add(new Section(book.getShortName(), rawData[0], verseNumbers, categorizations));
				}
			} else {
				List<Section> chapterList = new ArrayList<>();
				for (int i = 0; i < chapCount; i++) {
					if (!book.getChapters().get(i).getVerses().isEmpty()) {
						String[] verseNumbers = new String[book.getChapters().get(i).getVerses().size()];
						for (int j = 0; j < verseNumbers.length; j++) {
							verseNumbers[j] = book.getChapters().get(i).getVerses().get(j).getNumber();
						}
						chapterList.add(new Section(book.getShortName() + " " + (i + 1), rawData[i], verseNumbers, categorizations));
					}
				}
				if (!chapterList.isEmpty())
					bookList.add(new Section(book.getShortName(), chapterList));
			}
		}
		List<Section> testaments = new ArrayList<>();
		if (!otBooks.isEmpty())
			testaments.add(new Section(System.getProperty("heatmap.label.ot", "Old Testament"), otBooks));
		if (!ntBooks.isEmpty())
			testaments.add(new Section(System.getProperty("heatmap.label.ot", "New Testament"), ntBooks));
		Section total = new Section(System.getProperty("heatmap.label.total", "Total"), testaments);
		String infoText = System.getProperty("heatmap.label.infotext", "");
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
			bw.write("<html>\n" +
					"<head>\n" +
					"<meta charset=\"UTF-8\">\n" +
					"<title>" + html(title) + "</title>\n" +
					"<style type=\"text/css\">\n" +
					"body { font-family: Verdana, Arial, Helvetica, sans-serif; }\n" +
					"table {border-collapse: collapse; font-size: 6pt; }\n" +
					"td, th {border: 1px solid #777; }\n" +
					"span.versedetails {display: inline-block; min-width: 2em; text-align: center; border: 1px solid black; margin: 1px; }\n" +
					"span.versedetails b {margin: 0px 2px;}\n" +
					"</style>\n" +
					"<script>\n" +
					"function toggle(elem) {\n" +
					"    var label = '';\n" +
					"    var trs = document.getElementsByTagName('tr');\n" +
					"    for (var i=0; i < trs.length; i++) {\n" +
					"        var id = trs[i].id;\n" +
					"        if (id == elem) {\n" +
					"            label = trs[i].getElementsByTagName('a')[0].innerHTML;\n" +
					"            if (label == '[+]')\n" +
					"                trs[i].getElementsByTagName('a')[0].innerHTML = '[-]';\n" +
					"            else\n" +
					"                trs[i].getElementsByTagName('a')[0].innerHTML = '[+]';\n" +
					"        } else if (id.length > elem.length && id.substring(0, elem.length+1) == elem+'_') {\n" +
					"            if (label=='[-]') {\n" +
					"                trs[i].style.display='none';\n" +
					"            } else if (id.substring(elem.length+1).indexOf('_') == -1) {\n" +
					"                trs[i].style.display='';\n" +
					"            }\n" +
					"        }\n" +
					"    }\n" +
					"}\n" +
					"window.onload = function() {\n" +
					"    document.getElementById('root').style.display='';\n" +
					"    toggle('root');\n" +
					"}\n" +
					"</script>\n" +
					"</head>\n" +
					"<body>\n" +
					"<h1>" + html(title) + "</h1>\n" +
					(infoText.isEmpty() ? "" : "<p>" + html(infoText) + "</p>\n") + "<table>");
			for (Categorization c : categorizations) {
				bw.write("<tr><th colspan=\"2\">" + html(c.title) + "</th></tr>");
				for (int i = 0; i < c.colors.length; i++) {
					bw.write("<tr><th style=\"background-color: " + c.colors[i] + "\">#" + (i + 1) + "</th><td>" + html(c.names[i]) + "</td></tr>");
				}
			}
			bw.write("</table>\n<br />\n");
			bw.write("<table><tr><th>" + html(System.getProperty("heatmap.label.section", "Section")) + "</th>");
			for (Categorization c : categorizations) {
				bw.write("<th>" + html(c.title) + "</th>");
				for (int i = 0; i < c.colors.length; i++) {
					bw.write("<th style=\"background-color: " + c.colors[i] + "\"><i>" + html(c.names[i]) + "</i></th>");
				}
			}
			bw.write("</tr>\n");
			appendHTML(bw, 0, "root", total, categorizations);
			bw.write("</table>");
		}
	}

	private void appendHTML(BufferedWriter bw, int depth, String path, Section section, Categorization[] categorizations) throws Exception {
		bw.write("<tr id=\"" + path + "\" style=\"display:none\"><td><span style=\"display:block; float:left; width:" + depth + "em\">&nbsp;</span><a href=\"javascript:toggle('" + path + "');\">[+]</a> " + html(section.name) + "</td>");
		for (int i = 0; i < categorizations.length; i++) {
			Categorization c = categorizations[i];
			int total = 0, sum = 0, pixels = 0;
			for (int j = 0; j < c.colors.length; j++) {
				total += section.occurrences[i][j];
			}
			if (total == 0)
				total = 1;
			bw.write("<td><div style=\"width: 250px; position:relative; border: 1px solid black\">");
			for (int j = 0; j < c.colors.length; j++) {
				sum += section.occurrences[i][j];
				int px = (250 * sum / total) - pixels;
				pixels += px;
				bw.write("<div style=\"float:left; width:" + px + "px; background-color: " + c.colors[j] + "\">&nbsp;</div>");
			}
			bw.write("</div></td>");
			for (int j = 0; j < c.colors.length; j++) {
				bw.write("<td>" + section.occurrences[i][j] + "</td>");
			}
		}
		bw.write("</tr>\n");
		if (section.rawData != null) {
			int cols = 1;
			for (Categorization c : categorizations) {
				cols += 1 + c.colors.length;
			}
			bw.write("<tr id=\"" + path + "_raw\" style=\"display:none\"><td colspan=\"" + cols + "\">");
			for (int i = 0; i < section.rawData[0].length; i++) {
				bw.write("<span class=\"versedetails\">");
				for (int j = 0; j < categorizations.length; j++) {
					bw.write("<div style=\"background-color: " + categorizations[j].colors[section.rawData[j][i]] + "\">&nbsp;</div>");
				}
				bw.write("<b>" + section.verseNumbers[i] + "</b>");
				bw.write("</span>");
			}
			bw.write("</td></tr>\n");
		} else {
			int ctr = 1;
			for (Section child : section.children) {
				appendHTML(bw, depth + 1, path + "_" + ctr, child, categorizations);
				ctr++;
			}
		}
	}

	private String html(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static class Categorization {
		public final String title;
		public final String[] colors;
		public final String[] names;
		public final Pattern[] regexes;

		public Categorization(String title, String[] colors, String[] names, Pattern[] regexes) {
			if (colors.length != names.length || colors.length != regexes.length + 1)
				throw new IllegalArgumentException();
			this.title = title;
			this.colors = colors;
			this.names = names;
			this.regexes = regexes;
		}
	}

	private static class Section {
		public final String name;
		public final int[][] occurrences;
		public final int[][] rawData;
		public final String[] verseNumbers;
		public final Section[] children;

		public Section(String name, List<Section> children) {
			if (children.isEmpty())
				throw new IllegalArgumentException();
			this.name = name;
			this.rawData = null;
			this.verseNumbers = null;
			this.children = children.toArray(new Section[children.size()]);
			this.occurrences = new int[this.children[0].occurrences.length][];
			for (int i = 0; i < occurrences.length; i++) {
				occurrences[i] = new int[this.children[0].occurrences[i].length];
				for (Section child : children) {
					for (int j = 0; j < occurrences[i].length; j++) {
						occurrences[i][j] += child.occurrences[i][j];
					}
				}
			}
		}

		public Section(String name, int[][] rawData, String[] verseNumbers, Categorization[] categorizations) {
			if (rawData.length == 0 || rawData[0].length == 0 || rawData.length != categorizations.length)
				throw new IllegalArgumentException();
			this.name = name;
			this.rawData = rawData;
			this.verseNumbers = verseNumbers;
			this.children = new Section[0];
			this.occurrences = new int[categorizations.length][];
			for (int i = 0; i < categorizations.length; i++) {
				occurrences[i] = new int[categorizations[i].colors.length];
				if (rawData[i].length != verseNumbers.length)
					throw new IllegalArgumentException();
				for (int j = 0; j < rawData[i].length; j++) {
					occurrences[i][rawData[i][j]]++;
				}
			}
		}
	}
}
