package biblemulticonverter;

import java.util.Collection;
import java.util.Collections;

import biblemulticonverter.format.ExportFormat;
import biblemulticonverter.format.ImportFormat;
import biblemulticonverter.format.RoundtripFormat;
import biblemulticonverter.tools.Tool;
import biblemulticonverter.versification.VersificationFormat;

/**
 * Responsible for discovering modules in this or other JAR files.
 */
public abstract class ModuleRegistry {

	public abstract Collection<Module<ImportFormat>> getImportFormats();

	public abstract Collection<Module<ExportFormat>> getExportFormats();

	public abstract Collection<Module<RoundtripFormat>> getRoundtripFormats();

	public abstract Collection<Module<Tool>> getTools();

	public Collection<Module<VersificationFormat>> getVersificationFormats() {
		return Collections.emptyList();
	}

	public static class Module<T> {
		private final String name;
		private final String shortHelp;
		private final String[] longHelp;
		private final Class<? extends T> implementationClass;

		public Module(String name, String shortHelp, String[] longHelp, Class<? extends T> implementationClass) {
			this.name = name;
			this.shortHelp = shortHelp;
			this.longHelp = longHelp;
			this.implementationClass = implementationClass;
		}

		public String getName() {
			return name;
		}

		public String getShortHelp() {
			return shortHelp;
		}

		public String[] getLongHelp() {
			return longHelp;
		}

		public Class<? extends T> getImplementationClass() {
			return implementationClass;
		}
	}
}
