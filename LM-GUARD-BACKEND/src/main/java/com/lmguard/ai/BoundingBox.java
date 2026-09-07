package com.lmguard.ai;

/**
 * A pixel region on the package image, origin at the top-left of the image.
 *
 * @param x      left edge in pixels
 * @param y      top edge in pixels
 * @param width  region width in pixels
 * @param height region height in pixels
 */
public record BoundingBox(Integer x, Integer y, Integer width, Integer height) {

    public boolean isComplete() {
        return x != null && y != null && width != null && height != null;
    }

    public static BoundingBox of(int x, int y, int width, int height) {
        return new BoundingBox(x, y, width, height);
    }

    /** Used when a declaration is absent: there is no region on the image to point at. */
    public static BoundingBox absent() {
        return new BoundingBox(null, null, null, null);
    }
}
