package biblemulticonverter.tools;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.VersificationSet;

public class VersificationDetector extends AbstractVersificationDetector {

	public static final String[] HELP_TEXT = {
			"Detect what versification most closely matches a module",
			"",
			"Usage: VersificationDetector <dbfile> [-range|-title] [-xref]",
	};

	private boolean useRanges, titleAsVerseZero;
	private VersificationSet vs;

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		vs = new VersificationSet(new File(exportArgs[0]));
		useRanges = (exportArgs.length > 1 && exportArgs[1].equals("-range"));
		titleAsVerseZero = (exportArgs.length > 1 && exportArgs[1].equals("-title"));
		super.doExport(bible, Arrays.copyOfRange(exportArgs, useRanges || titleAsVerseZero ? 2 : 1, exportArgs.length));
	}

	@Override
	protected boolean useVerseRanges() {
		return useRanges;
	}

	@Override
	protected boolean useTitleAsVerseZero() {
		return titleAsVerseZero;
	}

	@Override
	protected VersificationScheme[] loadSchemes() throws IOException {
		VersificationScheme[] result = new VersificationScheme[vs.getVersifications().size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = vs.getVersifications().get(i).toNewVersificationScheme(titleAsVerseZero);
		}
		return result;
	}
}
