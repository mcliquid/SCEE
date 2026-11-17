import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/** Parse SVG without resolving external DTDs or entities (XXE-safe). DOCTYPE declarations are kept. */
fun parseSvgStream(input: InputStream): Document =
    newSecureSvgDocumentBuilder().parse(InputSource(input))

fun newSecureSvgDocumentBuilder(): DocumentBuilder {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    factory.isValidating = false
    factory.isExpandEntityReferences = false
    factory.isXIncludeAware = false
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    return factory.newDocumentBuilder()
}