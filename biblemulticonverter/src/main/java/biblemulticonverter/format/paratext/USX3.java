package biblemulticonverter.format.paratext;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xml.sax.SAXException;

import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import biblemulticonverter.format.paratext.utilities.UnifiedScriptureXMLWriter;
import biblemulticonverter.schema.usx3.Cell;
import biblemulticonverter.schema.usx3.CellAlign;
import biblemulticonverter.schema.usx3.CellStyle;
import biblemulticonverter.schema.usx3.Chapter;
import biblemulticonverter.schema.usx3.Char;
import biblemulticonverter.schema.usx3.CharStyle;
import biblemulticonverter.schema.usx3.Figure;
import biblemulticonverter.schema.usx3.Note;
import biblemulticonverter.schema.usx3.NoteStyle;
import biblemulticonverter.schema.usx3.ObjectFactory;
import biblemulticonverter.schema.usx3.Optbreak;
import biblemulticonverter.schema.usx3.Para;
import biblemulticonverter.schema.usx3.ParaStyle;
import biblemulticonverter.schema.usx3.Ref;
import biblemulticonverter.schema.usx3.Row;
import biblemulticonverter.schema.usx3.Sidebar;
import biblemulticonverter.schema.usx3.Table;
import biblemulticonverter.schema.usx3.Usx;
import biblemulticonverter.schema.usx3.Verse;
import biblemulticonverter.tools.ValidateXML;
import biblemulticonverter.utilities.UnmarshallerLocationListener;

/**
 * Importer and exporter for USX3.
 */
public class USX3 extends AbstractUSXFormat<ParaStyle, CharStyle> {

	public static final String[] HELP_TEXT = {
			"Version 3 of the XML Bible format used by Paratext and the Digital Bible Library",
			"",
			"Usage (export): USX <outdir> <filenamepattern>",
			"",
			"Point the importer to a directory that contains the .usx version 3 files.",
			"",
			"When exporting, you need to give a file name pattern. You can use # for ",
			"the book number and * for the book name."
	};

	private Map<NoteStyle, ParatextCharacterContent.FootnoteXrefKind> NOTE_STYLE_MAP = new EnumMap<>(NoteStyle.class);
	private Map<ParatextCharacterContent.FootnoteXrefKind, NoteStyle> NOTE_KIND_MAP = new EnumMap<>(ParatextCharacterContent.FootnoteXrefKind.class);

	private UnmarshallerLocationListener unmarshallerLocationListener = new UnmarshallerLocationListener();

	public USX3() {
		super("USX 3", new ParaStyleWrapper(), new CharStyleWrapper());
		prepareNoteMaps();
	}

	private void prepareNoteMaps() {
		NOTE_STYLE_MAP.put(NoteStyle.F, ParatextCharacterContent.FootnoteXrefKind.FOOTNOTE);
		NOTE_STYLE_MAP.put(NoteStyle.EF, ParatextCharacterContent.FootnoteXrefKind.FOOTNOTE);
		NOTE_STYLE_MAP.put(NoteStyle.FE, ParatextCharacterContent.FootnoteXrefKind.ENDNOTE);
		NOTE_STYLE_MAP.put(NoteStyle.X, ParatextCharacterContent.FootnoteXrefKind.XREF);
		NOTE_STYLE_MAP.put(NoteStyle.EX, ParatextCharacterContent.FootnoteXrefKind.XREF);
		NOTE_KIND_MAP.put(ParatextCharacterContent.FootnoteXrefKind.FOOTNOTE, NoteStyle.F);
		NOTE_KIND_MAP.put(ParatextCharacterContent.FootnoteXrefKind.ENDNOTE, NoteStyle.FE);
		NOTE_KIND_MAP.put(ParatextCharacterContent.FootnoteXrefKind.XREF, NoteStyle.X);
	}

