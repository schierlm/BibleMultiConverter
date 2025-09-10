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
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.HyperlinkType;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.MetadataBook.MetadataBookKey;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Versification;
import biblemulticonverter.schema.zefdic1.BibLinkType;
import biblemulticonverter.schema.zefdic1.Dictionary;
import biblemulticonverter.schema.zefdic1.MyAnyType;
import biblemulticonverter.schema.zefdic1.ObjectFactory;
import biblemulticonverter.schema.zefdic1.RefLinkType;
import biblemulticonverter.schema.zefdic1.SeeType;
import biblemulticonverter.schema.zefdic1.TEnumDicType;
import biblemulticonverter.schema.zefdic1.TItem;
import biblemulticonverter.schema.zefdic1.TParagraph;
import biblemulticonverter.schema.zefdic1.TStyle;

/**
 * Importer and exporter for Zefania Dictionaries.
 */
public class ZefDic implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Zefania Dictionary - dictionaries for well known bible format.",
			"",
			"Usage (export): ZefDic <OutputFile>"
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
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Unmarshaller u = ctx.createUnmarshaller();
		u.setSchema(getSchema());
		Dictionary doc = (Dictionary) u.unmarshal(inputFile);
		return parseBible(doc);
	}

	protected Bible parseBible(Dictionary doc) throws Exception {
		Bible result = new Bible(doc.getType().toString() + "@" + doc.getRefbible());
		MetadataBook metadata = new MetadataBook();
		if (doc.getDicversion() != null) {
			metadata.setValue(MetadataBookKey.version, doc.getDicversion());
		}
		if (doc.getRevision() != null) {
			metadata.setValue(MetadataBookKey.revision, doc.getRevision());
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
		int counter = 0;
		for (TItem item : doc.getItem()) {
			String id = item.getId();
			String internalId = id;
			if (!id.matches(Utils.BOOK_ABBR_REGEX))
				internalId = "L" + (++counter);
			Book bk = new Book(internalId, BookID.DICTIONARY_ENTRY, id, id);
			result.getBooks().add(bk);
			FormattedText prolog = new FormattedText();
			bk.getChapters().add(new Chapter());
			bk.getChapters().get(0).setProlog(prolog);
			Visitor<RuntimeException> vv = prolog.getAppendVisitor();
			String strongId = item.getStrongId();
			if (strongId != null) {
				Visitor<RuntimeException> vvvv = vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefdic", "itemfield", "strongid");
				vvvv.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText("Strong-ID: ");
				vvvv.visitText(strongId);
				vv.visitLineBreak(ExtendedLineBreakKind.PARAGRAPH, 0);
			}
			for (Object s : item.getContent()) {
				if (s instanceof String) {
					if (((String) s).trim().length() > 0)
						throw new RuntimeException((String) s);
				} else if (s instanceof JAXBElement) {
					Object v = ((JAXBElement<?>) s).getValue();
					if (!((JAXBElement<?>) s).getName().getNamespaceURI().equals("")) {
						throw new RuntimeException(((JAXBElement<?>) s).getName().getNamespaceURI());
					}
					String nn = ((JAXBElement<?>) s).getName().getLocalPart();
					if (v instanceof TParagraph && nn.equals("description")) {
						TParagraph para = (TParagraph) v;
						if (para.getId() != null)
							throw new RuntimeException(para.getId());
						Visitor<RuntimeException> vvv = vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefdic", "field", "description");
						for (Object oo : para.getContent()) {
							if (oo instanceof String) {
								vvv.visitText(normalize((String) oo, false));
							} else if (oo instanceof JAXBElement) {
								Object ovv = ((JAXBElement<?>) oo).getValue();
								if (!((JAXBElement<?>) oo).getName().getNamespaceURI().equals("")) {
									throw new RuntimeException(((JAXBElement<?>) oo).getName().getNamespaceURI());
								}
								String nnn = ((JAXBElement<?>) oo).getName().getLocalPart();
								if (nnn.equals("br") && ovv instanceof String) {
									if (((String) ovv).trim().length() > 0)
										throw new RuntimeException((String) ovv);
									vvv.visitLineBreak(ExtendedLineBreakKind.NEWLINE, 0);
								} else if (nnn.equals("title") && ovv instanceof String) {
									vvv.visitHeadline(2).visitText(((String) ovv).trim().replaceAll("  +", " "));
								} else if (nnn.equals("sub") && ovv instanceof String) {
									vvv.visitFormattingInstruction(FormattingInstructionKind.SUBSCRIPT).visitText(normalize((String) ovv, false));
								} else if (nnn.equals("reflink") && ovv instanceof RefLinkType) {
									RefLinkType r = (RefLinkType) ovv;
									if (r.getTarget() != null || r.getContent() == null || r.getContent().length() > 0)
										System.out.println("WARNING: Unsupported reflink attributes " + r.getTarget() + "|" + r.getContent());
									if (r.getMscope() == null)
										r.setMscope(r.getContent());
									vv.visitDictionaryEntry("reflink", r.getMscope().replace(';', '-'));
								} else if (nnn.equals("see") && ovv instanceof SeeType) {
									SeeType see = (SeeType) ovv;
									if (see.getTarget() != null && !see.getTarget().equals("x-self"))
										throw new RuntimeException(see.getTarget());
									vv.visitDictionaryEntry("dict", see.getContent());
								} else if (nnn.equals("bib_link") && ovv instanceof BibLinkType) {
									BibLinkType bl = (BibLinkType) ovv;
									Visitor<RuntimeException> fn = vv.visitFootnote(false);
									fn.visitText(FormattedText.XREF_MARKER);
									BookID bid = BookID.fromZefId(Integer.parseInt(bl.getBn()));
									int chapter = Integer.parseInt(bl.getCn1());
									String bookAbbr = bid.getOsisID();
									fn.visitCrossReference(bookAbbr, bid, chapter, bl.getVn1(), bookAbbr, bid, chapter, bl.getVn1()).visitText(bid.getOsisID() + " " + chapter + ":" + bl.getVn1());
								} else if (nnn.equals("greek") && ovv instanceof String) {
									vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefdic", "tag", "greek").visitText(normalize((String) ovv, false));
								} else if (nnn.equals("em") && ovv instanceof String) {
									vvv.visitFormattingInstruction(FormattingInstructionKind.ITALIC).visitText(normalize((String) ovv, false));
								} else if (nnn.equals("strong") && ovv instanceof String) {
									vvv.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText(normalize((String) ovv, false));
								} else if (nnn.equals("q") && ovv instanceof String) {
									vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefdic", "tag", "q").visitText(normalize((String) ovv, false));
								} else {
									throw new RuntimeException(nnn + "/" + ovv.getClass().getName());
								}
							} else {
								throw new RuntimeException(oo.getClass().getName());
							}
						}
						vv.visitLineBreak(ExtendedLineBreakKind.PARAGRAPH, 0);
					} else if (v instanceof String || v instanceof MyAnyType) {
						Visitor<RuntimeException> vvvv;
						boolean addParagraph = false;
						if (nn.equals("title")) {
							vvvv = vv.visitHeadline(1);
						} else if (nn.equals("strong_id")) {
							vvvv = vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefdic", "field", "strongid");
							vvvv.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText("Strong-ID: ");
							addParagraph = true;
						} else if (nn.equals("transliteration")) {
							vvvv = vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefdic", "field", "transliteration");
							vvvv.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText("Transliteration: ");
							addParagraph = true;
						} else if (nn.equals("pronunciation")) {
							vvvv = vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "zefdic", "field", "pronunciation");
							vvvv.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText("Pronunciation: ");
							addParagraph = true;
						} else {
							throw new RuntimeException(nn);
						}

						if (v instanceof MyAnyType) {
							parseElement(vvvv, (MyAnyType) v);
						} else if (v instanceof String) {
							vvvv.visitText(normalize((String) v, false));
						}
						if (addParagraph) {
							vv.visitLineBreak(ExtendedLineBreakKind.PARAGRAPH, 0);
						}
					} else {
						throw new RuntimeException(nn + "/" + v.getClass().getName());
					}
				} else {
					throw new RuntimeException("" + s.getClass());
				}
			}
			prolog.trimWhitespace();
			prolog.finished();
		}

		return result;
	}

	private static void parseElement(Visitor<RuntimeException> v, MyAnyType mat) {
		for (Object o : mat.getContent()) {
			if (o instanceof String) {
				v.visitText(normalize((String) o, false));
			} else if (o instanceof JAXBElement) {
				String name = ((JAXBElement<?>) o).getName().getLocalPart();
				MyAnyType value = (MyAnyType) ((JAXBElement<?>) o).getValue();
				if (name.equals("em")) {
					parseElement(v.visitFormattingInstruction(FormattingInstructionKind.ITALIC), value);
				} else if (name.equals("sup")) {
					parseElement(v.visitFormattingInstruction(FormattingInstructionKind.SUPERSCRIPT), value);
				} else {
					throw new RuntimeException(name);
				}
			} else {
				throw new RuntimeException("" + o.getClass());
			}
		}
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
		Dictionary xmlbible = createXMLBible(bible);

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Marshaller m = ctx.createMarshaller();
		if (!Boolean.getBoolean("biblemulticonverter.skipxmlvalidation"))
			m.setSchema(getSchema());
		m.marshal(xmlbible, doc);
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		if (Boolean.getBoolean("biblemulticonverter.indentxml")) {
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		}
		transformer.transform(new DOMSource(doc), new StreamResult(file));
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/zefDic1.xsd"));
	}

	protected Dictionary createXMLBible(Bible bible) throws Exception {
		final ObjectFactory of = new ObjectFactory();
		Dictionary doc = of.createDictionary();
		doc.setDicversion("1");
		doc.setRevision("1");
		doc.setRefbible("any");
		doc.setType(TEnumDicType.X_DICTIONARY);
		String title = null;
		if (bible.getName().matches("X_(DICTIONARY|COMMENTARY|STRONG|DAILY)@.*")) {
			String[] parts = bible.getName().split("@", 2);
			doc.setType(TEnumDicType.valueOf(parts[0]));
			doc.setRefbible(parts[1]);
		} else {
			title = bible.getName();
		}
		doc.setINFORMATION(of.createTINFORMATION());
		doc.getINFORMATION().getTitleOrCreatorOrDescription().add(new JAXBElement<String>(new QName("title"), String.class, title));
		MetadataBook metadata = bible.getMetadataBook();
		if (metadata != null) {
			for (String key : metadata.getKeys()) {
				String value = metadata.getValue(key);
				if (value.equals("-empty-"))
					value = "";
				if (key.equals(MetadataBookKey.version.toString())) {
					doc.setDicversion(value);
				} else if (key.equals(MetadataBookKey.revision.toString())) {
					doc.setRevision(value);
				} else if (Arrays.asList(INFORMATION_KEYS).contains(key)) {
					doc.getINFORMATION().getTitleOrCreatorOrDescription().add(new JAXBElement<String>(new QName(key), String.class, value));
				}
			}
		}
		for (Book bk : bible.getBooks()) {
			if (bk.getId().equals(BookID.METADATA))
				continue;
			if (!bk.getId().equals(BookID.DICTIONARY_ENTRY)) {
				System.out.println("WARNING: Unable to export book " + bk.getAbbr());
				continue;
			}

			final TItem item = of.createTItem();

			if (!bk.getLongName().equals(bk.getShortName())) {
				TItem itm = of.createTItem();
				itm.setId(bk.getShortName());
				appendTextElement(itm, "title", bk.getLongName());
				TParagraph para2 = of.createTParagraph();
				SeeType see = of.createSeeType();
				see.setContent(bk.getLongName());
				para2.getContent().add(new JAXBElement<SeeType>(new QName("see"), SeeType.class, see));
				itm.getContent().add(new JAXBElement<TParagraph>(new QName("description"), TParagraph.class, para2));
				doc.getItem().add(itm);
			}
			item.setId(bk.getLongName());
			doc.getItem().add(item);

			class ZefState {
				TParagraph para = of.createTParagraph();
				boolean eatParagraph = false;

				public void flushPara(TItem item) {
					item.getContent().add(new JAXBElement<TParagraph>(new QName("description"), TParagraph.class, para));
					para = of.createTParagraph();
				}
			}
			final ZefState state = new ZefState();
			FormattedText text = bk.getChapters().get(0).getProlog();
			class LevelVisitor implements Visitor<RuntimeException> {

				final List<Serializable> target;

				private LevelVisitor(ZefState state) {
					target = state.para.getContent();
				}

				private LevelVisitor(MyAnyType parent) {
					target = parent.getContent();
				}

				private LevelVisitor(TStyle parent) {
					target = parent.getContent();
				}

				@Override
				public int visitElementTypes(String elementTypes) throws RuntimeException {
					return 0;
				}

				@Override
				public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
					System.out.println("WARNING: Nested headlines are not supported");
					return null;
				}

				@Override
				public void visitStart() throws RuntimeException {
				}

				@Override
				public void visitText(String text) throws RuntimeException {
					if (text.length() > 0)
						target.add(text);
				}

				@Override
				public Visitor<RuntimeException> visitFootnote(boolean ofCrossReferences) throws RuntimeException {
					System.out.println("WARNING: footnotes are not supported");
					return null;
				}

				@Override
				public Visitor<RuntimeException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws RuntimeException {
					if (firstBook != lastBook || firstChapter != lastChapter || !firstVerse.equals(lastVerse))
						System.out.println("WARNING: Cross references to verse ranges are not supported");
					BibLinkType b = of.createBibLinkType();
					b.setBn("" + firstBook.getZefID());
					b.setCn1("" + firstChapter);
					b.setVn1(firstVerse);
					target.add(new JAXBElement<BibLinkType>(new QName("bib_link"), BibLinkType.class, b));
					return null;
				}

				@Override
				public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
					String tag;
					switch (kind) {
					case BOLD:
						tag = "strong";
						break;
					case ITALIC:
						tag = "em";
						break;
					case SUPERSCRIPT:
						tag = "sup";
						break;
					case SUBSCRIPT:
						tag = "sub";
						break;
					default:
						return visitCSSFormatting(kind.getCss());
					}
					MyAnyType mat = of.createMyAnyType();
					target.add(new JAXBElement<MyAnyType>(new QName(tag), MyAnyType.class, mat));
					return new LevelVisitor(mat);
				}

				@Override
				public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
					TStyle style = of.createTStyle();
					style.setCss(css);
					target.add(of.createTStyleSTYLE(style));
					return new LevelVisitor(style);
				}

				@Override
				public void visitVerseSeparator() throws RuntimeException {
					System.out.println("WARNING: Verse separators are not supported");
				}

				@Override
				public void visitLineBreak(ExtendedLineBreakKind lbk, int indent) throws RuntimeException {
					System.out.println("WARNING: Nested line breaks are not supported");
				}

				@Override
				public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					System.out.println("WARNING: Grammar information is not supported");
					return this;
				}

				@Override
				public Visitor<RuntimeException> visitSpeaker(String labelOrStrongs) throws RuntimeException {
					System.out.println("WARNING: Speaker information is not supported");
					return this;
				}

				@Override
				public Visitor<RuntimeException> visitHyperlink(HyperlinkType type, String target) throws RuntimeException {
					System.out.println("WARNING: Hyperlinks are not supported");
					return this;
				}

				@Override
				public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
					if (dictionary.equals("reflink")) {
						RefLinkType r = of.createRefLinkType();
						r.setMscope(entry.substring(1).replace('-', ';'));
						target.add(new JAXBElement<RefLinkType>(new QName("reflink"), RefLinkType.class, r));
					} else {
						SeeType see = of.createSeeType();
						see.setTarget(dictionary.equals("dict") ? "x-self" : dictionary);
						see.setContent(entry);
						target.add(new JAXBElement<SeeType>(new QName("see"), SeeType.class, see));
					}
					return null;
				}

				@Override
				public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
					System.out.println("WARNING: Raw html output not supported");
				}

				@Override
				public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
					throw new IllegalStateException("Variations not supported");
				}

				@Override
				public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
					return prio.handleVisitor(category, this);
				}

				@Override
				public boolean visitEnd() throws RuntimeException {
					return false;
				}
			}
			;
			text.accept(new Visitor<RuntimeException>() {

				@Override
				public int visitElementTypes(String elementTypes) throws RuntimeException {
					return 0;
				}

				@Override
				public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
					MyAnyType mat = of.createMyAnyType();
					JAXBElement<MyAnyType> elem = new JAXBElement<>(new QName("title"), MyAnyType.class, mat);
					if (depth == 1) {
						state.flushPara(item);
						item.getContent().add(elem);
					} else {
						state.para.getContent().add(elem);
					}
					return new LevelVisitor(mat);
				}

				@Override
				public void visitStart() throws RuntimeException {
				}

				@Override
				public void visitText(String text) throws RuntimeException {
					new LevelVisitor(state).visitText(text);
				}

				@Override
				public Visitor<RuntimeException> visitFootnote(boolean ofCrossReferences) throws RuntimeException {
					System.out.println("WARNING: footnotes are not supported");
					return null;
				}

				@Override
				public Visitor<RuntimeException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) {
					return new LevelVisitor(state).visitCrossReference(firstBookAbbr, firstBook, firstChapter, firstVerse, lastBookAbbr, lastBook, lastChapter, lastVerse);
				}

				@Override
				public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
					return new LevelVisitor(state).visitFormattingInstruction(kind);
				}

				@Override
				public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
					return new LevelVisitor(state).visitCSSFormatting(css);
				}

				@Override
				public void visitVerseSeparator() throws RuntimeException {
					System.out.println("WARNING: Verse separators are not supported");
				}

				@Override
				public void visitLineBreak(ExtendedLineBreakKind lbk, int indent) {
					if (state.eatParagraph) {
						state.eatParagraph = false;
					} else {
						state.flushPara(item);
						state.para = of.createTParagraph();
					}
				}

				@Override
				public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
					System.out.println("WARNING: Grammar information is not supported");
					return null;
				}

				@Override
				public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
					return new LevelVisitor(state).visitDictionaryEntry(dictionary, entry);
				}

				@Override
				public Visitor<RuntimeException> visitSpeaker(String labelOrStrongs) throws RuntimeException {
					return new LevelVisitor(state).visitSpeaker(labelOrStrongs);
				}

				@Override
				public Visitor<RuntimeException> visitHyperlink(HyperlinkType type, String target) throws RuntimeException {
					return new LevelVisitor(state).visitHyperlink(type, target);
				}

				@Override
				public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
					System.out.println("WARNING: Raw html output not supported");
				}

				@Override
				public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
					throw new IllegalStateException("Variations not supported");
				}

				@Override
				public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
					if (prio == ExtraAttributePriority.KEEP_CONTENT && category.equals("zefdic")) {
						// TODO
						// if (v.getChapterNumberAndType() == -1 && (lastChapter
						// == -1 || lastChapter == 0)) {
						// if (v.getChapterNumberAndType() == -1 &&
						// v.getVerse().equals("1i")) {
						// itm.setStrongId(v.getRawText());
						// lastChapter = -1;
						// lastVerse = -2;
						// continue;
						// } else if (v.getChapterNumberAndType() == -1 &&
						// v.getVerse().equals("1s")) {
						// appendTextElement(itm, "strong_id", v.getRawText());
						// lastChapter = -1;
						// lastVerse = -2;
						// continue;
						// } else if (v.getChapterNumberAndType() == -1 &&
						// v.getVerse().equals("1t")) {
						// appendTextElement(itm, "transliteration",
						// v.getRawText());
						// lastChapter = -1;
						// lastVerse = -2;
						// continue;
						// } else if (v.getChapterNumberAndType() == -1 &&
						// v.getVerse().equals("1p")) {
						// appendTextElement(itm, "pronunciation",
						// v.getRawText());
						// lastChapter = -1;
						// lastVerse = -2;
						// continue;
						// }
						// }
						// ===================================================//
						// Visitor<RuntimeException> vvvv =
						// vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT,
						// "zefdic", "itemfield", "strongid");
						// Visitor<RuntimeException> vvv =
						// vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT,
						// "zefdic", "field", "description");
						// vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT,
						// "zefdic", "tag",
						// "greek").visitText(normalize((String)ovv, false));
						// vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT,
						// "zefdic", "tag",
						// "q").visitText(normalize((String)ovv, false));
						// vvvv =
						// vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT,
						// "zefdic", "field", "strongid");
						// vvvv =
						// vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT,
						// "zefdic", "field", "transliteration");
						// vvvv =
						// vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT,
						// "zefdic", "field", "pronunciation");
						return null;
					} else {
						return prio.handleVisitor(category, this);
					}
				}

				@Override
				public boolean visitEnd() throws RuntimeException {
					return false;
				}
			});
			state.flushPara(item);
		}
		return doc;
	}

	private static void appendTextElement(TItem itm, String tag, String value) {
		MyAnyType mat = new MyAnyType();
		mat.getContent().add(value);
		itm.getContent().add(new JAXBElement<MyAnyType>(new QName(tag), MyAnyType.class, mat));
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}
}
