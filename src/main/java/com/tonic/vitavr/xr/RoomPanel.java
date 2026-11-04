package com.tonic.vitavr.xr;

import com.tonic.vitavr.panels.AbstractPanel;
import com.tonic.vitavr.space.Billboard;
import com.tonic.vitavr.space.Pose;
import lombok.Getter;

/**
 * A VitaVR panel shown as its own layer in the room, opened in front of the head and worked by a pointer.
 */
public final class RoomPanel implements AutoCloseable
{
    @Getter
    private final AbstractPanel panel;
    private final FloatingLayer layer;
    @Getter
    private final Billboard board = new Billboard();
    private final double metres;
    private final double belowEyes;
    @Getter
    private boolean shown;

    /**
     * Creates the panel's layer in the OpenGL context current on the calling thread.
     *
     * @param headset The headset.
     * @param panel The panel.
     * @param metres The distance in front of the head, in metres.
     * @param belowEyes The distance of its centre below the eyes, in metres.
     */
    public RoomPanel(Headset headset, AbstractPanel panel, double metres, double belowEyes)
    {
        this.panel = panel;
        this.layer = new FloatingLayer(headset, panel.width(), panel.height());
        this.metres = metres;
        this.belowEyes = belowEyes;
    }

    /**
     * Opens the panel in front of the head.
     *
     * @param head The head pose, in the room.
     * @param pixelAtOneMetre The size of a pixel at one metre, in metres.
     */
    public void open(Pose head, double pixelAtOneMetre)
    {
        double[] ahead = new double[3];
        head.rotate(0, 0, -1, ahead);
        double level = Math.hypot(ahead[0], ahead[2]);
        double x = level < 1e-6 ? 0 : ahead[0] / level;
        double z = level < 1e-6 ? -1 : ahead[2] / level;
        board.stand(head.x() + x * metres, head.y() - belowEyes, head.z() + z * metres, head, panel.width(), panel.height(), pixelAtOneMetre * metres,
                panel.width() / 2.0, panel.height() / 2.0);
        layer.place(board);
        panel.repaint();
        layer.upload(panel.pixels());
        shown = true;
    }

    /**
     * Hides the panel until it is opened again.
     */
    public void hide()
    {
        shown = false;
        panel.leave();
        layer.clear();
    }

    /**
     * Hovers the panel where the pointer crosses it.
     *
     * @param aim The pointer pose in the room, or null for none.
     * @return Whether the pointer is on the panel.
     */
    public boolean point(Pose aim)
    {
        int[] on = shown ? board.crossing(aim) : null;
        if (on == null)
        {
            panel.leave();
            return false;
        }
        panel.hover(on[0], on[1]);
        return true;
    }

    /**
     * Presses the panel where the pointer crosses it.
     *
     * @param aim The pointer pose, in the room.
     * @return Whether the pointer was on the panel.
     */
    public boolean press(Pose aim)
    {
        int[] on = shown ? board.crossing(aim) : null;
        if (on == null)
        {
            return false;
        }
        panel.press(on[0], on[1]);
        return true;
    }

    /**
     * Gets the distance from the pointer to the panel.
     * @param aim The pointer pose, in the room.
     * @return The distance in metres, or infinity if it misses.
     */
    public double reach(Pose aim)
    {
        return shown ? board.reach(aim) : Double.POSITIVE_INFINITY;
    }

    /**
     * Repaints the panel and uploads it if anything changed.
     */
    public void refresh()
    {
        if (shown && panel.repaint())
        {
            layer.upload(panel.pixels());
        }
    }

    /**
     * @return A texture with the panel's last picture, right way up.
     */
    public int getPicture()
    {
        return layer.getPicture();
    }

    /**
     * @return The layer's address, for the frame's layer list.
     */
    public long address()
    {
        return layer.address();
    }

    /**
     * Frees the layer's images. Safe to call more than once.
     */
    @Override
    public void close()
    {
        layer.close();
    }
}
