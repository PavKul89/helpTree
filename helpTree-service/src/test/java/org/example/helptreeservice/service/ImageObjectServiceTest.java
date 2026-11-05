package org.example.helptreeservice.service;

import org.example.helptreeservice.config.ImageUploadProperties;
import org.example.helptreeservice.entity.ImageObject;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.ImagePurpose;
import org.example.helptreeservice.enums.ImageStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.ImageObjectRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageObjectServiceTest {

    @Mock
    private ImageObjectRepository imageObjectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostRepository postRepository;

    private ImageUploadProperties properties;
    private ImageObjectService service;

    @BeforeEach
    void setUp() {
        properties = new ImageUploadProperties();
        service = new ImageObjectService(
                imageObjectRepository, userRepository, postRepository, properties);
    }

    @Test
    void registerPersistsEveryObjectAsActiveUnderTheLockedOwner() {
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user(7L)));
        when(imageObjectRepository.sumSizeByOwnerAndStatus(7L, ImageStatus.ACTIVE))
                .thenReturn(10L);
        when(imageObjectRepository.countByOwnerIdAndStatus(7L, ImageStatus.ACTIVE))
                .thenReturn(2L);
        when(imageObjectRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ImageObject> saved = service.register(7L, List.of(
                newImage("a.png", "https://cdn/helptree-images/a.png", 100),
                newImage("b.jpg", "https://cdn/helptree-images/b.jpg", 200)),
                ImagePurpose.POST);

        assertEquals(2, saved.size());
        assertEquals(7L, saved.get(0).getOwnerId());
        assertEquals(ImageStatus.ACTIVE, saved.get(0).getStatus());
        assertEquals(ImagePurpose.POST, saved.get(0).getPurpose());
        assertEquals("a".repeat(64), saved.get(0).getSha256());
        assertNotNull(saved.get(0).getCreatedAt());
        verify(userRepository).findByIdForUpdate(7L);
    }

    @Test
    void registerRejectsUnknownOrDeletedOwner() {
        properties.setMaxFiles(10);
        assertThrows(BadRequestException.class,
                () -> service.register(null, List.of(newImage("a.png", "u", 1)), ImagePurpose.POST));

        User deleted = user(7L);
        deleted.setDeleted(true);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(deleted));

        assertThrows(NotFoundException.class,
                () -> service.register(7L, List.of(newImage("a.png", "u", 1)), ImagePurpose.POST));
        verify(imageObjectRepository, never()).saveAll(anyList());
    }

    @Test
    void registerRejectsAnEmptyBatch() {
        assertThrows(BadRequestException.class,
                () -> service.register(7L, List.of(), ImagePurpose.POST));
        verifyNoInteractions(imageObjectRepository);
    }

    @Test
    void registerRejectsAnUploadThatExceedsTheByteQuota() {
        properties.setQuotaBytes(100);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user(7L)));
        when(imageObjectRepository.sumSizeByOwnerAndStatus(7L, ImageStatus.ACTIVE))
                .thenReturn(50L);
        when(imageObjectRepository.countByOwnerIdAndStatus(7L, ImageStatus.ACTIVE))
                .thenReturn(0L);

        assertThrows(BadRequestException.class,
                () -> service.register(7L, List.of(newImage("a.png", "u", 51)), ImagePurpose.POST));
        verify(imageObjectRepository, never()).saveAll(anyList());
    }

    @Test
    void registerRejectsAnUploadThatExceedsTheObjectQuota() {
        properties.setQuotaCount(2);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user(7L)));
        when(imageObjectRepository.sumSizeByOwnerAndStatus(7L, ImageStatus.ACTIVE))
                .thenReturn(0L);
        when(imageObjectRepository.countByOwnerIdAndStatus(7L, ImageStatus.ACTIVE))
                .thenReturn(1L);

        assertThrows(BadRequestException.class, () -> service.register(7L, List.of(
                newImage("a.png", "u1", 1),
                newImage("b.png", "u2", 1)), ImagePurpose.POST));
        verify(imageObjectRepository, never()).saveAll(anyList());
    }

    @Test
    void claimRejectsAnImageOwnedBySomebodyElse() {
        ImageObject foreign = imageObject(1L, ImagePurpose.GENERAL, "https://cdn/x.png");
        when(imageObjectRepository.findAllByUrlInAndStatus(anyCollection(), eq(ImageStatus.ACTIVE)))
                .thenReturn(List.of(foreign));

        assertThrows(ForbiddenException.class,
                () -> service.claimForOwner(7L, List.of("https://cdn/x.png"), ImagePurpose.POST));
    }

    @Test
    void claimRejectsAnUnknownOrDeletedUrl() {
        when(imageObjectRepository.findAllByUrlInAndStatus(anyCollection(), eq(ImageStatus.ACTIVE)))
                .thenReturn(List.of());

        assertThrows(BadRequestException.class,
                () -> service.claimForOwner(7L, List.of("https://cdn/missing.png"), ImagePurpose.POST));
    }

    @Test
    void claimRejectsUsingAnAvatarAsPostImage() {
        ImageObject avatar = imageObject(7L, ImagePurpose.AVATAR, "https://cdn/avatar.png");
        when(imageObjectRepository.findAllByUrlInAndStatus(anyCollection(), eq(ImageStatus.ACTIVE)))
                .thenReturn(List.of(avatar));

        assertThrows(BadRequestException.class,
                () -> service.claimForOwner(7L, List.of("https://cdn/avatar.png"), ImagePurpose.POST));
        assertEquals(ImagePurpose.AVATAR, avatar.getPurpose());
    }

    @Test
    void claimMovesAGeneralImageToThePostPurpose() {
        ImageObject general = imageObject(7L, ImagePurpose.GENERAL, "https://cdn/x.png");
        when(imageObjectRepository.findAllByUrlInAndStatus(anyCollection(), eq(ImageStatus.ACTIVE)))
                .thenReturn(List.of(general));

        service.claimForOwner(7L, List.of("https://cdn/x.png"), ImagePurpose.POST);

        assertEquals(ImagePurpose.POST, general.getPurpose());
        verify(imageObjectRepository).save(general);
    }

    @Test
    void claimIgnoresAnEmptyList() {
        service.claimForOwner(7L, List.of(), ImagePurpose.POST);
        service.claimForOwner(7L, null, ImagePurpose.POST);

        verifyNoInteractions(imageObjectRepository);
    }

    @Test
    void ownerDeletionDetachesEveryReferenceAndCreatesATombstone() {
        ImageObject object = imageObject(7L, ImagePurpose.POST, "https://cdn/x.png");
        when(imageObjectRepository.findByUrl("https://cdn/x.png"))
                .thenReturn(Optional.of(object));

        Optional<ImageObject> result = service.markDeletedByUser(
                7L, "USER", "https://cdn/x.png");

        assertTrue(result.isPresent());
        assertEquals(ImageStatus.DELETED, object.getStatus());
        assertNotNull(object.getDeletedAt());
        verify(userRepository).clearAvatarUrl("https://cdn/x.png");
        verify(postRepository).deletePostImageUrl("https://cdn/x.png");
        verify(imageObjectRepository).save(object);
    }

    @Test
    void deletionOfAnUnknownObjectIsRejected() {
        when(imageObjectRepository.findByUrl("https://cdn/x.png")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.markDeletedByUser(7L, "USER", "https://cdn/x.png"));
    }

    @Test
    void aUserCannotDeleteSomebodyElsesImage() {
        ImageObject object = imageObject(9L, ImagePurpose.POST, "https://cdn/x.png");
        when(imageObjectRepository.findByUrl("https://cdn/x.png"))
                .thenReturn(Optional.of(object));

        assertThrows(ForbiddenException.class,
                () -> service.markDeletedByUser(7L, "USER", "https://cdn/x.png"));

        assertEquals(ImageStatus.ACTIVE, object.getStatus());
        verify(userRepository, never()).clearAvatarUrl(any());
        verify(postRepository, never()).deletePostImageUrl(any());
    }

    @Test
    void anAdminCanDeleteSomebodyElsesImage() {
        ImageObject object = imageObject(9L, ImagePurpose.POST, "https://cdn/x.png");
        when(imageObjectRepository.findByUrl("https://cdn/x.png"))
                .thenReturn(Optional.of(object));

        Optional<ImageObject> result = service.markDeletedByUser(
                1L, "ADMIN", "https://cdn/x.png");

        assertTrue(result.isPresent());
        assertEquals(ImageStatus.DELETED, object.getStatus());
    }

    @Test
    void repeatedDeletionIsIdempotent() {
        ImageObject object = imageObject(7L, ImagePurpose.POST, "https://cdn/x.png");
        object.setStatus(ImageStatus.DELETED);
        object.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(imageObjectRepository.findByUrl("https://cdn/x.png"))
                .thenReturn(Optional.of(object));

        Optional<ImageObject> result = service.markDeletedByUser(
                7L, "USER", "https://cdn/x.png");

        assertTrue(result.isEmpty());
        verify(userRepository, never()).clearAvatarUrl(any());
        verify(imageObjectRepository, never()).save(any());
    }

    @Test
    void replacedAvatarIsReleasedWhenNoPostUsesIt() {
        ImageObject object = imageObject(7L, ImagePurpose.AVATAR, "https://cdn/old.png");
        when(imageObjectRepository.findByUrlAndStatus("https://cdn/old.png", ImageStatus.ACTIVE))
                .thenReturn(Optional.of(object));
        when(postRepository.existsPostImageUrl("https://cdn/old.png")).thenReturn(false);

        Optional<ImageObject> result = service.releaseReplacedAvatar(7L, "https://cdn/old.png");

        assertTrue(result.isPresent());
        assertEquals(ImageStatus.DELETED, object.getStatus());
        verify(imageObjectRepository).save(object);
    }

    @Test
    void replacedAvatarStaysAliveWhenAPostStillUsesIt() {
        ImageObject object = imageObject(7L, ImagePurpose.AVATAR, "https://cdn/shared.png");
        when(imageObjectRepository.findByUrlAndStatus("https://cdn/shared.png", ImageStatus.ACTIVE))
                .thenReturn(Optional.of(object));
        when(postRepository.existsPostImageUrl("https://cdn/shared.png")).thenReturn(true);

        Optional<ImageObject> result = service.releaseReplacedAvatar(7L, "https://cdn/shared.png");

        assertTrue(result.isEmpty());
        assertEquals(ImageStatus.ACTIVE, object.getStatus());
        verify(imageObjectRepository, never()).save(any());
    }

    @Test
    void releasingSomebodyElsesAvatarDoesNothing() {
        ImageObject object = imageObject(9L, ImagePurpose.AVATAR, "https://cdn/old.png");
        when(imageObjectRepository.findByUrlAndStatus("https://cdn/old.png", ImageStatus.ACTIVE))
                .thenReturn(Optional.of(object));

        Optional<ImageObject> result = service.releaseReplacedAvatar(7L, "https://cdn/old.png");

        assertTrue(result.isEmpty());
        assertEquals(ImageStatus.ACTIVE, object.getStatus());
        verifyNoInteractions(postRepository);
    }

    @Test
    void releasingANullUrlIsANoOp() {
        assertTrue(service.releaseReplacedAvatar(7L, null).isEmpty());
        verifyNoInteractions(imageObjectRepository, postRepository);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setDeleted(false);
        return user;
    }

    private ImageObjectService.NewImage newImage(String key, String url, long size) {
        return new ImageObjectService.NewImage(
                key, url, "image/png", size, 10, 10, "a".repeat(64));
    }

    private ImageObject imageObject(Long ownerId, ImagePurpose purpose, String url) {
        return ImageObject.builder()
                .id(5L)
                .ownerId(ownerId)
                .objectKey(url.substring(url.lastIndexOf('/') + 1))
                .url(url)
                .contentType("image/png")
                .sizeBytes(10L)
                .purpose(purpose)
                .status(ImageStatus.ACTIVE)
                .build();
    }
}
