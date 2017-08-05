package biblemulticonverter.sqlite.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.ISqlJetSchema;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetOptions;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import biblemulticonverter.tools.Tool;

public class SQLiteDump implements Tool {

	public static final String[] HELP_TEXT = {
			"Dump SQLite file as a diffable text file.",
			"",
			"Usage: SQLiteDump <databaseFile> <outputFile>",
			"",
			"Useful to compare two SQLite databases for differences or other debugging of SQLite databases.",
	};

	@Override
	public void run(String... args) throws Exception {
		SqlJetDb db = SqlJetDb.open(new File(args[0]), false);
		db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]), StandardCharsets.UTF_8))) {
			ISqlJetOptions options = db.getOptions();
			bw.write("FLAG\tAutovacuum\t" + options.isAutovacuum());
			bw.newLine();
			bw.write("FLAG\tLegacyFileFormat\t" + options.isLegacyFileFormat());
			bw.newLine();
			bw.write("FLAG\tIncrementalVacuum\t" + options.isIncrementalVacuum());
			bw.newLine();
			bw.write("OPTION\tCacheSize\t" + options.getCacheSize());
			bw.newLine();
			bw.write("OPTION\tEncoding\t" + options.getEncoding());
			bw.newLine();
			bw.write("OPTION\tFileFormat\t" + options.getFileFormat());
			bw.newLine();
			bw.write("OPTION\tSchemaVersion\t" + options.getSchemaVersion());
			bw.newLine();
			bw.write("OPTION\tUserVersion\t" + options.getUserVersion());
			bw.newLine();
			ISqlJetSchema schema = db.getSchema();
			List<String> triggers = new ArrayList<>(schema.getTriggerNames());
			Collections.sort(triggers);
			for (String trigger : triggers) {
				bw.write("TRG\t" + trigger + "\t" + schema.getTrigger(trigger).toSQL());
				bw.newLine();
			}
			List<String> indexes = new ArrayList<>(schema.getIndexNames());
			Collections.sort(indexes);
			for (String index : indexes) {
				bw.write("IDX\t" + index + "\t" + schema.getIndex(index).toSQL());
				bw.newLine();
			}
			List<String> views = new ArrayList<>(schema.getViewNames());
			Collections.sort(views);
			for (String view : views) {
				bw.write("VIEW\t" + view + "\t" + schema.getView(view).toSQL());
				bw.newLine();
			}
			List<String> vTables = new ArrayList<>(schema.getVirtualTableNames());
			Collections.sort(vTables);
			for (String vtbl : vTables) {
				bw.write("VTBL\t" + vtbl + "\t" + schema.getVirtualTable(vtbl).toSQL());
				bw.newLine();
			}
			List<String> tables = new ArrayList<>(schema.getTableNames());
			Collections.sort(tables);
			for (String table : tables) {
				bw.write("TABLE\t" + table + "\t" + schema.getTable(table).toSQL());
				bw.newLine();
				ISqlJetTable tbl = db.getTable(table);
				String primaryIndex = tbl.getPrimaryKeyIndexName();
				ISqlJetCursor cursor = primaryIndex == null ? tbl.open() : tbl.order(primaryIndex);
				int count = cursor.getFieldsCount();
				while (!cursor.eof()) {
					for (int i = 0; i < count; i++) {
						bw.write("  " + cursor.getFieldType(i).name().substring(0, 1) + "\t" + escape("" + cursor.getValue(i)));
						bw.newLine();
					}
					bw.write("  -----");
					bw.newLine();
					cursor.next();
				}
				cursor.close();
			}
		}
		db.commit();
		db.close();
	}

	private String escape(String string) {
		StringBuilder sb = new StringBuilder(string.length());
		for (int i = 0; i < string.length(); i++) {
			if (string.charAt(i) == '\\' || string.charAt(i) < ' ') {
				sb.append("\\" + String.format("%04x", (int) string.charAt(i)));
			} else {
				sb.append(string.charAt(i));
			}
		}
		return sb.toString();
	}
}
