package org.nlp_uk.bruk

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
class WordContext {
    ContextToken contextToken
    int offset
    
    String toString() {
        def offs = offset > 0 ? "+$offset" : "$offset"
        "$offs, $contextToken"
    }
}
