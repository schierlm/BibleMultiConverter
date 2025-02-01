package biblemulticonverter.logos.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.BookID;
import biblemulticonverter.logos.format.LogosHTML;

public class LogosVerseMapDownloader {

	/** List of all versifications supported by Logos */
	public static String[] ALL_VERSIFICATIONS = {
			"Bible", "BibleAFR1933", "BibleAFR1983", "BibleAKESONS", "BibleALFNT", "BibleAPOK82", "BibleARA",
			"BibleARC", "BibleAT", "BibleAT2", "BibleB21", "BibleBFC", "BibleBHS", "BibleBHS2", "BibleBIBEL82",
			"BibleBL2000", "BibleBNP", "BibleBRENTON", "BibleBUBER", "BibleBYZ", "BibleCAP", "BibleCEB", "BibleCJB",
			"BibleDANBIB", "BibleDANCLV", "BibleDR", "BibleDSS", "BibleDSSB", "BibleDSS2002", "BibleEINHEIT",
			"BibleELBER", "BibleESV", "BibleEXPNT", "BibleGNB", "BibleGNBNR", "BibleGNT", "BibleGP", "BibleHB",
			"BibleHCSB", "BibleISV", "BibleJFA", "BibleJPS1917", "BibleKJV", "BibleLEB", "BibleLSG", "BibleLU1545",
			"BibleLU1912", "BibleLUTBIB1984", "BibleLXX", "BibleLXX2", "BibleLXX2A", "BibleLXXG", "BibleLXXMTPAR",
			"BibleMENGE", "BibleNA27", "BibleNAB", "BibleNABRE", "BibleNASB95", "BibleNBG", "BibleNBMST",
			"BibleNBS", "BibleNBV", "BibleNCV", "BibleNIV", "BibleNJB", "BibleNKJV", "BibleNLT", "BibleNLV",
			"BibleNRSV", "BibleNRSVCE", "BibleNTLH", "BibleNVUL", "BibleOSG", "BibleOTP", "BiblePESH", "BiblePESH2",
			"BiblePIJIN", "BibleRAA1933", "BibleRCUV", "BibleRMNT", "BibleRST", "BibleRST2", "BibleRSV",
			"BibleRSVCE", "BibleRV1909", "BibleRVA", "BibleRVR60", "BibleRVR95", "BibleSBLGNT", "BibleSCRIV",
			"BibleSVV", "BibleTB", "BibleTGV", "BibleTHB1973", "BibleTISCH", "BibleTOBIBLE", "BibleTONGAN",
			"BibleB2000", "BibleBBA", "BibleBHT", "BibleBJL", "BibleDARFR", "BibleDHS", "BibleELBER1905", "BibleESV2",
			"BibleGENEVA", "BibleKJV2", "BibleKJV66", "BibleLC", "BibleLEB2", "BibleLP", "BibleMT", "BibleNEB",
			"BibleNO2011", "BiblePDV", "BibleREB", "BibleSCHLACTER2000", "BibleSEM", "BibleSER", "BibleTOB2010",
			"BibleBCP1662", "BibleBCP1928", "BibleBCP1979", "BibleCAMGT", "BibleCBG", "BibleCBL", "BibleCODEXS",
			"BibleEOBNT", "BibleGRAIL", "BibleGUDSORD", "BibleLXXSCS", "BiblePATR", "BibleRVG",
			"BibleSB2014", "BibleTOB-HL", "Bible4E2B",
			"BibleABUV1913", "BibleALMEIDA1819", "BibleCAMBRIDGE1895", "BibleCSB", "BibleCUV", "BibleCUV2",
			"BibleDKV", "BibleERK", "BibleFIGUEIREDO1885", "BibleKONCTB", "BibleKONKRV", "BibleKONKSV", "BibleKRV",
			"BibleLEESER1891", "BibleLONGMAN1864", "BibleNET", "BibleNEWCOME", "BiblePALFREY", "BiblePSALMSOFDAVID",
			"BibleSAWYER1861", "BibleSEPEDI2000", "BibleSHEN", "BibleSMITH1876", "BibleSNDBL2012", "BibleSPURRELL1885",
			"BibleTLV", "BibleTSHVND1998", "BibleTSWANA1994", "BibleXITSONGA",
			"BibleTSV", "BibleUBS4", "BibleUT", "BibleVPEE", "BibleVUL", "BibleVUL2", "BibleWH", "BibleWV95",
			"BibleCNV", "BibleCSB2", "BibleKCB", "BibleLUTBIB2017",
			"BibleCRAMPON", "BibleEHSG", "BibleHFA", "BibleHUERV", "BibleLBO", "BibleMARTIN", "BibleNEU", "BibleNVI",
			"BiblePASSION",	"BibleRUERV", "BibleSRERV", "BibleTREGELLES", "BibleURERV", "BibleLBLA", "BibleZUNZ",
			"BibleELBER2003", "BibleELBER2016", "BibleGNTTYN", "BibleLXXDE", "BibleLXXDEA", "BibleLXXDEKOMM",
			"BibleNETS", "BibleNETSA", "BibleORTHSB",
			"BibleBT4E","BibleCCB","BibleEHV","BibleESVCE","BibleNJBCT","BibleRHG",
			"BibleAM", "BibleBB", "BibleBH", "BibleBTX", "BibleDHH", "BibleHERDER", "BibleHSV", "BibleJJ", "BibleLJJ", "BibleLPD", "BibleNRSVUE", "BiblePESHES", "BibleSIHGAO", "BibleWEB", "BibleWEBME",
			"BibleAPC", "BibleARND", "BibleAS", "BibleBENGEL", "BibleBSNZ", "BibleCJB2", "BibleDCBOT", "BibleDPB", "BibleFILLION", "BibleJSHRZSIR",
			"BibleLEXOTAPOC", "BibleLH", "BibleLU1545CRED", "BibleLU1545DC", "BibleLUTHER21", "BibleLXXGAT", "BibleLXXGIGUET", "BibleMENGE20",
			"BibleNAVARRA", "BibleNBV2021", "BibleNVT", "BiblePATTLOCH", "BibleSACY", "BibleSB", "BibleTXT", "BibleVGRX", "BibleZB",
	};

