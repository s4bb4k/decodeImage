package com.example.imageqr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.opencv.core.*;
import org.opencv.objdetect.QRCodeDetector;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;

@Service
public class QrService {

    private final ObjectMapper mapper = new ObjectMapper();

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public Object decodeQR(InputStream inputStream) throws Exception {

        BufferedImage image = ImageIO.read(inputStream);

        if (image == null) {
            throw new RuntimeException("Imagen inválida");
        }

        // 🔥 PREPROCESAMIENTO
        image = toGrayscale(image);
        image = increaseContrast(image);

        // 🔄 Intentar con ZXing + rotaciones
        try {
            String result = tryDecodeWithRotations(image);
            return parseNestedJson(result);
        } catch (Exception ignored) {}

        // 🧠 Fallback OpenCV (corrige inclinación)
        String opencvResult = decodeWithOpenCV(image);

        return parseNestedJson(opencvResult);
    }

    // ============================
    // ZXing con rotaciones
    // ============================

    private String tryDecodeWithRotations(BufferedImage image) throws Exception {

        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));

        Reader reader = new MultiFormatReader();

        for (int i = 0; i < 4; i++) {

            try {
                LuminanceSource source = new BufferedImageLuminanceSource(image);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                Result result = reader.decode(bitmap, hints);
                return result.getText();
            } catch (Exception ignored) {}

            image = rotateImage(image);
        }

        throw new RuntimeException("No se pudo decodificar con ZXing");
    }

    private BufferedImage rotateImage(BufferedImage image) {

        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage rotated = new BufferedImage(height, width, image.getType());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rotated.setRGB(y, width - x - 1, image.getRGB(x, y));
            }
        }

        return rotated;
    }

    // ============================
    // OpenCV fallback
    // ============================

    private String decodeWithOpenCV(BufferedImage bufferedImage) {

        Mat mat = bufferedImageToMat(bufferedImage);

        QRCodeDetector detector = new QRCodeDetector();
        Mat points = new Mat();

        String data = detector.detectAndDecode(mat, points);

        if (data == null || data.isEmpty()) {
            throw new RuntimeException("No se pudo leer el QR con OpenCV");
        }

        return data;
    }

    private Mat bufferedImageToMat(BufferedImage bi) {

        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);

        int[] data = bi.getRGB(0, 0, bi.getWidth(), bi.getHeight(), null, 0, bi.getWidth());
        byte[] bytes = new byte[data.length * 3];

        for (int i = 0; i < data.length; i++) {
            bytes[i * 3] = (byte) ((data[i] >> 16) & 0xFF);
            bytes[i * 3 + 1] = (byte) ((data[i] >> 8) & 0xFF);
            bytes[i * 3 + 2] = (byte) (data[i] & 0xFF);
        }

        mat.put(0, 0, bytes);
        return mat;
    }

    // ============================
    // PREPROCESAMIENTO
    // ============================

    private BufferedImage toGrayscale(BufferedImage original) {
        BufferedImage gray = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );
        gray.getGraphics().drawImage(original, 0, 0, null);
        return gray;
    }

    private BufferedImage increaseContrast(BufferedImage image) {

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {

                int rgb = image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xff;

                int newValue = (gray > 128) ? 255 : 0;
                int newRgb = (newValue << 16) | (newValue << 8) | newValue;

                image.setRGB(x, y, newRgb);
            }
        }
        return image;
    }

    // ============================
    // JSON inteligente
    // ============================

    private Object parseNestedJson(String content) {

        try {
            Object parsed = mapper.readValue(content, Object.class);
            return parseRecursively(parsed);
        } catch (Exception e) {
            return content;
        }
    }

    private Object parseRecursively(Object obj) {

        if (obj instanceof Map<?, ?> map) {

            Map<String, Object> result = new HashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();

                if (value instanceof String strValue) {
                    String trimmed = strValue.trim();
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        try {
                            Object nested = mapper.readValue(trimmed, Object.class);
                            result.put(entry.getKey().toString(), parseRecursively(nested));
                            continue;
                        } catch (Exception ignored) {}
                    }
                }

                result.put(entry.getKey().toString(), parseRecursively(value));
            }

            return result;
        }

        if (obj instanceof List<?> list) {
            List<Object> parsedList = new ArrayList<>();
            for (Object item : list) {
                parsedList.add(parseRecursively(item));
            }
            return parsedList;
        }

        return obj;
    }

    /*public Object decodeQR(InputStream inputStream) throws Exception {

        // 1️⃣ Leer imagen
        BufferedImage bufferedImage = ImageIO.read(inputStream);

        LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Reader reader = new MultiFormatReader();
        Result result = reader.decode(bitmap);

        String qrContent = result.getText();

        // 2️⃣ Intentar convertir a JSON automáticamente
        return parseNestedJson(qrContent);
    }

    /**
     * Convierte automáticamente String JSON y JSON anidados
     */
    /*private Object parseNestedJson(String content) {

        try {
            Object parsed = mapper.readValue(content, Object.class);
            return parseRecursively(parsed);
        } catch (Exception e) {
            // Si no es JSON válido, retorna como texto plano
            return content;
        }
    }*/

    /**
     * Detecta JSON dentro de Strings y los convierte en JSON real
     */
    /*private Object parseRecursively(Object obj) {

        if (obj instanceof Map<?, ?> map) {

            Map<String, Object> result = new HashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {

                Object value = entry.getValue();

                if (value instanceof String strValue) {

                    String trimmed = strValue.trim();

                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        try {
                            Object nested = mapper.readValue(trimmed, Object.class);
                            result.put(entry.getKey().toString(), parseRecursively(nested));
                            continue;
                        } catch (Exception ignored) {}
                    }
                }

                result.put(entry.getKey().toString(), parseRecursively(value));
            }

            return result;
        }

        if (obj instanceof List<?> list) {
            List<Object> parsedList = new ArrayList<>();
            for (Object item : list) {
                parsedList.add(parseRecursively(item));
            }
            return parsedList;
        }

        return obj;
    }*/
}
