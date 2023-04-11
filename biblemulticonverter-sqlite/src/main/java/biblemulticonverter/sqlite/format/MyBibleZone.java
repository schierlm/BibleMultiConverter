package biblemulticonverter.sqlite.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.VirtualVerse;
import biblemulticonverter.format.AbstractHTMLVisitor;
import biblemulticonverter.format.AbstractNoCSSVisitor;
import biblemulticonverter.format.RoundtripFormat;
import biblemulticonverter.sqlite.SQLiteModuleRegistry;

public class MyBibleZone implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"MyBible.zone (Bible Reader for Android).",
			"",
			"Import: MyBibleZone <moduleName>.SQLite3",
			"Export: MyBibleZone <moduleName>.SQLite3 [<propertyfile>]",
			"",
			"Property file can be used for overriding values in the info table.",
			"In case of footnotes, a .commentaries.SQLite3 file is read/written automatically."
	};

	private static final MyBibleZoneBook[] BOOK_INFO = new MyBibleZoneBook[] {
			new MyBibleZoneBook(10, "#ccccff", BookID.BOOK_Gen),
			new MyBibleZoneBook(20, "#ccccff", BookID.BOOK_Exod),
			new MyBibleZoneBook(30, "#ccccff", BookID.BOOK_Lev),
			new MyBibleZoneBook(40, "#ccccff", BookID.BOOK_Num),
			new MyBibleZoneBook(50, "#ccccff", BookID.BOOK_Deut),
			new MyBibleZoneBook(60, "#ffcc99", BookID.BOOK_Josh),
			new MyBibleZoneBook(70, "#ffcc99", BookID.BOOK_Judg),
			new MyBibleZoneBook(80, "#ffcc99", BookID.BOOK_Ruth),
			new MyBibleZoneBook(90, "#ffcc99", BookID.BOOK_1Sam),
			new MyBibleZoneBook(100, "#ffcc99", BookID.BOOK_2Sam),
			new MyBibleZoneBook(110, "#ffcc99", BookID.BOOK_1Kgs),
			new MyBibleZoneBook(120, "#ffcc99", BookID.BOOK_2Kgs),
			new MyBibleZoneBook(180, "#ffcc99", BookID.BOOK_Jdt),
			new MyBibleZoneBook(130, "#ffcc99", BookID.BOOK_1Chr),
			new MyBibleZoneBook(140, "#ffcc99", BookID.BOOK_2Chr),
			new MyBibleZoneBook(145, "#66ff99", BookID.BOOK_PrMan),
			new MyBibleZoneBook(150, "#ffcc99", BookID.BOOK_Ezra),
			new MyBibleZoneBook(160, "#ffcc99", BookID.BOOK_Neh),
			new MyBibleZoneBook(165, "#ffcc99", BookID.BOOK_2Esd),
			new MyBibleZoneBook(170, "#ffcc99", BookID.BOOK_Tob),
			new MyBibleZoneBook(190, "#ffcc99", BookID.BOOK_Esth),
			new MyBibleZoneBook(192, "#c0c0c0", BookID.BOOK_AddEsth),
			new MyBibleZoneBook(220, "#66ff99", BookID.BOOK_Job),
			new MyBibleZoneBook(230, "#66ff99", BookID.BOOK_Ps),
			new MyBibleZoneBook(240, "#66ff99", BookID.BOOK_Prov),
			new MyBibleZoneBook(250, "#66ff99", BookID.BOOK_Eccl),
			new MyBibleZoneBook(260, "#66ff99", BookID.BOOK_Song),
			new MyBibleZoneBook(270, "#66ff99", BookID.BOOK_Wis),
			new MyBibleZoneBook(280, "#66ff99", BookID.BOOK_Sir),
			new MyBibleZoneBook(290, "#ff9fb4", BookID.BOOK_Isa),
			new MyBibleZoneBook(300, "#ff9fb4", BookID.BOOK_Jer),
			new MyBibleZoneBook(305, "#c0c0c0", BookID.BOOK_PrAzar),
			new MyBibleZoneBook(310, "#ff9fb4", BookID.BOOK_Lam),
			new MyBibleZoneBook(315, "#ff9fb4", BookID.BOOK_EpJer),
			new MyBibleZoneBook(320, "#ff9fb4", BookID.BOOK_Bar),
			new MyBibleZoneBook(323, "#c0c0c0", BookID.BOOK_AddDan),
			new MyBibleZoneBook(325, "#c0c0c0", BookID.BOOK_Sus),
			new MyBibleZoneBook(330, "#ff9fb4", BookID.BOOK_Ezek),
			new MyBibleZoneBook(340, "#ff9fb4", BookID.BOOK_Dan),
			new MyBibleZoneBook(345, "#c0c0c0", BookID.BOOK_Bel),
			new MyBibleZoneBook(350, "#ffff99", BookID.BOOK_Hos),
			new MyBibleZoneBook(360, "#ffff99", BookID.BOOK_Joel),
			new MyBibleZoneBook(370, "#ffff99", BookID.BOOK_Amos),
			new MyBibleZoneBook(380, "#ffff99", BookID.BOOK_Obad),
			new MyBibleZoneBook(390, "#ffff99", BookID.BOOK_Jonah),
			new MyBibleZoneBook(400, "#ffff99", BookID.BOOK_Mic),
			new MyBibleZoneBook(410, "#ffff99", BookID.BOOK_Nah),
			new MyBibleZoneBook(420, "#ffff99", BookID.BOOK_Hab),
			new MyBibleZoneBook(430, "#ffff99", BookID.BOOK_Zeph),
			new MyBibleZoneBook(440, "#ffff99", BookID.BOOK_Hag),
			new MyBibleZoneBook(450, "#ffff99", BookID.BOOK_Zech),
			new MyBibleZoneBook(460, "#ffff99", BookID.BOOK_Mal),
			new MyBibleZoneBook(462, "#d3d3d3", BookID.BOOK_1Macc),
			new MyBibleZoneBook(464, "#d3d3d3", BookID.BOOK_2Macc),
			new MyBibleZoneBook(466, "#d3d3d3", BookID.BOOK_3Macc),
			new MyBibleZoneBook(467, "#d3d3d3", BookID.BOOK_4Macc),
			new MyBibleZoneBook(468, "#d3d3d3", BookID.BOOK_1Esd),
			new MyBibleZoneBook(470, "#ff6600", BookID.BOOK_Matt),
			new MyBibleZoneBook(480, "#ff6600", BookID.BOOK_Mark),
			new MyBibleZoneBook(490, "#ff6600", BookID.BOOK_Luke),
			new MyBibleZoneBook(500, "#ff6600", BookID.BOOK_John),
			new MyBibleZoneBook(510, "#00ffff", BookID.BOOK_Acts),
			new MyBibleZoneBook(660, "#00ff00", BookID.BOOK_Jas),
			new MyBibleZoneBook(670, "#00ff00", BookID.BOOK_1Pet),
			new MyBibleZoneBook(680, "#00ff00", BookID.BOOK_2Pet),
			new MyBibleZoneBook(690, "#00ff00", BookID.BOOK_1John),
			new MyBibleZoneBook(700, "#00ff00", BookID.BOOK_2John),
			new MyBibleZoneBook(710, "#00ff00", BookID.BOOK_3John),
			new MyBibleZoneBook(720, "#00ff00", BookID.BOOK_Jude),
			new MyBibleZoneBook(520, "#ffff00", BookID.BOOK_Rom),
			new MyBibleZoneBook(530, "#ffff00", BookID.BOOK_1Cor),
			new MyBibleZoneBook(540, "#ffff00", BookID.BOOK_2Cor),
			new MyBibleZoneBook(550, "#ffff00", BookID.BOOK_Gal),
			new MyBibleZoneBook(560, "#ffff00", BookID.BOOK_Eph),
			new MyBibleZoneBook(570, "#ffff00", BookID.BOOK_Phil),
			new MyBibleZoneBook(580, "#ffff00", BookID.BOOK_Col),
			new MyBibleZoneBook(590, "#ffff00", BookID.BOOK_1Thess),
			new MyBibleZoneBook(600, "#ffff00", BookID.BOOK_2Thess),
			new MyBibleZoneBook(610, "#ffff00", BookID.BOOK_1Tim),
			new MyBibleZoneBook(620, "#ffff00", BookID.BOOK_2Tim),
			new MyBibleZoneBook(630, "#ffff00", BookID.BOOK_Titus),
			new MyBibleZoneBook(640, "#ffff00", BookID.BOOK_Phlm),
			new MyBibleZoneBook(650, "#ffff00", BookID.BOOK_Heb),
			new MyBibleZoneBook(730, "#ff7c80", BookID.BOOK_Rev),
			new MyBibleZoneBook(780, "#00ff00", BookID.BOOK_EpLao),
	};

	public static final Map<BookID, Integer> BOOK_NUMBERS = new EnumMap<>(BookID.class);

	static {
		for (MyBibleZoneBook bk : BOOK_INFO) {
			BOOK_NUMBERS.put(bk.bookID, bk.bookNumber);
		}
	}

	private boolean rawMorphology = Boolean.getBoolean("mybiblezone.morphology.raw");
	private boolean rawFootnotes = Boolean.getBoolean("mybiblezone.footnotes.raw");

	@Override
	public Bible doImport(File inputFile) throws Exception {
		SqlJetDb db = SQLiteModuleRegistry.openDB(inputFile, false);
		SqlJetDb footnoteDB = null;
		File footnoteFile = new File(inputFile.getParentFile(), inputFile.getName().replace(".SQLite3", ".commentaries.SQLite3"));
		if (inputFile.getName().endsWith(".SQLite3") && footnoteFile.exists()) {
			footnoteDB = SQLiteModuleRegistry.openDB(footnoteFile, false);
			if (!footnoteDB.getTable("commentaries").getIndexesNames().contains("commentaries_index")) {
				footnoteDB.close();
				footnoteDB = SQLiteModuleRegistry.openDB(footnoteFile, true);
				checkIndex(footnoteDB, "commentaries", "commentaries_index", "CREATE INDEX commentaries_index on commentaries(book_number, chapter_number_from, verse_number_from)");
			}
			footnoteDB.beginTransaction(SqlJetTransactionMode.READ_ONLY);
		}
		if (!db.getTable("verses").getIndexesNames().contains("versesIndex") || (db.getSchema().getTable("stories") != null && !db.getTable("stories").getIndexesNames().contains("stories_index"))) {
			db.close();
			db = SQLiteModuleRegistry.openDB(inputFile, true);
			checkIndex(db, "verses", "verses_index", "CREATE UNIQUE INDEX verses_index on verses (book_number, chapter, verse)");
			if (db.getSchema().getTable("stories") != null)
				if (db.getSchema().getTable("stories").getColumn("order_if_several") == null)
					checkIndex(db, "stories", "stories_index", "CREATE UNIQUE INDEX stories_index on stories(book_number, chapter, verse)");
				else
					checkIndex(db, "stories", "stories_index", "CREATE UNIQUE INDEX stories_index on stories(book_number, chapter, verse, order_if_several)");
		}
		db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
		String bibleName = null;
		MetadataBook mb = new MetadataBook();
		ISqlJetCursor cursor = db.getTable("info").open();
		while (!cursor.eof()) {
			String fn = cursor.getString("name");
			String fv = cursor.getString("value");
			if (fn.equals("description")) {
				bibleName = fv;
			} else if (!fv.isEmpty()) {
				fv = fv.replaceAll("[\r\n]+", "\n").replaceAll(" *\n *", "\n").replaceAll("\n$", "");
				try {
					mb.setValue("MyBible.zone@" + fn.replace('_', '.'), fv);
				} catch (IllegalArgumentException ex) {
					System.out.println("WARNING: Skipping malformed metadata property " + fn);
				}
			}
			cursor.next();
		}
		cursor.close();
		if (bibleName == null) {
			System.out.println("WARNING: No bible name in info table");
			bibleName = inputFile.getName();
		}
		Bible result = new Bible(bibleName.replaceAll("\\p{Cntrl}+", " ").replaceAll("\\s+", " ").trim());
		if (!mb.getKeys().isEmpty()) {
			mb.finished();
			result.getBooks().add(mb.getBook());
		}
		Map<Integer, Book> bookIDMap = new HashMap<>();
		cursor = db.getTable("books").open();
		while (!cursor.eof()) {
			int num = (int) cursor.getInteger("book_number");
			String col = cursor.getString("book_color");
			String shortName = cursor.getString("short_name").trim().replace(" ", "").replaceAll("[^A-Z0-9a-zäöü]++", "");
			if (!shortName.isEmpty())
				shortName = shortName.substring(0, 1).toUpperCase() + shortName.substring(1);
			String longName = cursor.getString("long_name").trim();
			BookID bid = null;
			for (MyBibleZoneBook bi : BOOK_INFO) {
				if (bi.bookNumber == num) {
					bid = bi.bookID;
					if (!col.equals(bi.bookColor))
						System.out.println("WARNING: Book " + bid.getOsisID() + " uses color " + col + " and not " + bi.bookColor);
				}
			}
			if (bid == null) {
				System.out.println("WARNING: Book number " + num + " unknown; skipping: " + shortName + "/" + longName);
				// generate dummy entry not stored in result object
				bookIDMap.put(num, new Book("Xxx", BookID.BOOK_Gen, "X", "X"));
			} else {
				if (shortName.length() < 2)
					shortName = bid.getOsisID().replaceAll("[^A-Z0-9a-zäöü]++", "");
				Book bk = new Book(shortName, bid, longName, longName);
				result.getBooks().add(bk);
				bookIDMap.put(num, bk);
			}
			cursor.next();
		}
		cursor.close();

		if (db.getSchema().getTable("introductions") != null) {
			cursor = db.getTable("introductions").open();
			while (!cursor.eof()) {
				int num = (int) cursor.getInteger("book_number");
				String intro = cursor.getString("introduction");
				Book bk;
				if (num == 0) {
					bk = new Book("Intro", BookID.INTRODUCTION, "_Introduction_", "_Introduction_");
					if (!result.getBooks().isEmpty() && result.getBooks().get(0).getId().equals(BookID.METADATA)) {
						result.getBooks().add(1, bk);
					} else {
						result.getBooks().add(0, bk);
					}
				} else {
					bk = bookIDMap.get(num);
				}
				if (bk == null) {
					System.out.println("WARNING: Skipping introduction for nonexisting book " + num);
				} else {
					FormattedText ft = new FormattedText();
					convertFromHTML(intro, ft.getAppendVisitor());
					ft.finished();
					if (bk.getChapters().isEmpty())
						bk.getChapters().add(new Chapter());
					bk.getChapters().get(0).setProlog(ft);
				}
				cursor.next();
			}
			cursor.close();
		}

		cursor = db.getTable("verses").order("verses_index");
		while (!cursor.eof()) {
			int b = (int) cursor.getInteger("book_number");
			int c = (int) cursor.getInteger("chapter");
			int v = (int) cursor.getInteger("verse");
			String text = cursor.getString("text");
			if (text == null)
				text = "";
			text = text.trim();
			if (!text.isEmpty()) {
				Book bk = bookIDMap.get(b);
				if (bk == null) {
					System.out.println("WARNING: Verse for unknown book " + b + " skipped");
				} else {
					while (bk.getChapters().size() < c)
						bk.getChapters().add(new Chapter());
					Chapter ch = bk.getChapters().get(c - 1);
					Verse vv = new Verse(v == 0 ? "1/t" : "" + v);
					try {
						String rest = convertFromVerse(text, vv.getAppendVisitor(), footnoteDB, new int[] { b, c, v }, bk.getId().isNT());
						if (!rest.isEmpty()) {
							System.out.println("WARNING: Treating tags as plaintext: " + rest);
							vv.getAppendVisitor().visitText(rest.replace('\t', ' ').replaceAll("  +", " "));
						}
					} catch (RuntimeException ex) {
						throw new RuntimeException(text, ex);
					}
					ch.getVerses().add(vv);
					vv.finished();
				}
			}
			cursor.next();
		}
		cursor.close();

		if (db.getSchema().getTable("stories") != null) {
			cursor = db.getTable("stories").order("stories_index");
			Map<Verse, List<FormattedText.Headline>> subheadings = new HashMap<>();
			Map<Verse, Chapter> subheadingChapters = new HashMap<>();
			while (!cursor.eof()) {
				int b = (int) cursor.getInteger("book_number");
				int c = (int) cursor.getInteger("chapter");
				int v = (int) cursor.getInteger("verse");
				String title = cursor.getString("title").replaceAll("[\0- ]+", " ").trim();
				Book bk = bookIDMap.get(b);
				if (bk == null) {
					System.out.println("WARNING: Subheading for unknown book " + b + " skipped");
				} else if (bk.getChapters().size() < c) {
					System.out.println("WARNING: Subheading for unknown chapter " + b + " " + c + " skipped");
				} else {
					Chapter ch = bk.getChapters().get(c - 1);
					Verse vv = null;
					for (Verse vvv : ch.getVerses()) {
						if (vvv.getNumber().equals(v == 0 ? "1/t" : "" + v))
							vv = vvv;
					}
					if (vv == null) {
						System.out.println("WARNING: Subheading for unknown verse " + b + " " + c + ":" + v + " skipped");
					} else {
						List<FormattedText.Headline> hls = subheadings.get(vv);
						if (hls == null) {
							hls = new ArrayList<>();
							subheadings.put(vv, hls);
							subheadingChapters.put(vv, ch);
						}
						Headline hl = new Headline(1);
						while (title.contains("<x>")) {
							int pos = title.indexOf("<x>");
							hl.getAppendVisitor().visitText(title.substring(0, pos));
							title = title.substring(pos + 3);
							pos = title.indexOf("</x>");
							if (pos == -1)
								System.out.println("WARNING: Unclosed cross reference: " + title);
							else {
								String ref = title.substring(0, pos);
								title = title.substring(pos + 4);
								hl.getAppendVisitor().visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText(ref);
							}
						}
						hl.getAppendVisitor().visitText(title);
						hl.finished();
						hls.add(hl);
					}
				}
				cursor.next();
			}
			cursor.close();
			for (Verse vv : subheadings.keySet()) {
				Chapter cc = subheadingChapters.get(vv);
				Verse vnew = new Verse(vv.getNumber());
				for (Headline hl : subheadings.get(vv)) {
					hl.accept(vnew.getAppendVisitor().visitHeadline(hl.getDepth()));
				}
				vv.accept(vnew.getAppendVisitor());
				vnew.finished();
				int pos = cc.getVerses().indexOf(vv);
				cc.getVerses().set(pos, vnew);
			}
		}
		if (footnoteDB != null) {
			footnoteDB.commit();
			footnoteDB.close();
		}
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

	private void convertFromHTML(String html, Visitor<RuntimeException> vv) {
		int pos = html.indexOf('<');
		while (pos != -1) {
			decodeEntities(vv, html.substring(0, pos));
			html = html.substring(pos);
			pos = html.indexOf('>');
			if (html.startsWith("<!--") && html.contains("-->"))
				pos = html.indexOf("-->") + 2;
			if (pos == -1)
				throw new RuntimeException(html);
			String tag = html.substring(0, pos + 1);
			html = html.substring(pos + 1);
			vv.visitRawHTML(RawHTMLMode.BOTH, tag.replace('\n', ' ').replaceAll("  +", " "));
			pos = html.indexOf('<');
		}
		decodeEntities(vv, html);
	}

	private void decodeEntities(Visitor<RuntimeException> vv, String text) {
		if (text.contains("\1") || text.contains("\2"))
			throw new RuntimeException(text);
		text = text.replace("&amp;", "\1").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
		text = text.replace("&#146;", "’").replace("&#147;", "“").replace("&#148;", "”");
		text = text.replace("&", "\2").replace("\1", "&").replace("\r\n", "\n").replace('\r', '\n').replaceAll("  +", " ");
		while (text.contains("\n") || text.contains("\2")) {
			int pos = text.indexOf('\n');
			int pos2 = text.indexOf('\2');
			if (pos2 != -1 && (pos == -1 || pos2 < pos)) {
				pos = text.indexOf(";", pos2);
				if (pos == -1) {
					System.out.println("WARNING: Unclosed HTML entity in " + text.substring(pos2));
					pos = pos2;
				}
				vv.visitText(text.substring(0, pos2).trim());
				vv.visitRawHTML(RawHTMLMode.BOTH, text.substring(pos2, pos + 1).replace('\2', '&'));
				text = text.substring(pos + 1).trim();
			} else {
				vv.visitText(text.substring(0, pos).trim());
				vv.visitLineBreak(LineBreakKind.NEWLINE);
				text = text.substring(pos + 1).trim();
			}
		}
		vv.visitText(text);
	}

	private String convertFromVerse(String text, Visitor<RuntimeException> vv, SqlJetDb footnoteDB, int[] vnums, boolean nt) {
		int pos = text.indexOf("<");
		while (pos != -1) {
			String strongsWord = "";
			if (text.startsWith("<S>", pos)) {
				strongsWord = text.substring(0, pos);
				int spacePos = strongsWord.lastIndexOf(' ');
				if (spacePos != -1) {
					vv.visitText(cleanText(strongsWord.substring(0, spacePos + 1)));
					strongsWord = strongsWord.substring(spacePos + 1);
				}
			} else {
				vv.visitText(cleanText(text.substring(0, pos)));
			}
			text = text.substring(pos);
			if (text.startsWith("<p>"))
				text = "<pb/>" + text.substring(3); // AT.SQLite3
			if (text.startsWith("<f>") && !rawFootnotes && footnoteDB == null) {
				System.out.println("WARNING: footnote(s) found but *.commentaries.SQLite3 file missing");
				rawFootnotes = true;
			}
			if (text.startsWith("<S>")) {
				pos = text.indexOf("</S>");
				String[] txt = cleanText(text.substring(3, pos)).split(",");
				char[] spfx = new char[txt.length];
				int[] snum = new int[txt.length];
				for (int i = 0; i < txt.length; i++) {
					try {
						if (txt[i].matches("[A-Z][0-9]+")) {
							spfx[i] = txt[i].charAt(0);
							snum[i] = Integer.parseInt(txt[i].substring(1));
						} else {
							spfx[i] = nt ? 'G' : 'H';
							snum[i] = Integer.parseInt(txt[i]);
						}
						if (snum[i] == 0) {
							System.out.println("WARNING: Strong number may not be zero");
							snum[i] = 99999;
						}
					} catch (NumberFormatException ex) {
						System.out.println("WARNING: Invalid Strong number: " + txt[i]);
						snum[i] = 99999;
					}
				}
				if (spfx.length > 0) {
					boolean allStandard = true;
					for (int i = 0; i < spfx.length; i++) {
						if (spfx[i] != (nt ? 'G' : 'H')) {
							allStandard = false;
							break;
						}
					}
					if (allStandard)
						spfx = new char[0];
				}
				String rmac = null;
				text = text.substring(pos + 4).replaceFirst("^ +<m>", "<m>");
				if (text.startsWith("<m>") && !rawMorphology) {
					pos = text.indexOf("</m>");
					rmac = cleanText(text.substring(3, pos));
					text = text.substring(pos + 4);
					if (!Utils.compilePattern(Utils.RMAC_REGEX).matcher(rmac).matches()) {
						System.out.println("WARNING: Skipping malformed RMAC morphology code: " + rmac);
						rmac = null;
					}
				}
				if (snum.length == 0 && rmac == null)
					vv.visitText(cleanText(strongsWord));
				else
					vv.visitGrammarInformation(spfx.length == 0 ? null : spfx, snum.length == 0 ? null : snum, rmac == null ? null : new String[] { rmac }, null).visitText(cleanText(strongsWord));
			} else if (text.startsWith("<n>")) {
				text = convertFromVerse(text.substring(3), vv.visitCSSFormatting("font-style: italic; myBibleType=note"), footnoteDB, vnums, nt);
				if (!text.startsWith("</n>"))
					System.out.println("WARNING: Unclosed <n> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<t>")) {
				vv.visitLineBreak(LineBreakKind.NEWLINE_WITH_INDENT);
				text = text.substring(3);
			} else if (text.startsWith("</t>")) {
				vv.visitLineBreak(LineBreakKind.NEWLINE);
				text = text.substring(4);
			} else if (text.startsWith("</")) {
				return text;
			} else if (text.startsWith("<pb/>")) {
				vv.visitLineBreak(LineBreakKind.PARAGRAPH);
				text = text.substring(5);
			} else if (text.startsWith("<br/>")) {
				vv.visitLineBreak(LineBreakKind.NEWLINE);
				text = text.substring(5);
			} else if ((text.startsWith("<m>") && rawMorphology) || (text.startsWith("<f>") && rawFootnotes)) {
				String tag = text.substring(1, 2);
				text = convertFromVerse(text.substring(3), vv.visitExtraAttribute(ExtraAttributePriority.SKIP, "mybiblezone", "rawtag", tag), footnoteDB, vnums, nt);
				if (!text.startsWith("</" + tag + ">"))
					System.out.println("WARNING: Unclosed <" + tag + "> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<m>")) {
				System.out.println("WARNING: Morph code without Strongs not supported");
				vv.visitText("<");
				text = text.substring(1);
			} else if (text.startsWith("<i>")) {
				text = convertFromVerse(text.substring(3), vv.visitFormattingInstruction(FormattingInstructionKind.ITALIC), footnoteDB, vnums, nt);
				if (!text.startsWith("</i>"))
					System.out.println("WARNING: Unclosed <i> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<J>")) {
				text = convertFromVerse(text.substring(3), vv.visitFormattingInstruction(FormattingInstructionKind.WORDS_OF_JESUS), footnoteDB, vnums, nt);
				if (!text.startsWith("</J>"))
					System.out.println("WARNING: Unclosed <J> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<e>")) {
				text = convertFromVerse(text.substring(3), vv.visitFormattingInstruction(FormattingInstructionKind.BOLD), footnoteDB, vnums, nt);
				if (!text.startsWith("</e>"))
					System.out.println("WARNING: Unclosed <e> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<h>")) {
				text = convertFromVerse(text.substring(3), vv.visitHeadline(1), footnoteDB, vnums, nt);
				if (!text.startsWith("</h>"))
					System.out.println("WARNING: Unclosed <e> tag at: " + text);
				else {
					text = text.substring(4);
				}
			} else if (text.startsWith("<f>")) {
				pos = text.indexOf("</f>");
				String fn = cleanText(text.substring(3, pos));
				text = text.substring(pos + 4);
				if (!fn.matches("\\[\u2020?\\*?[0-9-:]+\\]|[\u24D0-\u24E9\u2780-\u2793]"))
					System.out.println("WARNING: Unusual footnote mark: " + fn);
				try {
					ISqlJetCursor cursor = footnoteDB.getTable("commentaries").lookup("commentaries_index", vnums[0], vnums[1], vnums[2]);
					String html = null;
					while (!cursor.eof()) {
						String marker = cursor.getString("marker");
						if (marker.equals(fn)) {
							html = cursor.getString("text");
						}
						cursor.next();
					}
					cursor.close();
					if (html == null)
						System.out.println("WARNING: Footnote text " + fn + " not found in " + vnums[0] + " " + vnums[1] + ":" + vnums[2]);
					else
						convertFromHTML(html, vv.visitFootnote());
				} catch (SqlJetException ex) {
					throw new RuntimeException(text, ex);
				}
			} else {
				System.out.println("WARNING: Unknown tag, treated as plain text: " + text);
				vv.visitText("<");
				text = text.substring(1);
			}
			pos = text.indexOf("<");
		}
		vv.visitText(cleanText(text));
		return "";
	}

	private String cleanText(String text) {
		if (text.contains("<"))
			throw new RuntimeException(text);
		return text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').replaceAll("  +", " ");
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String outfile = exportArgs[0];
		if (!outfile.endsWith(".SQLite3"))
			outfile += ".SQLite3";
		boolean hasFootnotes = false, hasStrongs = false;
		for (Book bk : bible.getBooks()) {
			for (Chapter ch : bk.getChapters()) {
				for (Verse vv : ch.getVerses()) {
					String elementTypes = vv.getElementTypes(Integer.MAX_VALUE);
					if (elementTypes.contains("f")) {
						hasFootnotes = true;
					}
					if (elementTypes.contains("g")) {
						hasStrongs = true;
					}
				}
			}
		}
		new File(outfile).delete();
		SqlJetDb db = SQLiteModuleRegistry.openDB(new File(outfile), true);
		db.getOptions().setAutovacuum(true);
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		db.getOptions().setUserVersion(0);
		db.createTable("CREATE TABLE info (name TEXT, value TEXT)");
		db.createTable("CREATE TABLE books (book_number NUMERIC, book_color TEXT, short_name TEXT, long_name TEXT)");
		db.createTable("CREATE TABLE introductions (book_number NUMERIC, introduction TEXT)");
		db.createIndex("CREATE UNIQUE INDEX introductions_index on introductions(book_number)");
		db.createTable("CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)");
		db.createIndex("CREATE UNIQUE INDEX verses_index on verses (book_number, chapter, verse)");
		db.createTable("CREATE TABLE stories (book_number NUMERIC, chapter NUMERIC, verse NUMERIC, order_if_several NUMERIC, title TEXT)");
		db.createIndex("CREATE UNIQUE INDEX stories_index on stories(book_number, chapter, verse, order_if_several)");
		Map<String, String> infoValues = new LinkedHashMap<>();
		MetadataBook mb = bible.getMetadataBook();
		if (mb == null)
			mb = new MetadataBook();
		infoValues.put("language", "xx");
		infoValues.put("description", bible.getName());
		infoValues.put("detailed_info", "");
		infoValues.put("russian_numbering", "false");
		infoValues.put("chapter_string", "Chapter");
		infoValues.put("introduction_string", "Introduction");
		infoValues.put("strong_numbers", hasStrongs ? "true" : "false");
		infoValues.put("right_to_left", "false");
		infoValues.put("digits0-9", "0123456789");
		infoValues.put("swaps_non_localized_words_in_mixed_language_line", "false");
		infoValues.put("localized_book_abbreviations", "false");
		infoValues.put("font_scale", "1.0");
		infoValues.put("contains_accents", "true");
		for (String mbkey : mb.getKeys()) {
			if (mbkey.startsWith("MyBible.zone@")) {
				infoValues.put(mbkey.substring(13).replace('.', '_'), mb.getValue(mbkey));
			} else {
				infoValues.put("detailed_info", infoValues.get("detailed_info") + "\r\n<br><b>" + mbkey + ":</b>" + mb.getValue(mbkey));
			}
		}
		String bibleIntro = null, singleFootnoteMarker = null, singleXrefMarker = null;
		if (exportArgs.length > 1) {
			Properties props = new Properties();
			FileInputStream in = new FileInputStream(exportArgs[1]);
			props.load(in);
			in.close();
			bibleIntro = (String) props.remove("__INTRODUCTION__");
			singleFootnoteMarker = (String) props.remove("__FOOTNOTE_MARKER__");
			singleXrefMarker = (String) props.remove("__XREF_MARKER__");
			for (Object key : props.keySet()) {
				String template = props.getProperty(key.toString());
				template = template.replace("${name}", bible.getName());
				for (String mbkey : mb.getKeys())
					template = template.replace("${" + mbkey + "}", mb.getValue(mbkey));
				infoValues.put(key.toString(), template);
			}
		}
		ISqlJetTable infoTable = db.getTable("info");
		ISqlJetTable booksTable = db.getTable("books");
		ISqlJetTable introductionsTable = db.getTable("introductions");
		ISqlJetTable versesTable = db.getTable("verses");
		ISqlJetTable storiesTable = db.getTable("stories");
		for (Map.Entry<String, String> entry : infoValues.entrySet()) {
			infoTable.insert(entry.getKey(), entry.getValue());
		}
		SqlJetDb cdb = null;
		ISqlJetTable footnotesTable = null;
		if (hasFootnotes) {
			String commentaryfile = outfile.replace(".SQLite3", ".commentaries.SQLite3");
			new File(commentaryfile).delete();
			cdb = SQLiteModuleRegistry.openDB(new File(commentaryfile), true);
			cdb.getOptions().setAutovacuum(true);
			cdb.beginTransaction(SqlJetTransactionMode.WRITE);
			cdb.getOptions().setUserVersion(0);
			cdb.createTable("CREATE TABLE info (name TEXT, value TEXT)");
			cdb.createTable("CREATE TABLE commentaries (book_number NUMERIC, chapter_number_from NUMERIC, verse_number_from NUMERIC, chapter_number_to NUMERIC, verse_number_to NUMERIC, marker TEXT, text TEXT )");
			cdb.createIndex("CREATE INDEX commentaries_index on commentaries(book_number, chapter_number_from, verse_number_from)");
			ISqlJetTable cInfoTable = cdb.getTable("info");
			for (String key : Arrays.asList("language", "description", "russian_numbering")) {
				cInfoTable.insert(key, infoValues.get(key));
			}
			cInfoTable.insert("is_footnotes", "true");
			footnotesTable = cdb.getTable("commentaries");
		}
		final Set<String> unsupportedFeatures = new HashSet<>();
		FormattedText introProlog = null;
		for (Book bk : bible.getBooks()) {
			if (bk.getId() == BookID.INTRODUCTION || bk.getId() == BookID.INTRODUCTION_OT || bk.getId() == BookID.INTRODUCTION_NT || bk.getId() == BookID.APPENDIX) {
				if (introProlog == null)
					introProlog = new FormattedText();
				introProlog.getAppendVisitor().visitHeadline(1).visitText(bk.getLongName());
				bk.getChapters().get(0).getProlog().accept(introProlog.getAppendVisitor());
				continue;
			}
			MyBibleZoneBook info = null;
			for (MyBibleZoneBook bi : BOOK_INFO) {
				if (bi.bookID == bk.getId())
					info = bi;
			}
			if (info == null) {
				System.out.println("WARNING: Skipping unsupported book " + bk.getId());
				continue;
			}
			booksTable.insert(info.bookNumber, info.bookColor, bk.getAbbr(), bk.getShortName());
			FormattedText prologs = null;
			for (int cn = 1; cn <= bk.getChapters().size(); cn++) {
				Chapter ch = bk.getChapters().get(cn - 1);
				if (ch.getProlog() != null) {
					if (prologs == null)
						prologs = new FormattedText();
					prologs.getAppendVisitor().visitHeadline(1).visitText(cn == 1 ? bk.getLongName() : "" + cn);
					ch.getProlog().accept(prologs.getAppendVisitor());
				}
				int vn = -1;
				for (VirtualVerse vv : ch.createVirtualVerses(true, true)) {
					if (vn == -1 && vv.getNumber() != 0) vn = 0;
					vn++;
					while (vn < vv.getNumber())
						versesTable.insert(info.bookNumber, cn, vn++, "");
					if (vn != vv.getNumber())
						throw new RuntimeException(vn + " != " + vv.getNumber());
					for (int hl = 0; hl < vv.getHeadlines().size(); hl++) {
						final StringBuilder sb = new StringBuilder();
						final Map<StringBuilder, String> xrefTags = new HashMap<>();
						vv.getHeadlines().get(hl).accept(new VisitorAdapter<RuntimeException>(null) {
							@Override
							protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
								return this;
							}

							@Override
							protected void beforeVisit() throws RuntimeException {
								unsupportedFeatures.add("markup in headline");
							}

							@Override
							public void visitText(String text) throws RuntimeException {
								sb.append(text.replace('<', '〈').replace('>', '〉'));
							}

							@Override
							public Visitor<RuntimeException> visitFootnote() throws RuntimeException {
								// handle this separately; we do not like
								// footnote text inside the headline!
								unsupportedFeatures.add("footnote in headline");
								return new VisitorAdapter<RuntimeException>(null) {
									@Override
									protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
										return this;
									}

									@Override
									public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
										if (!BOOK_NUMBERS.containsKey(book))
											return null;
										final StringBuilder innerBuilder = new StringBuilder();
										String endVerse = firstChapter != lastChapter ? "-" + lastChapter + ":" + lastVerse : !firstVerse.equals(lastVerse) ? "-" + lastVerse : "";
										xrefTags.put(innerBuilder, "<x>" + BOOK_NUMBERS.get(book) + " " + firstChapter + ":" + firstVerse + endVerse + "</x>");
										return new VisitorAdapter<RuntimeException>(null) {
											@Override
											protected void beforeVisit() throws RuntimeException {
												throw new RuntimeException("Unsupported content inside headline xref");
											}

											@Override
											public void visitText(String text) throws RuntimeException {
												innerBuilder.append(text.replace('<', '〈').replace('>', '〉'));
											}
										};
									}
								};
							}

							@Override
							public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
								unsupportedFeatures.add("extra atrribute in headline");
								return prio.handleVisitor(category, this);
							}
						});
						String headline = sb.toString();
						for (Map.Entry<StringBuilder, String> xrefTag : xrefTags.entrySet()) {
							headline = headline.replace(xrefTag.getKey().toString(), xrefTag.getValue());
						}
						storiesTable.insert(info.bookNumber, cn, vn, hl, headline);
					}
					StringBuilder vb = new StringBuilder();
					Map<String, MyBibleHTMLVisitor> footnotes = new HashMap<>();
					MyBibleVerseVisitor mbvv = new MyBibleVerseVisitor(vb, footnotes, unsupportedFeatures, bk.getId().isNT());
					boolean first = true;
					for (Verse v : vv.getVerses()) {
						if (!first || !v.getNumber().equals("" + vv.getNumber()) && !(v.getNumber().equals("1/t") && vv.getNumber() == 0)) {
							vb.append(" <e>(" + v.getNumber() + ")</e> ");
						}
						mbvv.reset();
						v.accept(mbvv);
						first = false;
					}
					if (singleXrefMarker != null || singleFootnoteMarker != null) {
						String singleXref = null, singleFootnote = null;
						for (Map.Entry<String, MyBibleHTMLVisitor> fn : footnotes.entrySet()) {
							if (!fn.getKey().matches("\\[[0-9]+\\]"))
								continue;
							if (fn.getValue().getResult().startsWith(FormattedText.XREF_MARKER) && singleXrefMarker != null) {
								if (singleXref == null) {
									singleXref = fn.getKey();
								} else {
									System.out.println("WARNING: More than one XREF footnote in verse " + info.bookID + " " + cn + ":" + vn);
									singleXref = "-";
								}
							} else if (singleFootnoteMarker != null) {
								if (singleFootnote == null) {
									singleFootnote = fn.getKey();
								} else {
									System.out.println("WARNING: More than one normal footnote in verse " + info.bookID + " " + cn + ":" + vn);
									singleFootnote = "-";
								}
							}
						}
						if (singleXref != null && !singleXref.equals("-")) {
							MyBibleHTMLVisitor xfn = footnotes.remove(singleXref);
							if (xfn == null)
								throw new RuntimeException();
							footnotes.put(singleXrefMarker, xfn);
							String verse = vb.toString();
							vb.setLength(0);
							vb.append(verse.replace("<f>" + singleXref + "</f>", "<f>" + singleXrefMarker + "</f>"));
						}
						if (singleFootnote != null && !singleFootnote.equals("-")) {
							MyBibleHTMLVisitor sfn = footnotes.remove(singleFootnote);
							if (sfn == null)
								throw new RuntimeException();
							footnotes.put(singleFootnoteMarker, sfn);
							String verse = vb.toString();
							vb.setLength(0);
							vb.append(verse.replace("<f>" + singleFootnote + "</f>", "<f>" + singleFootnoteMarker + "</f>"));
						}
					}
					for (Map.Entry<String, MyBibleHTMLVisitor> fn : footnotes.entrySet()) {
						footnotesTable.insert(info.bookNumber, cn, vn, cn, vn, fn.getKey(), fn.getValue().getResult());
					}
					versesTable.insert(info.bookNumber, cn, vn, vb.toString().trim());
				}
			}
			if (prologs != null) {
				MyBibleHTMLVisitor v = new MyBibleHTMLVisitor(unsupportedFeatures, "in introduction");
				prologs.accept(v);
				introductionsTable.insert(info.bookNumber, v.getResult());
			}
		}

		if (bibleIntro != null) {
			introductionsTable.insert(0, bibleIntro);
		} else if (introProlog != null) {
			MyBibleHTMLVisitor v = new MyBibleHTMLVisitor(unsupportedFeatures, "in introduction");
			introProlog.accept(v);
			introductionsTable.insert(0, v.getResult());
		}
		if (!unsupportedFeatures.isEmpty()) {
			System.out.println("WARNING: Skipped unsupported features: " + unsupportedFeatures);
		}
		db.commit();
		db.close();
		if (cdb != null) {
			cdb.commit();
			cdb.close();
		}
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return false;
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	private static class MyBibleZoneBook {
		private final int bookNumber;
		private final String bookColor;
		private final BookID bookID;

		public MyBibleZoneBook(int bookNumber, String bookColor, BookID bookID) {
			this.bookNumber = bookNumber;
			this.bookColor = bookColor;
			this.bookID = bookID;
		}
	}

	protected static class MyBibleHTMLVisitor extends AbstractHTMLVisitor {
		private final Set<String> unsupportedFeatures;
		private final String locationText;

		public MyBibleHTMLVisitor(Set<String> unsupportedFeatures, String locationText) {
			super(new StringWriter(), "");
			this.unsupportedFeatures = unsupportedFeatures;
			this.locationText = locationText;
		}

		public String getResult() {
			return writer.toString().trim();
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			writer.write("<h" + depth + ">");
			pushSuffix("</h" + depth + ">");
			return this;
		}

		@Override
		public Visitor<IOException> visitFootnote() throws IOException {
			unsupportedFeatures.add("footnote " + locationText);
			return null;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws IOException {
			if (!BOOK_NUMBERS.containsKey(book)) {
				System.out.println("WARNING: cross reference to unknown book " + book);
				pushSuffix("");
			} else {
				writer.write("<a href=\"B:" + BOOK_NUMBERS.get(book) + " " + firstChapter + ":" + firstVerse + "\">");
				pushSuffix("</a>");
			}
			return this;
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws IOException {
			if (kind == LineBreakKind.NEWLINE_WITH_INDENT)
				unsupportedFeatures.add("indentation " + locationText);
			if (kind == LineBreakKind.PARAGRAPH)
				writer.write("<p>");
			else
				writer.write("<br>");
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, String[] rmac, int[] sourceIndices) throws IOException {
			unsupportedFeatures.add("grammar information " + locationText);
			pushSuffix("");
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			writer.write("<a href=\"S:[" + dictionary + "]" + entry + "\">");
			pushSuffix("</a>");
			return this;
		}
	}

	private static class MyBibleVerseVisitor extends AbstractNoCSSVisitor<IOException> {

		private final StringBuilder builder;
		private final List<String> suffixStack = new ArrayList<>();
		private final Set<String> unsupportedFeatures;
		private final Map<String, MyBibleHTMLVisitor> footnotes;
		private final boolean nt;
		private int lastFootnote = 0;
		private int lastDictionaryFootnote = 0;

		public MyBibleVerseVisitor(StringBuilder builder, Map<String, MyBibleHTMLVisitor> footnotes, Set<String> unsupportedFeatures, boolean nt) {
			this.builder = builder;
			this.footnotes = footnotes;
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
			throw new RuntimeException("Headlines may not exist in virtual verses");
		}

		@Override
		public void visitStart() throws RuntimeException {
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			String lastSuffix = suffixStack.get(suffixStack.size() - 1);
			if (lastSuffix.equals("--divine-name--")) {
				for (int i = 0; i < text.length(); i++) {
					char ch = text.charAt(i);
					if (ch >= 'a' && ch <= 'z' && ch != 'q' && ch != 'x') {
						ch = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘ-ʀꜱᴛᴜᴠᴡ-ʏᴢ".charAt(ch - 'a');
					} else if (ch == '<') {
						ch = '〈';
					} else if (ch == '>') {
						ch = '〉';
					} else if (Character.isLowerCase(ch)) {
						System.out.println("WARNING: Unable to create DIVINE_NAME character for " + ch);
					}
					builder.append(ch);
				}
			} else {
				builder.append(text.replace('<', '〈').replace('>', '〉'));
			}
		}

		@Override
		public Visitor<IOException> visitFootnote() throws RuntimeException {
			lastFootnote++;
			MyBibleHTMLVisitor fnv = new MyBibleHTMLVisitor(unsupportedFeatures, "in footnote");
			footnotes.put("[" + lastFootnote + "]", fnv);
			builder.append("<f>[" + lastFootnote + "]</f>");
			return fnv;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
			unsupportedFeatures.add("xref in verse");
			return null;
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
			String prefix, suffix;
			switch (kind) {
			case BOLD:
				prefix = "<e>";
				suffix = "</e>";
				break;
			case ITALIC:
				prefix = "<i>";
				suffix = "</i>";
				break;
			case WORDS_OF_JESUS:
				prefix = "<J>";
				suffix = "</J>";
				break;
			case DIVINE_NAME:
				prefix = "";
				suffix = "--divine-name--";
				break;
			default:
				unsupportedFeatures.add("formatting " + kind.toString() + " in verse");
				prefix = suffix = "";
				break;
			}
			builder.append(prefix);
			suffixStack.add(suffix);
			return this;
		}

		@Override
		protected Visitor<IOException> visitChangedCSSFormatting(String remainingCSS, Visitor<IOException> resultingVisitor, int replacements) {
			if (!remainingCSS.isEmpty())
				unsupportedFeatures.add("css formatting in verse");
			if (replacements != 1) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < replacements; i++) {
					String lastSuffix = suffixStack.remove(suffixStack.size() - 1);
					if (!lastSuffix.equals("--divine-name--"))
						sb.append(lastSuffix);
				}
				suffixStack.add(sb.toString());
			}
			return resultingVisitor;
		}

		@Override
		public void visitVerseSeparator() throws RuntimeException {
			visitText("/");
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) throws RuntimeException {
			String lastSuffix = suffixStack.get(suffixStack.size() - 1);
			boolean indentClosed = false;
			if (lastSuffix.startsWith("</t>")) {
				builder.append("</t>");
				lastSuffix = lastSuffix.substring(4);
				indentClosed = true;
			}
			if (kind == LineBreakKind.NEWLINE_WITH_INDENT) {
				builder.append("<t>");
				lastSuffix = "</t>" + lastSuffix;
			} else if (kind == LineBreakKind.PARAGRAPH) {
				builder.append("<pb/>");
			} else if (!indentClosed) {
				builder.append("<br/>");
			}
			suffixStack.set(suffixStack.size() - 1, lastSuffix);
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
					suffix += "<S>" + (strongsPrefixes == null || strongsPrefixes[i] == (nt ? 'G' : 'H') ? "" : "" + strongsPrefixes[i]) + strongs[i] + "</S>";
				}
				if (rmac != null && i < rmac.length) {
					suffix += "<m>" + rmac[i] + "</m>";
				}
			}
			suffixStack.add(suffix);
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			lastDictionaryFootnote++;
			MyBibleHTMLVisitor fnv = new MyBibleHTMLVisitor(unsupportedFeatures, "in dictionary footnote");
			footnotes.put("[\u2197" + lastDictionaryFootnote + "]", fnv);
			Visitor<IOException> dv = fnv.visitDictionaryEntry(dictionary, entry);
			dv.visitText("\u2197");
			dv.visitEnd();
			fnv.visitEnd();
			suffixStack.add("<f>[\u2197" + lastDictionaryFootnote + "]</f>");
			return this;
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws RuntimeException {
			unsupportedFeatures.add("raw HTML in verse");
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws RuntimeException {
			throw new RuntimeException("Variations not supported!");
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			if (category.equals("mybiblezone") && key.equals("rawtag")) {
				builder.append("<" + value + ">");
				suffixStack.add("</" + value + ">");
				return this;
			}
			unsupportedFeatures.add("extra attribute in verse");
			Visitor<IOException> next = prio.handleVisitor(category, this);
			if (next == this)
				suffixStack.add("");
			return next;
		}

		@Override
		public boolean visitEnd() throws RuntimeException {
			String lastSuffix = suffixStack.remove(suffixStack.size() - 1);
			if (!lastSuffix.equals("--divine-name--"))
				builder.append(lastSuffix);
			return false;
		}
	}
}
