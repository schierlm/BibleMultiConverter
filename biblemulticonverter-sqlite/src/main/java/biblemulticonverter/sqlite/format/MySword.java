package biblemulticonverter.sqlite.format;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.StandardVersification;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.format.AbstractHTMLVisitor;
import biblemulticonverter.format.RoundtripFormat;
import biblemulticonverter.sqlite.SQLiteModuleRegistry;

public class MySword implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"MySword (Bible Reader for Android).",
			"",
			"Usage: MySword <moduleName>.bbl.mybible"
	};

	private Set<String> seenHTML = new HashSet<>();

	@Override
	public Bible doImport(File inputFile) throws Exception {
		SqlJetDb db = SQLiteModuleRegistry.openDB(inputFile, false);
		if (!db.getTable("Bible").getIndexesNames().contains("bible_key") && !db.getTable("bible").getIndexesNames().contains("bible_key")) {
			db.close();
			db = SQLiteModuleRegistry.openDB(inputFile, true);
			checkIndex(db, "Bible", "bible_key", "CREATE UNIQUE INDEX bible_key ON Bible (Book ASC, Chapter ASC, Verse ASC)");
		}
		db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
		String bibleName = null;
		MetadataBook mb = new MetadataBook();
		ISqlJetTable tbl = db.getTable("Details");
		ISqlJetCursor cursor = tbl.open();
		int count = cursor.getFieldsCount();
		while (!cursor.eof()) {
			for (int i = 0; i < count; i++) {
				String fn = tbl.getDefinition().getColumns().get(i).getName();
				String fv = "" + cursor.getValue(i);

				if (fn.equals("Title")) {
					bibleName = fv;
				} else if (fn.equalsIgnoreCase("encryption") && !Arrays.asList("", "0").contains(fv)) {
					throw new IOException("Encrypted MySword modules are not supported!");
				} else if (!fv.isEmpty()) {
					fv = fv.replaceAll("[\r\n]+", "\n").replaceAll(" *\n *", "\n").replaceAll("\n$", "");
					try {
						mb.setValue("MySword@" + fn, fv);
					} catch (IllegalArgumentException ex) {
						System.out.println("WARNING: Skipping malformed metadata property " + fn);
					}
				}

			}
			cursor.next();
		}
		cursor.close();
		if (bibleName == null) {
			System.out.println("WARNING: No bible name in Details table");
			bibleName = inputFile.getName();
		}
		Bible result = new Bible(bibleName.replaceAll("\\p{Cntrl}+", " ").replaceAll("\\s+", " ").trim());
		if (!mb.getKeys().isEmpty()) {
			mb.finished();
			result.getBooks().add(mb.getBook());
		}
		Map<Integer, Book> bookIDMap = new HashMap<>();

		cursor = db.getTable("Bible").order("bible_key");
		while (!cursor.eof()) {
			int b = (int) cursor.getInteger("Book");
			int c = (int) cursor.getInteger("Chapter");
			int v = (int) cursor.getInteger("Verse");
			String scripture = cursor.getString("Scripture");
			if (scripture == null)
				scripture = "";
			scripture = scripture.replaceAll("\\p{Cntrl}+", " ").replaceAll("  +", " ").trim();
			if (!scripture.isEmpty()) {
				Book bk = bookIDMap.get(b);
				if (bk == null && b >= 1 && b <= 66) {
					BookID bid = BookID.fromZefId(b);
					bk = new Book(bid.getOsisID(), bid, bid.getEnglishName(), bid.getEnglishName());
					bookIDMap.put(b, bk);
					result.getBooks().add(bk);
				}
				if (bk == null) {
					System.out.println("WARNING: Verse for unknown book " + b + " skipped");
				} else {
					while (bk.getChapters().size() < c)
						bk.getChapters().add(new Chapter());
					Chapter ch = bk.getChapters().get(c - 1);
					Verse vv = new Verse(v == 0 ? "1/t" : "" + v);
					try {
						String rest = convertFromVerse(scripture, vv.getAppendVisitor(), false);
						if (!rest.isEmpty()) {
							System.out.println("WARNING: Treating tags as plaintext: " + rest);
							vv.getAppendVisitor().visitText(rest.replace('\t', ' ').replaceAll("  +", " "));
						}
					} catch (RuntimeException ex) {
						throw new RuntimeException(scripture, ex);
					}
					ch.getVerses().add(vv);
					vv.finished();
				}
			}
			cursor.next();
		}
		cursor.close();
		db.commit();
		db.close();
		return result;
	}

	private void checkIndex(SqlJetDb db, String tableName, String indexName, String definition) throws SqlJetException {
		if (!db.getTable(tableName).getIndexesNames().contains(indexName)) {
			System.out.println("WARNING: Rebuilding index " + indexName + " on " + tableName);
			db.beginTransaction(SqlJetTransactionMode.WRITE);
			db.createIndex(definition);
			db.commit();
		}
	}

	private void decodeEntities(Visitor<RuntimeException> vv, String text) {
		if (text.contains(">") && !text.contains("<")) {
			System.out.println("WARNING: Unclosed >, treating as plain text");
			text = text.replace(">", "&gt;");
		}
		AbstractHTMLVisitor.parseHTML(vv, null, text.replaceAll("\\p{Cntrl}+", " ").replaceAll("  +", " "), "");
	}

	private String convertFromVerse(String text, Visitor<RuntimeException> vv, boolean inFootnote) {
		int pos = text.indexOf("<");
		while (pos != -1) {
			String strongsWord = "";
			if (text.startsWith("<WG", pos) || text.startsWith("<WH", pos) || text.startsWith("<WT", pos)) {
				strongsWord = text.substring(0, pos).replaceFirst(" +$", "");
				int spacePos = strongsWord.lastIndexOf(' ');
				if (spacePos != -1) {
					decodeEntities(vv, strongsWord.substring(0, spacePos + 1));
					strongsWord = strongsWord.substring(spacePos + 1);
				}
			} else {
				decodeEntities(vv, text.substring(0, pos));
			}
			text = text.substring(pos);
			if (text.startsWith("<WG") || text.startsWith("<WH") || text.startsWith("<WT")) {
				List<String> stags = new ArrayList<>(), mtags = new ArrayList<>();
				while (text.startsWith("<WG") || text.startsWith("<WH") || text.startsWith("<WT")) {
					pos = text.indexOf(">");
					if (text.startsWith("<WT")) {
						mtags.add(text.substring(3, pos).replaceFirst(" l=\"[^\"]*\"$", ""));
					} else {
						stags.add(text.substring(2, pos));
					}
					text = text.substring(pos + 1).replaceFirst("^ +<W", "<W");
				}
				char[] spfx = new char[stags.size()];
				int[] snum = new int[stags.size()];
				char[] prefixHolder = new char[1];
				for (int i = 0; i < stags.size(); i++) {
					snum[i] = Utils.parseStrongs(stags.get(i), '\0', prefixHolder);
					spfx[i] = prefixHolder[0];
					if (snum[i] == -1) {
						System.out.println("WARNING: Invalid Strong number: " + stags.get(i));
						snum[i] = 99999;
						spfx[i] = stags.get(i).charAt(0);
					}
				}
				String[] rmacs = mtags.isEmpty() ? null : new String[mtags.size()];
				for (int i = 0; i < mtags.size(); i++) {
					rmacs[i] = mtags.get(i);
					if (!Utils.compilePattern(Utils.MORPH_REGEX).matcher(rmacs[i]).matches()) {
						System.out.println("WARNING: Skipping malformed RMAC/WIVU morphology code: " + rmacs[i]);
						rmacs = null;
						break;
					}
				}
				if (snum.length == 0 && rmacs == null)
					decodeEntities(vv, strongsWord);
				else
					decodeEntities(vv.visitGrammarInformation(spfx.length == 0 ? null : spfx, snum.length == 0 ? null : snum, rmacs, null), strongsWord);
			} else if (text.startsWith("<Fi>") || text.startsWith("<Fo>") || text.startsWith("<Fr>") || text.startsWith("<Fu>") || text.startsWith("<Rf>") || text.startsWith("<Ts>")) {
				return text;
			} else if (text.startsWith("<CM>")) {
				vv.visitLineBreak(LineBreakKind.PARAGRAPH);
				text = text.substring(4);
			} else if (text.startsWith("<FI>")) {
				text = convertFromVerse(text.substring(4), vv.visitFormattingInstruction(FormattingInstructionKind.ITALIC), inFootnote);
				if (!text.startsWith("<Fi>"))
					System.out.println("WARNING: Unclosed <FI> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<FO>")) {
				text = convertFromVerse(text.substring(4), vv.visitCSSFormatting("x-mysword: ot-quote"), inFootnote);
				if (!text.startsWith("<Fo>"))
					System.out.println("WARNING: Unclosed <FO> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<FR>")) {
				text = convertFromVerse(text.substring(4), vv.visitFormattingInstruction(FormattingInstructionKind.WORDS_OF_JESUS), inFootnote);
				if (!text.startsWith("<Fr>"))
					System.out.println("WARNING: Unclosed <FR> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<FU>")) {
				text = convertFromVerse(text.substring(4), vv.visitFormattingInstruction(FormattingInstructionKind.UNDERLINE), inFootnote);
				if (!text.startsWith("<Fu>"))
					System.out.println("WARNING: Unclosed <FU> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<RF>") || text.startsWith("<RF q=")) {
				pos = text.indexOf(">");
				text = convertFromVerse(text.substring(pos + 1), vv.visitFootnote(), true);
				if (!text.startsWith("<Rf>"))
					System.out.println("WARNING: Unclosed <RF> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<TS>")) {
				text = convertFromVerse(text.substring(4), vv.visitHeadline(1), inFootnote);
				if (!text.startsWith("<Ts>"))
					System.out.println("WARNING: Unclosed <TS> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<TS1>") || text.startsWith("<TS2>") || text.startsWith("<TS3>")) {
				text = convertFromVerse(text.substring(5), vv.visitHeadline(text.charAt(3) - '0'), false);
				if (!text.startsWith("<Ts>"))
					System.out.println("WARNING: Unclosed <TS#> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<RX")) {
				int posEnd = text.indexOf('>');
				String rangeText = text.substring(3, posEnd).trim();
				if (rangeText.matches("[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9]+)?")) {
					String[] range = rangeText.split("\\.");
					BookID rbook = BookID.fromZefId(Integer.parseInt(range[0]));
					int rch = Integer.parseInt(range[1]);
					String[] rvs = range[2].split("-");
					if (rvs.length == 1)
						rvs = new String[] { rvs[0], rvs[0] };
					Visitor<RuntimeException> vvv;
					if (inFootnote) {
						vvv = vv;
					} else {
						vvv = vv.visitFootnote();
						vvv.visitText(FormattedText.XREF_MARKER);
					}
					vvv.visitCrossReference(rbook.getOsisID(), rbook, rch, rvs[0], rch, rvs[1]).visitText(rbook.getOsisID() + " " + rch + ":" + range[2]);
				} else {
					System.out.println("WARNING: Unsupported <RX> tag format: " + rangeText);
				}
				text = text.substring(posEnd + 1);
			} else if (text.startsWith("<Q>")) {
				String q = text.substring(3, text.indexOf("<q>"));
				Map<Character, String> parts = new HashMap<>();
				while (!q.isEmpty()) {
					if (q.length() < 4 || q.charAt(0) != '<' || q.charAt(2) != '>' || q.charAt(1) < 'A' || q.charAt(1) > 'Z') {
						System.out.println("WARNING: Skipping malformed <Q> interlinear information");
						parts = null;
						break;
					}
					int ePos = q.indexOf(q.substring(0, 3).toLowerCase(), 3);
					if (ePos == -1) {
						System.out.println("WARNING: Skipping malformed <Q> interlinear information");
						parts = null;
						break;
					}
					parts.put(q.charAt(1), q.substring(3, ePos).replaceAll("<D>|<w[ght]>", ""));
					q = q.substring(ePos + 3);
				}
				if (parts != null) {
					StringBuffer expanded = new StringBuffer();
					Matcher m = Pattern.compile("@([A-Z])").matcher(System.getProperty("mysword.interlinearpattern", ""));
					while (m.find()) {
						m.appendReplacement(expanded, parts.getOrDefault(m.group(1).charAt(0), ""));
					}
					m.appendTail(expanded);
					if (expanded.length() == 0) {
						System.out.println("WARNING: Skipping <Q> interlinear information (parts " + parts.keySet() + " not mapped)");
					} else {
						String rest = convertFromVerse(expanded.toString(), vv, inFootnote);
						if (!rest.isEmpty()) {
							System.out.println("WARNING: Unclosed <Q> tag content at: " + rest);
						}
					}

				}
				text = text.substring(text.indexOf("<q>") + 3);
			} else if (text.startsWith("<CI>") || text.startsWith("<PF") || text.startsWith("<PI")) {
				// skip tag
				text = text.substring(text.indexOf(">") + 1);
			} else {
				int posEnd = text.indexOf('>');
				if (posEnd == -1) {
					if (seenHTML.add("<"))
						System.out.println("WARNING: Unclosed <, treating as plain text");
					vv.visitText("<");
					text = text.substring(1);
				} else {
					String tag = text.substring(0, posEnd + 1);
					if (seenHTML.add(tag))
						System.out.println("WARNING: Unknown tag, treated as HTML: " + tag);
					text = AbstractHTMLVisitor.parseHTML(vv, null, tag.replace('\n', ' ').replaceAll("  +", " "), text.substring(posEnd + 1));
				}
			}
			pos = text.indexOf("<");
		}
		decodeEntities(vv, text);
		return "";
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String outfile = exportArgs[0];
		if (!outfile.endsWith(".bbl.mybible"))
			outfile += ".bbl.mybible";
		boolean hasStrongs = false;
		for (Book bk : bible.getBooks()) {
			for (Chapter ch : bk.getChapters()) {
				for (Verse vv : ch.getVerses()) {
					String elementTypes = vv.getElementTypes(Integer.MAX_VALUE);
					if (elementTypes.contains("g")) {
						hasStrongs = true;
						break;
					}
				}
			}
		}
		new File(outfile).delete();
		SqlJetDb db = SQLiteModuleRegistry.openDB(new File(outfile), true);
		db.getOptions().setAutovacuum(true);
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		db.getOptions().setUserVersion(0);
		db.createTable("CREATE TABLE Details (Title NVARCHAR(255), Description TEXT, Abbreviation NVARCHAR(50), Comments TEXT, Version TEXT, VersionDate DATETIME, PublishDate DATETIME, Publisher TEXT, Author TEXT, Creator TEXT, Source TEXT, EditorialComments TEXT, Language NVARCHAR(3), RightToLeft BOOL, OT BOOL, NT BOOL, Strong BOOL, VerseRules TEXT)");
		db.createTable("CREATE TABLE Bible (Book INT, Chapter INT, Verse INT, Scripture TEXT)");
		db.createIndex("CREATE UNIQUE INDEX bible_key ON Bible (Book ASC, Chapter ASC, Verse ASC)");
		ISqlJetTable detailsTable = db.getTable("Details");
		ISqlJetTable bibleTable = db.getTable("Bible");
		Map<String, String> metadata = new HashMap<>();
		MetadataBook mdb = bible.getMetadataBook();
		if (mdb != null) {
			for (String key : mdb.getKeys()) {
				if (key.startsWith("MySword@"))
					metadata.put(key.substring(8).toLowerCase(), mdb.getValue(key));
			}
		}
		detailsTable.insert(bible.getName(), metadata.getOrDefault("description", ""), metadata.getOrDefault("abbreviation", System.getProperty("mysword.abbreviation", bible.getName().substring(0,1))), metadata.getOrDefault("comments", ""), metadata.getOrDefault("version", ""), metadata.getOrDefault("versiondate", ""), metadata.getOrDefault("publishdate", ""), metadata.get("publisher"), metadata.get("author"), metadata.get("creator"), metadata.get("source"), metadata.get("editorialcomments"), metadata.getOrDefault("language", "eng"), "0", "1", "1", hasStrongs ? "1" : "0", "");
		final Set<String> unsupportedFeatures = new HashSet<>();

		for (Book bk : bible.getBooks()) {
			int bnumber = bk.getId().getZefID();
			if (bnumber < 1 || bnumber > 66) {
				System.out.println("WARNING: Skipping unsupported book " + bk.getId());
				continue;
			}
			int[] verseCounts = StandardVersification.KJV.getVerseCount(bk.getId());
			if (bk.getChapters().size() > verseCounts.length) {
				System.out.println("WARNING: Only converted first " + verseCounts.length + " chapters of book " + bk.getId());
				bk.getChapters().subList(verseCounts.length, bk.getChapters().size()).clear();
			}
			for (int cnumber = 1; cnumber <= bk.getChapters().size(); cnumber++) {
				Chapter ch = bk.getChapters().get(cnumber - 1);
				int maxVerse = verseCounts[cnumber - 1];
				BitSet allowedNumbers = new BitSet(maxVerse + 1);
				allowedNumbers.set(1, maxVerse + 1);
				for (VirtualVerse vv : ch.createVirtualVerses(false, allowedNumbers, false)) {
					StringBuilder vb = new StringBuilder();
					MySwordVerseVisitor msvv = new MySwordVerseVisitor(vb, unsupportedFeatures, bk.getId().isNT());
					boolean first = true;
					for (Verse v : vv.getVerses()) {
						if (!first || !v.getNumber().equals("" + vv.getNumber())) {
							vb.append(" <b>(" + v.getNumber() + ")</b> ");
						}
						msvv.reset();
						v.accept(msvv);
						first = false;
					}
					bibleTable.insert(bnumber, cnumber, vv.getNumber(), vb.toString());
				}
			}
		}
		if (!unsupportedFeatures.isEmpty()) {
			System.out.println("WARNING: Skipped unsupported features: " + unsupportedFeatures);
		}
		db.commit();
		db.close();
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return false;
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	private static class MySwordVerseVisitor implements Visitor<IOException> {

		private final StringBuilder builder;
		private final List<String> suffixStack = new ArrayList<>();
		private final Set<String> unsupportedFeatures;
		private final boolean nt;

		public MySwordVerseVisitor(StringBuilder builder, Set<String> unsupportedFeatures, boolean nt) {
			this.builder = builder;
			this.unsupportedFeatures = unsupportedFeatures;
			this.nt = nt;
		}

		public void reset() {
			suffixStack.add("");
		}

		@Override
		public int visitElementTypes(String elementTypes) throws RuntimeException {
			return 0;
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws RuntimeException {
			builder.append("<TS" + (depth > 3 ? '3' : (char)('0' + depth)) + ">");
			suffixStack.add("<Ts>");
			return this;
		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			builder.append(text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"));
		}

		@Override
		public Visitor<IOException> visitFootnote() throws RuntimeException {
			builder.append("<RF>");
			suffixStack.add("<Rf>");
			return this;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			if (firstChapter != lastChapter || book.getZefID() < 1 || book.getZefID() > 66) {
				suffixStack.add("");
			} else {
				String ref = book.getZefID() + "." + firstChapter + "." + firstVerse + (lastVerse.equals(firstVerse) ? "" : "-" + lastVerse);
				builder.append("<a href=\"b" + ref + "\">");
				suffixStack.add("</a><RX" + ref + ">");
			}
			return this;
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			String prefix, suffix;
			switch (kind) {
			case UNDERLINE:
				prefix = "<FU>";
				suffix = "<Fu>";
				break;
			case ITALIC:
				prefix = "<FI>";
				suffix = "<Fi>";
				break;
			case WORDS_OF_JESUS:
				prefix = "<FR>";
				suffix = "<Fr>";
				break;
			default:
				prefix = "<span style=\"" + kind.getCss() + "\">";
				suffix = "</span>";
				break;
			}
			builder.append(prefix);
			suffixStack.add(suffix);
			return this;
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws RuntimeException {
			builder.append("<span style=\"" + css + "\">");
			suffixStack.add("</span>");
			return this;
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			visitText("/");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			builder.append("<CM>");
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws RuntimeException {
			int cnt = 0;
			String suffix = "";
			if (strongs != null)
				cnt = strongs.length;
			if (rmac != null)
				cnt = Math.max(cnt, rmac.length);
			for (int i = 0; i < cnt; i++) {
				if (strongs != null && i < strongs.length) {
					suffix += "<W" + Utils.formatStrongs(nt, i, strongsPrefixes, strongs) + ">";
				}
				if (rmac != null && i < rmac.length) {
					suffix += "<WT" + rmac[i] + ">";
				}
			}
			suffixStack.add(suffix);
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			builder.append("<a href=\"d-" + dictionary + " " + entry + "\">");
			suffixStack.add("</a>");
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			if (mode != RawHTMLMode.ONLINE)
				builder.append(raw);
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws RuntimeException {
			throw new RuntimeException("Variations not supported!");
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			unsupportedFeatures.add("extra attribute");
			Visitor<IOException> next = prio.handleVisitor(category, this);
			if (next == this)
				suffixStack.add("");
			return next;
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			builder.append(suffixStack.remove(suffixStack.size() - 1));
			return false;
		}
	}
}
