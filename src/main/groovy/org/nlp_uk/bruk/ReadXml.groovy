#!/bin/env groovy

package org.nlp_uk.bruk

@Grab(group='org.languagetool', module='language-uk', version='5.7-SNAPSHOT')
@Grab(group='org.apache.commons', module='commons-csv', version='1.9.0')

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.Node
import groovy.xml.slurpersupport.Attribute

import java.util.regex.Pattern

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.languagetool.AnalyzedToken
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.tagging.uk.UkrainianTagger



@CompileStatic
class Stats {
    int count = 0
    int wordCount = 0
    int multiTagCount = 0
    boolean sentence = false
    def unverified = []
    Map<String, Integer> freqs = [:].withDefault{ 0 }
    Map<String, Map<WordReading, Map<WordContext, Integer>>> disambigStats = [:].withDefault { [:].withDefault { [:].withDefault { 0 } }}
    Map<String, Map<WordReading, Integer>> disambigStatsF = [:].withDefault { [:].withDefault { 0 } }
    Map words = [:].withDefault{ 0 }
    Map wordsNorm = [:].withDefault{ 0 }
    Map lemmas = [:].withDefault{ 0 }
    Map pos1Freq = [:].withDefault{ 0 }
}

@Field
Stats stats = new Stats()
@Field 
@Lazy
UkrainianTagger ukTagger = { new UkrainianTagger() }()
@Field
boolean toTagged = false
@Field
Node prevChild = null
@Field
int sentIdx
@Field
Set<String> homonymTokens = new HashSet<>()
@Field
Set<String> allTags
@Field
Set<String> ignoreForStats
@Field
Set<String> ignored = new HashSet<>()
@Field
boolean produceTxt = true


void main2() {
    allTags = getClass().getResource('/ukrainian_tags.txt').readLines() as Set
    allTags += [ 'punct', 'number', 'number:latin', 'time', 'date', 'unclass', 'unknown', 'symb', 'hashtag' ] 

    ignoreForStats = getClass().getResource('/ignore_for_stats.txt').readLines().findAll { it && ! it.startsWith('#') } as Set
    println "Ignoring for stats: ${ignoreForStats.size()} files"
    
    File txt2Folder = new File("good_gen")
    txt2Folder.mkdirs()
    
    def files = new File("xml").listFiles().sort{ it.name }
    files.each { File file->
        if( ! file.name.endsWith('.xml') ) {
            System.err.println "Unknown file: ${file.name}"
            return
        }
            
        println "File: ${file.name}"
        
        File txtFile = new File(txt2Folder, file.name.replaceFirst(/.xml/, '.txt'))
        txtFile.text = ''
    
        String inText = file.text
    
        String origName = txtFile.getName() //.replaceFirst(/_dis\.txt/, '.txt')
        assert new File("good/$origName").isFile()
    
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(inText)
        
    //    println "header: " + xml.meta.'*'.size()
    
//        xml.meta.'*'.eachWithIndex { GPathResult it, int idx ->
//            printNode(txtFile, it, idx)
//        }
                
        Iterator<Node> childNodes = xml.childNodes()
//        println ":: " + childNodes.next().class
//        prevChild = childNodes[0]
        sentIdx = 0
        childNodes.eachWithIndex { Node it, int idx ->
            if( idx == 0 ) prevChild = it
            processItem(txtFile, it, idx)
        }
        txtFile << "\n"
    }
    
    writeStats()
    
} 

new ReadXml().main2()

// end



@CompileStatic
static boolean needsSpace(Node prevChild) {
    return prevChild != null && \
        (prevChild.name() != "format" || ((String)prevChild.attributes()['tag']).startsWith("/")) \
            && ! (((String)prevChild.attributes()['value']) =~ /[°\/]/)
}

