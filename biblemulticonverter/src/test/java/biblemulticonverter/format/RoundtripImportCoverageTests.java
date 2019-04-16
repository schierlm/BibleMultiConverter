package biblemulticonverter.format;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

import biblemulticonverter.data.Bible;

public class RoundtripImportCoverageTests {

	@Test
	public void testBibleWorks() throws Exception {
		testImportExportRoundtripCoverage(BibleWorks.class, ("" +
				"Gen 1:1  In [the] beginning<N1>, /God/ { These marks are not parsed yet } created the Heaven<N2> and the Earth<Ra> { <p><nsup>1</nsup> <i>Beg</i> <p>(2) <b>H</b><p> <p><rsup>a</rsup> X }\n" +
				"Gen 1:2  Word@aprdf-p^(apraf-p/aprgf-p) <33><R*>{ <rsup>*</rsup> Star }\n" +
				"Gen 1:3  <N1><N4> { (1) One (4) Four }\n").getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void testBibleWorksRTF() throws Exception {
		testImportExportRoundtripCoverage(BibleWorksRTF.class, ("" +
				"{\\rtf1\\adeflang1037\\fbidis\\ansi\\ansicpg1252\\deff0\\deflang1033{\\fonttbl{\\f0\\fcharset0 Arial;}{\\f1\\fcharset161 SBL Greek;}{\\f2\\fcharset177 SBL Hebrew;}{\\f3\\fnil Bwtranshs;}{\\f4\\fnil bwcyrl;}{\\f5\\fnil Bwviet;}{\\f6\\fnil Bweeti;}}\\pard\\plain\\fs20\r\n" +
				"{\\f0 \\fs24\\ltrch\\lang1033 Gen 1:1  }{\\f0\\fs20 In [the] beginning<N1>, /God/<N2> \\{ X \\} created@aprdf-p <Ra> \\{ <p><nsup>1</nsup> <i>Beg</i> <p>(2) <b>H</b><p> <p><rsup>a</rsup> X \\}}\\par \\ql \r\n" +
				"}").getBytes(StandardCharsets.ISO_8859_1));
	}

	private void testImportExportRoundtripCoverage(Class<? extends RoundtripFormat> module, byte[] content) throws Exception {
		File tempFile1 = File.createTempFile("~rtt", ".tmp");
		File tempFile2 = File.createTempFile("~rtt", ".tmp");
		try {
			Files.write(tempFile1.toPath(), content);
			RoundtripFormat format = module.newInstance();
			Assert.assertTrue(format.isImportExportRoundtrip());
			Bible bible = format.doImport(tempFile1);
			format.doExport(bible, tempFile2.getCanonicalPath());
			byte[] roundtripContent = Files.readAllBytes(tempFile2.toPath());
			System.out.println(new String(content));
			System.out.println(new String(roundtripContent));
			Assert.assertArrayEquals(content, roundtripContent);
		} finally {
			Assert.assertTrue(tempFile1.delete());
			Assert.assertTrue(tempFile2.delete());
		}
	}
}
