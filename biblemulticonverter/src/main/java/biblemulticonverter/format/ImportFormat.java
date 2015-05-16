package biblemulticonverter.format;

import java.io.File;

import biblemulticonverter.data.Bible;

/**
 * A format that can be imported from a file.
 */
public interface ImportFormat {

	public Bible doImport(File inputFile) throws Exception;
}
