#!/usr/bin/env groovy

package ua.net.nlp.bruk.tools;

int totalCnt = 0
int totalWordCnt = 0
int tokenCnt = 0
int minusCnt = 0
int lemmaCnt = 0
int lemmaPosCnt = 0
int tagCnt = 0
Map<String, Integer> tagChg = [:].withDefault { 0 }
Map<String, Set<Object>> lemmaChg = [:].withDefault { new HashSet<>() }

List<String> benchFiles = new File("ignore_for_stats.txt").readLines().collect{ it.replace('.txt', '') }

def errFile = new File("zDiff.err")
errFile.text = ''

new File("test-data").eachFile { File f ->
    if( ! benchFiles.find { f.name.startsWith(it) } ) {
        return
    }
    
    if( f.name ==~ /[A-I]_.*\.xml/ && ! f.name.endsWith('.tagged.xml') ) {
        totalCnt += f.readLines().count{  it.contains("<token") }
        totalWordCnt += f.readLines().count{  it =~ /(?iu)<token value="[а-яіїєґ]/ }
        return
    }

    if( ! f.name.endsWith('.tagged.diff') ) {
        return
    }
//    println "${f.name}"
    
    int line = 0
    List<String> minuses = []
    def lastPlus = null
    def ctxPrev = ''
    def ctxNext = ''
    
    f.eachLine{ String l ->
        if( lastPlus ) {
            lastPlus['ctxNext'] = l
            lastPlus = null
        }
        
        if( l =~ /^-\s*<token/ ) {
            minuses << l
            minusCnt++
            lastPlus = null
        }
        else if( l =~ /^\+\s*<token/ ) {
            String plus = l
            
            if( minuses ) {
                String minus = minuses.remove(0)
                def m = parse(minus)
                def p = parse(plus)

                def mm = m[1]
                if( m[1] != p[1] ) {
//                    println "$line: ${m[1]} -> ${p[1]}"
//                    println "\t$m\n\t$p"
                    lastPlus = ['minus': m, 'plus': p, 'ctxPrev': ctxPrev]
                    lemmaChg[ "${m[1]} -> ${p[1]}" ] << lastPlus 
                    lemmaCnt++
                    
                    lemmaPosCnt++
                }
                else {
                    if( m[2].split(':')[0] != p[2].split(':')[0] ) {
                        lemmaPosCnt++
                    }
                }
                if( m[2] != p[2] ) {
                    tagChg[ "${m[2]} -> ${p[2]}" ] += 1
                    tagCnt++
                }
            }
            else {
                def p = parse(plus)
                errFile << "\tno - for +: $p (${f.name})\n"
                lastPlus = null
            }
            ctxPrev = ''
        }
        else if( l =~ /^\s+<token/ ) {
//            if( lastPlus ) { 
//                lastPlus['ctxNext'] = l
//                lastPlus = null
//            }  
//            else 
                ctxPrev = l
        }
        else {
            minuses = []
            lastPlus = null
            ctxPrev = ''
        }
        line++
    }
}

println "Total -: $minusCnt (of $totalCnt - ${minusCnt*100/totalCnt}%, words: $totalWordCnt)"
println "Lemmas: $lemmaCnt (${lemmaCnt*100d/minusCnt}%) - (${100 - lemmaCnt*100d/totalCnt}%)"
println "Lemma/POS: $lemmaPosCnt (${lemmaPosCnt*100d/minusCnt}%) - (${100 - lemmaPosCnt*100d/totalCnt}%)"
println "Tags: $tagCnt (${tagCnt*100d/minusCnt}%)"

new File("stats.inc") << "stats[\"err_lemma\"]=$lemmaCnt\n"
new File("stats.inc") << "stats[\"err_lemma_pos\"]=$lemmaPosCnt\n"


new File("zz_diff_tag.txt").text = tagChg.toSorted{ e -> -e.value }
    .collect{ k,v -> "$k : $v" }.join("\n")

println "zna/naz: " + tagChg.findAll { e -> e.key =~ /naz.*zna|zna.*naz/ }.collect { e -> e.value }.sum(0d) 
    
new File("zz_diff_lemma.txt").text = lemmaChg.toSorted{ e -> -e.value.size() }
    .collect{ k,v ->
        def vv = v.collect{ "${it.ctxPrev}\n\t${it.minus}\n\t${it.plus}\n${it.ctxNext}"}.join("\n---\n\t")
        "$k - ${v.size()}\n\t$vv" 
     }.join("\n")

// prepare lemma matrix
     
def columns = [] as LinkedHashSet
Map<String, Map<String, Integer>> lemmaMatrix = [:]
lemmaChg.toSorted{ e -> -e.value.size() }
    .collect { k, v ->
        def (left, right) = k.split(" -> ")
        lemmaMatrix.computeIfAbsent(left, {[:]})[right] = v.size()
        columns << right
    }

def matrixFile = new File("zz_lemma_matrix.csv")
matrixFile.text = ''


columns.each { c ->
    matrixFile << ",$c"
}
matrixFile << "\n"

lemmaMatrix.each { k,v ->
    matrixFile << k
    
    columns.each { c ->
        def val = lemmaMatrix[k][c] ?: "" 
        matrixFile << ",$val"
    }
    
    matrixFile << "\n"
}

    
def parse(String l) {
    def m = l =~ /value="(.+?)" lemma="(.+?)" tags="(.+?)"/
    m.find()
    [m.group(1), m.group(2), m.group(3).replaceAll(/:comp.|:&amp;predic|:(.n)?anim|:&amp:.*?:(im)?perf/,'')]
}
