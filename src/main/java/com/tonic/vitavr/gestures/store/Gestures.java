package com.tonic.vitavr.gestures.store;

import com.tonic.vitavr.gestures.model.Binding;
import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.Profile;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the store holds: the gesture library, the profiles, and the profile in use.
 */
@Data
public class Gestures
{
    public static final String DEFAULT_PROFILE = "Default";

    private List<Gesture> library = new ArrayList<>();
    private List<Profile> profiles = new ArrayList<>();
    private String active = DEFAULT_PROFILE;

    /**
     * @param id A gesture's id.
     * @return The gesture, or null.
     */
    public Gesture gesture(String id)
    {
        for (Gesture gesture : library)
        {
            if (gesture.getId().equals(id))
            {
                return gesture;
            }
        }
        return null;
    }

    /**
     * @param name A profile's name.
     * @return The profile, or null.
     */
    public Profile profile(String name)
    {
        for (Profile profile : profiles)
        {
            if (profile.getName().equals(name))
            {
                return profile;
            }
        }
        return null;
    }

    /**
     * @return The profile in use; there is always one once the store has loaded.
     */
    public Profile activeProfile()
    {
        Profile profile = profile(active);
        return profile != null ? profile : profiles.isEmpty() ? null : profiles.get(0);
    }

    /**
     * @param gestureId A gesture's id.
     * @return What the gesture does in the profile in use, or null when it is not bound there.
     */
    public Binding binding(String gestureId)
    {
        Profile profile = activeProfile();
        return profile == null ? null : profile.getBindings().get(gestureId);
    }

    /**
     * Gives the store at least one profile, makes the active name one that exists, and drops bindings of gestures
     * that are gone.
     */
    void tidy()
    {
        if (profiles.isEmpty())
        {
            profiles.add(Profile.named(DEFAULT_PROFILE));
        }
        if (profile(active) == null)
        {
            active = profiles.get(0).getName();
        }
        for (Profile profile : profiles)
        {
            profile.getBindings().keySet().removeIf(id -> gesture(id) == null);
        }
    }
}
