package biblemulticonverter.logos;

import java.util.*;

import biblemulticonverter.ModuleRegistry;
import biblemulticonverter.format.*;
import biblemulticonverter.logos.format.*;
import biblemulticonverter.logos.tools.*;
import biblemulticonverter.tools.Tool;

public class LogosModuleRegistry extends ModuleRegistry {

	@Override
	public Collection<Module<ImportFormat>> getImportFormats() {
		return Collections.emptyList();
	}

	@Override
	public Collection<Module<ExportFormat>> getExportFormats() {
		List<Module<ExportFormat>> result = new ArrayList<Module<ExportFormat>>();
		result.add(new Module<ExportFormat>("LogosVersificationDetector", "Detect what Logos versification to use best for exporting a module", new String[0], LogosVersificationDetector.class));
		result.add(new Module<ExportFormat>("LogosRenumberedDiffable", "Renumber named verses for Logos before exporting as Diffable.", LogosRenumberedDiffable.HELP_TEXT, LogosRenumberedDiffable.class));
		result.add(new Module<ExportFormat>("LogosHTML", "HTML Export format for Logos Bible Software", LogosHTML.HELP_TEXT, LogosHTML.class));
		return result;
	}

	@Override
	public Collection<Module<RoundtripFormat>> getRoundtripFormats() {
		return Collections.emptyList();
	}

	@Override
	public Collection<Module<Tool>> getTools() {
		List<Module<Tool>> result = new ArrayList<Module<Tool>>();
		result.add(new Module<Tool>("LogosNestedHyperlinkPostprocessor", "Postprocess nested hyperlinks in DOCX converted from HTML by LibreOffice", LogosNestedHyperlinkPostprocessor.HELP_TEXT, LogosNestedHyperlinkPostprocessor.class));
		return result;
	}
}
