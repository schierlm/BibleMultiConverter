package biblemulticonverter.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for handling MobiPocket .bxr files.
 */
public class MobiPocketBXR {

	final String name;
	final String title;
	public final List<BookInfo> books = new ArrayList<BookInfo>();

	public MobiPocketBXR(String name, File f) throws IOException {
		this.name = name;
		try (final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
			title = br.readLine();
			String line;
			while ((line = br.readLine()) != null) {
				String[] fields = line.split("\\|");
				if (fields.length != 3)
					throw new RuntimeException(fields.length + "/" + line);
				books.add(new BookInfo(fields[0], fields[1], Integer.parseInt(fields[2])));
			}
		}
	}

	public static class BookInfo {
		public final String book;
		public final int chapterCount;
		public final String ref;

		public BookInfo(String book, String ref, int chapterCount) {
			this.book = book;
			this.ref = ref;
			this.chapterCount = chapterCount;
		}
	}
}
