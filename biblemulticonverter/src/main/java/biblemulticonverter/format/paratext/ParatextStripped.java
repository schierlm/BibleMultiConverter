package biblemulticonverter.format.paratext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import biblemulticonverter.Main;
import biblemulticonverter.data.Utils;
import biblemulticonverter.format.paratext.ParatextBook.Figure;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentPart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.PeripheralStart;
import biblemulticonverter.format.paratext.ParatextBook.Remark;
import biblemulticonverter.format.paratext.ParatextBook.SidebarEnd;
import biblemulticonverter.format.paratext.ParatextBook.SidebarStart;
import biblemulticonverter.format.paratext.ParatextBook.TableCellStart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.KeepIf;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import biblemulticonverter.format.paratext.model.Version;

/**
 * Exporter that only exports parts of a Paratext bible.
 */
public class ParatextStripped extends AbstractParatextFormat {

	public static final String[] HELP_TEXT = {
			"Export parts of a Paratext bible",
			"",
			"Usage (export): ParatextStripped [<mode>[=<arg>]...] -- <ParatextExportFormat> [<ExportArgs>...]",
			"",
			"Supported modes:",
			"",
			"StripStudyContent",
			"StripCustomMarkup",
			"StripIntroductions",
			"StripTagAttributes",
			"StripRemarks",
			"StripReferences",
			"StripPart={OT|NT|DC}",
			"StripStudyCategory=<name>",
			"StripParagraph=<tag>[#]",
			"CompatibleVersion=[version]",
	};

	public ParatextStripped() {
		super("ParatextStripped");
	}

