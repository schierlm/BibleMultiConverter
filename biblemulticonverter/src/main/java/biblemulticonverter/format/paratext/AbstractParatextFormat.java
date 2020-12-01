package biblemulticonverter.format.paratext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.format.RoundtripFormat;
import biblemulticonverter.format.paratext.ParatextBook.ChapterStart;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKindCategory;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphStart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentPart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXrefKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.KeepIf;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentVisitor;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Text;
import biblemulticonverter.format.paratext.ParatextCharacterContent.VerseStart;

/**
 * Base class for Paratext formats (USFM/USFX/USX).
 */
public abstract class AbstractParatextFormat implements RoundtripFormat {

	private static Map<String, AutoClosingFormattingKind> ALL_FORMATTINGS_CSS = AutoClosingFormattingKind.allCSS();

	@Override
	public Bible doImport(File inputFile) throws Exception {
		List<ParatextBook> books = doImportBooks(inputFile);
		String bibleName = null;
		final Map<ParatextID, String> bookAbbrs = new EnumMap<>(ParatextID.class);
		for (ParatextBook book : books) {
			if (bibleName == null || book.getBibleName().isEmpty()) {
				bibleName = book.getBibleName();
			} else {
				String bookBibleName = book.getBibleName();
				// use common suffix
				if (bookBibleName.length() > bibleName.length()) {
					bookBibleName = bookBibleName.substring(bookBibleName.length() - bibleName.length());
				} else if (bibleName.length() > bookBibleName.length()) {
					bibleName = bibleName.substring(bibleName.length() - bookBibleName.length());
				}
				for (int i = bibleName.length() - 1; i >= 0; i--) {
					if (bibleName.charAt(i) != bookBibleName.charAt(i)) {
						bibleName = bibleName.substring(i + 1);
						break;
					}
				}
			}
			String abbr = book.getAttributes().get("toc3"), fallbackAbbr = book.getId().getId().getOsisID().replace("x-", "").replace("-", "");
			if (abbr == null)
				abbr = fallbackAbbr;
			abbr = abbr.replace(" ", "");
			if (!Utils.compilePattern(Utils.BOOK_ABBR_REGEX).matcher(abbr).matches()) {
				System.out.println("WARNING: Unsupported book abbreviation " + abbr + ", using " + fallbackAbbr + " instead");
				abbr = fallbackAbbr;
			}
			bookAbbrs.put(book.getId(), abbr);
		}
		Bible bible = new Bible((bibleName == null || bibleName.isEmpty()) ? "Imported Bible" : bibleName);
		for (ParatextBook book : books) {
			String longName = book.getAttributes().get("toc1");
			if (longName == null || longName.isEmpty())
				longName = book.getId().getEnglishName();
			String shortName = book.getAttributes().get("toc2");
			if (shortName == null || shortName.isEmpty())
				shortName = longName;
			final Book bk = new Book(bookAbbrs.get(book.getId()), book.getId().getId(), shortName, longName);
			bible.getBooks().add(bk);
			final boolean forceProlog = book.getId().getId().getZefID() < 0;
			final ParatextImportContext ctx = new ParatextImportContext();
			ctx.bookAbbrs = bookAbbrs;
			book.accept(new ParatextBookContentVisitor<RuntimeException>() {

				@Override
				public void visitChapterStart(int newChapter) throws RuntimeException {
					if (ctx.cnum != -1 && !ctx.headlines.isEmpty()) {
						System.out.println("WARNING: Ignoring unreferenced headlines");
						ctx.headlines.clear();
					}
					if (ctx.cnum == 0 && newChapter == 1) {
						// we are in prolog (chapter already exists)
						ctx.cnum = newChapter;
					} else if (newChapter >= 1 && newChapter > ctx.cnum) {
						if (ctx.cnum == -1)
							ctx.cnum = 0;
						while (ctx.cnum < newChapter - 1) {
							bk.getChapters().add(new Chapter());
							ctx.cnum++;
						}
						ctx.currentChapter = new Chapter();
						bk.getChapters().add(ctx.currentChapter);
						ctx.cnum = newChapter;
					} else {
						System.out.println("WARNING: Ignoring chapter number " + newChapter + ", current chapter is " + ctx.cnum);
					}
					ctx.currentVisitor = null;
					ctx.currentVerse = null;
					ctx.currentParagraph = ParatextImportContext.CurrentParagraph.NONE;
				}

				@Override
				public void visitParagraphStart(ParagraphKind kind) throws RuntimeException {
					if (ctx.currentParagraph != ParatextImportContext.CurrentParagraph.NONE) {
						if (ctx.currentParagraph == ParatextImportContext.CurrentParagraph.PROLOG ||
								(ctx.currentParagraph == ParatextImportContext.CurrentParagraph.NORMAL && ctx.currentVisitor != null)) {
							ctx.currentVisitor.visitLineBreak(LineBreakKind.PARAGRAPH);
						}
						ctx.currentParagraph = ParatextImportContext.CurrentParagraph.NONE;
					}

					if (kind.getCategory() == ParagraphKindCategory.SKIP) {
						// do nothing
					} else if (kind.getCategory() == ParagraphKindCategory.HEADLINE) {
						Headline hl = null;
						if (kind.isJoinHeadlines() && !ctx.headlines.isEmpty()) {
							hl = ctx.headlines.get(ctx.headlines.size() - 1);
							if (hl.getDepth() == kind.getHeadlineDepth() || kind.getHeadlineDepth() == 0) {
								hl.getAppendVisitor().visitText(" ");
							} else {
								hl = null;
							}
						}
						if (hl == null) {
							hl = new Headline(kind.getHeadlineDepth());
							ctx.headlines.add(hl);
						}
						ctx.currentParagraph = ParatextImportContext.CurrentParagraph.HEADLINE;
						ctx.currentVisitor = hl.getAppendVisitor();
						if (kind.getExtraFormatting() != null) {
							ctx.currentVisitor = ctx.currentVisitor.visitFormattingInstruction(kind.getExtraFormatting());
						}
					} else { // BLANK_LINE, TABLE_ROW, TEXT
						if (kind.isProlog() || forceProlog) {
							if (ctx.cnum == -1) {
								ctx.cnum = 0;
								ctx.currentChapter = new Chapter();
								bk.getChapters().add(ctx.currentChapter);
							}
							if (ctx.currentChapter.getProlog() == null) {
								ctx.currentChapter.setProlog(new FormattedText());
							}
							if (!ctx.currentChapter.getVerses().isEmpty()) {
								System.out.println("WARNING: Adding to prolog after verses have been added!");
							}
							ctx.currentVisitor = ctx.currentChapter.getProlog().getAppendVisitor();
							ctx.currentParagraph = ParatextImportContext.CurrentParagraph.PROLOG;
							ctx.flushHeadlines();
						} else {
							ctx.currentParagraph = ParatextImportContext.CurrentParagraph.NORMAL;
						}
					}
				}

				@Override
				public void visitTableCellStart(String tag) throws RuntimeException {
					ctx.ensureParagraph();
					if (!tag.matches("t[hc]r?1") && ctx.currentParagraph != ParatextImportContext.CurrentParagraph.HEADLINE && ctx.currentVisitor != null) {
						ctx.currentVisitor.visitLineBreak(LineBreakKind.NEWLINE_WITH_INDENT);
					}
				}

				@Override
				public void visitParatextCharacterContent(ParatextCharacterContent content) throws RuntimeException {
					ctx.ensureParagraph();
					content.accept(new ParatextImportVisitor(ctx));
				}
			});
			if (!ctx.headlines.isEmpty()) {
				System.out.println("WARNING: Ignoring unreferenced headlines");
				ctx.headlines.clear();
			}
			for (Chapter ch : bk.getChapters()) {
				if (ch.getProlog() != null)
					ch.getProlog().finished();
				for (Verse v : ch.getVerses())
					v.finished();
			}
		}
		return bible;
	}

