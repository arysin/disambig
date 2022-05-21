package ua.net.nlp.bruk

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled

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
    void test1() {
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(str)
        List<groovy.xml.slurpersupport.Node> nodes = xml.childNodes().collect { it }
        
        def validator = new Validator(new Stats())
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
            
            def validator = new Validator(new Stats())
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
        
        def validator = new Validator(new Stats())
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
        
        def validator = new Validator(new Stats())
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
        
        def validator = new Validator(new Stats())
        validator.validateSentence(nodes, new File("1.txt"))
        
        assertEquals 1, validator.errValidations.size()
    }

}
