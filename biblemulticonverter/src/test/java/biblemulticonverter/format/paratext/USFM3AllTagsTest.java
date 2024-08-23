package biblemulticonverter.format.paratext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;

import biblemulticonverter.data.Bible;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentPart;
import biblemulticonverter.schema.usfx.ObjectFactory;
import biblemulticonverter.schema.usfx.PType;
import biblemulticonverter.schema.usfx.Usfx;
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

	@Test
	public void testUSX() throws Exception {
		String expectedContent = testBookContent.replaceAll("\n\\\\toca.*", "").replaceAll("\n\\\\usfm 3.0", "").replaceAll("\\\\zCustomTag[* ]", "")
				.replace("\\sd2", "\\b").replace("\\ph", "\\li").replace("\\po", "\\p").replace("\\pr ", "\\pmr ").replace("\\pr*", "\\pmr*").replace("\\qd", "\\d")
				.replace("\\lh", "\\p").replace("\\lf", "\\p").replace("\\lim", "\\li").replaceAll("\\\\li([kv]|tl)\\*", " ").replaceAll("\\\\li([kv]|tl) ", "")
				.replace(" strong=\"G0123\" srcloc=\"version:1.2.3.4\"\\w*", "\\w*").replace("\\png", "\\pn").replaceAll("\\\\wa[ *]", "").replace("\\efm", "\\fm")
				.replace("\\sup ", "").replace("\\sup*", " ").replace("~", " ").replace("\\rb Ruby|gloss=\"Roo:bee\"\\rb*", "Ruby\\pro Roo:bee\\pro*")
				.replace("\\tc1-2", "\\tc1 \\tc2").replace("\\th2-3", "\\th2 \\th3").replace("\\fw", "\\ft").replace("\\xop ibidem:\\xop*", "").replaceAll("\\\\xta[ *]", "").replace("|link-href=\"GEN 9:8\"\\xt*", "\\xt*")
				.replace("|id=\"measures\"", "").replace("|id=\"x-custom\"", "")
				.replace("\\jmp to nowhere|link-href=\"https://schierlm.github.io\" x-why=\"That's me\"\\jmp*. Here is \\jmp |link-id=\"a-loop\"\\jmp*\\jmp A loop|link-href=\"#a-loop\" link-title=\"Loop\"\\jmp*.\\ts \\*", "to nowhere . Here is A loop .")
				.replace("\\zmyMilestone \\* Milestone,\\qt-s |sid=\"qqA\" who=\"Nobody\"\\*\" Fake Quote:\\qt2-s |sid=\"qqB\" who=\"Nobodier\"\\* Nobody said this!\\qt-e |eid=\"qqB\"\\*\"\\qt-e |eid=\"qqA\"\\*\\ts \\*", " Milestone, \" Fake Quote: Nobody said this! \"");
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
