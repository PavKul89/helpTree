package com.example.helpTree.service;

import com.example.helpTree.dto.users.CreateUserRequest;
import com.example.helpTree.dto.users.UpdateUserRequest;
import com.example.helpTree.dto.users.UserDto;
import com.example.helpTree.entity.User;
import com.example.helpTree.enums.Role;
import com.example.helpTree.enums.UserStatus;
import com.example.helpTree.exception.ConflictException;
import com.example.helpTree.exception.NotFoundException;
import com.example.helpTree.mapper.UserMapper;
import com.example.helpTree.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserDto createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Пользователь с таким email уже существует");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setCity(request.getCity());
        user.setHelpedCount(0);
        user.setDebtCount(0);
        user.setRating(0.0);
        user.setStatus(UserStatus.NEWBIE);
        user.setRole(Role.USER);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }

    public UserDto getCurrentUser() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return getUserByEmail(userDetails.getUsername());
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        return userMapper.toDto(getUserEntityById(id));
    }

    @Transactional(readOnly = true)
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с email: " + email));
        return userMapper.toDto(user);
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !u.getDeleted())
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    public UserDto updateUser(Long id, UpdateUserRequest request) {
        User user = getUserEntityById(id);

        // Проверяем, что пользователь обновляет свой профиль или это админ
        checkUserAccess(user);

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null) {
            if (!request.getEmail().equals(user.getEmail()) &&
                    userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email уже используется");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getCity() != null) {
            user.setCity(request.getCity());
        }

        user.setUpdatedAt(LocalDateTime.now());
        User updatedUser = userRepository.save(user);
        return userMapper.toDto(updatedUser);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

        if (user.getDeleted()) {
            throw new ConflictException("Пользователь уже был удалён");
        }

        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void restoreUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

        if (!user.getDeleted()) {
            throw new ConflictException("Пользователь не удалён");
        }

        user.setDeleted(false);
        user.setDeletedAt(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserEntityById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));
        if (user.getDeleted()) {
            throw new NotFoundException("Пользователь не найден с id: " + id);
        }
        return user;
    }

    private void checkUserAccess(User user) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !userDetails.getUsername().equals(user.getEmail())) {
            throw new org.springframework.security.access.AccessDeniedException("Нет прав для этого действия");
        }
    }

    // Методы для логики помощи
    public void incrementHelpedCount(Long receiverId) {
        User receiver = getUserEntityById(receiverId);
        receiver.setDebtCount(receiver.getDebtCount() + 2);

        if (receiver.getStatus() == UserStatus.NEWBIE) {
            receiver.setStatus(UserStatus.HELPER);
        }
        if (receiver.getDebtCount() > 0) {
            receiver.setStatus(UserStatus.DEBTOR);
        }

        receiver.setUpdatedAt(LocalDateTime.now());
        userRepository.save(receiver);
    }

    public void userHelpedSomeone(Long helperId) {
        User helper = getUserEntityById(helperId);
        helper.setHelpedCount(helper.getHelpedCount() + 1);

        if (helper.getDebtCount() > 0) {
            helper.setDebtCount(helper.getDebtCount() - 1);
        }

        if (helper.getDebtCount() == 0) {
            helper.setStatus(UserStatus.ACTIVE);
        }

        helper.setUpdatedAt(LocalDateTime.now());
        userRepository.save(helper);
    }

    @Transactional
    public void processHelp(Long helperId, Long receiverId) {
        userHelpedSomeone(helperId);
        incrementHelpedCount(receiverId);
    }
}