package biblemulticonverter.format;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.schema.zef2005.BIBLEBOOK;
import biblemulticonverter.schema.zef2005.BR;
import biblemulticonverter.schema.zef2005.CAPTION;
import biblemulticonverter.schema.zef2005.CHAPTER;
import biblemulticonverter.schema.zef2005.DIV;
import biblemulticonverter.schema.zef2005.EnumBreak;
import biblemulticonverter.schema.zef2005.EnumCaptionType;
import biblemulticonverter.schema.zef2005.EnumModtyp;
import biblemulticonverter.schema.zef2005.GRAM;
import biblemulticonverter.schema.zef2005.NOTE;
import biblemulticonverter.schema.zef2005.ObjectFactory;
import biblemulticonverter.schema.zef2005.STYLE;
import biblemulticonverter.schema.zef2005.VERS;
import biblemulticonverter.schema.zef2005.XMLBIBLE;

public class ZefaniaXMLMyBible implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Zefania XML - well known bible format (with MyBible optimizations).",
			"",
			"This version will export Zefania XML modules optimized for use with MyBible.",
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		new StrippedDiffable().mergeIntroductionPrologs(bible);

		final ObjectFactory f = new ObjectFactory();
		XMLBIBLE doc = f.createXMLBIBLE();
		doc.setBiblename(bible.getName());
		doc.setType(EnumModtyp.X_BIBLE);
		doc.setINFORMATION(f.createINFORMATION());
		List<DIV> prologs = new ArrayList<DIV>();
		for (Book bk : bible.getBooks()) {
			if (bk.getId().equals(BookID.METADATA))
				continue;
			int bsnumber = bk.getId().getZefID();
			final BIBLEBOOK book = f.createBIBLEBOOK();
			book.setBnumber(BigInteger.valueOf(bsnumber));
			book.setBname(bk.getShortName());
			book.setBsname(bk.getAbbr());
			doc.getBIBLEBOOK().add(book);
			int cnumber = 0;

			for (Chapter cch : bk.getChapters()) {
				cnumber++;
				if (cch.getProlog() != null) {
					DIV xx = f.createDIV();
					prologs.add(xx);
					NOTE xxx = f.createNOTE();
					xx.setNOTE(xxx);
					xxx.setType("x-studynote");
					NOTE prolog = xxx;
					DIV vers = f.createDIV();
					prolog.getContent().add("<p>");
					prolog.getContent().add(vers);
					prolog.getContent().add("</p>");
					vers.setNOTE(f.createNOTE());
					final List<List<Object>> targetStack = new ArrayList<List<Object>>();
					targetStack.add(vers.getNOTE().getContent());
					cch.getProlog().accept(new Visitor<IOException>() {
						@Override
						public Visitor<IOException> visitHeadline(int depth) throws IOException {
							if (depth > 6)
								depth = 6;
							STYLE s = f.createSTYLE();
							s.setCss("-zef-dummy: true");
							targetStack.get(0).add("<h" + depth + ">");
							targetStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, s));
							targetStack.get(0).add("</h" + depth + ">");
							targetStack.add(0, s.getContent());
							return this;
						}

						@Override
						public void visitVerseSeparator() throws IOException {
							STYLE x = f.createSTYLE();
							x.setCss("color:gray");
							x.getContent().add("/");
							targetStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, x));
						}

						@Override
						public void visitText(String text) throws IOException {
							targetStack.get(0).add(text);
						}

						@Override
						public Visitor<IOException> visitFormattingInstruction(FormattedText.FormattingInstructionKind kind) throws IOException {
							String startTag, endTag;
							switch (kind) {
							case BOLD:
								startTag = "<b>";
								endTag = "</b>";
								break;
							case ITALIC:
								startTag = "<i>";
								endTag = "</i>";
								break;
							default:
								startTag = endTag = "";
								System.out.println("Unsupported formatting of kind " + kind + " in prolog - stripped");
							}

							STYLE s = f.createSTYLE();
							s.setCss("-zef-dummy: true");
							targetStack.get(0).add(startTag);
							targetStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, s));
							targetStack.get(0).add(endTag);
							targetStack.add(0, s.getContent());

							return this;
						}

						@Override
						public Visitor<IOException> visitFootnote() throws IOException {
							System.out.println("WARNING: Footnotes in prolog are not supported");
							return null;
						}

						@Override
						public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
							System.out.println("WARNING: Cross references in prologs are not supported");
							STYLE s = f.createSTYLE();
							s.setCss("-zef-dummy: true");
							targetStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, s));
							targetStack.add(0, s.getContent());
							return this;
						}

						@Override
						public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
							throw new RuntimeException("Variations not supported");
						}

						@Override
						public void visitLineBreak(LineBreakKind kind) throws IOException {
							;
							BR br = f.createBR();
							br.setArt(kind == LineBreakKind.PARAGRAPH ? EnumBreak.X_P : EnumBreak.X_NL);
							targetStack.get(0).add(" ");
							targetStack.get(0).add(br);
						}

						@Override
						public Visitor<IOException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
							throw new RuntimeException("Grammar tags in prologs not supported");

						}

						@Override
						public FormattedText.Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
							throw new RuntimeException("Dictionary entries in prologs not supported");
						};

						@Override
						public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
							throw new RuntimeException("Raw HTML in prologs not supported");
						}

						@Override
						public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
							STYLE s = f.createSTYLE();
							s.setCss("-zef-dummy: true");
							targetStack.get(0).add("<span style=\"" + css + "\">");
							targetStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, s));
							targetStack.get(0).add("</span>");
							targetStack.add(s.getContent());
							return this;
						}

						@Override
						public int visitElementTypes(String elementTypes) throws IOException {
							return 0;
						}

						@Override
						public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
							throw new RuntimeException("Extra attributes not supported");
						}

						@Override
						public void visitStart() throws IOException {
						}

						@Override
						public boolean visitEnd() throws IOException {
							targetStack.remove(0);
							return false;
						}
					});
					if (targetStack.size() != 0)
						throw new RuntimeException();
				}
				if (cch.getVerses().size() == 0)
					continue;
				CHAPTER chapter = f.createCHAPTER();
				chapter.setCnumber(BigInteger.valueOf(cnumber));
				book.getCHAPTER().add(chapter);
				for (VirtualVerse vv : cch.createVirtualVerses()) {
					for (Headline h : vv.getHeadlines()) {
						CAPTION caption = f.createCAPTION();
						EnumCaptionType[] types = new EnumCaptionType[] {
								null, EnumCaptionType.X_H_1, EnumCaptionType.X_H_2, EnumCaptionType.X_H_3,
								EnumCaptionType.X_H_4, EnumCaptionType.X_H_5, EnumCaptionType.X_H_6,
								EnumCaptionType.X_H_6, EnumCaptionType.X_H_6, EnumCaptionType.X_H_6,
						};
						caption.setType(types[h.getDepth()]);
						caption.setVref(BigInteger.valueOf(vv.getNumber()));
						final StringBuilder sb = new StringBuilder();
						h.accept(new FormattedText.VisitorAdapter<RuntimeException>(null) {
							@Override
							protected void beforeVisit() throws RuntimeException {
								throw new IllegalStateException();
							}

							public void visitText(String text) throws RuntimeException {
								sb.append(text);
							};
						});
						caption.getContent().add(sb.toString());
						chapter.getPROLOGOrCAPTIONOrVERS().add(caption);
					}
					VERS vers = f.createVERS();
					vers.setVnumber(BigInteger.valueOf(vv.getNumber()));
					for (DIV prolog : prologs) {
						vers.getContent().add(prolog);
					}
					prologs.clear();
					chapter.getPROLOGOrCAPTIONOrVERS().add(vers);
					for (Verse v : vv.getVerses()) {
						if (!v.getNumber().equals("" + vv.getNumber())) {
							STYLE x = f.createSTYLE();
							x.setCss("font-weight: bold");
							x.getContent().add("(" + v.getNumber() + ")");
							vers.getContent().add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, x));
							vers.getContent().add(" ");
						}
						final List<List<Object>> targetStack = new ArrayList<List<Object>>();
						targetStack.add(vers.getContent());
						v.accept(new FormattedText.Visitor<IOException>() {

							@Override
							public void visitVerseSeparator() throws IOException {
								STYLE x = f.createSTYLE();
								x.setCss("color:gray");
								x.getContent().add("/");
								targetStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, x));
							}

							@Override
							public void visitText(String text) throws IOException {
								targetStack.get(0).add(text);
							}

							@Override
							public FormattedText.Visitor<IOException> visitFormattingInstruction(biblemulticonverter.data.FormattedText.FormattingInstructionKind kind)
									throws IOException {
								STYLE x = f.createSTYLE();
								x.setCss(kind.getCss());
								targetStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, x));
								targetStack.add(0, x.getContent());
								return this;
							}

							@Override
							public Visitor<IOException> visitFootnote() throws IOException {
								DIV x = f.createDIV();
								targetStack.get(0).add(x);
								NOTE n = f.createNOTE();
								x.setNOTE(n);
								n.setType("x-studynote");
								final List<List<Object>> footnoteStack = new ArrayList<List<Object>>();
								footnoteStack.add(n.getContent());
								return new Visitor<IOException>() {

									@Override
									public void visitStart() throws IOException {
									}

									@Override
									public void visitVerseSeparator() throws IOException {
										STYLE x = f.createSTYLE();
										x.setCss("color:gray");
										x.getContent().add("/");
										footnoteStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, x));
									}

									@Override
									public void visitText(String text) throws IOException {
										footnoteStack.get(0).add(text);
									}

									@Override
									public void visitLineBreak(LineBreakKind kind) throws IOException {
										BR br = f.createBR();
										br.setArt(kind == LineBreakKind.PARAGRAPH ? EnumBreak.X_P : EnumBreak.X_NL);
										footnoteStack.get(0).add(" ");
										footnoteStack.get(0).add(br);
									}

									@Override
									public FormattedText.Visitor<IOException> visitFormattingInstruction(biblemulticonverter.data.FormattedText.FormattingInstructionKind kind) throws IOException {
										STYLE x = f.createSTYLE();
										x.setCss(kind.getCss());
										footnoteStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, x));
										footnoteStack.add(0, x.getContent());
										return this;
									}

									@Override
									public Visitor<IOException> visitFootnote() throws IOException {
										throw new RuntimeException("Footnotes in footnotes are not supported");
									}

									@Override
									public Visitor<IOException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
										GRAM gram = f.createGRAM();
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
										footnoteStack.get(0).add(new JAXBElement<GRAM>(new QName("gr"), GRAM.class, gram));
										footnoteStack.add(0, gram.getContent());
										return this;
									}

									@Override
									public FormattedText.Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
										GRAM gram = f.createGRAM();
										gram.setStr(entry);
										footnoteStack.get(0).add(new JAXBElement<GRAM>(new QName("gr"), GRAM.class, gram));
										footnoteStack.add(0, gram.getContent());
										return this;
									}

									@Override
									public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
										throw new RuntimeException("Raw HTML not supported");
									}

									@Override
									public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
										throw new RuntimeException("Variations not supported");
									}

									@Override
									public FormattedText.Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
										STYLE s = f.createSTYLE();
										s.setCss("-zef-dummy: true");
										int bookID = book.getZefID();
										String mscope;
										try {
											int start = firstVerse.equals("^") ? 1 : Integer.parseInt(firstVerse.replaceAll("[a-zG]", ""));
											int end;
											if (firstChapter == lastChapter && !lastVerse.equals("$")) {
												end = Integer.parseInt(lastVerse.replaceAll("[a-z]", ""));
											} else {
												end = -1;
											}
											mscope = bookID + "," + firstChapter + "," + start + "," + end;
										} catch (NumberFormatException ex) {
											ex.printStackTrace();
											mscope = bookID + ",1,1,999";
										}
										footnoteStack.get(0).add("<a href=\"mybible:content=location&amp;locations=" + mscope + "\">");
										footnoteStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, s));
										footnoteStack.get(0).add("</a>");
										footnoteStack.add(0, s.getContent());
										return this;
									}

									public boolean visitEnd() throws IOException {
										footnoteStack.remove(0);
										return false;
									}

									@Override
									public int visitElementTypes(String elementTypes) throws IOException {
										return 0;
									}

									@Override
									public Visitor<IOException> visitHeadline(int depth) throws IOException {
										throw new RuntimeException("Headlines in footnotes not supported");
									}

									@Override
									public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
										STYLE x = f.createSTYLE();
										x.setCss(css);
										footnoteStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, x));
										footnoteStack.add(0, x.getContent());
										return this;
									}

									@Override
									public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
										System.out.println("WARNING: Extra attributes not supported");
										Visitor<IOException> result = prio.handleVisitor(category, this);
										if (result != null)
											footnoteStack.add(0, footnoteStack.get(0));
										return result;
									};
								};
							}

							@Override
							public FormattedText.Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
								throw new RuntimeException("Xref outside of footnotes not supported!");
							}

							@Override
							public void visitLineBreak(LineBreakKind kind) throws IOException {
								BR br = f.createBR();
								br.setArt(kind == LineBreakKind.PARAGRAPH ? EnumBreak.X_P : EnumBreak.X_NL);
								targetStack.get(0).add(" ");
								targetStack.get(0).add(br);
							}

							@Override
							public Visitor<IOException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
								GRAM gram = f.createGRAM();
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
								targetStack.get(0).add(new JAXBElement<GRAM>(new QName("gr"), GRAM.class, gram));
								targetStack.add(0, gram.getContent());
								return this;
							}

							@Override
							public FormattedText.Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
								GRAM gram = f.createGRAM();
								gram.setStr(entry);
								targetStack.get(0).add(new JAXBElement<GRAM>(new QName("gr"), GRAM.class, gram));
								targetStack.add(0, gram.getContent());
								return this;
							}

							@Override
							public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
								throw new RuntimeException("Raw HTML is not supported");
							}

							@Override
							public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
								throw new RuntimeException("Variations not supported");
							}

							@Override
							public boolean visitEnd() throws IOException {
								targetStack.remove(0);
								return false;
							}

							@Override
							public int visitElementTypes(String elementTypes) throws IOException {
								return 0;
							}

							@Override
							public Visitor<IOException> visitHeadline(int depth) throws IOException {
								throw new RuntimeException("Headline in virtual verse is impossible");
							}

							@Override
							public void visitStart() throws IOException {
							}

							@Override
							public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
								STYLE x = f.createSTYLE();
								x.setCss(css);
								targetStack.get(0).add(new JAXBElement<STYLE>(new QName("STYLE"), STYLE.class, x));
								targetStack.add(0, x.getContent());
								return this;
							}

							@Override
							public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
								System.out.println("WARNING: Extra attributes not supported");
								Visitor<IOException> result = prio.handleVisitor(category, this);
								if (result != null)
									targetStack.add(0, targetStack.get(0));
								return result;
							};
						});
						if (targetStack.size() != 0)
							throw new RuntimeException();
					}

				}
			}
			if (book.getCHAPTER().size() == 0) {
				doc.getBIBLEBOOK().remove(book);
			}
		}

		final Document docc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		JAXBContext.newInstance(ObjectFactory.class.getPackage().getName()).createMarshaller().marshal(doc, docc);
		docc.getDocumentElement().setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		docc.getDocumentElement().setAttribute("xsi:noNamespaceSchemaLocation", "zef2005.xsd");
		docc.normalize();
		shiftWhitespaceNodes(docc.getDocumentElement());
		try (FileOutputStream fos = new FileOutputStream(exportArgs[0])) {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			transformer.transform(new DOMSource(docc), new StreamResult(fos));
		}
	}

	private static void shiftWhitespaceNodes(Element elem) {
		// remove dummy styles (<STYLE css="-zef-dummy: true">)
		for (int i = 0; i < elem.getChildNodes().getLength(); i++) {
			Node n = elem.getChildNodes().item(i);
			if (n instanceof Element && n.getNodeName().equals("STYLE")) {
				if (((Element) n).getAttribute("css").equals("-zef-dummy: true")) {
					while (n.getFirstChild() != null) {
						elem.insertBefore(n.getFirstChild(), n);
					}
					elem.removeChild(n);
					i--;
				}
			}
		}

		// first check direct children
		for (int i = 0; i < elem.getChildNodes().getLength(); i++) {
			Node n = elem.getChildNodes().item(i);
			if (n instanceof Text && n.getNodeValue().length() > 0 && n.getNodeValue().trim().length() == 0) {
				Node left = i == 0 ? null : elem.getChildNodes().item(i - 1);
				Node right = i == elem.getChildNodes().getLength() - 1 ? null : elem.getChildNodes().item(i + 1);
				if (left instanceof Element && (left.getNodeName().equals("STYLE") || left.getNodeName().equals("gr") || left.getNodeName().equals("strong"))) {
					left.appendChild(elem.getOwnerDocument().createTextNode(" "));
					left.normalize();
					elem.insertBefore(elem.getOwnerDocument().createComment("sL"), n);
					elem.removeChild(n);
				} else if (right instanceof Element && right.getNodeName().equals("STYLE")) {
					right.insertBefore(elem.getOwnerDocument().createTextNode(" "), right.getFirstChild());
					right.normalize();
					elem.insertBefore(elem.getOwnerDocument().createComment("sR"), n);
					elem.removeChild(n);
				} else if (left instanceof Element && left.getNodeName().equals("DIV")) {
					elem.insertBefore(elem.getOwnerDocument().createComment("sW"), n);
					elem.insertBefore(n, left);
					elem.normalize();
					i = 0;
				} else if (elem.getNodeName().equals("VERS") && (left == null || (left instanceof Element && left.getNodeName().equals("BR"))) && right instanceof Element && right.getNodeName().equals("BR")) {
					// we can ignore this one
					elem.insertBefore(elem.getOwnerDocument().createComment("iG"), n);
					elem.removeChild(n);
				} else {
					System.out.println(elem.getNodeName() + "/" + n.getNodeValue() + "/" + left + "/" + right);
				}
			}
		}

		// then recurse
		for (int i = 0; i < elem.getChildNodes().getLength(); i++) {
			Node n = elem.getChildNodes().item(i);
			if (n instanceof Element)
				shiftWhitespaceNodes((Element) n);
		}
	}

}
