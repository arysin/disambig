#!/bin/env groovy

package ua.net.nlp.bruk

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.Node
import groovy.xml.slurpersupport.Attribute

import java.util.regex.Pattern
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.languagetool.AnalyzedToken
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.tagging.uk.UkrainianTagger
import org.languagetool.rules.uk.*
import org.languagetool.rules.RuleMatch
import org.languagetool.AnalyzedSentence
import org.languagetool.JLanguageTool

import ua.net.nlp.bruk.ContextToken
import ua.net.nlp.bruk.Stats
import ua.net.nlp.bruk.Validator


@Field
Stats stats = new Stats()
@Field
Validator validator = new Validator(stats)
//@Field
//TxtGenerator txtGenerator

//void main2() {
    @Field
    ExecutorService executor = Executors.newSingleThreadExecutor();
//    ExecutorService executor = Executors.newFixedThreadPool(8)
//    List<Future<GPathResult>> futures = new ArrayList<>(100)   // we need to poll for futures in order to keep the queue busy

    def txtWordCount = 0 
    
    def files = new File("../corpus/data/disambig").listFiles().sort{ it.name }
    files.each { File file->
        if( ! file.name.endsWith('.xml') ) {
            System.err.println "Unknown file: ${file.name}"
            return
        }
            
        println "File: ${file.name}"
        
        File txtFile = new File(file.name.replaceFirst(/.xml/, '.txt'))
    
        String origName = txtFile.getName() //.replaceFirst(/_dis\.txt/, '.txt')
        assert new File("../corpus/data/so-so/$origName").isFile() || new File("../corpus/data/good/$origName").isFile()

        def text = new File("../corpus/data/so-so/$origName").isFile() ? new File("../corpus/data/so-so/$origName").text : new File("../corpus/data/good/$origName").text 
        def text0 = text.replaceAll('СхідSide|ГолосUA|Фirtka|ОsтаNNя|sovieticus’а|Iн-Iв-IIв-IIн-IVн-IVв-IIIв-IIIн-Vн-Vв', 'ААА')
        def words = text0 =~ /(?ui)[а-яіїєґ][а-яіїєґa-z0-9\u0301'’ʼ\/\u2013-]*/
        txtWordCount += words.size()
        
        String inText = file.text
        
//       futures << executor.submit({
            GPathResult xml = new groovy.xml.XmlSlurper().parseText(inText)
//            return [xml, txtFile]
//        } as Callable<List>)
        
//        txtFile << "\n"
//    }

//    println "Got ${futures.size()} futures"
    
//    futures.each { Future xmlF ->
//        def lst = xmlF.get()
//        def (xml, txtFile) = lst
        
        Iterator<Node> childNodes = xml.childNodes()
        childNodes.each { Node it ->
            processItem(txtFile, it)
        }
    }
    
    executor.shutdown()
    executor.awaitTermination(30, TimeUnit.SECONDS)
    
    validator.writeErrors()
    long tm1 = System.currentTimeMillis()
    stats.writeStats()
    long tm2 = System.currentTimeMillis()
    println "stats took ${tm2-tm1}"
    
    println "txt words: ${txtWordCount}"
//} 

//new ReadXml().main2()

// end


@CompileStatic
private void processItem(File txtFile, Node xml) {
    Iterator<Node> childNodes = xml.childNodes()
    
    if( xml.name() == "sentence" ) {
        List<Node> tokenXmls = new ArrayList<>()
        
        childNodes.each { Node it ->
            processItem(txtFile, it)
            if( it.name() == "token" ) {
                tokenXmls << it
            }
        }

        executor.execute {  
            validator.validateSentence(tokenXmls, txtFile)
            stats.generateStats(tokenXmls, txtFile)
        } as Runnable
        
    }
    else if( childNodes
            && ! ((Node)childNodes[0]).name() == "alts" ) {
        String xmlName = xml.name()
        
        childNodes.each { Node it -> 
            processItem(txtFile, it)
        }
    }
    else {
        String xmlName = xml.name()
        if( xmlName == "token" ) {
            validateToken(xml, txtFile)
        }
    }
    
//   txtGenerator.printNode(txtFile, xml, childIdx)
}


@Field
ContextToken prevToken = new ContextToken('', '', '')

@CompileStatic
void validateToken(Node xml, File txtFile) {
    def attributes = xml.attributes()
    String tags = attributes['tags']
    String lemma = attributes['lemma']
    String token = attributes['value']

    if( tags in ['xmltag'] )
        return

    if( validator.validateToken(token, lemma, tags, txtFile) ) {    
        stats.addToStats(token, lemma, tags, txtFile)
    }

    validator.validateToken2(token, lemma, tags, txtFile, prevToken)
    
    prevToken = new ContextToken(token, lemma, tags)
}

