package com.ncba.countryintegration;

import static org.junit.jupiter.api.Assertions.*;

import com.ncba.countryintegration.exception.CountryNotFoundException;
import com.ncba.countryintegration.exception.ExternalServiceException;
import com.ncba.countryintegration.soap.FullCountryInfoData;
import com.ncba.countryintegration.soap.SoapXmlParser;
import org.junit.jupiter.api.Test;

class SoapXmlParserTest {

    private static final String NS = "http://www.oorsprong.org/websamples.countryinfo";

    private static String iso(String v) {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<m:CountryISOCodeResponse xmlns:m=\"" + NS + "\"><m:CountryISOCodeResult>" + v
                + "</m:CountryISOCodeResult></m:CountryISOCodeResponse></soap:Body></soap:Envelope>";
    }

    private static final String FULL = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
            + "<m:FullCountryInfoResponse xmlns:m=\"" + NS + "\"><m:FullCountryInfoResult>"
            + "<m:sISOCode>KE</m:sISOCode><m:sName>Kenya</m:sName><m:sCapitalCity>Nairobi</m:sCapitalCity>"
            + "<m:sPhoneCode>254</m:sPhoneCode><m:sContinentCode>AF</m:sContinentCode>"
            + "<m:sCurrencyISOCode>KES</m:sCurrencyISOCode><m:sCountryFlag>http://flag/Kenya.jpg</m:sCountryFlag>"
            + "<m:Languages><m:tLanguage><m:sISOCode>swa</m:sISOCode><m:sName>Swahili</m:sName></m:tLanguage>"
            + "<m:tLanguage><m:sISOCode>eng</m:sISOCode><m:sName>English</m:sName></m:tLanguage></m:Languages>"
            + "</m:FullCountryInfoResult></m:FullCountryInfoResponse></soap:Body></soap:Envelope>";

    @Test void parsesIsoCode() {
        assertEquals("KE", SoapXmlParser.parseIsoCode(iso("KE"), "Kenya"));
    }

    @Test void unknownCountryIsBusinessError() {
        assertThrows(CountryNotFoundException.class,
                () -> SoapXmlParser.parseIsoCode(iso("Country not found in the database"), "Atlantis"));
    }

    @Test void parsesFullInfoIncludingLanguages() {
        FullCountryInfoData d = SoapXmlParser.parseFullCountryInfo(FULL, "KE");
        assertEquals("KE", d.isoCode());
        assertEquals("Nairobi", d.capitalCity());
        assertEquals("KES", d.currencyIsoCode());
        assertEquals(2, d.languages().size());
        assertEquals("Swahili", d.languages().get(0).name());
    }

    @Test void garbageIsUpstreamError() {
        assertThrows(ExternalServiceException.class, () -> SoapXmlParser.parseIsoCode("<html>oops", "Kenya"));
    }

    @Test void rejectsDoctype() {
        assertThrows(ExternalServiceException.class, () -> SoapXmlParser.parseIsoCode(
                "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>", "Kenya"));
    }
}
