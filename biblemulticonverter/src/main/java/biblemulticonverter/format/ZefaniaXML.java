package biblemulticonverter.format;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.MetadataBook.MetadataBookKey;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.schema.zef2005.BIBLEBOOK;
import biblemulticonverter.schema.zef2005.BR;
import biblemulticonverter.schema.zef2005.CAPTION;
import biblemulticonverter.schema.zef2005.CHAPTER;
import biblemulticonverter.schema.zef2005.DIV;
import biblemulticonverter.schema.zef2005.EnumBreak;
import biblemulticonverter.schema.zef2005.EnumCaptionType;
import biblemulticonverter.schema.zef2005.EnumModtyp;
import biblemulticonverter.schema.zef2005.EnumStatus;
import biblemulticonverter.schema.zef2005.GRAM;
import biblemulticonverter.schema.zef2005.NOTE;
import biblemulticonverter.schema.zef2005.ObjectFactory;
import biblemulticonverter.schema.zef2005.PROLOG;
import biblemulticonverter.schema.zef2005.REMARK;
import biblemulticonverter.schema.zef2005.STYLE;
import biblemulticonverter.schema.zef2005.VERS;
import biblemulticonverter.schema.zef2005.XMLBIBLE;
import biblemulticonverter.schema.zef2005.XREF;

/**
 * Importer and exporter for Zefania XML. This version may skip unknown features
 * during import; it is not guaranteed that exporting it again will yield the
 * original file.
 */
