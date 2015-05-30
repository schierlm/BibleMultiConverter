package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
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

public class YCHPalmBible implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export format for YCHBibleConverter for PalmBible+"
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
						for (Verse vv : v.getVerses()) {
							if (!vv.getNumber().equals("" + v.getNumber())) {
								bw.write("{" + vv.getNumber() + "} ");
							}
							vv.accept(contentVisitor);
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
}
