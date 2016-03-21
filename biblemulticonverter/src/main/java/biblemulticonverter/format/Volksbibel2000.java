package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;

public class Volksbibel2000 implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export format for reimporting into Volksbibel 2000.",
			"",
			"Usage: Volksbibel2000 <outfile> [<escapeChar>]",
			"",
			"Take the exported file, and import it in Volksbibel 2000 by Ctrl+clicking the 'Integration'",
			"button, then following the instructions. Usually, only verses and headlines without formatting",
			"can be exported; if you have a copy of LiveCode available, you can also specify an escape",
			"character (for example ¤), open the imported module in LiveCode and perform a few search&replace",
			"operations on 'the htmltext' of the module to get formatting. For more details, refer to the",
			"header of the exported file."
	};

	private static final Map<BookID, String> BOOK_NAMES = new EnumMap<>(BookID.class);
	private static final Pattern BOOK_HEADERS, MISDETECTED_WORDS = Pattern.compile("([0-5]\\.) (Mose|Samuel|Könige|Chronika|Makkabäer|Korinther|Thessalonicher|Timotheus|Petrus|Johannes)");

	static {
		BOOK_NAMES.put(BookID.BOOK_Gen, "Gen");
		BOOK_NAMES.put(BookID.BOOK_Exod, "Ex");
		BOOK_NAMES.put(BookID.BOOK_Lev, "Lev");
		BOOK_NAMES.put(BookID.BOOK_Num, "Num");
		BOOK_NAMES.put(BookID.BOOK_Deut, "Dtn");
		BOOK_NAMES.put(BookID.BOOK_Josh, "Jos");
		BOOK_NAMES.put(BookID.BOOK_Judg, "Ri");
		BOOK_NAMES.put(BookID.BOOK_Ruth, "Rut");
		BOOK_NAMES.put(BookID.BOOK_1Sam, "1Sam");
		BOOK_NAMES.put(BookID.BOOK_2Sam, "2Sam");
		BOOK_NAMES.put(BookID.BOOK_1Kgs, "1Kön");
		BOOK_NAMES.put(BookID.BOOK_2Kgs, "2Kön");
		BOOK_NAMES.put(BookID.BOOK_1Chr, "1Chr");
		BOOK_NAMES.put(BookID.BOOK_2Chr, "2Chr");
		BOOK_NAMES.put(BookID.BOOK_Ezra, "Esra");
		BOOK_NAMES.put(BookID.BOOK_Neh, "Neh");
		BOOK_NAMES.put(BookID.BOOK_Tob, "Tob");
		BOOK_NAMES.put(BookID.BOOK_Jdt, "Jdt");
		BOOK_NAMES.put(BookID.BOOK_Esth, "Est");
		BOOK_NAMES.put(BookID.BOOK_1Macc, "1Makk");
		BOOK_NAMES.put(BookID.BOOK_2Macc, "2Makk");
		BOOK_NAMES.put(BookID.BOOK_Job, "Ijob");
		BOOK_NAMES.put(BookID.BOOK_Ps, "Ps");
		BOOK_NAMES.put(BookID.BOOK_Prov, "Spr");
		BOOK_NAMES.put(BookID.BOOK_Eccl, "Koh");
		BOOK_NAMES.put(BookID.BOOK_Song, "Hld");
		BOOK_NAMES.put(BookID.BOOK_Wis, "Weish");
		BOOK_NAMES.put(BookID.BOOK_Sir, "Sir");
		BOOK_NAMES.put(BookID.BOOK_Isa, "Jes");
		BOOK_NAMES.put(BookID.BOOK_Jer, "Jer");
		BOOK_NAMES.put(BookID.BOOK_Lam, "Klgl");
		BOOK_NAMES.put(BookID.BOOK_Bar, "Bar");
		BOOK_NAMES.put(BookID.BOOK_Ezek, "Ez");
		BOOK_NAMES.put(BookID.BOOK_Dan, "Dan");
		BOOK_NAMES.put(BookID.BOOK_Hos, "Hos");
		BOOK_NAMES.put(BookID.BOOK_Joel, "Joel");
		BOOK_NAMES.put(BookID.BOOK_Amos, "Amos");
		BOOK_NAMES.put(BookID.BOOK_Obad, "Obd");
		BOOK_NAMES.put(BookID.BOOK_Jonah, "Jona");
		BOOK_NAMES.put(BookID.BOOK_Mic, "Mi");
		BOOK_NAMES.put(BookID.BOOK_Nah, "Nah");
		BOOK_NAMES.put(BookID.BOOK_Hab, "Hab");
		BOOK_NAMES.put(BookID.BOOK_Zeph, "Zef");
		BOOK_NAMES.put(BookID.BOOK_Hag, "Hag");
		BOOK_NAMES.put(BookID.BOOK_Zech, "Sach");
		BOOK_NAMES.put(BookID.BOOK_Mal, "Mal");
		BOOK_NAMES.put(BookID.BOOK_Matt, "Mt");
		BOOK_NAMES.put(BookID.BOOK_Mark, "Mk");
		BOOK_NAMES.put(BookID.BOOK_Luke, "Lk");
		BOOK_NAMES.put(BookID.BOOK_John, "Joh");
		BOOK_NAMES.put(BookID.BOOK_Acts, "Apg");
		BOOK_NAMES.put(BookID.BOOK_Rom, "Röm");
		BOOK_NAMES.put(BookID.BOOK_1Cor, "1Kor");
		BOOK_NAMES.put(BookID.BOOK_2Cor, "2Kor");
		BOOK_NAMES.put(BookID.BOOK_Gal, "Gal");
		BOOK_NAMES.put(BookID.BOOK_Eph, "Eph");
		BOOK_NAMES.put(BookID.BOOK_Phil, "Phil");
		BOOK_NAMES.put(BookID.BOOK_Col, "Kol");
		BOOK_NAMES.put(BookID.BOOK_1Thess, "1Thess");
		BOOK_NAMES.put(BookID.BOOK_2Thess, "2Thess");
		BOOK_NAMES.put(BookID.BOOK_1Tim, "1Tim");
		BOOK_NAMES.put(BookID.BOOK_2Tim, "2Tim");
		BOOK_NAMES.put(BookID.BOOK_Titus, "Tit");
		BOOK_NAMES.put(BookID.BOOK_Phlm, "Phlm");
		BOOK_NAMES.put(BookID.BOOK_Heb, "Hebr");
		BOOK_NAMES.put(BookID.BOOK_Jas, "Jak");
		BOOK_NAMES.put(BookID.BOOK_1Pet, "1Petr");
		BOOK_NAMES.put(BookID.BOOK_2Pet, "2Petr");
		BOOK_NAMES.put(BookID.BOOK_1John, "1Joh");
		BOOK_NAMES.put(BookID.BOOK_2John, "2Joh");
		BOOK_NAMES.put(BookID.BOOK_3John, "3Joh");
		BOOK_NAMES.put(BookID.BOOK_Jude, "Jud");
		BOOK_NAMES.put(BookID.BOOK_Rev, "Offb");
		StringBuilder bookHeaderRegex = new StringBuilder("(Kapitel");
		for (String name : BOOK_NAMES.values()) {
			bookHeaderRegex.append("|" + name);
		}
		BOOK_HEADERS = Pattern.compile(bookHeaderRegex.append(") ([1-9])").toString());
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		// update chapter splitting for Bibles that use a different one
		for (Book bk : bible.getBooks()) {
			if (bk.getId() == BookID.BOOK_Joel && bk.getChapters().size() == 3) {
				System.out.println("INFO: Splitting Joel chapters...");
				Chapter ch2 = bk.getChapters().get(1);
				Chapter ch3 = new Chapter();
				for (int i = 0; i < ch2.getVerses().size(); i++) {
					Verse v = ch2.getVerses().get(i);
					try {
						int vnum = Integer.parseInt(v.getNumber());
						if (vnum >= 28) {
							ch2.getVerses().remove(i);
							Verse vv = new Verse("2," + v.getNumber());
							v.accept(vv.getAppendVisitor());
							vv.finished();
							ch3.getVerses().add(vv);
							i--;
						}
					} catch (NumberFormatException ex) {
						// ignore non-numeric verses
					}
				}
				if (!ch3.getVerses().isEmpty()) {
					bk.getChapters().add(2, ch3);
				}
			} else if (bk.getId() == BookID.BOOK_Mal && bk.getChapters().size() == 4) {
				System.out.println("INFO: merging Maleachi chapters...");
				Chapter ch3 = bk.getChapters().get(2);
				Chapter ch4 = bk.getChapters().remove(3);
				for (Verse v : ch4.getVerses()) {
					Verse vv = new Verse("4," + v.getNumber());
					v.accept(vv.getAppendVisitor());
					vv.finished();
					ch3.getVerses().add(vv);
				}
			}
		}
		String escapeChar = exportArgs.length == 1 ? "" : exportArgs[1];
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(exportArgs[0])), StandardCharsets.ISO_8859_1))) {

			bw.write("Importdatei für Volksbibel 2000");
			bw.newLine();
			bw.write("Klicken Sie im Hauptfenster bei gedrückter STRG-Taste auf den Button");
			bw.newLine();
			bw.write("\"Integration\", wählen Sie diese Datei aus und folgen Sie den Anweisungen");
			bw.newLine();
			bw.write("auf dem Bildschirm.");
			bw.newLine();
			bw.newLine();
			if (!escapeChar.isEmpty()) {
				bw.write("Nach dem erfolgreichen Import öffnen Sie die .mc-Datei in LiveCode und");
				bw.newLine();
				bw.write("lassen folgendes Script laufen:");
				bw.newLine();
				bw.newLine();
				bw.write("   local myData, escapeChar");
				bw.newLine();
				bw.write("   put \"" + escapeChar + "\" into escapeChar");
				bw.newLine();
				bw.write("   repeat with i = 2 to the number of cards");
				bw.newLine();
				bw.write("      put the htmltext of field 2 of card i into myData");
				bw.newLine();
				bw.write("      replace escapeChar & \"&lt;\" with \"<\" in myData");
				bw.newLine();
				bw.write("      replace escapeChar & \"&gt;\" with \">\" in myData");
				bw.newLine();
				bw.write("      replace escapeChar & \" \" with \"\" in myData");
				bw.newLine();
				bw.write("      replace escapeChar & \"&amp;\" with \"&\" in myData");
				bw.newLine();
				bw.write("      set the htmltext of field 2 of card i to myData");
				bw.newLine();
				bw.write("   end repeat //");
				bw.newLine();
				bw.write("   answer \"Fertig.\" //");
				bw.newLine();
				bw.newLine();
			}
			for (Book book : bible.getBooks()) {
				String bookAbbr = BOOK_NAMES.get(book.getId());
				if (bookAbbr == null) {
					System.out.println("WARNING: Skipping book " + book.getAbbr());
					continue;
				}
				int cnumber = 0;
				for (Chapter chapter : book.getChapters()) {
					StringBuilder content = new StringBuilder();
					cnumber++;
					bw.write(bookAbbr + " " + cnumber);
					bw.newLine();
					for (Verse v : chapter.getVerses()) {
						v.accept(new Vb2000Visitor(content, v, "", escapeChar));
						content.append('\n');
					}
					for (String line : content.toString().split("\n")) {
						line = BOOK_HEADERS.matcher(line).replaceAll("$1 " + escapeChar + " $2");
						line = MISDETECTED_WORDS.matcher(line).replaceAll("$1 " + escapeChar + " $2");
						line = line.replace("Lied der Lieder", "Lied " + escapeChar + " der Lieder");
						while (!line.isEmpty() && line.split(" ").length < 3) {
							if (escapeChar.isEmpty())
								line += " *";
							else
								line = escapeChar + " " + line;
						}
						bw.write(line);
						bw.newLine();
					}
				}
			}
		}
	}

	private static class Vb2000Visitor implements Visitor<RuntimeException> {

		private Verse verse;
		private String suffix;
		private String escapeChar;
		private boolean inVerse = false;
		private StringBuilder content;

		public Vb2000Visitor(StringBuilder content, Verse verse, String suffix, String escapeChar) {
			this.content = content;
			this.verse = verse;
			this.suffix = suffix;
			this.escapeChar = escapeChar;
		}

		private void ensureInVerse() {
			if (verse == null || inVerse)
				return;
			content.append(verse.getNumber() + " ");
			inVerse = true;
		}

		private void visitTag(String tag) {
			content.append(buildTag(tag));
		}

		private String buildTag(String tag) {
			if (escapeChar.isEmpty())
				return "";
			return escapeChar + "<" + tag + escapeChar + ">";
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			if (verse == null)
				throw new RuntimeException("Headlines must be toplevel");
			if (inVerse) {
				content.append("\n");
				inVerse = false;
			}
			String endTag = buildTag("/B");
			if (depth == 1) {
				visitTag("FONT size=\"18\"");
				endTag += buildTag("/FONT");
			} else if (depth == 2) {
				visitTag("FONT size=\"16\"");
				endTag += buildTag("/FONT");
			}
			visitTag("B");
			return new Vb2000Visitor(content, null, endTag + "\n", escapeChar);
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			if (verse == null)
				return; // ignore linebreaks that are not toplevel
			inVerse = false;
			switch (kind) {
			case NEWLINE:
				content.append("\n");
				break;
			case PARAGRAPH:
				content.append("\n\n");
				break;
			case NEWLINE_WITH_INDENT:
				content.append("\n" + verse.getNumber() + "    ");
				inVerse = true;
			}
		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			ensureInVerse();
			if (text.matches("[ -\u00FF]*") && (escapeChar.isEmpty() || !text.contains(escapeChar))) {
				content.append(text);
			} else {
				for (int i = 0; i < text.length(); i++) {
					char ch = text.charAt(i);
					if (ch < 0x100 && !escapeChar.equals("" + ch)) {
						content.append(ch);
					} else if (!escapeChar.isEmpty()) {
						content.append(escapeChar + "&#" + (int) ch + ";");
					}
				}
			}
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
			ensureInVerse();
			visitTag("FONT size=\"10\" color=\"#ff0000\"");
			visitText("[");
			return new Vb2000Visitor(content, null, "]" + buildTag("/FONT"), escapeChar);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			ensureInVerse();
			visitTag("a");
			return new Vb2000Visitor(content, null, buildTag("/a"), escapeChar);
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			ensureInVerse();
			String endTag = "";
			if (kind.getHtmlTag() != null) {
				visitTag(kind.getHtmlTag().toUpperCase());
				endTag = buildTag("/" + kind.getHtmlTag().toUpperCase());
			} else {
				switch (kind) {
				case FOOTNOTE_LINK:
				case LINK:
					visitTag("FONT color=\"#0000ff\"");
					endTag = buildTag("/FONT");
					break;
				case WORDS_OF_JESUS:
					visitTag("FONT color=\"#ff0000\"");
					endTag = buildTag("/FONT");
					break;
				default:
					break;
				}
			}
			return new Vb2000Visitor(content, null, endTag, escapeChar);
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			ensureInVerse();
			return new Vb2000Visitor(content, null, "", escapeChar);
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			ensureInVerse();
			visitTag("FONT color=\"#808080\"");
			visitText("/");
			visitTag("/FONT");
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			ensureInVerse();
			StringBuilder strongSuffix = new StringBuilder(buildTag("SUP") + buildTag("FONT color=\"#008000\""));
			if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					strongSuffix.append(i == 0 ? "<" : " ").append(strongs[i]);
				}
				strongSuffix.append(">" + buildTag("/FONT") + buildTag("/SUP"));
			} else {
				strongSuffix.setLength(0);
			}
			return new Vb2000Visitor(content, null, strongSuffix.toString(), escapeChar);
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			ensureInVerse();
			return new Vb2000Visitor(content, null, "", escapeChar);
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			throw new RuntimeException("Variations not supported");
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			ensureInVerse();
			return prio.handleVisitor(category, new Vb2000Visitor(content, null, "", escapeChar));
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			content.append(suffix);
			return false;
		}
	}
}
