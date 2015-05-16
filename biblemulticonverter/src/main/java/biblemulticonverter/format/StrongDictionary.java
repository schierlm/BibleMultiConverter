package biblemulticonverter.format;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.regex.Matcher;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.MetadataBook.MetadataBookKey;
import biblemulticonverter.data.Utils;

/**
 * Importer for creating a Strong's dictionary from public domain resources.
 */
public class StrongDictionary implements ImportFormat {

	public static final String[] HELP_TEXT = {
			"Importer for creating a Strong's dictionary from public domain resources.",
			"",
			"Import file is ignored.",
	};

	@Override
	public Bible doImport(File inputFile) throws Exception {
		Bible result = new Bible("Strong's dictionary");
		MetadataBook mb = new MetadataBook();
		mb.setValue(MetadataBookKey.description, "Strong's dictionary compiled by BibleMultiConverter from public sources.");
		mb.setValue(MetadataBookKey.source, "https://github.com/openscriptures/HebrewLexicon/ and https://github.com/morphgnt/strongs-dictionary-xml/");
		mb.setValue(MetadataBookKey.rights, "Strong's Greek Dictionary is in the public domain. Strong's Hebrew Dictionary is provided as XML files by the Open Scriptures Hebrew Bible Project, which are licensed CC-BY-4.0.");
		mb.finished();
		result.getBooks().add(mb.getBook());
		DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc;
		try (InputStream in = new URL("https://raw.githubusercontent.com/morphgnt/strongs-dictionary-xml/master/strongsgreek.xml").openStream()) {
			doc = db.parse(in);
		}
		for (Node entryNode = doc.getDocumentElement().getLastChild().getFirstChild(); entryNode != null; entryNode = entryNode.getNextSibling()) {
			Element entry = (Element) entryNode;
			int number = Integer.parseInt(entry.getAttribute("strongs"));
			System.out.println("G" + number);
			Book bk = new Book("G" + number, BookID.DICTIONARY_ENTRY, "G" + number, "G" + number);
			FormattedText prolog = new FormattedText();
			bk.getChapters().add(new Chapter());
			bk.getChapters().get(0).setProlog(prolog);
			result.getBooks().add(bk);
			Visitor<RuntimeException> v = prolog.getAppendVisitor();
			for (Node childNode = entry.getFirstChild(); childNode != null; childNode = childNode.getNextSibling()) {
				if (childNode instanceof Text) {
					if (childNode.getTextContent().replaceAll("[ \r\n\t]+", " ").equals(" or ") && childNode.getNextSibling().getNodeName().equals("greek")) {
						v.visitFormattingInstruction(FormattingInstructionKind.ITALIC).visitText("-or-");
						v.visitLineBreak(LineBreakKind.PARAGRAPH);
					} else if (childNode.getTextContent().trim().length() > 0) {
						visitAttribute(v, "Remark", childNode.getTextContent());
					}
					continue;
				}
				Element elem = (Element) childNode;
				switch (elem.getNodeName()) {
				case "strongs":
					int compNumber = Integer.parseInt(elem.getTextContent());
					if (compNumber != number)
						throw new IOException(compNumber + " != " + number);
					break;
				case "greek":
					v.visitHeadline(1).visitText(elem.getAttribute("unicode"));
					visitAttribute(v, "Transliteration", elem.getAttribute("translit"));
					break;
				case "pronunciation":
					visitAttribute(v, "Pronunciation", elem.getAttribute("strongs"));
					break;
				case "strongs_derivation":
					visitAttribute(v, "Strongs Derivation", parseGreekContent(elem));
					break;
				case "strongs_def":
					visitAttribute(v, "Strongs Definition", parseGreekContent(elem));
					break;
				case "kjv_def":
					visitAttribute(v, "KJV Definition", parseGreekContent(elem));
					if (elem.getNextSibling() != null && !elem.getNextSibling().getNodeName().equals("see")) {
						Element moreInfo = doc.createElement("more_info");
						elem.getParentNode().insertBefore(moreInfo, elem.getNextSibling());
						while (moreInfo.getNextSibling() != null) {
							if (moreInfo.getNextSibling().getNodeName().equals("see"))
								break;
							moreInfo.appendChild(moreInfo.getNextSibling());
						}
						if (moreInfo.getTextContent().trim().isEmpty())
							moreInfo.getParentNode().removeChild(moreInfo);
					}
					break;
				case "strongsref":
					visitAttribute(v, "Reference", "[" + elem.getAttribute("language").substring(0, 1) + Integer.parseInt(elem.getAttribute("strongs")) + "]");
				case "more_info":
					visitAttribute(v, "More Information", parseGreekContent(elem));
					break;
				case "see":
					visitAttribute(v, "See Also", "[" + elem.getAttribute("language").substring(0, 1) + Integer.parseInt(elem.getAttribute("strongs")) + "]");
					break;
				default:
					throw new IOException(elem.getNodeName());
				}
			}
			prolog.trimWhitespace();
			prolog.finished();
		}
		try (InputStream in = new URL("https://raw.githubusercontent.com/openscriptures/HebrewLexicon/master/HebrewStrong.xml").openStream()) {
			doc = db.parse(in);
		}
		for (Node entryNode = doc.getDocumentElement().getFirstChild(); entryNode != null; entryNode = entryNode.getNextSibling()) {
			if (entryNode instanceof Text) {
				if (!entryNode.getTextContent().trim().isEmpty()) {
					throw new IOException(entryNode.getTextContent());
				}
				continue;
			}
			Element entry = (Element) entryNode;
			String id = entry.getAttribute("id");
			System.out.println(id);
			Book bk = new Book(id, BookID.DICTIONARY_ENTRY, id, id);
			FormattedText prolog = new FormattedText();
			bk.getChapters().add(new Chapter());
			bk.getChapters().get(0).setProlog(prolog);
			result.getBooks().add(bk);
			Visitor<RuntimeException> v = prolog.getAppendVisitor();
			for (Node childNode = entry.getFirstChild(); childNode != null; childNode = childNode.getNextSibling()) {
				if (childNode instanceof Text) {
					if (!childNode.getTextContent().trim().isEmpty()) {
						throw new IOException(childNode.getTextContent());
					}
					continue;
				}
				Element elem = (Element) childNode;
				switch (elem.getNodeName()) {
				case "w":
					v.visitHeadline(1).visitText(elem.getTextContent());
					visitAttribute(v, "Transliteration", elem.getAttribute("xlit"));
					visitAttribute(v, "Pronunciation", elem.getAttribute("pron"));
					if (elem.getAttribute("xml:lang").equals("heb")) {
						visitAttribute(v, "Language", "Hebrew");
					} else if (elem.getAttribute("xml:lang").equals("arc")) {
						visitAttribute(v, "Language", "Aramaic");
					} else if (elem.getAttribute("xml:lang").equals("x-pn")) {
						visitAttribute(v, "Language", "Proper Noun");
					} else {
						throw new IOException(elem.getAttribute("xml:lang"));
					}
					visitAttribute(v, "Part of speech", elem.getAttribute("pos"));
					break;
				case "source":
					visitAttribute(v, "Source", parseHebrewContent(elem));
					break;
				case "meaning":
					visitAttribute(v, "Meaning", parseHebrewContent(elem));
					break;
				case "usage":
					visitAttribute(v, "Usage", parseHebrewContent(elem));
					break;
				case "note":
					// skip
					break;
				default:
					throw new IOException(elem.getNodeName());
				}
			}
			prolog.trimWhitespace();
			prolog.finished();
		}
		return result;
	}

