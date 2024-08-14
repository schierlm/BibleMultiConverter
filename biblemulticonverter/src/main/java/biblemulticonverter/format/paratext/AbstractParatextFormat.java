package biblemulticonverter.format.paratext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
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
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;
import biblemulticonverter.format.paratext.utilities.LocationParser;
import biblemulticonverter.format.paratext.utilities.StandardExportWarningMessages;

/**
 * Base class for Paratext formats (USFM/USFX/USX).
 */
public abstract class AbstractParatextFormat implements RoundtripFormat {

	private static final boolean exportAllTags = Boolean.getBoolean("paratext.exportalltags");

	private static Map<String, AutoClosingFormattingKind> ALL_FORMATTINGS_CSS = AutoClosingFormattingKind.allCSS();

	protected static final Set<String> BOOK_HEADER_ATTRIBUTE_TAGS = new HashSet<>(Arrays.asList(
			"ide", "sts",
			"h", "h1", "h2", "h3",
			"toc1", "toc2", "toc3", "toca1", "toca2", "toca3")
	);

	protected static String getBibleName(List<ParatextBook> books) {
		String bibleName = null;
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
		}
		return bibleName;
	}

	private final String formatName;
	protected final StandardExportWarningMessages warningLogger;

	public AbstractParatextFormat(String formatName) {
		this.formatName = formatName;
		this.warningLogger = new StandardExportWarningMessages(formatName);
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		List<ParatextBook> books = doImportBooks(inputFile);
		final String bibleName = getBibleName(books);
		final Map<ParatextID, String> bookAbbrs = new EnumMap<>(ParatextID.class);
		for (ParatextBook book : books) {
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
		Bible bible = new Bible((bibleName == null || bibleName.isEmpty()) ? "Imported Bible" : bibleName.trim());
		List<Book> sidebars = new ArrayList<>();
		for (ParatextBook book : books) {
			bible.getBooks().add(importParatextBook(book, bookAbbrs, sidebars));
		}
		bible.getBooks().addAll(sidebars);
		return bible;
	}

	protected final Book importParatextBook(ParatextBook book, Map<ParatextID, String> bookAbbrs, List<Book> sidebars) {
		String longName = book.getAttributes().get("toc1");
		if (longName == null || longName.isEmpty())
			longName = book.getId().getEnglishName();
		String shortName = book.getAttributes().get("toc2");
		if (shortName == null || shortName.isEmpty())
			shortName = longName;
		final Book bk = new Book(bookAbbrs.get(book.getId()), book.getId().getId(), shortName, longName);
		final boolean forceProlog = book.getId().getId().getZefID() < 0;
		final ParatextImportContext ctx = new ParatextImportContext();
		ctx.bk = bk;
		ctx.bookAbbrs = bookAbbrs;
		book.fixTrailingWhitespace();
		book.accept(new ParatextBookContentVisitor<RuntimeException>() {

			@Override
			public void visitChapterStart(ChapterIdentifier location) throws RuntimeException {
				if (ctx.contextOutsideSidebar != null) {
					System.out.println("WARNING: ignoring chapter start inside sidebar");
					return;
				}
				if (ctx.cnum != -1 && !ctx.headlines.isEmpty()) {
					System.out.println("WARNING: Ignoring unreferenced headlines");
					ctx.headlines.clear();
				}
				int newChapter = location.chapter;
				if (ctx.cnum == 0 && newChapter == 1) {
					// we are in prolog (chapter already exists)
					ctx.cnum = newChapter;
				} else if (newChapter >= 1 && newChapter > ctx.cnum) {
					if (ctx.cnum == -1)
						ctx.cnum = 0;
					while (ctx.cnum < newChapter - 1) {
						ctx.bk.getChapters().add(new Chapter());
						ctx.cnum++;
					}
					ctx.currentChapter = new Chapter();
					ctx.bk.getChapters().add(ctx.currentChapter);
					ctx.cnum = newChapter;
				} else {
					System.out.println("WARNING: Ignoring chapter number " + newChapter + ", current chapter is " + ctx.cnum);
				}
				ctx.currentVisitor = null;
				ctx.currentVisitorExtraCSS = null;
				ctx.currentVerse = null;
				ctx.currentParagraph = ParatextImportContext.CurrentParagraph.NONE;
				ctx.currentParagraphExtraCSS = null;
			}

			@Override
			public void visitChapterEnd(ChapterIdentifier location) throws RuntimeException {
				// Not supported in the internal format
			}

			@Override
			public void visitRemark(String content) throws RuntimeException {
				// Not supported in the internal format
			}

			@Override
			public void visitParagraphStart(ParagraphKind kind) throws RuntimeException {
				if (exportAllTags && kind.getCategory() == ParagraphKindCategory.SKIP && ctx.currentVisitor != null) {
					ctx.currentVisitor.visitCSSFormatting("-bmc-usfm-tag: " + kind.getTag()).visitText("\uFEFF");
				}
				ctx.outsideCellVisitor = null;
				ExtendedLineBreakKind elbk = kind.getLineBreakKind();
				if (ctx.currentParagraph != ParatextImportContext.CurrentParagraph.NONE) {
					if (ctx.currentParagraph == ParatextImportContext.CurrentParagraph.PROLOG ||
							(ctx.currentParagraph == ParatextImportContext.CurrentParagraph.NORMAL && ctx.currentVisitor != null)) {
						if (elbk != null) {
							ctx.currentVisitor.visitLineBreak(elbk.toLineBreakKind(kind.getLineBreakIndent()));
							elbk = null;
						} else {
							ctx.currentVisitor.visitLineBreak(LineBreakKind.PARAGRAPH);
						}
					}
					if (ctx.currentParagraph != ParatextImportContext.CurrentParagraph.NORMAL) {
						ctx.currentVisitor = null;
						ctx.currentVisitorExtraCSS = null;
					}
					ctx.currentParagraph = ParatextImportContext.CurrentParagraph.NONE;
					ctx.currentParagraphExtraCSS = null;
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
					if (exportAllTags) {
						ctx.currentParagraphExtraCSS = "-bmc-usfm-tag: " + kind.getTag();
						ctx.currentVisitor = ctx.currentVisitor.visitCSSFormatting(ctx.currentParagraphExtraCSS);
					} else {
						ctx.currentParagraphExtraCSS = null;
					}
					ctx.currentVisitorExtraCSS = ctx.currentParagraphExtraCSS;
				} else { // BLANK_LINE, TABLE_ROW, TEXT, EXTRA_ATTRIBUTE
					if (kind.isProlog() || forceProlog || ctx.contextOutsideSidebar != null) {
						if (ctx.cnum == -1) {
							ctx.cnum = 0;
							ctx.currentChapter = new Chapter();
							ctx.bk.getChapters().add(ctx.currentChapter);
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
						if (exportAllTags) {
							ctx.currentParagraphExtraCSS = "-bmc-usfm-tag: " + kind.getTag();
							ctx.currentVisitor = ctx.currentVisitor.visitCSSFormatting(ctx.currentParagraphExtraCSS);
						} else {
							ctx.currentParagraphExtraCSS = null;
						}
						ctx.currentVisitorExtraCSS = ctx.currentParagraphExtraCSS;
						if (kind.getCategory() == ParagraphKindCategory.EXTRA_ATTRIBUTE_META) {
							ctx.currentVisitor = ctx.currentVisitor.visitExtraAttribute(ExtraAttributePriority.SKIP, "paratext", "meta-paragraph", kind.getTag());
						}
					} else {
						ctx.currentParagraph = ParatextImportContext.CurrentParagraph.NORMAL;
						if (kind == ParagraphKind.DESCRIPTIVE_TITLE) {
							ctx.currentParagraphExtraCSS = "font-style: italic; -bmc-psalm-title: true;" + (exportAllTags ? " -bmc-usfm-tag: " + kind.getTag() : "");
						} else if (kind != ParagraphKind.PARAGRAPH_P && exportAllTags) {
							ctx.currentParagraphExtraCSS = "-bmc-usfm-tag: " + kind.getTag();
						} else {
							ctx.currentParagraphExtraCSS = null;
						}
					}
					if (ctx.currentVisitor != null && elbk != null) {
						ctx.currentVisitor.visitLineBreak(elbk.toLineBreakKind(kind.getLineBreakIndent()));
					}
				}
			}

			@Override
			public void visitTableCellStart(String tag) throws RuntimeException {
				ctx.ensureParagraph();
				if (exportAllTags && ctx.currentVisitor != null) {
					ctx.currentVisitor.visitCSSFormatting("-bmc-usfm-tag: " + tag).visitText("\uFEFF");
				}
				if (ctx.currentParagraph != ParatextImportContext.CurrentParagraph.HEADLINE && ctx.currentVisitor != null) {
					ExtendedLineBreakKind lbk = ExtendedLineBreakKind.TABLE_ROW_FIRST_CELL;
					if (ctx.outsideCellVisitor != null) {
						ctx.currentVisitor = ctx.outsideCellVisitor;
						lbk = ExtendedLineBreakKind.TABLE_ROW_NEXT_CELL;
					}
					int indent = tag.contains("r") ? ExtendedLineBreakKind.INDENT_RIGHT_JUSTIFIED : 0;
					Matcher colspanMatcher = Utils.compilePattern("t[hcr]+([0-9]+)-([0-9]+)").matcher(tag);
					if (colspanMatcher.matches()) {
						int colspan = Integer.parseInt(colspanMatcher.group(2)) - Integer.parseInt(colspanMatcher.group(1)) + 1;
						if (colspan > 1)
							indent = colspan;
					}
					ctx.currentVisitor.visitLineBreak(lbk.toLineBreakKind(indent));
					ctx.outsideCellVisitor = ctx.currentVisitor;
					if (tag.startsWith("th")) {
						ctx.currentVisitor = ctx.currentVisitor.visitFormattingInstruction(FormattingInstructionKind.BOLD);
					}
				}
			}

			@Override
			public void visitSidebarStart(String[] categories) throws RuntimeException {
				int snum = sidebars == null ? 1 : sidebars.size() + 1;
				Book sidebar = new Book("Sb"+snum, BookID.DICTIONARY_ENTRY, "Sidebar "+snum, "Sidebar "+snum);
				ctx.startSidebar(sidebar);
				if (sidebars == null) {
					System.out.println("WARNING: Discarding sidebar content (exporting to single book)");
				} else {
					sidebars.add(sidebar);
				}
			}

			@Override
			public void visitSidebarEnd() throws RuntimeException {
				ctx.endSidebar();
			}

			@Override
			public void visitPeripheralStart(String title, String id) throws RuntimeException {
				Headline hl = new Headline(1);
				ctx.headlines.add(hl);
				Visitor<RuntimeException> v = hl.getAppendVisitor();
				if (exportAllTags) {
					v = v.visitCSSFormatting("-bmc-usfm-periph-id: "+id);
				}
				v.visitText(title);
			}


			@Override
			public void visitVerseStart(VerseIdentifier location, String verseNumber) {
				if (ctx.currentParagraph != ParatextImportContext.CurrentParagraph.NORMAL) {
					System.out.println("WARNING: Skipping verse number inside paragraph type " + ctx.currentParagraph);
					return;
				}
				if (ctx.currentChapter == null) {
					System.out.println("WARNING: Skipping verse number outside of chapter");
					return;
				}

				if (Verse.isValidNumber(verseNumber)) {
					// Try the raw full number first, which can be 5, 6-7, 6-7a, or even 6a-7b.
					ctx.currentVerse = new Verse(verseNumber);
				} else if (Verse.isValidNumber(location.verse())) {
					// Try the verse from the identifier if the above fails.
					ctx.currentVerse = new Verse(location.verse());
				} else if (Verse.isValidNumber(location.startVerse)) {
					System.out.println("WARNING: Using shortened verse number (" + location.startVerse + "), because the full verse number (" + verseNumber + ") is not a valid number for conversion");
					// raw full number and identifier verse are both not valid for the internal format, fallback to the first number which can be: 5, 6a etc.
					ctx.currentVerse = new Verse(location.startVerse);
				} else {
					System.out.println("WARNING: Skipping verse number, because it is not a valid number for conversion: " + verseNumber);
					return;
				}
				ctx.currentChapter.getVerses().add(ctx.currentVerse);
				ctx.currentVisitor = ctx.currentVerse.getAppendVisitor();
				ctx.flushHeadlines();
				ctx.currentVisitorExtraCSS = ctx.currentParagraphExtraCSS;
				if (ctx.currentParagraphExtraCSS != null) {
					ctx.currentVisitor = ctx.currentVisitor.visitCSSFormatting(ctx.currentParagraphExtraCSS);
				}
				if (exportAllTags && !verseNumber.equals(ctx.currentVerse.getNumber())) {
					ctx.currentVisitor = ctx.currentVisitor.visitCSSFormatting("-bmc-usfm-verse: " + verseNumber);
				}
			}

			@Override
			public void visitVerseEnd(VerseIdentifier verseLocation) {
				// Internal format does not have verse end milestones
			}


			@Override
			public void visitFigure(String caption, Map<String, String> attributes) throws RuntimeException {
				if (ctx.currentVisitor == null) {
					System.out.println("WARNING: Skipping figure outside of verse/headline/prolog");
					return;
				}
				String src = attributes.getOrDefault("src", "");
				Visitor<RuntimeException> v = ctx.currentVisitor.visitFormattingInstruction(FormattingInstructionKind.ITALIC);
				v.visitRawHTML(RawHTMLMode.OFFLINE, "<a href=\"" + src + "\">");
				v.visitRawHTML(RawHTMLMode.ONLINE, "<img src=\"" + src + "\" alt=\"");
				v.visitText(caption);
				v.visitRawHTML(RawHTMLMode.OFFLINE, "</a>");
				v.visitRawHTML(RawHTMLMode.ONLINE, "\">");
			}

			@Override
			public void visitParatextCharacterContent(ParatextCharacterContent content) throws RuntimeException {
				ctx.ensureParagraph();
				content.accept(new ParatextImportVisitor(ctx));
			}
		});
		if (ctx.contextOutsideSidebar != null) {
			System.out.println("WARNING: Closing unclosed sidebar");
			ctx.endSidebar();
		}
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
		return bk;
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
		File propertyFile = new File(inputFile, "biblemulticonverter.properties");
		if (propertyFile.exists()) {
			Properties props = new Properties();
			try (FileInputStream fis = new FileInputStream(propertyFile)) {
				props.load(fis);
			}
			for (String name : props.stringPropertyNames()) {
				if (System.getProperty(name) == null)
					System.setProperty(name, props.getProperty(name));
			}
		}
		for (File file : inputFile.listFiles()) {
			if (file.getName().equals("biblemulticonverter.properties"))
				continue;
			try {
				ParatextBook book = doImportBook(file);
				if (book != null)
					result.add(book);
			} catch (Exception ex) {
				throw new RuntimeException("Failed parsing " + file.getName(), ex);
			}
		}
		Map<ParatextID, Integer> bookOrder = new EnumMap<>(ParatextID.class);
		String bookOrderProperty = System.getProperty("biblemulticonverter.paratext.bookorder");
		if (bookOrderProperty != null && !bookOrderProperty.isEmpty()) {
			ParatextID[] values = ParatextID.values();
			int order = 0;
			for (String range : bookOrderProperty.split(",")) {
				String[] bounds = range.split("-", 2);
				ParatextID idFrom = ParatextID.fromIdentifier(bounds[0]);
				ParatextID idTo = bounds.length == 2 ? ParatextID.fromIdentifier(bounds[1]) : idFrom;
				for (int i = idFrom.ordinal(); i <= idTo.ordinal(); i++) {
					bookOrder.put(values[i], ++order);
				}
			}
		}
		result.sort(Comparator.<ParatextBook, Integer> comparing(b -> bookOrder.getOrDefault(b.getId(), Integer.MAX_VALUE)).thenComparing(ParatextBook::getId));
		return result;
	}

	protected abstract ParatextBook doImportBook(File inputFile) throws Exception;

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		List<ParatextBook> books = new ArrayList<>();
		for (Book bk : bible.getBooks()) {
			ParatextBook book = exportToParatextBook(bk, bible.getName());
			if (book != null) {
				books.add(book);
			}
		}
		doExportBooks(books, exportArgs);
	}

	protected ParatextBook exportToParatextBook(Book bk, String bibleName) {
		ParatextID pid = ParatextID.fromBookID(bk.getId());
		if (pid == null) {
			System.out.println("WARNING: Skipping unsupported book " + bk.getId());
			return null;
		}
		ParatextBook book = new ParatextBook(pid, bibleName);
		book.getAttributes().put("toc1", bk.getLongName());
		book.getAttributes().put("toc2", bk.getShortName());
		book.getAttributes().put("toc3", bk.getAbbr());
		ParatextExportContext ctx = new ParatextExportContext(book);
		for (int cnum = 1; cnum <= bk.getChapters().size(); cnum++) {
			Chapter ch = bk.getChapters().get(cnum - 1);
			if (cnum > 1)
				ctx.startChapter(cnum);
			if (ch.getProlog() != null) {
				ch.getProlog().accept(new ParatextExportVisitor("in prolog", bk.getId().isNT(), ctx, null, cnum == 1 ? ParagraphKind.INTRO_PARAGRAPH_P : ParagraphKind.CHAPTER_DESCRIPTION));
			}
			if (cnum == 1)
				ctx.startChapter(cnum);
			for (Verse v : ch.getVerses()) {
				String verseNumber = internalVerseNumberToParatextVerseNumber(v.getNumber());
				if (verseNumber == null) {
					throw new IllegalArgumentException("Could not export verse " + ctx.book.getId().getIdentifier() + " " + cnum + ":" + v.getNumber() + " because the verse number could not be transformed to a valid Paratext verse number");
				}
				VerseIdentifier location = VerseIdentifier.fromVerseNumberRangeOrThrow(pid, cnum, verseNumber);
				ctx.startVerse(location);
				v.accept(new ParatextExportVisitor("in verse", bk.getId().isNT(), ctx, null, ParagraphKind.PARAGRAPH_P));
				ctx.endVerse(location);
			}
			ctx.endChapter(cnum);
		}
		return book;
	}

	private String internalVerseNumberToParatextVerseNumber(String internalNumber) {
		if (LocationParser.isValidVerseId(internalNumber, true)) {
			return internalNumber;
		} else {
			// Try to parse the internal verse number
			// A lot of different formats can be expected here:
			// 5
			// 5-6
			// 5G
			// 5/7
			// 5/7/9
			// 5.6G
			Matcher matcher = Pattern.compile("(?:[1-9][0-9a-zG]*,)?([1-9][0-9a-zG]*)(?:-([1-9][0-9a-zG]*))?.*").matcher(internalNumber);
			if (matcher.matches()) {
				if (matcher.group(2) == null) {
					// - Nothing after group 1, assume a single verse number
					// - Something found after group 1 which does not start with a dash.
					//   Since we cannot represent those in Paratext, use the first verse number only
					String paratextVerseNumber = matcher.group(1).replaceAll("G", "");
					warningLogger.logVerseNumberDowngrade(internalNumber, paratextVerseNumber);
					return paratextVerseNumber;
				} else {
					// Something found after group 1 with a dash, assume a verse range
					String paratextVerseNumber = (matcher.group(1) + "-" + matcher.group(2)).replaceAll("G", "");
					warningLogger.logVerseNumberDowngrade(internalNumber, paratextVerseNumber);
					return paratextVerseNumber;
				}
			} else {
				return null;
			}
		}
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
		private Book bk = null;
		private Chapter currentChapter = null;
		private Verse currentVerse = null;
		private List<Headline> headlines = new ArrayList<>();
		private Visitor<RuntimeException> currentVisitor, outsideCellVisitor;
		private String currentVisitorExtraCSS;
		private CurrentParagraph currentParagraph = CurrentParagraph.NONE;
		private String currentParagraphExtraCSS;
		private Map<ParatextID, String> bookAbbrs;
		private ParatextImportContext contextOutsideSidebar = null;

		private static final boolean allowMixedHeadlineDepth = Boolean.getBoolean("paratext.allowmixedheadlinedepth");

		private void flushHeadlines() {
			int lastDepth = 0;
			for (Headline hl : headlines) {
				if (allowMixedHeadlineDepth)
					lastDepth = 0;
				lastDepth = Math.max(lastDepth == 9 ? 9 : lastDepth + 1, hl.getDepth());
				hl.accept(currentVisitor.visitHeadline(lastDepth));
			}
			headlines.clear();
		}

		public void startSidebar(Book sidebarBook) {
			if (contextOutsideSidebar != null)
				throw new IllegalStateException("Nested sidebars are not supported");
			contextOutsideSidebar = new ParatextImportContext();
			swapFields(contextOutsideSidebar);
			this.bk = sidebarBook;
		}

		public void endSidebar() {
			if (contextOutsideSidebar == null)
				throw new IllegalStateException("No sidebar open");
			if (!headlines.isEmpty()) {
				System.out.println("WARNING: Ignoring unreferenced headlines");
			}
			for (Chapter ch : bk.getChapters()) {
				if (ch.getProlog() != null)
					ch.getProlog().finished();
				for (Verse v : ch.getVerses())
					v.finished();
			}
			swapFields(contextOutsideSidebar);
			contextOutsideSidebar = null;
		}

		private void swapFields(ParatextImportContext other) {
			// do not swap `bookAbbrs` and `contextOutsideSidebar` fields!
			int x = this.cnum; this.cnum = other.cnum; other.cnum = x;
			Book b = this.bk; this.bk = other.bk; other.bk = b;
			Chapter c = this.currentChapter; this.currentChapter = other.currentChapter; other.currentChapter = c;
			Verse v= this.currentVerse; this.currentVerse = other.currentVerse; other.currentVerse = v;
			List<Headline> h = this.headlines; this.headlines = other.headlines; other.headlines = h;
			Visitor<RuntimeException> vv = this.currentVisitor; this.currentVisitor = other.currentVisitor; other.currentVisitor = vv;
			vv = this.outsideCellVisitor; this.outsideCellVisitor = other.outsideCellVisitor; other.outsideCellVisitor = vv;
			String s = this.currentVisitorExtraCSS; this.currentVisitorExtraCSS = other.currentVisitorExtraCSS; other.currentVisitorExtraCSS = s;
			CurrentParagraph p = this.currentParagraph; this.currentParagraph = other.currentParagraph; other.currentParagraph =p;
			s = this.currentParagraphExtraCSS; this.currentParagraphExtraCSS = other.currentParagraphExtraCSS; other.currentParagraphExtraCSS = s;
		}

		private void ensureParagraph() {
			if (currentParagraph == CurrentParagraph.NONE) {
				System.out.println("WARNING: No paragraph open; opening \\p");
				currentParagraph = CurrentParagraph.NORMAL;
				currentParagraphExtraCSS = null;
			}
		}

		private static enum CurrentParagraph {
			NONE, PROLOG, HEADLINE, NORMAL
		}
	}

	public static class ParatextImportVisitor implements ParatextCharacterContentVisitor<RuntimeException> {

		private ParatextImportContext ctx;
		private List<Visitor<RuntimeException>> visitorStack = new ArrayList<>();
		int inFootnoteDepth = -1;
		private FootnoteXrefKind justOpenedFootnote = null;

		public Visitor<RuntimeException> getCurrentVisitor() {
			Visitor<RuntimeException> v = visitorStack.get(visitorStack.size() - 1);
			if (v == null & ctx.currentVisitor != null && !Objects.equals(ctx.currentVisitorExtraCSS, ctx.currentParagraphExtraCSS)) {
				ctx.currentVisitor = null;
			}
			if (v == null && ctx.currentVisitor == null && ctx.currentVerse != null) {
				ctx.currentVisitor = ctx.currentVerse.getAppendVisitor();
				ctx.flushHeadlines();
				ctx.currentVisitorExtraCSS = ctx.currentParagraphExtraCSS;
				if (ctx.currentParagraphExtraCSS != null) {
					ctx.currentVisitor = ctx.currentVisitor.visitCSSFormatting(ctx.currentParagraphExtraCSS);
				}
			}
			return v != null ? v : ctx.currentVisitor;
		}

		public ParatextImportVisitor(ParatextImportContext ctx) {
			this.ctx = ctx;
			visitorStackAdd(null);
		}

		private void visitorStackAdd(Visitor<RuntimeException> v) {
			if (inFootnoteDepth >= 0)
				inFootnoteDepth++;
			visitorStack.add(v);
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitFootnoteXref(FootnoteXrefKind kind, String caller, String[] categories) {
			if (ctx.currentParagraph == ParatextImportContext.CurrentParagraph.NORMAL && getCurrentVisitor() == null) {
				System.out.println("WARNING: Skipping footnote/xref outside of verse/headline/prolog");
				return null;
			}
			justOpenedFootnote = kind;
			Visitor<RuntimeException> v = getCurrentVisitor().visitFootnote();
			if (exportAllTags) {
				v = v.visitCSSFormatting("-bmc-usfm-tag: " + kind.getTag());
			}
			if (kind.isXref())
				v.visitText(FormattedText.XREF_MARKER);
			visitorStackAdd(v);
			inFootnoteDepth = 0;
			return this;
		}

		@Override
		public ParatextCharacterContentVisitor<RuntimeException> visitAutoClosingFormatting(AutoClosingFormattingKind kind, Map<String, String> attributes) {
			if (ctx.currentParagraph == ParatextImportContext.CurrentParagraph.NORMAL && getCurrentVisitor() == null) {
				System.out.println("WARNING: Skipping formatting outside of verse/headline/prolog");
				return null;
			}
			if (kind == AutoClosingFormattingKind.LINK && attributes.containsKey("link-id")) {
				return null;
			}
			Visitor<RuntimeException> newVisitor;
			if (kind.getLineBreakKind() != null) {
				getCurrentVisitor().visitLineBreak(kind.getLineBreakKind().toLineBreakKind(kind.getIndent()));
				newVisitor = getCurrentVisitor();
				if (exportAllTags || kind.getEndLineBreakKind() != null) {
					Visitor<RuntimeException> outerNewVisitor = newVisitor;
					newVisitor = newVisitor.visitCSSFormatting("-bmc-usfm-tag: " + kind.getTag());
					if (kind.getEndLineBreakKind() != null) {
						outerNewVisitor.visitLineBreak(kind.getEndLineBreakKind().toLineBreakKind(kind.getIndent()));
					}
				}
			} else if (kind.getFormat() != null) {
				newVisitor = getCurrentVisitor().visitFormattingInstruction(kind.getFormat());
				if (exportAllTags) {
					newVisitor = newVisitor.visitCSSFormatting("-bmc-usfm-tag: " + kind.getTag());
				}
			} else if (kind.getExtraAttribute() != null) {
				newVisitor = getCurrentVisitor().visitExtraAttribute(ExtraAttributePriority.SKIP, "paratext", "meta-character-style", kind.getTag());
			} else if (kind == AutoClosingFormattingKind.WORDLIST && !attributes.isEmpty()) {
				StringBuilder strongsPrefixes = new StringBuilder();
				List<Integer> strongs = new ArrayList<>();
				String strongAttribute = attributes.get("strong");
				if (strongAttribute != null) {
					char[] prefixHolder = new char[1];
					for (String strong : strongAttribute.split("[, ]")) {
						int num = Utils.parseStrongs(strong, '\0', prefixHolder);
						if (num > 0) {
							strongsPrefixes.append(prefixHolder[0]);
							strongs.add(num);
						} else {
							System.out.println("WARNING: Skipping unsupported strong number: " + strong);
						}
					}
				}
				List<String> rmacs = new ArrayList<>();
				String morphAttribute = attributes.get("x-morph");
				if (morphAttribute != null && (morphAttribute.startsWith("robinson:") || morphAttribute.startsWith("wivu:"))) {
					for (String rmac : morphAttribute.split("[, ]")) {
						if (rmac.startsWith("robinson:"))
							rmac = rmac.substring("robinson:".length());
						if (rmac.startsWith("wivu:"))
							rmac = rmac.substring("wivu:".length());
						if (Utils.compilePattern(Utils.MORPH_REGEX).matcher(rmac).matches()) {
							rmacs.add(rmac);
						} else {
							System.out.println("Skipping unsupported RMAC/WIVU: " + rmac);
						}
					}
				}
				int[] strongsArray = strongs.isEmpty() ? null : strongs.stream().mapToInt(s -> s).toArray();
				if (rmacs.isEmpty() && strongsArray == null) {
					newVisitor = getCurrentVisitor().visitCSSFormatting(kind.getCss());
				} else {
					newVisitor = getCurrentVisitor().visitGrammarInformation(strongsPrefixes.toString().toCharArray(), strongsArray, rmacs.isEmpty() ? null : rmacs.toArray(new String[rmacs.size()]), null);
					if (exportAllTags) {
						newVisitor = newVisitor.visitCSSFormatting("-bmc-usfm-tag: " + kind.getTag());
					}
				}
			} else if (justOpenedFootnote != null && kind.equals(justOpenedFootnote.isXref() ? AutoClosingFormattingKind.XREF_TARGET_REFERENCES : AutoClosingFormattingKind.FOOTNOTE_TEXT)) {
				// footnote consists of just text; skip the start tag
				newVisitor = getCurrentVisitor();
				if (exportAllTags) {
					newVisitor = newVisitor.visitCSSFormatting(kind.getCss());
				}
			} else {
				newVisitor = getCurrentVisitor().visitCSSFormatting(kind.getCss());
			}
			justOpenedFootnote = null;
			visitorStackAdd(newVisitor);
			return this;
		}

		@Override
		public void visitMilestone(String tag, Map<String, String> attributes) throws RuntimeException {
			// ignore
		}

		@Override
		public void visitReference(Reference reference) throws RuntimeException {
			if (ctx.currentParagraph == ParatextImportContext.CurrentParagraph.NORMAL && getCurrentVisitor() == null) {
				System.out.println("WARNING: Skipping text outside of verse/headline/prolog");
				return;
			}
			justOpenedFootnote = null;

			int firstChapter = reference.getFirstChapter(), lastChapter = reference.getLastChapter();
			String firstVerse = reference.getFirstVerse(), lastVerse = reference.getLastVerse();

			// "denormalize" ranges (see ParatextExportVisitor.internalCrossReferenceToParatextCrossReference)
			if (firstChapter == -1) {
				firstChapter = 1; lastChapter = 999;
			} else if (lastChapter == -1) {
				lastChapter = firstChapter;
			}
			if (firstVerse == null) {
				firstVerse = "1"; lastVerse = "999";
			} else if (lastVerse == null) {
				lastVerse = firstVerse;
			}

			Visitor<RuntimeException> v = getCurrentVisitor();

			if (inFootnoteDepth == -1 && !Boolean.getBoolean("paratext.allowrefsoutsidefootnotes")) {
				v.visitText(reference.getContent());
				v = v.visitFootnote();
			}

			v.visitCrossReference(ctx.bookAbbrs.getOrDefault(reference.getBook(), reference.getBook().getId().getOsisID()), reference.getBook().getId(), firstChapter, firstVerse, lastChapter, lastVerse).visitText(reference.getContent());
		}

		@Override
		public void visitCustomMarkup(String tag, boolean ending) throws RuntimeException {
			// ignore
		}

		@Override
		public void visitSpecialSpace(boolean nonBreakSpace, boolean optionalLineBreak) throws RuntimeException {
			if (nonBreakSpace) visitText("\u00A0");
		}

		@Override
		public void visitText(String text) {
			if (ctx.currentParagraph == ParatextImportContext.CurrentParagraph.NORMAL && getCurrentVisitor() == null) {
				System.out.println("WARNING: Skipping text outside of verse/headline/prolog");
				return;
			}
			justOpenedFootnote = null;
			getCurrentVisitor().visitText(text);
		}

		@Override
		public void visitEnd() {
			if (inFootnoteDepth >= 0)
				inFootnoteDepth--;
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
			book.getContent().add(new ChapterStart(new ChapterIdentifier(book.getId(), cnum)));
			currentParagraph = null;
			charContent = null;
		}

		public void endChapter(int cnum) {
			book.getContent().add(new ParatextBook.ChapterEnd(new ChapterIdentifier(book.getId(), cnum)));
		}

		public void startVerse(VerseIdentifier verse) {
			book.getContent().add(new ParatextBook.VerseStart(verse, verse.verse()));
			charContent = null;
		}

		public void endVerse(VerseIdentifier verse) {
			book.getContent().add(new ParatextBook.VerseEnd(verse));
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

		public ParatextExportVisitor(String location, boolean nt, ParatextExportContext ctx, ParatextCharacterContentContainer ccnt, ParagraphKind paragraphKind) {
			this.location = location;
			this.nt = nt;
			this.ctx = ctx;
			this.ccnt = ccnt;
			this.paragraphKind = paragraphKind;
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
				headlineKinds = new ParagraphKind[]{
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
				headlineKinds = new ParagraphKind[]{
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
			return new ParatextExportVisitor("in headline", nt, null, ctx.getCharContent(headlineKinds[depth - 1]), null);
		}

		@Override
		public void visitStart() {
		}

		@Override
		public void visitText(String text) {
			final Text textContent = Text.from(text);
			if(textContent != null) {
				getCharContent().getContent().add(textContent);
			}
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() {
			final FootnoteXref footnote = new FootnoteXref(FootnoteXrefKind.FOOTNOTE, "+", new String[0]);
			getCharContent().getContent().add(footnote);

			return new VisitorAdapter<RuntimeException>(new ParatextExportVisitor("in footnote", nt, null, footnote, null)) {
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

				@Override
				public boolean visitEnd() throws RuntimeException {
					ParatextExportVisitor next = (ParatextExportVisitor) getVisitor();
					if (!next.getCharContent().getContent().isEmpty()) {
						boolean isXref = footnote.getKind().isXref();
						ParatextCharacterContentPart firstPart = next.getCharContent().getContent().get(0);
						if (firstPart instanceof AutoClosingFormatting) {
							if (((AutoClosingFormatting) firstPart).getKind().getTag().startsWith(isXref ? "x" : "f")) {
								// we already have \f? or \x? tags, do not synthesize!
								return super.visitEnd();
							}
						}
						// wrap with \ft / \xt tag
						AutoClosingFormattingKind kind = isXref ? AutoClosingFormattingKind.XREF_TARGET_REFERENCES : AutoClosingFormattingKind.FOOTNOTE_TEXT;
						final AutoClosingFormatting formatting = new AutoClosingFormatting(kind);
						formatting.getContent().addAll(next.getCharContent().getContent());
						next.getCharContent().getContent().clear();
						next.getCharContent().getContent().add(formatting);
					}
					return super.visitEnd();
				}
			};
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) {
			Reference reference = internalCrossReferenceToParatextCrossReference(book, firstChapter, lastChapter, firstVerse, lastVerse);
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

		/**
		 * Transform a cross reference from the internal format to the Paratext format. This process involves
		 * normalizing certain chapter and verse ranges to book and chapter references. For example MAT 1:1-999:999
		 * is turned into a single book cross reference to MAT.
		 */
		private Reference internalCrossReferenceToParatextCrossReference(BookID book, int firstChapter, int lastChapter, String firstVerse, String lastVerse) {
			final ParatextID paratextID = ParatextID.fromBookID(book);

			// Attempt to "normalize" ranges a bit
			if (firstChapter == 1 && lastChapter == 999 && firstVerse.equals("1") && lastVerse.equals("999")) {
				// MAT 1:1-999:999 > MAT
				return Reference.book(paratextID, "");
			} else if (firstChapter == lastChapter && firstVerse.equals("1") && lastVerse.equals("999")) {
				// MAT 5:1-5:999 > MAT 5
				return Reference.chapter(paratextID, firstChapter, "");
			} else if(firstChapter != lastChapter && firstVerse.equals("1") && lastVerse.equals("999")) {
				// MAT 5:1-7:999 > MAT 5-7
				return Reference.chapterRange(paratextID, firstChapter, lastChapter, "");
			} else if (firstChapter == lastChapter && firstVerse.equals(lastVerse)) {
				// MAT 5:5-5:5 > MAT 5:5
				return Reference.verse(paratextID, firstChapter, firstVerse, "");
			} else {
				// Anything else
				return Reference.verseRange(paratextID, firstChapter, firstVerse, lastChapter, lastVerse, "");
			}
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
				return new ParatextExportVisitor(location, nt, ctx, ccnt, paragraphKind);
			}
			final AutoClosingFormatting formatting = new AutoClosingFormatting(kind);
			getCharContent().getContent().add(formatting);
			return new ParatextExportVisitor("in formatting", nt, null, formatting, null);
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
				getCharContent().getContent().add(new AutoClosingFormatting(AutoClosingFormattingKind.FOOTNOTE_PARAGRAPH));
			}
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
			final AutoClosingFormatting formatting = new AutoClosingFormatting(AutoClosingFormattingKind.WORDLIST);
			if (strongs != null) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < strongs.length; i++) {
					if (sb.length() != 0)
						sb.append(",");
					sb.append(Utils.formatStrongs(nt, i, strongsPrefixes, strongs));
				}
				formatting.getAttributes().put("strong", sb.toString());
			}
			if (rmac != null) {
				String prefix;
				if (rmac[0].matches(Utils.RMAC_REGEX))
					prefix = "robinson:";
				else if (rmac[0].matches(Utils.WIVU_REGEX))
					prefix = "wivu:";
				else
					throw new IllegalStateException("Invalid morph format: "+rmac[0]);
				formatting.getAttributes().put("x-morph", prefix + String.join(",", Arrays.asList(rmac)));
			}
			getCharContent().getContent().add(formatting);
			return new ParatextExportVisitor("in formatting", nt, null, formatting, null);
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) {
			return new ParatextExportVisitor(location, nt, ctx, ccnt, paragraphKind);
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
