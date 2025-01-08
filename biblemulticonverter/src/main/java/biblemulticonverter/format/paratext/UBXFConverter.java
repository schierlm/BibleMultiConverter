package biblemulticonverter.format.paratext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import biblemulticonverter.Main;
import biblemulticonverter.data.Utils;
import biblemulticonverter.format.paratext.ParatextBook.ParagraphKind;
import biblemulticonverter.format.paratext.ParatextBook.ParatextBookContentVisitor;
import biblemulticonverter.format.paratext.ParatextBook.ParatextCharacterContentContainer;
import biblemulticonverter.format.paratext.ParatextBook.ParatextID;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormatting;
import biblemulticonverter.format.paratext.ParatextCharacterContent.AutoClosingFormattingKind;
import biblemulticonverter.format.paratext.ParatextCharacterContent.FootnoteXref;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Milestone;
import biblemulticonverter.format.paratext.ParatextCharacterContent.ParatextCharacterContentPart;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Reference;
import biblemulticonverter.format.paratext.ParatextCharacterContent.Text;
import biblemulticonverter.format.paratext.model.ChapterIdentifier;
import biblemulticonverter.format.paratext.model.VerseIdentifier;

/**
 * Exporter that can convert certain UBXF tags.
 */
public class UBXFConverter extends AbstractParatextFormat {

	public static final String[] HELP_TEXT = {
			"Convert some tags in an UBXF bible",
			"",
			"Usage: UBXFConverter createsrcloc <prefix>      -- <ParatextExportFormat> [<ExportArgs>...]",
			"Usage: UBXFConverter analyze <dbfile>",
			"Usage: UBXFConverter augmentmilestones <dbfile> -- <ParatextExportFormat> [<ExportArgs>...]",
			"Usage: UBXFConverter fillwordattr               -- <ParatextExportFormat> [<ExportArgs>...]",
			"Usage: UBXFConverter createwordattr             -- <ParatextExportFormat> [<ExportArgs>...]",
			"Usage: UBXFConverter convertgrammar             -- <ParatextExportFormat> [<ExportArgs>...]",
			"",
			"First add srcloc to source bibles if not present yet.",
			"Then analzye source and augment milestones in derived translations.",
			"Next, you can either fill the existing word attributes with Strongs and morphology",
			"(needed if they are another source translation, or if you want to keep the words separate), ",
			"or you can drop all existing word attributes and create new ones based on milestones (so you",
			"can have multiple words inside one word attribute).",
			"As a final step you can convert the strongs and morphology tags used by unfoldingWord to standard",
			"Strong's/RMAC/WIVU tags (to be usable in other bible software). This step applies to both source and",
			"derived bibles.",
			"It is possible to chain multiple UBXFConverters, if you do not care about the intermediate results."
	};

	public UBXFConverter() {
		super("UBXFConverter");
	}

