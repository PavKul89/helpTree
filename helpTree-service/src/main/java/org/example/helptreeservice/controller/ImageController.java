package org.example.helptreeservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.service.ImageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private final ImageService imageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = imageService.upload(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping(value = "/multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, List<String>>> uploadMultiple(
            @RequestParam("files") List<MultipartFile> files) {
        List<String> urls = imageService.uploadMultiple(files);
        return ResponseEntity.ok(Map.of("urls", urls));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam("url") String url) {
        imageService.delete(url);
        return ResponseEntity.noContent().build();
    }
}
