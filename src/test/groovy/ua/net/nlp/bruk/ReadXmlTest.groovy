package ua.net.nlp.bruk

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled

import groovy.xml.slurpersupport.GPathResult
import org.junit.jupiter.api.Test;


class ReadXmlTest {
    def readXml = new ReadXml()
    
    @Test
    void test1() {
        
        readXml.processFile(new File(ReadXml.DATA_DIR, "A_Ekho_Sitchenko_Lebedyna_virnist_2018.xml"), "A_Ekho_Sitchenko_Lebedyna_virnist_2018.txt")
        
        assertEquals 172, readXml.stats.disambigStats.size()
    }

    @Test
    void testIgnored() {
        
        readXml.processFile(new File(ReadXml.DATA_DIR, "D_Tykholoz_Ridni_Spravzhni_Zhyvi_2017.xml"), "D_Tykholoz_Ridni_Spravzhni_Zhyvi_2017.txt")
        
        assertEquals 0, readXml.stats.disambigStats.size()
        assertEquals 1, readXml.stats.ignored.size()
    }
}
