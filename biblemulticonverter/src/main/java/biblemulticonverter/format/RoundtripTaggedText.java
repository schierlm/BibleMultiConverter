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
import java.util.Arrays;
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
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.HyperlinkType;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;

public class RoundtripTaggedText implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"A text-format that consistently uses numbered tags to make automated editing easy.",
			"",
			"Usage (export): RoundtripTaggedText <OutputFile>",
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
					boolean ofCrossReferences = false;
					if (Diffable.parseXrefMarkers && tagContent.startsWith(FormattedText.XREF_MARKER)) {
						tagContent = tagContent.substring(FormattedText.XREF_MARKER.length());
						ofCrossReferences = true;
					}
					parseText(tagContent, vv.visitFootnote(ofCrossReferences));
					break;
				case "fx":
					parseText(tagContent, vv.visitFootnote(true));
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
					parsedMultiContent = parseMultiTags(tagContent, "kind", "indent");
					if (parsedMultiContent.get(0).size() > 1)
						throw new RuntimeException("More than one kind tag nested: " + tagContent);
					if (parsedMultiContent.get(1).size() > 1)
						throw new RuntimeException("More than one indent tag nested: " + tagContent);
					if (parsedMultiContent.get(0).isEmpty())
						throw new RuntimeException("Missing kind tag: " + tagContent);
					String kind = unescape(parsedMultiContent.get(0).get(0));
					if (kind.equals(LineBreakKind.NEWLINE_WITH_INDENT.name())) {
						vv.visitLineBreak(ExtendedLineBreakKind.NEWLINE, 1);
					} else {
						int indent = parsedMultiContent.get(1).isEmpty() ? 0 : Integer.parseInt(parsedMultiContent.get(1).get(0));
						vv.visitLineBreak(ExtendedLineBreakKind.valueOf(kind), indent);
					}
					break;
				case "grammar":
					parsedMultiContent = parseMultiTags(tagContent, "strong", "rmac", "idx", "attrkey", "attrvalue", "strongsemptysuffixes", "");
					List<String> ss = parsedMultiContent.get(0);
					char[] strongPfx = ss.isEmpty() ? null : new char[ss.size()];
					char[] strongSfx = null;
					int[] strong = ss.isEmpty() ? null : new int[ss.size()];
					for (int i = 0; i < ss.size(); i++) {
						if (Diffable.parseStrongsSuffix) {
							char[] prefixSuffixHolder = new char[2];
							strong[i] = Utils.parseStrongs(ss.get(i), '?', prefixSuffixHolder);
							if (prefixSuffixHolder[0] != '?') {
								strongPfx[i] = prefixSuffixHolder[0];
							} else {
								strongPfx = null;
							}
							if (prefixSuffixHolder[1] != ' ') {
								if (strongSfx == null) {
									strongSfx = new char[ss.size()];
									Arrays.fill(strongSfx, ' ');
								}
								strongSfx[i] = prefixSuffixHolder[1];
							}
						} else {
							String s= ss.get(i);
							if (s.matches("[A-Z]?[0-9]+[A-Za-z]")) {
								if (strongSfx == null) {
									strongSfx = new char[ss.size()];
									Arrays.fill(strongSfx, ' ');
								}
								strongSfx[i] = s.charAt(s.length() - 1);
								s = s.substring(0, s.length() - 1);
							}
							if (s.matches("[A-Z][0-9]+")) {
								strongPfx[i] = s.charAt(0);
								strong[i] = Integer.parseInt(s.substring(1));
							} else {
								strongPfx = null;
								strong[i] = Integer.parseInt(s);
							}
						}
					}
					String[] rmac = parsedMultiContent.get(1).isEmpty() ? null : parsedMultiContent.get(1).toArray(new String[0]);
					int[] idx = parsedMultiContent.get(2).isEmpty() ? null : parsedMultiContent.get(2).stream().mapToInt(Integer::parseInt).toArray();
					String[] attrKey = null, attrVal = null;
					List<String> attrkey = parsedMultiContent.get(3);
					List<String> attrval = parsedMultiContent.get(4);
					if (attrkey.size() != attrval.size()) {
						throw new RuntimeException("Unable to assign "+attrval.size()+" values to "+attrkey.size()+" keys.");
					}
					if (!attrkey.isEmpty()) {
						attrKey = new String[attrkey.size()];
						attrVal = new String[attrkey.size()];
						for (int i = 0; i < attrKey.length; i++) {
							attrKey[i] = unescape(attrkey.get(i));
							attrVal[i] = unescape(attrval.get(i));
						}
					}
					if (!parsedMultiContent.get(5).isEmpty() && strongSfx == null) {
						strongSfx = new char[ss.size()];
						Arrays.fill(strongSfx, ' ');
					}
					parseText(parsedMultiContent.get(6).isEmpty() ? null : parsedMultiContent.get(6).get(0),
							vv.visitGrammarInformation(strongPfx, strong, strongSfx, rmac, idx, attrKey, attrVal));
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
				case "speaker":
					parsedContent = parseTags(tagContent, "who", "");
					parseText(parsedContent[1], vv.visitSpeaker(unescape(parsedContent[0])));
					break;
				case "hyperlink":
					parsedContent = parseTags(tagContent, "type", "target", "");
					parseText(parsedContent[2], vv.visitHyperlink(HyperlinkType.valueOf(unescape(parsedContent[0])), unescape(parsedContent[1])));
					break;
				case "extra":
					parsedContent = parseTags(tagContent, "prio", "category", "key", "value", "");
					parseText(parsedContent[4], vv.visitExtraAttribute(ExtraAttributePriority.valueOf(unescape(parsedContent[0])), unescape(parsedContent[1]), unescape(parsedContent[2]), unescape(parsedContent[3])));
					break;
				case "xref":
					if(parseMultiTags(tagContent, "abbr", "id", "labbr", "lid", "").get(2).isEmpty()) {
						parsedContent = parseTags(tagContent, "abbr", "id", "fch", "lch", "fv", "lv", "");
						parseText(parsedContent[6], vv.visitCrossReference(unescape(parsedContent[0]), BookID.fromOsisId(unescape(parsedContent[1])), Integer.parseInt(unescape(parsedContent[2])), unescape(parsedContent[4]), unescape(parsedContent[0]), BookID.fromOsisId(unescape(parsedContent[1])), Integer.parseInt(unescape(parsedContent[3])), unescape(parsedContent[5])));
					} else {
						parsedContent = parseTags(tagContent, "abbr", "id", "fch", "lch", "fv", "lv", "labbr", "lid", "");
						parseText(parsedContent[8], vv.visitCrossReference(unescape(parsedContent[0]), BookID.fromOsisId(unescape(parsedContent[1])), Integer.parseInt(unescape(parsedContent[2])), unescape(parsedContent[4]), unescape(parsedContent[6]), BookID.fromOsisId(unescape(parsedContent[7])), Integer.parseInt(unescape(parsedContent[3])), unescape(parsedContent[5])));
					}
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
		public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
			addTag(ofCrossReferences ? "fx" : "fn");
			return this;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws IOException {
			if (firstBook == lastBook) {
				addTag("xref", "abbr", firstBookAbbr, "id", firstBook.getOsisID(), "fch", "" + firstChapter, "lch", "" + lastChapter, "fv", firstVerse, "lv", lastVerse);
			} else {
				addTag("xref", "abbr", firstBookAbbr, "id", firstBook.getOsisID(), "labbr", lastBookAbbr, "lid", lastBook.getOsisID(), "fch", "" + firstChapter, "lch", "" + lastChapter, "fv", firstVerse, "lv", lastVerse);
			}
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
		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws IOException {
			if (kind == ExtendedLineBreakKind.NEWLINE && indent == 1) {
				addTag("br", "kind", LineBreakKind.NEWLINE_WITH_INDENT.name());
			} else if (indent == 0) {
				addTag("br", "kind", kind.name());
			} else {
				addTag("br", "kind", kind.name(), "indent", "" + indent);
			}
			visitEnd();
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws IOException {
			String[] attrValuePairs = new String[(strongs == null ? 0 : strongs.length * 2) + (rmac == null ? 0 : rmac.length * 2) + (sourceIndices == null ? 0 : sourceIndices.length * 2) + (attributeKeys == null ? 0 : attributeKeys.length * 4)];
			int idx = 0;

			if (strongs != null) {
				boolean hasSuffix = false;
				for (int i = 0; i < strongs.length; i++) {
					attrValuePairs[idx++] = "strong";
					if (Diffable.writeStrongsSuffix && strongsPrefixes != null && strongs != null) {
						attrValuePairs[idx++] = Utils.formatStrongs(false, i, strongsPrefixes, strongs, strongsSuffixes, "");
					} else {
						attrValuePairs[idx++] = (strongsPrefixes == null ? "" : "" + strongsPrefixes[i]) + strongs[i] + (strongsSuffixes == null || strongsSuffixes[i] == ' ' ? "" : "" + strongsSuffixes[i]);
					}
					if (strongsSuffixes != null && strongsSuffixes[i] != ' ')
						hasSuffix = true;
				}
				if (strongsSuffixes != null && !hasSuffix) {
					attrValuePairs = Arrays.copyOf(attrValuePairs, attrValuePairs.length+2);
					attrValuePairs[idx++] = "strongsemptysuffixes";
					attrValuePairs[idx++] = "";
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
			if (attributeKeys != null) {
				for (int i = 0; i < attributeKeys.length; i++) {
					attrValuePairs[idx++] = "attrkey";
					attrValuePairs[idx++] = attributeKeys[i];
					attrValuePairs[idx++] = "attrvalue";
					attrValuePairs[idx++] = attributeValues[i];
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
		public Visitor<IOException> visitSpeaker(String labelOrStrongs) throws IOException {
			addTag("speaker", "who", labelOrStrongs);
			return this;
		}

		@Override
		public Visitor<IOException> visitHyperlink(HyperlinkType type, String target) throws IOException {
			addTag("hyperlink", "type", type.name(), "target", target);
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
