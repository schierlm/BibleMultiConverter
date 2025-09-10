package biblemulticonverter.format;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;
import biblemulticonverter.schema.uxlc.C;
import biblemulticonverter.schema.uxlc.K;
import biblemulticonverter.schema.uxlc.ObjectFactory;
import biblemulticonverter.schema.uxlc.Pe;
import biblemulticonverter.schema.uxlc.Q;
import biblemulticonverter.schema.uxlc.Reversednun;
import biblemulticonverter.schema.uxlc.S;
import biblemulticonverter.schema.uxlc.Samekh;
import biblemulticonverter.schema.uxlc.TanachRoot;
import biblemulticonverter.schema.uxlc.V;
import biblemulticonverter.schema.uxlc.W;
import biblemulticonverter.tools.ValidateXML;

public class UXLC implements ImportFormat {

	public static final String[] HELP_TEXT = {
			"Importer for the Unicode/XML Leningrad Codex with Documentary Hypothesis tagging",
			"",
			"Usage: UXLC <directory>",
			"",
			"Download UXLC from <https://www.tanach.us/Pages/XMLFiles.html>."
	};

	private static Map<String, BookID> BOOK_ID_MAP = new HashMap<>();

	static {
		BOOK_ID_MAP.put("Genesis", BookID.BOOK_Gen);
		BOOK_ID_MAP.put("Exodus", BookID.BOOK_Exod);
		BOOK_ID_MAP.put("Leviticus", BookID.BOOK_Lev);
		BOOK_ID_MAP.put("Numbers", BookID.BOOK_Num);
		BOOK_ID_MAP.put("Deuteronomy", BookID.BOOK_Deut);
		BOOK_ID_MAP.put("Joshua", BookID.BOOK_Josh);
		BOOK_ID_MAP.put("Judges", BookID.BOOK_Judg);
		BOOK_ID_MAP.put("Samuel_1", BookID.BOOK_1Sam);
		BOOK_ID_MAP.put("Samuel_2", BookID.BOOK_2Sam);
		BOOK_ID_MAP.put("Kings_1", BookID.BOOK_1Kgs);
		BOOK_ID_MAP.put("Kings_2", BookID.BOOK_2Kgs);
		BOOK_ID_MAP.put("Isaiah", BookID.BOOK_Isa);
		BOOK_ID_MAP.put("Jeremiah", BookID.BOOK_Jer);
		BOOK_ID_MAP.put("Ezekiel", BookID.BOOK_Ezek);
		BOOK_ID_MAP.put("Hosea", BookID.BOOK_Hos);
		BOOK_ID_MAP.put("Joel", BookID.BOOK_Joel);
		BOOK_ID_MAP.put("Amos", BookID.BOOK_Amos);
		BOOK_ID_MAP.put("Obadiah", BookID.BOOK_Obad);
		BOOK_ID_MAP.put("Jonah", BookID.BOOK_Jonah);
		BOOK_ID_MAP.put("Micah", BookID.BOOK_Mic);
		BOOK_ID_MAP.put("Nahum", BookID.BOOK_Nah);
		BOOK_ID_MAP.put("Habakkuk", BookID.BOOK_Hab);
		BOOK_ID_MAP.put("Zephaniah", BookID.BOOK_Zeph);
		BOOK_ID_MAP.put("Haggai", BookID.BOOK_Hag);
		BOOK_ID_MAP.put("Zechariah", BookID.BOOK_Zech);
		BOOK_ID_MAP.put("Malachi", BookID.BOOK_Mal);
		BOOK_ID_MAP.put("Chronicles_1", BookID.BOOK_1Chr);
		BOOK_ID_MAP.put("Chronicles_2", BookID.BOOK_2Chr);
		BOOK_ID_MAP.put("Psalms", BookID.BOOK_Ps);
		BOOK_ID_MAP.put("Job", BookID.BOOK_Job);
		BOOK_ID_MAP.put("Proverbs", BookID.BOOK_Prov);
		BOOK_ID_MAP.put("Ruth", BookID.BOOK_Ruth);
		BOOK_ID_MAP.put("Song_of_Songs", BookID.BOOK_Song);
		BOOK_ID_MAP.put("Ecclesiastes", BookID.BOOK_Eccl);
		BOOK_ID_MAP.put("Lamentations", BookID.BOOK_Lam);
		BOOK_ID_MAP.put("Esther", BookID.BOOK_Esth);
		BOOK_ID_MAP.put("Daniel", BookID.BOOK_Dan);
		BOOK_ID_MAP.put("Ezra", BookID.BOOK_Ezra);
		BOOK_ID_MAP.put("Nehemiah", BookID.BOOK_Neh);
	}

