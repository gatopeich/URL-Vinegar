package com.gatopeich.urlvinegar.util;

import com.gatopeich.urlvinegar.data.Transform;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Utility class for URL processing and cleaning.
 * Implements requirements from Section 4: URL Processing
 */
public class UrlProcessor {

    /**
     * Result of URL processing, containing the cleaned URL and any errors.
     */
    public static class ProcessResult {
        public final String url;
        public final boolean isValid;
        public final String error;

        public ProcessResult(String url, boolean isValid, String error) {
            this.url = url;
            this.isValid = isValid;
            this.error = error;
        }

        public static ProcessResult success(String url) {
            return new ProcessResult(url, true, null);
        }

        public static ProcessResult error(String url, String error) {
            return new ProcessResult(url, false, error);
        }
    }

    /**
     * Represents a query parameter with its name and value.
     */
    public static class QueryParam {
        public final String name;
        public final String value;
        public boolean keep;

        public QueryParam(String name, String value, boolean keep) {
            this.name = name;
            this.value = value;
            this.keep = keep;
        }
    }

    /**
     * Apply a list of transforms to a URL.
     * Requirement 4.1: Transform Application
     */
    public static ProcessResult applyTransforms(String url, List<Transform> transforms, Set<Integer> disabledIndices) {
        String result = url;
        
        for (int i = 0; i < transforms.size(); i++) {
            Transform transform = transforms.get(i);
            
            // Skip if not enabled in config or disabled for this URL
            if (!transform.isEnabled() || (disabledIndices != null && disabledIndices.contains(i))) {
                continue;
            }
            
            try {
                Pattern pattern = Pattern.compile(transform.getPattern());
                Matcher matcher = pattern.matcher(result);
                if (matcher.find()) {
                    result = matcher.replaceAll(transform.getReplacement());
                }
            } catch (PatternSyntaxException e) {
                // Requirement 9.1: Invalid regex patterns MUST NOT crash, SHOULD be skipped
                continue;
            }
        }
        
        // Requirement 4.3: Validate scheme
        if (!result.startsWith("http://") && !result.startsWith("https://")) {
            return ProcessResult.error(result, "Invalid URL scheme. URL must start with http:// or https://");
        }
        
        return ProcessResult.success(result);
    }

    /**
     * Check if a transform matches the given URL.
     */
    public static boolean transformMatches(String url, Transform transform) {
        try {
            Pattern pattern = Pattern.compile(transform.getPattern());
            return pattern.matcher(url).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /**
     * Check if a regex pattern is valid.
     */
    public static boolean isValidPattern(String pattern) {
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /**
     * Parse query parameters from a URL.
     * Requirement 3.6: Query Parameter List
     */
    public static List<QueryParam> parseQueryParams(String url, Set<String> allowedParams) {
        List<QueryParam> params = new ArrayList<>();
        
        try {
            URI uri = new URI(url);
            String query = uri.getRawQuery();
            
            if (query == null || query.isEmpty()) {
                return params;
            }
            
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                String name = keyValue[0];
                String value = keyValue.length > 1 ? keyValue[1] : "";
                
                // Requirement 3.6: Parameters in whitelist MUST be checked (kept) by default
                boolean keep = allowedParams.contains(name);
                params.add(new QueryParam(name, value, keep));
            }
        } catch (URISyntaxException e) {
            // Requirement 9.2: Malformed URLs MUST NOT crash
        }
        
        return params;
    }

    /**
     * Reconstruct URL with filtered query parameters.
     * Requirement 4.2: Query Parameter Filtering
     * Requirement 4.3: URL Reconstruction
     */
    public static String reconstructUrl(String url, List<QueryParam> params) {
        try {
            URI uri = new URI(url);
            
            // Build filtered query string
            StringBuilder queryBuilder = new StringBuilder();
            for (QueryParam param : params) {
                if (param.keep) {
                    if (queryBuilder.length() > 0) {
                        queryBuilder.append("&");
                    }
                    queryBuilder.append(param.name);
                    if (param.value != null && !param.value.isEmpty()) {
                        queryBuilder.append("=").append(param.value);
                    }
                }
            }
            
            // Reconstruct URL
            String query = queryBuilder.length() > 0 ? queryBuilder.toString() : null;
            
            URI newUri = new URI(
                uri.getScheme(),
                uri.getAuthority(),
                uri.getPath(),
                query,
                uri.getFragment()
            );
            
            return newUri.toString();
        } catch (URISyntaxException e) {
            // Requirement 9.2: If URL cannot be parsed, return unmodified
            return url;
        }
    }

    /**
     * Extract URL from text (for ACTION_SEND intents).
     * Requirement 2.2: URL Reception
     */
    public static String extractUrl(String text) {
        if (text == null) {
            return null;
        }
        
        // Try to find a URL pattern in the text
        Pattern urlPattern = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"
        );
        Matcher matcher = urlPattern.matcher(text);
        
        if (matcher.find()) {
            return matcher.group();
        }
        
        // If no URL found but text looks like it might be a URL, return it
        String trimmed = text.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        
        return null;
    }
}
