package com.example.helpTree.mapper;

import com.example.helpTree.dto.helps.HelpResponse;
import com.example.helpTree.entity.Help;
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
