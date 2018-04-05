package biblemulticonverter.versification;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import biblemulticonverter.data.StandardVersification;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;

public class KJV implements VersificationFormat {

	public static final String[] HELP_TEXT = {
			"Versification format for the hard-coded KJV versification."
	};

	@Override
	public void doImport(VersificationSet versifications, String... importArgs) throws Exception {
		versifications.add(Arrays.asList(Versification.fromStandardVersification("KJV", StandardVersification.KJV)), null);
	}

	@Override
	public boolean isExportSupported() {
		return false;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
	}
}
