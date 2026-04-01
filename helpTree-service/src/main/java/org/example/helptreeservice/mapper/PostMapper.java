package org.example.helptreeservice.mapper;

import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.email", target = "userEmail")
    @Mapping(source = "user.rating", target = "authorRating")
    @Mapping(source = "user.city", target = "userCity")
    @Mapping(source = "user.blockedAt", target = "authorBlockedAt")
    @Mapping(source = "latitude", target = "latitude")
    @Mapping(source = "longitude", target = "longitude")
    @Mapping(source = "user.name", target = "authorName")
    @Mapping(source = "user.avatarUrl", target = "authorAvatarUrl")
    @Mapping(source = "helper.id", target = "helperId")
    @Mapping(source = "helper.name", target = "helperName")
    @Mapping(source = "status", target = "status")
    PostDto toDto(Post post);
}
