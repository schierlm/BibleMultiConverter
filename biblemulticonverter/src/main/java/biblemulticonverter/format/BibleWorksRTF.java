package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Character.UnicodeScript;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.text.Document;
import javax.swing.text.rtf.RTFEditorKit;

import biblemulticonverter.data.Bible;

public class BibleWorksRTF extends BibleWorks {

	public static final String[] HELP_TEXT = {
			"RTF import and export format for BibleWorks",
			"",
			"This supports only a very rudimentary subset of RTF.",
			"",
			"Therefore, if possible (especially when importing), you should prefer to do the",
			"RTF to text conversion in a full-fledged word processor and then use the BibleWorks",
			"text import format instead."
	};

	@Override
	public Bible doImport(File inputFile) throws Exception {
		String rtf = new String(Files.readAllBytes(inputFile.toPath()), StandardCharsets.ISO_8859_1);
		RTFEditorKit rtfParser = new RTFEditorKit();
		Document document = rtfParser.createDefaultDocument();
		// Java RTFEditorKit does not handle \ansicpg tag correctly,
		// therefore fix critical characters manually
		for (int i = 0x80; i < 0xA0; i++) {
			int codepoint = new String(new byte[] { (byte) i }, "cp1252").charAt(0);
			if (codepoint != 0xFFFD) {
				rtf = rtf.replace(String.format("\\'%02x", i), "\\u" + codepoint + "?");
			}
		}
		rtfParser.read(new ByteArrayInputStream(rtf.getBytes(StandardCharsets.ISO_8859_1)), document, 0);
		String text = document.getText(0, document.getLength());
		return doImport(new BufferedReader(new StringReader(text)));
	}

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		StringWriter sw = new StringWriter();
		doExport(bible, sw);
		sw.close();
		try (Writer w = new OutputStreamWriter(new FileOutputStream(exportArgs[0]), StandardCharsets.UTF_8)) {
			writeRTF(w, sw.toString());
		}
	}

	private static void writeRTF(Writer w, String plainText) throws IOException {
		boolean hadGreek = false;
		boolean[] mayMerge = new boolean[1];
		w.write("{\\rtf1\\adeflang1037\\fbidis\\ansi\\ansicpg1252\\deff0\\deflang1033{\\fonttbl{\\f0\\fcharset0 Arial;}{\\f1\\fcharset161 SBL Greek;}{\\f2\\fcharset177 SBL Hebrew;}{\\f3\\fnil Bwtranshs;}{\\f4\\fnil bwcyrl;}{\\f5\\fnil Bwviet;}{\\f6\\fnil Bweeti;}}\\pard\\plain\\fs20\r\n");
		for (String line : plainText.split("\r\n|\r|\n")) {
			String[] parts = line.split(" ", 3);
			w.write("{\\f0 \\fs24\\ltrch\\lang1033 " + escapeRTF(parts[0] + " " + parts[1], false) + "  }");
			String verse = parts[2];
			if (verse.startsWith(" "))
				verse = verse.substring(1);
			if (verse.isEmpty() && !hadGreek)
				w.write("{\\f0\\fs20 }");
			while (!verse.isEmpty()) {
				boolean greek = isGreek(verse, 0, null);
				hadGreek |= greek;
				w.write(greek ? "{\\f1\\fs24 " : "{\\f0\\fs20 ");
				int end = 0;
				while (end < verse.length()) {
					boolean stillGreek = isGreek(verse, end, mayMerge);
					if (greek != stillGreek && !mayMerge[0])
						break;
					end++;
				}
				w.write(escapeRTF(verse.substring(0, end), greek));
				w.write("}");
				verse = verse.substring(end);
			}
			w.write("\\par \\ql \r\n");
		}
		w.write("}");
	}

	private static boolean isGreek(String str, int pos, boolean[] mayMerge) {
		while ("([".indexOf(str.charAt(pos)) != -1 && pos + 1 < str.length())
			pos++;
		if (mayMerge != null)
			mayMerge[0] = " .,:;!?·])".indexOf(str.charAt(pos)) != -1;
		return UnicodeScript.of(str.charAt(pos)) == UnicodeScript.GREEK;
	}

	private static String escapeRTF(String str, boolean greek) throws IOException {
		StringBuilder sb = new StringBuilder(str.length());
		for (int i = 0; i < str.length(); i++) {
			char ch = str.charAt(i);
			if (ch == '{' || ch == '}' || ch == '\\') {
				sb.append('\\').append(ch);
			} else if (ch >= ' ' && ch <= '~') {
				sb.append(ch);
			} else if (!greek && ch < 0x100) {
				sb.append(String.format("\\'%02x", (int) ch));
			} else if (!greek && "€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ".indexOf(ch) != -1) {
				int codepoint = ("" + ch).getBytes("windows-1252")[0] & 0xFF;
				if (codepoint < 0x80 || codepoint >= 0xA0)
					throw new RuntimeException();
				sb.append(String.format("\\'%02x", codepoint));
			} else {
				sb.append("\\u" + (int) ch + "?");
			}
		}
		return sb.toString();
	}
}
