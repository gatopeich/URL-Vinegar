package com.gatopeich.urlvinegar.data;

/**
 * Represents an allowed (whitelisted) query parameter.
 * Requirement 5.3: Allowed Parameters Configuration
 */
public class AllowedParameter {
    private String name;
    private String description;

    public AllowedParameter(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public AllowedParameter(String name) {
        this(name, "");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Creates a copy of this parameter.
     */
    public AllowedParameter copy() {
        return new AllowedParameter(name, description);
    }
}
