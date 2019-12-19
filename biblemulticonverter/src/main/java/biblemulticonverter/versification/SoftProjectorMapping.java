package biblemulticonverter.versification;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.format.SoftProjector;

public class SoftProjectorMapping implements VersificationFormat {

	public static final String[] HELP_TEXT = {
			"Build a versification mapping from a SoftProjector bible file.",
			"",
			"Usage for import: SoftProjector <filename> <name>",
			"",
			"It will produce a <name> mapping that mapps from <name>_Canonical",
			"to <name>_Original versification."
	};

	@Override
	public void doImport(VersificationSet versifications, String... importArgs) throws Exception {
		Set<Reference> canonRefsSet = new HashSet<>();
		Set<Reference> origRefsSet = new HashSet<>();
		final List<Reference> canonRefs = new ArrayList<>();
		final List<Reference> origRefs = new ArrayList<>();
		final Map<Reference, List<Reference>> mappings = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(importArgs[0]), StandardCharsets.UTF_8))) {
			String line = br.readLine();
			if (line.startsWith("\uFEFF"))
				line = line.substring(1);
			if (!line.equals("##spDataVersion:\t1"))
				throw new IOException(line);
			while (!line.equals("-----")) {
				line = br.readLine();
			}
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\t");
				if (parts.length < 4)
					throw new IOException(line);
				Reference canonRef = SoftProjector.parseRef(parts[0], Collections.emptyList());
				Reference origRef = SoftProjector.parseRef(String.format("B%03dC%03dV%03d", Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])), Collections.emptyList());
				if (!canonRefsSet.add(canonRef))
					throw new IOException("Canonical reference exists more than once: " + canonRef);
				canonRefs.add(canonRef);
				if (origRefsSet.add(origRef))
					origRefs.add(origRef);
				List<Reference> origRefList = new ArrayList<>();
				origRefList.add(origRef);
				mappings.put(canonRef, origRefList);
			}
		}
		Versification vCanon = Versification.fromReferenceList(importArgs[1] + "_Canonical", null, null, canonRefs);
		Versification vOrig = Versification.fromReferenceList(importArgs[1] + "_Original", null, null, origRefs);
		VersificationMapping mapping = VersificationMapping.build(vCanon, vOrig, mappings);
		versifications.add(Arrays.asList(vCanon, vOrig), Arrays.asList(mapping));
	}

	@Override
	public boolean isExportSupported() {
		return false;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
	}
}
