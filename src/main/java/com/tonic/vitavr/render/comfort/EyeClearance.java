package com.tonic.vitavr.render.comfort;

import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Darkens the view as an eye nears or enters scenery on the nine tiles around it, so walls are never seen inside.
 */
public final class EyeClearance
{
    private static final int TILE = 128;
    private static final double FADE_FROM_METRES = 0.08;
    private static final double DARK_FROM_METRES = 0.02;
    private static final double FADE_FROM_NEAR_PLANES = 3;
    private static final long GATHER_MILLIS = 1000;
    /** Level ray directions, tilted slightly off the axes so rays miss axis-aligned model edges. */
    private static final double[][] ACROSS = {{1, 0.013, 0.007}, {-1, 0.011, -0.009}, {0.007, 0.012, 1}, {-0.009, 0.014, -1}};

    private final Client client;
    private float[] faces = new float[0];
    private int count;
    private int gatheredX = Integer.MIN_VALUE;
    private int gatheredY = Integer.MIN_VALUE;
    private int gatheredPlane = -1;
    private long gatheredAt;

    public EyeClearance(Client client)
    {
        this.client = client;
    }

    /**
     * Gets the darkness for an eye at a world point.
     *
     * @param eye           The eye, across, down and along.
     * @param unitsPerMetre The game units per room metre.
     * @param nearPlane     The near plane, in game units.
     * @return The darkness, from 0 for clear to 1 for fully dark.
     */
    public double darkness(double[] eye, double unitsPerMetre, double nearPlane)
    {
        gather((int) Math.floor(eye[0] / TILE), (int) Math.floor(eye[2] / TILE));
        if (count == 0)
        {
            return 0;
        }
        if (inside(faces, count, eye))
        {
            return 1;
        }
        double fadeFrom = Math.max(FADE_FROM_METRES * unitsPerMetre, FADE_FROM_NEAR_PLANES * nearPlane);
        double darkFrom = Math.max(DARK_FROM_METRES * unitsPerMetre, nearPlane);
        return darkness(nearest(faces, count, eye), fadeFrom, darkFrom);
    }

    /**
     * Gets the darkness for a face distance, 0 beyond fadeFrom and 1 within darkFrom, smoothstep between.
     */
    static double darkness(double distance, double fadeFrom, double darkFrom)
    {
        if (distance >= fadeFrom)
        {
            return 0;
        }
        if (distance <= darkFrom)
        {
            return 1;
        }
        double along = (fadeFrom - distance) / (fadeFrom - darkFrom);
        return along * along * (3 - 2 * along);
    }

