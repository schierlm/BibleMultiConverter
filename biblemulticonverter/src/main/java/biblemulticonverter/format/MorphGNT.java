package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;

public class MorphGNT implements ImportFormat {

	public static final String[] HELP_TEXT = {
			"Importer for MorphGNT",
			"",
			"Usage: MorphGNT <directory>",
			"",
			"Download MorphGNT from <https://github.com/morphgnt/sblgnt>."
	};

	@Override
	public Bible doImport(File directory) throws Exception {
		Bible bible = new Bible("MorphGNT");
		File[] files = directory.listFiles();
		Arrays.sort(files);
		Verse currVerse = null;
		Visitor<RuntimeException> currVisitor = null;
		int idx = 0;
		for (File file : files) {
			String[] fileParts = file.getName().split("-");
			if (fileParts.length != 3 || !fileParts[2].equals("morphgnt.txt"))
				continue;
			BookID bid = BookID.fromZefId(Integer.parseInt(fileParts[0]) - 21);
			Book book = new Book(fileParts[1], bid, bid.getEnglishName(), bid.getEnglishName());
			bible.getBooks().add(book);
			System.out.println(file.getName());
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					String[] parts = line.split(" ");
					if (parts.length != 7)
						throw new IOException("Unparsable line: " + line);
					int bn = Integer.parseInt(parts[0].substring(0, 2));
					int cn = Integer.parseInt(parts[0].substring(2, 4));
					int vn = Integer.parseInt(parts[0].substring(4, 6));
					if (bn != bid.getZefID() - 39)
						throw new RuntimeException(bid.getZefID() + "/" + parts[0]);
					while (book.getChapters().size() < cn) {
						book.getChapters().add(new Chapter());
						if (currVerse != null)
							currVerse.finished();
						currVerse = null;
					}
					Chapter ch = book.getChapters().get(cn - 1);
					if (currVerse == null || !currVerse.getNumber().equals("" + vn)) {
						if (currVerse != null)
							currVerse.finished();
						currVerse = new Verse("" + vn);
						ch.getVerses().add(currVerse);
						currVisitor = currVerse.getAppendVisitor();
						idx = 1;
					} else {
						currVisitor.visitText(" ");
						idx++;
					}
					int pos = parts[3].indexOf(parts[4]);
					if (pos == -1)
						throw new RuntimeException(parts[3] + " " + parts[4]);
					currVisitor.visitText(parts[3].substring(0, pos));
					String rmac = convertToRMAC(parts[1], parts[2]);
					if (!parts[1].equals(convertToPartOfSpeech(rmac)) || !parts[2].equals(convertToParsing(rmac)))
						throw new RuntimeException(rmac + "/" + parts[1] + "=" + convertToPartOfSpeech(rmac) + "/" + parts[2] + "=" + convertToParsing(rmac));
					Visitor<RuntimeException> vv = currVisitor.visitGrammarInformation(null, null, null, new String[] { rmac }, new int[] { idx }, new String[] {"lemma", "morphgnt:normalized-word"}, new String[] {parts[6], parts[5]});
					vv.visitText(parts[4]);
					currVisitor.visitText(parts[3].substring(pos + parts[4].length()));
				}
			}
		}
		currVerse.finished();
		return bible;
	}

	private static Object convertToPartOfSpeech(String rmac) {
		String[] parts = rmac.split("-", 2);
		switch (parts[0]) {
			case "A": return "A-";
			case "CONJ": return "C-";
			case "ADV": return "D-";
			case "INJ": return "I-";
			case "N": return "N-";
			case "PREP": return "P-";
			case "T": return "RA";
			case "D": return "RD";
			case "I": return "RI";
			case "P": return "RP";
			case "R": return "RR";
			case "V": return "V-";
			case "PRT": return "X-";
			default: throw new RuntimeException(rmac);
		}
	}

	private static Object convertToParsing(String rmac) {
		String[] parts = rmac.split("-", 2);
		switch (parts[0]) {
			case "CONJ":
			case "ADV":
			case "INJ":
			case "PREP":
			case "PRT":
				if (parts.length == 1) return "--------";
				if (parts[1].matches("[CS]")) return "-------"+parts[1];
				throw new RuntimeException(rmac);
			case "A":
			case "N":
			case "T":
			case "D":
			case "I":
			case "P":
			case "R":
				return parts.length == 1 ? "--------" : convertDeclinedRMACToParsing(parts[1]);
			case "V":
				return convertVerbRMACToParsing(parts[1]);
			default:
				throw new RuntimeException(rmac);
		}
	}

	private static Object convertDeclinedRMACToParsing(String part) {
		if (!part.matches("^[123]"))
			part = "-" + part;
		String[] extra = part.substring(3).split("-");
		if (extra.length == 1)
			extra = new String[] { extra[0], "-" };
		if (extra[0].isEmpty())
			extra[0] = "-";
		return part.charAt(0) + "---" + part.substring(1, 3) + extra[0] + extra[1];
	}

	private static Object convertVerbRMACToParsing(String part) {
		if (part.startsWith("R"))
			part = "X" + part.substring(1);
		else if (part.startsWith("L"))
			part = "Y" + part.substring(1);
		if (part.charAt(2) == 'M')
			part = part.substring(0, 2)+ "D" + part.substring(3);
		if (part.matches("[PIFAXY][AMP][ISODNP]")) {
			return "-"+part+"----";
		} else if (part.matches("[PIFAXY][AMP][IDSONP]-[123][SP]")) {
			return part.charAt(4) + part.substring(0, 3)+"-"+part.charAt(5)+"--";
		} else if (part.matches("[PIFAXY][AMP][IDSONP]-[NGDAV][SP][MFN]")) {
			return "-" + part.substring(0, 3)+part.substring(4, 7)+"-";
		} else {
			throw new RuntimeException(part);
		}
	}

	private static String convertToRMAC(String speech, String parsing) {
		switch (speech) {
			case "A-": return convertDeclinedRMAC('A', parsing);
			case "C-": return convertSuffixRMAC("CONJ", parsing);
			case "D-": return convertSuffixRMAC("ADV", parsing);
			case "I-": return convertSuffixRMAC("INJ", parsing);
			case "N-": return convertDeclinedRMAC('N', parsing);
			case "P-": return convertSuffixRMAC("PREP", parsing);
			case "RA": return convertDeclinedRMAC('T', parsing);
			case "RD": return convertDeclinedRMAC('D', parsing);
			case "RI": return convertDeclinedRMAC('I', parsing);
			case "RP": return convertDeclinedRMAC('P', parsing);
			case "RR": return convertDeclinedRMAC('R', parsing);
			case "V-": return convertVerbRMAC(parsing);
			case "X-": return convertSuffixRMAC("PRT", parsing);
			default: throw new RuntimeException(speech + "/" + parsing);
		}
	}

	private static String convertSuffixRMAC(String prefix, String parsing) {
		if (parsing.equals("--------"))
			return prefix;
		else if (parsing.matches("-------[CS]"))
			return prefix + "-" + parsing.charAt(7);
		else
			throw new RuntimeException(parsing);
	}

	private static String convertDeclinedRMAC(char prefix, String parsing) {
		if (parsing.equals("--------"))
			return "" + prefix;
		if (!parsing.matches("[123-]---[NGDAV][SP][MFN-][CS-]"))
			throw new RuntimeException(parsing);
		char person = parsing.charAt(0);
		char cAse = parsing.charAt(4);
		char number = parsing.charAt(5);
		char gender = parsing.charAt(6);
		char degree = parsing.charAt(7);
		return prefix + "-" + (person == '-' ? "" : "" + person) + cAse + "" + number + (gender == '-' ? "" : "" + gender) + (degree == '-' ? "" : "-" + degree);
	}

	private static String convertVerbRMAC(String parsing) {
		if (!parsing.matches("[123-][PIFAXY][AMP][IDSONP][NGDAV-][SP-][MFN-]-"))
			throw new RuntimeException(parsing);
		char person = parsing.charAt(0);
		char tense = parsing.charAt(1);
		if (tense == 'X')
			tense = 'R';
		else if (tense == 'Y')
			tense = 'L';
		char voice = parsing.charAt(2);
		char mood = parsing.charAt(3);
		if (mood == 'D')
			mood = 'M';
		char cAse = parsing.charAt(4);
		char number = parsing.charAt(5);
		char gender = parsing.charAt(6);

		String extra;
		if (person == '-' && cAse == '-' && number == '-' && gender == '-') {
			extra = "";
		} else if (person != '-' && cAse == '-' && number != '-' && gender == '-') {
			extra = "-" + person + "" + number;
		} else if (person == '-' && cAse != '-' && number != '-' && gender != '-') {
			extra = "-" + cAse + "" + number + "" + gender;
		} else {
			throw new RuntimeException(parsing);
		}

		return "V-" + tense + "" + voice + "" + mood + "" + extra;
	}
}
