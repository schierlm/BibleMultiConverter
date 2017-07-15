package biblemulticonverter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import biblemulticonverter.ModuleRegistry.Module;
import biblemulticonverter.data.Bible;
import biblemulticonverter.format.ExportFormat;
import biblemulticonverter.format.ImportFormat;
import biblemulticonverter.format.RoundtripFormat;
import biblemulticonverter.tools.Tool;
import biblemulticonverter.versification.VersificationFormat;

/**
 * Main application entry point.
 */
public class Main {

	public static final Map<String, Module<ImportFormat>> importFormats = new HashMap<>();
	public static final Map<String, Module<ExportFormat>> exportFormats = new HashMap<>();
	public static final Map<String, Module<VersificationFormat>> versificationFormats = new HashMap<>();
	public static final Map<String, Module<Tool>> tools = new HashMap<>();

	public static void discoverModules() {
		if (importFormats.size() > 0)
			return;
		for (ModuleRegistry registry : ServiceLoader.load(ModuleRegistry.class)) {
			for (Module<ExportFormat> m : registry.getExportFormats()) {
				exportFormats.put(m.getName(), m);
			}
			for (Module<ImportFormat> m : registry.getImportFormats()) {
				importFormats.put(m.getName(), m);
			}
			for (Module<RoundtripFormat> m : registry.getRoundtripFormats()) {
				exportFormats.put(m.getName(), new Module<ExportFormat>(m.getName(), m.getShortHelp(), m.getLongHelp(), m.getImplementationClass()));
				importFormats.put(m.getName(), new Module<ImportFormat>(m.getName(), m.getShortHelp(), m.getLongHelp(), m.getImplementationClass()));
			}
			for (Module<Tool> m : registry.getTools()) {
				tools.put(m.getName(), m);
			}
			for (Module<VersificationFormat> m : registry.getVersificationFormats()) {
				versificationFormats.put(m.getName(), m);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		discoverModules();
		if (args.length > 0) {
			Module<Tool> toolModule = tools.get(args[0]);
			if (toolModule != null) {
				toolModule.getImplementationClass().newInstance().run(Arrays.copyOfRange(args, 1, args.length));
				return;
			}
		}
		if (args.length > 2) {
			Module<ImportFormat> importModule = importFormats.get(args[0]);
			Module<ExportFormat> exportModule = exportFormats.get(args[2]);
			if (importModule != null && exportModule != null) {
				Bible bible = importModule.getImplementationClass().newInstance().doImport(new File(args[1]));
				exportModule.getImplementationClass().newInstance().doExport(bible, Arrays.copyOfRange(args, 3, args.length));
				return;
			}
		}
		System.out.println("Usage:");
		System.out.println("java -jar BibleMultiConverter.jar <ImportFormat> <ImportFile> <ExportFormat> [<ExportArgs>...]");
		System.out.println("java -jar BibleMultiConverter.jar <Tool> [<ToolArgs>...]");
		printModules("import formats", importFormats);
		printModules("export formats", exportFormats);
		printModules("versification formats", versificationFormats);
		printModules("tools", tools);
	}

	private static <T> void printModules(String types, Map<String, Module<T>> moduleMap) {
		System.out.println();
		System.out.println("Supported " + types + ":");
		List<String> moduleNames = new ArrayList<String>(moduleMap.keySet());
		Collections.sort(moduleNames);
		for (String moduleName : moduleNames) {
			System.out.println(moduleName + " - " + moduleMap.get(moduleName).getShortHelp());
		}
	}
}
