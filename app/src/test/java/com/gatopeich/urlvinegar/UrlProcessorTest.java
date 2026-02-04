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
        transforms.add(new Transform("Remove UTM", "[?&](utm_[a-z_]+)=[^&]*", "", true));
        
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
}
