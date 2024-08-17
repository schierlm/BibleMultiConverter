package biblemulticonverter.format.paratext;

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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import biblemulticonverter.data.Utils;
import biblemulticonverter.format.paratext.ParatextBook.ChapterStart;
import biblemulticonverter.format.paratext.ParatextBook.Figure;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextBook.PeripheralStart;
import biblemulticonverter.format.paratext.ParatextBook.Remark;
import biblemulticonverter.format.paratext.ParatextBook.TableCellStart;
import biblemulticonverter.format.paratext.ParatextBook.VerseEnd;
import biblemulticonverter.format.paratext.ParatextBook.VerseStart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Text;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import biblemulticonverter.format.paratext.utilities.ImportUtilities;
import biblemulticonverter.format.paratext.utilities.TagParser;
import biblemulticonverter.format.paratext.utilities.TextUtilities;
import biblemulticonverter.schema.usfx.NoteContents;
import biblemulticonverter.schema.usfx.ObjectFactory;
import biblemulticonverter.schema.usfx.PType;
import biblemulticonverter.schema.usfx.PType.Ca;
import biblemulticonverter.schema.usfx.PType.Milestone;
import biblemulticonverter.schema.usfx.RefType;
import biblemulticonverter.schema.usfx.StyledString;
import biblemulticonverter.schema.usfx.Usfx;
import biblemulticonverter.schema.usfx.Usfx.Book;
import biblemulticonverter.schema.usfx.Usfx.Book.Cp;
import biblemulticonverter.schema.usfx.Usfx.Book.Table;
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
			"",
			"While this module also supports export, probably you get better (more compatible) results",
			"by using Haiola to convert from USFM or USX instead."
	};

	public USFX() {
		super("USFX");
	}

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

	private static class ImportBookContext {
		ChapterStart openChapter = null;
	}

	private ParatextBook parseBook(Book book) {

		String bookID = book.getId();
		while(book.getContent().size() > 1 && book.getContent().get(0) instanceof String && ((String)book.getContent().get(0)).trim().isEmpty()) {
			book.getContent().remove(0);
		}
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
		ImportBookContext context = new ImportBookContext();
		parseElements(result, containerStack, book.getContent(), context);
		ImportUtilities.closeOpenChapter(result, context.openChapter);
		return result;
	}

	private void parseElements(ParatextBook result, List<ParatextCharacterContentContainer> containerStack, List<Serializable> elements, ImportBookContext context) {
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
				Text text = Text.from((String) s);
				if(text == null) {
					continue;
				}
				if (containerStack.isEmpty()) {
					ParatextCharacterContent container = new ParatextCharacterContent();
					containerStack.add(container);
					result.getContent().add(container);
				}
				containerStack.get(containerStack.size() - 1).getContent().add(text);
			} else if (s instanceof JAXBElement<?>) {
				parseElement(result, containerStack, (JAXBElement<?>) s, context);
			} else {
				System.out.println("WARNING: Skipping unsupported content inside of book " + s);
			}
		}
	}

	private void parseElement(ParatextBook result, List<ParatextCharacterContentContainer> containerStack, JAXBElement<?> element, ImportBookContext context) {
		String localName = element.getName().getLocalPart();
		if (localName.equals("rem")) {
			String value = TextUtilities.whitespaceNormalization((String) element.getValue()).trim();
			if (value.startsWith("@@STATUS@@ ")) {
				result.getAttributes().put("sts", value.substring(11));
			} else if (!result.getContent().isEmpty()) {
				result.getContent().add(new Remark(value));
			} else if (result.getAttributes().containsKey("rem")) {
				int number = 2;
				while (result.getAttributes().containsKey("rem@" + number))
					number++;
				result.getAttributes().put("rem@" + number, value);
			} else {
				result.getAttributes().put("rem", value);
			}
		} else if (localName.equals("cl")) {
			result.getContent().add(new ParagraphStart(ParagraphKind.CHAPTER_LABEL));
			ParatextCharacterContent pcc = new ParatextCharacterContent();
			result.getContent().add(pcc);
			pcc.getContent().add(ParatextCharacterContent.Text.from(TextUtilities.whitespaceNormalization((String) element.getValue()).trim()));
		} else if (localName.equals("h")) {
			Usfx.Book.H h = (Usfx.Book.H) element.getValue();
			result.getAttributes().put("h" + (h.getLevel() == null ? "" : h.getLevel()), TextUtilities.whitespaceNormalization(h.getValue()).trim());
		} else if (localName.equals("b") && element.getValue() instanceof Usfx.Book.B) {
			Usfx.Book.B b = (Usfx.Book.B) element.getValue();
			String tag = (b.getSfm() == null ? localName : b.getSfm());
			ParagraphKind kind = USFM.PARAGRAPH_TAGS.get(tag);
			if (kind == null) {
				System.out.println("WARNING: Unsupported paragraph kind: " + kind);
				kind = ParagraphKind.PARAGRAPH_P;
			}
			result.getContent().add(new ParagraphStart(kind));
			containerStack.clear();
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
			parseElements(result, containerStack, pt.getContent(), context);
		} else if (Arrays.asList("sectionBoundary", "fm", "gw", "wr").contains(localName)) {
			System.out.println("WARNING: Skipping unsupported tag: " + localName);
		} else if (Arrays.asList("generated", "wtp", "da", "fs").contains(localName)) {
			// to be skipped
		} else if (localName.equals("c")) {
			ImportUtilities.closeOpenChapter(result, context.openChapter);
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
			context.openChapter = new ChapterStart(new ChapterIdentifier(result.getId(), Integer.parseInt(id)));
			result.getContent().add(context.openChapter);
			containerStack.clear();
		} else if (localName.equals("va")) {
			String value = (String) element.getValue();
			AutoClosingFormatting va = new AutoClosingFormatting(AutoClosingFormattingKind.ALTERNATE_VERSE);
			va.getContent().add(Text.from(value));
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			containerStack.get(containerStack.size() - 1).getContent().add(va);
		} else if (localName.equals("ca")) {
			String value = ((PType.Ca) element.getValue()).getValue();
			AutoClosingFormatting ca = new AutoClosingFormatting(AutoClosingFormattingKind.ALTERNATE_CHAPTER);
			ca.getContent().add(Text.from(value));
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			containerStack.get(containerStack.size() - 1).getContent().add(ca);
		} else if (localName.equals("cp") && element.getValue() instanceof Usfx.Book.Cp) {
			Usfx.Book.Cp cp = (Usfx.Book.Cp) element.getValue();
			result.getContent().add(new ParagraphStart(ParagraphKind.CHAPTER_PRESENTATION));
			ParatextCharacterContent pcc = new ParatextCharacterContent();
			pcc.getContent().add(Text.from(cp.getId()));
			result.getContent().add(pcc);
		} else if (localName.equals("vp")) {
			String vp = (String) element.getValue();
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			AutoClosingFormatting acf = new AutoClosingFormatting(AutoClosingFormattingKind.PUBLISHED_VERSE);
			acf.getContent().add(Text.from(vp));
			containerStack.get(containerStack.size() - 1).getContent().add(acf);
		} else if (localName.equals("fig")) {
			PType.Fig fig = (PType.Fig) element.getValue();
			String caption = "";
			Map<String, String> attributes = new HashMap<>();
			Map<String, String> tagMap = Arrays.asList("description:alt", "catalog:src", "size:size", "location:loc", "copyright:copy", "reference:ref").stream().map(s -> s.split(":")).collect(Collectors.toMap(x -> x[0], x -> x[1]));
			for (Serializable s : fig.getContent()) {
				if (!(s instanceof JAXBElement<?>))
					continue;
				JAXBElement<String> je = (JAXBElement<String>) s;
				String figLocalName = je.getName().getLocalPart();
				if (figLocalName.equals("caption")) {
					caption = je.getValue();
				} else {
					attributes.put(tagMap.getOrDefault(figLocalName, "-ignored-"), je.getValue());
				}
			}
			for (String optatt : Arrays.asList("alt", "loc", "copy", "ref", "size")) {
				if (attributes.containsKey(optatt) && attributes.get(optatt).trim().isEmpty())
					attributes.remove(optatt);
			}
			Figure figure = new ParatextBook.Figure(caption);
			figure.getAttributes().putAll(attributes);
			result.getContent().add(figure);
		} else if (localName.equals("toc")) {
			StyledString ss = (StyledString) element.getValue();
			String tag = ss.getSfm() == null ? "toc" : ss.getSfm();
			result.getAttributes().put(tag + ss.getLevel(), TextUtilities.whitespaceNormalization(ss.getContent().stream().filter(c -> c instanceof String).map(Serializable::toString).collect(Collectors.joining())).trim());
		} else if (localName.equals("table") && element.getValue() instanceof Usfx.Book.Table) {
			Usfx.Book.Table table = (Usfx.Book.Table) element.getValue();
			for (Usfx.Book.Table.Tr tr : table.getTr()) {
				result.getContent().add(new ParagraphStart(ParagraphKind.TABLE_ROW));
				for (JAXBElement<PType> cell : tr.getThOrThrOrTc()) {
					result.getContent().add(new TableCellStart(cell.getName().getLocalPart() + cell.getValue().getLevel()));
					containerStack.clear();
					parseElements(result, containerStack, cell.getValue().getContent(), context);
				}
			}
		} else if (localName.equals("table") && element.getValue() instanceof PType.Table) {
			PType.Table table = (PType.Table) element.getValue();
			for (PType.Table.Tr tr : table.getTr()) {
				result.getContent().add(new ParagraphStart(ParagraphKind.TABLE_ROW));
				for (JAXBElement<PType> cell : tr.getThOrThrOrTc()) {
					result.getContent().add(new TableCellStart(cell.getName().getLocalPart() + cell.getValue().getLevel()));
					containerStack.clear();
					parseElements(result, containerStack, cell.getValue().getContent(), context);
				}
			}
		} else if (localName.equals("periph")) {
			result.getContent().add(new PeripheralStart((String) element.getValue(), null));
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
			ChapterStart chapter = result.findLastBookContent(ChapterStart.class);
			if (chapter == null) {
				throw new IllegalStateException("Verse found before chapter start: " + id);
			}
			VerseIdentifier location = new VerseIdentifier(result.getId(), chapter.getChapter(), id);
			result.getContent().add(new VerseStart(location, id));
			containerStack.clear();
		} else if (localName.equals("ve")) {
			VerseStart start = result.findLastBookContent(VerseStart.class);
			if (start == null) {
				throw new IllegalStateException("Verse end found before verse start!");
			}
			result.getContent().add(new VerseEnd(start.getLocation()));
			containerStack.clear();
		} else if (Arrays.asList("f", "x", "fe", "ef", "ex").contains(localName)) {
			NoteContents nc = (NoteContents) element.getValue();
			String sfm = nc.getSfm();
			if (sfm == null || sfm.isEmpty())
				sfm = localName;
			String caller = nc.getCaller();
			if (caller == null || caller.isEmpty())
				caller = "+";
			FootnoteXref nextContainer = new FootnoteXref(USFM.FOOTNOTE_XREF_TAGS.get(sfm), caller, new String[0]);
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
			containerStack.add(nextContainer);
			parseElements(result, containerStack, nc.getContent(), context);
			containerStack.remove(nextContainer);
		} else if (Arrays.asList("fp", "fr", "fk", "fq", "fqa", "fl", "fdc", "fv", "ft", "fm", "xo", "xk", "xq", "xt", "xot", "xnt", "xdc").contains(localName)
				|| (Arrays.asList("nd", "c", "tl", "it", "qt", "sls", "dc", "bdit", "bk", "pn", "k", "ord", "add", "bd", "sc", "wh", "wg", "wr", "wj", "cs", "em").contains(localName) && element.getValue() instanceof NoteContents)) {

			NoteContents nc = (NoteContents) element.getValue();
			String sfm = nc.getSfm();
			if (sfm == null || sfm.isEmpty())
				sfm = localName;
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			if (!USFM.AUTO_CLOSING_TAGS.containsKey(sfm)) {
				System.out.println("WARNING: Replacing char style \\" + sfm + " by \\no");
				sfm = "no";
			}
			AutoClosingFormatting nextContainer = new AutoClosingFormatting(USFM.AUTO_CLOSING_TAGS.get(sfm));
			containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
			containerStack.add(nextContainer);
			parseElements(result, containerStack, nc.getContent(), context);
			containerStack.remove(nextContainer);
		} else if (localName.equals("optionalLineBreak")) {
			containerStack.get(containerStack.size() - 1).getContent().add(new ParatextCharacterContent.SpecialSpace(false, true));
		} else if (localName.equals("ref")) {
			RefType rt = (RefType) element.getValue();
			ParatextCharacterContentPart ref = Text.from(rt.getContent());

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
					if (parts.length > 3) {
						// second verse
						String v2 = parts[parts.length - 1];
						if (parts.length == 5) {
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
			if(ref != null) {
				if (containerStack.isEmpty()) {
					ParatextCharacterContent container = new ParatextCharacterContent();
					containerStack.add(container);
					result.getContent().add(container);
				}
				containerStack.get(containerStack.size() - 1).getContent().add(ref);
			}
		} else if (localName.equals("w")) {
			PType.W w = (PType.W) element.getValue();
			String sfm = w.getSfm();
			if (sfm == null || sfm.isEmpty())
				sfm = localName;
			AutoClosingFormatting nextContainer = new AutoClosingFormatting(USFM.AUTO_CLOSING_TAGS.get(sfm));
			if (w.getL() != null && !w.getL().isEmpty())
				nextContainer.getAttributes().put("lemma", w.getL());
			if (w.getS() != null && !w.getS().isEmpty())
				nextContainer.getAttributes().put("strong", w.getS());
			if (w.getM() != null && !w.getM().isEmpty())
				nextContainer.getAttributes().put("x-morph", w.getM());
			if (w.getSrcloc() != null && !w.getSrcloc().isEmpty())
				nextContainer.getAttributes().put("srcloc", w.getSrcloc());
			if (w.isPlural() != null)
				nextContainer.getAttributes().put("x-plural", "" + w.isPlural());
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
			containerStack.add(nextContainer);
			parseElements(result, containerStack, w.getContent(), context);
			containerStack.remove(nextContainer);
		} else if (localName.equals("quoteStart")) {
			PType.QuoteStart qs = (PType.QuoteStart) element.getValue();
			Text text = Text.from(qs.getValue());
			if(text != null) {
				if (containerStack.isEmpty()) {
					ParatextCharacterContent container = new ParatextCharacterContent();
					containerStack.add(container);
					result.getContent().add(container);
				}
				containerStack.get(containerStack.size() - 1).getContent().add(text);
			}
		} else if (localName.equals("quoteRemind") || localName.equals("quoteEnd")) {
			Text text = Text.from((String) element.getValue());
			if(text != null) {
				if (containerStack.isEmpty()) {
					ParatextCharacterContent container = new ParatextCharacterContent();
					containerStack.add(container);
					result.getContent().add(container);
				}
				containerStack.get(containerStack.size() - 1).getContent().add(text);
			}
		} else if (Arrays.asList("rq", "em", "qt", "nd", "tl", "qs", "qac", "sls", "dc", "bk", "k", "add", "sig", "bd", "it", "bdit", "sc", "wj", "cs").contains(localName)) {
			PType v = (PType) element.getValue();
			String sfm = v.getSfm();
			if (sfm == null || sfm.isEmpty())
				sfm = localName;
			if (!USFM.AUTO_CLOSING_TAGS.containsKey(sfm)) {
				System.out.println("WARNING: Replacing char style \\" + sfm + " by \\no");
				sfm = "no";
			}
			AutoClosingFormatting nextContainer = new AutoClosingFormatting(USFM.AUTO_CLOSING_TAGS.get(sfm));
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);
			containerStack.add(nextContainer);
			parseElements(result, containerStack, v.getContent(), context);
			containerStack.remove(nextContainer);
		} else if (Arrays.asList("pn", "ord", "no", "ndx", "wh", "wg", "ior").contains(localName)) {
			AutoClosingFormatting nextContainer = new AutoClosingFormatting(USFM.AUTO_CLOSING_TAGS.get(localName));
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			containerStack.get(containerStack.size() - 1).getContent().add(nextContainer);

			Text text = Text.from((String) element.getValue());
			if(text != null) {
				nextContainer.getContent().add(text);
			}
		} else if (localName.equals("milestone")) {
			if (containerStack.isEmpty()) {
				ParatextCharacterContent container = new ParatextCharacterContent();
				containerStack.add(container);
				result.getContent().add(container);
			}
			Milestone ms = (Milestone) element.getValue();
			String tag = ms.getSfm() + (ms.getLevel() == null ? "" : ms.getLevel());
			String attr = ms.getAttribute();
			ParatextCharacterContentPart pccp = null;
			if (tag.startsWith("z") && attr.equals("z")) {
				if (tag.endsWith("*")) {
					pccp = new ParatextCharacterContent.CustomMarkup(tag.substring(0, tag.length() - 1), true);
				} else {
					pccp = new ParatextCharacterContent.CustomMarkup(tag, false);
				}
			} else if ((tag.startsWith("z") || tag.matches("qt[1-5]?(-[se])?|ts?(\\-[se])?"))) {
				ParatextCharacterContent.Milestone pms = new ParatextCharacterContent.Milestone(tag);
				pccp = pms;
				while (!attr.isEmpty()) {
					int pos = attr.indexOf("=\"");
					int pos2 = attr.indexOf('"', pos + 2);
					if (pos == -1 || pos2 == -1) {
						System.out.println("WARNING: Skipped malformed milestone attributes: " + attr);
						break;
					}
					pms.getAttributes().put(attr.substring(0, pos), attr.substring(pos + 2, pos2));
					attr = attr.substring(pos2 + 1).trim();
				}
			} else {
				System.out.println("WARNING: Skipping unsupported milestone: \\" + tag);
			}
			if (pccp != null) {
				containerStack.get(containerStack.size() - 1).getContent().add(pccp);
			}
		} else {
			System.out.println("WARNING: Unexpected tag: " + localName);
		}
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
		if (!Boolean.getBoolean("biblemulticonverter.skipxmlvalidation"))
			m.setSchema(getSchema());
		m.marshal(usfx, doc);
		doc.getDocumentElement().setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		doc.getDocumentElement().setAttribute("xsi:noNamespaceSchemaLocation", "https://eBible.org/usfx.xsd");
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		transformer.transform(new DOMSource(doc), new StreamResult(file));
	}

	private Book createBook(ParatextBook book) {
		ObjectFactory of = new ObjectFactory();
		Book result = of.createUsfxBook();
		result.setId(book.getId().getIdentifier());
		if (!book.getBibleName().isEmpty()) {
			Usfx.Book.Id id = of.createUsfxBookId();
			id.setId(book.getId().getIdentifier());
			id.setValue(book.getBibleName());
			result.getContent().add(of.createUsfxBookId(id));
		}
		for (Map.Entry<String, String> attr : book.getAttributes().entrySet()) {
			String key = attr.getKey().replaceFirst("@[0-9]+$", ""), value = attr.getValue();
			if(key.equals("sts")) {
				key="rem";
				value = "@@STATUS@@ "+value;
			} else if (key.matches("h[123]?")) {
				Usfx.Book.H h = of.createUsfxBookH();
				if (key.length() == 2) {
					h.setLevel((short) (key.charAt(1) - '0'));
				}
				h.setValue(value);
				result.getContent().add(of.createUsfxBookH(h));
				continue;
			} else if (key.matches("toca?[123]")) {
				StyledString ss = of.createStyledString();
				ss.setStyle(key);
				ss.setSfm(key.substring(0, key.length() - 1));
				ss.setLevel((short) (key.charAt(key.length() - 1) - '0'));
				ss.getContent().add(value);
				result.getContent().add(of.createUsfxBookToc(ss));
				continue;
			}
			result.getContent().add(new JAXBElement<String>(new QName("", key), String.class, Usfx.class, value));
		}
		book.accept(new ParatextBookContentVisitor<RuntimeException>() {

			PType openParagraph = null;
			Table openTable = null;
			Usfx.Book.Cp openCp = null;
			JAXBElement<String> openCl = null;
			boolean inSidebar = false;

			@Override
			public void visitChapterStart(ChapterIdentifier chapter) throws RuntimeException {
				Usfx.Book.C c = of.createUsfxBookC();
				c.setId(String.valueOf(chapter.chapter));
				result.getContent().add(of.createUsfxBookC(c));
				openParagraph = null;
				openTable = null;
				openCp = null;
				openCl = null;
			}

			@Override
			public void visitChapterEnd(ChapterIdentifier chapter) throws RuntimeException {
			}

			@Override
			public void visitRemark(String content) throws RuntimeException {
				result.getContent().add(of.createPTypeRem(content));
				openParagraph = null;
				openTable = null;
				openCp = null;
				openCl = null;
			}

			@Override
			public void visitParagraphStart(ParagraphKind kind) throws RuntimeException {
				if (inSidebar) return;
				openCp = null;
				openCl = null;
				openParagraph = null;
				if (kind == ParagraphKind.TABLE_ROW) {
					if (openTable == null) {
						openTable = of.createUsfxBookTable();
						result.getContent().add(of.createUsfxBookTable(openTable));
					}
					openTable.getTr().add(of.createUsfxBookTableTr());
				} else if (kind == ParagraphKind.CHAPTER_LABEL) {
					openTable = null;
					openCl = of.createUsfxBookCl("");
					result.getContent().add(openCl);
				} else if (kind == ParagraphKind.CHAPTER_PRESENTATION) {
					openTable = null;
					openCp = of.createUsfxBookCp();
					openCp.setId("");
					result.getContent().add(of.createUsfxBookCp(openCp));
				} else if (kind == ParagraphKind.BLANK_LINE) {
					openTable = null;
					Usfx.Book.B b = of.createUsfxBookB();
					b.setStyle(kind.getTag());
					b.setStyle(kind.getTag());
					result.getContent().add(of.createUsfxBookB(b));
				} else {
					openTable = null;
					PType pt = of.createPType();
					pt.setStyle(kind.getTag());
					TagParser parser = new TagParser();
					parser.parse(kind.getTag());
					pt.setSfm(parser.getTag());
					if (parser.getNumber() != -1)
						pt.setLevel((short) parser.getNumber());
					String tag = parser.getTag();
					if (!Arrays.asList("p", "q", "d", "s", "mt").contains(tag))
						tag = "p";
					result.getContent().add(new JAXBElement<PType>(new QName("", tag), PType.class, Usfx.Book.class, pt));
					openParagraph = pt;
				}
			}

			@Override
			public void visitTableCellStart(String tag) throws RuntimeException {
				if (inSidebar) return;
				if (openTable == null) {
					System.out.println("WARNING: Table cell outside of table");
					return;
				}
				if (tag.contains("-")) {
					Matcher m = Utils.compilePattern("(t[hcr]+)([0-9]+)-([0-9]+)").matcher(tag);
					if (!m.matches())
						throw new RuntimeException("Unsupported table tag " + tag);
					String prefix = m.group(1);
					int min = Integer.parseInt(m.group(2));
					int max = Integer.parseInt(m.group(3));
					for (int i = min; i <= max; i++) {
						visitTableCellStart(prefix + i);
					}
					return;
				}
				PType cell = of.createPType();
				TagParser tp = new TagParser();
				tp.parse(tag);
				cell.setSfm(tp.getTag());
				cell.setLevel((short) tp.getNumber());
				cell.setStyle(tag);
				openTable.getTr().get(openTable.getTr().size()-1).getThOrThrOrTc().add(new JAXBElement<PType>(new QName("", tp.getTag()), PType.class, Usfx.Book.Table.Tr.class, cell));
				openParagraph = cell;
			}

			@Override
			public void visitSidebarStart(String[] categories) throws RuntimeException {
				System.out.println("WARNING: Sidebars are not supported");
				inSidebar = true;
			}

			@Override
			public void visitSidebarEnd() throws RuntimeException {
				inSidebar = false;
			}

			@Override
			public void visitPeripheralStart(String title, String id) throws RuntimeException {
				if (inSidebar) return;
				result.getContent().add(of.createUsfxBookPeriph(title));
			}

			@Override
			public void visitVerseStart(VerseIdentifier location, String verseNumber) throws RuntimeException {
				if (inSidebar) return;
				if (openParagraph == null) {
					Usfx.Book.V v = of.createUsfxBookV();
					v.setId(verseNumber);
					result.getContent().add(of.createUsfxBookV(v));
				} else {
					Usfx.Book.V v = of.createUsfxBookV();
					v.setId(verseNumber);
					openParagraph.getContent().add(of.createUsfxBookV(v));
				}
			}

			@Override
			public void visitVerseEnd(VerseIdentifier verseLocation) throws RuntimeException {
				if (inSidebar) return;
				if (openParagraph == null) {
					result.getContent().add(of.createUsfxBookVe(""));
				} else {
					openParagraph.getContent().add(of.createPTypeVe(""));
				}
			}

			@Override
			public void visitFigure(String caption, Map<String, String> attributes) throws RuntimeException {
				if (inSidebar) return;
				if (openParagraph == null)
					visitParagraphStart(ParatextBook.ParagraphKind.PARAGRAPH_P);
				PType.Fig fig = of.createPTypeFig();
				fig.getContent().add(of.createPTypeFigDescription(attributes.getOrDefault("alt", "")));
				fig.getContent().add(of.createPTypeFigCatalog(attributes.getOrDefault("src", "")));
				fig.getContent().add(of.createPTypeFigSize(attributes.getOrDefault("size", "")));
				fig.getContent().add(of.createPTypeFigLocation(attributes.getOrDefault("loc", "")));
				fig.getContent().add(of.createPTypeFigCopyright(attributes.getOrDefault("copy", "")));
				fig.getContent().add(of.createPTypeFigCaption(caption));
				fig.getContent().add(of.createPTypeFigReference(attributes.getOrDefault("ref", "")));
				openParagraph.getContent().add(of.createPTypeFig(fig));
			}

			@Override
			public void visitParatextCharacterContent(ParatextCharacterContent content) throws RuntimeException {
				if (inSidebar) return;
				if (openCp != null) {
					content.accept(new USFXTextCollectVisitor(null, openCp, null));
				} else if (openCl != null) {
					content.accept(new USFXTextCollectVisitor(openCl, null, null));
				} else {
					if (openParagraph == null)
						visitParagraphStart(ParatextBook.ParagraphKind.PARAGRAPH_P);
					content.accept(new USFXCharacterContentVisitor(of, openParagraph));
				}
			}
		});
		return result;
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		throw new UnsupportedOperationException();
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/usfx.xsd"));
	}

	private static class USFXCharacterContentVisitor implements ParatextCharacterContentVisitor<RuntimeException> {

		private PType parent;
		private ObjectFactory of;

		public USFXCharacterContentVisitor(ObjectFactory of, PType parent) {
			this.of = of;
			this.parent = parent;
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) throws RuntimeException {
			NoteContents nc = of.createNoteContents();
			nc.setSfm(kind.getTag());
			nc.setCaller(caller);
			parent.getContent().add(new JAXBElement<NoteContents>(new QName("", kind.getTag().matches("e?[fx]") ? kind.getTag() : "f"), NoteContents.class, PType.class, nc));
			return new USFXNoteContentVisitor(of, nc);
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws RuntimeException {
			if (kind == AutoClosingFormattingKind.PUBLISHED_VERSE) {
				JAXBElement<String> vp = of.createPTypeVp("");
				parent.getContent().add(vp);
				return new USFXTextCollectVisitor(vp, null, null);
			} else if (kind == AutoClosingFormattingKind.ALTERNATE_VERSE) {
				JAXBElement<String> va = of.createPTypeVa("");
				parent.getContent().add(va);
				return new USFXTextCollectVisitor(va, null, null);
			} else if (kind == AutoClosingFormattingKind.ALTERNATE_CHAPTER) {
				PType.Ca ca = of.createPTypeCa();
				ca.setValue("");
				ca.setId("");
				parent.getContent().add(of.createPTypeCa(ca));
				return new USFXTextCollectVisitor(null, null, ca);
			} else if (kind == AutoClosingFormattingKind.WORDLIST) {
				PType.W w = of.createPTypeW();
				w.setStyle("w");
				w.setSfm("w");
				if (attributes.containsKey("lemma"))
					w.setL(attributes.get("lemma"));
				if (attributes.containsKey("strong"))
					w.setS(attributes.get("strong"));
				if (attributes.containsKey("x-morph"))
					w.setM(attributes.get("x-morph"));
				if (attributes.containsKey("srcloc"))
					w.setSrcloc(attributes.get("srcloc"));
				if (attributes.containsKey("x-plural"))
					w.setPlural(Boolean.parseBoolean(attributes.get("x-plural")));
				parent.getContent().add(of.createPTypeW(w));
				return new USFXCharacterContentVisitor(of, w);
			}
			PType cs = of.createPType();
			String tag = kind.getTag();
			if (!Arrays.asList("rq", "em", "qt", "nd", "tl", "qs", "qac", "sls", "dc", "bk", "k", "add", "sig", "bd", "it", "bdit", "sc", "wj").contains(tag))
				tag = "cs";
			cs.setSfm(kind.getTag());
			cs.setStyle(kind.getTag());
			parent.getContent().add( new JAXBElement<PType>(new QName("", tag), PType.class, PType.class, cs));
			return new USFXCharacterContentVisitor(of, cs);
		}


		@Override
		public void visitMilestone(String tag, Map<String, String> attributes) throws RuntimeException {
			Milestone ms = of.createPTypeMilestone();
			ms.setSfm(tag);
			ms.setAttribute(attributes.entrySet().stream().map(e -> e.getKey()+"=\""+e.getValue()+"\"").collect(Collectors.joining(" ")));
			parent.getContent().add(of.createPTypeMilestone(ms));
		}


		@Override
		public void visitReference(Reference reference) throws RuntimeException {
			RefType rt = of.createRefType();
			rt.setTgt(reference.toString());
			rt.setContent(reference.getContent());
			parent.getContent().add(of.createPTypeRef(rt));
		}


		@Override
		public void visitCustomMarkup(String tag, boolean ending) throws RuntimeException {
			Milestone ms = of.createPTypeMilestone();
			ms.setSfm(tag + (ending ? "*" : ""));
			ms.setAttribute("z");
			parent.getContent().add(of.createPTypeMilestone(ms));
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			parent.getContent().add(text);
		}


		@Override
		public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws RuntimeException {
			if (nonBreakSpace) {
				visitText("\u00A0");
			} else {
				parent.getContent().add(of.createPTypeOptionalLineBreak(""));
			}
		}


		@Override
		public void visitEnd() throws RuntimeException {
		}
	}

	private static class USFXNoteContentVisitor implements ParatextCharacterContentVisitor<RuntimeException> {

		private NoteContents parent;
		private ObjectFactory of;

		public USFXNoteContentVisitor(ObjectFactory of, NoteContents parent) {
			this.of = of;
			this.parent = parent;
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) throws RuntimeException {
			System.out.println("WARNING: Skipping note inside note");
			return null;
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws RuntimeException {
			NoteContents nc = of.createNoteContents();
			String tag = kind.getTag();
			if (!Arrays.asList("nd", "c", "tl", "it", "qt", "sls", "dc", "bdit", "bk", "pn", "k", "ord", "add", "bd", "sc", "wh", "wg", "wr", "wj", "cs", "em").contains(kind.getTag()))
				tag = "cs";
			nc.setSfm(kind.getTag());
			parent.getContent().add(new JAXBElement<NoteContents>(new QName("", tag), NoteContents.class, NoteContents.class, nc));
			return new USFXNoteContentVisitor(of, nc);
		}


		@Override
		public void visitMilestone(String tag, Map<String, String> attributes) throws RuntimeException {
			System.out.println("WARNING: Skipping milestone inside note");
		}

		@Override
		public void visitReference(Reference reference) throws RuntimeException {
			RefType rt = of.createRefType();
			rt.setTgt(reference.toString());
			rt.setContent(reference.getContent());
			parent.getContent().add(of.createNoteContentsRef(rt));
		}


		@Override
		public void visitCustomMarkup(String tag, boolean ending) throws RuntimeException {
			System.out.println("WARNING: Skipping custom markup inside note");
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			parent.getContent().add(text);
		}

		@Override
		public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws RuntimeException {
			if (nonBreakSpace) {
				visitText("\u00A0");
			} else {
				parent.getContent().add(of.createNoteContentsOptionalLineBreak(""));
			}
		}

		@Override
		public void visitEnd() throws RuntimeException {
		}
	}

	private static class USFXTextCollectVisitor implements ParatextCharacterContentVisitor<RuntimeException> {

		private JAXBElement<String> target;
		private Usfx.Book.Cp cpTarget;
		private PType.Ca caTarget;

		public USFXTextCollectVisitor(JAXBElement<String> target, Usfx.Book.Cp cpTarget, PType.Ca caTarget) {
			this.target = target;
			this.cpTarget = cpTarget;
			this.caTarget = caTarget;
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) throws RuntimeException {
			System.out.println("WARNING: Skipping note in text-only tag");
			return null;
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws RuntimeException {
			System.out.println("WARNING: Skipping formatting in text-only tag");
			return this;
		}

		@Override
		public void visitMilestone(String tag, Map<String, String> attributes) throws RuntimeException {
			System.out.println("WARNING: Skipping milestone in text-only tag");
		}

		@Override
		public void visitReference(Reference reference) throws RuntimeException {
			System.out.println("WARNING: Skipping reference in text-only tag");
		}

		@Override
		public void visitCustomMarkup(String tag, boolean ending) throws RuntimeException {
			System.out.println("WARNING: Skipping custom markup in text-only tag");
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			if (target != null) {
				target.setValue(target.getValue() + text);
			} else if (cpTarget != null) {
				cpTarget.setId(cpTarget.getId() + text);
			} else if (caTarget != null) {
				caTarget.setValue(caTarget.getValue() + text);
			} else {
				throw new IllegalStateException();
			}
		}

		@Override
		public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws RuntimeException {
			System.out.println("WARNING: Skipping special whitespace in text-only tag");
		}

		@Override
		public void visitEnd() throws RuntimeException {
		}
	}
}