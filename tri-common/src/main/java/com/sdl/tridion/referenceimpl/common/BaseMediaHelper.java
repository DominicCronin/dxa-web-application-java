package com.sdl.tridion.referenceimpl.common;

import com.google.common.base.Strings;
import com.sdl.tridion.referenceimpl.common.config.ScreenWidth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseMediaHelper implements MediaHelper {
    private static final Logger LOG = LoggerFactory.getLogger(BaseMediaHelper.class);

    protected static final int[] IMAGE_WIDTHS = { 160, 320, 640, 1024, 2048 };

    @Override
    public int getResponsiveWidth(String widthFactor, int containerSize) {
        final int gridSize = getGridSize();
        final String defaultMediaFill = getDefaultMediaFill();

        if (Strings.isNullOrEmpty(widthFactor)) {
            widthFactor = defaultMediaFill;
        }

        if (containerSize == 0) {
            containerSize = gridSize;
        }

        double width = 0.0;

        if (!widthFactor.endsWith("%")) {
            try {
                // TODO: Get pixel ratio
                final double pixelRatio = 1.0;

                width = Double.parseDouble(widthFactor) * pixelRatio;
            } catch (NumberFormatException e) {
                LOG.warn("Invalid width factor (\"{}\") when resizing image, defaulting to {}", widthFactor, defaultMediaFill);
                widthFactor = defaultMediaFill;
            }
        }

        if (widthFactor.endsWith("%")) {
            int fillFactor = 0;
            try {
                fillFactor = Integer.parseInt(widthFactor.substring(0, widthFactor.length() - 1));
            } catch (NumberFormatException e) {
                LOG.warn("Invalid width factor (\"{}\") when resizing image, defaulting to {}", widthFactor, defaultMediaFill);
            }

            if (fillFactor == 0) {
                fillFactor = Integer.parseInt(defaultMediaFill.substring(0, defaultMediaFill.length() - 1));
            }

            // Adjust container size for extra small and small screens
            // TODO: Get screen width
            final ScreenWidth screenWidth = ScreenWidth.LARGE;
            switch (screenWidth) {
                case EXTRA_SMALL:
                    // Extra small screens are only one column
                    containerSize = gridSize;
                    break;

                case SMALL:
                    // Small screens are max 2 columns
                    containerSize = containerSize <= gridSize / 2 ? gridSize / 2 : gridSize;
                    break;
            }

            int cols = gridSize / containerSize;
            int padding = (cols - 1) * 30;

            // TODO: Get max media width
            int maxMediaWidth = 2048;

            width = (fillFactor * containerSize * maxMediaWidth / (gridSize * 100)) - padding;
        }

        return (int) Math.ceil(width);
    }

    @Override
    public int getResponsiveHeight(String widthFactor, double aspect, int containerSize) {
        return (int) Math.ceil(getResponsiveWidth(widthFactor, containerSize) / aspect);
    }

    @Override
    public int getGridSize() {
        // TODO: Get this from configuration?
        return 12;
    }

    @Override
    public double getDefaultMediaAspect() {
        // TODO: Get this from configuration?
        return 1.62;
    }

    @Override
    public String getDefaultMediaFill() {
        // TODO: Get this from configuration?
        return "100%";
    }
}
