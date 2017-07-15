package biblemulticonverter.sword;

import java.util.*;

import biblemulticonverter.ModuleRegistry;
import biblemulticonverter.format.*;
import biblemulticonverter.sword.format.*;
import biblemulticonverter.sword.tools.*;
import biblemulticonverter.sword.versification.*;
import biblemulticonverter.tools.Tool;
import biblemulticonverter.versification.VersificationFormat;

public class SWORDModuleRegistry extends ModuleRegistry {

	@Override
	public Collection<Module<ImportFormat>> getImportFormats() {
		List<Module<ImportFormat>> result = new ArrayList<Module<ImportFormat>>();
		result.add(new Module<ImportFormat>("SWORD", "Importer for SWORD modules", SWORD.HELP_TEXT, SWORD.class));
		return result;
	}

	@Override
	public Collection<Module<ExportFormat>> getExportFormats() {
		List<Module<ExportFormat>> result = new ArrayList<Module<ExportFormat>>();
		result.add(new Module<ExportFormat>("SWORDVersificationDetector", "Detect what SWORD versification to use best for exporting a module", new String[0], SWORDVersificationDetector.class));
		return result;
	}

	@Override
	public Collection<Module<RoundtripFormat>> getRoundtripFormats() {
		return Collections.emptyList();
	}

	@Override
	public Collection<Module<Tool>> getTools() {
		List<Module<Tool>> result = new ArrayList<Module<Tool>>();
		result.add(new Module<Tool>("SWORDDownloader", "Download or update SWORD modules from a remote HTTP repository", SWORDDownloader.HELP_TEXT, SWORDDownloader.class));
		return result;
	}

	@Override
	public Collection<Module<VersificationFormat>> getVersificationFormats() {
		List<Module<VersificationFormat>> result = new ArrayList<ModuleRegistry.Module<VersificationFormat>>();
		result.add(new Module<VersificationFormat>("SWORDVersification", "Versification format for versifications included in the SWORD project.", SWORDVersification.HELP_TEXT, SWORDVersification.class));
		return result;
	}
}
