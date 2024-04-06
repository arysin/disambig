package ua.net.nlp.bruk.tools;

import java.io.File;
import java.util.regex.Pattern;

import groovy.transform.CompileStatic;
import groovy.xml.slurpersupport.Node;

public class TxtGenerator {
    static final Pattern PUNCT_PATTERN = Pattern.compile(/[.!?,»\u201D)\]%\/:;…]|[.!?]{2,3}/) // (\.,»…\)\]|[.!?]{3})/)

//    if( xml.name() == "sentence" ) {
//                if( sentIdx > 0 && needsSpace(prevChild) ) {
//                    txtFile << " "
//                }
//                txtFile << "<$xmlName>"
//                txtFile << "</$xmlName>"
//    }

    Node prevChild = null
    boolean quoteOpen
    
    void init() {
        File txt2Folder = new File("../txt/gen")
        //    txt2Folder.mkdirs()
    }
    
    @CompileStatic
    private void printNode(File txtFile, Node node, int childIdx) {

//      if( xmlName.contains("format") ) {
//          xmlName = xml.attributes()['tag']
//          if( ((Node)xml).attributes()['tag'] == 'br' ) {
//              txtFile << "\n"
//              prevChild = null
//              childIdx = 0
//              return
//          }
//
//          if( childIdx > 0 
//                  && prevChild != null 
//                  && prevChild.name() != 'format'
//                  && ! (prevChild.attributes()['value'] ==~ /[«\u201C(\[]/) 
//                  && ! ((String)xml.attributes()['tag']).startsWith("/") ) {
//              txtFile << " "
//          }
//          
//          txtFile << "<$xmlName>"
//          prevChild = xml
//          return
//      }

        
        if( node.text() || node.parent().name()=="meta" ) {
//            txtFile << "<${node.name()}>${node.text()}</${node.name()}>\n"
        }
        else if( node.name() == "token" ) {
            String nodeValue = node.attributes()['value'].toString()
            if( nodeValue == '"' ) quoteOpen = ! quoteOpen
            if( childIdx > 0 
                    && ! PUNCT_PATTERN.matcher(nodeValue).matches()
                    && prevChild != null 
                    && ! (prevChild.attributes()['value'] ==~ /[«\u201C\/(\[$]/) 
                    && needsSpace(prevChild) ) {
                if( childIdx == 1 && prevChild.attributes()['value'] ==~ /…|\.{3}/ ) {
                    
                }
                else {
                    if( prevChild.attributes()['value'] == '"' && quoteOpen ) {
                    }
                    else if( nodeValue == '"' && ! quoteOpen ) {
                    }
                    else 
                    txtFile << " "
                }
            } 
            txtFile << nodeValue
        }
        else {
//            String attrs = ((Node)node).attributes().collect { k,v -> "$k=\"$v\"" }.join(" ")
//            if( attrs ) attrs = " " + attrs
    //
//            if( node.name() == "paragraph" ) {
//               txtFile << "\n\n" 
//               prevChild = null
//               quoteOpen = false
//               childIdx = 0
//               sentIdx = 0
//            }
//            else {
//                txtFile << "<${node.name()}$attrs/>"
//            }
        }
        prevChild = node
    }

    
    @CompileStatic
    static boolean needsSpace(Node prevChild) {
        return prevChild != null && \
            (prevChild.name() != "format" || ((String)prevChild.attributes()['tag']).startsWith("/")) \
                && ! (((String)prevChild.attributes()['value']) ==~ /[°\/]/)
    }
    
    
}
