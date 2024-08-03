package biblemulticonverter.format.paratext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import biblemulticonverter.data.Bible;
import biblemulticonverter.tools.Validate;

public class USX3Test {

	@Test
	public void test_roundtrip_USX3_to_USX3() throws Exception {
		// 1. Import USX3 then export to USX3
		// 2. Assert that the original file has the same contents as the exported file by reading them both and
		//    dumping them to a diffable format.

		File originalFile = getResource("/usx3/GEN-with-only-USX3-end-milestones.usx");
		File resultFile = createTempFile("usx3-export", ".usx");
		USX3 usx3 = new USX3();

		ParatextBook paratextBook = usx3.doImportBook(originalFile);
		assertNotNull(paratextBook);
		usx3.doExportBook(paratextBook, resultFile);

		// Compare the two files by importing them and dumping them so they can easily be compared
		File expected = createTempFile("expected", ".txt");
		File actual = createTempFile("actual", ".txt");

		dump(usx3.doImportBook(originalFile), expected);
		dump(usx3.doImportBook(resultFile), actual);

		assertEqualLines(expected, actual);
	}

	@Test
	public void test_USFM2_end_milestone_insertion() throws Exception {
		// 1. Import from USFM 2, the USFM 2 importer should add chapter and verse end milestones to the internal
		//    format.
		// 2. Assert that when imported the result is equal to the same file but in USX 3 format (without any USX 3
		//    features except for the end milestones)

		File originalFile = getResource("/usfm2/GEN.usfm");
		File resultFile = createTempFile("usx3-export", ".usx");

		USFM usfm = new USFM(true);
		ParatextBook paratextBook = usfm.doImportBook(originalFile);
		assertNotNull(paratextBook);

		USX3 usx3 = new USX3();
		usx3.doExportBook(paratextBook, resultFile);

		// Compare the two files by importing them and dumping them so they can easily be compared
		File expected = createTempFile("expected", ".txt");
		File actual = createTempFile("actual", ".txt");

		dump(usx3.doImportBook(getResource("/usx3/GEN-with-only-USX3-end-milestones.usx")), expected);
		dump(usx3.doImportBook(resultFile), actual);

		assertEqualLines(expected, actual);
	}

	@Test
	public void test_USX2_end_milestone_insertion() throws Exception {
		// 1. Import from USX 2, the USX 2 importer should add chapter and verse end milestones to the internal
		//    format.
		// 2. Assert that when imported the result is equal to the same file but in USX 3 format (without any USX 3
		//    features except for the end milestones)

		File originalFile = getResource("/usx2/GEN.usx");
		File resultFile = createTempFile("usx3-export", ".usx");

		USX usx = new USX();
		ParatextBook paratextBook = usx.doImportBook(originalFile);
		assertNotNull(paratextBook);

		USX3 usx3 = new USX3();
		usx3.doExportBook(paratextBook, resultFile);

		// Compare the two files by importing them and dumping them so they can easily be compared
		File expected = createTempFile("expected", ".txt");
		File actual = createTempFile("actual", ".txt");

		dump(usx3.doImportBook(getResource("/usx3/GEN-with-only-USX3-end-milestones.usx")), expected);
		dump(usx3.doImportBook(resultFile), actual);

		assertEqualLines(expected, actual);
	}

	@Test
	public void test_import_USX3_export_USX() throws Exception {
		File originalFile = getResource("/usx3/GEN-with-only-USX3-end-milestones.usx");
		File resultFile = createTempFile("usx-export", ".usx");

		USX3 usx3 = new USX3();
		ParatextBook paratextBook = usx3.doImportBook(originalFile);

		USX usx = new USX();
		usx.doExportBook(paratextBook, resultFile);
	}

	@Test
	public void test_import_USX3_export_USFM2() throws Exception {
		File originalFile = getResource("/usx3/GEN-with-only-USX3-end-milestones.usx");
		File resultFile = createTempFile("usfm-export", ".usfm");

		USX3 usx3 = new USX3();
		ParatextBook paratextBook = usx3.doImportBook(originalFile);

		USFM usfm = new USFM();
		usfm.doExportBook(paratextBook, resultFile);
	}

	/**
	 * Tests whether or not a single space between two XML nodes is preserved:
	 * <p>
	 * Sample:
	 * <pre>
	 * &lt;char style="nd&gt;Lorem&lt;/char&gt; &lt;note caller="+" style="f"&gt;Dolor&lt;/note&gt;Ipsum
	 * </pre>
	 * <p>
	 * Expected result (without the note):
	 * <pre>
	 * Lorem Ipsum
	 * </pre>
	 * <p>
	 * Wrong result:
	 * <pre>
	 * LoremIpsum
	 * </pre>
	 */
	@Test
	public void single_whitespace_between_xml_nodes_is_preserved() throws Exception {
		File originalFile = getResource("/whitespace-test/input.usx");
		File resultFile = createTempFile("dump", ".txt");

		USX3 usx3 = new USX3();
		ParatextBook paratextBook = usx3.doImportBook(originalFile);
		dump(paratextBook, resultFile);

		assertEqualLines(getResource("/whitespace-test/expected-output.txt"), resultFile);
	}

	private static void dump(ParatextBook book, File file) throws IOException {
		ParatextDump paratextDump = new ParatextDump();
		paratextDump.doExportBook(book, file);
	}

	private static void assertEqualLines(File expected, File actual) throws IOException {
		Iterator<String> expectedLines = Files.readAllLines(expected.toPath()).iterator();
		Iterator<String> actualLines = Files.readAllLines(actual.toPath()).iterator();

		while (expectedLines.hasNext() && actualLines.hasNext()) {
			String expectedLine = expectedLines.next();
			String actualLine = actualLines.next();
			assertEquals(expectedLine, actualLine);
		}
		assertEquals(expectedLines.hasNext(), actualLines.hasNext());
	}

	public static File createTempFile(String prefix, String suffix) throws IOException {
		File tempFile = File.createTempFile(prefix, suffix);
		tempFile.deleteOnExit();
		return tempFile;
	}

	public static File getResource(String path) {
		try {
			URI pathUri = USX3Test.class.getResource(path).toURI();
			if (pathUri.getScheme().equals("file"))
				return new File(pathUri);
			File tempFile = createTempFile("input", path.substring(path.lastIndexOf('.')));
			try (InputStream in = USX3Test.class.getResourceAsStream(path);
					OutputStream out = new FileOutputStream(tempFile)) {
				byte[] buf = new byte[4096];
				int len;
				while ((len = in.read(buf)) != -1)
					out.write(buf, 0, len);
			}
			return tempFile;
		} catch (URISyntaxException | IOException e) {
			throw new RuntimeException(e);
		}
	}
}
