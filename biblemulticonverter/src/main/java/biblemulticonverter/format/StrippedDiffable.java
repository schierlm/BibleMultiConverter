package biblemulticonverter.format;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VerseRange;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.tools.AbstractVersificationDetector.VersificationScheme;

public class StrippedDiffable implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Usage: StrippedDiffable <OutputFile> [<Feature>...]",
			"Like Diffable, but with features stripped.",
			"",
			"Supported features: " + Arrays.toString(Feature.values()),
			"",
			"It can also perform some other transformations:",
			"- ExtractPrologs",
			"- MergeIntroductionPrologs",
			"- ExtractFootnotes",
			"- RearrangeChapterBoundaries",
			"- AddStrongsPrefixes",
			"- RemoveStrongsPrefixes",
			"- SelectVariation <Name>",
			"- ChangeVerseStructure {Raw|Virtual|Virtual_ignoring_Headlines|Range|Range_Ascending} [<VersificationFile> <VersificationName>]",
			"- RenameBook <OldAbbr> <NewAbbr>",
			"- RenameAllBooks [OSIS|Zefania|3LC|Paratext]",
			"- SortBooks",
			"- OptimizeFormatting [SplitCSS|FromCSS|ToCSS|RemoveCSS|JoinCSS|<OldFormatting>-><NewFormatting>[&<NewFormatting>[...]] [...]"
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String outputFile = exportArgs[0];
		doConversion(bible, System.out, exportArgs);
		new Diffable().doExport(bible, new String[] {outputFile});
	}

	protected void doConversion(final Bible bible, PrintStream out, String... exportArgs) throws IOException {
		if (exportArgs.length == 2 && exportArgs[1].equals("ExtractPrologs")) {
			extractPrologs(bible);
			out.println("Prologs extracted.");
		} else if (exportArgs.length == 2 && exportArgs[1].equals("MergeIntroductionPrologs")) {
			mergeIntroductionPrologs(bible, out);
			out.println("Introduction prologs merged.");
		} else if (exportArgs.length == 2 && exportArgs[1].equals("ExtractFootnotes")) {
			extractFootnotes(bible);
			out.println("Footnotes extracted.");
		} else if (exportArgs.length == 2 && exportArgs[1].equals("RearrangeChapterBoundaries")) {
			rearrangeChapterBoundaries(bible);
			out.println("Chapter boundaries rearranged.");
		} else if (exportArgs.length == 2 && exportArgs[1].equals("AddStrongsPrefixes")) {
			changeStrongsPrefixes(bible, true);
			out.println("Strongs prefixes added.");
		} else if (exportArgs.length == 2&&exportArgs[1].equals("RemoveStrongsPrefixes")) {
			changeStrongsPrefixes(bible, false);
			out.println("Strongs prefixes removed.");
		} else if (exportArgs.length == 3 && exportArgs[1].equals("SelectVariation")) {
			selectVariation(bible, exportArgs[2]);
			out.println("Variation " + exportArgs[2] + " kept.");
		} else if (exportArgs.length == 3 && exportArgs[1].equals("ChangeVerseStructure")) {
			changeVerseStructure(bible, VerseStructureFormat.valueOf(exportArgs[2].toUpperCase()), null);
		} else if (exportArgs.length == 5 && exportArgs[1].equals("ChangeVerseStructure") && (exportArgs[2].equalsIgnoreCase(VerseStructureFormat.VIRTUAL.name()) || exportArgs[2].equalsIgnoreCase(VerseStructureFormat.VIRTUAL_IGNORING_HEADLINES.name()))) {
			changeVerseStructure(bible, VerseStructureFormat.VIRTUAL, new VersificationSet(new File(exportArgs[3])).findVersification(exportArgs[4]).toNewVersificationScheme(false));
		} else if (exportArgs.length == 4 && exportArgs[1].equals("RenameBook")) {
			renameBookInXref(bible, exportArgs[2], exportArgs[3], true);
			for (int i = 0; i < bible.getBooks().size(); i++) {
				Book oldBook = bible.getBooks().get(i);
				if (oldBook.getAbbr().equals(exportArgs[2])) {
					Book newBook = new Book(exportArgs[3], oldBook.getId(), oldBook.getShortName(), oldBook.getLongName());
					newBook.getChapters().addAll(oldBook.getChapters());
					bible.getBooks().set(i, newBook);
				}
			}
			out.println("Book " + exportArgs[2] + " renamed to " + exportArgs[3]);
		} else if (exportArgs.length == 3 && exportArgs[1].equals("RenameAllBooks")) {
			Function<BookID, String> abbrMapper;
			if (exportArgs[2].equals("OSIS")) {
				abbrMapper = BookID::getOsisID;
			} else if (exportArgs[2].equals("Zefania")) {
				abbrMapper = bi -> bi.getZefID() <0 ? null : "Z" + bi.getZefID();
			} else if (exportArgs[2].equals("3LC")) {
				abbrMapper = BookID::getThreeLetterCode;
			} else if (exportArgs[2].equals("Paratext")) {
				Function<BookID, ParatextID> find = (bi -> ParatextID.fromBookID(bi));
				abbrMapper = find.andThen(pi -> pi == null ? null : pi.getIdentifier());
			} else {
				throw new IllegalArgumentException("Unsupported book name scheme: " + exportArgs[2]);
			}
			for (int i = 0; i < bible.getBooks().size(); i++) {
				Book oldBook = bible.getBooks().get(i);
				String newAbbr = abbrMapper.apply(oldBook.getId());
				if (newAbbr != null && !newAbbr.startsWith("x-")) {
					renameBookInXref(bible, oldBook.getAbbr(), newAbbr, true);
					Book newBook = new Book(newAbbr, oldBook.getId(), oldBook.getShortName(), oldBook.getLongName());
					newBook.getChapters().addAll(oldBook.getChapters());
					bible.getBooks().set(i, newBook);
					out.println("Book " + oldBook.getAbbr() + " renamed to " + newAbbr);
				}
			}
		} else if (exportArgs.length == 2 && exportArgs[1].equals("SortBooks")) {
			bible.getBooks().sort(Comparator.comparingInt((Book b) -> b.getId().ordinal()).thenComparing(Book::getAbbr));
			out.println("Books sorted.");
		} else if (exportArgs.length >= 2 && exportArgs[1].equals("OptimizeFormatting")) {
			EnumSet<OptimizeFormattingOption> options = EnumSet.noneOf(OptimizeFormattingOption.class);
			Map<String, String[]> mappings = new HashMap<>();
			for (int i = 2; i < exportArgs.length; i++) {
				int pos = exportArgs[i].indexOf("->");
				if (pos == -1) {
					try {
						options.add(OptimizeFormattingOption.valueOf(exportArgs[i]));
						continue;
					} catch (IllegalArgumentException ex) {
						out.println("WARNING: Skipped malformed formatting mapping " + exportArgs[i]);
						continue;
					}
				}
				String key = exportArgs[i].substring(0, pos).trim();
				String[] values = exportArgs[i].substring(pos + 2).split("&");
				mappings.put(key, values);
			}
			optimizeFormatting(bible, out, options, mappings);
		} else {
			EnumSet<Feature> chosenFeeatures = EnumSet.noneOf(Feature.class);
			for (int i = 1; i < exportArgs.length; i++) {
				chosenFeeatures.add(Feature.valueOf(exportArgs[i]));
			}
			strip(bible, out, chosenFeeatures);
		}
		List<Book> booksToRemove = new ArrayList<Book>();
		for (Book book : bible.getBooks()) {
			while (book.getChapters().size() > 0) {
				Chapter lastChapter = book.getChapters().get(book.getChapters().size() - 1);
				if (lastChapter.getProlog() != null || lastChapter.getVerses().size() > 0)
					break;
				book.getChapters().remove(lastChapter);
			}
			if (book.getChapters().size() == 0)
				booksToRemove.add(book);
		}
		bible.getBooks().removeAll(booksToRemove);
	}

	private void optimizeFormatting(Bible bible, PrintStream out, EnumSet<OptimizeFormattingOption> options, Map<String, String[]> mappings) {
		CountFormattingVisitor oldCount = new CountFormattingVisitor();
		CountFormattingVisitor newCount = new CountFormattingVisitor();
		for (Book book : bible.getBooks()) {
			for (Chapter chap : book.getChapters()) {
				if (chap.getProlog() != null) {
					chap.getProlog().accept(oldCount);
					FormattedText newProlog = new FormattedText();
					chap.getProlog().accept(new OptimizeFormattingVisitor(newProlog.getAppendVisitor(), options, mappings));
					newProlog.finished();
					newProlog.accept(newCount);
					chap.setProlog(newProlog);
				}
				for (int j = 0; j < chap.getVerses().size(); j++) {
					Verse v = chap.getVerses().get(j);
					v.accept(oldCount);
					Verse nv = new Verse(v.getNumber());
					v.accept(new OptimizeFormattingVisitor(nv.getAppendVisitor(), options, mappings));
					nv.finished();
					nv.accept(newCount);
					chap.getVerses().set(j, nv);
				}
			}
		}
		out.println("Formatting instructions before optimizing:");
		oldCount.printSummary(out);
		out.println();
		out.println("Formatting instructions after optimizing:");
		newCount.printSummary(out);
	}

	private void extractPrologs(Bible bible) {
		for (Book book : bible.getBooks()) {
			for (Chapter chapter : book.getChapters()) {
				chapter.getVerses().clear();
				if (chapter.getProlog() != null) {
					Verse prologVerse = new Verse("1");
					chapter.getProlog().accept(prologVerse.getAppendVisitor());
					chapter.getVerses().add(prologVerse);
					chapter.setProlog(null);
				}
			}
		}
	}

	protected void mergeIntroductionPrologs(Bible bible) {
		mergeIntroductionPrologs(bible, System.out);
	}

	private void mergeIntroductionPrologs(Bible bible, PrintStream out) {
		List<FormattedText> prologBuffer = new ArrayList<FormattedText>();
		for (int i = 0; i < bible.getBooks().size(); i++) {
			Book book = bible.getBooks().get(i);
			if (book.getId().getZefID() < 0) {
				if (book.getChapters().size() == 1) {
					Chapter ch = book.getChapters().get(0);
					if (ch.getVerses().size() > 0)
						out.println("WARNING: Book " + book.getAbbr() + " has verses; not merged.");
					if (ch.getProlog() != null)
						prologBuffer.add(ch.getProlog());
					else
						out.println("WARNING: Book " + book.getAbbr() + " does not have a prolog; not merged.");
				} else {
					out.println("WARNING: Book " + book.getAbbr() + " has " + book.getChapters().size() + " chapters; not merged.");
				}
				bible.getBooks().remove(i);
				i--;
			} else if (prologBuffer.size() > 0 && book.getChapters().size() > 0) {
				Chapter ch = book.getChapters().get(0);
				if (ch.getProlog() != null)
					prologBuffer.add(ch.getProlog());
				FormattedText newProlog = new FormattedText();
				Visitor<RuntimeException> v = newProlog.getAppendVisitor();
				ch.setProlog(newProlog);
				boolean first = true;
				for (FormattedText oldProlog : prologBuffer) {
					if (!first)
						v.visitLineBreak(LineBreakKind.PARAGRAPH);
					first = false;
					oldProlog.accept(v);
				}
				prologBuffer.clear();
			}
		}
		if (prologBuffer.size() > 0) {
			out.println("WARNING: " + prologBuffer.size() + " introduction prologs after last bible book were merged to first bible book!");
			for (int i = 0; i < bible.getBooks().size(); i++) {
				Book book = bible.getBooks().get(i);
				if (book.getId().getZefID() > 0 && prologBuffer.size() > 0 && book.getChapters().size() > 0) {
					Chapter ch = book.getChapters().get(0);
					Visitor<RuntimeException> v = ch.getProlog().getAppendVisitor();
					for (FormattedText oldProlog : prologBuffer) {
						v.visitLineBreak(LineBreakKind.PARAGRAPH);
						oldProlog.accept(v);
					}
					break;
				}
			}
		}
	}

	protected void renameBookInXref(Bible bible, String oldName, String newName, boolean setFinished) {
		for (Book book : bible.getBooks()) {
			for (Chapter chap : book.getChapters()) {
				if (chap.getProlog() != null) {
					FormattedText newProlog = new FormattedText();
					chap.getProlog().accept(new RenameXrefVisitor(newProlog.getAppendVisitor(), oldName, newName));
					if (setFinished)
						newProlog.finished();
					chap.setProlog(newProlog);
				}
				for (int j = 0; j < chap.getVerses().size(); j++) {
					Verse v = chap.getVerses().get(j);
					Verse nv = new Verse(v.getNumber());
					v.accept(new RenameXrefVisitor(nv.getAppendVisitor(), oldName, newName));
					if (setFinished)
						nv.finished();
					chap.getVerses().set(j, nv);
				}
			}
		}
	}

	private void extractFootnotes(Bible bible) {
		for (Book book : bible.getBooks()) {
			for (Chapter chapter : book.getChapters()) {
				chapter.getVerses().clear();
				if (chapter.getProlog() != null) {
					chapter.setProlog(extractFootnotes(chapter.getProlog()));
				}
				List<Verse> verses = chapter.getVerses();
				for (int i = 0; i < verses.size(); i++) {
					Verse v = verses.get(i);
					FormattedText extracted = extractFootnotes(v);
					if (extracted == null) {
						verses.remove(i);
						i--;
					} else {
						Verse vv = new Verse(v.getNumber());
						extracted.accept(vv.getAppendVisitor());
						verses.set(i, vv);
					}
				}
			}
		}
	}

	private FormattedText extractFootnotes(FormattedText text) {
		String types = text.getElementTypes(1);
		if (!types.contains("f"))
			return null;
		final FormattedText result = new FormattedText();
		if (types.matches("[^f]+f")) {
			List<FormattedText> parts = text.splitContent(false, true);
			parts.get(parts.size() - 1).accept(result.getAppendVisitor());
		} else {
			text.accept(new FormattedText.VisitorAdapter<RuntimeException>(null) {
				Visitor<RuntimeException> outerVisitor = result.getAppendVisitor();
				Visitor<RuntimeException> innerVisitor = null;

				@Override
				protected void beforeVisit() throws RuntimeException {
					if (innerVisitor == null) {
						innerVisitor = outerVisitor.visitFormattingInstruction(FormattingInstructionKind.ITALIC);
						innerVisitor.visitText("[");
					}
				}

				@Override
				protected Visitor<RuntimeException> getVisitor() throws RuntimeException {
					return innerVisitor;
				}

				@Override
				public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
					return null;
				}

				@Override
				public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
					if (innerVisitor != null) {
						innerVisitor.visitText("]");
						innerVisitor = null;
					}
					return outerVisitor;
				}

				@Override
				public boolean visitEnd() throws RuntimeException {
					if (innerVisitor != null) {
						innerVisitor.visitText("]");
						innerVisitor = null;
					}
					return false;
				}
			});
		}
		return result;
	}

	protected void rearrangeChapterBoundaries(Bible bible) {
		for (Book book : bible.getBooks()) {
			for (int i = 0; i < book.getChapters().size(); i++) {
				Chapter chap = book.getChapters().get(i);
				while (chap.getVerses().size() >= 2) {
					if (i >= 1 && chap.getVerses().get(0).getNumber().startsWith(i + ",")) {
						Verse v = chap.getVerses().remove(0);
						Verse nv = new Verse(v.getNumber().substring((i + ",").length()));
						v.accept(nv.getAppendVisitor());
						nv.finished();
						book.getChapters().get(i - 1).getVerses().add(nv);
					} else if (i < book.getChapters().size() - 1 && chap.getVerses().get(chap.getVerses().size() - 1).getNumber().startsWith((i + 2) + ",")) {
						Verse v = chap.getVerses().remove(chap.getVerses().size() - 1);
						Verse nv = new Verse(v.getNumber().substring(((i + 2) + ",").length()));
						v.accept(nv.getAppendVisitor());
						nv.finished();
						book.getChapters().get(i + 1).getVerses().add(0, nv);
					} else {
						break;
					}
				}
			}
		}
	}

	private void changeStrongsPrefixes(Bible bible, boolean add) {
		for (Book book : bible.getBooks()) {
			for (Chapter chapter : book.getChapters()) {
				List<Verse> verses = chapter.getVerses();
				for (int i = 0; i < verses.size(); i++) {
					Verse v1 = verses.get(i);
					Verse v2 = new Verse(v1.getNumber());
					v1.accept(new ChangeStrongsPrefixesVisitor(v2.getAppendVisitor(), book.getId().isNT() ? 'G' : 'H', add));
					v2.finished();
					verses.set(i, v2);
				}
			}
		}
	}

	protected void selectVariation(Bible bible, String variation) {
		for (Book book : bible.getBooks()) {
			for (Chapter chapter : book.getChapters()) {
				if (chapter.getProlog() != null) {
					FormattedText newProlog = new FormattedText();
					chapter.getProlog().accept(new SelectVariationVisitor(newProlog.getAppendVisitor(), variation));
					newProlog.finished();
					chapter.setProlog(newProlog);
				}
				List<Verse> verses = chapter.getVerses();
				for (int i = 0; i < verses.size(); i++) {
					Verse v1 = verses.get(i);
					Verse v2 = new Verse(v1.getNumber());
					v1.accept(new SelectVariationVisitor(v2.getAppendVisitor(), variation));
					v2.finished();
					verses.set(i, v2);
				}
			}
		}
	}

	protected void strip(Bible bible, PrintStream out, EnumSet<Feature> chosenFeatures) {
		EnumSet<Feature> foundFeatures = EnumSet.noneOf(Feature.class);
		for (int i = 0; i < bible.getBooks().size(); i++) {
			Book book = bible.getBooks().get(i);
			BookID bid = book.getId();
			boolean stripBook;
			switch (bid) {
			case METADATA:
				stripBook = isEnabled(Feature.StripMetadataBook, chosenFeatures, foundFeatures);
				break;
			case INTRODUCTION:
				stripBook = isEnabled(Feature.StripIntroductionBooks, chosenFeatures, foundFeatures);
				break;
			case INTRODUCTION_OT:
				// no "shortcut" or here due to side effects of isEnabled!
				stripBook = isEnabled(Feature.StripIntroductionBooks, chosenFeatures, foundFeatures) |
						isEnabled(Feature.StripOldTestament, chosenFeatures, foundFeatures);
				break;
			case INTRODUCTION_NT:
				// no "shortcut" or here due to side effects of isEnabled!
				stripBook = isEnabled(Feature.StripIntroductionBooks, chosenFeatures, foundFeatures) |
						isEnabled(Feature.StripNewTestament, chosenFeatures, foundFeatures);
				break;
			case APPENDIX:
				stripBook = isEnabled(Feature.StripAppendixBook, chosenFeatures, foundFeatures);
				break;
			case DICTIONARY_ENTRY:
				stripBook = isEnabled(Feature.StripDictionaryEntries, chosenFeatures, foundFeatures);
				break;
			default:
				stripBook = isEnabled(bid.isNT() ? Feature.StripNewTestament : Feature.StripOldTestament, chosenFeatures, foundFeatures);
				if (bid.isDeuterocanonical())
					stripBook = stripBook | isEnabled(Feature.StripDeuterocanonicalBooks, chosenFeatures, foundFeatures);
				break;
			}
			if (stripBook) {
				bible.getBooks().remove(i);
				i--;
				continue;
			}
			for (Chapter chap : book.getChapters()) {
				if (chap.getProlog() != null) {
					if (isEnabled(Feature.StripPrologs, chosenFeatures, foundFeatures)) {
						chap.setProlog(null);
					} else {
						FormattedText newProlog = new FormattedText();
						chap.getProlog().accept(new StripVisitor(newProlog.getAppendVisitor(), chosenFeatures, foundFeatures));
						newProlog.finished();
						chap.setProlog(newProlog);
					}
				}
				for (int j = 0; j < chap.getVerses().size(); j++) {
					Verse v = chap.getVerses().get(j);
					Verse nv = new Verse(v.getNumber());
					v.accept(new StripVisitor(nv.getAppendVisitor(), chosenFeatures, foundFeatures));
					nv.finished();
					chap.getVerses().set(j, nv);
				}
			}
		}

		EnumSet<Feature> stripped = EnumSet.copyOf(chosenFeatures);
		stripped.retainAll(foundFeatures);
		chosenFeatures.removeAll(stripped);
		foundFeatures.removeAll(stripped);
		printFeatureSet("Features stripped:", out, stripped);
		printFeatureSet("Features to strip not found:", out, chosenFeatures);
		printFeatureSet("Features still present after stripping:", out, foundFeatures);
	}

	private void printFeatureSet(String headline, PrintStream out, EnumSet<Feature> set) {
		out.println(headline);
		for (Feature f : set) {
			out.println("\t" + f.toString());
		}
		out.println();
	}

	private static boolean isEnabled(Feature feature, EnumSet<Feature> chosenFeatures, EnumSet<Feature> foundFeatures) {
		foundFeatures.add(feature);
		return chosenFeatures.contains(feature);
	}

	private void changeVerseStructure(Bible bible, VerseStructureFormat format, VersificationScheme vs) {
		for (Book book : bible.getBooks()) {
			for (int i = 0; i < book.getChapters().size(); i++) {
				Chapter chap = book.getChapters().get(i);
				List<Verse> newVerses = new ArrayList<>();
				List<GroupedVerse> groupedVerses = new ArrayList<>();
				switch (format) {
				case RAW:
					for (Verse v : chap.getVerses()) {
						v.accept(new SplitVerseVisitor(v.getNumber(), newVerses));
					}
					break;
				case RANGE:
				case RANGE_ASCENDING:
					for (VerseRange range : chap.createVerseRanges(format.equals(VerseStructureFormat.RANGE_ASCENDING))) {
						String verseNumber = (range.getMinVerse() == range.getMaxVerse() ? "" : range.getMinVerse() + "-") + range.getMaxVerse();
						verseNumber = (range.getChapter() == 0 ? "" : range.getChapter() + ",") + verseNumber;
						groupedVerses.add(new GroupedVerse(verseNumber, Collections.emptyList(), range.getVerses()));
					}
					break;
				case VIRTUAL:
				case VIRTUAL_IGNORING_HEADLINES:
					BitSet allowedNumbers = null;
					if (vs != null) {
						BitSet[] bookInfo = vs.getCoveredBooks().get(book.getId());
						if (bookInfo != null && i < bookInfo.length) {
							allowedNumbers = bookInfo[i];
						}
					}
					for (VirtualVerse vv : chap.createVirtualVerses(false, allowedNumbers, format.equals(VerseStructureFormat.VIRTUAL))) {
						groupedVerses.add(new GroupedVerse("" + vv.getNumber(), vv.getHeadlines(), vv.getVerses()));
					}
					break;
				}
				chap.getVerses().clear();
				chap.getVerses().addAll(newVerses);
				for (GroupedVerse groupedVerse : groupedVerses) {
					Verse v = new Verse(groupedVerse.number);
					for (Headline hl : groupedVerse.headlines) {
						hl.accept(v.getAppendVisitor().visitHeadline(hl.getDepth()));
					}
					boolean firstVerse = true;
					for (Verse vv : groupedVerse.verses) {
						if (!firstVerse || !vv.getNumber().equals(groupedVerse.number)) {
							String rawNumber = vv.getNumber().replace('.', 'D').replace(',', 'C').replace('/', 'S');
							Visitor<RuntimeException> vvv = v.getAppendVisitor();
							vvv = vvv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "verse", "raw-number", rawNumber);
							if (!firstVerse)
								vvv.visitText(" ");
							vvv.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText("(" + vv.getNumber() + ")");
							vvv.visitText(" ");
						}
						firstVerse = false;
						vv.accept(v.getAppendVisitor());
					}
					v.finished();
					chap.getVerses().add(v);
				}
			}
		}
	}

	public static enum Feature {
		StripPrologs, StripFootnotes, StripHeadlines, StripVerseSeparators, InlineVerseSeparators,
		StripCrossReferences, StripFormatting, StripCSSFormatting, StripGrammar, StripDictionaryReferences,
		StripRawHTML, StripExtraAttributes, StripOrKeepExtraAttributes, StripLineBreaks, StripVariations,
		StripStrongs, StripStrongsWithPrefixes, StripRMAC, StripMorph, StripWIVU, StripSourceIndexes,
		StripOldTestament, StripNewTestament, StripDeuterocanonicalBooks,
		StripMetadataBook, StripIntroductionBooks, StripDictionaryEntries, StripAppendixBook
	}

	public static enum OptimizeFormattingOption {
		SplitCSS, FromCSS, ToCSS, RemoveCSS, JoinCSS
	}

	private static class SelectVariationVisitor extends FormattedText.VisitorAdapter<RuntimeException> {
		private final String variation;

		private SelectVariationVisitor(Visitor<RuntimeException> next, String variation) throws RuntimeException {
			super(next);
			this.variation = variation;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return new SelectVariationVisitor(childVisitor, variation);
		}

		public FormattedText.Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			if (Arrays.asList(variations).contains(variation)) {
				return new SelectVariationVisitor(getVisitor(), variation);
			}
			return null;
		}
	}

	private static class RenameXrefVisitor extends FormattedText.VisitorAdapter<RuntimeException> {

		private final String oldName;
		private final String newName;

		private RenameXrefVisitor(Visitor<RuntimeException> next, String oldName, String newName) throws RuntimeException {
			super(next);
			this.oldName = oldName;
			this.newName = newName;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return new RenameXrefVisitor(childVisitor, oldName, newName);
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			if (bookAbbr.equals(oldName))
				bookAbbr = newName;
			return super.visitCrossReference(bookAbbr, book, firstChapter, firstVerse, lastChapter, lastVerse);
		}
	}

	private static class StripVisitor implements Visitor<RuntimeException> {

		private Visitor<RuntimeException> next;
		private EnumSet<Feature> chosenFeatures;
		private EnumSet<Feature> foundFeatures;

		private StripVisitor(Visitor<RuntimeException> next, EnumSet<Feature> chosenFeatures, EnumSet<Feature> foundFeatures) {
			this.next = next;
			this.chosenFeatures = chosenFeatures;
			this.foundFeatures = foundFeatures;
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			if (isEnabled(Feature.StripHeadlines, chosenFeatures, foundFeatures)) {
				return null;
			} else {
				return new StripVisitor(next.visitHeadline(depth), chosenFeatures, foundFeatures);
			}

		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			next.visitText(text);
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
			if (isEnabled(Feature.StripFootnotes, chosenFeatures, foundFeatures)) {
				return null;
			} else {
				return new StripVisitor(next.visitFootnote(), chosenFeatures, foundFeatures);
			}
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			if (isEnabled(Feature.StripCrossReferences, chosenFeatures, foundFeatures)) {
				return null;
			} else {
				return new StripVisitor(next.visitCrossReference(bookAbbr, book, firstChapter, firstVerse, lastChapter, lastVerse), chosenFeatures, foundFeatures);
			}
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			if (isEnabled(Feature.StripFormatting, chosenFeatures, foundFeatures)) {
				return this;
			} else {
				return new StripVisitor(next.visitFormattingInstruction(kind), chosenFeatures, foundFeatures);
			}
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			if (isEnabled(Feature.StripFormatting, chosenFeatures, foundFeatures) | isEnabled(Feature.StripCSSFormatting, chosenFeatures, foundFeatures)) {
				return this;
			} else {
				return new StripVisitor(next.visitCSSFormatting(css), chosenFeatures, foundFeatures);
			}
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			// do not inline next two lines because of side effect
			boolean strip = isEnabled(Feature.StripVerseSeparators, chosenFeatures, foundFeatures);
			boolean inline = isEnabled(Feature.InlineVerseSeparators, chosenFeatures, foundFeatures);
			if (inline) {
				boolean formatted = !isEnabled(Feature.StripFormatting, chosenFeatures, foundFeatures)
						& !isEnabled(Feature.StripCSSFormatting, chosenFeatures, foundFeatures);
				Visitor<RuntimeException> v = formatted ? next.visitCSSFormatting("color:gray;") : next;
				v.visitText("/");
			} else if (!strip) {
				next.visitVerseSeparator();
			}
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			if (!isEnabled(Feature.StripLineBreaks, chosenFeatures, foundFeatures)) {
				next.visitLineBreak(kind);
			}
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			if (isEnabled(Feature.StripGrammar, chosenFeatures, foundFeatures)) {
				return this;
			} else {
				if (strongsPrefixes != null && isEnabled(Feature.StripStrongsWithPrefixes, chosenFeatures, foundFeatures)) {
					strongsPrefixes = null;
					strongs = null;
				}
				if (strongs != null && isEnabled(Feature.StripStrongs, chosenFeatures, foundFeatures)) {
					strongs = null;
					strongsPrefixes = null;
				}
				if (rmac != null && isEnabled(Feature.StripMorph, chosenFeatures, foundFeatures))
					rmac = null;
				else if (rmac != null) {
					boolean changed = false;
					for (int i = 0; i < rmac.length; i++) {
						if (rmac[i].matches(Utils.RMAC_REGEX) && isEnabled(Feature.StripRMAC, chosenFeatures, foundFeatures)) {
							rmac[i] = null;
							changed = true;
						} else if (rmac[i].matches(Utils.WIVU_REGEX) && isEnabled(Feature.StripWIVU, chosenFeatures, foundFeatures)) {
							rmac[i] = null;
							changed = true;
						}
					}
					if (changed) {
						rmac = Arrays.asList(rmac).stream().filter(r -> r != null).toArray(String[]::new);
						if (rmac.length == 0) {
							rmac = null;
						}
					}
				}
				if (sourceIndices != null && isEnabled(Feature.StripSourceIndexes, chosenFeatures, foundFeatures))
					sourceIndices = null;
				if (strongs == null && rmac == null && sourceIndices == null) {
					return this;
				}
				return new StripVisitor(next.visitGrammarInformation(strongsPrefixes, strongs, rmac, sourceIndices), chosenFeatures, foundFeatures);
			}
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			if (isEnabled(Feature.StripDictionaryReferences, chosenFeatures, foundFeatures)) {
				return null;
			} else {
				return new StripVisitor(next.visitDictionaryEntry(dictionary, entry), chosenFeatures, foundFeatures);
			}
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			if (!isEnabled(Feature.StripRawHTML, chosenFeatures, foundFeatures)) {
				next.visitRawHTML(mode, raw);
			}
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			if (isEnabled(Feature.StripVariations, chosenFeatures, foundFeatures)) {
				return null;
			} else {
				return new StripVisitor(next.visitVariationText(variations), chosenFeatures, foundFeatures);
			}
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			if (isEnabled(Feature.StripOrKeepExtraAttributes, chosenFeatures, foundFeatures)) {
				return prio == ExtraAttributePriority.KEEP_CONTENT ? this : null;
			} else if (isEnabled(Feature.StripExtraAttributes, chosenFeatures, foundFeatures)) {
				return null;
			} else {
				return new StripVisitor(next.visitExtraAttribute(prio, category, key, value), chosenFeatures, foundFeatures);
			}
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			return false;
		}
	}

	private class CountFormattingVisitor extends VisitorAdapter<RuntimeException> {
		Map<String, AtomicInteger> counts = new HashMap<>();

		public CountFormattingVisitor() throws RuntimeException {
			super(null);
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			count("C=" + css);
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			count("F=" + kind.name());
			return this;
		}

		private void count(String key) {
			if (!counts.containsKey(key))
				counts.put(key, new AtomicInteger(0));
			counts.get(key).incrementAndGet();
		}

		public void printSummary(PrintStream out) {
			List<String> keys = new ArrayList<>(counts.keySet());
			Collections.sort(keys);
			for (String key : keys) {
				out.println("\t" + key + ": " + counts.get(key));
			}
		}
	}

	private static class OptimizeFormattingVisitor extends FormattedText.VisitorAdapter<RuntimeException> {

		private final Set<OptimizeFormattingOption> options;
		private final Map<String, String[]> mappings;
		private final Visitor<RuntimeException> next;
		private final FormattingSetList formattingSets;
		private final Set<String> currentFormattings;

		public OptimizeFormattingVisitor(Visitor<RuntimeException> next, Set<OptimizeFormattingOption> options, Map<String, String[]> mappings) {
			super(null);
			this.next = next;
			this.options = options;
			this.mappings = mappings;
			this.formattingSets = new FormattingSetList();
			this.currentFormattings = new HashSet<>();
		}

		private OptimizeFormattingVisitor(OptimizeFormattingVisitor parent, String newFormatting) {
			super(null);
			this.next = null;
			this.options = parent.options;
			this.mappings = parent.mappings;
			this.formattingSets = parent.formattingSets;
			this.currentFormattings = new HashSet<>(parent.currentFormattings);
			if (mappings.containsKey(newFormatting)) {
				for (String value : mappings.get(newFormatting)) {
					if (value.trim().length() > 0) {
						currentFormattings.add(value.trim());
					}
				}
			} else if (options.contains(OptimizeFormattingOption.ToCSS) && newFormatting.startsWith("F=")) {
				if (!options.contains(OptimizeFormattingOption.RemoveCSS)) {
					currentFormattings.add("C="+FormattingInstructionKind.valueOf(newFormatting.substring(2)).getCss());
				}
			} else if (!options.isEmpty() && newFormatting.startsWith("C=")) {
				Stream<String> newFormattings;
				if (options.contains(OptimizeFormattingOption.SplitCSS)) {
					newFormattings = Arrays.asList(newFormatting.substring(2).split(";")).stream().map(f -> "C=" + f.trim()).filter(f -> !f.equals("C="));
				} else {
					newFormattings = Arrays.asList(newFormatting).stream();
				}
				if (options.contains(OptimizeFormattingOption.FromCSS)) {
					newFormattings = newFormattings.map(f -> {
						String cf = f.substring(2).toLowerCase().trim();
						if (!cf.endsWith(";"))
							cf += ";";
						for (FormattingInstructionKind fi : FormattingInstructionKind.values()) {
							if (fi.getCss().equals(cf)) {
								return "F=" + fi.name();
							}
						}
						return f;
					});
				}
				if (options.contains(OptimizeFormattingOption.RemoveCSS)) {
					newFormattings = newFormattings.filter(f -> !f.startsWith("C="));
				}
				newFormattings.forEach(f -> currentFormattings.add(f));
			} else {
				currentFormattings.add(newFormatting);
			}
		}

		@Override
		protected Visitor<RuntimeException> getVisitor() throws RuntimeException {
			return formattingSets.getVisitor(currentFormattings);
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return new OptimizeFormattingVisitor(childVisitor, options, mappings);
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			return new OptimizeFormattingVisitor(this, "F=" + kind.name());
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			return new OptimizeFormattingVisitor(this, "C=" + css);
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			if (next != null) {
				formattingSets.accept(next, options);
				next.visitEnd();
			}
			return false;
		}
	}

	private static class FormattingSetList {

		private List<FormattingSet> formattingSets = new ArrayList<>();

		public Visitor<RuntimeException> getVisitor(Set<String> formattings) {
			FormattingSet current = formattingSets.size() > 0 ? formattingSets.get(formattingSets.size() - 1) : null;
			if (current == null || !current.formattings.equals(formattings)) {
				current = new FormattingSet(formattings);
				formattingSets.add(current);
			}
			return current.content.getAppendVisitor();
		}

		public <T extends Throwable> void accept(Visitor<T> next, Set<OptimizeFormattingOption> options) throws T {
			List<String> activeFormattings = new ArrayList<>();
			List<Visitor<T>> visitorStack = new ArrayList<>();
			visitorStack.add(next);
			for (int i = 0; i < formattingSets.size(); i++) {
				FormattingSet set = formattingSets.get(i);
				if (set.content.getElementTypes(1).length() == 0)
					continue;

				// close formattings that are no longer valid
				for (int j = 0; j < activeFormattings.size(); j++) {
					if (!set.formattings.contains(activeFormattings.get(j))) {
						while (activeFormattings.size() > j) {
							activeFormattings.remove(activeFormattings.size() - 1);
							visitorStack.remove(visitorStack.size() - 1);
						}
					}
				}

				// find formattings to open
				List<String> formattingsToOpen = new ArrayList<>(set.formattings);
				formattingsToOpen.removeAll(activeFormattings);

				// sort them based on the number of sets that will use it
				final Map<String, Integer> formattingLengths = new HashMap<>();
				for (String formatting : formattingsToOpen) {
					int count = 1;
					for (int j = i + 1; j < formattingSets.size(); j++) {
						if (!formattingSets.get(j).formattings.contains(formatting))
							break;
						count++;
					}
					formattingLengths.put(formatting, count);
				}
				Collections.sort(formattingsToOpen, new Comparator<String>() {
					public int compare(String s1, String s2) {
						int result = Integer.compare(formattingLengths.get(s2), formattingLengths.get(s1));
						if (result == 0) result = s1.compareTo(s2);
						return result;
					}
				});

				// open
				Visitor<T> v = visitorStack.get(visitorStack.size() - 1);
				String cssPrefix = "";
				for (int j = 0; j < formattingsToOpen.size(); j++) {
					String formatting = formattingsToOpen.get(j);
					if (formatting.startsWith("F=")) {
						v = v.visitFormattingInstruction(FormattingInstructionKind.valueOf(formatting.substring(2)));
					} else if (formatting.startsWith("C=")) {
						// join subsequent CSS formattings that have the same formatting length;
						// keep the original formattings in activeformattings to properly close them later
						String nextFormatting = j + 1 < formattingsToOpen.size() && options.contains(OptimizeFormattingOption.JoinCSS) ? formattingsToOpen.get(j+1) : null;
						if (nextFormatting != null && nextFormatting.startsWith("C=") && formattingLengths.get(formatting) == formattingLengths.get(nextFormatting)) {
							cssPrefix += formatting.substring(2).trim() + (formatting.trim().endsWith(";") ? " " : "; ");
						} else {
							v = v.visitCSSFormatting(cssPrefix + formatting.substring(2));
							cssPrefix = "";
						}
					} else {
						throw new IllegalArgumentException("Unknown formatting: " + formatting);
					}
					activeFormattings.add(formatting);
					visitorStack.add(v);
				}

				// visit content
				set.content.accept(v);
			}
		}

		private static class FormattingSet {
			private final Set<String> formattings;
			private final FormattedText content = new FormattedText();

			private FormattingSet(Set<String> formattings) {
				this.formattings = formattings;
			}
		}
	}

	private static enum VerseStructureFormat {
		VIRTUAL, VIRTUAL_IGNORING_HEADLINES, RANGE, RANGE_ASCENDING, RAW
	}

	private static class GroupedVerse {
		private final String number;
		private final List<Headline> headlines;
		private final List<Verse> verses;

		private GroupedVerse(String number, List<Headline> headlines, List<Verse> verses) {
			super();
			this.number = number;
			this.headlines = headlines;
			this.verses = verses;
		}
	}

	public static class SplitVerseVisitor extends FormattedText.VisitorAdapter<RuntimeException> {

		private boolean verseEmpty;
		private Verse currentVerse;
		private final List<Verse> verses;
		private final List<Headline> headlines = new ArrayList<>();

		public SplitVerseVisitor(String originalNumber, List<Verse> verses) throws RuntimeException {
			super(null);
			this.currentVerse = new Verse(originalNumber);
			verseEmpty = true;
			this.verses = verses;
		}

		@Override
		protected Visitor<RuntimeException> getVisitor() throws RuntimeException {
			return currentVerse.getAppendVisitor();
		}

		@Override
		protected void beforeVisit() throws RuntimeException {
			while (!headlines.isEmpty()) {
				Headline hl = headlines.remove(0);
				hl.accept(currentVerse.getAppendVisitor().visitHeadline(hl.getDepth()));
			}
			verseEmpty = false;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			Headline hl = new Headline(depth);
			headlines.add(hl);
			return hl.getAppendVisitor();
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			String verseNumber = getVerseNumber(category, key, value);
			if (verseNumber != null) {
				if (!verseEmpty) {
					currentVerse.finished();
					verses.add(currentVerse);
				}
				currentVerse = new Verse(verseNumber);
				verseEmpty = true;
				return null;
			} else {
				return super.visitExtraAttribute(prio, category, key, value);
			}
		}

		protected String getVerseNumber(String category, String key, String value) {
			if (category.equals("verse") && key.equals("raw-number")) {
				return value.replace('D', '.').replace('C', ',').replace('S', '/');
			} else {
				return null;
			}
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			if (!verseEmpty || !headlines.isEmpty()) {
				beforeVisit();
				currentVerse.finished();
				verses.add(currentVerse);
			}
			return false;
		}
	}

	private static class ChangeStrongsPrefixesVisitor extends FormattedText.VisitorAdapter<RuntimeException> {

		private final char defaultPrefix;
		private final boolean add;

		private ChangeStrongsPrefixesVisitor(Visitor<RuntimeException> next, char defaultPrefix, boolean add) throws RuntimeException {
			super(next);
			this.defaultPrefix = defaultPrefix;
			this.add = add;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return new ChangeStrongsPrefixesVisitor(childVisitor, defaultPrefix, add);
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			if (add && strongsPrefixes == null && strongs != null) {
				strongsPrefixes = new char[strongs.length];
				Arrays.fill(strongsPrefixes, defaultPrefix);
			} else if (!add && strongsPrefixes != null) {
				boolean allDefault = true;
				for (int i = 0; i < strongsPrefixes.length; i++) {
					if (strongsPrefixes[i] != defaultPrefix) {
						allDefault = false;
						break;
					}
				}
				if (allDefault)
					strongsPrefixes = null;
			}
			return super.visitGrammarInformation(strongsPrefixes, strongs, rmac, sourceIndices);
		}
	}
}
