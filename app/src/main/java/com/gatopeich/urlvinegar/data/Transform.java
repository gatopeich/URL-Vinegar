package com.gatopeich.urlvinegar.data;

/**
 * Represents a URL transform rule with regex pattern and replacement.
 * Requirement 5.2: Transform Configuration
 */
public class Transform {
    private String name;
    private String pattern;
    private String replacement;
    private boolean enabled;

    public Transform(String name, String pattern, String replacement, boolean enabled) {
        this.name = name;
        this.pattern = pattern;
        this.replacement = replacement;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Creates a copy of this transform.
     */
    public Transform copy() {
        return new Transform(name, pattern, replacement, enabled);
    }
}
