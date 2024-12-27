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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.xml.sax.SAXException;

import biblemulticonverter.data.Utils;
import biblemulticonverter.format.paratext.ParatextBook.PeripheralStart;
import biblemulticonverter.format.paratext.ParatextBook.Remark;
import biblemulticonverter.format.paratext.ParatextBook.SidebarEnd;
import biblemulticonverter.format.paratext.ParatextBook.SidebarStart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.SpecialSpace;
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
import biblemulticonverter.schema.usx3.Ms;
import biblemulticonverter.schema.usx3.Note;
import biblemulticonverter.schema.usx3.NoteStyle;
import biblemulticonverter.schema.usx3.ObjectFactory;
import biblemulticonverter.schema.usx3.Optbreak;
import biblemulticonverter.schema.usx3.Para;
import biblemulticonverter.schema.usx3.ParaStyle;
import biblemulticonverter.schema.usx3.Periph;
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
			"the book number and * for the book name. Use ? to split by chapters, for ",
			"the chapter number; ?? or ??? to force leading zeroes."
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
						result.getAttributes().put(para.getStyle().value(), value);
					}
					charContent = null;
				} else if (PARA_STYLE_UNSUPPORTED.contains(para.getStyle())) {
					// skip
					charContent = null;
				} else {
					result.getContent().add(new ParatextBook.ParagraphStart(PARA_STYLE_MAP.get(para.getStyle())));
					charContent = null;
					if (!para.getContent().isEmpty()) {
						charContent = new ParatextCharacterContent();
						result.getContent().add(charContent);
						parseCharContent(para.getContent(), charContent, result);
					}
				}
			} else if (o instanceof Table) {
				Table table = (Table) o;
				for (Row row : table.getRow()) {
					result.getContent().add(new ParatextBook.ParagraphStart(ParatextBook.ParagraphKind.TABLE_ROW));
					for (Object oo : row.getVerseOrCell()) {
						if (oo instanceof Verse) {
							Verse verse = (Verse) oo;
							ParatextBook.ParatextBookContentPart verseStartOrEnd = handleVerse(verse);
							result.getContent().add(verseStartOrEnd);
						} else if (oo instanceof Cell) {
							Cell cell = (Cell) oo;
							String tag = cell.getStyle().value();
							if (cell.getColspan() != null && cell.getColspan().intValue() > 1) {
								tag += "-" + (Integer.parseInt((tag.replaceFirst("t[hcr]+", ""))) + cell.getColspan().intValue() - 1);
							}
							result.getContent().add(new ParatextBook.TableCellStart(tag));
							charContent = new ParatextCharacterContent();
							result.getContent().add(charContent);
							parseCharContent(cell.getContent(), charContent, result);
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
					String altnumber = ((Chapter) o).getAltnumber();
					if (altnumber != null) {
						ParatextCharacterContent pcc = new ParatextCharacterContent();
						ParatextCharacterContent.AutoClosingFormatting acf = new ParatextCharacterContent.AutoClosingFormatting(ParatextCharacterContent.AutoClosingFormattingKind.ALTERNATE_CHAPTER);
						acf.getContent().add(ParatextCharacterContent.Text.from(altnumber));
						pcc.getContent().add(acf);
						result.getContent().add(pcc);
					}
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
				ParatextCharacterContent.FootnoteXref nx = new ParatextCharacterContent.FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller(), note.getCategory() == null ? new String[0] : note.getCategory().split(" "));
				charContent.getContent().add(nx);
				parseCharContent(note.getContent(), nx, null);
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
							result.getContent().add(new ParatextBook.ParagraphStart(PARA_STYLE_MAP.get(para.getStyle())));
							charContent = null;
							if (!para.getContent().isEmpty()) {
								charContent = new ParatextCharacterContent();
								result.getContent().add(charContent);
								parseCharContent(para.getContent(), charContent, null);
							}
						}
					} else if (os instanceof Table) {
						Table table = (Table) os;
						for (Row row : table.getRow()) {
							result.getContent().add(new ParatextBook.ParagraphStart(ParatextBook.ParagraphKind.TABLE_ROW));
							for (Object oo : row.getVerseOrCell()) {
								if (oo instanceof Verse) {
									Verse verse = (Verse) oo;
									ParatextBook.ParatextBookContentPart verseStartOrEnd = handleVerse(verse);
									result.getContent().add(verseStartOrEnd);
								} else if (oo instanceof Cell) {
									Cell cell = (Cell) oo;
									result.getContent().add(new ParatextBook.TableCellStart(cell.getStyle().value()));
									charContent = new ParatextCharacterContent();
									result.getContent().add(charContent);
									parseCharContent(cell.getContent(), charContent, result);
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
						ParatextCharacterContent.FootnoteXref nx = new ParatextCharacterContent.FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller(), note.getCategory() == null ? new String[0] : note.getCategory().split(" "));
						charContent.getContent().add(nx);
						parseCharContent(note.getContent(), nx, null);
					} else {
						throw new IOException("Unsupported sidebar element: " + os.getClass().getName());
					}
				}
				result.getContent().add(new SidebarEnd());
				charContent = null;
			} else if (o instanceof Periph) {
				Periph p = (Periph) o;
				result.getContent().add(new PeripheralStart(p.getAlt(), p.getId()));
				for (Object os : p.getParaOrTableOrNote()) {
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
							result.getContent().add(new ParatextBook.ParagraphStart(PARA_STYLE_MAP.get(para.getStyle())));
							charContent = null;
							if (!para.getContent().isEmpty()) {
								charContent = new ParatextCharacterContent();
								result.getContent().add(charContent);
								parseCharContent(para.getContent(), charContent, result);
							}
						}
					} else if (os instanceof Table) {
						Table table = (Table) os;
						for (Row row : table.getRow()) {
							result.getContent().add(new ParatextBook.ParagraphStart(ParatextBook.ParagraphKind.TABLE_ROW));
							for (Object oo : row.getVerseOrCell()) {
								if (oo instanceof Verse) {
									Verse verse = (Verse) oo;
									ParatextBook.ParatextBookContentPart verseStartOrEnd = handleVerse(verse);
									result.getContent().add(verseStartOrEnd);
								} else if (oo instanceof Cell) {
									Cell cell = (Cell) oo;
									result.getContent().add(new ParatextBook.TableCellStart(cell.getStyle().value()));
									charContent = new ParatextCharacterContent();
									result.getContent().add(charContent);
									parseCharContent(cell.getContent(), charContent, result);
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
						ParatextCharacterContent.FootnoteXref nx = new ParatextCharacterContent.FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller(), note.getCategory() == null ? new String[0] : note.getCategory().split(" "));
						charContent.getContent().add(nx);
						parseCharContent(note.getContent(), nx, null);
					} else {
						throw new IOException("Unsupported peripheral element: " + os.getClass().getName());
					}
				}
				charContent = null;
			} else {
				throw new IOException("Unsupported book level element: " + o.getClass().getName());
			}
		}

		return result;
	}

	private void parseCharContent(List<Object> content, ParatextBook.ParatextCharacterContentContainer container, ParatextBook bookIfToplevel) throws IOException {
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
			} else if (o instanceof Figure && bookIfToplevel != null) {
				Figure fig = (Figure) o;
				ParatextBook.Figure f = new ParatextBook.Figure(fig.getContent());
				if (fig.getCopy() != null)
					f.getAttributes().put("copy", fig.getCopy());
				if (fig.getAlt() != null)
					f.getAttributes().put("alt", fig.getAlt());
				f.getAttributes().put("src", fig.getFile());
				if (fig.getLoc() != null)
					f.getAttributes().put("loc", fig.getLoc());
				if (fig.getRef() != null)
					f.getAttributes().put("ref", fig.getRef());
				if (fig.getSize() != null)
					f.getAttributes().put("size", fig.getSize());
				bookIfToplevel.getContent().add(f);
				container = new ParatextCharacterContent();
				bookIfToplevel.getContent().add((ParatextCharacterContent) container);
			} else if (o instanceof Ms) {
				Ms ms = (Ms) o;
				ParatextCharacterContent.Milestone m = new ParatextCharacterContent.Milestone(ms.getStyle());
				if (ms.getSid() != null)
					m.getAttributes().put("sid", ms.getSid());
				if (ms.getEid() != null)
					m.getAttributes().put("eid", ms.getEid());
				if (ms.getWho() != null)
					m.getAttributes().put("who", ms.getWho());
				container.getContent().add(m);
			} else if (o instanceof Char) {
				Char chr = (Char) o;
				if (CHAR_STYLE_UNSUPPORTED.contains(chr.getStyle())) {
					parseCharContent(chr.getContent(), container, bookIfToplevel);
				} else {
					ParatextCharacterContent.AutoClosingFormatting f = new ParatextCharacterContent.AutoClosingFormatting(CHAR_STYLE_MAP.get(chr.getStyle()));
					if (f.getKind() == ParatextCharacterContent.AutoClosingFormattingKind.WORDLIST) {
						String lemma = chr.getLemma();
						String strong = chr.getStrong();
						String srcloc = chr.getSrcloc();
						if (lemma != null && !lemma.isEmpty()) {
							f.getAttributes().put("lemma", lemma);
						}
						if (strong != null && !strong.isEmpty()) {
							f.getAttributes().put("strong", strong);
						}
						if (srcloc != null && !srcloc.isEmpty()) {
							f.getAttributes().put("srcloc", srcloc);
						}
					} else if (f.getKind() == ParatextCharacterContent.AutoClosingFormattingKind.RUBY) {
						String gloss = chr.getGloss();
						if (gloss != null && !gloss.isEmpty()) {
							f.getAttributes().put("gloss", gloss);
						}
					}
					String href = chr.getLinkHref();
					if (href != null && !href.isEmpty()) {
						f.getAttributes().put("link-href", href);
					}
					String linkid = chr.getLinkId();
					if (linkid != null && !linkid.isEmpty()) {
						f.getAttributes().put("link-id", linkid);
					}
					String linktitle = chr.getLinkTitle();
					if (linktitle != null && !linktitle.isEmpty()) {
						f.getAttributes().put("link-title", linktitle);
					}
					container.getContent().add(f);
					parseCharContent(chr.getContent(), f, null);
				}
			} else if (o instanceof Verse && bookIfToplevel != null) {
				bookIfToplevel.getContent().add(handleVerse((Verse) o));
				container = new ParatextCharacterContent();
				bookIfToplevel.getContent().add((ParatextCharacterContent) container);
			} else if (o instanceof Note) {
				Note note = (Note) o;
				ParatextCharacterContent.FootnoteXref nx = new ParatextCharacterContent.FootnoteXref(NOTE_STYLE_MAP.get(note.getStyle()), note.getCaller(), note.getCategory() == null ? new String[0] : note.getCategory().split(" "));
				container.getContent().add(nx);
				parseCharContent(note.getContent(), nx, null);
			} else {
				throw new IOException("Unsupported character content element: " + o.getClass().getName());
			}
		}
	}

	private ParatextBook.ParatextBookContentPart handleVerse(Verse verse) throws IOException {
		try {
			if (verse.getSid() != null) {
				return new ParatextBook.VerseStart(
						VerseIdentifier.fromStringOrThrow(verse.getSid()),
						verse.getNumber());
			} else if (verse.getEid() != null) {
				return new ParatextBook.VerseEnd(
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
			if (attr.getKey().equals("sts")) {
				para.setStyle(ParaStyle.REM);
				para.getContent().add("@@STATUS@@ ");
			} else {
				para.setStyle(ParaStyle.fromValue(attr.getKey().replaceFirst("@[0-9]+$", "")));
			}
			para.getContent().add(attr.getValue());
			usx.getParaOrTableOrChapter().add(para);
		}

		book.accept(new ParatextBook.ParatextBookContentVisitor<IOException>() {

			List<Object> currentRoot = usx.getParaOrTableOrChapter();
			List<Object> currentContent = null;
			Table currentTable = null;

			@Override
			public void visitChapterStart(ChapterIdentifier location) throws IOException {
				Chapter ch = new Chapter();
				ch.setStyle("c");
				ch.setSid(location.toString());
				ch.setNumber(BigInteger.valueOf(location.chapter));
				currentRoot.add(ch);
				currentContent = null;
				currentTable = null;
			}

			@Override
			public void visitChapterEnd(ChapterIdentifier location) throws IOException {
				Chapter ch = new Chapter();
				ch.setEid(location.toString());
				currentRoot.add(ch);
				currentContent = null;
				currentTable = null;
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
			public void visitParagraphStart(ParatextBook.ParagraphKind kind) throws IOException {
				if (kind == ParatextBook.ParagraphKind.TABLE_ROW) {
					if (currentTable == null) {
						currentTable = new Table();
						currentRoot.add(currentTable);
					}
					Row row = new Row();
					row.setStyle("tr");
					currentTable.getRow().add(row);
					currentContent = currentTable.getRow().get(currentTable.getRow().size() - 1).getVerseOrCell();
				} else {
					Para para = new Para();
					para.setStyle(PARA_KIND_MAP.get(kind));
					currentRoot.add(para);
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
				if (tag.contains("-")) {
					Matcher m = Utils.compilePattern("(t[hcr]+)([0-9]+)-([0-9]+)").matcher(tag);
					if (!m.matches())
						throw new RuntimeException("Unsupported table tag " + tag);
					String prefix = m.group(1);
					int min = Integer.parseInt(m.group(2));
					int max = Integer.parseInt(m.group(3));
					cell.setColspan(BigInteger.valueOf(max - min + 1));
					tag = prefix + min;
				}
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
				if (categories.length > 0) {
					sidebar.setCategory(String.join(" ", categories));
				}
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
				currentRoot = usx.getParaOrTableOrChapter();
				Periph periph = new Periph();
				periph.setAlt(title);
				if (id == null) {
					for(String[] predef : PeripheralStart.DEFINED_PERIPHERALS) {
						if (predef[1].equals(title)) {
							id = predef[2];
							break;
						}
					}
					if (id == null) {
						id="x-undefined";
					}
				}
				periph.setId(id);
				currentRoot.add(periph);
				currentRoot = periph.getParaOrTableOrNote();
			}

			@Override
			public void visitVerseStart(VerseIdentifier location, String verseNumber) throws IOException {
				if (currentContent == null)
					visitParagraphStart(ParatextBook.ParagraphKind.PARAGRAPH_P);
				else if (!currentContent.isEmpty() && verseSeparatorText != null)
					currentContent.add(verseSeparatorText);
				Verse verse = new Verse();
				verse.setStyle("v");
				verse.setSid(location.toString());
				verse.setNumber(verseNumber);
				currentContent.add(verse);
			}
			@Override
			public void visitVerseEnd(VerseIdentifier location) throws IOException {
				if (currentContent == null)
					visitParagraphStart(ParatextBook.ParagraphKind.PARAGRAPH_P);
				Verse verse = new Verse();
				verse.setEid(location.toString());
				currentContent.add(verse);
			}

			@Override
			public void visitFigure(String caption, Map<String, String> attributes) throws IOException {
				Figure fig = new Figure();
				fig.setStyle("fig");
				fig.setContent(caption);
				fig.setCopy(attributes.get("copy"));
				fig.setAlt(attributes.get("alt"));
				fig.setFile(attributes.get("src"));
				fig.setLoc(attributes.get("loc"));
				fig.setRef(attributes.get("ref"));
				fig.setSize(attributes.get("size"));
				if (currentContent == null)
					visitParagraphStart(ParatextBook.ParagraphKind.PARAGRAPH_P);
				currentContent.add(fig);
			}

			@Override
			public void visitParatextCharacterContent(ParatextCharacterContent content) throws IOException {
				if (currentContent == null && content.getContent().size() == 1 && content.getContent().get(0) instanceof ParatextCharacterContent.AutoClosingFormatting && !currentRoot.isEmpty() && currentRoot.get(currentRoot.size() - 1) instanceof Chapter) {
					ParatextCharacterContent.AutoClosingFormatting acf = (ParatextCharacterContent.AutoClosingFormatting) content.getContent().get(0);
					if (acf.getKind() == ParatextCharacterContent.AutoClosingFormattingKind.ALTERNATE_CHAPTER && acf.getContent().size() == 1 && acf.getContent().get(0) instanceof ParatextCharacterContent.Text) {
						Chapter c = (Chapter) currentRoot.get(currentRoot.size() - 1);
						String num = ((ParatextCharacterContent.Text) acf.getContent().get(0)).getChars();
						c.setAltnumber(num);
						return;
					}
				}
				if (currentContent == null)
					visitParagraphStart(ParatextBook.ParagraphKind.PARAGRAPH_P);
				content.accept(new USX3.USXCharacterContentVisitor(currentContent));
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
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/usx3.xsd"));
	}

	private class USXCharacterContentVisitor implements ParatextCharacterContent.ParatextCharacterContentVisitor<IOException> {
		private List<Object> target;

		public USXCharacterContentVisitor(List<Object> target) {
			this.target = target;
		}

		@Override
		public ParatextCharacterContent.ParatextCharacterContentVisitor<IOException> visitFootnoteXref(ParatextCharacterContent.FootnoteXrefKind kind, String caller, String[] categories) throws IOException {
			Note note = new Note();
			note.setCaller(caller);
			note.setStyle(NOTE_KIND_MAP.get(kind));
			if (categories.length > 0)
				note.setCategory(String.join(" ", categories));
			target.add(note);
			return new USX3.USXCharacterContentVisitor(note.getContent());
		}

		@Override
		public ParatextCharacterContent.ParatextCharacterContentVisitor<IOException> visitAutoClosingFormatting(ParatextCharacterContent.AutoClosingFormattingKind kind, Map<String, String> attributes) throws IOException {
				Char chr = new Char();
				chr.setLemma(nullIfEmpty(attributes.get("lemma")));
				chr.setStrong(nullIfEmpty(attributes.get("strong")));
				chr.setSrcloc(nullIfEmpty(attributes.get("srcloc")));
				chr.setLinkHref(nullIfEmpty(attributes.get("link-href")));
				chr.setLinkId(nullIfEmpty(attributes.get("link-id")));
				chr.setLinkTitle(attributes.get("link-title"));
				chr.setGloss(nullIfEmpty(attributes.get("gloss")));
				chr.setStyle(CHAR_KIND_MAP.get(kind));
				target.add(chr);
				return new USX3.USXCharacterContentVisitor(chr.getContent());
		}

		private String nullIfEmpty(String s) {
			return s == null || s.isEmpty() ? null : s;
		}

		@Override
		public void visitMilestone(String tag, Map<String, String> attributes) throws IOException {
			Ms ms = new Ms();
			ms.setStyle(tag);
			ms.setSid(attributes.get("sid"));
			ms.setEid(attributes.get("eid"));
			ms.setWho(attributes.get("who"));
			target.add(ms);
		}

		@Override
		public void visitReference(ParatextCharacterContent.Reference reference) throws IOException {
			Ref ref = new Ref();
			ref.setLoc(reference.toString());
			ref.setContent(reference.getContent());
			target.add(ref);
		}

		@Override
		public void visitCustomMarkup(String tag, boolean ending) throws IOException {
			warningLogger.logSkippedCustomWarning(tag + (ending ? "*" : ""));
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
	boolean isAutoClosingFormattingKindSupported(ParatextCharacterContent.AutoClosingFormattingKind kind) {
		return true;
	}

	@Override
	boolean isParagraphKindSupported(ParatextBook.ParagraphKind kind) {
		return true;
	}

	private static class CharStyleWrapper extends StyleWrapper<CharStyle> {

		@Override
		Class<CharStyle> getStyleClass() {
			return CharStyle.class;
		}

		@Override
		Set<CharStyle> getUnsupportedStyles() {
			return Collections.emptySet();
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
				ParaStyle.USFM, // gets synthesized again anyway
				ParaStyle.RESTORE,
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