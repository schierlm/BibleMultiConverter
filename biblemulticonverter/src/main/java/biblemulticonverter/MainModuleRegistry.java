package biblemulticonverter;

import java.util.*;

import biblemulticonverter.format.*;
import biblemulticonverter.format.paratext.*;
import biblemulticonverter.tools.*;
import biblemulticonverter.versification.*;

public class MainModuleRegistry extends ModuleRegistry {

	@Override
	public Collection<Module<ImportFormat>> getImportFormats() {
		List<Module<ImportFormat>> result = new ArrayList<ModuleRegistry.Module<ImportFormat>>();
		result.add(new Module<ImportFormat>("StrongDictionary", "Importer for creating a Strong's dictionary from public domain resources.", StrongDictionary.HELP_TEXT, StrongDictionary.class));
		result.add(new Module<ImportFormat>("MorphGNT", "Importer for MorphGNT", MorphGNT.HELP_TEXT, MorphGNT.class));
		result.add(new Module<ImportFormat>("OSHB", "Importer for OpenScriptures Hebrew Bible MorphBB", OSHB.HELP_TEXT, OSHB.class));
		result.add(new Module<ImportFormat>("TranslatorsAmalgamated", "Importer for Translators Amalgamated Hebrew OT / Greek NT", TranslatorsAmalgamated.HELP_TEXT, TranslatorsAmalgamated.class));
		return result;
	}

	@Override
	public Collection<Module<ExportFormat>> getExportFormats() {
		List<Module<ExportFormat>> result = new ArrayList<ModuleRegistry.Module<ExportFormat>>();
		result.add(new Module<ExportFormat>("Validate", "Validate bible for inconsistencies", Validate.HELP_TEXT, Validate.class));
		result.add(new Module<ExportFormat>("StrippedDiffable", "Like Diffable, but with features stripped.", StrippedDiffable.HELP_TEXT, StrippedDiffable.class));
		result.add(new Module<ExportFormat>("ZefaniaXMLMyBible", "Zefania XML - well known bible format (with MyBible optimizations).", ZefaniaXMLMyBible.HELP_TEXT, ZefaniaXMLMyBible.class));
		result.add(new Module<ExportFormat>("ZefDicMyBible", "Zefania Dictionary exporter for MyBible.", ZefDicMyBible.HELP_TEXT, ZefDicMyBible.class));
		result.add(new Module<ExportFormat>("MobiPocket", "MobiPocket ebook format (predecessor of Kindle's format)", MobiPocket.HELP_TEXT, MobiPocket.class));
		result.add(new Module<ExportFormat>("ESwordHTML", "HTML Export format for E-Sword", ESwordHTML.HELP_TEXT, ESwordHTML.class));
		result.add(new Module<ExportFormat>("StrongConcordance", "Add concordance information to a Strong dictionary", StrongConcordance.HELP_TEXT, StrongConcordance.class));
		result.add(new Module<ExportFormat>("ScrambledDiffable", "Like Diffable, but with scrambled text; for tests with non-free bibles.", ScrambledDiffable.HELP_TEXT, ScrambledDiffable.class));
		result.add(new Module<ExportFormat>("ScrambledParatextDump", "Like ParatextDump, but with scrambled text; for tests with non-free bibles.", ScrambledParatextDump.HELP_TEXT, ScrambledParatextDump.class));
		result.add(new Module<ExportFormat>("Volksbibel2000", "Export format for reimporting into Volksbibel 2000", Volksbibel2000.HELP_TEXT, Volksbibel2000.class));
		result.add(new Module<ExportFormat>("OnLineBible", "Export format for importing into OnLine Bible", OnLineBible.HELP_TEXT, OnLineBible.class));
		result.add(new Module<ExportFormat>("BrowserBible", "Export format for The Browser Bible 3 by Digital Bible Society", BrowserBible.HELP_TEXT, BrowserBible.class));
		result.add(new Module<ExportFormat>("BibleAnalyzerFormattedText", "Formatted Text Export format for Bible Analyzer", BibleAnalyzerFormattedText.HELP_TEXT, BibleAnalyzerFormattedText.class));
		result.add(new Module<ExportFormat>("QuickBible", "Export format for QuickBible (Bible for Android)", QuickBible.HELP_TEXT, QuickBible.class));
		result.add(new Module<ExportFormat>("EquipdEPUB", "Epub export format for Equipd Bible", EquipdEPUB.HELP_TEXT, EquipdEPUB.class));
		result.add(new Module<ExportFormat>("SimpleJSON", "Very simple JSON bible export (verse text only).", SimpleJSON.HELP_TEXT, SimpleJSON.class));
		result.add(new Module<ExportFormat>("VersificationDetector", "Detect what versification most closely matches a module", VersificationDetector.HELP_TEXT, VersificationDetector.class));
		result.add(new Module<ExportFormat>("VersificationCountsDetector", "Detect what versification most closely matches a module, looking on chapter/verse counts only", VersificationCountsDetector.HELP_TEXT, VersificationCountsDetector.class));
		result.add(new Module<ExportFormat>("VersificationMappedDiffable", "Export like Diffable, but change the Versification first.", VersificationMappedDiffable.HELP_TEXT, VersificationMappedDiffable.class));
		result.add(new Module<ExportFormat>("HeatMapHTML", "Create HTML page that shows which verses contain certain features.", HeatMapHTML.HELP_TEXT, HeatMapHTML.class));
		result.add(new Module<ExportFormat>("SwordSearcher", "Export format for SwordSearcher", SwordSearcher.HELP_TEXT, SwordSearcher.class));
		result.add(new Module<ExportFormat>("Obsidian", "Export to Markdown for Obsidian (one chapter per file)", Obsidian.HELP_TEXT, Obsidian.class));
		result.add(new Module<ExportFormat>("AugmentGrammar", "Analyze used Grammar information in one bible and augment another one from that data.", AugmentGrammar.HELP_TEXT, AugmentGrammar.class));
		result.add(new Module<ExportFormat>("LaridianPocketBible", "Export to Laridian Pocket Bible", LaridianPocketBible.HELP_TEXT, LaridianPocketBible.class));
		return result;
	}

