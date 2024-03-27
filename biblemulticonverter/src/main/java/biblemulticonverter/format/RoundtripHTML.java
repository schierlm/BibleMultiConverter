package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;

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
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;

public class RoundtripHTML implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Roundtrip HTML Export",
			"",
			"Usage (export): RoundtripHTML <OutputDirectory>",
			"",
			"Export into a directory of HTML files. The resulting HTML files contain all features",
			"supported by the import file. While it is perfectly usable (and indexable) without",
			"JavaScript, some advanced features (like Navigation or Strongs highlighting) require",
			"that JavaScript is available.",
			"",
			"The HTML files (or more generally, only metadata.js and the chapter files) can be parsed",
			"back to the original module. While this feature has been more useful in times where file",
			"hosters did not exist and web hosters had strict file format and file size checks, it is",
			"today still useful for testing that the HTML file contains indeed all information.",
			"",
			"In case the input file contains cross references to chapters that do not exist in that",
			"edition, you can pass an extra parameter of a Properties file, which will be used to link",
			"to those chapters. The file will be updated with all chapters that exist in the input file,",
			"so you can just pass the file from one invocation to the next and don't have to create it",
			"manually."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {

		List<String> filenames = new ArrayList<String>();
		File directory = new File(exportArgs[0]);
		if (!directory.exists())
			directory.mkdirs();

		// build xref map
		Properties xrefMap = new Properties();
		if (exportArgs.length > 1 && new File(exportArgs[1]).exists()) {
			try (InputStream in = new FileInputStream(new File(exportArgs[1]))) {
				xrefMap.load(in);
			}
		}
		for (Book bk : bible.getBooks()) {
			for (int cnumber = 1; cnumber <= bk.getChapters().size(); cnumber++) {
				xrefMap.setProperty(bk.getId().getOsisID() + "," + cnumber, new File(directory, getTypeDir(bk.getId()) + "/" + bk.getAbbr() + "_" + cnumber + ".html").getAbsolutePath());
			}
		}
		if (exportArgs.length > 1) {
			try (OutputStream out = new FileOutputStream(new File(exportArgs[1]))) {
				xrefMap.store(out, "RoundtripHTML Cross Reference Map");
			}
		}
		Path base = new File(directory, "dummydir").getAbsoluteFile().toPath();
		for (Object key : xrefMap.keySet()) {
			String newValue = base.relativize(new File((String) xrefMap.get(key)).toPath()).toString().replace('\\', '/');
			xrefMap.setProperty((String) key, newValue);
		}

		// metadata
		try (BufferedWriter bw = createWriter(directory, filenames, "metadata.js")) {
			bw.write("biblename = \"" + bible.getName().replace("\\", "\\\\").replace("\"", "\\\"") + "\";\n");
			bw.write("metadata = [{\n");
			boolean first = true;
			for (Book bk : bible.getBooks()) {
				if (!first)
					bw.write("},{\n");
				first = false;
				String[] keys = new String[] { "abbr", "short", "long", "osis", "type" };
				String[] values = new String[] { bk.getAbbr(), bk.getShortName(), bk.getLongName(), bk.getId().getOsisID(), getTypeDir(bk.getId()) };
				for (int i = 0; i < keys.length; i++) {
					bw.write(keys[i] + ":\"" + values[i].replace("\\", "\\\\").replace("\"", "\\\"") + "\",\n");
				}
				bw.write("nt:" + bk.getId().isNT() + ",\n");
				bw.write("chapters:" + bk.getChapters().size() + "\n");
			}
			bw.write("}];\n");
		}

		// chapters
		for (Book bk : bible.getBooks()) {
			int cnumber = 0;
			for (Chapter ch : bk.getChapters()) {
				cnumber++;
				try (BufferedWriter bw = createWriter(directory, filenames, getTypeDir(bk.getId()) + "/" + bk.getAbbr() + "_" + cnumber + ".html")) {
					bw.write("<html><head>\n" +
							"<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" />\n" +
							"<title>" + (bk.getChapters().size() == 1 ? "" : bk.getAbbr() + " " + cnumber + " &ndash; ") + bk.getLongName() + " &ndash; " + bible.getName() + "</title>\n" +
							"<script type=\"text/javascript\" src=\"../metadata.js\"></script>\n" +
							"<script type=\"text/javascript\" src=\"../script.js\"></script>\n" +
							"<style type=\"text/css\">div.v { display:inline; } /*changed dynamically*/</style>\n" +
							"<link rel=\"stylesheet\" type=\"text/css\" href=\"../style.css\">\n" +
							"</head>\n");
					bw.write("<body onload=\"showNavbar('" + bk.getAbbr() + "', " + cnumber + ");\">\n");
					bw.write("<div id=\"navbar\"><a href=\"../index.html\">" + bible.getName() + "</a> &ndash; <b>" + bk.getLongName() + "</b>");
					if (bk.getChapters().size() > 1) {
						bw.write(" &ndash; ");
						for (int i = 1; i <= bk.getChapters().size(); i++) {
							if (i == cnumber) {
								bw.write("<b>" + i + "</b> ");
							} else if (i == 2 && cnumber > 4) {
								bw.write("... ");
								i = cnumber - 3;
							} else if (i == cnumber + 3 && i < bk.getChapters().size()) {
								bw.write("... ");
								i = bk.getChapters().size() - 1;
							} else {
								bw.write("<a href=\"" + bk.getAbbr() + "_" + i + ".html\">" + i + "</a> ");
							}
						}
					}
					bw.write("</div><hr>\n");
					bw.write("<h1>" + bk.getAbbr() + (bk.getChapters().size() == 1 ? "" : " " + cnumber) + "</h1>\n");
					bw.write("<!-- PARSED BELOW; EDITING MAY BREAK PARSER -->\n");
					List<StringWriter> footnotes = new ArrayList<StringWriter>();
					if (ch.getProlog() != null) {
						bw.write("<div class=\"biblehtmlcontent prolog\">\n");
						ch.getProlog().accept(new RoundtripHTMLVisitor(bw, footnotes, "", "", xrefMap));
						bw.write("\n");
						bw.write("</div>\n");
					}
					if (ch.getVerses().size() > 0) {
						bw.write("<div class=\"biblehtmlcontent verses\" id=\"verses\">\n");
						for (Verse v : ch.getVerses()) {
							bw.write("<div class=\"v\" id=\"v" + v.getNumber() + "\">");
							v.accept(new RoundtripHTMLVisitor(bw, footnotes, "<span class=\"vn\">" + v.getNumber() + "</span> ", "", xrefMap));
							bw.write("</div>\n");
						}
						bw.write("</div>\n");
					}
					if (footnotes.size() > 0) {
						bw.write("<div class=\"biblehtmlcontent footnotes\">\n");
						for (StringWriter footnote : footnotes) {
							bw.write(footnote.toString() + "\n");
						}
						bw.write("</div>\n");
					}
					bw.write("<!-- PARSED ABOVE; EDITING MAY BREAK PARSER -->\n");
					bw.write("</body></html>");
				}
			}
		}

		// /// rest is not needed for roundtrip import /// //

		// index file
		try (BufferedWriter bw = createWriter(directory, filenames, "index.html")) {
			bw.write("<html><head>\n" +
					"<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" />\n" +
					"<title>" + bible.getName() + "</title>\n" +
					"<script type=\"text/javascript\" src=\"metadata.js\"></script>\n" +
					"<script type=\"text/javascript\" src=\"script.js\"></script>\n" +
					"<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\">\n" +
					"</head>\n");
			bw.write("<body onload=\"showNavbar('', 0);\">\n");
			bw.write("<div id=\"navbar\"><b>" + bible.getName() + "</b></div><hr>\n");
			bw.write("<h1>" + bible.getName() + "</h1>\n");
			for (Book bk : bible.getBooks()) {
				bw.write("<a href=\"" + getTypeDir(bk.getId()) + "/" + bk.getAbbr() + "_1.html\">" + bk.getLongName() + "</a><br>\n");
			}
			bw.write("</body></html>");
		}

		// static files
		for (String staticFile : Arrays.asList("script.js", "style.css", "crossdomain.html")) {
			try (BufferedWriter bw = createWriter(directory, filenames, staticFile)) {
				Reader r = new InputStreamReader(RoundtripHTML.class.getResourceAsStream("/RoundtripHTML/" + staticFile), StandardCharsets.UTF_8);
				char[] buf = new char[4096];
				int len;
				while ((len = r.read(buf)) != -1) {
					bw.write(buf, 0, len);
				}
			}
		}

		// filelist.html (for mirroring)
		try (BufferedWriter bw = createWriter(directory, filenames, "filelist.html")) {
			bw.write("<html><head>\n" +
					"<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" />\n" +
					"<title>File list &ndash; " + bible.getName() + "</title>\n" +
					"<script type=\"text/javascript\" src=\"metadata.js\"></script>\n" +
					"<script type=\"text/javascript\" src=\"script.js\"></script>\n" +
					"<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\">\n" +
					"</head>\n");
			bw.write("<body onload=\"showNavbar('', 0);\">\n");
			bw.write("<div id=\"navbar\"><a href=\"index.html\">" + bible.getName() + "</a></div><hr>\n");
			bw.write("<h1>File list</h1>\n");
			for (String name : filenames) {
				bw.write("<a href=\"" + name + "\">" + name + "</a><br>\n");
			}
			bw.write("</body></html>");
		}

	}

	private static String getTypeDir(BookID id) {
		if (id == BookID.DICTIONARY_ENTRY)
			return "dict";
		if (id.getZefID() < 0)
			return "meta";
		if (id.isNT())
			return "nt";
		return "ot";
	}

	private static BufferedWriter createWriter(File directory, List<String> filenames, String name) throws IOException {
		filenames.add(name);
		File f = new File(directory, name);
		if (!f.getParentFile().exists())
			f.getParentFile().mkdirs();
		return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
	}

	@Override
	public Bible doImport(File inputDir) throws Exception {
		Bible bible;
		// metadata
		try (BufferedReader br = createReader(inputDir, "metadata.js")) {
			String line = br.readLine();
			br.readLine();
			bible = new Bible(line.substring(13, line.length() - 2).replace("\\\"", "\"").replace("\\\\", "\\"));
			Map<String, Object> fieldMap = new HashMap<String, Object>();
			while ((line = br.readLine()) != null) {
				if (line.startsWith("}")) {
					Book bk = new Book((String) fieldMap.get("abbr"), BookID.fromOsisId((String) fieldMap.get("osis")), (String) fieldMap.get("short"), (String) fieldMap.get("long"));
					for (int i = 0; i < (Integer) fieldMap.get("chapters"); i++) {
						bk.getChapters().add(new Chapter());
					}
					bible.getBooks().add(bk);
					continue;
				}
				int pos = line.indexOf(":");
				String key = line.substring(0, pos);
				String value = line.substring(pos + 1);
				if (value.endsWith(","))
					value = value.substring(0, value.length() - 1);
				if (value.startsWith("\"") && value.endsWith("\"")) {
					fieldMap.put(key, value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\"));
				} else if (value.equals("true") || value.equals("false")) {
					fieldMap.put(key, Boolean.parseBoolean(value));
				} else {
					fieldMap.put(key, Integer.parseInt(value));
				}
			}
		}

		// chapters
		for (Book bk : bible.getBooks()) {
			int cnumber = 0;
			for (Chapter ch : bk.getChapters()) {
				cnumber++;
				try (BufferedReader br = createReader(inputDir, getTypeDir(bk.getId()) + "/" + bk.getAbbr() + "_" + cnumber + ".html")) {
					String line;
					List<FormattedText.Visitor<RuntimeException>> footnotes = new ArrayList<>();
					while ((line = br.readLine()) != null) {
						if (line.equals("<div class=\"biblehtmlcontent prolog\">")) {
							line = br.readLine();
							FormattedText prolog = new FormattedText();
							int end = parseLine(prolog.getAppendVisitor(), line, 0, footnotes);
							ch.setProlog(prolog);
							if (end != line.length())
								throw new IOException(line.substring(end));
							line = br.readLine();
							if (!line.equals("</div>"))
								throw new IOException(line);
						} else if (line.equals("<div class=\"biblehtmlcontent verses\" id=\"verses\">")) {
							while ((line = br.readLine()) != null) {
								if (line.equals("</div>"))
									break;
								if (!line.startsWith("<div class=\"v\" id=\"v") || !line.endsWith("</div>"))
									throw new IOException(line);
								line = line.substring(20, line.length() - 6);
								int pos = line.indexOf("\">");
								Verse v = new Verse(line.substring(0, pos));
								int end = parseLine(v.getAppendVisitor(), line, pos + 2, footnotes);
								if (end != line.length())
									throw new IOException(line.substring(end));
								ch.getVerses().add(v);
							}
							if (!line.equals("</div>"))
								throw new IOException(line);
						} else if (line.equals("<div class=\"biblehtmlcontent footnotes\">")) {
							for (int i = 0; i < footnotes.size(); i++) {
								line = br.readLine();
								String prefix = "<div class=\"fn\"><sup class=\"fnt\"><a name=\"fn" + (i + 1) + "\" href=\"#fnm" + (i + 1) + "\">" + (i + 1) + "</a></sup> ";
								if (!line.startsWith(prefix) || !line.endsWith("</div>"))
									throw new IOException(line);
								line = line.substring(prefix.length(), line.length() - 6);
								int end = parseLine(footnotes.get(i), line, 0, null);
								if (end != line.length())
									throw new IOException(line.substring(end));
							}
							line = br.readLine();
							if (!line.equals("</div>"))
								throw new IOException(line);
						}
					}
					if (ch.getProlog() != null)
						ch.getProlog().finished();
					for (Verse v : ch.getVerses())
						v.finished();
				}
			}
		}
		return bible;
	}

	private static BufferedReader createReader(File directory, String name) throws IOException {
		return new BufferedReader(new InputStreamReader(new FileInputStream(new File(directory, name)), StandardCharsets.UTF_8));
	}

	private int parseLine(Visitor<RuntimeException> visitor, String line, int pos, List<Visitor<RuntimeException>> footnotes) throws IOException {
		while (pos < line.length()) {
			if (line.charAt(pos) != '<') {
				int endPos = line.indexOf('<', pos);
				if (endPos == -1)
					endPos = line.length();
				visitor.visitText(line.substring(pos, endPos).replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&"));
				pos = endPos;
				continue;
			}
			if (line.startsWith("</", pos))
				return pos;
			int endPos = line.indexOf('>', pos);
			if (endPos == -1)
				throw new IOException(line.substring(pos));
			int spacePos = line.indexOf(' ', pos);
			if (spacePos == -1 || spacePos > endPos)
				spacePos = endPos;
			Visitor<RuntimeException> childVisitor;
			String tag = line.substring(pos + 1, spacePos);
			switch (tag) {
			case "b":
			case "i":
			case "u":
				childVisitor = visitor.visitFormattingInstruction(FormattingInstructionKind.fromChar(tag.charAt(0)));
				pos = parseLine(childVisitor, line, endPos + 1, footnotes);
				if (!line.startsWith("</" + tag + ">", pos))
					throw new IOException(line.substring(pos));
				pos += 4;
				break;
			case "br":
				visitor.visitLineBreak(LineBreakKind.NEWLINE);
				pos += 4;
				break;
			case "h2":
			case "h3":
			case "h4":
			case "h5":
				childVisitor = visitor.visitHeadline(tag.charAt(1) - '1');
				pos = parseLine(childVisitor, line, endPos + 1, footnotes);
				if (!line.startsWith("</" + tag + ">", pos))
					throw new IOException(line.substring(pos));
				pos += 5;
				break;
			case "h6":
				if (!line.startsWith("<h6 class=\"depth-", pos))
					throw new IOException(line.substring(pos));
				childVisitor = visitor.visitHeadline(line.charAt(pos + 17) - '0');
				pos = parseLine(childVisitor, line, endPos + 1, footnotes);
				if (!line.startsWith("</h6>", pos))
					throw new IOException(line.substring(pos));
				pos += 5;
				break;
			case "!--raw":
				String[] parts = line.substring(spacePos + 1, endPos).split(" ");
				String marker = parts[0];
				RawHTMLMode mode;
				switch (parts[1]) {
				case "of":
					mode = RawHTMLMode.OFFLINE;
					break;
				case "on":
					mode = RawHTMLMode.ONLINE;
					break;
				case "bo":
					mode = RawHTMLMode.BOTH;
					break;
				default:
					throw new IOException(parts[1]);
				}
				int rawEnd = line.indexOf("endraw " + marker + "-->", pos);
				String content;
				if (mode == RawHTMLMode.OFFLINE) {
					content = line.substring(endPos + 1, rawEnd - 2).replace("-d", "-");
				} else {
					content = line.substring(endPos + 1, rawEnd - 4);
				}
				visitor.visitRawHTML(mode, content);
				pos = rawEnd + 10 + marker.length();
				break;
			case "sub":
				childVisitor = visitor.visitFormattingInstruction(FormattingInstructionKind.SUBSCRIPT);
				pos = parseLine(childVisitor, line, endPos + 1, footnotes);
				if (!line.startsWith("</sub>", pos))
					throw new IOException(line.substring(pos));
				pos += 6;
				break;
			case "sup":
				if (spacePos == endPos) {
					childVisitor = visitor.visitFormattingInstruction(FormattingInstructionKind.SUPERSCRIPT);
					pos = parseLine(childVisitor, line, endPos + 1, footnotes);
					if (!line.startsWith("</sup>", pos))
						throw new IOException(line.substring(pos));
					pos += 6;
				} else if (line.startsWith("<sup class=\"fnm\"><a name=\"fnm", pos)) {
					footnotes.add(visitor.visitFootnote());
					int cnt = footnotes.size();
					String value = "<sup class=\"fnm\"><a name=\"fnm" + cnt + "\" href=\"#fn" + cnt + "\">" + cnt + "</a></sup>";
					if (!line.startsWith(value, pos))
						throw new IOException(line.substring(pos));
					pos += value.length();
				} else {
					throw new IOException(line.substring(pos));
				}
				break;
			case "a":
				if (line.startsWith("<a class=\"footnote-link\" href=\"", pos)) {
					childVisitor = visitor.visitFormattingInstruction(FormattingInstructionKind.FOOTNOTE_LINK);
				} else if (line.startsWith("<a class=\"xr\" href=\"", pos)) {
					String params = line.substring(spacePos, endPos - 1);
					params = params.substring(params.indexOf("\"-bmc-xr: ") + 10);
					String[] fields = params.split(" ");
					if (fields.length != 6)
						throw new IOException(params);
					childVisitor = visitor.visitCrossReference(fields[0], BookID.fromOsisId(fields[1]), Integer.parseInt(fields[2]), fields[3], Integer.parseInt(fields[4]), fields[5]);
				} else if (line.startsWith("<a class=\"dict\" href=\"../../", pos)) {
					String[] params = line.substring(spacePos + 26, endPos - 8).split("/dict/");
					if (params.length != 2)
						throw new IOException(line.substring(pos));
					childVisitor = visitor.visitDictionaryEntry(params[0], params[1]);
				} else {
					throw new IOException(line.substring(pos));
				}
				pos = parseLine(childVisitor, line, endPos + 1, footnotes);
				if (!line.startsWith("</a>", pos))
					throw new IOException(line.substring(pos));
				pos += 4;
				break;
			case "span":
				if (line.startsWith("<span class=\"vn\">", pos)) {
					// skip explicit verse numbers
					pos = line.indexOf("</span> ", pos) + 8;
				} else if (line.startsWith("<span class=\"vsep\">/</span>", pos)) {
					pos += 27;
					visitor.visitVerseSeparator();
				} else if (line.startsWith("<span class=\"br-ind\"><br><span class=\"indent\">&nbsp;</span></span>", pos)) {
					pos += 66;
					visitor.visitLineBreak(LineBreakKind.NEWLINE_WITH_INDENT);
				} else if (line.startsWith("<span class=\"br-p\"><br><br></span>", pos)) {
					pos += 34;
					visitor.visitLineBreak(LineBreakKind.PARAGRAPH);
				} else {

					if (line.startsWith("<span class=\"css\" style=\"", pos)) {
						childVisitor = visitor.visitCSSFormatting(line.substring(pos + 25, endPos - 1));
					} else if (line.startsWith("<span class=\"fmt-", pos)) {
						childVisitor = visitor.visitFormattingInstruction(FormattingInstructionKind.valueOf(line.substring(pos + 17, endPos - 1).replace('-', '_').toUpperCase()));
					} else if (line.startsWith("<span class=\"var", pos)) {
						childVisitor = visitor.visitVariationText(line.substring(pos + 21, endPos - 1).split(" var-"));
					} else if (line.startsWith("<span class=\"xa xa-", pos)) {
						String tagContent = line.substring(pos, endPos + 1);
						Matcher m = Utils.compilePattern("<span class=\"xa xa-([eks])\" style=\"-bmc-xa-([a-z0-9]+)-([a-z0-9-]+): ([A-Za-z0-9-]+);\">").matcher(tagContent);
						if (!m.matches())
							throw new IOException(tagContent);
						char prioChar = m.group(1).charAt(0);
						ExtraAttributePriority prio = prioChar == 'e' ? ExtraAttributePriority.ERROR :
								prioChar == 'k' ? ExtraAttributePriority.KEEP_CONTENT :
										prioChar == 's' ? ExtraAttributePriority.SKIP : null;
						childVisitor = visitor.visitExtraAttribute(prio, m.group(2), m.group(3), m.group(4));
					} else if (line.startsWith("<span class=\"g ", pos)) {
						StringBuilder strongPfxL = new StringBuilder();
						List<Integer> strongL = new ArrayList<Integer>();
						List<String> rmacL = new ArrayList<String>();
						List<Integer> sourceIndexL = new ArrayList<Integer>();
						for (String part : line.substring(pos + 15, endPos - 1).split(" ")) {
							if (part.startsWith("gs")) {
								if (Diffable.parseStrongsSuffix) {
									char[] prefixHolder = new char[1];
									strongL.add(Utils.parseStrongs(part.substring(2), '?', prefixHolder));
									if (prefixHolder[0] != '?')
										strongPfxL.append(prefixHolder[0]);
								} else if (part.matches("gs[A-Z][0-9]+")) {
									strongPfxL.append(part.charAt(2));
									strongL.add(Integer.parseInt(part.substring(3)));
								} else {
									strongL.add(Integer.parseInt(part.substring(2)));
								}
							} else if (part.startsWith("gr-")) {
								rmacL.add(part.substring(3).toUpperCase());
							} else if (part.startsWith("gw-!")) {
								rmacL.add(part.substring(4));
							} else if (part.startsWith("gi")) {
								sourceIndexL.add(Integer.parseInt(part.substring(2)));
							} else {
								throw new IOException(part);
							}
						}
						char[] strongPfx = strongPfxL.length() == 0 ? null : strongPfxL.toString().toCharArray();
						int[] strongs = strongL.size() == 0 ? null : new int[strongL.size()];
						if (strongs != null) {
							for (int i = 0; i < strongs.length; i++) {
								strongs[i] = strongL.get(i);
							}
						}
						String[] rmacs = rmacL.size() == 0 ? null : (String[]) rmacL.toArray(new String[rmacL.size()]);
						int[] sourceIndices = sourceIndexL.size() == 0 ? null : new int[sourceIndexL.size()];
						if (sourceIndices != null) {
							for (int i = 0; i < sourceIndices.length; i++) {
								sourceIndices[i] = sourceIndexL.get(i);
							}
						}
						childVisitor = visitor.visitGrammarInformation(strongPfx, strongs, rmacs, sourceIndices);
					} else {
						throw new IOException(line.substring(pos));
					}
					pos = parseLine(childVisitor, line, endPos + 1, footnotes);
					if (!line.startsWith("</span>", pos))
						throw new IOException(line.substring(pos));
					pos += 7;
				}
				break;
			default:
				throw new IOException(tag);
			}
		}
		return pos;
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return true;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private static class RoundtripHTMLVisitor extends AbstractHTMLVisitor {

		private final String prefix;
		private final List<StringWriter> footnotes;
		private final Properties xrefMap;

		private RoundtripHTMLVisitor(Writer writer, List<StringWriter> footnotes, String prefix, String suffix, Properties xrefMap) {
			super(writer, suffix);
			this.footnotes = footnotes;
			this.prefix = prefix;
			this.xrefMap = xrefMap;
		}

		protected String getNextFootnoteTarget() {
			return "#fn" + (footnotes.size() + 1);
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			writer.write("<span class=\"vsep\">/</span>");
		}

		@Override
		public void visitStart() throws IOException {
			if (suffixStack.size() == 1)
				writer.write(prefix);
		}

		protected String createFormattingInstructionStartTag(FormattingInstructionKind kind) {
			return "<span class=\"fmt-" + kind.name().toLowerCase().replace('_', '-') + "\">";
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
			int marker = 1;
			while (raw.contains("endraw " + marker + "-->")) {
				marker = (int) (Math.random() * 1000000);
			}
			writer.write("<!--raw " + marker + " " + mode.name().substring(0, 2).toLowerCase() + " ");
			if (mode == RawHTMLMode.OFFLINE) {
				writer.write(">" + raw.replace("-", "-d") + "<!");
			} else {
				writer.write("-->" + raw + "<!--");
			}
			writer.write("endraw " + marker + "-->");
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			writer.write("<span class=\"var");
			for (String var : variations) {
				writer.write(" var-" + var);
			}
			writer.write("\">");
			pushSuffix("</span>");
			return this;
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			writer.write("<span class=\"xa xa-" + prio.toString().substring(0, 1).toLowerCase() + "\" style=\"-bmc-xa-" + category + "-" + key + ": " + value + ";\">");
			pushSuffix("</span>");
			return this;
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			if (depth < 5) {
				writer.write("<h" + (depth + 1) + ">");
				pushSuffix("</h" + (depth + 1) + ">");
			} else {
				writer.write("<h6 class=\"depth-" + depth + "\">");
				pushSuffix("</h6>");
			}
			return this;
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			StringWriter fnw = new StringWriter();
			footnotes.add(fnw);
			int cnt = footnotes.size();
			writer.write("<sup class=\"fnm\"><a name=\"fnm" + cnt + "\" href=\"#fn" + cnt + "\">" + cnt + "</a></sup>");
			fnw.write("<div class=\"fn\"><sup class=\"fnt\"><a name=\"fn" + cnt + "\" href=\"#fnm" + cnt + "\">" + cnt + "</a></sup> ");
			return new RoundtripHTMLVisitor(fnw, null, "", "</div>", xrefMap);
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			String xrefLink = xrefMap.getProperty(book.getOsisID() + "," + firstChapter);
			if (xrefLink == null) {
				System.out.println("WARNING: Unsatisfiable reference to " + bookAbbr + " " + firstChapter);
				xrefLink = "../index.html";
			}
			writer.write("<a class=\"xr\" href=\"" + xrefLink + "#v" + firstVerse + "\" style=\"-bmc-xr: " + bookAbbr + " " + book.getOsisID() + " " + firstChapter + " " + firstVerse + " " + lastChapter + " " + lastVerse + "\">");
			pushSuffix("</a>");
			return this;
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws IOException {
			switch (kind) {
			case NEWLINE:
				writer.write("<br>");
				return;
			case NEWLINE_WITH_INDENT:
				writer.write("<span class=\"br-ind\"><br><span class=\"indent\">&nbsp;</span></span>");
				return;
			case PARAGRAPH:
				writer.write("<span class=\"br-p\"><br><br></span>");
				return;
			}
			throw new IllegalStateException("Unsupported paragraph type");
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			writer.write("<span class=\"g");
			if (Diffable.writeStrongsSuffix && strongsPrefixes != null && strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					writer.write(" gs" + Utils.formatStrongs(false, i, strongsPrefixes, strongs));
				}
			} else if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					writer.write(" gs" + (strongsPrefixes != null ? "" + strongsPrefixes[i] : "") + strongs[i]);
				}
			}
			if (rmac != null) {
				for (String r : rmac) {
					if (r.matches(Utils.RMAC_REGEX))
						writer.write(" gr-" + r.toLowerCase());
					else if (r.matches(Utils.WIVU_REGEX))
						writer.write(" gw-!" + r);
					else
						throw new IllegalStateException("Invalid morphology code: " + r);
				}
			}
			if (sourceIndices != null) {
				for (int idx : sourceIndices) {
					writer.write(" gi" + idx);
				}
			}

			writer.write("\">");
			pushSuffix("</span>");
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			writer.write("<a class=\"dict\" href=\"../../" + dictionary + "/dict/" + entry + "_1.html\">");
			pushSuffix("</a>");
			return this;
		}
	}
}