	public final List<ParatextBook> doImportBooks(File inputFile) throws Exception {
		List<ParatextBook> result = doImportAllBooks(inputFile);
		String keepParts = System.getProperty("biblemulticonverter.paratext.keepparts", null);
		if (keepParts != null) {
			EnumSet<KeepIf> partsToKeep = EnumSet.noneOf(KeepIf.class);
			for (String flag : keepParts.split("[^A-Za-z]+")) {
				partsToKeep.add(KeepIf.valueOf(flag.toUpperCase()));
			}
			for (int i = 0; i < result.size(); i++) {
				ParatextBook book = result.get(i);
				boolean keep = true;
				if (book.getId().getId().isDeuterocanonical()) {
					keep = partsToKeep.contains(KeepIf.DC);
				} else if (book.getId().getId().isNT()) {
					keep = partsToKeep.contains(KeepIf.NT);
				} else if (book.getId().getId().getZefID() > 0) {
					keep = partsToKeep.contains(KeepIf.OT);
				}
				if (!keep) {
					result.remove(i);
					i--;
					continue;
				}
				for (int j = 0; j < book.getContent().size(); j++) {
					ParatextBookContentPart part = book.getContent().get(i);
					if (part instanceof ParatextCharacterContent) {
						filterContents(((ParatextCharacterContent) part).getContent(), partsToKeep);
					}
				}
			}
		}
		return result;
	}

