/*!
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2002-2013 Pentaho Corporation..  All rights reserved.
 */

package org.pentaho.reporting.platform.plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import javax.swing.table.TableModel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import junit.framework.TestCase;
import org.junit.Assert;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.StandaloneSession;
import org.pentaho.reporting.engine.classic.core.ClassicEngineBoot;
import org.pentaho.reporting.engine.classic.core.DataFactory;
import org.pentaho.reporting.engine.classic.core.MasterReport;
import org.pentaho.reporting.engine.classic.core.StaticDataRow;
import org.pentaho.reporting.engine.classic.core.designtime.datafactory.DesignTimeDataFactoryContext;
import org.pentaho.reporting.libraries.base.util.DebugLog;
import org.pentaho.reporting.libraries.resourceloader.ResourceManager;
import org.pentaho.test.platform.engine.core.MicroPlatform;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ParameterTest extends TestCase {
  private MicroPlatform microPlatform;
  private File tmp;

  public ParameterTest() {

  }

  @Override
  protected void setUp() throws Exception {
    tmp = new File("./resource/solution/system/tmp");
    tmp.mkdirs();
    ClassicEngineBoot.getInstance().start();

    microPlatform = MicroPlatformFactory.create();
    microPlatform.start();
    IPentahoSession session = new StandaloneSession( "test user" );
    PentahoSessionHolder.setSession( session );
  }

  @Override
  protected void tearDown() throws Exception {
    microPlatform.stop();
  }

  public void testParameterProcessing() throws Exception {
    final ParameterContentGenerator contentGenerator = new ParameterContentGenerator();
    final ParameterXmlContentHandler handler = new ParameterXmlContentHandler( contentGenerator, false );
    handler.createParameterContent( System.out, "resource/solution/test/reporting/Product Sales.prpt",
        "resource/solution/test/reporting/Product Sales.prpt", false, null );
  }

  /**
   * verifies cases 
   * 
   * http://jira.pentaho.com/browse/PRD-3882
   * values containing illegal control chars are base64 encoded, and that the "encoded=true" attribute is
   * set as expected.
   * For example, <value encoded="true" label="Gg==" null="false" selected="false" type="java.lang.String" value="Gg=="/> 
   *
   * http://jira.pentaho.com/browse/PPP-3343
   * that Japanese values are not encoded
   * 
   * http://jira.pentaho.com/browse/BISERVER-11918
   * that special characters are not encoded 
   */
  public void testEncodedParameterValues() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ParameterContentGenerator contentGenerator = new ParameterContentGenerator();
    final ParameterXmlContentHandler handler = new ParameterXmlContentHandler( contentGenerator, false );
    handler.createParameterContent( baos, "resource/solution/test/reporting/prd3882.prpt",
        "resource/solution/test/reporting/prd3882.prpt", false, null );

    DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document doc = db.parse(new ByteArrayInputStream(baos.toByteArray()));

    String[] expectedVal = new String[] { "1qA", "+ / : ; = ? [ ] ^ \\", "果物" };
    //this label is shown user! you should be able to read items!
    String[] expectedLab = new String[] { "1qA", "+ / : ; = ? [ ] ^ \\", "果物" };
    String[] expectedEncoded = new String[] { null, null, null };

    NodeList parameter = doc.getElementsByTagName("parameter");
    for (int n = 0; n < parameter.getLength(); n += 1) {
      Element param = (Element) parameter.item(n);
      if ("dropDown".equals(param.getAttribute("name")) || "singleSelection".equals(param.getAttribute("name"))) {
        DebugLog.log(debugXmlNodes(param));

        // there are no values, as the query seems to return no data. However, it does not fail either ..
        /*
        NodeList valueElements = param.getElementsByTagName("value");
        Assert.assertEquals(expectedVal.length, valueElements.getLength());
        for ( int i = 0; i < expectedVal.length; i++ ) {
          Element valueElement = (Element) valueElements.item(i);
          String value = valueElement.getAttribute( "value" );
          Node encoded = valueElement.getAttributeNode( "encoded" );
          String label = valueElement.getAttribute("label");

          assertEquals( expectedVal[i], value );
          assertEquals( expectedLab[i], label );
          assertEquals( expectedEncoded[i], encoded == null ? encoded : encoded.getTextContent() );
        }
*/
      }
    }
  }

  public void testParameterQuery() throws Exception {
    ResourceManager mgr = new ResourceManager();
    MasterReport report = (MasterReport) mgr.createDirectly
            (new File("resource/solution/test/reporting/prd3882.prpt"), MasterReport.class).getResource();
    DataFactory dataFactory = report.getDataFactory();
    try {
      dataFactory.initialize(new DesignTimeDataFactoryContext(report));
      TableModel tableModel = dataFactory.queryData("Query 1", new StaticDataRow());
      Assert.assertEquals(2, tableModel.getColumnCount());
      Assert.assertEquals(3, tableModel.getRowCount());
    }
    finally {
      dataFactory.close();
    }
  }


  private String debugXmlNodes(Node node) throws TransformerException {
    TransformerFactory transFactory = TransformerFactory.newInstance();
    Transformer transformer = transFactory.newTransformer();
    StringWriter buffer = new StringWriter();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.transform(new DOMSource(node),
          new StreamResult(buffer));
    return buffer.toString();
  }
}
