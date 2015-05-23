package biblemulticonverter.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;

/**
 * Very rudimentary importer/exporter for OSIS. This does not use JAXB as most
 * OSIS files I wanted to use are not valid according to the schema, and as most
 * OSIS tags are mixed content anyway where JAXB does not provide that great
 * advantages. Also, JAXB bindings for OSIS schema are several hundred KB large.
 */
public class OSIS implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Very rudimentary OSIS (Open Scripture Information Standard) import/export.",
	};

	private static final Pattern XREF_PATTERN = Pattern.compile("([A-Za-z0-9]+) ([0-9]+), ([0-9]+)");

	private Set<String> printedWarnings = new HashSet<String>();
	private Properties osisRefMap = null;

	@Override
	public Bible doImport(File inputFile) throws Exception {
		printedWarnings.clear();
		DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
		Document osisDoc = docBuilder.parse(inputFile);
		Bible result = new Bible(xpath.evaluate("/osis/osisText/header/work/title/text()", osisDoc));
		NodeList osisBooks = (NodeList) xpath.evaluate("/osis/osisText/div[@type='book']", osisDoc, XPathConstants.NODESET);
		for (int bookIndex = 0; bookIndex < osisBooks.getLength(); bookIndex++) {
			Element osisBook = (Element) osisBooks.item(bookIndex);
			String bookOsisID = osisBook.getAttribute("osisID");
			BookID bookID = BookID.fromOsisId(bookOsisID);
			String title = bookID.getEnglishName();
			Node titleElem = osisBook.getFirstChild();
			while (titleElem instanceof Text)
				titleElem = titleElem.getNextSibling();
			if (titleElem instanceof Element && titleElem.getNodeName().equals("title")) {
				Element titleElement = (Element) titleElem;
				if (titleElement.getAttribute("type").equals("main"))
					title = titleElement.getTextContent();
			}
			Book bibleBook = new Book(bookOsisID, bookID, title, title);
			result.getBooks().add(bibleBook);
			parseBook(bookOsisID, osisBook, bibleBook);
		}
		return result;
	}

	private void parseBook(String bookName, Element osisBook, Book bibleBook) {
		for (Node node = osisBook.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Text) {
				if (((Text) node).getTextContent().trim().length() > 0)
					printWarning("WARNING: Non-whitespace text at book level");
			} else {
				Element elem = (Element) node;
				if (elem.getNodeName().equals("title")) {
					if (elem.getAttribute("type").equals("main")) {
						if (!getTextChildren(elem).equals(bibleBook.getLongName())) {
							printWarning("WARNING: More than one book title found");
						}
					} else {
						printWarning("WARNING: invalid book level title type: " + elem.getAttribute("type"));
					}
				} else if (elem.getNodeName().equals("chapter")) {
					String chapterName = elem.getAttribute("osisID");
					if (!chapterName.startsWith(bookName + ".")) {
						throw new IllegalStateException("Invalid chapter " + chapterName + " of book " + bookName);
					} else {
						int cnumber = Integer.parseInt(chapterName.substring(bookName.length() + 1));
						while (bibleBook.getChapters().size() < cnumber) {
							bibleBook.getChapters().add(new Chapter());
						}
						parseChapter(chapterName, elem, bibleBook.getChapters().get(cnumber - 1));
					}
				} else {
					printWarning("WARNING: invalid book level tag: " + elem.getNodeName());
				}
			}
		}
	}

	private String getTextChildren(Element elem) {
		StringBuilder result = new StringBuilder();
		for (Node node = elem.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element) {
				printWarning("WARNING: Unsupported tag inside " + elem.getNodeName() + ": " + node.getNodeName());
				continue;
			}
			result.append(((Text) node).getTextContent());
		}
		return result.toString().replaceAll("[\r\n\t ]+", " ").trim();
	}

	private void parseChapter(String chapterName, Element osisChapter, Chapter chapter) {
		Verse verse = null;
		FormattedText prolog = null;
		flattenChildren(osisChapter);
		int nextVerse = 1;
		List<Headline> headlines = new ArrayList<Headline>();
		for (Node node = osisChapter.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Text) {
				if (verse != null) {
					String text = node.getTextContent().replaceAll("[ \r\n\t]+", " ");
					if (text.endsWith(" ") && node.getNextSibling() != null && Arrays.asList("verse", "brp", "lb").contains(node.getNextSibling().getNodeName())) {
						printWarning("WARNING: Whitespace at end of verse");
						text = text.substring(0, text.length() - 1);
					}
					verse.getAppendVisitor().visitText(text);
				} else if (nextVerse == 1) {
					if (prolog == null && ((Text) node).getTextContent().trim().length() == 0)
						continue;
					if (prolog == null) {
						prolog = new FormattedText();
						chapter.setProlog(prolog);
					}
					prolog.getAppendVisitor().visitText(node.getTextContent().replaceAll("[ \r\n\t]+", " "));
				} else if (((Text) node).getTextContent().trim().length() > 0) {
					printWarning("WARNING: Non-whitespace at chapter level: " + node.getTextContent());
				}
			} else {
				Element elem = (Element) node;
				if (elem.getNodeName().equals("title")) {
					Headline hl = new Headline(2);
					if (elem.getAttribute("type").equals("chapter")) {
						hl = new Headline(1);
					}
					if (elem.getChildNodes().getLength() == 1 && elem.getFirstChild() instanceof Text) {
						String text = elem.getFirstChild().getTextContent();
						if (!text.equals(text.trim())) {
							printWarning("WARNING: Whitespace at beginning/end of headline: '" + text + "'");
							elem.getFirstChild().setNodeValue(text.trim());
						}
					}
					parseStructuredTextChildren(hl.getAppendVisitor(), elem);
					if (hl.getElementTypes(1).length() == 0) {
						printWarning("WARNING: Empty headline in " + chapterName + (verse == null ? "" : "." + verse.number));
					} else if (verse != null) {
						hl.accept(verse.getAppendVisitor().visitHeadline(hl.getDepth()));
					} else {
						headlines.add(hl);
					}
				} else if (elem.getNodeName().equals("lb")) {
					if (verse != null) {
						verse.getAppendVisitor().visitLineBreak(LineBreakKind.NEWLINE);
					}
				} else if (elem.getNodeName().equals("brp")) {
					if (verse != null) {
						verse.getAppendVisitor().visitLineBreak(LineBreakKind.PARAGRAPH);
					}
				} else if (elem.getNodeName().equals("divineName")) {
					FormattedText parent = nextVerse == 1 ? prolog : verse;
					if (parent == null)
						throw new IllegalStateException("divineName at invalid location");
					parseStructuredTextElement(parent.getAppendVisitor(), elem);
				} else if (elem.getNodeName().equals("note")) {
					if (elem.getAttribute("type").equals("crossReference")) {
						if (verse != null) {
							parseStructuredTextElement(verse.getAppendVisitor(), elem);
						} else {
							throw new IllegalStateException("note tag of type crossReference at invalid location");
						}
					} else if (verse != null) {
						parseStructuredTextElement(verse.getAppendVisitor(), elem);
					} else if (nextVerse == 1) {
						if (prolog == null) {
							prolog = new FormattedText();
							chapter.setProlog(prolog);
						}
						parseStructuredTextElement(prolog.getAppendVisitor(), elem);
					} else {
						printWarning("WARNING: note tag at invalid location");
					}
				} else if (elem.getNodeName().equals("verse")) {
					String osisID = elem.getAttribute("osisID");
					if (osisID.isEmpty())
						osisID = null;
					String sID = elem.getAttribute("sID");
					if (sID.isEmpty())
						sID = null;
					String eID = elem.getAttribute("eID");
					if (eID.isEmpty())
						eID = null;
					if (osisID != null && sID != null && eID == null && osisID.equals(sID)) {
						if (!sID.startsWith(chapterName + "."))
							throw new IllegalStateException("Invalid verse " + sID + " in chapter " + chapterName);
						if (verse != null) {
							printWarning("WARNING: Opening verse " + sID + " while verse " + verse.getNumber() + " is open");
							verse.trimWhitespace();
							verse.finished();
							verse = null;
						}
						String vnumber = sID.substring(chapterName.length() + 1);
						verse = new Verse(vnumber);
						for (Headline hl : headlines) {
							hl.accept(verse.getAppendVisitor().visitHeadline(hl.getDepth()));
						}
						headlines.clear();
						chapter.getVerses().add(verse);
						nextVerse = Integer.parseInt(verse.getNumber()) + 1;
					} else if (osisID == null && sID == null && eID != null) {
						if (verse == null) {
							printWarning("WARNING: Closing verse " + eID + " that is not open");
						} else if (!eID.equals(chapterName + "." + verse.getNumber())) {
							throw new IllegalStateException("Closing verse " + eID + " but open is " + verse.getNumber());
						} else {
							verse.trimWhitespace();
							verse.finished();
							if (verse.getElementTypes(1).length() == 0) {
								printWarning("WARNING: Empty verse " + eID);
								chapter.getVerses().remove(verse);
							}
							verse = null;
						}
					} else {
						throw new IllegalStateException("Invalid combination of verse IDs:" + osisID + "/" + sID + "/" + eID);
					}
				} else {
					if (verse == null)
						printWarning("WARNING: " + elem.getNodeName() + " at invalid location");
					else
						parseStructuredTextElement(verse.getAppendVisitor(), elem);
				}
			}
		}
		if (prolog != null) {
			prolog.trimWhitespace();
			prolog.finished();
		}
		if (verse != null) {
			verse.finished();
			printWarning("WARNING: Unclosed verse: " + chapterName + "." + verse.getNumber());
		}
		if (headlines.size() > 0)
			printWarning("WARNING: Unused headlines: " + headlines.size());
	}

	private void printWarning(String warning) {
		if (printedWarnings.add(warning)) {
			System.out.println(warning);
		}
	}

	public void parseStructuredTextChildren(Visitor<RuntimeException> vv, Element textElem) {
		flattenChildren(textElem);
		for (Node node = textElem.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Text) {
				String text = node.getTextContent().replaceAll("[ \t\r\n]+", " ");
				if (text.endsWith(" ") && node.getNextSibling() != null && Arrays.asList("brp", "lb").contains(node.getNextSibling().getNodeName())) {
					printWarning("WARNING: Whitespace before newline");
					text = text.substring(0, text.length() - 1);
				}
				vv.visitText(text);
			} else {
				Element elem = (Element) node;
				parseStructuredTextElement(vv, elem);
			}
		}
	}

	private void parseStructuredTextElement(Visitor<RuntimeException> vv, Element elem) {
		if (elem.getNodeName().equals("lb")) {
			vv.visitLineBreak(LineBreakKind.NEWLINE);
		} else if (elem.getNodeName().equals("brp")) {
			vv.visitLineBreak(LineBreakKind.PARAGRAPH);
		} else if (elem.getNodeName().equals("divineName")) {
			parseStructuredTextChildren(vv.visitFormattingInstruction(FormattingInstructionKind.DIVINE_NAME), elem);
		} else if (elem.getNodeName().equals("woj")) {
			parseStructuredTextChildren(vv.visitFormattingInstruction(FormattingInstructionKind.WORDS_OF_JESUS), elem);
		} else if (elem.getNodeName().equals("hi")) {
			FormattingInstructionKind kind;
			if (elem.getAttribute("type").equals("italic")) {
				kind = FormattingInstructionKind.ITALIC;
			} else if (elem.getAttribute("type").equals("bold")) {
				kind = FormattingInstructionKind.BOLD;
			} else {
				kind = null;
				printWarning("WARNING: Invalid hi type: " + elem.getAttribute("type"));
			}
			if (elem.getChildNodes().getLength() != 0) {
				Visitor<RuntimeException> vv1 = kind == null ? vv : vv.visitFormattingInstruction(kind);
				parseStructuredTextChildren(vv1, elem);
			}
		} else if (elem.getNodeName().equals("seg") || elem.getNodeName().equals("transChange")) {
			String css;
			if (elem.getNodeName().equals("seg") && elem.getAttribute("type").equals("x-alternative")) {
				css = "osis-style: alternative; color: gray;";
			} else if (elem.getNodeName().equals("transChange") && elem.getAttribute("type").equals("added")) {
				css = "osis-style: added; font-style:italic;";
			} else if (elem.getNodeName().equals("transChange") && elem.getAttribute("type").equals("deleted")) {
				css = "osis-style: deleted; text-decoration: line-through; color: gray;";
			} else {
				css = null;
				printWarning("WARNING: Invalid " + elem.getNodeName() + " type: " + elem.getAttribute("type"));
			}
			if (elem.getChildNodes().getLength() != 0) {
				Visitor<RuntimeException> vv1 = css == null ? vv : vv.visitCSSFormatting(css);
				parseStructuredTextChildren(vv1, elem);
			}
		} else if (elem.getNodeName().equals("note")) {
			if (elem.getAttribute("type").equals("crossReference")) {
				Visitor<RuntimeException> fn = vv.visitFootnote();
				fn.visitText(FormattedText.XREF_MARKER);
				if (elem.getTextContent().length() > 0) {
					boolean first = true;
					for (String ref : elem.getTextContent().split("\\|")) {
						Matcher m = XREF_PATTERN.matcher(ref);
						if (!m.matches())
							throw new IllegalStateException("Malformed cross reference: " + ref);
						if (!first)
							fn.visitText("; ");
						first = false;
						String book = m.group(1);
						BookID bookID = BookID.fromOsisId(book);
						int ch = Integer.parseInt(m.group(2));
						String vs = m.group(3);
						fn.visitCrossReference(book, bookID, ch, vs, ch, vs).visitText(ref);
					}
				} else {
					printWarning("WARNING: crossReference without content");
					fn.visitText("-");
				}
			} else if (elem.getFirstChild() != null) {
				Visitor<RuntimeException> v = vv.visitFootnote();
				flattenChildren(elem);
				parseStructuredTextChildren(v, elem);
			}
		} else if (elem.getNodeName().equals("w")) {
			if (elem.getFirstChild() == null)
				return; // skip empty w tags
			String lemma = elem.getAttribute("lemma");
			String morph = elem.getAttribute("morph");
			String src = elem.getAttribute("src");
			Visitor<RuntimeException> v = vv;
			int[] strong = null, idx = null;
			String[] rmac = null;
			String rawStrong = null;
			if (lemma.startsWith("strong:G")) {
				rawStrong = lemma.substring(8).replace(" strong:G", "-");
			} else if (lemma.startsWith("strong:H")) {
				rawStrong = lemma.substring(8).replace(" strong:H", "-");
			}
			if (rawStrong != null) {
				if (rawStrong.matches("[0-9]+(-[0-9]+)*")) {
					String[] strs = rawStrong.split("-");
					strong = new int[strs.length];
					for (int i = 0; i < strs.length; i++) {
						strong[i] = Integer.parseInt(strs[i]);
					}
				} else {
					printWarning("WARNING: Invalid strong dictionary entry: " + rawStrong);
				}
			}
			if (morph.startsWith("robinson:")) {
				rmac = morph.substring(9).replace(" robinson:", " ").split(" ");
				boolean skipped = false;
				for (int i = 0; i < rmac.length; i++) {
					if (!Utils.compilePattern(Utils.RMAC_REGEX).matcher(rmac[i]).matches()) {
						printWarning("WARNING: Invalid RMAC: " + rmac[i]);
						skipped = true;
						rmac[i] = null;
					}
				}
				if (skipped) {
					List<String> tempList = new ArrayList<String>(Arrays.asList(rmac));
					tempList.removeAll(Collections.singleton(null));
					rmac = (String[]) tempList.toArray(new String[tempList.size()]);
					if (rmac.length == 0 || rmac.length != strong.length) {
						printWarning("WARNING: Skipped RMAC because different length!");
						rmac = null;
					}
				}
			}
			if (src.matches("[0-9]{2}( [0-9]{2})*")) {
				String[] strs = src.split(" ");
				idx = new int[strs.length];
				for (int i = 0; i < strs.length; i++) {
					idx[i] = Integer.parseInt(strs[i]);
				}
			}
			if (strong != null) {
				if (rmac == null && idx != null) {
					printWarning("WARNING: Skipping idx because rmac missing");
					idx = null;
				}
				v = v.visitGrammarInformation(strong, rmac, idx);
			} else if (rmac != null || idx != null) {
				printWarning("WARNING: Skipping rmac/idx because strongs missing");
			}
			parseStructuredTextChildren(v, elem);
		} else if (elem.getNodeName().equals("reference")) {
			String osisRef = elem.getAttribute("osisRef");
			if (osisRef.contains("\u00A0")) {
				printWarning("WARNING: osisRef contains Non-Breaking spaces: '" + osisRef + "'");
				osisRef = osisRef.replace('\u00A0', ' ');
			}
			if (!osisRef.equals(osisRef.trim())) {
				printWarning("WARNING: Removed whitespace from osisRef '" + osisRef + "' - replaced by '" + osisRef.trim() + "'");
				osisRef = osisRef.trim();
			}
			Matcher fixupMatcher = Utils.compilePattern("([A-Z0-9][A-Z0-9a-z]+\\.[0-9]+\\.)([0-9]+)((?:[+-][0-9]+)+)").matcher(osisRef);
			if (fixupMatcher.matches()) {
				osisRef = fixupMatcher.group(1) + fixupMatcher.group(2);
				for (String suffix : fixupMatcher.group(3).split("(?=[+-])")) {
					if (suffix.isEmpty())
						continue;
					osisRef += suffix.substring(0, 1).replace('+', ' ') + fixupMatcher.group(1) + suffix.substring(1);
				}
				printWarning("INFO: Replaced osisRef " + elem.getAttribute("osisRef") + " by " + osisRef);
			}
			if (osisRefMap == null) {
				osisRefMap = new Properties();
				String path = System.getProperty("biblemulticonverter.osisrefmap", "");
				if (!path.isEmpty()) {
					try (FileInputStream fis = new FileInputStream(path)) {
						osisRefMap.load(fis);
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
				}
			}
			String mappedOsisRef = osisRefMap.getProperty(osisRef);
			if (mappedOsisRef != null) {
				printWarning("INFO: Replaced osisRef " + osisRef + " by " + mappedOsisRef + " (based on osisRef map)");
				osisRef = mappedOsisRef;
			}
			if (osisRef.matches("[^ ]+ [^ ]+") && elem.getFirstChild() instanceof Text && elem.getFirstChild().getNextSibling() == null) {
				String value = elem.getTextContent();
				int lastPos = value.lastIndexOf('.');
				if (lastPos != -1) {
					Element newElem = elem.getOwnerDocument().createElement("reference");
					newElem.setAttribute("osisRef", osisRef.split(" ")[0]);
					newElem.appendChild(elem.getOwnerDocument().createTextNode(value.substring(0, lastPos)));
					parseStructuredTextElement(vv, newElem);
					vv.visitText(".");
					newElem = elem.getOwnerDocument().createElement("reference");
					newElem.setAttribute("osisRef", osisRef.split(" ")[1]);
					newElem.appendChild(elem.getOwnerDocument().createTextNode(value.substring(lastPos + 1)));
					parseStructuredTextElement(vv, newElem);
					return;
				}
			}
			Visitor<RuntimeException> v = vv;
			if (osisRef.matches("[A-Z0-9][A-Z0-9a-z]+\\.[0-9]+\\.[0-9]+")) {
				String[] osisRefParts = osisRef.split("\\.");
				int chapter = Integer.parseInt(osisRefParts[1]);
				try {
					v = v.visitCrossReference(osisRefParts[0], BookID.fromOsisId(osisRefParts[0]), chapter, osisRefParts[2], chapter, osisRefParts[2]);
				} catch (IllegalArgumentException ex) {
					printWarning("WARNING: " + ex.getMessage());
				}
			} else if (osisRef.matches("([A-Z0-9][A-Z0-9a-z]+)\\.[0-9]+\\.[0-9]+-\\1\\.[0-9]+\\.[0-9]+")) {
				String[] osisRefParts = osisRef.split("[.-]");
				int firstChapter = Integer.parseInt(osisRefParts[1]);
				int lastChapter = Integer.parseInt(osisRefParts[4]);
				try {
					v = v.visitCrossReference(osisRefParts[0], BookID.fromOsisId(osisRefParts[0]), firstChapter, osisRefParts[2], lastChapter, osisRefParts[5]);
				} catch (IllegalArgumentException ex) {
					printWarning("WARNING: " + ex.getMessage());
				}
			} else {
				printWarning("WARNING: Unsupported osisRef: " + osisRef);
			}
			parseStructuredTextChildren(v, elem);
		} else if (elem.getNodeName().equals("variation")) {
			parseStructuredTextChildren(vv.visitVariationText(new String[] { elem.getAttribute("name") }), elem);
		} else {
			printWarning("WARNING: invalid structured element level tag: " + elem.getNodeName());
		}
	}

	private static void flattenChildren(Element parent) {
		flattenChildren(parent, false);
	}

	private static void flattenChildren(Element parent, boolean recursive) {
		// flatten quotes / foreign / line groups / lines; add <br> tags around
		// lines
		boolean lbTagsInserted = false, wojTagsInserted = true;
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node.getNodeName().equals("l")) {
				lbTagsInserted = true;
				parent.insertBefore(parent.getOwnerDocument().createElement("lb"), node);
				if (node.getNextSibling() == null)
					parent.appendChild(parent.getOwnerDocument().createElement("lb"));
				else
					parent.insertBefore(parent.getOwnerDocument().createElement("lb"), node.getNextSibling());
			}
			if (node.getNodeName().equals("p")) {
				if (node.getLastChild() instanceof Element && node.getLastChild().getNodeName().equals("verse")) {
					node.insertBefore(parent.getOwnerDocument().createElement("brp"), node.getLastChild());
				} else {
					node.appendChild(parent.getOwnerDocument().createElement("brp"));
				}
			}
			if (node.getNodeName().equals("q") && !((Element) node).getAttribute("who").isEmpty()) {
				Element elem = (Element) node;
				if (!elem.getAttribute("who").equals("Jesus")) {
					System.out.println("WARNING: Unsupported q-who value: " + ((Element) node).getAttribute("who"));
				} else if (!elem.getAttribute("sID").isEmpty() && elem.getAttribute("eID").isEmpty()) {
					// start
					parent.insertBefore(parent.getOwnerDocument().createElement("woj"), node);
					wojTagsInserted = true;
				} else if (elem.getAttribute("sID").isEmpty() && !elem.getAttribute("eID").isEmpty()) {
					// end
					parent.insertBefore(parent.getOwnerDocument().createElement("wojEnd"), node);
					wojTagsInserted = true;
				} else {
					System.out.println("WARNING: Unsupported milestone attributes for WOJ");
				}
			}
			if (Arrays.asList("q", "foreign", "lg", "l", "div", "p").contains(node.getNodeName())) {
				flattenChildren((Element) node, true);
				while (node.getFirstChild() != null) {
					Node child = node.getFirstChild();
					if (node.getNodeName().equals("woj") || node.getNodeName().equals("wojEnd"))
						wojTagsInserted = true;
					node.removeChild(child);
					parent.insertBefore(child, node);
				}
				parent.removeChild(node);
				node = parent.getFirstChild();
			}
		}
		if (lbTagsInserted) {
			for (Node node1 = parent.getFirstChild(); node1 != null; node1 = node1.getNextSibling()) {
				Node node2 = node1.getNextSibling();
				while (node2 != null && node1.getNodeName().equals("lb") && node2.getNodeName().equals("lb")) {
					parent.removeChild(node2);
					node2 = node1.getNextSibling();
				}
			}
		}
		if (wojTagsInserted && !recursive) {
			for (Node node1 = parent.getFirstChild(); node1 != null; node1 = node1.getNextSibling()) {
				if (node1.getNodeName().equals("woj")) {
					Node node2 = node1.getNextSibling();
					for (; node2 != null; node2 = node2.getNextSibling()) {
						if (node2.getNodeName().equals("verse")) {
							// do not create groups over verse nodes!
							node2 = null;
							break;
						}
						if (node2.getNodeName().equals("woj")) {
							// do not create nested woj tags!
							node2 = null;
							break;
						}
						if (node2.getNodeName().equals("title")) {
							// surround by WOJ tags
							parent.insertBefore(parent.getOwnerDocument().createElement("wojEnd"), node2);
							parent.insertBefore(parent.getOwnerDocument().createElement("woj"), node2.getNextSibling());
							node2 = node2.getPreviousSibling();
						}
						if (node2.getNodeName().equals("wojEnd"))
							break;
					}
					if (node2 == null) {
						System.out.println("WARNING: Unclosed WOJ detected");
						Node old = node1;
						node1 = node1.getNextSibling();
						parent.removeChild(old);
					} else {
						while (node1.getNextSibling() != node2) {
							node1.appendChild(node1.getNextSibling());
						}
						parent.removeChild(node2);
					}
				} else if (node1.getNodeName().equals("wojEnd")) {
					System.out.println("WARNING: Closed WOJ that was not open");
					Node old = node1;
					node1 = node1.getNextSibling();
					parent.removeChild(old);
				}
			}
		}
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element osis = doc.createElement("osis");
		doc.appendChild(osis);
		osis.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		osis.setAttribute("xmlns", "http://www.bibletechnologies.net/2003/OSIS/namespace");
		osis.setAttribute("xsi:schemaLocation", "http://www.bibletechnologies.net/2003/OSIS/namespace http://www.bibletechnologies.net/osisCore.2.1.1.xsd");
		Element osisText = doc.createElement("osisText");
		osis.appendChild(osisText);
		osisText.setAttribute("canonical", "true");
		osisText.setAttribute("osisIDWork", "Exported");
		osisText.appendChild(buildHeader(doc, bible.getName()));

		for (Book bk : bible.getBooks()) {
			Element book = doc.createElement("div");
			osisText.appendChild(book);
			book.setAttribute("type", "book");
			book.setAttribute("canonical", "true");
			book.setAttribute("osisID", bk.getId().getOsisID());
			Element bookTitle = doc.createElement("title");
			book.appendChild(bookTitle);
			bookTitle.setAttribute("type", "main");
			bookTitle.appendChild(doc.createTextNode(bk.getLongName()));

			int cnumber = 0;
			for (Chapter chp : bk.getChapters()) {
				cnumber++;
				Element chapter = doc.createElement("chapter");
				book.appendChild(chapter);
				chapter.setAttribute("osisID", bk.getId().getOsisID() + "." + cnumber);
				OSISVisitor visitor = new OSISVisitor(chapter, bk.getId().isNT());
				Element elem = doc.createElement("title");
				chapter.appendChild(elem);
				elem.setAttribute("type", "chapter");
				elem.appendChild(doc.createTextNode(bk.getAbbr() + " " + cnumber));
				if (chp.getProlog() != null) {
					chp.getProlog().accept(visitor);
				}
				for (VirtualVerse vv : chp.createVirtualVerses()) {
					String osisID = bk.getId().getOsisID() + "." + cnumber + "." + vv.getNumber();
					for (Headline hl : vv.getHeadlines()) {
						hl.accept(visitor.visitHeadline(hl.getDepth()));
					}
					elem = doc.createElement("verse");
					chapter.appendChild(elem);
					elem.setAttribute("sID", osisID);
					elem.setAttribute("osisID", osisID);
					for (Verse v : vv.getVerses()) {
						if (!v.getNumber().equals("" + vv.getNumber())) {
							elem = doc.createElement("hi");
							chapter.appendChild(elem);
							elem.setAttribute("type", "bold");
							elem.appendChild(doc.createTextNode("(" + v.getNumber() + ")"));
						}
						v.accept(visitor);
					}
					elem = doc.createElement("verse");
					chapter.appendChild(elem);
					elem.setAttribute("eID", osisID);
				}
			}
		}
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(new File(exportArgs[0])));
	}

	private Element buildHeader(Document doc, String bookTitle) {
		Element header = doc.createElement("header");
		Element work = doc.createElement("work");
		header.appendChild(work);
		work.setAttribute("osisWork", "Exported");
		Element title = doc.createElement("title");
		work.appendChild(title);
		title.appendChild(doc.createTextNode(bookTitle));
		return header;
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return false;
	}

	private static class OSISVisitor implements Visitor<RuntimeException>
	{
		private final Element target;
		private boolean nt;

		private OSISVisitor(Element target, boolean nt) {
			this.target = target;
			this.nt = nt;
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			Element elem = target.getOwnerDocument().createElement("title");
			target.appendChild(elem);
			elem.setAttribute("canonical", "false");
			return new OSISVisitor(elem, nt);
		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			target.appendChild(target.getOwnerDocument().createTextNode(text));
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
			Element note = target.getOwnerDocument().createElement("note");
			target.appendChild(note);
			note.setAttribute("type", "x-footnote");
			return new OSISVisitor(note, nt);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			String ref = book.getOsisID() + "." + firstChapter + "." + firstVerse;
			if (firstChapter != lastChapter || firstVerse != lastVerse)
				ref += "-" + book.getOsisID() + "." + lastChapter + "." + lastVerse;
			Element reference = target.getOwnerDocument().createElement("reference");
			target.appendChild(reference);
			reference.setAttribute("osisRef", ref);
			return new OSISVisitor(reference, nt);
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			String hiType;

			switch (kind) {
			case DIVINE_NAME:
				Element divineName = target.getOwnerDocument().createElement("divineName");
				target.appendChild(divineName);
				return new OSISVisitor(divineName, nt);
			case BOLD:
				hiType = "bold";
				break;
			case FOOTNOTE_LINK:
				hiType = "x-footnote-link";
				break;
			case ITALIC:
				hiType = "italic";
				break;
			case LINK:
				hiType = "x-link";
				break;
			case STRIKE_THROUGH:
				hiType = "line-through";
				break;
			case SUBSCRIPT:
				hiType = "sub";
				break;
			case SUPERSCRIPT:
				hiType = "super";
				break;
			case UNDERLINE:
				hiType = "underline";
				break;
			case WORDS_OF_JESUS:
				hiType = "x-words-of-jesus";
				break;
			default:
				hiType = "x-unknown";
				break;
			}
			Element hi = target.getOwnerDocument().createElement("hi");
			target.appendChild(hi);
			hi.setAttribute("type", hiType);
			return new OSISVisitor(hi, nt);
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			// not supported
			return this;
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			// probably better be handled with <lg>/<l>?
			visitText("/");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			Element lb = target.getOwnerDocument().createElement("lb");
			target.appendChild(lb);
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			Element w = target.getOwnerDocument().createElement("w");
			target.appendChild(w);
			StringBuilder lemma = new StringBuilder();
			for (int strong : strongs) {
				if (lemma.length() > 0)
					lemma.append(' ');
				lemma.append("strong:" + (nt ? "G" : "H") + strong);
			}
			w.setAttribute("lemma", lemma.toString());

			if (rmac != null) {
				StringBuilder morph = new StringBuilder();
				for (String r : rmac) {
					if (morph.length() > 0)
						morph.append(' ');
					morph.append("robinson:" + r);
				}
				w.setAttribute("morph", morph.toString());
			}

			if (sourceIndices != null) {
				StringBuilder src = new StringBuilder();
				for (int idx : sourceIndices) {
					if (src.length() > 0)
						src.append(' ');
					src.append(String.format("%02d", idx));
				}
				w.setAttribute("src", src.toString());
			}

			return new OSISVisitor(w, nt);
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			// not supported
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			// not supported
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			throw new RuntimeException("Variations not supported");
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			return prio.handleVisitor(category, this);
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			return false;
		}
	}
}
