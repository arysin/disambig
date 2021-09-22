#!/bin/env groovy

@Grab(group='org.languagetool', module='language-uk', version='5.5-SNAPSHOT')

import static groovy.io.FileType.FILES

import java.util.regex.Pattern

import org.languagetool.AnalyzedToken
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.language.Ukrainian
import org.languagetool.tagging.uk.UkrainianTagger
import org.languagetool.tokenizers.uk.UkrainianWordTokenizer
import groovy.xml.XmlUtil

import groovy.transform.Field

def freqs = [:].withDefault{ 0 }
def freqs2 = [:].withDefault{ [:].withDefault { 0 } }

// only collect lemmas which are ambiguous
def ambigs = new File("lemma_ambig_freq.txt").readLines().collect{ it.split("\t")[1] }

def xmlFolder = new File("xml")

int count = 0
int wordCount = 0
int multiWordCount = 0
int multiTagCount = 0
int nullTagCount = 0
boolean sentence = false
//def unverified = []
def nullTags = []
@Field
//def titles = [:].withDefault { [] }
def parseFailues = []

//@Field @Lazy
//UkrainianTagger ukTagger = { new UkrainianTagger() }()

//@Field 
//static final Pattern PUNCT_PATTERN = Pattern.compile(/[,.:;!?\/()«»„“"…\u2013\u2014-]+/)
//@Field 
//static final Pattern LATIN_WORD_PATTERN = Pattern.compile(/\p{IsLatin}+/)
@Field 
static final Pattern SYMBOL_PATTERN = Pattern.compile(/[\u00A0-\u00BF\u2070-\u209F\u20A0-\u20CF\u2100-\u214F\u2150-\u218F\u2200-\u22FF]+/)
@Field 
static final Pattern UNKNOWN_PATTERN = Pattern.compile(/(.*-)?[а-яіїєґА-ЯІЇЄҐ]+(-.*)?/)
@Field
List<String> xmlElems = []

List<File> files = new File("txt").listFiles().sort { it.name }

