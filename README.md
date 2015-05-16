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
- **RoundtripHTML**: HTML format that can be read back if desired (originally
  intended for publishing on free website hosters, but with the advent of free
  file hosters this feature is pretty much obsolete).

In addition, the following other formats are supported, with varying accuracy:

- **HaggaiXML**: import and export
- **OSIS**: import and export (only a very limited subset of OSIS standard)
- **ZefaniaXML**: import and export (There are two import filters and three export
  filters available that focus on different subsets/features of this quite diverse
  format)
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


Planned formats
---------------

I intend to add importer and exporter for theWord Bibles (export is currently possible
by importing Zefania XML in theWord), and an importer for SWORD bibles (export is 
currently already possible via OSIS export and osis2mod).

I also plan lossless import and export to ODT, to make manual editing of the Bible text
easier. But no guarantees here, I don't know how hard ODT import will be at the end.

Dictionary support will be improved; at least import and export of ZefDic format will
be added in the future.

EPUB export is also planned (but not high priority at the moment).

If you want to see any other formats, feel free to open an issue (or a pull request :-D).

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

As LibreOffice has some limitations in exporting of hyperlinks, if your original bible
contained Grammar information (Strongs or Morphology tags), you will have to run the
resulting DOCX file through **LogosNestedHyperlinkPostprocessor** or the Grammar
information will look broken in Logos.

[In case anybody wants to contribute a Logos exporter that directly writes .docx files,
it will be very much appreciated.]