BibleMultiConverter
===================

Converter written in Java to convert between different Bible program formats

Copyright (c) 2007-2015 Michael Schierl   
Licensed unter MIT License; for details, see the LICENSE file.

Usage
-----

If you clone from Git or download a source zip, you will need a Java JDK 7 or
above, and Apache Maven, to build. Just run "mvn package" and you will find
a suitable distribution .zip file in the TARGET folder.

If you download a precompiled .zip file, you will need a Java Runtime Environment
7 or above, available from java.com. Just run

    java -jar BibleMultiConverter.jar

on the command line for usage information. Each module has its own help,
which can be shown by using the "help" module.


Documentation
-------------

The documentation is currently a bit lacking. Try the commands, or look at the
source, or open an issue if anything is unclear.


Supported Formats
-----------------

BibleMultiConverter supports three custom formats, which are loss-less (support
all features supported by the BibleMultiConverter framework) and are supported
both for import and for export:

- **Compact**: Designed for creating small text files that compress well
- **Diffable**: Designed to make comparing different bibles easier
- **RoundtripXML**: Useful for interchange of modules with converters written
  in other programming languages (that prefer XML binding to plaintext parsing)
- **RoundtripHTML**: HTML format that can be read back if desired (originally
  intended for publishing on free website hosters, but with the advent of free
  file hosters this feature is pretty much obsolete).

In addition, the following other formats are supported, with varying accuracy:

- **HaggaiXML**: import and export
- **OSIS**: import and export (only a very limited subset of OSIS standard)
- **ZefaniaXML**: import and export (There are two import filters and three export
  filters available that focus on different subsets/features of this quite diverse
  format)
- **TheWord**: import and export
- **PalmBible+**: export only
- **MobiPocket**: export only

In combination with third party tools, other export formats are available:

- In combination with LibreOffice 4.4, it is possible to export bibles for
  Logos Bible Software (see below for details)
- In combination with the E-Sword ToolTipTool NT v2.51, it is possible to
  export bibles for E-Sword (see below for details)

While the focus of this tool is for bible texts, there is also limited support
for (Strong) dictionaries.

The **StrongDictionary** import filter downloads a public domain Strong dictionary
and compiles it for exporting as HTML, MobiPocket or Logos (currently no other
exporters support dictionaries).

The **StrongConcordance** import filter takes a Strong dictionary and a Bible and
augments the dictionary with concordance information (i. e. links that link back
to all verses that contain this word in that particular Bible).

Two utility exporters are also available: **Validate** validates the syntax of a
bible file, and **StrippedDiffable** exports a Diffable, but removes certain features
(like prologs, footnotes, headlines, etc.) In case you want to rename or remove
certain books automatically, have a look at the **Diffable** importer, which provides
options for that.

The *ValidateXML* tool can be used to validate an input XML file against a XSD schema.
The schema can be given as a file, as an URL or one of the embedded schema names `OSIS`,
`ZefaniaXML`, `HaggaiXML`, `RoundtripXML` or `ZefDic`. This is useful as in case of an
invalid XML input file, the schema usually provides better error messages than what is
provided by the import modules.


Planned formats
---------------

I intend to add an importer for SWORD bibles (export is
currently already possible via OSIS export and osis2mod).

I also plan lossless import and export to ODT, to make manual editing of the Bible text
easier. But no guarantees here, I don't know how hard ODT import will be at the end.

Dictionary support will be improved; at least import and export of ZefDic format will
be added in the future.

EPUB export is also planned (but not high priority at the moment).

If you want to see any other formats, feel free to open an issue (or a pull request :-D).


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


E-Sword export
--------------

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
(Office 2007 XML format).

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