files.each() { File file->
//    if( ! (file.name =~ /^[A-ZА-ЯІЇЄҐ]_.*/) ) {
//        println "Skipping ${file.name}..."
//        return
//    }

    String id = "<id>${file.name[0]}</id>"
    if( ! file.text.startsWith(id) ) {
        System.err.println "File ${file.name} does not start with $id"
        return
    }

    String srcTxtName = file.getName().replaceFirst(/_dis\.txt$/, '.txt')
    if( ! new File("good/$srcTxtName").isFile() ) {
        System.err.println "Source file good/${srcTxtName} does not exist"   
        System.exit(1)
    }

    println "File: ${file.name}"
        
    def paragraphs = getParagraphs(srcTxtName)
    
    def text = file.text
    text = text.replace('<P/>', '').replace('\u2019', "'")
    
    paragraphs.each { List<String> tokens ->
        String regex = tokens.collect{ 
              it = it.replaceAll(/[?.+*()]/, '\\\\$0')
              it.startsWith('<') ? it + '\\h*' : it + '\\[.*' 
            }
            .join("\n+")

        regex = "(?m)^<S>\\h*\n+$regex\$".replace('<S>\\h*\n+<b>', '(<S>\\h*\n+<b>|<b>\\h*\n+<S>)') \
            .replace('<S>\\h*\n+<i>', '(<S>\\h*\n+<i>|<i>\\h*\n+<S>)')
            .replace('<S>\\h*\n+<emphasis>', '(<S>\\h*\n+<emphasis>|<emphasis>\\h*\n+<S>)')
        if( ! (text =~ regex) ) {
            println "No match: $regex"
            return 
        }
        text = text.replaceFirst(regex, '<P/>\n$0')
    }
    text = text.replaceAll(/<P\/>(\n<P\/>)+/, '<P/>')
    
//    text = text.replaceAll(/^\u2014.*\n[А-ЯІЇЄҐ]/, '<br>$0')
    text = text.replaceAll(/<S>\h*\n([•■])/, '<br>\n<S>\n$1')
    text = text.replaceAll(/<S>\h*\n([\u2014])/, '<br>\n<S>\n$1')
    
//    System.exit 1
    
    
    File xmlFile = new File(xmlFolder, file.name.replaceFirst(/_dis.txt$/, '.xml'))
    xmlFile.text = '<?xml version="1.0" encoding="UTF-8"?>\n<text>\n<meta>\n'

    int lineCount = 0
    xmlElems = []
    
    boolean start = false
    String prevLineT = ""
    String prevLine = ""
    boolean inForeign
    
    text.eachLine { String line ->
        lineCount++
        
        prevLine = prevLineT
        prevLineT = line

        if( ! start ) {
            def isBody = line.startsWith('<body>') 
            if( isBody ) {
                xmlFile << "</meta>\n"
                xmlFile << "<body>\n"
                start = true
                return
            }
            else {
                handleHeader(line, xmlFile)
                return
            }
        }

        if( line.startsWith('</body>') ) {
//            handleHeader(line, xmlFile)
            start = false
            return
        }
        
        String trimmed = line.trim().replace("\u00A0", "")
        if( ! trimmed )
            return
        
        if( trimmed == "<S>" ) {
            xmlFile << "<sentence>\n"
            if( xmlElems && xmlElems[-1] == "S" ) {
                System.err.println "last open is already <S> at $lineCount, $xmlElems, file: ${file.name}"
            }
            else {
                xmlElems << "S"
            }
            sentence = true
            return
        }
        if( trimmed.length() < 12 ) {
            def m1 = trimmed =~ /^([,.:;!?\/()«»„“"'…\u2013\u2014\u201D\u201C-]+)\[<\/S>\]\s*$/
            if( m1 ) {
                String value = XmlUtil.escapeXml(m1[0][1])
                xmlFile << "  <token value=\"$value\" lemma=\"$value\" tags=\"punct\" />\n"
                xmlFile << "</sentence>\n"
                sentence = false
                if( ! xmlElems || xmlElems[-1] != "S" ) {
                    System.err.println "last open 2 is not S at $lineCount, $xmlElems, file: ${file.name}"
                }
                else {
                    xmlElems.remove(xmlElems.size()-1)
                }
                return
            }
        }
        if( trimmed == "</S>" ) {
            xmlFile << "</sentence>\n"
            sentence = false
            if( ! xmlElems || xmlElems[-1] != "S" ) {
                System.err.println "last open is not S at $lineCount, $xmlElems, file: ${file.name}"
            }
            else {
                xmlElems.remove(xmlElems.size()-1)
            }
            return
        }
        if( trimmed == "<P/>") {
            xmlFile << "<paragraph/>\n"
            sentence = false
            return
        }

        if( trimmed ==~ /<\/?[a-z]+>/ ) {
            xmlFile << "<format tag=\"${trimmed[1..<-1]}\"/>\n"
//            xmlFile << "$trimmed\n"
//            if( trimmed[1] == "/" && trimmed != "<chart>" ) {
//                if( ! xmlElems || xmlElems[-1] != trimmed[2..<-1] ) {
//                    System.err.println "last open is not $trimmed at $lineCount, $xmlElems, file: ${file.name}"
//                }
//                else {
//                    xmlElems.remove(xmlElems.size()-1)
//                }
//            }
//            else {
//                xmlElems << trimmed[1..<-1]
//            }
            if( trimmed == "<foreign>" )
                inForeign = true
            else if( trimmed == "</foreign>" )
                inForeign = false
            
            return
        }

                
        if( ! sentence ) {
            xmlFile << "<sentence>\n"
            sentence = true
            xmlElems << "S"
        }
        
        if( line =~ /\/<[a-z]+>\]/ ) {
            multiWordCount++
//            xmlFile << "  ---multi---\n"
            return
        }
        
//        if( line =~ /^[•■]/ && prevLine =~ /^[:;]\[/ ) {
//            xmlFile << "<format tag=\"br\"/>\n"
//        }
//        else if( line =~ /^\u2014/ ) {
//            xmlFile << "<format tag=\"br\"/>\n"
//        }
        
        def m = trimmed =~ /(.+?)\[(.+?)\/([a-z].*?)\]/
        
        if( ! m ) {
            parseFailues << "Failed to parse: \"$line\" (${file.name})"
//            System.err.println "${file.name}\nFailed to parse: \"$line\""
            return
        }

        m.each { mt ->
            if( mt.size() < 3 ) {
                println "ERROR: failed to parse2: \"$line\""
                return
            }
            
            String token = mt[1]
            String lemma = mt[2]
            String allTags = mt[3]
            
    //        if( ! (line =~ /(?iu)^[а-яіїєґ]/) )
    //            return
    
            if( allTags.contains(",") ) {
                multiTagCount++
            }
            String[] allTagsList = allTags.split(",")
            tags = allTagsList[0]
            
            if( ! (tags ==~ /[a-z_0-9:&]+/) ) {
                println "Invalid tag: $tags"
                return
            }

            if( lemma =~ /(?iu)^[а-яіїєґ]/ ) {
                count++
            }
            if( lemma =~ /(?iu)^[а-яіїєґa-z0-9]/ ) {
                wordCount++
                if( allTags == "null" ) {
    
                    if( SYMBOL_PATTERN.matcher(token).matches() ) {
                        tags = "symb"
                    }
                    else if( UNKNOWN_PATTERN.matcher(token).matches() ) {
                        tags = "unknown"
                        nullTags << token
                        nullTagCount++
                    }
                    else {
                        tags = "unclass"
                        nullTags << token
                        nullTagCount++
                    }
                }
                else if( ! allTags.startsWith("noninfl") && ! allTags.startsWith("unclass") ) { //! (token =~ /^[А-ЯІЇЄҐA-Z].*/ ) ){
//                    AnalyzedTokenReadings ltTags = ukTagger.tag([token])[0]
//                    def ltTags2 = ltTags.getReadings().collect { AnalyzedToken t -> t.getLemma() + "/" + t.getPOSTag() }
//                    allTagsList.each { tagPair ->
//                        if( tagPair.endsWith("noninfl:foreign") ) {
//                        }
//                        else {
//                            if( ! tagPair.contains("/") ) {
//                                tagPair = lemma + "/" +tagPair
//                            }
//                            if( ! (tagPair in ltTags2) ) {
//                                unverified << "$tagPair (token: $token) (avail: $ltTags2)"
//                                //                            println "Unverified tag: $tagPair (token: $token) (avail: $ltTags2)"
//                                return
//                            }
//                        }
//                    }
                }
            }

            String commentAttr = ""
            
            def mComment = (trimmed =~ /\]\s+([^\h].+)$/)
            if( mComment ) {
                commentAttr = "comment=\"" + XmlUtil.escapeXml(mComment[0][1]) + "\" "
            } 
            
    
            if( token ==~ /([.…?!]{1,3}|[,:;%«»"“”\/()&\u2013\u2014-])/ ) {
                String tokenEnc = XmlUtil.escapeXml(token)
                xmlFile << "  <token value=\"$tokenEnc\" lemma=\"$tokenEnc\" tags=\"punct\" />\n"
            }
            else {
                String tagsEnc = XmlUtil.escapeXml(tags)
                // hopefully we don't need to encode apostrophe
                String tokenEnc = token =~ /["<>]/ ? XmlUtil.escapeXml(token) : token
                String lemmaEnc = lemma =~ /["<>]/ ? XmlUtil.escapeXml(lemma) : lemma
    
                if( allTagsList.size() > 1 ) {
                    xmlFile << "  <token value=\"$tokenEnc\" lemma=\"$lemmaEnc\" tags=\"$tagsEnc\" $commentAttr>\n"
                    xmlFile << "    <alts>\n"
                    allTagsList[1..-1].each { tkn -> 
                        println ":: '$tkn'"
                        (lemma, tags) = tkn.split("/") 
                        tagsEnc = XmlUtil.escapeXml(tags)
                        // hopefully we don't need to encode apostrophe
                        lemmaEnc = lemma =~ /["<>]/ ? XmlUtil.escapeXml(lemma) : lemma
                        xmlFile << "      <token value=\"$tokenEnc\" lemma=\"$lemmaEnc\" tags=\"$tagsEnc\" $commentAttr/>\n"
                    }
                    xmlFile << "    </alts>\n"
                    xmlFile << "  </token>"
                }
                else {
                    xmlFile << "  <token value=\"$tokenEnc\" lemma=\"$lemmaEnc\" tags=\"$tagsEnc\" $commentAttr/>\n"
                }
                
                
                String tagPos = tags.replaceFirst(/:.*/, '')
            
                if( ! (lemma in ambigs) )
                    return
    
                freqs[lemma] += 1
                freqs2[token][lemma+"/"+tagPos] += 1
            }

        } // each match
        
        def endingM = trimmed =~ /\]\s?([.,:;«»"”\/?!)]|[!?.]{3})(\[$1\/null\])?\h*$/
        if( endingM ) {
            def value = endingM[0][1]
            xmlFile << "  <token value=\"$value\" lemma=\"$value\" tags=\"punct\" />\n"
        }
    }

    xmlFile << "</body>\n</text>\n"
}

println "$count Ukrainian tokens"
println "$wordCount word/number tokens"
println "$multiWordCount multiword tags !!"
println "$multiTagCount tokens with multiple tags !!"
println "$nullTagCount tokens with null tags !!"
//println "${unverified.size()} tokens with unverified tags !!"

freqs = freqs.toSorted { - it.value }

def outFile = new File("lemma_freqs.txt")
outFile.text = ""

freqs.each { k,v ->
    outFile << "$v\t$k\n"
}

def outFile2 = new File("lemma_freqs_hom.txt")
outFile2.text = ""

java.text.Collator coll = java.text.Collator.getInstance(new Locale("uk", "UA"));
coll.setStrength(java.text.Collator.IDENTICAL)
coll.setDecomposition(java.text.Collator.NO_DECOMPOSITION)

freqs2
    .findAll{ k,v -> v.size() > 1 }
    .toSorted { a, b -> coll.compare a.key, b.key }
    .each { k,v ->
        v.each { k2,v2 -> 
            outFile2 << k.padRight(30) << " " << k2.padRight(30) << " " << v2 << "\n"
        }
    }

def outFileFreqFull = new File("lemma_freqs_full.txt")
outFileFreqFull.text = ""

freqs2
    .toSorted { a, b -> coll.compare a.key, b.key }
    .each { k,v ->
        v.each { k2,v2 ->
            outFileFreqFull << k.padRight(30) << " " << k2.padRight(30) << " " << v2 << "\n"
        }
    }

//new File("err_unverified.txt").text = unverified.collect{ it.toString() }.toSorted(coll).join("\n")

def nullTagsFile = new File("err_null_tags.txt")
nullTagsFile.text = nullTags.collect{ it.toString() }.toSorted(coll).join("\n")

def parseFailuresFile = new File("err_parse_failures.txt")
parseFailuresFile.text = parseFailues.collect{ it.toString() }.toSorted(coll).join("\n")

//def dupsFile = new File("err_dups.txt")
//dupsFile.text = titles.findAll { k,v -> v.size() > 1 }.collect{ k,v -> "$k\n\t"+ v.join("\n\t") }.join("\n")


void handleHeader(String line, File xmlFile) {
//    if( ! inTags && ! line.startsWith('<') ) {
//        println "Bad line: $line"
//        return
//    }
//    if( line.startsWith('<') ) {
//        inTag << line.trim()
//    }
    
    String trimmed = line.trim()
    if( ! trimmed )
        return

    if( trimmed =~ /^<\/?[^>]+>$/ ) {
        xmlFile << trimmed
        return
    }
    if( ! trimmed.startsWith("<") ) {
        xmlFile << trimmed
        return
    }

    def m = (trimmed =~ /^(<.+?>)(.*?)(<\/.+?>)$/)
    if( ! m || m[0].size() < 3 )
        println "Failed to parse: $line"
        
//    if( m[0][1] == "<title>" ) {
//        String title = m[0][2].trim()
//        titles[title] << xmlFile.name
//    }
        
    String textEnc = XmlUtil.escapeXml(m[0][2])
    xmlFile << "  " << m[0][1] << textEnc << m[0][3] << "\n"
}


@Field
def language = new Ukrainian() {
    @Override
    protected synchronized List<?> getPatternRules() { return [] }
}

@Field
UkrainianWordTokenizer wordTokenizer = new UkrainianWordTokenizer()

List<List<String>> getParagraphs(String filename) {
    
    def file = new File("good", filename)
    
    def text = file.text.replaceFirst(/(?s).*?<body>(.*)<\/body>.*/, '$1')
    def m = text =~ /(?m)(?<![:;]\n)^(?!(?:<b>|\(<i>|[1-9]\)\h*|[•■]\h*)[а-яіїєґ])([^\u2014\u2013\u2212-].{1,100})/
    
//    def inFile = new File("txt", file.name.replaceFirst(/\.txt$/, '_dis.txt'))
//    def outFile = new File("txt3", inFile.name)
    
    //    def m = file.text =~ /(?m)^([А-ЯІЇЄҐа-яіїєґ'\u2019\u02bc]+)/
    if( m.size() < 2 ) {
        println "Single paragraph"
        return []
    }
    else {
        def paras = m[1..-1].collect {
            def sent = it[1].replace("Врешті-таки", "Врешті - таки")
            def words = wordTokenizer.tokenize(sent)
            words = words.findAll { ! (it =~ /\h+/) && it != '\n' }
            words = words.take(6)
            int idx = words.findIndexOf{ it ==~ /[.?…!]|[.?!]{2,3}/ }
            if( idx >=0 && idx < 5 ) words = words.take(idx+1)
    //        println words.join("|") + "\n---"
            words
        }
        
        println "Found ${paras.size()} paragraphs"
        return paras
    }
}
