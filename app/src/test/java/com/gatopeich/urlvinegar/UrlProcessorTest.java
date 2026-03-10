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

    @Test
    public void testReconstructUrl_noDuplicationAfterTransformRemovesQuestionMark() {
        // Regression test: When transforms remove the '?' separator (e.g. removing
        // utm_source=xxx from ?utm_source=xxx&goal=yyy), surviving params must NOT
        // be duplicated in the result URL.
        // The fix: always reconstruct from the original URL, not the transform output.
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove UTM", "[?&](utm_[a-z_]+)=[^&]*", "", true));
        transforms.add(new Transform("Clean query", "(\\?)&+|&+(?=&)|&+$", "$1", true));
        transforms.add(new Transform("Remove empty query", "\\?$", "", true));

        String originalUrl = "https://www.eldiario.es/sociedad/article.html?utm_source=newsletter&goal=0_abc&mc_cid=1e44fb";

        // Parse params with tracking
        Set<String> userRemoved = new HashSet<>();
        List<UrlProcessor.QueryParam> params = UrlProcessor.parseParamsWithTracking(
            originalUrl, transforms, null, userRemoved);

        // goal and mc_cid should be kept, utm_source should be removed
        assertEquals(3, params.size());

        UrlProcessor.QueryParam goalParam = null;
        UrlProcessor.QueryParam mcParam = null;
        for (UrlProcessor.QueryParam p : params) {
            if ("goal".equals(p.name)) goalParam = p;
            if ("mc_cid".equals(p.name)) mcParam = p;
        }
        assertNotNull(goalParam);
        assertTrue(goalParam.keep);
        assertNotNull(mcParam);
        assertTrue(mcParam.keep);

        // Reconstruct from ORIGINAL URL (not transform output) to avoid duplication
        String result = UrlProcessor.reconstructUrl(originalUrl, params);

        // The result must NOT contain params before the '?'
        // i.e., no ".html&goal=" pattern
        assertFalse("Params should not appear in path before '?'",
            result.contains(".html&goal"));

        // Should have exactly one '?'
        int questionMarks = 0;
        for (char c : result.toCharArray()) {
            if (c == '?') questionMarks++;
        }
        assertEquals("URL should have exactly one '?'", 1, questionMarks);

        // Should have goal and mc_cid as proper query params
        assertTrue(result.contains("?goal=0_abc") || result.contains("&goal=0_abc"));
        assertTrue(result.contains("&mc_cid=1e44fb") || result.contains("?mc_cid=1e44fb"));

        // Should NOT contain utm_source (it was removed by transform)
        assertFalse(result.contains("utm_source"));
    }

    // --- Tests for anyTransformMatches ---

    @Test
    public void testAnyTransformMatches_matchFound() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove UTM", "utm_source", "", true));

        assertTrue(UrlProcessor.anyTransformMatches(
            "https://example.com?utm_source=test", transforms));
    }

    @Test
    public void testAnyTransformMatches_noMatch() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove UTM", "utm_source", "", true));

        assertFalse(UrlProcessor.anyTransformMatches("just plain text", transforms));
    }

    @Test
    public void testAnyTransformMatches_disabledTransform() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Disabled", "plain", "", false));

        assertFalse(UrlProcessor.anyTransformMatches("just plain text", transforms));
    }

    @Test
    public void testAnyTransformMatches_nullInput() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Test", ".*", "", true));

        assertFalse(UrlProcessor.anyTransformMatches(null, transforms));
    }

    @Test
    public void testAnyTransformMatches_nullTransforms() {
        assertFalse(UrlProcessor.anyTransformMatches("some text", null));
    }

    @Test
    public void testAnyTransformMatches_multilineText() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove Sent by", "\\nSent by.*$", "", true));

        String text = "https://example.com\nSent by MyApp";
        assertTrue(UrlProcessor.anyTransformMatches(text, transforms));
    }

    @Test
    public void testAnyTransformMatches_invalidRegex() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Invalid", "[bad(", "", true));

        assertFalse(UrlProcessor.anyTransformMatches("some text", transforms));
    }

    // --- Tests for applyTextTransforms ---

    @Test
    public void testApplyTextTransforms_basic() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove foo", "foo", "bar", true));

        String result = UrlProcessor.applyTextTransforms("hello foo world", transforms, null);
        assertEquals("hello bar world", result);
    }

    @Test
    public void testApplyTextTransforms_removeSentByLine() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove Sent by", "\\nSent by.*$", "", true));

        String text = "https://example.com/article\nSent by Samsung Mail";
        String result = UrlProcessor.applyTextTransforms(text, transforms, null);
        assertEquals("https://example.com/article", result);
    }

    @Test
    public void testApplyTextTransforms_podcastConversion() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Convert podcast",
            "Listen on OldPodcast: (\\S+)",
            "https://newpodcast.com/$1", true));

        String text = "Listen on OldPodcast: episode123";
        String result = UrlProcessor.applyTextTransforms(text, transforms, null);
        assertEquals("https://newpodcast.com/episode123", result);
    }

    @Test
    public void testApplyTextTransforms_multilineWithUrl() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove footer", "\\n+Sent from.*$", "", true));
        transforms.add(new Transform("Remove UTM", "[?&](utm_[a-z_]+)=[^&]*", "", true));
        transforms.add(new Transform("Fix leading ampersand", "^([^?&#]+)&", "$1?", true));

        String text = "https://example.com?utm_source=test&id=123\n\nSent from my iPhone";
        String result = UrlProcessor.applyTextTransforms(text, transforms, null);
        assertEquals("https://example.com?id=123", result);
    }

    @Test
    public void testApplyTextTransforms_disabledTransform() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Disabled", "foo", "bar", false));

        String result = UrlProcessor.applyTextTransforms("hello foo world", transforms, null);
        assertEquals("hello foo world", result);
    }

    @Test
    public void testApplyTextTransforms_invalidRegex() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Invalid", "[bad(", "", true));

        // Should not crash, should return original text
        String result = UrlProcessor.applyTextTransforms("some text", transforms, null);
        assertEquals("some text", result);
    }

    @Test
    public void testApplyTextTransforms_nullInput() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Test", ".*", "", true));

        assertNull(UrlProcessor.applyTextTransforms(null, transforms, null));
    }

    @Test
    public void testApplyTextTransforms_trimsResult() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Remove suffix", "world$", "", true));

        String result = UrlProcessor.applyTextTransforms("hello world", transforms, null);
        assertEquals("hello", result);
    }

    @Test
    public void testApplyTextTransforms_disabledIndices() {
        List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform("Transform A", "foo", "bar", true));
        transforms.add(new Transform("Transform B", "hello", "bye", true));

        Set<Integer> disabled = new HashSet<>();
        disabled.add(0); // Disable Transform A

        String result = UrlProcessor.applyTextTransforms("hello foo", transforms, disabled);
        assertEquals("bye foo", result);
    }
}
