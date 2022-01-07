package org.nlp_uk.bruk

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
class ContextToken {
    String word
    String lemma
//    String postagKey
    String postag
    
    @CompileStatic
    ContextToken(String word, String lemma, String postag) {
        this.word = word
        this.lemma = lemma
//        this.postagKey = getPostagKey(postag)
        this.postag = getPostagCore(postag)
    }
    
    @CompileStatic
    static ContextToken normalized(String word, String lemma, String postag) {
        new ContextToken(normalizeContextString(word),
            normalizeContextString(lemma),
            postag)
    }
        
    @CompileStatic
    String toString() {
        def w = safeguard(word)
        def l = safeguard(lemma)
        "$w\t$l\t$postag"
    }
    
    static final Pattern POSTAG_KEY_PATTERN = Pattern.compile("^(noun:(anim|[iu]nanim)|verb(:rev)?:(perf|imperf)|adj|adv(p:(imperf:perf))?|part|prep|numr|conj:(coord|subord)|intj|onomat|punct|symb|noninfl|unclass|number|unknown|time|date|hashtag|BEG|END)")
    static final Pattern POSTAG_CORE_KEY_PATTERN = Pattern.compile(/:(rare|arch|coll|slang|bad|subst)/)

    @CompileStatic
    static String getPostagCore(String postag) {
        // short/long
        POSTAG_CORE_KEY_PATTERN.matcher(postag).replaceAll('')
    }

    @CompileStatic
    static String safeguard(String w) {
        if( w == '' ) return '^'
        w.indexOf(' ') >= 0 ? w.replace(' ', '\u2009') : w
    }

    @CompileStatic
    static String unsafeguard(String w) {
        if( w == '^' ) return ''
        w = w.indexOf('\u2009') >= 0 ? w.replace('\u2009', ' ') : w
    }

    @CompileStatic
    static String normalizeContextString(String w) {
        def m0 = Pattern.compile(/[12][0-9]{3}/).matcher(w) // preserve a year - often works as adj
        if( m0.matches() )
            return w

        def m1 = Pattern.compile(/[0-9]+([0-9]{2})/).matcher(w) // we only care about last two digits
        if( m1.matches() )
            return m1.replaceFirst('$1')

        def m2 = Pattern.compile(/[0-9]+([,.])[0-9]+/).matcher(w) // we only care that it's decimal
        if( m2.matches() )
            return m2.replaceFirst('0$10')
    
        if( w.length() == 3 )
            return w.replaceFirst(/^\.\.\.$/, '…')

        if( w.length() == 1 )            
            return w.replaceAll(/^[\u2013\u2014]$/, '-')
                .replace('„', '«')
                .replace('“', '»')
           
        if( w.indexOf(".") > 0 )
            return w.replaceAll(/^([?!.])\.+/, '$1')

        return w
    }
}
