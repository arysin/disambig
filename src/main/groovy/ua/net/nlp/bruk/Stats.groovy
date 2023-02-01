package ua.net.nlp.bruk

import java.util.regex.Pattern
import groovy.xml.slurpersupport.Node
import groovy.transform.CompileStatic

@CompileStatic
class Stats {
    static final Pattern MAIN_POS = Pattern.compile(/^(noun|adj|verb|advp?|prep|conj|numr|part|onomat|intj|noninfl)/)
    static final Pattern UKR_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґ].*/)
    static final String statsVersion = "3.1.1"
    static final Map<String, Integer> CATEGORIES = ["A": 25, "B": 3, "C": 7, "D": 7, "E": 3, "F": 5, "G": 10, "H": 15, "I": 25]

    int totalCount = 0
    int ukWordCount = 0
    int wordCount = 0
    int multiTagCount = 0
    boolean sentence = false
    Map<String, Integer> ukWordCountByCat = [:].withDefault{ 0 }
    Map<String, Integer> freqs = [:].withDefault{ 0 }
    Map<String, Map<WordReading, Map<WordContext, Integer>>> disambigStats = [:].withDefault { [:].withDefault { [:].withDefault { 0 } }}
    Map<String, Map<WordReading, Integer>> disambigStatsF = [:].withDefault { [:].withDefault { 0 } }
//    Map<String, Map<WordReading, Map<WordContext, Integer>>> lemmaSuffixStats = [:].withDefault { [:].withDefault { [:].withDefault { 0 } }}
    Map<String, Map<WordReading, Integer>> lemmaSuffixStatsF = [:].withDefault { [:].withDefault { 0 } }
    Map<String, Integer> words = [:].withDefault{ 0 }
    Map<String, Integer> wordsNorm = [:].withDefault{ 0 }
    Map<String, Integer> lemmas = [:].withDefault{ 0 }
    Map<String, Integer> pos1Freq = [:].withDefault{ 0 }
    Set<String> homonymTokens = new HashSet<>()
    Set<String> ignored = new HashSet<>()
    Set<String> ignoreForStats
    
    Stats() {
        if( ! Boolean.getBoolean("full") ) {
            ignoreForStats = getClass().getResource('/ignore_for_stats.txt').readLines().findAll { it && ! it.startsWith('#') } as Set
            println "Ignoring for stats: ${ignoreForStats.size()} files"
        }
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
    void addToStats(String token, String lemma, String tags, File txtFile) {
        pos1Freq[keyPos(tags)]++

        totalCount++
        
        if( UKR_LEMMA.matcher(lemma).matches() ) {
            String cat = txtFile.name[0]
            
            ukWordCount++
            ukWordCountByCat[cat]++
            words[token]++
            wordsNorm[normalize(token, lemma)]++
            lemmas[getLemmaKey(lemma, tags)]++
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
    
            token = ContextToken.normalizeWord(token, lemma, postag)
            
            WordReading wordReading = new WordReading(lemma, postag)
            disambigStatsF[token][wordReading] += 1

            addLemmaSuffixStats(token, lemma, postag)

            if( postag != null && MAIN_POS.matcher(postag).find() ) {
                freqs[lemma] += 1
                
    //            def currCtxToken = ContextToken.normalized(token, lemma, postag)
                
                [-1, +1].each { int offset ->
                
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
                        println "skipping for lemma suffixes: $token"
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
            WordReading wordReadingLemmaSuffix = new WordReading(lemmaSuffix, adjustedPostag)
            def tokenSuffix = adjustedToken[-lemmaSuffixLen..-1]
            
            assert tokenSuffix.toLowerCase() ==~ /[а-яіїєґ'-]{4}|[а-яіїєґ']{6}/  
            
            lemmaSuffixStatsF[tokenSuffix][wordReadingLemmaSuffix] += 1
        }

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
        File f = new File("out/stats_disambig.txt")
        f.text = ''
        
        f << "$ukWordCount Ukrainian tokens\n"
        f << "$totalCount total tokens\n"
        f << "$wordCount word/number tokens\n"
        f << "${words.size()} unique Ukrainian words\n"
        f << "${wordsNorm.size()} unique Ukrainian words (case-insensitive)\n"
        f << "${lemmas.size()} unique lemmas\n"
    //    println "Tag freqs:\n" + pos1Freq.sort{k,v -> -v}.collect{k,v -> k.padRight(25) + " $v"}.join("\n")
        
    //    println "$multiWordCount multiword tags !!"
        println "$multiTagCount tokens with multiple tags !!"

        int ukWordCountByCatSum = (int)ukWordCountByCat.values().sum(0)
        
        f << "\nUkrainian tokens by category:\n"
        ukWordCountByCat.toSorted{ e -> e.getKey() }.each { k,v ->
            BigDecimal pct = v*100.0/ukWordCountByCatSum
            pct = pct.round(1)
            def ct = CATEGORIES[k]
            f << "$k $v - ${pct} of $ct%\n"
        }
        
        java.text.Collator coll = java.text.Collator.getInstance(new Locale("uk", "UA"));
        coll.setStrength(java.text.Collator.IDENTICAL)
        coll.setDecomposition(java.text.Collator.NO_DECOMPOSITION)

        // write stats
            
        freqs = freqs.toSorted { - it.value }
        
        def outFile = new File("out/lemma_freqs.txt")
        outFile.text = ""
        
        freqs.each { k,v ->
            outFile << "$v\t$k\n"
        }
        
        new File("out/lemmas.txt").text = lemmas.toSorted { e -> - e.getValue() }.collect { k,v -> "$k $v" }.join("\n")
        
        writeDisambigStats(coll)
    }
     
    @CompileStatic
    void writeDisambigStats(java.text.Collator coll) {
    //    def outFileFreqFull = new File("lemma_freqs_full.txt")
    //    outFileFreqFull.text = ""
        def dir = Boolean.getBoolean("full") ? "out/full/" : "out/"
        def outFileFreqHom = new File(dir, "lemma_freqs_hom.txt")
        outFileFreqHom.text = "# version: $statsVersion\n"
    
        //println "Ignored for stats:\n\t${ignored.toSorted().join("\n\t")}"
        println "Ignored for stats: ${ignored.size()}"
        
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
                            if( ContextToken.useRightContext(token) || wordContext.getOffset() == -1 ) {
                                def rateCtx = value / tokenTotalRate
                                outFileFreqHom << "\t" << wordContext.toString() << "\t\t" << rateCtx << "\n"
                            }
                        }
                    }
                }
            }

        def outFileLemmaSuffixFreqHom = new File("out/lemma_suffix_freqs.txt")
        outFileLemmaSuffixFreqHom.text = ""

        lemmaSuffixStatsF
            .toSorted { a, b -> coll.compare a.getKey(), b.getKey() }
            .each { String token, Map<WordReading, Integer> map1 ->
                map1
                .toSorted{ a, b -> b.getKey().getPostag().compareTo(a.getKey().getPostag()) }
                .each { WordReading wordReading, Integer rate ->
                    outFileLemmaSuffixFreqHom << token << "\t\t" << wordReading << "\t" << rate << "\n"
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
