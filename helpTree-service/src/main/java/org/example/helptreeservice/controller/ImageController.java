package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.error.ErrorResponse;
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
@Tag(name = "Media", description = "Загрузка и удаление изображений")
@SecurityRequirement(name = "bearerAuth")
public class ImageController {

    private final ImageService imageService;
    private final AuthorizationService authService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить одно изображение",
            description = "multipart/form-data с полем file. Размер и MIME-тип проверяются сервером.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL загруженного изображения"),
            @ApiResponse(responseCode = "413", description = "Файл превышает допустимый размер",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Неподдерживаемый тип файла",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompletableFuture<ResponseEntity<Map<String, String>>> upload(
            @RequestParam("file") MultipartFile file) {
        UserContext user = currentUser();
        return imageService.uploadAsync(file, user.getUserId(), ImagePurpose.GENERAL)
                .thenApply(url -> ResponseEntity.ok(Map.of("url", url)));
    }

    @PostMapping(value = "/multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить несколько изображений",
            description = "multipart/form-data с полем files. Проверка размера и типа применяется к каждому файлу.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список URL загруженных изображений"),
            @ApiResponse(responseCode = "413", description = "Один из файлов превышает допустимый размер",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Неподдерживаемый тип файла",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompletableFuture<ResponseEntity<Map<String, List<String>>>> uploadMultiple(
            @RequestParam("files") List<MultipartFile> files) {
        UserContext user = currentUser();
        return imageService.uploadMultipleAsync(files, user.getUserId(), ImagePurpose.GENERAL)
                .thenApply(urls -> ResponseEntity.ok(Map.of("urls", urls)));
    }

    @DeleteMapping
    @Operation(summary = "Удалить изображение",
            description = "Владелец файла или ADMIN. Повторное удаление не ломает состояние.")
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
