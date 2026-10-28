package org.example.helptreeservice.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.example.helptreeservice.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private MinioClient siloClient;

    @Mock
    private Executor imageUploadExecutor;

    @InjectMocks
    private ImageService imageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(imageService, "bucket", "helptree-images");
        ReflectionTestUtils.setField(imageService, "publicEndpoint", "https://images.example.com/");
        ReflectionTestUtils.setField(imageService, "maxSize", 10_485_760L);
        ReflectionTestUtils.setField(imageService, "allowedTypes",
                List.of("image/jpeg", "image/png", "image/webp", "image/gif"));
    }

    @Test
    void uploadCreatesBucketAndReturnsPublicSiloUrl() throws Exception {
        when(siloClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "tree.png", "image/png", new byte[]{1, 2, 3});

        String url = imageService.upload(file);

        assertTrue(url.matches("https://images\\.example\\.com/helptree-images/[0-9a-f-]{36}\\.png"));
        verify(siloClient).makeBucket(any(MakeBucketArgs.class));
        verify(siloClient).setBucketPolicy(any(SetBucketPolicyArgs.class));
        verify(siloClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadRejectsUnsupportedType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "tree.svg", "image/svg+xml", new byte[]{1});

        assertThrows(BadRequestException.class, () -> imageService.upload(file));
    }

    @Test
    void deleteRemovesSiloObjectFromMatchingPublicUrl() throws Exception {
        ReflectionTestUtils.setField(imageService, "bucketInitialized", true);

        imageService.delete("https://images.example.com/helptree-images/tree.png");

        verify(siloClient).removeObject(any(RemoveObjectArgs.class));
    }
}