    private void gather(int tileX, int tileY)
    {
        int plane = client.getTopLevelWorldView().getPlane();
        long now = System.currentTimeMillis();
        if (tileX == gatheredX && tileY == gatheredY && plane == gatheredPlane && now - gatheredAt < GATHER_MILLIS)
        {
            return;
        }
        gatheredX = tileX;
        gatheredY = tileY;
        gatheredPlane = plane;
        gatheredAt = now;
        count = 0;

        Scene scene = client.getTopLevelWorldView().getScene();
        Tile[][][] tiles = scene.getTiles();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int x = tileX - 1; x <= tileX + 1; x++)
        {
            for (int y = tileY - 1; y <= tileY + 1; y++)
            {
                if (x < 0 || y < 0 || x >= tiles[plane].length || y >= tiles[plane][x].length || tiles[plane][x][y] == null)
                {
                    continue;
                }
                gatherTile(tiles[plane][x][y], seen);
            }
        }
    }

    private void gatherTile(Tile tile, Set<Object> seen)
    {
        for (GameObject object : tile.getGameObjects())
        {
            if (object != null && seen.add(object))
            {
                keep(object.getRenderable(), object.getX(), object.getZ(), object.getY(), object.getModelOrientation());
            }
        }
        WallObject wall = tile.getWallObject();
        if (wall != null && seen.add(wall))
        {
            keep(wall.getRenderable1(), wall.getX(), wall.getZ(), wall.getY(), 0);
            keep(wall.getRenderable2(), wall.getX(), wall.getZ(), wall.getY(), 0);
        }
        DecorativeObject decoration = tile.getDecorativeObject();
        if (decoration != null && seen.add(decoration))
        {
            keep(decoration.getRenderable(), decoration.getX() + decoration.getXOffset(), decoration.getZ(), decoration.getY() + decoration.getYOffset(), 0);
            keep(decoration.getRenderable2(), decoration.getX() + decoration.getXOffset2(), decoration.getZ(), decoration.getY() + decoration.getYOffset2(), 0);
        }
    }

    /**
     * Adds a model's placed faces as nine floats each.
     */
    private void keep(Renderable renderable, int x, int y, int z, int orientation)
    {
        Model model = renderable instanceof Model ? (Model) renderable : renderable == null ? null : renderable.getModel();
        if (model == null || model.getFaceCount() == 0)
        {
            return;
        }
        float[] xs = model.getVerticesX();
        float[] ys = model.getVerticesY();
        float[] zs = model.getVerticesZ();
        int[] first = model.getFaceIndices1();
        int[] second = model.getFaceIndices2();
        int[] third = model.getFaceIndices3();
        int[] colours = model.getFaceColors3();
        double sine = Perspective.SINE[orientation & 2047] / 65536.0;
        double cosine = Perspective.COSINE[orientation & 2047] / 65536.0;
        for (int face = 0; face < model.getFaceCount(); face++)
        {
            if (colours[face] == -2)
            {
                continue;
            }
            if (count + 9 > faces.length)
            {
                faces = Arrays.copyOf(faces, Math.max(1024, faces.length * 2));
            }
            for (int vertex : new int[]{first[face], second[face], third[face]})
            {
                faces[count++] = (float) (x + zs[vertex] * sine + xs[vertex] * cosine);
                faces[count++] = y + ys[vertex];
                faces[count++] = (float) (z + zs[vertex] * cosine - xs[vertex] * sine);
            }
        }
    }

    /**
     * Gets the distance from a point to the nearest triangle.
     */
    static double nearest(float[] triangles, int count, double[] point)
    {
        double nearest = Double.POSITIVE_INFINITY;
        for (int at = 0; at + 8 < count; at += 9)
        {
            nearest = Math.min(nearest, distance(point, triangles, at));
        }
        return nearest;
    }

    /**
     * Checks whether a point is inside the triangles, by odd crossings on every level ray.
     */
    static boolean inside(float[] triangles, int count, double[] point)
    {
        for (double[] direction : ACROSS)
        {
            int crossings = 0;
            for (int at = 0; at + 8 < count; at += 9)
            {
                if (crosses(point, direction, triangles, at))
                {
                    crossings++;
                }
            }
            if (crossings % 2 == 0)
            {
                return false;
            }
        }
        return true;
    }

    private static double distance(double[] p, float[] t, int at)
    {
        double ax = t[at];
        double ay = t[at + 1];
        double az = t[at + 2];
        double abx = t[at + 3] - ax;
        double aby = t[at + 4] - ay;
        double abz = t[at + 5] - az;
        double acx = t[at + 6] - ax;
        double acy = t[at + 7] - ay;
        double acz = t[at + 8] - az;
        double apx = p[0] - ax;
        double apy = p[1] - ay;
        double apz = p[2] - az;
        double d1 = abx * apx + aby * apy + abz * apz;
        double d2 = acx * apx + acy * apy + acz * apz;
        if (d1 <= 0 && d2 <= 0)
        {
            return length(apx, apy, apz);
        }
        double bpx = p[0] - t[at + 3];
        double bpy = p[1] - t[at + 4];
        double bpz = p[2] - t[at + 5];
        double d3 = abx * bpx + aby * bpy + abz * bpz;
        double d4 = acx * bpx + acy * bpy + acz * bpz;
        if (d3 >= 0 && d4 <= d3)
        {
            return length(bpx, bpy, bpz);
        }
        double vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0)
        {
            double v = d1 / (d1 - d3);
            return length(apx - abx * v, apy - aby * v, apz - abz * v);
        }
        double cpx = p[0] - t[at + 6];
        double cpy = p[1] - t[at + 7];
        double cpz = p[2] - t[at + 8];
        double d5 = abx * cpx + aby * cpy + abz * cpz;
        double d6 = acx * cpx + acy * cpy + acz * cpz;
        if (d6 >= 0 && d5 <= d6)
        {
            return length(cpx, cpy, cpz);
        }
        double vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0)
        {
            double w = d2 / (d2 - d6);
            return length(apx - acx * w, apy - acy * w, apz - acz * w);
        }
        double va = d3 * d6 - d5 * d4;
        if (va <= 0 && d4 - d3 >= 0 && d5 - d6 >= 0)
        {
            double w = (d4 - d3) / (d4 - d3 + d5 - d6);
            double bcx = t[at + 6] - t[at + 3];
            double bcy = t[at + 7] - t[at + 4];
            double bcz = t[at + 8] - t[at + 5];
            return length(bpx - bcx * w, bpy - bcy * w, bpz - bcz * w);
        }
        double denominator = 1 / (va + vb + vc);
        double v = vb * denominator;
        double w = vc * denominator;
        return length(apx - abx * v - acx * w, apy - aby * v - acy * w, apz - abz * v - acz * w);
    }

    /**
     * Checks whether a ray crosses a triangle from either side.
     */
    private static boolean crosses(double[] p, double[] direction, float[] t, int at)
    {
        double e1x = t[at + 3] - t[at];
        double e1y = t[at + 4] - t[at + 1];
        double e1z = t[at + 5] - t[at + 2];
        double e2x = t[at + 6] - t[at];
        double e2y = t[at + 7] - t[at + 1];
        double e2z = t[at + 8] - t[at + 2];
        double hx = direction[1] * e2z - direction[2] * e2y;
        double hy = direction[2] * e2x - direction[0] * e2z;
        double hz = direction[0] * e2y - direction[1] * e2x;
        double a = e1x * hx + e1y * hy + e1z * hz;
        if (Math.abs(a) < 1e-9)
        {
            return false;
        }
        double f = 1 / a;
        double sx = p[0] - t[at];
        double sy = p[1] - t[at + 1];
        double sz = p[2] - t[at + 2];
        double u = f * (sx * hx + sy * hy + sz * hz);
        if (u < 0 || u > 1)
        {
            return false;
        }
        double qx = sy * e1z - sz * e1y;
        double qy = sz * e1x - sx * e1z;
        double qz = sx * e1y - sy * e1x;
        double v = f * (direction[0] * qx + direction[1] * qy + direction[2] * qz);
        if (v < 0 || u + v > 1)
        {
            return false;
        }
        return f * (e2x * qx + e2y * qy + e2z * qz) > 1e-6;
    }

    private static double length(double x, double y, double z)
    {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
