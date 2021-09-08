#!/bin/env groovy


//@GrabConfig(systemClassLoader=true)
@Grab(group='org.languagetool', module='language-uk', version='5.5-SNAPSHOT')

import org.languagetool.JLanguageTool
import org.languagetool.language.*
import org.languagetool.tokenizers.*
import org.languagetool.tokenizers.uk.*

    def language = new Ukrainian() {
        @Override
        protected synchronized List<?> getPatternRules() { return [] }
    }

//    SRXSentenceTokenizer sentTokenizer = new SRXSentenceTokenizer(language)
    UkrainianWordTokenizer wordTokenizer = new UkrainianWordTokenizer()

new File("good").eachFile { file ->
   println file.name

//        List<String> tokenized = sentTokenizer.tokenize(file.text);

//    def text = file.text.replaceFirst(/(?s)<body>(.*)<\/body>/, '$1')
    def text = file.text.replaceFirst(/(?s).*?<body>(.*)<\/body>/, '$1')
    def m = text =~ /(?m)^(.{1,50})/

    def inFile = new File("txt", file.name.replaceFirst(/\.txt$/, '_dis.txt'))
    def outFile = new File("txt3", inFile.name)

//    def m = file.text =~ /(?m)^([А-ЯІЇЄҐа-яіїєґ'\u2019\u02bc]+)/
    m[1..-1].each { 
        def words = wordTokenizer.tokenize(it[1])
        words = words.findAll { ! (it =~ /\h+/) }
        println words.take(3) 
    }
    System.exit 1 

}