package com.example.imageqr.service;

import com.example.imageqr.dto.ErrorResponse;
import com.example.imageqr.dto.QrRequestParseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.opencv.core.*;
import org.opencv.objdetect.QRCodeDetector;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;

@Service
public class QrService {

    private final ObjectMapper mapper = new ObjectMapper();

    public Object decodeQR(InputStream inputStream) throws Exception {

        BufferedImage image = ImageIO.read(inputStream);

        if (image == null) {
            throw new RuntimeException("Imagen inválida o formato no soportado");
        }

        // 🔥 PIPELINE PARA JPG (ORDEN IMPORTANTE)
        image = resize(image, 2);          // mejora resolución
        image = toGrayscale(image);        // elimina ruido de color
        image = binarize(image);           // convierte a blanco/negro real

        // 🔄 Intentar lectura con rotaciones
        String qrText = tryDecodeWithRotations(image);

        return parseNestedJson(qrText);
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

            } catch (Exception ignored) {
            }

            image = rotateImage(image);
        }

        throw new RuntimeException("No se pudo leer el QR (incluso después de rotaciones)");
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
    // PREPROCESAMIENTO (CLAVE JPG)
    // ============================

    private BufferedImage resize(BufferedImage original, int scale) {

        int width = original.getWidth() * scale;
        int height = original.getHeight() * scale;

        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = resized.createGraphics();
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();

        return resized;
    }

    private BufferedImage toGrayscale(BufferedImage original) {

        BufferedImage gray = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics g = gray.getGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();

        return gray;
    }

    private BufferedImage binarize(BufferedImage image) {

        BufferedImage binary = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_BYTE_BINARY
        );

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {

                int rgb = image.getRGB(x, y);

                int gray = rgb & 0xff;

                int value = (gray > 140) ? 255 : 0;

                int newRgb = (value << 16) | (value << 8) | value;

                binary.setRGB(x, y, newRgb);
            }
        }

        return binary;
    }

    // ============================
    // JSON INTELIGENTE
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
                        } catch (Exception ignored) {
                        }
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


    public ResponseEntity<?> readQRCode(MultipartFile file) throws Exception {
        try {

            if (file.isEmpty()) {
                System.out.println("-------->  entro");
                return ResponseEntity
                        .badRequest()
                        .body(new ErrorResponse("QR-001", "Archivo vacío"));
            }

            if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                return ResponseEntity
                        .badRequest()
                        .body(new ErrorResponse("QR-002", "El archivo no es una imagen válida"));
            }

            InputStream inputStream = file.getInputStream();
            BufferedImage originalImage = ImageIO.read(inputStream);

            if (originalImage == null) {
                return ResponseEntity
                        .badRequest()
                        .body(new ErrorResponse("QR-003", "No se pudo procesar la imagen"));
            }

            BufferedImage grayImage = convertToGrayscale(originalImage);

            int[] angles = {0, 90, 180, 270, -15, 15, -30, 30};

            for (int angle : angles) {
                try {

                    BufferedImage rotated = rotateImage(grayImage, angle);
                    String result = decode(rotated);

                    if (result != null) {

                        QrRequestParseDto dto = new QrRequestParseDto(result);

                        // 🔥 Retorna directamente
                        return parseQrPresent(dto);
                    }

                } catch (Exception ignored) {
                }
            }

            return ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new ErrorResponse("QR-004", "No se pudo leer el código QR"));

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("QR-500", "Error interno procesando el QR"));
        }
    }

    private String decode(BufferedImage image) throws NotFoundException {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        Result result = new MultiFormatReader().decode(bitmap, hints);
        return result.getText();
    }

    private BufferedImage convertToGrayscale(BufferedImage original) {
        BufferedImage gray = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );
        Graphics g = gray.getGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();
        return gray;
    }

    private BufferedImage rotateImage(BufferedImage image, double angle) {

        double radians = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int w = image.getWidth();
        int h = image.getHeight();

        int newWidth = (int) Math.floor(w * cos + h * sin);
        int newHeight = (int) Math.floor(h * cos + w * sin);

        BufferedImage rotated = new BufferedImage(
                newWidth,
                newHeight,
                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics2D g2d = rotated.createGraphics();
        AffineTransform at = new AffineTransform();

        at.translate((newWidth - w) / 2, (newHeight - h) / 2);
        at.rotate(radians, w / 2.0, h / 2.0);

        g2d.setTransform(at);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return rotated;
    }

    public ResponseEntity<?> parseQrPresent(QrRequestParseDto emvco) {
        System.out.println("parseQrPresent" + emvco);
        return null;
    }

}
