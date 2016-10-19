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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import biblemulticonverter.data.Utils;
import biblemulticonverter.data.MetadataBook.MetadataBookKey;
import biblemulticonverter.data.Verse;

/**
 * Importer and exporter for Unbound Bible.
 */
public class UnboundBible implements RoundtripFormat {

	public static final String[] HELP_TEXT = {
			"Bible format downloadable from Biola University",
			"",
			"Usage (export): UnboundBible <outfile> [<mappingfile>]",
			"",
			"If the optional mapping file is present, it is used to create a",
			"Mapped-BCVS type file, else an Unmapped-BCVS is created.",
			"If the system property unbound.parsed is set, \"parsed grammar",
			"information\" is converted to BibleMultiConverter's native format",
			"(on import) or back (on export)"
	};

	private static final UnboundBibleBookInfo[] BOOK_INFO = {
			new UnboundBibleBookInfo("01O", BookID.BOOK_Gen, "Genesis"),
			new UnboundBibleBookInfo("02O", BookID.BOOK_Exod, "Exodus"),
			new UnboundBibleBookInfo("03O", BookID.BOOK_Lev, "Leviticus"),
			new UnboundBibleBookInfo("04O", BookID.BOOK_Num, "Numbers"),
			new UnboundBibleBookInfo("05O", BookID.BOOK_Deut, "Deuteronomy"),
			new UnboundBibleBookInfo("06O", BookID.BOOK_Josh, "Joshua"),
			new UnboundBibleBookInfo("07O", BookID.BOOK_Judg, "Judges"),
			new UnboundBibleBookInfo("08O", BookID.BOOK_Ruth, "Ruth"),
			new UnboundBibleBookInfo("09O", BookID.BOOK_1Sam, "1 Samuel"),
			new UnboundBibleBookInfo("10O", BookID.BOOK_2Sam, "2 Samuel"),
			new UnboundBibleBookInfo("11O", BookID.BOOK_1Kgs, "1 Kings"),
			new UnboundBibleBookInfo("12O", BookID.BOOK_2Kgs, "2 Kings"),
			new UnboundBibleBookInfo("13O", BookID.BOOK_1Chr, "1 Chronicles"),
			new UnboundBibleBookInfo("14O", BookID.BOOK_2Chr, "2 Chronicles"),
			new UnboundBibleBookInfo("15O", BookID.BOOK_Ezra, "Ezra"),
			new UnboundBibleBookInfo("16O", BookID.BOOK_Neh, "Nehemiah"),
			new UnboundBibleBookInfo("17O", BookID.BOOK_Esth, "Esther"),
			new UnboundBibleBookInfo("18O", BookID.BOOK_Job, "Job"),
			new UnboundBibleBookInfo("19O", BookID.BOOK_Ps, "Psalms"),
			new UnboundBibleBookInfo("20O", BookID.BOOK_Prov, "Proverbs"),
			new UnboundBibleBookInfo("21O", BookID.BOOK_Eccl, "Ecclesiastes"),
			new UnboundBibleBookInfo("22O", BookID.BOOK_Song, "Song of Solomon"),
			new UnboundBibleBookInfo("23O", BookID.BOOK_Isa, "Isaiah"),
			new UnboundBibleBookInfo("24O", BookID.BOOK_Jer, "Jeremiah"),
			new UnboundBibleBookInfo("25O", BookID.BOOK_Lam, "Lamentations"),
			new UnboundBibleBookInfo("26O", BookID.BOOK_Ezek, "Ezekiel"),
			new UnboundBibleBookInfo("27O", BookID.BOOK_Dan, "Daniel"),
			new UnboundBibleBookInfo("28O", BookID.BOOK_Hos, "Hosea"),
			new UnboundBibleBookInfo("29O", BookID.BOOK_Joel, "Joel"),
			new UnboundBibleBookInfo("30O", BookID.BOOK_Amos, "Amos"),
			new UnboundBibleBookInfo("31O", BookID.BOOK_Obad, "Obadiah"),
			new UnboundBibleBookInfo("32O", BookID.BOOK_Jonah, "Jonah"),
			new UnboundBibleBookInfo("33O", BookID.BOOK_Mic, "Micah"),
			new UnboundBibleBookInfo("34O", BookID.BOOK_Nah, "Nahum"),
			new UnboundBibleBookInfo("35O", BookID.BOOK_Hab, "Habakkuk"),
			new UnboundBibleBookInfo("36O", BookID.BOOK_Zeph, "Zephaniah"),
			new UnboundBibleBookInfo("37O", BookID.BOOK_Hag, "Haggai"),
			new UnboundBibleBookInfo("38O", BookID.BOOK_Zech, "Zechariah"),
			new UnboundBibleBookInfo("39O", BookID.BOOK_Mal, "Malachi"),
			new UnboundBibleBookInfo("40N", BookID.BOOK_Matt, "Matthew"),
			new UnboundBibleBookInfo("41N", BookID.BOOK_Mark, "Mark"),
			new UnboundBibleBookInfo("42N", BookID.BOOK_Luke, "Luke"),
			new UnboundBibleBookInfo("43N", BookID.BOOK_John, "John"),
			new UnboundBibleBookInfo("44N", BookID.BOOK_Acts, "Acts of the Apostles"),
			new UnboundBibleBookInfo("45N", BookID.BOOK_Rom, "Romans"),
			new UnboundBibleBookInfo("46N", BookID.BOOK_1Cor, "1 Corinthians"),
			new UnboundBibleBookInfo("47N", BookID.BOOK_2Cor, "2 Corinthians"),
			new UnboundBibleBookInfo("48N", BookID.BOOK_Gal, "Galatians"),
			new UnboundBibleBookInfo("49N", BookID.BOOK_Eph, "Ephesians"),
			new UnboundBibleBookInfo("50N", BookID.BOOK_Phil, "Philippians"),
			new UnboundBibleBookInfo("51N", BookID.BOOK_Col, "Colossians"),
			new UnboundBibleBookInfo("52N", BookID.BOOK_1Thess, "1 Thessalonians"),
			new UnboundBibleBookInfo("53N", BookID.BOOK_2Thess, "2 Thessalonians"),
			new UnboundBibleBookInfo("54N", BookID.BOOK_1Tim, "1 Timothy"),
			new UnboundBibleBookInfo("55N", BookID.BOOK_2Tim, "2 Timothy"),
			new UnboundBibleBookInfo("56N", BookID.BOOK_Titus, "Titus"),
			new UnboundBibleBookInfo("57N", BookID.BOOK_Phlm, "Philemon"),
			new UnboundBibleBookInfo("58N", BookID.BOOK_Heb, "Hebrews"),
			new UnboundBibleBookInfo("59N", BookID.BOOK_Jas, "James"),
			new UnboundBibleBookInfo("60N", BookID.BOOK_1Pet, "1 Peter"),
			new UnboundBibleBookInfo("61N", BookID.BOOK_2Pet, "2 Peter"),
			new UnboundBibleBookInfo("62N", BookID.BOOK_1John, "1 John"),
			new UnboundBibleBookInfo("63N", BookID.BOOK_2John, "2 John"),
			new UnboundBibleBookInfo("64N", BookID.BOOK_3John, "3 John"),
			new UnboundBibleBookInfo("65N", BookID.BOOK_Jude, "Jude"),
			new UnboundBibleBookInfo("66N", BookID.BOOK_Rev, "Revelation"),
			new UnboundBibleBookInfo("67A", BookID.BOOK_Tob, "Tobit"),
			new UnboundBibleBookInfo("68A", BookID.BOOK_Jdt, "Judith"),
			new UnboundBibleBookInfo("69A", BookID.BOOK_EsthGr, "Esther, Greek"),
			new UnboundBibleBookInfo("70A", BookID.BOOK_Wis, "Wisdom of Solomon"),
			new UnboundBibleBookInfo("71A", BookID.BOOK_Sir, "Ecclesiasticus (Sira)"),
			new UnboundBibleBookInfo("72A", BookID.BOOK_Bar, "Baruch"),
			new UnboundBibleBookInfo("73A", BookID.BOOK_EpJer, "Epistle of Jeremiah"),
			new UnboundBibleBookInfo("74A", BookID.BOOK_PrAzar, "Prayer of Azariah"),
			new UnboundBibleBookInfo("75A", BookID.BOOK_Sus, "Susanna"),
			new UnboundBibleBookInfo("76A", BookID.BOOK_Bel, "Bel and the Dragon"),
			new UnboundBibleBookInfo("77A", BookID.BOOK_1Macc, "1 Maccabees"),
			new UnboundBibleBookInfo("78A", BookID.BOOK_2Macc, "2 Maccabees"),
			new UnboundBibleBookInfo("79A", BookID.BOOK_3Macc, "3 Maccabees"),
			new UnboundBibleBookInfo("80A", BookID.BOOK_4Macc, "4 Maccabees"),
			new UnboundBibleBookInfo("81A", BookID.BOOK_1Esd, "1 Esdras"),
			new UnboundBibleBookInfo("82A", BookID.BOOK_2Esd, "2 Esdras"),
			new UnboundBibleBookInfo("83A", BookID.BOOK_PrMan, "Prayer of Manasseh"),
			new UnboundBibleBookInfo("84A", BookID.BOOK_AddPs, "Psalm 151"),
			new UnboundBibleBookInfo("85A", BookID.BOOK_PssSol, "Psalm of Solomon"),
			new UnboundBibleBookInfo("86A", BookID.BOOK_Odes, "Odes"),
	};

