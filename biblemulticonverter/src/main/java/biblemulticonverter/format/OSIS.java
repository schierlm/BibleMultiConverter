package biblemulticonverter.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;

import org.w3c.dom.Comment;
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
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.HyperlinkType;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.MetadataBook.MetadataBookKey;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.schema.roundtripxml.ObjectFactory;
import biblemulticonverter.tools.ValidateXML;

/**
 * Very rudimentary importer/exporter for OSIS. This does not use JAXB as most
 * OSIS files I wanted to use are not valid according to the schema, and as most
 * OSIS tags are mixed content anyway where JAXB does not provide that great
 * advantages. Also, JAXB bindings for OSIS schema are several hundred KB large.
 */
public class OSIS implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Very rudimentary OSIS (Open Scripture Information Standard) import/export.",
			"",
			"Usage (export): OSIS <OutputFile> [-|verse|chapter|div|q][,...]",
			"",
			"When exporting to OSIS, you can pass a comma separated list of tag names you",
			"like to have milestoned in the output file as second parameter. The default",
			"is 'verse', the maximum is 'div,chapter,verse,q'."
	};

	private static final Pattern XREF_PATTERN = Pattern.compile("([A-Za-z0-9]+) ([0-9]+), ([0-9]+)");
	private static final Pattern XREF_PATTERN_2 = Pattern.compile("([A-Za-z0-9]+) ([0-9]+)[., ]+([0-9]+)[.,]?");

	private Set<String> printedWarnings = new HashSet<String>();
	private Properties osisRefMap = null;
	private String warningContext = "";
	private int milestoneIndex = 0;

	@Override
	public Bible doImport(File inputFile) throws Exception {
		ValidateXML.validateFileBeforeParsing(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/osisCore.2.1.1.xsd")), inputFile);
		printedWarnings.clear();
		DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
		Document osisDoc = docBuilder.parse(inputFile);
		String name = xpath.evaluate("/osis/osisText/header/work/title/text()", osisDoc);
		if (name.isEmpty())
			name = "OSIS Bible";
		Bible result = new Bible(name);
		String description = xpath.evaluate("/osis/osisText/header/work/description/text()", osisDoc);
		String rights = xpath.evaluate("/osis/osisText/header/work/rights/text()", osisDoc);
		if (!description.isEmpty() || !rights.isEmpty()) {
			String date = xpath.evaluate("/osis/osisText/header/work/date/text()", osisDoc);
			String titleDesc = xpath.evaluate("/osis/osisText/titlePage/description/text()", osisDoc);
			MetadataBook mb = new MetadataBook();
			if (!description.isEmpty())
				mb.setValue(MetadataBookKey.description, description.replaceAll("[\r\n\t ]+", " ").trim());
			if (!rights.isEmpty())
				mb.setValue(MetadataBookKey.rights, rights.replaceAll("[\r\n\t ]+", " ").trim());
			if (!date.isEmpty())
				mb.setValue(MetadataBookKey.date, date);
			if (!titleDesc.isEmpty())
				mb.setValue("description@titlePage", titleDesc.replaceAll("[\r\n\t ]+", " ").trim());
			mb.finished();
			result.getBooks().add(mb.getBook());
		}
		NodeList osisBooks = (NodeList) xpath.evaluate("/osis/osisText//div[@type='book']", osisDoc, XPathConstants.NODESET);
		for (int bookIndex = 0; bookIndex < osisBooks.getLength(); bookIndex++) {
			Element osisBook = (Element) osisBooks.item(bookIndex);
			if (!osisBook.getAttribute("sID").isEmpty()) {
				Element osisBookEnd = (Element) xpath.evaluate("//div[@eID='" + osisBook.getAttribute("sID") + "']", osisDoc, XPathConstants.NODE);
				if (osisBookEnd == null) {
					throw new IllegalStateException("No milestoned div found with eID " + osisBook.getAttribute("sID"));
				}
				if (!osisBookEnd.getParentNode().isSameNode(osisBook.getParentNode())) {
					List<Node> candidates = new ArrayList<>();
					Node commonParent = osisBookEnd;
					while (commonParent != null) {
						candidates.add(commonParent);
						commonParent = commonParent.getParentNode();
					}
					commonParent = osisBook;
					search: while (commonParent != null) {
						for (Node candidate : candidates) {
							if (commonParent.isSameNode(candidate)) {
								break search;
							}
						}
						commonParent = commonParent.getParentNode();
					}
					if (commonParent == null)
						throw new IllegalStateException("Unable to find common parent of milestoned div start and end tag");
					convertToMilestoned((Element) commonParent);
					if (!osisBookEnd.getParentNode().isSameNode(osisBook.getParentNode())) {
						throw new IllegalStateException("Unable to normalize XML so that milestoned div start and end tags are siblings");
					}
				}
				while (osisBook.getNextSibling() != null && !osisBook.getNextSibling().isSameNode(osisBookEnd)) {
					osisBook.appendChild(osisBook.getNextSibling());
				}
				osisBookEnd.getParentNode().removeChild(osisBookEnd);
			}
			String bookOsisID = osisBook.getAttribute("osisID");
			BookID bookID = BookID.fromOsisId(bookOsisID);
			String title = bookID.getEnglishName();
			Node titleElem = osisBook.getFirstChild();
			while (titleElem instanceof Text)
				titleElem = titleElem.getNextSibling();
			if (titleElem instanceof Element && titleElem.getNodeName().equals("title")) {
				Element titleElement = (Element) titleElem;
				if (titleElement.getAttribute("type").equals("main") && titleElement.getChildNodes().getLength() > 0)
					title = titleElement.getTextContent();
			}
			Book bibleBook = new Book(bookOsisID, bookID, title, title);
			result.getBooks().add(bibleBook);
			parseBook(bookOsisID, osisBook, bibleBook);
		}
		return result;
	}

	protected void convertToMilestoned(Element root) {
		boolean wojTagsInserted = convertAllToMilestoned(root);
		convertTitleVerseChapterFromMilestoned(root, wojTagsInserted);
	}

	protected boolean convertAllToMilestoned(Element root) {
		// convert everything to milestoned form
		for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element && node.getFirstChild() != null) {
				Element startNode = (Element) node;
				if (!startNode.getAttribute("sID").isEmpty() || !startNode.getAttribute("eID").isEmpty())
					throw new RuntimeException("Element " + node.getNodeName() + " has milestone and child nodes!");
				String milestone = "BibleMultiConverter-Milestone-" + (++milestoneIndex);
				startNode.setAttribute("sID", milestone);
				Element endNode = root.getOwnerDocument().createElement(node.getNodeName());
				endNode.setAttribute("eID", milestone);
				if (!startNode.getAttribute("who").isEmpty())
					endNode.setAttribute("who", startNode.getAttribute("who"));
				root.insertBefore(endNode, node.getNextSibling());
				while (node.getFirstChild() != null) {
					root.insertBefore(node.getFirstChild(), endNode);
				}
			}
		}
		// flatten quotes / foreign / line groups / lines; add <br> tags around
		// lines
		boolean lbTagsInserted = false, wojTagsInserted = true;
		for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (!(node instanceof Element))
				continue;
			Element elem = (Element) node;
			Node insertPoint = elem;
			for (String hoistTag : Arrays.asList("chapter", "verse")) {
				Node pointToCheck = insertPoint.getPreviousSibling();
				while (pointToCheck instanceof Text && pointToCheck.getTextContent().trim().isEmpty()) {
					pointToCheck = pointToCheck.getPreviousSibling();
				}
				if (pointToCheck != null && pointToCheck.getNodeName().equals(hoistTag)
						&& !((Element) pointToCheck).getAttribute("eID").isEmpty())
					insertPoint = pointToCheck;
			}
			if (elem.getNodeName().equals("l")) {
				lbTagsInserted = true;
				root.insertBefore(root.getOwnerDocument().createElement("lb"), insertPoint);
			} else if (elem.getNodeName().equals("p") && !elem.getAttribute("eID").isEmpty()) {
				root.insertBefore(root.getOwnerDocument().createElement("brp"), insertPoint);
			}
			if (node.getNodeName().equals("q") && !elem.getAttribute("who").isEmpty()) {
				if (!elem.getAttribute("who").equals("Jesus")) {
					System.out.println("WARNING: Unsupported q-who value: " + ((Element) node).getAttribute("who"));
				} else {
					// start
					Element woj = root.getOwnerDocument().createElement("woj");
					woj.setAttribute("sID", elem.getAttribute("sID"));
					woj.setAttribute("eID", elem.getAttribute("eID"));
					root.insertBefore(woj, node);
					wojTagsInserted = true;
				}
			}
			if (Arrays.asList("q", "foreign", "lg", "l", "div", "p").contains(node.getNodeName())) {
				if (node.getFirstChild() != null) {
					throw new IllegalStateException("Children have been already flattened!");
				}
				Node nextNode = node.getPreviousSibling();
				root.removeChild(node);
				node = nextNode != null ? nextNode : root.getFirstChild();
			}
		}
		// join <lb> tags
		if (lbTagsInserted) {
			for (Node node1 = root.getFirstChild(); node1 != null; node1 = node1.getNextSibling()) {
				Node node2 = node1.getNextSibling();
				while (node2 != null && node1.getNodeName().equals("lb") && node2.getNodeName().equals("lb")) {
					root.removeChild(node2);
					node2 = node1.getNextSibling();
				}
			}
		}
		// merge adjacent WOJ nodes
		if (wojTagsInserted) {
			for (Node node1 = root.getFirstChild(); node1 != null; node1 = node1.getNextSibling()) {
				if (!(node1 instanceof Element))
					continue;
				Element elem = (Element) node1;
				if (node1.getNodeName().equals("woj") && !elem.getAttribute("eID").isEmpty()) {
					if (node1.getNextSibling().getNodeName().equals("woj")) {
						printWarning("WARNING: adjacent <q who=\"Jesus\"> tags merged");
						node1 = node1.getPreviousSibling();
						root.removeChild(node1.getNextSibling());
						root.removeChild(node1.getNextSibling());
					}
				}
			}
		}
		return wojTagsInserted;
	}

	protected void convertTitleVerseChapterFromMilestoned(Element root, boolean wojTagsInserted) {
		// unmilestone title, verse, chapter
		for (String tagName : Arrays.asList("title", "verse", "chapter")) {
			for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
				if (!(node instanceof Element) || !node.getNodeName().equals(tagName))
					continue;
				Element elem = (Element) node;
				String sID = elem.getAttribute("sID");
				if (sID.isEmpty())
					continue;
				boolean found = false;
				while (node.getNextSibling() != null) {
					Node n = node.getNextSibling();
					if (n instanceof Element && n.getNodeName().equals(tagName) && ((Element) n).getAttribute("eID").equals(sID)) {
						found = true;
						root.removeChild(n);
						break;
					}
					node.appendChild(n);
				}
				elem.removeAttribute("sID");
				if (!found)
					printWarning("WARNING: Unclosed " + tagName + " with sID=" + sID);
			}
		}
		// wrap woj around titles
		if (wojTagsInserted) {
			String inWOJ = null;
			for (Node node1 = root.getFirstChild(); node1 != null; node1 = node1.getNextSibling()) {
				if (!(node1 instanceof Element))
					continue;
				Element elem = (Element) node1;
				if (node1.getNodeName().equals("woj") && !elem.getAttribute("sID").isEmpty()) {
					inWOJ = elem.getAttribute("sID");
				} else if (node1.getNodeName().equals("woj") && !elem.getAttribute("eID").isEmpty()) {
					inWOJ = null;
				} else if (inWOJ != null && elem.getNodeName().equals("title")) {
					Element newElem = root.getOwnerDocument().createElement("woj");
					newElem.setAttribute("eID", inWOJ);
					root.insertBefore(newElem, elem);
					newElem = root.getOwnerDocument().createElement("woj");
					newElem.setAttribute("sID", inWOJ);
					root.insertBefore(newElem, elem.getNextSibling());
				}
			}
		}
	}

	private static final Set<String> REWRAPPABLE_MILESTONED_ELEMENTS = new HashSet<>(Arrays.asList("woj", "hi", "seg", "divineName", "transChange", "catchWord", "rdg"));
	private static final Set<String> FIXED_MILESTONED_ELEMENTS = new HashSet<>(Arrays.asList("note", "w", "reference", "variation"));

	protected void convertFromMilestoned(Element root, List<Element> unclosedElements) {
		// unmilestone all supported milestoned elements
		Element tempContainer = root.getOwnerDocument().createElement("tempContainer");
		Element targetContainer = tempContainer;
		for (Element unclosed : unclosedElements) {
			root.insertBefore(unclosed, root.getFirstChild());
		}
		unclosedElements.clear();
		for (Node node = root.getFirstChild(); node != null;) {
			Node nextNode = node.getNextSibling();
			targetContainer.appendChild(node);
			if (node instanceof Element) {
				Element elem = (Element) node;
				if (!elem.getAttribute("sID").isEmpty()) {
					if (!REWRAPPABLE_MILESTONED_ELEMENTS.contains(elem.getNodeName()) && !FIXED_MILESTONED_ELEMENTS.contains(elem.getNodeName())) {
						printWarning("WARNING: Ignoring unsupported milestoned element: " + elem.getNodeName());
					} else {
						targetContainer = elem;
					}
				} else if (!elem.getAttribute("eID").isEmpty()) {
					if (!REWRAPPABLE_MILESTONED_ELEMENTS.contains(elem.getNodeName()) && !FIXED_MILESTONED_ELEMENTS.contains(elem.getNodeName())) {
						printWarning("WARNING: Ignoring unsupported milestoned element: " + elem.getNodeName());
					} else {
						targetContainer.removeChild(elem);
						Element foundContainer = targetContainer;
						while (foundContainer != null) {
							if (foundContainer.getAttribute("sID").equals(elem.getAttribute("eID")) && foundContainer.getNodeName().equals(elem.getNodeName()))
								break;
							foundContainer = (Element) foundContainer.getParentNode();
						}
						if (foundContainer != null) {
							// close (and potentially reopen) parent elements
							while (targetContainer != foundContainer) {
								if (REWRAPPABLE_MILESTONED_ELEMENTS.contains(targetContainer.getNodeName())) {
									nextNode = root.insertBefore(targetContainer.cloneNode(false), nextNode);
								} else {
									printWarning("WARNING: Implicitly closed milestoned element: " + targetContainer.getNodeName() + "[" + targetContainer.getAttribute("sID") + "]");
								}
								targetContainer.removeAttribute("sID");
								targetContainer = (Element) targetContainer.getParentNode();
							}
							// finally close the found element
							targetContainer.removeAttribute("sID");
							targetContainer = (Element) targetContainer.getParentNode();
						} else {
							printWarning("WARNING: Ignoring unopened milestoned element: " + elem.getNodeName() + "[" + elem.getAttribute("eID") + "]");
						}
					}
				}
			}
			node = nextNode;
		}
		while (targetContainer != tempContainer) {
			if (REWRAPPABLE_MILESTONED_ELEMENTS.contains(targetContainer.getNodeName())) {
				unclosedElements.add((Element) targetContainer.cloneNode(false));
			} else {
				printWarning("WARNING: Implicitly closed milestoned element: " + targetContainer.getNodeName() + "[" + targetContainer.getAttribute("sID") + "]");
			}
			targetContainer.removeAttribute("sID");
			targetContainer = (Element) targetContainer.getParentNode();
		}
		if (root.getFirstChild() != null)
			throw new IllegalStateException();
		while (targetContainer.getFirstChild() != null) {
			root.appendChild(targetContainer.getFirstChild());
		}
	}

	private void parseBook(String bookName, Element osisBook, Book bibleBook) {
		warningContext = bookName;
		convertToMilestoned(osisBook);
		List<Element> unclosedElements = new ArrayList<Element>();
		for (Node node = osisBook.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Text) {
				if (((Text) node).getTextContent().trim().length() > 0)
					printWarning("WARNING: Non-whitespace text at book level");
			} else if (node instanceof Comment) {
				continue;
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
					if (chapterName.contains("-")) {
						chapterName = chapterName.substring(0, chapterName.indexOf("-"));
						printWarning("WARNING: Invalid chapter OSIS reference: " + elem.getAttribute("osisID") + ", using " + chapterName);
					}
					if (!chapterName.startsWith(bookName + ".")) {
						throw new IllegalStateException("Invalid chapter " + chapterName + " of book " + bookName);
					} else {
						int cnumber = Integer.parseInt(chapterName.substring(bookName.length() + 1));
						while (bibleBook.getChapters().size() < cnumber) {
							bibleBook.getChapters().add(new Chapter());
						}
						warningContext = chapterName;
						parseChapter(chapterName, elem, bibleBook.getChapters().get(cnumber - 1), unclosedElements);
						warningContext = bookName;
					}
				} else {
					printWarning("WARNING: invalid book level tag: " + elem.getNodeName());
				}
			}
		}
		if (unclosedElements.size() > 0) {
			StringBuilder message = new StringBuilder("WARNING: Unclosed milestoned elements:");
			for (Element elem : unclosedElements) {
				message.append(" " + elem.getNodeName() + "[" + elem.getAttribute("sID") + "]");
			}
			printWarning(message.toString());
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

	private void parseChapter(String chapterName, Element osisChapter, Chapter chapter, List<Element> unclosedElements) {
		String[] chapterNameParts = chapterName.split("\\.", 2);
		String nextChapterName = chapterNameParts[0] + "." + (Integer.parseInt(chapterNameParts[1]) + 1);
		String prevChapterName = chapterNameParts[0] + "." + (Integer.parseInt(chapterNameParts[1]) - 1);
		int lastVerse = -1;
		List<Headline> headlines = new ArrayList<Headline>();
		for (Node node = osisChapter.getFirstChild(); node != null; node = node.getNextSibling()) {
			boolean startProlog = false;
			if (node instanceof Text) {
				if (node.getTextContent().trim().length() == 0)
					continue;
				if (lastVerse == -1) {
					startProlog = true;
				} else {
					printWarning("WARNING: Non-whitespace at chapter level: " + node.getTextContent());
				}
			} else if (node instanceof Element) {
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
					convertFromMilestoned(elem, unclosedElements);
					parseFormattedText(null, elem, hl);
					if (hl.getElementTypes(1).length() == 0) {
						printWarning("WARNING: Empty headline in " + chapterName);
					} else {
						headlines.add(hl);
					}
				} else if (elem.getNodeName().equals("verse")) {
					String osisID = elem.getAttribute("osisID");
					if (!elem.getAttribute("sID").isEmpty() || !elem.getAttribute("sID").isEmpty())
						throw new IllegalArgumentException("verse should have been de-milestoned already.");
					if (osisID.isEmpty())
						throw new IllegalStateException("Verse without osisID");
					String vnumber, vprefix;
					if (osisID.startsWith(chapterName + ".")) {
						vnumber = osisID.substring(chapterName.length() + 1);
						vprefix = "";
					} else if (osisID.startsWith(nextChapterName + ".")) {
						vnumber = osisID.substring(nextChapterName.length() + 1);
						vprefix = nextChapterName.substring(chapterNameParts[0].length() + 1) + ",";
					} else if (osisID.startsWith(prevChapterName + ".")) {
						vnumber = osisID.substring(prevChapterName.length() + 1);
						vprefix = prevChapterName.substring(chapterNameParts[0].length() + 1) + ",";
					} else {
						throw new IllegalStateException("Invalid verse " + osisID + " in chapter " + chapterName);
					}
					if (osisID.contains(" ")) {
						vnumber = vnumber.substring(0, vnumber.indexOf(' '));
						lastVerse = Integer.parseInt(vnumber);
						int nextInRange = lastVerse + 1;
						boolean first = true;
						for (String part : osisID.split(" ")) {
							if (first) {
								first = false;
								continue;
							}
							if (!part.startsWith(chapterName + "."))
								throw new IllegalStateException("Invalid verse " + osisID + " in chapter " + chapterName);
							String partNumber = part.substring(chapterName.length() + 1);
							vnumber = vnumber + "." + partNumber;
							if (partNumber.equals("" + nextInRange)) {
								nextInRange++;
							} else {
								nextInRange = -1;
							}
						}
						if (nextInRange != -1) {
							vnumber = lastVerse + "-" + (nextInRange - 1);
						}
					} else {
						lastVerse = Integer.parseInt(vnumber);
					}
					Verse verse = new Verse(vprefix + vnumber);
					warningContext = osisID;
					for (Headline hl : headlines) {
						hl.accept(verse.getAppendVisitor().visitHeadline(hl.getDepth()));
					}
					headlines.clear();
					chapter.getVerses().add(verse);
					convertFromMilestoned(elem, unclosedElements);
					parseFormattedText(osisID, elem, verse);
					verse.trimWhitespace();
					verse.finished();
					if (verse.getElementTypes(1).length() == 0) {
						printWarning("WARNING: Empty verse " + osisID);
						chapter.getVerses().remove(verse);
					}
					warningContext += " (after closing)";
				} else if (lastVerse == -1) {
					startProlog = true;
				} else {
					printWarning("WARNING: " + elem.getNodeName() + " at invalid location");
				}
			}
			if (startProlog) {
				Element holder = osisChapter.getOwnerDocument().createElement("prolog");
				osisChapter.insertBefore(holder, node);
				while (holder.getNextSibling() != null && !holder.getNextSibling().getNodeName().equals("verse")) {
					holder.appendChild(holder.getNextSibling());
				}
				lastVerse = 0;
				FormattedText prolog = new FormattedText();
				chapter.setProlog(prolog);
				for (Headline hl : headlines) {
					hl.accept(prolog.getAppendVisitor().visitHeadline(hl.getDepth()));
				}
				headlines.clear();
				convertFromMilestoned(holder, unclosedElements);
				parseFormattedText(null, holder, prolog);
				prolog.trimWhitespace();
				prolog.finished();
				node = holder;
			}
		}
		if (headlines.size() > 0)
			printWarning("WARNING: Unused headlines: " + headlines.size());
	}

	protected void parseFormattedText(String verseName, Element root, FormattedText ft) {
		root.normalize();
		for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Text) {
				String text = node.getTextContent().replaceAll("[ \r\n\t]+", " ");
				if (text.startsWith(" ") && (node.getPreviousSibling() == null || Arrays.asList("brp", "lb", "title").contains(node.getPreviousSibling().getNodeName()))) {
					printWarning("WARNING: Whitespace at beginning of verse or after title/newline");
					text = text.substring(1);
				}
				Node ns = node.getNextSibling();
				while (ns != null && Arrays.asList("w", "q").contains(ns.getNodeName()) && ns.getFirstChild() == null)
					ns = ns.getNextSibling();
				if (text.endsWith(" ") && (ns == null || Arrays.asList("brp", "lb", "title").contains(ns.getNodeName()))) {
					printWarning("WARNING: Whitespace at end of verse or after title/newline");
					text = text.substring(0, text.length() - 1);
				}
				if (text.length() > 0)
					ft.getAppendVisitor().visitText(text);
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
						printWarning("WARNING: Empty headline in " + verseName);
					} else {
						hl.accept(ft.getAppendVisitor().visitHeadline(hl.getDepth()));
					}
				} else {
					parseStructuredTextElement(ft.getAppendVisitor(), elem);
				}
			}
		}
	}

	private void printWarning(String warning) {
		if (Boolean.getBoolean("biblemulticonverter.osis.warningcontext"))
			warning += " [" + warningContext + "]";
		if (printedWarnings.add(warning)) {
			System.out.println(warning);
		}
	}

	public void parseStructuredTextChildren(Visitor<RuntimeException> vv, Element textElem) {
		for (Node node = textElem.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Text) {
				String text = node.getTextContent().replaceAll("[ \t\r\n]+", " ");
				Node ns = node.getNextSibling();
				while (ns != null && Arrays.asList("w", "q").contains(ns.getNodeName()) && ns.getFirstChild() == null)
					ns = ns.getNextSibling();
				if (text.endsWith(" ") && ns != null && Arrays.asList("brp", "lb").contains(ns.getNodeName())) {
					printWarning("WARNING: Whitespace before newline");
					text = text.substring(0, text.length() - 1);
				} else if (text.endsWith(" ") && ns == null) {
					printWarning("WARNING: Whitespace at end of element");
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
			vv.visitLineBreak(ExtendedLineBreakKind.NEWLINE, 0);
		} else if (elem.getNodeName().equals("brp")) {
			vv.visitLineBreak(ExtendedLineBreakKind.PARAGRAPH, 0);
		} else if (elem.getNodeName().equals("divineName")) {
			parseStructuredTextChildren(vv.visitFormattingInstruction(FormattingInstructionKind.DIVINE_NAME), elem);
		} else if (elem.getNodeName().equals("woj")) {
			parseStructuredTextChildren(vv.visitFormattingInstruction(FormattingInstructionKind.WORDS_OF_JESUS), elem);
		} else if (elem.getNodeName().equals("catchWord")) {
			parseStructuredTextChildren(vv.visitCSSFormatting("osis-style: catchWord; font-style:italic;"), elem);
		} else if (elem.getNodeName().equals("hi")) {
			FormattingInstructionKind kind;
			if (elem.getAttribute("type").equals("italic")) {
				kind = FormattingInstructionKind.ITALIC;
			} else if (elem.getAttribute("type").equals("bold")) {
				kind = FormattingInstructionKind.BOLD;
			} else if (elem.getAttribute("type").equals("emphasis")) {
				kind = FormattingInstructionKind.ITALIC;
			} else {
				kind = null;
				printWarning("WARNING: Invalid hi type: " + elem.getAttribute("type"));
			}
			if (elem.getChildNodes().getLength() != 0) {
				Visitor<RuntimeException> vv1 = kind == null ? vv : vv.visitFormattingInstruction(kind);
				parseStructuredTextChildren(vv1, elem);
			}
		} else if (elem.getNodeName().equals("seg") || elem.getNodeName().equals("transChange") || elem.getNodeName().equals("rdg")) {
			String css;
			if (elem.getNodeName().equals("seg") && elem.getAttribute("type").equals("x-alternative")) {
				css = "osis-style: alternative; color: gray;";
			} else if (elem.getNodeName().equals("transChange") && elem.getAttribute("type").equals("added")) {
				css = "osis-style: added; font-style:italic;";
			} else if (elem.getNodeName().equals("transChange") && elem.getAttribute("type").equals("deleted")) {
				css = "osis-style: deleted; text-decoration: line-through; color: gray;";
			} else if (elem.getNodeName().equals("transChange") && elem.getAttribute("type").equals("amplified")) {
				css = "osis-style: amplified; font-style: italic;";
			} else if (elem.getNodeName().equals("transChange") && elem.getAttribute("type").isEmpty()) {
				css = "osis-style: trans-change;";
			} else if (elem.getNodeName().equals("rdg") && elem.getAttribute("type").equals("alternative")) {
				css = "osis-style: alternative-reading; color: gray;";
			} else if (elem.getNodeName().equals("rdg") && elem.getAttribute("type").equals("x-literal")) {
				css = "osis-style: literal-reading; color: gray;";
			} else if (elem.getNodeName().equals("rdg") && elem.getAttribute("type").equals("x-meaning")) {
				css = "osis-style: meaning-reading; color: gray;";
			} else if (elem.getNodeName().equals("rdg") && elem.getAttribute("type").equals("x-equivalent")) {
				css = "osis-style: equivalent-reading; color: gray;";
			} else if (elem.getNodeName().equals("rdg") && elem.getAttribute("type").equals("x-identity")) {
				css = "osis-style: identity-reading; color: gray;";
			} else if (elem.getNodeName().equals("rdg") && elem.getAttribute("type").isEmpty()) {
				css = "osis-style: reading; color: gray;";
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
				Visitor<RuntimeException> fn = vv.visitFootnote(false);
				fn.visitText(FormattedText.XREF_MARKER);
				if (elem.getFirstChild() != null && elem.getFirstChild().getNodeName().equals("reference")) {
					for (Node n = elem.getFirstChild(); n != null; n = n.getNextSibling()) {
						if (n instanceof Text) {
							fn.visitText(n.getTextContent());
							continue;
						}
						Element e = (Element) n;
						String[] ref = e.getAttribute("osisRef").split("\\.");
						if (ref.length != 3 || !ref[1].matches("[0-9]+")) {
							printWarning("WARNING: Invalid reference target: " + e.getAttribute("osisRef"));
							fn.visitText(e.getTextContent());
							continue;
						}
						BookID bookID = BookID.fromOsisId(ref[0]);
						int ch = Integer.parseInt(ref[1]);
						String vs = ref[2];
						String bookAbbr = ref[0];
						fn.visitCrossReference(bookAbbr, bookID, ch, vs, bookAbbr, bookID, ch, vs).visitText(e.getTextContent());
					}
				} else if (elem.getTextContent().length() > 0) {
					// OSIS converted from USFM contains a reference back to the verse itself
					for (Node n = elem.getFirstChild(); n != null; n = n.getNextSibling()) {
						if (n instanceof Element && n.getNodeName().equals("reference")) {
							if (((Element)n).getAttribute("osisRef").equals(((Element)elem).getAttribute("osisRef"))) {
								n = n.getPreviousSibling();
								n.getParentNode().removeChild(n.getNextSibling());
							}
						}
					}
					boolean first = true;
					for (String ref : elem.getTextContent().split("\\|")) {
						Matcher m = XREF_PATTERN.matcher(ref);
						if (!m.matches()) {
							ref = ref.trim();
							if (ref.startsWith("1 ") || ref.startsWith("2 ") || ref.startsWith("3 ")) {
								ref = ref.substring(0,1)+ref.substring(2);
							}
							m = XREF_PATTERN_2.matcher(ref);
							if (m.matches()) {
								try {
									BookID.fromOsisId(m.group(1));
								} catch (IllegalArgumentException ex) {
									BookID bk = null;
									for (BookID id : BookID.values()) {
										if (id.getThreeLetterCode().equalsIgnoreCase(m.group(1)))
											bk = id;
									}
									if (bk != null) {
										m = XREF_PATTERN_2.matcher(bk.getOsisID()+" "+m.group(2)+"."+m.group(3));
									} else {
										m = XREF_PATTERN_2.matcher("");
									}
								}
							}
						}
						if (!first)
							fn.visitText("; ");
						first = false;
						if (m.matches()) {
							String book = m.group(1);
							BookID bookID = BookID.fromOsisId(book);
							int ch = Integer.parseInt(m.group(2));
							String vs = m.group(3);
							fn.visitCrossReference(book, bookID, ch, vs, book, bookID, ch, vs).visitText(ref);
						} else {
							printWarning("WARNING: Malformed cross reference: " + ref);
							fn.visitText(ref.replaceAll("[\r\n\t ]+", " ").trim());
						}
					}
				} else {
					printWarning("WARNING: crossReference without content");
					fn.visitText("-");
				}
			} else if (elem.getFirstChild() != null) {
				Visitor<RuntimeException> v = vv.visitFootnote(false);
				parseStructuredTextChildren(v, elem);
			}
		} else if (elem.getNodeName().equals("w")) {
			if (elem.getFirstChild() == null)
				return; // skip empty w tags
			String src = elem.getAttribute("src");
			Visitor<RuntimeException> v = vv;
			List<String[]> grammarTags = new ArrayList<>();
			int[] strong = null, idx = null;
			char[] strongPfx = null, strongSfx = null;
			StringBuilder strongPrefixes = new StringBuilder();
			StringBuilder strongSuffixes = new StringBuilder();
			List<Integer> strongList = new ArrayList<Integer>();
			for (String lemma : elem.getAttribute("lemma").trim().split(" +")) {
				if (!lemma.startsWith("strong:G") && !lemma.startsWith("strong:H")) {
					grammarTags.add(new String[] {"lemma", lemma});
					continue;
				}
				String rawStrong = lemma.substring(8);
				String[] strs = rawStrong.split("-");
				char[] prefixSuffixHolder = new char[2];
				for (String str : strs) {
					int number = Utils.parseStrongs(lemma.charAt(7) + str, '\0', prefixSuffixHolder);
					if (number == -1) {
						printWarning("WARNING: Invalid strong dictionary entry: " + rawStrong);
						continue;
					}
					strongPrefixes.append(prefixSuffixHolder[0]);
					strongList.add(number);
					strongSuffixes.append(prefixSuffixHolder[1]);
				}
			}
			if (!strongList.isEmpty()) {
				strong = strongList.stream().mapToInt(s -> s).toArray();
				strongPfx = strongPrefixes.toString().toCharArray();
				strongSfx = strongSuffixes.toString().trim().isEmpty() ? null : strongSuffixes.toString().toCharArray();
			}
			List<String> rmac = new ArrayList<>();
			for (String morph : elem.getAttribute("morph").trim().split(" +")) {
				if (morph.startsWith("robinson:")) {
					String rmacCandidate = morph.substring(9);
					if (Utils.compilePattern(Utils.RMAC_REGEX).matcher(rmacCandidate).matches()) {
						rmac.add(rmacCandidate);
					} else {
						printWarning("WARNING: Invalid RMAC: " + rmacCandidate);
					}
				} else if (morph.startsWith("wivu:")) {
					String rmacCandidate = morph.substring(9);
					if (Utils.compilePattern(Utils.WIVU_REGEX).matcher(rmacCandidate).matches()) {
						rmac.add(rmacCandidate);
					} else {
						printWarning("WARNING: Invalid WIVU: " + rmacCandidate);
					}
				} else {
					grammarTags.add(new String[] {"morph", morph});
				}
			}
			if (src.matches("[0-9]{2}( [0-9]{2})*")) {
				String[] strs = src.split(" ");
				idx = new int[strs.length];
				for (int i = 0; i < strs.length; i++) {
					idx[i] = Integer.parseInt(strs[i]);
				}
			}
			boolean grammarXattr = Boolean.getBoolean("biblemulticonverter.osis.grammar.xattr");
			String[] attributeKeys = null, attributeValues = null;
			if (!grammarXattr) {
				List<String[]> attrPairs = new ArrayList<>();
				for(String[] grammarTag : grammarTags) {
					String[] parts = grammarTag[1].split(":", 2);
					if (parts.length == 2 && parts[0].matches("[a-z0-9-]+")) {
						String key = "osisgrammar:"+grammarTag[0]+":"+parts[0];
						if (key.equals("osisgrammar:lemma:lemma")) {
							key = "lemma";
						}
						attrPairs.add(new String[] {key, parts[1]});
					}
				}
				if (!attrPairs.isEmpty()) {
					attributeKeys = new String[attrPairs.size()];
					attributeValues = new String[attrPairs.size()];
					for (int i = 0; i < attributeKeys.length; i++) {
						attributeKeys[i] = attrPairs.get(i)[0];
						attributeValues[i] = attrPairs.get(i)[0];
					}
				}
			}
			if (strong == null && rmac.isEmpty() && idx == null && attributeKeys == null) {
				printWarning("INFO: Skipped <w> tag without any usable information");
			} else {
				v = v.visitGrammarInformation(strongPfx, strong, strongSfx, rmac.isEmpty() ? null : rmac.toArray(new String[rmac.size()]), null, idx, attributeKeys, attributeValues);
				if (grammarXattr) {
					for(String[] grammarTag : grammarTags) {
						String[] parts = grammarTag[1].split(":", 2);
						if (parts.length == 2 && parts[0].matches("[a-z0-9-]+")) {
							Visitor<RuntimeException> vc = v.visitExtraAttribute(ExtraAttributePriority.SKIP, "osisgrammar", grammarTag[0], parts[0]);
							vc.visitText(parts[1]);
							vc.visitEnd();
						}
					}
				}
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
					String bookAbbr = osisRefParts[0];
					BookID book = BookID.fromOsisId(osisRefParts[0]);
					v = v.visitCrossReference(bookAbbr, book, chapter, osisRefParts[2], bookAbbr, book, chapter, osisRefParts[2]);
				} catch (IllegalArgumentException ex) {
					printWarning("WARNING: " + ex.getMessage());
				}
			} else if (osisRef.matches("([A-Z0-9][A-Z0-9a-z]+)\\.[0-9]+\\.[0-9]+-\\1\\.[0-9]+\\.[0-9]+")) {
				String[] osisRefParts = osisRef.split("[.-]");
				int firstChapter = Integer.parseInt(osisRefParts[1]);
				int lastChapter = Integer.parseInt(osisRefParts[4]);
				try {
					String bookAbbr = osisRefParts[0];
					BookID book = BookID.fromOsisId(osisRefParts[0]);
					v = v.visitCrossReference(bookAbbr, book, firstChapter, osisRefParts[2], bookAbbr, book, lastChapter, osisRefParts[5]);
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

	private static final Set<String> GENERATED_MILESTONEABLE_ELEMENTS = new HashSet<>(Arrays.asList("chapter", "div", "q", "verse"));
	private static final Set<String> GENERATED_UNMILESTONEABLE_ELEMENTS = new HashSet<>(Arrays.asList("divineName", "header", "hi", "lb", "note", "osis", "osisText", "reference", "title", "w", "work"));

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
				for (VirtualVerse vv : chp.createVirtualVerses(false, false)) {
					String osisID = bk.getId().getOsisID() + "." + cnumber + "." + vv.getNumber();
					if (!vv.getHeadlines().isEmpty())
						throw new IllegalStateException();
					Element verse = doc.createElement("verse");
					chapter.appendChild(verse);
					verse.setAttribute("osisID", osisID);
					boolean firstVerse = true;
					for (Verse v : vv.getVerses()) {
						if (!firstVerse || !v.getNumber().equals("" + vv.getNumber())) {
							elem = doc.createElement("hi");
							verse.appendChild(elem);
							elem.setAttribute("type", "bold");
							elem.appendChild(doc.createTextNode("(" + v.getNumber() + ")"));
						}
						v.accept(new OSISVisitor(verse, bk.getId().isNT()));
						firstVerse = false;
					}
				}
			}
		}

		String milestonedElementNames = exportArgs.length > 1 ? exportArgs[1] : "verse";
		if (!milestonedElementNames.equals("-")) {
			Set<String> milestonedElements = new HashSet<>(Arrays.asList(milestonedElementNames.split(",")));
			Set<String> unsupportedMilestonedElements = new HashSet<>(milestonedElements);
			unsupportedMilestonedElements.removeAll(GENERATED_MILESTONEABLE_ELEMENTS);
			if (!unsupportedMilestonedElements.isEmpty()) {
				for (String elem : unsupportedMilestonedElements) {
					if (GENERATED_UNMILESTONEABLE_ELEMENTS.contains(elem)) {
						System.out.println("ERROR: " + elem + " may not be milestoned");
					} else {
						System.out.println("ERROR: " + elem + " is never generated by the OSIS export");
					}
				}
				throw new IllegalArgumentException("Cannot create milestoned elements: " + milestonedElementNames);
			}
			convertChildrenToMilestoned(doc.getDocumentElement(), milestonedElements);
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

	private void convertChildrenToMilestoned(Element parent, Set<String> milestonedElements) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (!(node instanceof Element))
				continue;
			Element elem = (Element) node;
			convertChildrenToMilestoned(elem, milestonedElements);
			if (milestonedElements.contains(node.getNodeName())) {
				String milestone = elem.getAttribute("osisID");
				if (milestone.isEmpty())
					milestone = "m" + (++milestoneIndex);
				elem.setAttribute("sID", milestone);
				Element endNode = parent.getOwnerDocument().createElement(node.getNodeName());
				endNode.setAttribute("eID", milestone);
				parent.insertBefore(endNode, node.getNextSibling());
				while (node.getFirstChild() != null) {
					parent.insertBefore(node.getFirstChild(), endNode);
				}
				node = endNode;
			}
		}
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return false;
	}

	private static class OSISVisitor extends AbstractNoCSSVisitor<RuntimeException> {
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
		public Visitor<RuntimeException> visitFootnote(boolean ofCrossReferences) throws RuntimeException {
			Visitor<RuntimeException> result = visitFootnote0();
			if (ofCrossReferences)
				result.visitText(FormattedText.XREF_MARKER);
			return result;
		}

		public Visitor<RuntimeException> visitFootnote0() throws RuntimeException {
			Element note = target.getOwnerDocument().createElement("note");
			target.appendChild(note);
			note.setAttribute("type", "x-footnote");
			return new OSISVisitor(note, nt);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) {
			if (firstBook == lastBook  && !lastVerse.equals("*")) {
				return visitCrossReference0(firstBookAbbr, firstBook, firstChapter, firstVerse, lastChapter, lastVerse);
			} else {
				return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "cross", "reference");
			}
		}

		public Visitor<RuntimeException> visitCrossReference0(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
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
			case PSALM_DESCRIPTIVE_TITLE:
				hiType = "italic";
				break;
			case ADDITION:
				hiType = "emphasis";
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
		protected Visitor<RuntimeException> visitChangedCSSFormatting(String remainingCSS, Visitor<RuntimeException> resultingVisitor, int replacements) {
			// not supported
			return resultingVisitor;
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			// probably better be handled with <lg>/<l>?
			visitText("/");
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind lbk, int indent) throws RuntimeException {
			Element lb = target.getOwnerDocument().createElement("lb");
			target.appendChild(lb);
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws RuntimeException {
			Element w = target.getOwnerDocument().createElement("w");
			target.appendChild(w);
			if (strongs != null) {
				StringBuilder lemma = new StringBuilder();
				for (int i = 0; i < strongs.length; i++) {
					if (lemma.length() > 0)
						lemma.append(' ');
					lemma.append("strong:" + Utils.formatStrongs(nt, i, strongsPrefixes, strongs, strongsSuffixes, ""));
				}
				w.setAttribute("lemma", lemma.toString());
			}

			if (rmac != null) {
				StringBuilder morph = new StringBuilder();
				for (String r : rmac) {
					if (morph.length() > 0)
						morph.append(' ');
					if (r.matches(Utils.RMAC_REGEX))
						morph.append("robinson:" + r);
					else if (r.matches(Utils.WIVU_REGEX))
						morph.append("wivu:" + r);
					else
						throw new IllegalStateException("Invalid morph: " + r);
				}
				w.setAttribute("morph", morph.toString());
			}

			if (sourceIndices != null) {
				StringBuilder src = new StringBuilder();
				for (int i = 0; i < sourceIndices.length; i++) {
					if (sourceVerses != null && sourceVerses[i] != null)
						continue;
					int idx = sourceIndices[i];
					if (src.length() > 0)
						src.append(' ');
					src.append(String.format("%02d", idx));
				}
				if (src.length() > 0)
					w.setAttribute("src", src.toString());
			}

			if (attributeKeys != null) {
				for(int i=0; i<attributeKeys.length; i++) {
					String fullKey = attributeKeys[i];
					if (fullKey.equals("lemma")) {
						fullKey = "osisgrammar:lemma:lemma";
					}
					if (fullKey.startsWith("osisgrammar:")) {
						String[] keyParts = fullKey.split(":", 3);
						String attrName = keyParts[1], valueKey = keyParts[2];
						String attr = target.getAttribute(attrName);
						if (!attr.isEmpty())
							attr += " ";
						target.setAttribute(attrName, attr + valueKey + ":" + attributeValues[i]);
					}
				}
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
		public Visitor<RuntimeException> visitSpeaker(String labelOrStrongs) {
			return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "speaker", labelOrStrongs);
		}

		@Override
		public Visitor<RuntimeException> visitHyperlink(HyperlinkType type, String target) {
			return visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "unsupported", "hyperlink", type.toString());
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			if (prio == ExtraAttributePriority.SKIP && category.equals("osisgrammar") && target.getNodeName().equals("w")) {
				StringBuilder valueBuilder = new StringBuilder();
				return new VisitorAdapter<RuntimeException>(null) {
					@Override
					public void visitText(String text) throws RuntimeException {
						valueBuilder.append(text);
					}

					protected void beforeVisit() throws RuntimeException {
						throw new IllegalStateException();
					}

					public boolean visitEnd() throws RuntimeException {
						String attr = target.getAttribute(key);
						if (!attr.isEmpty())
							attr += " ";
						target.setAttribute(key, attr + value + ":" + valueBuilder.toString());
						return false;
					}
				};
			}
			return prio.handleVisitor(category, this);
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			return false;
		}
	}
}
