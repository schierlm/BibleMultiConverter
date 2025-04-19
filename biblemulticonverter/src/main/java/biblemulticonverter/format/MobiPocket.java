package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.HyperlinkType;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.tools.MobiPocketBXR;
import biblemulticonverter.tools.MobiPocketBXR.BookInfo;

public class MobiPocket implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"MobiPocket ebook format (predecessor of Kindle's format)",
			"",
			"Usage: MobiPocket <OutputFile>"
	};

	private static final String TITLEPREFIX = "Die Bibel - ";
	private static final String LANGUAGE = "de";
	private static final String TOC = "Inhaltsverzeichnis";

	StringBuffer footNotes = new StringBuffer(), crossRefs = new StringBuffer();
	int footNoteCount = 0;
	String chapref = "";
	List<MobiPocketBXR> bxrs = new ArrayList<MobiPocketBXR>();

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		footNotes.setLength(0);
		crossRefs.setLength(0);
		footNoteCount = 0;
		chapref = "";
		File exportFile = new File(exportArgs[0]);
		String title = bible.getName();
		File directory = exportFile.getParentFile();
		String filename = exportFile.getName();
		try (final BufferedWriter opfw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, filename + ".opf")), "UTF-8"))) {
			opfw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n" +
					"<package unique-identifier=\"uid\">\r\n" +
					"  <metadata>\r\n" +
					"    <dc-metadata xmlns:dc=\"http://purl.org/metadata/dublin_core\" xmlns:oebpackage=\"http://openebook.org/namespaces/oeb-package/1.0/\">\r\n" +
					"      <dc:Title>" + TITLEPREFIX + title + "</dc:Title>\r\n" +
					"      <dc:Language>" + LANGUAGE + "</dc:Language>\r\n" +
					"    </dc-metadata>\r\n" +
					"    <x-metadata>\r\n" +
					"      <DatabaseName>Bible" + filename + "</DatabaseName>\r\n" +
					"      <output encoding=\"Windows-1252\"></output>\r\n" +
					"    </x-metadata>\r\n" +
					"  </metadata>\r\n" +
					"  <manifest>\r\n" +
					"    <item id=\"onlyfile\" media-type=\"text/x-oeb1-document\" href=\"" + filename + ".html\"></item>\r\n" +
					"  </manifest>\r\n" +
					"  <spine>\r\n" +
					"    <itemref idref=\"onlyfile\" />\r\n" +
					"  </spine>\r\n" +
					"  <tours></tours>\r\n" +
					"  <guide></guide>\r\n" +
					"</package>\r\n" +
					"");
		}
		boolean isDictionary = false;
		for (Book bk : bible.getBooks()) {
			if (bk.getId() == BookID.DICTIONARY_ENTRY) {
				isDictionary = true;
				break;
			} else if (bk.getId().getZefID() > 0) {
				isDictionary = false;
				break;
			}
		}
		try (final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, filename + ".html")), "UTF-8"))) {
			bw.write("<html><head>");
			bw.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">");
			bw.write("<style type=\"text/css\">body {font-family: Verdana, Arial, Helvetica, sans-serif}</style>");
			bw.write("<title>" + TITLEPREFIX + title + "</title></head><body>");
			bw.newLine();
			bw.write("<h1>" + title + "</h1>");
			if (isDictionary) {
				bw.write("<a onclick=\"index_search()\">Suchen</a><br>");
			}
			bw.write("<h2>" + TOC + "</h2>");
			bw.newLine();
			List<String> books = new ArrayList<String>();
			Map<String, Integer> maxChapter = new HashMap<String, Integer>();
			Map<String, BookID> bookIDs = new HashMap<String, BookID>();
			for (Book bk : bible.getBooks()) {
				books.add(bk.getAbbr());
				if (isDictionary) {
					bw.write("<p><a href=\"#" + bk.getAbbr() + "\">" + bk.getLongName() + "</a></p>");
				} else {
					bw.write("<p><a href=\"#b" + bookRef(bk) + "\">" + bk.getLongName() + " (" + bk.getAbbr() + ")</a></p>");
				}
				bw.newLine();
				maxChapter.put(bk.getAbbr(), bk.getChapters().size());
				bookIDs.put(bk.getAbbr(), bk.getId());
			}
			try (final BufferedWriter bxw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, filename + ".bxr")), "UTF-8"))) {
				bxw.write(title);
				bxw.newLine();
				for (String bk : books) {
					bxw.write(bk + "|" + bookRef(bk, bookIDs.get(bk)) + "|" + maxChapter.get(bk));
					bxw.newLine();
				}
			}
			bxrs.add(new MobiPocketBXR(filename, new File(directory, filename + ".bxr")));
			final String lexiconName = (isDictionary ? "Bible" : "BibleDict") + filename;
			for (Book bk : bible.getBooks()) {
				if (bk.getId() == BookID.DICTIONARY_ENTRY) {
					bw.write("<mbp:pagebreak><idx:entry>");
					bw.newLine();
					bw.write("<h2><a name=\"" + bk.getAbbr() + "\" external=\"yes\"><idx:orth>" + bk.getLongName() + "</idx:orth></a></h2>");
					bw.newLine();
					bw.write("<p>");
					bw.newLine();
					writeVerse(bw, bk.getChapters().get(0).getProlog(), bible, "", lexiconName);
					bw.write("</p>");
					bw.newLine();
					writeFootNotes(bw);
					bw.write("</idx:entry>");
				} else {
					bw.write("<mbp:pagebreak>");
					bw.newLine();
					chapref = "b" + bookRef(bk);
					bw.write("<h2><a name=\"" + chapref + "\" external=\"yes\">" + bk.getLongName() + " (" + bk.getAbbr() + ")</a> (<a href=\"oeb:redirect?title=BibleNavigation#" + chapref + "\">Navigation</a>)</h2>");
					bw.newLine();
					int chapter = 0;
					for (Chapter ch : bk.getChapters()) {
						chapter++;
						chapref = "b" + bookRef(bk) + "c" + chapter;
						bw.write("<h3><a name=\"" + chapref + "\" external=\"yes\">" + bk.getAbbr() + " " + chapter + "</a> (<a href=\"oeb:redirect?title=BibleNavigation#" + chapref + "\">Navigation</a>)</h3>");
						bw.newLine();
						if (ch.getProlog() != null) {
							bw.write("<small>");
							bw.newLine();
							writeVerse(bw, ch.getProlog(), bible, "", lexiconName);
							bw.write("</small>");
							bw.newLine();
						}
						for (final Verse v : ch.getVerses()) {
							writeVerse(bw, v, bible, "<b>" + v.getNumber() + "</b> ", lexiconName);
						}
						writeFootNotes(bw);
					}
				}
			}
		}
	}

	private void writeVerse(BufferedWriter bw, FormattedText v, final Bible bb, final String versePrefix, final String lexiconName) throws IOException {
		final String lineSeparator = System.getProperty("line.separator");
		v.accept(new AbstractHTMLVisitor(bw, "</p>" + lineSeparator) {
			@Override
			public Visitor<IOException> visitHeadline(int depth) throws IOException {
				int level = depth < 3 ? depth + 3 : 6;
				writer.write("<h" + level + ">");
				pushSuffix("</h" + level + ">" + lineSeparator);
				return this;
			}

			@Override
			public void visitStart() throws IOException {
				if (suffixStack.size() == 1) {
					writer.write("<p>" + versePrefix);
				}
			}

			@Override
			protected String getNextFootnoteTarget() {
				return "#" + chapref + "f" + (footNoteCount + 1);
			}

			@Override
			public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
				final Writer outerWriter = writer;
				return new AbstractHTMLVisitor(new StringWriter(), "") {

					@Override
					public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws IOException {
						writer.append("<br>");
					}

					@Override
					public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
						throw new IllegalStateException("Footnote in footnote");
					}

					@Override
					public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws IOException {
						pushSuffix("");
						return this;
					}

					@Override
					public FormattedText.Visitor<IOException> visitDictionaryEntry(String dictionary, String entry)
							throws IOException {
						writer.write("<a href=\"oeb:redirect?title=" + lexiconName + "#" + entry + "\">");
						pushSuffix("</a>");
						return this;
					}

					@Override
					public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
						if (mode != RawHTMLMode.ONLINE)
							writer.write(raw);
					}

					@Override
					public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
						throw new UnsupportedOperationException("Variations not supported");
					}

					@Override
					public Visitor<IOException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws IOException {
						if (checkBXR(bookRef(firstBookAbbr, firstBook), firstChapter)) {
							writer.write("<a href=\"oeb:redirect?title=BibleNavigation#b" + bookRef(firstBookAbbr, firstBook) + "c" + firstChapter + "\">");
							pushSuffix("</a>");
						} else {
							pushSuffix("");
						}
						return this;
					}

					@Override
					public Visitor<IOException> visitSpeaker(String labelOrStrongs) throws IOException {
						pushSuffix("");
						return this;
					}

					@Override
					public boolean visitEnd() throws IOException {
						super.visitEnd();
						if (suffixStack.size() == 0) {
							if (ofCrossReferences) {
								// this is a cross reference!
								if (crossRefs.length() > 0)
									crossRefs.append("<br>");
								String xref = writer.toString().trim();
								if (!xref.startsWith("<b>"))
									xref = versePrefix + xref;
								crossRefs.append(xref);
							} else {
								footNoteCount++;
								outerWriter.write("<sup><a name=\"" + chapref + "ft" + footNoteCount + "\" href=\"#" + chapref + "f" + footNoteCount + "\">" + footNoteCount + "</a></sup>");
								if (footNotes.length() > 0)
									footNotes.append("<br>");
								footNotes.append("<sup><a name=\"" + chapref + "f" + footNoteCount + "\" href=\"#" + chapref + "ft" + footNoteCount + "\">" + footNoteCount + "</a></sup> ");
								footNotes.append(writer.toString());
							}
						}
						return false;
					}

					@Override
					public Visitor<IOException> visitHeadline(int depth) throws IOException {
						throw new IOException("Headline inside footnote");
					}
				};
			}

			@Override
			public FormattedText.Visitor<IOException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws IOException {
				if (checkBXR(bookRef(firstBookAbbr, firstBook), firstChapter)) {
					writer.write("<a href=\"oeb:redirect?title=BibleNavigation#b" + bookRef(firstBookAbbr, firstBook) + "c" + firstChapter + "\">");
					pushSuffix("</a>");
				} else {
					pushSuffix("");
				}
				return this;
			};

			@Override
			public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws IOException {
				writer.write("<br>");
			}

			@Override
			public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKEys, String[] attributeValues) throws IOException {
				pushSuffix("");
				return this;
			}

			@Override
			public Visitor<IOException> visitSpeaker(String labelOrStrongs) throws IOException {
				pushSuffix("");
				return this;
			}

			@Override
			public FormattedText.Visitor<IOException> visitDictionaryEntry(String dictionary, String entry)
					throws IOException {
				writer.write("<a href=\"oeb:redirect?title=" + lexiconName + "#" + entry + "\">");
				pushSuffix("</a>");
				return this;
			}

			@Override
			public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
				throw new UnsupportedOperationException("Variations not supported");
			}

		});
	}

	private String bookRef(Book bk) {
		return bookRef(bk.getAbbr(), bk.getId());
	}

	private String bookRef(String abbr, BookID id) {
		if (id.getZefID() > 0)
			return "" + id.getZefID();
		return abbr;
	}

	private void writeFootNotes(BufferedWriter bw) throws IOException {
		if (footNotes.length() == 0 && crossRefs.length() == 0)
			return;

		bw.write("<hr width=\"50%\"><small>");
		bw.newLine();
		if (crossRefs.length() != 0) {
			bw.write("<p>" + crossRefs.toString() + "</p>");
			bw.newLine();
		}
		if (footNotes.length() != 0) {
			bw.write("<p>" + footNotes.toString() + "</p>");
			bw.newLine();
		}
		bw.write("</small><hr width=\"50%\">");
		bw.newLine();

		footNotes.setLength(0);
		crossRefs.setLength(0);
		footNoteCount = 0;
	}

	private boolean checkBXR(String bookRef, int chapter) {
		for (MobiPocketBXR bxr : bxrs) {
			for (BookInfo book : bxr.books) {
				if (book.ref.equals(bookRef)) {
					if (chapter > book.chapterCount) {
						System.out.println("WARNING: " + bookRef + " (" + book.book + ") has " + book.chapterCount + " chapters, < " + chapter);
						return false;
					} else {
						return true;
					}
				}
			}
		}
		System.out.println("WARNING: Book not found: " + bookRef);
		return false;
	}
}
