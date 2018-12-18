package biblemulticonverter.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

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
import biblemulticonverter.format.Diffable.DiffableVisitor;

public class RoundtripODT implements RoundtripFormat {

	private static final String PARA_STYLE_BOOK = "BMC_5f_Book";
	private static final String PARA_STYLE_NEXT_CHAPTER = "BMC_5f_NextChapter";
	private static final String PARA_STYLE_HEADING_PREFIX = "BMC_5f_Heading_5f_";
	private static final String PARA_STYLE_CONTENT = "BMC_5f_Content";
	private static final String PARA_STYLE_FOOTNOTE = "Footnote";

	private static final String TEXT_STYLE_SPECIAL = "BMC_5f_Special";
	private static final String TEXT_STYLE_IGNORED = "BMC_5f_Ignored";
	private static final String TEXT_STYLE_GRAMMAR = "BMC_5f_Grammar";
	private static final String TEXT_STYLE_VERSE = "BMC_5f_Verse";
	private static final String TEXT_STYLE_CONTENT = "BMC_5f_Content";
	private static final String TEXT_STYLE_CONTENT_AND_BOLD = "BMC_5f_Content_2b_Bold";
	private static final String TEXT_STYLE_CONTENT_AND_ITALIC = "BMC_5f_Content_2b_Italic";
	private static final String TEXT_STYLE_CONTENT_AND_DIVINE_NAME = "BMC_5f_Content_2b_DivineName";
	private static final String TEXT_STYLE_CONTENT_AND_WOJ = "BMC_5f_Content_2b_WOJ";

	public static final String[] HELP_TEXT = {
			"ODT export and re-import",
			"",
			"Usage: RoundtripODT <outfile>.odt [contrast|plain|printable|<styles.xml>]",
			"",
			"Export into an OpenDocument Text file. All features are exported, but some might look",
			"strange in OpenOffice/LibreOffice. The file can be edited in LibreOffice 6 and",
			"the resulting file can be converted back, without any loss of features in between.",
			"",
			"When editing the file, keep in mind that inline formatting is not parsed; in case you",
			"want to change the formatting, you have to use the existing paragraph and text",
			"styles, whose names start with BMC_.",
			"",
			"To verify a Bible, use the 'contrast' style; the 'plain' style is more friendly for",
			"reading; the 'printable' style hides all meta-information for printing. Or use your",
			"custom styles.xml."
	};

