package net.theradar.halfmask;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaskerTest {
    private final Masker m = new Masker(Masker.defaultRules(), '#');

    @Test void keepsLengthAndPrefixOfJsonPassword() {
        String out = m.mask("{\"password\":\"Winter2026!\"}");
        assertEquals("{\"password\":\"Winte######\"}", out);
    }

    @Test void authorizationHeaderKeepsTwelveChars() {
        String in = "GET / HTTP/1.1\r\nAuthorization: Bearer abcdefghijklmnop\r\n\r\n";
        String out = m.mask(in);
        assertEquals(in.length(), out.length());
        assertTrue(out.contains("Authorization: Bearer abcde###########\r\n"), out);
    }

    @Test void jwtAnywhereKeepsHeaderSegmentVisible() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqYW5lIn0.abcdefghijklmnopqrstuvwxyz0123456789";
        String out = m.mask("token=" + jwt);
        assertTrue(out.startsWith("token=eyJhbGciOiJIUzI1NiJ9.eyJ"), out);
        assertFalse(out.contains("abcdefghijklmnopqrstuvwxyz"));
    }

    @Test void emailKeepsThreeChars() {
        assertEquals("jan#################", m.mask("jane.doe@example.com"));
    }

    @Test void cookieHeaderHalf() {
        String out = m.mask("Cookie: session=12345678\r\n");
        assertEquals("Cookie: session=12345678".length() + 2, out.length());
        assertTrue(out.startsWith("Cookie: sess"), out);
        assertTrue(out.endsWith("########\r\n"), out);
    }

    @Test void untouchedTextIsUnchanged() {
        String in = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"facilityID\":2075,\"name\":\"Ward A\"}";
        assertEquals(in, m.mask(in));
    }

    @Test void numericJsonValueIsMasked() {
        assertEquals("{\"ssn\":\"1234#####\"}", m.mask("{\"ssn\":\"123456789\"}"));
        assertEquals("{\"code\":123###}", m.mask("{\"code\":123456}"));
    }

    @Test void narrowRuleWinsOverBroadRule() {
        // Email rule (3 visible) runs after JSON "username" is not a rule, so the email is masked by the email rule only.
        Masker one = new Masker(List.of(new MaskRule(true, "u", MaskRule.Kind.JSON_FIELD, "username", 6),
                new MaskRule(true, "e", MaskRule.Kind.REGEX, "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", 3)), '#');
        assertEquals("{\"username\":\"jane.d##############\"}", one.mask("{\"username\":\"jane.doe@example.com\"}"));
    }

    @Test void disabledRuleDoesNothing() {
        Masker off = new Masker(List.of(new MaskRule(false, "p", MaskRule.Kind.JSON_FIELD, "password", -1)), '#');
        assertEquals("{\"password\":\"x\"}", off.mask("{\"password\":\"x\"}"));
    }

    @Test void serializationRoundTrip() {
        for (MaskRule r : Masker.defaultRules()) {
            MaskRule p = MaskRule.parse(r.serialize());
            assertNotNull(p); assertEquals(r.serialize(), p.serialize());
        }
    }

    @Test void fiveSegmentJweIsMaskedToTheEnd() {
        String jwe = "eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ.AAAAAAAAAAAAAAAA.BBBBBBBBBBBB.CCCCCCCCCCCCCCCCCCCC.DDDDDDDDDDDDDDDD";
        String out = m.mask("x=" + jwe + "&y=1");
        assertTrue(out.startsWith("x=eyJhbGciOiJSU0EtT0FFUCIs#"), out);
        assertFalse(out.contains("CCCC"), out);
        assertTrue(out.endsWith("&y=1"), out);
    }

    @Test void collapseShortensLongRunsOnly() {
        assertEquals("ab####cd", Masker.collapse("ab####cd", '#', 8));
        assertEquals("ab####[+6 masked]cd", Masker.collapse("ab##########cd", '#', 4));
    }
}
