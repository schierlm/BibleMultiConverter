package biblemulticonverter.format.paratext;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import biblemulticonverter.format.paratext.ParatextBook.ChapterStart;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextBook.TableCellStart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Text;
import biblemulticonverter.format.paratext.ParatextCharacterContent.VerseStart;
import biblemulticonverter.schema.usfx.NoteContents;
import biblemulticonverter.schema.usfx.ObjectFactory;
import biblemulticonverter.schema.usfx.PType;
import biblemulticonverter.schema.usfx.RefType;
import biblemulticonverter.schema.usfx.StyledString;
import biblemulticonverter.schema.usfx.Usfx;
import biblemulticonverter.schema.usfx.Usfx.Book;
import biblemulticonverter.tools.ValidateXML;

/**
 * Importer and exporter for USFX.
 */
public class USFX extends AbstractParatextFormat {

	public static final String[] HELP_TEXT = {
			"XML Bible format based on USFM used by ebible.org",
			"",
			"Usage (export): USFX <OutputFile>",
			"",
			"Point the importer to .usfx files, not to directories!",
	};

	@Override
	protected List<ParatextBook> doImportAllBooks(File inputFile) throws Exception {
		ValidateXML.validateFileBeforeParsing(getSchema(), inputFile);
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Unmarshaller u = ctx.createUnmarshaller();
		Usfx doc = (Usfx) u.unmarshal(inputFile);
		List<ParatextBook> result = new ArrayList<ParatextBook>();
		for (Serializable s : doc.getContent()) {
			if (s instanceof String) {
				if (!s.toString().trim().isEmpty()) {
					System.out.println("WARNING: Skipping text outside of book: " + s);
				}
			} else if (s instanceof JAXBElement<?>) {
				JAXBElement<?> elem = (JAXBElement<?>) s;
				if (elem.getName().getLocalPart().equals("book")) {
					Usfx.Book book = (Usfx.Book) elem.getValue();
					ParatextBook bk = parseBook(book);
					if (bk != null)
						result.add(bk);

				} else {
					System.out.println("WARNING: Skipping unsupported tag outside of book: " + elem.getName());
				}
			} else {
				System.out.println("WARNING: Skipping unsupported content outside of book " + s);
			}
		}
		result.sort(Comparator.comparing(ParatextBook::getId));
		return result;
	}

	private ParatextBook parseBook(Book book) {
		String bookID = book.getId();
		if (!book.getContent().isEmpty() && book.getContent().get(0) instanceof JAXBElement<?> && ((JAXBElement<?>) book.getContent().get(0)).getName().getLocalPart().equals("id")) {
			Usfx.Book.Id id = (Usfx.Book.Id) ((JAXBElement<?>) book.getContent().remove(0)).getValue();
			bookID = (id.getId() + " " + id.getValue()).trim();
		}
		String[] idParts = bookID.trim().split(" ", 2);
		ParatextID id = ParatextID.fromIdentifier(idParts[0].toUpperCase());
		if (id == null) {
			System.out.println("WARNING: Skipping book with unknown ID: " + idParts[0]);
			return null;
		}
		ParatextBook result = new ParatextBook(id, idParts.length == 1 ? "" : idParts[1]);
		List<ParatextCharacterContentContainer> containerStack = new ArrayList<>();
		parseElements(result, containerStack, book.getContent());
		return result;
	}

	private void parseElements(ParatextBook result, List<ParatextCharacterContentContainer> containerStack, List<Serializable> elements) {
		for (int i = 0; i < elements.size() - 1; i++) {
			Serializable s1 = elements.get(i);
			Serializable s2 = elements.get(i + 1);
			if (s1 instanceof String && s2 instanceof JAXBElement) {
				if (Arrays.asList("c", "v", "ve").contains(((JAXBElement<?>) s2).getName().getLocalPart())) {
					elements.set(i, s1.toString().replaceAll("[\r\n\t ]+$", ""));
				}
			} else if (s1 instanceof JAXBElement<?> && s2 instanceof String) {
				if (Arrays.asList("c", "v", "ve").contains(((JAXBElement<?>) s1).getName().getLocalPart())) {
					elements.set(i + 1, s2.toString().replaceAll("^[\r\n\t ]", ""));
				}
			}
		}
		for (Serializable s : elements) {
			if (s instanceof String) {
				if (s.toString().isEmpty())
					continue;
				if (containerStack.isEmpty()) {
					ParatextCharacterContent container = new ParatextCharacterContent();
					containerStack.add(container);
					result.getContent().add(container);
				}
				containerStack.get(containerStack.size() - 1).getContent().add(new Text(normalize(s.toString())));
			} else if (s instanceof JAXBElement<?>) {
				parseElement(result, containerStack, (JAXBElement<?>) s);
			} else {
				System.out.println("WARNING: Skipping unsupported content inside of book " + s);
			}
		}
	}