	private void filterContents(List<ParatextCharacterContentPart> parts, EnumSet<KeepIf> partsToKeep) {
		for (int i = 0; i < parts.size(); i++) {
			ParatextCharacterContentPart part = parts.get(i);
			if (part instanceof AutoClosingFormatting) {
				KeepIf keepCondition = ((AutoClosingFormatting) part).getKind().getKeepIf();
				if (keepCondition != null && !partsToKeep.contains(keepCondition)) {
					parts.remove(i);
					i--;
					continue;
				}
			}
			if (part instanceof ParatextCharacterContentContainer) {
				filterContents(((ParatextCharacterContentContainer) part).getContent(), partsToKeep);
			}
		}
	}

	protected List<ParatextBook> doImportAllBooks(File inputFile) throws Exception {
		List<ParatextBook> result = new ArrayList<ParatextBook>();
		if (!inputFile.isDirectory())
			throw new IOException("Not a directory: " + inputFile);
		for (File file : inputFile.listFiles()) {
			try {
				ParatextBook book = doImportBook(file);
				if (book != null)
					result.add(book);
			} catch (Exception ex) {
				throw new RuntimeException("Failed parsing " + file.getName(), ex);
			}
		}
		result.sort(Comparator.comparing(ParatextBook::getId));
		return result;
	}