	@Override
	protected List<ParatextBook> doImportAllBooks(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	protected ParatextBook doImportBook(File inputFile) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void doExportBooks(List<ParatextBook> books, String... exportArgs) throws Exception {
		int formatArg;
		if (exportArgs[0].equals("createsrcloc") && exportArgs[2].equals("--")) {
			final String prefix = exportArgs[1];
			for (ParatextBook book : books) {
				book.accept(new UBXFBookVisitor(book.getId(), false, new UBXFGrammarHandlerVisitor() {
					int index;

					@Override
					public void setWhere(Reference newWhere) {
						super.setWhere(newWhere);
						index = 0;
					}

					@Override
					protected void handleWordlist(AutoClosingFormatting acf, Reference where) {
						index++;
						String srcloc = acf.getAttributes().get("srcloc");
						acf.getAttributes().put("srcloc", (srcloc == null ? "" : srcloc + " ") + prefix + ":" + where.getBook().getNumber() + "." + where.getFirstChapter() + "." + where.getFirstVerse() + "." + index);
					}
				}));
			}
			formatArg = 3;
		} else if (exportArgs[0].equals("analyze")) {
			Map<String, Integer> occurrences = new HashMap<>();
			Map<String, String> words = new HashMap<>();
			for (ParatextBook book : books) {
				book.accept(new UBXFBookVisitor(book.getId(), false, new UBXFGrammarHandlerVisitor() {
					private void extractContent(StringBuilder sb, ParatextCharacterContentContainer pccc) {
						for (ParatextCharacterContentPart part : pccc.getContent()) {
							if (part instanceof ParatextCharacterContentContainer) {
								extractContent(sb, (ParatextCharacterContentContainer) part);
							} else if (part instanceof Text) {
								sb.append(((Text) part).getChars());
							}
						}
					}

					@Override
					protected void handleWordlist(AutoClosingFormatting acf, Reference where) {
						StringBuilder sb = new StringBuilder();
						extractContent(sb, acf);
						String word = sb.toString();
						sb = new StringBuilder();
						for (Map.Entry<String, String> attr : acf.getAttributes().entrySet()) {
							String akey = attr.getKey(), aval = attr.getValue();
							if (akey.startsWith(" ") || akey.indexOf('"') != -1 || aval.indexOf('"') != -1) {
								System.out.println("WARNING: Skipping attribute " + akey + "=\"" + aval + "\" - unable to parse back");
								continue;
							}
							if (!akey.startsWith("x-"))
								akey = "x-" + akey;
							if (sb.length() > 0) {
								sb.append(' ');
							}
							sb.append(akey + "\"" + aval + "\"");
						}
						String key = word + "@" + where.getBook().getIdentifier() + "." + where.getFirstChapter() + "." + where.getFirstVerse();
						int index = occurrences.getOrDefault(key, 0) + 1;
						occurrences.put(key, index);
						words.put(index + "#" + key, sb.toString());
					}
				}));
			}
			final String dbfile = exportArgs[1];
			Properties props = new Properties();
			if (new File(dbfile).exists()) {
				try (FileInputStream fis = new FileInputStream(dbfile)) {
					props.load(fis);
				}
			}
			for (Map.Entry<String, String> entry : words.entrySet()) {
				String[] keyParts = entry.getKey().split("#", 2);
				int count = occurrences.get(keyParts[1]);
				props.setProperty(keyParts[1] + "@" + keyParts[0] + "/" + count, entry.getValue());
			}
			try (FileOutputStream fos = new FileOutputStream(dbfile)) {
				props.store(fos, "UBXF database");
			}
			return;
		} else if (exportArgs[0].equals("augmentmilestones") && exportArgs[2].equals("--")) {
			final String dbfile = exportArgs[1];
			Properties props = new Properties();
			try (FileInputStream fis = new FileInputStream(dbfile)) {
				props.load(fis);
			}
			for (ParatextBook book : books) {
				book.accept(new UBXFBookVisitor(book.getId(), true, new UBXFGrammarHandlerVisitor() {
					@Override
					protected void handleAlignMilestone(boolean start, Milestone milestone, Reference where) {
						if (!start)
							return;
						String key = milestone.getAttributes().get("x-content") + "@" + where.getBook().getIdentifier() + "." + where.getFirstChapter() + "." + where.getFirstVerse() + "@" + milestone.getAttributes().get("x-occurrence") + "/" + milestone.getAttributes().get("x-occurrences");
						String value = props.getProperty(key);
						if (value == null) {
							System.out.println("WARNING: Unable to augment milestone in " + where + " due to missing database key: " + key);
							return;
						}
						String[] keyvals = value.split("\"");
						for (int i = 0; i < keyvals.length; i += 2) {
							String akey = keyvals[i].trim(), avalue = keyvals[i + 1];
							String oldVal = milestone.getAttributes().get(akey);
							if (oldVal != null && !oldVal.equals(avalue)) {
								System.out.println("WARNING: Overwriting milestone attribute " + akey + " old value " + oldVal + " with new value " + avalue + " in milestone: " + key);
							}
							milestone.getAttributes().put(akey, avalue);
						}
					}

					@Override
					protected void handleWordlist(AutoClosingFormatting acf, Reference where) {
					}
				}));
			}
			formatArg = 3;
		} else if ((exportArgs[0].equals("fillwordattr") || exportArgs[0].equals("createwordattr")) && exportArgs[1].equals("--")) {
			final boolean restructure = exportArgs[0].equals("createwordattr");
			for (ParatextBook book : books) {
				book.accept(new UBXFBookVisitor(book.getId(), true, new UBXFGrammarHandlerVisitor() {
					private void restructure(ParatextCharacterContentContainer pccc, boolean inWordlist, boolean inNewWordlist) {
						for (int i = 0; i < pccc.getContent().size(); i++) {
							ParatextCharacterContentPart part = pccc.getContent().get(i);
							if (part instanceof AutoClosingFormatting && ((AutoClosingFormatting) part).getKind() == AutoClosingFormattingKind.WORDLIST) {
								AutoClosingFormatting acf = (AutoClosingFormatting) part;
								if (inWordlist) {
									System.out.println("WARNING: wordlist in wordlist");
									return;
								} else {
									restructure(acf, true, inNewWordlist);
									pccc.getContent().remove(i);
									pccc.getContent().addAll(i, acf.getContent());
									i = i - 1 + acf.getContent().size();
								}
							} else if (part instanceof Milestone) {
								Milestone milestone = (Milestone) part;
								if (milestone.getTag().equals("zaln-e")) {
									System.out.println("WARNING: End milestone without start milestone");
									return;
								}
								if (milestone.getTag().equals("zaln-s")) {
									if (inNewWordlist) {
										System.out.println("WARNING: Start milestone inside new wordlist");
										return;
									}
									if (inWordlist) {
										System.out.println("WARNING: Start milestone inside existing wordlist");
										return;
									}
									int startCounter = 1, contentCounter = 0;
									while (i + startCounter < pccc.getContent().size() && pccc.getContent().get(i + startCounter) instanceof Milestone && ((Milestone) pccc.getContent().get(i + startCounter)).getTag().equals("zaln-s")) {
										startCounter++;
									}
									while (i + startCounter + contentCounter < pccc.getContent().size() && (!((pccc.getContent().get(i + startCounter + contentCounter) instanceof Milestone)) || !((Milestone) pccc.getContent().get(i + startCounter + contentCounter)).getTag().equals("zaln-e"))) {
										contentCounter++;
									}
									for (int j = 0; j < startCounter; j++) {
										if (i + startCounter + contentCounter + j >= pccc.getContent().size() || !(pccc.getContent().get(i + startCounter + contentCounter + j) instanceof Milestone) || !((Milestone) pccc.getContent().get(i + startCounter + contentCounter + j)).getTag().equals("zaln-e")) {
											System.out.println("WARNING: Not properly nested milestone content");
											return;
										}
									}
									AutoClosingFormatting newWordlist = new AutoClosingFormatting(AutoClosingFormattingKind.WORDLIST);
									for (int j = 0; j < contentCounter; j++) {
										newWordlist.getContent().add(pccc.getContent().remove(i + startCounter));
									}
									restructure(newWordlist, false, true);
									pccc.getContent().add(i + startCounter, newWordlist);
									i += startCounter * 2;
								}
							}
						}
					}

					@Override
					protected void handleContent(ParatextCharacterContentContainer pccc, boolean enterFootnotes) {
						if (restructure) {
							restructure(pccc, false, false);
						}
						super.handleContent(pccc, enterFootnotes);
					}

					List<Milestone> openMilestones = new ArrayList<>();
					Map<String, String> activeAttributes = null;

					@Override
					protected void handleAlignMilestone(boolean start, Milestone milestone, Reference where) {
						if (start) {
							openMilestones.add(milestone);
						} else if (!openMilestones.isEmpty()) {
							openMilestones.remove(openMilestones.size() - 1);
						}
						activeAttributes = null;
					}

					@Override
					protected void handleWordlist(AutoClosingFormatting acf, Reference where) {
						if (openMilestones.isEmpty()) {
							return;
						}
						if (activeAttributes == null) {
							activeAttributes = new Hashtable<>();
							for (Milestone ms : openMilestones) {
								for (Map.Entry<String, String> e : ms.getAttributes().entrySet()) {
									String key = e.getKey();
									if (Arrays.asList("x-content", "x-occurrence", "x-occurrences").contains(key))
										continue;
									if (Arrays.asList("x-lemma", "x-strong", "x-srcloc").contains(key))
										key = key.substring(2);
									String existingValue = activeAttributes.get(key);
									activeAttributes.put(key, (existingValue == null ? "" : existingValue + " ") + e.getValue());
								}
							}
						}
						acf.getAttributes().putAll(activeAttributes);
					}
				}));
			}
			formatArg = 2;
		} else if (exportArgs[0].equals("convertgrammar") && exportArgs[1].equals("--")) {
			for (ParatextBook book : books) {
				book.accept(new UBXFBookVisitor(book.getId(), true, new UBXFGrammarHandlerVisitor() {

					private String getHebPrefixStrong(char ch) {
						switch (ch) {
						case 'b':
							return "H9003";
						case 'c':
							return "H9002";
						case 'd':
							return "H9009";
						case 'i':
							return "H9008";
						case 'k':
							return "H9004";
						case 'l':
							return "H9005";
						case 'm':
							return "H9006";
						case 's':
							return "H9007";
						default:
							throw new IllegalArgumentException("Char: " + ch);
						}
					}

					private String replaceChars(String s, char... chars) {
						if (s.length() != 1)
							throw new IllegalArgumentException();
						for (int i = 0; i < chars.length; i += 2) {
							if (s.charAt(0) == chars[i])
								return ("" + chars[i + 1]).replace("#", "");
						}
						return s;
					}

					@Override
					protected void handleWordlist(AutoClosingFormatting acf, Reference where) {
						String strongA = acf.getAttributes().get("strong");
						if (strongA != null) {
							String[] strong = strongA.split(" ");
							for (int i = 0; i < strong.length; i++) {

								if (strong[i].matches("G[0-9]{5}")) {
									String suffix = "";
									if (strong[i].charAt(5) != '0') {
										suffix += (char) (strong[i].charAt(5) - '1' + 'a');
									}
									strong[i] = strong[i].substring(0, 5).replaceFirst("^G0{1,3}", "G") + suffix;
								} else if (strong[i].matches("([bcdiklms]:)*H[0-9]{4}[a-z]?")) {
									String prefixes = "";
									int pos = 0;
									while (strong[i].charAt(pos + 1) == ':') {
										prefixes += getHebPrefixStrong(strong[i].charAt(pos)) + " ";
										pos += 2;
									}
									strong[i] = prefixes + strong[i].substring(pos).replaceFirst("^H0{1,3}", "H");
								} else if (strong[i].matches("([bcdiklms]:)*[bcdiklms]")) {
									String prefixes = "";
									for (int j = 0; j < strong[i].length(); j += 2) {
										prefixes += getHebPrefixStrong(strong[i].charAt(j)) + " ";
									}
									strong[i] = prefixes.trim();
								} else {
									System.out.println("WARNING: Unsupported Strong entry " + strong[i] + " in " + where);
								}
							}
							acf.getAttributes().put("strong", String.join(" ", strong));
						}
						String morphA = acf.getAttributes().get("x-morph");
						if (morphA != null) {
							String[] morph = morphA.split(" ");
							for (int i = 0; i < morph.length; i++) {
								if (morph[i].startsWith("He,") || morph[i].startsWith("Ar,")) {
									String wivu = morph[i].substring(0, 1) + morph[i].substring(3).replace(':', '/');
									if (!wivu.matches(Utils.WIVU_REGEX)) {
										System.out.println("WARNING: Invalid WIVU morphology " + wivu + " in " + where);
									} else {
										morph[i] = "wivu:" + wivu;
									}
								} else if (morph[i].startsWith("Gr,")) {
									String rmac = morph[i];
									if (rmac.matches("Gr,C[CSO],,,,,,,,")) {
										rmac = "CONJ";
									} else if (rmac.matches("Gr,P[,I],,,,[GDA,],,,")) {
										rmac = "PREP";
									} else if (rmac.matches("Gr,D[,O],,,,,,,[,CS]")) {
										rmac = "ADV" + (rmac.charAt(12) != ',' ? "-" + rmac.charAt(12) : "");
									} else if (rmac.matches("Gr,I[,EDR][,MN][,AP][,A][,2],,[,SP][,I]")) {
										rmac = "INJ";
									} else if (rmac.matches("Gr,T[,FE],,,,,,,,")) {
										rmac = "PRT";
									} else if (rmac.matches("Gr,(EN|N[SP]),,,,[NVGDA][MFN][SP]I")) {
										rmac = "A-NUI";
									} else if (rmac.matches("Gr,N,,,,,[NVGDA][MFN][SP][,ID]")) {
										rmac = "N-" + rmac.substring(9, 10) + rmac.substring(11, 12) + rmac.substring(10, 11);
									} else if (rmac.matches("Gr,(A[AR]|E[FQNO]|N[SP]),,,,[NVGDA][MFN][SP][,SC]")) {
										rmac = "A-" + rmac.substring(9, 10) + rmac.substring(11, 12) + rmac.substring(10, 11) + (rmac.charAt(12) != ',' ? "-" + rmac.charAt(12) : "");
									} else if (rmac.matches("Gr,(EA|RD),,,,[NVGDA][MFN][SP],")) {
										rmac = "T-" + rmac.substring(9, 10) + rmac.substring(11, 12) + rmac.substring(10, 11);
									} else if (rmac.matches("Gr,EP,,,[123,][NVGDA][MFN][SP],")) {
										rmac = "S-" + replaceChars(rmac.substring(8, 9), ',', '#') + rmac.substring(9, 10) + rmac.substring(11, 12) + rmac.substring(10, 11);
									} else if (rmac.matches("Gr,(R[DPECIRT]|E[DRT]),,,[123,][NVGDA][MFN,][SP][,SC]")) {
										rmac = replaceChars(rmac.substring(4, 5), 'E', 'F', 'I', 'X', 'T', 'I') + "-" + replaceChars(rmac.substring(8, 9), ',', '#') + rmac.substring(9, 10) + rmac.substring(11, 12) + replaceChars(rmac.substring(10, 11), ',', '#') + (rmac.charAt(12) != ',' ? "-" + rmac.charAt(12) : "");
									} else if (rmac.matches("Gr,V,[ISOMNP][PIFAEL,][AMP],,,,,")) {
										rmac = "V-" + replaceChars(rmac.substring(6, 7), 'E', 'R', ',', 'X') + rmac.substring(7, 8) + rmac.substring(5, 6);
									} else if (rmac.matches("Gr,V,[ISOMNP][PIFAEL,][AMP][123],,[SP],")) {
										rmac = "V-" + replaceChars(rmac.substring(6, 7), 'E', 'R', ',', 'X') + rmac.substring(7, 8) + rmac.substring(5, 6) + "-" + rmac.substring(8, 9) + rmac.substring(11, 12);
									} else if (rmac.matches("Gr,V,[ISOMNP][PIFAEL,][AMP],[NGDAV][MFN][SP],")) {
										rmac = "V-" + replaceChars(rmac.substring(6, 7), 'E', 'R', ',', 'X') + rmac.substring(7, 8) + rmac.substring(5, 6) + "-" + rmac.substring(9, 10) + rmac.substring(11, 12) + rmac.substring(10, 11);
									} else {
										System.out.println("WARNING: Unsupported Greek Morph entry " + rmac + " in " + where);
										rmac = null;
									}
									if (rmac != null) {
										if (!rmac.matches(Utils.RMAC_REGEX)) {
											throw new IllegalStateException("Built invalid RMAC: " + rmac);
										}
										morph[i] = "robinson:"+ rmac;
									}
								} else {
									System.out.println("WARNING: Unsupported Morph entry " + morph[i] + " in " + where);
								}
							}
							acf.getAttributes().put("x-morph", String.join(" ", morph));
						}
					}
				}));
			}
			formatArg = 2;
		} else {
			System.out.println("Supported operations: createsrcloc, analyze, augmentmilestones, fillwordattr, createwordattr, convertgrammar");
			return;
		}
		AbstractParatextFormat exportFormat = (AbstractParatextFormat) Main.exportFormats.get(exportArgs[formatArg]).getImplementationClass().newInstance();
		exportFormat.doExportBooks(books, Arrays.copyOfRange(exportArgs, formatArg + 1, exportArgs.length));
	}

	@Override
	protected void doExportBook(ParatextBook book, File outFile) throws IOException {
		throw new UnsupportedOperationException();
	}

	private static class UBXFBookVisitor implements ParatextBookContentVisitor<RuntimeException> {

		private final ParatextID bookID;
		private final UBXFGrammarHandlerVisitor ghv;
		private final boolean enterFootnotes;
		private int chapterNumber = -1;

		public UBXFBookVisitor(ParatextID bookID, boolean enterFootnotes, UBXFGrammarHandlerVisitor ghv) {
			this.bookID = bookID;
			this.enterFootnotes = enterFootnotes;
			this.ghv = ghv;
		}

		@Override
		public void visitChapterStart(ChapterIdentifier chapter) throws RuntimeException {
			chapterNumber = chapter.chapter;
		}

		@Override
		public void visitChapterEnd(ChapterIdentifier chapter) throws RuntimeException {
		}

		@Override
		public void visitRemark(String content) throws RuntimeException {
		}

		@Override
		public void visitParagraphStart(ParagraphKind kind) throws RuntimeException {
		}

		@Override
		public void visitTableCellStart(String tag) throws RuntimeException {
		}

		@Override
		public void visitSidebarStart(String[] categories) throws RuntimeException {
		}

		@Override
		public void visitSidebarEnd() throws RuntimeException {
		}

		@Override
		public void visitPeripheralStart(String title, String id) throws RuntimeException {
		}

		@Override
		public void visitVerseStart(VerseIdentifier location, String verseNumber) throws RuntimeException {
			ghv.setWhere(Reference.verse(bookID, chapterNumber, verseNumber, ""));
		}

		@Override
		public void visitVerseEnd(VerseIdentifier verseLocation) throws RuntimeException {
		}

		@Override
		public void visitFigure(String caption, Map<String, String> attributes) throws RuntimeException {
		}

		@Override
		public void visitParatextCharacterContent(ParatextCharacterContent content) throws RuntimeException {
			ghv.handleContent(content, enterFootnotes);
		}
	}

	private static abstract class UBXFGrammarHandlerVisitor {

		private Reference where = null;

		public void setWhere(Reference newWhere) {
			where = newWhere;
		}

		protected abstract void handleWordlist(AutoClosingFormatting acf, Reference where);

		protected void handleAlignMilestone(boolean start, Milestone milestone, Reference where) {
		}

		protected void handleContent(ParatextCharacterContentContainer pccc, boolean enterFootnotes) {
			for (ParatextCharacterContentPart part : pccc.getContent()) {
				if (part instanceof AutoClosingFormatting && ((AutoClosingFormatting) part).getKind() == AutoClosingFormattingKind.WORDLIST) {
					handleWordlist((AutoClosingFormatting) part, where);
				} else if (part instanceof Milestone) {
					Milestone milestone = (Milestone) part;
					if (milestone.getTag().equals("zaln-s")) {
						handleAlignMilestone(true, milestone, where);
					} else if (milestone.getTag().equals("zaln-e")) {
						handleAlignMilestone(false, milestone, where);
					}
				}
				if (part instanceof ParatextCharacterContentContainer && (enterFootnotes || !(part instanceof FootnoteXref))) {
					handleContent((ParatextCharacterContentContainer) part, enterFootnotes);
				}
			}
		}
	}
}
