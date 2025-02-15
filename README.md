BibleMultiConverter
===================

Converter written in Java to convert between different Bible program formats

Copyright (c) 2007-2025 Michael Schierl
Licensed unter MIT License; for details, see the LICENSE file.

Usage
-----

If you clone from Git or download a source zip, you will need a Java JDK 8 or above (tested up to 11),
and Apache Maven 3.5 or above, to build. Just run "mvn package" and you will find
a suitable distribution .zip file in the TARGET folder.

If you download a precompiled .zip file, you will need a Java Runtime Environment
8 or above, available from java.com. Just run

    java -jar BibleMultiConverter.jar

on the command line for usage information. Each module has its own help,
which can be shown by using the "help" module.


Documentation
-------------

The documentation is currently a bit lacking. Try the commands, or look at the
source, or open an issue if anything is unclear.


Supported Formats
-----------------

BibleMultiConverter supports four custom formats, which are loss-less (support
all features supported by the BibleMultiConverter framework) and are supported
both for import and for export:

- **Compact**: Designed for creating small text files that compress well
- **Diffable**: Designed to make comparing different bibles easier
- **RoundtripTaggedText**: Similar to **Diffable**, but optimized for
  automated editing using regular expression, at the expense of legibility
- **RoundtripXML**: Useful for interchange of modules with converters written
  in other programming languages (that prefer XML binding to plaintext parsing)

Note that the **Diffable** format got new features in v0.0.3, v0.0.8 and v0.0.9, which
are backwards compatible but *not* forwards compatible. Use the **OldDiffable** format
to export Bibles in v0.0.9 so that v0.0.8 or older versions are guaranteed to be able
to read them.

In addition, there are other formats that can preserve all features supported by
the BibleMultiConverter framework, and can therefore used for exchanging or editing
modules without loss of data:

- **RoundtripHTML**: HTML format that can be read back if desired (originally
  intended for publishing on free website hosters, but with the advent of free
  file hosters this feature is pretty much obsolete).
- **RoundtripStructuredHTML**: Similar to above, but converts the structure (i. e.
  paragraphs, headlines, tables) as toplevel elements, and not the verses. Often results
  in better HTML when converting from Paratext formats.
- **RoundtripODT**: Export as an editable .odt (OpenOffice/LibreOffice Document
  Text), which can be edited in LibreOffice (tested with LibreOffice 6.0) and later
  imported again. Large bibles can take a minute or so to open in LibreOffice 6, which
  is quite some improvement since LibreOffice 5.x sometimes took more than 15 minutes.
  Note that all formatting is exported as named Paragraph or Text styles, and other
  individual formatting will be ignored when importing.

In addition, the following other formats are supported, with varying accuracy:

- **[HaggaiXML](http://www.freie-bibel.de/official/projekt/haggai_xml.html)**: import and export
- **[OSIS](https://ebible.org/osis/)**: import and export (only a very limited subset of OSIS standard)
- **[ZefaniaXML](https://bgfdb.de/zefania.de/)**: import and export (There are two import filters and three export
  filters available that focus on different subsets/features of this quite diverse
  format)
- **ZefDic** (Zefania's dictionary format): import and export (two export filters)
- **[TheWord](https://www.theword.net/)**: import and export
- **[PalmBible+](http://palmbibleplus.sourceforge.net/)**: import and export (for the XML-like format used to build PDBs)
- **[UnboundBible](https://unbound.biola.edu/)**: Import and export
- **[MobiPocket](https://en.wikipedia.org/wiki/Mobipocket)**: export only
- **[Volksbibel2000](http://www.volksbibel-2000.de/)**: export only
- **[OnLineBible](https://onlinebible.net/)**: export only
- **[BrowserBible](https://github.com/digitalbiblesociety/browserbible-3/)**: export only
- **[Quick Bible](http://www.bibleforandroid.com/)**: export only
- **[SWORD](https://www.crosswire.org/sword) modules**: import only (see below for details)
- **Original Languages with tagging**: import only
  - [MorphGNT](https://github.com/morphgnt/sblgnt)
  - [OpenScriptures Hebrew Bible (OSHB) MorphBB](https://github.com/openscriptures/morphhb)
  - [Translators Amalgamated Hebrew Old Testament / Greek New Testament](https://github.com/schierlm/STEPBible-Data) (basic features only)
- **[MyBible.Zone](https://mybible.zone/index-eng.php)** ([more bibles](http://www.ph4.org/b4_index.php)): import and export (in a special SQLite edition)
- **[Bible Analyzer](http://www.bibleanalyzer.com/)**: export only (text export for
  bibles and dictionaries, SQLite export for bibles)
- **[Accordance](https://www.accordancebible.com/)**: import and export
- **[BibleWorks](https://www.bibleworks.com/)**: import and export
- **[Equipd Bible](https://www.equipd.me/)**: export only
- **[SoftProjector](https://softprojector.org/)**: import and export
- **Paratext:**
  - **[USFM 2](https://markups.paratext.org/usfm/)**: import and export
  - **[USX 2/USX 3](https://markups.paratext.org/usx/)**: import and export
- **[USFX](https://ebible.org/usfx/)**: import and export
- **[UBXF](https://resource-container.readthedocs.io/en/latest/ubxf.html)**: import (and conversion to "normal" USFM)
- **[SwordSearcher](https://www.swordsearcher.com/) ([Forge](https://www.swordsearcher.com/forge/))**: export only
- **[MySword](https://www.mysword.info/)**: import and export
- **[Obsidian](https://obsidian.md/)**: export only
- **[Beblia XML](https://beblia.com/)**: import and export
- **[Laridian Book Builder](https://www.laridian.com/)**: export only
- **[e-Sword](https://e-sword.net/)**: export only

In combination with third party tools, other export formats are available:

- In combination with LibreOffice 4.4 to 7.5, it is possible to export bibles for
  Logos Bible Software (see below for details)
- In combination with the E-Sword ToolTipTool NT v2.51, it is possible to
  export bibles for E-Sword versions older than 11 (see below for details)

While the focus of this tool is for bible texts, there is also limited support
for (Strong) dictionaries.

The **StrongDictionary** import filter downloads a public domain Strong dictionary
and compiles it for exporting as HTML, MobiPocket, Logos or ZefDic (currently no
other exporters support dictionaries).

The **StrongConcordance** import filter takes a Strong dictionary and a Bible and
augments the dictionary with concordance information (i. e. links that link back
to all verses that contain this word in that particular Bible).

Three utility exporters are also available: **Validate** validates the syntax of a
bible file, and **StrippedDiffable** exports a Diffable, but removes certain features
(like prologs, footnotes, headlines, etc.) In case you want to rename or remove
certain books automatically, have a look at the **Diffable** importer, which provides
options for that. **HeatMapHTML** can generate heat maps (or verse statistics) which show
how often a certain feature (e. g. a footnote, a divine name, or the word "Jesus") appears
in the Bible and where exactly.

**AugmentGrammar** can analyze the use of grammar information (Strongs, morphology,
source indices) in one or more bible modules and use this information to augment
other modules (e.g. modules that contain Strongs but no morphology). It can also be
used to dump grammar information as a CSV file to analyze it elsewhere, or generate
source indices in Original Language modules by counting grammar info tags.

The **ValidateXML** tool can be used to validate an input XML file against a XSD schema.
The schema can be given as a file, as an URL or one of the embedded schema names `OSIS`,
`ZefaniaXML`, `HaggaiXML`, `RoundtripXML`, `USFX`, `USX` or `ZefDic`. This is useful as in case of an
invalid XML input file, the schema usually provides better error messages than what is
provided by the import modules.

The **SQLiteDump** tool (part of the SQLite edition) can dump SQLite databases in a
diffable text format; useful for diagnosing problems with Bible programs that use
SQLite based formats or for importing MyBible.Zone bibles.

The **ParatextConverter** tool can be used to convert between USFM/USFX/USX formats
without converting to BibleMultiConverter's internal format first, or to remove
tagged OT/NT/Deuterocanonical content from such a file. It can also be used to convert
from/to **ParatextDump** (which is a diffable plain text dump of the internal Paratext
structure and useful for comparing different Paratext formats), **ParatextCompact* (a more compact
representation intended for archival which will remain forward compatible) and **ParatextVPL** (which is
a different diffable format that looks more like VPL, but uses Paratext tags) formats. In combination
with the **ParatextStripped** format, various features of the file can be stripped or it can be
made compatible to an older USFM version. And **ParatextValidate** format can be used to validate
inconsistencies of the paratext format, like unclosed/unopened milestones or paragraph markers
without text or unexpected extra text.

The **MyBibleZoneListDownloader** tool (part of SQLite edition) can be used to download
the list of available MyBible.Zone modules from the module registry (that is also queried
by the Android app) and create a HTML file with download links and (JavaScript) filters.
This is needed as the website apparently does not include all modules available in the app.

Planned formats
---------------

EPUB export is planned (but not high priority at the moment).

If you want to see any other formats, feel free to open an issue (or a pull request :-D).


New `FormattedText` Model
-------------------------

BibleMultiConverter is currently switching to a new `FormattedText` model (intermediate format) which can
handle USX3 and USFM3 inputs better. Some formats are **not** yet converted, and therefore are still
limited to the old intermediate format.

**Status:**

| Format                               | Implementation *(missing features*)      | Testing        |
| ------------------------------------ | ---------------------------------------- | -------------- |
| `NeUeParser`                         | complete                                 | extensive      |
| `Validate`                           | complete                                 | partial        |
| `Compact`                            | complete                                 | unit tests     |
| `Diffable`                           | complete                                 | unit tests     |
| `RoundtripTaggedText`                | complete                                 | unit tests     |
| `RoundtripXML`                       | complete                                 | unit tests     |
| `AbstractParatextFormat`             | partial *(maybe complete?)*              | unit tests     |
| `RoundtripHTML`                      | partial *(Show `ga-` attributes)*        | partial        |
| `RoundtripStructuredHTML`            | partial *(Show `ga-` attributes)*        | partial        |
| `RoundtripODT`                       | partial *(Handling of hyperlinks)*       | unit tests     |
| `AbstractVersificationDetector`      | complete                                 | none           |
| `AugmentGrammar`                     | complete                                 | none           |
| `AugmentLogosLinks`                  | complete                                 | none           |
| `BebliaXML`                          | complete                                 | none           |
| `LogosRenumberedDiffable`            | complete                                 | none           |
| `MorphGNT`                           | complete                                 | none           |
| `OldDiffable`                        | complete                                 | none           |
| `ReplaceStrongs`                     | complete                                 | none           |
| `SimpleJSON`                         | complete                                 | none           |
| `StrippedDiffable`                   | complete                                 | none           |
| `TranslatorsAmalgamated`             | complete                                 | none           |
| `VersificationCountsDetector`        | complete                                 | none           |
| `VersificationMappedDiffable`        | complete                                 | none           |
| `Accordance`                         | partial                                  | none           |
| `LaridianPocketBible`    (2x)        | partial                                  | none           |
| `LogosHTML`                          | partial                                  | partial        |
| `MobiPocket`                         | partial                                  | none           |
| `MyBibleZone`                        | partial                                  | none           |
| `OSIS` + `SWORD`                     | partial                                  | none           |
| `BibleAnalyzerDatabase`              | minimal                                  | none           |
| `BibleWorks`                         | minimal                                  | none           |
| `BrowserBible`                       | minimal                                  | none           |
| `ESwordHTML`                         | minimal                                  | none           |
| `ESwordV11`                          | minimal                                  | none           |
| `EquipdEPUB`                         | minimal                                  | none           |
| `HaggaiXML`                          | minimal                                  | none           |
| `MyBibleZoneCrossreferences`         | minimal                                  | none           |
| `MySword`                            | minimal                                  | none           |
| `OSHB`                               | minimal                                  | none           |
| `Obsidian`                           | minimal                                  | none           |
| `OnLineBible`                        | minimal                                  | none           |
| `QuickBible`                         | minimal                                  | none           |
| `SoftProjector`                      | minimal                                  | none           |
| `StrongConcordance`                  | minimal                                  | none           |
| `StrongDictionary`                   | minimal                                  | none           |
| `SwordSearcher`                      | minimal                                  | none           |
| `TheWord`                            | minimal                                  | none           |
| `UnboundBible`                       | minimal                                  | none           |
| `Volksbibel2000`                     | minimal                                  | none           |
| `YCHPalmBible`                       | minimal                                  | none           |
| `ZefDic` *(2x)*                      | minimal                                  | none           |
| `ZefaniaXML` *(3x)*                  | minimal                                  | none           |


Limitations
-----------


When comparing the bible formats that are currently used (both free and commercial), they
can be divided into two broad categories (or paradigms).

**The first category** uses books, prologs, chapters, verses and head-lines as primary
structural elements. As a consequence, formattings/styles cannot span verse boundaries,
and paragraph separators are always inside (or often at the end of) verses.

Zefania XML, Haggai XML, TheWord, PalmBible+, e-Sword, SWORD, most online bible
websites, and also the commercial MfChi
format follow this category. Therefore, the format internally used by this converter
follows this category.

**The other category**, which covers popular formats like Logos Bible Software or
USFM, treats formattings and paragraphs as primary structural elements. Within these
formattings, so-called milestone markers are used to denote the beginning and end of
chapters and verses. Prologs, appendixes, or headlines are a concept that does not
exist (and neither is required) for this paradigm - text that is inside a chapter
but before the first verse happens to be a chapter prolog, for example.

Non-bible-specific Export formats like MobiPocket, HTML or ePub can also handle
these formats quite well, by skipping all the milestone markers.

**OSIS** is some kind of hybrid format, as the creator can decide whether formatting
or structural elements are represented as nested tags; the other type is then
represented as milestones.

**As this converter internally uses the first category**, conversions between
different second-category formats (like from verse-milestoned OSIS to Logos) will
always lose more information than needed; if you want to convert this way, perhaps
another converter tool is more appropriate for your needs. As soon as either the
input or the output format is of first category, this converter probably outperforms
other converters easily.

In addition, some Bible formats have very sophisticated formatting features (which
are not used by most of the available modules), like several paragraph styles or even
list and tables. All these formats get reduced to the bare minimum: paragraph breaks
as well as line breaks with and without indentation.


Reporting exporter bugs
-----------------------

In case you are trying to export a module, but the exporter throws an error message you
do not understand, I'd prefer if you could share a **Diffable** version of the module
with me. However, I understand that this is not always possible, e.g. due to copyright
restrictions. In that case, you can try if the bug can still be reproduced after
exporting export the module using the **ScrambledDiffable** export format; this format
is designed to leave the structure of the document intact but scramble all the (Greek
and Latin) letters and digits are scrambled beyond repair (or repairable with a password
if you prefer).

Try using `=23` as argument first, which should replace all letters by 'X', resulting in
a well compressible file. In case that one does not reproduce the bug, use without
arguments (random numbers). In case you want to share a Bible where others are able to
compare if their verses are the same, use '#SHA-1' as argument; that way, the same verse
will scramble to the same 'ciphertext', so the resulting files are still diffable although
unreadable. In case you have to be able to reverse the scrambling (if the whole file is
unchanged), you can use '+Password' for initial scrambling and '-Password' for later
decrypting. To verify if two bible were scrambled from the same source (using different
parameters), scramble them again in constant mode, and diff the results. Note that since
'encrypting' uses a stream cipher, if you use the same password for more than one file, an
attacker with cryptanalysis background that knows only this piece of information can use it
for correlation attacks to get the plain text. Therefore, use different passwords for
multiple bibles (like, add the bible name to them), or better, use real encryption like AES
instead.


Versification handling
----------------------

Most Bible formats do not care about versifications (they just store `book chapter:verse`
without caring how many verses a certain chapter has), or support only a single versification
(usually `KJV` or `KJVA`). Exceptions being SWORD and Logos, which encode the versification mapping
in the bible itself. Therefore, this converter usually does not care about versifications; in case
a format is limited to a versification, verses will be merged until they fit.

However, there is some support for handling "external" versification mappings (stored in `.bmcv`
files). A `Versification` tool can be used to import and export versification mappings from
different formats, and perform some other modifications (like renaming or deleting versifications,
or joining or comparing them). A `VersificationDetector` export filter can be used to determine
which versification in a `.bmcv` file fits a given bible best. And a `VersificationMappedDiffable`
can be used to "change" the versification of an existing Bible text.

When referring to versification mappings in a file, the general syntax is `from`/`to` or
`from`/`to`/`number`. Use the latter form (`number` starts with 1) in case there is more than
one mapping stored in your file between the same two versifications. If you use the form without
number, but there are multiple mappings, the system will automatically try to find the "best" mapping;
i. e. the one that maps more verses and/or maps them more precisely (e. g. a mapping that maps Gen 1:1
to Gen 1:1 and Gen 1:2 to Gen 1:2 is more precise than a mapping that maps Gen 1:1-2 to Gen 1:1-2). In case
this cannot be determined, an error message is shown.

When merging mappings from multiple files, you can come into a situation where you have two versifications
that represent essentially the same bible, but have different names. Trying to join a path between these two
versifications will fail (as there is no common mapping). In this case, you can write `name1`/`name2`/-1 to
dynamically create an identity mapping: all verses that exist in both versifications automatically map to
themselves, all other verses are unmapped.

Supported Versification formats:

- **BMCV** (own database format): Import and export (versifications and mappings)
- **KJV** (hard-coded KJV versification): "Import" only (no mapping)
- **[CCEL](https://web.archive.org/web/20190204014352/https://ccel.org/refsys/refsys.html)**: Import and export (versifications and mappings)
- **[OpenScriptures](https://github.com/openscriptures/BibleOrgSys/tree/master/BibleOrgSys/DataFiles/VersificationSystems)**: Import only (versifications only, no mappings)
- **SWORDVersification**: Import versifications and mappings from SWORD
- **AccordanceReferenceList**: Import a reference list from Accordance
- **SoftProjectorVersification**: Import versification mapping from SoftProjector bible
- **ReportHTML**: Export only (HTML report that shows difference of covered verses)

SWORD import
------------

As the SWORD format is quite complex, I'm using a third party library JSword for parsing it.
That library adds quite some footprint to the application (almost 20MB) so SWORD import is
only available in a special SWORD edition, which is available as a separate download (but
in the same source repository).

SWORD is special in the sense that you do not have a file to import, but a module
directory and some bibles in there to import (specified by initials). Just separate those
by a slash, and use this as the filename.

In case you do not have a SWORD module directory locally, you can use the **SWORDDownloader**
tool to download some bibles from a SWORD http repository into a new module directory.


Paratext formats (USFM/USX)
---------------------------

Not all verse number formats are supported by Paratext, the following conversions can occur
when converting from a non Paratext format to a Paratext format (warnings will be shown
when these occur):

    - 11/13 becomes 11
    - 4.6.9 becomes 4
    - 2G becomes 2
    - 10/12G becomes 10
    - 1-4.7 becomes 1-4
    - 1.4-7 becomes 1

UBXF Support
------------

UBXF bibles are based on USFM 3 but contain extra alignment milestones that can be used to
align the text to another UBXF bible. In general, the `\w` tags in UBXF bibles only contain
lemma and morphology information if the bible is in a source language - other bibles contain
alignment information to another Bible instead. BibleMulticonverter can perform the following
operations on UBXF files:

- Add `srcloc` attributes to `\w` tags of source bibles, simply by counting them within a verse
- Create a database file that contains word information from a (source language) bible and maps
  it to Lemma/Strongs/Morphology
- Use this database file to augment alignment milestones in a translated bible with those data
- Create new `\w` tags for parts between aligment milestones, if your bible does not have them
  and/or you want them grouped by source word not by translated word.
- Fill `\w` tag attributes based on the augmentations in the alignment milestones (to convert to
  a format that does not support aligment milestones, i.e. most formats)
- Convert grammar tags (strongs,morphology) from the format used by
  [unfoldingWord][https://unfoldingword.us/] to normal Strongs/RMAC/WIVU tagging used by other formats

Depending on which UBXF translations you have and what format you ultimately want to convert them
to, you will have to find out which of the steps above you want to perform and which ones not.


E-Sword export
--------------

BibleMultiConverter has two export filters. One (**ESwordV11**) targets the new (version 11)
module format, which is based on SQLite and HTML, and can be exported without any third party
tools and natively supports Strong's numbers and footnotes.
The other one is the format for older versions (**ESwordHTML**), requires third party tools
(ToolTipTool), and does not support native footnotes (puts them into a commentary module instead).
The rest of this chapter is about the second one.

To export for E-Sword, first use the ESwordHTML export filter, which generates two
HTML files (.bblx.html and .cmtx.html) which can then imported into ToolTipTool and
converted to an E-Sword Bible. As the HTML import filter of ToolTipTool is a bit buggy
and nondeterministic (it tends to insert line breaks in the middle of lines, resulting
in conversion errors; sometimes it helps to just import the same file again, sometimes
not) there is a special "marker" parameter that can be set to anything that does not
occur in the bible text (`$EOF$` works fine usually). Then import in ToolTipTool NT
(dont mind if extra newlines get added), export in ToolTipTool NT as RTF, and run
**ESwordRTFPostprocessor** over the RTF file to fix it. The fixed RTF file can then be
imported in ToolTipTool without issues and converted as desired.

[In case you know an easier way to deal with this issue, please tell me :-). If you
want to contribute an E-Sword exporter that directly writes the SQL Database files,
it will be very much appreciated.]


Logos Bible Software export
---------------------------

As the dependencies are quite large and non-free, this feature is only available
in the "Logos Edition" which is a separate binary download (but included in the
same source repository). As this format is quite complex (compared to the others),
this export is a multi-step process:

First run the **LogosVersificationDetector**, which will find a verse map for you
that covers (hopefully) all verses of your Bible. Then run **LogosHTML** to produce
a HTML file, which you can open in LibreOffice (HTML Writer format) and save as .docx
(Office 2007 XML format). Note that HTML import in LibreOffice 7.6 is totally broken,
and in LibreOffice 24.2 it changed in incompatible ways, for now please use LibreOffice
7.5 or older for conversion.

In case your bible contains cross references to books/verses that are not covered
by your bible itself, don't forget to pass the `-xref` option to the versification
detector, as Logos will not render datatype links to cross references that do not
exist in the verse map (In case you do not get a match that covers both your verses
and your xrefs, but there is a match that covers all verses, use that one, as it is
better to lose datatype links for your cross references, than to lose some verses).

As LibreOffice has some limitations in exporting of hyperlinks, if your original bible
contained Grammar information (Strongs or Morphology tags), you will have to run the
resulting DOCX file through **LogosNestedHyperlinkPostprocessor** or the Grammar
information will look broken in Logos.

[In case anybody wants to contribute a Logos exporter that directly writes .docx files,
it will be very much appreciated.]

### Logos Word Numbers

An integral part of Logos' native resources are word numbers. These are used to link
individual words in a Bible text to their greek/hebrew/aramaic sources and power features
like word-for-word-highlighting and most advanced search and Factbook options. If your Bible
includes source indices / source locations that follow any of the editions covered by
[Aligned Bible Corpus Data](https://github.com/schierlm/aligned-bible-corpus-data) (for example
TAHOT, OSHB, LHB, UHB, TAGNT, SBLGNT, RP05, RP18, NA28 or UBS4), you can use this resource alongside with word
numbers lists available on the [Logos Wiki](https://community.logos.com/kb/articles/2793-word-numbers),
to add word numbers to your converted Logos Bible. (In case your Bible does not have source locations, but it has
Strong's numbers and another Bible that uses the same Strong's number scheme has source locations
to a supported edition, you can use the **AugmentGrammar** module to transfer the word numbers
from one resource to the other one). Note that the word number files on the Logos wiki do not include word counts
for verses, so even if you want to use exactly the same edition as covered by the file (e.g. LHB), you still
need to convert the text file to a database and then back to a word map file which includes this information.

First you need to use **LogosWordNumberTool** to build a database that covers your edition.

At the time of writing, this example imports all available word number files into two databases for OT and NT:

    LogosWordNumberTool ot.db import abcd/hebrew_mini.csv LHB wiki/wordnumber-lhb.txt
    LogosWordNumberTool nt.db import abcd/greek_mini.csv SBLGNT wiki/wordnumber-sblgnt.txt
    LogosWordNumberTool nt.db importapparatus abcd/sblgnt_apparatus.csv wiki/wordnumber-sblgnt-apparatus.txt SBL NA28 RP05
    LogosWordNumberTool nt.db import abcd/na28+ubs4.csv NA28 wiki/wordnumber-na28-additions.txt
    LogosWordNumberTool nt.db import abcd/greek_mini.csv RP05 wiki/wordnumber-rp05-additions.txt

Then, convert your db to a word map for the desired target edition using the **exportmappeddb** subcommand.

When you have an edition covered by the corpus data but without individual word number data (like UBS4, RP18, OSHB),
some word number assignments may be ambiguous: When your edition, compared to one with word number data available,
replaces a single word with a single other word, that new word may get assigned the same word number or a different one.
In the alignment data, it is usually mapped to the same line, so by default, it will get assigned the same word number
in the converted Bible. If you want to avoid that, use the **updatewordlock** subcommand to create a wordlock file for
your source edition(s); the target edition will then only use the word if it matches one of your source editions (after
normalizing Unicode and stripping non-letters and modifier letters).

Finally, use the `-Dbiblemulticonverter.logos.wordnumberfiles` option to point to your word maps (one for OT and one for NT)
while using the **LogosHTML** export module.


Accordance export
-----------------

Accordance export also is quite complex, but this time not because of the complexity
of the format, but because of some quirks in Accordance and because - due to the
limited features - there are several common workarounds available (which are used by
people when manually creating modules as well), but which do not apply to every module.
Sometimes, it makes even sense to create multiple modules of the same bible with
different options and use them at the same time.

Character formats are limited to bold, italic, underlined, small caps, subscript,
superscript, and 20 named colors. While it is quite common to format divine names
in small caps, and words of Jesus in red (which by the way is the only color that
can be switched on and off in the settings), it is unclear how to format e.g.
prologs, headlines or footnotes, or whether to include them at all. Another
peculiarity: When searching in the module, you can exclude text that is written
in square brackets; therefore some people like to put these kinds of content
into square brackets; others (who prefer "clean" display) do not.

Therefore, it is possible to configure the appearance of each of the available
Bible features individually, and whether they should be exported at all. Features
used in the text that are not configured (except the aforementioned divine names
and words of Jesus) cause a warning. Each feature can be configured independently
how it should appear if it occurs in a chapter prolog. Available appearance options
include the mentioned character formats, adding newlines or paragraphs before/after
the items, and putting the item in round, square or curly brackets.

As character set, MacRoman or UTF-8 can be used; while MacRoman is supported better
by older Accordance versions, UTF-8 supports more characters. Line endings can be CR
or LF; again CR for better compatibility, LF for interoperability with other editors.

For all the options, see the help text of the module.


Another point to keep in mind are versification schemas.

When importing a Bible in accordance, there are basically two options for the
Versification schema:

- When choosing "Standard KJV", you are quite free what verse numbers to include.
  As long as they basically fall inside the bounds of the KJV (about 10 more verses
  are permitted per book), verse numbers can be omitted and the number of actual
  verses in a book does not matter (as long as there is at least one). As a
  disadvantage, viewing this Bible in parallel with other (official) Bibles will
  likely not match your expectations.

- The other option is to choose to take the versification scheme of any other
  Bible you own. In that case, the verse numbers (and order and gaps) have to match
  the verse numbers of that Bible exactly. If verses at the beginning or in the middle
  are missing, wrong verse numbers are displayed next to the verses (even when the Bible
  is shown alone), and if verses at the end are missing or too many, only the parallel
  view is impacted. But, on the other hand, parallel display with other Bibles will
  most likely work as you would expect.

To cover these two variants, there are several options in the exporter.

- By default, reordered verses will be sorted (and the format for the real verse
  number can be given by the `VN=` option, `VN=BRACKETS+CERULEAN` is a good option),
  but apart from that, verses will stay as they were in the import file. This option
  is often suitable for "Standard KJV" versification.

- When specifying `verseschema=restrictkjv`, verses that lie outside
  the KJV versification will be joined, again using the `VN=` option for the real
  verse numbers.

- When specifying `verseschame=fillone`, verses that are missing and lie before
  an existing verse (starting with verse 1) will be filled with `-`. This is the
  poor man's option when using an existing versification scheme, in the hope that
  this scheme does not have any reorderings or gaps, and will not help for
  parallel view.

- When specifying `verseschema=fillzero`, some Psalms (in particular 3-9, 11-32,
  34-42, 44-70, 72-90, 92, 98, 100-103, 108-110, 120-134, 138-145) will be filled
  starting with verse 0. This helps for versification schemes that follow KJV and
  put the psalm title into verse 0, but still only a workaround.

- The best option when using custom versifications is to export a reference list
  from Accordance (or more if you are unsure which versification to use), import
  them into BibleMultiConverter and then specify the versification as
  `verseschema=<name>@<dbname>`. In that case, the versification schema is
  followed exactly, omissions are moved to the previous verse (if present) and
  reorderings are followed at a best effort basis. In addition, missing verses
  at the end of the book will also be filled with `-`, and extra books will be
  merged with the previous book.

- In some cases, the total verses of a book would follow an existing versification
  exactly, except that the chapter boundaries are moved. There are two options how
  this can be handled: either add a `#` sign to the `verseschema=` option (like
  `verseschema=#<name>@<dbname>`), which means that the export will still follow the
  verse schema exactly, only that in case a book contains both gaps (empty verses)
  and merged verses, verse content gets shifted to keep every verse separate (adding
  real verse numbers to the beginning of the verse text). Another option exploits an
  implementation detail in Accordance 13: In some cases, it is possible to import for
  a versification in case the total verse number matches but not the chapter boundaries
  (e.g if a versification has chapters with 13 and 9 verses, and the bible has 12 and 10
  verses, it imports fine). To allow that kind of verse number tweaking,  you can give a
  `verseschemashift=<nbr>` option, where `<nbr>` is the number of verses that may
  be added/removed from a single chapter. In case a chapter has gaps at the end of
  one chapter and merged verses at the end of another chapter, the verse schema is
  tweaked accordingly. If you want to see when this happens, add a `+` to the beginning
  of the number.

The usual workflow when using custom versifications is as follow

1. Identify which versification formats you may want to use (or export all
   if you are unsure and have the time/patience to do so).

2. Export a versification list from Accordance. To do so, open the corresponding
   Bible, choose `Display->Set Text Pane Display->Show As->References only` as well
   as `Display->Set Text Pane Display->Advanced->Use English Book Names`. Then, select
   all verses (`Edit->Select All`) and save them using
   `File->Save Text Selection->Plain Text...`.

3. Import the versifications into BibleMultiConverter. Therefore, a database file
   with extension `.bmcv` (BibleMultiConverter Versification) is created. The command
   to do so is:

       java -jar BibleMultiConverter.jar Versification <file>.bmcv import AccordanceReferenceList <file>.txt <NAME>

   Repeat this for every versification you want to import, into the *same* database
   file, but using *different* names.

4. Run **VersificationDetector** to decide which module to use. Use the options
   `-title` and `-ignoreheadlines` for best results.

5. Do the actual conversion, using the `verseschema=` option as mentioned above.