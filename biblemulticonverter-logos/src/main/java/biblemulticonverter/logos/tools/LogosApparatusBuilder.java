package biblemulticonverter.logos.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biblemulticonverter.data.BookID;
import biblemulticonverter.tools.Tool;

public class LogosApparatusBuilder implements Tool {

	public static final String[] HELP_TEXT = {
			"Create a Logos Personal book from SBLGNT Apparatus",
			"",
			"Usage: LogosApparatusBuilder <apparatusdir> <txtfile> <outfile>",
			"",
			"<apparatusdir> points to <https://github.com/LogosBible/SBLGNT/tree/master/data/sblgntapp>,",
			"<txtfile> is the apparatus text file from <https://community.logos.com/kb/articles/2793-word-numbers>,",
			"and <outfile> is the output filename.",
			"Convert the resulting HTML to .docs with either LibreOffice or MS Word."
	};

	public void run(String... args) throws Exception {
		Map<String, List<List<String>>> variationGroups = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(args[1]), StandardCharsets.UTF_8))) {
			String line = br.readLine();
			if (!line.equals("PREFIX\tgnt/"))
				throw new IOException("Invalid word number file: " + line);
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\t");
				if (parts.length != 2)
					throw new IOException(line);
				List<List<String>> verseData = new ArrayList<>();
				List<String> vgData = new ArrayList<>();
				verseData.add(vgData);
				variationGroups.put(parts[0], verseData);
				for (String word : parts[1].split(" ")) {
					if (word.equals("/")) {
						vgData = new ArrayList<>();
						verseData.add(vgData);
					} else {
						vgData.add(word);
					}
				}
			}
		}
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(args[2])), StandardCharsets.UTF_8))) {
			bw.write("<html><head>\n" +
					"<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" />\n" +
					"<style>" +
					"body, h1, h2 { font-family: \"Times New Roman\";}\n" +
					"a { color: black; text-decoration: none; so-language: en-US;}\n" +
					"h1 {font-size: 24pt;}\n" +
					"h2 {font-size: 22pt;}\n" +
					"</style>\n" +
					"</head><body lang=\"de-DE\">\n");
			Pattern versePtn = Pattern.compile("[A-Za-z1-3 ]+ ([0-9]+):([0-9]+)");
			Pattern greekPtn = Pattern.compile("[\\p{IsGreek}\u02BC\u02B9\u0387]++[.,\u00B7]?");
			for (int zefid = BookID.BOOK_Matt.getZefID(); zefid <= BookID.BOOK_Rev.getZefID(); zefid++) {
				BookID bid = BookID.fromZefId(zefid);
				try (BufferedReader br1 = new BufferedReader(new InputStreamReader(new FileInputStream(new File(new File(args[0]), bid.getOsisID() + ".txt")), StandardCharsets.UTF_8))) {
					String line = br1.readLine();
					bw.write("<h1>" + line + "</h1>\n");
					line = br1.readLine();
					if (!line.isEmpty())
						throw new RuntimeException(line);
					while ((line = br1.readLine()) != null) {
						String verse = line;
						Matcher m = versePtn.matcher(verse);
						if (!m.matches())
							throw new IOException(verse);
						List<List<String>> groups = variationGroups.get(bid.getOsisID() + " " + m.group(1) + ":" + m.group(2));
						if (groups == null) {
							throw new IOException(verse + "/" + bid.getOsisID() + " " + m.group(1) + ":" + m.group(2));
						}
						bw.write("<h2>[[@BibleSBLGNT:" + verse + "]]" + verse + "</h2>\n");
						int groupIndex = 0;
						while ((line = br1.readLine()) != null) {
							if (line.isEmpty())
								break;
							int pos = line.indexOf(']');
							if (pos != -1) {
								line = "<b>" + line.substring(0, pos + 1) + "</b>" + line.substring(pos + 1);
							}
							StringBuffer sb = new StringBuffer(groupIndex == 0 ? "<p>" : "<br>\n");
							Matcher mm = greekPtn.matcher(line);
							List<String> wordNums = groups.get(groupIndex);
							int cnt = 0;
							while (mm.find()) {
								String word = mm.group(0);
								String wordNum = wordNums.get(cnt);
								cnt++;
								if (!wordNum.isEmpty() && !wordNum.equals("?")) {
									String[] wordNumParts = wordNum.split("\\|");
									if (wordNumParts[0].isEmpty())
										throw new IOException(wordNumParts[0]);
									word = "<a href=\"WordNumber:gnt/" + wordNumParts[0].replaceFirst("/.*", "") + "\">" + word + "</a>";
									for (int i = 1; i < wordNumParts.length; i++) {
										String[] kv = wordNumParts[i].split("=");
										word += " <a href=\"WordNumber:gnt/" + kv[1].replaceFirst("/.*", "") + "\"><i>(" + kv[0] + ")</i>";
									}
								}
								mm.appendReplacement(sb, Matcher.quoteReplacement(word));
							}
							if (cnt != wordNums.size())
								throw new IOException(verse + "@" + groupIndex);
							mm.appendTail(sb);
							bw.write(sb.toString());
							groupIndex++;
						}
						bw.write("</p>\n");
					}
				}
			}
			bw.write("</body></html>");
		}
	}
}
