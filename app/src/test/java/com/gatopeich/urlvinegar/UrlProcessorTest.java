package com.gatopeich.urlvinegar;

import com.gatopeich.urlvinegar.data.Transform;
import com.gatopeich.urlvinegar.util.UrlProcessor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for URL processing functionality.
 */
public class UrlProcessorTest {

    @Test
    public void testExtractUrl_simpleUrl() {
        String text = "Check out this link: https://example.com/page";
        String result = UrlProcessor.extractUrl(text);
        assertEquals("https://example.com/page", result);
    }

    @Test
    public void testExtractUrl_urlWithParams() {
        String text = "https://example.com/page?utm_source=test&id=123";
        String result = UrlProcessor.extractUrl(text);
        assertEquals("https://example.com/page?utm_source=test&id=123", result);
    }

    @Test
    public void testExtractUrl_noUrl() {
        String text = "This is just plain text";
        String result = UrlProcessor.extractUrl(text);
        assertNull(result);
    }

    @Test
    public void testExtractUrl_nullInput() {
        String result = UrlProcessor.extractUrl(null);
        assertNull(result);
    }

    @Test
    public void testApplyTransforms_removeUtmParams() {
        List<Transform> transforms = new ArrayList<>();
        // UTM removal transform
        transforms.add(new Transform("Remove UTM", "[?&](utm_[a-z_]+)=[^&]*", "", true));
        // Cleanup: convert leading & to ? for first query parameter (handles path ending before & becomes query start)
        transforms.add(new Transform("Fix leading ampersand", "^([^?&#]+)&", "$1?", true));
        
        String url = "https://example.com/page?utm_source=test&id=123&utm_medium=email";
        UrlProcessor.ProcessResult result = UrlProcessor.applyTransforms(url, transforms, null);
        
        assertTrue(result.isValid);
        assertEquals("https://example.com/page?id=123", result.url);
    }

    @Test
    public void testApplyTransforms_disabledTransform() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Disabled", "utm_source", "", false));
        
        String url = "https://example.com?utm_source=test";
        UrlProcessor.ProcessResult result = UrlProcessor.applyTransforms(url, transforms, null);
        