	private void parseElement(ParatextBook result, List<ParatextCharacterContentContainer> containerStack, JAXBElement<?> element) {
		String localName = element.getName().getLocalPart();
		if (localName.equals("rem") || localName.equals("cl")) {
			result.getAttributes().put(localName, normalize((String) element.getValue()));
		} else if (localName.equals("h")) {
			Usfx.Book.H h = (Usfx.Book.H) element.getValue();
			result.getAttributes().put("h" + (h.getLevel() == null ? "" : h.getLevel()), normalize(h.getValue()));
		} else if (Arrays.asList("p", "q", "d", "s", "mt", "b").contains(localName)) {
			PType pt = (PType) element.getValue();
			String tag = (pt.getSfm() == null ? localName : pt.getSfm()) + (pt.getLevel() == null ? "" : "" + pt.getLevel());
			ParagraphKind kind = USFM.PARAGRAPH_TAGS.get(tag);
			if (kind == null) {
				System.out.println("WARNING: Unsupported paragraph kind: " + kind);
				kind = ParagraphKind.PARAGRAPH_P;
			}
			result.getContent().add(new ParagraphStart(kind));
			containerStack.clear();
			parseElements(result, containerStack, pt.getContent());
		} else if (Arrays.asList("sectionBoundary", "ca", "milestone", "va", "fm", "fig", "gw", "cs", "wr").contains(localName)) {
			System.out.println("WARNING: Skipping unsupported tag: " + localName);
		} else if (Arrays.asList("generated", "ve", "cp", "vp", "wtp", "da", "fs").contains(localName)) {
			// to be skipped
		} else if (localName.equals("c")) {
			String id;
			if (element.getValue() instanceof Usfx.Book.C) {
				Usfx.Book.C c = (Usfx.Book.C) element.getValue();
				id = c.getId();
			} else if (element.getValue() instanceof PType.C) {
				PType.C c = (PType.C) element.getValue();
				id = c.getId();
			} else {
				throw new IllegalStateException(element.getValue().getClass().getName());
			}
			result.getContent().add(new ChapterStart(Integer.parseInt(id)));
			containerStack.clear();
		} else if (localName.equals("toc")) {
			StyledString ss = (StyledString) element.getValue();
			result.getAttributes().put("toc" + ss.getLevel(), normalize(ss.getContent().stream().filter(c -> c instanceof String).map(Serializable::toString).collect(Collectors.joining())));
		} else if (localName.equals("table") && element.getValue() instanceof Usfx.Book.Table) {
			Usfx.Book.Table table = (Usfx.Book.Table) element.getValue();
			for (Usfx.Book.Table.Tr tr : table.getTr()) {
				result.getContent().add(new ParagraphStart(ParagraphKind.TABLE_ROW));
				for (JAXBElement<PType> cell : tr.getThOrThrOrTc()) {
					result.getContent().add(new TableCellStart(cell.getName().getLocalPart() + cell.getValue().getLevel()));
					containerStack.clear();
					parseElements(result, containerStack, cell.getValue().getContent());
				}
			}
		} else if (localName.equals("table") && element.getValue() instanceof PType.Table) {
			PType.Table table = (PType.Table) element.getValue();
			for (PType.Table.Tr tr : table.getTr()) {
				result.getContent().add(new ParagraphStart(ParagraphKind.TABLE_ROW));
				for (JAXBElement<PType> cell : tr.getThOrThrOrTc()) {
					result.getContent().add(new TableCellStart(cell.getName().getLocalPart() + cell.getValue().getLevel()));
					containerStack.clear();
					parseElements(result, containerStack, cell.getValue().getContent());
				}
			}
		} else if (localName.equals("periph")) {
			result.getContent().add(new ParagraphStart(ParagraphKind.PERIPHERALS));
			containerStack.clear();
			ParatextCharacterContent container = new ParatextCharacterContent();
			container.getContent().add(new Text(normalize((String) element.getValue())));
			containerStack.add(container);
			result.getContent().add(container);
		} else if (localName.equals("v")) {
			String id;
			if (element.getValue() instanceof Usfx.Book.V) {
				Usfx.Book.V v = (Usfx.Book.V) element.getValue();
				id = v.getId();
			} else if (element.getValue() instanceof PType.V) {
				PType.V v = (PType.V) element.getValue();
				id = v.getId();
			} else {
				throw new IllegalStateException(element.getValue().getClass().getName());
			}
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			containerStack.get(containerStack.size() - 1).getContent().add(new VerseStart(id));
		} else if (Arrays.asList("f", "x", "fe").contains(localName)) {
			NoteContents nc = (NoteContents) element.getValue();
			String sfm = nc.getSfm();
			if (sfm == null || sfm.isEmpty())
				sfm = localName;
			String caller = nc.getCaller();
			if (caller == null || caller.isEmpty())
				caller = "+";
			FootnoteXref nextContainer = new FootnoteXref(USFM.FOOTNOTE_XREF_TAGS.get(sfm), caller);
			containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
			containerStack.add(nextContainer);
			parseElements(result, containerStack, nc.getContent());
			containerStack.remove(nextContainer);
		} else if (Arrays.asList("fp", "fr", "fk", "fq", "fqa", "fl", "fdc", "fv", "ft", "fm", "xo", "xk", "xq", "xt", "xot", "xnt", "xdc").contains(localName)
				|| (Arrays.asList("nd", "c", "tl", "it", "qt", "sls", "dc", "bdit", "bk", "pn", "k", "ord", "add", "bd", "sc", "wh", "wg", "wr", "wj", "cs", "em").contains(localName) && element.getValue() instanceof NoteContents)) {

			NoteContents nc = (NoteContents) element.getValue();
			String sfm = nc.getSfm();
			if (sfm == null || sfm.isEmpty())
				sfm = localName;
			AutoClosingFormatting nextContainer = new AutoClosingFormatting(USFM.AUTO_CLOSING_TAGS.get(sfm), false);
			containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
			containerStack.add(nextContainer);
			parseElements(result, containerStack, nc.getContent());
			containerStack.remove(nextContainer);
		} else if (localName.equals("optionalLineBreak")) {
			System.out.println("WARNING: Skipping optional line break");
		} else if (localName.equals("ref")) {
			RefType rt = (RefType) element.getValue();
			ParatextCharacterContentPart ref = new Text(normalize(rt.getContent()));

			// TODO
			// The following code does not seem to be exactly according to the specification found here:
			// https://ebible.org/usfx/usfx_xsd.html#refType
			// This code does not allow for a second book, as in: ISA.7.14-ISA.7.15.
			if (rt.getTgt() == null || !rt.getTgt().matches("[A-Z1-4]{3}\\.[0-9]+\\.[0-9]+(-[0-9]+(\\.[0-9]+)?)?")) {
				System.out.println("WARNING: Unsupported structured reference format - replaced by plain text: " + rt.getTgt());
			} else {
				String[] parts = rt.getTgt().split("[ .-]");
				ParatextID id = ParatextID.fromIdentifier(parts[0]);
				if (id == null) {
					System.out.println("WARNING: Unsupported book in structured reference - replaced by plain text: " + parts[0]);
				} else {
					int c1 = Integer.parseInt(parts[1]);
					String v1 = parts[2];
					if(parts.length > 3) {
						// second verse
						String v2 = parts[parts.length - 1];
						if(parts.length == 5) {
							// second chapter
							int c2 = Integer.parseInt(parts[3]);
							ref = Reference.verseRange(id, c1, v1, c2, v2, rt.getContent());
						} else {
							// No second chapter, but we do have a second verse, use first chapter as second chapter.
							ref = Reference.verseRange(id, c1, v1, c1, v2, rt.getContent());
						}
					} else {
						ref = Reference.verse(id, c1, v1, rt.getContent());
					}
				}
			}
			containerStack.get(containerStack.size() - 1).getContent().add(ref);
		} else if (localName.equals("w")) {
			PType.W w = (PType.W) element.getValue();
			String sfm = w.getSfm();
			if (sfm == null || sfm.isEmpty())
				sfm = localName;
			AutoClosingFormatting nextContainer = new AutoClosingFormatting(USFM.AUTO_CLOSING_TAGS.get(sfm), false);
			if (w.getL() != null && !w.getL().isEmpty())
				nextContainer.getAttributes().put("lemma", w.getL());
			if (w.getS() != null && !w.getS().isEmpty())
				nextContainer.getAttributes().put("strong", w.getS());
			if (w.getM() != null && !w.getM().isEmpty())
				nextContainer.getAttributes().put("x-morph", w.getM());
			if (w.getSrcloc() != null && !w.getSrcloc().isEmpty())
				nextContainer.getAttributes().put("x-srcloc", w.getSrcloc());
			if (w.isPlural() != null)
				nextContainer.getAttributes().put("x-plural", "" + w.isPlural());
			containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
			containerStack.add(nextContainer);
			parseElements(result, containerStack, w.getContent());
			containerStack.remove(nextContainer);
		} else if (localName.equals("quoteStart")) {
			PType.QuoteStart qs = (PType.QuoteStart) element.getValue();
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			containerStack.get(containerStack.size() - 1).getContent().add(new Text(normalize(qs.getValue())));
		} else if (localName.equals("quoteRemind") || localName.equals("quoteEnd")) {
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			containerStack.get(containerStack.size() - 1).getContent().add(new Text(normalize(element.getValue().toString())));
		} else if (Arrays.asList("rq", "em", "qt", "nd", "tl", "qs", "qac", "sls", "dc", "bk", "k", "add", "sig", "bd", "it", "bdit", "sc", "wj").contains(localName)) {
			PType v = (PType) element.getValue();
			String sfm = v.getSfm();
			if (sfm == null || sfm.isEmpty())
				sfm = localName;
			AutoClosingFormatting nextContainer = new AutoClosingFormatting(USFM.AUTO_CLOSING_TAGS.get(sfm), false);
			containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
			containerStack.add(nextContainer);
			parseElements(result, containerStack, v.getContent());
			containerStack.remove(nextContainer);
		} else if (Arrays.asList("pn", "ord", "no", "ndx", "wh", "wg", "ior").contains(localName)) {
			AutoClosingFormatting nextContainer = new AutoClosingFormatting(USFM.AUTO_CLOSING_TAGS.get(localName), false);
			containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
			nextContainer.getContent().add(new ParatextCharacterContent.Text((String) element.getValue()));
		} else {
			System.out.println("WARNING: Unexpected tag: " + localName);
		}
	}

	private String normalize(String value) {
		return value.replaceAll("[ \r\n\t]+", " ");
	}

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void doExportBooks(List<ParatextBook> books, String... exportArgs) throws Exception {
		ObjectFactory of = new ObjectFactory();
		File file = new File(exportArgs[0]);
		Usfx usfx = of.createUsfx();
		for (ParatextBook book : books) {
			usfx.getContent().add(of.createUsfxBook(createBook(book)));
		}
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Marshaller m = ctx.createMarshaller();
		m.setSchema(getSchema());
		m.marshal(usfx, doc);
		doc.getDocumentElement().setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		doc.getDocumentElement().setAttribute("xsi:noNamespaceSchemaLocation", "usfx.xsd");
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		transformer.transform(new DOMSource(doc), new StreamResult(file));
	}

	private Book createBook(ParatextBook book) {
		throw new UnsupportedOperationException("Exporting to USFX is not implemented!");
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		throw new UnsupportedOperationException();
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/usfx.xsd"));
	}
}