	@Override
	public Bible doImport(File directory) throws Exception {
		Bible bible = new Bible("UXLC");
		Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/uxlc.xsd"));
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Unmarshaller unmarshaller = ctx.createUnmarshaller();
		File indexFile = new File(directory, "TanachIndex.xml");
		TanachRoot idxdoc = (TanachRoot) unmarshaller.unmarshal(indexFile);
		for (biblemulticonverter.schema.uxlc.Book idxbook : idxdoc.getTanach().getBook()) {
			String filename = idxbook.getNames().getFilename();
			BookID bid = BOOK_ID_MAP.get(filename);
			String dh = "";
			if (bid.getZefID() <= BookID.BOOK_Deut.getZefID()) {
				dh = ".DH";
			}
			File dataFile = new File(directory, filename + dh + ".xml");
			System.out.println(bid + "\t" + filename);
			ValidateXML.validateFileBeforeParsing(schema, dataFile);
			TanachRoot datadoc = (TanachRoot) unmarshaller.unmarshal(dataFile);
			if (datadoc.getTanach().getBook().size() != 1)
				throw new IOException("Incorrect number of books");
			biblemulticonverter.schema.uxlc.Book databook = datadoc.getTanach().getBook().get(0);
			Book book = new Book(databook.getNames().getAbbrev().replace(" ", ""), bid, databook.getNames().getName(), databook.getNames().getHebrewname());
			bible.getBooks().add(book);
			for (C dataC : databook.getC()) {
				Chapter ch = new Chapter();
				book.getChapters().add(ch);
				if (dataC.getN().intValueExact() != book.getChapters().size())
					throw new IOException("Invalid chapter number");
				Verse currVerse = null;
				int nextIdx = -1;
				for (V dataV : dataC.getV()) {
					String vn = dataV.getN().toString();
					if (currVerse == null || !currVerse.getNumber().equals(vn)) {
						currVerse = new Verse(vn);
						ch.getVerses().add(currVerse);
						nextIdx = 1;
					} else if (dh.isEmpty()) {
						throw new IOException(vn);
					}
					Visitor<RuntimeException> v = currVerse.getAppendVisitor();
					String[] akeys = null, avals = null, kqkeys = { "kq" }, kqvals = { "" };
					if (dataV.getS() != null) {
						akeys = new String[] { "dh" };
						avals = new String[] { dataV.getS() };
						kqkeys = new String[] { "kq", "dh" };
						kqvals = new String[] { "", dataV.getS() };
					}
					for (Object o : dataV.getWOrPeOrSamekh()) {
						if (o instanceof W || o instanceof K || o instanceof Q) {
							List<Object> content;
							Visitor<RuntimeException> vv;
							if (o instanceof Q) {
								content = ((Q) o).getContent();
								Visitor<RuntimeException> vf = v.visitFootnote(false);
								vf.visitText("Q: ");
								kqvals[0] = "q";
								vv = vf.visitGrammarInformation(null, null, null, null, null, null, kqkeys, Arrays.copyOf(kqvals, kqvals.length));
							} else if (o instanceof K) {
								content = ((K) o).getContent();
								kqvals[0] = "k";
								vv = v.visitGrammarInformation(null, null, null, null, null, new int[] { nextIdx }, kqkeys, Arrays.copyOf(kqvals, kqvals.length));
								nextIdx++;
							} else {
								content = ((W) o).getContent();
								if (content.size() == 1 && content.get(0).equals(".")) {
									if (dh.isEmpty())
										throw new IOException("Placeholder word");
									continue;
								}
								vv = v.visitGrammarInformation(null, null, null, null, null, new int[] { nextIdx }, akeys, avals);
								nextIdx++;
							}
							for (Object oo : content) {
								if (oo instanceof String) {
									vv.visitText(((String) oo).replaceAll("[ \r\n\t]+", ""));
								} else if (oo instanceof JAXBElement) {
									JAXBElement<String> je = (JAXBElement<String>) oo;
									if (je.getName().getLocalPart().equals("x")) {
										vv.visitFootnote(false).visitText("X: " + je.getValue());
									} else {
										throw new IOException(je.getName().toString());
									}
								} else if (oo instanceof S) {
									S s = (S) oo;
									vv.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "uxlc", "style", s.getT().value()).visitText(s.getContent());
								} else {
									throw new IOException(oo.getClass().getName());
								}
							}
						} else if (o instanceof Pe) {
							v.visitLineBreak(ExtendedLineBreakKind.PARAGRAPH, 0);
						} else if (o instanceof Samekh) {
							v.visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "uxlc", "samekh", "samekh").visitLineBreak(ExtendedLineBreakKind.PARAGRAPH, 0);
						} else if (o instanceof String) {
							v.visitFootnote(false).visitText("X: " + (String) o);
						} else if (o instanceof Reversednun) {
							v.visitExtraAttribute(ExtraAttributePriority.SKIP, "uxlc", "reversednun", "reversednun");
						} else {
							throw new IOException(o.getClass().getName());
						}
					}
				}
				for (Verse vv : ch.getVerses()) {
					vv.finished();
				}
			}
		}
		return bible;
	}
}
