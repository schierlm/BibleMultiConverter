package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import biblemulticonverter.data.Verse;

public class RoundtripTaggedText implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"A text-format that consistently uses numbered tags to make automated editing easy.",
			"",
			"This format uses HTML-like tags, but every open tag is matched with a close tag,",
			"and every occurrence of a tag (with its matching end tag) gets a unique number appended.",
			"That way, corresponding end tags can be easily found using regular expressions.",
			"",
			"Also, unlike Diffable format, everyhing uses tags here, no extra spaces are used",
			"and every < > and ~ is part of a tag (others are replaced by escape tags).",
			"",
			"Each book info, prolog or verse is on a separate line; therefore diffing the files is still feasible."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File exportFile = new File(exportArgs[0]);
		try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8))) {
			w.write("<bible~1>" + escape(bible.getName()) + "</bible~1>\n");
			for (Book book : bible.getBooks()) {
				int tc = ++tagCounter;
				writeTag(w, tc, "bookinfo", "bookabbr", book.getAbbr(), "bookid", book.getId().getOsisID(), "bookshortname", book.getShortName(), "booklongname", book.getLongName());
				w.write("</bookinfo~" + tc + ">\n");
				int chapterNumber = 0;
				for (Chapter ch : book.getChapters()) {
					chapterNumber++;
					if (ch.getProlog() != null) {
						tc = ++tagCounter;
						writeTag(w, tc, "prolog", "pref", book.getAbbr() + ":" + chapterNumber);
						ch.getProlog().accept(new TaggedTextVisitor(w));
						w.write("</prolog~" + tc + ">\n");
					}
					for (Verse v : ch.getVerses()) {
						tc = ++tagCounter;
						writeTag(w, tc, "verse", "vref", book.getAbbr() + ":" + chapterNumber + ":" + v.getNumber());
						v.accept(new TaggedTextVisitor(w));
						w.write("</verse~" + tc + ">\n");
					}
				}
			}
		}
	}

	private int tagCounter = 1;

	private void writeTag(Writer w, int tc, String tagName, String... attrValuePairs) throws IOException {
		w.write("<" + tagName + "~" + tc + ">");
		if (attrValuePairs.length % 2 != 0)
			throw new IllegalStateException();
		for (int i = 0; i < attrValuePairs.length; i += 2) {
			w.write("<" + attrValuePairs[i] + "~" + tc + ">" + escape(attrValuePairs[i + 1]) + "</" + attrValuePairs[i] + "~" + tc + ">");
		}
	}

	private String escape(String text) {
		if (!text.contains("<") && !text.contains(">") && !text.contains("~"))
			return text;
		StringBuffer result = new StringBuffer();
		Matcher m = Pattern.compile("[<>~]").matcher(text);
		while (m.find()) {
			String replacement;
			switch (m.group()) {
			case "<":
				replacement = "lt";
				break;
			case ">":
				replacement = "gt";
				break;
			case "~":
				replacement = "tilde";
				break;
			default:
				throw new IllegalStateException(m.group());
			}
			int tc = ++tagCounter;
			m.appendReplacement(result, "<" + replacement + "~" + tc + "></" + replacement + "~" + tc + ">");
		}
		m.appendTail(result);
		return result.toString();
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
			String line = br.readLine();
			if (!line.startsWith("<bible~"))
				throw new IOException("Invalid header line: " + line);
			Bible result = new Bible(unescape(parseTags(line, "bible")[0]));
			Map<String, Book> bookMap = new HashMap<String, Book>();
			while ((line = br.readLine()) != null) {
				line = line.trim();
				try {
					String[] refParts;
					if (line.startsWith("<bookinfo~")) {
						String[] attrs = parseTags(parseTags(line, "bookinfo")[0], "bookabbr", "bookid", "bookshortname", "booklongname");
						String babbr = unescape(attrs[0]), bid = unescape(attrs[1]);
						BookID id = BookID.fromOsisId(bid);
						if (id == null)
							throw new IOException("Unknown book ID: " + bid);
						if (bookMap.containsKey(babbr))
							throw new IOException("Duplicate book abbreviation: " + babbr);
						Book newBook = new Book(babbr, id, unescape(attrs[2]), unescape(attrs[3]));
						result.getBooks().add(newBook);
						bookMap.put(babbr, newBook);
						continue;
					} else if (line.startsWith("<prolog~")) {
						String[] values = parseTags(parseTags(line, "prolog")[0], "pref", "");
						refParts = unescape(values[0]).split(":");
						line = values[1] == null ? "" : values[1];
						if (refParts.length != 2)
							throw new RuntimeException("Unsupported prolog reference: " + values[0]);
					} else if (line.startsWith("<verse~")) {
						String[] values = parseTags(parseTags(line, "verse")[0], "vref", "");
						refParts = unescape(values[0]).split(":");
						line = values[1] == null ? "" : values[1];
						if (refParts.length != 3)
							throw new RuntimeException("Unsupported verse reference: " + values[0]);
					} else {
						throw new IOException("Unsupported line: " + line);
					}
					Book book = bookMap.get(refParts[0]);
					if (book == null)
						throw new IOException("Unknown book prefix (header line missing?): " + refParts[0]);
					int chapterNumber = Integer.parseInt(refParts[1]);
					while (book.getChapters().size() < chapterNumber) {
						book.getChapters().add(new Chapter());
					}
					Chapter chapter = book.getChapters().get(chapterNumber - 1);
					if (refParts.length == 2) {
						if (chapter.getProlog() != null)
							throw new RuntimeException("Two prologs for " + refParts[0] + " " + refParts[1]);
						FormattedText p = new FormattedText();
						chapter.setProlog(p);
						parseText(line, p.getAppendVisitor());
						p.finished();
					} else {
						Verse v = new Verse(refParts[2]);
						chapter.getVerses().add(v);
						parseText(line, v.getAppendVisitor());
						v.finished();
					}

				} catch (Exception ex) {
					throw new IOException("Error while parsing line: " + line, ex);
				}
			}
			return result;
		}
	}

	private static void parseText(String line, Visitor<RuntimeException> vv) throws IOException {
		if (line == null)
			return;
		int lastPos = 0, pos = line.indexOf('<');
		while (pos != -1) {
			if (pos > lastPos) {
				vv.visitText(unescape(line.substring(lastPos, pos)));
			}
			int endPos = line.indexOf('~', pos);
			if (endPos == -1)
				throw new IOException("Incomplete tag: " + line.substring(pos));
			String tagName = line.substring(pos + 1, endPos);
			endPos = line.indexOf('>', endPos);
			if (endPos == -1)
				throw new IOException("Incomplete tag: " + line.substring(pos));
			int endTagPos = line.indexOf("</" + line.substring(pos + 1, endPos + 1), endPos);
			if (endTagPos == -1)
				throw new IOException("Unclosed tag: " + line.substring(pos, endPos + 1));
			String tagContent = line.substring(endPos + 1, endTagPos);
			lastPos = endTagPos + endPos - pos + 2;
			if (tagName.length() == 1 && tagName.charAt(0) >= 'a' && tagName.charAt(0) <= 'z') {
				parseText(tagContent, vv.visitFormattingInstruction(FormattingInstructionKind.fromChar(tagName.charAt(0))));
			} else if (tagName.length() == 2 && tagName.startsWith("h") && tagName.charAt(1) >= '1' && tagName.charAt(1) <= '9') {
				parseText(tagContent, vv.visitHeadline(tagName.charAt(1) - '0'));
			} else {
				String[] parsedContent;
				List<List<String>> parsedMultiContent;
				switch (tagName) {
				case "lt":
				case "gt":
				case "tilde":
					vv.visitText(unescape(line.substring(pos, lastPos)));
					break;
				case "rawhtml":
					parsedContent = parseTags(tagContent, "mode", "");
					vv.visitRawHTML(RawHTMLMode.valueOf(unescape(parsedContent[0])), unescape(parsedContent[1]));
					break;
				case "fn":
					parseText(tagContent, vv.visitFootnote());
					break;
				case "css":
					parsedContent = parseTags(tagContent, "style", "");
					parseText(parsedContent[1], vv.visitCSSFormatting(unescape(parsedContent[0])));
					break;
				case "vs":
					if (!tagContent.equals("/"))
						System.out.println("WARNING: Unexpected verse separator content: " + tagContent);
					vv.visitVerseSeparator();
					break;
				case "br":
					parsedContent = parseTags(tagContent, "kind");
					vv.visitLineBreak(LineBreakKind.valueOf(unescape(parsedContent[0])));
					break;
				case "grammar":
					parsedMultiContent = parseMultiTags(tagContent, "strong", "rmac", "idx", "");
					List<String> ss = parsedMultiContent.get(0);
					char[] strongPfx = ss.isEmpty() ? null : new char[ss.size()];
					int[] strong = ss.isEmpty() ? null : new int[ss.size()];
					for (int i = 0; i < ss.size(); i++) {
						if (ss.get(i).matches("[A-Z][0-9]+")) {
							strongPfx[i] = ss.get(i).charAt(0);
							strong[i] = Integer.parseInt(ss.get(i).substring(1));
						} else {
							strongPfx = null;
							strong[i] = Integer.parseInt(ss.get(i));
						}
					}
					String[] rmac = parsedMultiContent.get(1).isEmpty() ? null : parsedMultiContent.get(1).toArray(new String[0]);
					int[] idx = parsedMultiContent.get(2).isEmpty() ? null : parsedMultiContent.get(2).stream().mapToInt(Integer::parseInt).toArray();
					parseText(parsedMultiContent.get(3).isEmpty() ? null : parsedMultiContent.get(3).get(0),
							vv.visitGrammarInformation(strongPfx, strong, rmac, idx));
					break;
				case "dictentry":
					parsedContent = parseTags(tagContent, "dictionaryname", "entry", "");
					parseText(parsedContent[2], vv.visitDictionaryEntry(unescape(parsedContent[0]), unescape(parsedContent[1])));
					break;
				case "variation":
					parsedMultiContent = parseMultiTags(tagContent, "variationname", "");
					parseText(parsedMultiContent.get(1).isEmpty() ? null : parsedMultiContent.get(1).get(0),
							vv.visitVariationText(parsedMultiContent.get(0).toArray(new String[0])));
					break;
				case "extra":
					parsedContent = parseTags(tagContent, "prio", "category", "key", "value", "");
					parseText(parsedContent[4], vv.visitExtraAttribute(ExtraAttributePriority.valueOf(unescape(parsedContent[0])), unescape(parsedContent[1]), unescape(parsedContent[2]), unescape(parsedContent[3])));
					break;
				case "xref":
					parsedContent = parseTags(tagContent, "abbr", "id", "fch", "lch", "fv", "lv", "");
					parseText(parsedContent[6], vv.visitCrossReference(unescape(parsedContent[0]), BookID.fromOsisId(unescape(parsedContent[1])), Integer.parseInt(unescape(parsedContent[2])), unescape(parsedContent[4]), Integer.parseInt(unescape(parsedContent[3])), unescape(parsedContent[5])));
					break;
				default:
					throw new IOException("Unsupported tag: " + tagName);
				}
			}
			pos = line.indexOf('<', lastPos);
		}
		if (lastPos < line.length())
			vv.visitText(unescape(line.substring(lastPos)));
	}

	private static String[] parseTags(String line, String... tagNames) {
		List<List<String>> multiResult = parseMultiTags(line, tagNames);
		String[] result = new String[multiResult.size()];
		for (int i = 0; i < result.length; i++) {
			if (multiResult.get(i).size() == 1)
				result[i] = multiResult.get(i).get(0);
			else if (multiResult.get(i).size() > 1)
				throw new RuntimeException("More than one " + tagNames[i] + " tag nested: " + line);
			else if (!tagNames[i].isEmpty())
				throw new RuntimeException("Missing " + tagNames[i] + " tag: " + line);
		}
		return result;
	}

	private static List<List<String>> parseMultiTags(String line, String... tagNames) {
		List<List<String>> result = new ArrayList<>();
		for (int i = 0; i < tagNames.length; i++) {
			result.add(new ArrayList<>());
		}
		outer: while (!line.isEmpty()) {
			for (int i = 0; i < tagNames.length; i++) {
				if (tagNames[i].isEmpty()) {
					result.get(i).add(line);
					break outer;
				} else if (line.startsWith("<" + tagNames[i] + "~")) {
					int pos = line.indexOf('>');
					if (pos == -1)
						throw new RuntimeException("Incomplete tag: " + line);
					String counter = line.substring(tagNames[i].length() + 2, pos);
					line = line.substring(pos + 1);
					pos = line.indexOf("</" + tagNames[i] + "~" + counter + ">");
					result.get(i).add(line.substring(0, pos));
					line = line.substring(pos + tagNames[i].length() + counter.length() + 4);
					continue outer;
				}
			}
			throw new RuntimeException("Unexpected tags: " + line);
		}
		return result;
	}

	private static String unescape(String text) {
		if (!text.contains("<"))
			return text;
		if (text.contains("\1") || text.contains("\2"))
			throw new RuntimeException("Control characters in " + text);
		String result = text.replaceAll("<(lt~[^>]+)></\\1>", "\1").replaceAll("<(gt~[^>]+)></\\1>", "\2")
				.replaceAll("<(tilde~[0-9]+)></\\1>", "~");
		if (result.contains("<") || result.contains(">"))
			throw new RuntimeException("Unsupported tag in " + text);
		return result.replace('\1', '<').replace('\2', '>');
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return true;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private class TaggedTextVisitor implements Visitor<IOException> {
		private final Writer w;
		private List<String> suffixStack = new ArrayList<>();

		private TaggedTextVisitor(Writer w) throws IOException {
			this.w = w;
			suffixStack.add("");
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			return 0;
		}

		private void addTag(String tagName, String... attrValuePairs) throws IOException {
			int tc = ++tagCounter;
			writeTag(w, tc, tagName, attrValuePairs);
			suffixStack.add("</" + tagName + "~" + tc + ">");
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			addTag("h" + depth);
			return this;
		}

		@Override
		public void visitStart() throws IOException {
		}

		@Override
		public void visitText(String text) throws IOException {
			w.write(escape(text));
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			addTag("fn");
			return this;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			addTag("xref", "abbr", bookAbbr, "id", book.getOsisID(), "fch", "" + firstChapter, "lch", "" + lastChapter, "fv", firstVerse, "lv", lastVerse);
			return this;
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			addTag("" + kind.getCode());
			return this;
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			addTag("css", "style", css);
			return this;
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			addTag("vs");
			w.write("/");
			visitEnd();
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws IOException {
			addTag("br", "kind", kind.name());
			visitEnd();
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			String[] attrValuePairs = new String[(strongs == null ? 0 : strongs.length * 2) + (rmac == null ? 0 : rmac.length * 2) + (sourceIndices == null ? 0 : sourceIndices.length * 2)];
			int idx = 0;

			if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					attrValuePairs[idx++] = "strong";
					attrValuePairs[idx++] = (strongsPrefixes == null ? "" : "" + strongsPrefixes[i]) + strongs[i];
				}
			}
			if (rmac != null) {
				for (int i = 0; i < rmac.length; i++) {
					attrValuePairs[idx++] = "rmac";
					attrValuePairs[idx++] = rmac[i];
				}
			}
			if (sourceIndices != null) {
				for (int i = 0; i < sourceIndices.length; i++) {
					attrValuePairs[idx++] = "idx";
					attrValuePairs[idx++] = "" + sourceIndices[i];
				}
			}
			if (idx != attrValuePairs.length)
				throw new IllegalStateException();
			addTag("grammar", attrValuePairs);
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			addTag("dictentry", "dictionaryname", dictionary, "entry", entry);
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
			addTag("rawhtml", "mode", mode.name());
			visitText(raw);
			visitEnd();
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			String[] attrValuePairs = new String[variations.length * 2];
			for (int i = 0; i < variations.length; i++) {
				attrValuePairs[i * 2] = "variationname";
				attrValuePairs[i * 2 + 1] = variations[i];
			}
			addTag("variation", attrValuePairs);
			return this;
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			addTag("extra", "prio", prio.name(), "category", category, "key", key, "value", value);
			return this;
		}

		@Override
		public boolean visitEnd() throws IOException {
			w.write(suffixStack.remove(suffixStack.size() - 1));
			return false;
		}
	}
}
