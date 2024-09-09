#!/bin/env groovy

package ua.net.nlp.bruk.tools

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
import org.apache.commons.lang3.StringUtils
import org.languagetool.AnalyzedToken
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.tagging.uk.UkrainianTagger
import org.languagetool.rules.uk.*
import org.languagetool.rules.RuleMatch
import org.languagetool.AnalyzedSentence
import org.languagetool.JLanguageTool

import ua.net.nlp.bruk.ContextToken
import ua.net.nlp.bruk.tools.Stats
import ua.net.nlp.bruk.tools.Validator
import ua.net.nlp.tools.tag.TagTextCore


@Field
Stats stats = new Stats()
@Field
Validator validator = new Validator()
@Field
ExecutorService executor = Executors.newWorkStealingPool()
@Field
static final String DATA_DIR = "../corpus/data/disambig"
@Field
int txtWordCount = 0
    

TagTextCore.printLtVersion()

def files = new File(DATA_DIR).listFiles().sort{ it.name }
files.each { File file->
    if( ! file.name.endsWith('.xml') ) {
        System.err.println "Unknown file: ${file.name}"
        return
    }

    //println "File: ${file.name}"

    String origName = file.name.replaceFirst(/\.xml$/, '.txt')

    def sosoFile = new File("../corpus/data/so-so/$origName")
    def goodFile = new File("../corpus/data/good/$origName")
    assert sosoFile.isFile() || goodFile.isFile()

//    File txtFile = sosoFile.isFile() ? sosoFile : goodFile  
//    
//    int xmlCnt = StringUtils.countMatches(file.text.replaceAll(/.*? value="(.*?)" .*/, '$1'), '\u201C')
//    int txtCnt = StringUtils.countMatches(txtFile.text, '\u201C')
//    
//    assert xmlCnt == txtCnt, "U+201C does not match in ${file.name}"
    
    executor.submit{
        processFile(file, origName)
    }
}

println "Submitted ${files.size()} files"

executor.shutdown()
executor.awaitTermination(30, TimeUnit.SECONDS)

validator.writeErrors()
long tm1 = System.currentTimeMillis()
stats.writeStats()
long tm2 = System.currentTimeMillis()
println "write stats took ${tm2-tm1}ms"

println "txt words: ${txtWordCount}"

Thread.sleep(500)
exe("/bin/bash -e out/z_diff.sh")

// end main

    
@CompileStatic
private void processFile(File file, String origName) {
    Stats localStats = new Stats(validator: validator)
    
    def text = new File("../corpus/data/so-so/$origName").isFile() ? new File("../corpus/data/so-so/$origName").text : new File("../corpus/data/good/$origName").text
    def text0 = text.replaceAll('СхідSide|ГолосUA|Фirtka|ОsтаNNя|sovieticus’а|Iн-Iв-IIв-IIн-IVн-IVв-IIIв-IIIн-Vн-Vв', 'ААА')
    def words = text0 =~ /(?ui)[а-яіїєґ][а-яіїєґa-z0-9\u0301'’ʼ\/\u2013-]*/
//    txtWordCount += words.size()
    int count = 0;
    while (words.find()) {
        count++
    }
    txtWordCount += count
    
    String inText = file.text
    
    GPathResult xml = new groovy.xml.XmlSlurper().parseText(inText)
    
    Iterator<Node> childNodes = xml.childNodes()
    childNodes.each { Node node ->
        processItem(file, node, localStats)
    }

//    println "adding local stats for ${file.name}"
    try {
        stats.add(localStats)
    }
    catch(e) {
        e.printStackTrace()
        System.exit(1)
    }
}

@CompileStatic
private void processItem(File file, Node xml, Stats stats) {
    Iterator<Node> childNodes = xml.childNodes()
    
    if( xml.name() == "sentence" ) {
        List<Node> tokenXmls = new ArrayList<>()
        
        childNodes.each { Node it ->
            processItem(file, it, stats)
            if( it.name() == "token" ) {
                tokenXmls << it
            }
        }

        validator.validateSentence(tokenXmls, file)
        stats.generateStats(tokenXmls, file)
    }
    else if( childNodes
            && ! ((Node)childNodes[0]).name() == "alts" ) {
        String xmlName = xml.name()
        
        childNodes.each { Node it -> 
            processItem(file, it, stats)
        }
    }
    else {
        String xmlName = xml.name()
        if( xmlName == "token" ) {
            validateToken(xml, file, stats)
        }
    }
    
//   txtGenerator.printNode(txtFile, xml, childIdx)
}


@Field
ThreadLocal<ContextToken> prevToken = 
    new ThreadLocal<ContextToken>() {
        public ContextToken initialValue() {
          return new ContextToken('', '', '')
        }
      }
  
@CompileStatic
void validateToken(Node xml, File file, Stats stats) {
    def attributes = xml.attributes()
    String tags = attributes['tags']
    String lemma = attributes['lemma']
    String token = attributes['value']

    if( tags in ['xmltag'] )
        return

    if( validator.validateToken(token, lemma, tags, file) ) {    
        stats.addToStats(token, lemma, tags, file)
    }

    validator.validateToken2(token, lemma, tags, file, prevToken.get(), stats)
    
    prevToken.set(new ContextToken(token, lemma, tags))
}


def exe(cmd) {
    def sout = new StringBuilder(), serr = new StringBuilder()
    def proc = cmd.execute()
    proc.waitForProcessOutput(sout, serr)
//    proc.consumeProcessOutput(sout, serr)
//    proc.waitForOrKill(2000)
    println "out> $sout"
    System.err.println "err> $serr"
}
