package org.nlp_uk.bruk

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
class WordReading {
    String lemma
    String postag
    
    String toString() {
        "$lemma, $postag"
    }
}
