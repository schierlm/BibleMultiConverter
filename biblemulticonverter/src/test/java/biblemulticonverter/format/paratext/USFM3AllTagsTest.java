package biblemulticonverter.format.paratext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import biblemulticonverter.data.Bible;
import biblemulticonverter.format.paratext.model.Version;
import biblemulticonverter.tools.Validate;

public class USFM3AllTagsTest {

	static ParatextBook testBook;
	static String testBookContent;

	@BeforeClass
	public static void loadTestBook() throws Exception {
		File originalFile = USX3Test.getResource("/usfm3allTags/01-MAT.usfm");
		USFM usfm = new USFM();
		testBook = usfm.doImportBook(originalFile);
		assertNotNull(testBook);
		testBookContent = new String(Files.readAllBytes(originalFile.toPath()), StandardCharsets.UTF_8);
	}

	@Test
	public void testUSFM3MinimalTemplate() throws Exception {
		File templateFile = USX3Test.getResource("/usfm3allTags/minimal-template.txt");
		String templateContent = new String(Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);
		String[] templateLines = templateContent.split("\n");
		String[] testFileLines = testBookContent.split("\n");
		for (int i = 0; i < templateLines.length; i++) {
			if (!templateLines[i].equals("~"))
				testFileLines[i] = templateLines[i];
		}
		File resultFile = USX3Test.createTempFile("minimal", ".usfm");
		String minimalFile = String.join("\n", testFileLines);
		Files.write(resultFile.toPath(), minimalFile.getBytes(StandardCharsets.UTF_8));
		USFM usfm = new USFM();
		ParatextBook minimalBook = usfm.doImportBook(resultFile);
		assertNotNull(minimalBook);
		usfm.doExportBook(minimalBook, resultFile);
		assertEquals(testBookContent, new String(Files.readAllBytes(resultFile.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void testFastRoundtrip() throws Exception {
		USFM usfm = new USFM();
		File resultFile = USX3Test.createTempFile("export", ".usfm");
		usfm.doExportBook(testBook, resultFile);
		assertEquals(testBookContent, new String(Files.readAllBytes(resultFile.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void testValidateModule() throws Exception {
		// act on a copy as ParatextBook.fixTrailingWhitespace messes with the document!
		File originalFile = USX3Test.getResource("/usfm3allTags/01-MAT.usfm");
		ParatextBook testBookCopy = new USFM().doImportBook(originalFile);

		Bible bible = new AbstractParatextFormat("Dummy") {
			@Override
			protected List<ParatextBook> doImportAllBooks(File inputFile) throws Exception {
				return Arrays.asList(testBookCopy);
			}

			@Override
			protected ParatextBook doImportBook(File inputFile) throws Exception {
				throw new UnsupportedOperationException();
			}

			@Override
			protected void doExportBook(ParatextBook book, File outFile) throws Exception {
				throw new UnsupportedOperationException();
			}
		}.doImport(null);
		new Validate().doExport(bible);
	}

	private void testSingleFormat(AbstractParatextFormat format, String extraArg, String expectedContent) throws Exception {
		File roundtripFile = File.createTempFile("~roundtrip", ".tmp");
		if (!extraArg.isEmpty()) {
			roundtripFile.delete();
			roundtripFile.mkdir();
		}
		format.doExportBooks(Arrays.asList(testBook), roundtripFile.getPath(), extraArg);

		List<ParatextBook> books = format.doImportBooks(roundtripFile);
		deleteRecursively(roundtripFile);
		assertEquals(1, books.size());
		USFM usfm = new USFM();
		File resultFile = USX3Test.createTempFile("export", ".usfm");
		usfm.doExportBook(books.get(0), resultFile);
		String actualContent = new String(Files.readAllBytes(resultFile.toPath()), StandardCharsets.UTF_8);
		assertEquals(expectedContent == null ? testBookContent : expectedContent, actualContent);
		if (expectedContent != null) {
			usfm.doExportBook(usfm.doImportBook(resultFile), resultFile);
			assertEquals(expectedContent, new String(Files.readAllBytes(resultFile.toPath()), StandardCharsets.UTF_8));
		}
	}

	private String toVersion2_2(String version3) {
		return version3.replace("\\usfm 3.0\n", "").replaceAll("\n\\\\toca[123].*", "").replace("\\qd", "\\d")
				.replace("\\efm", "\\fm").replace("\\sd2", "\\b").replace("\\po", "\\p").replace("\\lh", "\\p")
				.replace("\\lf", "\\m").replace("\\lim", "\\li").replaceAll("\\\\li([kv]|tl)[ *]", "")
				.replace(" strong=\"G0123\" srcloc=\"version:1.2.3.4\"\\w*", "\\w*").replace("\\png", "\\pn").replaceAll("\\\\wa[ *]", "").replace("\\efm", "\\fm")
				.replace("\\rb Ruby|gloss=\"Roo:bee\"\\rb*", "\\pro Ruby\\pro*").replace("\\sup ", "").replace("\\sup*", "")
				.replace("\\tc1-2", "\\tc1 \\tc2").replace("\\th2-3", "\\th2 \\th3").replace("\\fw", "\\ft").replace("\\xop ibidem:\\xop*", "").replaceAll("\\\\xta[ *]", "").replace("|link-href=\"GEN 9:8\"\\xt*", "\\xt*")
				.replace("|id=\"measures\"", "").replace("|id=\"x-custom\"", "")
				.replace("\\jmp to nowhere|link-href=\"https://schierlm.github.io\" x-why=\"That's me\"\\jmp*. Here is \\jmp |link-id=\"a-loop\"\\jmp*\\jmp A loop|link-href=\"#a-loop\" link-title=\"Loop\"\\jmp*.\\ts \\*", "to nowhere. Here is A loop.")
				.replace("\\zmyMilestone \\* Milestone,\\qt-s |sid=\"qqA\" who=\"Nobody\"\\*\" Fake Quote:\\qt2-s |sid=\"qqB\" who=\"Nobodier\"\\* Nobody said this!\\qt-e |eid=\"qqB\"\\*\"\\qt-e |eid=\"qqA\"\\*\\ts \\*", " Milestone,\" Fake Quote: Nobody said this!\"");
	}

	private String toVersion2_1(String version3) {
		return toVersion2_2(version3).replace("\\iqt", "\\qt").replace("\\xot", "\\xt").replace("\\xnt", "\\xt");
	}

	private String toVersion2_0_4(String version3) {
		return toVersion2_1(version3).replace("\\ef", "\\f").replaceFirst("\\\\esb(.|\n)*?\\\\esbe\n", "");
	}

	private String toVersion2(String version3) {
		return toVersion2_0_4(testBookContent).replaceAll("\n\\\\toc[123] .*", "").replaceAll("\\\\f[pl]", "\\\\ft");
	}

	@Test
	public void testUSX() throws Exception {
		String expectedContent = toVersion2_2(testBookContent.replace("\\sup*", " ").replaceAll("\\\\li([kv]|tl)\\*", " ")
				.replace("\\rb Ruby|gloss=\"Roo:bee\"\\rb*", "Ruby\\pro Roo:bee\\pro*"))
				.replace("\\pr ", "\\pmr ").replace("\\ph", "\\li")
				.replace("~", " ").replaceAll("\\\\zCustomTag[* ]", "")
				.replace("nowhere. Here is A loop.", "nowhere . Here is A loop .")
				.replace("Milestone,\" Fake Quote: Nobody said this!\"", "Milestone, \" Fake Quote: Nobody said this! \"");
		testSingleFormat(new USX(), "#-*.usx", expectedContent);
	}

	@Test
	public void testUSX3() throws Exception {
		String expectedContent = testBookContent.replaceAll("\\\\zCustomTag[* ]", "").replace("\\ph", "\\li").replace("~", " ") // \u00A0
				.replace(" x-why=\"That's me\"", "").replace("\\periph Custom peripheral without ID", "\\periph Custom peripheral without ID|id=\"x-undefined\"")
				.replace("\\periph Chronology", "\\periph Chronology|id=\"chron\"");
		testSingleFormat(new USX3(), "#-*.usx", expectedContent);
	}

	@Test
	public void testUSFX() throws Exception {
		String expectedContent = testBookContent.replace("\\ca ", " \n\\p \\ca ").replace("\\cat Etymology\\cat*", "")
				.replace("\\tc1-2", "\\tc1 \\tc2").replace("\\th2-3", "\\th2 \\th3").replace("~", " ").replace("|link-href=\"GEN 9:8\"", "")
				.replace("|gloss=\"Roo:bee\"", "").replace("|link-href=\"https://schierlm.github.io\" x-why=\"That's me\"", "")
				.replace("|link-id=\"a-loop\"", "").replace("|link-href=\"#a-loop\" link-title=\"Loop\"", "")
				.replace("|id=\"measures\"", "").replace("|id=\"x-custom\"", "")
				.replaceFirst("\\\\esb(.|\n)*?\\\\esbe\n", "").replaceAll("  +", " ").replaceAll(" +\n", "\n");
		testSingleFormat(new USFX(), "", expectedContent);
	}

	@Test
	public void testParatextDump() throws Exception {
		testSingleFormat(new ParatextDump(), "", null);
	}

	@Test
	public void testParatextVPL() throws Exception {
		testSingleFormat(new ParatextVPL(), "", null);
	}

	@Test
	public void testParatextCompact() throws Exception {
		testSingleFormat(new ParatextCompact(), "", null);
	}

	@Test
	public void testParatextUSFM() throws Exception {
		testSingleFormat(new USFM(), "#-*.usfm", null);
	}

	private void testParatextStripped(String[] exportArgs, String expectedContent, Version expectedVersion) throws Exception {
		File originalFile = USX3Test.getResource("/usfm3allTags/01-MAT.usfm");
		ParatextBook testBookCopy = new USFM().doImportBook(originalFile);
		new ParatextStripped().stripBooks(Arrays.asList(testBookCopy), exportArgs);
		USFM usfm = new USFM();
		assertEquals(expectedVersion, usfm.getMinRequiredVersion(testBookCopy));
		File resultFile = USX3Test.createTempFile("export", ".usfm");
		usfm.doExportBook(testBookCopy, resultFile);
		String actualContent = new String(Files.readAllBytes(resultFile.toPath()), StandardCharsets.UTF_8);
		assertEquals(expectedContent, actualContent);
		usfm.doExportBook(usfm.doImportBook(resultFile), resultFile);
		assertEquals(expectedContent, new String(Files.readAllBytes(resultFile.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void testConvertVersion3() throws Exception {
		testParatextStripped(new String[0], testBookContent, Version.V3);
		testParatextStripped(new String[] { "CompatibleVersion=" + Version.V3.toString() }, testBookContent, Version.V3);
	}

	@Test
	public void testConvertVersion2_2() throws Exception {
		String expectedContent = toVersion2_2(testBookContent);
		testParatextStripped(new String[] { "CompatibleVersion=" + Version.V2_3.toString() }, expectedContent, Version.V2_2);
		testParatextStripped(new String[] { "CompatibleVersion=" + Version.V2_2.toString() }, expectedContent, Version.V2_2);
	}

	@Test
	public void testConvertVersion2_1() throws Exception {
		String expectedContent = toVersion2_1(testBookContent);
		testParatextStripped(new String[] { "CompatibleVersion=" + Version.V2_1.toString() }, expectedContent, Version.V2_1);
	}

	@Test
	public void testConvertVersion2_0_4() throws Exception {
		String expectedContent = toVersion2_0_4(testBookContent);
		testParatextStripped(new String[] { "CompatibleVersion=" + Version.V2_0_4.toString() }, expectedContent, Version.V2_0_4);
	}

	@Test
	public void testConvertVersion2_0_3() throws Exception {
		String expectedContent = toVersion2_0_4(testBookContent).replaceFirst("\n\\\\toc3 .*", "");
		testParatextStripped(new String[] { "CompatibleVersion=" + Version.V2_0_3.toString() }, expectedContent, Version.V2_0_3);
	}

	@Test
	public void testConvertVersion2() throws Exception {
		String expectedContent = toVersion2(testBookContent);
		testParatextStripped(new String[] { "CompatibleVersion=" + Version.V2.toString() }, expectedContent, Version.V2);
	}

	@Test
	public void testConvertVersion1() throws Exception {
		String expectedContent = toVersion2(testBookContent).replace("\\sr ", "\\mr ").replace("\\pmo ", "\\mi ")
				.replace("\\pm ", "\\pi ").replace("\\pmr ", "\\pr ").replace("\\pmc ", "\\mi ").replace("\\em", "\\it")
				.replace("\\qm", "\\q").replace("\\addpn The Great\\addpn*", "\\add \\+pn The Great\\+pn*\\add*")
				.replaceAll("\\\\(wj|pro)[ *]", "");
		testParatextStripped(new String[] { "CompatibleVersion=" + Version.V1.toString() }, expectedContent, Version.V1);
	}

	@Test
	public void testStripEverything() throws Exception {
		String expectedContent = testBookContent.replaceAll("\n\\\\(i[mspbloeq]|rem ).*?(?=\n)", "")
				.replace("\\ef † \\cat Etymology\\cat*\\fr 1.1:\\fr*\\fq First:\\fq*\\ft Fore-est\\ft*\\ef*", "")
				.replaceFirst("\\\\esb (.|\n)*?\\\\esbe\n", "")
				.replaceAll("\\\\zCustomTag[ *]", "").replace("\\zmyMilestone \\*", "")
				.replace("\\fdc This bible contains deuterocanonical content.\\fdc*", "")
				.replace("\\dc in deuterocanonical books\\dc*", "")
				.replace("\\xot Exo 1.1\\xot*", "").replace("\\xdc Sir 2.3\\xdc*", "")
				.replaceAll("\\|(lemma|link|gloss|[se]id).*?\\\\", "\\\\");
		testParatextStripped(new String[] {
				"StripStudyContent", "StripCustomMarkup", "StripIntroductions", "StripTagAttributes",
				"StripRemarks", "StripReferences", "StripPart=OT", "StripPart=DC",
				"StripStudyCategory=Sidey", "StripStudyCategory=Etymology", "StripParagraph=qxq",
		}, expectedContent, Version.V3);
	}

	private static void deleteRecursively(File f) throws IOException {
		if (!f.exists())
			return;
		if (f.isDirectory()) {
			for (File cf : f.listFiles()) {
				deleteRecursively(cf);
			}
		}
		if (!f.delete())
			throw new IOException("Unable to delete " + f.getPath());
	}
}
