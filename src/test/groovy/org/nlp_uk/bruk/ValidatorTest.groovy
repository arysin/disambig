package org.nlp_uk.bruk;

import static org.junit.jupiter.api.Assertions.*;

import groovy.xml.slurpersupport.GPathResult
import org.junit.jupiter.api.Test;


class ValidatorTest {

    String str=
"""
<text>
    <token value="Прескаленна" lemma="прескаленний" tags="adj:f:v_naz" />
    <token value="та" lemma="та" tags="conj:coord" />
    <token value="парастернальна" lemma="парастернальний" tags="adj:f:v_naz" />
    <token value="біопсії" lemma="біопсія" tags="noun:inanim:p:v_naz" />
    <token value="є" lemma="бути" tags="verb:imperf:pres:p:3" />
</text>
"""
    
    @Test
    void test() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        def validator = new Validator(new Stats())
        validator.validateSentence(nodes, new File("1.txt"))
        
        def map = [:]
        assertEquals map, validator.errValidations
    }

}
