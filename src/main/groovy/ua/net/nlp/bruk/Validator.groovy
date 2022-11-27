package ua.net.nlp.bruk

import java.util.regex.Matcher
import java.util.regex.Pattern

import org.languagetool.AnalyzedSentence
import org.languagetool.AnalyzedToken
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.JLanguageTool
import org.languagetool.Languages
import org.languagetool.language.Ukrainian
import org.languagetool.rules.Category
import org.languagetool.rules.CategoryIds
import org.languagetool.rules.Rule
import org.languagetool.rules.RuleMatch
import org.languagetool.rules.uk.CaseGovernmentHelper
import org.languagetool.rules.uk.TokenAgreementAdjNounRule
import org.languagetool.rules.uk.TokenAgreementNounVerbRule
import org.languagetool.rules.uk.TokenAgreementNumrNounRule
import org.languagetool.rules.uk.TokenAgreementPrepNounRule
import org.languagetool.rules.uk.TokenAgreementVerbNounRule
import org.languagetool.tagging.uk.UkrainianTagger

import groovy.transform.CompileStatic
import groovy.xml.slurpersupport.Node

class Validator {
    static final Pattern WORD_LEMMA = Pattern.compile(/(?iu)^[а-яіїєґa-z0-9].*/)
    static final List<String> EXTRA_TAGS = [ 'punct', 'number', 'number:latin', 'time', 'date', 'unclass', 'unknown', 'symb', 'hashtag' ]
    
    def lt = new JLanguageTool(Languages.getLanguageForShortCode("uk"), [], null, null, null, null)
    def ukrainian = lt.getLanguage()
//    @Lazy
    UkrainianTagger ukTagger = ukrainian.getTagger()
    Set<String> allTags
    ResourceBundle messages = JLanguageTool.getDataBroker().getResourceBundle(JLanguageTool.MESSAGE_BUNDLE, new Locale("uk"))
    List<org.languagetool.rules.Rule> validationRules = 
        [new TokenAgreementNounVerbRule(messages),
        new TokenAgreementAdjNounRule(messages, ukrainian),
        new TokenAgreementNumrNounRule(messages, ukrainian),
        new TokenAgreementPrepNounRule(messages, ukrainian),
        new TokenAgreementVerbNounRule(messages),
        ]
    Stats stats
    
    Map<String, List<String>> errValidations = [:].withDefault{ [] }
    List<String> errUnverified = []
    

    Validator(Stats stats) {
        this.stats = stats
        allTags = getClass().getResource('/org/languagetool/resource/uk/ukrainian_tags.txt').readLines() as Set
        allTags += EXTRA_TAGS
        
        def xmlRules = ukrainian.getPatternRules().findAll { Rule r -> 
            r.getCategory().getId() == CategoryIds.GRAMMAR && r.getId() =~ "(?i)(CONSISTENCY.*NUMERIC|PIVTORA|PRIZVY|LAST_NAME|MODAL)" //|token_agreement_noun_noun)"
        }
        println "Added ${xmlRules.size()} xml rules"
        validationRules += xmlRules
    }
    
    
    @CompileStatic
    boolean validateToken(String token, String lemma, String tags, File txtFile) {
        assert tags, "no tags for token $token"
        assert lemma != null, "no lemma for token $token"
        
        def xmlFilename = txtFile.name.replace('.txt', '.xml')
        
        if( tags in EXTRA_TAGS ) {
            if( token != lemma && token != "…" ) {
                println "\tWrong lemma: $tags for $token"
                errValidations[xmlFilename] << "Wrong lemma: $lemma for $token tags: $tags".toString()
            }
        }
        
        if( ! (tags in allTags) && ! (tags.replaceAll(/:(alt|bad|short)/, '') in allTags) ) {
            if( ! ( tags =~ /noninfl(:foreign)?:prop|noun:anim:p:v_zna:var|noun:anim:[mfp]:v_...:nv:abbr:prop:[fp]name/ ) ) {
                println "\tInvalid tag: $tags for $token"
                errValidations[xmlFilename] << "Invalid tag: $tags for $token".toString()
                return false
            }
        }

        if( tags.contains(":prop") && lemma =~ /^[а-яіїєґ]/ 
                || tags.startsWith("noun") && ! tags.contains(":prop") && ! tags.contains(":abbr") && lemma =~ /^[А-ЯІЇЄҐ]([а-яіїєї].*|$)/ ) {
            println "\tInvalid tag: $tags for $token"
            errValidations[xmlFilename] << "Invalid tag: $tags for $token".toString()
            return false
        }
            
        if( tags.contains(":nv") && lemma.toLowerCase() != token.toLowerCase() ) {
            println "\tInvalid tag: $tags for $token"
            errValidations[xmlFilename] << "Lemma $lemma mismatches $token for $tags".toString()
            return false
        }

//        if( ! tags.contains(":nv") && lemma.toLowerCase() == token.toLowerCase() && tags =~ /^(noun|adj):m:/ ) {
//            boolean ok = tags =~ /^(.*v_naz|adj:.:v_kly|adj:m:v_(zna:rinanim)|noun:inanim:m:v_zna)/
//            if( ! ok ) {
//                println "\tInvalid tag: $tags for $token"
//                errValidations[xmlFilename] << "Lemma $lemma mismatches $token for $tags".toString()
//                return false
//            }
//        }

        return true
    //    if( ! VALID_TAG.matcher(tags).matches() ) {
    //        System.err.println "Invalid tag: $tags"
    //        return
    //    }
    
    }
    
