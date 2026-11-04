package org.example.helptreeservice.mapper;

import org.example.helptreeservice.dto.comments.CommentResponse;
import org.example.helptreeservice.entity.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    @Mapping(target = "postId", source = "post.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", source = "user.name")
    @Mapping(target = "userAvatarUrl", source = "user.avatarUrl")
    @Mapping(target = "parentCommentId", source = "parentCommentId")
    @Mapping(target = "content",
             expression = "java(Boolean.TRUE.equals(comment.getDeleted()) ? null : comment.getContent())")
    CommentResponse toResponse(Comment comment);

    List<CommentResponse> toResponseList(List<Comment> comments);
}
