#!/bin/env groovy

import static groovy.io.FileType.FILES

import groovy.transform.Field
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.NodeChild

@Field
boolean toTagged = false
@Field
GPathResult prevChild = null
@Field
int sentIdx

File txt2Folder = new File("txt2")
txt2Folder.mkdirs()

new File("xml").eachFileRecurse(FILES) { File file->
//    if( ! file.name.startsWith("A_") )
//        return

    println "File: ${file.name}"
    
    File txtFile = new File(txt2Folder, file.name.replaceFirst(/.xml/, '.txt'))
    txtFile.text = ''

    String inText = file.text

    if( ! toTagged ) {
        String name = txtFile.getName().replaceFirst(/_dis\.txt/, '.txt')
        def orig = new File("good/$name").text
        if( orig.contains("\u2019") ) {
//            println "apo: 2019"
            String apo = "\u2019"
            inText = inText.replace("'", apo)
        } 
        else if (orig.contains("\u02bc") ) {
//            println "apo: 02bc"
            String apo = "\u02bc"
            inText = inText.replace("'", apo)
        }
    }

    GPathResult xml = new groovy.xml.XmlSlurper().parseText(inText)
    
//    println "header: " + xml.meta.'*'.size()


    xml.meta.'*'.eachWithIndex { GPathResult it, int idx ->
        printNode(txtFile, it, idx)
    }
    txtFile << "<body>\n"
    prevChild = xml['body']
    sentIdx = 0
    xml['body'].'*'.eachWithIndex { it, idx -> 
        processItem(txtFile, it, idx)
    } 
    txtFile << "\n</body>\n"
}


private void processItem(File txtFile, GPathResult xml, int childIdx) {
    if( xml.name() == "sentence" ) {
        if( toTagged ) {
            txtFile << "<S>\n"
        }
        else {
            if( sentIdx > 0 && ! (prevChild.name() == "format") ) {
                txtFile << " "
            }
            childIdx = 0
        }
        sentIdx++
        xml.'*'.eachWithIndex { it, idx2 ->
            processItem(txtFile, it, idx2)
        }
        if( toTagged ) {
            txtFile << "</S>\n"
        }
    }
    else if( xml.childNodes() ) {
        String xmlName = xml.name()
        
        if( toTagged ) {
            txtFile << "<$xmlName>\n"
        }
        else {
            txtFile << "<$xmlName>"
        }
        xml.'*'.eachWithIndex { it, idx2 -> 
            processItem(txtFile, it, idx2)
        }
        if( toTagged ) {
            txtFile << "</$xmlName>\n"
        }
        else {
            txtFile << "</$xmlName>"
        }
    }
    else {
        String xmlName = xml.name()

        if( xmlName.contains("format") ) {
            xmlName = xml.@tag
            if( toTagged ) {
                txtFile << "<$xmlName>\n"
            }
            else {
                txtFile << "<$xmlName>"
            }
            prevChild = xml
            return
        }
        printNode(txtFile, xml, childIdx)
    } 
}

//@CompileStatic
private void printNode(File txtFile, GPathResult xml, int childIdx) {
    if( xml.text() || xml.parent().name()=="meta" ) {
        txtFile << "<${xml.name()}>${xml.text()}</${xml.name()}>\n"
    }
    else if( xml.name() == "token" ) {
//        String attrs = ((NodeChild)xml).attributes().collect { k,v -> "$k=\"$v\"" }.join(" ")
//        if( attrs ) attrs = " " + attrs
//        txtFile << "<${xml.name()}$attrs/>\n"
        if( toTagged ) {
            txtFile << xml.@value << "[" << xml.@lemma << "/" << xml.@tags << "]\n"
        }
        else {
            if( childIdx > 0 && ! (xml.@value ==~ /[.!?,»):;…]/)  // (\.,»…\)\]|[.!?]{3})/) 
                    && prevChild != null && ! (prevChild.@value ==~ /[«(]/) 
                    && ! (prevChild.name() == "format") ) {
                txtFile << " "
            } 
            txtFile << xml.@value
        }
    }
    else {
        String attrs = ((NodeChild)xml).attributes().collect { k,v -> "$k=\"$v\"" }.join(" ")
        if( attrs ) attrs = " " + attrs
        if( toTagged || xml.name() != "paragraph" ) {
            txtFile << "<${xml.name()}$attrs/>\n"
        }
        else {
            if( xml.name() == "paragraph" ) {
               txtFile << "\n" 
               prevChild = null
               sentIdx = 0
            }
            else {
                txtFile << "<${xml.name()}$attrs/>"
            }
        }
    }
    prevChild = xml
}