    @CompileStatic
    void validateToken2(String token, String lemma, String postag, File txtFile, ContextToken prevToken) {
        if( WORD_LEMMA.matcher(lemma).matches() ) {
            stats.wordCount++
            if( postag == "null" ) {
                System.err.println "null tag for $token"
            }
            else if( postag.startsWith("noninfl") || postag.startsWith("unclass") ) {
                
            }
            else {
                if( postag == "part" && token ==~ /-(бо|но|то|от|таки)/ && token == "-" + lemma )
                    return
                
                AnalyzedTokenReadings ltTags = ukTagger.tag([token])[0]
                
                if( ltTags.getReadings().size() > 1 ) {
                    stats.homonymTokens << token
                }
                
                def ltTags2 = ltTags.getReadings().collect { AnalyzedToken t -> t.getLemma() + "/" + t.getPOSTag() }
                
                String tagPair = "$lemma/$postag"
                if( postag.startsWith("noninfl:foreign") || postag.startsWith("unclass") ) {
                }
//                if( ltTags2[0].startsWith("null") && token.matches("[А-ЯІЇЄҐ][а-яіїєґА-ЯІЇЄҐ'-]+") && tags.matches(/noun:anim:.:v_...(:nv)?:prop:lname/) ) {
//                }
//                {
                    boolean initials = token ==~ /[А-ЯІЇЄҐ][а-яіїєґ]?\./ && tagPair ==~ /[А-ЯІЇЄҐ][а-яіїєґ]?\.\/noun:anim:[mf]:v_...:nv:abbr:prop:[fp]name/
                    if( ! (tagPair in ltTags2) && ! initials ) {
                        if( token != "їх" || ! (postag ==~ /adj:[mfnp]:v_...(:r(in)?anim)?:nv:&pron:pos:bad/ ) ) {
                            
                            errUnverified << "value=\"$token\" lemma=\"${tagPair.replace('/', '\" tags=\"')}\"  (avail: $ltTags2)".toString()
                    //                            println "Unverified tag: $tagPair (token: $token) (avail: $ltTags2)"
                        }
                        return
                    }
//                }
            }
            
            if( postag =~ /.*:p:v_naz.*/ ) {
                if( prevToken.word.toLowerCase() == "є" && prevToken.postag =~ /:s:3/ ) {
                    errUnverified << "value=\"$token\" lemma=\"$lemma\" tags=\"$postag\" (prev: $prevToken)".toString()
                }
            }
            if( postag =~ /.*:[mnfs]:v_naz.*/ ) {
                if( prevToken.word.toLowerCase() == "є" && prevToken.postag =~ /:p:3/ ) {
                    errUnverified << "value=\"$token\" lemma=\"$lemma\" tags=\"$postag\" (prev: $prevToken)".toString()
                }
            }
            if( token.toLowerCase() == "є" && postag =~ /:s:3/ ) {
                if( prevToken.postag =~ /.*:p:v_naz.*/ ) {
                    errUnverified << "value=\"$token\" lemma=\"$lemma\" tags=\"$postag\" (prev: $prevToken)".toString()
                }
            }
            if( token.toLowerCase() == "є" && postag =~ /:p:3/ ) {
                if( prevToken.postag =~ /.*:[mnfs]:v_naz.*/ ) {
                    errUnverified << "value=\"$token\" lemma=\"$lemma\" tags=\"$postag\" (prev: $prevToken)".toString()
                }
            }
        }
    }
    
