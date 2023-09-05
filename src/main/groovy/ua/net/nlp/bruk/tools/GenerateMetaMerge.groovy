#!/bin/env groovy

package ua.net.nlp.bruk.tools

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

headers = origHeaders + newHeaders

println "headers: $headers"

def csvFormat = CSVFormat.Builder.create()
    .setHeader()
    .setSkipHeaderRecord(true)
    .setIgnoreSurroundingSpaces(true)
    .setAllowMissingColumnNames(true)
    .build();

CSVParser metaParser = csvFormat.parse(new FileReader("../corpus/meta/meta.csv"))
List<CSVRecord> records = []
for(CSVRecord record: metaParser) {
     records << record
}

Map<String, Map<String, String>> newColumns = [:].withDefault{ [:] }

def dir = "tmp/good"
File txtFolder = new File(dir)

def files = txtFolder.listFiles().findAll { it.name.endsWith('.txt') }.sort{ it.name }

files.each { File file->

//    println "File: ${file.name}"
    
    File txtFile = new File(txtFolder, file.name)

    String metaXml = file.text.replaceAll(/(?s)(.*?)<body>.*/, '<text><meta>$1</meta></text>')
    metaXml = metaXml.replace('&', '&amp;')

    GPathResult xml = new groovy.xml.XmlSlurper().parseText(metaXml)
    
    def metaItems = xml.children().getAt(0).children().collect { GPathResult it -> it.name() }
    
    metaItems -=  origHeaders
    metaItems -= "id"
    
    def extraRecords = [:]
    
    if( metaItems ) {
        
        if( "author_name_1" in metaItems ) {
//        println "Unknown meta items for ${file.name}: $metaItems"
//            println "${file.name}: ${metaItems['author_surname_1']} ${metaItems['author_name_1']}"
            
             CSVRecord rec = records.find { it.get('file') == file.name }
             assert rec

             String filename = rec.get('file')
             
             newHeaders.each { hdr -> 
                 newColumns[filename][hdr] = xml.meta[hdr]
             }
             
    //          println "Unknown meta items for ${file.name}: ${xml.meta['author_name_1']}"
             println "Extra records: $filename: ${newColumns[filename].size()}"
        }
        
//        println "Unknown meta items for ${file.name}: $metaItems"
//        System.exit(1)
    }
    
}


CSVPrinter printer = new CSVPrinter(new FileWriter("meta2.csv"), CSVFormat.EXCEL)
printer.printRecord(headers);

records.each { record ->
    record.with {
        def values = [dir.replaceFirst(/.*\//, ''), cat, file, author_surname, author_name, title, publ_in, url, publ_part, publ_place, publisher, year, pages, length, alt_orth, errors, comments]
        if( newColumns[file] ) {
            values += newColumns[file].values()
        }
        printer.printRecord(values)
    }
}


printer.flush()
