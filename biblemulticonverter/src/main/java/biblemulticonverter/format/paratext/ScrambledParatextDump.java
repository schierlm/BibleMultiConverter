package biblemulticonverter.format.paratext;

import java.io.File;
import java.io.IOException;
import java.util.List;

import biblemulticonverter.format.ScrambledDiffable.Scrambler;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentPart;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Text;

/**
 * ScrambledDiffable for Paratext.
 */
public class ScrambledParatextDump extends AbstractParatextFormat {

	public static final String[] HELP_TEXT = {
			"Like ParatextDump, but with scrambled text; for tests with non-free bibles.",
			"",
			"Usage (export): ScrambledParatextDump <OutputFile> [+<Password>|-<Password>|=<Const>|#<Hash>]",
			"",
			"See the help text for the ScrambledDiffable format for details.",
	};

	public ScrambledParatextDump() {
		super("ScrambledParatextDump");
	}

	@Override
	protected List<ParatextBook> doImportAllBooks(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void doExportBooks(List<ParatextBook> books, String... exportArgs) throws Exception {
		Scrambler scrambler = new Scrambler(exportArgs.length == 1 ? null : exportArgs[1]);
		if (scrambler.getDigest() != null) {
			throw new IllegalStateException("Digest mode not supported");
		}
		for (ParatextBook book : books) {
			for (ParatextBookContentPart part : book.getContent()) {
				if (part instanceof ParatextCharacterContent) {
					scrambleContent((ParatextCharacterContent) part, scrambler);
				}
			}
		}
		new ParatextDump().doExportBooks(books, exportArgs);
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		throw new UnsupportedOperationException();
	}

	private void scrambleContent(ParatextCharacterContentContainer container, Scrambler scrambler) {
		for (int i = 0; i < container.getContent().size(); i++) {
			ParatextCharacterContentPart part = container.getContent().get(i);
			if (part instanceof ParatextCharacterContentContainer) {
				scrambleContent((ParatextCharacterContentContainer) part, scrambler);
			} else if (part instanceof Reference) {
				((Reference) part).setContent(scrambler.scrambleText(((Reference) part).getContent()));
			} else if (part instanceof Text) {
				container.getContent().set(i, Text.from(scrambler.scrambleText(((Text) part).getChars())));
			}
		}
	}
}
