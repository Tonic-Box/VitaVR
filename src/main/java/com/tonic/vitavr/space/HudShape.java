package com.tonic.vitavr.space;

/**
 * Represents the interface panel in the room, cut into chat, middle and inventory columns on a cylinder round the head.
 * Without curved layer support each column is a flat chord instead.
 */
public final class HudShape
{
    public static final int LEFT = 0;
    public static final int MIDDLE = 1;
    public static final int RIGHT = 2;
    public static final int COLUMNS = 3;

    private static final double BELOW_EYES = 0.15;

    private final int pixelWidth;
    private final int pixelHeight;
    private final boolean curved;
    private final int usualLeft;
    private final int usualRight;
    private final int[] fromPixel = new int[COLUMNS];
    private final int[] toPixel = new int[COLUMNS];
    private final double[] perPixel = new double[COLUMNS];
    private final double[] fromAngle = new double[COLUMNS];
    private final double[] toAngle = new double[COLUMNS];
    private double radius;
    private double pixelRadians;
    private double leftScale;
    private double rightScale;
    private double apartRadians;
    private double centreX;
    private double centreY;
    private double centreZ;
    private double yaw;
    private boolean placed;

    /**
     * Creates a panel shape.
     *
     * @param pixelWidth  The picture width, in pixels.
     * @param pixelHeight The picture height, in pixels.
     * @param leftPixels  The left column width, in pixels.
     * @param rightPixels The right column width, in pixels.
     * @param curved      Whether the columns are arcs instead of flat chords.
     * @throws IllegalArgumentException if a size is not positive or the sides leave no middle
     */
    public HudShape(int pixelWidth, int pixelHeight, int leftPixels, int rightPixels, boolean curved)
    {
        if (pixelWidth < 1 || pixelHeight < 1 || leftPixels < 1 || rightPixels < 1 || leftPixels + rightPixels >= pixelWidth)
        {
            throw new IllegalArgumentException("sizes must be positive and the sides must leave a middle");
        }
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.curved = curved;
        this.usualLeft = leftPixels;
        this.usualRight = rightPixels;
        cut(leftPixels, rightPixels);
    }

    private void cut(int leftPixels, int rightPixels)
    {
        fromPixel[LEFT] = 0;
        toPixel[LEFT] = leftPixels;
        fromPixel[MIDDLE] = leftPixels;
        toPixel[MIDDLE] = pixelWidth - rightPixels;
        fromPixel[RIGHT] = pixelWidth - rightPixels;
        toPixel[RIGHT] = pixelWidth;
    }

    /**
     * Sets the side column widths, keeping the panel in place.
     *
     * @param leftPixels  The left column width, in pixels.
     * @param rightPixels The right column width, in pixels.
     * @throws IllegalArgumentException if either is not positive or the sides leave no middle
     */
    public void columns(int leftPixels, int rightPixels)
    {
        if (leftPixels < 1 || rightPixels < 1 || leftPixels + rightPixels >= pixelWidth)
        {
            throw new IllegalArgumentException("the sides must be positive and leave a middle");
        }
        cut(leftPixels, rightPixels);
        if (radius > 0)
        {
            arrange();
        }
    }

    /**
     * Sizes the panel and clears its placement.
     *
     * @param radius        The distance from the head, in metres.
     * @param pixelRadians  The angle of one middle pixel, in radians.
     * @param leftScale     The left column scale relative to the middle.
     * @param rightScale    The right column scale relative to the middle.
     * @param apartRadians  The gap between each side and the middle, in radians.
     * @throws IllegalArgumentException if any is out of range, or the panel would wrap all the way round
     */
    public void size(double radius, double pixelRadians, double leftScale, double rightScale, double apartRadians)
    {
        if (!(radius > 0) || !(pixelRadians > 0) || !(leftScale > 0) || !(rightScale > 0) || !(apartRadians >= 0))
        {
            throw new IllegalArgumentException("radius, pixelRadians and the scales must be above zero and apartRadians at least zero");
        }
        this.pixelRadians = pixelRadians;
        this.leftScale = leftScale;
        this.rightScale = rightScale;
        this.apartRadians = apartRadians;
        arrange();
        this.radius = radius;
        placed = false;
    }

    /**
     * Resizes the side columns, keeping the panel in place.
     *
     * @param leftScale    The left column scale relative to the middle.
     * @param rightScale   The right column scale relative to the middle.
     * @param apartRadians The gap between each side and the middle, in radians.
     * @throws IllegalArgumentException if any is out of range, or the panel would wrap all the way round
     */
    public void sides(double leftScale, double rightScale, double apartRadians)
    {
        if (!(leftScale > 0) || !(rightScale > 0) || !(apartRadians >= 0))
        {
            throw new IllegalArgumentException("the scales must be above zero and apartRadians at least zero");
        }
        this.leftScale = leftScale;
        this.rightScale = rightScale;
        this.apartRadians = apartRadians;
        if (radius > 0)
        {
            arrange();
        }
    }

