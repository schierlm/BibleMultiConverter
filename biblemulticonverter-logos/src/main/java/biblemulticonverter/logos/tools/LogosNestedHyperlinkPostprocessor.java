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

public class LogosNestedHyperlinkPostprocessor implements Tool {
	public static final String[] HELP_TEXT = {
			"Postprocess nested hyperlinks in DOCX converted from HTML by LibreOffice",
			"",
			"Usage: LogosNestedHyperlinkPostprocessor <infile> <outfile>",
			"",
			"Postprocesses the hyperlinks separated by (+) to nested hyperlinks,",
			"which are not supported by LibreOffice. Also postprocess hyperlinks that",
			"were exported as strike tags due to LibreOffice's hyperlink limit."
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
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Map<String, String> linkIDs = new HashMap<String, String>();
		ZipEntry rels = null;
		Document relsDoc = null;
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(args[0]))) {
			ZipEntry ze ;
			while ((ze = zis.getNextEntry()) != null) {
				if (ze.getName().equals("word/_rels/document.xml.rels")) {
					baos.reset();
					copy(zis, baos);
					rels = ze;
					dbf.setNamespaceAware(false);
					relsDoc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray()));
					dbf.setNamespaceAware(true);
					break;
				}
			}
		}
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(args[0]));
				ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(args[1]))) {
			ZipEntry ze;
			while ((ze = zis.getNextEntry()) != null) {
				if (ze.getName().equals("word/_rels/document.xml.rels")) {
					// add it later
					continue;
				}
				zos.putNextEntry(new ZipEntry(ze.getName()));
				if (ze.getName().equals("word/document.xml")) {
					baos.reset();
					copy(zis, baos);
					Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray()));
					NodeList strikes = (NodeList) xp.evaluate("//w:strike", doc, XPathConstants.NODESET);
					for (int i = 0; i < strikes.getLength(); i++) {
						Node strike = strikes.item(i);
						if (strike.getPreviousSibling() != null)
							continue;
						if (strike.getNextSibling() != null && !strike.getNextSibling().getNodeName().equals("w:color"))
							continue;
						if (strike.getNextSibling() != null && strike.getNextSibling().getNextSibling() != null)
							continue;
						Node text = strike.getParentNode().getNextSibling().getFirstChild();
						if (!(text instanceof Text))
							continue;
						String value = text.getNodeValue();
						if (!value.startsWith("|") || !value.contains("||"))
							continue;
						String[] parts = value.split("\\|\\|", 2);
						if (parts[1].contains("||"))
							System.out.println("WARNING: link target contains pipes; probably preprocessing bug: " + parts[1]);
						text.setNodeValue(parts[1]);
						parts = parts[0].split("\\|");
						Element style = doc.createElementNS(NS_URI_W, "w:rStyle");
						style.setAttributeNS(NS_URI_W, "w:val", "Internetlink");
						strike.getParentNode().insertBefore(style, strike);
						Node toWrap = strike.getParentNode().getParentNode();
						strike.getParentNode().removeChild(strike);
						// wrap them so that the first link gets innermost
						for (String link : parts) {
							Element hyperlink = doc.createElementNS(NS_URI_W, "w:hyperlink");
							String linkID = linkIDs.get(link);
							if (linkID == null) {
								linkID = "rIdBMC" + linkIDs.size();
								linkIDs.put(link, linkID);
								Element rel = relsDoc.createElement("Relationship");
								rel.setAttribute("Id", linkID);
								rel.setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink");
								rel.setAttribute("Target", link);
								rel.setAttribute("TargetMode", "External");
								relsDoc.getDocumentElement().appendChild(rel);
							}
							hyperlink.setAttributeNS(NS_URI_R, "r:id", linkID);
							toWrap.getParentNode().insertBefore(hyperlink, toWrap);
							toWrap.getParentNode().removeChild(toWrap);
							hyperlink.appendChild(toWrap);
							toWrap = hyperlink;
						}
					}
					NodeList nestedLinks = (NodeList) xp.evaluate("//w:hyperlink[w:r/w:t/text()='\u2295']", doc, XPathConstants.NODESET);
					for (int i = 0; i < nestedLinks.getLength(); i++) {
						Node link = nestedLinks.item(i);
						Node target = link.getPreviousSibling();
						target.getParentNode().removeChild(target);
						if (!target.getLocalName().equals("hyperlink"))
							throw new RuntimeException(target.getLocalName());
						while (link.getFirstChild() != null) {
							link.removeChild(link.getFirstChild());
						}
						link.appendChild(target);
					}
					baos.reset();
					t.transform(new DOMSource(doc), new StreamResult(baos));
					zos.write(baos.toByteArray());
				} else {
					copy(zis, zos);
				}
				zos.closeEntry();
			}
			if (rels != null) {
				zos.putNextEntry(new ZipEntry(rels.getName()));
				baos.reset();
				t.transform(new DOMSource(relsDoc), new StreamResult(baos));
				zos.write(baos.toByteArray());
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