package biblemulticonverter.tools;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import biblemulticonverter.Main;
import biblemulticonverter.ModuleRegistry.Module;
import biblemulticonverter.format.ExportFormat;
import biblemulticonverter.format.ImportFormat;
import biblemulticonverter.format.paratext.AbstractParatextFormat;
import biblemulticonverter.format.paratext.ParatextBook;

public class ParatextConverter implements Tool {

	public static final String[] HELP_TEXT = {
			"Convert between Paratext formats without information loss",
			"",
			"Usage: ParatextConverter <ImportFormat> <ImportFile> <ExportFormat> [<ExportArgs>...]",
			"",
			"You can use this tool to convert between Paratext formats (USFM 2, USX 2, USX 3) and USFX without",
			"losing information. Unlike the normal converter, which converts everything to",
			"BibleMultiConverter's internal XML format, this will only parse the input file to",
			"a paratext AST and then write it out in the other format again.",
			"",
			"Use the option -Dbiblemulticonverter.paratext.keepparts option (supported values are",
			"OT, NT and DC; separate multiple values by comma) to remove conditional parts.",
			"",
			"Use the option -Dbiblemulticonverter.paratext.usfm.preserveSpacesAtEndOfLines to keep",
			"single spaces at the end of lines when importing from USFM (sometimes these spaces are",
			"significant). Supported values are true and false (defaults to false)."
	};

	@Override
	public void run(String... args) throws Exception {
		Module<ImportFormat> importModule = Main.importFormats.get(args[0]);
		Module<ExportFormat> exportModule = Main.exportFormats.get(args[2]);
		AbstractParatextFormat importFormat = (AbstractParatextFormat) importModule.getImplementationClass().newInstance();
		AbstractParatextFormat exportFormat = (AbstractParatextFormat) exportModule.getImplementationClass().newInstance();
		List<ParatextBook> books = importFormat.doImportBooks(new File(args[1]));
		exportFormat.doExportBooks(books, Arrays.copyOfRange(args, 3, args.length));
	}
}