	private static Map<BookID, UnboundBibleBookInfo> BOOK_INFO_BY_ID = new EnumMap<>(BookID.class);
	private static Map<String, UnboundBibleBookInfo> BOOK_INFO_BY_CODE = new HashMap<>();

	static {
		for (UnboundBibleBookInfo bi : BOOK_INFO) {
			BOOK_INFO_BY_ID.put(bi.id, bi);
			BOOK_INFO_BY_CODE.put(bi.code, bi);
		}
		if (BOOK_INFO_BY_CODE.size() != BOOK_INFO.length || BOOK_INFO_BY_ID.size() != BOOK_INFO.length)
			throw new RuntimeException();
	}

	@Override
	public Bible doImport(File inputFile) throws Exception {
		boolean useRoundtrip = Boolean.getBoolean("unbound.roundtrip");
		boolean useParsedFormat = Boolean.getBoolean("unbound.parsed");
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
			String line = br.readLine();
			if (line.isEmpty())
				line = br.readLine(); // mapped ones have an extra empty line...
			if (!line.equals("#THE UNBOUND BIBLE (www.unboundbible.org)"))
				throw new IOException(line);
			line = br.readLine();
			if (!line.startsWith("#name\t"))
				throw new IOException(line);
			Bible result = new Bible(line.substring(6));
			MetadataBook mb = new MetadataBook();
			result.getBooks().add(mb.getBook());
			line = br.readLine();
			if (!line.startsWith("#filetype\t"))
				throw new IOException(line);
			UnboundBibleFileType filetype = UnboundBibleFileType.valueOf(line.substring(10).replace('-', '_'));
			if (filetype == UnboundBibleFileType.Unmapped_BCV && useRoundtrip) {
				mb.setValue("filetype@unbound", filetype.toString());
			}
			readMetadata(br, mb, "copyright", MetadataBookKey.rights.toString());
			readMetadata(br, mb, "abbreviation", "abbreviation@unbound");
			readMetadata(br, mb, "language", MetadataBookKey.language.toString());
			readMetadata(br, mb, "note", MetadataBookKey.description.toString());
			mb.finished();
			line = br.readLine();
			if (!line.equals("#columns\t" + filetype.getColumnHeader()))
				throw new IOException(line);
			Map<BookID, Book> books = new HashMap<>();
			int sorting = -1, lastChapter = 0;
			String[] lastFields = new String[0];
			while ((line = br.readLine()) != null) {
				if (line.startsWith("#"))
					throw new IOException(line);
				if (line.trim().isEmpty())
					continue;
				String[] fields = filetype.parseFields(line, "orig_book_index", "orig_chapter", "orig_verse", "orig_subverse", "order_by", "text");
				if (fields[4] != null && Arrays.equals(fields, lastFields))
					continue;
				if (fields[2].isEmpty() && fields[4].equals("0") && fields[5].isEmpty())
					continue;
				UnboundBibleBookInfo bi = BOOK_INFO_BY_CODE.get(fields[0]);
				if (bi == null)
					throw new IOException("Invalid book code: " + fields[0] + " in " + line);
				Book bk = books.get(bi.id);
				if (bk == null) {
					bk = new Book(bi.id.getOsisID(), bi.id, bi.name, bi.name);
					result.getBooks().add(bk);
					books.put(bi.id, bk);
					lastChapter = 0;
				}
				int chapter = Integer.parseInt(fields[1]);
				String verse = "" + Integer.parseInt(fields[2]);
				if (chapter == 0) {
					chapter = 1;
					verse += "//";
				} else if (verse.equals("0")) {
					verse = "1-/";
				}
				String subverse = fields[3];
				if (subverse != null && !subverse.isEmpty()) {
					if (subverse.length() == 1 && subverse.charAt(0) >= 'a' && subverse.charAt(0) <= 'z') {
						verse += subverse;
					} else if (subverse.length() == 2 && subverse.charAt(0) == subverse.charAt(1) && subverse.charAt(0) >= 'a' && subverse.charAt(0) <= 'z') {
						verse += "." + subverse.charAt(0);
					} else if (subverse.matches("[.-][0-9]+")) {
						verse += subverse;
					} else if (subverse.equals("EndA")) {
						verse += "/a";
					} else if (subverse.equals("EndB")) {
						verse += "/b";
					} else {
						throw new IOException(subverse);
					}
				}
				if (chapter < lastChapter) {
					System.out.println("WARNING: Verses reordered across chapters detected");
					verse = chapter+","+verse;
					chapter = lastChapter;
				}
				lastChapter = chapter;
				int sortingDiff = 0;
				if (fields[4] == null) {
					if (sorting != -1)
						throw new IOException("Inconsistent sorting: " + line);
				} else {
					int s = Integer.parseInt(fields[4]);
					if (s <= sorting && lastFields[2].equals(fields[2]))
						throw new IOException("Inconsistent sorting: " + s + " <= last " + sorting + " in " + line);
					if (s != (sorting == -1 ? 10 : sorting + 10)) {
						sortingDiff = s - (sorting == -1 ? 10 : sorting + 10);
					}
					sorting = s;
					if (lastFields.length > 5 && lastFields[5].equals(fields[5]) && lastFields[2].equals(fields[2]))
						System.out.println("WARNING: Same verse text as previous: " + line);
				}
				lastFields = fields;
				String text = fields[5];
				if (useRoundtrip) {
					String last;
					do {
						last = text;
						text = text.replace("  ", " \uFEFF ");
					} while (!last.equals(text));
					if (text.endsWith(" "))
						text += "\uFEFF";
					if (text.startsWith(" "))
						text = "\uFEFF" + text;
					if (text.length() == 0)
						text = "\uFEFF-\uFEFF";
				} else {
					text = text.replaceAll("  +", " ").trim();
					if (text.length() == 0) {
						if (bk.getChapters().size() == 0) {
							books.remove(bk.getId());
							result.getBooks().remove(bk);
						}
						continue;
					}
				}
				while (bk.getChapters().size() < chapter)
					bk.getChapters().add(new Chapter());
				if (bk.getChapters().size() != chapter && useRoundtrip)
					throw new RuntimeException("Invalid chapter order: " + bk.getId() + chapter + "/" + verse + " " + text);
				Chapter ch = bk.getChapters().get(chapter - 1);
				if (!ch.getVerses().isEmpty() && ch.getVerses().get(ch.getVerses().size() - 1).getNumber().equals(verse))
					verse += "/";
				Verse vv = new Verse(verse);
				Visitor<RuntimeException> vvv = vv.getAppendVisitor();
				if (useParsedFormat) {
					String[] words = text.split(" ");
					int[] strongs = new int[10];
					String[] rmacs = new String[10];
					int strongCount = 0, rmacCount = 0;
					String word = words[0];
					for (int i = 1; i < words.length; i++) {
						if (words[i].matches("[GH][0-9]+")) {
							strongs[strongCount++] = Integer.parseInt(words[i].substring(1));
						} else if (words[i].matches(Utils.RMAC_REGEX)) {
							rmacs[rmacCount++] = words[i];
						} else {
							if (strongCount > 0 || rmacCount > 0) {
								vvv.visitGrammarInformation(strongCount > 0 ? Arrays.copyOf(strongs, strongCount) : null, rmacCount > 0 ? Arrays.copyOf(rmacs, rmacCount) : null, null).visitText(word);
								strongCount = rmacCount = 0;
							} else {
								vvv.visitText(word);
							}
							vvv.visitText(" ");
							word = words[i];
						}
					}
					if (strongCount > 0 || rmacCount > 0) {
						vvv.visitGrammarInformation(strongCount > 0 ? Arrays.copyOf(strongs, strongCount) : null, rmacCount > 0 ? Arrays.copyOf(rmacs, rmacCount) : null, null).visitText(word);
						strongCount = rmacCount = 0;
					} else {
						vvv.visitText(word);
					}
				} else {
					vvv.visitText(text);
				}
				if (useRoundtrip && sortingDiff != 0) {
					vvv.visitExtraAttribute(ExtraAttributePriority.SKIP, "unbound", "sorting-diff", "" + sortingDiff);
				}
				vv.finished();
				ch.getVerses().add(vv);
			}
			return result;
		}
	}

	private void readMetadata(BufferedReader br, MetadataBook mb, String tag, String key) throws IOException {
		String line = br.readLine();
		if (!line.startsWith("#" + tag + "\t"))
			throw new IOException(tag + ": " + line);
		if (line.length() > tag.length() + 2) {
			mb.setValue(key, line.substring(tag.length() + 2));
		}
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		MetadataBook mb = bible.getMetadataBook();
		if (mb == null)
			mb = new MetadataBook();
		String fileTypeName = mb.getValue("filetype@unbound");
		UnboundBibleFileType fileType = fileTypeName != null ? UnboundBibleFileType.valueOf(fileTypeName) : UnboundBibleFileType.Unmapped_BCVS;
		Map<String, List<String[]>> mapping = new HashMap<>();
		Map<BookID, List<String>> extraEmptyVerses = new EnumMap<>(BookID.class);
		List<Book> allBooks = new ArrayList<>(bible.getBooks());
		if (exportArgs.length == 2) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(exportArgs[1]), StandardCharsets.UTF_8))) {
				String line;
				List<Book> nonexistingExtraEmptyVersesBooks = new ArrayList<>();
				while ((line = br.readLine()) != null) {
					if (line.isEmpty() || line.startsWith("#"))
						continue;
					String[] fields = line.split("\t", -1);
					if (fields.length != 8)
						throw new IOException(line);
					int isNull = Integer.parseInt(fields[7]);
					if (isNull == 0) {
						String key = fields[3] + " " + fields[4] + ":" + fields[5] + " " + fields[6];
						String[] value = Arrays.copyOf(fields, 3);
						if (!mapping.containsKey(key))
							mapping.put(key, new ArrayList<String[]>());
						mapping.get(key).add(value);
					} else if (isNull == 1) {
						BookID id = BOOK_INFO_BY_CODE.get(fields[3]).id;
						if (!extraEmptyVerses.containsKey(id))
							extraEmptyVerses.put(id, new ArrayList<String>());
						extraEmptyVerses.get(id).add(fields[0] + "\t" + fields[1] + "\t" + fields[2] + "\t" + fields[3] + "\t" + fields[4] + "\t" + fields[5] + "\t" + fields[6] + "\t0\t");
						Book existingBook = null;
						for(Book bk : allBooks) {
							if (bk.getId() == id)
								existingBook = bk;
						}
						if (existingBook == null) {
							if (nonexistingExtraEmptyVersesBooks.isEmpty() || nonexistingExtraEmptyVersesBooks.get(nonexistingExtraEmptyVersesBooks.size()-1).getId() != id)
								nonexistingExtraEmptyVersesBooks.add(new Book(id.getOsisID(), id, id.getOsisID(), id.getOsisID()));
						} else if (!nonexistingExtraEmptyVersesBooks.isEmpty()) {
							int pos = allBooks.indexOf(existingBook);
							allBooks.addAll(pos, nonexistingExtraEmptyVersesBooks);
							nonexistingExtraEmptyVersesBooks.clear();
						}
					} else {
						throw new IOException(line);
					}
				}
			}
			fileType = UnboundBibleFileType.Mapped_BCVS;
		}
		boolean useRoundtrip = Boolean.getBoolean("unbound.roundtrip");
		boolean useParsedFormat = Boolean.getBoolean("unbound.parsed");
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[0]), StandardCharsets.UTF_8))) {
			if (exportArgs.length == 2 && useRoundtrip)
				bw.write("\r\n");
			bw.write("#THE UNBOUND BIBLE (www.unboundbible.org)\r\n");
			bw.write("#name\t" + bible.getName() + "\r\n");
			bw.write("#filetype\t" + fileType.name().replace('_', '-') + "\r\n");
			writeMetadata(bw, "copyright", mb.getValue(MetadataBookKey.rights));
			writeMetadata(bw, "abbreviation", mb.getValue("abbreviation@unbound"));
			writeMetadata(bw, "language", mb.getValue(MetadataBookKey.language));
			writeMetadata(bw, "note", mb.getValue(MetadataBookKey.description));
			bw.write("#columns\t" + fileType.getColumnHeader() + "\r\n");
			int[] sorting = { 0 };
			for (Book bk : allBooks) {
				if (bk.getId() == BookID.METADATA)
					continue;
				UnboundBibleBookInfo bi = BOOK_INFO_BY_ID.get(bk.getId());
				if (bi == null) {
					System.out.println("WARNING: Skipping unsupported book: " + bk.getAbbr());
					continue;
				}
				if (extraEmptyVerses.containsKey(bk.getId())) {
					for (String emptyVerse : extraEmptyVerses.get(bk.getId())) {
						bw.write(emptyVerse + "\r\n");
					}
				}
				for (int cc = 0; cc < bk.getChapters().size(); cc++) {
					Chapter ch = bk.getChapters().get(cc);
					int chapter = cc + 1;
					for (Verse vv : ch.getVerses()) {
						String vn = vv.getNumber(), svn = "";
						int c = chapter;
						if (vn.matches("[0-9]+,.*")) {
							int pos = vn.indexOf(',');
							c = Integer.parseInt(vn.substring(0, pos));
							vn = vn.substring(pos+1);
						}
						if (vn.equals("1-/") || vn.equals("1-//")) {
							vn = "0";
						} else if (c == 1 && vn.endsWith("//")) {
							c = 0;
							vn = vn.substring(0, vn.length() - 2);
						} else if (vn.endsWith("/a")) {
							vn = vn.substring(0, vn.length() - 2);
							svn = "EndA";
						} else if (vn.endsWith("/b")) {
							vn = vn.substring(0, vn.length() - 2);
							svn = "EndB";
						} else if (vn.endsWith("/")) {
							vn = vn.substring(0, vn.length() - 1);
						} else if (vn.matches("[0-9]+[.][a-z]")) {
							svn = vn.substring(vn.length() - 1) + vn.substring(vn.length() - 1);
							vn = vn.substring(0, vn.length() - 2);
						} else if (!vn.matches("[0-9]+")) {
							Matcher m = Pattern.compile("([0-9]+)([-,/.a-zG][-0-9,/.a-zG]*)").matcher(vn);
							if (!m.matches())
								throw new IOException(vn);
							vn = m.group(1);
							svn = m.group(2);
						}
						int v = Integer.parseInt(vn);
						sorting[0] += 10;
						StringBuilder sb = new StringBuilder();
						vv.accept(new UnboundBibleVisitor(sb, sorting, useParsedFormat));
						String text = sb.toString();
						if (useRoundtrip && text.contains("\uFEFF")) {
							if (text.equals("\uFEFF-\uFEFF"))
								text = "";
							text = text.replace("\uFEFF ", " ").replace(" \uFEFF", " ");
						}
						for (String[] nrsva_fields : lookup(mapping, bi.code, c, v, svn)) {
							String[] fields = new String[] { nrsva_fields[0], nrsva_fields[1], nrsva_fields[2], bi.code, "" + c, "" + v, svn, "" + sorting[0], text };
							fileType.writeFields(bw, fields, "nrsva_book_index", "nrsva_chapter", "nrsva_verse", "orig_book_index", "orig_chapter", "orig_verse", "orig_subverse", "order_by", "text");
						}
					}
				}
			}
		}
	}

	private List<String[]> lookup(Map<String, List<String[]>> mapping, String book, int chapter, int verse, String subverse) {
		String key = book + " " + chapter + ":" + verse + " " + subverse;
		if (mapping.containsKey(key))
			return mapping.get(key);
		List<String[]> result = new ArrayList<>();
		result.add(new String[] { book, "" + chapter, "" + verse });
		return result;
	}

	private void writeMetadata(BufferedWriter bw, String name, String value) throws IOException {
		if (value == null || value.isEmpty())
			value = "";
		bw.write("#" + name + "\t" + value + "\r\n");
	}

	@Override
	public boolean isExportImportRoundtrip() {
		return false;
	}

	@Override
	public boolean isImportExportRoundtrip() {
		return true;
	}

	private static class UnboundBibleBookInfo {
		private final String code;
		private final BookID id;
		private final String name;

		public UnboundBibleBookInfo(String code, BookID id, String name) {
			this.code = code;
			this.id = id;
			this.name = name;
		}
	}

	private static enum UnboundBibleFileType {
		Unmapped_BCVS("orig_book_index", "orig_chapter", "orig_verse", "orig_subverse", "order_by", "text"), Unmapped_BCV("orig_book_index", "orig_chapter", "orig_verse", "text"),

		Mapped_BCVS("nrsva_book_index", "nrsva_chapter", "nrsva_verse", "orig_book_index", "orig_chapter", "orig_verse", "orig_subverse", "order_by", "text");

		private final String[] columns;

		private UnboundBibleFileType(String... columns) {
			this.columns = columns;
		}

		public String getColumnHeader() {
			StringBuilder result = new StringBuilder();
			for (String col : columns) {
				result.append("\t" + col);
			}
			result.delete(0, 1);
			return result.toString();
		}

		public String[] parseFields(String line, String... desiredColumns) throws IOException {
			String[] fields = line.split("\t", -1);
			if (fields.length != columns.length)
				throw new IOException(fields.length + " != " + columns.length + ": " + line);
			String[] result = new String[desiredColumns.length];
			for (int i = 0; i < result.length; i++) {
				for (int j = 0; j < columns.length; j++) {
					if (desiredColumns[i].equals(columns[j])) {
						result[i] = fields[j];
						break;
					}
				}
			}
			return result;
		}

		public void writeFields(BufferedWriter bw, String[] fields, String... fieldColumns) throws IOException {
			for (int i = 0; i < columns.length; i++) {
				if (i > 0)
					bw.write("\t");
				for (int j = 0; j < fieldColumns.length; j++) {
					if (fieldColumns[j].equals(columns[i])) {
						bw.write(fields[j]);
						break;
					}
				}
			}
			bw.write("\r\n");
		}
	}

	private static class UnboundBibleVisitor implements Visitor<RuntimeException> {

		private final StringBuilder sb;
		protected final List<String> suffixStack = new ArrayList<String>();
		private final int[] sorting;
		private final boolean useParsedFormat;

		private UnboundBibleVisitor(StringBuilder sb, int[] sorting, boolean useParsedFormat) {
			this.sb = sb;
			this.sorting = sorting;
			this.useParsedFormat = useParsedFormat;
			suffixStack.add("");
		}

		@Override
		public void visitVerseSeparator() {
			sb.append("/");
		}

		@Override
		public void visitText(String text) {
			// there seems to be no escaping syntax; let's hope for the best
			sb.append(text);
		}

		@Override
		public void visitStart() {
		}

		@Override
		public void visitLineBreak(LineBreakKind kind) {
			sb.append(" ");
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
			suffixStack.add("");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitFootnote() {
			System.out.println("WARNING: Footnotes are not supported");
			return null;
		}

		@Override
		public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) {
			if (category.equals("unbound") && key.equals("sorting-diff")) {
				sorting[0] += Integer.parseInt(value);
				return null;
			}
			Visitor<RuntimeException> result = prio.handleVisitor(category, this);
			if (result != null)
				suffixStack.add("");
			return result;
		}

		@Override
		public boolean visitEnd() {
			sb.append(suffixStack.remove(suffixStack.size() - 1));
			return false;
		}

		@Override
		public Visitor<RuntimeException> visitDictionaryEntry(String dictionary, String entry) {
			System.out.println("WARNING: Dictionary entries are not supported");
			suffixStack.add("");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(int[] strongs, String[] rmac, int[] sourceIndices) {
			if (useParsedFormat) {
				StringBuilder suffix = new StringBuilder();
				if (strongs != null) {
					for (int strong : strongs) {
						suffix.append(" G" + strong);
					}
				}
				if (rmac != null) {
					for (String r : rmac) {
						suffix.append(" " + r);
					}
				}
				suffixStack.add(suffix.toString());
			} else {
				System.out.println("WARNING: Grammar information is only supported in the \"Parsed\" format");
				suffixStack.add("");
			}
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) {
			System.out.println("WARNING: Cross references are not supported");
			suffixStack.add("");
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitCSSFormatting(String css) {
			System.out.println("WARNING: Formatting is not supported");
			suffixStack.add("");
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
}
