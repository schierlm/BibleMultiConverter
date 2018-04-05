package biblemulticonverter.versification;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.schema.versification.ccel.ObjectFactory;
import biblemulticonverter.schema.versification.ccel.RefSys;
import biblemulticonverter.schema.versification.ccel.RefSys.Alias;
import biblemulticonverter.schema.versification.ccel.RefSys.OsisIDs.OsisID;
import biblemulticonverter.schema.versification.ccel.RefSys.RefMap;
import biblemulticonverter.tools.ValidateXML;

public class CCEL implements VersificationFormat {

	public static final String[] HELP_TEXT = {
			"Versification format used by http://www.ccel.org/refsys/"
	};

	List<String> KINGDOMS_IDS = Arrays.asList("1Kgdms", "2Kgdms", "3Kgdms", "4Kgdms");
	List<String> KINGS_IDS = Arrays.asList("1Sam", "2Sam", "1Kgs", "2Kgs");

	protected Schema getSchema() throws SAXException {
		return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(ObjectFactory.class.getResource("/ccelVersification.xsd"));
	}

	private Boolean kingdoms;

	@Override
	public void doImport(VersificationSet vset, String... importArgs) throws Exception {
		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class);
		Unmarshaller u = ctx.createUnmarshaller();
		for (String filename : importArgs) {
			File inputFile = new File(filename);
			ValidateXML.validateFileBeforeParsing(getSchema(), inputFile);
			RefSys doc = (RefSys) u.unmarshal(inputFile);
			List<String> aliases = new ArrayList<>();
			for (Alias alias : doc.getAlias()) {
				aliases.add(alias.getCode());
			}
			List<Versification.Reference> refs = new ArrayList<>();
			Set<Versification.Reference> refsSeen = new HashSet<>();
			kingdoms = null;
			for (OsisID osisid : doc.getOsisIDs().getOsisID()) {
				Reference r = parseReference(osisid.getCode());
				if (r != null) {
					if (!refsSeen.add(r))
						System.out.println("WARNING: Same verse referenced twice in versification: " + r);
					else
						refs.add(r);
				}
			}
			if (kingdoms == null)
				kingdoms = false;
			if (kingdoms) {
				aliases.add(doc.getCode() + "__-_KINGDOMS");
			}
			vset.add(Arrays.asList(Versification.fromReferenceList(doc.getCode(), doc.getName(), aliases.isEmpty() ? null : (String[]) aliases.toArray(new String[aliases.size()]), refs)), null);
			for (RefMap refmap : doc.getRefMap()) {
				Versification from = vset.findVersification(refmap.getFrom());
				Versification to = vset.findVersification(refmap.getTo());
				System.out.println("-- " + from.getName() + " -> " + to.getName() + " --");
				Map<BookID, BookID> bookMap = new EnumMap<>(BookID.class);
				Map<Reference, Reference> chapterMap = new HashMap<>();
				Map<Reference, List<Reference>> mapMap = new HashMap<>();
				for (RefMap.Map map : refmap.getMap()) {
					int partsCount = map.getFrom().split("\\.").length;
					if (partsCount == 1) {
						kingdoms = isKingdoms(from);
						BookID ff = parseBook(map.getFrom());
						kingdoms = isKingdoms(to);
						;
						BookID tt = map.getTo().isEmpty() ? BookID.DICTIONARY_ENTRY : parseBook(map.getTo());
						if (ff == null || tt == null)
							continue;
						bookMap.put(ff, tt);
					} else if (partsCount == 2) {
						kingdoms = isKingdoms(from);
						Reference ff = parseReference(map.getFrom() + ".1");
						kingdoms = isKingdoms(to);
						Reference tt = parseReference(map.getTo() + ".1");
						if (ff == null || tt == null)
							continue;
						chapterMap.put(ff, tt);
					} else {
						kingdoms = isKingdoms(from);
						Reference ff = parseReference(map.getFrom());
						if (ff == null)
							continue;
						if (!from.containsReference(ff)) {
							System.out.println("WARNING: Versification " + from.getName() + " does not contain verse " + ff + "; skipping");
							continue;
						}
						if (map.getTo().isEmpty()) {
							mapMap.put(ff, new ArrayList<Reference>());
							continue;
						}
						kingdoms = isKingdoms(to);
						List<Reference> list = new ArrayList<>();
						if (map.getTo().contains(" ")) {
							for (String rrr : map.getTo().split(" ")) {
								Reference r1 = parseReference(rrr);
								if (r1 == null)
									continue;
								if (!to.containsReference(r1)) {
									System.out.println("WARNING: Verse " + r1 + " does not exist in " + to.getName() + "; skipping");
									continue;
								}
								list.add(r1);
							}
						} else {
							String[] parts = map.getTo().split("-", 2);
							Reference r1 = parseReference(parts[0]);
							if (r1 == null)
								continue;
							if (!to.containsReference(r1)) {
								System.out.println("WARNING: Mapping maps to verse " + r1 + " not contained in destination mapping " + to.getName());
								continue;
							}
							if (parts.length == 1) {
								list.add(r1);
							} else {
								Reference r2 = parseReference(parts[1]);
								int i1 = to.getIndexForReference(r1);
								int i2 = to.getIndexForReference(r2);
								for (int i = i1; i <= i2; i++) {
									list.add(to.getReference(i));
								}
							}
						}
						mapMap.put(ff, list);
					}
				}
				for (int i = 0; i < from.getVerseCount(); i++) {
					final Reference ff = from.getReference(i), tt;
					if (mapMap.containsKey(ff))
						continue;
					Reference chapterMapped = chapterMap.get(new Reference(ff.getBook(), ff.getChapter(), "1"));
					if (chapterMapped != null) {
						tt = new Reference(chapterMapped.getBook(), chapterMapped.getChapter(), ff.getVerse());
					} else if (bookMap.containsKey(ff.getBook())) {
						tt = new Reference(bookMap.get(ff.getBook()), ff.getChapter(), ff.getVerse());
					} else {
						tt = ff;
					}
					if (to.containsReference(tt)) {
						List<Reference> list = new ArrayList<>();
						list.add(tt);
						mapMap.put(ff, list);
					}
				}
				for (Iterator<Map.Entry<Reference, List<Reference>>> it = mapMap.entrySet().iterator(); it.hasNext();) {
					Map.Entry<Reference, List<Reference>> reference = it.next();
					if (reference.getValue().isEmpty())
						it.remove();
				}
				vset.add(null, Arrays.asList(VersificationMapping.build(from, to, mapMap)));
			}
		}
	}

	private boolean isKingdoms(Versification v) {
		return v.getAliases() != null && Arrays.asList(v.getAliases()).contains(v.getName() + "__-_KINGDOMS");
	}

	private Reference parseReference(String ref) throws IOException {
		String[] parts = ref.split("\\.");
		if (parts.length != 3)
			throw new IOException("Invalid OSIS ID: " + ref);
		String book = parts[0];
		BookID bk = parseBook(book);
		if (bk == null)
			return null;
		if (bk == BookID.BOOK_Esth && Arrays.asList("A", "B", "C", "D", "E", "F").contains(parts[1])) {
			parts[1] = String.valueOf(101 + parts[1].charAt(0) - 'A');
		}
		int chapter = Integer.parseInt(parts[1]);
		String verse = parts[2];
		if (verse.equals("SKIP")) {
			System.out.println("WARNING: Invalid verse number: SKIP");
			return null;
		}
		if (verse.endsWith("I") || verse.endsWith("L")) {
			System.out.println("WARNING: Invalid verse number: " + verse + ", replaced by " + verse.toLowerCase());
			verse = verse.toLowerCase();
		}
		return new Reference(bk, chapter, verse);
	}

	private BookID parseBook(String book) throws IOException {
		if (book.equals("2Kdgms")) {
			System.out.println("WARNING: invalid OSIS ID 2Kdgms; replaced by 2Kgdms");
			book = "2Kgdms";
		}
		if (KINGS_IDS.contains(book)) {
			if (kingdoms != null && kingdoms) {
				System.out.println("WARNING: Both Kingdoms and Kings: " + book);
			} else {
				kingdoms = false;
			}
		} else if (KINGDOMS_IDS.contains(book)) {
			if (kingdoms != null && !kingdoms) {
				throw new IOException("WARNING: Both Kingdoms and Kings: " + book);
			} else {
				kingdoms = true;
			}
			book = KINGS_IDS.get(KINGDOMS_IDS.indexOf(book));
		} else if (book.equals("Ps151")) {
			System.out.println("WARNING: invalid OSIS ID Ps151; replaced by AddPs");
			book = "AddPs";
		} else if (book.equals("GrEsth")) {
			System.out.println("WARNING: invalid OSIS ID GrEsth; replaced by EsthGr");
			book = "EsthGr";
		} else if (book.equals("Sng")) {
			System.out.println("WARNING: invalid OSIS ID Sng; replaced by Song");
			book = "Song";
		}
		if (Arrays.asList("JoshA", "JudgB", "TobS", "DanTheo", "SusTheo", "BelTheo", "OdesSol").contains(book)) {
			System.out.println("WARNING: Reference to unsupported book: " + book);
			return null;
		}
		BookID bk = BookID.fromOsisId(book);
		return bk;
	}

	@Override
	public boolean isExportSupported() {
		return true;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
		if (versifications.size() != 1)
			throw new IllegalArgumentException("CCEL files have to contain exactly one versification");
		Versification versification = versifications.get(0);

		ObjectFactory of = new ObjectFactory();
		RefSys refsys = of.createRefSys();
		kingdoms = false;
		if (versification.getAliases() != null) {
			for (String a : versification.getAliases()) {
				if (a.equals(versification.getName() + "__-_KINGDOMS")) {
					kingdoms = true;
				} else {
					Alias alias = of.createRefSysAlias();
					alias.setCode(a);
					refsys.getAlias().add(alias);
				}
			}
		}
		refsys.setCode(versification.getName());
		if (versification.getDescription() != null)
			refsys.setName(versification.getDescription());
		refsys.setOsisIDs(of.createRefSysOsisIDs());
		for (int i = 0; i < versification.getVerseCount(); i++) {
			Reference r = versification.getReference(i);
			OsisID oi = of.createRefSysOsisIDsOsisID();
			oi.setCode(formatReference(r, kingdoms));
			refsys.getOsisIDs().getOsisID().add(oi);
		}
		for (VersificationMapping vm : mappings) {
			Map<BookID, List<List<Reference>>> destReferencesByBook = new EnumMap<>(BookID.class);
			Map<Reference, List<List<Reference>>> destReferencesByChapter = new HashMap<>();
			for (int i = 0; i < vm.getFrom().getVerseCount(); i++) {
				Reference from = vm.getFrom().getReference(i);
				List<Reference> to = vm.getMapping(from);
				if (to.isEmpty())
					continue;
				if (!destReferencesByBook.containsKey(from.getBook()))
					destReferencesByBook.put(from.getBook(), new ArrayList<List<Reference>>());
				destReferencesByBook.get(from.getBook()).add(to);
				Reference fromChapter = new Reference(from.getBook(), from.getChapter(), "1");
				if (!destReferencesByChapter.containsKey(fromChapter))
					destReferencesByChapter.put(fromChapter, new ArrayList<List<Reference>>());
				destReferencesByChapter.get(fromChapter).add(to);
			}
			Map<BookID, BookID> bookMappings = new EnumMap<>(BookID.class);
			for (Entry<BookID, List<List<Reference>>> byBook : destReferencesByBook.entrySet()) {
				if (byBook.getValue().size() < 2)
					continue;
				BookID sameBook = byBook.getValue().get(0).get(0).getBook();
				for (List<Reference> rr : byBook.getValue()) {
					for (Reference r : rr) {
						if (sameBook != r.getBook()) {
							sameBook = null;
							break;
						}
					}
					if (sameBook == null)
						break;
				}
				if (sameBook != null && sameBook != byBook.getKey())
					bookMappings.put(byBook.getKey(), sameBook);
			}
			Map<Reference, Reference> chapterMappings = new HashMap<>();
			for (Entry<Reference, List<List<Reference>>> byChapter : destReferencesByChapter.entrySet()) {
				if (byChapter.getValue().size() < 2)
					continue;
				Reference sameChapter = byChapter.getValue().get(0).get(0);
				sameChapter = new Reference(sameChapter.getBook(), sameChapter.getChapter(), "1");
				for (List<Reference> rr : byChapter.getValue()) {
					for (Reference r : rr) {
						Reference thisChapter = new Reference(r.getBook(), r.getChapter(), "1");
						if (sameChapter != null && !sameChapter.equals(thisChapter)) {
							sameChapter = null;
							break;
						}
					}
					if (sameChapter == null)
						break;
				}
				if (sameChapter == null)
					continue;
				BookID keyBook = byChapter.getKey().getBook();
				if (bookMappings.containsKey(keyBook)) {
					keyBook = bookMappings.get(keyBook);
					;
				}
				if (sameChapter.getBook() != keyBook || sameChapter.getChapter() != byChapter.getKey().getChapter()) {
					chapterMappings.put(new Reference(byChapter.getKey().getBook(), byChapter.getKey().getChapter(), "1"), sameChapter);
				}
			}

			RefMap refmap = of.createRefSysRefMap();
			refmap.setFrom(vm.getFrom().getName());
			refmap.setTo(vm.getTo().getName());

			boolean kingdomsFrom = isKingdoms(vm.getFrom());
			boolean kingdomsTo = isKingdoms(vm.getTo());

			Set<BookID> bookMappingsDumped = EnumSet.noneOf(BookID.class);
			Set<Reference> chapterMappingsDumped = new HashSet<>();

			for (int i = 0; i < vm.getFrom().getVerseCount(); i++) {
				Reference from = vm.getFrom().getReference(i);
				Reference mappedFrom = from;
				List<Reference> to = vm.getMapping(from);

				if (bookMappings.containsKey(from.getBook())) {
					if (!bookMappingsDumped.contains(from.getBook())) {
						addMapping(refmap, formatBook(from.getBook(), kingdomsFrom), formatBook(bookMappings.get(from.getBook()), kingdomsTo));
						bookMappingsDumped.add(from.getBook());
					}
					mappedFrom = new Reference(bookMappings.get(from.getBook()), from.getChapter(), from.getVerse());
				} else if (kingdomsFrom != kingdomsTo && KINGS_IDS.contains(from.getBook().getOsisID())) {
					if (!bookMappingsDumped.contains(from.getBook())) {
						addMapping(refmap, formatBook(from.getBook(), kingdomsFrom), formatBook(from.getBook(), kingdomsTo));
						bookMappingsDumped.add(from.getBook());
					}
				}
				Reference chapRef = new Reference(from.getBook(), from.getChapter(), "1");
				Reference mapped = chapterMappings.get(chapRef);
				if (mapped != null) {
					if (!chapterMappingsDumped.contains(chapRef)) {
						addMapping(refmap, formatBook(chapRef.getBook(), kingdomsFrom) + "." + chapRef.getChapter(), formatBook(mapped.getBook(), kingdomsTo) + "." + mapped.getChapter());
						chapterMappingsDumped.add(chapRef);
					}
					mappedFrom = new Reference(mapped.getBook(), mapped.getChapter(), from.getVerse());
				}

				String formattedFrom = formatReference(from, kingdomsFrom);
				if (to.size() == 0) {
					addMapping(refmap, formattedFrom, "");
				} else if (to.size() == 1) {
					if (!to.get(0).equals(mappedFrom)) {
						addMapping(refmap, formattedFrom, formatReference(to.get(0), kingdomsTo));
					}
				} else {
					boolean consecutive = true;
					int base = vm.getTo().getIndexForReference(to.get(0));
					for (int j = 1; j < to.size(); j++) {
						if (vm.getTo().getIndexForReference(to.get(j)) != base + j) {
							consecutive = false;
							break;
						}
					}
					if (consecutive) {
						addMapping(refmap, formattedFrom, formatReference(to.get(0), kingdomsTo) + "-" + formatReference(to.get(to.size() - 1), kingdomsTo));
					} else {
						StringBuilder formattedTo = new StringBuilder();
						for (Reference r : to) {
							if (formattedTo.length() > 0)
								formattedTo.append(" ");
							formattedTo.append(formatReference(r, kingdomsTo));
						}
						addMapping(refmap, formattedFrom, formattedTo.toString());
					}
				}
			}
			refsys.getRefMap().add(refmap);
		}

		JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class);
		Marshaller m = ctx.createMarshaller();
		m.setSchema(getSchema());
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		m.marshal(refsys, doc);
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		transformer.transform(new DOMSource(doc), new StreamResult(outputFile));
	}

	private void addMapping(RefMap refmap, String osisFrom, String osisTo) {
		RefMap.Map map = new ObjectFactory().createRefSysRefMapMap();
		map.setFrom(osisFrom);
		map.setTo(osisTo);
		refmap.getMap().add(map);
	}

	private String formatReference(Reference ref, boolean kingdoms) throws IOException {
		String chap = "" + ref.getChapter();
		if (ref.getBook() == BookID.BOOK_Esth && ref.getChapter() > 100 && ref.getChapter() < 107) {
			chap = "" + (ref.getChapter() - 101 + 'A');
		}
		return formatBook(ref.getBook(), kingdoms) + "." + chap + "." + ref.getVerse();
	}

	private String formatBook(BookID book, boolean kingdoms) throws IOException {
		String osisID = book.getOsisID();
		if (kingdoms && KINGS_IDS.contains(osisID)) {
			osisID = KINGDOMS_IDS.get(KINGS_IDS.indexOf(osisID));
		}
		return osisID;
	}
}
