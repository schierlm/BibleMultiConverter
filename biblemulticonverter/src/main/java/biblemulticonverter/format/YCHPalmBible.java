package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.MetadataBook.MetadataBookKey;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;

public class YCHPalmBible implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"YCHBibleConverter for PalmBible+",
			"",
			"Usage (export): YCHPalmBible <OutputFile>"
	};

	private static final String CHAPTER_NAME = "Kapitel";

	private static final int[] PALM_BOOK_NUMBERS = { 0,
			10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160,
			190, 220, 230, 240, 250, 260, 290, 300, 310, 330, 340, 350, 360, 370,
			380, 390, 400, 410, 420, 430, 440, 450, 460, 470, 480, 490, 500, 510,
			520, 530, 540, 550, 560, 570, 580, 590, 600, 610, 620, 630, 640, 650,
			660, 670, 680, 690, 700, 710, 720, 730, 180, 270, 170, 280, 320, 200,
			210, 0, 0, 790, 215, 216, 315, 145, 146, 1001, 1002, 1802, 0, 0, 335,
			346, 991,
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String filename = exportArgs[0];
		String description = bible.getName();
		MetadataBook metadata = bible.getMetadataBook();
		if (metadata != null) {
			String metaDescription = bible.getMetadataBook().getValue(MetadataBookKey.description);
			if (metaDescription != null)
				description = metaDescription;
		}

		try (final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), "windows-1252"))) {
			bw.write("<PARSERINFO ENCODE=\"Cp1252\" WORDTYPE=\"SPCSEP\">");
			bw.newLine();
			bw.write("<BIBLE NAME=\"" + bible.getName() + "\" INFO=\"" + description + "\">");
			bw.newLine();
			Visitor<IOException> contentVisitor = new FormattedText.VisitorAdapter<IOException>(null) {
				@Override
				public void visitVerseSeparator() throws IOException {
					// strip
				}

				@Override
				public void visitText(String text) throws IOException {
					bw.write(text);
				}

				@Override
				public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
					return this;
				}

				@Override
				public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
					return this;
				}

				@Override
				public void visitLineBreak(LineBreakKind kind) throws IOException {
					bw.write(" ");
				}
			};

			for (Book bk : bible.getBooks()) {
				int zefID = bk.getId().getZefID();
				if (zefID < 1 || zefID >= PALM_BOOK_NUMBERS.length || PALM_BOOK_NUMBERS[zefID] == 0) {
					System.out.println("WARNING: Skipping unsupported book " + bk.getAbbr() + " (" + bk.getId().getOsisID() + ")");
					continue;
				}
				bw.write("<BOOK NAME=\"" + bk.getShortName() + "\" NUMBER=\"" + PALM_BOOK_NUMBERS[bk.getId().getZefID()] + "\" SHORTCUT=\"" + bk.getAbbr() + "\">");
				bw.newLine();
				String longtitle = bk.getLongName();
				int chapter = 0, verse;
				for (Chapter chap : bk.getChapters()) {
					chapter++;
					if (chap.getProlog() != null)
						System.out.println("WARNING: Skipping prolog (prologs not supported)!");
					verse = 1;
					String chaptext = CHAPTER_NAME + " " + chapter;
					bw.write("<CHAPTER>");
					bw.newLine();
					for (VirtualVerse v : chap.createVirtualVerses()) {
						while (v.getNumber() > verse) {
							bw.write("<VERSE></VERSE>");
							bw.newLine();
							verse++;
						}
						if (v.getNumber() != verse)
							throw new RuntimeException("Verse is " + v.getNumber() + ", should be " + verse);
						boolean needVersText = false;
						bw.write("<VERSE>");
						if (longtitle != null) {
							bw.write("<BOOKTEXT>" + longtitle);
							longtitle = null;
							needVersText = true;
						}
						if (chaptext != null) {
							bw.write("<CHAPTEXT>" + chaptext);
							chaptext = null;
							needVersText = true;
						}
						for (Headline hl : v.getHeadlines()) {
							bw.write("<DESCTEXT>");
							hl.accept(contentVisitor);
							needVersText = true;
						}
						if (needVersText)
							bw.write("<VERSTEXT>");
						boolean firstVerse = true;
						for (Verse vv : v.getVerses()) {
							if (!firstVerse || !vv.getNumber().equals("" + v.getNumber())) {
								bw.write("{" + vv.getNumber() + "} ");
							}
							vv.accept(contentVisitor);
							firstVerse = false;
						}
						bw.write("</VERSE>");
						verse++;
						bw.newLine();
					}
					bw.write("</CHAPTER>");
					bw.newLine();
				}
				bw.write("</BOOK>");
				bw.newLine();
			}
			bw.write("</BIBLE>");
			bw.newLine();
		}
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		String content = new String(Files.readAllBytes(inputFile.toPath()), StandardCharsets.ISO_8859_1);
		content = content.replaceAll("[\r\n\t ]+", " ").replace(" <", "<").replaceAll("> ", ">");
		if (!content.startsWith("<PARSERINFO "))
			throw new IOException("Invalid file, does not start with <PARSERINFO>");
		int pos = content.indexOf('>');
		Map<String, String> params = parseParams(content.substring(12, pos));

		String charset = params.get("DECODE");
		if (charset == null)
			charset = params.get("ENCODE");
		else
			charset = "ISO-8859-1";
		content = new String(content.substring(pos + 1).getBytes(StandardCharsets.ISO_8859_1), charset);
		if (!content.startsWith("<BIBLE ")) {
			throw new IOException("Missing tag <BIBLE>");
		}
		pos = content.indexOf('>');
		params = parseParams(content.substring(7, pos));
		String name = params.get("NAME");
		String info = params.get("INFO");
		if (name == null || name.isEmpty())
			name = "Untitled YCHPalmBible bible";
		Bible bbl = new Bible(name);
		if (info != null && !info.equals(name)) {
			MetadataBook mb = new MetadataBook();
			mb.setValue(MetadataBookKey.description, info);
			bbl.getBooks().add(mb.getBook());
		}
		int offs = pos + 1;
		while (content.startsWith("<BOOK ", offs)) {
			pos = content.indexOf('>', offs);
			params = parseParams(content.substring(offs + 6, pos));
			offs = pos + 1;
			String bname = params.get("NAME");
			int bnumber = Integer.parseInt(params.get("NUMBER"));
			String babbr = params.get("SHORTCUT");
			BookID bid = null;
			for (int i = 0; i < PALM_BOOK_NUMBERS.length; i++) {
				if (PALM_BOOK_NUMBERS[i] == bnumber)
					bid = BookID.fromZefId(i);
			}
			if (bid == null)
				throw new IOException("Unsupported BOOK NUMBER: " + bnumber);
			Book bk = new Book(babbr, bid, bname, bname);
			while (content.startsWith("<CHAPTER>", offs)) {
				offs += 9;
				Chapter ch = new Chapter();
				int vnum = 1;
				while (content.startsWith("<VERSE>", offs)) {
					pos = content.indexOf("</VERSE>", offs);
					String[] verseContent = parseVerseContent(content.substring(offs + 7, pos));
					offs = pos + 8;
					Verse vv = new Verse("" + vnum);
					vnum++;
					if (verseContent.length == 1) {
						if (verseContent[0].isEmpty())
							continue;
						verseContent = new String[] { "", "<VERSTEXT>", verseContent[0] };
					}
					if (!verseContent[0].isEmpty())
						throw new IOException("Untagged text inside verse: " + verseContent[0]);
					for (int i = 1; i < verseContent.length; i += 2) {
						switch (verseContent[i]) {
						case "<BOOKTEXT>":
							if (bk.getChapters().size() > 0) {
								throw new IOException("<BOOKTEXT> not in first chapter");
							}
							bk = new Book(babbr, bid, bname, verseContent[i + 1]);
							break;
						case "<CHAPTEXT>":
							vv.getAppendVisitor().visitHeadline(1).visitText(verseContent[i + 1]);
							break;
						case "<DESCTEXT>":
							vv.getAppendVisitor().visitHeadline(9).visitText(verseContent[i + 1]);
							break;
						case "<VERSTEXT>":
							vv.getAppendVisitor().visitText(verseContent[i + 1]);
							break;
						default:
							throw new RuntimeException("Internal error parsing verse content: " + verseContent[i]);
						}
					}

					ch.getVerses().add(vv);
				}
				if (!content.startsWith("</CHAPTER>", offs))
					throw new IOException("<CHAPTER> tag not closed: " + babbr + "/" + bname);
				offs += 10;
				bk.getChapters().add(ch);
			}
			if (!content.startsWith("</BOOK>", offs))
				throw new IOException("<BOOK> tag not closed: " + babbr + "/" + bname);
			offs += 7;
			bbl.getBooks().add(bk);
		}
		if (!content.substring(offs).equals("</BIBLE>"))
			throw new IOException("Unknown tag, </BIBLE> expected");
		return bbl;
	}

	private Map<String, String> parseParams(String tagContent) throws IOException {
		Map<String, String> result = new HashMap<>();
		while (tagContent.length() > 1) {
			int pos = tagContent.indexOf("=\"");
			if (pos == -1)
				throw new IOException("Malformed parameter: " + tagContent);
			String name = tagContent.substring(0, pos);
			tagContent = tagContent.substring(pos + 2);
			pos = tagContent.indexOf('"');
			if (pos == -1)
				throw new IOException("Unclosed parameter value: " + tagContent);
			String value = tagContent.substring(0, pos);
			tagContent = tagContent.substring(pos + 1).trim();
			if (name.matches("[A-Z]+")) {
				result.put(name, value);
			} else {
				System.out.println("WARNING: Skipping unsupported attribute name: " + name);
			}
		}
		return result;
	}

	private String[] parseVerseContent(String content) {
		String[] delims = { "<BOOKTEXT>", "<CHAPTEXT>", "<DESCTEXT>", "<VERSTEXT>" };
		List<String> result = new ArrayList<>();
		while (true) {
			int pos = -1;
			for (String delim : delims) {
				int delimPos = content.indexOf(delim);
				if (delimPos != -1 && (pos == -1 || delimPos < pos)) {
					pos = delimPos;
				}
			}
			if (pos == -1)
				break;
			result.add(content.substring(0, pos));
			result.add(content.substring(pos, pos + 10));
			content = content.substring(pos + 10);
		}
		result.add(content);
		return (String[]) result.toArray(new String[result.size()]);
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return false;
	}
}
