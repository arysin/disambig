package org.nlp_uk.bruk

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
class ContextToken {
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
        def w = word.indexOf(',') >= 0 ? word.replace(',', '^') : word
        def l = lemma.indexOf(',') >= 0 ? lemma.replace(',', '^') : lemma
        "$w, $l, $postag"
    }
    
    static final Pattern POSTAG_KEY_PATTERN = Pattern.compile("^(noun:(anim|[iu]nanim)|verb(:rev)?:(perf|imperf)|adj|adv(p:(imperf:perf))?|part|prep|numr|conj:(coord|subord)|intj|onomat|punct|symb|noninfl|unclass|number|unknown|time|date|hashtag)")
    
    static String getPostagKey(String postag) {
        Matcher match = POSTAG_KEY_PATTERN.matcher(postag)
        assert match, "postag: $postag"
        match.group(0)
    }
    static String getPostagCore(String postag) {
        // short/long
        postag.replaceAll(/:rare|:arch|:coll|:slang|:bad|:subst/, '')
    }
    
}
