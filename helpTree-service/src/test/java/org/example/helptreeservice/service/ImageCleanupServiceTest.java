package org.example.helptreeservice.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.example.helptreeservice.config.ImageCleanupProperties;
import org.example.helptreeservice.entity.ImageObject;
import org.example.helptreeservice.enums.ImagePurpose;
import org.example.helptreeservice.enums.ImageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageCleanupServiceTest {

    @Mock
    private ImageObjectService imageObjectService;

    @Mock
    private ImageService imageService;

    @Mock
    private MinioClient siloClient;

    private ImageCleanupProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private ImageCleanupService service;

    @BeforeEach
    void setUp() {
        properties = new ImageCleanupProperties();
        properties.setBatchSize(10);
        properties.setMaxBatchesPerRun(3);
        properties.setBucketScanEnabled(false);
        meterRegistry = new SimpleMeterRegistry();
        service = new ImageCleanupService(
                imageObjectService, imageService, siloClient, properties, meterRegistry);
        ReflectionTestUtils.setField(service, "bucket", "helptree-images");
    }

    @Test
    void removesTombstonedRowsFromSiloThenDeletesTheRow() {
        ImageObject object = imageObject(5L, "gone.png");
        when(imageObjectService.findDeletedBefore(any(LocalDateTime.class), eq(10)))
                .thenReturn(List.of(object), List.of());
        when(imageService.tryRemoveObject("gone.png")).thenReturn(true);
        when(imageObjectService.deleteDeletedRow(5L)).thenReturn(1);

        ImageCleanupService.CleanupSummary summary = service.cleanup();

        assertEquals(1, summary.deletedRows());
        verify(imageObjectService).deleteDeletedRow(5L);
        assertEquals(1.0, meterRegistry
                .get("helptree.images.cleanup.deleted").tag("source", "deleted")
                .counter().count());
    }

    @Test
    void aFailedSiloRemovalKeepsTheTombstoneForTheNextRun() {
        ImageObject object = imageObject(5L, "stuck.png");
        when(imageObjectService.findDeletedBefore(any(LocalDateTime.class), eq(10)))
                .thenReturn(List.of(object), List.of());
        when(imageService.tryRemoveObject("stuck.png")).thenReturn(false);

        ImageCleanupService.CleanupSummary summary = service.cleanup();

        assertEquals(0, summary.deletedRows());
        verify(imageObjectService, never()).deleteDeletedRow(any());
        assertEquals(1.0, meterRegistry
                .get("helptree.images.cleanup.failures").tag("source", "deleted")
                .counter().count());
    }

    @Test
    void orphanRowClaimedInTheMeantimeIsNotRemoved() {
        ImageObject object = imageObject(6L, "claimed.png");
        when(imageObjectService.findUnreferencedActive(any(LocalDateTime.class), eq(10)))
                .thenReturn(List.of(object), List.of());
        when(imageObjectService.markUnreferencedDeleted(eq(6L), any(LocalDateTime.class)))
                .thenReturn(0);

        ImageCleanupService.CleanupSummary summary = service.cleanup();

        assertEquals(0, summary.orphanRows());
        verify(imageService, never()).tryRemoveObject(any());
        verify(imageObjectService, never()).deleteDeletedRow(any());
    }

    @Test
    void unreferencedRowIsMarkedThenRemoved() {
        ImageObject object = imageObject(6L, "orphan.png");
        when(imageObjectService.findUnreferencedActive(any(LocalDateTime.class), eq(10)))
                .thenReturn(List.of(object), List.of());
        when(imageObjectService.markUnreferencedDeleted(eq(6L), any(LocalDateTime.class)))
                .thenReturn(1);
        when(imageService.tryRemoveObject("orphan.png")).thenReturn(true);
        when(imageObjectService.deleteDeletedRow(6L)).thenReturn(1);

        ImageCleanupService.CleanupSummary summary = service.cleanup();

        assertEquals(1, summary.orphanRows());
        verify(imageObjectService).deleteDeletedRow(6L);
        assertEquals(1.0, meterRegistry
                .get("helptree.images.cleanup.deleted").tag("source", "orphan")
                .counter().count());
    }

    @Test
    void bucketObjectWithoutAnOwnershipRowIsRemoved() throws Exception {
        properties.setBucketScanEnabled(true);
        Item orphan = item("orphan.png", LocalDateTime.now().minusDays(2));
        when(siloClient.listObjects(any(ListObjectsArgs.class)))
                .thenReturn(List.of(new Result<>(orphan)));
        when(imageObjectService.hasOwnershipRow("orphan.png")).thenReturn(false);
        when(imageService.tryRemoveObject("orphan.png")).thenReturn(true);

        ImageCleanupService.CleanupSummary summary = service.cleanup();

        assertEquals(1, summary.bucketObjects());
        verify(imageService).tryRemoveObject("orphan.png");
    }

    @Test
    void bucketObjectWithAnOwnershipRowIsKept() throws Exception {
        properties.setBucketScanEnabled(true);
        Item owned = item("owned.png", LocalDateTime.now().minusDays(2));
        when(siloClient.listObjects(any(ListObjectsArgs.class)))
                .thenReturn(List.of(new Result<>(owned)));
        when(imageObjectService.hasOwnershipRow("owned.png")).thenReturn(true);

        ImageCleanupService.CleanupSummary summary = service.cleanup();

        assertEquals(0, summary.bucketObjects());
        verify(imageService, never()).tryRemoveObject(any());
    }

    @Test
    void freshBucketObjectIsProtectedByTheTtl() throws Exception {
        properties.setBucketScanEnabled(true);
        Item fresh = item("in-flight.png", LocalDateTime.now());
        when(siloClient.listObjects(any(ListObjectsArgs.class)))
                .thenReturn(List.of(new Result<>(fresh)));

        ImageCleanupService.CleanupSummary summary = service.cleanup();

        assertEquals(0, summary.bucketObjects());
        verifyNoInteractions(imageService);
        verify(imageObjectService, never()).hasOwnershipRow(any());
    }

    @Test
    void bucketScanCanBeDisabled() {
        properties.setBucketScanEnabled(false);

        ImageCleanupService.CleanupSummary summary = service.cleanup();

        assertEquals(0, summary.bucketObjects());
        verifyNoInteractions(siloClient);
    }

    @Test
    void aListFailureIsRecordedAndDoesNotAbortTheRun() throws Exception {
        properties.setBucketScanEnabled(true);
        when(siloClient.listObjects(any(ListObjectsArgs.class)))
                .thenThrow(new RuntimeException("Silo offline"));

        ImageCleanupService.CleanupSummary summary = service.cleanup();

        assertEquals(0, summary.bucketObjects());
        assertEquals(1.0, meterRegistry
                .get("helptree.images.cleanup.failures").tag("source", "bucket")
                .counter().count());
    }

    @Test
    void disabledSchedulerDoesNothing() {
        properties.setEnabled(false);

        service.scheduledCleanup();

        verifyNoInteractions(imageObjectService, imageService, siloClient);
    }

    private ImageObject imageObject(Long id, String key) {
        return ImageObject.builder()
                .id(id)
                .ownerId(1L)
                .objectKey(key)
                .url("https://cdn/helptree-images/" + key)
                .contentType("image/png")
                .sizeBytes(10L)
                .purpose(ImagePurpose.POST)
                .status(ImageStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
    }

    private Item item(String name, LocalDateTime lastModified) {
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn(name);
        when(item.isDir()).thenReturn(false);
        when(item.lastModified()).thenReturn(
                ZonedDateTime.of(lastModified, ZoneId.systemDefault()));
        return item;
    }
}
