package biblemulticonverter.sword.format;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.sword.SwordBookPath;
import org.crosswire.jsword.passage.Verse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.format.ImportFormat;
import biblemulticonverter.format.OSIS;

public class SWORD implements ImportFormat {

	public static final String[] HELP_TEXT = {
			"Importer for SWORD modules",
			"",
			"Usage: SWORD <moduleDir>/<moduleName>",
			"",
			"In case you do not have the module yet (or it is not in a SWORD module directory),",
			"you can use the SWORDDownloader tool to download it from a SWORD repository."
	};

	@Override
	public Bible doImport(File inputFile) throws Exception {
		System.out.println("Loading locally installed books...");
		SwordBookPath.setDownloadDir(inputFile.getParentFile());
		Book book = Books.installed().getBook(inputFile.getName());
		System.out.println("======");
		return doImport(book);
	}

	protected Bible doImport(Book book) throws Exception {
		OSISHelper helper = new OSISHelper();
		Bible result = new Bible(book.getName());
		TransformerHandler th = ((SAXTransformerFactory) SAXTransformerFactory.newInstance()).newTransformerHandler();
		Map<BookID, biblemulticonverter.data.Book> parsedBooks = new EnumMap<>(BookID.class);
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		for (Iterator<?> iter = book.getGlobalKeyList().iterator(); iter.hasNext();) {
			Verse v = (Verse) iter.next();
			BookID bkid = biblemulticonverter.sword.BookMapping.MAPPING.get(v.getBook());
			biblemulticonverter.data.Book bk = parsedBooks.get(bkid);
			if (!parsedBooks.containsKey(bkid)) {
				bk = new biblemulticonverter.data.Book(bkid.getOsisID().replace("x-Intr", "Intr"), bkid, bkid.getEnglishName(), bkid.getEnglishName());
				parsedBooks.put(bkid, bk);
				bk.getChapters().add(new Chapter());
				result.getBooks().add(bk);
			}
			int chapterNum = v.getChapter(), verseNum = v.getVerse();
			while (bk.getChapters().size() < chapterNum)
				bk.getChapters().add(new Chapter());
			Chapter chapter = bk.getChapters().get(chapterNum == 0 ? 0 : chapterNum - 1);
			FormattedText verse;
			if (verseNum == 0) {
				verse = new FormattedText();
				if (chapter.getProlog() != null) {
					chapter.getProlog().accept(verse.getAppendVisitor());
				}
				chapter.setProlog(verse);
			} else {
				if (chapterNum == 0)
					throw new IllegalStateException("Verse " + verseNum + " in chapter 0 is invalid");
				verse = new biblemulticonverter.data.Verse("" + verseNum);
				chapter.getVerses().add((biblemulticonverter.data.Verse) verse);
			}
			Element root = doc.createElement("verse");
			th.setResult(new DOMResult(root));
			new BookData(book, v).getSAXEventProvider().provideSAXEvents(th);
			if (root.getChildNodes().getLength() == 1 && root.getFirstChild() instanceof Element && root.getFirstChild().getNodeName().equals("div") && root.getFirstChild().getChildNodes().getLength() >= 1 && root.getFirstChild().getFirstChild().getNodeName().equals("title")) {
				Element div = (Element) root.getFirstChild();
				root.removeChild(div);
				div.removeChild(div.getFirstChild());
				while (div.getFirstChild() != null) {
					Node child = div.getFirstChild();
					div.removeChild(child);
					root.appendChild(child);
				}
			} else {
				throw new RuntimeException("Unexpected OSIS structure!");
			}
			helper.handleVerse(root, verse);
			if (verse.getElementTypes(1).length() == 0) {
				System.out.println("WARNING: Empty verse " + bk.getAbbr() + " " + chapterNum + ":" + verseNum);
				if (verse instanceof biblemulticonverter.data.Verse)
					chapter.getVerses().remove(verse);
				else
					chapter.setProlog(null);
			}
		}
		for (biblemulticonverter.data.Book bk : parsedBooks.values()) {
			while (!bk.getChapters().isEmpty()) {
				Chapter ch = bk.getChapters().get(bk.getChapters().size() - 1);
				if (ch.getProlog() == null && ch.getVerses().isEmpty()) {
					bk.getChapters().remove(ch);
				} else {
					break;
				}
			}
			if (bk.getChapters().isEmpty()) {
				result.getBooks().remove(bk);
			}
		}
		return result;
	}

	private static class OSISHelper extends OSIS {
		private List<Element> unclosedElements = new ArrayList<>();

		public void handleVerse(Element root, FormattedText verse) {
			convertToMilestoned(root);
			Element elem = root;
			if (root.getFirstChild().getNodeName().equals("verse") && root.getFirstChild().getNextSibling() == null) {
				elem = (Element) root.getFirstChild();
			}
			convertFromMilestoned(elem, unclosedElements);
			parseFormattedText("SWORD module", elem, verse);
			verse.trimWhitespace();
			verse.finished();
		}
	}
}
