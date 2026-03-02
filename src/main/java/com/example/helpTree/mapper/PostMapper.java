package com.example.helpTree.mapper;

import com.example.helpTree.dto.posts.PostDto;
import com.example.helpTree.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.email", target = "userEmail")
    @Mapping(source = "user.rating", target = "userRating")
    @Mapping(source = "helper.id", target = "helperId")
    @Mapping(source = "helper.name", target = "helperName")
    @Mapping(source = "status", target = "status")
    PostDto toDto(Post post);
}
