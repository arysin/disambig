#!/bin/env groovy

@Grab(group='org.languagetool', module='language-uk', version='5.5-SNAPSHOT')

import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.NodeChild

import java.util.regex.Pattern

import org.languagetool.AnalyzedToken
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.tagging.uk.UkrainianTagger


class Stats {
    int count = 0
    int wordCount = 0
//    int multiWordCount = 0
    int multiTagCount = 0
    int nullTagCount = 0
    boolean sentence = false
    def unverified = []
    def nullTags = []
    def titles = [:].withDefault { [] }
    def parseFailues = []
    def freqs = [:].withDefault{ 0 }
    def freqs2 = [:].withDefault{ [:].withDefault { 0 } }
    def words = [:].withDefault{ 0 }
    def wordsNorm = [:].withDefault{ 0 }
    def lemmas = [:].withDefault{ 0 }
    def pos1Freq = [:].withDefault{ 0 }
}
@Field
static final Pattern PUNCT_PATTERN = Pattern.compile(/[\p{Punct}«»„“…—–]+/)
@Field
static final Pattern LATIN_WORD_PATTERN = Pattern.compile(/\p{IsLatin}+/)

@Field
Stats stats = new Stats()
@Field @Lazy
UkrainianTagger ukTagger = { new UkrainianTagger() }()
@Field
boolean toTagged = false
@Field
GPathResult prevChild = null
@Field
int sentIdx

File txt2Folder = new File("txt2")
txt2Folder.mkdirs()

def files = new File("xml").listFiles().sort{ it.name }
files.each { File file->
//    if( ! file.name.startsWith("A_") )
//        return

    println "File: ${file.name}"
    
    File txtFile = new File(txt2Folder, file.name.replaceFirst(/.xml/, '.txt'))
    txtFile.text = ''

    String inText = file.text

    String origName = txtFile.getName().replaceFirst(/_dis\.txt/, '.txt')
    assert new File("good/$origName").isFile()
        //    if( ! toTagged ) {
//        String name = txtFile.getName().replaceFirst(/_dis\.txt/, '.txt')
//        def orig = new File("good/$name").text
//        if( orig.contains("\u2019") ) {
////            println "apo: 2019"
//            String apo = "\u2019"
//            inText = inText.replace("'", apo)
//        } 
//        else if (orig.contains("\u02bc") ) {
////            println "apo: 02bc"
//            String apo = "\u02bc"
//            inText = inText.replace("'", apo)
//        }
//    }

    GPathResult xml = new groovy.xml.XmlSlurper().parseText(inText)
    
//    println "header: " + xml.meta.'*'.size()


    xml.meta.'*'.eachWithIndex { GPathResult it, int idx ->
        printNode(txtFile, it, idx)
    }
    txtFile << "<body>\n"
    prevChild = xml['body'][0]
    sentIdx = 0
    xml['body'].'*'.eachWithIndex { it, idx -> 
        processItem(txtFile, it, idx)
    } 
    txtFile << "\n</body>\n"
}

writeStats()

// end


@CompileStatic
static boolean needsSpace(NodeChild prevChild) {
    return prevChild != null && \
        (prevChild.name() != "format" || ((String)prevChild.attributes()['tag']).startsWith("/"))
}

