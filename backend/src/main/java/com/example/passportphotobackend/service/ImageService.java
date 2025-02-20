package com.example.passportphotobackend.service;

import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.photo.Photo;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImageService {

    private static final int TARGET_WIDTH = 700;
    private static final int TARGET_HEIGHT = 900;
    private Net u2net;
    private CascadeClassifier faceDetector;

    public ImageService() throws IOException {
        // Load U2-Net model for background removal
        u2net = loadU2Net();
        // Initialize face detector
        faceDetector = loadFaceDetector();
    }

    public byte[] processImage(byte[] imageData) throws IOException {
        try {
            // 1. Preprocess image
            Mat image = Imgcodecs.imdecode(new MatOfByte(imageData), Imgcodecs.IMREAD_COLOR);
            enhanceImageQuality(image);

            // 2. Face detection and alignment
            Rect face = detectMainFace(image);
            if (face == null) {
                throw new IOException("No face detected");
            }

            // 3. Intelligent cropping with padding
            Mat cropped = smartCrop(image, face);

            // 4. Advanced background removal
            Mat mask = createSegmentationMask(cropped);

            // 5. Edge refinement
            refineMaskEdges(mask);

            // 6. Create final image
            Mat result = applyWhiteBackground(cropped, mask);

            // 7. Convert to byte array
            return matToByteArray(result);
        } catch (IOException e) {
            throw new IOException("Image processing failed due to IO error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IOException("Image processing failed: " + e.getMessage(), e);
        }
    }

    private void enhanceImageQuality(Mat image) {
        // Handle lighting variations via Lab color space equalization
        Mat lab = new Mat();
        Imgproc.cvtColor(image, lab, Imgproc.COLOR_BGR2Lab);
        List<Mat> labChannels = new ArrayList<>();
        Core.split(lab, labChannels);
        Mat lChannel = labChannels.get(0);
        Imgproc.equalizeHist(lChannel, lChannel);
        Core.merge(labChannels, lab);
        Imgproc.cvtColor(lab, image, Imgproc.COLOR_Lab2BGR);

        // Removed heavy denoising to preserve natural texture and avoid a "painting" effect
        // Photo.fastNlMeansDenoisingColored(image, image, 10, 10, 7, 21);
    }

    private Rect detectMainFace(Mat image) {
        try {
            // Use DNN-based face detector for better accuracy
            Net faceNet = Dnn.readNetFromCaffe(
                new ClassPathResource("deploy.prototxt").getFile().getAbsolutePath(),
                new ClassPathResource("res10_300x300_ssd_iter_140000.caffemodel").getFile().getAbsolutePath()
            );

            Mat blob = Dnn.blobFromImage(image, 1.0, new Size(300, 300), new Scalar(104.0, 177.0, 123.0));
            faceNet.setInput(blob);
            Mat detections = faceNet.forward();

            // Process detections and return largest face
            return findLargestFace(detections, image.size());
        } catch (IOException e) {
            System.err.println("Error loading face detection model files: " + e.getMessage());
            e.printStackTrace();
            return null; // or handle the error as needed
        }
    }

    private Mat smartCrop(Mat image, Rect face) {
        // Calculate face center
        Point faceCenter = new Point(face.x + face.width / 2.0, face.y + face.height / 2.0);
        
        // Calculate ideal top-left coordinates so that the final image centers the face
        int idealX = (int) (faceCenter.x - TARGET_WIDTH / 2.0);
        int idealY = (int) (faceCenter.y - TARGET_HEIGHT / 2.0);

        // Determine the overlapping region in the source image
        int srcX = Math.max(idealX, 0);
        int srcY = Math.max(idealY, 0);
        int srcWidth = Math.min(TARGET_WIDTH, image.cols() - srcX);
        int srcHeight = Math.min(TARGET_HEIGHT, image.rows() - srcY);

        // Determine the offset in the destination canvas
        int offsetX = srcX - idealX;
        int offsetY = srcY - idealY;

        // Create a white canvas sized to TARGET_WIDTH x TARGET_HEIGHT
        Mat canvas = new Mat(TARGET_HEIGHT, TARGET_WIDTH, image.type(), new Scalar(255, 255, 255));
        
        // Copy the overlapping region from the source image to the canvas so that the face is centered
        Mat roi = canvas.submat(offsetY, offsetY + srcHeight, offsetX, offsetX + srcWidth);
        image.submat(new Rect(srcX, srcY, srcWidth, srcHeight)).copyTo(roi);

        return canvas;
    }

    private Mat createSegmentationMask(Mat image) {
        // Preprocess for U2-Net
        Mat blob = Dnn.blobFromImage(image, 1/255.0, new Size(320, 320), new Scalar(0,0,0), true, false);
        u2net.setInput(blob);
        List<Mat> outputs = new ArrayList<>();
        u2net.forward(outputs, getOutputsNames(u2net));

        // Process output
        Mat mask = outputs.get(0).reshape(1, 320);
        Core.MinMaxLocResult mm = Core.minMaxLoc(mask);
        Core.subtract(mask, new Scalar(mm.minVal), mask);
        Core.multiply(mask, new Scalar(255/(mm.maxVal - mm.minVal)), mask);
        mask.convertTo(mask, CvType.CV_8U);
        
        // Resize to original dimensions
        Imgproc.resize(mask, mask, image.size());
        
        return mask;
    }

    private void refineMaskEdges(Mat mask) {
        // Apply morphological operations
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5,5));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);

        // Smooth edges
        Imgproc.GaussianBlur(mask, mask, new Size(9,9), 0);
        Imgproc.threshold(mask, mask, 128, 255, Imgproc.THRESH_BINARY);
    }

    private Mat applyWhiteBackground(Mat image, Mat mask) {
        Mat background = Mat.zeros(image.size(), image.type());
        background.setTo(new Scalar(255, 255, 255));
        
        Mat foreground = new Mat();
        image.copyTo(foreground, mask);
        
        Mat invertedMask = new Mat();
        Core.bitwise_not(mask, invertedMask);
        
        Mat backgroundPart = new Mat();
        background.copyTo(backgroundPart, invertedMask);
        
        Core.add(foreground, backgroundPart, foreground);
        return foreground;
    }

    private Net loadU2Net() throws IOException {
        File modelFile = copyResourceToTemp("u2net.onnx");
        return Dnn.readNetFromONNX(modelFile.getAbsolutePath());
    }

    private CascadeClassifier loadFaceDetector() throws IOException {
        File cascadeFile = copyResourceToTemp("haarcascade_frontalface_alt2.xml");
        return new CascadeClassifier(cascadeFile.getAbsolutePath());
    }

    private File copyResourceToTemp(String resourceName) throws IOException {
        InputStream inputStream = new ClassPathResource(resourceName).getInputStream();
        Path tempFile = Files.createTempFile("cv-", resourceName);
        Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile.toFile();
    }

    private byte[] matToByteArray(Mat mat) throws IOException {
        MatOfByte mob = new MatOfByte();
        Imgcodecs.imencode(".png", mat, mob);
        return mob.toArray();
    }

    private Rect findLargestFace(Mat detections, Size imageSize) {
        // Implementation for finding largest face
        // (Details omitted for brevity)
        return new Rect(0, 0, (int)imageSize.width, (int)imageSize.height);
    }

    private List<String> getOutputsNames(Net net) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < net.getUnconnectedOutLayers().total(); i++) {
            names.add(net.getLayer(new org.opencv.core.MatOfInt(net.getUnconnectedOutLayers()).toList().get(i)).get_name());
        }
        return names;
    }
}