package biblemulticonverter.versification;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.schema.versification.openscriptures.BibleBookVersification;
import biblemulticonverter.schema.versification.openscriptures.BibleVersificationSystem;
import biblemulticonverter.schema.versification.openscriptures.NumVerses;
import biblemulticonverter.schema.versification.openscriptures.ObjectFactory;
import biblemulticonverter.tools.ValidateXML;

public class OpenScriptures implements VersificationFormat {

	public static final String[] HELP_TEXT = {
			"Versification format used by https://github.com/openscriptures/BibleOrgSys/",
			"",
			"Usage for import: OpenScriptures <filename> <name>"
	};

	private static final Map<String, BookID> REFERENCE_ABBREVIATIONS = new HashMap<>();

	static {
		REFERENCE_ABBREVIATIONS.put("GEN", BookID.BOOK_Gen);
		REFERENCE_ABBREVIATIONS.put("EXO", BookID.BOOK_Exod);
		REFERENCE_ABBREVIATIONS.put("LEV", BookID.BOOK_Lev);
		REFERENCE_ABBREVIATIONS.put("NUM", BookID.BOOK_Num);
		REFERENCE_ABBREVIATIONS.put("DEU", BookID.BOOK_Deut);
		REFERENCE_ABBREVIATIONS.put("JOS", BookID.BOOK_Josh);
		REFERENCE_ABBREVIATIONS.put("JDG", BookID.BOOK_Judg);
		REFERENCE_ABBREVIATIONS.put("RUT", BookID.BOOK_Ruth);
		REFERENCE_ABBREVIATIONS.put("SA1", BookID.BOOK_1Sam);
		REFERENCE_ABBREVIATIONS.put("SA2", BookID.BOOK_2Sam);
		REFERENCE_ABBREVIATIONS.put("KI1", BookID.BOOK_1Kgs);
		REFERENCE_ABBREVIATIONS.put("KI2", BookID.BOOK_2Kgs);
		REFERENCE_ABBREVIATIONS.put("CH1", BookID.BOOK_1Chr);
		REFERENCE_ABBREVIATIONS.put("CH2", BookID.BOOK_2Chr);
		REFERENCE_ABBREVIATIONS.put("EZR", BookID.BOOK_Ezra);
		REFERENCE_ABBREVIATIONS.put("NEH", BookID.BOOK_Neh);
		REFERENCE_ABBREVIATIONS.put("EST", BookID.BOOK_Esth);
		REFERENCE_ABBREVIATIONS.put("JOB", BookID.BOOK_Job);
		REFERENCE_ABBREVIATIONS.put("PSA", BookID.BOOK_Ps);
		REFERENCE_ABBREVIATIONS.put("PRO", BookID.BOOK_Prov);
		REFERENCE_ABBREVIATIONS.put("ECC", BookID.BOOK_Eccl);
		REFERENCE_ABBREVIATIONS.put("SNG", BookID.BOOK_Song);
		REFERENCE_ABBREVIATIONS.put("ISA", BookID.BOOK_Isa);
		REFERENCE_ABBREVIATIONS.put("JER", BookID.BOOK_Jer);
		REFERENCE_ABBREVIATIONS.put("LAM", BookID.BOOK_Lam);
		REFERENCE_ABBREVIATIONS.put("EZE", BookID.BOOK_Ezek);
		REFERENCE_ABBREVIATIONS.put("DAN", BookID.BOOK_Dan);
		REFERENCE_ABBREVIATIONS.put("HOS", BookID.BOOK_Hos);
		REFERENCE_ABBREVIATIONS.put("JOL", BookID.BOOK_Joel);
		REFERENCE_ABBREVIATIONS.put("AMO", BookID.BOOK_Amos);
		REFERENCE_ABBREVIATIONS.put("OBA", BookID.BOOK_Obad);
		REFERENCE_ABBREVIATIONS.put("JNA", BookID.BOOK_Jonah);
		REFERENCE_ABBREVIATIONS.put("MIC", BookID.BOOK_Mic);
		REFERENCE_ABBREVIATIONS.put("NAH", BookID.BOOK_Nah);
		REFERENCE_ABBREVIATIONS.put("HAB", BookID.BOOK_Hab);
		REFERENCE_ABBREVIATIONS.put("ZEP", BookID.BOOK_Zeph);
		REFERENCE_ABBREVIATIONS.put("HAG", BookID.BOOK_Hag);
		REFERENCE_ABBREVIATIONS.put("ZEC", BookID.BOOK_Zech);
		REFERENCE_ABBREVIATIONS.put("MAL", BookID.BOOK_Mal);
		REFERENCE_ABBREVIATIONS.put("MAT", BookID.BOOK_Matt);
		REFERENCE_ABBREVIATIONS.put("MRK", BookID.BOOK_Mark);
		REFERENCE_ABBREVIATIONS.put("LUK", BookID.BOOK_Luke);
		REFERENCE_ABBREVIATIONS.put("JHN", BookID.BOOK_John);
		REFERENCE_ABBREVIATIONS.put("ACT", BookID.BOOK_Acts);
		REFERENCE_ABBREVIATIONS.put("ROM", BookID.BOOK_Rom);
		REFERENCE_ABBREVIATIONS.put("CO1", BookID.BOOK_1Cor);
		REFERENCE_ABBREVIATIONS.put("CO2", BookID.BOOK_2Cor);
		REFERENCE_ABBREVIATIONS.put("GAL", BookID.BOOK_Gal);
		REFERENCE_ABBREVIATIONS.put("EPH", BookID.BOOK_Eph);
		REFERENCE_ABBREVIATIONS.put("PHP", BookID.BOOK_Phil);
		REFERENCE_ABBREVIATIONS.put("COL", BookID.BOOK_Col);
		REFERENCE_ABBREVIATIONS.put("TH1", BookID.BOOK_1Thess);
		REFERENCE_ABBREVIATIONS.put("TH2", BookID.BOOK_2Thess);
		REFERENCE_ABBREVIATIONS.put("TI1", BookID.BOOK_1Tim);
		REFERENCE_ABBREVIATIONS.put("TI2", BookID.BOOK_2Tim);
		REFERENCE_ABBREVIATIONS.put("TIT", BookID.BOOK_Titus);
		REFERENCE_ABBREVIATIONS.put("PHM", BookID.BOOK_Phlm);
		REFERENCE_ABBREVIATIONS.put("HEB", BookID.BOOK_Heb);
		REFERENCE_ABBREVIATIONS.put("JAM", BookID.BOOK_Jas);
		REFERENCE_ABBREVIATIONS.put("PE1", BookID.BOOK_1Pet);
		REFERENCE_ABBREVIATIONS.put("PE2", BookID.BOOK_2Pet);
		REFERENCE_ABBREVIATIONS.put("JN1", BookID.BOOK_1John);
		REFERENCE_ABBREVIATIONS.put("JN2", BookID.BOOK_2John);
		REFERENCE_ABBREVIATIONS.put("JN3", BookID.BOOK_3John);
		REFERENCE_ABBREVIATIONS.put("JDE", BookID.BOOK_Jude);
		REFERENCE_ABBREVIATIONS.put("REV", BookID.BOOK_Rev);
		REFERENCE_ABBREVIATIONS.put("BAR", BookID.BOOK_Bar);
		REFERENCE_ABBREVIATIONS.put("DNA", BookID.BOOK_AddDan);
		REFERENCE_ABBREVIATIONS.put("ESA", BookID.BOOK_AddEsth);
		REFERENCE_ABBREVIATIONS.put("PAZ", BookID.BOOK_PrAzar);
		REFERENCE_ABBREVIATIONS.put("BEL", BookID.BOOK_Bel);
		REFERENCE_ABBREVIATIONS.put("SUS", BookID.BOOK_Sus);
		REFERENCE_ABBREVIATIONS.put("GES", BookID.BOOK_1Esd);
		REFERENCE_ABBREVIATIONS.put("LES", BookID.BOOK_2Esd);
		REFERENCE_ABBREVIATIONS.put("ESG", BookID.BOOK_EsthGr);
		REFERENCE_ABBREVIATIONS.put("LJE", BookID.BOOK_EpJer);
		REFERENCE_ABBREVIATIONS.put("JDT", BookID.BOOK_Jdt);
		REFERENCE_ABBREVIATIONS.put("MAN", BookID.BOOK_PrMan);
		REFERENCE_ABBREVIATIONS.put("SIR", BookID.BOOK_Sir);
		REFERENCE_ABBREVIATIONS.put("TOB", BookID.BOOK_Tob);
		REFERENCE_ABBREVIATIONS.put("WIS", BookID.BOOK_Wis);
		REFERENCE_ABBREVIATIONS.put("MA1", BookID.BOOK_1Macc);
		REFERENCE_ABBREVIATIONS.put("MA2", BookID.BOOK_2Macc);
		REFERENCE_ABBREVIATIONS.put("MA3", BookID.BOOK_3Macc);
		REFERENCE_ABBREVIATIONS.put("MA4", BookID.BOOK_4Macc);
		REFERENCE_ABBREVIATIONS.put("PS2", BookID.BOOK_AddPs);
		REFERENCE_ABBREVIATIONS.put("PSS", BookID.BOOK_PssSol);
		REFERENCE_ABBREVIATIONS.put("LAO", BookID.BOOK_EpLao);
		REFERENCE_ABBREVIATIONS.put("ODE", BookID.BOOK_Odes);
		REFERENCE_ABBREVIATIONS.put("1EN", BookID.BOOK_1En);
		REFERENCE_ABBREVIATIONS.put("DNG", BookID.BOOK_DanGr);
		REFERENCE_ABBREVIATIONS.put("JUB", BookID.BOOK_Jub);
		REFERENCE_ABBREVIATIONS.put("EZA", BookID.BOOK_4Ezra);
		REFERENCE_ABBREVIATIONS.put("EZ5", BookID.BOOK_5Ezra);
		REFERENCE_ABBREVIATIONS.put("EZ6", BookID.BOOK_6Ezra);
		REFERENCE_ABBREVIATIONS.put("PS3", BookID.BOOK_5ApocSyrPss);
		REFERENCE_ABBREVIATIONS.put("BA2", BookID.BOOK_2Bar);
		REFERENCE_ABBREVIATIONS.put("BA4", BookID.BOOK_4Bar);
		REFERENCE_ABBREVIATIONS.put("LBA", BookID.BOOK_EpBar);
		REFERENCE_ABBREVIATIONS.put("MQ1", BookID.BOOK_1Meq);
		REFERENCE_ABBREVIATIONS.put("MQ2", BookID.BOOK_2Meq);
		REFERENCE_ABBREVIATIONS.put("MQ3", BookID.BOOK_3Meq);
		REFERENCE_ABBREVIATIONS.put("REP", BookID.BOOK_Rep);
		REFERENCE_ABBREVIATIONS.put("FRT", BookID.METADATA);
		REFERENCE_ABBREVIATIONS.put("INT", BookID.INTRODUCTION);
		REFERENCE_ABBREVIATIONS.put("BAK", BookID.APPENDIX_OTHER);
		REFERENCE_ABBREVIATIONS.put("CNC", BookID.APPENDIX_CONCORDANCE);
		REFERENCE_ABBREVIATIONS.put("GLS", BookID.APPENDIX_GLOSSARY);
		REFERENCE_ABBREVIATIONS.put("IXT", BookID.APPENDIX_TOPICAL);
		REFERENCE_ABBREVIATIONS.put("IXN", BookID.APPENDIX_NAMES);
		// map our own special books to Extra/Unknown
		REFERENCE_ABBREVIATIONS.put("XXA", BookID.INTRODUCTION_OT);
		REFERENCE_ABBREVIATIONS.put("XXB", BookID.INTRODUCTION_NT);
		REFERENCE_ABBREVIATIONS.put("XXC", BookID.BOOK_kGen);
		REFERENCE_ABBREVIATIONS.put("XXD", BookID.APPENDIX);
		REFERENCE_ABBREVIATIONS.put("XXE", BookID.DICTIONARY_ENTRY);
	}

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/OpenScripturesBibleVersificationSystem.xsd"));
	}

	@Override
	public void doImport(VersificationSet versifications, String... importArgs) throws Exception {
		File inputFile = new File(importArgs[0]);
		ValidateXML.validateFileBeforeParsing(getSchema(), inputFile);
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
		Unmarshaller u = ctx.createUnmarshaller();
		BibleVersificationSystem doc = (BibleVersificationSystem) u.unmarshal(inputFile);
		List<Reference> allRefs = new ArrayList<>();
		for (BibleBookVersification bbv : doc.getBibleBookVersification()) {
			BookID bid = REFERENCE_ABBREVIATIONS.get(bbv.getReferenceAbbreviation());
			if (bid == null) {
				System.out.println("WARNING: Skipping " + bbv.getNameEnglish() + " [" + bbv.getReferenceAbbreviation() + "]");
				continue;
			}
			if (bbv.getNumChapters() != bbv.getNumVerses().size())
				System.out.println("WARNING: Chapter count " + bbv.getNumChapters() + " of " + bid + " does not match verse info " + bbv.getNumVerses().size());
			for (NumVerses nv : bbv.getNumVerses()) {
				if (nv.getChapter() == 0 && bbv.getNumVerses().size() == 1) {
					System.out.println("WARNING: Changing chapter " + bid + " 0 to 1");
					nv.setChapter(1);
				}
				if (nv.getCombinedVerses() != null || nv.getReorderedVerses() != null)
					System.out.println("WARNING: Unsupported attribute for " + bid + " " + nv.getChapter());
				Set<Integer> omitted = new HashSet<>();
				if (nv.getOmittedVerses() != null) {
					for (String num : nv.getOmittedVerses().split(",")) {
						omitted.add(Integer.parseInt(num));
					}
				}
				for (int j = 1; j <= nv.getValue(); j++) {
					if (!omitted.contains(j)) {
						allRefs.add(new Reference(bid, nv.getChapter(), "" + j));
					}
				}
			}
		}
		versifications.add(Arrays.asList(Versification.fromReferenceList(importArgs[1], doc.getHeader().getWork().getTitle(), null, allRefs)), null);
	}

	@Override
	public boolean isExportSupported() {
		return false;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
	}
}