    @CompileStatic
    void validateSentence(List<Node> xmls, File txtFile) {
        def xmlFileName = txtFile.name.replace('.txt', '.xml')
        int pos = 0
        def readings = xmls.collect { Node xml ->
            def attributes = xml.attributes()
            String tags = attributes['tags']
            String lemma = attributes['lemma']
            String token = attributes['value']
            
            if( tags =~ /unclass|punct|unknown|symbol/ ) {
                tags = null
            }
            
            def tokens = Arrays.asList(new AnalyzedToken(token, tags, lemma))
            def atr = new AnalyzedTokenReadings(tokens, pos)
            pos += token.length() + 1
            atr
        }
        readings.add(0, new AnalyzedTokenReadings(Arrays.asList(new AnalyzedToken('', JLanguageTool.SENTENCE_START_TAGNAME, '')), 0))
        AnalyzedSentence sent = new AnalyzedSentence(readings.toArray(new AnalyzedTokenReadings[0]))
        
        validationRules.each { rule ->
            RuleMatch[] matches = rule.match(sent)
            matches.each {
                def sample = xmls.collect{it.attributes()['value']}.join(' ')
                int fromPos = it.fromPos - 15
                if( fromPos < 0 ) fromPos = 0 
                sample = sample[fromPos..-1]
//                println "\trule violation: $it\n\t$sample"
                errValidations[xmlFileName] << "$it\n\t\t$sample".toString()
            }
        }
        validateAdjAdj(readings, xmlFileName)
        validateAnd(readings, xmlFileName)
        validateComma(readings, xmlFileName)
    }
    
    @CompileStatic
    void validateAdjAdj(List<AnalyzedTokenReadings> readings, String xmlFileName) {
        
        for(int ii=1; ii<readings.size(); ii++) {
            def reading1 = readings.get(ii)
            def reading0 = readings.get(ii-1)
            def r1 = reading1.getReadings().get(0)
            def r0 = reading0.getReadings().get(0)
            if( r0.getPOSTag() == null || r1.getPOSTag() == null )
                continue;
            
            if( r1.getPOSTag().startsWith("adj")
                    && r0.getPOSTag().startsWith("adj") ) {
                
                Matcher m1 = r1.getPOSTag() =~ /adj:.:(v_...)(:r(in)?anim)?/
                Matcher m0 = r0.getPOSTag() =~ /adj:.:(v_...)(:r(in)?anim)?/
                
                if( m1[0] != m0[0] ) {
                    if( r0.getToken() =~ /^[0-9]+-[а-яіїєґ]+/
                        || (r0.getPOSTag() =~ /adjp:pasv:perf/ 
                            && r1.getPOSTag() =~ /adj:.:v_oru/)
                            || r0.getLemma() =~ /^(який|котрий|кожен)$/
                            || CaseGovernmentHelper.hasCaseGovernment(reading0, m1.group(1)) )
                        continue
                    
                    errValidations[xmlFileName] << "${r0.getToken()} ${r1.getToken()} -- ${r0.getPOSTag()} ${r1.getPOSTag()}".toString()
                }
            }
        }
    }

    
    @CompileStatic
    void validateComma(List<AnalyzedTokenReadings> readings, String xmlFileName) {
        
        for(int ii=2; ii<readings.size(); ii++) {
            def reading2 = readings.get(ii)
            def reading1 = readings.get(ii-1)
            def reading0 = readings.get(ii-2)
            def r2 = reading2.getReadings().get(0)
            def r0 = reading0.getReadings().get(0)

//            if( ! (reading1.getCleanToken() ==~ /та|і|й|,/)
            if( ! (reading1.getCleanToken() ==~ /,/)
                || r0.getPOSTag() == null || r2.getPOSTag() == null )
                continue;

            if( r0.getToken() =~ /^[0-9]+-[а-яіїєґ]+/ )
                continue

            if( r0.getPOSTag() =~ /^(noun)(?!.*pro[np])/
                    && r2.getPOSTag() =~ /^(adj)(?!.*pron)/ ) {
                
                Matcher m2 = r2.getPOSTag() =~ /:(.):(v_...)/
                Matcher m0 = r0.getPOSTag() =~ /:(.):(v_...)/
                m2.find()
                m0.find()
                
                if( m2.group(1) == m0.group(1)
                        && m2.group(2) != m0.group(2) ) {
                    
                    if( m2.group(1) == "f" 
                            && m2.group(2) =~ /(mis|rod|dav)/ && m0.group(2) =~ /(mis|rod|dav)/
                            || m2.group(1) == "n"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                            || m2.group(1) =~ "p"
                            && m2.group(2) =~ /(rod|dav|mis)/ && m0.group(2) =~ /(rod|dav|mis)/
                            || m2.group(1) =~ "p"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                            || m2.group(1) =~ "p"
                            && m2.group(2) =~ /(zna|rod)/ && m0.group(2) =~ /(zna|rod)/
                            || m2.group(1) =~ "m"
                            && m2.group(2) =~ /(rod|zna)/ && m0.group(2) =~ /(rod|zna)/
                            || m2.group(1) =~ "m"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                             ) {
                        
                        errValidations[xmlFileName] << "${r0.getToken()} ${reading1.getCleanToken()} ${r2.getToken()} -- ${r0.getPOSTag()} ${r2.getPOSTag()}".toString()
                    }
                }
            }
        }
    }