	public static String[] ALL_BOOK_NAMES = {
			"Genesis=Gen", "Exodus=Exod", "Leviticus=Lev", "Numbers=Num", "Deuteronomy=Deut", "Joshua=Josh",
			"Judges=Judg", "Ruth=Ruth", "1 Samuel=1Sam", "2 Samuel=2Sam", "1 Kings=1Kgs", "2 Kings=2Kgs",
			"1 Chronicles=1Chr", "2 Chronicles=2Chr", "Ezra=Ezra", "Nehemiah=Neh", "Esther=Esth", "Job=Job",
			"Psalm=Ps", "Proverbs=Prov", "Ecclesiastes=Eccl", "Song of Solomon=Song", "Isaiah=Isa", "Jeremiah=Jer",
			"Lamentations=Lam", "Ezekiel=Ezek", "Daniel=Dan", "Hosea=Hos", "Joel=Joel", "Amos=Amos", "Obadiah=Obad",
			"Jonah=Jonah", "Micah=Mic", "Nahum=Nah", "Habakkuk=Hab", "Zephaniah=Zeph", "Haggai=Hag", "Zechariah=Zech",
			"Malachi=Mal", "Tobit=Tob", "Judith=Jdt", "Additions to Esther=AddEsth", "Wisdom of Solomon=Wis",
			"Sirach=Sir", "Baruch=Bar", "Letter of Jeremiah=EpJer", "Song of Three Youths=PrAzar", "Susanna=Sus",
			"Bel and the Dragon=Bel", "1 Maccabees=1Macc", "2 Maccabees=2Macc", "1 Esdras=1Esd",
			"Prayer of Manasseh=PrMan", "Additional Psalm=AddPs", "3 Maccabees=3Macc", "2 Esdras=2Esd",
			"4 Maccabees=4Macc", "Ode=Odes", "Psalms of Solomon=PssSol", "Epistle to the Laodiceans=EpLao",
			"Matthew=Matt", "Mark=Mark", "Luke=Luke", "John=John", "Acts=Acts", "Romans=Rom", "1 Corinthians=1Cor",
			"2 Corinthians=2Cor", "Galatians=Gal", "Ephesians=Eph", "Philippians=Phil", "Colossians=Col",
			"1 Thessalonians=1Thess", "2 Thessalonians=2Thess", "1 Timothy=1Tim", "2 Timothy=2Tim", "Titus=Titus",
			"Philemon=Phlm", "Hebrews=Heb", "James=Jas", "1 Peter=1Pet", "2 Peter=2Pet", "1 John=1John", "2 John=2John",
			"3 John=3John", "Jude=Jude", "Revelation=Rev", "Additions to Daniel=AddDan", "Plea for Deliverance=-",
			"Apostrophe to Zion=-", "Hymn to the Creator=-", "Apostrophe to Judah=-", "David's Compositions=-",
			"Apocryphal Psalms=5ApocSyrPss", "Psalm 151A=-", "Psalm 151B=-",
			"Epistle of Baruch=EpBar", "Apocalypse of Baruch=2Bar", "Catena=-",
			"Eschatological Hymn=-", "1 Enoch=1En", "4 Ezra=4Ezra", "2 Baruch=4Bar", "Odes=Odes",
	};

