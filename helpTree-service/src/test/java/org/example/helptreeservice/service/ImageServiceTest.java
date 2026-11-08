package org.example.helptreeservice.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.example.helptreeservice.config.ImageUploadProperties;
import org.example.helptreeservice.dto.images.ProcessedImage;
import org.example.helptreeservice.entity.ImageObject;
import org.example.helptreeservice.enums.ImagePurpose;
import org.example.helptreeservice.metrics.HelpTreeMetrics;
import org.example.helptreeservice.enums.ImageStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private MinioClient siloClient;

    @Mock
    private ImageProcessor imageProcessor;

    @Mock
    private ImageObjectService imageObjectService;

    @Mock
    private HelpTreeMetrics metrics;

    private ImageUploadProperties properties;
    private ImageService imageService;

    @BeforeEach
    void setUp() {
        properties = new ImageUploadProperties();
        imageService = new ImageService(
                siloClient, Runnable::run, imageProcessor, imageObjectService, properties, metrics);
        ReflectionTestUtils.setField(imageService, "bucket", "helptree-images");
        ReflectionTestUtils.setField(imageService, "publicEndpoint", "https://images.example.com/");
    }

    @Test
    void uploadStoresProcessedBytesAndRegistersOwnership() throws Exception {
        when(siloClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(imageProcessor.process(any(byte[].class)))
                .thenReturn(processed(".png", "image/png", new byte[]{1, 2, 3}));
        when(imageObjectService.register(anyLong(), anyList(), any(ImagePurpose.class)))
                .thenReturn(List.of());

        MockMultipartFile file = new MockMultipartFile(
                "file", "tree.png", "image/png", new byte[]{9, 9, 9});

        String url = imageService.upload(file, 42L, ImagePurpose.POST);

        assertTrue(url.matches("https://images\\.example\\.com/helptree-images/[0-9a-f-]{36}\\.png"));

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(siloClient).putObject(put.capture());
        assertEquals("helptree-images", put.getValue().bucket());
        assertEquals("image/png", put.getValue().contentType());
        assertFalse(put.getValue().object().contains("/"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImageObjectService.NewImage>> registered =
                ArgumentCaptor.forClass(List.class);
        verify(imageObjectService).register(eq(42L), registered.capture(), eq(ImagePurpose.POST));
        ImageObjectService.NewImage newImage = registered.getValue().get(0);
        assertEquals(3, newImage.sizeBytes());
        assertEquals(10, newImage.width());
        assertEquals("a".repeat(64), newImage.sha256());
        assertEquals(put.getValue().object(), newImage.objectKey());
    }

    @Test
    void uploadCreatesMissingBucketAndAppliesReadOnlyPolicy() throws Exception {
        when(siloClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        when(imageProcessor.process(any(byte[].class)))
                .thenReturn(processed(".jpg", "image/jpeg", new byte[]{1}));
        when(imageObjectService.register(anyLong(), anyList(), any(ImagePurpose.class)))
                .thenReturn(List.of());

        imageService.upload(new MockMultipartFile(
                "file", "tree.jpg", "image/jpeg", new byte[]{1}), 1L, ImagePurpose.AVATAR);

        verify(siloClient).makeBucket(any(MakeBucketArgs.class));
        ArgumentCaptor<SetBucketPolicyArgs> policy = ArgumentCaptor.forClass(SetBucketPolicyArgs.class);
        verify(siloClient).setBucketPolicy(policy.capture());
        String config = policy.getValue().config();
        assertTrue(config.contains("s3:GetObject"));
        assertFalse(config.contains("s3:ListBucket"));
        assertFalse(config.contains("s3:PutObject"));
        assertFalse(config.contains("s3:DeleteObject"));
    }

    @Test
    void bucketPolicyIsAppliedOnlyOnce() throws Exception {
        when(siloClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(imageProcessor.process(any(byte[].class)))
                .thenReturn(processed(".png", "image/png", new byte[]{1}));
        when(imageObjectService.register(anyLong(), anyList(), any(ImagePurpose.class)))
                .thenReturn(List.of());

        imageService.upload(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}),
                1L, ImagePurpose.POST);
        imageService.upload(new MockMultipartFile("file", "b.png", "image/png", new byte[]{1}),
                1L, ImagePurpose.POST);

        verify(siloClient, times(1)).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    void rejectsMoreFilesThanTheConfiguredMaximumBeforeReadingThem() {
        properties.setMaxFiles(1);
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.png", "image/png", new byte[]{1}),
                new MockMultipartFile("files", "b.png", "image/png", new byte[]{1}));

        assertThrows(BadRequestException.class,
                () -> imageService.uploadMultiple(files, 1L, ImagePurpose.POST));

        verifyNoInteractions(imageProcessor);
        verifyNoInteractions(siloClient);
    }

    @Test
    void rejectsRequestWhoseTotalBytesExceedTheLimit() {
        properties.setMaxRequestSize(5);
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.png", "image/png", new byte[]{1, 2, 3}),
                new MockMultipartFile("files", "b.png", "image/png", new byte[]{1, 2, 3}));

        assertThrows(BadRequestException.class,
                () -> imageService.uploadMultiple(files, 1L, ImagePurpose.POST));

        verifyNoInteractions(imageProcessor);
    }

    @Test
    void rejectsSingleFileAboveTheLimit() {
        properties.setMaxSize(2);

        assertThrows(BadRequestException.class, () -> imageService.upload(
                new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3}),
                1L, ImagePurpose.POST));

        verifyNoInteractions(imageProcessor);
    }

    @Test
    void fakeImageContentTypeCannotSmuggleArbitraryBytes() throws Exception {
        // Real processor, no mocks around the validation stage: a text file
        // named .png and declared image/png must still be rejected.
        ImageUploadProperties realProperties = new ImageUploadProperties();
        ImageService realService = new ImageService(
                siloClient, Runnable::run, new ImageProcessor(realProperties),
                imageObjectService, realProperties, metrics);
        ReflectionTestUtils.setField(realService, "bucket", "helptree-images");
        ReflectionTestUtils.setField(realService, "publicEndpoint", "https://images.example.com/");

        MockMultipartFile fake = new MockMultipartFile(
                "file", "evil.png", "image/png",
                "#!/bin/sh\nrm -rf /\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(BadRequestException.class,
                () -> realService.upload(fake, 1L, ImagePurpose.POST));

        verify(siloClient, never()).putObject(any(PutObjectArgs.class));
        verifyNoInteractions(imageObjectService);
    }

    @Test
    void partialUploadIsCompensatedWhenALaterPutFails() throws Exception {
        when(siloClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(imageProcessor.process(any(byte[].class)))
                .thenReturn(processed(".png", "image/png", new byte[]{1}))
                .thenReturn(processed(".png", "image/png", new byte[]{2}));
        when(siloClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(null)
                .thenThrow(new RuntimeException("Silo unavailable"));

        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.png", "image/png", new byte[]{1}),
                new MockMultipartFile("files", "b.png", "image/png", new byte[]{2}));

        assertThrows(RuntimeException.class,
                () -> imageService.uploadMultiple(files, 1L, ImagePurpose.POST));

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(siloClient, times(2)).putObject(put.capture());
        ArgumentCaptor<RemoveObjectArgs> removed = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(siloClient).removeObject(removed.capture());
        assertEquals(put.getAllValues().get(0).object(), removed.getValue().object());
        verify(imageObjectService, never()).register(anyLong(), anyList(), any(ImagePurpose.class));
    }

    @Test
    void everyStoredObjectIsRemovedWhenOwnershipRegistrationFails() throws Exception {
        when(siloClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(imageProcessor.process(any(byte[].class)))
                .thenReturn(processed(".png", "image/png", new byte[]{1}))
                .thenReturn(processed(".jpg", "image/jpeg", new byte[]{2}));
        when(imageObjectService.register(anyLong(), anyList(), any(ImagePurpose.class)))
                .thenThrow(new BadRequestException("quota"));

        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.png", "image/png", new byte[]{1}),
                new MockMultipartFile("files", "b.jpg", "image/jpeg", new byte[]{2}));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> imageService.uploadMultiple(files, 1L, ImagePurpose.POST));
        assertEquals("quota", error.getMessage());

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(siloClient, times(2)).putObject(put.capture());
        ArgumentCaptor<RemoveObjectArgs> removed = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(siloClient, times(2)).removeObject(removed.capture());
        assertEquals(
                put.getAllValues().stream().map(PutObjectArgs::object).toList(),
                removed.getAllValues().stream().map(RemoveObjectArgs::object).toList());
    }

    @Test
    void uploadAsyncRunsOnTheInjectedExecutor() throws Exception {
        when(siloClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(imageProcessor.process(any(byte[].class)))
                .thenReturn(processed(".png", "image/png", new byte[]{1}));
        when(imageObjectService.register(anyLong(), anyList(), any(ImagePurpose.class)))
                .thenReturn(List.of());

        CompletableFuture<String> future = imageService.uploadAsync(
                new MockMultipartFile("file", "tree.png", "image/png", new byte[]{1}),
                7L, ImagePurpose.AVATAR);

        assertTrue(future.get(5, TimeUnit.SECONDS)
                .matches("https://images\\.example\\.com/helptree-images/[0-9a-f-]{36}\\.png"));
        verify(siloClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void deleteUsesTheOwnershipRowKeyNotTheClientUrl() throws Exception {
        String url = "https://images.example.com/helptree-images/actual.png";
        ImageObject object = imageObject("actual.png", url, 1L);
        when(imageObjectService.markDeletedByUser(1L, "USER", url))
                .thenReturn(Optional.of(object));

        imageService.deleteImage(1L, "USER", url);

        ArgumentCaptor<RemoveObjectArgs> removed = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(siloClient).removeObject(removed.capture());
        assertEquals("actual.png", removed.getValue().object());
        assertEquals("helptree-images", removed.getValue().bucket());
    }

    @Test
    void deletePropagatesForbiddenAndNeverTouchesSilo() {
        String url = "https://images.example.com/helptree-images/foreign.png";
        when(imageObjectService.markDeletedByUser(1L, "USER", url))
                .thenThrow(new ForbiddenException("not yours"));

        assertThrows(ForbiddenException.class,
                () -> imageService.deleteImage(1L, "USER", url));
        verifyNoInteractions(siloClient);
    }

    @Test
    void releaseReplacedAvatarRemovesTheOldObjectOnceMarked() throws Exception {
        String oldUrl = "https://images.example.com/helptree-images/old.png";
        ImageObject object = imageObject("old.png", oldUrl, 1L);
        when(imageObjectService.releaseReplacedAvatar(1L, oldUrl))
                .thenReturn(Optional.of(object));

        imageService.releaseReplacedAvatar(1L, oldUrl);

        ArgumentCaptor<RemoveObjectArgs> removed = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(siloClient).removeObject(removed.capture());
        assertEquals("old.png", removed.getValue().object());
    }

    @Test
    void extractObjectKeyRejectsForeignBucketTraversalAndNestedKeys() {
        assertEquals(Optional.of("tree.png"), imageService.extractObjectKey(
                "https://images.example.com/helptree-images/tree.png"));
        assertEquals(Optional.empty(), imageService.extractObjectKey(
                "https://images.example.com/other-bucket/tree.png"));
        assertEquals(Optional.empty(), imageService.extractObjectKey(
                "https://images.example.com/helptree-images/../secret"));
        assertEquals(Optional.empty(), imageService.extractObjectKey(
                "https://images.example.com/helptree-images/nested/tree.png"));
        assertEquals(Optional.empty(), imageService.extractObjectKey(null));
    }

    @Test
    void tryRemoveObjectReportsFailureInsteadOfThrowing() throws Exception {
        doThrow(new RuntimeException("offline"))
                .when(siloClient).removeObject(any(RemoveObjectArgs.class));

        assertFalse(imageService.tryRemoveObject("tree.png"));
        assertTrue(imageService.tryRemoveObject(null));
    }

    private ProcessedImage processed(String extension, String contentType, byte[] data) {
        return new ProcessedImage(data, contentType, extension, 10, 10, "a".repeat(64));
    }

    private ImageObject imageObject(String key, String url, Long ownerId) {
        return ImageObject.builder()
                .id(5L)
                .ownerId(ownerId)
                .objectKey(key)
                .url(url)
                .contentType("image/png")
                .sizeBytes(3L)
                .purpose(ImagePurpose.POST)
                .status(ImageStatus.ACTIVE)
                .build();
    }
}
