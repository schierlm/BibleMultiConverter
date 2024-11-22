package biblemulticonverter.logos.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
			"Apostrophe to Zion=-", "Hymn to the Creator=-", "Apostrophe to Judah=-", "David’s Compositions=-",
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
		try (InputStream in = new URL("https://web.archive.org/web/20240226003157id_/https://wiki.logos.com/Bible_Verse_Maps").openStream();
				Writer w = new OutputStreamWriter(new FileOutputStream(versemap), StandardCharsets.ISO_8859_1)) {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			builder.setEntityResolver(new EntityResolver() {
				@Override
				public InputSource resolveEntity(String publicId, String systemId)
						throws SAXException, IOException {
					return new InputSource(new StringReader(""));
				}
			});
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int len;
			while ((len = in.read(buf)) != -1)
				baos.write(buf, 0, len);
			if (baos.toByteArray()[0] == 0x1f && (baos.toByteArray()[1] & 0xFF) == 0x8b) {
				GZIPInputStream zin = new GZIPInputStream(new ByteArrayInputStream(baos.toByteArray()));
				baos = new ByteArrayOutputStream();
				while ((len = zin.read(buf)) != -1)
					baos.write(buf, 0, len);
			}
			String xml = new String(baos.toByteArray(), StandardCharsets.UTF_8);
			xml = xml.replaceAll("<script type=\"text/javascript\">\\(?window.*?</script>", "").replace("createCookie&authorizationHeader=", "").replaceAll("<script async charset=\"utf-8\"", "<script charset=\"utf-8\"");
			parseVerseMap(builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))), w);
		}
		System.out.println("Downloading Logos verse map done.");
		if (new File(basedir, "target/classes").exists()) {
			System.out.print("Copying Logos verse map to target/classes...");
			Files.copy(versemap.toPath(), new File(new File(basedir, "target/classes"), versemap.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
			System.out.println("done");
		}
	}

	private static void parseVerseMap(Document doc, Writer w) throws Exception {
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

		XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
		NodeList nl = (NodeList) xp.evaluate("//div[@class='Level2']/table", doc, XPathConstants.NODESET);
		for (int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			String book = xp.evaluate("../h2/text()", n).trim();
			BookID bookID = bookMap.get(book);
			if (bookID == null)
				throw new IOException("Unknown book name: " + book);
			if (bookID == BookID.DICTIONARY_ENTRY) // skip this book
				continue;
			w.write(bookID.getOsisID() + " ");
			List<List<String>> table = new ArrayList<List<String>>();
			Node tr = n.getFirstChild();
			while (true) {
				while (tr instanceof Text && tr.getTextContent().trim().length() == 0)
					tr = tr.getNextSibling();
				if (tr == null)
					break;
				List<String> row = new ArrayList<String>();
				table.add(row);
				Node td = tr.getFirstChild();
				while (true) {
					while (td instanceof Text && td.getTextContent().trim().length() == 0)
						td = td.getNextSibling();
					if (td == null)
						break;
					row.add(td.getTextContent());
					td = td.getNextSibling();
				}
				tr = tr.getNextSibling();
			}
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
