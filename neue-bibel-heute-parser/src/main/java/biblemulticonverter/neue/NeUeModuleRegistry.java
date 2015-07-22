package biblemulticonverter.neue;

import java.util.*;

import biblemulticonverter.ModuleRegistry;
import biblemulticonverter.format.*;
import biblemulticonverter.tools.Tool;

public class NeUeModuleRegistry extends ModuleRegistry {

	@Override
	public Collection<Module<ImportFormat>> getImportFormats() {
		List<Module<ImportFormat>> result = new ArrayList<>();
		result.add(new Module<ImportFormat>("NeUeParser", "Parse HTML from Neue Evangelistische Übersetzung", NeUeParser.HELP_TEXT, NeUeParser.class));
		return result;
	}

	@Override
	public Collection<Module<ExportFormat>> getExportFormats() {
		return Collections.emptyList();
	}

	@Override
	public Collection<Module<RoundtripFormat>> getRoundtripFormats() {
		return Collections.emptyList();
	}

	@Override
	public Collection<Module<Tool>> getTools() {
		List<Module<Tool>> result = new ArrayList<>();
		result.add(new Module<Tool>("NeUeInputPatcher", "Patch syntax errors HTML from Neue Evangelistische Übersetzung before parsing", NeUeInputPatcher.HELP_TEXT, NeUeInputPatcher.class));
		return result;
	}
}
