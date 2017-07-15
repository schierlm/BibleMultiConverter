package biblemulticonverter.versification;

import java.io.File;
import java.util.List;

import biblemulticonverter.data.Versification;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;

/**
 * A versification format that can be imported and optionally exported.
 */
public interface VersificationFormat {

	public void doImport(VersificationSet versifications, String... importArgs) throws Exception;

	public boolean isExportSupported();

	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception;
}
