package biblemulticonverter.neue;

import biblemulticonverter.tools.Tool;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class NeUeInputPatcher implements Tool {

	public static final String[] HELP_TEXT = {
			"Patch syntax errors HTML from Neue Evangelistische Übersetzung before parsing",
			"",
			"Usage: NeUeInputPatcher <directory> <patchFile>"
	};

	@Override
	public void run(String... args) throws Exception {
		char[] buffer = new char[4096];
		File directory = new File(args[0]);
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(args[1]), StandardCharsets.UTF_8))) {
			String filename = br.readLine();
			while (filename != null && !filename.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				try (BufferedReader fileR = new BufferedReader(new InputStreamReader(new FileInputStream(new File(directory, filename)), StandardCharsets.ISO_8859_1))) {
					int len;
					while ((len = fileR.read(buffer)) != -1) {
						sb.append(buffer, 0, len);
					}
				}
				String content = sb.toString().replace("\r\n", "\n").replace('\r', '\n');
				String search = br.readLine();
				while (!search.isEmpty()) {
					String replace = br.readLine();
					content = content.replaceAll(search.replace('¶', '\n'), replace.replace('¶', '\n'));
					search = br.readLine();
				}
				try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(directory, filename)), StandardCharsets.ISO_8859_1)) {
					w.write(content.replace("\n", "\r\n"));
				}
				filename = br.readLine();
			}
		}
	}
}
