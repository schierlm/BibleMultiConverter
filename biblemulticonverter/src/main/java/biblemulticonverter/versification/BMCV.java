package biblemulticonverter.versification;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import biblemulticonverter.data.Versification;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;

public class BMCV implements VersificationFormat {

	public static final String[] HELP_TEXT = {
			"Versification format internally used by BibleMultiConverter."
	};

	@Override
	public void doImport(VersificationSet versifications, String... importArgs) throws Exception {
		for (String filename : importArgs) {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8))) {
				versifications.loadFrom(r);
			}
		}
	}

	@Override
	public boolean isExportSupported() {
		return true;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
		VersificationSet set = new VersificationSet();
		set.add(versifications, mappings);
		try (Writer w = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
			set.saveTo(w);
		}
	}
}
