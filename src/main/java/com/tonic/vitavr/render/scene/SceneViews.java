package com.tonic.vitavr.render.scene;

import com.tonic.vitavr.render.EyesTarget;
import com.tonic.vitavr.render.recording.Recording;
import com.tonic.vitavr.space.View;
import com.tonic.vitavr.space.placement.Placement;

/**
 * The views drawn each frame: both eyes, then the steadied recording camera while it records.
 */
public final class SceneViews
{
    private static final int EYES = 2;

    private final EyesTarget eyes;
    private final float[][] eyeMatrices;
    private final Placement placement;
    private final Recording recording;

    /**
     * Creates the views.
     *
     * @param eyes The eyes' target.
     * @param eyeMatrices Each eye's world to clip matrix, set each frame.
     * @param placement Where the eyes are.
     * @param recording The session's recording.
     */
    public SceneViews(EyesTarget eyes, float[][] eyeMatrices, Placement placement, Recording recording)
    {
        this.eyes = eyes;
        this.eyeMatrices = eyeMatrices;
        this.placement = placement;
        this.recording = recording;
    }

    /**
     * @return How many views are drawn this frame.
     */
    public int count()
    {
        return recording.isSpectating() ? EYES + 1 : EYES;
    }

    /**
     * Binds a view's target and viewport for drawing.
     *
     * @param view The view index.
     */
    public void bind(int view)
    {
        if (view < EYES)
        {
            eyes.use(view);
        }
        else
        {
            recording.useSpectator();
        }
    }

    /**
     * @param view The view index.
     * @return The view's world to clip matrix.
     */
    public float[] worldToClip(int view)
    {
        return view < EYES ? eyeMatrices[view] : recording.getSpectatorMatrix();
    }

    /**
     * @param view The view index.
     * @return Where the view is and which way it faces.
     */
    public View view(int view)
    {
        return view < EYES ? placement.eye(view) : recording.getSpectatorView();
    }
}
