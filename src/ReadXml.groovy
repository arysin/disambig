#!/bin/env groovy

@Grab(group='org.languagetool', module='language-uk', version='5.6-SNAPSHOT')

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.NodeChild

import java.util.regex.Pattern

import org.languagetool.AnalyzedToken
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.tagging.uk.UkrainianTagger


class ReadXml {

static final Pattern POSTAG_KEY_PATTERN = Pattern.compile("^(noun:(anim|[iu]nanim)|verb(:rev)?:(perf|imperf)|adj|adv(p:(imperf:perf))?|part|prep|numr|conj:(coord|subord)|intj|onomat|punct|symb|noninfl|unclass|number|unknown|time|date|hashtag)")

static String getPostagKey(String postag) {
    def match = POSTAG_KEY_PATTERN.matcher(postag)
    assert match, "postag: $postag"
    match[0][0]
}
static String getPostagCore(String postag) {
    // short/long
    postag.replaceAll(/:rare|:arch|:coll|:slang|:bad|:subst/, '')
}

@CompileStatic
@Canonical
static class ContextToken {
    String word
    String lemma
    String postagKey
    String postag
    
    ContextToken(String word, String lemma, String postag) {
        this.word = word
        this.lemma = lemma
        this.postagKey = getPostagKey(postag)
        this.postag = getPostagCore(postag)
    }
    
    String toString() {
        "$word    $lemma    $postagKey    $postag"
    }
}

@CompileStatic
@Canonical
class WordContext {
    ContextToken contextToken
    int offset
    String postag // core
    String lemma
    
