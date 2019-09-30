package biblemulticonverter.versification;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.format.Accordance;

public class AccordanceReferenceList implements VersificationFormat {

	public static final String[] HELP_TEXT = {
			"Build a versification from a plain text reference list (English book names) exported from Accordance.",
			"",
			"Usage for import: AccordanceReferenceList <filename> <name>"
	};

	private static final Map<String, BookID> BOOK_NAME_MAP = new HashMap<>();

	static {
		for (Map.Entry<BookID, String> entry : Accordance.BOOK_NAME_MAP.entrySet()) {
			BOOK_NAME_MAP.put(entry.getValue(), entry.getKey());
		}
		// long names
		BOOK_NAME_MAP.put("Genesis", BookID.BOOK_Gen);
		BOOK_NAME_MAP.put("Exodus", BookID.BOOK_Exod);
		BOOK_NAME_MAP.put("Leviticus", BookID.BOOK_Lev);
		BOOK_NAME_MAP.put("Numbers", BookID.BOOK_Num);
		BOOK_NAME_MAP.put("Deuteronomy", BookID.BOOK_Deut);
		BOOK_NAME_MAP.put("Joshua", BookID.BOOK_Josh);
		BOOK_NAME_MAP.put("Judges", BookID.BOOK_Judg);
		BOOK_NAME_MAP.put("1Samuel", BookID.BOOK_1Sam);
		BOOK_NAME_MAP.put("2Samuel", BookID.BOOK_2Sam);
		BOOK_NAME_MAP.put("1Chronicles", BookID.BOOK_1Chr);
		BOOK_NAME_MAP.put("2Chronicles", BookID.BOOK_2Chr);
		BOOK_NAME_MAP.put("Nehemiah", BookID.BOOK_Neh);
		BOOK_NAME_MAP.put("Esther", BookID.BOOK_Esth);
		BOOK_NAME_MAP.put("Psalms", BookID.BOOK_Ps);
		BOOK_NAME_MAP.put("Proverbs", BookID.BOOK_Prov);
		BOOK_NAME_MAP.put("Ecclesiastes", BookID.BOOK_Eccl);
		BOOK_NAME_MAP.put("Isaiah", BookID.BOOK_Isa);
		BOOK_NAME_MAP.put("Jeremiah", BookID.BOOK_Jer);
		BOOK_NAME_MAP.put("Lamentations", BookID.BOOK_Lam);
		BOOK_NAME_MAP.put("Ezekiel", BookID.BOOK_Ezek);
		BOOK_NAME_MAP.put("Daniel", BookID.BOOK_Dan);
		BOOK_NAME_MAP.put("Hosea", BookID.BOOK_Hos);
		BOOK_NAME_MAP.put("Obadiah", BookID.BOOK_Obad);
		BOOK_NAME_MAP.put("Micah", BookID.BOOK_Mic);
		BOOK_NAME_MAP.put("Nahum", BookID.BOOK_Nah);
		BOOK_NAME_MAP.put("Habakkuk", BookID.BOOK_Hab);
		BOOK_NAME_MAP.put("Zephaniah", BookID.BOOK_Zeph);
		BOOK_NAME_MAP.put("Haggai", BookID.BOOK_Hag);
		BOOK_NAME_MAP.put("Zechariah", BookID.BOOK_Zech);
		BOOK_NAME_MAP.put("Malachi", BookID.BOOK_Mal);
		BOOK_NAME_MAP.put("Manasse", BookID.BOOK_PrMan);
		BOOK_NAME_MAP.put("Matthew", BookID.BOOK_Matt);
		BOOK_NAME_MAP.put("Romans", BookID.BOOK_Rom);
		BOOK_NAME_MAP.put("1Corinthians", BookID.BOOK_1Cor);
		BOOK_NAME_MAP.put("2Corinthians", BookID.BOOK_2Cor);
		BOOK_NAME_MAP.put("Galatians", BookID.BOOK_Gal);
		BOOK_NAME_MAP.put("Ephesians", BookID.BOOK_Eph);
		BOOK_NAME_MAP.put("Philippians", BookID.BOOK_Phil);
		BOOK_NAME_MAP.put("Colossians", BookID.BOOK_Col);
		BOOK_NAME_MAP.put("1Thessalonians", BookID.BOOK_1Thess);
		BOOK_NAME_MAP.put("2Thessalonians", BookID.BOOK_2Thess);
		BOOK_NAME_MAP.put("1Timothy", BookID.BOOK_1Tim);
		BOOK_NAME_MAP.put("2Timothy", BookID.BOOK_2Tim);
		BOOK_NAME_MAP.put("Titus", BookID.BOOK_Titus);
		BOOK_NAME_MAP.put("Philemon", BookID.BOOK_Phlm);
		BOOK_NAME_MAP.put("Hebrews", BookID.BOOK_Heb);
		BOOK_NAME_MAP.put("1Peter", BookID.BOOK_1Pet);
		BOOK_NAME_MAP.put("2Peter", BookID.BOOK_2Pet);
		BOOK_NAME_MAP.put("Revelation", BookID.BOOK_Rev);
	}

	@Override
	public void doImport(VersificationSet versifications, String... importArgs) throws Exception {
		List<Reference> allRefs = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(importArgs[0]), StandardCharsets.UTF_16))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.trim().replace(',', ':').replace(' ', ':').split(":");
				if (parts.length == 2 && Arrays.asList("Obadiah", "Obad.", "Philemon", "Philem.", "2John", "3John", "Jude", "Man.", "Manasse").contains(parts[0]))
					parts = new String[] { parts[0], "1", parts[1] };
				if (parts.length != 3)
					throw new IOException("Unsupported verse reference: " + line);
				BookID bid = BOOK_NAME_MAP.get(parts[0]);
				if (bid == null)
					throw new IOException("Unsupported book name (did you use English Book Names?): " + parts[0]);
				int chapter = Integer.parseInt(parts[1]);
				int verse = Integer.parseInt(parts[2]);
				allRefs.add(new Reference(bid, chapter == 0 ? 1 : chapter,
						(verse == 0 ? "1/t" : "" + verse) + (chapter == 0 ? "/p" : "")));
			}
		}
		versifications.add(Arrays.asList(Versification.fromReferenceList(importArgs[1], null, null, allRefs)), null);
	}

	@Override
	public boolean isExportSupported() {
		return false;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
	}
}
