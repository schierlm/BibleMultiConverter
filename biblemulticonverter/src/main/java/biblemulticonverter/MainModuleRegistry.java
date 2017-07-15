package biblemulticonverter;

import java.util.*;

import biblemulticonverter.format.*;
import biblemulticonverter.tools.*;
import biblemulticonverter.versification.*;

public class MainModuleRegistry extends ModuleRegistry {

	@Override
	public Collection<Module<ImportFormat>> getImportFormats() {
		List<Module<ImportFormat>> result = new ArrayList<ModuleRegistry.Module<ImportFormat>>();
		result.add(new Module<ImportFormat>("StrongDictionary", "Importer for creating a Strong's dictionary from public domain resources.", StrongDictionary.HELP_TEXT, StrongDictionary.class));
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
		result.add(new Module<ExportFormat>("Volksbibel2000", "Export format for reimporting into Volksbibel 2000", Volksbibel2000.HELP_TEXT, Volksbibel2000.class));
		result.add(new Module<ExportFormat>("OnLineBible", "Export format for importing into OnLine Bible", OnLineBible.HELP_TEXT, OnLineBible.class));
		result.add(new Module<ExportFormat>("BrowserBible", "Export format for The Browser Bible 3 by Digital Bible Society", BrowserBible.HELP_TEXT, BrowserBible.class));
		result.add(new Module<ExportFormat>("BibleAnalyzerFormattedText", "Formatted Text Export format for Bible Analyzer", BibleAnalyzerFormattedText.HELP_TEXT, BibleAnalyzerFormattedText.class));
		result.add(new Module<ExportFormat>("QuickBible", "Export format for QuickBible (Bible for Android)", QuickBible.HELP_TEXT, QuickBible.class));
		result.add(new Module<ExportFormat>("Accordance", "Export format for Accordance", Accordance.HELP_TEXT, Accordance.class));
		result.add(new Module<ExportFormat>("EquipdEPUB", "Epub export format for Equipd Bible", EquipdEPUB.HELP_TEXT, EquipdEPUB.class));
		result.add(new Module<ExportFormat>("SimpleJSON", "Very simple JSON bible export (verse text only).", SimpleJSON.HELP_TEXT, SimpleJSON.class));
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
		return result;
	}

	@Override
	public Collection<Module<VersificationFormat>> getVersificationFormats() {
		List<Module<VersificationFormat>> result = new ArrayList<ModuleRegistry.Module<VersificationFormat>>();
		result.add(new Module<VersificationFormat>("BMCV", "Versification format internally used by BibleMultiConverter.", BMCV.HELP_TEXT, BMCV.class));
		result.add(new Module<VersificationFormat>("KJV", "Versification format for the hard-coded KJV versification.", KJV.HELP_TEXT, KJV.class));
		return result;
	}
}
