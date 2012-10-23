import com.sas.groovy.test.ServerTestCase;
import com.sas.groovy.util.IOMHelper;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.sas.meta.SASOMI.IOMI;
import com.sas.meta.SASOMI.IOMIHelper;

import org.omg.CORBA.StringHolder;

/* The ServerMetadataSubmit class tests submitting an MDX query to an OLAP
 * server using a direct manual connection.  In addition to the ServerTestCase,
 * ThreadedTestCase, and TestCase properties this class expects the following:
 *
 * -Dinfile
 *     The path to a local file.  The contents of this file will be submitted
 *     the the Metadata server as a IOMI::DoRequest call.
 *
 * -Dstyle
 *     The path to a local XSL file.  The contents of this file will be assumed to be
 *     xsl that will be used to transform the results of the Metadata call.
 *
 * -Doutfile
 *     The path to a local file.  This file is where the results of the Metadata call will
 *     be written.  If not specified then stdout is used.
 *
 * -Drepname
 *     The name of the repository to use.  The id of this repository will be substituted
 *     for _repid_ in the infile.  The default is "Foundation".
 *
 * -D_<name>_
 *     Any property specified with a leading and following underscore on the command line
 *     or in the config file will be used as a substitution into the infile.  For example,
 *     if the infile contains
 *     <GetMetadata>
 *         <Metadata>
 *             <Person Id="_personid_" />
 *         </Metadata>
 *         <NS>SAS</NS>
 *         <!--   8 = all simple
 *             2048 = succinct
 *          -->
 *         <Flags>2056</Flags>
 *         <Options />
 *     </GetMetadata>
 *     Then -D_personid_=mypersonid on the command line will substute "mypersonid" for _personid_
 *     before submitting the contents to the metadata server.
 *
 * See com.sas.groovy.test.ServerTestCase for connection options.
 *
 * Example command lines
 *
 * Submit a query with the properties read from config.properties
 * runvjrscript -Dprops=config.properties serverMetadataSubmit.groovy
 *
 * Submit the contents of test.query over an SSPI connection, two iterations of four threads, and styling the result with test.xsl
 * runvjrscript -Diomsrv.uri=iom://localhost:8591;Bridge;CLSID=CLSID_SASOMI -Dinfile=test.query -Dstyle=test.xsl -Diteration.count=2 -Dthread.count=4 serverMetadataSubmit.groovy
 *
 * Submit the contents of test.query over a User connection, one iteration of one thread
 * runvjrscript -Diomsrv.uri=iom://localhost:8591;Bridge;CLSID=CLSID_SASOMI,USER=carynt\sasiom1,PASS=123456 serverMetadataSubmit.groovy
 *
 */

class ServerMetadataSubmit extends ServerTestCase {

    String source = "";
    String style = "";
    def out = null;
    String repname = "";
    String repid = "";

    void printHelp() {
        super.printHelp();
        println """
=============Test Properties=============

infile
The path to a local file.  The contents of this file will be submitted the the Metadata server as a IOMI::DoRequest call.

style
The path to a local XSL file.  The contents of this file will be assumed to be xsl that will be used to transform the results of the Metadata call.

outfile
The path to a local file.  This file is where the results of the Metadata call will be written.  If not specified then stdout is used.

repname
The name of the repository to use.  The id of this repository will be substituted for _repid_ in the infile.  The default is \"Foundation\".

_<name>_
Any property specified with a leading and following underscore on the command line or in the config file will be used as a substitution into the infile.  For example, if the infile contains
    <GetMetadata>
        <Metadata>
            <Person Id=\"_personid_\" />
        </Metadata>
        <NS>SAS</NS>
        <!--   8 = all simple
            2048 = succinct
         -->
        <Flags>2056</Flags>
        <Options />
    </GetMetadata>
Then \"-D_personid_=mypersonid\" on the command line or \"_persionid_=mypersionid\" in a properties file will substute \"mypersonid\" for _personid_ before submitting the contents to the metadata server.""";
    }

    void setUp() {
        Properties p = getProperties();

        /* Load the source xml
         */
        if( !p.containsKey("infile") ) {
            throw new IllegalArgumentException( "source argument required" );
        }
        StringBuilder buffer = new StringBuilder();
        new File(p.getProperty("infile")).eachLine { line -> buffer.append(line); }
        source = buffer.toString();
        /* Substitute any properties we can find now.  Repid will have
         * to wait for a connection.
         */
        p.each{ k, v ->
            if( k.startsWith("_") && k.endsWith("_") ) {
                source = source.replaceAll( k ) { return v; }
            }
        }

        /* Load the style sheet
         */
        style = p.getProperty( "style" );

        /* Set up the output device
         */
        out = System.out;
        def outpath = p.getProperty( "outfile" );
        if( outpath ) {
            out = new PrintStream( new FileOutputStream( outpath ) );
        }

        /* Get repository identification
         */
        repname = p.getProperty( "_repname_" );
        if( !repname ){ repname = p.getProperty( "repname", "Foundation" ); }
        repid = p.getProperty( "_repid_" );

        /* Let the base classes do their setup
         */
        super.setUp();
    }

    void testMetadataSubmit() {
        onExecutions { iteration, thread ->
            IOMI iOMI = IOMIHelper.narrow( getTopLevelObject( thread ) );

            /* Find the repository id for the requested repname
             */
            if( !repid ) {
                repid = IOMHelper.ReposIdForName( iOMI, repname );
            }
            source = source.replaceAll( "_repid_" ){ return repid }

            /* Submit the request
             */
            StringHolder outMetadata = new StringHolder();
            iOMI.DoRequest( source, outMetadata );
            String result = outMetadata.value;
            /* Style the result
             */
            if( style ) {
                StringWriter styledResult = new StringWriter();
                TransformerFactory tFactory = TransformerFactory.newInstance();
                Transformer transformer = tFactory.newTransformer( new StreamSource( new FileReader( style ) ) );
                transformer.transform(new StreamSource(new StringReader( result ) ), new StreamResult( styledResult ));
                result = styledResult.toString();
            }

            /* Print the result
             */
            out.println( result );
        }
    }
}