    @CompileStatic
    void validateAnd(List<AnalyzedTokenReadings> readings, String xmlFileName) {
        
        for(int ii=2; ii<readings.size(); ii++) {
            def reading2 = readings.get(ii)
            def reading1 = readings.get(ii-1)
            def reading0 = readings.get(ii-2)
            def r2 = reading2.getReadings().get(0)
            def r0 = reading0.getReadings().get(0)

            if( ! (reading1.getCleanToken() ==~ /та|і|й|або|чи/)
                    || r0.getPOSTag() == null || r2.getPOSTag() == null )
                continue;
                
            if( r0.getToken() =~ /^[0-9]+-[а-яіїєґ]+/ )
                continue
    
            if( r0.getPOSTag() =~ /^(noun|adj)(?!.*(pro[np]|abbr))/
                    && r2.getPOSTag() =~ /^(noun|adj)(?!.*pron)/ ) {
                
                Matcher m2 = r2.getPOSTag() =~ /:(.):(v_...)/
                Matcher m0 = r0.getPOSTag() =~ /:(.):(v_...)/
                m2.find()
                m0.find()
                
                if( m2.group(1) == m0.group(1)
                        && m2.group(2) != m0.group(2) ) {
                    
                    if( m2.group(1) == "n"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                            || m2.group(1) =~ "p"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                            || m2.group(1) =~ "p"
                            && m2.group(2) =~ /(zna|rod)/ && m0.group(2) =~ /(zna|rod)/
                            || m2.group(1) =~ "m"
                            && m2.group(2) =~ /(rod|zna)/ && m0.group(2) =~ /(rod|zna)/
                            || m2.group(1) =~ "m"
                            && m2.group(2) =~ /(naz|zna)/ && m0.group(2) =~ /(naz|zna)/
                             ) {
                        
                        errValidations[xmlFileName] << "${r0.getToken()} ${reading1.getCleanToken()} ${r2.getToken()} -- ${r0.getPOSTag()} ${r2.getPOSTag()}".toString()
                    }
                }
            }
        }
    }


//    @CompileStatic
    void writeErrors() {
        println "${errUnverified.size()} tokens with unverified tags !!"

        java.text.Collator coll = java.text.Collator.getInstance(new Locale("uk", "UA"));
        coll.setStrength(java.text.Collator.IDENTICAL)
        coll.setDecomposition(java.text.Collator.NO_DECOMPOSITION)

        // write warnings
        
        new File("out/err_unverified.txt").text = errUnverified.collect{ 
                it.replaceFirst(/(value=")([^а-яіїєґ])/, '$1* $2').toString()
            }
            .toSorted(coll)
            .collect{ it.replace('="* ', '="') }
            .join("\n")

        new File("out/err_validations.txt").text = errValidations.collect { k,v -> "$k\n\t" + v.join("\n\t") }.join("\n")

    }    
}
