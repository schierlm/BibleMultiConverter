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
import biblemulticonverter.mybiblezone.tools.SQLiteDump;
import biblemulticonverter.tools.Tool;

public class MyBibleModuleRegistry extends ModuleRegistry {

	@Override
	public Collection<Module<ImportFormat>> getImportFormats() {
		return Collections.emptyList();
	}

	@Override
	public Collection<Module<ExportFormat>> getExportFormats() {
		List<Module<ExportFormat>> result = new ArrayList<Module<ExportFormat>>();
		result.add(new Module<ExportFormat>("MyBibleZoneDictionary", "MyBible.zone (Bible Reader for Android) Dictionary.", MyBibleZoneDictionary.HELP_TEXT, MyBibleZoneDictionary.class));
		return result;
	}

	@Override
	public Collection<Module<RoundtripFormat>> getRoundtripFormats() {
		List<Module<RoundtripFormat>> result = new ArrayList<Module<RoundtripFormat>>();
		result.add(new Module<RoundtripFormat>("MyBibleZone", "MyBible.zone (Bible Reader for Android).", MyBibleZone.HELP_TEXT, MyBibleZone.class));
		return result;
	}

	@Override
	public Collection<Module<Tool>> getTools() {
		List<Module<Tool>> result = new ArrayList<Module<Tool>>();
		result.add(new Module<Tool>("SQLiteDump", "Dump SQLite file as a diffable text file.", SQLiteDump.HELP_TEXT, SQLiteDump.class));
		return result;
	}
}
