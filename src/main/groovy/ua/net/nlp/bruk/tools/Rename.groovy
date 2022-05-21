#!/bin/env groovy

package org.nlp_uk.bruk.tools

def names = [] as Set

new File('.').listFiles().sort{ f -> f.name }.each { f ->
    def name = f.name
    def newName
    
    if( name =~ /20[0-9][0-9]_([1-9])\.(txt|xml)$/ ) {
        newName = name.replaceFirst(/(20[0-9][0-9])_([1-9])\.(txt|xml)$/, '$2_$1.$3')
    }
//    if( name =~ /\([0-9]\)\.(txt|xml)$/ ) {
//        newName = name.replaceFirst(/\([0-9]\)\.(txt|xml)/, '.$1')
//    }
//    else if( name =~ /\([0-9]\)_20[0-9]{2}\.(txt|xml)$/ ) {
//        newName = name.replaceFirst(/\(([0-9])\)_(20[0-9]{2}\.(txt|xml))$/, '$1_$2')
//    }
    else
        return

    assert ! ( newName in names )
    names << newName
    
//    name = name.replace(' ', '\\ ')
    newName = newName.replace(' ', '_')
    def cmd = ["git", "mv", name, newName]
    println cmd
    def proc = cmd.execute()
    
    def sout = new StringBuilder(), serr = new StringBuilder()
    proc.consumeProcessOutput(sout, serr)
    proc.waitForOrKill(1000)
    if( sout )
        println "out> $sout"
    if( serr ) 
        println "err> $serr"
    
    assert ! proc.exitValue()
}