    String toString() {
        def offs = offset > 0 ? "+$offset" : "$offset"
        "$offs $contextToken".padRight(50) + " $postag / $lemma"
    }

}


@CompileStatic
class Stats {
    int count = 0
    int wordCount = 0
    int multiTagCount = 0
    int nullTagCount = 0
    boolean sentence = false
    def unverified = []
    def nullTags = []
    Map titles = [:].withDefault { [] }
    def parseFailues = []
    Map freqs = [:].withDefault{ 0 }
    Map<String, Map<WordContext, Integer>> freqs2 = [:].withDefault { [:].withDefault { 0 } }
    Map words = [:].withDefault{ 0 }
    Map wordsNorm = [:].withDefault{ 0 }
    Map lemmas = [:].withDefault{ 0 }
    Map pos1Freq = [:].withDefault{ 0 }
}

//@Field
Stats stats = new Stats()
//@Field 
@Lazy
UkrainianTagger ukTagger = { new UkrainianTagger() }()
//@Field
boolean toTagged = false
//@Field
GPathResult prevChild = null
//@Field
int sentIdx
//@Field
Set<String> homonymTokens = new HashSet<>()

void main2(String[] args) {
    File txt2Folder = new File("txt2")
    txt2Folder.mkdirs()
    
    def files = new File("xml").listFiles().sort{ it.name }
    files.each { File file->
    
        println "File: ${file.name}"
        
        File txtFile = new File(txt2Folder, file.name.replaceFirst(/.xml/, '.txt'))
        txtFile.text = ''
    
        String inText = file.text
    
        String origName = txtFile.getName().replaceFirst(/_dis\.txt/, '.txt')
        assert new File("good/$origName").isFile()
    
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
    
} 

static void main(String[] args) {
    new ReadXml().main2(args)
}

// end



@CompileStatic
static boolean needsSpace(NodeChild prevChild) {
    return prevChild != null && \
        (prevChild.name() != "format" || ((String)prevChild.attributes()['tag']).startsWith("/"))
}

private void generateStats(List<GPathResult> tokenXmls) {
    
    tokenXmls.eachWithIndex { it, idx ->
        String token = it.@value.toString() 
        String lemma = it.@lemma.toString() 
        String postag = it.@tags.toString()
    
        assert token, "Empty token at $idx, in $tokenXmls"
        
//        println "tk: $token"
//        println "pt: $postag"
            
//        if( postag == null || postag =~ /^(punct|symbol|unknown|unclass)/)
//            return 
        
//        String tagPos = postag.replaceFirst(/:.*/, '')
        
        if( postag != null && MAIN_POS.matcher(postag).find() ) {
            stats.freqs[lemma] += 1
            
            def currCtxToken = new ContextToken(token, lemma, postag)
            if( idx > 0 ) { 
                def ctxXml = tokenXmls[idx-1]
//                assert ctxXml.@token.text()
                def ctxToken = new ContextToken(ctxXml.@value.text(), ctxXml.@lemma.text(), ctxXml.@tags.text())
                def context = new WordContext(ctxToken, -1, postag, lemma)
                stats.freqs2[token][context] += 1
            }
            if( idx < tokenXmls.size()-1 ) { 
                def ctxXml = tokenXmls[idx+1]
                def ctxToken = new ContextToken(ctxXml.@value.text(), ctxXml.@lemma.text(), ctxXml.@tags.text())
                def context = new WordContext(ctxToken, +1, postag, lemma)
                stats.freqs2[token][context] += 1
            }
        }
    }
//    println "generateStats stats: ${stats.freqs2.size()}"
}

private void processItem(File txtFile, GPathResult xml, int childIdx) {
    if( xml.name() == "sentence" ) {
        if( sentIdx > 0 && needsSpace(prevChild) ) {
            txtFile << " "
        }
        childIdx = 0
        sentIdx++

        List tokenXmls = []
        
        xml.'*'.eachWithIndex { it, idx2 ->
            processItem(txtFile, it, idx2)
            if( it.name() == "token" ) {
                tokenXmls << it
            }
        }
        
        generateStats(tokenXmls)
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

//@Field
static final Pattern PUNCT_PATTERN = Pattern.compile(/[.!?,»\u201D)\]:;…°]|[.!?]{2,3}/) // (\.,»…\)\]|[.!?]{3})/)

//@CompileStatic
private void printNode(File txtFile, GPathResult xml, int childIdx) {
    if( xml.text() || xml.parent().name()=="meta" ) {
        txtFile << "<${xml.name()}>${xml.text()}</${xml.name()}>\n"
    }
    else if( xml.name() == "token" ) {
        boolean txt = false
        if( txt ) {
        if( childIdx > 0 
                && ! PUNCT_PATTERN.matcher(xml.@value.text()).matches()
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
        }
        
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


//@Field
static final Pattern VALID_TAG = Pattern.compile(/[a-z]+[a-z_0-9:&]*/)
//@Field
static final Pattern UKR_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґ].*/)
//@Field
static final Pattern WORD_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґa-z0-9].*/)
//@Field
static final Pattern MAIN_POS = Pattern.compile(/^(noun|adj|verb|advp?|prep|conj|numr|part|onomat|intj|noninfl)/)


void validateToken(xml) {
    String tags = xml.@tags
    String lemma = xml.@lemma
    String token = xml.@value
    
    if( ! VALID_TAG.matcher(tags).matches() ) {
        System.err.println "Invalid tag: $tags"
        return
    }

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
                stats.nullTags << token
                stats.nullTagCount++
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
                boolean initials = token ==~ /[А-ЯІЇЄҐ][а-яіїєґ]?\./ && tagPair ==~ /[А-ЯІЇЄҐ][а-яіїєґ]?\.\/noun:anim:[mf]:v_...:nv:abbr:prop:.name/ 
                if( ! (tagPair in ltTags2) && ! initials ) {
                    stats.unverified << "$tagPair (token: $token) (avail: $ltTags2)"
                    //                            println "Unverified tag: $tagPair (token: $token) (avail: $ltTags2)"
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
        println "$nullTagCount tokens with null tags !!"
        println "${unverified.size()} tokens with unverified tags !!"

        
        java.text.Collator coll = java.text.Collator.getInstance(new Locale("uk", "UA"));
        coll.setStrength(java.text.Collator.IDENTICAL)
        coll.setDecomposition(java.text.Collator.NO_DECOMPOSITION)

        // write warnings
        
        new File("err_unverified.txt").text = unverified.collect{ it.toString() }.toSorted(coll).join("\n")
        
        def nullTagsFile = new File("err_null_tags.txt")
        nullTagsFile.text = nullTags.collect{ it.toString() }.toSorted(coll).join("\n")
        
        def parseFailuresFile = new File("err_parse_failures.txt")
        parseFailuresFile.text = parseFailues.collect{ it.toString() }.toSorted(coll).join("\n")
        
        def dupsFile = new File("err_dups.txt")
        dupsFile.text = titles.findAll { k,v -> v.size() > 1 }.collect{ k,v -> "$k\n\t"+ v.join("\n\t") }.join("\n")
    
        // write stats
            
        freqs = freqs.toSorted { - it.value }
        
        def outFile = new File("lemma_freqs.txt")
        outFile.text = ""
        
        freqs.each { k,v ->
            outFile << "$v\t$k\n"
        }
        
        def outFileFreqFull = new File("lemma_freqs_full.txt")
        outFileFreqFull.text = ""
        def outFileFreqHom = new File("lemma_freqs_hom.txt")
        outFileFreqHom.text = ""

        freqs2
            .toSorted { a, b -> coll.compare a.key, b.key }
            .each { String token, Map<WordContext, Integer> map ->
                map.each { WordContext wordContext, value ->
                    outFileFreqFull << token.padRight(25) << " " << wordContext.toString().padRight(30) << " " << value << "\n"
                    if( token in homonymTokens ) {
                        outFileFreqHom << token.padRight(25) << " " << wordContext.toString().padRight(30) << " " << value << "\n"
                    }
                }
            }
        
    //    println ":: " + freqs2WithHoms.take(1)
                
        int uniqForms = freqs2.size()
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
}