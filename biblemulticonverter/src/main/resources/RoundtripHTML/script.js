var currentBook = "";
var strongPrefix = "";
var pathPrefix = "../";

function selectBook() {
	window.location.href = document.getElementById("booksel").value + "_1.html";
}

function selectChapter() {
	window.location.href = currentBook + "_" + document.getElementById("chapsel").value + ".html";
}

function quickkey(e) {
	e = e || window.event;
	if (e.keyCode == 13)
		quicknav();
}

function quicknav() {
	var text = document.getElementById("quickbox").value.trim().toLowerCase();
	var match = null, matchlen = 0;
	for (var i = 0; i < metadata.length; i++) {
		var md = metadata[i];
		var labels = [ md.abbr, md.short, md.long, md.osis ];
		for (var j = 0; j < labels.length; j++) {
			var label = labels[j].toLowerCase();
			if (text.substring(0, label.length) == label && label.length > matchlen) {
				match = md;
				matchlen = label.length;
			}
		}
	}
	if (matchlen > 0) {
		var rest = text.substring(matchlen).trim();
		if (rest == "") {
			window.location.href = pathPrefix + match.type + "/" + match.abbr + "_1.html";
		} else if (rest.search("^[0-9]+$") != -1) {
			var num = rest - 0;
			if (num >= 1 && num <= match.chapters) {
				window.location.href = pathPrefix + match.type + "/" + match.abbr + "_" + num + ".html";
			}
		}
	}
}

function cssRule(selector) {
	if (!document.styleSheets)
		return;
	var rules = document.styleSheets[0].rules || document.styleSheets[0].cssRules;
	for (var i = 0; i < rules.length; i++) {
		if (rules[i].selectorText == selector) {
			return rules[i];
		}
	}
	if (document.styleSheets[0].addRule) {
		document.styleSheets[0].addRule(selector, null, 0);
	} else {
		document.styleSheets[0].insertRule(selector + ' { }', 0);
	}
	return rules[0];
}

function toggleInline() {
	cssRule("div.v").style.display = document.getElementById("inline").checked ? "" : "inline";
}

