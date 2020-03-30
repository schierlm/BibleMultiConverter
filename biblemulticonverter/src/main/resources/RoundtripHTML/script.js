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
		ATT : "Attic Greek form"
	};
	if (rmac.substring(0, 2) == "V-") {
		var parts = rmac.match("V-([PIFARLX]|2[FARL])([AMPEDON][ISOMNP])(-([123][SP]|[NGDAV][SPD][MFN]))?(-ATT)?");
		if (parts.length == 6) {
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
				extra += ", " + suffixes[parts[5]];
			}
			return "Verb (Tense=" + ({
				P : "Present",
				I : "Imperfect",
				F : "Future",
				A : "Aorist",
				R : "Perfect",
				L : "Pluperfect",
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
				N : "Middle or passive deponent"
			})[parts[2].substring(0, 1)] + ", Mood=" + ({
				I : "Indicative",
				S : "Subjunctive",
				O : "Optative",
				M : "Imperative",
				N : "Infinitive",
				P : "Participle"
			})[parts[2].substring(1, 2)] + extra + ")";
		}
	} else if (rmac.search("^[NARCDTKIXQFSP](-[123]?[NVGDA][SP][MFN]?)?(-(S|C|ABB|I|N|K|ATT))?$") != -1) {
		var parts = rmac.match("^([NARCDTKIXQFSP])(-([123]?)([NVGDA][SP][MFN]?))?(-(S|C|ABB|I|N|K|ATT))?$");
		if (parts.length == 7) {
			var extra = "";
			if (parts[4] || parts[6]) {
				extra += " (";
				if (parts[3]) {
					extra += "Person=" + parts[3] + ", ";
				}
				if (parts[4]) {
					extra += "Case=" + ({
						N : "Nominative",
						G : "Genitive",
						D : "Dative",
						A : "Accusative",
						V : "Vocative"
					})[parts[4].substring(0, 1)] + ", Number=" + ({
						S : "Singular",
						D : "Dual",
						P : "Plural"
					})[parts[4].substring(1, 2)];
					if (parts[4].length == 3) {
						extra += ", Gender=" + ({
							M : "Masculine",
							F : "Feminine",
							N : "Neuter"
						})[parts[4].substring(2, 3)];
					}
				}
				if (parts[6]) {
					extra += (parts[4] ? ", " : "") + suffixes[parts[6]];
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
	} else if (rmac.search("^(ADV|CONJ|COND|PRT|PREP|INJ|ARAM|HEB|N-PRI|A-NUI|N-LI|N-OI)(-(S|C|ABB|I|N|K|ATT))?$") != -1) {
		var parts = rmac.match("^(ADV|CONJ|COND|PRT|PREP|INJ|ARAM|HEB|N-PRI|A-NUI|N-LI|N-OI)(-(S|C|ABB|I|N|K|ATT))?$");
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

function hoverGrammar(selector, val) {
	cssRule(selector).style.backgroundColor = val;
}

function toggleGrammar() {
	var enabled = document.getElementById("grammar").checked;
	var gs = document.getElementsByClassName("g");
	for (var i = 0; i < gs.length; i++) {
		var g = gs[i];
		if (g.lastChild.className == "gram") {
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