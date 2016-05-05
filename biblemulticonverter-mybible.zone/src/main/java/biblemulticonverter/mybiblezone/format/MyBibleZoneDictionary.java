package biblemulticonverter.mybiblezone.format;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.format.ExportFormat;
import biblemulticonverter.mybiblezone.format.MyBibleZone.MyBibleHTMLVisitor;

public class MyBibleZoneDictionary implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"MyBible.zone (Bible Reader for Android) Dictionary.",
			"",
			"Usage: MyBibleZoneDictionary <moduleName>.SQLite3 <dictname> [<propertyfile>]",
			"",
			"Dictionary name is the name this dictionary is referred to by bibles (or 'strongs'",
			"for Strongs dictionary, or '-' to not create pointer topics).",
			"Property file can be used for overriding values in the info table."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String outfile = exportArgs[0];
		String dictionaryName = exportArgs[1];
		if (!outfile.endsWith(".dictionary.SQLite3"))
			outfile += ".dictionary.SQLite3";
		new File(outfile).delete();
		SqlJetDb db = SqlJetDb.open(new File(outfile), true);
		db.getOptions().setAutovacuum(true);
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		db.getOptions().setUserVersion(0);
		db.createTable("CREATE TABLE info (name TEXT, value TEXT)");
		db.createTable("CREATE TABLE dictionary (topic TEXT, definition TEXT)");
		db.createIndex("CREATE UNIQUE INDEX dictionary_topic ON dictionary(topic ASC)");
		Map<String, String> infoValues = new LinkedHashMap<>();
		MetadataBook mb = bible.getMetadataBook();
		if (mb == null)
			mb = new MetadataBook();
		infoValues.put("language", "xx");
		infoValues.put("description", bible.getName());
		infoValues.put("detailed_info", "");
		infoValues.put("russian_numbering", "false");
		infoValues.put("is_strong", dictionaryName.equals("strong") ? "true" : "false");
		infoValues.put("is_word_forms", "false");
		infoValues.put("morphology_topic_reference", "");
		for (String mbkey : mb.getKeys()) {
			if (mbkey.startsWith("MyBible.zone@")) {
				infoValues.put(mbkey.substring(13).replace('.', '_'), mb.getValue(mbkey));
			} else {
				infoValues.put("detailed_info", infoValues.get("detailed_info") + "\r\n<br><b>" + mbkey + ":</b>" + mb.getValue(mbkey));
			}
		}
		if (exportArgs.length > 2) {
			Properties props = new Properties();
			FileInputStream in = new FileInputStream(exportArgs[2]);
			props.load(in);
			in.close();
			for (Object key : props.keySet()) {
				String template = props.getProperty(key.toString());
				template = template.replace("${name}", bible.getName());
				for (String mbkey : mb.getKeys())
					template = template.replace("${" + mbkey + "}", mb.getValue(mbkey));
				infoValues.put(key.toString(), template);
			}
		}
		ISqlJetTable infoTable = db.getTable("info");
		ISqlJetTable dictionaryTable = db.getTable("dictionary");
		for (Map.Entry<String, String> entry : infoValues.entrySet()) {
			infoTable.insert(entry.getKey(), entry.getValue());
		}
		Set<String> usedTopics = new HashSet<>();
		final Set<String> unsupportedFeatures = new HashSet<>();
		for (Book bk : bible.getBooks()) {
			if (bk.getId() != BookID.DICTIONARY_ENTRY) {
				System.out.println("WARNING: Skipping book " + bk.getAbbr());
				continue;
			}
			String topicName = bk.getShortName();
			if (usedTopics.contains(topicName) && !dictionaryName.equals("strongs")) {
				for (int i = 2;; i++) {
					if (!usedTopics.contains(topicName + " (" + i + ")")) {
						topicName += " (" + i + ")";
						break;
					}
				}
			}
			if (usedTopics.contains(topicName)) {
				System.out.println("WARNING: Skipping duplicate topic " + topicName);
				continue;
			}
			if (dictionaryName.equals("strongs")) {
				if (!topicName.matches("[GH][1-9][0-9]*")) {
					System.out.println("WARNING: Skipping invalid Strong number " + topicName);
					continue;
				}
			} else if (!dictionaryName.equals("-")) {
				dictionaryTable.insert("[" + dictionaryName + "]" + bk.getAbbr(), "\u2197 <a href=\"S:" + topicName + "\">" + bk.getShortName() + "</a>");
			}
			MyBibleHTMLVisitor v = new MyBibleHTMLVisitor(unsupportedFeatures, "in dictionary entry");
			bk.getChapters().get(0).getProlog().accept(v);
			dictionaryTable.insert(topicName, v.getResult());
		}
		if (!unsupportedFeatures.isEmpty()) {
			System.out.println("WARNING: Skipped unsupported features: " + unsupportedFeatures);
		}
		db.commit();
		db.close();
	}

}