function renderRMAC(rmac) {
	var suffixes = {
		S : "Superlative",
		C : "Comparative",
		ABB : "Abbreviated form",
		I : "Interrogative",
		N : "Negative",
		K : "Kai",
		ARAM : "Aramaic",
		HEB : "Hebrew",
		ATT : "Attic Greek form"
	};
	if (rmac.substring(0, 2) == "V-") {
		var parts = rmac.match("V-([PIFARLX]|2[PFARL])([AMPEDONQX][ISOMNP])(-([123][SP]|[NGDAV][SPD][MFN]))?(-ATT|-ARAM|-HEB)?");
		if (parts != null && parts.length == 6) {
			var extra = "";
			if (parts[4]) {
				if (parts[4].length == 2) {
					extra += ", Person=" + parts[4].substring(0, 1) + ", Number=" + ({
						S : "Singular",
						P : "Plural"
					})[parts[4].substring(1, 2)];
				} else {
					extra += ", Case=" + ({
						N : "Nominative",
						G : "Genitive",
						D : "Dative",
						A : "Accusative",
						V : "Vocative"
					})[parts[4].substring(0, 1)] + ", Number=" + ({
						S : "Singular",
						D : "Dual",
						P : "Plural"
					})[parts[4].substring(1, 2)] + ", Gender=" + ({
						M : "Masculine",
						F : "Feminine",
						N : "Neuter"
					})[parts[4].substring(2, 3)];
				}
			}
			if (parts[5]) {
				extra += ", " + suffixes[parts[5].substring(1)];
			}
			return "Verb (Tense=" + ({
				P : "Present",
				I : "Imperfect",
				F : "Future",
				A : "Aorist",
				R : "Perfect",
				L : "Pluperfect",
				X : "No tense stated",
				"2P" : "Second Present",
				"2F" : "Second Future",
				"2A" : "Second Aorist",
				"2R" : "Second peRfect",
				"2L" : "Second pLuperfect"
			})[parts[1]] + ", Voice=" + ({
				A : "Active",
				M : "Middle",
				P : "Passive",
				E : "Either middle or passive",
				D : "Middle deponent",
				O : "Passive deponent",
				N : "Middle or passive deponent",
				Q : "Impersonal active",
				X : "No voice stated",
			})[parts[2].substring(0, 1)] + ", Mood=" + ({
				I : "Indicative",
				S : "Subjunctive",
				O : "Optative",
				M : "Imperative",
				N : "Infinitive",
				P : "Participle"
			})[parts[2].substring(1, 2)] + extra + ")";
		}
	} else if (rmac.search("^[NARCDTKIXQFSP](-[123]?[SP]?[NVGDA][SP][MFN]?)?(-([PLT]|[PL]G|LI|NUI))?(-(S|C|ABB|I|N|K|ATT|ARAM|HEB))?$") != -1) {
		var parts = rmac.match("^([NARCDTKIXQFSP])(-([123]?)([SP]?)([NVGDA][SP][MFN]?))?(-([PLT]|[PL]G|LI|NUI))?(-(S|C|ABB|I|N|K|ATT|ARAM|HEB))?$");
		if (parts.length == 10) {
			var extra = "";
			if (parts[5] || parts[7] || parts[9]) {
				extra += " (";
				if (parts[3]) {
					extra += "Person=" + parts[3] + ", ";
				}
				if (parts[4]) {
					extra += "Number=" + ({
						S : "Singular",
						D : "Dual",
						P : "Plural"
					})[parts[4]] + ", ";
				}
				if (parts[5]) {
					extra += "Case=" + ({
						N : "Nominative",
						G : "Genitive",
						D : "Dative",
						A : "Accusative",
						V : "Vocative"
					})[parts[5].substring(0, 1)] + ", Number=" + ({
						S : "Singular",
						D : "Dual",
						P : "Plural"
					})[parts[5].substring(1, 2)];
					if (parts[5].length == 3) {
						extra += ", Gender=" + ({
							M : "Masculine",
							F : "Feminine",
							N : "Neuter"
						})[parts[5].substring(2, 3)];
					}
				}
				if (parts[7]) {
					extra += (parts[5] ? ", " : "") + ({
							P : "Person",
							L : "Location",
							T : "Title",
							PG : "Person Gentilic",
							LG : "Location Gentilic",
							LI : "Letter Indeclinable",
							NUI : "Numerical Indiclinable",
						})[parts[7]];
				}
				if (parts[9]) {
					extra += (parts[5] || parts[7] ? ", " : "") + suffixes[parts[9]];
				}
				extra += ")";
			}
			return ({
				N : "Noun",
				A : "Adjective",
				R : "Relative pronoun",
				C : "Reciprocal pronoun",
				D : "Demonstrative pronoun",
				T : "Definite article",
				K : "Correlative pronoun",
				I : "Interrogative pronoun",
				X : "Indefinite pronoun",
				Q : "correlative or interrogative pronoun",
				F : "Reflexive pronoun",
				S : "Possessive pronoun",
				P : "Personal pronoun"
			})[parts[1]] + extra;
		}
	} else if (rmac.search("^(ADV|CONJ|COND|PRT|PREP|INJ|ARAM|HEB|N-PRI|A-NUI|N-LI|N-OI)(-(S|C|ABB|I|N|K|ATT|ARAM|HEB))?$") != -1) {
		var parts = rmac.match("^(ADV|CONJ|COND|PRT|PREP|INJ|ARAM|HEB|N-PRI|A-NUI|N-LI|N-OI)(-(S|C|ABB|I|N|K|ATT|ARAM|HEB))?$");
		if (parts.length == 4) {
			return ({
				ADV : "Adverb or adverb and particle combined",
				CONJ : "Conjunction or conjunctive particle",
				COND : "Conditional particle or conjunction",
				PRT : "Particle, disjunctive particle",
				PREP : "Preposition",
				INJ : "Interjection",
				ARAM : "Aramaic transliterated word (indeclinable)",
				HEB : "Hebrew transliterated word (indeclinable)",
				"N-PRI" : "Indeclinable Proper Noun",
				"A-NUI" : "Indeclinable Numeral (Adjective)",
				"N-LI" : "Indeclinable Letter (Noun)",
				"N-OI" : "Indeclinable Noun of Other type"
			})[parts[1]] + (!parts[3] ? "" : " (" + suffixes[parts[3]] + ")");
		}
	}
	return rmac;
}

