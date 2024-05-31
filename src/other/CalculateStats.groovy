#!/bin/env groovy

package ua.net.nlp.analyzes

@Grab(group='org.languagetool', module='languagetool-core', version='6.4')
@Grab(group='org.languagetool', module='language-uk', version='6.4')
@Grab(group='ch.qos.logback', module='logback-classic', version='1.4.+')

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.Node
import groovy.xml.slurpersupport.Attribute

import java.util.regex.Pattern
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.lang.reflect.Modifier
import java.util.regex.Matcher

import org.languagetool.AnalyzedToken
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.tagging.uk.UkrainianTagger
import org.languagetool.rules.RuleMatch
import org.languagetool.AnalyzedSentence
import org.languagetool.JLanguageTool
import org.languagetool.Languages
import org.languagetool.language.Ukrainian
import org.languagetool.rules.Category
import org.languagetool.rules.CategoryIds
import org.languagetool.rules.Rule
import org.languagetool.rules.uk.CaseGovernmentHelper
import org.languagetool.rules.uk.TokenAgreementAdjNounRule
import org.languagetool.rules.uk.TokenAgreementNounVerbRule
import org.languagetool.rules.uk.TokenAgreementNumrNounRule
import org.languagetool.rules.uk.TokenAgreementPrepNounRule
import org.languagetool.rules.uk.TokenAgreementVerbNounRule

import ua.net.nlp.analyzes.Stats
import ua.net.nlp.analyzes.Validator


@Canonical
class ContextToken {
    String word
    String lemma
    String postag
}

if( args.length < 1 ) {
    System.err.println("Usage CaculateStats.groovy <dir name>")
    System.exit(1)
}

String DATA_DIR = args[0]


@Field
Stats stats = new Stats()
@Field
Validator validator = new Validator()
@Field
ExecutorService executor = Executors.newWorkStealingPool()
@Field
int txtWordCount = 0



//TagTextCore.printLtVersion()

def files = new File(DATA_DIR).listFiles().sort{ it.name }
files.each { File file->
    if( ! file.name.endsWith('.xml') ) {
        System.err.println "Unknown file type: ${file.name}"
        return
    }

//    println "Submit ${file)} files"
    executor.submit {
        processFile(file)
    }
}

println "Submitted ${files.size()} files"

executor.shutdown()
executor.awaitTermination(30, TimeUnit.SECONDS)

validator.writeErrors()
long tm1 = System.currentTimeMillis()
stats.writeStats()
long tm2 = System.currentTimeMillis()
println "write stats took ${tm2-tm1}ms"

println "txt words: ${txtWordCount}"


