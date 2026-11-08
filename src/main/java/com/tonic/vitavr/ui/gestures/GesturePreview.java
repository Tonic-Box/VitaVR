package com.tonic.vitavr.ui.gestures;

import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.model.Sample;
import com.tonic.vitavr.gestures.model.Take;
import com.tonic.vitavr.gestures.store.GestureJson;
import com.tonic.vitavr.ui.Style;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.List;

/**
 * A gesture played back on a simple figure seen from behind: the recorded hand paths replay in real time with a fading
 * trail, take after take, a spot's sphere pulses as it is squeezed, and a flick's hand turns; the view turns slowly
 * until dragged, and a double click puts it back.
 */
final class GesturePreview extends JComponent
{
    private static final int FRAME_MILLIS = 16;
    private static final double AUTO_TURN = Math.toRadians(10);
    private static final double DRAG_RADIANS_PER_PIXEL = 0.01;
    private static final double NECK = 0.12;
    private static final double TRAIL_SECONDS = 0.8;
    private static final Color FIGURE = new Color(150, 150, 160);
    private static final Color FLOOR = new Color(70, 70, 76);
    private static final Color PATH = new Color(110, 140, 170, 90);
    private static final Color[] HANDS = {new Color(240, 170, 80), new Color(90, 200, 240)};
    private static final Color SPOT = new Color(220, 190, 90);

    private final PreviewCamera camera = new PreviewCamera();
    private final Timer timer = new Timer(FRAME_MILLIS, e -> step());
    private final double[] projected = new double[3];
    private final double[] other = new double[3];
    private final double[] placeAt = new double[7];
    private Gesture gesture;
    private long started = System.nanoTime();
    private long last = System.nanoTime();
    private boolean turning = true;
    private int dragX;
    private int dragY;

    GesturePreview()
    {
        setOpaque(true);
        setBackground(Style.BG_SECONDARY);
        MouseAdapter mouse = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                dragX = e.getX();
                dragY = e.getY();
                turning = false;
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                camera.orbit((e.getX() - dragX) * DRAG_RADIANS_PER_PIXEL, (e.getY() - dragY) * DRAG_RADIANS_PER_PIXEL);
                dragX = e.getX();
                dragY = e.getY();
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2)
                {
                    camera.reset();
                    turning = true;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setToolTipText("Drag to turn the view; double click to put it back");
    }

