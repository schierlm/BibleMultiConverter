package biblemulticonverter.format;

public interface RoundtripFormat extends ExportFormat, ImportFormat {

	public boolean isExportImportRoundtrip();

	public boolean isImportExportRoundtrip();
}