    /**
     * Lays out column angles from the usual side widths, so changing the cut only moves pixels at the cuts.
     */
    private void arrange()
    {
        perPixel[LEFT] = pixelRadians * leftScale;
        perPixel[MIDDLE] = pixelRadians;
        perPixel[RIGHT] = pixelRadians * rightScale;
        double usualMiddle = perPixel[MIDDLE] * (pixelWidth - usualLeft - usualRight);
        double middleCentre = (usualLeft + pixelWidth - usualRight) / 2.0;
        fromAngle[MIDDLE] = (fromPixel[MIDDLE] - middleCentre) * perPixel[MIDDLE];
        toAngle[MIDDLE] = (toPixel[MIDDLE] - middleCentre) * perPixel[MIDDLE];
        fromAngle[LEFT] = -usualMiddle / 2 - apartRadians - perPixel[LEFT] * usualLeft;
        toAngle[LEFT] = fromAngle[LEFT] + perPixel[LEFT] * width(LEFT);
        toAngle[RIGHT] = usualMiddle / 2 + apartRadians + perPixel[RIGHT] * usualRight;
        fromAngle[RIGHT] = toAngle[RIGHT] - perPixel[RIGHT] * width(RIGHT);
        if (toAngle[RIGHT] - fromAngle[LEFT] >= 2 * Math.PI)
        {
            throw new IllegalArgumentException("the panel cannot wrap all the way round the head");
        }
    }

    /**
     * Places the panel level round the head, facing its yaw.
     *
     * @param head The head pose in the room.
     */
    public void place(Pose head)
    {
        double[] ahead = new double[3];
        head.rotate(0, 0, -1, ahead);
        yaw = Math.atan2(-ahead[0], -ahead[2]);
        centreX = head.x();
        centreY = head.y() - BELOW_EYES;
        centreZ = head.z();
        placed = true;
    }

    /** Clears the panel placement. */
    public void clear()
    {
        placed = false;
    }

    public boolean placed()
    {
        return placed;
    }

    /**
     * Gets where a pointer hits the panel, including past the outer edges for drags.
     *
     * @param aim The pointer pose in the room.
     * @return The distance, pixel x, pixel y and 1 if on the panel or 0 if off; x is -1 between columns; or null on a miss.
     */
    public double[] crossing(Pose aim)
    {
        if (!placed || aim == null)
        {
            return null;
        }
        double[] direction = new double[3];
        aim.rotate(0, 0, -1, direction);
        return curved ? crossArcs(aim, direction) : crossChords(aim, direction);
    }

    private double[] crossArcs(Pose aim, double[] direction)
    {
        double px = aim.x() - centreX;
        double pz = aim.z() - centreZ;
        double a = direction[0] * direction[0] + direction[2] * direction[2];
        if (a < 1e-12)
        {
            return null;
        }
        double b = 2 * (px * direction[0] + pz * direction[2]);
        double c = px * px + pz * pz - radius * radius;
        double root = b * b - 4 * a * c;
        if (root < 0)
        {
            return null;
        }
        double t = (-b + Math.sqrt(root)) / (2 * a);
        if (t <= 0)
        {
            return null;
        }
        double hx = px + direction[0] * t;
        double hz = pz + direction[2] * t;
        double up = aim.y() + direction[1] * t - centreY;
        double across = hx * Math.cos(yaw) - hz * Math.sin(yaw);
        double forward = -hx * Math.sin(yaw) - hz * Math.cos(yaw);
        double angle = Math.atan2(across, forward);
        int column = columnAt(angle);
        if (column < 0)
        {
            return new double[] {t, -1, -1, 0};
        }
        double x = fromPixel[column] + (angle - fromAngle[column]) / perPixel[column];
        double y = pixelHeight / 2.0 - up / (radius * perPixel[column]);
        boolean on = x >= fromPixel[column] && x < toPixel[column] && y >= 0 && y < pixelHeight;
        return new double[] {t, Math.floor(x), Math.floor(y), on ? 1 : 0};
    }

    /** Gets the column at an angle, outer columns extending beyond their edges, or -1 between columns. */
    private int columnAt(double angle)
    {
        if (angle < toAngle[LEFT])
        {
            return LEFT;
        }
        if (angle > fromAngle[RIGHT])
        {
            return RIGHT;
        }
        if (angle >= fromAngle[MIDDLE] && angle <= toAngle[MIDDLE])
        {
            return MIDDLE;
        }
        return -1;
    }

    private double[] crossChords(Pose aim, double[] direction)
    {
        double[] best = null;
        double[][] corners = new double[4][3];
        for (int column = 0; column < COLUMNS; column++)
        {
            chord(column, fromAngle[column], toAngle[column], corners);
            double[] hit = crossQuad(aim, direction, corners);
            if (hit == null)
            {
                continue;
            }
            hit[1] = Math.floor(fromPixel[column] + hit[1] * width(column));
            hit[2] = Math.floor(hit[2] * pixelHeight);
            if (best == null || hit[3] > best[3] || hit[3] == best[3] && hit[0] < best[0])
            {
                best = hit;
            }
        }
        return best;
    }

