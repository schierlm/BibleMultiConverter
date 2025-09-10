package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;

import org.w3c.dom.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;

public class OSHB implements ImportFormat {

	public static final String[] HELP_TEXT = {
			"Importer for OpenScriptures Hebrew Bible MorphBB",
			"",
			"Usage: OSHB <directory>",
			"",
			"Download OSHB from <https://github.com/openscriptures/morphhb>."
	};

	@Override
	public Bible doImport(File directory) throws Exception {
		Bible bible = new Bible("OSHB");
		DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
		for (int zefID = 1; zefID < 40; zefID++) {
			BookID bid = BookID.fromZefId(zefID);
			Book book = new Book(bid.getOsisID(), bid, bid.getEnglishName(), bid.getEnglishName());
			bible.getBooks().add(book);
			Document doc = docBuilder.parse(new File(directory, bid.getOsisID() + ".xml"));
			NodeList verses = (NodeList) xpath.evaluate("//verse", doc, XPathConstants.NODESET);
			for (int i = 0; i < verses.getLength(); i++) {
				Element verse = (Element) verses.item(i);
				String[] parts = verse.getAttribute("osisID").split("\\.");
				if (parts.length != 3 || !parts[0].equals(book.getAbbr()))
					throw new RuntimeException();
				int cnum = Integer.parseInt(parts[1]);
				int vnum = Integer.parseInt(parts[2]);
				while (book.getChapters().size() < cnum)
					book.getChapters().add(new Chapter());
				Verse v = new Verse("" + vnum);
				book.getChapters().get(cnum - 1).getVerses().add(v);
				Visitor<RuntimeException> vv = v.getAppendVisitor();
				boolean spaceAllowed = false;
				for (Node ww = verse.getFirstChild(); ww != null; ww = ww.getNextSibling()) {
					if (ww instanceof Text && ww.getTextContent().trim().isEmpty()) {
						continue;
					}
					Element w = (Element) ww;
					if (spaceAllowed)
						vv.visitText(" ");
					spaceAllowed = true;
					if (w.getNodeName().equals("seg")) {
						vv.visitText(w.getTextContent().trim());
						continue;
					} else if (w.getNodeName().equals("note")) {
						vv.visitFootnote(false).visitText(w.getTextContent().replaceAll("[\r\n\t ]+", " ").trim());
						continue;
					}
					if (!w.getNodeName().equals("w"))
						throw new RuntimeException(w.getNodeName());
					List<String> snums = new ArrayList<>(Arrays.asList(w.getAttribute("lemma").split("[^0-9]+")));
					snums.removeIf(s -> s.isEmpty());
					int[] strong = new int[snums.size()];
					for (int j = 0; j < strong.length; j++) {
						strong[j] = Integer.parseInt(snums.get(j));
					}
					vv.visitGrammarInformation((char[]) null, strong.length == 0 ? null : strong, null, new String[] { w.getAttribute("morph") }, null, (int[]) null, null, null).visitText(w.getTextContent());
				}
				v.finished();
			}
		}
		return bible;
	}
}