function renderWIVUPart(wivu, language) {
	if (wivu.startsWith("Np")) {
		var extra = "";
		if (wivu.length == 3) {
			extra = ", Gender=" + ({
				m: "masculine",
				f: "feminine",
				l: "location",
				t: "title"
			})[wivu.charAt(2)];
		}
		return "Noun (Type=proper name" + extra + ")";
	}
	var type = ({
		A: ["Adjective", "TGNS", "^[acgo](|[bcfmx][dpsx][acd])$"],
		C: ["Conjunction", "", ""],
		D: ["Adverb", "", ""],
		N: ["Noun", "TGNS", "^[cgtx](|[bcfmx][dpsx][acd])$"],
		P: ["Pronoun", "TPGN", "^[dfipr](|[123x][bcfm][dps])$"],
		R: ["Preposition", "T", "d?"],
		S: ["Suffix", "TPGN", "^[dhnp](|[123x][bcfm][dps])$"],
		T: ["Particle", "T", "^[acdeijmnor]?$"],
		V: ["Verb", "ETPGNS", "^[DGHKLMNOPQabcefhijklmopqrstuvwyz][pqiwhjvrsauc]([123x][bcfmx][dpsx][acdx]?)?$"]
	})[wivu.charAt(0)];
	var rest = wivu.substring(1);
	if (wivu.startsWith("V") && rest.search("^[DGHKLMNOPQabcefhijklmopqrstuvwyz][pqiwhjvrsauc][bfm]$") != -1) {
		rest = rest.substring(0, 2) + "x" + rest.substring(2) + "xx";
	} else if (wivu.startsWith("V") && rest.search("^[DGHKLMNOPQabcefhijklmopqrstuvwyz][pqiwhjvrsauc][bcfm][dps][acd]$") != -1) {
		rest = rest.substring(0, 2) + "x" + rest.substring(2);
	} else if (wivu.startsWith("V") && rest.search("^[DGHKLMNOPQabcefhijklmopqrstuvwyz][pqiwhjvrsauc][ac]$") != -1) {
		rest = rest.substring(0, 2) + "xxx" + rest.substring(2);
	}
	var subtypes = {
		Aa: "adjective",
		Ac: "cardinal number",
		Ag: "gentilic",
		Ao: "ordinal number",
		Nc: "common",
		Ng: "gentilic",
		Np: "proper name",
		Nt: "title",
		Pd: "demonstrative",
		Pf: "indefinite",
		Pi: "interrogative",
		Pp: "personal",
		Pr: "relative",
		Rd: "definite article",
		Sd: "directional he",
		Sh: "paragogic he",
		Sn: "paragogic nun",
		Sp: "pronominal",
		Ta: "affirmation",
		Tc: "conditional+logical",
		Td: "definite article",
		Te: "exhortation",
		Ti: "interrogative",
		Tj: "interjection",
		Tm: "demonstrative",
		Tn: "negative",
		To: "direct object marker",
		Tr: "relative",
		Vp: "perfect (qatal)",
		Vq: "sequential perfect (weqatal)",
		Vi: "imperfect (yiqtol)",
		Vw: "sequential imperfect (wayyiqtol)",
		Vh: "cohortative",
		Vj: "jussive",
		Vv: "imperative",
		Vr: "participle active",
		Vs: "participle passive",
		Va: "infinitive absolute",
		Vu: "conjunctive weyyiqtol",
		Vc: "infinitive construct"
	};
	if (type && rest.search(type[2]) != -1) {
		var extras = " (";
		for(var i=0; i < rest.length; i++) {
			if (rest.charAt(i) != 'x') {
				switch(type[1].charAt(i)) {
					case 'T':
						extras += "Type=" + subtypes[wivu.charAt(0)+rest.charAt(i)] + ", ";
						break;
					case 'P':
						extras += "Person=" + rest.charAt(i) + ", ";
						break;
					case 'G':
						extras += "Gender=" + ({
							b: "both (noun)",
							c: "common (verb)",
							f: "feminine",
							m: "masculine"
						})[rest.charAt(i)] + ", ";
						break;
					case 'N':
						extras += "Number=" + ({
							d: "dual",
							p: "plural",
							s: "singular"
						})[rest.charAt(i)] + ", ";
						break;
					case 'S':
						extras += "State=" + ({
							a: "absolute",
							c: "construct",
							d: "determined"
						})[rest.charAt(i)] + ", ";
						break;
					case 'E':
						extras += "Stem=" + ({
							Hq: "qal",
							HN: "niphal",
							Hp: "piel",
							HP: "pual",
							Hh: "hiphil",
							HH: "hophal",
							Ht: "hithpael",
							Ho: "polel",
							HO: "polal",
							Hr: "hithpolel",
							Hm: "poel",
							HM: "poal",
							Hk: "palel",
							HK: "pulal",
							HQ: "qal passive",
							Hl: "pilpel",
							HL: "polpal",
							Hf: "hithpalpel",
							HD: "nithpael",
							Hj: "pealal",
							Hi: "pilel",
							Hu: "hothpaal",
							Hc: "tiphil",
							Hv: "hishtaphel",
							Hw: "nithpalel",
							Hy: "nithpoel",
							Hz: "hithpoel",
							Aq: "peal",
							AQ: "peil",
							Au: "hithpeel",
							Ap: "pael",
							AP: "ithpaal",
							AM: "hithpaal",
							Aa: "aphel",
							Ah: "haphel",
							As: "saphel",
							Ae: "shaphel",
							AH: "hophal",
							Ai: "ithpeel",
							At: "hishtaphel",
							Av: "ishtaphel",
							Aw: "hithaphel",
							Ao: "polel",
							Az: "ithpoel",
							Ar: "hithpolel",
							Af: "hithpalpel",
							Ab: "hephal",
							Ac: "tiphel",
							Am: "poel",
							Al: "palpel",
							AL: "ithpalpel",
							AO: "ithpolel",
							AG: "ittaphal"
						})[language+rest.charAt(i)]+", ";
						break;
					default: return wivu;
				}
			}
		}
		extras = extras.substring(0, extras.length - 2) + ")";
		if (extras == ")")
			extras = "";
		wivu = type[0] + extras;
	}
	return wivu;
}

