var fs = require('fs'),
path = require('path'),
bibleData = require('../data/bible_data.js'),
bibleFormatter = require('../bible_formatter.js'),
readline = require('readline');
stream = require('stream'),
verseIndexer = require('../verse_indexer.js');

function generate(inputPath, info, createIndex, startProgress, updateProgress) {
	var sourceFilePath = path.join(inputPath, 'verses.txt');
	var breakChar = '\r';
	var aboutPath = path.join(inputPath, 'about.html');
	var data = {
		chapterData: [],
		indexData: {},
		lemmaindexData: {},
		aboutHtml: fs.existsSync(aboutPath) ? fs.readFileSync(aboutPath, 'utf8') : ''
	};
	if (!fs.existsSync(sourceFilePath)) {
		console.log('MISSING', sourceFilePath);
		return;
	}
	var validBooks = [],
		validBookNames = [],
		validChapters = [],
		currentChapter = null,
		paraOpen = false,
		chapNumPending = false,
		rawText = fs.readFileSync( sourceFilePath , 'utf8'),
		lines = rawText.split('\n');
	startProgress(lines.length, 'Lines');
	for (var i=0, il=lines.length; i<il; i++) {
		updateProgress();
		if (lines[i].trim() == '') {
			continue;
		}
		var parts = lines[i].split('\t');
		var dbsCode = parts[0].trim(),
			bookInfo = bibleData.getBookInfoByDbsCode(dbsCode),
			bookName = parts[1].trim(),
			chapter = parts[2].trim(),
			verse = parts[3].trim(),
			hideVerse = false,
			headlines = parts[4].trim(),
			text = parts[5].trim(),
			notes = parts[6].trim();
		if (bookInfo == null) {
			console.log("Can't find: " + dbsCode);
			continue;
		}
		if (verse.substring(0, 1) == "!") {
			verse = verse.substring(1);
			hideVerse = true;
		}
		chapterCode = dbsCode + '' + chapter;
		if (validBooks.indexOf(dbsCode) == -1) {
			validBooks.push(dbsCode);
		}
		if (validBookNames.indexOf(bookName) == -1) {
			validBookNames.push(bookName);
		}
		if (validChapters.indexOf(chapterCode) == -1) {
			validChapters.push(chapterCode);
		}
		if (currentChapter == null || currentChapter.id != chapterCode) {
			if (currentChapter != null && paraOpen) {
				currentChapter["html"] += "</div>" + breakChar;
				paraOpen = false;
			}
			currentChapter = {
					id: chapterCode,
					nextid: null,
					lastid: null,
					html: '',
					notes: '',
					title: bookName + ' ' + chapter,
			};
			chapNumPending = true;
			data.chapterData.push(currentChapter)
		}
		if (headlines != '') {
			if (paraOpen) {
				currentChapter["html"] += "</div>" + breakChar;
				paraOpen = false;
			}
			currentChapter['html'] += headlines;
		}
		currentChapter['notes'] += notes;
		if (verse == '') {
			// prolog; paragraph is never open here!
			currentChapter['html'] += '<div class="p">' + text + '</div>';
			continue;
		}
		if (chapNumPending) {
			currentChapter['html'] += '<div class="c">' + chapter + "</div>" + breakChar;
			chapNumPending = false;
		}
		if (!paraOpen) {
			currentChapter['html'] += '<div class="p">' + breakChar;
			paraOpen = true;
		}
		verseCode = chapterCode + '_' + verse;
		currentChapter['html'] +=
			bibleFormatter.openVerse(verseCode, hideVerse ? null : verse) +
			text +
			bibleFormatter.closeVerse();
		if (createIndex) {
			verseIndexer.indexVerse(verseCode, text, data.indexData, info.lang);
		}
	}
	if (currentChapter != null && paraOpen) {
		currentChapter["html"] += "</div>" + breakChar;
		paraOpen = false;
	}
	for (var i=0, il=data.chapterData.length; i<il; i++) {
		var thisChapter = data.chapterData[i];
		thisChapter.previd = (i > 0) ? data.chapterData[i-1]['id'] : null;
		thisChapter.nextid = (i < il-1) ? data.chapterData[i+1]['id'] : null;
		thisChapter.html =
			bibleFormatter.openChapter(info, thisChapter) +
			thisChapter.html +
			bibleFormatter.closeChapter();
	}
	info.type = 'bible';
	info.divisionNames = validBookNames;
	info.divisions = validBooks;
	info.sections = validChapters;
	data.infoHtml =	'<h1>' + info['name'] + '</h1>';
	return data;
}

module.exports = {
		generate: generate
}
