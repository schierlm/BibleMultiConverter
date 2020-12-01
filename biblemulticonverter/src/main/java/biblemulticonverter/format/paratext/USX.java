package biblemulticonverter.format.paratext;

import biblemulticonverter.format.paratext.ParatextBook.ChapterStart;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.VerseStart;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import biblemulticonverter.format.paratext.model.Version;
import biblemulticonverter.format.paratext.utilities.ImportUtilities;
import biblemulticonverter.format.paratext.utilities.StandardExportLogMessages;
import biblemulticonverter.format.paratext.utilities.UsxTextUtilities;
import biblemulticonverter.schema.usx.Cell;
import biblemulticonverter.schema.usx.CellAlign;
import biblemulticonverter.schema.usx.CellStyle;
import biblemulticonverter.schema.usx.Chapter;
import biblemulticonverter.schema.usx.Char;
import biblemulticonverter.schema.usx.CharStyle;
import biblemulticonverter.schema.usx.Figure;
import biblemulticonverter.schema.usx.Note;
import biblemulticonverter.schema.usx.NoteStyle;
import biblemulticonverter.schema.usx.ObjectFactory;
import biblemulticonverter.schema.usx.Optbreak;
import biblemulticonverter.schema.usx.Para;
import biblemulticonverter.schema.usx.ParaStyle;
import biblemulticonverter.schema.usx.Ref;
import biblemulticonverter.schema.usx.Row;
import biblemulticonverter.schema.usx.Sidebar;
import biblemulticonverter.schema.usx.Table;
import biblemulticonverter.schema.usx.Usx;
import biblemulticonverter.schema.usx.Verse;
import biblemulticonverter.tools.ValidateXML;
import biblemulticonverter.utilities.UnmarshallerLocationListener;
import org.xml.sax.SAXException;

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
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Importer and exporter for USX.
 */
public class USX extends AbstractUSXFormat<ParaStyle, CharStyle> {

	public static final String[] HELP_TEXT = {
			"Version 2 of the XML Bible format used by Paratext and the Digital Bible Library",
			"",
			"Usage (export): USX <outdir> <filenamepattern>",
			"",
			"Point the importer to a directory that contains the .usx version 2 files.",
			"",
			"When exporting, you need to give a file name pattern. You can use # for ",
			"the book number and * for the book name."
	};

	private static final Set<AutoClosingFormattingKind> USX_2_AUTO_CLOSING_FORMATTING_KINDS = AutoClosingFormattingKind.allForVersion(Version.V2_2);
	private static final Set<ParagraphKind> USX_2_PARAGRAPH_KINDS = ParagraphKind.allForVersion(Version.V2_2);

	private Map<NoteStyle, FootnoteXrefKind> NOTE_STYLE_MAP = new EnumMap<>(NoteStyle.class);
	private Map<FootnoteXrefKind, NoteStyle> NOTE_KIND_MAP = new EnumMap<>(FootnoteXrefKind.class);

	private final StandardExportLogMessages logger = new StandardExportLogMessages("USX 2");
	private UnmarshallerLocationListener unmarshallerLocationListener = new UnmarshallerLocationListener();