// end main

    
@CompileStatic
private void processFile(File file) {
    Stats localStats = new Stats(validator: validator)
    
    String inText = file.getText('UTF-8')
    
//    println "adding local stats for ${file.name}"
    try {
        
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(inText)
        
        Iterator<Node> childNodes = xml.childNodes()
        childNodes.each { Node node ->
            processItem(file, node, localStats)
        }

        stats.add(localStats)
    }
    catch(AssertionError e) {
        System.err.println("Invalid XML: ${e.getMessage()}")
        System.exit(1)
    }
    catch(Throwable e) {
        System.err.println("Failed to parse file ${file.name}: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}

@CompileStatic
private void processItem(File file, Node xml, Stats stats) {
    Iterator<Node> childNodes = xml.childNodes()
    
    if( xml.name() == "sentence" ) {
        List<Node> tokenXmls = new ArrayList<>()
        
        childNodes.each { Node it ->
            processItem(file, it, stats)
            if( it.name() == "token" ) {
                tokenXmls << it
            }
        }

        validator.validateSentence(tokenXmls, file)
        stats.generateStats(tokenXmls, file)
    }
    else if( childNodes
            && ! ((Node)childNodes[0]).name() == "alts" ) {
        String xmlName = xml.name()
        
        childNodes.each { Node it -> 
            processItem(file, it, stats)
        }
    }
    else {
        String xmlName = xml.name()
        if( xmlName == "token" ) {
            validateToken(xml, file, stats)
        }
    }
    
//   txtGenerator.printNode(txtFile, xml, childIdx)
}

@Field
ThreadLocal<ContextToken> prevToken =
    new ThreadLocal<ContextToken>() {
        public ContextToken initialValue() {
          return new ContextToken('')
        }
      }

  
@Field
int tokenIdx = 0


@CompileStatic
void validateToken(Node xml, File file, Stats stats) {
    def attributes = xml.attributes()
    String tags = attributes['tags']
    String lemma = attributes['lemma']
    String token = attributes['value']

    if( tags in ['xmltag'] )
        return

    if( validator.validateToken(token, lemma, tags, file) ) {    
        stats.addToStats(token, lemma, tags, file)
    }
    ++tokenIdx

    validator.validateToken2(token, lemma, tags, file, prevToken.get(), stats)
    
    prevToken.set(new ContextToken(token, lemma, tags))
}


// -------------


@CompileStatic
public class Stats {
    static final outDir="."
    
    static final Pattern MAIN_POS = Pattern.compile(/^(noun|adj|verb|advp?|prep|conj|numr|part|onomat|intj|noninfl)/)
    static final Pattern UKR_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґ].*/)
    static final String statsVersion = "3.2.1"
    static final Map<String, Integer> CATEGORIES = ["A": 25, "B": 3, "C": 7, "D": 7, "E": 3, "F": 5, "G": 10, "H": 15, "I": 25]
    static final boolean COLLECT_HOMONYMS = false

    int totalCount = 0
    int ukWordCount = 0
    public int wordCount = 0
    public int alphaWordCount = 0
    int multiTagCount = 0
    Map<String, Integer> ukWordCountByCat = [:].withDefault{ 0 }
    Map<String, Integer> freqCounts = [:].withDefault{ 0 }
    Map<String, Integer> wordCounts = [:].withDefault{ 0 }
    Map<String, Integer> wordsNormCounts = [:].withDefault{ 0 }
    Map<String, Integer> lemmaCounts = [:].withDefault{ 0 }
    Map<String, Integer> pos1Freq = [:].withDefault{ 0 }
    Map<String, Integer> ukWordHoms = [:].withDefault{ 0 }
    
    public Set<String> homonymTokens = new HashSet<>()
    Set<String> ignored = new HashSet<>()

    static Set<String> ignoreForStats = new HashSet()
    
    // homon stats
    int ukWordCountWithHom = 0
    int ukWordCountWithHomLemma = 0
    int ukWordWordWithHomHomTotalCount = 0
    int ukWordCountWithHomLemmaTotalCount = 0
    int ukWordHomTotalCount = 0
    Validator validator
    
    
    @CompileStatic
    static String keyPos(String tags) {
         tags.replaceFirst(/:.*/, '')
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
    synchronized
    void addToStats(String token, String lemma, String tags, File txtFile) {
        pos1Freq[keyPos(tags)]++

        totalCount++
        
        if( UKR_LEMMA.matcher(lemma).matches() ) {
            String cat = "all"
            
            ukWordCount++
            ukWordCountByCat[cat]++
            wordCounts[token]++
            wordsNormCounts[normalize(token, lemma)]++
            lemmaCounts[getLemmaKey(lemma, tags)]++
            
            if( COLLECT_HOMONYMS ) {
                def readings = validator.ukTagger.tag(Arrays.asList(token))[0].getReadings()
                if( readings.size() > 1 ) {
                    ukWordCountWithHom += 1
                    ukWordHoms[readings.toString()] += 1
                }
                ukWordWordWithHomHomTotalCount += readings.size()

                def lemmas = readings.collect { it.lemma }.unique()
                if( lemmas.size() > 1 ) {
                    ukWordCountWithHomLemma += 1
                }
                ukWordCountWithHomLemmaTotalCount += lemmas.size()

                ukWordHomTotalCount += readings.size()
            }
        }
    }

    @CompileStatic
    static String getLemmaKey(String lemma, String tags) {
        def m = tags =~ /^[a-z]+(:(([iu]n)?anim|perf|imperf))?/
        m.find()
        String key = m.group(0)
        if( tags.contains("pron") ) 
            key += "_pron"
        if( tags.contains(":nv") ) 
            key += "_nv"
        return "${lemma}_${key}"
    }
    
    @CompileStatic
    static int findCommon(String s1, String s2) {
        int i;
        for(i=0; i<s1.length() && i<s2.length(); i++) {
            if( s1.charAt(i) != s2.charAt(i) )
                break
        }
        return i
    }
        
    @CompileStatic
    void generateStats(List<Node> tokenXmls, File file) {
        if( (file.name.replaceFirst(/\.xml$/, '.txt') in ignoreForStats) ) {
            ignored << file.name
            return
        }
    
        tokenXmls.eachWithIndex { it, idx ->
            String token = it.attributes()['value'].toString()
            String lemma = it.attributes()['lemma'].toString()
            String postag = it.attributes()['tags'].toString()
        
            assert token, "Empty token at $idx, in $tokenXmls"
    
            addLemmaSuffixStats(token, lemma, postag)

            if( postag != null && MAIN_POS.matcher(postag).find() ) {
                freqCounts[lemma] += 1
            }
        }
    //    println "generateStats stats: ${freqs2.size()}"
    }

    @CompileStatic
    void addLemmaSuffixStats(String token, String lemma, String postag) {
        if( ! ( token.toLowerCase() ==~ /[а-яіїєґ']{4,}/) )
            return
        
        int lemmaSuffixLen = 4
        if( token.length() > lemmaSuffixLen
                && postag =~ /^(noun|adj|verb|adv)/
                && ! (postag =~ /(bad|arch|slang|pron|abbr)/)
                && ! (token =~ /^[0-9]/) ) { // аадського -> ого | ий/3

            if( token.endsWith("ться") ) {
                lemmaSuffixLen += 2
            }
            
            assert lemmaSuffixLen < token.length()
                
            String adjustedToken = postag =~ /prop|abbr/ ? token : token.toLowerCase()
            String adjustedPostag = postag.replaceAll(/:(xp[0-9]|comp.|&predic|&insert|&numr|&adjp:....:(im)?perf|ua_....)/, '')
            int commonLength = findCommon(adjustedToken, lemma)
            if( commonLength == 0 ) {
                commonLength = findCommon(token, lemma)
//                    assert commonLength, token
                if( commonLength < 2 ) {
//                    println "skipping for lemma suffixes: $token"
                    return
                }
            }
            
            int dashIdx = token.indexOf('-')
            if( dashIdx >= commonLength ) {
//                    println "skipping for lemma suffixes (hyphened): $token"
                return
            }
            
            def add = lemma.substring(commonLength, lemma.length())
//                assert add.length() <= 1, token
            def dropN = adjustedToken.length()-commonLength
            def lemmaSuffix = "$add/$dropN"
            def tokenSuffix = adjustedToken[-lemmaSuffixLen..-1]
            
            assert tokenSuffix.toLowerCase() ==~ /[а-яіїєґ'-]{4}|[а-яіїєґ']{6}/  
        }

    }
    
    void writeStats() {
        println "Writing stats"
        
        File f = new File("$outDir/stats_disambig.txt")
        f.text = ''
        
        f << "$ukWordCount Ukrainian tokens\n"
        f << "$totalCount total tokens\n"
        f << "$wordCount word/number tokens\n"
        f << "$alphaWordCount alpha word tokens\n"
        f << "${wordCounts.size()} unique Ukrainian words\n"
        f << "${wordsNormCounts.size()} unique Ukrainian words (case-insensitive)\n"
        f << "${lemmaCounts.size()} unique lemmas\n"
    //    println "Tag freqs:\n" + pos1Freq.sort{k,v -> -v}.collect{k,v -> k.padRight(25) + " $v"}.join("\n")
        
    //    println "$multiWordCount multiword tags !!"
        println "$multiTagCount tokens with multiple tags !!"

        int ukWordCountByCatSum = (int)ukWordCountByCat.values().sum(0)
        
//        f << "\nUkrainian tokens by category:\n"
//        ukWordCountByCat.toSorted{ e -> e.getKey() }.each { k,v ->
//            BigDecimal pct = v*100.0/ukWordCountByCatSum
//            pct = pct.round(1)
//            def ct = CATEGORIES[k]
//            f << "$k $v - ${pct} of $ct%\n"
//        }

        if( COLLECT_HOMONYMS ) {
            println "words with hom: $ukWordCountWithHom"
            println "avg hom cnt: ${(double)ukWordHomTotalCount/ukWordCountByCatSum}"
    //        println "avg hom cnt for word with homonym: ${(double)ukWordWordWithHomHomTotalCount/ukWordCountWithHom}"
            println "words with hom lemma: $ukWordCountWithHomLemma"
            println "avg hom lemma cnt per Ukrainian word: ${(double)ukWordCountWithHomLemmaTotalCount/ukWordCountByCatSum}"
    //        println "avg hom lemma cnt for word with lemma homonym: ${(double)ukWordWordWithHomHomTotalCount/ukWordCountWithHomLemma}"
            
            new File("$outDir/homs.txt").text = ukWordHoms.toSorted { e -> -e.value }.collect { e -> "${e.key}  ${e.value}" }.join("\n")
        }
        
        java.text.Collator coll = java.text.Collator.getInstance(new Locale("uk", "UA"));
        coll.setStrength(java.text.Collator.IDENTICAL)
        coll.setDecomposition(java.text.Collator.NO_DECOMPOSITION)

        // write stats
            
        freqCounts = freqCounts.toSorted { - it.value }
        
        def outFile = new File("$outDir/lemma_freqs.txt")
        outFile.text = ""
        
        freqCounts.each { k,v ->
            outFile << "$v\t$k\n"
        }
        
        def outFileW = new File("$outDir/word_freqs.txt")
        outFileW.text = ""
        
        wordCounts.each { k,v ->
            outFileW << "$v\t$k\n"
        }
        
        new File("$outDir/lemmas.txt").text = lemmaCounts.toSorted { e -> - e.getValue() }.collect { k,v -> "$k $v" }.join("\n")
    }
     

    static final List<java.lang.reflect.Field> FIELDS = Stats.class.getDeclaredFields() \
        .findAll{ f -> ! Modifier.isStatic(f.getModifiers()) && !(f.name ==~ /validator|metaClass/) }
    
    @CompileStatic
    synchronized
    void add(Stats newStats) {
        int updated = 0
        def fields = FIELDS.findAll{ f -> f.setAccessible(true); f.get(newStats) != null }
//        println "fields: ${fields.size()}"
        fields.each { field ->
            if( int.class.isAssignableFrom(field.getType()) ) {
                def val = ((Integer)field.get(this)) + (Integer)field.get(newStats)
                field.set(this, val)
            }
            else if( Collection.class.isAssignableFrom(field.getType()) ) {
                ((Collection)field.get(this)).addAll( (Collection)field.get(newStats) )
            }
            else if( Map.class.isAssignableFrom(field.getType()) ) {
                def newMap = (Map)field.get(newStats)
                def totalMap = (Map)field.get(this)
                
//                System.err.println("summing ${field.name}")
                addMapCounts(field, (Map)field.get(this), newMap)
            }
            else {
                println "WARNING: ignoring ${field.name} in Stats.add()"
            }
        }
    }

    private static void addMapCounts(java.lang.reflect.Field field, Map totalMap, Map newMap) {
        if( newMap.isEmpty() )
            return

        def firstValue = ((Map.Entry)newMap.iterator().next()).getValue()
        
        if( firstValue instanceof Integer ) {
            newMap.each{ k,v ->
                if( v ) {
                    ((Map<?, Integer>)totalMap)[k] += (Integer)v
                }
            }
        }
        else if( firstValue instanceof Map ) {
            newMap.each { k,v ->
                addMapCounts(field, totalMap[k], (Map)v)
            }
        }
        else {
            println "WARN: unknown map for ${field.name}"
        }
    }
}

// --------------


public class Validator {
    static final outDir="."
    
    static final Pattern WORD_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґa-z0-9].*/)
    static final Pattern ALPHA_WORD_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґa-z].*/)
    static final List<String> EXTRA_TAGS = [ 'punct', 'number', 'number:latin', 'time', 'date', 'unclass', 'unknown', 'symb', 'hashtag' ]
    
    def lt = new JLanguageTool(Languages.getLanguageForShortCode("uk"), [], null, null, null, null)
    def ukrainian = lt.getLanguage()
//    @Lazy
    UkrainianTagger ukTagger = ukrainian.getTagger()
    static Set<String> allTags
    ResourceBundle messages = JLanguageTool.getDataBroker().getResourceBundle(JLanguageTool.MESSAGE_BUNDLE, new Locale("uk"))
    List<org.languagetool.rules.Rule> validationRules = 
        [new TokenAgreementNounVerbRule(messages),
        new TokenAgreementAdjNounRule(messages, ukrainian),
        new TokenAgreementNumrNounRule(messages, ukrainian),
        new TokenAgreementPrepNounRule(messages, ukrainian),
        new TokenAgreementVerbNounRule(messages, ukrainian),
        ]
    
    Map<String, List<String>> errValidations = Collections.synchronizedMap([:].withDefault{ [] })
    List<String> errUnverified = Collections.synchronizedList([])

    static {
        allTags = Validator.class.getResource('/org/languagetool/resource/uk/ukrainian_tags.txt').readLines() as Set
        allTags += EXTRA_TAGS
    }    

    Validator() {
        def xmlRules = ukrainian.getPatternRules().findAll { Rule r -> 
            r.getCategory().getId() == CategoryIds.GRAMMAR && r.getId() =~ "(?i)(CONSISTENCY.*NUMERIC|PIVTORA|PRIZVY|LAST_NAME|MODAL|PREP_BEFORE_VERB)" //|token_agreement_noun_noun)"
        }
        println "Added ${xmlRules.size()} xml rules"
        validationRules += xmlRules
    }
    
    
    @CompileStatic
    boolean validateToken(String token, String lemma, String tags, File file) {
        assert tags, "no tags for token $token"
        assert lemma != null, "no lemma for token $token"
        
        def xmlFilename = file.name
        
        List<String> tagSet = tags.split(/:/) as List
        if( EXTRA_TAGS.intersect(tagSet) ) {
            if( token != lemma && token != "…" ) {
//                println "\tWrong lemma: $tags for $token"
                errValidations[xmlFilename] << "Wrong lemma: $lemma for $token tags: $tags".toString()
            }
        }

        if( tags =~ /:bad/ ) {
            def tkns = ukTagger.tag([token])[0].getReadings()
                    .findAll { AnalyzedToken it ->
                        it.lemma == lemma \
                        && it.POSTag.startsWith(tags.replaceFirst(/:.*/, '')) }
            if( tkns && tkns.find { it.POSTag.contains(":bad") } == null ) {
                errValidations[xmlFilename] << "Bad with no bad lemma, maybe subst?: $lemma for $token tags: $tags".toString()
            }
        }

        if( ! (tags in allTags) && ! (tags.replaceAll(/:(alt|bad|short)/, '') in allTags) ) {
            if( ! ( tags =~ /noninfl(:foreign)?:prop|noun:anim:p:v_zna:var|noun:anim:[mfp]:v_...:nv:abbr:prop:[fp]name/ ) ) {
//                println "\tInvalid tag: $tags for $token"
                errValidations[xmlFilename] << "Invalid tag: $tags for $token".toString()
                return false
            }
        }

        if( tags.contains(":prop") && lemma =~ /^[а-яіїєґ]/ 
                || tags.startsWith("noun") && ! tags.contains(":prop") && ! tags.contains(":abbr") && lemma =~ /^[А-ЯІЇЄҐ]([а-яіїєї].*|$)/ ) {
//            println "\tInvalid tag: $tags for $token"
            errValidations[xmlFilename] << "Invalid tag 2: $tags for $token".toString()
            return false
        }
            
        if( tags.contains(":nv") && lemma.toLowerCase() != token.toLowerCase() ) {
//            println "\tInvalid tag: $tags for $token"
            errValidations[xmlFilename] << "Lemma $lemma mismatches $token for $tags".toString()
            return false
        }

//        if( ! tags.contains(":nv") && lemma.toLowerCase() == token.toLowerCase() && tags =~ /^(noun|adj):m:/ ) {
//            boolean ok = tags =~ /^(.*v_naz|adj:.:v_kly|adj:m:v_(zna:rinanim)|noun:inanim:m:v_zna)/
//            if( ! ok ) {
//                println "\tInvalid tag: $tags for $token"
//                errValidations[xmlFilename] << "Lemma $lemma mismatches $token for $tags".toString()
//                return false
//            }
//        }

        return true
    //    if( ! VALID_TAG.matcher(tags).matches() ) {
    //        System.err.println "Invalid tag: $tags"
    //        return
    //    }
    }

    @CompileStatic
    synchronized
    AnalyzedTokenReadings tag(String token) {
        ukTagger.tag([token])[0]
    }
        
    @CompileStatic
    void validateToken2(String token, String lemma, String postag, File file, ContextToken prevToken, Stats stats) {
        if( WORD_LEMMA.matcher(lemma).matches() ) {
            stats.wordCount++
            
            if( ALPHA_WORD_LEMMA.matcher(lemma).matches() ) {
                stats.alphaWordCount++
            }
            
            if( postag == "null" ) {
                System.err.println "null tag for $token"
            }
            else if( postag.startsWith("noninfl") || postag.startsWith("unclass") ) {
                
            }
            else {
                if( postag == "part" && token ==~ /-(бо|но|то|от|таки)/ && token == "-" + lemma )
                    return

                AnalyzedTokenReadings ltTags = tag(token)
                
                if( ltTags.getReadings().size() > 1 ) {
                    stats.homonymTokens << token
                }
                
                def ltTags2 = ltTags.getReadings().collect { AnalyzedToken t -> t.getLemma() + "/" + t.getPOSTag() }
                
                String tagPair = "$lemma/$postag"
                if( postag.startsWith("noninfl:foreign") || postag.startsWith("unclass") ) {
                }
//                if( ltTags2[0].startsWith("null") && token.matches("[А-ЯІЇЄҐ][а-яіїєґА-ЯІЇЄҐ'-]+") && tags.matches(/noun:anim:.:v_...(:nv)?:prop:lname/) ) {
//                }
//                {
                    boolean initials = token ==~ /[А-ЯІЇЄҐ][а-яіїєґ]?\./ && tagPair ==~ /[А-ЯІЇЄҐ][а-яіїєґ]?\.\/noun:anim:[mf]:v_...:nv:abbr:prop:[fp]name/
                    if( ! (tagPair in ltTags2) && ! initials ) {
                        if( token != "їх" || ! (postag ==~ /adj:[mfnp]:v_...(:r(in)?anim)?:nv:&pron:pos:bad/ ) ) {
                            
                            errUnverified << "value=\"$token\" lemma=\"${tagPair.replace('/', '\" tags=\"')}\"  (avail: $ltTags2)".toString()
                    //                            println "Unverified tag: $tagPair (token: $token) (avail: $ltTags2)"
                        }
                        return
                    }
//                }
            }
            
            String xmlFileName = file.name
            
            if( postag =~ /.*:p:v_naz.*/ ) {
                if( prevToken.word.toLowerCase() == "є" && prevToken.postag =~ /:s:3/ ) {
                    errValidations[xmlFileName] << "value=\"$token\" lemma=\"$lemma\" tags=\"$postag\" (prev: $prevToken)".toString()
                }
            }
            if( postag =~ /.*:[mnfs]:v_naz.*/ ) {
                if( prevToken.word.toLowerCase() == "є" && prevToken.postag =~ /:p:3/ ) {
                    errValidations[xmlFileName] << "value=\"$token\" lemma=\"$lemma\" tags=\"$postag\" (prev: $prevToken)".toString()
                }
            }
            if( token.toLowerCase() == "є" && postag =~ /:s:3/ ) {
                if( prevToken.postag =~ /.*:p:v_naz.*/ ) {
                    errValidations[xmlFileName] << "value=\"$token\" lemma=\"$lemma\" tags=\"$postag\" (prev: $prevToken)".toString()
                }
            }
            if( token.toLowerCase() == "є" && postag =~ /:p:3/ ) {
                if( prevToken.postag =~ /.*:[mnfs]:v_naz.*/ ) {
                    errValidations[xmlFileName] << "value=\"$token\" lemma=\"$lemma\" tags=\"$postag\" (prev: $prevToken)".toString()
                }
            }
        }
    }
    
    @CompileStatic
    void validateSentence(List<Node> xmls, File file) {
        int pos = 0
        List<AnalyzedTokenReadings> readings = []
        readings << new AnalyzedTokenReadings(Arrays.asList(new AnalyzedToken('', JLanguageTool.SENTENCE_START_TAGNAME, '')), 0)
        
        xmls.each { Node xml ->
            def attributes = xml.attributes()
            String tags = attributes['tags']
            String lemma = attributes['lemma']
            String token = attributes['value']
            
            if( tags =~ /unclass|punct|unknown|symbol/ ) {
                tags = null
            }
            
            boolean whitespaceBefore = false
            if( pos > 0 && tags != null ) {
                readings << new AnalyzedTokenReadings(new AnalyzedToken(' ', null, null), pos)
                pos += 1
                whitespaceBefore = true
            }
            
            def tokens = Arrays.asList(new AnalyzedToken(token, tags, lemma))
            def atr = new AnalyzedTokenReadings(tokens, pos)
            if( whitespaceBefore ) {
                atr.setWhitespaceBefore(" ")
            }
            readings << atr
            pos += token.length()
        }
        
        AnalyzedSentence sent = new AnalyzedSentence(readings.toArray(new AnalyzedTokenReadings[0]))
        
        validationRules.each { rule ->
            RuleMatch[] matches = rule.match(sent)
            matches.each {
                def sample = xmls.collect{it.attributes()['value']}.join(' ')
                int fromPos = it.fromPos - 15
                if( fromPos < 0 ) fromPos = 0 
                sample = sample[fromPos..-1]
//                println "\trule violation: $it\n\t$sample"
                errValidations[file.name] << "$it\n\t\t$sample".toString()
            }
        }
        
        readings.removeIf{AnalyzedTokenReadings t -> ! t.getReadings()[0].lemma } // remove SENT_START and spaces
        
        validateAdjAdj(readings, file.name)
        validateNounAdj(readings, file.name)
        validateAnd(readings, file.name)
        validateComma(readings, file.name)
    }
    
    @CompileStatic
    void validateAdjAdj(List<AnalyzedTokenReadings> readings, String xmlFileName) {
        
        for(int ii=1; ii<readings.size(); ii++) {
            def reading1 = readings.get(ii)
            def reading0 = readings.get(ii-1)
            def r1 = reading1.getReadings().get(0)
            def r0 = reading0.getReadings().get(0)

            def r0POSTag = r0.getPOSTag()
            def r1POSTag = r1.getPOSTag()
            
            if( r0POSTag== null || r1POSTag == null )
                continue;

            if( r0POSTag =~ /^numr:.:v_(rod|dav|oru|mis)/ ) {
                r0POSTag = r0POSTag.replaceFirst(/^numr/, 'adj')
            }
                            
            if( r0POSTag.startsWith("adj") && r1POSTag.startsWith("adj") ) {
                
                Matcher m0 = r0POSTag =~ /adj:.:(v_...)(:r(in)?anim)?/
                Matcher m1 = r1POSTag =~ /adj:.:(v_...)(:r(in)?anim)?/
                
                if( m1[0] != m0[0] ) {
                    if( r0.getToken() =~ /^[0-9]+-[а-яіїєґ]+/
                        || (r0POSTag=~ /adjp:pasv:perf/ 
                            && r1POSTag =~ /adj:.:v_oru/)
                            || r0.getLemma() =~ /^(який|котрий|кожен)$/
                            || CaseGovernmentHelper.hasCaseGovernment(reading0, m1.group(1)) )
                        continue
                    
                    errValidations[xmlFileName] << "${r0.getToken()} ${r1.getToken()} -- ${r0.getPOSTag()} ${r1.getPOSTag()}".toString()
                }
            }
        }
    }

    @CompileStatic
    void validateNounAdj(List<AnalyzedTokenReadings> readings, String xmlFileName) {
        
        for(int ii=1; ii<readings.size(); ii++) {
            def reading1 = readings.get(ii)
            def reading0 = readings.get(ii-1)
            def r1 = reading1.getReadings().get(0)
            def r0 = reading0.getReadings().get(0)

            def r0POSTag = r0.getPOSTag()
            def r1POSTag = r1.getPOSTag()
            
            if( r0POSTag== null || r1POSTag == null )
                continue;

//            if( r0POSTag =~ /^numr:.:v_(rod|dav|oru|mis)/ ) {
//                r0POSTag = r0POSTag.replaceFirst(/^numr/, 'adj')
//            }
                            
            if( r0POSTag.startsWith("noun") && r1POSTag.startsWith("adj") ) {
                
                Matcher m0 = r0POSTag =~ /:([mnp]):v_(naz|zna)/
                Matcher m1 = r1POSTag =~ /:([mnp]):v_(naz|zna)(?!.*pron)/
                
                if( ! m0 || ! m1 )
                    continue
                if( m0.group(1) != m1.group(1) )
                    continue
    
                if( m1.group(2) != m0.group(2) ) {
//                    if( r0.getToken() =~ /^[0-9]+-[а-яіїєґ]+/
//                        || (r0POSTag=~ /adjp:pasv:perf/
//                            && r1POSTag =~ /adj:.:v_oru/)
//                            || r0.getLemma() =~ /^(який|котрий|кожен)$/
//                            || CaseGovernmentHelper.hasCaseGovernment(reading0, m1.group(1)) )
//                        continue
                    
                    errValidations[xmlFileName] << "noun-adj: ${r0.getToken()} ${r1.getToken()} -- ${r0.getPOSTag()} ${r1.getPOSTag()}".toString()
                }
            }
        }
    }

    @CompileStatic
    void validateComma(List<AnalyzedTokenReadings> readings, String xmlFileName) {
        
        for(int ii=2; ii<readings.size(); ii++) {
            def reading2 = readings.get(ii)
            def reading1 = readings.get(ii-1)
            def reading0 = readings.get(ii-2)
            def r2 = reading2.getReadings().get(0)
            def r0 = reading0.getReadings().get(0)

//            if( ! (reading1.getCleanToken() ==~ /та|і|й|,/)
            if( ! (reading1.getCleanToken() ==~ /,/)
                || r0.getPOSTag() == null || r2.getPOSTag() == null )
                continue;

            if( r0.getToken() =~ /^[0-9]+-[а-яіїєґ]+/ )
                continue

            if( r0.getPOSTag() =~ /^(noun)(?!.*pro[np])/
                    && r2.getPOSTag() =~ /^(adj)(?!.*pron)/ ) {
                
                Matcher m2 = r2.getPOSTag() =~ /:(.):(v_...)/
                Matcher m0 = r0.getPOSTag() =~ /:(.):(v_...)/
                m2.find()
                m0.find()
                
                if( m2.group(1) == m0.group(1)
                        && m2.group(2) != m0.group(2) ) {
                    
                    if( m2.group(1) == "f" 
                            && m2.group(2) =~ /(mis|rod|dav)/ && m0.group(2) =~ /(mis|rod|dav)/
                            || m2.group(1) == "n"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                            || m2.group(1) =~ "p"
                            && m2.group(2) =~ /(rod|dav|mis)/ && m0.group(2) =~ /(rod|dav|mis)/
                            || m2.group(1) =~ "p"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                            || m2.group(1) =~ "p"
                            && m2.group(2) =~ /(zna|rod)/ && m0.group(2) =~ /(zna|rod)/
                            || m2.group(1) =~ "m"
                            && m2.group(2) =~ /(rod|zna)/ && m0.group(2) =~ /(rod|zna)/
                            || m2.group(1) =~ "m"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                             ) {
                        
                        errValidations[xmlFileName] << "${r0.getToken()} ${reading1.getCleanToken()} ${r2.getToken()} -- ${r0.getPOSTag()} ${r2.getPOSTag()}".toString()
                    }
                }
            }
        }
    }

    @CompileStatic
    void validateAnd(List<AnalyzedTokenReadings> readings, String xmlFileName) {
        
        for(int ii=2; ii<readings.size(); ii++) {
            def reading2 = readings.get(ii)
            def reading1 = readings.get(ii-1)
            def reading0 = readings.get(ii-2)
            def r2 = reading2.getReadings().get(0)
            def r0 = reading0.getReadings().get(0)

            if( ! (reading1.getCleanToken() ==~ /та|і|й|або|чи/)
                    || r0.getPOSTag() == null || r2.getPOSTag() == null )
                continue;
                
            if( r0.getToken() =~ /^[0-9]+-[а-яіїєґ]+/ )
                continue
    
            if( r0.getPOSTag() =~ /^(noun|adj)(?!.*(pro[np]|abbr))/
                    && r2.getPOSTag() =~ /^(noun|adj)(?!.*pron)/ ) {
                
                Matcher m2 = r2.getPOSTag() =~ /:(.):(v_...)/
                Matcher m0 = r0.getPOSTag() =~ /:(.):(v_...)/
                m2.find()
                m0.find()
                
                if( m2.group(1) == m0.group(1)
                        && m2.group(2) != m0.group(2) ) {
                    
                    if( m2.group(1) == "n"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                            || m2.group(1) =~ "p"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                            || m2.group(1) =~ "p"
                            && m2.group(2) =~ /(zna|rod)/ && m0.group(2) =~ /(zna|rod)/
                            || m2.group(1) =~ "m"
                            && m2.group(2) =~ /(rod|zna)/ && m0.group(2) =~ /(rod|zna)/
                            || m2.group(1) =~ "m"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                             ) {
                        
                        errValidations[xmlFileName] << "${r0.getToken()} ${reading1.getCleanToken()} ${r2.getToken()} -- ${r0.getPOSTag()} ${r2.getPOSTag()}".toString()
                    }
                }
            }
        }
    }


//    @CompileStatic
    void writeErrors() {
        println "${errUnverified.size()} tokens with unverified tags !!"

        java.text.Collator coll = java.text.Collator.getInstance(new Locale("uk", "UA"));
        coll.setStrength(java.text.Collator.IDENTICAL)
        coll.setDecomposition(java.text.Collator.NO_DECOMPOSITION)

        // write warnings
        
        new File("$outDir/err_unverified.txt").text = errUnverified.collect{ 
                it.replaceFirst(/(value=")([^а-яіїєґ])/, '$1* $2').toString()
            }
            .toSorted(coll)
            .collect{ it.replace('="* ', '="') }
            .join("\n")

        new File("$outDir/err_validations.txt").text = errValidations
            .toSorted{ e -> e.getKey() }
            .collect { k,v -> "$k\n\t" + v.join("\n\t") }.join("\n")

    }    
}


