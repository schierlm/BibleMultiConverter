package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;

public class LaridianPocketBible implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Export to Laridian Pocket Bible",
			"",
			"Export: LaridianPocketBible <moduleName>.html <Versification>[,<VersificationNT>] <HeadingTypes> [-inline | <interlinearTypes>]",
			"",
			"THe resulting HTML file can be used with Laridian Book Builder (tested version 2.1.0.624) to a LBK file.", 
			"",
			"When two versifications are given, one is used for OT and one for NT.",
			"Versifications can be overridden per book in the meta tags after conversion.",
			"",
			"<HeadingTypes> can be any subset of 'Bible,Book,Chapter', or 'None'",
			"<InterlinearTypes> can be a comma-separated list of 'SG', 'SH', 'RMAC', 'WIVU', 'IDX'."
	};

	private static final Map<BookID, String> BOOK_MAP = new EnumMap<>(BookID.class);

	static {
		BOOK_MAP.put(BookID.INTRODUCTION, "FntM");

		BOOK_MAP.put(BookID.BOOK_Gen, "Gn");
		BOOK_MAP.put(BookID.BOOK_Exod, "Ex");
		BOOK_MAP.put(BookID.BOOK_Lev, "Lv");
		BOOK_MAP.put(BookID.BOOK_Num, "Nu");
		BOOK_MAP.put(BookID.BOOK_Deut, "Dt");
		BOOK_MAP.put(BookID.BOOK_Josh, "Josh");
		BOOK_MAP.put(BookID.BOOK_Judg, "Jdg");
		BOOK_MAP.put(BookID.BOOK_Ruth, "Ru");
		BOOK_MAP.put(BookID.BOOK_1Sam, "1S");
		BOOK_MAP.put(BookID.BOOK_2Sam, "2S");
		BOOK_MAP.put(BookID.BOOK_1Kgs, "1K");
		BOOK_MAP.put(BookID.BOOK_2Kgs, "2K");
		BOOK_MAP.put(BookID.BOOK_1Chr, "1Ch");
		BOOK_MAP.put(BookID.BOOK_2Chr, "2Ch");
		BOOK_MAP.put(BookID.BOOK_Ezra, "Ezr");
		BOOK_MAP.put(BookID.BOOK_Neh, "Ne");
		BOOK_MAP.put(BookID.BOOK_Esth, "Es");
		BOOK_MAP.put(BookID.BOOK_Job, "Job");
		BOOK_MAP.put(BookID.BOOK_Ps, "Ps");
		BOOK_MAP.put(BookID.BOOK_Prov, "Pr");
		BOOK_MAP.put(BookID.BOOK_Eccl, "Ec");
		BOOK_MAP.put(BookID.BOOK_Song, "Song");
		BOOK_MAP.put(BookID.BOOK_Isa, "Isa");
		BOOK_MAP.put(BookID.BOOK_Jer, "Je");
		BOOK_MAP.put(BookID.BOOK_Lam, "La");
		BOOK_MAP.put(BookID.BOOK_Ezek, "Ezk");
		BOOK_MAP.put(BookID.BOOK_Dan, "Da");
		BOOK_MAP.put(BookID.BOOK_Hos, "Ho");
		BOOK_MAP.put(BookID.BOOK_Joel, "Joe");
		BOOK_MAP.put(BookID.BOOK_Amos, "Am");
		BOOK_MAP.put(BookID.BOOK_Obad, "Ob");
		BOOK_MAP.put(BookID.BOOK_Jonah, "Jnh");
		BOOK_MAP.put(BookID.BOOK_Mic, "Mi");
		BOOK_MAP.put(BookID.BOOK_Nah, "Na");
		BOOK_MAP.put(BookID.BOOK_Hab, "Hab");
		BOOK_MAP.put(BookID.BOOK_Zeph, "Zeph");
		BOOK_MAP.put(BookID.BOOK_Hag, "Hag");
		BOOK_MAP.put(BookID.BOOK_Zech, "Zec");
		BOOK_MAP.put(BookID.BOOK_Mal, "Mal");

		BOOK_MAP.put(BookID.BOOK_Matt, "Mt");
		BOOK_MAP.put(BookID.BOOK_Mark, "Mk");
		BOOK_MAP.put(BookID.BOOK_Luke, "Lk");
		BOOK_MAP.put(BookID.BOOK_John, "Jn");
		BOOK_MAP.put(BookID.BOOK_Acts, "Ac");
		BOOK_MAP.put(BookID.BOOK_Rom, "Rm");
		BOOK_MAP.put(BookID.BOOK_1Cor, "1Co");
		BOOK_MAP.put(BookID.BOOK_2Cor, "2Co");
		BOOK_MAP.put(BookID.BOOK_Gal, "Ga");
		BOOK_MAP.put(BookID.BOOK_Eph, "Ep");
		BOOK_MAP.put(BookID.BOOK_Phil, "Php");
		BOOK_MAP.put(BookID.BOOK_Col, "Col");
		BOOK_MAP.put(BookID.BOOK_1Thess, "1Th");
		BOOK_MAP.put(BookID.BOOK_2Thess, "2Th");
		BOOK_MAP.put(BookID.BOOK_1Tim, "1Ti");
		BOOK_MAP.put(BookID.BOOK_2Tim, "2Ti");
		BOOK_MAP.put(BookID.BOOK_Titus, "Tit");
		BOOK_MAP.put(BookID.BOOK_Phlm, "Phm");
		BOOK_MAP.put(BookID.BOOK_Heb, "Heb");
		BOOK_MAP.put(BookID.BOOK_Jas, "Ja");
		BOOK_MAP.put(BookID.BOOK_1Pet, "1P");
		BOOK_MAP.put(BookID.BOOK_2Pet, "2P");
		BOOK_MAP.put(BookID.BOOK_1John, "1Jn");
		BOOK_MAP.put(BookID.BOOK_2John, "2Jn");
		BOOK_MAP.put(BookID.BOOK_3John, "3Jn");
		BOOK_MAP.put(BookID.BOOK_Jude, "Jde");
		BOOK_MAP.put(BookID.BOOK_Rev, "Rev");

		BOOK_MAP.put(BookID.BOOK_Jdt, "Jdt");
		BOOK_MAP.put(BookID.BOOK_Wis, "Wis");
		BOOK_MAP.put(BookID.BOOK_Tob, "Tob");
		BOOK_MAP.put(BookID.BOOK_Sir, "Sir");
		BOOK_MAP.put(BookID.BOOK_Bar, "Bar");
		BOOK_MAP.put(BookID.BOOK_1Macc, "1M");
		BOOK_MAP.put(BookID.BOOK_2Macc, "2M");
		BOOK_MAP.put(BookID.BOOK_AddEsth, "AddEst");
		BOOK_MAP.put(BookID.BOOK_PrMan, "Man");
		BOOK_MAP.put(BookID.BOOK_3Macc, "3M");
		BOOK_MAP.put(BookID.BOOK_4Macc, "4M");
		BOOK_MAP.put(BookID.BOOK_EpJer, "LetJer");
		BOOK_MAP.put(BookID.BOOK_1Esd, "1E");
		BOOK_MAP.put(BookID.BOOK_2Esd, "2E");
		BOOK_MAP.put(BookID.BOOK_Sus, "Sus");
		BOOK_MAP.put(BookID.BOOK_Bel, "Bel");
		BOOK_MAP.put(BookID.BOOK_PrAzar, "Aza");
		BOOK_MAP.put(BookID.BOOK_EsthGr, "Est (Gk)");

		BOOK_MAP.put(BookID.APPENDIX, "BckM");

		// BOOK_MAP.put(BookID.BOOK_AddDan,"");
		// BOOK_MAP.put(BookID.BOOK_Odes,"");
		// BOOK_MAP.put(BookID.BOOK_PssSol,"");
		// BOOK_MAP.put(BookID.BOOK_EpLao,"");
		// BOOK_MAP.put(BookID.BOOK_1En,"");
		// BOOK_MAP.put(BookID.BOOK_AddPs,"");
		// BOOK_MAP.put(BookID.BOOK_DanGr,"");
		// BOOK_MAP.put(BookID.BOOK_Jub,"");
		// BOOK_MAP.put(BookID.BOOK_4Ezra,"");
		// BOOK_MAP.put(BookID.BOOK_5Ezra,"");
		// BOOK_MAP.put(BookID.BOOK_6Ezra,"");
		// BOOK_MAP.put(BookID.BOOK_5ApocSyrPss,"");
		// BOOK_MAP.put(BookID.BOOK_2Bar,"");
		// BOOK_MAP.put(BookID.BOOK_4Bar,"");
		// BOOK_MAP.put(BookID.BOOK_EpBar,"");
		// BOOK_MAP.put(BookID.BOOK_1Meq,"");
		// BOOK_MAP.put(BookID.BOOK_2Meq,"");
		// BOOK_MAP.put(BookID.BOOK_3Meq,"");
		// BOOK_MAP.put(BookID.BOOK_Rep,"");

	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String outfile = exportArgs[0];
		String versificationOT = exportArgs[1], versificationNT = versificationOT;
		if (versificationOT.contains(",")) {
			String[] parts = versificationOT.split(",", 2);
			versificationOT = parts[0];
			versificationNT = parts[1];
		}
		EnumMap<HeadingType, Integer> headingTypes = new EnumMap<>(HeadingType.class);
		if (!exportArgs[2].equals("None")) {
			for (String part : exportArgs[2].split(","))
				headingTypes.put(HeadingType.valueOf(part), -1);
		}
		int firstHeading = 1;
		for (HeadingType ht : Arrays.asList(HeadingType.Bible, HeadingType.Book, HeadingType.Chapter)) {
			if (headingTypes.containsKey(ht)) {
				headingTypes.put(ht, firstHeading);
				firstHeading++;
			}
		}
		boolean inline = false, hasChapterPrologs = false, hasStrongsGreek = false, hasStrongsHebrew = false;
		InterlinearType<?>[] interlinearTypes = null;
		if (exportArgs.length > 3) {
			if (exportArgs[3].equals("-inline")) {
				inline = true;
			} else {
				String[] parts = exportArgs[3].split(",");
				interlinearTypes = new InterlinearType[parts.length];
				for (int i = 0; i < interlinearTypes.length; i++) {
					interlinearTypes[i] = parseInterlinearType(i, parts[i]);
					if (interlinearTypes[i] == null) {
						throw new RuntimeException("Unsupported interlinear type: " + parts[i]);
					}
				}
			}
		}
		StringBuilder bookList = new StringBuilder();
		for (Book bk : bible.getBooks()) {
			if (!BOOK_MAP.containsKey(bk.getId()))
				continue;
			if (bookList.length() > 0)
				bookList.append(", ");
			bookList.append(BOOK_MAP.get(bk.getId()) + "(" + (bk.getId().isNT() ? versificationNT : versificationOT) + ")");
			final boolean nt = bk.getId().isNT();
			List<Chapter> chapters = bk.getChapters();
			for (int cn = 0; cn < chapters.size(); cn++) {
				Chapter ch = chapters.get(cn);
				if (cn > 0 && ch.getProlog() != null) {
					hasChapterPrologs = true;
				}
				if (interlinearTypes == null) {
					for (Verse vv : ch.getVerses()) {
						String elementTypes = vv.getElementTypes(Integer.MAX_VALUE);
						if (elementTypes.contains("g")) {
							boolean[] strongsFlags = new boolean[2];
							vv.accept(new VisitorAdapter<RuntimeException>(null) {
								@Override
								protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
									return this;
								}

								@Override
								public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
									if (strongs != null) {
										for (int i = 0; i < strongs.length; i++) {
											String formatted = Utils.formatStrongs(nt, i, strongsPrefixes, strongs);
											if (formatted.startsWith("G"))
												strongsFlags[0] = true;
											else if (formatted.startsWith("H"))
												strongsFlags[1] = true;
										}
									}
									return super.visitGrammarInformation(strongsPrefixes, strongs, rmac, sourceIndices);
								}
							});
							hasStrongsGreek |= strongsFlags[0];
							hasStrongsHebrew |= strongsFlags[1];
						}
					}
				}
			}
		}
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outfile), StandardCharsets.UTF_8))) {
			bw.write("<html><head>\n");
			bw.write("<meta name=\"pb_title\" content=\"" + h(bible.getName()) + "\">\n");
			bw.write("<meta name=\"pb_abbrev\" content=\"BIBLESAMPLE\">\n");
			bw.write("<meta name=\"pb_copyright\" content=\"Copyright &copy; ...\">\n");
			bw.write("<meta name=\"pb_publisher\" content=\"...\">\n");
			bw.write("<meta name=\"pb_city\" content=\"...\">\n");
			bw.write("<meta name=\"pb_date\" content=\"" + new SimpleDateFormat("yyyy").format(new Date()) + "\">\n");
			bw.write("<meta name=\"pb_pubid\" content=\"501\">\n");
			bw.write("<meta name=\"pb_bookid\" content=\"999\">\n");
			bw.write("<meta name=\"pb_editionid\" content=\"1\">\n");
			bw.write("<meta name=\"pb_revisionid\" content=\"1\">\n");
			bw.write("<meta name=\"pb_synctype\" content=\"verse\">\n");
			bw.write("<meta name=\"pb_isabible\" content=\"yes\">\n");
			if (interlinearTypes != null) {
				for (int i = 0; i < interlinearTypes.length; i++) {
					bw.write("<meta name=\"pb_attr\" content=\"" + interlinearTypes[i].name + "=" + (i + 1) + ", " + interlinearTypes[i].metaValue + "\">\n");
				}
			} else {
				if (hasStrongsHebrew)
					bw.write("<meta name=\"pb_hebdictid\" content=\"701,19\">\n");
				if (hasStrongsGreek)
					bw.write("<meta name=\"pb_grkdictid\" content=\"701,19\">\n");
			}
			bw.write("<meta name=\"pb_biblebooks\" content=\"" + bookList.toString() + "\">\n");
			bw.write("</head><body>\n");
			String pendingLine = null;
			for (Book book : bible.getBooks()) {
				String abbr = BOOK_MAP.get(book.getId());
				if (abbr == null) {
					System.out.println("WARNING: Skipping unsupported book " + book.getAbbr());
					continue;
				}
				int chapterNumber = 0;
				for (Chapter ch : book.getChapters()) {
					chapterNumber++;
					boolean firstVerse = true;
					for (Verse v : ch.getVerses()) {
						LaridianVisitor vv = new LaridianVisitor(firstHeading, abbr + " " + chapterNumber + ":" + v.getNumber(), new Versification.Reference(book.getId(), chapterNumber, v.getNumber()), interlinearTypes);
						if (firstVerse) {
							if (headingTypes.containsKey(HeadingType.Bible)) {
								vv.addHeadline(headingTypes.get(HeadingType.Bible), h(bible.getName()));
								headingTypes.remove(HeadingType.Bible);
							}
							if (headingTypes.containsKey(HeadingType.Book) && chapterNumber == 1) {
								vv.addHeadline(headingTypes.get(HeadingType.Book), h(book.getLongName()));
							}
							if (headingTypes.containsKey(HeadingType.Chapter) && hasChapterPrologs) {
								vv.addHeadline(headingTypes.get(HeadingType.Chapter), "<pb_noconc>" + h(book.getAbbr()) + " " + chapterNumber + "</pb_noconc>");
							}
							if (ch.getProlog() != null) {
								vv.sb.append("<!-- prolog -->\n");
								vv.startProlog();
								ch.getProlog().accept(vv);
								vv.sb.append("<!-- /prolog -->\n"); // ensure paragraphs are not merged!
							}
							if (headingTypes.containsKey(HeadingType.Chapter) && !hasChapterPrologs) {
								vv.addHeadline(headingTypes.get(HeadingType.Chapter), "<pb_noconc>" + h(book.getAbbr()) + " " + chapterNumber + "</pb_noconc>");
							}
							firstVerse = false;
						}
						v.accept(vv);
						for (String line : vv.getLines()) {
							if (line.isEmpty())
								continue;
							if (inline) {
								if (pendingLine != null && pendingLine.endsWith("</p>") && line.startsWith("<p>")) {
									pendingLine = pendingLine.substring(0, pendingLine.length() - 4) + " " + line.substring(3);
								} else {
									if (pendingLine != null) {
										bw.write(pendingLine + "\n");
									}
									pendingLine = line;
								}
							} else {
								if (interlinearTypes != null && line.contains("<pb_attr ")) {
									line = line.replaceFirst(">", " class=\"pb_interlinear\">");
								}
								bw.write(line + "\n");
							}
						}
					}
				}
			}
			if (pendingLine != null) {
				bw.write(pendingLine + "\n");
			}
			bw.write("</body></html>\n");
		}
	}

	private static String h(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	protected InterlinearType<?> parseInterlinearType(int index, String type) {
		try {
			return BuiltinInterlinearType.valueOf(type).it;
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	public static enum HeadingType {
		Bible, Book, Chapter;
	}

	protected static abstract class InterlinearType<C> {

		public static InterlinearTypePrecalculator<Void> NO_PRECALCULATOR = new InterlinearTypePrecalculator<Void>() {
			@Override
			public Void precalculate(boolean nt, Reference verseReference, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
				return null;
			}
		};

		public final String name, metaValue;
		public final InterlinearTypePrecalculator<C> precalculator;

		protected InterlinearType(InterlinearTypePrecalculator<C> precalculator, String name, String metaValue) {
			this.precalculator = precalculator;
			this.name = name;
			this.metaValue = metaValue;
		}

		protected abstract List<String> determineValues(C precalculatedValue, boolean nt, Versification.Reference verseReference, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices);
	}

	protected static interface InterlinearTypePrecalculator<C> {
		public abstract C precalculate(boolean nt, Versification.Reference verseReference, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices);
	}

	private static class StrongsInterlinearType extends InterlinearType<List<String>> {

		private static final InterlinearTypePrecalculator<List<String>> STRONGS_PRECALCULATOR = new InterlinearTypePrecalculator<List<String>>() {
			public List<String> precalculate(boolean nt, Reference verseReference, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
				List<String> result = new ArrayList<>();
				if (strongs != null) {
					for (int i = 0; i < strongs.length; i++) {
						result.add(Utils.formatStrongs(nt, i, strongsPrefixes, strongs));
					}
				}
				return result;
			}
		};

		private final boolean greek;

		private StrongsInterlinearType(boolean greek) {
			super(STRONGS_PRECALCULATOR, greek ? "strongsgreek" : "strongshebrew", //
					(greek ? "Strong's Greek Number, G, G, G, nt" : "Strong's Hebrew Number, H, H, H, ot") + ", , sync:\\\\, yes");
			this.greek = greek;
		}

		@Override
		protected List<String> determineValues(List<String> formattedStrongs, boolean nt, Reference verseReference, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
			return formattedStrongs.stream().filter(s -> s.startsWith(greek ? "G" : "H")).map(s -> s.substring(1)).collect(Collectors.toList());
		}
	}

	private static class MorphInterlinearType extends InterlinearType<Void> {
		private final String regex;

		private MorphInterlinearType(boolean greek) {
			super(NO_PRECALCULATOR, greek ? "rmacmorph" : "wivumorph", //
					(greek ? "Greek RMAC Morphology, , , , nt" : "Hebrew WIVU morphology, , , , ot") + ", , sync:\\\\\\\\, yes");
			this.regex = greek ? Utils.RMAC_REGEX : Utils.WIVU_REGEX;
		}

		@Override
		protected List<String> determineValues(Void unused, boolean nt, Reference verseReference, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
			List<String> result = new ArrayList<>();
			if (rmac != null) {
				for (int i = 0; i < rmac.length; i++) {
					if (rmac[i].matches(regex))
						result.add(rmac[i]);
				}
			}
			return result;
		}
	}

	private static class SourceIndexInterlinearType extends InterlinearType<Void> {
		public SourceIndexInterlinearType() {
			super(NO_PRECALCULATOR, "sourceindex", "Source Index, , , , , , , no");
		}

		@Override
		protected List<String> determineValues(Void unused, boolean nt, Reference verseReference, char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) {
			List<String> result = new ArrayList<>();
			if (sourceIndices != null) {
				for (int i = 0; i < sourceIndices.length; i++) {
					result.add("" + sourceIndices[i]);
				}
			}
			return result;
		}
	}

	private static enum BuiltinInterlinearType {
		SG(new StrongsInterlinearType(true)), SH(new StrongsInterlinearType(false)), //
		RMAC(new MorphInterlinearType(true)), WIVU(new MorphInterlinearType(false)), //
		IDX(new SourceIndexInterlinearType());

		private InterlinearType<?> it;

		private BuiltinInterlinearType(InterlinearType<?> it) {
			this.it = it;
		}
	}

	private static class LaridianVisitor extends AbstractNoCSSVisitor<RuntimeException> {

		private final List<String> suffixStack = new ArrayList<>();
		private final StringBuilder sb = new StringBuilder();
		private final int firstHeading;
		private final String verseReference;
		private final Versification.Reference reference;
		private final InterlinearType<?>[] interlinearTypes;

		private boolean afterHeadline, inVerse, inProlog;

		private LaridianVisitor(int firstHeading, String verseReference, Versification.Reference reference, InterlinearType<?>[] interlinearTypes) {
			this.firstHeading = firstHeading;
			this.verseReference = verseReference;
			this.reference = reference;
			this.interlinearTypes = interlinearTypes;
			suffixStack.add("");
		}

		public void addHeadline(int depth, String text) {
			visitHeadline(depth - firstHeading + 1);
			visitStart();
			sb.append(text);
			visitEnd();
		}

		public String[] getLines() {
			if (suffixStack.size() > 0)
				throw new IllegalStateException("Unused suffixes");
			return sb.toString().split("\n");
		}

		private void beforeAddHeadline() {
			if (suffixStack.size() != 1)
				return;
			if (!afterHeadline) {
				if (!inVerse && !inProlog) {
					sb.append("<pb_sync type=\"verse\" display=\"later\" value=\"" + verseReference + "\" />\n");
				} else {
					sb.append("</p>\n");
				}
				afterHeadline = true;
			}
		}

		public void startProlog() {
			if (suffixStack.size() != 1 || inVerse || inProlog)
				throw new IllegalStateException("Unable to start prolog");
			beforeAddHeadline();
			inProlog = true;
		}

		private void ensureInParagraph() {
			if (suffixStack.size() != 1)
				return;
			if (!inVerse && !inProlog)
				throw new IllegalStateException("Neither verse nor prolog was started");
			if (afterHeadline) {
				sb.append("<p>");
				afterHeadline = false;
			}
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<RuntimeException> visitHeadline(int depth) throws RuntimeException {
			if (suffixStack.size() != 1) {
				System.out.println("WARNING: Skipped headline not at toplevel");
				suffixStack.add("");
				return this;
			}
			beforeAddHeadline();
			int tagNum = depth + firstHeading - 1;
			if (tagNum > 9)
				tagNum = 9;
			if (tagNum < 1)
				throw new IllegalStateException("Invalid headline tag h" + tagNum);
			sb.append("<h" + tagNum + ">");
			suffixStack.add("</h" + tagNum + ">\n");
			return this;
		}

		@Override
		public void visitStart() throws RuntimeException {
			if (suffixStack.size() != 1 || inProlog)
				return;
			if (inVerse)
				throw new IllegalStateException("Verse already started");
			sb.append("<p><pb_sync type=\"verse\" display=\"now\" value=\"" + verseReference + "\" />");
			afterHeadline = false;
			inVerse = true;
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			ensureInParagraph();
			if (suffixStack.get(suffixStack.size() - 1).endsWith("<!-- /divine-name -->")) {
				char[] chars = text.toCharArray();
				for (int i = 0; i < chars.length; i++) {
					char ch = chars[i];
					if (ch >= 'a' && ch <= 'z' && ch != 'q' && ch != 'x') {
						chars[i] = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘ-ʀꜱᴛᴜᴠᴡ-ʏᴢ".charAt(ch - 'a');
					} else if (Character.isLowerCase(ch)) {
						System.out.println("WARNING: Unable to create DIVINE_NAME character for " + ch);
					}
				}
				text = new String(chars);
			}
			sb.append(h(text));
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
			ensureInParagraph();
			sb.append("<pb_note>");
			suffixStack.add("</pb_note>");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			ensureInParagraph();
			String abbr = BOOK_MAP.get(book);
			if (abbr == null) {
				System.out.println("WARNING: Skipping cross reference to unsupported book " + book.getOsisID());
				suffixStack.add("");
			} else {
				sb.append("<a href=\"bible:" + abbr + " " + firstChapter + ":" + firstVerse + "\">");
				suffixStack.add("</a>");
			}
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			ensureInParagraph();
			String startTag, endTag;

			switch (kind) {
			case BOLD:
			case ITALIC:
			case UNDERLINE:
			case SUBSCRIPT:
			case SUPERSCRIPT:
				startTag = "<" + kind.getHtmlTag() + ">";
				endTag = "</" + kind.getHtmlTag() + ">";
				break;
			case DIVINE_NAME:
				startTag = "<!-- divine-name -->";
				endTag = "<!-- /divine-name -->";
				break;
			case FOOTNOTE_LINK:
			case LINK:
				startTag = "<font color=\"#0000ff\">";
				endTag = "</font>";
				break;
			case STRIKE_THROUGH:
				startTag = "<!-- strike-through -->";
				endTag = "<!-- /strike-through -->";
				break;
			case WORDS_OF_JESUS:
				startTag = "<pb_woc>";
				endTag = "</pb_woc>";
				break;
			default:
				throw new IllegalArgumentException("Unsupported formatting " + kind);
			}
			sb.append(startTag);
			suffixStack.add(endTag);
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) throws RuntimeException {
			ensureInParagraph();
			return super.visitCSSFormatting(css);
		}

		@Override
		protected Visitor<RuntimeException> visitChangedCSSFormatting(String remainingCSS, Visitor<RuntimeException> resultingVisitor, int replacements) {
			if (!remainingCSS.isEmpty())
				System.out.println("WARNING: Skipping unsupported CSS formatting");
			fixupSuffixStack(replacements, suffixStack);
			return resultingVisitor;
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			ensureInParagraph();
			sb.append("<font color=\"#808080\">/</font>");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			ensureInParagraph();
			if (interlinearTypes != null || kind == LineBreakKind.PARAGRAPH)
				sb.append("</p>\n<p>");
			else
				sb.append("<br />");
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			ensureInParagraph();
			StringBuilder suffixBuilder = new StringBuilder();
			if (interlinearTypes != null) {
				Map<InterlinearTypePrecalculator, Object> precalculatedCache = new IdentityHashMap<>();
				for (InterlinearType<?> it : interlinearTypes) {
					if (!precalculatedCache.containsKey(it.precalculator))
						precalculatedCache.put(it.precalculator, it.precalculator.precalculate(reference.getBook().isNT(), reference, strongsPrefixes, strongs, rmac, sourceIndices));
					for (String value : ((InterlinearType<Object>) it).determineValues(precalculatedCache.get(it.precalculator), reference.getBook().isNT(), reference, strongsPrefixes, strongs, rmac, sourceIndices)) {
						suffixBuilder.append("<pb_attr name=\"" + it.name + "\" value=\"" + value + "\" />");
					}
				}
			} else if (strongs != null) {
				for (int i = 0; i < strongs.length; i++) {
					String formatted = Utils.formatStrongs(reference.getBook().isNT(), i, strongsPrefixes, strongs);
					String name = null;
					if (formatted.startsWith("G"))
						name = "strongsgreek";
					else if (formatted.startsWith("H"))
						name = "strongshebrew";
					if (name != null)
						suffixBuilder.append("<pb_attr name=\"" + name + "\" value=\"" + formatted.substring(1) + "\" />");
				}
			}
			suffixStack.add(suffixBuilder.toString());
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) throws RuntimeException {
			ensureInParagraph();
			String target = System.getProperty("biblemulticonverter.dictionarytarget." + dictionary);
			if (target != null) {
				sb.append("<a href=\"" + target + entry + "\">");
				suffixStack.add("</a>");
			} else {
				System.out.println("WARNING: Skipping unsupported dictionary entry for " + dictionary);
				suffixStack.add("");
			}
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			ensureInParagraph();
			System.out.println("WARNING: Skipping unsupported Raw HTML");
		}

		@Override
		public Visitor<RuntimeException> visitVariationText(String[] variations) throws RuntimeException {
			throw new UnsupportedOperationException("Variation text not supported");
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
			Visitor<RuntimeException> next = prio.handleVisitor(category, this);
			if (next != null)
				suffixStack.add("");
			return next;
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			if (suffixStack.size() == 1) {
				beforeAddHeadline();
				if (inProlog) {
					inProlog = false;
					return false;
				}
				if (!inVerse) {
					throw new IllegalStateException("Verse was not started");
				}
			}
			sb.append(suffixStack.remove(suffixStack.size() - 1));
			return false;
		}
	}
}
