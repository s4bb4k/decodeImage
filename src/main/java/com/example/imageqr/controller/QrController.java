package com.example.imageqr.controller;

import com.example.imageqr.dto.QrDecodeResponse;
import com.example.imageqr.service.QrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@RestController
@RequestMapping("/api/qr")
public class QrController {

    @Autowired
    private QrService qrService;

    @PostMapping("/decode")
    public ResponseEntity<?> decodeQR(@RequestParam("file") MultipartFile file) {

        try {
            Object qrData = qrService.decodeQR(file.getInputStream());

            return ResponseEntity.ok(
                    Map.of(
                            "status", "SUCCESS",
                            "data", qrData
                    )
            );

        } catch (Exception e) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "status", "ERROR",
                            "message", e.getMessage()
                    )
            );
        }
    }

    @PostMapping("/read")
    public ResponseEntity<?> readQR(@RequestParam("file") MultipartFile file) {
        try {
            String result = qrService.readQRCode(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error leyendo QR: " + e.getMessage());
        }
    }
}
