package biblemulticonverter.logos.format;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Generate a HTML page that validates that the RoundtripHTML script can handle
 * all possible WIVU morphology tags.
 */
public class WIVURoundtripHTMLTestGenerator {
	// TODO
	public static void main(String[] args) throws Exception {
		try(BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("wivutest.html"), StandardCharsets.UTF_8))) {
			bw.write("<html>\n<script type=\"text/javascript\" src=\"script.js\"></script>\n<script type=\"text/javascript\">\n");
			bw.write("let choices = [");
			for(String pattern: WIVUConversionTest.computePatterns()) {
				for(String expansion : WIVUConversionTest.expandPattern(pattern)) {
					bw.write("'"+expansion+"',");
				}
			}
			bw.write("];\n");
			bw.write("window.onload = function() {\n");
			bw.write("\tlet errors = \"\", log = \"\", seen = {};\n\tfor (let wivu of choices) { \n\t\tlet x;\n");
			bw.write("\t\ttry {\n\t\t\tx = renderWIVU(wivu);\n\t\t} catch (e) {\n\t\t\terrors +=\"<br>\"+wivu+\" results in error: \"+e;\n");
			bw.write("\t\t\tcontinue;\n\t\t}\n\t\tif (seen[x]) {\n\t\t\terrors += \"<br>Both \"+seen[x]+\" and \"+wivu+\" render to the same value: \"+x;\n");
			bw.write("\t\t} else if (x == wivu || x.endsWith(wivu.substring(1)) || x.indexOf(\"undefined\") != -1) {\n\t\t\terrors += \"<br>\"+wivu + \" renders as \" + x;\n\t\t} else {\n");
			bw.write("\t\t\tlog +=\"<br>\"+wivu+\" renders as \" + x;\n\t\t}\n\t\tseen[x] = wivu;\n\t}\n");
			bw.write("\tdocument.getElementById(\"errors\").innerHTML = errors; document.getElementById(\"log\").innerHTML = log;\n};\n");
			bw.write("</script><body><h1>WIVU Test</h1><div id=\"errors\">(errors)</div><h2>Log</h2><div id=\"log\"></div></body></html>");
		}
	}
}
