package com.example.passportphotobackend.controller;

import com.example.passportphotobackend.service.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/image")
@CrossOrigin(origins = "http://localhost:3000") // Allow requests from React dev server
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    private final ImageService imageService;

    @Autowired
    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/process")
    public ResponseEntity<byte[]> processImage(@RequestParam("image") MultipartFile file) {
        logger.info("Received image processing request");
        try {
            byte[] processedImage = imageService.processImage(file.getBytes());
            logger.info("Image processing completed successfully");
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(processedImage);
        } catch (IOException e) {
            logger.error("Error processing image", e);
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
