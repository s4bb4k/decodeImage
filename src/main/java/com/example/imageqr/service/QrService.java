package com.example.imageqr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;

@Service
public class QrService {

    private final ObjectMapper mapper = new ObjectMapper();

    public Object decodeQR(InputStream inputStream) throws Exception {

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
    private Object parseNestedJson(String content) {

        try {
            Object parsed = mapper.readValue(content, Object.class);
            return parseRecursively(parsed);
        } catch (Exception e) {
            // Si no es JSON válido, retorna como texto plano
            return content;
        }
    }

    /**
     * Detecta JSON dentro de Strings y los convierte en JSON real
     */
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
}
