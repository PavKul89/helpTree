package com.example.helpTree.mapper;

import com.example.helpTree.dto.users.UserDto;
import com.example.helpTree.entity.User;
import com.example.helpTree.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class UserMapperTest {

    private UserMapper userMapper;

    @BeforeEach
    public void setup() {
        userMapper = Mappers.getMapper(UserMapper.class);
    }

    @Test
    public void toDto_shouldMapAllFields() {
        User user = new User();
        user.setId(42L);
        user.setName("Ivan");
        user.setEmail("ivan@example.com");
        user.setPhone("+70000000000");
        user.setCity("Moscow");
        user.setHelpedCount(5);
        user.setDebtCount(1);
        user.setRating(4.5);
        user.setStatus(UserStatus.HELPER);
        user.setCreatedAt(LocalDateTime.of(2023,1,1,12,0));

        UserDto dto = userMapper.toDto(user);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getName()).isEqualTo("Ivan");
        assertThat(dto.getEmail()).isEqualTo("ivan@example.com");
        assertThat(dto.getPhone()).isEqualTo("+70000000000");
        assertThat(dto.getCity()).isEqualTo("Moscow");
        assertThat(dto.getHelpedCount()).isEqualTo(5);
        assertThat(dto.getDebtCount()).isEqualTo(1);
        assertThat(dto.getRating()).isEqualTo(4.5);
        assertThat(dto.getStatus()).isEqualTo(UserStatus.HELPER);
        assertThat(dto.getCreatedAt()).isEqualTo(LocalDateTime.of(2023,1,1,12,0));
    }
}
