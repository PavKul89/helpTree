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
}