@CompileStatic
private void generateStats(List<Node> tokenXmls) {
    
    tokenXmls.eachWithIndex { it, idx ->
        String token = it.attributes()['value'].toString() 
        String lemma = it.attributes()['lemma'].toString() 
        String postag = it.attributes()['tags'].toString()
    
        
        assert token, "Empty token at $idx, in $tokenXmls"

        WordReading wordReading = new WordReading(lemma, postag)
        stats.disambigStatsF[token][wordReading] += 1
        
//        println "tk: $token"
//        println "pt: $postag"
            
//        if( postag == null || postag =~ /^(punct|symbol|unknown|unclass)/)
//            return 
        
//        String tagPos = postag.replaceFirst(/:.*/, '')
        
        if( postag != null && MAIN_POS.matcher(postag).find() ) {
            stats.freqs[lemma] += 1
            
            def currCtxToken = ContextToken.normalized(token, lemma, postag)
            if( idx > 0 ) { 
                def ctxXml = tokenXmls[idx-1]
//                assert ctxXml.@token.text()
                Map<String, String> ctxAttrs = ctxXml.attributes()
                ContextToken ctxToken = ContextToken.normalized(ctxAttrs['value'], ctxAttrs['lemma'], ctxAttrs['tags'])
                def context = new WordContext(ctxToken, -1)
                stats.disambigStats[token][wordReading][context] += 1
            }
            else {
                def ctxToken = new ContextToken('^', '^', "BEG")
                def context = new WordContext(ctxToken, -1)
                stats.disambigStats[token][wordReading][context] += 1
            }

            if( idx < tokenXmls.size()-1 ) { 
                def ctxXml = tokenXmls[idx+1]
                Map<String, String> ctxAttrs = ctxXml.attributes()
                ContextToken ctxToken = ContextToken.normalized(ctxAttrs['value'], ctxAttrs['lemma'], ctxAttrs['tags'])
                def context = new WordContext(ctxToken, +1)
                stats.disambigStats[token][wordReading][context] += 1
            }
            else {
                def ctxToken = new ContextToken('^', '^', "END")
                def context = new WordContext(ctxToken, +1)
                stats.disambigStats[token][wordReading][context] += 1
            }
        }
    }
//    println "generateStats stats: ${stats.freqs2.size()}"
}