public class ZefaniaXML implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Zefania XML - well known bible format.",
			"",
			"This version should be able to import any Zefania XML file without errors; it may skip unknown features",
			"during import; it is not guaranteed that exporting it again will yield the original file.",
	};

	private static final Map<MetadataBookKey, Pattern> INFORMATION_FIELDS = new EnumMap<MetadataBookKey, Pattern>(MetadataBookKey.class);

	static {
		Pattern everything = Utils.compilePattern("(?s).*");
		INFORMATION_FIELDS.put(MetadataBookKey.source, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.identifier, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.type, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.publisher, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.date, Utils.compilePattern("[0-9]{4}-[0-9]{2}-[0-9]{2}"));
		INFORMATION_FIELDS.put(MetadataBookKey.coverage, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.creator, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.language, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.subject, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.contributors, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.description, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.title, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.rights, everything);
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Unmarshaller u = ctx.createUnmarshaller();
		XMLBIBLE doc = (XMLBIBLE) u.unmarshal(inputFile);
		return parseBible(doc);
	}

	protected Bible parseBible(XMLBIBLE doc) throws Exception {
		Bible result = new Bible(doc.getBiblename());
		MetadataBook metadata = new MetadataBook();
		if (doc.getStatus() != null) {
			metadata.setValue(MetadataBookKey.status, doc.getStatus().value());
		}
		if (doc.getVersion() != null) {
			metadata.setValue(MetadataBookKey.version, doc.getVersion());
		}
		if (doc.getRevision() != null) {
			metadata.setValue(MetadataBookKey.revision, doc.getRevision().toString());
		}
		for (JAXBElement<?> elem : doc.getINFORMATION().getTitleOrCreatorOrDescription()) {
			if (elem.getValue() == null)
				continue;
			String value = normalize(elem.getValue().toString(), true).trim();
			if (value.length() != 0)
				metadata.setValue(elem.getName().getLocalPart(), value);
		}
		if (metadata.getKeys().size() > 0)
			result.getBooks().add(metadata.getBook());
		Set<String> abbrs = new HashSet<String>();
		Set<String> shortnames = new HashSet<String>();
		Set<String> longnames = new HashSet<String>();
		Map<BookID, String> abbrMap = new EnumMap<BookID, String>(BookID.class);
		List<BIBLEBOOK> nl = doc.getBIBLEBOOK();
		for (BIBLEBOOK e : nl) {
			String shortname = e.getBsname();
			int number = e.getBnumber().intValue();
			BookID bookID;
			try {
				bookID = BookID.fromZefId(number);
			} catch (IllegalArgumentException ex) {
				continue;
			}
			if (shortname == null || shortname.length() == 0)
				shortname = "_" + bookID.getOsisID();
			String abbr = shortname.replaceAll("[^A-Z0-9a-zäöü]++", "");
			if (abbr.length() == 0 || Character.isLowerCase(abbr.charAt(0)))
				abbr = "X" + abbr;
			if (abbr.length() == 1)
				abbr += "x";
			if (abbrs.contains(abbr)) {
				for (int i = 2; i < 100; i++) {
					if (!abbrs.contains(abbr + i))
					{
						abbr = abbr + i;
						break;
					}
				}
			}
			abbrs.add(abbr);
			abbrMap.put(bookID, abbr);
		}
		abbrs.clear();
		EnumMap<BookID, Book> existingBooks = new EnumMap<BookID, Book>(BookID.class);

		for (BIBLEBOOK e : nl) {
			String shortname = e.getBsname();
			String longname = e.getBname();
			int number = e.getBnumber().intValue();
			BookID bookID;
			try {
				bookID = BookID.fromZefId(number);
			} catch (IllegalArgumentException ex) {
				System.out.println("WARNING: Skipping book with unknown id " + number);
				continue;
			}
			if (shortname == null || shortname.length() == 0)
				shortname = "_" + bookID.getOsisID();
			if (longname == null || longname.length() == 0)
				longname = "_" + bookID.getEnglishName();
			else
				longname = longname.replaceAll("  ++", " ").trim();
			String abbr = shortname.replaceAll("[^A-Z0-9a-zäöü]++", "");
			if (abbr.length() == 0 || Character.isLowerCase(abbr.charAt(0)))
				abbr = "X" + abbr;
			if (abbr.length() == 1)
				abbr += "x";
			if (abbrs.contains(abbr)) {
				for (int i = 2; i < 100; i++) {
					if (!abbrs.contains(abbr + i))
					{
						abbr = abbr + i;
						break;
					}
				}
			}
			abbrs.add(abbr);
			if (shortname.equals("Gen") && longname.equals("Genesis") && bookID == BookID.BOOK_Exod) {
				System.out.println("WARNING: Book number " + bookID.getZefID() + " has name " + longname);
				shortname = "Exo";
				longname = "Exodus";
			}
			if (shortname.equals("1Chr") && longname.equals("2 Chronicles")) {
				System.out.println("WARNING: Book name 2 Chronicles has short name 1Chr");
				shortname = "2Chr";
			}
			if (shortnames.contains(shortname)) {
				System.out.println("WARNING: Duplicate short name " + shortname);
				for (int i = 2; i < 100; i++) {
					if (!shortnames.contains(shortname + i))
					{
						shortname = shortname + i;
						break;
					}
				}
			}
			shortnames.add(shortname);
			if (longnames.contains(longname)) {
				System.out.println("WARNING: Duplicate long name " + shortname);
				for (int i = 2; i < 100; i++) {
					if (!longnames.contains(longname + i))
					{
						longname = longname + i;
						break;
					}
				}
			}
			longnames.add(longname);
			Book book = existingBooks.get(bookID);
			if (book == null) {
				book = new Book(abbr, bookID, shortname, longname);
				existingBooks.put(bookID, book);
				result.getBooks().add(book);
			}
			List<Headline> headlineBuffer = new ArrayList<Headline>();
			for (CHAPTER e2 : e.getCHAPTER()) {
				int chapterNumber = e2.getCnumber().intValue();
				while (book.getChapters().size() < chapterNumber)
					book.getChapters().add(new Chapter());
				Chapter chapter = book.getChapters().get(chapterNumber - 1);
				int existingVerses = chapter.getVerses().size();
				for (Object e3 : e2.getPROLOGOrCAPTIONOrVERS()) {
					if (e3 instanceof CAPTION) {
						CAPTION caption = (CAPTION) e3;
						int depth;
						if (caption.getType() == null) {
							depth = 9;
						} else {
							switch (caption.getType()) {
							case X_H_1:
								depth = 1;
								break;
							case X_H_2:
								depth = 2;
								break;
							case X_H_3:
								depth = 3;
								break;
							case X_H_4:
								depth = 4;
								break;
							case X_H_5:
								depth = 5;
								break;
							case X_H_6:
								depth = 6;
								break;
							default:
								depth = 9;
								break;
							}
						}
						int lastDepth = headlineBuffer.size() == 0 ? -1 : headlineBuffer.get(headlineBuffer.size() - 1).getDepth();
						if (depth <= lastDepth)
							depth = lastDepth == 9 ? 9 : lastDepth + 1;
						Headline h = new Headline(depth);
						if (parseContent(h.getAppendVisitor(), caption.getContent(), abbrMap)) {
							h.trimWhitespace();
							h.finished();
							headlineBuffer.add(h);
						}
					} else if (e3 instanceof REMARK) {
						REMARK remark = (REMARK) e3;
						int vref = remark.getVref().intValue();
						int idx = chapter.getVerseIndex("" + vref);
						if (idx == -1)
							continue;
						Verse v = chapter.getVerses().get(idx);
						if (remark.getContent().size() != 1)
							continue;
						String remarkText = normalize((String) remark.getContent().get(0), true).trim();
						v.getAppendVisitor().visitFootnote().visitText(remarkText);
					} else if (e3 instanceof XREF) {
						XREF xref = (XREF) e3;
						int vref = xref.getVref().intValue();
						int idx = chapter.getVerseIndex("" + vref);
						if (idx == -1)
							continue;
						Verse v = chapter.getVerses().get(idx);
						Visitor<RuntimeException> footnoteVisitor = v.getAppendVisitor().visitFootnote();
						boolean first = true;
						for (String mscope : xref.getMscope().split(" ")) {
							Matcher m = Utils.compilePattern("([0-9]+);([0-9]+)(-[0-9]+)?;([0-9]+)(-[0-9]+)?").matcher(mscope);
							if (!m.matches())
								continue;
							BookID xrefBookID = BookID.fromZefId(Integer.parseInt(m.group(1)));
							int xrefChapter = Integer.parseInt(m.group(2)), endChapter = xrefChapter;
							if (m.group(3) != null)
								endChapter = Integer.parseInt(m.group(3).substring(1));
							String verse = m.group(4);
							String endVerse = m.group(5);
							if (endVerse == null)
								endVerse = verse;
							else
								endVerse = endVerse.substring(1);
							if (verse.equals("0") || endVerse.equals("0"))
								continue;
							if (xrefChapter == endChapter && Integer.parseInt(verse) > Integer.parseInt(endVerse))
								continue;
							String xrefAbbr = abbrMap.get(xrefBookID);
							if (xrefAbbr == null)
								xrefAbbr = xrefBookID.getOsisID();
							if (first)
								first = false;
							else
								footnoteVisitor.visitText(" ");
							footnoteVisitor.visitCrossReference(xrefAbbr, xrefBookID, xrefChapter, verse, endChapter, endVerse).visitText(xrefAbbr + " " + xrefChapter + ":" + verse);
						}
						if (first)
							visitEmptyMarker(footnoteVisitor);
					} else if (e3 instanceof PROLOG) {
						PROLOG prolog = (PROLOG) e3;
						if (chapter.getProlog() != null)
							continue;
						FormattedText prologText = new FormattedText();
						if (parseContent(prologText.getAppendVisitor(), prolog.getContent(), abbrMap)) {
							prologText.trimWhitespace();
							prologText.finished();
							chapter.setProlog(prologText);
						}
					} else if (e3 instanceof VERS) {
						VERS vers = (VERS) e3;
						int vnumber = vers.getVnumber().intValue();
						if (vnumber == 0)
							vnumber = chapter.getVerses().size() + 1;
						String verseNumber = vnumber + (vers.getAix() == null ? "" : vers.getAix());
						while (chapter.getVerseIndex(verseNumber) != -1) {
							String infix = "";
							for (char ch = 'a'; ch <= 'z'; ch++) {
								if (chapter.getVerseIndex(vnumber + infix + ch) == -1) {
									verseNumber = vnumber + infix + ch;
									break;
								}
							}
							infix += "-";
						}
						Verse verse = new Verse(verseNumber);
						Visitor<RuntimeException> visitor = verse.getAppendVisitor();
						boolean contentFound = false;
						if (headlineBuffer.size() > 0) {
							for (Headline h : headlineBuffer) {
								h.accept(visitor.visitHeadline(h.getDepth()));
							}
							headlineBuffer.clear();
							contentFound = true;
						}
						contentFound |= parseContent(visitor, vers.getContent(), abbrMap);
						if (contentFound) {
							verse.trimWhitespace();
							chapter.getVerses().add(verse);
						}
					}
				}
				for (Verse v : chapter.getVerses()) {
					if (existingVerses > 0) {
						existingVerses--;
						continue;
					}
					v.finished();
				}
			}
		}

		return result;
	}

	private boolean parseContent(Visitor<RuntimeException> visitor, List<Object> contentList, Map<BookID, String> abbrMap) throws IOException {
		boolean contentFound = false;
		for (Object n : contentList) {
			if (n instanceof String) {
				String value = normalize((String) n, false);
				visitor.visitText(value);
				contentFound |= value.trim().length() > 0;
			} else if (n instanceof DIV || n instanceof NOTE) {
				NOTE note;
				if (n instanceof DIV) {
					note = ((DIV) n).getNOTE();
				} else {
					note = (NOTE) n;
				}
				if (note.getContent().size() == 0)
					continue;
				Visitor<RuntimeException> v;
				v = visitor.visitFootnote();
				boolean subContentFound = parseContent(v, note.getContent(), abbrMap);
				if (!subContentFound)
					visitEmptyMarker(v);
				contentFound = true;
			} else if (n instanceof BR) {
				BR br = (BR) n;
				Visitor<RuntimeException> v = visitor;
				int count = 1;
				if (br.getCount() != null) {
					count = br.getCount().intValue();
				}
				if (count < 1 || count > 10)
					count = 1;
				for (int ii = 0; ii < count; ii++) {
					if (br.getArt() == EnumBreak.X_P)
						v.visitLineBreak(LineBreakKind.PARAGRAPH);
					else
						v.visitLineBreak(LineBreakKind.NEWLINE);
				}
				contentFound = true;
			} else if (n instanceof XREF) {
				XREF xref = (XREF) n;
				Visitor<RuntimeException> footnoteVisitor = visitor.visitFootnote();
				footnoteVisitor.visitText(FormattedText.XREF_MARKER.trim());
				boolean first = true;
				if (xref.getMscope() == null) {
					if (xref.getFscope() == null) {
						System.out.println("WARNING: Ignoring XREF with neither fscope nor mscope");
					} else {
						for (String fscope : xref.getFscope().split("; ")) {
							Matcher m = Utils.compilePattern("([0-9A-Za-z]+) ([0-9]+), ([0-9]+[a-z]?)").matcher(fscope);
							if (!m.matches()) {
								System.out.println("WARNING: Unable to parse XREF fscope " + fscope + ", skipping");
								continue;
							}
							String xBook = m.group(1);
							int xChapter = Integer.parseInt(m.group(2));
							String xVerse = m.group(3);
							BookID xID = null;
							for (Map.Entry<BookID, String> abbrEntry : abbrMap.entrySet()) {
								if (abbrEntry.getValue().equals(xBook)) {
									xID = abbrEntry.getKey();
									break;
								}
							}
							if (xID == null) {
								System.out.println("WARNING: Book not found for XREF fscope " + fscope + ", skipping");
								continue;
							}
							footnoteVisitor.visitText(" ");
							footnoteVisitor.visitCrossReference(xBook, xID, xChapter, xVerse, xChapter, xVerse).visitText(xBook + " " + xChapter + ":" + xVerse);
						}
					}
				} else {
					for (String mscope : xref.getMscope().split(" ")) {
						Matcher m = Utils.compilePattern("([0-9]+);([0-9]+)(-[0-9]+)?;([0-9]+)(-[0-9]+)?").matcher(mscope);
						if (!m.matches())
							continue;
						BookID bookID = BookID.fromZefId(Integer.parseInt(m.group(1)));
						int chapter = Integer.parseInt(m.group(2)), endChapter = chapter;
						if (m.group(3) != null)
							endChapter = Integer.parseInt(m.group(3).substring(1));
						String verse = m.group(4);
						String endVerse = m.group(5);
						if (endVerse == null)
							endVerse = verse;
						else
							endVerse = endVerse.substring(1);
						if (verse.equals("0") || endVerse.equals("0"))
							continue;
						if (chapter == endChapter && Integer.parseInt(verse) > Integer.parseInt(endVerse))
							continue;
						String abbr = abbrMap.get(bookID);
						if (abbr == null)
							abbr = bookID.getOsisID();
						if (first)
							first = false;
						else
							footnoteVisitor.visitText(" ");
						footnoteVisitor.visitCrossReference(abbr, bookID, chapter, verse, endChapter, endVerse).visitText(abbr + " " + chapter + ":" + verse);
					}
				}
				if (first)
					visitEmptyMarker(footnoteVisitor);
				contentFound = true;
			} else if (n instanceof JAXBElement<?>) {
				String name = ((JAXBElement<?>) n).getName().toString();
				Object nn = ((JAXBElement<?>) n).getValue();
				if (name.equals("STYLE") && nn instanceof STYLE) {
					String css = ((STYLE) nn).getCss();
					String id = ((STYLE) nn).getId();
					FormattingInstructionKind kind = null;
					if (id != null && id.equals("cl:divineName")) {
						kind = FormattingInstructionKind.DIVINE_NAME;
					} else if (css == null || css.startsWith("display:block;")) {
						kind = null;
					} else if (css.contains("italic")) {
						kind = FormattingInstructionKind.ITALIC;
					} else if (css.contains("bold")) {
						kind = FormattingInstructionKind.BOLD;
					} else if (css.toLowerCase().contains("color:#ff0000")) {
						kind = FormattingInstructionKind.WORDS_OF_JESUS;
					} else if (css.contains("color:blue")) {
						kind = FormattingInstructionKind.LINK;
					} else if (css.contains("vertical-align:super") || css.equals("font-size:small")) {
						kind = FormattingInstructionKind.SUPERSCRIPT;
					}
					Visitor<RuntimeException> contentVisitor = visitor;
					if (kind != null) {
						contentVisitor = contentVisitor.visitFormattingInstruction(kind);
					} else if (css != null && (kind == null || !kind.getCss().equals(css))) {
						contentVisitor = contentVisitor.visitCSSFormatting(css);
					}
					List<Object> content = ((STYLE) nn).getContent();
					boolean subContentFound = parseContent(contentVisitor, content, abbrMap);
					if (!subContentFound)
						visitEmptyMarker(contentVisitor);
				} else if ((name.equals("gr") || name.equals("GRAM")) && nn instanceof GRAM) {
					GRAM gram = (GRAM) nn;
					boolean addSpace = false;
					int lastIndex = gram.getContent().size() - 1;
					if (lastIndex >= 0 && gram.getContent().get(lastIndex) instanceof String) {
						String lastString = normalize((String) gram.getContent().get(lastIndex), false);
						if (lastString.endsWith(" ")) {
							String afterString = "";
							int pos = contentList.indexOf(n);
							if (pos < contentList.size() - 1 && contentList.get(pos + 1) instanceof String) {
								afterString = normalize((String) contentList.get(pos + 1), false);
							}
							if (!afterString.startsWith(" ")) {
								addSpace = true;
								gram.getContent().set(lastIndex, lastString.substring(0, lastString.length() - 1));
							}
						}
					}
					Visitor<RuntimeException> strongVisitor = visitor;
					int[] strongs = null;
					if (gram.getStr() != null) {
						List<String> strongList = new ArrayList<String>(Arrays.asList(gram.getStr().trim().replaceAll(" ++", " ").replace("G", "").replace("H", "").split(" ")));
						for (int i = 0; i < strongList.size(); i++) {
							if (!strongList.get(i).matches("[0-9]+")) {
								System.out.println("WARNING: Skipping invalid Strong number " + strongList.get(i));
								strongList.remove(i);
								i--;
							}
						}
						strongs = new int[strongList.size()];
						for (int i = 0; i < strongs.length; i++) {
							strongs[i] = Integer.parseInt(strongList.get(i));
						}
					}
					String[] rmac = null;
					if (gram.getRmac() != null && gram.getRmac().length() > 0) {
						List<String> rmacList = new ArrayList<String>(Arrays.asList(gram.getRmac().toUpperCase().split(" ")));
						for (int i = 0; i < rmacList.size(); i++) {
							String rmacValue = rmacList.get(i);
							if (rmacValue.endsWith("-"))
								rmacValue = rmacValue.substring(0, rmacValue.length() - 1);
							rmacList.set(i, rmacValue);
							if (!rmacValue.matches(Utils.RMAC_REGEX)) {
								System.out.println("WARNING: Skipping invalid RMAC: " + rmacValue);
								rmacList.remove(i);
								i--;
							}
							rmac = (String[]) rmacList.toArray(new String[rmacList.size()]);
						}
					}
					if (strongs != null && strongs.length == 0)
						strongs = null;
					if (rmac != null && rmac.length == 0)
						rmac = null;
					else if (rmac != null && strongs == null) {
						System.out.println("WARNING: Stripping RMAC because we don't have strongs: " + Arrays.toString(rmac));
					} else if (rmac != null && strongs != null && strongs.length != rmac.length) {
						System.out.println("WARNING: Stripping RMAC because RMAC length is different from Strongs length");
						rmac = null;
					}
					if (strongs != null)
						strongVisitor = strongVisitor.visitGrammarInformation(strongs, rmac, null);
					if (!parseContent(strongVisitor, gram.getContent(), abbrMap) && strongVisitor != visitor) {
						visitEmptyMarker(strongVisitor);
					}
					if (addSpace)
						visitor.visitText(" ");
				} else {
					continue;
				}
				contentFound = true;
			}
		}
		return contentFound;
	}

	private void visitEmptyMarker(Visitor<RuntimeException> v) {
		v.visitExtraAttribute(ExtraAttributePriority.SKIP, "zefania", "empty", "true");
	}

	private static String normalize(String str, boolean keepNL) {
		str = str.replace('\t', ' ');
		if (keepNL)
			str = str.replaceAll(" *+\r?\n *+", "\n").replaceAll("\n\n++", "\n");
		else
			str = str.replace('\n', ' ').replace('\r', ' ');
		return str.replaceAll("  ++", " ");
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File file = new File(exportArgs[0]);
		XMLBIBLE xmlbible = createXMLBible(bible);
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Marshaller m = ctx.createMarshaller();
		m.setSchema(getSchema());
		m.marshal(xmlbible, doc);
		doc.getDocumentElement().setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		doc.getDocumentElement().setAttribute("xsi:noNamespaceSchemaLocation", "zef2005.xsd");
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		transformer.transform(new DOMSource(doc), new StreamResult(file));
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/zef2005.xsd"));
	}

	protected XMLBIBLE createXMLBible(Bible bible) throws Exception {
		ObjectFactory of = new ObjectFactory();
		XMLBIBLE doc = of.createXMLBIBLE();
		doc.setBiblename(bible.getName());
		doc.setType(EnumModtyp.X_BIBLE);
		doc.setINFORMATION(of.createINFORMATION());
		MetadataBook metadata = bible.getMetadataBook();
		if (metadata != null) {
			for (String key : metadata.getKeys()) {
				String value = metadata.getValue(key);
				if (key.equals(MetadataBookKey.status.toString())) {
					doc.setStatus(EnumStatus.fromValue(value));
				} else if (key.equals(MetadataBookKey.version.toString())) {
					doc.setVersion(value);
				} else if (key.equals(MetadataBookKey.revision.toString())) {
					doc.setRevision(new BigInteger(value));
				} else if (!key.contains("@")) {
					Pattern regex = INFORMATION_FIELDS.get(MetadataBookKey.valueOf(key));
					if (regex != null && regex.matcher(value).matches())
						doc.getINFORMATION().getTitleOrCreatorOrDescription().add(new JAXBElement<String>(new QName(key), String.class, value));
				}
			}
		}
		doc.getINFORMATION().getTitleOrCreatorOrDescription().add(new JAXBElement<String>(new QName("format"), String.class, "Zefania XML Bible Markup Language"));

		for (Book bk : bible.getBooks()) {
			if (bk.getId().equals(BookID.METADATA))
				continue;
			if (bk.getId().getZefID() <= 0) {
				System.out.println("WARNING: Unable to export book " + bk.getAbbr());
				continue;
			}
			BIBLEBOOK bb = of.createBIBLEBOOK();
			bb.setBnumber(BigInteger.valueOf(bk.getId().getZefID()));
			bb.setBsname(bk.getShortName());
			bb.setBname(bk.getLongName());

			int cnumber = 0;
			for (Chapter ccc : bk.getChapters()) {
				cnumber++;
				if (ccc.getVerses().size() == 0)
					continue;
				CHAPTER cc = of.createCHAPTER();
				cc.setCnumber(BigInteger.valueOf(cnumber));
				bb.getCHAPTER().add(cc);

				if (ccc.getProlog() != null) {
					PROLOG prolog = of.createPROLOG();
					prolog.setVref(BigInteger.ONE);
					ccc.getProlog().accept(new CreateContentVisitor(of, prolog.getContent(), null));
					cc.getPROLOGOrCAPTIONOrVERS().add(prolog);
				}

				for (VirtualVerse vv : ccc.createVirtualVerses()) {
					for (Headline h : vv.getHeadlines()) {
						CAPTION caption = of.createCAPTION();
						caption.setVref(BigInteger.valueOf(vv.getNumber()));
						h.accept(new CreateContentVisitor(of, caption.getContent(), null));
						EnumCaptionType[] types = new EnumCaptionType[] {
								null,
								EnumCaptionType.X_H_1, EnumCaptionType.X_H_2, EnumCaptionType.X_H_3,
								EnumCaptionType.X_H_4, EnumCaptionType.X_H_5, EnumCaptionType.X_H_6,
								null, null, null
						};
						caption.setType(types[h.getDepth()]);
						cc.getPROLOGOrCAPTIONOrVERS().add(caption);
					}
					VERS vers = of.createVERS();
					vers.setVnumber(BigInteger.valueOf(vv.getNumber()));
					for (Verse v : vv.getVerses()) {
						if (!v.getNumber().equals("" + vv.getNumber())) {
							STYLE verseNum = of.createSTYLE();
							verseNum.setCss("font-weight: bold");
							verseNum.getContent().add("(" + v.getNumber() + ")");
							vers.getContent().add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, verseNum));
							vers.getContent().add(" ");
						}
						v.accept(new CreateContentVisitor(of, vers.getContent(), vers));
					}
					cc.getPROLOGOrCAPTIONOrVERS().add(vers);
				}
			}
			doc.getBIBLEBOOK().add(bb);
		}
		return doc;
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return false;
	}

	private static class CreateContentVisitor implements Visitor<IOException> {

		private List<Object> result;
		private final ObjectFactory of;
		private final VERS containingVerse;

		private CreateContentVisitor(ObjectFactory of, List<Object> result, VERS containingVerse) {
			this.of = of;
			this.result = result;
			this.containingVerse = containingVerse;
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			STYLE style = of.createSTYLE();
			style.setCss("color:gray");
			style.getContent().add("/");
			result.add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, style));
		}

		@Override
		public void visitText(String text) throws IOException {
			result.add(text);
		}

		@Override
		public void visitStart() throws IOException {
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws IOException {
			BR br = of.createBR();
			if (kind == LineBreakKind.PARAGRAPH)
				br.setArt(EnumBreak.X_P);
			else
				br.setArt(EnumBreak.X_NL);
			result.add(br);
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			return 0;
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			System.out.println("WARNING: Skipping headline where no headlines allowed");
			return null;
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			STYLE style = of.createSTYLE();
			result.add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, style));
			if (kind == FormattingInstructionKind.DIVINE_NAME) {
				style.setId("cl:divineName");
			} else {
				style.setCss(kind.getCss());
			}
			return new CreateContentVisitor(of, style.getContent(), containingVerse);
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			if (containingVerse == null) {
				System.out.println("WARNING: Skipping footnote outside of verse");
				return null;
			}
			NOTE note = of.createNOTE();
			note.setType("x-studynote");
			if (containingVerse.getContent() == result) {
				result.add(note);
			} else {
				List<Object> currResult = containingVerse.getContent();
				List<Object> appendResult = currResult;
				while (currResult != result) {
					STYLE style = (STYLE) ((JAXBElement<?>) currResult.get(currResult.size() - 1)).getValue();
					STYLE newStyle = of.createSTYLE();
					newStyle.setId(style.getId());
					newStyle.setCss(style.getCss());
					if (appendResult == currResult)
						currResult.add(note);
					appendResult.add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, newStyle));
					currResult = style.getContent();
					appendResult = newStyle.getContent();
				}
				result = appendResult;
			}
			return new CreateContentVisitor(of, note.getContent(), null);
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			return prio.handleVisitor(category, this);
		}

		@Override
		public boolean visitEnd() throws IOException {
			return false;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			return this;
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			final GRAM gram = of.createGRAM();
			result.add(new JAXBElement<GRAM>(new QName("gr"), GRAM.class, gram));
			Visitor<IOException> nextVisitor = new CreateContentVisitor(of, gram.getContent(), null);
			if (strongs != null) {
				StringBuilder entryBuilder = new StringBuilder();
				for (int i = 0; i < strongs.length; i++) {
					entryBuilder.append((i > 0 ? " " : "") + strongs[i]);
				}
				String entry = entryBuilder.toString();
				gram.setStr(entry);
			}
			if (rmac != null) {
				StringBuilder entryBuilder = new StringBuilder();
				for (int i = 0; i < rmac.length; i++) {
					if (i > 0)
						entryBuilder.append(' ');
					entryBuilder.append(rmac[i]);
				}
				gram.setRmac(entryBuilder.toString());
			}
			return nextVisitor;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			try {
				Integer.parseInt(firstVerse);
				Integer.parseInt(lastVerse);
			} catch (NumberFormatException ex) {
				System.out.println("WARNING: Skipping xref of non-numeric verse numbers: " + firstVerse + "-" + lastVerse);
				return null;
			}
			XREF xref = of.createXREF();
			result.add(xref);
			String chap = firstChapter + (firstChapter == lastChapter ? "" : "-" + lastChapter);
			String verse = firstVerse + (firstVerse.equals(lastVerse) ? "" : "-" + lastVerse);
			xref.setMscope(book.getZefID() + ";" + chap + ";" + verse + " ");
			return null;
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			STYLE style = of.createSTYLE();
			result.add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, style));
			style.setCss(css);
			return new CreateContentVisitor(of, style.getContent(), containingVerse);
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			return this;
		}
	}
}
