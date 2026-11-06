package org.example.helptreeservice.mapper;

import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.entity.Help;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HelpMapper {

    @Mapping(source = "post.id", target = "postId")
    @Mapping(source = "post.title", target = "postTitle")
    @Mapping(source = "helper.id", target = "helperId")
    @Mapping(source = "helper.name", target = "helperName")
    @Mapping(source = "receiver.id", target = "receiverId")
    @Mapping(source = "receiver.name", target = "receiverName")
    @Mapping(source = "status", target = "status")
    HelpResponse toResponse(Help help);

    /**
     * Public projection of a help record. Cancellation details (who cancelled
     * and why) belong to the two participants and admins only, so they are
     * omitted from lists that any signed-in user may read. Participants can
     * still read the audit trail through {@code GET /api/helps/{id}/history}.
     */
    @Mapping(source = "post.id", target = "postId")
    @Mapping(source = "post.title", target = "postTitle")
    @Mapping(source = "helper.id", target = "helperId")
    @Mapping(source = "helper.name", target = "helperName")
    @Mapping(source = "receiver.id", target = "receiverId")
    @Mapping(source = "receiver.name", target = "receiverName")
    @Mapping(source = "status", target = "status")
    @Mapping(target = "cancelReason", ignore = true)
    @Mapping(target = "cancelledById", ignore = true)
    @Mapping(target = "cancelInitiator", ignore = true)
    HelpResponse toPublicResponse(Help help);
}
