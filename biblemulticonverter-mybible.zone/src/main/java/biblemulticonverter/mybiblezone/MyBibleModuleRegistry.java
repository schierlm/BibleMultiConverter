package biblemulticonverter.mybiblezone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import biblemulticonverter.ModuleRegistry;
import biblemulticonverter.format.ExportFormat;
import biblemulticonverter.format.ImportFormat;
import biblemulticonverter.format.RoundtripFormat;
import biblemulticonverter.mybiblezone.format.MyBibleZone;
import biblemulticonverter.mybiblezone.format.MyBibleZoneDictionary;
import biblemulticonverter.tools.Tool;

public class MyBibleModuleRegistry extends ModuleRegistry {

	@Override
	public Collection<Module<ImportFormat>> getImportFormats() {
		return Collections.emptyList();
	}

	@Override
	public Collection<Module<ExportFormat>> getExportFormats() {
		return Collections.emptyList();
	}

	@Override
	public Collection<Module<RoundtripFormat>> getRoundtripFormats() {
		List<Module<RoundtripFormat>> result = new ArrayList<Module<RoundtripFormat>>();
		result.add(new Module<RoundtripFormat>("MyBibleZone", "MyBible.zone (Bible Reader for Android).", MyBibleZone.HELP_TEXT, MyBibleZone.class));
		return result;
	}

	@Override
	public Collection<Module<Tool>> getTools() {
		return Collections.emptyList();
	}
}
