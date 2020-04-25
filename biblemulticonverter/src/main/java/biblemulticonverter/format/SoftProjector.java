package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VirtualVerse;

/**
 * Importer and exporter for SoftProjector.
 */
public class SoftProjector implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Bible format used by SoftProjector",
			"",
			"Usage (export): SoftProjector <OutputFile>",
			"",
			"For importing, the system property 'softprojector.verses' can be set to",
			"'tagged' (default), 'original' or 'canonical'. The tagged format can be",
			"converted back without loss of information, if the additional system",
			"property 'softprojector.roundtrip' is also set."
	};

	private static final BookID[] BOOK_IDS = new BookID[87];
	private static final Map<BookID, Integer> BOOK_NUMBERS = new EnumMap<>(BookID.class);

	static {
		for (int i = 1; i <= 66; i++) {
			BOOK_IDS[i] = BookID.fromZefId(i);
		}

		BOOK_IDS[67] = BookID.BOOK_Tob;
		BOOK_IDS[68] = BookID.BOOK_Jdt;
		BOOK_IDS[69] = BookID.BOOK_EsthGr;
		BOOK_IDS[70] = BookID.BOOK_Wis;
		BOOK_IDS[71] = BookID.BOOK_Sir;
		BOOK_IDS[72] = BookID.BOOK_Bar;
		BOOK_IDS[73] = BookID.BOOK_EpJer;
		BOOK_IDS[74] = BookID.BOOK_PrAzar;
		BOOK_IDS[75] = BookID.BOOK_Sus;
		BOOK_IDS[76] = BookID.BOOK_Bel;
		BOOK_IDS[77] = BookID.BOOK_1Macc;
		BOOK_IDS[78] = BookID.BOOK_2Macc;
		BOOK_IDS[79] = BookID.BOOK_3Macc;
		BOOK_IDS[80] = BookID.BOOK_4Macc;
		BOOK_IDS[81] = BookID.BOOK_1Esd;
		BOOK_IDS[82] = BookID.BOOK_2Esd;
		BOOK_IDS[82] = BookID.BOOK_5Ezra;
		BOOK_IDS[83] = BookID.BOOK_PrMan;
		BOOK_IDS[84] = BookID.BOOK_3Meq;
		BOOK_IDS[85] = BookID.BOOK_PssSol;
		BOOK_IDS[86] = BookID.BOOK_Odes;

		for (int i = 0; i < BOOK_IDS.length; i++) {
			if (BOOK_IDS[i] != null)
				BOOK_NUMBERS.put(BOOK_IDS[i], i);
		}
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		Set<String> seenCanonRefs = new HashSet<>();
		VerseFormat verseFormat = VerseFormat.valueOf(System.getProperty("softprojector.verses", VerseFormat.TAGGED.name()).toUpperCase());
		boolean doRoundtrip = Boolean.getBoolean("softprojector.roundtrip");
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
			String line = br.readLine();
			if (line.startsWith("\uFEFF"))
				line = line.substring(1);
			if (!line.equals("##spDataVersion:\t1"))
				throw new IOException(line);
			line = br.readLine();
			if (!line.startsWith("##Title:\t"))
				throw new IOException(line);
			Bible result = new Bible(line.substring(9));
			MetadataBook mb = new MetadataBook();
			line = br.readLine();
			while (line.startsWith("##")) {
				if (line.endsWith(":\t") && !doRoundtrip) {
					line = br.readLine();
					continue;
				}
				if (line.endsWith(":\t"))
					line += "\uFEFF";
				String[] parts = line.split("\t");
				if (parts.length != 2 || !parts[0].endsWith(":"))
					throw new IOException(line);
				String key = parts[0].substring(2, parts[0].length() - 1);
				String value = parts[1].replace("@%", "\n");
				if (doRoundtrip)
					value = value.replace("\n\n", "\n\uFEFF\n").replace("\n\n", "\n\uFEFF\n").replace("\n ", "\n\uFEFF ").replace(" \n", " \uFEFF\n").replaceFirst("\n$", "\n\uFEFF").replace("  ", " \uFEFF ").replace("  ", " \uFEFF ");
				else
					value = value.replaceAll("\n\n+", "\n").replaceAll("  +", " ").replace("\n ", "\n").replace(" \n", "\n").replaceFirst("\n$", "");
				mb.setValue(key + "@SoftProjector", value);
				line = br.readLine();
			}
			if (!mb.getKeys().isEmpty()) {
				mb.finished();
				result.getBooks().add(mb.getBook());
			}
			Map<BookID, Book> bookMap = new HashMap<>();
			List<Integer> unsupportedBooks = new ArrayList<>();
			while (!line.equals("-----")) {
				String[] parts = line.split("\t");
				if (parts.length != 3)
					throw new IOException(line);
				int number = Integer.parseInt(parts[0]), chapCount = Integer.parseInt(parts[2]);
				if (number >= BOOK_IDS.length || BOOK_IDS[number] == null) {
					System.out.println("WARNING: Skipping unsupported book " + number + " (" + parts[1] + ")");
					unsupportedBooks.add(number);
					bookMap.put(BookID.APPENDIX, new Book("X", BookID.APPENDIX, "X", "X"));
				} else {
					Book bk = new Book(BOOK_IDS[number].getOsisID(), BOOK_IDS[number], parts[1].trim(), parts[1].trim());
					result.getBooks().add(bk);
					for (int i = 0; i < chapCount; i++)
						bk.getChapters().add(new Chapter());
					bookMap.put(BOOK_IDS[number], bk);
				}
				line = br.readLine();
			}
			while ((line = br.readLine()) != null) {
				if (line.endsWith("\t")) {
					if (doRoundtrip)
						line += "\uFEFF";
					else
						continue;
				}
				String[] parts = line.split("\t");
				if (parts.length != 5 || line.contains("\1"))
					throw new IOException(line);
				if (!seenCanonRefs.add(parts[0]))
					System.out.println("WARNING: Canonical reference exists more than once: " + parts[0]);
				Reference canonRef = parseRef(parts[0], unsupportedBooks);
				Reference origRef = parseRef(String.format("B%03dC%03dV%03d", Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])), unsupportedBooks);
				String text = parts[4].replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "\1");
				if (doRoundtrip)
					text = text.replace("  ", " \uFEFF ").replace("  ", " \uFEFF ").replaceFirst(" $", " \uFEFF").replaceFirst("^ ", "\uFEFF ");
				else
					text = text.replaceAll("  +", " ").trim();
				if (text.contains("&"))
					throw new IOException("Unsupported entity in text: " + text);
				final Reference primaryRef = (verseFormat == VerseFormat.CANONICAL) ? canonRef : origRef;
				Book bk = bookMap.get(primaryRef.getBook());
				if (bk == null)
					throw new IOException("Verse for nonexisting book/chapter: " + primaryRef);
				if (bk.getChapters().size() < primaryRef.getChapter()) {
					System.out.println("WARNING: Adding chapter " + primaryRef.getChapter() + " to book " + primaryRef.getBook());
					bk.getChapters().add(new Chapter());
				}
				if (bk.getChapters().size() < primaryRef.getChapter()) {
					throw new IOException("Verse for nonexisting book/chapter: " + primaryRef);
				}
				Chapter ch = bk.getChapters().get(primaryRef.getChapter() - 1);
				Verse v = ch.getVerses().stream().filter(vv -> vv.getNumber().equals(primaryRef.getVerse())).findFirst().orElse(null);
				boolean needSpace = true;
				if (v == null) {
					v = new Verse(primaryRef.getVerse());
					ch.getVerses().add(v);
					needSpace = false;
				}
				if (verseFormat == VerseFormat.TAGGED && (needSpace || !origRef.equals(canonRef))) {
					Visitor<RuntimeException> vv = v.getAppendVisitor();
					vv = vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "v11n", "origverse", canonRef.getBook().getOsisID() + "--" + canonRef.getChapter() + "--" + canonRef.getVerse().replace('.', 'D').replace(',', 'C').replace('/', 'S'));
					vv.visitText(needSpace ? "\uFEFF \uFEFF" : "\uFEFF");
				} else if (needSpace) {
					v.getAppendVisitor().visitText(" ");
				}
				v.getAppendVisitor().visitText(text.replace("\1", "&"));
			}
			for (Book bk : result.getBooks()) {
				if (bk.getId() == BookID.METADATA)
					continue;
				while (!bk.getChapters().isEmpty() && bk.getChapters().get(bk.getChapters().size() - 1).getVerses().isEmpty()) {
					bk.getChapters().remove(bk.getChapters().size() - 1);
				}
				for (int c = 0; c < bk.getChapters().size(); c++) {
					for (Verse v : bk.getChapters().get(c).getVerses())
						v.finished();
				}
			}
			return result;
		}
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[0]), StandardCharsets.UTF_8))) {
			bw.write("##spDataVersion:\t1\n");
			bw.write("##Title:\t" + bible.getName() + "\n");
			MetadataBook mb = bible.getMetadataBook();
			if (mb == null)
				mb = new MetadataBook();
			for (String key : mb.getKeys()) {
				if (!key.endsWith("@SoftProjector"))
					continue;
				bw.write("##" + key.substring(0, key.length() - 14) + ":\t");
				bw.write(mb.getValue(key).replace("\uFEFF", "").replace("\n", "@%") + "\n");
			}
			for (Book bk : bible.getBooks()) {
				if (bk.getId() == BookID.METADATA)
					continue;
				Integer bookNumber = BOOK_NUMBERS.get(bk.getId());
				if (bookNumber == null) {
					System.out.println("WARNING: Skipping unsupported book: " + bk.getAbbr());
					continue;
				}
				bw.write(bookNumber + "\t" + bk.getLongName() + "\t" + bk.getChapters().size() + "\n");
			}
			bw.write("-----\n");
			for (Book bk : bible.getBooks()) {
				if (bk.getId() == BookID.METADATA)
					continue;
				Integer bookNumber = BOOK_NUMBERS.get(bk.getId());
				if (bookNumber == null)
					continue;
				int bnum = bookNumber;
				for (int cc = 0; cc < bk.getChapters().size(); cc++) {
					Chapter ch = bk.getChapters().get(cc);
					int cnum = cc + 1;
					for (VirtualVerse vv : ch.createVirtualVerses(true, false)) {
						String vnum = vv.getNumber() == 0 ? "1/t" : "" + vv.getNumber();
						SoftProjectorVisitor spv = new SoftProjectorVisitor(new Reference(bk.getId(), cnum, vnum));
						boolean firstVerse = true;
						for (Verse v : vv.getVerses()) {
							if (!firstVerse || !v.getNumber().equals(vnum)) {
								spv.visitText(" (" + v.getNumber() + ") ");
							}
							firstVerse = false;
							v.accept(spv);
						}
						for (int i = 0; i < spv.references.size(); i++) {
							Reference ref = spv.references.get(i);
							StringBuilder sb = spv.builders.get(i);
							if (sb.length() == 0)
								continue;
							bw.write(formatRef(ref) + "\t" + bnum + "\t" + cnum + "\t" + vv.getNumber() + "\t" + sb.toString() + "\n");
						}
					}
				}
			}
		}
	}

	public static Reference parseRef(String ref, List<Integer> unsupportedBooks) {
		Matcher m = Pattern.compile("B([0-9]{3})C([0-9]{3})V([0-9]{3})([0-9]?)").matcher(ref);
		if (!m.matches())
			throw new RuntimeException(ref);
		int bno = Integer.parseInt(m.group(1)), cno = Integer.parseInt(m.group(2)), vno = Integer.parseInt(m.group(3));
		BookID bid = unsupportedBooks.contains(bno) ? BookID.APPENDIX : bno < BOOK_IDS.length ? BOOK_IDS[bno] : null;
		if (bid == null)
			throw new RuntimeException("Invalid book number in verse: " + bno);
		String verse = vno == 0 ? "1/t" : "" + vno;
		if (!m.group(4).isEmpty())
			verse += "." + m.group(4);
		return new Reference(bid, cno, verse);
	}

	private String formatRef(Reference ref) {
		int bnum = BOOK_NUMBERS.get(ref.getBook());
		String verse = ref.getVerse(), suffix = "";
		if (ref.getVerse().contains(".")) {
			String[] parts = verse.split("\\.", 2);
			verse = parts[0];
			suffix = parts[1];
		}
		int vnum = verse.equals("1/t") ? 0 : Integer.parseInt(verse);
		return String.format("B%03dC%03dV%03d", bnum, ref.getChapter(), vnum) + suffix;
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private static class SoftProjectorVisitor implements Visitor<RuntimeException> {

		private final List<StringBuilder> builders = new ArrayList<>();
		private final List<Reference> references = new ArrayList<>();

		private SoftProjectorVisitor(Reference currentRef) {
			references.add(currentRef);
			builders.add(new StringBuilder());
		}

		@Override
		public void visitVerseSeparator() {
			builders.get(builders.size() - 1).append("/");
		}

		@Override
		public void visitText(String text) {
			builders.get(builders.size() - 1).append(text.replace("\uFEFF", "").replace("&", "&amp;").replace("\"", "&quot;"));
		}

		@Override
		public void visitStart() {
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) {
			builders.get(builders.size() - 1).append(" ");
		}

		@Override
		public int visitElementTypes(String elementTypes) {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) {
			System.out.println("WARNING: Headlines are not supported");
			return null;
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) {
			System.out.println("WARNING: Formatting is not supported");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() {
			System.out.println("WARNING: Footnotes are not supported");
			return null;
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) {
			if (category.equals("v11n") && key.equals("origverse")) {
				String[] parts = value.split("--");
				references.add(new Reference(BookID.fromOsisId(parts[0]), Integer.parseInt(parts[1]), parts[2].replace('D', '.').replace('C', ',').replace('S', '/')));
				builders.add(new StringBuilder());
				return null;
			}
			return prio.handleVisitor(category, this);
		}

		@Override
		public boolean visitEnd() {
			return false;
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) {
			System.out.println("WARNING: Dictionary entries are not supported");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
			System.out.println("WARNING: Grammar information is not supported");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) {
			System.out.println("WARNING: Cross references are not supported");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) {
			System.out.println("WARNING: Formatting is not supported");
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) {
			System.out.println("WARNING: Raw HTML is not supported");
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) {
			throw new RuntimeException("Variations are not supported");
		}
	}

	private static enum VerseFormat {
		TAGGED, ORIGINAL, CANONICAL
	};
}
