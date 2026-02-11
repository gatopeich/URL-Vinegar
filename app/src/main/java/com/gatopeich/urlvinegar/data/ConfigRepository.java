package com.gatopeich.urlvinegar.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for persisting and loading configuration.
 * Requirement 5.4: Persistence using SharedPreferences
 */
public class ConfigRepository {
    private static final String PREFS_NAME = "url_vinegar_config";
    private static final String KEY_TRANSFORMS = "transforms";

    private final SharedPreferences prefs;
    private static ConfigRepository instance;

    private ConfigRepository(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ConfigRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigRepository(context);
        }
        return instance;
    }

    /**
     * Load transforms from SharedPreferences.
     * Returns default transforms if none are stored.
     */
    public List<Transform> loadTransforms() {
        String json = prefs.getString(KEY_TRANSFORMS, null);
        if (json == null) {
            return getDefaultTransforms();
        }
        try {
            return parseTransforms(json);
        } catch (JSONException e) {
            return getDefaultTransforms();
        }
    }

    /**
     * Save transforms to SharedPreferences.
     */
    public void saveTransforms(List<Transform> transforms) {
        try {
            String json = serializeTransforms(transforms);
            prefs.edit().putString(KEY_TRANSFORMS, json).apply();
        } catch (JSONException e) {
            // Ignore save errors
        }
    }

    /**
     * Requirement 5.3: Default Configuration
     * Default transforms for common tracking parameters.
     */
    private List<Transform> getDefaultTransforms() {
        List<Transform> transforms = new ArrayList<>();
        
        // YouTube URL shortener - convert to youtu.be format (preserves timestamp with &t=)
        transforms.add(new Transform(
            "Shorten YouTube URL",
            "https?://(?:www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+)(?:&t=([0-9]+)s?)?.*",
            "https://youtu.be/$1?t=$2",
            true
        ));
        
        // Clean up youtu.be URLs with empty timestamp
        transforms.add(new Transform(
            "Clean YouTube timestamp",
            "(https://youtu\\.be/[a-zA-Z0-9_-]+)\\?t=$",
            "$1",
            true
        ));
        
        // UTM parameters removal
        transforms.add(new Transform(
            "Remove UTM parameters",
            "[?&](utm_[a-z_]+)=[^&]*",
            "",
            true
        ));
        
        // Facebook click ID
        transforms.add(new Transform(
            "Remove Facebook click ID",
            "[?&]fbclid=[^&]*",
            "",
            true
        ));
        
        // Google click ID
        transforms.add(new Transform(
            "Remove Google click ID",
            "[?&]gclid=[^&]*",
            "",
            true
        ));
        
        // Amazon referral tag
        transforms.add(new Transform(
            "Remove Amazon referral tag",
            "[?&]tag=[^&]*",
            "",
            true
        ));
        
        // Generic affiliate/tracking parameters
        transforms.add(new Transform(
            "Remove affiliate tracking",
            "[?&](ref|aff|affiliate|campaign|source|medium)=[^&]*",
            "",
            true
        ));
        
        // Clean up double ? or & characters after removal
        transforms.add(new Transform(
            "Clean up query string",
            "(\\?)&+|&+(?=&)|&+$",
            "$1",
            true
        ));
        
        // Remove trailing ? if no parameters left
        transforms.add(new Transform(
            "Remove empty query string",
            "\\?$",
            "",
            true
        ));
        
        return transforms;
    }

    private String serializeTransforms(List<Transform> transforms) throws JSONException {
        JSONArray array = new JSONArray();
        for (Transform t : transforms) {
            JSONObject obj = new JSONObject();
            obj.put("name", t.getName());
            obj.put("pattern", t.getPattern());
            obj.put("replacement", t.getReplacement());
            obj.put("enabled", t.isEnabled());
            array.put(obj);
        }
        return array.toString();
    }

    private List<Transform> parseTransforms(String json) throws JSONException {
        List<Transform> transforms = new ArrayList<>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            transforms.add(new Transform(
                obj.getString("name"),
                obj.getString("pattern"),
                obj.optString("replacement", ""),
                obj.optBoolean("enabled", true)
            ));
        }
        return transforms;
    }
}
