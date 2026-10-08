package com.ncba.countryintegration.soap;

import com.ncba.countryintegration.exception.CountryNotFoundException;
import com.ncba.countryintegration.exception.ExternalServiceException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Namespace-agnostic (local-name based) parsing of CountryInfoService SOAP responses. */
public final class SoapXmlParser {
    private SoapXmlParser() { }

    public static String parseIsoCode(String xml, String requestedName) {
        Element result = firstByLocalName(parse(xml), "CountryISOCodeResult");
        String value = result == null ? null : result.getTextContent().trim();
        if (value == null || !value.matches("[A-Za-z]{2}")) {
            // The service returns free text such as "Country not found in the database"
            throw new CountryNotFoundException("Country '" + requestedName + "' was not found");
        }
        return value.toUpperCase();
    }

    public static FullCountryInfoData parseFullCountryInfo(String xml, String isoCode) {
        Element result = firstByLocalName(parse(xml), "FullCountryInfoResult");
        if (result == null) {
            throw new ExternalServiceException("SOAP response missing FullCountryInfoResult");
        }
        String iso = childText(result, "sISOCode");
        if (iso == null || iso.isBlank()) {
            throw new CountryNotFoundException("No country information found for ISO code '" + isoCode + "'");
        }
        List<FullCountryInfoData.LanguageData> languages = new ArrayList<>();
        NodeList langs = result.getElementsByTagNameNS("*", "tLanguage");
        for (int i = 0; i < langs.getLength(); i++) {
            Element l = (Element) langs.item(i);
            languages.add(new FullCountryInfoData.LanguageData(childText(l, "sISOCode"), childText(l, "sName")));
        }
        return new FullCountryInfoData(iso, childText(result, "sName"), childText(result, "sCapitalCity"),
                childText(result, "sPhoneCode"), childText(result, "sContinentCode"),
                childText(result, "sCurrencyISOCode"), childText(result, "sCountryFlag"), languages);
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // XXE protection
            return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new ExternalServiceException("Unparseable SOAP response", e);
        }
    }

    private static Element firstByLocalName(Document d, String name) {
        NodeList n = d.getElementsByTagNameNS("*", name);
        return n.getLength() == 0 ? null : (Element) n.item(0);
    }

    /** Text of a DIRECT child element (so a nested language sISOCode is not confused with the country's). */
    private static String childText(Element parent, String localName) {
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element e && localName.equals(e.getLocalName())) {
                return e.getTextContent().trim();
            }
        }
        return null;
    }
}
