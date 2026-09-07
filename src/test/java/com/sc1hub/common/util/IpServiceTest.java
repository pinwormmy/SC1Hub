package com.sc1hub.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpServiceTest {

    @Test
    void rightmostForwardedValueIsTheClientAndLeadingValuesAreIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.7, 203.0.113.9");

        assertEquals("203.0.113.9", IpService.getRemoteIP(request));
        assertTrue(IpService.hasForwardedClient(request));
    }

    @Test
    void invalidForwardedValueFallsBackToTheRemoteAddressAndIsNotTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.9, unknown");

        assertEquals("10.0.0.5", IpService.getRemoteIP(request));
        assertFalse(IpService.hasForwardedClient(request));
    }

    @Test
    void directConnectionWithoutHeaderUsesRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertEquals("127.0.0.1", IpService.getRemoteIP(request));
        assertFalse(IpService.hasForwardedClient(request));
    }

    @Test
    void plainHttpIsRecognisedByTheProxyRealIpHeaderUnlessProtoSaysHttps() {
        MockHttpServletRequest plain = new MockHttpServletRequest();
        plain.addHeader("X-Real-IP", "203.0.113.9");
        assertTrue(IpService.isPlainHttpViaProxy(plain));

        MockHttpServletRequest declaredHttps = new MockHttpServletRequest();
        declaredHttps.addHeader("X-Real-IP", "203.0.113.9");
        declaredHttps.addHeader("X-Forwarded-Proto", "https");
        assertFalse(IpService.isPlainHttpViaProxy(declaredHttps));

        assertFalse(IpService.isPlainHttpViaProxy(new MockHttpServletRequest()));
    }

    @Test
    void onlyPublicAddressesQualifyForBans() {
        assertTrue(IpService.isPublicAddress("203.0.113.9"));
        assertTrue(IpService.isPublicAddress("2001:db8::1"));
        assertFalse(IpService.isPublicAddress("10.0.0.1"));
        assertFalse(IpService.isPublicAddress("192.168.1.1"));
        assertFalse(IpService.isPublicAddress("127.0.0.1"));
        assertFalse(IpService.isPublicAddress("::1"));
        assertFalse(IpService.isPublicAddress("not-an-ip"));
        assertFalse(IpService.isValidIp("999.1.1.1"));
    }
}