	private static void visitAttribute(Visitor<RuntimeException> v, String key, String value) {
		if (value.trim().length() == 0)
			return;
		v.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText(key + ":");
		value = " " + value.replaceAll("[ \r\n\t]+", " ").trim();
		Matcher m = Utils.compilePattern("\\[([GH][1-9][0-9]+)\\]|<<<([^>]+)>>>").matcher(value);
		while (m.find()) {
			StringBuffer tmp = new StringBuffer();
			m.appendReplacement(tmp, "");
			v.visitText(tmp.toString());
			if (m.group(1) != null) {
				v.visitDictionaryEntry("strong", m.group(1)).visitText(m.group(1));
			} else if (m.group(2) != null) {
				v.visitFormattingInstruction(FormattingInstructionKind.ITALIC).visitText(m.group(2));
			} else {
				throw new RuntimeException();
			}
		}
		StringBuffer tmp = new StringBuffer();
		m.appendTail(tmp);
		v.visitText(tmp.toString());
		v.visitLineBreak(LineBreakKind.PARAGRAPH);
	}

	private String parseGreekContent(Element elem) throws IOException {
		StringBuilder result = new StringBuilder();
		for (Node child = elem.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Text) {
				result.append(child.getTextContent());
				continue;
			}
			Element childElement = (Element) child;
			switch (childElement.getNodeName()) {
			case "strongsref":
				result.append("[" + childElement.getAttribute("language").substring(0, 1) + Integer.parseInt(childElement.getAttribute("strongs")) + "]");
				break;
			case "latin":
				result.append(childElement.getTextContent());
				break;
			case "greek":
				result.append(childElement.getAttribute("unicode"));
				break;
			case "pronunciation":
				// skip
				break;
			default:
				throw new IOException(childElement.getNodeName());
			}
		}
		return result.toString();
	}

	private String parseHebrewContent(Element elem) throws IOException {
		StringBuilder result = new StringBuilder();
		for (Node child = elem.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Text) {
				result.append(child.getTextContent());
				continue;
			}
			Element childElement = (Element) child;
			switch (childElement.getNodeName()) {
			case "w":
				if (childElement.getAttribute("src").isEmpty())
					result.append(childElement.getTextContent());
				else
					result.append("[" + childElement.getAttribute("src") + "]");
				break;
			case "def":
				result.append("<<<" + childElement.getTextContent() + ">>>");
				break;
			case "note":
				// skip
				break;
			default:
				throw new IOException(childElement.getNodeName());
			}
		}
		return result.toString();
	}
}
