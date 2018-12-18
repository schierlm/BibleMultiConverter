package biblemulticonverter.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import biblemulticonverter.data.Bible;

/**
 * Roundtrip convert {@code CoverageTest.bmc} using the supported roundtrip
 * formats and validate that it stays the same.
 */
public class RoundtripCoverageTest {

	public static final List<Class<? extends RoundtripFormat>> FULL_EXPORT_IMPORT_ROUNDTRIP_MODULES = Arrays.asList(Compact.class, Diffable.class, RoundtripHTML.class, RoundtripXML.class, RoundtripODT.class);

	@Test
	public void testExportImportRoundtripCoverage() throws Exception {
		String testFile;
		try (Reader r = new BufferedReader(new InputStreamReader(RoundtripCoverageTest.class.getResourceAsStream("/CoverageTest.bmc"), StandardCharsets.UTF_8));
				StringWriter sw = new StringWriter()) {
			char[] buffer = new char[4096];
			int len;
			while ((len = r.read(buffer)) != -1) {
				sw.write(buffer, 0, len);
			}
			testFile = sw.toString();
		}
		Compact compact = new Compact();
		Bible bible = compact.doImport(new BufferedReader(new StringReader(testFile)));
		assertCompactExport(testFile, compact, bible);

		for (Class<? extends RoundtripFormat> module : FULL_EXPORT_IMPORT_ROUNDTRIP_MODULES) {
			File tempFile = File.createTempFile("~rtt", ".tmp");
			Assert.assertTrue(tempFile.delete());
			RoundtripFormat format = module.newInstance();
			Assert.assertTrue(format.isExportImportRoundtrip());
			Assert.assertTrue(format.isImportExportRoundtrip());
			format.doExport(bible, tempFile.getCanonicalPath());
			Bible roundtripBible = format.doImport(tempFile);
			assertCompactExport(testFile, compact, roundtripBible);
			if (tempFile.isDirectory()) {
				for (File file : tempFile.listFiles()) {
					if (file.isDirectory()) {
						for (File file2 : file.listFiles()) {
							Assert.assertTrue(file2.delete());
						}
						Assert.assertTrue(file.delete());
					} else {
						Assert.assertTrue(file.delete());
					}
				}
				Assert.assertTrue(tempFile.delete());
			} else {
				Assert.assertTrue(tempFile.delete());
			}
		}
	}

	private void assertCompactExport(String expected, Compact compact, Bible bible) throws IOException {
		List<String> danglingReferences = new ArrayList<String>();
		bible.validate(danglingReferences);
		Assert.assertTrue(danglingReferences.isEmpty());
		StringWriter sw = new StringWriter();
		compact.doExport(bible, sw);
		Assert.assertEquals(expected, sw.toString());
	}
}
