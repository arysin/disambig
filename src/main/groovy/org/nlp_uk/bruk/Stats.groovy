package org.nlp_uk.bruk

import java.util.regex.Pattern
import groovy.xml.slurpersupport.Node
import groovy.transform.CompileStatic

@CompileStatic
class Stats {
    static final Pattern MAIN_POS = Pattern.compile(/^(noun|adj|verb|advp?|prep|conj|numr|part|onomat|intj|noninfl)/)
    static final Pattern UKR_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґ].*/)
    
    int count = 0
    int wordCount = 0
    int multiTagCount = 0
    boolean sentence = false
    Map<String, Integer> freqs = [:].withDefault{ 0 }
    Map<String, Map<WordReading, Map<WordContext, Integer>>> disambigStats = [:].withDefault { [:].withDefault { [:].withDefault { 0 } }}
    Map<String, Map<WordReading, Integer>> disambigStatsF = [:].withDefault { [:].withDefault { 0 } }
    Map<String, Integer> words = [:].withDefault{ 0 }
    Map<String, Integer> wordsNorm = [:].withDefault{ 0 }
    Map<String, Integer> lemmas = [:].withDefault{ 0 }
    Map<String, Integer> pos1Freq = [:].withDefault{ 0 }
    Set<String> homonymTokens = new HashSet<>()
    Set<String> ignored = new HashSet<>()
    Set<String> ignoreForStats
    
    Stats() {
        ignoreForStats = getClass().getResource('/ignore_for_stats.txt').readLines().findAll { it && ! it.startsWith('#') } as Set
        println "Ignoring for stats: ${ignoreForStats.size()} files"
    }
    
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
    void addToStats(String token, String lemma, String tags) {
        pos1Freq[keyPos(tags)]++
        
        if( UKR_LEMMA.matcher(lemma).matches() ) {
            count++
            words[token]++
            wordsNorm[normalize(token, lemma)]++
            lemmas[lemma]++
        }
    }
    
    @CompileStatic
    void generateStats(List<Node> tokenXmls, File txtFile) {
        if( (txtFile.name in ignoreForStats) ) {
            ignored << txtFile.name
            return
        }
    
        tokenXmls.eachWithIndex { it, idx ->
            String token = it.attributes()['value'].toString()
            String lemma = it.attributes()['lemma'].toString()
            String postag = it.attributes()['tags'].toString()
        
            assert token, "Empty token at $idx, in $tokenXmls"
    
            WordReading wordReading = new WordReading(lemma, postag)
            disambigStatsF[token][wordReading] += 1
            
            if( postag != null && MAIN_POS.matcher(postag).find() ) {
                freqs[lemma] += 1
                
    //            def currCtxToken = ContextToken.normalized(token, lemma, postag)
                
                [-1].each { int offset ->
                
                    ContextToken ctxToken = null
                    def ctxXml = findCtx(tokenXmls, idx, offset) 

                    if( ctxXml != null ) {                    
                        Map<String, String> ctxAttrs = ctxXml.attributes()
                        ctxToken = ContextToken.normalized(ctxAttrs['value'], ctxAttrs['lemma'], ctxAttrs['tags'])
                        def context = new WordContext(ctxToken, offset)
                    }
                    else {
                        ctxToken = new ContextToken('^', '^', offset < 0 ? "BEG" : "END")
                    }
                    def context = new WordContext(ctxToken, offset)
                    disambigStats[token][wordReading][context] += 1
                }
                
            }
        }
    //    println "generateStats stats: ${freqs2.size()}"
    }

    @CompileStatic
    Node findCtx(List<Node> tokensXml, int pos, int offset) {
        for( ; pos+offset >= 0 && pos+offset < tokensXml.size()-1; offset++) {
            if( ! isIgnorableCtx(tokensXml[pos+offset]) ) // TODO: stop at 1 skipped?
                return tokensXml[pos+offset]
        }
        return null
    }
    
    @CompileStatic
    static boolean isIgnorableCtx(Node item) {
        Map<String, String> ctxAttrs = item.attributes()
        
        ctxAttrs['value'] in ContextToken.IGNORE_TOKENS
    }
    
    void writeStats() {
        println "$count Ukrainian tokens"
        println "$wordCount word/number tokens"
        println "${words.size()} unique Ukrainian words"
        println "${wordsNorm.size()} unique Ukrainian words (case-insensitive)"
        println "${lemmas.size()} unique lemmas"
    //    println "Tag freqs:\n" + pos1Freq.sort{k,v -> -v}.collect{k,v -> k.padRight(25) + " $v"}.join("\n")
        
    //    println "$multiWordCount multiword tags !!"
        println "$multiTagCount tokens with multiple tags !!"

        
        java.text.Collator coll = java.text.Collator.getInstance(new Locale("uk", "UA"));
        coll.setStrength(java.text.Collator.IDENTICAL)
        coll.setDecomposition(java.text.Collator.NO_DECOMPOSITION)

        // write stats
            
        freqs = freqs.toSorted { - it.value }
        
        def outFile = new File("lemma_freqs.txt")
        outFile.text = ""
        
        freqs.each { k,v ->
            outFile << "$v\t$k\n"
        }
        
        writeDisambigStats(coll)
    }
     
    @CompileStatic
    void writeDisambigStats(java.text.Collator coll) {
    //    def outFileFreqFull = new File("lemma_freqs_full.txt")
    //    outFileFreqFull.text = ""
        def outFileFreqHom = new File("lemma_freqs_hom.txt")
        outFileFreqHom.text = ""
    
        println "Ignored for stats: $ignored"
        
        println "Writing ${disambigStats.size()} disambig stats..."
        disambigStats
            .toSorted { a, b -> coll.compare a.getKey(), b.getKey() }
            .each { String token, Map<WordReading, Map<WordContext, Integer>> map1 ->
                double tokenTotalRate = Double.valueOf(((Integer)disambigStatsF[token].values().sum()))
                
                map1
                .toSorted{ a, b -> b.getKey().getPostag().compareTo(a.getKey().getPostag()) }
                .each { WordReading wordReading, Map<WordContext, Integer> map2 ->
//                        outFileFreqFull << token.padRight(20) << "," << wordReading << ", " << stats.disambigStatsF[token][wordReading] << "\n"
                    if( token in homonymTokens ) {
                        def rate = disambigStatsF[token][wordReading] / tokenTotalRate
                        outFileFreqHom << token << "\t\t" << wordReading << "\t" << rate << "\n"
                        
                        map2
//                        .toSorted{ a, b -> b.key.contextToken.word.compareTo(a.key.contextToken.word) }
                        .toSorted{ a, b -> b.getValue().compareTo(a.getValue()) }
                        .each { WordContext wordContext, int value ->
//                            outFileFreqFull << "  , " << wordContext.toString().padRight(30) << ", " << value << "\n"
                            if( wordContext.getOffset() == -1 ) {
                                def rateCtx = value / tokenTotalRate
                                outFileFreqHom << "\t" << wordContext.toString() << "\t\t" << rateCtx << "\n"
                            }
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
