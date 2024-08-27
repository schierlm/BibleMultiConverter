package biblemulticonverter.format.paratext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

import biblemulticonverter.data.Utils;
import biblemulticonverter.format.paratext.ParatextBook.ChapterStart;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextBook.PeripheralStart;
import biblemulticonverter.format.paratext.ParatextBook.Remark;
import biblemulticonverter.format.paratext.ParatextBook.SidebarEnd;
import biblemulticonverter.format.paratext.ParatextBook.SidebarStart;
import biblemulticonverter.format.paratext.ParatextBook.VerseStart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.SpecialSpace;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Text;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import biblemulticonverter.format.paratext.model.Version;
import biblemulticonverter.format.paratext.utilities.ImportUtilities;
import biblemulticonverter.format.paratext.utilities.StandardExportWarningMessages;
import biblemulticonverter.format.paratext.utilities.UnifiedScriptureXMLWriter;
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

	private final StandardExportWarningMessages logger = new StandardExportWarningMessages("USX 2");
	private UnmarshallerLocationListener unmarshallerLocationListener = new UnmarshallerLocationListener();

	public USX() {
		super("USX 2", new ParaStyleWrapper(), new CharStyleWrapper());
		prepareNoteMaps();
	}

	private void prepareNoteMaps() {
		NOTE_STYLE_MAP.put(NoteStyle.F, ParatextCharacterContent.FootnoteXrefKind.FOOTNOTE);
		NOTE_STYLE_MAP.put(NoteStyle.EF, ParatextCharacterContent.FootnoteXrefKind.STUDY_EXTENDED_FOOTNOTE);
		NOTE_STYLE_MAP.put(NoteStyle.FE, ParatextCharacterContent.FootnoteXrefKind.ENDNOTE);
		NOTE_STYLE_MAP.put(NoteStyle.X, ParatextCharacterContent.FootnoteXrefKind.XREF);
		NOTE_STYLE_MAP.put(NoteStyle.EX, ParatextCharacterContent.FootnoteXrefKind.STUDY_EXTENDED_XREF);
		NOTE_KIND_MAP.put(ParatextCharacterContent.FootnoteXrefKind.FOOTNOTE, NoteStyle.F);
		NOTE_KIND_MAP.put(ParatextCharacterContent.FootnoteXrefKind.ENDNOTE, NoteStyle.FE);
		NOTE_KIND_MAP.put(ParatextCharacterContent.FootnoteXrefKind.XREF, NoteStyle.X);
		NOTE_KIND_MAP.put(ParatextCharacterContent.FootnoteXrefKind.STUDY_EXTENDED_FOOTNOTE, NoteStyle.EF);
		NOTE_KIND_MAP.put(ParatextCharacterContent.FootnoteXrefKind.STUDY_EXTENDED_XREF, NoteStyle.EX);
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
		unmarshallerLocationListener.setXMLStreamReader(inputFile.getName(), xsr);
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
				} else if (para.getStyle().value().equals("rem")) {
					String value = "";
					for (Object oo : para.getContent()) {
						if (oo instanceof String) {
							value += ((String) oo).replaceAll("[ \r\n\t]+", " ");
						} else {
							throw new RuntimeException("Unsupported content in remark: " + oo.getClass());
						}
					}
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
					charContent = null;
				} else if (PARA_STYLE_UNSUPPORTED.contains(para.getStyle())) {
					// skip
					charContent = null;
				} else if (para.getStyle() == ParaStyle.PERIPH) {
					String value = "";
					for (Object oo : para.getContent()) {
						if (oo instanceof String) {
							value += ((String) oo).replaceAll("[ \r\n\t]+", " ");
						} else {
							throw new RuntimeException("Unsupported content in periph: " + oo.getClass());
						}
					}
					result.getContent().add(new PeripheralStart(value, null));
				} else {
					result.getContent().add(new ParagraphStart(PARA_STYLE_MAP.get(para.getStyle())));
					charContent = null;
					if (!para.getContent().isEmpty()) {
						charContent = new ParatextCharacterContent();
						result.getContent().add(charContent);
						parseCharContent(para.getContent(), charContent, false, result, context);
					}
				}
			} else if (o instanceof Table) {
				Table table = (Table) o;
				for (Row row : table.getRow()) {
					result.getContent().add(new ParagraphStart(ParagraphKind.TABLE_ROW));
					for (Object oo : row.getVerseOrCell()) {
						if (oo instanceof Verse) {
							ImportUtilities.closeOpenVerse(result, context.openVerse);
							result.getContent().add(context.openVerse);
						} else if (oo instanceof Cell) {
							Cell cell = (Cell) oo;
							result.getContent().add(new ParatextBook.TableCellStart(cell.getStyle().value()));
							charContent = new ParatextCharacterContent();
							result.getContent().add(charContent);
							parseCharContent(cell.getContent(), charContent, false, result, context);
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
				String altnumber = ((Chapter) o).getAltnumber();
				if (altnumber != null) {
					ParatextCharacterContent pcc = new ParatextCharacterContent();
					AutoClosingFormatting acf = new AutoClosingFormatting(AutoClosingFormattingKind.ALTERNATE_CHAPTER);
					acf.getContent().add(Text.from(altnumber));
					pcc.getContent().add(acf);
					result.getContent().add(pcc);
				}
				charContent = null;
			} else if (o instanceof Note) {
				if (charContent == null) {
					charContent = new ParatextCharacterContent();
					result.getContent().add(charContent);
				}
				Note note = (Note) o;
				List<Object> nc = note.getContent();
				List<String> categories = new ArrayList<>();
				while (!nc.isEmpty() && nc.get(0) instanceof Char && ((Char) nc.get(0)).getStyle() == CharStyle.CAT && ((Char) nc.get(0)).getContent().size() == 1 && ((Char) nc.get(0)).getContent().get(0) instanceof String) {
					nc = new ArrayList<>(nc);
					categories.add(((Char) nc.remove(0)).getContent().get(0).toString().trim());
				}
				FootnoteXref nx = new FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller(), categories.toArray(new String[0]));
				charContent.getContent().add(nx);
				parseCharContent(nc, nx, true, result, context);
			} else if (o instanceof Sidebar) {
				Sidebar s = (Sidebar) o;
				result.getContent().add(new SidebarStart(s.getCategory() == null ? new String[0] : s.getCategory().split(" ")));
				for (Object os : s.getParaOrTableOrNote()) {
					if (os instanceof Para) {
						Para para = (Para) os;
						if (para.getStyle().value().equals("rem")) {
							String value = "";
							for (Object oo : para.getContent()) {
								if (oo instanceof String) {
									value += ((String) oo).replaceAll("[ \r\n\t]+", " ");
								} else {
									throw new RuntimeException("Unsupported content in remark: " + oo.getClass());
								}
							}
							result.getContent().add(new Remark(value));
							charContent = null;
						} else if (PARA_STYLE_UNSUPPORTED.contains(para.getStyle())) {
							// skip
							charContent = null;
						} else {
							result.getContent().add(new ParagraphStart(PARA_STYLE_MAP.get(para.getStyle())));
							charContent = null;
							if (!para.getContent().isEmpty()) {
								charContent = new ParatextCharacterContent();
								result.getContent().add(charContent);
								parseCharContent(para.getContent(), charContent, false, result, context);
							}
						}
					} else if (os instanceof Table) {
						Table table = (Table) os;
						for (Row row : table.getRow()) {
							result.getContent().add(new ParagraphStart(ParagraphKind.TABLE_ROW));
							for (Object oo : row.getVerseOrCell()) {
								if (oo instanceof Verse) {
									ImportUtilities.closeOpenVerse(result, context.openVerse);
									context.openVerse = handleVerse(result, (Verse) oo);
									result.getContent().add(context.openVerse);
								} else if (oo instanceof Cell) {
									Cell cell = (Cell) oo;
									result.getContent().add(new ParatextBook.TableCellStart(cell.getStyle().value()));
									charContent = new ParatextCharacterContent();
									result.getContent().add(charContent);
									parseCharContent(cell.getContent(), charContent, false, result, context);
								} else {
									throw new IOException("Unsupported table row element: " + os.getClass().getName());
								}
							}
						}
						charContent = null;
					} else if (os instanceof Note) {
						if (charContent == null) {
							charContent = new ParatextCharacterContent();
							result.getContent().add(charContent);
						}
						Note note = (Note) os;
						List<Object> nc = note.getContent();
						List<String> categories = new ArrayList<>();
						while (!nc.isEmpty() && nc.get(0) instanceof Char && ((Char) nc.get(0)).getStyle() == CharStyle.CAT && ((Char) nc.get(0)).getContent().size() == 1 && ((Char) nc.get(0)).getContent().get(0) instanceof String) {
							nc = new ArrayList<>(nc);
							categories.add(((Char) nc.remove(0)).getContent().get(0).toString().trim());
						}
						FootnoteXref nx = new FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller(), categories.toArray(new String[0]));
						charContent.getContent().add(nx);
						parseCharContent(nc, nx, true, result, context);
					} else {
						throw new IOException("Unsupported sidebar level element: " + os.getClass().getName());
					}
				}
				result.getContent().add(new SidebarEnd());
				charContent = null;
			} else {
				throw new IOException("Unsupported book level element: " + o.getClass().getName());
			}
		}
		ImportUtilities.closeOpenVerse(result, context.openVerse);
		ImportUtilities.closeOpenChapter(result, context.openChapter);
		return result;
	}

	private void parseCharContent(List<Object> content, ParatextCharacterContentContainer container, boolean nested, ParatextBook result, ImportContext context) throws IOException {
		for (Object o : content) {
			if (o instanceof Optbreak) {
				container.getContent().add(new SpecialSpace(false, true));
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
			} else if (o instanceof Figure && !nested) {
				Figure fig = (Figure) o;
				ParatextBook.Figure f = new ParatextBook.Figure(fig.getContent());
				if (!fig.getCopy().isEmpty())
					f.getAttributes().put("copy", fig.getCopy());
				if (!fig.getDesc().isEmpty())
					f.getAttributes().put("alt", fig.getDesc());
				f.getAttributes().put("src", fig.getFile());
				if (!fig.getLoc().isEmpty())
					f.getAttributes().put("loc", fig.getLoc());
				f.getAttributes().put("ref", fig.getRef());
				f.getAttributes().put("size", fig.getSize());
				result.getContent().add(f);
				container = new ParatextCharacterContent();
				result.getContent().add((ParatextCharacterContent)container);
			} else if (o instanceof Char) {
				Char chr = (Char) o;
				if (CHAR_STYLE_UNSUPPORTED.contains(chr.getStyle())) {
					parseCharContent(chr.getContent(), container, nested, result, context);
				} else {
					AutoClosingFormatting f = new AutoClosingFormatting(CHAR_STYLE_MAP.get(chr.getStyle()));
					String lemma = chr.getLemma();
					if (f.getKind() == AutoClosingFormattingKind.WORDLIST && lemma != null && !lemma.isEmpty()) {
						f.getAttributes().put("lemma", lemma);
					}
					container.getContent().add(f);
					parseCharContent(chr.getContent(), f, true, result, context);
				}
			} else if (o instanceof Verse && !nested) {
				ImportUtilities.closeOpenVerse(result, context.openVerse);
				context.openVerse = handleVerse(result, (Verse) o);
				result.getContent().add(context.openVerse);
				container = new ParatextCharacterContent();
				result.getContent().add((ParatextCharacterContent)container);
			} else if (o instanceof Note) {
				Note note = (Note) o;
				List<Object> nc = note.getContent();
				List<String> categories = new ArrayList<>();
				while (!nc.isEmpty() && nc.get(0) instanceof Char && ((Char) nc.get(0)).getStyle() == CharStyle.CAT && ((Char) nc.get(0)).getContent().size() == 1 && ((Char) nc.get(0)).getContent().get(0) instanceof String) {
					nc = new ArrayList<>(nc);
					categories.add(((Char) nc.remove(0)).getContent().get(0).toString().trim());
				}
				FootnoteXref nx = new FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller(), categories.toArray(new String[0]));
				container.getContent().add(nx);
				parseCharContent(nc, nx, true, result, context);
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
			if (attr.getKey().startsWith("toca")) {
				logger.logSkippedMetadataWarning(attr.getKey());
				continue;
			}
			Para para = new Para();
			if (attr.getKey().equals("sts")) {
				para.setStyle(ParaStyle.REM);
				para.getContent().add("@@STATUS@@ ");
			} else {
				para.setStyle(ParaStyle.fromValue(attr.getKey().replaceFirst("@[0-9]+$", "")));
			}
			para.getContent().add(attr.getValue());
			usx.getParaOrTableOrChapter().add(para);
		}

		book.accept(new ParatextBookContentVisitor<IOException>() {

			List<Object> currentRoot = usx.getParaOrTableOrChapter();
			List<Object> currentContent = null;
			Table currentTable = null;

			@Override
			public void visitChapterStart(ChapterIdentifier location) throws IOException {
				Chapter ch = new Chapter();
				ch.setStyle("c");
				ch.setNumber(BigInteger.valueOf(location.chapter));
				currentRoot.add(ch);
				currentContent = null;
				currentTable = null;
			}

			@Override
			public void visitChapterEnd(ChapterIdentifier location) throws IOException {
				// Chapter end does not exist in USX 2
			}

			@Override
			public void visitRemark(String content) throws IOException {
				Para para = new Para();
				para.setStyle(ParaStyle.REM);
				currentRoot.add(para);
				para.getContent().add(content);
				currentContent = null;
				currentTable = null;
			}

			@Override
			public void visitParagraphStart(ParagraphKind kind) throws IOException {
				if (kind == ParagraphKind.TABLE_ROW) {
					if (currentTable == null) {
						currentTable = new Table();
						currentRoot.add(currentTable);
					}
					Row row = new Row();
					row.setStyle("tr");
					currentTable.getRow().add(row);
					currentContent = currentTable.getRow().get(currentTable.getRow().size() - 1).getVerseOrCell();
				} else if (USX_2_PARAGRAPH_KINDS.contains(kind)) {
					ParaStyle style = PARA_KIND_MAP.get(kind);
					if (style == null) {
						throw new RuntimeException("Error could not get ParaStyle for ParagraphKind: " + kind);
					}
					Para para = new Para();
					para.setStyle(style);
					currentRoot.add(para);
					currentContent = para.getContent();
					currentTable = null;
				} else {
					ParagraphKind replacement = ParatextStripped.replaceParagraphKind(kind);
					logger.logReplaceWarning(kind, replacement);
					visitParagraphStart(replacement);
				}
			}

			@Override
			public void visitTableCellStart(String tag) throws IOException {
				if (currentTable == null) {
					System.out.println("WARNING: Table cell outside of table");
					return;
				}
				if (tag.contains("-")) {
					Matcher m = Utils.compilePattern("(t[hcr]+)([0-9]+)-([0-9]+)").matcher(tag);
					if (!m.matches())
						throw new RuntimeException("Unsupported table tag "+tag);
					String prefix = m.group(1);
					int min = Integer.parseInt(m.group(2));
					int max = Integer.parseInt(m.group(3));
					for(int i=min; i<=max; i++) {
						visitTableCellStart(prefix+i);
					}
					return;
				}
				Row currentRow = currentTable.getRow().get(currentTable.getRow().size() - 1);
				Cell cell = new Cell();
				cell.setAlign(tag.contains("r") ? CellAlign.END : CellAlign.START);
				cell.setStyle(CellStyle.fromValue(tag));
				currentRow.getVerseOrCell().add(cell);
				currentContent = cell.getContent();
			}

			@Override
			public void visitSidebarStart(String[] categories) throws IOException {
				if (currentRoot != usx.getParaOrTableOrChapter())
					throw new RuntimeException("Nested sidebars are not supported");
				Sidebar sidebar = new Sidebar();
				sidebar.setStyle("esb");
				if (categories.length > 0)
					sidebar.setCategory(String.join(" ", categories));
				currentRoot.add(sidebar);
				currentRoot = sidebar.getParaOrTableOrNote();
			}

			@Override
			public void visitSidebarEnd() throws IOException {
				if (currentRoot == usx.getParaOrTableOrChapter())
					throw new RuntimeException("No sidebar open");
				currentRoot = usx.getParaOrTableOrChapter();
			}

			@Override
			public void visitPeripheralStart(String title, String id) throws IOException {
				Para para = new Para();
				para.setStyle(ParaStyle.PERIPH);
				currentRoot.add(para);
				para.getContent().add(title);
			}

			@Override
			public void visitVerseStart(VerseIdentifier location, String verseNumber) throws IOException {
				if (currentContent == null)
					visitParagraphStart(ParagraphKind.PARAGRAPH_P);
				else if (!currentContent.isEmpty() && verseSeparatorText != null)
					currentContent.add(verseSeparatorText);
				Verse verse = new Verse();
				verse.setStyle("v");
				verse.setNumber(verseNumber);
				currentContent.add(verse);
			}

			@Override
			public void visitVerseEnd(VerseIdentifier verseNumber) throws IOException {
				// USX 2.x does not support verse end milestones, hence we don't add them.
			}

			@Override
			public void visitFigure(String caption, Map<String, String> attributes) throws IOException {
				Figure fig = new Figure();
				fig.setStyle("fig");
				fig.setContent(caption);
				fig.setCopy(attributes.getOrDefault("copy",""));
				fig.setDesc(attributes.getOrDefault("alt",""));
				fig.setFile(attributes.get("src"));
				fig.setLoc(attributes.getOrDefault("loc",""));
				fig.setRef(attributes.get("ref"));
				fig.setSize(attributes.get("size"));
				if (currentContent == null)
					visitParagraphStart(ParagraphKind.PARAGRAPH_P);
				currentContent.add(fig);
			}

			@Override
			public void visitParatextCharacterContent(ParatextCharacterContent content) throws IOException {
				if (currentContent == null && content.getContent().size() == 1 && content.getContent().get(0) instanceof AutoClosingFormatting && !currentRoot.isEmpty() && currentRoot.get(currentRoot.size() - 1) instanceof Chapter) {
					AutoClosingFormatting acf = (AutoClosingFormatting) content.getContent().get(0);
					if (acf.getKind() == AutoClosingFormattingKind.ALTERNATE_CHAPTER && acf.getContent().size() == 1 && acf.getContent().get(0) instanceof Text) {
						Chapter c = (Chapter) currentRoot.get(currentRoot.size() - 1);
						String num = ((Text) acf.getContent().get(0)).getChars();
						c.setAltnumber(num);
						return;
					}
				}
				if (currentContent == null)
					visitParagraphStart(ParagraphKind.PARAGRAPH_P);
				content.accept(new USXCharacterContentVisitor(logger, currentContent));
			}
		});
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Marshaller m = ctx.createMarshaller();
		if (!Boolean.getBoolean("biblemulticonverter.skipxmlvalidation"))
			m.setSchema(getSchema());
		try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
			m.marshal(usx, new UnifiedScriptureXMLWriter(osw, "UTF-8"));
		}
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/usx.xsd"));
	}

	private class USXCharacterContentVisitor implements ParatextCharacterContentVisitor<IOException> {
		private final List<Object> target;
		private final StandardExportWarningMessages logger;

		public USXCharacterContentVisitor(StandardExportWarningMessages logger, List<Object> target) {
			this.target = target;
			this.logger = logger;
		}

		@Override
		public ParatextCharacterContentVisitor<IOException> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) throws IOException {
			Note note = new Note();
			note.setCaller(caller);
			note.setStyle(NOTE_KIND_MAP.get(kind));
			target.add(note);
			for(String cat: categories) {
				Char ch = new Char();
				ch.setStyle(CharStyle.CAT);
				ch.getContent().add(cat);
				note.getContent().add(ch);
			}
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
			if (kind == AutoClosingFormattingKind.SUPERSCRIPT) {
				// There is not really a good way to represent superscript in USFM 2.x
				// To avoid losing data, we skip the tag and just add the content directly to `target`.
				// TODO, maybe we can use `sc` (Small caps) instead?
				USX.this.logger.logSkippedWarning(kind, "This might lead to text that is not" +
						"separated by whitespace, since the previous text and superscript text may not have had been" +
						"separated by whitespace.");
				return new USXCharacterContentVisitor(USX.this.logger, target);
			} else if (kind == AutoClosingFormattingKind.RUBY) {
				// Replace by putting the gloss into PRONUNCIATION.
				USX.this.logger.logReplaceWarning(kind, AutoClosingFormattingKind.PRONUNCIATION);
				final USXCharacterContentVisitor outer = this;
				return new USXCharacterContentVisitor(USX.this.logger, target) {
					public void visitEnd() throws IOException {
						outer.visitAutoClosingFormatting(AutoClosingFormattingKind.PRONUNCIATION, new HashMap<>(3)).visitText(attributes.get("gloss"));
					}
				};
			} else {
				AutoClosingFormattingKind[] replacement = ParatextStripped.replaceAutoClosingFormattingKind(kind);
				if (replacement == null) {
					USX.this.logger.logRemovedWarning(kind);
					return null;
				} else if (replacement.length == 0) {
					USX.this.logger.logSkippedWarning(kind);
					return new USXCharacterContentVisitor(USX.this.logger, target);
				} else if (replacement.length == 1) {
					USX.this.logger.logReplaceWarning(kind, replacement[0]);
					return visitAutoClosingFormatting(replacement[0], attributes);
				} else {
					USX.this.logger.logReplaceWarning(kind, replacement);
					ParatextCharacterContentVisitor<IOException> v = this;
					for(AutoClosingFormattingKind r: replacement) {
						v = v.visitAutoClosingFormatting(r, attributes);
					}
					return v;
				}
			}
		}

		@Override
		public void visitMilestone(String tag, Map<String, String> attributes) throws IOException {
			// ignore
		}

		@Override
		public void visitReference(Reference reference) throws IOException {
			Ref ref = new Ref();
			ref.setLoc(reference.toString());
			ref.setContent(reference.getContent());
			target.add(ref);
		}

		@Override
		public void visitCustomMarkup(String tag, boolean ending) throws IOException {
			logger.logSkippedCustomWarning(tag + (ending ? "*" : ""));
		}

		@Override
		public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws IOException {
			if (nonBreakSpace) {
				visitText("\u00A0");
			} else {
				target.add(new Optbreak());
			}
		}

		@Override
		public void visitText(String text) throws IOException {
			target.add(text);
		}

		@Override
		public void visitEnd() throws IOException {

		}
	}

	@Override
	boolean isAutoClosingFormattingKindSupported(AutoClosingFormattingKind kind) {
		return USX_2_AUTO_CLOSING_FORMATTING_KINDS.contains(kind);
	}

	@Override
	boolean isParagraphKindSupported(ParagraphKind kind) {
		return USX_2_PARAGRAPH_KINDS.contains(kind);
	}

	private static class CharStyleWrapper extends StyleWrapper<CharStyle> {

		private static Set<CharStyle> CHAR_STYLE_UNSUPPORTED = Collections.unmodifiableSet(EnumSet.of(
				CharStyle.EFM
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
