package com.example.passportphotobackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.opencv.core.Core;
import com.example.passportphotobackend.OpenCVLoader;

@SpringBootApplication
public class PassportPhotoBackendApplication {

    static {
        try {
            // Use the OpenCVLoader to load the library
            OpenCVLoader.load();
        } catch (Exception e) {
            System.err.println("Error loading OpenCV native library: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        OpenCVLoader.load(); // Ensure OpenCV is loaded once
        SpringApplication.run(PassportPhotoBackendApplication.class, args);
    }
}
