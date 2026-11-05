package org.example.helptreeservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.enums.ImagePurpose;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.AuthorizationService.UserContext;
import org.example.helptreeservice.service.ImageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private final ImageService imageService;
    private final AuthorizationService authService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<Map<String, String>>> upload(
            @RequestParam("file") MultipartFile file) {
        UserContext user = currentUser();
        return imageService.uploadAsync(file, user.getUserId(), ImagePurpose.GENERAL)
                .thenApply(url -> ResponseEntity.ok(Map.of("url", url)));
    }

    @PostMapping(value = "/multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<Map<String, List<String>>>> uploadMultiple(
            @RequestParam("files") List<MultipartFile> files) {
        UserContext user = currentUser();
        return imageService.uploadMultipleAsync(files, user.getUserId(), ImagePurpose.GENERAL)
                .thenApply(urls -> ResponseEntity.ok(Map.of("urls", urls)));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam("url") String url) {
        UserContext user = currentUser();
        imageService.deleteImage(user.getUserId(), user.getRole(), url);
        return ResponseEntity.noContent().build();
    }

    private UserContext currentUser() {
        UserContext user = authService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        return user;
    }
}