	protected abstract ParatextBook doImportBook(File inputFile) throws Exception;

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		List<ParatextBook> books = new ArrayList<>();
		for (Book bk : bible.getBooks()) {
			ParatextID pid = ParatextID.fromBookID(bk.getId());
			if (pid == null) {
				System.out.println("WARNING: Skipping unsupported book " + bk.getId());
				continue;
			}
			ParatextBook book = new ParatextBook(pid, bible.getName());
			books.add(book);
			book.getAttributes().put("toc1", bk.getLongName());
			book.getAttributes().put("toc2", bk.getShortName());
			book.getAttributes().put("toc3", bk.getAbbr());
			ParatextExportContext ctx = new ParatextExportContext(book);
			for (int cnum = 1; cnum <= bk.getChapters().size(); cnum++) {
				Chapter ch = bk.getChapters().get(cnum - 1);
				if (cnum > 1)
					ctx.startChapter(cnum);
				if (ch.getProlog() != null) {
					ch.getProlog().accept(new ParatextExportVisitor("in prolog", bk.getId().isNT(), ctx, null, cnum == 1 ? ParagraphKind.INTRO_PARAGRAPH_P : ParagraphKind.CHAPTER_DESCRIPTION, null));
				}
				if (cnum == 1)
					ctx.startChapter(cnum);
				for (Verse v : ch.getVerses()) {
					v.accept(new ParatextExportVisitor("in verse", bk.getId().isNT(), ctx, null, ParagraphKind.PARAGRAPH_P, v.getNumber()));
				}
			}
		}
		doExportBooks(books, exportArgs);
	}

	public void doExportBooks(List<ParatextBook> books, String... exportArgs) throws Exception {
		File baseDir = new File(exportArgs[0]);
		baseDir.mkdirs();
		String namePattern = exportArgs[1];
		for (ParatextBook book : books) {
			String name = namePattern.replace("#", book.getId().getNumber()).replace("*", book.getId().getIdentifier());
			doExportBook(book, new File(baseDir, name));
		}
	}

	protected abstract void doExportBook(ParatextBook book, File outFile) throws Exception;

	@Override
	public final boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public final boolean isImportExportRoundtrip() {
		return false;
	}

	private static class ParatextImportContext {
		private int cnum = -1;
		private Chapter currentChapter = null;
		private Verse currentVerse = null;
		private List<Headline> headlines = new ArrayList<>();
		private Visitor<RuntimeException> currentVisitor;
		private CurrentParagraph currentParagraph = CurrentParagraph.NONE;
		private Map<ParatextID, String> bookAbbrs;

		private void flushHeadlines() {
			for (Headline hl : headlines) {
				hl.accept(currentVisitor.visitHeadline(hl.getDepth()));
			}
			headlines.clear();
		}

		private void ensureParagraph() {
			if (currentParagraph == CurrentParagraph.NONE) {
				System.out.println("WARNING: No paragraph open; opening \\p");
				currentParagraph = CurrentParagraph.NORMAL;
			}
		}

		private static enum CurrentParagraph {
			NONE, PROLOG, HEADLINE, NORMAL
		}
	}

	public class ParatextImportVisitor implements ParatextCharacterContentVisitor<RuntimeException> {

		private ParatextImportContext ctx;
		private List<Visitor<RuntimeException>> visitorStack = new ArrayList<>();

		public Visitor<RuntimeException> getCurrentVisitor() {
			Visitor<RuntimeException> v = visitorStack.get(visitorStack.size() - 1);
			return v != null ? v : ctx.currentVisitor;
		}

		public ParatextImportVisitor(ParatextImportContext ctx) {
			this.ctx = ctx;
			visitorStack.add(null);
		}

		@Override
		public void visitVerseStart(String verseNumber) {
			if (visitorStack.size() != 1) {
				System.out.println("WARNING: Skipping verse number nested deeply in character markup");
				return;
			}
			if (ctx.currentParagraph != ParatextImportContext.CurrentParagraph.NORMAL) {
				System.out.println("WARNING: Skipping verse number inside paragraph type " + ctx.currentParagraph);
				return;
			}
			if (ctx.currentChapter == null) {
				System.out.println("WARNING: Skipping verse number outside of chapter");
				return;
			}
			ctx.currentVerse = new Verse(verseNumber);
			ctx.currentChapter.getVerses().add(ctx.currentVerse);
			ctx.currentVisitor = ctx.currentVerse.getAppendVisitor();
			ctx.flushHeadlines();
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitFootnoteXref(FootnoteXrefKind kind, String caller) {
			if (ctx.currentParagraph == ParatextImportContext.CurrentParagraph.NORMAL && getCurrentVisitor() == null) {
				System.out.println("WARNING: Skipping footnote/xref outside of verse/headline/prolog");
				return null;
			}
			Visitor<RuntimeException> v = getCurrentVisitor().visitFootnote();
			if (kind == FootnoteXrefKind.XREF)
				v.visitText(FormattedText.XREF_MARKER);
			visitorStack.add(v);
			return this;
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) {
			if (ctx.currentParagraph == ParatextImportContext.CurrentParagraph.NORMAL && getCurrentVisitor() == null) {
				System.out.println("WARNING: Skipping formatting outside of verse/headline/prolog");
				return null;
			}
			Visitor<RuntimeException> newVisitor;
			if (kind.getLineBreakKind() != null) {
				getCurrentVisitor().visitLineBreak(kind.getLineBreakKind());
				newVisitor = getCurrentVisitor();
			} else if (kind.getFormat() != null) {
				newVisitor = getCurrentVisitor().visitFormattingInstruction(kind.getFormat());
			} else if (kind == AutoClosingFormattingKind.WORDLIST && !attributes.isEmpty()) {
				StringBuilder strongsPrefixes = new StringBuilder();
				List<Integer> strongs = new ArrayList<>();
				String strongAttribute = attributes.get("strong");
				if (strongAttribute != null) {
					for (String strong : strongAttribute.split("[, ]")) {
						if (strong.matches("[A-Z][0-9]+")) {
							strongsPrefixes.append(strong.charAt(0));
							int num = Integer.parseInt(strong.substring(1));
							if (num > 0) {
								strongs.add(num);
							} else {
								System.out.println("WARNING: Skipping unsupported strong number: " + strong);
							}
						} else {
							System.out.println("WARNING: Skipping unsupported strong number: " + strong);
						}
					}
				}
				List<String> rmacs = new ArrayList<>();
				String morphAttribute = attributes.get("x-morph");
				if (morphAttribute != null && morphAttribute.startsWith("robinson:")) {
					for (String rmac : morphAttribute.split("[, ]")) {
						if (rmac.startsWith("robinson:"))
							rmac = rmac.substring("robinson:".length());
						if (Utils.compilePattern(Utils.RMAC_REGEX).matcher(rmac).matches()) {
							rmacs.add(rmac);
						} else {
							System.out.println("Skipping unsupported RMAC: " + rmac);
						}
					}
				}
				int[] strongsArray = strongs.isEmpty() ? null : strongs.stream().mapToInt(s -> s).toArray();
				if (rmacs.isEmpty() && strongsArray == null) {
					newVisitor = getCurrentVisitor().visitCSSFormatting(kind.getCss());
				} else {
					newVisitor = getCurrentVisitor().visitGrammarInformation(strongsPrefixes.toString().toCharArray(), strongsArray, rmacs.isEmpty() ? null : rmacs.toArray(new String[rmacs.size()]), null);
				}
			} else {
				newVisitor = getCurrentVisitor().visitCSSFormatting(kind.getCss());
			}
			visitorStack.add(newVisitor);
			return this;
		}

		@Override
		public void visitReference(Reference reference) throws RuntimeException {
			if (ctx.currentParagraph == ParatextImportContext.CurrentParagraph.NORMAL && getCurrentVisitor() == null) {
				System.out.println("WARNING: Skipping text outside of verse/headline/prolog");
				return;
			}
			getCurrentVisitor().visitCrossReference(ctx.bookAbbrs.get(reference.getBook()), reference.getBook().getId(), reference.getFirstChapter(), reference.getFirstVerse(), reference.getLastChapter(), reference.getLastVerse()).visitText(reference.getContent());
		}

		@Override
		public void visitText(String text) {
			if (ctx.currentParagraph == ParatextImportContext.CurrentParagraph.NORMAL && getCurrentVisitor() == null) {
				System.out.println("WARNING: Skipping text outside of verse/headline/prolog");
				return;
			}
			getCurrentVisitor().visitText(text);
		}

		@Override
		public void visitEnd() {
			visitorStack.remove(visitorStack.size() - 1);
		}
	}

	private static class ParatextExportContext {
		private ParatextBook book;
		private ParagraphKind currentParagraph;
		private ParatextCharacterContent charContent;

		public ParatextExportContext(ParatextBook book) {
			this.book = book;
		}

		public void startChapter(int cnum) {
			book.getContent().add(new ChapterStart(cnum));
			currentParagraph = null;
			charContent = null;
		}

		public void closeParagraph() {
			currentParagraph = null;
			charContent = null;
		}

		public ParatextCharacterContent getCharContent(ParagraphKind kind) {
			if (currentParagraph != kind) {
				book.getContent().add(new ParagraphStart(kind));
				currentParagraph = kind;
				charContent = null;
			}
			if (charContent == null) {
				charContent = new ParatextCharacterContent();
				book.getContent().add(charContent);
			}
			return charContent;
		}
	}

	private static class ParatextExportVisitor implements Visitor<RuntimeException> {

		private final String location;
		private final boolean nt;
		private final ParatextExportContext ctx;
		private final ParatextCharacterContentContainer ccnt;
		private final ParagraphKind paragraphKind;
		private final String vnum;

		public ParatextExportVisitor(String location, boolean nt, ParatextExportContext ctx, ParatextCharacterContentContainer ccnt, ParagraphKind paragraphKind, String vnum) {
			this.location = location;
			this.nt = nt;
			this.ctx = ctx;
			this.ccnt = ccnt;
			this.paragraphKind = paragraphKind;
			this.vnum = vnum;
			if ((ccnt == null) == (ctx == null))
				throw new IllegalStateException();
			if ((ctx == null) != (paragraphKind == null))
				throw new IllegalStateException();
		}

		private ParatextCharacterContentContainer getCharContent() {
			if (ccnt != null)
				return ccnt;
			else
				return ctx.getCharContent(paragraphKind);
		}

		@Override
		public int visitElementTypes(String elementTypes) {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) {
			if (ctx == null) {
				System.out.println("WARNING: Headlines are not supported " + location);
				return null;
			}
			ParagraphKind[] headlineKinds;
			if (paragraphKind.isProlog()) {
				headlineKinds = new ParagraphKind[] {
						ParagraphKind.INTRO_MAJOR_TITLE_1,
						ParagraphKind.INTRO_SECTION_1,
						ParagraphKind.INTRO_SECTION_2,
						ParagraphKind.INTRO_SECTION_3,
						ParagraphKind.INTRO_SECTION_4,
						ParagraphKind.INTRO_SECTION_5,
						ParagraphKind.INTRO_SECTION_6,
						ParagraphKind.INTRO_SECTION_7,
						ParagraphKind.INTRO_SECTION_8,
				};
			} else {
				headlineKinds = new ParagraphKind[] {
						ParagraphKind.MAJOR_TITLE_1,
						ParagraphKind.MAJOR_SECTION_1,
						ParagraphKind.MAJOR_SECTION_2,
						ParagraphKind.MAJOR_SECTION_3,
						ParagraphKind.MAJOR_SECTION_4,
						ParagraphKind.SECTION_1,
						ParagraphKind.SECTION_2,
						ParagraphKind.SECTION_3,
						ParagraphKind.SECTION_4,
						ParagraphKind.DESCRIPTIVE_TITLE
				};
			}
			ctx.closeParagraph();
			return new ParatextExportVisitor("in headline", nt, null, ctx.getCharContent(headlineKinds[depth - 1]), null, null);
		}

		@Override
		public void visitStart() {
			if (vnum != null) {
				getCharContent().getContent().add(new VerseStart(vnum));
			}
		}

		@Override
		public void visitText(String text) {
			getCharContent().getContent().add(new Text(text));
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() {
			final FootnoteXref footnote = new FootnoteXref(FootnoteXrefKind.FOOTNOTE, "+");
			getCharContent().getContent().add(footnote);

			return new VisitorAdapter<RuntimeException>(new ParatextExportVisitor("in footnote", nt, null, footnote, null, null)) {
				boolean start = true;

				@Override
				public void visitText(String text) throws RuntimeException {
					if (start && text.startsWith(FormattedText.XREF_MARKER)) {
						text = text.substring(FormattedText.XREF_MARKER.length());
						footnote.setKind(FootnoteXrefKind.XREF);
					}
					start = false;
					super.visitText(text);
				}

				@Override
				protected void beforeVisit() throws RuntimeException {
					start = false;
				}
			};
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) {
			ParatextID paratextID = ParatextID.fromBookID(book);

			// Book only or chapter only or book ranges are not supported by the internal format.
			Reference reference;
			if (firstChapter == lastChapter && firstVerse.equals(lastVerse)) {
				reference = Reference.verse(paratextID, firstChapter, firstVerse, "");
			} else {
				reference = Reference.verseRange(paratextID, firstChapter, firstVerse, lastChapter, lastVerse, "");
			}
			getCharContent().getContent().add(reference);
			return new VisitorAdapter<RuntimeException>(this) {
				boolean start = true;

				@Override
				protected void beforeVisit() throws RuntimeException {
					start = false;
				}

				@Override
				public void visitText(String text) throws RuntimeException {
					if (start) {
						reference.setContent(text);
						start = false;
					} else {
						super.visitText(text);
					}
				}
			};
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) {
			return visitCSSFormatting(kind.getCss());
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) {
			AutoClosingFormattingKind kind = ALL_FORMATTINGS_CSS.get(css);
			if (kind == null && css.equals("font-style: italic; myBibleType=note")) {
				kind = AutoClosingFormattingKind.ADDITION;
			}
			if (kind == null) {
				// fuzzy search
				Set<String> cleanedCSS = new HashSet<>(Arrays.asList(css.toLowerCase().replace(" ", "").split(";")));
				if (cleanedCSS.contains("font-weight:bold") && cleanedCSS.contains("font-style:italic"))
					kind = AutoClosingFormattingKind.BOLD_ITALIC;
				else if (cleanedCSS.contains("font-weight:bold"))
					kind = AutoClosingFormattingKind.BOLD;
				else if (cleanedCSS.contains("font-style:italic"))
					kind = AutoClosingFormattingKind.ITALIC;
				else if (cleanedCSS.contains("font-variant:small-caps"))
					kind = AutoClosingFormattingKind.SMALL_CAPS;
				else if (cleanedCSS.contains("color:red"))
					kind = AutoClosingFormattingKind.WORDS_OF_JESUS;
				if (kind != null) {
					System.out.println("WARNING: Replaced CSS formatting \"" + css + "\" by fuzzy match \"" + kind.getCss() + "\" (tag: \\" + kind.getTag() + ")");
				}
			}
			if (kind == null) {
				System.out.println("WARNING: No tag found for formatting: " + css);
				return this;
			}
			final AutoClosingFormatting formatting = new AutoClosingFormatting(kind, false);
			getCharContent().getContent().add(formatting);
			return new ParatextExportVisitor("in formatting", nt, null, formatting, null, null);
		}

		@Override
		public void visitVerseSeparator() {
			visitText("/");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) {
			if (ctx != null) {
				ctx.closeParagraph();
			} else {
				getCharContent().getContent().add(new AutoClosingFormatting(AutoClosingFormattingKind.FOOTNOTE_PARAGRAPH, false));
			}
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
			final AutoClosingFormatting formatting = new AutoClosingFormatting(AutoClosingFormattingKind.WORDLIST, false);
			if (strongs != null) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < strongs.length; i++) {
					if (sb.length() != 0)
						sb.append(",");
					sb.append((strongsPrefixes != null ? "" + strongsPrefixes[i] : nt ? "G" : "H") + strongs[i]);
				}
				formatting.getAttributes().put("strong", sb.toString());
			}
			if (rmac != null) {
				formatting.getAttributes().put("x-morph", "robinson:" + String.join(",", Arrays.asList(rmac)));
			}
			getCharContent().getContent().add(formatting);
			return new ParatextExportVisitor("in formatting", nt, null, formatting, null, null);
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) {
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) {
			System.out.println("WARNING: Raw HTML is not supported");
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) {
			throw new UnsupportedOperationException("Variation text not supported");
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) {
			return prio.handleVisitor(category, this);
		}

		@Override
		public boolean visitEnd() {
			return false;
		}
	}
}
