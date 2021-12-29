#!/bin/env groovy

package org.nlp_uk.bruk

@Grab(group='org.apache.commons', module='commons-csv', version='1.9.0')

import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.NodeChild

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter


def headers = ["group", "cat", "file", "author_surname", "author_name", "title", "publ_in", "url", "publ_part", "publ_place", "publisher", "year", "pages", "length", "alt_orth", "errors", "comments"]
CSVPrinter printer = new CSVPrinter(new FileWriter("meta.csv"), CSVFormat.EXCEL)
printer.printRecord(headers);
    

def dir = args[0]
File txtFolder = new File(dir)

def files = txtFolder.listFiles().findAll { it.name.endsWith('.txt') }.sort{ it.name }

files.each { File file->

    println "File: ${file.name}"
    
    File txtFile = new File(txtFolder, file.name)

    String metaXml = file.text.replaceAll(/(?s)(.*?)<body>.*/, '<text><meta>$1</meta></text>')
    metaXml = metaXml.replace('&', '&amp;')

    GPathResult xml = new groovy.xml.XmlSlurper().parseText(metaXml)
    
    xml.meta.with {
        printer.printRecord(dir, id, file.name, author_surname, author_name, title, publ_in, url, publ_part, publ_place, publisher, year, pages, length, alt_orth, errors, comments)
    }
}

printer.flush()
