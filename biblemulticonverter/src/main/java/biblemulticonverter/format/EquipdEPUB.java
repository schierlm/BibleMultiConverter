package biblemulticonverter.format;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;

public class EquipdEPUB implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Epub export format for Equipd Bible",
			"",
			"Usage: EquipdEPUB <outfile> [-headlinesAfter|-noHeadlines]",
			"",
			"When the optional -headlinesafter switch is given, headlines are exported after the",
			"verse markers; this makes the headline appear inside the correct verse (instead of",
			"the previous one), but the verse number will appear at the end of the previous",
			"paragraph.",
			"",
			"When the optional -noheadlines switch is given, headlines are not exported at all."
	};

	// true: pass EpubCheck
	// false: work in Equipd Bible
	public static final boolean VALIDATABLE = false;

	private static String[] BOOK_NAMES = { null,
			"GE", "EX", "LE", "NU", "DE", "JOS", "JG", "RU", "1SA", "2SA", "1KI", "2KI",
			"1CH", "2CH", "EZR", "NE", "ES", "JOB", "PS", "PR", "EC", "CA", "ISA", "JER", "LA",
			"EZE", "DA", "HO", "JOE", "AM", "OB", "JON", "MIC", "NA", "HAB", "ZEP", "HAG", "ZEC", "MAL",
			"MT", "MR", "LU", "JOH", "AC", "RO", "1CO", "2CO", "GA", "EPH", "PHP", "COL", "1TH", "2TH",
			"1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JO", "2JO", "3JO", "JUDE", "RE"
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		final Set<String> unsupportedFeatures = new HashSet<>();
		String uuid = UUID.randomUUID().toString() + "-" + System.currentTimeMillis() / 1000;
		Boolean headlinesAfter = false;
		if (exportArgs.length > 1) {
			if (exportArgs[1].equals("-headlinesAfter"))
				headlinesAfter = true;
			else if (exportArgs[1].equals("-noHeadlines"))
				headlinesAfter = null;
			else
				System.out.println("WARNING: Unsupported argument: " + exportArgs[1]);
		}
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(exportArgs[0] + ".epub"))) {
			ZipEntry mimetypeZE = new ZipEntry("mimetype");
			mimetypeZE.setSize(20);
			mimetypeZE.setCompressedSize(20);
			mimetypeZE.setCrc(749429103);
			mimetypeZE.setMethod(ZipOutputStream.STORED);
			zos.putNextEntry(mimetypeZE);
			zos.write("application/epub+zip".getBytes(StandardCharsets.US_ASCII));
			zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
			zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<container" +
					" version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n <rootfiles>\n" +
					"  <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
					" </rootfiles>\n</container>").getBytes(StandardCharsets.US_ASCII));
			zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
			StringBuilder sb = new StringBuilder();
			sb.append("<?xml version=\"1.0\"?>\n<package version=\"2.0\" xmlns=\"http://www.idpf.org/2007/opf\"" +
					" unique-identifier=\"uuid\">\n  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" +
					" xmlns:opf=\"http://www.idpf.org/2007/opf\">\n   <dc:title>" + xml(bible.getName()) +
					"</dc:title>\n    <dc:creator opf:role=\"aut\"></dc:creator>\n    <dc:language>en</dc:language>\n" +
					"    <dc:rights>Public Domain</dc:rights>\n    <dc:publisher></dc:publisher>\n" +
					"    <dc:identifier id=\"uuid\">" + uuid + "</dc:identifier>\n  </metadata>\n  <manifest>\n" +
					"    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\" />\n" +
					"    <item id=\"style\" href=\"global.css\" media-type=\"text/css\" />\n");
			int counter = 1;
			for (Book book : bible.getBooks()) {
				if (book.getId().getZefID() < 1 || book.getId().getZefID() > 66)
					continue;
				String fileName = String.format("%02d.%s", book.getId().getZefID(), BOOK_NAMES[book.getId().getZefID()]);
				for (int i = 0; i <= book.getChapters().size(); i++) {
					sb.append("    <item id=\"chapter" + counter + "\" href=\"" + fileName + "." + i + ".xhtml\" media-type=\"application/xhtml+xml\" />\n");
					counter++;
				}
			}
			sb.append("  </manifest>\n  <spine toc=\"ncx\">\n");
			for (int i = 1; i < counter; i++) {
				sb.append("    <itemref idref=\"chapter" + i + "\" />\n");
			}
			sb.append("  </spine>\n</package>");
			zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
			zos.putNextEntry(new ZipEntry("OEBPS/global.css"));
			zos.write(("span.vn {\n  font-weight: bold;\n}\n" +
					"div.ci a {\n  display: block;\n  float: left;\n  margin: 0 15px 15px 0;\n}\n" +
					"").getBytes(StandardCharsets.US_ASCII));
			zos.putNextEntry(new ZipEntry("OEBPS/toc.ncx"));
			sb.setLength(0);
			sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>\n<ncx" +
					" xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n  <head>\n" +
					"    <meta content=\"urn:uuid:" + uuid + "\" name=\"dtb:uuid\" />\n    <meta content=\"2\"" +
					" name=\"dtb:depth\" />\n    <meta content=\"0\" name=\"dtb:totalPageCount\" />\n" +
					"    <meta content=\"0\" name=\"dtb:maxPageNumber\" />\n  </head>\n  <docTitle>\n    <text>" +
					xml(bible.getName()) + "</text>\n  </docTitle>\n  <navMap>\n");
			counter = 1;
			for (Book book : bible.getBooks()) {
				if (book.getId().getZefID() < 1 || book.getId().getZefID() > 66)
					continue;
				String fileName = String.format("%02d.%s", book.getId().getZefID(), BOOK_NAMES[book.getId().getZefID()]);
				sb.append("    <navPoint id=\"navPoint" + counter + "\" playOrder=\"" + counter + "\">\n      <navLabel><text>" +
						xml(book.getShortName()) + "</text></navLabel>\n      <content src=\"" + fileName + ".0.xhtml\" />\n" +
						"    </navPoint>\n");
				counter++;
			}
			sb.append("  </navMap>\n</ncx>");
			zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
			for (Book book : bible.getBooks()) {
				if (book.getId().getZefID() < 1 || book.getId().getZefID() > 66) {
					System.out.println("WARNING: Skipping book " + book.getAbbr());
					continue;
				}
				String fileName = String.format("%02d.%s", book.getId().getZefID(), BOOK_NAMES[book.getId().getZefID()]);
				zos.putNextEntry(new ZipEntry("OEBPS/" + fileName + ".0.xhtml"));
				sb.setLength(0);
				sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n<html dir=\"ltr\" xmlns=\"http://www.w3.org/1999/xhtml\" " +
						"xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"en\">\n<head>\n  <meta http-equiv=\"Content-Type\"" +
						" content=\"text/html; charset=UTF-8\" />\n  <title>" + xml(book.getShortName()) + " </title>\n" +
						"  <link rel=\"stylesheet\" href=\"global.css\" type=\"text/css\" />\n</head>\n<body>\n\n<h2>" +
						xml(book.getShortName()) + " </h2>\n\n<div class=\"ci\">\n");
				for (int i = 1; i <= book.getChapters().size(); i++) {
					sb.append("<a href=\"" + fileName + "." + i + ".xhtml\">" + i + "</a>");
				}
				sb.append("\n</div>\n\n</body>\n</html>");
				zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
				for (int i = 1; i <= book.getChapters().size(); i++) {
					zos.putNextEntry(new ZipEntry("OEBPS/" + fileName + "." + i + ".xhtml"));
					StringWriter sw = new StringWriter();
					writeChapter(sw, unsupportedFeatures, book, i, headlinesAfter);
					zos.write(sw.toString().getBytes(StandardCharsets.UTF_8));
				}
			}
		}
		if (!unsupportedFeatures.isEmpty()) {
			System.out.println("WARNING: Skipped unsupported features: " + unsupportedFeatures);
		}
	}

	private boolean paragraphOpen = false;

	private void writeChapter(StringWriter sw, Set<String> unsupportedFeatures, Book book, int cnum, Boolean headlinesAfter) throws IOException {
		sw.write("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n<html dir=\"ltr\" xmlns=\"http://www.w3.org/1999/xhtml\"" +
				" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"en\">\n<head>\n  <meta http-equiv=\"Content-Type\"" +
				" content=\"text/html;charset=UTF-8\" />\n  <title>" + xml(book.getShortName()) + " " + cnum + "</title>\n" +
				"  <link rel=\"stylesheet\" href=\"global.css\" type=\"text/css\" />\n</head>\n<body>\n\n<h2>" +
				xml(book.getShortName()) + " " + cnum + "</h2>\n\n");
		StringWriter footnoteWriter = new StringWriter();
		int[] footnoteCounter = { 0 };
		Chapter chapter = book.getChapters().get(cnum - 1);
		if (chapter.getProlog() != null)
			unsupportedFeatures.add("prolog");
		for (VirtualVerse vv : chapter.createVirtualVerses()) {
			boolean markerWritten = false;
			if (headlinesAfter != null) {
				if (headlinesAfter && !vv.getHeadlines().isEmpty()) {
					if (!paragraphOpen) {
						sw.write("<p>");
						paragraphOpen = true;
					}
					sw.write("<a id=\"c" + cnum + "_v" + vv.getNumber() + "\"></a>");
					markerWritten = true;
				}
				for (Headline h : vv.getHeadlines()) {
					closeParagraph(sw);
					int depth = Math.min(h.getDepth() + 2, 6);
					sw.write("<h" + depth + ">");
					h.accept(new EquipdVisitor(sw, "</h" + depth + ">\n\n", unsupportedFeatures, " in headline", footnoteWriter, footnoteCounter, book.getId().isNT()));
				}
			}
			sw.write(paragraphOpen ? " " : "<p>");
			paragraphOpen = true;
			if (!markerWritten) {
				sw.write("<a id=\"c" + cnum + "_v" + vv.getNumber() + "\"></a>");
			}
			sw.write("<span class=\"vn\">" + vv.getNumber() + "</span> ");
			boolean firstVerse = true;
			for (Verse v : vv.getVerses()) {
				if (!firstVerse || !v.getNumber().equals("" + vv.getNumber())) {
					sw.write(" <b>(" + v.getNumber() + ")</b> ");
				}
				v.accept(new EquipdVisitor(sw, "", unsupportedFeatures, " in verse", footnoteWriter, footnoteCounter, book.getId().isNT()));
				firstVerse = false;
			}
		}
		closeParagraph(sw);
		if (footnoteCounter[0] != 0) {
			sw.write("<div class=\"groupFootnote\">\n" + footnoteWriter.toString() + "</div>\n\n");
		}
		sw.write("</body>\n</html>");
	}

	private void closeParagraph(StringWriter sw) {
		if (paragraphOpen)
			sw.write("</p>\n\n");
		paragraphOpen = false;
	}

	private static String xml(String unescaped) {
		return unescaped.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static class EquipdVisitor extends AbstractHTMLVisitor {

		private final StringWriter footnoteWriter;
		private final Set<String> unsupportedFeatures;
		private final String featureSuffix;
		private final int[] footnoteCounter;
		private final boolean nt;

		private EquipdVisitor(Writer writer, String suffix, Set<String> unsupportedFeatures, String featureSuffix, StringWriter footnoteWriter, int[] footnoteCounter, boolean nt) {
			super(writer, suffix);
			this.unsupportedFeatures = unsupportedFeatures;
			this.featureSuffix = featureSuffix;
			this.footnoteWriter = footnoteWriter;
			this.footnoteCounter = footnoteCounter;
			this.nt = nt;
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			writer.write("<span style=\"color: #808080;\">/</span>");
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			if (kind == FormattingInstructionKind.UNDERLINE) {
				writer.write("<span style=\"" + kind.getCss() + "\">");
				pushSuffix("</span>");
				return this;
			} else {
				return super.visitFormattingInstruction(kind);
			}
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			unsupportedFeatures.add("headline" + featureSuffix);
			return null;
		}

		@Override
		public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
			Visitor<IOException> result = visitFootnote0();
			if (ofCrossReferences)
				result.visitText(FormattedText.XREF_MARKER);
			return result;
		}

		public Visitor<IOException> visitFootnote0() throws IOException {
			if (footnoteWriter == null) {
				unsupportedFeatures.add("footnote" + featureSuffix);
				return null;
			}
			footnoteCounter[0]++;
			writer.write((VALIDATABLE ? "<a href=\"#footnote" : "<a epub:type=\"noteref\" href=\"#footnote") + footnoteCounter[0] + "\">*</a>");
			footnoteWriter.write((VALIDATABLE ? "  <div id=\"footnote" : "  <aside epub:type=\"footnote\"><div epub:type=\"footnote\" id=\"footnote") + footnoteCounter[0] + "\"><p>");
			return new EquipdVisitor(footnoteWriter, VALIDATABLE ? "</p></div>\n" : "</p></div></aside>\n", unsupportedFeatures, " in footnote", null, null, nt);
		}

		@Override
		public Visitor<IOException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws IOException {
			unsupportedFeatures.add("cross reference" + featureSuffix);
			pushSuffix("");
			return this;
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind lbk, int indent) throws IOException {
			LineBreakKind kind = lbk.toLineBreakKind(indent);
			switch (kind) {
			case NEWLINE:
				writer.write("<br />");
				break;
			case NEWLINE_WITH_INDENT:
				writer.write("<br />\u00A0\u00A0");
				break;
			case PARAGRAPH:
				writer.write("</p>\n\n<p>");
				break;

			}
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws IOException {
			if (rmac != null)
				unsupportedFeatures.add("rmac" + featureSuffix);
			if (sourceIndices != null)
				unsupportedFeatures.add("source index" + featureSuffix);
			if (attributeKeys != null)
				unsupportedFeatures.add("grammar attributes" + featureSuffix);
			if (VALIDATABLE || strongs == null) {
				pushSuffix("");
				return this;
			}
			if (strongs.length != 1)
				unsupportedFeatures.add("multi strongs" + featureSuffix);
			if (strongsSuffixes != null)
				unsupportedFeatures.add("strongs suffixes" + featureSuffix);
			boolean useNT = nt;
			if (strongsPrefixes != null && strongsPrefixes[0] == 'G')
				useNT = true;
			if (strongsPrefixes != null && strongsPrefixes[0] == 'H')
				useNT = false;
			writer.write("<a href=\"" + (useNT ? "" : "0") + strongs[0] + "\" class=\"strongs\">");
			pushSuffix("</a>");
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			unsupportedFeatures.add("dictionary entry" + featureSuffix);
			pushSuffix("");
			return this;
		}

		@Override
		public Visitor<IOException> visitSpeaker(String labelOrStrongs) throws IOException {
			return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "speaker", labelOrStrongs);
		}

	}
}