	@Override
	public Collection<Module<RoundtripFormat>> getRoundtripFormats() {
		List<Module<RoundtripFormat>> result = new ArrayList<ModuleRegistry.Module<RoundtripFormat>>();
		result.add(new Module<RoundtripFormat>("Compact", "A text-format that is small and well-compressible.", Compact.HELP_TEXT, Compact.class));
		result.add(new Module<RoundtripFormat>("Diffable", "A VPL-like text-format that can be diffed easily.", Diffable.HELP_TEXT, Diffable.class));
		result.add(new Module<RoundtripFormat>("RoundtripHTML", "Roundtrip HTML Export", RoundtripHTML.HELP_TEXT, RoundtripHTML.class));
		result.add(new Module<RoundtripFormat>("RoundtripXML", "Roundtrip XML Export", RoundtripXML.HELP_TEXT, RoundtripXML.class));
		result.add(new Module<RoundtripFormat>("ZefaniaXML", "Zefania XML - well known bible format.", ZefaniaXML.HELP_TEXT, ZefaniaXML.class));
		result.add(new Module<RoundtripFormat>("ZefaniaXMLRoundtrip", "Zefania XML - well known bible format (Roundtrip converter).", ZefaniaXMLRoundtrip.HELP_TEXT, ZefaniaXMLRoundtrip.class));
		result.add(new Module<RoundtripFormat>("ZefDic", "Zefania Dictionary - dictionaries for well known bible format.", ZefDic.HELP_TEXT, ZefDic.class));
		result.add(new Module<RoundtripFormat>("YCHPalmBible", "YCHBibleConverter for PalmBible+", YCHPalmBible.HELP_TEXT, YCHPalmBible.class));
		result.add(new Module<RoundtripFormat>("HaggaiXML", "Haggai XML - used by Free Scriptures project.", HaggaiXML.HELP_TEXT, HaggaiXML.class));
		result.add(new Module<RoundtripFormat>("OSIS", "Very rudimentary OSIS (Open Scripture Information Standard) import/export.", OSIS.HELP_TEXT, OSIS.class));
		result.add(new Module<RoundtripFormat>("TheWord", "Bible format used by theWord", TheWord.HELP_TEXT, TheWord.class));
		result.add(new Module<RoundtripFormat>("UnboundBible", "Bible format downloadable from Biola University", UnboundBible.HELP_TEXT, UnboundBible.class));
		result.add(new Module<RoundtripFormat>("Accordance", "Bible format for Accordance", Accordance.HELP_TEXT, Accordance.class));
		result.add(new Module<RoundtripFormat>("USFM", "Bible format used by Paratext", USFM.HELP_TEXT, USFM.class));
		result.add(new Module<RoundtripFormat>("USFX", "XML Bible format based on USFM used by ebible.org", USFX.HELP_TEXT, USFX.class));
		result.add(new Module<RoundtripFormat>("USX", "XML Bible format used by Paratext and the Digital Bible Library (version 2)", USX.HELP_TEXT, USX.class));
		result.add(new Module<RoundtripFormat>("USX3", "XML Bible format used by Paratext and the Digital Bible Library (version 3)", USX3.HELP_TEXT, USX3.class));
		result.add(new Module<RoundtripFormat>("ParatextDump", "Dump a Paratext bible to diffable plain text", ParatextDump.HELP_TEXT, ParatextDump.class));
		result.add(new Module<RoundtripFormat>("ParatextVPL", "VPL inspired format using Paratext tags", ParatextVPL.HELP_TEXT, ParatextVPL.class));
		result.add(new Module<RoundtripFormat>("RoundtripODT", "ODT export and re-import", RoundtripODT.HELP_TEXT, RoundtripODT.class));
		result.add(new Module<RoundtripFormat>("BibleWorks", "Plain text import and export format for BibleWorks", BibleWorks.HELP_TEXT, BibleWorks.class));
		result.add(new Module<RoundtripFormat>("BibleWorksRTF", "RTF import and export format for BibleWorks", BibleWorksRTF.HELP_TEXT, BibleWorksRTF.class));
		result.add(new Module<RoundtripFormat>("RoundtripTaggedText", "A text-format that consistently uses numbered tags to make automated editing easy.", RoundtripTaggedText.HELP_TEXT, RoundtripTaggedText.class));
		result.add(new Module<RoundtripFormat>("SoftProjector", "Bible format used by SoftProjector", SoftProjector.HELP_TEXT, SoftProjector.class));
		result.add(new Module<RoundtripFormat>("BebliaXML", "Beblia XML format.", BebliaXML.HELP_TEXT, BebliaXML.class));
		return result;
	}