@CompileStatic
private void processItem(File txtFile, Node xml, int childIdx) {
    Iterator<Node> childNodes = xml.childNodes()
    
    if( xml.name() == "sentence" ) {
        if( sentIdx > 0 && needsSpace(prevChild) ) {
            txtFile << " "
        }
        childIdx = 0
        sentIdx++

        List<Node> tokenXmls = new ArrayList<>()
        
        childNodes.eachWithIndex { Node it, int idx2 ->
            processItem(txtFile, it, idx2)
            if( it.name() == "token" ) {
                tokenXmls << it
            }
        }
        
        if( ! (txtFile.name in ignoreForStats) ) {
            generateStats(tokenXmls)
        }
        else {
            ignored << txtFile.name
        }
    }
    else if( childNodes
            && ! ((Node)childNodes[0]).name() == "alts" ) {
        String xmlName = xml.name()
        
        txtFile << "<$xmlName>"

        childNodes.eachWithIndex { Node it, int idx2 -> 
            processItem(txtFile, it, idx2)
        }

        txtFile << "</$xmlName>"
    }
    else {
        String xmlName = xml.name()

        if( xmlName.contains("format") ) {
            xmlName = xml.attributes()['tag']
            if( ((Node)xml).attributes()['tag'] == 'br' ) {
                txtFile << "\n"
                prevChild = null
                childIdx = 0
                return
            }

            if( childIdx > 0 
                    && prevChild != null 
                    && prevChild.name() != 'format'
                    && ! (prevChild.attributes()['value'] ==~ /[«\u201C(\[]/) 
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

@Field
static final Pattern PUNCT_PATTERN = Pattern.compile(/[.!?,»\u201D)\]:;…]|[.!?]{2,3}/) // (\.,»…\)\]|[.!?]{3})/)

@CompileStatic
private void printNode(File txtFile, Node node, int childIdx) {
    if( node.text() || node.parent().name()=="meta" ) {
        txtFile << "<${node.name()}>${node.text()}</${node.name()}>\n"
    }
    else if( node.name() == "token" ) {
        if( produceTxt ) {
            if( childIdx > 0 
                    && ! PUNCT_PATTERN.matcher(node.attributes()['value'].toString()).matches()
                    && prevChild != null 
                    && ! (prevChild.attributes()['value'] ==~ /[«\u201C\/(\[]/) 
                    && needsSpace(prevChild) ) {
                if( childIdx == 1 && prevChild.attributes()['value'] ==~ /…|\.{3}/ ) {
                    
                }
                else {
                    txtFile << " "
                }
            } 
            txtFile << node.attributes()['value']
        }
        
        validateToken(node)
    }
    else {
        String attrs = ((Node)node).attributes().collect { k,v -> "$k=\"$v\"" }.join(" ")
        if( attrs ) attrs = " " + attrs

        if( node.name() == "paragraph" ) {
           txtFile << "\n\n" 
           prevChild = null
           childIdx = 0
           sentIdx = 0
        }
        else {
            txtFile << "<${node.name()}$attrs/>"
        }
    }
    prevChild = node
}

@CompileStatic
static String normalize(String token, String lemma) {
    if( ! lemma 
        || (lemma.length() == 1 && lemma ==~ /[А-ЯІЇЄҐ]/ )
        || lemma =~ /^[А-ЯІЇЄҐ]['-]?[а-яіїєґ]/ )
        return token
    return token.toLowerCase()
}

@CompileStatic
static String keyPos(String tags) {
     tags.replaceFirst(/:.*/, '')
}


@Field
static final Pattern VALID_TAG = Pattern.compile(/[a-z]+[a-z_0-9:&]*/)
@Field
static final Pattern UKR_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґ].*/)
@Field
static final Pattern WORD_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґa-z0-9].*/)
@Field
static final Pattern MAIN_POS = Pattern.compile(/^(noun|adj|verb|advp?|prep|conj|numr|part|onomat|intj|noninfl)/)


//@CompileStatic
void validateToken(Node xml) {
    def attributes = xml.attributes()
    String tags = attributes['tags']
    String lemma = attributes['lemma']
    String token = attributes['value']

    if( tags in ['xmltag'] )
        return
    
    if( ! (tags in allTags) && ! (tags.replaceAll(/:(alt|bad|short)/, '') in allTags) ) {
        if( ! ( tags =~ /noninfl(:foreign)?:prop|noun:anim:p:v_zna:var|noun:anim:[mf]:v_...:nv(:abbr:prop:[fp]name|:prop:[fp]name:abbr)/ ) ) {
            System.err.println "Invalid tag: $tags"
            return
        }
    }

//    if( ! VALID_TAG.matcher(tags).matches() ) {
//        System.err.println "Invalid tag: $tags"
//        return
//    }

    stats.pos1Freq[keyPos(tags)]++
    
    
    if( UKR_LEMMA.matcher(lemma).matches() ) {
        stats.count++
        stats.words[token]++
        stats.wordsNorm[normalize(token, lemma)]++
        stats.lemmas[lemma]++
    }

    if( WORD_LEMMA.matcher(lemma).matches() ) {
        stats.wordCount++
        if( tags == "null" ) {
            if( LATIN_WORD_PATTERN.matcher(token).matches() ) {
                tags = "unclass"
            }
            else {
                System.err.println "null tag for $token"
            }
        }
        else if( ! tags.startsWith("noninfl") && ! tags.startsWith("unclass") ) { //! (token =~ /^[А-ЯІЇЄҐA-Z].*/ ) ){
            AnalyzedTokenReadings ltTags = ukTagger.tag([token])[0]
            
            if( ltTags.getReadings().size() > 1 ) {
                homonymTokens << token
            }
            
            def ltTags2 = ltTags.getReadings().collect { AnalyzedToken t -> t.getLemma() + "/" + t.getPOSTag() }
            
            String tagPair = "$lemma/$tags"
            if( tags.startsWith("noninfl:foreign") || tags.startsWith("unclass") ) {
            }
            if( ltTags2[0].startsWith("null") && token.matches("[А-ЯІЇЄҐ][а-яіїєґА-ЯІЇЄҐ'-]+") && tags.matches(/noun:anim:.:v_...(:nv)?:prop:lname/) ) {
            } 
            else {
                boolean initials = token ==~ /[А-ЯІЇЄҐ][а-яіїєґ]?\./ && tagPair ==~ /[А-ЯІЇЄҐ][а-яіїєґ]?\.\/noun:anim:[mf]:v_...:nv(:abbr:prop:[fp]name|:prop:[fp]name:abbr)/ 
                if( ! (tagPair in ltTags2) && ! initials ) {
                    if( token != "їх" || ! (tags ==~ /adj:[mfnp]:v_...(:r(in)?anim)?:nv:&pron:pos:bad/ ) ) {
                        
                        stats.unverified << "$tagPair (token: $token) (avail: $ltTags2)"
                //                            println "Unverified tag: $tagPair (token: $token) (avail: $ltTags2)"
                    }
                    return
                }
            }
        }
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
        println "${unverified.size()} tokens with unverified tags !!"

        
        java.text.Collator coll = java.text.Collator.getInstance(new Locale("uk", "UA"));
        coll.setStrength(java.text.Collator.IDENTICAL)
        coll.setDecomposition(java.text.Collator.NO_DECOMPOSITION)

        // write warnings
        
        new File("err_unverified.txt").text = unverified.collect{ def s = it.toString() 
            s =~ /^[^а-яіїєґ]/ ? "* $s".toString() : s.toString()
        }
            .toSorted(coll)
            .collect{ it.replaceFirst(/^\* /, '') }
            .join("\n")
        
        // write stats
            
        freqs = freqs.toSorted { - it.value }
        
        def outFile = new File("lemma_freqs.txt")
        outFile.text = ""
        
        freqs.each { k,v ->
            outFile << "$v\t$k\n"
        }
        
        writeDisambigStats(coll)
    }
}
 
@CompileStatic
void writeDisambigStats(java.text.Collator coll) {
//    def outFileFreqFull = new File("lemma_freqs_full.txt")
//    outFileFreqFull.text = ""
    def outFileFreqHom = new File("lemma_freqs_hom.txt")
    outFileFreqHom.text = ""

    println "Ignored for stats: $ignored"
    
    stats.with {
        println "Writing ${disambigStats.size()} disambig stats..."
        stats.disambigStats
                .toSorted { a, b -> coll.compare a.getKey(), b.getKey() }
                .each { String token, Map<WordReading, Map<WordContext, Integer>> map1 ->
                    map1
                    .toSorted{ a, b -> b.getKey().getPostag().compareTo(a.getKey().getPostag()) }
                    .each { WordReading wordReading, Map<WordContext, Integer> map2 ->
//                        outFileFreqFull << token.padRight(20) << "," << wordReading << ", " << stats.disambigStatsF[token][wordReading] << "\n"
                        if( token in homonymTokens ) {
                            outFileFreqHom << token << "\t\t" << wordReading << "\t" << stats.disambigStatsF[token][wordReading] << "\n"
                        }
                        map2
//                        .toSorted{ a, b -> b.key.contextToken.word.compareTo(a.key.contextToken.word) }
                        .toSorted{ a, b -> b.getValue().compareTo(a.getValue()) }
                        .each { WordContext wordContext, int value ->
//                            outFileFreqFull << "  , " << wordContext.toString().padRight(30) << ", " << value << "\n"
                            if( wordContext.getOffset() == -1 && token in homonymTokens ) {
                                outFileFreqHom << "\t" << wordContext.toString() << "\t\t" << value << "\n"
                            }
                        }
                    }
                }

    //    println ":: " + freqs2WithHoms.take(1)
                
//        int uniqForms = disambigStats.size()
    //    int uniqFormsWithHom = freqs2WithHoms.size()
    //    println "Total uniq forms: ${uniqForms}, with homonyms: ${uniqFormsWithHom}, ${(double)uniqFormsWithHom/uniqForms}"
    
    //    int uniqFormsSum = freqs2.collect{ k,v -> v.values().sum(0) }.sum(0)
    //    int uniqFormsWithHomSum = freqs2WithHoms.collect{ k,v -> v.values().sum(0) }.sum(0)
    //    println "Total uniq forms sum: ${uniqFormsSum}, with homonyms: ${uniqFormsWithHomSum}, ${(double)uniqFormsWithHomSum/uniqFormsSum}"
//    
//        Map freqs2Main = freqs2.findAll { k,v -> v.keySet().find{ ks -> (ks =~ /\/(noun|adj|adv|verb)/)} != null }
//        Map freqs2WithHomsMain = freqs2Main.findAll{ k,v -> v.size() > 1 } 
//        int uniqFormsSumMain = freqs2Main.collect{ k,v -> v.values().sum(0) }.sum(0)
//        int uniqFormsWithHomSumMain = freqs2WithHomsMain.collect{ k,v -> v.values().sum(0) }.sum(0)
//        println "Total uniq forms main: ${uniqFormsSumMain}, with homonyms: ${uniqFormsWithHomSumMain}, ${(double)uniqFormsWithHomSumMain/uniqFormsSumMain}"
    }
}
