package biblemulticonverter.sqlite.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import biblemulticonverter.tools.Tool;

public class MyBibleZoneListDownloader implements Tool {

	public static final String[] HELP_TEXT = {
			"Download MyBible.Zone module list from module registry.",
			"",
			"Usage: MyBibleZoneListDownloader <listfile>.html [<servername|URL>]",
			"",
			"Download a list of available MyBible.Zone modules. The created HTML file contains",
			"JavaScript to download the modules from the available mirrors.",
			"",
			"In case the default server (http://myb.1gb.ru/) changed or is unavailable, you",
			"can give the server name (or a complete URL) as second argument."
	};

	@Override
	public void run(String... args) throws Exception {
		String url = "http://myb.1gb.ru/registry.zip";
		if (args.length == 2) {
			url = args[1];
			if (!url.contains("/"))
				url = "http://" + url + "/registry.zip";
		}
		try (BufferedReader br = new BufferedReader(new InputStreamReader(MyBibleZoneListDownloader.class.getResourceAsStream("/MyBibleZone/list-template-html.txt"), StandardCharsets.UTF_8));
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[0]), StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.contains("@@REGISTRY@@")) {
					try (ZipInputStream zis = new ZipInputStream(new URL(url).openStream())) {
						ZipEntry ze;
						while ((ze = zis.getNextEntry()) != null) {
							if (ze.getName().endsWith(".json")) {
								ByteArrayOutputStream baos = new ByteArrayOutputStream();
								byte[] buf = new byte[4096];
								int len;
								while ((len = zis.read(buf)) != -1) {
									baos.write(buf, 0, len);
								}
								line = line.replace("@@REGISTRY@@", new String(baos.toByteArray(), StandardCharsets.UTF_8));
							}
						}
					}
				}
				bw.write(line);
				bw.newLine();
			}
		}
	}
}
