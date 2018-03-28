package biblemulticonverter.logos.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import biblemulticonverter.tools.Tool;

public class LogosFootnotePostprocessor implements Tool {
	public static final String[] HELP_TEXT = {
			"Postprocess footnote numbers in DOCX converted from HTML by LibreOffice",
			"",
			"Usage: LogosFootnotePostprocessor <infile> <outfile>",
			"",
			"When exporting footnotes from LibreOffice, the 'fixed' footnote marks are not",
			"correctly written to footnotes.xml, but instead <w:footnoteRef/> is used.",
			"Therefore, if you open the converted document in Word, the footnote numbers",
			"that are shown in the footnote section are off (the footnote numbers of the",
			"footnote marks are correct). Postprocess your file with this postprocessor to",
			"align both footnote numbers.",
			"",
			"Note that this also results in the footnote numbers to appear in the tooltips",
			"within Logos, so use with care."
	};

	private static final String NS_URI_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
	private static final String NS_URI_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

	public void run(String... args) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
		Transformer t = TransformerFactory.newInstance().newTransformer();
		xp.setNamespaceContext(new NamespaceContext() {
			public String getNamespaceURI(String prefix) {
				if ("w".equals(prefix))
					return NS_URI_W;
				return XMLConstants.NULL_NS_URI;
			}

			public String getPrefix(String uri) {
				throw new UnsupportedOperationException();
			}

			public Iterator<?> getPrefixes(String uri) {
				throw new UnsupportedOperationException();
			}
		});
		Map<String, String> footnoteMarks = new HashMap<String, String>();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(args[0]))) {
			ZipEntry ze;
			while ((ze = zis.getNextEntry()) != null) {
				if (ze.getName().equals("word/document.xml")) {
					baos.reset();
					copy(zis, baos);
					Document mainDoc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray()));
					NodeList footnotes = (NodeList) xp.evaluate("//w:footnoteReference", mainDoc, XPathConstants.NODESET);
					for (int i = 0; i < footnotes.getLength(); i++) {
						Element footnote = (Element)footnotes.item(i);
						String id = footnote.getAttributeNS(NS_URI_W, "id");
						String mark = footnote.getNextSibling().getTextContent();
						footnoteMarks.put(id, mark);
					}
					break;
				}
			}
		}
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(args[0]));
				ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(args[1]))) {
			ZipEntry ze;
			while ((ze = zis.getNextEntry()) != null) {
				zos.putNextEntry(new ZipEntry(ze.getName()));
				if (ze.getName().equals("word/footnotes.xml")) {
					baos.reset();
					copy(zis, baos);
					Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray()));
					NodeList fnRefs = (NodeList) xp.evaluate("//w:footnoteRef", doc, XPathConstants.NODESET);
					for (int i = 0; i < fnRefs.getLength(); i++) {
						Element fnRef = (Element) fnRefs.item(i);
						Element parentFootnote = (Element)fnRef.getParentNode();
						while (!parentFootnote.getLocalName().equals("footnote")) {
							parentFootnote = (Element)parentFootnote.getParentNode();
						}
						String id = parentFootnote.getAttributeNS(NS_URI_W, "id");
						String mark = footnoteMarks.get(id).toString();
						Element fnMark = doc.createElementNS(NS_URI_W, "w:t");
						fnMark.appendChild(doc.createTextNode(mark));
						fnRef.getParentNode().insertBefore(fnMark, fnRef);
						fnRef.getParentNode().removeChild(fnRef);
					}
					baos.reset();
					t.transform(new DOMSource(doc), new StreamResult(baos));
					zos.write(baos.toByteArray());
				} else {
					copy(zis, zos);
				}
				zos.closeEntry();
			}
		}
	}

	private static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[4096];
		int len;
		while ((len = in.read(buffer)) != -1) {
			out.write(buffer, 0, len);
		}
	};
}