	public USX() {
		super(new ParaStyleWrapper(), new CharStyleWrapper());
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
		// These mappings are for markers that are known in the schema but without the right "number", and therefore mapped
		// to the closest number that is known.
		// TODO maybe it is better to add them to the schema, the tags are valid, it is just that the numbers are not
		// known and this behaviour does not happen in the USFM export, since it does not use a schema it will just
		// export these ParagraphKinds.
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

		// This one is a bit weird, see: https://github.com/ubsicap/usx/issues/48
		// According to documentation the use of Paragraph style `ph#` is strongly discouraged, it is not deprecated
		// or removed. However it is not part of the USX 2 schema, which is a bit weird. For now we do the recommended
		// thing and map this one to the recommended alternative: `li#`
		// https://ubsicap.github.io/usx/v2.5/parastyles.html#usx-parastyle-ph
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_HANGING, ParaStyle.LI);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_HANGING1, ParaStyle.LI_1);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_HANGING2, ParaStyle.LI_2);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_HANGING3, ParaStyle.LI_3);
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_HANGING4, ParaStyle.LI_4);

		// The Paragraph style `pr` was deprecated in USX 2, however restored in V3. It was never fully removed, however
		// it is not part of the USX 2 schema, the recommended alternative is PRM.
		// https://ubsicap.github.io/usx/v2.5/parastyles.html#usx-parastyle-pr
		// TODO instead add the `pr` style to the USX 2 schema?
		PARA_KIND_MAP.put(ParatextBook.ParagraphKind.PARAGRAPH_RIGHT, ParaStyle.PMR);
	}

	private static class ImportContext {
		VerseStart openVerse = null;
		ChapterStart openChapter = null;
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
		unmarshallerLocationListener.setXMLStreamReader(xsr);
		Usx doc = (Usx) u.unmarshal(xsr);
		xsr.close();

		ParatextID id = ParatextID.fromIdentifier(doc.getBook().getCode().toUpperCase());
		if (id == null) {
			System.out.println("WARNING: Skipping book with unknown ID: " + doc.getBook().getCode());
			return null;
		}

		ParatextBook result = new ParatextBook(id, doc.getBook().getContent());
		ParatextCharacterContent charContent = null;
		ImportContext context = new ImportContext();
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
					charContent.getContent().add(new AutoClosingFormatting(AutoClosingFormattingKind.PAGE_BREAK, false));
				} else if (PARA_STYLE_UNSUPPORTED.contains(para.getStyle())) {
					// skip
					charContent = null;
				} else {
					result.getContent().add(new ParagraphStart(PARA_STYLE_MAP.get(para.getStyle())));
					charContent = null;
					if (!para.getContent().isEmpty()) {
						charContent = new ParatextCharacterContent();
						result.getContent().add(charContent);
						parseCharContent(para.getContent(), charContent, result, context);
					}
				}
			} else if (o instanceof Table) {
				Table table = (Table) o;
				for (Row row : table.getRow()) {
					result.getContent().add(new ParagraphStart(ParagraphKind.TABLE_ROW));
					for (Object oo : row.getVerseOrCell()) {
						if (oo instanceof Verse) {
							ImportUtilities.closeOpenVerse(result, context.openVerse);
							
							context.openVerse = handleVerse(result, (Verse) oo);
							charContent = new ParatextCharacterContent();
							result.getContent().add(charContent);
							charContent.getContent().add(context.openVerse);
						} else if (oo instanceof Cell) {
							Cell cell = (Cell) oo;
							result.getContent().add(new ParatextBook.TableCellStart(cell.getStyle().value()));
							charContent = new ParatextCharacterContent();
							result.getContent().add(charContent);
							parseCharContent(cell.getContent(), charContent, result, context);
						} else {
							throw new IOException("Unsupported table row element: " + o.getClass().getName());
						}
					}
				}
				charContent = null;
			} else if (o instanceof Chapter) {
				ImportUtilities.closeOpenVerse(result, context.openVerse);
				context.openVerse = null;
				
				// There is not really a good way to accurately determine where the end of a chapter should be placed
				// based on USX 2 content. Maybe a title above this chapter marker was already intended to be part of
				// this chapter. This is basically a best guess. This should not really matter when converting from
				// USX 2 to USFM 2 or USFX (which is based on USFM 2), however when up-converting to USX 3 or USFM 3
				// this might lead to unexpected results.
				ImportUtilities.closeOpenChapter(result, context.openChapter);

				context.openChapter = new ChapterStart(new ChapterIdentifier(result.getId(), ((Chapter) o).getNumber().intValue()));
				result.getContent().add(context.openChapter);
				charContent = null;
			} else if (o instanceof Note) {
				if (charContent == null) {
					charContent = new ParatextCharacterContent();
					result.getContent().add(charContent);
				}
				Note note = (Note) o;
				FootnoteXref nx = new FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller());
				charContent.getContent().add(nx);
				parseCharContent(note.getContent(), nx, result, context);
			} else if (o instanceof Sidebar) {
				System.out.println("WARNING: Skipping sidebar (study bible content)");
				charContent = null;
			} else {
				throw new IOException("Unsupported book level element: " + o.getClass().getName());
			}
		}
		ImportUtilities.closeOpenVerse(result, context.openVerse);
		ImportUtilities.closeOpenChapter(result, context.openChapter);
		return result;
	}
	
	private void parseCharContent(List<Object> content, ParatextCharacterContentContainer container, ParatextBook result, ImportContext context) throws IOException {
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
					container.getContent().add(new ParatextCharacterContent.Text(r.getContent()));
				}
			} else if (o instanceof String) {
				String text = UsxTextUtilities.normalizeText((String) o);
				if (!text.isEmpty()) {
					container.getContent().add(new ParatextCharacterContent.Text(text));
				}
			} else if (o instanceof Figure) {
				System.out.println("WARNING: Skipping figure");
			} else if (o instanceof Char) {
				Char chr = (Char) o;
				if (CHAR_STYLE_UNSUPPORTED.contains(chr.getStyle())) {
					parseCharContent(chr.getContent(), container, result, context);
				} else {
					AutoClosingFormatting f = new AutoClosingFormatting(CHAR_STYLE_MAP.get(chr.getStyle()), false);
					String lemma = chr.getLemma();
					if (f.getKind() == AutoClosingFormattingKind.WORDLIST && lemma != null && !lemma.isEmpty()) {
						f.getAttributes().put("lemma", lemma);
					}
					container.getContent().add(f);
					parseCharContent(chr.getContent(), f, result, context);
				}
			} else if (o instanceof Verse) {
				ImportUtilities.closeOpenVerse(result, context.openVerse);
				context.openVerse = handleVerse(result, (Verse) o);
				container.getContent().add(context.openVerse);
			} else if (o instanceof Note) {
				Note note = (Note) o;
				FootnoteXref nx = new FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller());
				container.getContent().add(nx);
				parseCharContent(note.getContent(), nx, result, context);
			} else {
				throw new IOException("Unsupported character content element: " + o.getClass().getName());
			}
		}
	}

	private VerseStart handleVerse(ParatextBook result, Verse verse) throws IOException {
		ChapterStart chapter = result.findLastBookContent(ChapterStart.class);
		if (chapter == null) {
			throw new IllegalStateException("Verse found before chapter start: " + verse.getNumber());
		}
		// A verse number in USX 2 may be in the format 6-7, 6a or even 6-7a.
		// Attempt to parse these numbers by first adding the book and chapter and then parsing it as a whole.
		VerseIdentifier location = VerseIdentifier.fromStringOrThrow(chapter.getLocation() + ":" + verse.getNumber());
		return new VerseStart(location, verse.getNumber());
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws Exception {
		ObjectFactory of = new ObjectFactory();
		Usx usx = of.createUsx();
		usx.setVersion("2.5");
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

		book.accept(new ParatextBookContentVisitor<IOException>() {

			List<Object> currentContent = null;
			Table currentTable = null;

			@Override
			public void visitChapterStart(ChapterIdentifier location) throws IOException {
				Chapter ch = new Chapter();
				ch.setStyle("c");
				ch.setNumber(BigInteger.valueOf(location.chapter));
				usx.getParaOrTableOrChapter().add(ch);
				currentContent = null;
				currentTable = null;
			}

			@Override
			public void visitChapterEnd(ChapterIdentifier location) throws IOException {
				// Chapter end does not exist in USX 2
			}

			@Override
			public void visitParagraphStart(ParagraphKind kind) throws IOException {
				if (kind == ParagraphKind.TABLE_ROW) {
					if (currentTable == null) {
						currentTable = new Table();
						usx.getParaOrTableOrChapter().add(currentTable);
					}
					currentTable.getRow().add(new Row());
					currentContent = currentTable.getRow().get(currentTable.getRow().size() - 1).getVerseOrCell();
				}
				if (USX_2_PARAGRAPH_KINDS.contains(kind)) {
					ParaStyle style = PARA_KIND_MAP.get(kind);
					if (style == null) {
						throw new RuntimeException("Error could not get ParaStyle for ParagraphKind: " + kind);
					}
					Para para = new Para();
					para.setStyle(style);
					usx.getParaOrTableOrChapter().add(para);
					currentContent = para.getContent();
					currentTable = null;
				} else {
					visitUnsupportedParagraphStart(kind);
				}
			}

			private void visitUnsupportedParagraphStart(ParagraphKind kind) throws IOException {
				if (kind == ParagraphKind.HEBREW_NOTE) {
					// See: USFM.visitUnsupportedParagraphStart
					visitParagraphStart(ParagraphKind.DESCRIPTIVE_TITLE);
					logger.logReplaceWarning(kind, ParagraphKind.DESCRIPTIVE_TITLE);
				} else if (kind.isSameBase(ParagraphKind.SEMANTIC_DIVISION)) {
					// See: USFM.visitUnsupportedParagraphStart
					visitParagraphStart(ParagraphKind.BLANK_LINE);
					logger.logReplaceWarning(kind, ParagraphKind.BLANK_LINE);
				} else if (kind == ParagraphKind.PARAGRAPH_PO || kind == ParagraphKind.PARAGRAPH_LH || kind == ParagraphKind.PARAGRAPH_LF) {
					// See: USFM.visitUnsupportedParagraphStart
					logger.logReplaceWarning(kind, ParagraphKind.PARAGRAPH_P);
					visitParagraphStart(ParagraphKind.PARAGRAPH_P);
				} else if (kind.getTag().startsWith(ParagraphKind.PARAGRAPH_LIM.getTag())) {
					ParagraphKind replacement = ParagraphKind.PARAGRAPH_LI.getWithNumber(kind.getNumber());
					logger.logReplaceWarning(kind, replacement);
					visitParagraphStart(replacement);
				} else {
					throw new RuntimeException("Could not export to USX 2 because an unhandled paragraph type `" + kind + "` from a newer USFM/USX version was found.");
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
					visitParagraphStart(ParagraphKind.PARAGRAPH_P);
				content.accept(new USXCharacterContentVisitor(logger, currentContent));
			}
		});
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Marshaller m = ctx.createMarshaller();
		m.setSchema(getSchema());
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		m.marshal(usx, new FileOutputStream(outFile));
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/usx.xsd"));
	}

	private class USXCharacterContentVisitor implements ParatextCharacterContentVisitor<IOException> {
		private final List<Object> target;
		private final StandardExportLogMessages logger;

		public USXCharacterContentVisitor(StandardExportLogMessages logger, List<Object> target) {
			this.target = target;
			this.logger = logger;
		}

		@Override
		public void visitVerseStart(VerseIdentifier location, String verseNumber) throws IOException {
			Verse verse = new Verse();
			verse.setStyle("v");
			verse.setNumber(verseNumber);
			target.add(verse);
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitFootnoteXref(FootnoteXrefKind kind, String caller) throws IOException {
			Note note = new Note();
			note.setCaller(caller);
			note.setStyle(NOTE_KIND_MAP.get(kind));
			target.add(note);
			return new USXCharacterContentVisitor(USX.this.logger, note.getContent());
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws IOException {
			if (USX_2_AUTO_CLOSING_FORMATTING_KINDS.contains(kind)) {
				CharStyle style = CHAR_KIND_MAP.get(kind);
				if (style == null) {
					throw new RuntimeException("Error could not get CharStyle for AutoClosingFormattingKind: " + kind);
				}
				Char chr = new Char();
				chr.setLemma(attributes.get("lemma"));
				chr.setStyle(CHAR_KIND_MAP.get(kind));
				target.add(chr);
				return new USXCharacterContentVisitor(USX.this.logger, chr.getContent());
			} else {
				return visitUnsupportedAutoClosingFormatting(kind, attributes);
			}
		}

		private ParatextCharacterContentVisitor<IOException> visitUnsupportedAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws IOException {
			if (kind == AutoClosingFormattingKind.PAGE_BREAK) {
				USX.this.logger.logRemovedWarning(kind);
				return null;
			} else if (kind == AutoClosingFormattingKind.LIST_TOTAL || kind == AutoClosingFormattingKind.LIST_KEY || kind.isSameBase(AutoClosingFormattingKind.LIST_VALUE)) {
				// see: USFM.visitUnsupportedAutoClosingFormatting
				USX.this.logger.logSkippedWarning(kind);
				return new USXCharacterContentVisitor(USX.this.logger, target);
			} else if (kind == AutoClosingFormattingKind.FOOTNOTE_WITNESS_LIST) {
				// see: USFM.visitUnsupportedAutoClosingFormatting
				USX.this.logger.logReplaceWarning(kind, AutoClosingFormattingKind.FOOTNOTE_TEXT);
				return visitUnsupportedAutoClosingFormatting(AutoClosingFormattingKind.FOOTNOTE_TEXT, attributes);
			} else if (kind == AutoClosingFormattingKind.XREF_PUBLISHED_ORIGIN) {
				// see: USFM.visitUnsupportedAutoClosingFormatting
				USX.this.logger.logRemovedWarning(kind);
				return null;
			} else if (kind == AutoClosingFormattingKind.XREF_TARGET_REFERENCES_TEXT) {
				// see: USFM.visitUnsupportedAutoClosingFormatting
				USX.this.logger.logSkippedWarning(kind);
				return new USXCharacterContentVisitor(USX.this.logger, target);
			} else if (kind == AutoClosingFormattingKind.SUPERSCRIPT) {
				// see: USFM.visitUnsupportedAutoClosingFormatting
				USX.this.logger.logSkippedWarning(kind, "This might lead to text that is not" +
						"separated by whitespace, since the previous text and superscript text may not have had been" +
						"separated by whitespace.");
				return new USXCharacterContentVisitor(USX.this.logger, target);
			} else if (kind == AutoClosingFormattingKind.ARAMAIC_WORD) {
				// see: USFM.visitUnsupportedAutoClosingFormatting
				USX.this.logger.logSkippedWarning(kind);
				return new USXCharacterContentVisitor(USX.this.logger, target);
			} else if (kind == AutoClosingFormattingKind.PROPER_NAME_GEOGRAPHIC) {
				// see: USFM.visitUnsupportedAutoClosingFormatting
				USX.this.logger.logSkippedWarning(kind);
				return new USXCharacterContentVisitor(USX.this.logger, target);
			} else {
				throw new RuntimeException("Could not export to USX 2 because an unhandled char type `" + kind + "` from a newer USFM/USX version was found.");
			}
		}

		@Override
		public void visitReference(Reference reference) throws IOException {
			Ref ref = new Ref();
			String loc = reference.getBook().getIdentifier() + " " + reference.getFirstChapter() + ":" + reference.getFirstVerse();
			if (reference.getLastChapter() != reference.getFirstChapter()) {
				loc += "-" + reference.getLastChapter() + ":" + reference.getLastVerse();
			} else if (reference.getLastVerse() != reference.getFirstVerse()) {
				loc += "-" + reference.getLastVerse();
			}
			ref.setLoc(loc);
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
		public void visitVerseEnd(VerseIdentifier verseNumber) throws IOException {
			// USX 2.x does not support verse end milestones, hence we don't add them.
		}
	}

	@Override
	boolean isAutoClosingFormattingKindSupported(AutoClosingFormattingKind kind) {
		// See AutoClosingFormattingKind.PAGE_BREAK for why it is unsupported as AutoClosingFormattingKind.
		return kind != AutoClosingFormattingKind.PAGE_BREAK
				&& USX_2_AUTO_CLOSING_FORMATTING_KINDS.contains(kind);
	}

	@Override
	boolean isParagraphKindSupported(ParagraphKind kind) {
		return USX_2_PARAGRAPH_KINDS.contains(kind);
	}

	private static class CharStyleWrapper extends StyleWrapper<CharStyle> {

		private static Set<CharStyle> CHAR_STYLE_UNSUPPORTED = Collections.unmodifiableSet(EnumSet.of(
				CharStyle.VA,
				CharStyle.CA,
				CharStyle.FM,
				CharStyle.EFM,
				CharStyle.CAT
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
				ParaStyle.K_2
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
