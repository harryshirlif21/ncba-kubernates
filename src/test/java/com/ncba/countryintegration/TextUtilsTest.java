package com.ncba.countryintegration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ncba.countryintegration.util.TextUtils;
import org.junit.jupiter.api.Test;

class TextUtilsTest {
    @Test void convertsToSentenceCase() {
        assertEquals("Kenya", TextUtils.toSentenceCase("kenya"));
        assertEquals("Tanzania", TextUtils.toSentenceCase("  TANZANIA "));
        assertEquals("South africa", TextUtils.toSentenceCase("south   AFRICA"));
    }
    @Test void escapesXml() {
        assertEquals("a&amp;b&lt;c&gt;", TextUtils.escapeXml("a&b<c>"));
    }
}
