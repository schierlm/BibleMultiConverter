package biblemulticonverter.format;

import biblemulticonverter.data.Bible;

/**
 * A format that can be exported to a file.
 */
public interface ExportFormat {

	public void doExport(Bible bible, String... exportArgs) throws Exception;
}
