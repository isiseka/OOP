package com.example.passportphotobackend.service;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = 
        LoggerFactory.getLogger(ImageService.class);
    // Define desired output dimensions with a 7:9 ratio.
    // (700/900 simplifies to 7:9. Adjust if needed.)
    private static final int DESIRED_WIDTH = 700;
    private static final int DESIRED_HEIGHT = 900;

    public byte[] processImage(byte[] imageBytes) throws IOException {
        try {
            // 1. Load input image and convert to OpenCV Mat
            InputStream in = new ByteArrayInputStream(imageBytes);
            BufferedImage bufferedImage = ImageIO.read(in);
            if (bufferedImage == null) {
                throw new IOException("Could not read input image.");
            }
            Mat mat = bufferedImageToMat(bufferedImage);

            // 2. Load Haar Cascade for face detection
            String cascadeFilePath = 
                "src/main/resources/haarcascade_frontalface_default.xml";
            if (!Files.exists(Paths.get(cascadeFilePath))) {
                throw new IOException("Haar cascade file not found at " +
                    cascadeFilePath);
            }
            CascadeClassifier faceDetector = new CascadeClassifier(cascadeFilePath);

            // 3. Detect faces in the image
            MatOfRect faceDetections = new MatOfRect();
            faceDetector.detectMultiScale(mat, faceDetections);
            if (faceDetections.empty()) {
                throw new Exception("No face detected");
            }

            // 4. Select the best face (largest by area)
            Rect bestFace = null;
            double maxArea = 0;
            for (Rect face : faceDetections.toArray()) {
                double area = face.width * face.height;
                if (area > maxArea) {
                    maxArea = area;
                    bestFace = face;
                }
            }
            if (bestFace == null) {
                throw new Exception("No face detected");
            }

            // 5. Compute face center in the original image
            int faceCenterX = bestFace.x + bestFace.width / 2;
            int faceCenterY = bestFace.y + bestFace.height / 2;

            // 6. Determine the ideal crop coordinates so that the face center 
            //    lands at the center of a DESIRED_WIDTH x DESIRED_HEIGHT frame.
            int idealCropX = faceCenterX - DESIRED_WIDTH / 2;
            int idealCropY = faceCenterY - DESIRED_HEIGHT / 2;

            // 7. Create a white canvas (Mat) of the desired dimensions
            Mat cropped = new Mat(DESIRED_HEIGHT, DESIRED_WIDTH, CvType.CV_8UC3,
                new Scalar(255, 255, 255));

            // 8. Compute the overlapping region between the original image and 
            //    the ideal crop and then copy it over.
            int srcX = Math.max(idealCropX, 0);
            int srcY = Math.max(idealCropY, 0);
            int destX = srcX - idealCropX; // This equals -idealCropX if idealCropX < 0
            int destY = srcY - idealCropY;
            int srcWidth = Math.min(DESIRED_WIDTH - destX, mat.cols() - srcX);
            int srcHeight = Math.min(DESIRED_HEIGHT - destY, mat.rows() - srcY);

            if (srcWidth > 0 && srcHeight > 0) {
                Rect srcRect = new Rect(srcX, srcY, srcWidth, srcHeight);
                Rect destRect = new Rect(destX, destY, srcWidth, srcHeight);
                Mat srcROI = new Mat(mat, srcRect);
                Mat destROI = cropped.submat(destY, destY + srcHeight,
                                             destX, destX + srcWidth);
                srcROI.copyTo(destROI);
            }

            // 9. Adjust the face coordinates relative to the cropped result.
            Rect shiftedFace = new Rect(
                bestFace.x - idealCropX,
                bestFace.y - idealCropY,
                bestFace.width,
                bestFace.height
            );

            // 10. Estimate a body region based on the face location.
            //     (Assuming body width is ~2× face width and height ~4× face height)
            int bodyWidth = shiftedFace.width * 2;
            int bodyHeight = shiftedFace.height * 4;
            int bodyX = shiftedFace.x - (bodyWidth - shiftedFace.width) / 2;
            int bodyY = shiftedFace.y - shiftedFace.height / 2;
            // Ensure the body region does not go out of bounds.
            bodyX = Math.max(0, bodyX);
            bodyY = Math.max(0, bodyY);
            bodyWidth = Math.min(bodyWidth, cropped.cols() - bodyX);
            bodyHeight = Math.min(bodyHeight, cropped.rows() - bodyY);
            Rect bodyRect = new Rect(bodyX, bodyY, bodyWidth, bodyHeight);

            // 11. Use GrabCut on the cropped image (using the estimated body rect)
            //     to separate the foreground (person) from the background.
            Mat mask = new Mat();
            Mat bgModel = new Mat();
            Mat fgModel = new Mat();
            Imgproc.grabCut(cropped, mask, bodyRect, bgModel, fgModel, 5, 
                Imgproc.GC_INIT_WITH_RECT);

            // 12. Combine definite foreground and probable foreground regions.
            Mat maskForeground = new Mat();
            Core.compare(mask, new Scalar(Imgproc.GC_FGD), maskForeground,
                Core.CMP_EQ);
            Mat maskProbForeground = new Mat();
            Core.compare(mask, new Scalar(Imgproc.GC_PR_FGD), maskProbForeground,
                Core.CMP_EQ);
            Core.bitwise_or(maskForeground, maskProbForeground, mask);

            // 13. Smooth the mask’s edges for a cleaner result.
            //     Increase the blur kernel size and apply morphological closing.
            Imgproc.GaussianBlur(mask, mask, new Size(7, 7), 0);
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,
                new Size(5, 5));
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
            Imgproc.threshold(mask, mask, 128, 255, Imgproc.THRESH_BINARY);

            // 14. Create the final image by copying the foreground (person) onto 
            //     a white background.
            Mat foreground = new Mat(cropped.size(), CvType.CV_8UC3,
                new Scalar(255, 255, 255));
            cropped.copyTo(foreground, mask);

            // 15. Convert the resulting Mat to a BufferedImage and then to a PNG byte array.
            BufferedImage processedImage = matToBufferedImage(foreground);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(processedImage, "png", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            logger.error("Error processing image", e);
            throw new IOException(e);
        }
    }

    // Helper: Convert BufferedImage to Mat
    private Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage converted = new BufferedImage(bi.getWidth(), bi.getHeight(),
            BufferedImage.TYPE_3BYTE_BGR);
        converted.getGraphics().drawImage(bi, 0, 0, null);
        byte[] data = ((java.awt.image.DataBufferByte)
            converted.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    // Helper: Convert Mat to BufferedImage
    private BufferedImage matToBufferedImage(Mat mat) throws IOException {
        MatOfByte mob = new MatOfByte();
        Imgcodecs.imencode(".png", mat, mob);
        return ImageIO.read(new ByteArrayInputStream(mob.toArray()));
    }
}
