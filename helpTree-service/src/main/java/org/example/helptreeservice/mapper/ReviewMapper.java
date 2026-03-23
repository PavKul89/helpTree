package org.example.helptreeservice.mapper;

import org.example.helptreeservice.dto.reviews.ReviewResponse;
import org.example.helptreeservice.entity.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(source = "help.id", target = "helpId")
    @Mapping(source = "fromUser.id", target = "fromUserId")
    @Mapping(source = "fromUser.name", target = "fromUserName")
    @Mapping(source = "toUser.id", target = "toUserId")
    @Mapping(source = "toUser.name", target = "toUserName")
    ReviewResponse toResponse(Review review);
}
