package ua.net.nlp.bruk

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled

import groovy.xml.slurpersupport.GPathResult
import ua.net.nlp.bruk.tools.Validator

import org.junit.jupiter.api.Test;


class ValidatorTest {
    def validator = new Validator()
    
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
    void test1() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        validator.validateSentence(nodes, new File("1.txt"))
        
        def map = [:]
        assertEquals map, validator.errValidations
    }

    String str2=
    """
<text>
  <token value="при" lemma="при" tags="prep" />
  <token value="його" lemma="його" tags="adj:m:v_naz:&amp;pron:pos" />
  <token value="батьки" lemma="батьки" tags="noun:anim:p:v_naz:ns" />
</text>
"""

String str3=
"""
<text>
  <token value="при" lemma="при" tags="prep" />
  <token value="його" lemma="він" tags="noun:unanim:m:v_rod:&amp;pron:pers:3" />
  <token value="батьки" lemma="батьки" tags="noun:anim:p:v_naz:ns" />
</text>
"""

        @Test
        void test2() {
            GPathResult xml = new groovy.xml.XmlSlurper().parseText(str2)
            List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
            
            validator.validateSentence(nodes, new File("1.txt"))
            
//            def map = ['x':['y']]
//            println ":: ${validator.errValidations}"
            assertEquals 1, validator.errValidations.size()
            
            xml = new groovy.xml.XmlSlurper().parseText(str3)
            nodes = xml.childNodes().collect { it }
            
            validator.validateSentence(nodes, new File("1.txt"))
            
            assertEquals 1, validator.errValidations.size()

        }

String str4 =
"""
<text>
  <token value="таке" lemma="такий" tags="adj:n:v_zna:&amp;pron:dem" />
  <token value="об'єднання" lemma="об'єднання" tags="noun:inanim:p:v_naz" />
</text>
"""

    @Test
    void test4() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str4)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        validator.validateSentence(nodes, new File("1.txt"))
        
        assertEquals 1, validator.errValidations.size()
    }
    
    String str5 =
    """
<text>
  <token value="двох" lemma="двоє" tags="numr:p:v_zna" />
  <token value="тренерів" lemma="тренер" tags="noun:anim:p:v_rod" />
</text>
"""
    
    @Test
    void test5() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str5)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        validator.validateSentence(nodes, new File("1.txt"))
        
        assertEquals 0, validator.errValidations.size()
    }
    
    String str6 =
"""
<text>
  <token value="обидві" lemma="обидва" tags="numr:p:v_zna" />
  <token value="ноги" lemma="нога" tags="noun:inanim:p:v_naz" />
</text>
"""
    
    @Test
    void test6() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str6)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        validator.validateSentence(nodes, new File("1.txt"))
        
        assertEquals 0, validator.errValidations.size()
    }

    String str71 =
    """
<text>
  <token value="позбавлені" lemma="позбавлений" tags="adj:p:v_naz:&amp;adjp:pasv:perf" />
  <token value="батьківських" lemma="батьківський" tags="adj:p:v_rod" />
</text>
"""

    @Test
    void test71AdjAdj() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str71)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        validator.validateSentence(nodes, new File("1.txt"))
        
        assertEquals 0, validator.errValidations.size()
    }
    
    String str72 =
"""
<text>
  <token value="інші" lemma="інший" tags="adj:p:v_zna:rinanim:&amp;pron:def" />
  <token value="гірші" lemma="гірший" tags="adj:p:v_naz:compc" />
</text>
"""

    @Test
    void test72AdjAdj() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str72)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        validator.validateSentence(nodes, new File("1.txt"))
        
        assertEquals 1, validator.errValidations.size()
    }
    
    String str73 =
"""
<text>
    <token value="до" lemma="до" tags="prep" />
    <token value="порядку" lemma="порядок" tags="noun:inanim:m:v_dav" />
    <token value="денного" lemma="денний" tags="adj:m:v_rod" />
    <token value="нинішньої" lemma="нинішній" tags="adj:f:v_rod" />
</text>
"""

    @Test
    void test73AdjAdj() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str73)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        validator.validateSentence(nodes, new File("1.txt"))
        
        assertEquals 1, validator.errValidations.size()
    }

    String str8 =
"""
<text>
  <token value="це" lemma="це" tags="noun:inanim:n:v_naz:&amp;pron:dem" />
  <token value="була" lemma="бути" tags="verb:imperf:past:f" />
</text>
"""
    
    @Test
    void test8() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str8)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        validator.validateSentence(nodes, new File("1.txt"))
        
        assertEquals 1, validator.errValidations.size(), validator.errValidations.toString()
    }
    
    String str9 =
"""
<text>
  <token value="їв" lemma="їсти" tags="verb:imperf:past:m" />
  <token value="таки" lemma="таки" tags="part" />
  <token value="танку" lemma="танк" tags="noun:inanim:m:v_dav" />
</text>
"""
    
    @Test
    void test9() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str9)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        validator.validateSentence(nodes, new File("1.txt"))
        println ":: ${validator.errValidations}"
        assertEquals 1, validator.errValidations.size(), validator.errValidations.toString()
    }

}