    /** Gets a pointer hit on a quad: distance, x and y from 0 to 1, and 1 if on it. */
    private static double[] crossQuad(Pose aim, double[] direction, double[][] corners)
    {
        double[] u = minus(corners[1], corners[0]);
        double[] v = minus(corners[3], corners[0]);
        double[] normal = {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
        double facing = dot(direction, normal);
        if (Math.abs(facing) < 1e-12)
        {
            return null;
        }
        double t = dot(minus(corners[0], new double[] {aim.x(), aim.y(), aim.z()}), normal) / facing;
        if (t <= 0)
        {
            return null;
        }
        double[] hit = {aim.x() + direction[0] * t - corners[0][0], aim.y() + direction[1] * t - corners[0][1], aim.z() + direction[2] * t - corners[0][2]};
        double across = dot(hit, u) / dot(u, u);
        double down = dot(hit, v) / dot(v, v);
        boolean on = across >= 0 && across < 1 && down >= 0 && down < 1;
        return new double[] {t, across, down, on ? 1 : 0};
    }

    /**
     * Gets the panel as flat strips in the room.
     *
     * @param stripsPerColumn The strips per curved column; flat columns use one.
     * @return Each strip's top left, top right, bottom right and bottom left corners, left to right.
     */
    public double[][][] strips(int stripsPerColumn)
    {
        int each = curved ? Math.max(1, stripsPerColumn) : 1;
        double[][][] out = new double[COLUMNS * each][4][3];
        for (int column = 0; column < COLUMNS; column++)
        {
            for (int strip = 0; strip < each; strip++)
            {
                double from = fromAngle[column] + (toAngle[column] - fromAngle[column]) * strip / each;
                double to = fromAngle[column] + (toAngle[column] - fromAngle[column]) * (strip + 1) / each;
                chord(column, from, to, out[column * each + strip]);
            }
        }
        return out;
    }

    /**
     * Gets the picture range each strip covers.
     *
     * @param stripsPerColumn The same value passed to strips.
     * @return Each strip's start and end x, from 0 to 1.
     */
    public double[][] stripsAcross(int stripsPerColumn)
    {
        int each = curved ? Math.max(1, stripsPerColumn) : 1;
        double[][] out = new double[COLUMNS * each][2];
        for (int column = 0; column < COLUMNS; column++)
        {
            for (int strip = 0; strip < each; strip++)
            {
                out[column * each + strip][0] = (fromPixel[column] + (double) width(column) * strip / each) / pixelWidth;
                out[column * each + strip][1] = (fromPixel[column] + (double) width(column) * (strip + 1) / each) / pixelWidth;
            }
        }
        return out;
    }

    /** Gets the corners of a column's flat piece between two angles. */
    private void chord(int column, double from, double to, double[][] out)
    {
        double half = tall(column) / 2;
        arcPoint(from, half, out[0]);
        arcPoint(to, half, out[1]);
        arcPoint(to, -half, out[2]);
        arcPoint(from, -half, out[3]);
    }

    /** Gets a cylinder point at an angle from the middle, right positive, and a height above it. */
    private void arcPoint(double angle, double up, double[] out)
    {
        double across = Math.sin(angle) * radius;
        double forward = Math.cos(angle) * radius;
        out[0] = centreX + across * Math.cos(yaw) - forward * Math.sin(yaw);
        out[1] = centreY + up;
        out[2] = centreZ - across * Math.sin(yaw) - forward * Math.cos(yaw);
    }

    private static double[] minus(double[] a, double[] b)
    {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double dot(double[] a, double[] b)
    {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /**
     * @param column The column.
     * @return The column's first pixel x.
     */
    public int fromPixel(int column)
    {
        return fromPixel[column];
    }

    /**
     * @param column The column.
     * @return The column width, in pixels.
     */
    public int width(int column)
    {
        return toPixel[column] - fromPixel[column];
    }

    /**
     * @param column The column.
     * @return The column height, in metres.
     */
    public double tall(int column)
    {
        return radius * perPixel[column] * pixelHeight;
    }

    /**
     * @param column The column.
     * @return The column's arc angle, in radians.
     */
    public double angle(int column)
    {
        return toAngle[column] - fromAngle[column];
    }

    /**
     * @param column The column.
     * @return The yaw of the column's middle, in radians.
     */
    public double yawOf(int column)
    {
        return yaw - (fromAngle[column] + toAngle[column]) / 2;
    }

    /**
     * Gets the corners of a column's flat chord.
     *
     * @param column The column.
     * @param out    Receives the top left, top right, bottom right and bottom left corners in the room.
     */
    public void chordCorners(int column, double[][] out)
    {
        chord(column, fromAngle[column], toAngle[column], out);
    }

    /** @return The cylinder centre at panel middle height, across, up and back. */
    public double[] centre()
    {
        return new double[] {centreX, centreY, centreZ};
    }

    /** @return The panel radius, in metres. */
    public double radius()
    {
        return radius;
    }

    public boolean curved()
    {
        return curved;
    }
}
