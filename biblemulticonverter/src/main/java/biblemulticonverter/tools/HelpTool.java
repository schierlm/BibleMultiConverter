package biblemulticonverter.tools;

import biblemulticonverter.Main;
import biblemulticonverter.ModuleRegistry.Module;

public class HelpTool implements Tool {

	public static final String[] HELP_TEXT = {
			"Usage: help <name>",
			"",
			"Show help about an import format, export format or tool.",
	};

	@Override
	public void run(String... args) throws Exception {
		Main.discoverModules();
		Module<?> module;
		if (args.length == 0) {
			module = Main.tools.get("help");
		} else {
			module = Main.importFormats.get(args[0]);
			if (module == null)
				module = Main.exportFormats.get(args[0]);
			if (module == null)
				module = Main.versificationFormats.get(args[0]);
			if (module == null)
				module = Main.tools.get(args[0]);
			if (module == null) {
				System.out.println("Module not found: " + args[0]);
				System.out.println();
				module = Main.tools.get("help");
			}
		}
		System.out.println(module.getName() + " - " + module.getShortHelp());
		System.out.println();
		for (String line : module.getLongHelp()) {
			System.out.println(line);
		}
	}
}
