#!/bin/env groovy

package ua.net.nlp.bruk.tools

import groovy.transform.CompileStatic
@Grab(group='org.apache.commons', module='commons-csv', version='1.9.0')

import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.NodeChild

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord


def origHeaders = ["group", "cat", "file", "author_surname", "author_name", "title", "publ_in", "url", "publ_part", "publ_place", "publisher", "year", "pages", "length", "alt_orth", "errors", "comments"]
def newHeaders = []
for(int i=1; i<11; i++) {
    newHeaders << "author_surname_$i"
    newHeaders << "author_name_$i"
}

def headers = origHeaders + newHeaders

def dirName = "so-so"

CSVPrinter printer = new CSVPrinter(new FileWriter("meta_${dirName}.csv"), CSVFormat.EXCEL)
printer.printRecord(headers);

def dir = "../corpus/data/$dirName"
File txtFolder = new File(dir)

def files = txtFolder.listFiles().findAll { it.name.endsWith('.txt') }.sort{ it.name }

files.each { File file->

//    println "File: ${file.name}"
    
    File txtFile = new File(txtFolder, file.name)
    String text = file.getText('utf-8')
    
    if( ! text.trim().endsWith("</body>") ) 
        println "closing </body> missing - ${file.name}"
    
    String metaXml = text.replaceAll(/(?s)(.*?)<body>.*/, '<text><meta>$1</meta></text>')
    metaXml = metaXml.replace('&', '&amp;')

    GPathResult xml = new groovy.xml.XmlSlurper().parseText(metaXml)
    
    def metaItems = xml.children().getAt(0).children().collect { GPathResult it -> it.name() }
    
    metaItems -=  headers
    metaItems -= "id"
    if( metaItems ) {
        println "Unknown meta items for ${file.name}: $metaItems"
    }

    def trueLength = countWords(text)
    
    xml.meta.with {
        def values = [dirName, id, file.name, author_surname, author_name, title, publ_in, url, publ_part, publ_place, publisher, year, pages, trueLength, alt_orth, errors, comments]
        newHeaders.each {
            values << xml.meta[it] 
        }
        printer.printRecord(values)
    }
}

printer.flush()


@CompileStatic
int countWords(String text) {
    def pureText = text.replaceFirst(/.*<body>/, '')
    pureText = pureText.replaceAll(/([0-9])[:,.-]([0-9])/, '$1$2').trim()
    def words = pureText =~ /(?ui)[а-яіїєґ][а-яіїєґa-z0-9\u0301'’ʼ\/\u2013-]*/
    return words.size()
}