private void processItem(File txtFile, GPathResult xml, int childIdx) {
    if( xml.name() == "sentence" ) {
        if( sentIdx > 0 && needsSpace(prevChild) ) {
            txtFile << " "
        }
        childIdx = 0
        sentIdx++

        xml.'*'.eachWithIndex { it, idx2 ->
            processItem(txtFile, it, idx2)
        }
    }
    else if( xml.childNodes() && ! xml.childNodes()[0].name() == "alts" ) {
        String xmlName = xml.name()
        
        txtFile << "<$xmlName>"

        xml.'*'.eachWithIndex { it, idx2 -> 
            processItem(txtFile, it, idx2)
        }

        txtFile << "</$xmlName>"
    }
    else {
        String xmlName = xml.name()

        if( xmlName.contains("format") ) {
            xmlName = xml.@tag
            if( ((NodeChild)xml).attributes()['tag'] == 'br' ) {
                txtFile << "\n"
                prevChild = null
                childIdx = 0
                return
            }

            if( childIdx > 0 
                    && prevChild != null 
                    && prevChild.name() != 'format'
                    && ! (prevChild.@value ==~ /[«\u201C(\[]/) 
                    && ! ((String)xml.attributes()['tag']).startsWith("/") ) {
                txtFile << " "
            }
            
            txtFile << "<$xmlName>"
            prevChild = xml
            return
        }
        printNode(txtFile, xml, childIdx)
    } 
}

//@CompileStatic
private void printNode(File txtFile, GPathResult xml, int childIdx) {
    if( xml.text() || xml.parent().name()=="meta" ) {
        txtFile << "<${xml.name()}>${xml.text()}</${xml.name()}>\n"
    }
    else if( xml.name() == "token" ) {
        if( childIdx > 0 
                && ! (xml.@value ==~ /[.!?,»\u201D)\]:;…°]|[.!?]{2,3}/)  // (\.,»…\)\]|[.!?]{3})/) 
                && prevChild != null 
                && ! (prevChild.@value ==~ /[«\u201C(\[]/) 
                && needsSpace(prevChild) ) {
                if( childIdx == 1 && prevChild.@value ==~ '…|[.]{3}' ) {
                    
                }
                else {
                    txtFile << " "
                }
        } 
        txtFile << xml.@value
        
        validateToken(xml)
    }
    else {
        String attrs = ((NodeChild)xml).attributes().collect { k,v -> "$k=\"$v\"" }.join(" ")
        if( attrs ) attrs = " " + attrs

        if( xml.name() == "paragraph" ) {
           txtFile << "\n" 
           prevChild = null
           childIdx = 0
           sentIdx = 0
        }
        else {
            txtFile << "<${xml.name()}$attrs/>"
        }
    }
    prevChild = xml
}

@CompileStatic
static String normalize(String token, String lemma) {
    if( ! lemma || lemma =~ /^[А-ЯІЇЄҐ]([а-яіїєґ'-]|$)/ )
        return token
    return token.toLowerCase()
}

@CompileStatic
static String keyPos(String tags) {
     tags.replaceFirst(/:.*/, '')
}

void validateToken(xml) {
    String tags = xml.@tags
    String lemma = xml.@lemma
    String token = xml.@value
    
    if( ! (tags ==~ /[a-z]+[a-z_0-9:&]*/) ) {
        System.err.println "Invalid tag: $tags"
        return
    }

    stats.pos1Freq[keyPos(tags)]++
    
    
    if( lemma =~ /(?iu)^[а-яіїєґ]/ ) {
        stats.count++
        stats.words[token]++
        stats.wordsNorm[normalize(token, lemma)]++
        stats.lemmas[lemma]++
    }
    if( lemma =~ /(?iu)^[а-яіїєґa-z0-9]/ ) {
        stats.wordCount++
        if( tags == "null" ) {

            if( LATIN_WORD_PATTERN.matcher(token).matches() ) {
                tags = "unclass"
            }
            else {
                stats.nullTags << token
                stats.nullTagCount++
            }
        }
        else if( ! tags.startsWith("noninfl") && ! tags.startsWith("unclass") ) { //! (token =~ /^[А-ЯІЇЄҐA-Z].*/ ) ){
            AnalyzedTokenReadings ltTags = ukTagger.tag([token])[0]
            def ltTags2 = ltTags.getReadings().collect { AnalyzedToken t -> t.getLemma() + "/" + t.getPOSTag() }
            
            String tagPair = "$lemma/$tags"
            if( tags.startsWith("noninfl:foreign") || tags.startsWith("unclass") ) {
            }
            else {
                boolean initials = token ==~ /[А-ЯІЇЄҐ]\./ && tagPair ==~ /[А-ЯІЇЄҐ]\.\/noun:anim:[mf]:v_...:nv:prop:.name:abbr/ 
                if( ! (tagPair in ltTags2) && ! initials ) {
                    stats.unverified << "$tagPair (token: $token) (avail: $ltTags2)"
                    //                            println "Unverified tag: $tagPair (token: $token) (avail: $ltTags2)"
                    return
                }
            }
        }
    }

    
    // stats
    
    String tagPos = tags.replaceFirst(/:.*/, '')
    
    if( tagPos ==~ /noun|adj|verb|advp?|prep|conj|numr|part|onomat|intj|noninfl/ ) {
        stats.freqs[lemma] += 1
        stats.freqs2[token][lemma+"/"+tagPos] += 1
    }
}


void writeStats() {
    stats.with {
    
    println "$count Ukrainian tokens"
    println "$wordCount word/number tokens"
    println "${words.size()} unique Ukrainian words"
    println "${wordsNorm.size()} unique Ukrainian words (case-insensitive)"
    println "${lemmas.size()} unique lemmas"
//    println "Tag freqs:\n" + pos1Freq.sort{k,v -> -v}.collect{k,v -> k.padRight(25) + " $v"}.join("\n")
    
//    println "$multiWordCount multiword tags !!"
    println "$multiTagCount tokens with multiple tags !!"
    println "$nullTagCount tokens with null tags !!"
    println "${unverified.size()} tokens with unverified tags !!"
    
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
    
    new File("err_unverified.txt").text = unverified.collect{ it.toString() }.toSorted(coll).join("\n")
    
    def nullTagsFile = new File("err_null_tags.txt")
    nullTagsFile.text = nullTags.collect{ it.toString() }.toSorted(coll).join("\n")
    
    def parseFailuresFile = new File("err_parse_failures.txt")
    parseFailuresFile.text = parseFailues.collect{ it.toString() }.toSorted(coll).join("\n")
    
    def dupsFile = new File("err_dups.txt")
    dupsFile.text = titles.findAll { k,v -> v.size() > 1 }.collect{ k,v -> "$k\n\t"+ v.join("\n\t") }.join("\n")
    }   
}