package org.example.helptreeservice.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private ImageService imageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(imageService, "bucket", "helptree-images");
        ReflectionTestUtils.setField(imageService, "maxSize", 10485760L);
        ReflectionTestUtils.setField(imageService, "allowedTypes", List.of("image/jpeg", "image/png", "image/webp", "image/gif"));
    }

    @Test
    void refreshUrl_shouldReturnNewUrl() throws Exception {
        String oldUrl = "https://minio.example.com/helptree-images/abc123.png?X-Amz-Algorithm=AWS4&Signature=old";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example.com/helptree-images/abc123.png?X-Amz-Algorithm=AWS4&Signature=new");

        String newUrl = imageService.refreshUrl(oldUrl);

        assertEquals("https://minio.example.com/helptree-images/abc123.png?X-Amz-Algorithm=AWS4&Signature=new", newUrl);
        verify(minioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void refreshUrl_shouldReturnOriginalForNull() {
        assertNull(imageService.refreshUrl(null));
    }

    @Test
    void refreshUrl_shouldReturnOriginalForSimpleString() {
        assertEquals("no-slash", imageService.refreshUrl("no-slash"));
    }

    @Test
    void refreshUrl_shouldReturnOriginalOnException() throws Exception {
        String oldUrl = "https://minio.example.com/helptree-images/abc123.png";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        String result = imageService.refreshUrl(oldUrl);

        assertEquals(oldUrl, result);
    }

    @Test
    void refreshUrls_shouldReturnNullForNull() {
        assertNull(imageService.refreshUrls(null));
    }

    @Test
    void refreshUrls_shouldRefreshAllUrls() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example.com/helptree-images/new.jpg");

        List<String> result = imageService.refreshUrls(List.of(
                "https://minio.example.com/helptree-images/a.jpg?sig=old1",
                "https://minio.example.com/helptree-images/b.png?sig=old2"
        ));

        assertEquals(2, result.size());
        verify(minioClient, times(2)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }
}