	@Override
	protected List<ParatextBook> doImportAllBooks(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void doExportBooks(List<ParatextBook> books, String... exportArgs) throws Exception {
		int formatArg = stripBooks(books, exportArgs);
		AbstractParatextFormat exportFormat = (AbstractParatextFormat) Main.exportFormats.get(exportArgs[formatArg]).getImplementationClass().newInstance();
		exportFormat.doExportBooks(books, Arrays.copyOfRange(exportArgs, formatArg + 1, exportArgs.length));
	}

	protected int stripBooks(List<ParatextBook> books, String... exportArgs) throws Exception {
		Set<ParatextFeature> featuresToStrip = EnumSet.noneOf(ParatextFeature.class);
		EnumSet<KeepIf> partsToKeep = EnumSet.allOf(KeepIf.class);
		Set<String> categoriesToStrip = new HashSet<>();
		Set<ParagraphKind> paragraphKindsToStrip = EnumSet.noneOf(ParagraphKind.class);
		Version compatibleVersionInit = Version.V3;
		int formatArg = 0;
		while (formatArg < exportArgs.length) {
			if (exportArgs[formatArg].equals("--")) {
				formatArg++;
				break;
			} else if (exportArgs[formatArg].startsWith("StripStudyCategory=")) {
				categoriesToStrip.add(exportArgs[formatArg].substring(19));
			} else if (exportArgs[formatArg].startsWith("StripPart=")) {
				partsToKeep.remove(KeepIf.valueOf(exportArgs[formatArg].substring(10)));
			} else if (exportArgs[formatArg].startsWith("StripParagraph=")) {
				String kind = exportArgs[formatArg].substring(15).replaceFirst("#$", ".*");
				for (ParagraphKind pk : ParagraphKind.values()) {
					if (pk.getTag().matches(kind))
						paragraphKindsToStrip.add(pk);
				}
			} else if (exportArgs[formatArg].startsWith("CompatibleVersion=")) {
				String versionString = exportArgs[formatArg].substring(18);
				compatibleVersionInit = Version.V1;
				for (Version v : Version.values()) {
					if (v.toString().compareTo(versionString) <= 0 && compatibleVersionInit.isLowerOrEqualTo(v)) {
						compatibleVersionInit = v;
					}
				}
				System.out.println("NOTE: Set compatible version to " + compatibleVersionInit.toString());
			} else {
				featuresToStrip.add(ParatextFeature.valueOf(exportArgs[formatArg]));
			}
			formatArg++;
		}
		if (featuresToStrip.contains(ParatextFeature.StripIntroductions)) {
			for (ParagraphKind pk : ParagraphKind.values()) {
				if (pk.name().startsWith("INTRO_"))
					paragraphKindsToStrip.add(pk);
			}
		}
		Version compatibleVersion = compatibleVersionInit;
		for (int i = 0; i < books.size(); i++) {
			ParatextBook book = books.get(i);
			boolean keep = true;
			if (book.getId().getId().isDeuterocanonical()) {
				keep = partsToKeep.contains(KeepIf.DC);
			} else if (book.getId().getId().isNT()) {
				keep = partsToKeep.contains(KeepIf.NT);
			} else if (book.getId().getId().getZefID() > 0) {
				keep = partsToKeep.contains(KeepIf.OT);
			}
			if (!keep) {
				books.remove(i);
				i--;
				continue;
			}
			book.getAttributes().entrySet().removeIf(e -> !ParatextBook.getMinVersionForAttribute(e.getKey()).isLowerOrEqualTo(compatibleVersion));
			if (featuresToStrip.contains(ParatextFeature.StripRemarks))
				book.getAttributes().entrySet().removeIf(e -> e.getKey().equals("rem") || e.getKey().startsWith("rem@"));
			for (int j = 0; j < book.getContent().size(); j++) {
				boolean[] skipSidebarHolder = { false }, skipParagraphHolder = { false };
				List<ParatextBookContentPart> partHolder = new ArrayList<>();
				partHolder.add(book.getContent().get(j));
				partHolder.get(0).acceptThis(new ParatextBookContentVisitor<RuntimeException>() {

					@Override
					public void visitChapterStart(ChapterIdentifier location) {
					}

					@Override
					public void visitChapterEnd(ChapterIdentifier location) {
					}

					@Override
					public void visitRemark(String content) {
						if (featuresToStrip.contains(ParatextFeature.StripRemarks)) {
							partHolder.clear();
						}
					}

					@Override
					public void visitParagraphStart(ParagraphKind kind) {
						if (paragraphKindsToStrip.contains(kind))
							skipParagraphHolder[0] = true;
						else if (!kind.getVersion().isLowerOrEqualTo(compatibleVersion)) {
							ParagraphKind replacement = replaceParagraphKind(kind);
							partHolder.set(0, new ParagraphStart(replacement));
						}
					}

					@Override
					public void visitTableCellStart(String tag) {
						if (tag.contains("-") && !Version.V3.isLowerOrEqualTo(compatibleVersion)) {
							if (tag.contains("-")) {
								Matcher m = Utils.compilePattern("(t[hcr]+)([0-9]+)-([0-9]+)").matcher(tag);
								if (!m.matches())
									throw new IllegalStateException();
								String prefix = m.group(1);
								int min = Integer.parseInt(m.group(2));
								int max = Integer.parseInt(m.group(3));
								partHolder.clear();
								for (int i = min; i <= max; i++) {
									partHolder.add(new TableCellStart(prefix + i));
								}
								return;
							}
						}
					}

					@Override
					public void visitSidebarStart(String[] categories) throws RuntimeException {
						HashSet<String> cats = new HashSet<>(Arrays.asList(categories));
						cats.retainAll(categoriesToStrip);
						if (!cats.isEmpty() || featuresToStrip.contains(ParatextFeature.StripStudyContent) || !Version.V2_1.isLowerOrEqualTo(compatibleVersion)) {
							skipSidebarHolder[0] = true;
						}
					}

					@Override
					public void visitSidebarEnd() throws RuntimeException {
					}

					public void visitPeripheralStart(String title, String id) throws RuntimeException {
						if (id != null && !Version.V3.isLowerOrEqualTo(compatibleVersion)) {
							partHolder.set(0, new PeripheralStart(((PeripheralStart) partHolder.get(0)).getTitle(), null));
						}
					};

					@Override
					public void visitVerseStart(VerseIdentifier location, String verseNumber) throws RuntimeException {
					}

					@Override
					public void visitVerseEnd(VerseIdentifier verseLocation) throws RuntimeException {
					}

					@Override
					public void visitFigure(String caption, Map<String, String> attributes) throws RuntimeException {
						if (!Version.V3.isLowerOrEqualTo(compatibleVersion)) {
							attributes.entrySet().removeIf(me -> !Arrays.asList(Figure.FIGURE_PROVIDED_ATTRIBUTES).contains(me.getKey()));
						}
					}

					@Override
					public void visitParatextCharacterContent(ParatextCharacterContent content) {
						filterContents(content.getContent(), featuresToStrip, partsToKeep, categoriesToStrip, compatibleVersion);
					}
				});
				if (skipSidebarHolder[0] && partHolder.get(0) instanceof SidebarStart) {
					while (j < book.getContent().size()) {
						ParatextBookContentPart removedPart = book.getContent().remove(j);
						if (removedPart instanceof SidebarEnd)
							break;
					}
					j--;
				} else if (skipParagraphHolder[0] && partHolder.get(0) instanceof ParagraphStart) {
					book.getContent().remove(j);
					while (j < book.getContent().size() && (book.getContent().get(j) instanceof Figure ||
							book.getContent().get(j) instanceof Remark ||
							book.getContent().get(j) instanceof ParatextCharacterContent ||
							book.getContent().get(j) instanceof TableCellStart)) {
						book.getContent().remove(j);
					}
					j--;
				} else {
					book.getContent().remove(j);
					j--;
					for (ParatextBookContentPart part : partHolder) {
						j++;
						book.getContent().add(j, part);
					}
				}
			}
		}
		return formatArg;
	}

	private void filterContents(List<ParatextCharacterContentPart> parts, Set<ParatextFeature> featuresToStrip, EnumSet<KeepIf> partsToKeep, Set<String> categoriesToStrip, Version compatibleVersion) {
		for (int i = 0; i < parts.size(); i++) {
			boolean[] skipPartHolder = { false, false };
			ParatextCharacterContentPart[] partHolder = { parts.get(i) };
			partHolder[0].acceptThis(new ParatextCharacterContentVisitor<RuntimeException>() {

				@Override
				public ParatextCharacterContentVisitor<RuntimeException> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) throws RuntimeException {
					if (featuresToStrip.contains(ParatextFeature.StripStudyContent) && (kind == FootnoteXrefKind.STUDY_EXTENDED_FOOTNOTE || kind == FootnoteXrefKind.STUDY_EXTENDED_XREF)) {
						skipPartHolder[0] = true;
						return null;
					}
					if (!Version.V2_1.isLowerOrEqualTo(compatibleVersion) && kind == FootnoteXrefKind.STUDY_EXTENDED_FOOTNOTE) {
						ParatextCharacterContent.FootnoteXref fx = new FootnoteXref(FootnoteXrefKind.FOOTNOTE, caller, categories);
						fx.getContent().addAll(((FootnoteXref) partHolder[0]).getContent());
						partHolder[0] = fx;
					} else if (!Version.V2_3.isLowerOrEqualTo(compatibleVersion) && kind == FootnoteXrefKind.STUDY_EXTENDED_XREF) {
						ParatextCharacterContent.FootnoteXref fx = new FootnoteXref(FootnoteXrefKind.XREF, caller, categories);
						fx.getContent().addAll(((FootnoteXref) partHolder[0]).getContent());
						partHolder[0] = fx;
					}
					Set<String> cats = new HashSet<>(Arrays.asList(categories));
					cats.retainAll(categoriesToStrip);
					if (!cats.isEmpty()) {
						skipPartHolder[0] = true;
						return null;
					}
					filterContents(((FootnoteXref) partHolder[0]).getContent(), featuresToStrip, partsToKeep, categoriesToStrip, compatibleVersion);
					return null;
				}

				@Override
				public ParatextCharacterContentVisitor<RuntimeException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) throws RuntimeException {
					if (kind.getKeepIf() != null && !partsToKeep.contains(kind.getKeepIf())) {
						skipPartHolder[0] = true;
						return null;
					}
					filterContents(((ParatextCharacterContentContainer) partHolder[0]).getContent(), featuresToStrip, partsToKeep, categoriesToStrip, compatibleVersion);
					if (featuresToStrip.contains(ParatextFeature.StripTagAttributes)) {
						attributes.clear();
					} else if (!Version.V3.isLowerOrEqualTo(compatibleVersion)) {
						attributes.keySet().removeIf(k -> !k.equals("lemma"));
					}
					if (!kind.getVersion().isLowerOrEqualTo(compatibleVersion)) {
						List<AutoClosingFormattingKind> replacementList = new ArrayList<>();
						replacementList.add(kind);
						for (int i = 0; i < replacementList.size(); i++) {
							if (!replacementList.get(i).getVersion().isLowerOrEqualTo(compatibleVersion)) {
								AutoClosingFormattingKind[] replacements = replaceAutoClosingFormattingKind(replacementList.get(i));
								if (replacements == null) {
									skipPartHolder[0] = true;
									return null;
								}
								replacementList.remove(i);
								replacementList.addAll(i, Arrays.asList(replacements));
								i--;
							}
						}
						if (replacementList.size() == 0) {
							skipPartHolder[1] = true;
						} else {
							AutoClosingFormatting acfRoot = new AutoClosingFormatting(replacementList.get(0));
							acfRoot.getAttributes().putAll(attributes);
							AutoClosingFormatting acf = acfRoot;
							for (int i = 1; i < replacementList.size(); i++) {
								AutoClosingFormatting acfParent = acf;
								acf = new AutoClosingFormatting(replacementList.get(1));
								acf.getAttributes().putAll(attributes);
								acfParent.getContent().add(acf);
							}
							acf.getContent().addAll(((AutoClosingFormatting) partHolder[0]).getContent());
							partHolder[0] = acfRoot;
						}
					}
					return null;
				}

				@Override
				public void visitMilestone(String tag, Map<String, String> attributes) throws RuntimeException {
					if (tag.startsWith("z") && featuresToStrip.contains(ParatextFeature.StripCustomMarkup)) {
						skipPartHolder[0] = true;
					} else if (!Version.V3.isLowerOrEqualTo(compatibleVersion)) {
						skipPartHolder[0] = true;
					} else if (featuresToStrip.contains(ParatextFeature.StripTagAttributes)) {
						attributes.clear();
					}
				}

				@Override
				public void visitReference(Reference reference) throws RuntimeException {
					if (featuresToStrip.contains(ParatextFeature.StripReferences)) {
						partHolder[0] = ParatextCharacterContent.Text.from(((Reference) partHolder[0]).getContent());
					}
				}

				@Override
				public void visitCustomMarkup(String tag, boolean ending) throws RuntimeException {
					if (featuresToStrip.contains(ParatextFeature.StripCustomMarkup))
						skipPartHolder[0] = true;
				}

				@Override
				public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws RuntimeException {
				}

				@Override
				public void visitText(String text) throws RuntimeException {
				}

				@Override
				public void visitEnd() throws RuntimeException {
				}

			});
			if (skipPartHolder[0]) {
				parts.remove(i);
				i--;
			} else if (skipPartHolder[1]) {
				ParatextCharacterContentContainer pcc = (ParatextCharacterContentContainer) parts.remove(i);
				parts.addAll(i, pcc.getContent());
				i--;
			} else {
				parts.set(i, partHolder[0]);
			}
		}
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		throw new UnsupportedOperationException();
	}

	private static enum ParatextFeature {
		StripStudyContent, StripCustomMarkup, StripIntroductions, StripTagAttributes, StripRemarks, StripReferences;
	}

	public static ParagraphKind replaceParagraphKind(ParagraphKind kind) {
		switch (kind) {
		case SECTION_REFERENCE:
			return ParagraphKind.MAJOR_SECTION_REFERENCE;
		case PARAGRAPH_PMO:
			return ParagraphKind.PARAGRAPH_MI;
		case PARAGRAPH_PM:
			return ParagraphKind.PARAGRAPH_PI;
		case PARAGRAPH_PMC:
			return ParagraphKind.PARAGRAPH_MI;
		case PARAGRAPH_PMR:
			return ParagraphKind.PARAGRAPH_RIGHT;
		case PARAGRAPH_QM:
			return ParagraphKind.PARAGRAPH_Q;
		case PARAGRAPH_QM1:
			return ParagraphKind.PARAGRAPH_Q1;
		case PARAGRAPH_QM2:
			return ParagraphKind.PARAGRAPH_Q2;
		case PARAGRAPH_QM3:
			return ParagraphKind.PARAGRAPH_Q3;
		case PARAGRAPH_QM4:
			return ParagraphKind.PARAGRAPH_Q4;
		case HEBREW_NOTE:
			// According to documentation this is very similar to `d`
			// (ParagraphKind.DESCRIPTIVE_TITLE)
			return ParagraphKind.DESCRIPTIVE_TITLE;
		case SEMANTIC_DIVISION:
			// TODO maybe add more than 1 blank line?
			return ParagraphKind.BLANK_LINE;
		case SEMANTIC_DIVISION_1:
			return ParagraphKind.BLANK_LINE;
		case SEMANTIC_DIVISION_2:
			return ParagraphKind.BLANK_LINE;
		case SEMANTIC_DIVISION_3:
			return ParagraphKind.BLANK_LINE;
		case SEMANTIC_DIVISION_4:
			return ParagraphKind.BLANK_LINE;
		case PARAGRAPH_PO:
			return ParagraphKind.PARAGRAPH_P;
		case PARAGRAPH_LH:
			return ParagraphKind.PARAGRAPH_P;
		case PARAGRAPH_LF:
			return ParagraphKind.PARAGRAPH_M;
		case PARAGRAPH_LIM:
			// Documentation is not entirely clear on what the exact difference
			// is between `lim#` and `li#`
			// one is "embedded" the other is not:
			// https://ubsicap.github.io/usfm/lists/index.html#lim
			// The assumption is made here that `lim#` is directly replaceable
			// with `li#`
			return ParagraphKind.PARAGRAPH_LI;
		case PARAGRAPH_LIM1:
			return ParagraphKind.PARAGRAPH_LI1;
		case PARAGRAPH_LIM2:
			return ParagraphKind.PARAGRAPH_LI2;
		case PARAGRAPH_LIM3:
			return ParagraphKind.PARAGRAPH_LI3;
		case PARAGRAPH_LIM4:
			return ParagraphKind.PARAGRAPH_LI4;

		default:
			throw new IllegalStateException("No replacement given for " + kind);
		}
	}

	public static AutoClosingFormattingKind[] replaceAutoClosingFormattingKind(AutoClosingFormattingKind kind) {
		switch (kind) {
		case INTRODUCTION_QUOTED_TEXT:
			return new AutoClosingFormattingKind[] { AutoClosingFormattingKind.QUOTED_TEXT };
		case FOOTNOTE_LABEL:
		case FOOTNOTE_PARAGRAPH:
			return new AutoClosingFormattingKind[] { AutoClosingFormattingKind.FOOTNOTE_TEXT };
		case XREF_OT_CONTENT:
		case XREF_NT_CONTENT:
			return new AutoClosingFormattingKind[] { AutoClosingFormattingKind.XREF_TARGET_REFERENCES };
		case ADDED_PROPER_NAME:
			return new AutoClosingFormattingKind[] { AutoClosingFormattingKind.ADDITION, AutoClosingFormattingKind.PROPER_NAME };
		case RUBY:
			return new AutoClosingFormattingKind[] { AutoClosingFormattingKind.PRONUNCIATION };
		case PRONUNCIATION:
			return new AutoClosingFormattingKind[0];
		case STUDY_CONTENT_CATEGORY:
			return null;
		case WORDS_OF_JESUS:
			return new AutoClosingFormattingKind[0];
		case EMPHASIS:
			return new AutoClosingFormattingKind[] { AutoClosingFormattingKind.ITALIC };
		case LIST_TOTAL:
		case LIST_KEY:
		case LIST_VALUE:
			// It should not be too much of an issue to just skip these list
			// tags
			// E.g.
			// \li1 \lik Reuben\lik* \liv1 Eliezer son of Zichri\liv1*
			// Will become:
			// \li1 Reuben Eliezer son of Zichri
			return new AutoClosingFormattingKind[0];
		case FOOTNOTE_WITNESS_LIST:
			// The Footnote witness list is just extra markup found within a
			// footnote, however according to
			// documentation found here:
			// https://ubsicap.github.io/usfm/v3.0.rc1/notes_basic/fnotes.html
			// Each element within a footnote must start with it's appropriate
			// tag. So we can't just skip this tag
			// since it could contain text. It would be better to turn this into
			// a text entry `ft`.
			return new AutoClosingFormattingKind[] { AutoClosingFormattingKind.FOOTNOTE_TEXT };
		case XREF_PUBLISHED_ORIGIN:
			// Published cross reference origin texts do not exist in USFM 2.x
			// There is not really a nice way to downgrade these, we cannot put
			// the `xop` tag into `xo` because it
			// might not follow the usual `<chapter><separator><verse>` pattern.
			// TODO, maybe we can just write the contents to the parent target,
			// just like FOOTNOTE_WITNESS_LIST?
			return null;
		case XREF_TARGET_REFERENCES_TEXT:
			// "Target reference(s) extra / added text" does not exist in USFM
			// 2.x
			// We should be able to get away with just adding the raw content
			// directly `target`.
			return new AutoClosingFormattingKind[0];
		case SUPERSCRIPT:
			// There is not really a good way to represent superscript in USFM
			// 2.x
			// To avoid losing data, we skip the tag and just add the content
			// directly to `target`.
			// TODO, maybe we can use `sc` (Small caps) or `ord` (Ordinal)
			// instead?
			return new AutoClosingFormattingKind[0];
		case ARAMAIC_WORD:
			// There is not really a good way to represent Aramaic words in USFM
			// 2.x
			// To avoid losing data, we skip the tag and just add the content
			// directly to `target`.
			return new AutoClosingFormattingKind[0];
		case PROPER_NAME_GEOGRAPHIC:
			// This marker just gives geographic names a different presentation,
			// replace by PROPER_NAME.
			return new AutoClosingFormattingKind[] { AutoClosingFormattingKind.PROPER_NAME };
		case LINK:
			// just strip the tag and keep the contents.
			return new AutoClosingFormattingKind[0];
		case EXTENDED_FOOTNOTE_MARK:
			// Use non-extended footnote mark instead
			return new AutoClosingFormattingKind[] { AutoClosingFormattingKind.FOOTNOTE_MARK };
		default:
			throw new IllegalStateException("No replacement given for " + kind);
		}
	}
}
