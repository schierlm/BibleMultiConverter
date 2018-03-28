package biblemulticonverter.tools;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import biblemulticonverter.schema.roundtripxml.ObjectFactory;

public class ValidateXML implements Tool {

	public static final String[] HELP_TEXT = {
			"Usage: ValidateXML <schema> <file> [<file>...]",
			"",
			"Validate one or more XML files according to a XSD schema.",
			"Schema can be a file name/path, a URL, or one of the predefined schemas:",
			"ZefaniaXML, HaggaiXML, RoundtripXML, ZefDic or OSIS.",
			"Validation errors are printed to the console."
	};

	@Override
	public void run(String... args) throws Exception {
		String schemaResourceName;
		if (args[0].equals("ZefaniaXML")) {
			schemaResourceName = "/zef2005.xsd";
		} else if (args[0].equals("HaggaiXML")) {
			schemaResourceName = "/haggai_20130620.xsd";
		} else if (args[0].equals("RoundtripXML")) {
			schemaResourceName = "/RoundtripXML.xsd";
		} else if (args[0].equals("ZefDic")) {
			schemaResourceName = "/zefDic1.xsd";
		} else if (args[0].equals("OSIS")) {
			schemaResourceName = "/osisCore.2.1.1.xsd";
		} else if (args[0].equals("CCEL")) {
			schemaResourceName = "/ccelVersification.xsd";
		} else if (args[0].equals("OpenScriptures")) {
			schemaResourceName = "/OpenScripturesBibleVersificationSystem.xsd";
		} else if (args[0].equals("USFX")) {
			schemaResourceName = "/usfx.xsd";
		} else if (args[0].equals("USX")) {
			schemaResourceName = "/usx.xsd";
		} else {
			schemaResourceName = null;
		}
		URL schemaURL;
		if (schemaResourceName != null) {
			schemaURL = ObjectFactory.class.getResource(schemaResourceName);
		} else if (new File(args[0]).exists()) {
			schemaURL = new File(args[0]).toURI().toURL();
		} else {
			schemaURL = new URL(args[0]);
		}
		Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaURL);

		for (int i = 1; i < args.length; i++) {
			System.out.print(args[i] + ": ");
			validateFile(schema, new File(args[i]), "Schema validation failed:", "Schema validation ok.", null);
		}
	}

	public static void validateFileBeforeParsing(Schema schema, File file) throws IOException {
		validateFile(schema, file, "WARNING: Schema validation failed: ", null, "WARNING: Parsing anyway after validation errors");
	}

	private static void validateFile(Schema schema, File file, final String errorHeader, String okMessage, String errorFooter) throws IOException {
		Validator validator = schema.newValidator();
		final int[] errorCountHolder = new int[1];
		validator.setErrorHandler(new ErrorHandler() {

			private void printHeader() {
				if (errorCountHolder[0] == 0 && errorHeader != null) {
					System.out.println(errorHeader);
				}
				errorCountHolder[0]++;
			}

			@Override
			public void warning(SAXParseException exception) throws SAXException {
				printHeader();
				System.out.println("\t[Warning] " + exception.toString());
			}

			@Override
			public void fatalError(SAXParseException exception) throws SAXException {
				printHeader();
				System.out.println("\t[Fatal Error] " + exception.toString());
			}

			@Override
			public void error(SAXParseException exception) throws SAXException {
				printHeader();
				System.out.println("\t[Error] " + exception.toString());
			}
		});
		try {
			validator.validate(new StreamSource(file));
		} catch (SAXException ex) {
			// already handled by ValidationHandler
		}
		String resultMessage = (errorCountHolder[0] > 0) ? errorFooter : okMessage;
		if (resultMessage != null)
			System.out.println(resultMessage);
	}
}
