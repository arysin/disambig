#!/bin/env groovy

package org.nlp_uk.bruk

//@Grab(group='org.languagetool', module='language-uk', version='5.7-SNAPSHOT')
//@Grab(group='org.apache.commons', module='commons-csv', version='1.9.0')

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.Node
import groovy.xml.slurpersupport.Attribute

import java.util.regex.Pattern

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.languagetool.AnalyzedToken
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.tagging.uk.UkrainianTagger
import org.languagetool.rules.uk.*
import org.languagetool.rules.RuleMatch
import org.languagetool.AnalyzedSentence
import org.languagetool.JLanguageTool


@Field
Stats stats = new Stats()
@Field
Validator validator = new Validator(stats)
@Field
boolean toTagged = false
@Field
Node prevChild = null
@Field
int sentIdx
@Field
boolean produceTxt = true


//void main2() {
    
    File txt2Folder = new File("good_gen")
    txt2Folder.mkdirs()
    
    def files = new File("xml").listFiles().sort{ it.name }
    files.each { File file->
        if( ! file.name.endsWith('.xml') ) {
            System.err.println "Unknown file: ${file.name}"
            return
        }
            
        println "File: ${file.name}"
        
        File txtFile = new File(txt2Folder, file.name.replaceFirst(/.xml/, '.txt'))
        txtFile.text = ''
    
        String inText = file.text
    
        String origName = txtFile.getName() //.replaceFirst(/_dis\.txt/, '.txt')
        assert new File("good/$origName").isFile()
    
        GPathResult xml = new groovy.xml.XmlSlurper().parseText(inText)
        
        Iterator<Node> childNodes = xml.childNodes()
//        println ":: " + childNodes.next().class
//        prevChild = childNodes[0]
        sentIdx = 0
        childNodes.eachWithIndex { Node it, int idx ->
            if( idx == 0 ) prevChild = it
            processItem(txtFile, it, idx)
        }
        txtFile << "\n"
    }
    
    validator.writeErrors()
    stats.writeStats()
//} 

//new ReadXml().main2()

// end



@CompileStatic
static boolean needsSpace(Node prevChild) {
    return prevChild != null && \
        (prevChild.name() != "format" || ((String)prevChild.attributes()['tag']).startsWith("/")) \
            && ! (((String)prevChild.attributes()['value']) =~ /[°\/]/)
}


@CompileStatic
private void processItem(File txtFile, Node xml, int childIdx) {
    Iterator<Node> childNodes = xml.childNodes()
    
    if( xml.name() == "sentence" ) {
        if( sentIdx > 0 && needsSpace(prevChild) ) {
            txtFile << " "
        }
        childIdx = 0
        sentIdx++

        List<Node> tokenXmls = new ArrayList<>()
        
        childNodes.eachWithIndex { Node it, int idx2 ->
            processItem(txtFile, it, idx2)
            if( it.name() == "token" ) {
                tokenXmls << it
            }
        }

        validator.validateSentence(tokenXmls, txtFile)
        
        stats.generateStats(tokenXmls, txtFile)
    }
    else if( childNodes
            && ! ((Node)childNodes[0]).name() == "alts" ) {
        String xmlName = xml.name()
        
        txtFile << "<$xmlName>"

        childNodes.eachWithIndex { Node it, int idx2 -> 
            processItem(txtFile, it, idx2)
        }

        txtFile << "</$xmlName>"
    }
    else {
        String xmlName = xml.name()

        if( xmlName.contains("format") ) {
            xmlName = xml.attributes()['tag']
            if( ((Node)xml).attributes()['tag'] == 'br' ) {
                txtFile << "\n"
                prevChild = null
                childIdx = 0
                return
            }

            if( childIdx > 0 
                    && prevChild != null 
                    && prevChild.name() != 'format'
                    && ! (prevChild.attributes()['value'] ==~ /[«\u201C(\[]/) 
                    && ! ((String)xml.attributes()['tag']).startsWith("/") ) {
                txtFile << " "
            }
            
            txtFile << "<$xmlName>"
            prevChild = xml
            return
        }
        printNode(txtFile, xml, childIdx)
    } 
}

@Field
static final Pattern PUNCT_PATTERN = Pattern.compile(/[.!?,»\u201D)\]:;…]|[.!?]{2,3}/) // (\.,»…\)\]|[.!?]{3})/)

@CompileStatic
private void printNode(File txtFile, Node node, int childIdx) {
    if( node.text() || node.parent().name()=="meta" ) {
        txtFile << "<${node.name()}>${node.text()}</${node.name()}>\n"
    }
    else if( node.name() == "token" ) {
        if( produceTxt ) {
            if( childIdx > 0 
                    && ! PUNCT_PATTERN.matcher(node.attributes()['value'].toString()).matches()
                    && prevChild != null 
                    && ! (prevChild.attributes()['value'] ==~ /[«\u201C\/(\[]/) 
                    && needsSpace(prevChild) ) {
                if( childIdx == 1 && prevChild.attributes()['value'] ==~ /…|\.{3}/ ) {
                    
                }
                else {
                    txtFile << " "
                }
            } 
            txtFile << node.attributes()['value']
        }
        
        validateToken(node, txtFile)
    }
    else {
        String attrs = ((Node)node).attributes().collect { k,v -> "$k=\"$v\"" }.join(" ")
        if( attrs ) attrs = " " + attrs

        if( node.name() == "paragraph" ) {
           txtFile << "\n\n" 
           prevChild = null
           childIdx = 0
           sentIdx = 0
        }
        else {
            txtFile << "<${node.name()}$attrs/>"
        }
    }
    prevChild = node
}



@CompileStatic
void validateToken(Node xml, File txtFile) {
    def attributes = xml.attributes()
    String tags = attributes['tags']
    String lemma = attributes['lemma']
    String token = attributes['value']

    if( tags in ['xmltag'] )
        return

    if( validator.validateToken(token, lemma, tags, txtFile) ) {    
        stats.addToStats(token, lemma, tags)
    }

    validator.validateToken2(token, lemma, tags, txtFile)
}

