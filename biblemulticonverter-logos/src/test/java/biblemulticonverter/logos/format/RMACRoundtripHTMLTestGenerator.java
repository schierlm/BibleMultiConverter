package biblemulticonverter.logos.format;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Generate a HTML page that validates that the RoundtripHTML script can handle
 * all possible RMAC.
 */
public class RMACRoundtripHTMLTestGenerator {
	public static void main(String[] args) throws Exception {
		try(BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("rmactest.html"), StandardCharsets.UTF_8))) {
			bw.write("<html>\n<script type=\"text/javascript\" src=\"script.js\"></script>\n<script type=\"text/javascript\">\n");
			bw.write("let choices = [");
			for(String pattern: RMACConversionTest.computePatterns()) {
				for(String expansion : RMACConversionTest.expandPattern(pattern)) {
					bw.write("'"+expansion+"',");
				}
			}
			bw.write("];\n");
			bw.write("window.onload = function() {\n");
			bw.write("\tlet errors = \"\", log = \"\", seen = {};\n\tfor (let rmac of choices) { \n\t\tlet x;\n");
			bw.write("\t\ttry {\n\t\t\tx = renderRMAC(rmac);\n\t\t} catch (e) {\n\t\t\terrors +=\"<br>\"+rmac+\" results in error: \"+e;\n");
			bw.write("\t\t\tcontinue;\n\t\t}\n\t\tif (seen[x]) {\n\t\t\terrors += \"<br>Both \"+seen[x]+\" and \"+rmac+\" render to the same value: \"+x;\n");
			bw.write("\t\t} else if (x == rmac || x.indexOf(\"undefined\") != -1) {\n\t\t\terrors += \"<br>\"+rmac + \" renders as \" + x;\n\t\t} else {\n");
			bw.write("\t\t\tlog +=\"<br>\"+rmac+\" renders as \" + x;\n\t\t}\n\t\tseen[x] = rmac;\n\t}\n");
			bw.write("\tdocument.getElementById(\"errors\").innerHTML = errors; document.getElementById(\"log\").innerHTML = log;\n};\n");
			bw.write("</script><body><h1>RMAC Test</h1><div id=\"errors\">(errors)</div><h2>Log</h2><div id=\"log\"></div></body></html>");
		}
	}
}
