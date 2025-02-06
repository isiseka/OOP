package com.example.passportphotobackend.service;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    public byte[] processImage(byte[] imageBytes) throws IOException {
        try {
            // Load the image
            InputStream in = new ByteArrayInputStream(imageBytes);
            BufferedImage bImageFromConvert = ImageIO.read(in);

            // Convert BufferedImage to Mat
            Mat mat = bufferedImageToMat(bImageFromConvert);

            // Load the Haar cascade file
            String cascadeFilePath = "src/main/resources/haarcascade_frontalface_default.xml";
            if (!Files.exists(Paths.get(cascadeFilePath))) {
                throw new IOException("Haar cascade file not found at " + cascadeFilePath);
            }
            CascadeClassifier faceDetector = new CascadeClassifier(cascadeFilePath);

            // Face Detection
            MatOfRect faceDetections = new MatOfRect();
            faceDetector.detectMultiScale(mat, faceDetections);

            // Draw rectangles around the faces
            for (Rect rect : faceDetections.toArray()) {
                Imgproc.rectangle(mat, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 255, 0));
            }

            // Convert Mat back to BufferedImage
            BufferedImage processedImage = matToBufferedImage(mat);

            // Convert BufferedImage to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(processedImage, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            // Log the error for debugging
            logger.error("Error processing image", e);
            return null;
        }
    }

    private Mat bufferedImageToMat(BufferedImage bi) {
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
        byte[] data = ((java.awt.image.DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        return mat;
    }

    private BufferedImage matToBufferedImage(Mat mat) throws IOException {
        MatOfByte mob = new MatOfByte();
        Imgcodecs.imencode(".png", mat, mob);
        byte[] ba = mob.toArray();
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(ba));
        return bi;
    }

    public void processImage(MultipartFile file) {
        try {
            // existing image processing code...
        } catch (Exception e) {
            // Log the error for debugging
            logger.error("Error processing image", e);
            
        }
    }
}
