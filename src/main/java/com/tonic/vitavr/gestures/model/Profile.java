package com.tonic.vitavr.gestures.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named set of bindings, keyed by gesture id.
 */
@Data
public class Profile
{
    private String name = "";
    private Map<String, Binding> bindings = new LinkedHashMap<>();

    /**
     * @param name The profile's name.
     * @return An empty profile.
     */
    public static Profile named(String name)
    {
        Profile profile = new Profile();
        profile.setName(name);
        return profile;
    }
}