function renderWIVU(wivu) {
	var languages = {
		H: "Hebrew",
		A: "Aramaic"
	};
	let result = languages[wivu.substring(0, 1)]+": ";
	for(var part of wivu.substring(1).split("/")) {
		result += renderWIVUPart(part, wivu.substring(0, 1)) + "; ";
	}
	return result.substring(0, result.length - 2);
}

function hoverGrammar(selector, val) {
	cssRule(selector).style.backgroundColor = val;
}

function toggleGrammar() {
	var enabled = document.getElementById("grammar").checked;
	var gs = document.getElementsByClassName("g");
	for (var i = 0; i < gs.length; i++) {
		var g = gs[i];
		if (g.lastChild != null && g.lastChild.className == "gram") {
			g.removeChild(g.lastChild);
		}
		if (enabled) {
			var html = "";
			var cn = g.className.split(" ");
			for (var j = 0; j < cn.length; j++) {
				if (j > 0)
					html += " ";
				if (cn[j].substring(0, 2) == "gs") {
					var num = cn[j].substring(2);
					var strong = /^[A-Z]/.test(num) ? num : strongPrefix + num;
					html += "<a class=\"gs\" onmouseover=\"hoverGrammar('." + cn[j] + "', '#80FF80');\" "
							+ "onmouseout=\"hoverGrammar('." + cn[j] + "', '');\" href=\"../../strong/dict/" + strong
							+ "_1.html\">" + strong + "</a>";
				} else if (cn[j].substring(0, 3) == "gr-") {
					var rmac = cn[j].substring(3).toUpperCase();
					html += "<abbr onmouseover=\"hoverGrammar('." + cn[j] + "', '#FF80FF');\" "
							+ "onmouseout=\"hoverGrammar('." + cn[j] + "', '');\" title=\"" + renderRMAC(rmac)
							+ "\" class=\"gr\">" + rmac + "</abbr>";
				} else if (cn[j].substring(0, 4) == 'gw-!') {
					var wivu = cn[j].substring(4);
					var selector = cn[j].replace("!", "\\\\!").replace("/", "\\\\/");
					html += "<abbr onmouseover=\"hoverGrammar('." + selector + "', '#FF80FF');\" "
							+ "onmouseout=\"hoverGrammar('." + selector + "', '');\" title=\"" + renderWIVU(wivu)
							+ "\" class=\"gw\">" + wivu + "</abbr>";
				}
			}
			var sup = document.createElement("sup");
			sup.className = "gram";
			sup.innerHTML = html;
			g.appendChild(sup);
		}
	}
}

function showNavbar(book, chapter) {
	currentBook = book;
	var ccount = 0;
	var html = "<br><select id=\"booksel\" name=\"booksel\" onchange=\"selectBook();\"></select>"
			+ "<select id=\"chapsel\" name=\"chapsel\" onchange=\"selectChapter();\"></select>"
			+ " &ndash; <input type=\"text\" id=\"quickbox\" name=\"quickbox\" onkeypress=\"quickkey(event);\">";
	if (book != "" && document.getElementById("verses")) {
		html += " &ndash; <label><input type=\"checkbox\" id=\"inline\" onchange=\"toggleInline()\"> Verses on separate lines</label>";
		if (document.getElementsByClassName && document.getElementsByClassName("g").length > 0) {
			html += " <label><input type=\"checkbox\" id=\"grammar\" onchange=\"toggleGrammar()\"> Show grammar information</label>";
		}
	}
	document.getElementById("navbar").innerHTML += html;
	var booksel = document.getElementById("booksel");
	if (book == "") {
		var option = document.createElement("option");
		option.selected = "selected";
		booksel.appendChild(option);
		pathPrefix = "";
	}
	for (var i = 0; i < metadata.length; i++) {
		var md = metadata[i];
		var option = document.createElement("option");
		option.value = pathPrefix + md.type + "/" + md.abbr;
		option.appendChild(document.createTextNode(md.short));
		if (md.abbr == book) {
			ccount = md.chapters;
			strongPrefix = md.nt ? "G" : "H";
			option.selected = "selected";
		}
		booksel.appendChild(option);
	}
	var chapsel = document.getElementById("chapsel");
	if (ccount > 1) {
		for (var i = 1; i <= ccount; i++) {
			var option = document.createElement("option");
			option.appendChild(document.createTextNode("" + i));
			option.selected = (i == chapter);
			chapsel.appendChild(option);
		}
	} else {
		chapsel.parentNode.removeChild(chapsel);
	}
}