package com.example.helpTree.mapper;

import com.example.helpTree.dto.users.UpdateUserRequest;
import com.example.helpTree.dto.users.UserDto;
import com.example.helpTree.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserDto toDto(User user);

    void updateFromDto(UpdateUserRequest dto, @MappingTarget User user);
}
