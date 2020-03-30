package biblemulticonverter.format;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.schema.zefdic1.Dictionary;
import biblemulticonverter.schema.zefdic1.MyAnyType;
import biblemulticonverter.schema.zefdic1.ObjectFactory;
import biblemulticonverter.schema.zefdic1.RefLinkType;
import biblemulticonverter.schema.zefdic1.SeeType;
import biblemulticonverter.schema.zefdic1.TItem;
import biblemulticonverter.schema.zefdic1.TParagraph;
import biblemulticonverter.schema.zefdic1.TStyle;

/**
 * Zefania Dictionary exporter for MyBible
 */
public class ZefDicMyBible implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Zefania Dictionary exporter for MyBible.",
			"",
			"Usage: LogosHTML <outfile> [<idfields>]",
			"",
			"This version will export Zefania Dictionary modules optimized for use with MyBible.",
			"In <idfields> you can optionally give a comma separated list of 'abbr', 'short' and 'long',",
			"the default being 'long,short'. This specifies what kind of IDs (abbreviation, short",
			"title and long title) are used to create items. Item fields mentioned later in the list will",
			"create redirects if they have a different value.",
			"For some features, it is required to add the following snippet to the end of",
			"Templates\\dictionary.xsl (before the </xsl:stylesheet>):",
			"",
			"  <xsl:template match=\"/DICTIONARY/item//labeledlink\">",
			"    <a class=\"alwaysunvisitedLink\">",
			"      <xsl:attribute name=\"href\"><xsl:value-of select=\"link/@href\"/></xsl:attribute>",
			"      <xsl:apply-templates select=\"linklabel\"/>",
			"    </a>",
			"  </xsl:template>",
			"  <xsl:template match=\"/DICTIONARY/item//raw\">",
			"	<xsl:value-of select=\".\" disable-output-escaping=\"yes\"/>",
			"  </xsl:template>"
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File file = new File(exportArgs[0]);
		String[] idfields = (exportArgs.length > 1 ? exportArgs[1] : "long,short").split(",");
		Dictionary xmlbible = createXMLBible(bible, idfields);
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class);
		Marshaller m = ctx.createMarshaller();
		m.marshal(xmlbible, doc);
		doc.normalize();
		maskWhitespaceNodes(doc.getDocumentElement());
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		transformer.transform(new DOMSource(doc), new StreamResult(file));
	}

	private static void maskWhitespaceNodes(Element elem) {
		for (int i = 0; i < elem.getChildNodes().getLength(); i++) {
			Node n = elem.getChildNodes().item(i);
			if (n instanceof Text && n.getNodeValue().length() > 0 && n.getNodeValue().trim().length() == 0) {
				n.setNodeValue(n.getNodeValue() + "\uFEFF");
			} else if (n instanceof Element) {
				maskWhitespaceNodes((Element) n);
			}
		}
	}

	protected Dictionary createXMLBible(Bible bible, String[] idfields) throws Exception {
		final ObjectFactory of = new ObjectFactory();
		Dictionary doc = of.createDictionary();
		doc.setRevision("1");
		doc.setINFORMATION(of.createTINFORMATION());
		doc.getINFORMATION().getTitleOrCreatorOrDescription().add(of.createTINFORMATIONTitle(bible.getName()));

		Map<String, String> xrefMap = new HashMap<String, String>();
		if (!idfields[0].equals("abbr")) {
			for (Book bk : bible.getBooks()) {
				String value;
				switch (idfields[0].toLowerCase()) {
				case "abbr":
					value = bk.getAbbr();
					break;
				case "short":
					value = bk.getShortName();
					break;
				case "long":
					value = bk.getLongName();
					break;
				default:
					value = null;
				}
				if (value != null && !value.equals(bk.getAbbr())) {
					xrefMap.put(bk.getAbbr(), value);
				}
			}
		}

		for (Book bk : bible.getBooks()) {
			if (bk.getId().equals(BookID.METADATA))
				continue;
			if (!bk.getId().equals(BookID.DICTIONARY_ENTRY)) {
				System.out.println("WARNING: Unable to export book " + bk.getAbbr());
				continue;
			}

			List<String> ids = new ArrayList<>();
			for (String idfield : idfields) {
				String value;
				switch (idfield.toLowerCase()) {
				case "abbr":
					value = bk.getAbbr();
					break;
				case "short":
					value = bk.getShortName();
					break;
				case "long":
					value = bk.getLongName();
					break;
				default:
					continue;
				}
				if (!ids.contains(value))
					ids.add(value);
			}

			if (ids.isEmpty()) {
				System.out.println("WARNING: Skipping book " + bk.getAbbr() + "due to missing ID.");
				continue;
			}

			for (int i = 1; i < ids.size(); i++) {
				TItem item = of.createTItem();
				item.setId(ids.get(i));
				TParagraph para = of.createTParagraph();
				SeeType see = of.createSeeType();
				see.setContent(ids.get(0));
				para.getContent().add(of.createSee(see));
				item.getContent().add(of.createTParagraphDescription(para));
				doc.getItem().add(item);
			}

			final TItem item = of.createTItem();
			item.setId(ids.get(0));
			doc.getItem().add(item);
			TParagraph description = of.createTParagraph();
			item.getContent().add(of.createTParagraphDescription(description));
			bk.getChapters().get(0).getProlog().accept(new ZefDicVisitor(description.getContent(), xrefMap));
		}
		return doc;
	}

	private static class ZefDicVisitor implements Visitor<RuntimeException> {

		private static ObjectFactory of = new ObjectFactory();

		private final List<Serializable> target;

		private final Map<String, String> xrefMap;

		public ZefDicVisitor(List<Serializable> target, Map<String, String> xrefMap) {
			this.target = target;
			this.xrefMap = xrefMap;
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			String tagName = "h" + (depth + 2 < 6 ? depth + 2 : 6);
			addRaw("<" + tagName + ">");
			MyAnyType holder = of.createMyAnyType();
			target.add(of.createTParagraphQ(holder));
			addRaw("</" + tagName + ">");
			return new ZefDicVisitor(holder.getContent(), xrefMap);
		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			if (text.length() > 0)
				target.add(text);
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
			System.out.println("WARNING: footnotes are not supported");
			return null;
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			if (firstChapter != lastChapter) {
				Visitor<RuntimeException> firstVisitor = visitCrossReference(bookAbbr, book, firstChapter, firstVerse, firstChapter, "999");
				visitText(" ");
				for (int i = firstChapter + 1; i < lastChapter; i++) {
					visitCrossReference(bookAbbr, book, i, "1", i, "999").visitText("[" + i + "]");
					visitText(" ");
				}
				visitCrossReference(bookAbbr, book, lastChapter, "1", lastChapter, lastVerse).visitText("[" + lastChapter + "]");
				return firstVisitor;
			}
			MyAnyType link = of.createMyAnyType();
			MyAnyType label = of.createMyAnyType();
			target.add(new JAXBElement<MyAnyType>(new QName("labeledlink"), MyAnyType.class, link));
			RefLinkType rl = of.createRefLinkType();
			rl.setMscope(book.getZefID() + ";" + firstChapter + ";" + firstVerse + (firstVerse.equals(lastVerse) ? "" : "-" + lastVerse));
			link.getContent().add(of.createTItemReflink(rl));
			link.getContent().add(new JAXBElement<MyAnyType>(new QName("linklabel"), MyAnyType.class, label));
			return new ZefDicVisitor(label.getContent(), xrefMap);
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			return visitCSSFormatting(kind.getCss());
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			TStyle style = of.createTStyle();
			style.setCss(css);
			target.add(new JAXBElement<TStyle>(new QName("style"), TStyle.class, style));
			return new ZefDicVisitor(style.getContent(), xrefMap);
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			System.out.println("WARNING: Verse separators are not supported");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			target.add(of.createTParagraphBr(""));
			if (kind == LineBreakKind.PARAGRAPH)
				target.add(of.createTParagraphBr(""));
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			System.out.println("WARNING: Grammar information is not supported");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			if (xrefMap.containsKey(entry))
				entry = xrefMap.get(entry);
			MyAnyType link = of.createMyAnyType();
			MyAnyType label = of.createMyAnyType();
			target.add(new JAXBElement<MyAnyType>(new QName("labeledlink"), MyAnyType.class, link));
			SeeType see = of.createSeeType();
			see.setTarget("x-self");
			see.setContent(entry);
			link.getContent().add(of.createSee(see));
			link.getContent().add(new JAXBElement<MyAnyType>(new QName("linklabel"), MyAnyType.class, label));
			return new ZefDicVisitor(label.getContent(), xrefMap);
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			if (!mode.equals(Boolean.getBoolean("rawhtml.online") ? RawHTMLMode.OFFLINE : RawHTMLMode.ONLINE)) {
				addRaw(raw);
			}
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			throw new IllegalStateException("Variations not supported");
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			return prio.handleVisitor(category, this);
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			return false;
		}

		private void addRaw(String html) {
			MyAnyType mat = of.createMyAnyType();
			target.add(new JAXBElement<MyAnyType>(new QName("raw"), MyAnyType.class, mat));
			mat.getContent().add(html);
		}
	}
}
