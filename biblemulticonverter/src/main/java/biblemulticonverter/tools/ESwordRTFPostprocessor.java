package biblemulticonverter.tools;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class ESwordRTFPostprocessor implements Tool {

	public static final String[] HELP_TEXT = {
			"Postprocess RTF for exporting to E-Sword",
			"",
			"Usage: ESwordRTFPostprocessor <marker> <infile.rtf> <outfile.rtf>",
			"",
			"As ToolTipTool NT sometimes introduces fake newlines when importing from HTML, a marker can",
			"be written to the end of every line (which should not exist elsewhere in the file), so that",
			"it can later be used to identify and remove the fake lines as well as the markers.",
			"",
			"To use, convert the output of ESwordHTML tool to RTF (using ToolTipTool NT), then run the",
			"postprocessor over the resulting files, then process the postprocessor output with",
			"ToolTipTool as usual."
	};

	@Override
	public void run(String... args) throws Exception {
		String marker = args[0];
		String infile = args[1];
		String outfile = args[2];
		char[] buffer = new char[4096];
		StringBuilder sb = new StringBuilder();
		try (Reader r = new InputStreamReader(new FileInputStream(infile), StandardCharsets.ISO_8859_1)) {
			int len;
			while ((len = r.read(buffer)) != -1) {
				sb.append(buffer, 0, len);
			}
		}
		int pos = -1, copyPos = 0;
		String orig = sb.toString();
		sb.setLength(0);
		while ((pos = orig.indexOf("\\par", pos + 1)) != -1) {
			if (orig.charAt(pos + 4) >= 'a' && orig.charAt(pos + 4) <= 'z')
				continue; // part of a word;
			if (pos >= marker.length() && orig.substring(pos - marker.length(), pos).equals(marker)) {
				// marker found; remove marker and keep line break
				sb.append(orig.substring(copyPos, pos - marker.length()));
				copyPos = pos;
			} else {
				// no marker found; remove line break instead (probably with
				// trailing space)
				sb.append(orig.substring(copyPos, pos));
				copyPos = pos + 4;
				if (orig.charAt(copyPos) == ' ') {
					copyPos++;
				}
			}
		}
		sb.append(orig.substring(copyPos));
		String result = sb.toString();
		if (result.indexOf(marker) != -1) {
			System.out.println("WARNING: Markers remain in output file!");
		}
		try (Writer w = new OutputStreamWriter(new FileOutputStream(outfile), StandardCharsets.ISO_8859_1)) {
			w.write(result);
		}
	}
}
