package biblemulticonverter.format;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.schema.roundtripxml.BibleType;
import biblemulticonverter.schema.roundtripxml.ExtraAttributePrioType;
import biblemulticonverter.schema.roundtripxml.FormattedTextType;
import biblemulticonverter.schema.roundtripxml.FormattedTextType.CrossReference;
import biblemulticonverter.schema.roundtripxml.FormattedTextType.CssFormatting;
import biblemulticonverter.schema.roundtripxml.FormattedTextType.LineBreak;
import biblemulticonverter.schema.roundtripxml.FormattedTextType.RawHTML;
import biblemulticonverter.schema.roundtripxml.FormattedTextType.Variation;
import biblemulticonverter.tools.ValidateXML;
import biblemulticonverter.schema.roundtripxml.FormattingInstructionKindType;
import biblemulticonverter.schema.roundtripxml.LineBreakKindType;
import biblemulticonverter.schema.roundtripxml.ObjectFactory;
import biblemulticonverter.schema.roundtripxml.RawHTMLModeType;

public class RoundtripXML implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Roundtrip XML Export",
			"",
			"Export into a XML file that contains all features supported by the import file."
	};

	@Override
	public Bible doImport(File inputFile) throws Exception {
		ValidateXML.validateFileBeforeParsing(getSchema(), inputFile);
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Unmarshaller u = ctx.createUnmarshaller();
		u.setSchema(getSchema());
		return parseBible(u.unmarshal(new StreamSource(inputFile), BibleType.class));
	}

	protected Bible parseBible(JAXBElement<BibleType> sBible) throws Exception {
		Bible dBible = new Bible(sBible.getValue().getName());
		for (BibleType.Book sBook : sBible.getValue().getBook()) {
			Book dBook = new Book(sBook.getAbbr(), BookID.fromOsisId(sBook.getId().replaceAll("-[0-9]+$", "")), sBook.getShortName(), sBook.getLongName());
			dBible.getBooks().add(dBook);
			for (BibleType.Book.Chapter sChapter : sBook.getChapter()) {
				Chapter dChapter = new Chapter();
				dBook.getChapters().add(dChapter);
				if (sChapter.getProlog() != null) {
					dChapter.setProlog(new FormattedText());
					parseContent(dChapter.getProlog().getAppendVisitor(), sChapter.getProlog().getContent());
					dChapter.getProlog().finished();
				}
				for (BibleType.Book.Chapter.Verse sVerse : sChapter.getVerse()) {
					Verse dVerse = new Verse(sVerse.getNumber());
					dChapter.getVerses().add(dVerse);
					parseContent(dVerse.getAppendVisitor(), sVerse.getContent());
					dVerse.finished();
				}
			}
		}
		return dBible;
	}

	private void parseContent(Visitor<RuntimeException> visitor, List<Serializable> contentList) throws IOException {
		for (Serializable content : contentList) {
			if (content instanceof String) {
				visitor.visitText((String) content);
			} else if (content instanceof JAXBElement<?>) {
				Object value = ((JAXBElement<?>) content).getValue();
				Visitor<RuntimeException> next;
				if (value instanceof FormattedTextType.Headline) {
					next = visitor.visitHeadline(((FormattedTextType.Headline) value).getDepth());
				} else if (value instanceof FormattedTextType.Footnote) {
					next = visitor.visitFootnote();
				} else if (value instanceof FormattedTextType.CrossReference) {
					FormattedTextType.CrossReference xr = (FormattedTextType.CrossReference) value;
					next = visitor.visitCrossReference(xr.getBookAbbr(), BookID.fromOsisId(xr.getBook()), xr.getFirstChapter(), xr.getFirstVerse(), xr.getLastChapter(), xr.getLastVerse());
				} else if (value instanceof FormattedTextType.LineBreak) {
					visitor.visitLineBreak(LineBreakKind.valueOf(((FormattedTextType.LineBreak) value).getKind().name()));
					continue;
				} else if (value instanceof FormattedTextType.DictionaryEntry) {
					FormattedTextType.DictionaryEntry de = (FormattedTextType.DictionaryEntry) value;
					next = visitor.visitDictionaryEntry(de.getDictionary(), de.getEntry());
				} else if (value instanceof FormattedTextType.GrammarInformation) {
					FormattedTextType.GrammarInformation gi = (FormattedTextType.GrammarInformation) value;
					int[] strongs = new int[gi.getStrongs().size()];
					for (int i = 0; i < strongs.length; i++) {
						strongs[i] = gi.getStrongs().get(i);
					}
					String[] rmacs = null;
					if (!gi.getRmac().isEmpty()) {
						rmacs = (String[]) gi.getRmac().toArray(new String[gi.getRmac().size()]);
					}
					int[] sidxs = null;
					if (!gi.getSourceIndices().isEmpty()) {
						sidxs = new int[gi.getSourceIndices().size()];
						for (int i = 0; i < sidxs.length; i++) {
							sidxs[i] = gi.getSourceIndices().get(i);
						}
					}
					next = visitor.visitGrammarInformation(strongs, rmacs, sidxs);
				} else if (value instanceof FormattedTextType.FormattingInstruction) {
					next = visitor.visitFormattingInstruction(FormattingInstructionKind.valueOf(((FormattedTextType.FormattingInstruction) value).getKind().name()));
				} else if (value instanceof FormattedTextType.CssFormatting) {
					next = visitor.visitCSSFormatting(((FormattedTextType.CssFormatting) value).getCss());
				} else if (value instanceof FormattedTextType.ExtraAttribute) {
					FormattedTextType.ExtraAttribute xa = (FormattedTextType.ExtraAttribute) value;
					next = visitor.visitExtraAttribute(ExtraAttributePriority.valueOf(xa.getPrio().name()), xa.getCategory(), xa.getKey(), xa.getValue());
				} else if (value instanceof FormattedTextType.Variation) {
					List<String> vars = ((FormattedTextType.Variation) value).getVariations();
					next = visitor.visitVariationText((String[]) vars.toArray(new String[vars.size()]));
				} else if (value instanceof FormattedTextType.RawHTML) {
					FormattedTextType.RawHTML rh = (FormattedTextType.RawHTML) value;
					visitor.visitRawHTML(RawHTMLMode.valueOf(rh.getMode().name()), rh.getValue());
					continue;
				} else if (value instanceof FormattedTextType.VerseSeparator) {
					visitor.visitVerseSeparator();
					continue;
				} else {
					throw new IOException("Invalid JAXBElement value: " + value.getClass());
				}
				parseContent(next, ((FormattedTextType) value).getContent());
			} else {
				throw new IOException("Invalid content: " + content.getClass());
			}
		}
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		File file = new File(exportArgs[0]);
		JAXBElement<BibleType> result = createBible(bible);
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Marshaller m = ctx.createMarshaller();
		m.setSchema(getSchema());
		m.marshal(result, file);
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/RoundtripXML.xsd"));
	}

	protected JAXBElement<BibleType> createBible(Bible sBible) throws Exception {
		ObjectFactory of = new ObjectFactory();
		JAXBElement<BibleType> dBible = of.createBible(of.createBibleType());
		dBible.getValue().setName(sBible.getName());
		int dictCounter = 0;
		for (Book sBook : sBible.getBooks()) {
			BibleType.Book dBook = of.createBibleTypeBook();
			dBook.setAbbr(sBook.getAbbr());
			if (sBook.getId().equals(BookID.DICTIONARY_ENTRY)) {
				dBook.setId(sBook.getId().getOsisID() + "-" + (++dictCounter));
			} else {
				dBook.setId(sBook.getId().getOsisID());
			}
			dBook.setShortName(sBook.getShortName());
			dBook.setLongName(sBook.getLongName());
			dBible.getValue().getBook().add(dBook);
			for (Chapter sChapter : sBook.getChapters()) {
				BibleType.Book.Chapter dChapter = of.createBibleTypeBookChapter();
				dBook.getChapter().add(dChapter);
				if (sChapter.getProlog() != null) {
					dChapter.setProlog(of.createBibleTypeBookChapterProlog());
					sChapter.getProlog().accept(new CreateContentVisitor(of, dChapter.getProlog().getContent()));
				}
				for (Verse sVerse : sChapter.getVerses()) {
					BibleType.Book.Chapter.Verse dVerse = of.createBibleTypeBookChapterVerse();
					dChapter.getVerse().add(dVerse);
					dVerse.setNumber(sVerse.getNumber());
					sVerse.accept(new CreateContentVisitor(of, dVerse.getContent()));
				}
			}
		}
		return dBible;
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return true;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private static class CreateContentVisitor implements Visitor<IOException> {

		private final List<Serializable> result;
		private final ObjectFactory of;

		private CreateContentVisitor(ObjectFactory of, List<Serializable> result) {
			this.of = of;
			this.result = result;
		}

		@Override
		public int visitElementTypes(String elementTypes) throws IOException {
			return 0;
		}

		@Override
		public void visitStart() throws IOException {
		}

		@Override
		public boolean visitEnd() throws IOException {
			return false;
		}

		@Override
		public void visitText(String text) throws IOException {
			result.add(text);
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			result.add(of.createFormattedTextTypeVerseSeparator(of.createFormattedTextTypeVerseSeparator()));
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws IOException {
			LineBreak lb = of.createFormattedTextTypeLineBreak();
			lb.setKind(LineBreakKindType.valueOf(kind.name()));
			result.add(of.createFormattedTextTypeLineBreak(lb));
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			FormattedTextType.Headline hl = of.createFormattedTextTypeHeadline();
			hl.setDepth(depth);
			result.add(of.createFormattedTextTypeHeadline(hl));
			return new CreateContentVisitor(of, hl.getContent());
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			FormattedTextType.FormattingInstruction fi = of.createFormattedTextTypeFormattingInstruction();
			fi.setKind(FormattingInstructionKindType.valueOf(kind.name()));
			result.add(of.createFormattedTextTypeFormattingInstruction(fi));
			return new CreateContentVisitor(of, fi.getContent());
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			FormattedTextType.Footnote fn = of.createFormattedTextTypeFootnote();
			result.add(of.createFormattedTextTypeFootnote(fn));
			return new CreateContentVisitor(of, fn.getContent());
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			FormattedTextType.ExtraAttribute xattr = of.createFormattedTextTypeExtraAttribute();
			xattr.setPrio(ExtraAttributePrioType.valueOf(prio.name()));
			xattr.setCategory(category);
			xattr.setKey(key);
			xattr.setValue(value);
			result.add(of.createFormattedTextTypeExtraAttribute(xattr));
			return new CreateContentVisitor(of, xattr.getContent());
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			FormattedTextType.DictionaryEntry de = of.createFormattedTextTypeDictionaryEntry();
			de.setDictionary(dictionary);
			de.setEntry(entry);
			result.add(of.createFormattedTextTypeDictionaryEntry(de));
			return new CreateContentVisitor(of, de.getContent());
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			FormattedTextType.GrammarInformation gi = of.createFormattedTextTypeGrammarInformation();
			for (int strong : strongs)
				gi.getStrongs().add(strong);
			if (rmac != null)
				gi.getRmac().addAll(Arrays.asList(rmac));
			if (sourceIndices != null)
				for (int sidx : sourceIndices)
					gi.getSourceIndices().add(sidx);
			result.add(of.createFormattedTextTypeGrammarInformation(gi));
			return new CreateContentVisitor(of, gi.getContent());
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			CrossReference xr = of.createFormattedTextTypeCrossReference();
			xr.setBookAbbr(bookAbbr);
			xr.setBook(book.getOsisID());
			xr.setFirstChapter(firstChapter);
			xr.setFirstVerse(firstVerse);
			xr.setLastChapter(lastChapter);
			xr.setLastVerse(lastVerse);
			result.add(of.createFormattedTextTypeCrossReference(xr));
			return new CreateContentVisitor(of, xr.getContent());
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			CssFormatting cf = of.createFormattedTextTypeCssFormatting();
			cf.setCss(css);
			result.add(of.createFormattedTextTypeCssFormatting(cf));
			return new CreateContentVisitor(of, cf.getContent());
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
			RawHTML rh = of.createFormattedTextTypeRawHTML();
			rh.setMode(RawHTMLModeType.valueOf(mode.name()));
			rh.setValue(raw);
			result.add(of.createFormattedTextTypeRawHTML(rh));
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			Variation var = of.createFormattedTextTypeVariation();
			var.getVariations().addAll(Arrays.asList(variations));
			result.add(of.createFormattedTextTypeVariation(var));
			return new CreateContentVisitor(of, var.getContent());
		}
	}
}
