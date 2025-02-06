package com.example.passportphotobackend;

import org.opencv.core.Core;

public class OpenCVLoader {
    private static boolean loaded = false;

    static {
        try {
            if (!loaded) {
                System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
                loaded = true;
            }
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load OpenCV native library: " + e.getMessage());
        }
    }

    public static void load() {
        // This method can be called to ensure the static block is executed
    }
}