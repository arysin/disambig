package org.nlp_uk.bruk

import java.util.regex.Pattern

import org.languagetool.AnalyzedSentence
import org.languagetool.AnalyzedToken
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.JLanguageTool
import org.languagetool.rules.RuleMatch
import org.languagetool.rules.uk.TokenAgreementAdjNounRule
import org.languagetool.rules.uk.TokenAgreementNounVerbRule
import org.languagetool.rules.uk.TokenAgreementPrepNounRule
import org.languagetool.tagging.uk.UkrainianTagger

import groovy.transform.CompileStatic
import groovy.xml.slurpersupport.Node

class Validator {
    static final Pattern WORD_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґa-z0-9].*/)
    @Lazy
    UkrainianTagger ukTagger = { new UkrainianTagger() }()
    Set<String> allTags
    ResourceBundle messages = JLanguageTool.getDataBroker().getResourceBundle(JLanguageTool.MESSAGE_BUNDLE, new Locale("uk"))
    List<org.languagetool.rules.Rule> validationRules = [new TokenAgreementNounVerbRule(messages),
        new TokenAgreementAdjNounRule(messages),
        new TokenAgreementPrepNounRule(messages)]
    Stats stats
    
    
    Map<String, List<String>> errValidations = [:].withDefault{ [] }
    List<String> errUnverified = []
    

    Validator(Stats stats) {
        this.stats = stats
        allTags = getClass().getResource('/ukrainian_tags.txt').readLines() as Set
        allTags += [ 'punct', 'number', 'number:latin', 'time', 'date', 'unclass', 'unknown', 'symb', 'hashtag' ]
    
    }
    
    @CompileStatic
    boolean validateToken(String token, String lemma, String tags, File txtFile) {
        if( ! (tags in allTags) && ! (tags.replaceAll(/:(alt|bad|short)/, '') in allTags) ) {
            if( ! ( tags =~ /noninfl(:foreign)?:prop|noun:anim:p:v_zna:var|noun:anim:[mf]:v_...:nv(:abbr:prop:[fp]name|:prop:[fp]name:abbr)/ ) ) {
                println "\tInvalid tag: $tags for $token"
                errValidations[txtFile.name.replace('.txt', '.xml')] << "Invalid tag: $tags for $token".toString()
                return false
            }
        }
    
        return true
    //    if( ! VALID_TAG.matcher(tags).matches() ) {
    //        System.err.println "Invalid tag: $tags"
    //        return
    //    }
    
    }
    
    @CompileStatic
    void validateToken2(String token, String lemma, String tags, File txtFile) {
        if( WORD_LEMMA.matcher(lemma).matches() ) {
            stats.wordCount++
            if( tags == "null" ) {
                System.err.println "null tag for $token"
            }
            else if( ! tags.startsWith("noninfl") && ! tags.startsWith("unclass") ) { //! (token =~ /^[А-ЯІЇЄҐA-Z].*/ ) ){
                AnalyzedTokenReadings ltTags = ukTagger.tag([token])[0]
                
                if( ltTags.getReadings().size() > 1 ) {
                    stats.homonymTokens << token
                }
                
                def ltTags2 = ltTags.getReadings().collect { AnalyzedToken t -> t.getLemma() + "/" + t.getPOSTag() }
                
                String tagPair = "$lemma/$tags"
                if( tags.startsWith("noninfl:foreign") || tags.startsWith("unclass") ) {
                }
                if( ltTags2[0].startsWith("null") && token.matches("[А-ЯІЇЄҐ][а-яіїєґА-ЯІЇЄҐ'-]+") && tags.matches(/noun:anim:.:v_...(:nv)?:prop:lname/) ) {
                }
                else {
                    boolean initials = token ==~ /[А-ЯІЇЄҐ][а-яіїєґ]?\./ && tagPair ==~ /[А-ЯІЇЄҐ][а-яіїєґ]?\.\/noun:anim:[mf]:v_...:nv(:abbr:prop:[fp]name|:prop:[fp]name:abbr)/
                    if( ! (tagPair in ltTags2) && ! initials ) {
                        if( token != "їх" || ! (tags ==~ /adj:[mfnp]:v_...(:r(in)?anim)?:nv:&pron:pos:bad/ ) ) {
                            
                            errUnverified << "$tagPair (token: $token) (avail: $ltTags2)".toString()
                    //                            println "Unverified tag: $tagPair (token: $token) (avail: $ltTags2)"
                        }
                        return
                    }
                }
            }
        }
    
    }
    
    @CompileStatic
    void validateSentence(List<Node> xmls, File txtFile) {
    
        int pos = 0
        def readings = xmls.collect { Node xml ->
            def attributes = xml.attributes()
            String tags = attributes['tags']
            String lemma = attributes['lemma']
            String token = attributes['value']
            def tokens = Arrays.asList(new AnalyzedToken(token, tags, lemma))
            def atr = new AnalyzedTokenReadings(tokens, pos)
            pos += token.length() + 1
            atr
        }
        AnalyzedSentence sent = new AnalyzedSentence(readings.toArray(new AnalyzedTokenReadings[0]))
        
        validationRules.each { rule ->
            RuleMatch[] matches = rule.match(sent)
            matches.each {
                def sample = xmls.collect{it.attributes()['value']}.join(' ')
                sample = sample[it.fromPos..-1]
                println "\trule violation: $it\n\t$sample"
                errValidations[txtFile.name.replace('.txt', '.xml')] << "$it\n\t\t$sample".toString()
            }
        }
    }

    void writeErrors() {
        println "${errUnverified.size()} tokens with unverified tags !!"

        java.text.Collator coll = java.text.Collator.getInstance(new Locale("uk", "UA"));
        coll.setStrength(java.text.Collator.IDENTICAL)
        coll.setDecomposition(java.text.Collator.NO_DECOMPOSITION)

        // write warnings
        
        new File("err_unverified.txt").text = errUnverified.collect{ def s = it.toString()
            s =~ /^[^а-яіїєґ]/ ? "* $s".toString() : s.toString()
        }
            .toSorted(coll)
            .collect{ it.replaceFirst(/^\* /, '') }
            .join("\n")

        new File("err_validations.txt").text = errValidations.collect { k,v -> "$k\n\t" + v.join("\n\t") }.join("\n")

    }    
}