	@Override
	public Collection<Module<Tool>> getTools() {
		List<Module<Tool>> result = new ArrayList<ModuleRegistry.Module<Tool>>();
		result.add(new Module<Tool>("help", "Show help about an import format, export format or tool.", HelpTool.HELP_TEXT, HelpTool.class));
		result.add(new Module<Tool>("MobiPocketTOCBuilder", "Create MobiPocket TOC file from multiple bibles", MobiPocketTOCBuilder.HELP_TEXT, MobiPocketTOCBuilder.class));
		result.add(new Module<Tool>("ESwordRTFPostprocessor", "Postprocess RTF for exporting to E-Sword", ESwordRTFPostprocessor.HELP_TEXT, ESwordRTFPostprocessor.class));
		result.add(new Module<Tool>("ValidateXML", "Validate one or more XML files according to a XSD schema.", ValidateXML.HELP_TEXT, ValidateXML.class));
		result.add(new Module<Tool>("Versification", "Change versification databases or query information from them.", VersificationTool.HELP_TEXT, VersificationTool.class));
		result.add(new Module<Tool>("ParatextConverter", "Convert between Paratext formats without information loss", ParatextConverter.HELP_TEXT, ParatextConverter.class));
		return result;
	}

	@Override
	public Collection<Module<VersificationFormat>> getVersificationFormats() {
		List<Module<VersificationFormat>> result = new ArrayList<ModuleRegistry.Module<VersificationFormat>>();
		result.add(new Module<VersificationFormat>("BMCV", "Versification format internally used by BibleMultiConverter.", BMCV.HELP_TEXT, BMCV.class));
		result.add(new Module<VersificationFormat>("KJV", "Versification format for the hard-coded KJV versification.", KJV.HELP_TEXT, KJV.class));
		result.add(new Module<VersificationFormat>("CCEL", "Versification format used by http://www.ccel.org/refsys/", CCEL.HELP_TEXT, CCEL.class));
		result.add(new Module<VersificationFormat>("OpenScriptures", "Versification format used by https://github.com/openscriptures/BibleOrgSys/", OpenScriptures.HELP_TEXT, OpenScriptures.class));
		result.add(new Module<VersificationFormat>("AccordanceReferenceList", "Build a versification from a plain text reference list (English book names) exported from Accordance.", AccordanceReferenceList.HELP_TEXT, AccordanceReferenceList.class));
		result.add(new Module<VersificationFormat>("SoftProjectorMapping", "Build a versification mapping from a SoftProjector bible file.", SoftProjectorMapping.HELP_TEXT, SoftProjectorMapping.class));
		result.add(new Module<VersificationFormat>("ReportHTML", "HTML report that shows difference of covered verses.", ReportHTML.HELP_TEXT, ReportHTML.class));
		result.add(new Module<VersificationFormat>("Bible", "Extract versification from a Bible or create dummy Bible from versification.", Bible.HELP_TEXT, Bible.class));
		return result;
	}
}
