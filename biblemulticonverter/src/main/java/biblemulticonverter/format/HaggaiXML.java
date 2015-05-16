package biblemulticonverter.format;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.w3c.dom.Element;
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
import biblemulticonverter.schema.haggai.BIBLEBOOK;
import biblemulticonverter.schema.haggai.CAPTION;
import biblemulticonverter.schema.haggai.CHAPTER;
import biblemulticonverter.schema.haggai.GRAM;
import biblemulticonverter.schema.haggai.INFORMATION;
import biblemulticonverter.schema.haggai.ModuleStatus;
import biblemulticonverter.schema.haggai.NOTE;
import biblemulticonverter.schema.haggai.ObjectFactory;
import biblemulticonverter.schema.haggai.PARAGRAPH;
import biblemulticonverter.schema.haggai.PROLOG;
import biblemulticonverter.schema.haggai.REMARK;
import biblemulticonverter.schema.haggai.STYLE;
import biblemulticonverter.schema.haggai.TStyleFix;
import biblemulticonverter.schema.haggai.VERSE;
import biblemulticonverter.schema.haggai.XMLBIBLE;

/**
 * Importer and exporter for Haggai XML.
 */
public class HaggaiXML implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Haggai XML - used by Free Scriptures project.",
	};

	private static final Map<MetadataBookKey, Pattern> INFORMATION_FIELDS = new EnumMap<MetadataBookKey, Pattern>(MetadataBookKey.class);

	static {
		Pattern everything = Utils.compilePattern("(?s).*");
		INFORMATION_FIELDS.put(MetadataBookKey.source, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.identifier, Utils.compilePattern("[a-zA-Z][a-zA-Z0-9_-]*"));
		INFORMATION_FIELDS.put(MetadataBookKey.publisher, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.date, Utils.compilePattern("[0-9]{4}-[0-9]{2}-[0-9]{2}"));
		INFORMATION_FIELDS.put(MetadataBookKey.coverage, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.creator, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.language, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.description, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.title, everything);
		INFORMATION_FIELDS.put(MetadataBookKey.rights, everything);
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Unmarshaller u = ctx.createUnmarshaller();
		XMLBIBLE doc = (XMLBIBLE) ((JAXBElement<?>) u.unmarshal(inputFile)).getValue();
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
		for (JAXBElement<?> elem : doc.getINFORMATION().getValue().getTitleOrCreatorOrDescription()) {
			if (elem.getValue() == null)
				continue;
			String value = normalize(elem.getValue().toString(), true).trim();
			if (value.length() != 0)
				metadata.setValue(elem.getName().getLocalPart(), value);
		}
		if (metadata.getKeys().size() > 0) {
			metadata.finished();
			result.getBooks().add(metadata.getBook());
		}
		Set<String> abbrs = new HashSet<String>();
		Set<String> shortnames = new HashSet<String>();
		Map<BookID, String> abbrMap = new EnumMap<BookID, String>(BookID.class);
		List<JAXBElement<BIBLEBOOK>> nl = doc.getBIBLEBOOK();
		for (JAXBElement<BIBLEBOOK> ee : nl) {
			BIBLEBOOK e = ee.getValue();
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

		for (JAXBElement<BIBLEBOOK> ee : nl) {
			BIBLEBOOK e = ee.getValue();
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
			Book book = existingBooks.get(bookID);
			if (book == null) {
				book = new Book(abbr, bookID, shortname, longname);
				existingBooks.put(bookID, book);
				result.getBooks().add(book);
			}
			List<Headline> headlineBuffer = new ArrayList<Headline>();
			for (JAXBElement<?> cpr : e.getCAPTIONOrPROLOGOrREMARK()) {
				if (cpr.getValue() instanceof CAPTION) {
					CAPTION caption = (CAPTION) cpr.getValue();
					Headline h = new Headline(9);
					if (parseContent(h.getAppendVisitor(), caption.getContent(), abbrMap)) {
						h.trimWhitespace();
						h.finished();
						headlineBuffer.add(h);
					}
				} else if (cpr.getValue() instanceof CHAPTER) {
					CHAPTER e2 = (CHAPTER) cpr.getValue();
					int chapterNumber = e2.getCnumber().intValue();
					while (book.getChapters().size() < chapterNumber)
						book.getChapters().add(new Chapter());
					Chapter chapter = book.getChapters().get(chapterNumber - 1);
					int existingVerses = chapter.getVerses().size();
					for (Object e3 : e2.getCAPTIONOrPARAGRAPHOrVERSE()) {
						parseChapterObject(e3, chapter, abbrMap, headlineBuffer);
					}
					for (Verse v : chapter.getVerses()) {
						if (existingVerses > 0) {
							existingVerses--;
							continue;
						}
						v.finished();
					}
				} else {
					throw new IOException(cpr.getValue().getClass().toString());
				}
			}
		}

		return result;
	}

	private void parseChapterObject(Object e3, Chapter chapter, Map<BookID, String> abbrMap, List<Headline> headlineBuffer) throws IOException {
		if (e3 instanceof CAPTION) {
			CAPTION caption = (CAPTION) e3;
			Headline h = new Headline(9);
			if (parseContent(h.getAppendVisitor(), caption.getContent(), abbrMap)) {
				h.trimWhitespace();
				h.finished();
				headlineBuffer.add(h);
			}
		} else if (e3 instanceof REMARK) {
			REMARK remark = (REMARK) e3;
			Verse v = chapter.getVerses().get(chapter.getVerses().size() - 1);
			if (remark.getContent().size() != 1)
				return;
			String remarkText = normalize((String) remark.getContent().get(0), true).trim();
			v.getAppendVisitor().visitFootnote().visitText(remarkText);
		} else if (e3 instanceof PROLOG) {
			PROLOG prolog = (PROLOG) e3;
			if (chapter.getProlog() != null)
				return;
			FormattedText prologText = new FormattedText();
			if (parseContent(prologText.getAppendVisitor(), prolog.getContent(), abbrMap)) {
				prologText.trimWhitespace();
				prologText.finished();
				chapter.setProlog(prologText);
			}
		} else if (e3 instanceof PARAGRAPH) {
			PARAGRAPH para = (PARAGRAPH) e3;
			for (Object ee3 : para.getCAPTIONOrPROLOGOrREMARK()) {
				parseChapterObject(ee3, chapter, abbrMap, headlineBuffer);
			}
			chapter.getVerses().get(chapter.getVerses().size() - 1).getAppendVisitor().visitLineBreak(LineBreakKind.PARAGRAPH);
		} else if (e3 instanceof JAXBElement<?>) {
			JAXBElement<?> je = (JAXBElement<?>) e3;
			if (je.getName().getLocalPart().equals("VERSE") && je.getValue() instanceof VERSE) {
				VERSE vers = (VERSE) je.getValue();
				int vnumber = vers.getVnumber().intValue();
				if (vnumber == 0)
					vnumber = chapter.getVerses().size() + 1;
				String verseNumber = "" + vnumber;
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
			} else {
				throw new IOException(je.getName() + "/" + je.getDeclaredType());
			}
		} else {
			throw new IOException(e3.getClass().toString());
		}
	}

	private boolean parseContent(Visitor<RuntimeException> visitor, List<? extends Object> contentList, Map<BookID, String> abbrMap) throws IOException {
		boolean contentFound = false;
		for (Object n : contentList) {
			if (n instanceof String) {
				String value = normalize((String) n, false);
				visitor.visitText(value);
				contentFound |= value.trim().length() > 0;
			} else if (n instanceof JAXBElement<?>) {
				String name = ((JAXBElement<?>) n).getName().toString();
				Object nn = ((JAXBElement<?>) n).getValue();
				if (name.equals("STYLE") && nn instanceof STYLE) {
					TStyleFix fs = ((STYLE) nn).getFs();
					FormattingInstructionKind kind;
					switch (fs) {
					case BOLD:
						kind = FormattingInstructionKind.BOLD;
						break;
					case DIVINE_NAME:
						kind = FormattingInstructionKind.DIVINE_NAME;
						break;
					case EMPHASIS:
					case ITALIC:
						kind = FormattingInstructionKind.ITALIC;
						break;
					case LINE_THROUGH:
						kind = FormattingInstructionKind.STRIKE_THROUGH;
						break;
					case SUB:
						kind = FormattingInstructionKind.SUBSCRIPT;
						break;
					case SUPER:
						kind = FormattingInstructionKind.SUPERSCRIPT;
						break;
					case UNDERLINE:
						kind = FormattingInstructionKind.UNDERLINE;
						break;
					case UPPERCASE:
					case ACROSTIC:
					case ILLUMINATED:
					case LOWERCASE:
					case NORMAL:
					case OVERLINE:
					case SMALL_CAPS:
					default:
						kind = null;
						break;
					}
					if (kind == null)
						throw new IOException(fs.toString());
					Visitor<RuntimeException> contentVisitor = visitor;
					if (kind != null) {
						contentVisitor = contentVisitor.visitFormattingInstruction(kind);
					}
					List<Serializable> content = ((STYLE) nn).getContent();
					boolean subContentFound = parseContent(contentVisitor, content, abbrMap);
					if (!subContentFound)
						visitEmptyMarker(contentVisitor);
				} else if ((name.equals("gr") || name.equals("GRAM")) && nn instanceof GRAM) {
					GRAM gram = (GRAM) nn;
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
				} else if (name.equals("NOTE") && nn instanceof NOTE) {
					NOTE note = (NOTE) nn;
					if (note.getContent().size() == 0)
						continue;
					Visitor<RuntimeException> v;
					v = visitor.visitFootnote();
					boolean subContentFound = parseContent(v, note.getContent(), abbrMap);
					if (!subContentFound)
						visitEmptyMarker(v);
					contentFound = true;
				} else if (name.equals("BR")) {
					visitor.visitLineBreak(LineBreakKind.NEWLINE);
					contentFound = true;
				} else {
					throw new IOException(name);
				}
				contentFound = true;
			} else {
				throw new IOException(n.getClass().toString());
			}
		}
		return contentFound;
	}

	private void visitEmptyMarker(Visitor<RuntimeException> v) {
		v.visitExtraAttribute(ExtraAttributePriority.SKIP, "haggai", "empty", "true");
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
		m.marshal(new JAXBElement<XMLBIBLE>(new QName("XMLBIBLE"), XMLBIBLE.class, xmlbible), doc);
		doc.getDocumentElement().setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		doc.getDocumentElement().setAttribute("xsi:noNamespaceSchemaLocation", "haggai_20130620.xsd");
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		transformer.transform(new DOMSource(doc), new StreamResult(file));
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/haggai_20130620.xsd"));
	}

	protected XMLBIBLE createXMLBible(Bible bible) throws Exception {
		ObjectFactory of = new ObjectFactory();
		XMLBIBLE doc = of.createXMLBIBLE();
		doc.setBiblename(bible.getName());
		doc.setINFORMATION(new JAXBElement<INFORMATION>(new QName("INFORMATION"), INFORMATION.class, of.createINFORMATION()));
		MetadataBook metadata = bible.getMetadataBook();
		if (metadata != null) {
			for (String key : metadata.getKeys()) {
				String value = metadata.getValue(key);
				if (value.equals("-empty-"))
					value = "";
				if (key.equals(MetadataBookKey.status.toString())) {
					doc.setStatus(ModuleStatus.fromValue(value));
				} else if (key.equals(MetadataBookKey.version.toString())) {
					doc.setVersion(value);
				} else if (key.equals(MetadataBookKey.revision.toString())) {
					doc.setRevision(new BigInteger(value));
				} else if (!key.contains("@")) {
					Pattern regex = INFORMATION_FIELDS.get(MetadataBookKey.valueOf(key));
					if (regex != null && regex.matcher(value).matches())
						doc.getINFORMATION().getValue().getTitleOrCreatorOrDescription().add(new JAXBElement<String>(new QName(key), String.class, value));
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
				bb.getCAPTIONOrPROLOGOrREMARK().add(new JAXBElement<CHAPTER>(new QName("CHAPTER"), CHAPTER.class, cc));

				if (ccc.getProlog() != null) {
					PROLOG prolog = of.createPROLOG();
					ccc.getProlog().accept(new CreateContentVisitor(of, prolog.getContent(), null));
					cc.getCAPTIONOrPARAGRAPHOrVERSE().add(new JAXBElement<PROLOG>(new QName("PROLOG"), PROLOG.class, prolog));
				}

				for (VirtualVerse vv : ccc.createVirtualVerses()) {
					for (Headline h : vv.getHeadlines()) {
						CAPTION caption = of.createCAPTION();
						h.accept(new CreateContentVisitor(of, caption.getContent(), null));
						cc.getCAPTIONOrPARAGRAPHOrVERSE().add(new JAXBElement<CAPTION>(new QName("CAPTION"), CAPTION.class, caption));
					}
					VERSE vers = of.createVERSE();
					vers.setVnumber(BigInteger.valueOf(vv.getNumber()));
					for (Verse v : vv.getVerses()) {
						if (!v.getNumber().equals("" + vv.getNumber())) {
							STYLE verseNum = of.createSTYLE();
							verseNum.setFs(TStyleFix.BOLD);
							verseNum.getContent().add("(" + v.getNumber() + ")");
							vers.getContent().add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, verseNum));
							vers.getContent().add(" ");
						}
						v.accept(new CreateContentVisitor(of, vers.getContent(), vers));
					}
					cc.getCAPTIONOrPARAGRAPHOrVERSE().add(new JAXBElement<VERSE>(new QName("VERSE"), VERSE.class, vers));
				}
			}
			doc.getBIBLEBOOK().add(new JAXBElement<BIBLEBOOK>(new QName("BIBLEBOOK"), BIBLEBOOK.class, bb));
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

		private List<Serializable> result;
		private final ObjectFactory of;
		private static Element brElement = null;
		private final VERSE containingVerse;

		private CreateContentVisitor(ObjectFactory of, List<Serializable> result, VERSE containingVerse) {
			this.of = of;
			this.result = result;
			this.containingVerse = containingVerse;
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			STYLE style = of.createSTYLE();
			style.setFs(TStyleFix.BOLD);
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
			if (brElement == null) {
				try {
					brElement = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().createElement("BR");
				} catch (Exception ex) {
					throw new IOException(ex);
				}
			}
			result.add(new JAXBElement<Object>(new QName("BR"), Object.class, brElement));
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
			TStyleFix fs;
			switch (kind) {
			case BOLD:
				fs = TStyleFix.BOLD;
				break;
			case DIVINE_NAME:
				fs = TStyleFix.DIVINE_NAME;
				break;
			case ITALIC:
				fs = TStyleFix.ITALIC;
				break;
			case STRIKE_THROUGH:
				fs = TStyleFix.LINE_THROUGH;
				break;
			case SUBSCRIPT:
				fs = TStyleFix.SUB;
				break;
			case SUPERSCRIPT:
				fs = TStyleFix.SUPER;
				break;
			case UNDERLINE:
				fs = TStyleFix.UNDERLINE;
				break;
			default:
				fs = null;
				break;
			}
			if (fs != null) {
				result.add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, style));
				style.setFs(fs);
				return new CreateContentVisitor(of, style.getContent(), containingVerse);
			} else {
				return this;
			}
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			if (containingVerse == null) {
				System.out.println("WARNING: Skipping footnote outside of verse");
				return null;
			}
			NOTE note = of.createNOTE();
			if (containingVerse.getContent() == result) {
				result.add(new JAXBElement<NOTE>(new QName("NOTE"), NOTE.class, note));
			} else {
				List<Serializable> currResult = containingVerse.getContent();
				List<Serializable> appendResult = currResult;
				while (currResult != result) {
					STYLE style = (STYLE) ((JAXBElement<?>) currResult.get(currResult.size() - 1)).getValue();
					STYLE newStyle = of.createSTYLE();
					newStyle.setFs(style.getFs());
					if (appendResult == currResult)
						currResult.add(new JAXBElement<NOTE>(new QName("NOTE"), NOTE.class, note));
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
		public Visitor<IOException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			final GRAM gram = of.createGRAM();
			result.add(new JAXBElement<GRAM>(new QName("GRAM"), GRAM.class, gram));
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
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			return this;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			// skip for now
			return null;
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			// skip for now
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			throw new RuntimeException("Variations not supported");
		}
	}
}
