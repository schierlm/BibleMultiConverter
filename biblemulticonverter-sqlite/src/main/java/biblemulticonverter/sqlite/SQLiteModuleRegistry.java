package biblemulticonverter.sqlite;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import biblemulticonverter.ModuleRegistry;
import biblemulticonverter.format.ExportFormat;
import biblemulticonverter.format.ImportFormat;
import biblemulticonverter.format.RoundtripFormat;
import biblemulticonverter.sqlite.format.BibleAnalyzerDatabase;
import biblemulticonverter.sqlite.format.ESwordV11;
import biblemulticonverter.sqlite.format.MyBibleZone;
import biblemulticonverter.sqlite.format.MyBibleZoneCrossreferences;
import biblemulticonverter.sqlite.format.MyBibleZoneDictionary;
import biblemulticonverter.sqlite.format.MySword;
import biblemulticonverter.sqlite.tools.MyBibleZoneListDownloader;
import biblemulticonverter.sqlite.tools.SQLiteDump;
import biblemulticonverter.tools.Tool;

public class SQLiteModuleRegistry extends ModuleRegistry {

	/**
	 * Open SQLite database; check version first and print warning.
	 */
	public static SqlJetDb openDB(File file, boolean write) throws SqlJetException {
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			byte[] header = new byte[20];
			in.readFully(header);
			if (new String(header, 0, 16, StandardCharsets.ISO_8859_1).equals("SQLite format 3\0") && (header[18] > 1 || header[19] > 1)) {
				System.err.println("WARNING: SQLite version of " + file.getName() + " is too new.");
				System.err.println("To convert SQLite file to version 1, open it in a SQLite editor and run SQL 'PRAGMA journal_mode=DELETE;'.");
				System.err.println();
			}
		} catch (IOException ex) {
			// ignore
		}
		return SqlJetDb.open(file, write);
	}

	@Override
	public Collection<Module<ImportFormat>> getImportFormats() {
		return Collections.emptyList();
	}

	@Override
	public Collection<Module<ExportFormat>> getExportFormats() {
		List<Module<ExportFormat>> result = new ArrayList<Module<ExportFormat>>();
		result.add(new Module<ExportFormat>("MyBibleZoneDictionary", "MyBible.zone (Bible Reader for Android) Dictionary.", MyBibleZoneDictionary.HELP_TEXT, MyBibleZoneDictionary.class));
		result.add(new Module<ExportFormat>("MyBibleZoneCrossreferences", "MyBible.zone (Bible Reader for Android) Crossreferences.", MyBibleZoneCrossreferences.HELP_TEXT, MyBibleZoneCrossreferences.class));
		result.add(new Module<ExportFormat>("BibleAnalyzerDatabase", "Database Export format for Bible Analyzer", BibleAnalyzerDatabase.HELP_TEXT, BibleAnalyzerDatabase.class));
		result.add(new Module<ExportFormat>("ESwordV11", "Export format for new (version 11) E-Sword modules", ESwordV11.HELP_TEXT, ESwordV11.class));
		return result;
	}

	@Override
	public Collection<Module<RoundtripFormat>> getRoundtripFormats() {
		List<Module<RoundtripFormat>> result = new ArrayList<Module<RoundtripFormat>>();
		result.add(new Module<RoundtripFormat>("MyBibleZone", "MyBible.zone (Bible Reader for Android).", MyBibleZone.HELP_TEXT, MyBibleZone.class));
		result.add(new Module<RoundtripFormat>("MySword", "MySword (Bible Reader for Android).", MySword.HELP_TEXT, MySword.class));
		return result;
	}

	@Override
	public Collection<Module<Tool>> getTools() {
		List<Module<Tool>> result = new ArrayList<Module<Tool>>();
		result.add(new Module<Tool>("SQLiteDump", "Dump SQLite file as a diffable text file.", SQLiteDump.HELP_TEXT, SQLiteDump.class));
		result.add(new Module<Tool>("MyBibleZoneListDownloader", "Download MyBible.Zone module list from module registry.", MyBibleZoneListDownloader.HELP_TEXT, MyBibleZoneListDownloader.class));
		return result;
	}
}