	public static void main(String[] args) throws Exception {
		String basedir = args.length == 0 ? "." : args[0];
		File versemap = new File(basedir, "src/main/resources/logos-versemap.dat");
		if (versemap.exists())
			return;
		System.out.println("Downloading Logos verse map...");
		final String PREFIX = "<a href=\"https://community.logos.com/home/leaving?allowTrusted=1&amp;target=https%3A%2F%2Fhtmlpreview.github.io%2F%3F";
		String url = "https://us.v-cdn.net/6038263/uploads/RKBF44XS827B/bible-verse-maps-html.txt";
		HttpURLConnection uc;
		try {
			uc = (HttpURLConnection) new URL("https://community.logos.com/kb/articles/549-bible-verse-maps").openConnection();
			uc.setRequestProperty("User-Agent", "BibleMultiConverter/1.0");
			try (BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.contains(PREFIX)) {
						line = line.substring(line.indexOf(PREFIX) + PREFIX.length());
						line = line.substring(0, line.indexOf('"'));
						url = URLDecoder.decode(line, "UTF-8");
						break;
					}
				}
			}
		} catch (IOException ex) {
			System.err.println("Determining dynamic URL failed, using static one");
			ex.printStackTrace();
		}
		System.out.println("Using URL: " + url);
		uc = (HttpURLConnection) new URL(url).openConnection();
		uc.setRequestProperty("User-Agent", "BibleMultiConverter/1.0");
		try (BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream(), StandardCharsets.UTF_8));
				Writer w = new OutputStreamWriter(new FileOutputStream(versemap), StandardCharsets.ISO_8859_1)) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("<script type=\"text/plain\""))
					break;
			}
			Map<String, List<List<String>>> books = new LinkedHashMap<>();
			String letters = line.substring(line.indexOf('>') + 1);
			List<String> versemaps = Arrays.asList(br.readLine().split(","));
			line = br.readLine();
			if (!line.isEmpty())
				throw new IOException("Line should be empty: " + line);
			line = br.readLine();
			while (!line.startsWith("</script>")) {
				String name = line;
				List<List<String>> headers = new ArrayList<>();
				String cols = br.readLine();
				int vmap = 0, lastCol = -1;
				while (!cols.isEmpty()) {
					int col = letters.indexOf(cols.charAt(0));
					if (col != -1) {
						lastCol = col;
						while (col >= headers.size())
							headers.add(new ArrayList<>());
						headers.get(col).add(versemaps.get(vmap));
						vmap++;
						cols = cols.substring(1);
					} else if (cols.charAt(0) == '-') {
						lastCol = -2;
						vmap++;
						cols = cols.substring(1);
					} else if (lastCol != -1 && cols.charAt(0) >= '0' && cols.charAt(0) <= '9') {
						int len = 1;
						while (len < cols.length() && cols.charAt(len) >= '0' && cols.charAt(len) <= '9')
							len++;
						int cnt = Integer.parseInt(cols.substring(0, len));
						if (lastCol != -2) {
							headers.get(lastCol).addAll(versemaps.subList(vmap, vmap + cnt - 1));
						}
						vmap += cnt - 1;
						cols = cols.substring(len);
					} else {
						throw new IOException("Unsupported column specification: " + cols);
					}
				}
				List<List<String>> cells = new ArrayList<>();
				cells.add(new ArrayList<>(headers.size() + 1));
				cells.get(0).add("");
				for (List<String> header : headers) {
					cells.get(0).add(String.join(", ", header));
				}
				if (vmap != versemaps.size())
					throw new IOException("Incomplete columns specification");
				int lastRow = 0;
				line = br.readLine();
				while (!line.isEmpty() && !line.startsWith("</script>")) {
					String[] row = new String[headers.size() + 1];
					int epos = line.indexOf("=");
					Arrays.fill(row, "");
					row[0] = "" + (++lastRow);
					if (epos != -1) {
						row[0] = line.substring(0, epos);
						line = line.substring(epos + 1);
						if (row[0].matches("[1-9][0-9]*")) {
							lastRow = Integer.parseInt(row[0]);
						} else {
							lastRow = 0;
						}
					}
					while (!line.isEmpty()) {
						List<Integer> affectedCols = new ArrayList<>();
						String affectedVal = "";
						while (!line.isEmpty()) {
							int pos = letters.indexOf(line.charAt(0));
							if (pos == -1)
								break;
							affectedCols.add(pos);
							line = line.substring(1);
						}
						if (affectedCols.isEmpty())
							throw new IOException("Rule without cols");
						if (line.isEmpty())
							throw new IOException("Col without value");
						if (line.charAt(0) == '[') {
							int pos = line.indexOf(']');
							affectedVal = line.substring(1, pos);
							line = line.substring(pos + 1);
						} else {
							int len = 0;
							while (len < line.length() && letters.indexOf(line.charAt(len)) == -1)
								len++;
							affectedVal = line.substring(0, len);
							line = line.substring(len);
							if (affectedVal.matches("[0-9]+")) {
								affectedVal = "1-" + affectedVal;
							}
						}
						for (int affectedCol : affectedCols) {
							row[affectedCol + 1] = affectedVal;
						}
					}
					cells.add(Arrays.asList(row));
					line = br.readLine();
				}
				if (line.isEmpty())
					line = br.readLine();
				books.put(name, cells);
			}
			parseVerseMap(books, w);
		}
		System.out.println("Downloading Logos verse map done.");
		if (new File(basedir, "target/classes").exists()) {
			System.out.print("Copying Logos verse map to target/classes...");
			Files.copy(versemap.toPath(), new File(new File(basedir, "target/classes"), versemap.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
			System.out.println("done");
		}
	}

	private static void parseVerseMap(Map<String, List<List<String>>> books, Writer w) throws Exception {
		Map<String, BookID> bookMap = new HashMap<String, BookID>();
		for (String bookName : ALL_BOOK_NAMES) {
			String[] parts = bookName.split("=");
			bookMap.put(parts[0], parts[1].equals("-") ? BookID.DICTIONARY_ENTRY : BookID.fromOsisId(parts[1]));
		}
		Map<String, Integer> versificationMap = new HashMap<String, Integer>();
		for (int i = 0; i < ALL_VERSIFICATIONS.length; i++) {
			String v11n = ALL_VERSIFICATIONS[i];
			if (i != 0)
				w.write(' ');
			w.write(v11n);
			versificationMap.put(v11n.equals("Bible") ? "bible" : v11n.substring(5).toLowerCase(), i);
		}
		w.write('\n');

		Map<String, Integer> namedVerseMap = new HashMap<>();
		for (int i = 0; i < LogosHTML.NAMED_VERSES.length; i++) {
			namedVerseMap.put(LogosHTML.NAMED_VERSES[i], i);
		}

		for (Map.Entry<String, List<List<String>>> entry : books.entrySet()) {
			String book = entry.getKey().trim();
			BookID bookID = bookMap.get(book);
			if (bookID == null)
				throw new IOException("Unknown book name: " + book);
			if (bookID == BookID.DICTIONARY_ENTRY) // skip this book
				continue;
			w.write(bookID.getOsisID() + " ");
			List<List<String>> table = entry.getValue();
			for (int j = 1; j < table.get(0).size(); j++) {
				BitSet v11ns = new BitSet();
				for (String v11n : table.get(0).get(j).split(", ")) {
					if (!versificationMap.containsKey(v11n))
						System.out.println("SKIPPING VERSEMAP " + v11n);
					else
						v11ns.set(versificationMap.get(v11n));
				}

				List<BitSet> chapters = new ArrayList<>();
				for (int k = 1; k < table.size(); k++) {
					if (table.get(k).get(0).isEmpty())
						table.get(k).set(0, "1");
					if (!table.get(k).get(0).matches("[0-9]+"))
						continue;
					int chapterNumber = Integer.parseInt(table.get(k).get(0));
					String ranges = table.get(k).get(j);
					if (ranges.length() > 0) {
						while (chapters.size() < chapterNumber)
							chapters.add(new BitSet());
						BitSet chapter = chapters.get(chapterNumber - 1);
						for (String range : ranges.split(", ")) {
							if (!range.matches("[0-9-]+")) {
								Integer idx = namedVerseMap.get(range);
								if (idx == null) {
									if (!range.matches("3[a-c][a-z]"))
										System.out.println("SKIPPING VERSE " + range);
									continue;
								}
								range = String.valueOf(idx + 1000);
							}
							String[] parts = range.split("-");
							if (parts.length == 1) {
								chapter.set(Integer.parseInt(parts[0]));
							} else if (parts.length == 2) {
								chapter.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) + 1);
							} else {
								throw new IOException("Unsupported verse ranges: " + ranges);
							}
						}
					}
				}
				writeBits(v11ns, w);
				for (BitSet chapter : chapters) {
					w.write(' ');
					writeBits(chapter, w);
				}
				w.write('\n');
			}
		}
	}

	private static void writeBits(BitSet bits, Writer w) throws IOException {
		if (bits.isEmpty()) {
			w.write("-");
		} else {

		}

		int from = bits.nextSetBit(0);
		while (from != -1) {
			int to = bits.nextClearBit(from) - 1;
			if (to == from)
				w.write("" + from);
			else
				w.write(from + "-" + to);
			from = bits.nextSetBit(to + 1);
			if (from != -1)
				w.write(',');
		}
	}
}