	@Override
	protected void setupCustomParaMappings() {
		// See: USX.setupCustomParaMappings()

		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_SECTION_3, ParaStyle.IS_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_SECTION_4, ParaStyle.IS_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_SECTION_5, ParaStyle.IS_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_SECTION_6, ParaStyle.IS_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_SECTION_7, ParaStyle.IS_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_SECTION_8, ParaStyle.IS_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_PARAGRAPH_Q4, ParaStyle.IQ_3);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_PARAGRAPH_LI3, ParaStyle.ILI_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_PARAGRAPH_LI4, ParaStyle.ILI_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_MAJOR_TITLE_ENDING_3, ParaStyle.IMTE_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.INTRO_MAJOR_TITLE_ENDING_4, ParaStyle.IMTE_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.MAJOR_TITLE_ENDING_3, ParaStyle.MTE_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.MAJOR_TITLE_ENDING_4, ParaStyle.MTE_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.MAJOR_SECTION_4, ParaStyle.MS_3);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.MAJOR_SECTION_5, ParaStyle.MS_3);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.SECTION_5, ParaStyle.S_4);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_PI4, ParaStyle.PI_3);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_QM4, ParaStyle.QM_3);

		// See: USX.setupCustomParaMappings() and https://ubsicap.github.io/usx/v3.0.0/parastyles.html#ph
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_HANGING, ParaStyle.LI);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_HANGING1, ParaStyle.LI_1);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_HANGING2, ParaStyle.LI_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_HANGING3, ParaStyle.LI_3);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_HANGING4, ParaStyle.LI_4);
	}

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		if (!inputFile.getName().toLowerCase().endsWith(".usx"))
			return null;
		ValidateXML.validateFileBeforeParsing(getSchema(), inputFile);
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());

		XMLInputFactory xif = XMLInputFactory.newFactory();
		XMLStreamReader xsr = xif.createXMLStreamReader(new FileInputStream(inputFile));
		Unmarshaller u = ctx.createUnmarshaller();
		u.setListener(unmarshallerLocationListener);
		unmarshallerLocationListener.setXMLStreamReader(inputFile.getName(), xsr);
		Usx doc = (Usx) u.unmarshal(xsr);
		xsr.close();

		ParatextBook.ParatextID id = ParatextBook.ParatextID.fromIdentifier(doc.getBook().getCode().toUpperCase());
		if (id == null) {
			System.out.println("WARNING: Skipping book with unknown ID: " + doc.getBook().getCode());
			return null;
		}
		ParatextBook result = new ParatextBook(id, doc.getBook().getContent());
		ParatextCharacterContent charContent = null;
		for (Object o : doc.getParaOrTableOrChapter()) {
			if (o instanceof Para) {
				Para para = (Para) o;
				if (BOOK_HEADER_ATTRIBUTE_TAGS.contains(para.getStyle().value())) {
					String value = "";
					for (Object oo : para.getContent()) {
						if (oo instanceof String) {
							value += ((String) oo).replaceAll("[ \r\n\t]+", " ");
						} else {
							throw new RuntimeException("Unsupported content in attribute: " + oo.getClass());
						}
					}
					result.getAttributes().put(para.getStyle().value(), value);
					charContent = null;
				} else if (para.getStyle() == ParaStyle.PB) {
					if (charContent == null) {
						charContent = new ParatextCharacterContent();
						result.getContent().add(charContent);
					}
					charContent.getContent().add(new ParatextCharacterContent.AutoClosingFormatting(ParatextCharacterContent.AutoClosingFormattingKind.PAGE_BREAK, false));
				} else if (PARA_STYLE_UNSUPPORTED.contains(para.getStyle())) {
					// skip
					charContent = null;
				} else {
					result.getContent().add(new ParatextBook.ParagraphStart(PARA_STYLE_MAP.get(para.getStyle())));
					charContent = null;
					if (!para.getContent().isEmpty()) {
						charContent = new ParatextCharacterContent();
						result.getContent().add(charContent);
						parseCharContent(para.getContent(), charContent);
					}
				}
			} else if (o instanceof Table) {
				Table table = (Table) o;
				for (Row row : table.getRow()) {
					result.getContent().add(new ParatextBook.ParagraphStart(ParatextBook.ParagraphKind.TABLE_ROW));
					for (Object oo : row.getVerseOrCell()) {
						if (oo instanceof Verse) {
							Verse verse = (Verse) oo;
							ParatextCharacterContent.ParatextCharacterContentPart verseStartOrEnd = handleVerse(verse);
							charContent = new ParatextCharacterContent();
							result.getContent().add(charContent);
							charContent.getContent().add(verseStartOrEnd);
						} else if (oo instanceof Cell) {
							Cell cell = (Cell) oo;
							result.getContent().add(new ParatextBook.TableCellStart(cell.getStyle().value()));
							charContent = new ParatextCharacterContent();
							result.getContent().add(charContent);
							parseCharContent(cell.getContent(), charContent);
						} else {
							throw new IOException("Unsupported table row element: " + o.getClass().getName());
						}
					}
				}
				charContent = null;
			} else if (o instanceof Chapter) {
				Chapter chapter = (Chapter) o;
				if (chapter.getSid() != null) {
					// Assume start chapter
					result.getContent().add(new ParatextBook.ChapterStart(new ChapterIdentifier(result.getId(), ((Chapter) o).getNumber().intValue())));
				} else if (chapter.getEid() != null) {
					// Assume end chapter
					ChapterIdentifier location = ChapterIdentifier.fromLocationString(chapter.getEid());
					if (location == null) {
						throw new IOException("Invalid chapter eid found: " + chapter.getEid());
					}
					result.getContent().add(new ParatextBook.ChapterEnd(location));
				} else {
					throw new IOException("Invalid chapter found, both sid and eid are undefined: " + chapter);
				}
				charContent = null;
			} else if (o instanceof Note) {
				if (charContent == null) {
					charContent = new ParatextCharacterContent();
					result.getContent().add(charContent);
				}
				Note note = (Note) o;
				ParatextCharacterContent.FootnoteXref nx = new ParatextCharacterContent.FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller());
				charContent.getContent().add(nx);
				parseCharContent(note.getContent(), nx);
			} else if (o instanceof Sidebar) {
				System.out.println("WARNING: Skipping sidebar (study bible content)");
				charContent = null;
			} else {
				throw new IOException("Unsupported book level element: " + o.getClass().getName());
			}
		}

		return result;
	}

	private void parseCharContent(List<Object> content, ParatextBook.ParatextCharacterContentContainer container) throws IOException {
		for (Object o : content) {
			if (o instanceof Optbreak) {
				// is ignored in USFM as well
				System.out.println("WARNING: Skipping optional break");
			} else if (o instanceof Ref) {
				Ref r = (Ref) o;
				try {
					container.getContent().add(ParatextCharacterContent.Reference.parse(r.getLoc(), r.getContent()));
				} catch (IllegalArgumentException e) {
					String location = unmarshallerLocationListener.getHumanReadableLocation(o);
					System.out.println("WARNING: Unsupported structured reference format at " + location + " - replaced by plain text: " + r.getLoc());
					final ParatextCharacterContent.Text text = ParatextCharacterContent.Text.from(r.getContent());
					if(text != null) {
						container.getContent().add(text);
					}
				}
			} else if (o instanceof String) {
				final ParatextCharacterContent.Text text = ParatextCharacterContent.Text.from((String) o);
				if(text != null) {
					container.getContent().add(text);
				}
			} else if (o instanceof Figure) {
				System.out.println("WARNING: Skipping figure");
			} else if (o instanceof Char) {
				Char chr = (Char) o;
				if (CHAR_STYLE_UNSUPPORTED.contains(chr.getStyle())) {
					parseCharContent(chr.getContent(), container);
				} else {
					ParatextCharacterContent.AutoClosingFormatting f = new ParatextCharacterContent.AutoClosingFormatting(CHAR_STYLE_MAP.get(chr.getStyle()), false);
					String lemma = chr.getLemma();
					if (f.getKind() == ParatextCharacterContent.AutoClosingFormattingKind.WORDLIST && lemma != null && !lemma.isEmpty()) {
						f.getAttributes().put("lemma", lemma);
					}
					container.getContent().add(f);
					parseCharContent(chr.getContent(), f);
				}
			} else if (o instanceof Verse) {
				container.getContent().add(handleVerse((Verse) o));
			} else if (o instanceof Note) {
				Note note = (Note) o;
				ParatextCharacterContent.FootnoteXref nx = new ParatextCharacterContent.FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller());
				container.getContent().add(nx);
				parseCharContent(note.getContent(), nx);
			} else {
				throw new IOException("Unsupported character content element: " + o.getClass().getName());
			}
		}
	}

	private ParatextCharacterContent.ParatextCharacterContentPart handleVerse(Verse verse) throws IOException {
		try {
			if (verse.getSid() != null) {
				return new ParatextCharacterContent.VerseStart(
						VerseIdentifier.fromStringOrThrow(verse.getSid()),
						verse.getNumber());
			} else if (verse.getEid() != null) {
				return new ParatextCharacterContent.VerseEnd(
						VerseIdentifier.fromStringOrThrow(verse.getEid()));
			} else {
				throw new IOException("Invalid verse found, both sid and eid are undefined: " + verse);
			}
		} catch (IllegalArgumentException e) {
			throw new IOException(e);
		}
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws Exception {
		ObjectFactory of = new ObjectFactory();
		Usx usx = of.createUsx();
		usx.setVersion("3.0");
		usx.setBook(of.createBook());
		usx.getBook().setStyle("id");
		usx.getBook().setCode(book.getId().getIdentifier());
		usx.getBook().setContent(book.getBibleName());

		for (Map.Entry<String, String> attr : book.getAttributes().entrySet()) {
			Para para = new Para();
			para.setStyle(ParaStyle.fromValue(attr.getKey()));
			para.getContent().add(attr.getValue());
			usx.getParaOrTableOrChapter().add(para);
		}

		book.accept(new ParatextBook.ParatextBookContentVisitor<IOException>() {

			List<Object> currentContent = null;
			Table currentTable = null;

			@Override
			public void visitChapterStart(ChapterIdentifier location) throws IOException {
				Chapter ch = new Chapter();
				ch.setStyle("c");
				ch.setSid(location.toString());
				ch.setNumber(BigInteger.valueOf(location.chapter));
				usx.getParaOrTableOrChapter().add(ch);
				currentContent = null;
				currentTable = null;
			}

			@Override
			public void visitChapterEnd(ChapterIdentifier location) throws IOException {
				Chapter ch = new Chapter();
				ch.setEid(location.toString());
				usx.getParaOrTableOrChapter().add(ch);
				currentContent = null;
				currentTable = null;
			}

			@Override
			public void visitParagraphStart(ParatextBook.ParagraphKind kind) throws IOException {
				if (kind == ParatextBook.ParagraphKind.TABLE_ROW) {
					if (currentTable == null) {
						currentTable = new Table();
						usx.getParaOrTableOrChapter().add(currentTable);
					}
					currentTable.getRow().add(new Row());
					currentContent = currentTable.getRow().get(currentTable.getRow().size() - 1).getVerseOrCell();
				} else {
					Para para = new Para();
					para.setStyle(PARA_KIND_MAP.get(kind));
					usx.getParaOrTableOrChapter().add(para);
					currentContent = para.getContent();
					currentTable = null;
				}
			}

			@Override
			public void visitTableCellStart(String tag) throws IOException {
				if (currentTable == null) {
					System.out.println("WARNING: Table cell outside of table");
					return;
				}
				Row currentRow = currentTable.getRow().get(currentTable.getRow().size() - 1);
				Cell cell = new Cell();
				cell.setAlign(tag.contains("r") ? CellAlign.END : CellAlign.START);
				cell.setStyle(CellStyle.valueOf(tag));
				currentRow.getVerseOrCell().add(cell);
				currentContent = cell.getContent();
			}

			@Override
			public void visitParatextCharacterContent(ParatextCharacterContent content) throws IOException {
				if (currentContent == null)
					visitParagraphStart(ParatextBook.ParagraphKind.PARAGRAPH_P);
				content.accept(new USX3.USXCharacterContentVisitor(currentContent));
			}
		});

		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Marshaller m = ctx.createMarshaller();
		m.setSchema(getSchema());
		m.marshal(usx, new UnifiedScriptureXMLWriter(new FileWriter(outFile), "UTF-8"));
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/usx3.xsd"));
	}

	private class USXCharacterContentVisitor implements ParatextCharacterContent.ParatextCharacterContentVisitor<IOException> {
		private List<Object> target;

		public USXCharacterContentVisitor(List<Object> target) {
			this.target = target;
		}

		@Override
		public void visitVerseStart(VerseIdentifier location, String verseNumber) throws IOException {
			if (!target.isEmpty() && verseSeparatorText != null)
				target.add(verseSeparatorText);
			Verse verse = new Verse();
			verse.setStyle("v");
			verse.setSid(location.toString());
			verse.setNumber(verseNumber);
			target.add(verse);
		}

		@Override
		public ParatextCharacterContent.ParatextCharacterContentVisitor<IOException> visitFootnoteXref(ParatextCharacterContent.FootnoteXrefKind kind, String caller) throws IOException {
			Note note = new Note();
			note.setCaller(caller);
			note.setStyle(NOTE_KIND_MAP.get(kind));
			target.add(note);
			return new USX3.USXCharacterContentVisitor(note.getContent());
		}

		@Override
		public ParatextCharacterContent.ParatextCharacterContentVisitor<IOException> visitAutoClosingFormatting(ParatextCharacterContent.AutoClosingFormattingKind kind, Map<String, String> attributes) throws IOException {
			if (kind == ParatextCharacterContent.AutoClosingFormattingKind.PAGE_BREAK) {
				System.out.println("WARNING: Ignored page break");
				return null;
			} else {
				Char chr = new Char();
				chr.setLemma(attributes.get("lemma"));
				chr.setStrong(attributes.get("strong"));
				chr.setSrcloc(attributes.get("srcloc"));
				chr.setStyle(CHAR_KIND_MAP.get(kind));
				target.add(chr);
				return new USX3.USXCharacterContentVisitor(chr.getContent());
			}
		}

		@Override
		public void visitReference(ParatextCharacterContent.Reference reference) throws IOException {
			Ref ref = new Ref();
			ref.setLoc(reference.toString());
			ref.setContent(reference.getContent());
			target.add(ref);
		}

		@Override
		public void visitText(String text) throws IOException {
			target.add(text);
		}

		@Override
		public void visitEnd() throws IOException {
		}

		@Override
		public void visitVerseEnd(VerseIdentifier location) throws IOException {
			Verse verse = new Verse();
			verse.setEid(location.toString());
			target.add(verse);
		}
	}

	@Override
	boolean isAutoClosingFormattingKindSupported(ParatextCharacterContent.AutoClosingFormattingKind kind) {
		// See AutoClosingFormattingKind.PAGE_BREAK for why it is unsupported as AutoClosingFormattingKind.
		return kind != ParatextCharacterContent.AutoClosingFormattingKind.PAGE_BREAK;
	}

	@Override
	boolean isParagraphKindSupported(ParatextBook.ParagraphKind kind) {
		return true;
	}

	private static class CharStyleWrapper extends StyleWrapper<CharStyle> {

		private static Set<CharStyle> CHAR_STYLE_UNSUPPORTED = Collections.unmodifiableSet(EnumSet.of(
				// "Second (alternate) verse number"
				CharStyle.VA,
				// "Second (alternate) chapter number"
				CharStyle.CA,
				// "Reference to caller of previous footnote"
				CharStyle.FM,
				// "Reference to caller of previous footnote in a study Bible"
				CharStyle.EFM,
				// "Note category (study Bible)"
				CharStyle.CAT,
				// Linking attributes (https://ubsicap.github.io/usx/v3.0.0/linking.html) are not supported, therefore also
				// the special JMP placeholder is not supported.
				CharStyle.JMP,
				// Handling of Ruby glossing is currently not supported
				CharStyle.RB
		));

		@Override
		Class<CharStyle> getStyleClass() {
			return CharStyle.class;
		}

		@Override
		Set<CharStyle> getUnsupportedStyles() {
			return CHAR_STYLE_UNSUPPORTED;
		}

		@Override
		CharStyle[] values() {
			return CharStyle.values();
		}

		@Override
		String tag(CharStyle charStyle) {
			return charStyle.value();
		}
	}

	private static class ParaStyleWrapper extends StyleWrapper<ParaStyle> {

		private static Set<ParaStyle> PARA_STYLE_UNSUPPORTED = Collections.unmodifiableSet(EnumSet.of(
				ParaStyle.RESTORE,
				ParaStyle.CP,
				ParaStyle.CL,
				ParaStyle.K_1,
				ParaStyle.K_2,
				// No documentation available on the TS tag except for the small comment available in the Relax NG spec
				// for USX 3.0:
				// "Translators chunk (to identify chunks of text suitable for translating at one time)"
				ParaStyle.TS
		));

		@Override
		Class<ParaStyle> getStyleClass() {
			return ParaStyle.class;
		}

		@Override
		Set<ParaStyle> getUnsupportedStyles() {
			return PARA_STYLE_UNSUPPORTED;
		}

		@Override
		ParaStyle[] values() {
			return ParaStyle.values();
		}

		@Override
		String tag(ParaStyle paraStyle) {
			return paraStyle.value();
		}
	}
}