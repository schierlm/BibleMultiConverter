package biblemulticonverter.format;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.schema.beblia.BibleType;
import biblemulticonverter.schema.beblia.BibleType.Testament;
import biblemulticonverter.schema.beblia.BookType;
import biblemulticonverter.schema.beblia.ObjectFactory;
import biblemulticonverter.schema.beblia.TestamentType;
import biblemulticonverter.tools.ValidateXML;

/**
 * Importer and exporter for Beblia XML.
 */
public class BebliaXML implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Beblia XML format.",
			"",
			"Usage (export): BebliaXML <OutputFile>",
	};

	private static final List<InfoField> INFORMATION_FIELDS = new ArrayList<>();

	static {
		INFORMATION_FIELDS.add(new InfoField("translation", true, BibleType::getTranslation, BibleType::setTranslation));
		INFORMATION_FIELDS.add(new InfoField("name", true, BibleType::getName, BibleType::setName));
		INFORMATION_FIELDS.add(new InfoField("id", true, BibleType::getId, BibleType::setId));
		INFORMATION_FIELDS.add(new InfoField("bible", true, BibleType::getBible, BibleType::setBible));
		INFORMATION_FIELDS.add(new InfoField("language", true, BibleType::getLanguage, BibleType::setLanguage));

		INFORMATION_FIELDS.add(new InfoField("status", false, BibleType::getStatus, BibleType::setStatus));
		INFORMATION_FIELDS.add(new InfoField("info", false, BibleType::getInfo, BibleType::setInfo));
		INFORMATION_FIELDS.add(new InfoField("version", false, BibleType::getVersion, BibleType::setVersion));
		INFORMATION_FIELDS.add(new InfoField("link", false, BibleType::getLink, BibleType::setLink));
		INFORMATION_FIELDS.add(new InfoField("site", false, BibleType::getSite, BibleType::setSite));
		INFORMATION_FIELDS.add(new InfoField("Copyright", false, BibleType::getCopyright, BibleType::setCopyright));
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		ValidateXML.validateFileBeforeParsing(getSchema(), inputFile);
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Unmarshaller u = ctx.createUnmarshaller();
		BibleType doc = u.unmarshal(new StreamSource(inputFile), BibleType.class).getValue();
		return parseBible(doc);
	}

	protected Bible parseBible(BibleType doc) throws Exception {
		String name = null;
		MetadataBook metadata = new MetadataBook();
		for (InfoField infoField : INFORMATION_FIELDS) {
			String value = infoField.getter.apply(doc);
			if (value != null && !value.trim().isEmpty()) {
				if (infoField.useForTitle && name == null) {
					name = value.replaceAll("[\r\n\t ]+", " ").trim();
				}
				metadata.setValue("beblia@" + infoField.name, value.replaceAll("[\r\n\t ]+", " ").trim());
			}
		}
		metadata.finished();
		if (name == null)
			name = "Imported from Beblia";
		Bible result = new Bible(name == null ? "Imported from Beblia" : name);
		if (metadata.getKeys().size() > 0)
			result.getBooks().add(metadata.getBook());
		for (Testament testament : doc.getTestament()) {
			parseBooks(result, testament.getName(), testament.getBook());
		}
		parseBooks(result, null, doc.getBook());
		return result;
	}

	private void parseBooks(Bible result, TestamentType testament, List<BookType> books) {
		for (BookType bb : books) {
			BookID bookID;
			try {
				bookID = BookID.fromZefId(bb.getNumber());
			} catch (IllegalArgumentException ex) {
				System.out.println("WARNING: Skipping book with unknown id " + bb.getNumber());
				continue;
			}
			Book book = new Book(bookID.getOsisID(), bookID, bookID.getEnglishName(), bookID.getEnglishName());
			result.getBooks().add(book);
			TestamentType expectedTestament = bookID.isNT() ? TestamentType.NEW : TestamentType.OLD;
			if (testament != null && testament != expectedTestament) {
				System.out.println("WARNING: Book " + book.getAbbr() + " is in " + testament.value() + " testamentbut should be in " + expectedTestament.value() + " testament");
			}
			for (BookType.Chapter cc : bb.getChapter()) {
				int chapterNumber = cc.getNumber();
				if (book.getChapters().size() < chapterNumber - 1) {
					System.out.println("WARNING: Empty chapters between " + chapterNumber + " and " + book.getChapters().size());
				}
				while (book.getChapters().size() < chapterNumber) {
					book.getChapters().add(new Chapter());
				}
				Chapter chapter = book.getChapters().get(book.getChapters().size() - 1);
				String prefix = "";
				if (chapterNumber < book.getChapters().size()) {
					System.out.println("WARNING: Chapter " + chapterNumber + " appears after chapter " + book.getChapters().size() + ", using mixed verse numbers");
					prefix = chapterNumber + ",";
				}
				for (BookType.Chapter.Verse vv : cc.getVerse()) {
					Verse v = new Verse(prefix + vv.getNumber());
					v.getAppendVisitor().visitText(vv.getValue().replaceAll("[\r\n\t ]+", " ").trim());
					v.finished();
					chapter.getVerses().add(v);
				}
			}
		}
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File file = new File(exportArgs[0]);
		JAXBElement<BibleType> xmlbible = createXMLBible(bible);
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Marshaller m = ctx.createMarshaller();
		if (!Boolean.getBoolean("biblemulticonverter.skipxmlvalidation"))
			m.setSchema(getSchema());
		m.marshal(xmlbible, doc);
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		if (System.getProperty("biblemulticonverter.indentxml") == null || Boolean.getBoolean("biblemulticonverter.indentxml")) {
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		}
		transformer.transform(new DOMSource(doc), new StreamResult(file));
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/beblia.xsd"));
	}

	protected JAXBElement<BibleType> createXMLBible(Bible bible) throws Exception {
		ObjectFactory of = new ObjectFactory();
		BibleType doc = of.createBibleType();
		boolean nameUsed = false;
		MetadataBook metadata = bible.getMetadataBook();
		if (metadata != null) {
			for (String key : metadata.getKeys()) {
				String value = metadata.getValue(key);
				if (key.startsWith("beblia@")) {
					InfoField ff = INFORMATION_FIELDS.stream().filter(f -> f.name.equalsIgnoreCase(key.substring(7))).findFirst().orElse(null);
					if (ff != null) {
						ff.setter.accept(doc, value);
						if (ff.useForTitle && value.equalsIgnoreCase(bible.getName()))
							nameUsed = true;
					}
				}
			}
		}
		if (!nameUsed && doc.getTranslation() == null) {
			doc.setTranslation(bible.getName());
		}
		BibleType.Testament[] testaments = new BibleType.Testament[2];
		for (Book bk : bible.getBooks()) {
			if (bk.getId().equals(BookID.METADATA))
				continue;
			if (bk.getId().getZefID() <= 0) {
				System.out.println("WARNING: Unable to export book " + bk.getAbbr());
				continue;
			}
			BookType bb = of.createBookType();
			bb.setNumber(bk.getId().getZefID());
			int tt = bk.getId().isNT() ? 1 : 0;
			if (testaments[tt] == null) {
				testaments[tt] = of.createBibleTypeTestament();
				testaments[tt].setName(bk.getId().isNT() ? TestamentType.NEW : TestamentType.OLD);
				doc.getTestament().add(testaments[tt]);
			}
			testaments[tt].getBook().add(bb);

			int cnumber = 0;
			for (Chapter ccc : bk.getChapters()) {
				cnumber++;
				if (ccc.getVerses().size() == 0)
					continue;
				BookType.Chapter cc = of.createBookTypeChapter();
				cc.setNumber(cnumber);
				bb.getChapter().add(cc);
				for (VirtualVerse vv : ccc.createVirtualVerses()) {
					BookType.Chapter.Verse vers = of.createBookTypeChapterVerse();
					vers.setNumber(vv.getNumber());
					StringBuilder sb = new StringBuilder();
					boolean firstVerse = true;
					for (Verse v : vv.getVerses()) {
						if (!firstVerse || !v.getNumber().equals("" + vv.getNumber())) {
							sb.append(" (" + v.getNumber() + ") ");
						}
						v.accept(new BebliaXMLVisitor(sb));
						firstVerse = false;
					}
					vers.setValue(sb.toString().replaceAll("[\r\n\t ]+", " ").trim());
					cc.getVerse().add(vers);
				}
			}
		}
		return of.createBible(doc);
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return false;
	}

	private static class InfoField {
		private final String name;
		private final boolean useForTitle;
		private final Function<BibleType, String> getter;
		private final BiConsumer<BibleType, String> setter;

		public InfoField(String name, boolean useForTitle, Function<BibleType, String> getter, BiConsumer<BibleType, String> setter) {
			this.name = name;
			this.useForTitle = useForTitle;
			this.getter = getter;
			this.setter = setter;
		}
	}

	private static class BebliaXMLVisitor extends VisitorAdapter<RuntimeException> {

		private final StringBuilder sb;

		protected BebliaXMLVisitor(StringBuilder sb) {
			super(null);
			this.sb = sb;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) {
			return this;
		}

		@Override
		public void visitText(String text) {
			sb.append(text);
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) {
			throw new RuntimeException("Headlines not supported");
		}

		@Override
		public Visitor<RuntimeException> visitFootnote(boolean ofCrossReferences) {
			return null;
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) {
			visitText(" ");
		}

		@Override
		public void visitVerseSeparator() {
			visitText("/");
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) {
			throw new RuntimeException("Variations not supported");
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) {
			return prio.handleVisitor(category, this);
		}
	}
}