	private static final String OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
	private static final String TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
	private static final String XLINK = "http://www.w3.org/1999/xlink";

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File exportFile = new File(exportArgs[0]);
		String styleName = exportArgs.length > 1 ? exportArgs[1] : "contrast";
		InputStream in = null;
		if (styleName.matches("[a-z]+")) {
			in = RoundtripODT.class.getResourceAsStream("/RoundtripODT/" + styleName + "_styles.xml");
		}
		if (in == null) {
			in = new FileInputStream(styleName);
		}
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(exportFile))) {
			ZipEntry mimetypeZE = new ZipEntry("mimetype");
			mimetypeZE.setSize(39);
			mimetypeZE.setCompressedSize(39);
			mimetypeZE.setCrc(204654174);
			mimetypeZE.setMethod(ZipOutputStream.STORED);
			zos.putNextEntry(mimetypeZE);
			zos.write("application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII));
			zos.putNextEntry(new ZipEntry("content.xml"));
			int paragraphCount = writeContentXml(bible, zos);
			zos.putNextEntry(new ZipEntry("styles.xml"));
			copyStream(in, zos);
			zos.putNextEntry(new ZipEntry("meta.xml"));
			try (InputStream metaIn = RoundtripODT.class.getResourceAsStream("/RoundtripODT/meta.xml")) {
				byte[] buf = new byte[1024];
				int len = metaIn.read(buf);
				int len2 = metaIn.read(buf, len, buf.length - len);
				if (len2 != -1)
					throw new IOException();
				String data = new String(buf, 0, len, StandardCharsets.US_ASCII).replace("#PARA#", "" + paragraphCount);
				zos.write(data.getBytes(StandardCharsets.US_ASCII));
			}
			zos.putNextEntry(new ZipEntry("META-INF/manifest.xml"));
			copyStream(RoundtripODT.class.getResourceAsStream("/RoundtripODT/manifest.xml"), zos);
		}
	}

	private int writeContentXml(Bible bible, OutputStream out) throws Exception {
		Map<String, Set<String>> xrefTargets = new HashMap<String, Set<String>>();
		XrefVisitor xrv = new XrefVisitor(xrefTargets);
		for (Book bk : bible.getBooks()) {
			for (Chapter ch : bk.getChapters()) {
				if (ch.getProlog() != null)
					ch.getProlog().accept(xrv);
				for (Verse v : ch.getVerses()) {
					v.accept(xrv);
				}
			}
		}
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		Document doc = dbf.newDocumentBuilder().newDocument();
		doc.appendChild(doc.createElementNS(OFFICE, "office:document-content"));
		doc.getDocumentElement().appendChild(doc.createElementNS(OFFICE, "office:body"));
		doc.getDocumentElement().setAttribute("xmlns:text", TEXT);
		doc.getDocumentElement().setAttribute("xmlns:xlink", XLINK);
		Element text = doc.createElementNS(OFFICE, "office:text");
		doc.getDocumentElement().getFirstChild().appendChild(text);
		RoundtripODTVisitor v = new RoundtripODTVisitor(text, false);
		Element p = doc.createElementNS(TEXT, "text:p");
		p.setAttributeNS(TEXT, "text:style-name", PARA_STYLE_NEXT_CHAPTER);
		text.appendChild(p);
		appendSpan(p, TEXT_STYLE_CONTENT, bible.getName());
		for (Book bb : bible.getBooks()) {
			p = doc.createElementNS(TEXT, "text:p");
			p.setAttributeNS(TEXT, "text:style-name", PARA_STYLE_BOOK);
			text.appendChild(p);
			appendBookmarkTag(p, "start", "BMC-" + bb.getAbbr().replace('.', '_'));
			appendSpan(p, TEXT_STYLE_VERSE, bb.getAbbr());
			appendBookmarkTag(p, "end", "BMC-" + bb.getAbbr().replace('.', '_'));
			appendSpan(p, TEXT_STYLE_GRAMMAR, bb.getId().getOsisID());
			appendSpan(p, TEXT_STYLE_IGNORED, " – ");
			if (!bb.getShortName().equals(bb.getLongName()))
				appendSpan(p, TEXT_STYLE_SPECIAL, bb.getShortName());
			appendSpan(p, TEXT_STYLE_CONTENT, bb.getLongName());
			int cnumber = 0;
			for (Chapter ch : bb.getChapters()) {
				cnumber++;
				if (cnumber != 1) {
					p = doc.createElementNS(TEXT, "text:p");
					p.setAttributeNS(TEXT, "text:style-name", PARA_STYLE_NEXT_CHAPTER);
					text.appendChild(p);
					appendSpan(p, TEXT_STYLE_IGNORED, "– " + cnumber + " –");
				}
				if (ch.getProlog() != null) {
					ch.getProlog().accept(v);
					v.reset();
				}
				for (Verse vv : ch.getVerses()) {
					p = v.makeParagraph();
					String bmk = bb.getAbbr().replace('.', '_') + "-" + cnumber + "-" + vv.getNumber();
					Set<String> targets = xrefTargets.get(bmk);
					if (targets == null) {
						targets = Collections.emptySet();
					}
					appendBookmarkTag(p, "start", "BMC-" + bmk);
					for (String target : targets) {
						appendBookmarkTag(p, "start", "BMC-" + target);
					}
					appendSpan(p, TEXT_STYLE_VERSE, vv.getNumber() + " ");
					appendBookmarkTag(p, "end", "BMC-" + bmk);
					for (String target : targets) {
						appendBookmarkTag(p, "end", "BMC-" + target);
					}
					vv.accept(v);
					v.reset();
				}
			}
		}
		int paragraphCount = 0;
		NodeList paraTags = doc.getElementsByTagNameNS(TEXT, "p");
		for (int i = 0; i < paraTags.getLength(); i++) {
			if (paraTags.item(i).getFirstChild() != null)
				paragraphCount++;
		}
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(out));
		return paragraphCount;
	}

	private static Element appendSpan(Element elem, String style, String value) {
		Element span = null;
		if (elem.getLastChild() != null && elem.getLastChild() instanceof Element) {
			Element last = (Element) elem.getLastChild();
			if (last.getLocalName().equals("span") && style.equals(last.getAttributeNS(TEXT, "style-name"))) {
				span = last;
			}
		}
		if (span == null) {
			span = elem.getOwnerDocument().createElementNS(TEXT, "text:span");
			span.setAttributeNS(TEXT, "text:style-name", style);
			elem.appendChild(span);
		}
		span.appendChild(elem.getOwnerDocument().createTextNode(value));
		return span;
	}

	private void appendBookmarkTag(Element elem, String type, String bookmark) {
		Element bmk = elem.getOwnerDocument().createElementNS(TEXT, "text:bookmark-" + type);
		bmk.setAttributeNS(TEXT, "text:name", bookmark);
		elem.appendChild(bmk);
	}

	private static Element appendLink(Element elem, String target) {
		Element link = elem.getOwnerDocument().createElementNS(TEXT, "text:a");
		link.setAttributeNS(XLINK, "xlink:type", "simple");
		link.setAttributeNS(XLINK, "xlink:href", target);
		elem.appendChild(link);
		return link;
	}

	private void copyStream(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[4096];
		int len;
		while ((len = in.read(buffer)) != -1) {
			out.write(buffer, 0, len);
		}
		in.close();
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(inputFile))) {
			ZipEntry ze;
			while ((ze = zis.getNextEntry()) != null) {
				if (ze.getName().equals("content.xml")) {
					DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
					dbf.setNamespaceAware(true);
					Document doc = dbf.newDocumentBuilder().parse(zis);
					return fromContentXml(doc);
				}
			}
			throw new IOException("Invalid input file - no content.xml found!");
		}
	}

	private Bible fromContentXml(Document doc) throws Exception {
		NodeList texts = doc.getElementsByTagNameNS(OFFICE, "text");
		if (texts.getLength() != 1)
			throw new RuntimeException("Invalid number of text elements");
		Element p = (Element) texts.item(0).getFirstChild();
		if (p.getLocalName().equals("sequence-decls"))
			p = (Element) p.getNextSibling();
		if (!getParagraphStyle(p).equals(PARA_STYLE_NEXT_CHAPTER))
			throw new IOException("Bible name not found!");
		Span[] bibleNameSpans = parseParagraph(p);
		if (bibleNameSpans.length != 1 || !bibleNameSpans[0].getStyleName().equals(PARA_STYLE_CONTENT))
			throw new IOException("Invalid bible name formatting");
		Bible bible = new Bible(bibleNameSpans[0].getContent());
		p = (Element) p.getNextSibling();
		while (p != null && getParagraphStyle(p).equals(PARA_STYLE_BOOK)) {
			Span[] bookSpans = parseParagraph(p);
			if (bookSpans.length < 3 || bookSpans.length > 4 || !bookSpans[0].getStyleName().equals(TEXT_STYLE_VERSE) || !bookSpans[1].getStyleName().equals(TEXT_STYLE_GRAMMAR))
				throw new IOException("Incorrectly formatted book header");

			String shortTitle = bookSpans[2].getContent(), longTitle;
			if (bookSpans.length == 4) {
				if (!bookSpans[2].getStyleName().equals(TEXT_STYLE_SPECIAL) || !bookSpans[3].getStyleName().equals(TEXT_STYLE_CONTENT))
					throw new IOException("Incorrectly formatted book header");
				longTitle = bookSpans[3].getContent();
			} else {
				if (!bookSpans[2].getStyleName().equals(TEXT_STYLE_CONTENT))
					throw new IOException("Incorrectly formatted book header");
				longTitle = shortTitle;
			}
			Book bk = new Book(bookSpans[0].getContent(), BookID.fromOsisId(bookSpans[1].getContent()), shortTitle, longTitle);
			bible.getBooks().add(bk);
			Chapter ch = new Chapter();
			bk.getChapters().add(ch);
			p = (Element) p.getNextSibling();
			ImportContext ic = new ImportContext(ch);
			boolean lastWasParagraph = false;
			while (p != null) {
				String pStyle = getParagraphStyle(p);
				if (pStyle.equals(PARA_STYLE_BOOK)) {
					break;
				} else if (pStyle.equals(PARA_STYLE_NEXT_CHAPTER)) {
					ic.finished();
					ch = new Chapter();
					bk.getChapters().add(ch);
					ic = new ImportContext(ch);
					lastWasParagraph = false;
				} else if (pStyle.startsWith(PARA_STYLE_HEADING_PREFIX)) {
					ic.pushVisitor(ic.getVisitor().visitHeadline(Integer.parseInt(pStyle.substring(PARA_STYLE_HEADING_PREFIX.length()))));
					Span[] spans = parseParagraph(p);
					for (int i = 0; i < spans.length; i++) {
						i = appendSpan(spans, i, ic);
					}
					ic.popVisitor();
					lastWasParagraph = false;
				} else if (pStyle.equals(PARA_STYLE_CONTENT)) {
					Span[] spans = parseParagraph(p);
					if (lastWasParagraph && !(spans.length != 0 && spans[0].getStyleName().equals(TEXT_STYLE_VERSE)))
						ic.getVisitor().visitLineBreak(LineBreakKind.PARAGRAPH);
					for (int i = 0; i < spans.length; i++) {
						if (spans[i].getStyleName().equals(TEXT_STYLE_VERSE)) {
							ic.finished();
							Verse verse = new Verse(spans[i].getContent().trim());
							ch.getVerses().add(verse);
							ic = new ImportContext(verse);
						} else {
							i = appendSpan(spans, i, ic);
						}
					}
					lastWasParagraph = true;
				} else {
					throw new IOException("Unexpected paragraph with style " + getParagraphStyle(p));
				}
				p = (Element) p.getNextSibling();
			}
			ic.finished();
		}
		if (p != null)
			throw new IOException("Unexpected paragraph with style " + getParagraphStyle(p));
		return bible;
	}

	private int appendSpan(Span[] spans, int index, ImportContext ic) throws IOException {
		Span span = spans[index];
		if (span.getStyleName().equals(TEXT_STYLE_SPECIAL)) {
			String content = span.getContent();
			int endPos = 0;
			while (endPos < content.length()) {
				if (content.startsWith("/", endPos)) {
					ic.popVisitor();
					endPos++;
				} else if (content.startsWith("<vs>", endPos)) {
					ic.getVisitor().visitVerseSeparator();
					endPos += 4;
					if (endPos != content.length())
						throw new RuntimeException("Verse separator content missing");
					if (!spans[index + 1].getStyleName().equals(TEXT_STYLE_CONTENT) || !spans[index + 1].getContent().equals("/"))
						throw new IOException("Invalid verse separator content");
					if (!spans[index + 2].getStyleName().equals(TEXT_STYLE_SPECIAL) || !spans[index + 2].getContent().startsWith("/"))
						throw new IOException("Invalid verse separator delimiter");
					spans[index + 2].setContent(spans[index + 2].getContent().substring(1));
					index++;
					if (spans[index + 1].getContent().isEmpty())
						index++;
					return index;
				} else if (content.startsWith("<", endPos)) {
					endPos = Diffable.parseSingleTag(content, endPos, ic.getVisitorStack());
				} else {
					throw new RuntimeException("Invalid special style value: " + content.substring(endPos));
				}
			}
		} else if (span.getStyleName().equals(TEXT_STYLE_GRAMMAR)) {
			String content = span.getContent();
			if (content.startsWith("\u0001")) {
				ic.popVisitor();
				content = content.substring(1);
			}
			if (content.equals("[")) {
				String endContent = null;
				for (int i = index + 1; i < spans.length; i++) {
					if (spans[i].getStyleName().equals(TEXT_STYLE_GRAMMAR)) {
						endContent = spans[i].getContent();
						if (!endContent.startsWith("]")) {
							throw new RuntimeException("Invalid grammar value or nesting: " + endContent);
						}
						if (endContent.endsWith("[")) {
							endContent = endContent.substring(0, endContent.length() - 1);
							spans[i].setContent("\u0001[");
						} else {
							spans[i].setContent("\u0001");
						}
						break;
					}
				}
				if (endContent == null)
					throw new IOException("Grammar tag not closed");
				List<Integer> strongs = new ArrayList<>(), sourceIndices = new ArrayList<>();
				List<String> rmac = new ArrayList<>();
				for (String part : endContent.substring(1).split("'")) {
					String[] subparts = part.split(":");
					if (subparts.length > 0 && !subparts[0].isEmpty()) {
						strongs.add(Integer.parseInt(subparts[0]));
					}
					if (subparts.length > 1 && !subparts[1].isEmpty()) {
						rmac.add(subparts[1]);
					}
					if (subparts.length > 2 && !subparts[2].isEmpty()) {
						sourceIndices.add(Integer.parseInt(subparts[2]));
					}
				}
				ic.pushVisitor(ic.getVisitor().visitGrammarInformation(strongs.isEmpty() ? null : strongs.stream().mapToInt(s -> s).toArray(), rmac.isEmpty() ? null : rmac.toArray(new String[rmac.size()]), sourceIndices.isEmpty() ? null : sourceIndices.stream().mapToInt(s -> s).toArray()));
			} else if (!content.isEmpty()) {
				throw new IOException("Invalid grammar value or nesting: " + content);
			}
		} else if (span.getStyleName().equals(TEXT_STYLE_CONTENT_AND_BOLD)) {
			ic.getVisitor().visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText(span.getContent());
		} else if (span.getStyleName().equals(TEXT_STYLE_CONTENT_AND_DIVINE_NAME)) {
			ic.getVisitor().visitFormattingInstruction(FormattingInstructionKind.DIVINE_NAME).visitText(span.getContent());
		} else if (span.getStyleName().equals(TEXT_STYLE_CONTENT_AND_ITALIC)) {
			ic.getVisitor().visitFormattingInstruction(FormattingInstructionKind.ITALIC).visitText(span.getContent());
		} else if (span.getStyleName().equals(TEXT_STYLE_CONTENT_AND_WOJ)) {
			ic.getVisitor().visitFormattingInstruction(FormattingInstructionKind.WORDS_OF_JESUS).visitText(span.getContent());
		} else if (span.getStyleName().equals(TEXT_STYLE_CONTENT) && span.getElementContent() == null) {
			ic.getVisitor().visitText(span.getContent());
		} else if (span.getStyleName().equals(":A") && span.getElementContent() != null && span.getElementContent().getLocalName().equals("a")) {
			String href = span.getElementContent().getAttributeNS(XLINK, "href");
			if (href.equals("#Link"))
				ic.pushVisitor(ic.getVisitor().visitFormattingInstruction(FormattingInstructionKind.LINK));
			else if (href.equals("#FootnoteLink"))
				ic.pushVisitor(ic.getVisitor().visitFormattingInstruction(FormattingInstructionKind.FOOTNOTE_LINK));
			else if (href.contains(".odt#BMC-")) {
				String[] parts = href.split("\\.odt#BMC-");
				ic.pushVisitor(ic.getVisitor().visitDictionaryEntry(parts[0], parts[1]));
			} else if (href.startsWith("#")) {
				String[] parts = href.substring(1).split("-");
				ic.pushVisitor(ic.getVisitor().visitCrossReference(parts[0].replace('_', '.'), BookID.fromOsisId(parts[1].replace('_', '-')), Integer.parseInt(parts[2]), parts[3], Integer.parseInt(parts[4]), parts[5]));
			} else {
				throw new IOException("Unsupported link: " + href);
			}
			Span[] subSpans = parseParagraph(span.getElementContent());
			for (int i = 0; i < subSpans.length; i++) {
				i = appendSpan(subSpans, i, ic);
			}
			ic.popVisitor();
		} else if (span.getStyleName().equals(TEXT_STYLE_CONTENT)) {
			if (!span.getElementContent().getNamespaceURI().equals(TEXT))
				throw new IOException("Unexpected tag: " + span.getElementContent().getNodeName());
			if (span.getElementContent().getLocalName().equals("line-break")) {
				if (index + 1 < spans.length && spans[index + 1].getElementContent() != null && spans[index + 1].getElementContent().getLocalName().equals("tab")) {
					index++;
					ic.getVisitor().visitLineBreak(LineBreakKind.NEWLINE_WITH_INDENT);
				} else {
					ic.getVisitor().visitLineBreak(LineBreakKind.NEWLINE);
				}
			} else if (span.getElementContent().getLocalName().equals("note")) {
				ic.pushVisitor(ic.getVisitor().visitFootnote());
				Span[] subSpans = parseParagraph((Element) span.getElementContent().getLastChild().getFirstChild());
				for (int i = 0; i < subSpans.length; i++) {
					i = appendSpan(subSpans, i, ic);
				}
				ic.popVisitor();
			} else {
				throw new IOException("Unexpected tag: " + span.getElementContent().getNodeName());
			}
		} else {
			throw new IOException("Unexpected text style: " + span.getStyleName());
		}
		return index;
	}

	private String getParagraphStyle(Element p) throws IOException {
		if (!p.getNamespaceURI().equals(TEXT) || !p.getLocalName().equals("p"))
			throw new IOException("Not a paragraph tag: " + p.getLocalName());
		return p.getAttributeNS(TEXT, "style-name");
	}

	private Span[] parseParagraph(Element p) throws IOException {
		List<Span> result = new ArrayList<>();
		for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling()) {
			parseParagraphNode(result, null, n);
		}
		return result.toArray(new Span[result.size()]);
	}

	private void parseParagraphNode(List<Span> result, String styleName, Node n) throws IOException {
		if (n instanceof Element) {
			Element e = (Element) n;
			if (e.getLocalName().equals("span")) {
				String innerStyleName = e.getAttributeNS(TEXT, "style-name");
				if (innerStyleName.startsWith("BMC_5f_")) {
					if (styleName != null)
						throw new IOException("Nested paragraph styles are not supported!");
				} else {
					innerStyleName = styleName;
				}
				for (Node nn = e.getFirstChild(); nn != null; nn = nn.getNextSibling()) {
					parseParagraphNode(result, innerStyleName, nn);
				}
			} else if (e.getLocalName().equals("a")) {
				if (styleName == null)
					result.add(new Span(":A", e, null));
			} else if (e.getLocalName().equals("note") || e.getLocalName().equals("line-break") || e.getLocalName().equals("tab")) {
				if (styleName != null && !styleName.equals(TEXT_STYLE_IGNORED))
					result.add(new Span(styleName, e, null));
			} else if (!e.getLocalName().equals("bookmark-start") && !e.getLocalName().equals("bookmark-end") && !e.getLocalName().equals("soft-page-break")) {
				System.out.println("WARNING: Skipping unsupported tag " + e.getNodeName());
			}
		} else if (n instanceof Text) {
			if (styleName != null && !styleName.equals(TEXT_STYLE_IGNORED)) {
				String content = n.getTextContent();
				Span last = result.isEmpty() ? null : result.get(result.size() - 1);
				if (last != null && last.getElementContent() == null && last.getStyleName().equals(styleName)) {
					last.setContent(last.getContent() + content);
				} else {
					result.add(new Span(styleName, null, content));
				}
			}
		} else {
			System.out.println("WARNING: Skipping unsupported XML content");
		}
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return true;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private static interface VisitorAction {
		public void doAction(Visitor<IOException> v) throws IOException;
	}

	private static class RoundtripODTVisitor implements FormattedText.Visitor<RuntimeException> {
		private final Element parent;
		private Element p;
		private String textStyle = TEXT_STYLE_CONTENT, paragraphStyle;
		private final List<String> suffixStack = new ArrayList<String>();
		private FormattedText.FormattingInstructionKind pendingInstruction = null;

		public RoundtripODTVisitor(Element parent, boolean isParagraph) {
			this(parent, isParagraph, false);
		}

		private RoundtripODTVisitor(Element parent, boolean isParagraph, boolean isFootnote) {
			this.parent = isParagraph ? null : parent;
			this.p = isParagraph ? parent : null;
			suffixStack.add(null);
			this.paragraphStyle = isFootnote ? PARA_STYLE_FOOTNOTE : PARA_STYLE_CONTENT;
		}

		public void reset() {
			if (suffixStack.size() != 0 || p != null || !paragraphStyle.equals(PARA_STYLE_CONTENT) || !textStyle.equals(TEXT_STYLE_CONTENT) || pendingInstruction != null)
				throw new IllegalStateException();
			suffixStack.add(null);
		}

		private void withDiffableVisitor(VisitorAction va, boolean leaveOpen) {
			try {
				StringWriter sw = new StringWriter();
				DiffableVisitor dv = new DiffableVisitor(sw);
				va.doAction(dv);
				appendSpan(makeParagraph(), TEXT_STYLE_SPECIAL, sw.toString());
				if (leaveOpen)
					suffixStack.add("/");
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			if (pendingInstruction == null)
				return 0;
			if (elementTypes == null)
				return 1;
			if (elementTypes.equals("t")) {
				switch (pendingInstruction) {
				case BOLD:
					textStyle = TEXT_STYLE_CONTENT_AND_BOLD;
					break;
				case ITALIC:
					textStyle = TEXT_STYLE_CONTENT_AND_ITALIC;
					break;
				case DIVINE_NAME:
					textStyle = TEXT_STYLE_CONTENT_AND_DIVINE_NAME;
					break;
				case WORDS_OF_JESUS:
					textStyle = TEXT_STYLE_CONTENT_AND_WOJ;
					break;
				default:
					throw new IllegalStateException(pendingInstruction.toString());
				}
				suffixStack.add("//");
			} else {
				withDiffableVisitor(v -> v.visitFormattingInstruction(pendingInstruction), true);
			}
			pendingInstruction = null;
			return 0;
		}

		private Element makeParagraph() {
			if (p == null) {
				p = parent.getOwnerDocument().createElementNS(TEXT, "text:p");
				p.setAttributeNS(TEXT, "text:style-name", paragraphStyle);
				parent.appendChild(p);
			}
			return p;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			p = null;
			paragraphStyle = PARA_STYLE_HEADING_PREFIX + depth;
			makeParagraph();
			suffixStack.add(null);
			return this;
		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			appendSpan(makeParagraph(), textStyle, text);
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
			makeParagraph();
			Element note = p.getOwnerDocument().createElementNS(TEXT, "text:note");
			appendSpan(p, textStyle, "").appendChild(note);
			note.setAttributeNS(TEXT, "text:note-class", "footnote");
			Element body = p.getOwnerDocument().createElementNS(TEXT, "text:note-body");
			note.appendChild(body);
			return new RoundtripODTVisitor(body, false, true);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			return new RoundtripODTVisitor(appendLink(makeParagraph(), "#" + bookAbbr.replace('.', '_') + "-" + book.getOsisID().replace('-', '_') + "-" + firstChapter + "-" + firstVerse + "-" + lastChapter + "-" + lastVerse), true);
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			makeParagraph();
			switch (kind) {
			case BOLD:
			case ITALIC:
			case DIVINE_NAME:
			case WORDS_OF_JESUS:
				pendingInstruction = kind;
				break;
			case FOOTNOTE_LINK:
				return new RoundtripODTVisitor(appendLink(p, "#FootnoteLink"), true);
			case LINK:
				return new RoundtripODTVisitor(appendLink(p, "#Link"), true);
			default:
				withDiffableVisitor(v -> v.visitFormattingInstruction(kind), true);
				break;
			}
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			withDiffableVisitor(v -> v.visitCSSFormatting(css), true);
			return this;
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			appendSpan(makeParagraph(), TEXT_STYLE_SPECIAL, "<vs>");
			visitText("/");
			appendSpan(makeParagraph(), TEXT_STYLE_SPECIAL, "/");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			makeParagraph();
			switch (kind) {
			case NEWLINE:
				appendSpan(p, textStyle, "").appendChild(p.getOwnerDocument().createElementNS(TEXT, "text:line-break"));
				break;
			case NEWLINE_WITH_INDENT:
				Element span = appendSpan(p, textStyle, "");
				span.appendChild(p.getOwnerDocument().createElementNS(TEXT, "text:line-break"));
				span.appendChild(p.getOwnerDocument().createElementNS(TEXT, "text:tab"));
				break;
			case PARAGRAPH:
				p = null;
				makeParagraph();
				break;
			default:
				throw new IllegalArgumentException("Unsupported line break kind");
			}
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			appendSpan(makeParagraph(), TEXT_STYLE_GRAMMAR, "[");
			StringBuilder suffixBuilder = new StringBuilder("]");
			int max = Math.max(Math.max(strongs == null ? 0 : strongs.length, rmac == null ? 0 : rmac.length), sourceIndices == null ? 0 : sourceIndices.length);
			for (int i = 0; i < max; i++) {
				if (strongs != null && i < strongs.length) {
					suffixBuilder.append(strongs[i]);
				}
				if ((rmac != null && i < rmac.length) || (sourceIndices != null && i < sourceIndices.length)) {
					suffixBuilder.append(":");
					if (rmac != null && i < rmac.length)
						suffixBuilder.append(rmac[i]);
					if (sourceIndices != null && i < sourceIndices.length)
						suffixBuilder.append(":" + sourceIndices[i]);
				}
				suffixBuilder.append('\'');
			}
			suffixStack.add(suffixBuilder.toString());
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			return new RoundtripODTVisitor(appendLink(makeParagraph(), dictionary + ".odt#BMC-" + entry), true);
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			withDiffableVisitor(v -> v.visitRawHTML(mode, raw), false);
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			withDiffableVisitor(v -> v.visitVariationText(variations), true);
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			withDiffableVisitor(v -> v.visitExtraAttribute(prio, category, key, value), true);
			return this;
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			String suffix = suffixStack.remove(suffixStack.size() - 1);
			if (suffix == null) {
				p = null;
				paragraphStyle = PARA_STYLE_CONTENT;
			} else if (suffix.equals("//")) {
				textStyle = TEXT_STYLE_CONTENT;
			} else if (suffix.equals("/")) {
				appendSpan(p, TEXT_STYLE_SPECIAL, "/");
			} else {
				appendSpan(p, TEXT_STYLE_GRAMMAR, suffix);
			}
			return false;
		}
	}

	private static class XrefVisitor extends FormattedText.VisitorAdapter<RuntimeException> {
		private final Map<String, Set<String>> xrefTargets;

		public XrefVisitor(Map<String, Set<String>> xrefTargets) {
			super(null);
			this.xrefTargets = xrefTargets;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			String prefix = bookAbbr.replace('.', '_') + "-" + firstChapter + "-" + firstVerse;
			String full = bookAbbr.replace('.', '_') + "-" + book.getOsisID().replace('-', '_') + "-" + firstChapter + "-" + firstVerse + "-" + lastChapter + "-" + lastVerse;
			Set<String> targets = xrefTargets.get(prefix);
			if (targets == null) {
				targets = new HashSet<String>();
				xrefTargets.put(prefix, targets);
			}
			targets.add(full);
			return this;
		}
	}

	private static class Span {
		private final String styleName;
		private final Element elementContent;
		private String content;

		private Span(String styleName, Element elementContent, String content) {
			if (!(elementContent == null ^ content == null))
				throw new IllegalArgumentException("Either elementContent or content need to be filled");
			this.styleName = styleName;
			this.elementContent = elementContent;
			this.content = content;
		}

		public String getStyleName() {
			return styleName;
		}

		public Element getElementContent() {
			return elementContent;
		}

		public String getContent() {
			if (elementContent != null)
				throw new RuntimeException("Text content expected, but found " + elementContent.getNodeName() + " element");
			return content;
		}

		public void setContent(String content) {
			if (this.content == null || content == null)
				throw new IllegalArgumentException("Changing presence of content not permitted");
			this.content = content;
		}
	}

	private static class ImportContext {

		private final List<Visitor<RuntimeException>> visitorStack = new ArrayList<>();
		private FormattedText target = null;
		private final Chapter chapter;

		private ImportContext(Chapter chapter) {
			this.chapter = chapter;
		}

		private ImportContext(Verse verse) {
			this.chapter = null;
			this.target = verse;
			visitorStack.add(verse.getAppendVisitor());
		}

		private Visitor<RuntimeException> getVisitor() {
			if (target == null) {
				target = new FormattedText();
				chapter.setProlog(target);
				visitorStack.add(target.getAppendVisitor());
			}
			return visitorStack.get(visitorStack.size() - 1);
		}

		private List<Visitor<RuntimeException>> getVisitorStack() {
			return visitorStack;
		}

		private void pushVisitor(Visitor<RuntimeException> v) {
			getVisitor();
			visitorStack.add(v);
		}

		private void popVisitor() {
			visitorStack.remove(visitorStack.size() - 1);
		}

		private void finished() {
			if (target == null)
				return;
			target.finished();
			if (visitorStack.size() != 1)
				throw new RuntimeException("Unclosed formatting detected");
			visitorStack.clear();
		}
	}
}
