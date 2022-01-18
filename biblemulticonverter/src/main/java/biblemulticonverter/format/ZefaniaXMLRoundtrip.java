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
import biblemulticonverter.data.FormattedText.VisitorAdapter;
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
import biblemulticonverter.tools.ValidateXML;

/**
 * Importer and exporter for Zefania XML. This version will reject Zefania XML
 * files which cannot be exported to equivalent XML later.
 */
public class ZefaniaXMLRoundtrip implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Zefania XML - well known bible format (Roundtrip converter).",
			"",
			"Usage (export): ZefaniaXMLRoundtrip <OutputFile>",
			"",
			"This version will reject Zefania XML files which cannot be exported to equivalent XML later.",
	};

	private static final String[] INFORMATION_KEYS = {
			MetadataBookKey.source.toString(), MetadataBookKey.identifier.toString(), MetadataBookKey.type.toString(),
			MetadataBookKey.publisher.toString(), MetadataBookKey.date.toString(), MetadataBookKey.coverage.toString(),
			MetadataBookKey.format.toString(), MetadataBookKey.creator.toString(), MetadataBookKey.language.toString(),
			MetadataBookKey.subject.toString(), MetadataBookKey.contributors.toString(), MetadataBookKey.description.toString(),
			MetadataBookKey.title.toString(), MetadataBookKey.rights.toString()
	};

	@Override
	public Bible doImport(File inputFile) throws Exception {
		ValidateXML.validateFileBeforeParsing(getSchema(), inputFile);
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Unmarshaller u = ctx.createUnmarshaller();
		u.setSchema(getSchema());
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
			if (value.length() == 0)
				value = "-empty-";
			metadata.setValue(elem.getName().getLocalPart(), value);
		}
		metadata.finished();
		if (metadata.getKeys().size() > 0)
			result.getBooks().add(metadata.getBook());
		Set<String> abbrs = new HashSet<String>();
		Set<String> shortnames = new HashSet<String>();
		Map<BookID, String> abbrMap = new EnumMap<BookID, String>(BookID.class);
		List<BIBLEBOOK> nl = doc.getBIBLEBOOK();
		for (BIBLEBOOK e : nl) {
			String shortname = e.getBsname();
			int number = e.getBnumber().intValue();
			BookID bookID = BookID.fromZefId(number);
			if (shortname == null)
				shortname = "_" + bookID.getOsisID();
			else if (shortname.length() == 0)
				shortname = "_" + bookID.getOsisID() + "[[]]";
			String abbr = shortname.replaceAll("[^A-Z0-9a-zäöü]++", "");
			if (abbr.length() == 0 || Character.isLowerCase(abbr.charAt(0)))
				abbr = "X" + abbr;
			if (abbr.length() == 1)
				abbr += "x";
			if (abbrs.contains(abbr)) {
				for (int i = 2; i < 100; i++) {
					if (!abbrs.contains(abbr + i)) {
						abbr = abbr + i;
						break;
					}
				}
			}
			abbrs.add(abbr);
			abbrMap.put(bookID, abbr);
		}
		abbrs.clear();

		for (BIBLEBOOK e : nl) {
			String shortname = e.getBsname();
			String longname = e.getBname();
			int number = e.getBnumber().intValue();
			BookID bookID = BookID.fromZefId(number);
			if (shortname == null)
				shortname = "_" + bookID.getOsisID();
			else if (shortname.length() == 0)
				shortname = "_" + bookID.getOsisID() + "[[]]";
			if (longname == null)
				longname = "_" + bookID.getEnglishName();
			else if (longname.length() == 0)
				longname = "_" + bookID.getEnglishName() + "[[]]";
			else
				longname = longname.replaceAll("  ++", " ").trim();
			String abbr = shortname.replaceAll("[^A-Z0-9a-zäöü]++", "");
			if (abbr.length() == 0 || Character.isLowerCase(abbr.charAt(0)))
				abbr = "X" + abbr;
			if (abbr.length() == 1)
				abbr += "x";
			if (abbrs.contains(abbr)) {
				for (int i = 2; i < 100; i++) {
					if (!abbrs.contains(abbr + i)) {
						abbr = abbr + i;
						break;
					}
				}
			}
			abbrs.add(abbr);
			if (shortname.equals("Gen") && longname.equals("Genesis") && bookID == BookID.BOOK_Exod) {
				System.out.println("WARNING: Book number " + bookID.getZefID() + " has name " + longname);
				shortname = "Exo[[Gen]]";
				longname = "Exodus[[Genesis]]";
			}
			if (shortname.equals("1Chr") && longname.equals("2 Chronicles")) {
				System.out.println("WARNING: Book name 2 Chronicles has short name 1Chr");
				shortname = "2Chr[[1Chr]]";
			}
			if (shortnames.contains(shortname)) {
				System.out.println("WARNING: Duplicate short name " + shortname);
				for (int i = 2; i < 100; i++) {
					if (!shortnames.contains(shortname + i + "[[" + shortname + "]]")) {
						shortname = shortname + i + "[[" + shortname + "]]";
						break;
					}
				}
			}
			shortnames.add(shortname);
			Book book = new Book(abbr, bookID, shortname, longname);

			int lastvref = -1;
			List<Headline> headlineBuffer = new ArrayList<Headline>();
			for (CHAPTER e2 : e.getCHAPTER()) {
				int chapterNumber = e2.getCnumber().intValue();
				while (book.getChapters().size() < chapterNumber)
					book.getChapters().add(new Chapter());
				Chapter chapter = book.getChapters().get(chapterNumber - 1);
				for (Object e3 : e2.getPROLOGOrCAPTIONOrVERS()) {
					if (e3 instanceof CAPTION) {
						CAPTION caption = (CAPTION) e3;

						if (lastvref != -1 && lastvref != caption.getVref().intValue())
							throw new IOException();
						lastvref = caption.getVref().intValue();
						int level;
						if (caption.getType() == null) {
							level = 9;
						} else {
							switch (caption.getType()) {
							case X_H_1:
								level = 1;
								break;
							case X_H_2:
								level = 2;
								break;
							case X_H_3:
								level = 3;
								break;
							case X_H_4:
								level = 4;
								break;
							case X_H_5:
								level = 5;
								break;
							case X_H_6:
								level = 6;
								break;
							default:
								throw new IOException();
							}
						}
						Headline h = new Headline(level);
						headlineBuffer.add(h);
						if (!parseContent(h.getAppendVisitor(), caption.getContent(), abbrMap)) {
							visitEmptyMarker(h.getAppendVisitor());
						} else {
							h.trimWhitespace();
						}
						h.finished();
					} else if (e3 instanceof REMARK) {
						REMARK remark = (REMARK) e3;
						int vref = remark.getVref().intValue();
						int idx = chapter.getVerseIndex("" + vref);
						if (idx == -1)
							throw new IOException(vref + ":" + remark.getContent());
						Verse v = chapter.getVerses().get(idx);
						if (remark.getContent().size() != 1)
							throw new IOException();
						String remarkText = normalize((String) remark.getContent().get(0), true).trim();
						v.getAppendVisitor().visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefania", "footnote-source", "remark").visitFootnote().visitText(remarkText);
					} else if (e3 instanceof XREF) {
						XREF xref = (XREF) e3;
						int vref = xref.getVref().intValue();
						int idx = chapter.getVerseIndex("" + vref);
						if (idx == -1)
							throw new IOException(vref + ":" + xref.getMscope());
						Verse v = chapter.getVerses().get(idx);
						Visitor<RuntimeException> footnoteVisitor = v.getAppendVisitor().visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefania", "footnote-source", "outer-xref").visitFootnote();
						boolean first = true;
						for (String mscope : xref.getMscope().split(" ")) {
							Matcher m = Utils.compilePattern("([0-9]+);([0-9]+)(-[0-9]+)?;([0-9]+)(-[0-9]+)?").matcher(mscope);
							if (!m.matches())
								throw new IOException(mscope);
							BookID xrefBookID = BookID.fromZefId(Integer.parseInt(m.group(1)));
							int xrefChapter = Integer.parseInt(m.group(2)), endChapter = xrefChapter;
							if (m.group(3) != null)
								endChapter = Integer.parseInt(m.group(3).substring(1));
							String verse = m.group(4);
							if (verse.equals("0"))
								verse = "1//G";
							String endVerse = m.group(5);
							if (endVerse == null)
								endVerse = verse;
							else
								endVerse = endVerse.substring(1);
							if (endVerse.equals("0"))
								endVerse = "1//G";
							String xrefAbbr = abbrMap.get(xrefBookID);
							if (xrefAbbr == null)
								xrefAbbr = xrefBookID.getOsisID();
							if (first)
								first = false;
							else
								footnoteVisitor.visitText(" ");
							if (xrefChapter == endChapter && !verse.equals("1//G") && !endVerse.equals("1//G") && Integer.parseInt(verse) > Integer.parseInt(endVerse)) {
								String tmp = verse;
								verse = endVerse;
								endVerse = tmp;
							}
							footnoteVisitor.visitCrossReference(xrefAbbr, xrefBookID, xrefChapter, verse, endChapter, endVerse).visitText(xrefAbbr + " " + xrefChapter + ":" + verse);
						}
					} else if (e3 instanceof PROLOG) {
						PROLOG prolog = (PROLOG) e3;
						if (prolog.getVref().intValue() != 1)
							throw new IOException("" + prolog.getVref());
						if (chapter.getProlog() != null)
							throw new IOException("More than one prolog found");
						FormattedText prologText = new FormattedText();
						if (parseContent(prologText.getAppendVisitor(), prolog.getContent(), abbrMap)) {
							prologText.trimWhitespace();
							prologText.finished();
							chapter.setProlog(prologText);
						}
					} else if (e3 instanceof VERS) {
						VERS vers = (VERS) e3;
						int vnumber = vers.getVnumber().intValue();
						if (lastvref != -1) {
							if (lastvref != vnumber)
								throw new IOException(lastvref + " != " + vnumber);
							lastvref = -1;
						}
						Verse verse = new Verse("" + vnumber);
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
						if (!contentFound) {
							visitEmptyMarker(visitor);
						}
						verse.trimWhitespace();
						chapter.getVerses().add(verse);
					} else {
						throw new IOException(e3.getClass().toString());
					}
				}
				for (Verse v : chapter.getVerses())
					v.finished();
			}
			result.getBooks().add(book);
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
				Visitor<RuntimeException> v;
				if (n instanceof DIV) {
					note = ((DIV) n).getNOTE();
					if (note.getContent().size() == 0)
						continue;
					v = visitor.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefania", "footnote-source", "div").visitFootnote();
				} else {
					note = (NOTE) n;
					if (note.getContent().size() == 0)
						continue;
					v = visitor.visitFootnote();
				}
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
					v = visitor.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefania", "newline-group", br.getCount() + "--" + br.getArt().value());
				}
				if (count < 1 || count > 10)
					throw new RuntimeException();
				for (int ii = 0; ii < count; ii++) {
					switch (br.getArt()) {
					case X_NL:
						v.visitLineBreak(LineBreakKind.NEWLINE);
						break;
					case X_P:
						v.visitLineBreak(LineBreakKind.PARAGRAPH);
						break;
					default:
						throw new RuntimeException(br.getArt().toString());
					}
				}
				contentFound = true;
			} else if (n instanceof XREF) {
				XREF xref = (XREF) n;
				Visitor<RuntimeException> footnoteVisitor = visitor.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefania", "footnote-source", "inner-xref").visitFootnote();
				boolean first = true;
				for (String mscope : xref.getMscope().split(" ")) {
					Matcher m = Utils.compilePattern("([0-9]+);([0-9]+)(-[0-9]+)?;([0-9]+)(-[0-9]+)?").matcher(mscope);
					if (!m.matches())
						throw new IOException(mscope);
					BookID bookID = BookID.fromZefId(Integer.parseInt(m.group(1)));
					int chapter = Integer.parseInt(m.group(2)), endChapter = chapter;
					if (m.group(3) != null)
						endChapter = Integer.parseInt(m.group(3).substring(1));
					String verse = m.group(4);
					if (verse.equals("0"))
						verse = "1//G";
					String endVerse = m.group(5);
					if (endVerse == null)
						endVerse = verse;
					else
						endVerse = endVerse.substring(1);
					if (endVerse.equals("0"))
						endVerse = "1//G";
					String abbr = abbrMap.get(bookID);
					if (abbr == null)
						abbr = bookID.getOsisID();
					if (first)
						first = false;
					else
						footnoteVisitor.visitText(" ");
					if (chapter == endChapter && !verse.equals("1//G") && !endVerse.equals("1//G") && Integer.parseInt(verse) > Integer.parseInt(endVerse)) {
						String tmp = verse;
						verse = endVerse;
						endVerse = tmp;
					}
					footnoteVisitor.visitCrossReference(abbr, bookID, chapter, verse, endChapter, endVerse).visitText(abbr + " " + chapter + ":" + verse);
				}
				contentFound = true;
			} else if (n instanceof JAXBElement<?>) {
				String name = ((JAXBElement<?>) n).getName().toString();
				Object nn = ((JAXBElement<?>) n).getValue();
				if (name.equals("STYLE") && nn instanceof STYLE) {
					String css = ((STYLE) nn).getCss();
					String id = ((STYLE) nn).getId();
					if (id != null && css != null)
						throw new IOException(id + "/" + css);
					if (css != null && css.startsWith("display:block;")) {
						// not really a formatting instruction, but more some
						// clever way of indentation
						List<Object> content = ((STYLE) nn).getContent();
						Visitor<RuntimeException> contentVisitor = visitor.visitCSSFormatting(css);
						boolean subContentFound = parseContent(contentVisitor, content, abbrMap);
						if (!subContentFound)
							visitEmptyMarker(contentVisitor);
					} else {
						FormattingInstructionKind kind;
						if (id != null && id.equals("cl:divineName")) {
							kind = FormattingInstructionKind.DIVINE_NAME;
						} else if (css == null) {
							throw new IOException(id);
						} else if (css.contains("italic")) {
							kind = FormattingInstructionKind.ITALIC;
						} else if (css.contains("bold")) {
							kind = FormattingInstructionKind.BOLD;
						} else if (css.equalsIgnoreCase("color:#FF0000")) {
							kind = FormattingInstructionKind.WORDS_OF_JESUS;
						} else if (css.equals("color:blue")) {
							kind = FormattingInstructionKind.LINK;
						} else if (css.equals("color:#00CC33;font-size:8pt;vertical-align:super") || css.equals("font-size:small")) {
							kind = FormattingInstructionKind.SUPERSCRIPT;
						} else {
							throw new IOException(css);
						}
						List<Object> content = ((STYLE) nn).getContent();
						Visitor<RuntimeException> contentVisitor = visitor.visitFormattingInstruction(kind);
						if (css != null && !kind.getCss().equals(css)) {
							contentVisitor = contentVisitor.visitCSSFormatting(css);
						}
						if (content.size() == 0) {
							visitEmptyMarker(contentVisitor);
						} else {
							boolean subContentFound = parseContent(contentVisitor, content, abbrMap);
							if (!subContentFound)
								visitEmptyMarker(contentVisitor);
						}
					}
				} else if ((name.equals("gr") || name.equals("GRAM")) && nn instanceof GRAM) {
					GRAM gram = (GRAM) nn;
					Visitor<RuntimeException> strongVisitor = visitor;
					if (!name.equals("GRAM")) {
						strongVisitor = strongVisitor.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefania", "gram-tag", name);
					}
					if (gram.getStr() == null && gram.getRmac() == null)
						throw new IOException();
					boolean realPrefixes = false;
					char[] strongsPrefixes = null;
					int[] strongs = null;
					if (gram.getStr() != null) {
						String strong = gram.getStr().trim().replaceAll(" ++", " ");
						if (strong.length() == 0)
							strong = "0";
						if (strong.equals("?"))
							strong = "99111";
						if (!strong.matches("[GH]?[0-9]+( [GH]?[0-9]+)*"))
							throw new IOException(strong);
						String[] tmpStrongs = strong.split(" ");
						strongsPrefixes = new char[tmpStrongs.length];
						strongs = new int[tmpStrongs.length];
						for (int i = 0; i < tmpStrongs.length; i++) {
							if (tmpStrongs[i].matches("[GH][0-9]+")) {
								strongsPrefixes[i] = tmpStrongs[i].charAt(0);
								strongs[i] = Integer.parseInt(tmpStrongs[i].substring(1));
								realPrefixes = true;
							} else {
								strongsPrefixes[i] = 'X';
								strongs[i] = Integer.parseInt(tmpStrongs[i]);
							}
						}
					}
					String[] rmacs = null;
					if (gram.getRmac() != null) {
						String rmac = gram.getRmac();
						rmacs = rmac.split(" ");
					}
					strongVisitor = strongVisitor.visitGrammarInformation(realPrefixes ? strongsPrefixes : null, strongs, rmacs, null);
					if (!parseContent(strongVisitor, gram.getContent(), abbrMap)) {
						visitEmptyMarker(strongVisitor);
					}
				} else {
					throw new IOException(name + "/" + nn.getClass().toString());
				}
				contentFound = true;
			} else {
				throw new IOException(n.getClass().toString());
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
		if (!Boolean.getBoolean("biblemulticonverter.skipxmlvalidation"))
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
				if (value.equals("-empty-"))
					value = "";
				if (key.equals(MetadataBookKey.status.toString())) {
					doc.setStatus(EnumStatus.fromValue(value));
				} else if (key.equals(MetadataBookKey.version.toString())) {
					doc.setVersion(value);
				} else if (key.equals(MetadataBookKey.revision.toString())) {
					doc.setRevision(new BigInteger(value));
				} else if (Arrays.asList(INFORMATION_KEYS).contains(key)) {
					doc.getINFORMATION().getTitleOrCreatorOrDescription().add(new JAXBElement<String>(new QName(key), String.class, value));
				}
			}
		}

		for (Book bk : bible.getBooks()) {
			if (bk.getId().equals(BookID.METADATA))
				continue;
			if (bk.getId().getZefID() <= 0) {
				System.out.println("WARNING: Unable to export book " + bk.getAbbr());
				continue;
			}
			String shortname = removeRoundtripMarker(bk.getShortName());
			String longname = removeRoundtripMarker(bk.getLongName());
			BookID bookID = bk.getId();
			BIBLEBOOK bb = of.createBIBLEBOOK();
			bb.setBnumber(BigInteger.valueOf(bookID.getZefID()));
			if (!shortname.equals("_" + bookID.getOsisID()))
				bb.setBsname(shortname);
			if (!longname.equals("_" + bookID.getEnglishName()))
				bb.setBname(longname);

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
					ccc.getProlog().accept(new CreateContentVisitor(of, prolog.getContent(), null, 0, null));
					cc.getPROLOGOrCAPTIONOrVERS().add(prolog);
				}

				for (VirtualVerse vv : ccc.createVirtualVerses()) {
					for (Headline h : vv.getHeadlines()) {
						CAPTION caption = of.createCAPTION();
						caption.setVref(BigInteger.valueOf(vv.getNumber()));
						h.accept(new CreateContentVisitor(of, caption.getContent(), null, 0, null));
						EnumCaptionType[] types = new EnumCaptionType[] {
								null,
								EnumCaptionType.X_H_1, EnumCaptionType.X_H_2, EnumCaptionType.X_H_3,
								EnumCaptionType.X_H_4, EnumCaptionType.X_H_5, EnumCaptionType.X_H_6,
								null, null, null
						};
						caption.setType(types[h.getDepth()]);
						cc.getPROLOGOrCAPTIONOrVERS().add(caption);
					}
					List<Object> remarksAndXrefs = new ArrayList<Object>();
					VERS vers = of.createVERS();
					vers.setVnumber(BigInteger.valueOf(vv.getNumber()));
					boolean firstVerse = true;
					for (Verse v : vv.getVerses()) {
						if (!firstVerse || !v.getNumber().equals("" + vv.getNumber())) {
							STYLE verseNum = of.createSTYLE();
							verseNum.setCss("font-weight: bold");
							verseNum.getContent().add("(" + v.getNumber() + ")");
							vers.getContent().add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, verseNum));
							vers.getContent().add(" ");
						}
						v.accept(new CreateContentVisitor(of, vers.getContent(), remarksAndXrefs, vv.getNumber(), null));
						firstVerse = false;
					}
					cc.getPROLOGOrCAPTIONOrVERS().add(vers);
					cc.getPROLOGOrCAPTIONOrVERS().addAll(remarksAndXrefs);
				}
			}
			doc.getBIBLEBOOK().add(bb);
		}
		return doc;
	}

	private String removeRoundtripMarker(String name) {
		if (name.endsWith("]]") && name.contains("[["))
			name = name.substring(name.lastIndexOf("[[") + 2, name.length() - 2);
		return name;
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private static class CreateContentVisitor implements Visitor<IOException> {

		private final List<Object> result;
		private final List<Object> remarksAndXref;
		private final ObjectFactory of;
		private final String footnoteSource;
		private final int verseNumber;

		private CreateContentVisitor(ObjectFactory of, List<Object> result, List<Object> remarksAndXref, int verseNumber, String footnoteSource) {
			this.of = of;
			this.result = result;
			this.remarksAndXref = remarksAndXref;
			this.verseNumber = verseNumber;
			this.footnoteSource = footnoteSource;
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
			switch (kind) {
			case NEWLINE:
			case NEWLINE_WITH_INDENT:
				br.setArt(EnumBreak.X_NL);
				break;
			case PARAGRAPH:
				br.setArt(EnumBreak.X_P);
				break;
			default:
				throw new IllegalStateException();
			}
			result.add(br);
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			return 0;
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			// headlines were already separated by virtual verse building
			throw new IllegalStateException();
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			STYLE style = of.createSTYLE();
			result.add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, style));
			if (kind == FormattingInstructionKind.DIVINE_NAME) {
				style.setId("cl:divineName");
				return new CreateContentVisitor(of, style.getContent(), null, 0, null);
			}
			style.setCss(kind.getCss());
			return new StyleCSSUpdaterVisitor(style, new CreateContentVisitor(of, style.getContent(), null, 0, null));
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			if (footnoteSource == null) {
				NOTE note = of.createNOTE();
				note.setType("x-studynote");
				result.add(note);
				return new CreateContentVisitor(of, note.getContent(), null, 0, null);
			} else if (footnoteSource.equals("div")) {
				DIV div = of.createDIV();
				result.add(div);
				NOTE note = of.createNOTE();
				note.setType("x-studynote");
				div.setNOTE(note);
				return new CreateContentVisitor(of, note.getContent(), null, 0, null);
			} else if (footnoteSource.equals("inner-xref")) {
				XREF xref = of.createXREF();
				result.add(xref);
				return new MScopeVisitor(xref);
			} else if (footnoteSource.equals("outer-xref")) {
				XREF xref = of.createXREF();
				xref.setVref(BigInteger.valueOf(verseNumber));
				remarksAndXref.add(xref);
				return new MScopeVisitor(xref);
			} else if (footnoteSource.equals("remark")) {
				REMARK remark = of.createREMARK();
				remark.setVref(BigInteger.valueOf(verseNumber));
				remarksAndXref.add(remark);
				return new CreateContentVisitor(of, remark.getContent(), null, 0, null);
			} else {
				throw new IllegalStateException(footnoteSource);
			}
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			if (!category.equals("zefania"))
				return prio.handleVisitor(category, this);
			if (key.equals("empty")) {
				// just ignore, value should be empty
				return null;
			} else if (key.equals("gram-tag")) {
				if (footnoteSource != null)
					throw new IllegalArgumentException();
				return new CreateContentVisitor(of, result, remarksAndXref, verseNumber, "gram-tag=" + value);
			} else if (key.equals("strong-prefix")) {
				if (footnoteSource != null && footnoteSource.startsWith("gram-tag=")) {
					value = value + ";" + footnoteSource;
				} else if (footnoteSource != null) {
					throw new IllegalArgumentException();
				}
				return new CreateContentVisitor(of, result, remarksAndXref, verseNumber, "strong-prefix=" + value);
			} else if (key.equals("footnote-source")) {
				if (footnoteSource != null)
					throw new IllegalArgumentException();
				if (remarksAndXref == null) {
					if (value.equals("outer-xref"))
						value = "inner-xref";
					else if (value.equals("remark"))
						value = null;
				}
				return new CreateContentVisitor(of, result, remarksAndXref, verseNumber, value);
			} else if (key.equals("newline-group")) {
				String[] parts = value.split("--");
				BR br = of.createBR();
				br.setCount(new BigInteger(parts[0]));
				br.setArt(EnumBreak.fromValue(parts[1]));
				result.add(br);
				return null;
			} else {
				throw new IllegalStateException();
			}
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
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			final GRAM gram = of.createGRAM();
			String gramName = "GRAM", prefix = "";
			if (footnoteSource != null) {
				for (String part : footnoteSource.split(";")) {
					if (part.startsWith("gram-tag=")) {
						gramName = part.substring(9);
					} else if (part.startsWith("strong-prefix=")) {
						prefix = part.substring(14);
					} else {
						throw new IllegalStateException(part);
					}
				}
			}
			result.add(new JAXBElement<GRAM>(new QName(gramName), GRAM.class, gram));
			Visitor<IOException> nextVisitor = new CreateContentVisitor(of, gram.getContent(), null, 0, footnoteSource);
			if (strongs != null) {
				StringBuilder entryBuilder = new StringBuilder();
				for (int i = 0; i < strongs.length; i++) {
					entryBuilder.append((i > 0 ? " " : "") + prefix + (strongsPrefixes != null && strongsPrefixes[i] != 'X' ? "" + strongsPrefixes[i] : "") + strongs[i]);
				}
				String entry = entryBuilder.toString();
				if (entry.equals("0"))
					entry = "";
				else if (entry.equals("99111"))
					entry = "?";
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
			XREF xref = of.createXREF();
			result.add(xref);
			new MScopeVisitor(xref).visitCrossReference(bookAbbr, book, firstChapter, firstVerse, lastChapter, lastVerse);
			return null;
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			STYLE style = of.createSTYLE();
			result.add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, style));
			style.setCss(css);
			return new CreateContentVisitor(of, style.getContent(), null, 0, null);
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
			throw new IllegalStateException("Raw HTML is not supported");
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			throw new IllegalStateException("Variation export is not supported");
		}
	}

	private static class MScopeVisitor extends VisitorAdapter<IOException> {

		private XREF xref;

		private MScopeVisitor(XREF xref) throws IOException {
			super(null);
			this.xref = xref;
			xref.setMscope("");
		}

		@Override
		protected void beforeVisit() throws IOException {
			throw new IllegalStateException();
		}

		@Override
		public void visitText(String text) throws IOException {
			if (!text.equals(" "))
				throw new RuntimeException();
			xref.setMscope(xref.getMscope() + text);
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			if (firstVerse.equals("1//G"))
				firstVerse = "0";
			if (lastVerse.equals("1//G"))
				lastVerse = "0";
			String chap = firstChapter + (firstChapter == lastChapter ? "" : "-" + lastChapter);
			String verse = firstVerse + (firstVerse.equals(lastVerse) ? "" : "-" + lastVerse);
			xref.setMscope(xref.getMscope() + book.getZefID() + ";" + chap + ";" + verse);
			return null;
		}
	}

	private static class StyleCSSUpdaterVisitor extends VisitorAdapter<IOException> {
		int mode = 0;
		private STYLE style;
		private Visitor<IOException> nextVisitor;

		private StyleCSSUpdaterVisitor(STYLE style, Visitor<IOException> nextVisitor) throws IOException {
			super(null);
			this.style = style;
			this.nextVisitor = nextVisitor;
		}

		@Override
		protected Visitor<IOException> getVisitor() throws IOException {
			if (mode == 1)
				return null;
			else if (mode == 2)
				return nextVisitor;
			else
				throw new IllegalStateException();
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			if (elementTypes == null)
				return 1;
			mode = elementTypes.equals("c") ? 1 : 2;
			return 0;
		}

		@Override
		protected void beforeVisit() throws IOException {
			if (mode == 1)
				throw new IllegalStateException();
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			if (mode == 1) {
				style.setCss(css);
				return nextVisitor;
			} else if (mode == 2) {
				return super.visitCSSFormatting(css);
			} else {
				throw new IllegalStateException();
			}
		}
	}
}