        assertTrue(result.isValid);
        assertEquals(url, result.url);
    }

    @Test
    public void testApplyTransforms_invalidScheme() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove scheme", "https://", "", true));
        
        String url = "https://example.com";
        UrlProcessor.ProcessResult result = UrlProcessor.applyTransforms(url, transforms, null);
        
        assertFalse(result.isValid);
        assertNotNull(result.error);
    }

    @Test
    public void testApplyTransforms_invalidRegex() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Invalid regex", "[invalid(", "", true));
        
        String url = "https://example.com";
        UrlProcessor.ProcessResult result = UrlProcessor.applyTransforms(url, transforms, null);
        
        // Should not crash, should skip invalid regex
        assertTrue(result.isValid);
        assertEquals(url, result.url);
    }

    @Test
    public void testTransformMatches_matching() {
        Transform transform = new Transform("Test", "utm_source", "", true);
        String url = "https://example.com?utm_source=test";
        
        assertTrue(UrlProcessor.transformMatches(url, transform));
    }

    @Test
    public void testTransformMatches_notMatching() {
        Transform transform = new Transform("Test", "utm_source", "", true);
        String url = "https://example.com?id=123";
        
        assertFalse(UrlProcessor.transformMatches(url, transform));
    }

    @Test
    public void testIsValidPattern_valid() {
        assertTrue(UrlProcessor.isValidPattern("[a-z]+"));
        assertTrue(UrlProcessor.isValidPattern("\\d{3}-\\d{4}"));
        assertTrue(UrlProcessor.isValidPattern(".*"));
    }

    @Test
    public void testIsValidPattern_invalid() {
        assertFalse(UrlProcessor.isValidPattern("[invalid("));
        assertFalse(UrlProcessor.isValidPattern("(unclosed"));
    }

    @Test
    public void testParseQueryParams_basic() {
        Set<String> allowed = new HashSet<>();
        allowed.add("id");
        
        String url = "https://example.com?id=123&utm_source=test";
        List<UrlProcessor.QueryParam> params = UrlProcessor.parseQueryParams(url, allowed);
        
        assertEquals(2, params.size());
        assertEquals("id", params.get(0).name);
        assertEquals("123", params.get(0).value);
        assertTrue(params.get(0).keep);
        assertEquals("utm_source", params.get(1).name);
        assertFalse(params.get(1).keep);
    }

    @Test
    public void testParseQueryParams_noParams() {
        Set<String> allowed = new HashSet<>();
        
        String url = "https://example.com/page";
        List<UrlProcessor.QueryParam> params = UrlProcessor.parseQueryParams(url, allowed);
        
        assertEquals(0, params.size());
    }

    @Test
    public void testReconstructUrl_filterParams() {
        List<UrlProcessor.QueryParam> params = new ArrayList<>();
        params.add(new UrlProcessor.QueryParam("id", "123", true));
        params.add(new UrlProcessor.QueryParam("utm_source", "test", false));
        params.add(new UrlProcessor.QueryParam("page", "2", true));
        
        String url = "https://example.com/path?id=123&utm_source=test&page=2";
        String result = UrlProcessor.reconstructUrl(url, params);
        
        assertEquals("https://example.com/path?id=123&page=2", result);
    }

    @Test
    public void testReconstructUrl_noParamsKept() {
        List<UrlProcessor.QueryParam> params = new ArrayList<>();
        params.add(new UrlProcessor.QueryParam("utm_source", "test", false));
        
        String url = "https://example.com/path?utm_source=test";
        String result = UrlProcessor.reconstructUrl(url, params);
        
        assertEquals("https://example.com/path", result);
    }

    @Test
    public void testReconstructUrl_preserveFragment() {
        List<UrlProcessor.QueryParam> params = new ArrayList<>();
        params.add(new UrlProcessor.QueryParam("id", "123", true));
        
        String url = "https://example.com/path?id=123#section";
        String result = UrlProcessor.reconstructUrl(url, params);
        
        assertEquals("https://example.com/path?id=123#section", result);
    }

    @Test
    public void testLooksLikeUrl_httpUrl() {
        assertTrue(UrlProcessor.looksLikeUrl("http://example.com"));
    }

    @Test
    public void testLooksLikeUrl_httpsUrl() {
        assertTrue(UrlProcessor.looksLikeUrl("https://example.com/page?id=1"));
    }

    @Test
    public void testLooksLikeUrl_caseInsensitive() {
        assertTrue(UrlProcessor.looksLikeUrl("Http://Example.com"));
        assertTrue(UrlProcessor.looksLikeUrl("HTTP://EXAMPLE.COM"));
    }

    @Test
    public void testLooksLikeUrl_leadingWhitespace() {
        assertTrue(UrlProcessor.looksLikeUrl("  https://example.com"));
    }

    @Test
    public void testLooksLikeUrl_plainText() {
        assertFalse(UrlProcessor.looksLikeUrl("just some text"));
    }

    @Test
    public void testLooksLikeUrl_nullInput() {
        assertFalse(UrlProcessor.looksLikeUrl(null));
    }

    @Test
    public void testLooksLikeUrl_emptyInput() {
        assertFalse(UrlProcessor.looksLikeUrl(""));
    }

    @Test
    public void testLooksLikeUrl_shortInput() {
        assertFalse(UrlProcessor.looksLikeUrl("htt"));
    }

    @Test
    public void testLooksLikeUrl_httpWithoutScheme() {
        assertFalse(UrlProcessor.looksLikeUrl("httpnotaurl"));
    }

    @Test
    public void testLooksLikeUrl_httpColonOnly() {
        assertFalse(UrlProcessor.looksLikeUrl("http:something"));
    }

    @Test
    public void testLooksLikeUrl_urlNotAtStart() {
        assertFalse(UrlProcessor.looksLikeUrl("see http://example.com"));
    }

    @Test
    public void testParseParamsWithTracking_removedByTransform() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove UTM", "[?&](utm_[a-z_]+)=[^&]*", "", true));
        transforms.add(new Transform("Clean query", "(\\?)&+|&+(?=&)|&+$", "$1", true));

        String originalUrl = "https://example.com/page?id=123&utm_source=test&utm_medium=email";

        Set<String> userRemoved = new HashSet<>();
        List<UrlProcessor.QueryParam> params = UrlProcessor.parseParamsWithTracking(
            originalUrl, transforms, null, userRemoved);

        // Kept params come first (id), then removed params (utm_source, utm_medium)
        assertEquals(3, params.size());

        // id should be kept (first, since kept comes first)
        assertEquals("id", params.get(0).name);
        assertTrue(params.get(0).keep);
        assertNull(params.get(0).removedBy);

        // utm_source should be removed by "Remove UTM"
        assertEquals("utm_source", params.get(1).name);
        assertFalse(params.get(1).keep);
        assertEquals("Remove UTM", params.get(1).removedBy);

        // utm_medium should be removed by "Remove UTM"
        assertEquals("utm_medium", params.get(2).name);
        assertFalse(params.get(2).keep);
        assertEquals("Remove UTM", params.get(2).removedBy);
    }

    @Test
    public void testParseParamsWithTracking_userRemovedParam() {
        List<Transform> transforms = new ArrayList<>();
        String originalUrl = "https://example.com?id=123&ref=abc";

        Set<String> userRemoved = new HashSet<>();
        userRemoved.add("ref");

        List<UrlProcessor.QueryParam> params = UrlProcessor.parseParamsWithTracking(
            originalUrl, transforms, null, userRemoved);

        assertEquals(2, params.size());
        // id kept
        assertEquals("id", params.get(0).name);
        assertTrue(params.get(0).keep);
        // ref removed by user (no transform)
        assertEquals("ref", params.get(1).name);
        assertFalse(params.get(1).keep);
        assertNull(params.get(1).removedBy); // no transform, removed by user
    }

    @Test
    public void testParseParamsWithTracking_noParams() {
        List<Transform> transforms = new ArrayList<>();
        String originalUrl = "https://example.com/page";

        Set<String> userRemoved = new HashSet<>();
        List<UrlProcessor.QueryParam> params = UrlProcessor.parseParamsWithTracking(
            originalUrl, transforms, null, userRemoved);

        assertEquals(0, params.size());
    }

    @Test
    public void testParseParamsWithTracking_stepByStepAccuracy() {
        // Regression: "goal" param should NOT be attributed to "Remove UTM parameters"
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove UTM parameters", "[?&](utm_[a-z_]+)=[^&]*", "", true));
        transforms.add(new Transform("Remove affiliate tracking", "[?&](ref|aff|affiliate|campaign|source|medium)=[^&]*", "", true));
        transforms.add(new Transform("Clean up query string", "(\\?)&+|&+(?=&)|&+$", "$1", true));
        transforms.add(new Transform("Remove empty query string", "\\?$", "", true));

        String originalUrl = "https://example.com/page?goal=signup&utm_source=twitter&ref=partner1&id=42";

        Set<String> userRemoved = new HashSet<>();
        List<UrlProcessor.QueryParam> params = UrlProcessor.parseParamsWithTracking(
            originalUrl, transforms, null, userRemoved);

        // Should have 4 params total
        assertEquals(4, params.size());

        // Kept params come first: "goal" and "id" should be kept
        UrlProcessor.QueryParam goalParam = null;
        UrlProcessor.QueryParam idParam = null;
        UrlProcessor.QueryParam utmParam = null;
        UrlProcessor.QueryParam refParam = null;
        for (UrlProcessor.QueryParam p : params) {
            if ("goal".equals(p.name)) goalParam = p;
            else if ("id".equals(p.name)) idParam = p;
            else if ("utm_source".equals(p.name)) utmParam = p;
            else if ("ref".equals(p.name)) refParam = p;
        }

        // "goal" should be kept, NOT removed
        assertNotNull(goalParam);
        assertTrue(goalParam.keep);
        assertNull(goalParam.removedBy);

        // "id" should be kept
        assertNotNull(idParam);
        assertTrue(idParam.keep);
        assertNull(idParam.removedBy);

        // "utm_source" removed by "Remove UTM parameters"
        assertNotNull(utmParam);
        assertFalse(utmParam.keep);
        assertEquals("Remove UTM parameters", utmParam.removedBy);

        // "ref" removed by "Remove affiliate tracking"
        assertNotNull(refParam);
        assertFalse(refParam.keep);
        assertEquals("Remove affiliate tracking", refParam.removedBy);
    }

    @Test
    public void testParseParamsWithTracking_keptBeforeRemoved() {
        // Verify ordering: kept params first, then removed params
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove UTM", "[?&](utm_[a-z_]+)=[^&]*", "", true));

        String originalUrl = "https://example.com?utm_source=test&id=123&utm_medium=email&page=2";

        Set<String> userRemoved = new HashSet<>();
        List<UrlProcessor.QueryParam> params = UrlProcessor.parseParamsWithTracking(
            originalUrl, transforms, null, userRemoved);

        assertEquals(4, params.size());
        // First two should be kept params (id and page)
        assertTrue(params.get(0).keep);
        assertTrue(params.get(1).keep);
        // Last two should be removed params (utm_source and utm_medium)
        assertFalse(params.get(2).keep);
        assertFalse(params.get(3).keep);
    }
}