    /**
     * @param shown The gesture to play, or null for none; it is copied.
     */
    void show(Gesture shown)
    {
        gesture = shown == null ? null : GestureJson.copy(shown, Gesture.class);
        started = System.nanoTime();
        if (gesture != null)
        {
            double head = Math.max(1.0, gesture.getHeadHeight());
            camera.target(0, -head * 0.3, 0.15);
            camera.distance(head * 1.6);
        }
        repaint();
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify()
    {
        timer.stop();
        super.removeNotify();
    }

    private void step()
    {
        long now = System.nanoTime();
        double seconds = (now - last) / 1e9;
        last = now;
        if (turning)
        {
            camera.orbit(AUTO_TURN * seconds, 0);
        }
        if (isShowing())
        {
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (gesture == null || gesture.getTakes().isEmpty())
            {
                g.setColor(Style.TEXT_MUTED);
                g.setFont(Style.font(Style.FONT_SIZE));
                g.drawString(gesture == null ? "Choose a gesture to see it" : "This gesture has no takes to play", 16, 24);
                return;
            }
            paintScene(g);
        }
        finally
        {
            g.dispose();
        }
    }

    private void paintScene(Graphics2D g)
    {
        double head = Math.max(1.0, gesture.getHeadHeight());
        double floor = -(head - NECK);
        floor(g, floor);
        List<Take> takes = gesture.getTakes();
        double[] lengths = new double[takes.size()];
        for (int i = 0; i < takes.size(); i++)
        {
            lengths[i] = takes.get(i).seconds();
        }
        double[] now = TakeClock.at((System.nanoTime() - started) / 1e9, lengths);
        Take take = takes.get((int) now[0]);
        double t = now[1];
        boolean resting = t >= take.seconds() - 1e-9;
        double[][] hands = new double[2][];
        for (int hand = 0; hand < 2; hand++)
        {
            List<Sample> samples = take.hand(hand);
            if (samples.isEmpty())
            {
                continue;
            }
            path(g, samples, hand, t);
            TakeClock.place(samples, t, placeAt);
            hands[hand] = placeAt.clone();
        }
        figure(g, floor, hands);
        if (gesture.getKind() == GestureKind.SPOT)
        {
            spot(g, resting);
        }
        for (int hand = 0; hand < 2; hand++)
        {
            if (hands[hand] != null)
            {
                handDot(g, hands[hand], hand);
            }
        }
        g.setColor(Style.TEXT_MUTED);
        g.setFont(Style.font(Style.FONT_SIZE));
        g.drawString(gesture.getName() + "  ·  " + gesture.getKind() + ", " + gesture.getHand() + "  ·  take " + ((int) now[0] + 1) + " of " + takes.size(), 12, 20);
    }

    private void floor(Graphics2D g, double y)
    {
        g.setStroke(new BasicStroke(1));
        g.setColor(FLOOR);
        for (int i = -4; i <= 4; i++)
        {
            line(g, i * 0.25, y, -1, i * 0.25, y, 1);
            line(g, -1, y, i * 0.25, 1, y, i * 0.25);
        }
        g.setStroke(new BasicStroke(2));
        g.setColor(Style.ACCENT_BRIGHT);
        line(g, 0, y, 0.2, 0, y, 0.7);
        line(g, 0, y, 0.7, -0.06, y, 0.6);
        line(g, 0, y, 0.7, 0.06, y, 0.6);
    }

    private void figure(Graphics2D g, double floor, double[][] hands)
    {
        g.setColor(FIGURE);
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double shoulderY = -0.05;
        double hipY = shoulderY - 0.5;
        line(g, 0, NECK - 0.02, 0, 0, shoulderY, 0);
        line(g, -0.2, shoulderY, 0, 0.2, shoulderY, 0);
        line(g, -0.2, shoulderY, 0, -0.13, hipY, 0);
        line(g, 0.2, shoulderY, 0, 0.13, hipY, 0);
        line(g, -0.13, hipY, 0, 0.13, hipY, 0);
        line(g, -0.1, hipY, 0, -0.12, floor, 0.02);
        line(g, 0.1, hipY, 0, 0.12, floor, 0.02);
        for (int hand = 0; hand < 2; hand++)
        {
            double side = hand == 0 ? -0.2 : 0.2;
            double[] at = hands[hand] != null ? hands[hand] : new double[]{side * 1.25, hipY + 0.05, 0.05};
            double elbowX = (side + at[0]) / 2 + side * 0.15;
            double elbowY = (shoulderY + at[1]) / 2 - 0.08;
            double elbowZ = at[2] / 2 - 0.05;
            line(g, side, shoulderY, 0, elbowX, elbowY, elbowZ);
            line(g, elbowX, elbowY, elbowZ, at[0], at[1], at[2]);
        }
        camera.project(0, NECK + 0.02, 0, getWidth(), getHeight(), projected);
        double radius = pixels(0.11, projected[2]);
        g.draw(new Ellipse2D.Double(projected[0] - radius, projected[1] - radius, radius * 2, radius * 2));
        camera.project(0, NECK + 0.02, 0.14, getWidth(), getHeight(), other);
        g.setStroke(new BasicStroke(2));
        g.draw(new Line2D.Double(projected[0], projected[1], other[0], other[1]));
    }

    private void path(Graphics2D g, List<Sample> samples, int hand, double t)
    {
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Color colour = HANDS[hand];
        for (int i = 1; i < samples.size(); i++)
        {
            Sample a = samples.get(i - 1);
            Sample b = samples.get(i);
            if (b.getT() <= t && t - b.getT() <= TRAIL_SECONDS)
            {
                int alpha = (int) Math.round(255 * (1 - (t - b.getT()) / TRAIL_SECONDS));
                g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), Math.max(30, alpha)));
            }
            else
            {
                g.setColor(PATH);
            }
            line(g, a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        }
    }

    private void handDot(Graphics2D g, double[] at, int hand)
    {
        camera.project(at[0], at[1], at[2], getWidth(), getHeight(), projected);
        double radius = Math.max(4, pixels(0.035, projected[2]));
        g.setColor(HANDS[hand]);
        g.fill(new Ellipse2D.Double(projected[0] - radius, projected[1] - radius, radius * 2, radius * 2));
        if (gesture.getKind() == GestureKind.FLICK)
        {
            double[] pointing = pointing(at);
            camera.project(at[0] + pointing[0] * 0.12, at[1] + pointing[1] * 0.12, at[2] + pointing[2] * 0.12, getWidth(), getHeight(), other);
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(projected[0], projected[1], other[0], other[1]));
        }
    }

    /**
     * The way the hand points, in the body frame: the controller's forward, -z in the room, turned by the sample's
     * orientation, which is kept in the room's axes with the body's turn taken off, so its z is the body's backward.
     */
    private static double[] pointing(double[] at)
    {
        double qx = at[3];
        double qy = at[4];
        double qz = at[5];
        double qw = at[6];
        double vx = 0;
        double vy = 0;
        double vz = -1;
        double tx = 2 * (qy * vz - qz * vy);
        double ty = 2 * (qz * vx - qx * vz);
        double tz = 2 * (qx * vy - qy * vx);
        double rx = vx + qw * tx + (qy * tz - qz * ty);
        double ry = vy + qw * ty + (qz * tx - qx * tz);
        double rz = vz + qw * tz + (qx * ty - qy * tx);
        return new double[]{rx, ry, -rz};
    }

    private void spot(Graphics2D g, boolean squeezing)
    {
        double[] centre = gesture.getCentre();
        camera.project(centre[0], centre[1], centre[2], getWidth(), getHeight(), projected);
        double pulse = squeezing ? 1 + 0.15 * Math.sin((System.nanoTime() - started) / 1e9 * 12) : 1;
        double radius = pixels(gesture.getRadius() * pulse, projected[2]);
        g.setColor(new Color(SPOT.getRed(), SPOT.getGreen(), SPOT.getBlue(), squeezing ? 110 : 50));
        g.fill(new Ellipse2D.Double(projected[0] - radius, projected[1] - radius, radius * 2, radius * 2));
        g.setColor(SPOT);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Ellipse2D.Double(projected[0] - radius, projected[1] - radius, radius * 2, radius * 2));
    }

    private double pixels(double metres, double depth)
    {
        double focal = Math.min(getWidth(), getHeight()) * 1.1;
        return metres * focal / Math.max(0.05, depth);
    }

    private void line(Graphics2D g, double x1, double y1, double z1, double x2, double y2, double z2)
    {
        camera.project(x1, y1, z1, getWidth(), getHeight(), projected);
        camera.project(x2, y2, z2, getWidth(), getHeight(), other);
        if (projected[2] <= 0.05 || other[2] <= 0.05)
        {
            return;
        }
        g.draw(new Line2D.Double(projected[0], projected[1], other[0], other[1]));
    }
}
