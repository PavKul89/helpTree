package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UpdateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.UserMapper;
import org.example.helptreeservice.repository.UserRepository;
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
        log.info("Создание нового пользователя с email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Попытка создания пользователя с уже существующим email: {}", request.getEmail());
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
        log.info("Пользователь успешно создан с ID: {}, email: {}", savedUser.getId(), savedUser.getEmail());
        return userMapper.toDto(savedUser);
    }

    public UserDto getCurrentUser() {
        log.info("Получение информации о текущем авторизованном пользователе");
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            log.debug("Текущий пользователь из контекста безопасности: {}", userDetails.getUsername());
            UserDto userDto = getUserByEmail(userDetails.getUsername());
            log.info("Информация о текущем пользователе успешно получена: ID={}, email={}",
                    userDto.getId(), userDto.getEmail());
            return userDto;
        } catch (Exception e) {
            log.error("Ошибка при получении информации о текущем пользователе", e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        log.info("Запрос пользователя по ID: {}", id);
        try {
            UserDto userDto = userMapper.toDto(getUserEntityById(id));
            log.info("Пользователь с ID {} найден: {}", id, userDto.getEmail());
            return userDto;
        } catch (NotFoundException e) {
            log.warn("Пользователь с ID {} не найден", id);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при поиске пользователя с ID: {}", id, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public UserDto getUserByEmail(String email) {
        log.info("Поиск пользователя по email: {}", email);
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с email: " + email));
            log.info("Пользователь с email {} найден, ID: {}", email, user.getId());
            return userMapper.toDto(user);
        } catch (NotFoundException e) {
            log.warn("Пользователь с email {} не найден", email);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при поиске пользователя с email: {}", email, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        log.info("Запрос списка всех активных пользователей");
        try {
            List<UserDto> users = userRepository.findAll().stream()
                    .filter(u -> !u.getDeleted())
                    .map(userMapper::toDto)
                    .collect(Collectors.toList());
            log.info("Получен список пользователей, количество: {}", users.size());
            log.debug("ID пользователей в списке: {}", users.stream().map(UserDto::getId).collect(Collectors.toList()));
            return users;
        } catch (Exception e) {
            log.error("Ошибка при получении списка всех пользователей", e);
            throw e;
        }
    }

    public UserDto updateUser(Long id, UpdateUserRequest request) {
        log.info("Обновление данных пользователя с ID: {}", id);
        log.debug("Данные для обновления: name={}, email={}, phone={}, city={}",
                request.getName(), request.getEmail(), request.getPhone(), request.getCity());

        try {
            User user = getUserEntityById(id);
            log.debug("Найден пользователь для обновления: текущий email={}", user.getEmail());

            // Проверяем, что пользователь обновляет свой профиль или это админ
            checkUserAccess(user);
            log.debug("Проверка доступа для пользователя ID: {} пройдена", id);

            boolean changed = false;

            if (request.getName() != null && !request.getName().equals(user.getName())) {
                user.setName(request.getName());
                changed = true;
                log.debug("Изменено имя пользователя на: {}", request.getName());
            }

            if (request.getEmail() != null) {
                if (!request.getEmail().equals(user.getEmail())) {
                    if (userRepository.existsByEmail(request.getEmail())) {
                        log.warn("Попытка обновления на уже существующий email: {}", request.getEmail());
                        throw new ConflictException("Email уже используется");
                    }
                    user.setEmail(request.getEmail());
                    changed = true;
                    log.debug("Изменен email пользователя на: {}", request.getEmail());
                }
            }

            if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
                user.setPhone(request.getPhone());
                changed = true;
                log.debug("Изменен телефон пользователя на: {}", request.getPhone());
            }

            if (request.getCity() != null && !request.getCity().equals(user.getCity())) {
                user.setCity(request.getCity());
                changed = true;
                log.debug("Изменен город пользователя на: {}", request.getCity());
            }

            if (changed) {
                user.setUpdatedAt(LocalDateTime.now());
                User updatedUser = userRepository.save(user);
                log.info("Данные пользователя с ID: {} успешно обновлены", id);
                return userMapper.toDto(updatedUser);
            } else {
                log.info("Нет изменений для обновления пользователя с ID: {}", id);
                return userMapper.toDto(user);
            }

        } catch (NotFoundException e) {
            log.warn("Не удалось обновить пользователя: ID {} не найден", id);
            throw e;
        } catch (ConflictException e) {
            log.warn("Конфликт при обновлении пользователя ID {}: {}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при обновлении пользователя с ID: {}", id, e);
            throw e;
        }
    }

    public void deleteUser(Long id) {
        log.info("Запрос на мягкое удаление пользователя с ID: {}", id);
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

            if (user.getDeleted()) {
                log.warn("Попытка удалить уже удаленного пользователя с ID: {}", id);
                throw new ConflictException("Пользователь уже был удалён");
            }

            user.setDeleted(true);
            user.setDeletedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Пользователь с ID: {} успешно помечен как удаленный", id);
            log.debug("Детали удаленного пользователя: email={}, время удаления={}",
                    user.getEmail(), user.getDeletedAt());

        } catch (NotFoundException e) {
            log.warn("Не удалось удалить пользователя: ID {} не найден", id);
            throw e;
        } catch (ConflictException e) {
            log.warn("Не удалось удалить пользователя: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при удалении пользователя с ID: {}", id, e);
            throw e;
        }
    }

    public void restoreUser(Long id) {
        log.info("Запрос на восстановление пользователя с ID: {}", id);
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

            if (!user.getDeleted()) {
                log.warn("Попытка восстановить не удаленного пользователя с ID: {}", id);
                throw new ConflictException("Пользователь не удалён");
            }

            user.setDeleted(false);
            user.setDeletedAt(null);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Пользователь с ID: {} успешно восстановлен", id);
            log.debug("Детали восстановленного пользователя: email={}", user.getEmail());

        } catch (NotFoundException e) {
            log.warn("Не удалось восстановить пользователя: ID {} не найден", id);
            throw e;
        } catch (ConflictException e) {
            log.warn("Не удалось восстановить пользователя: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при восстановлении пользователя с ID: {}", id, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public User getUserEntityById(Long id) {
        log.debug("Поиск сущности пользователя по ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

        if (user.getDeleted()) {
            log.debug("Пользователь с ID {} найден, но помечен как удаленный", id);
            throw new NotFoundException("Пользователь не найден с id: " + id);
        }

        log.debug("Сущность пользователя с ID {} успешно найдена", id);
        return user;
    }

    private void checkUserAccess(User user) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        log.debug("Проверка доступа для пользователя {}. Текущий пользователь: {}, isAdmin: {}",
                user.getEmail(), userDetails.getUsername(), isAdmin);

        if (!isAdmin && !userDetails.getUsername().equals(user.getEmail())) {
            log.warn("Отказ в доступе: пользователь {} попытался изменить данные пользователя {}",
                    userDetails.getUsername(), user.getEmail());
            throw new org.springframework.security.access.AccessDeniedException("Нет прав для этого действия");
        }

        log.debug("Доступ разрешен для пользователя {}", userDetails.getUsername());
    }

    // Методы для логики помощи
    public void incrementHelpedCount(Long receiverId) {
        log.info("Увеличение счетчика долгов для получателя помощи с ID: {}", receiverId);
        try {
            User receiver = getUserEntityById(receiverId);
            int oldDebtCount = receiver.getDebtCount();
            UserStatus oldStatus = receiver.getStatus();

            receiver.setDebtCount(receiver.getDebtCount() + 2);

            log.debug("Получатель ID {}: debtCount изменен с {} на {}",
                    receiverId, oldDebtCount, receiver.getDebtCount());

            if (receiver.getStatus() == UserStatus.NEWBIE) {
                receiver.setStatus(UserStatus.HELPER);
                log.debug("Статус получателя ID {} изменен с {} на {}",
                        receiverId, oldStatus, receiver.getStatus());
            }
            if (receiver.getDebtCount() > 0 && receiver.getStatus() != UserStatus.DEBTOR) {
                receiver.setStatus(UserStatus.DEBTOR);
                log.debug("Статус получателя ID {} изменен на DEBTOR", receiverId);
            }

            receiver.setUpdatedAt(LocalDateTime.now());
            userRepository.save(receiver);

            log.info("Счетчик долгов для получателя ID {} успешно обновлен", receiverId);

        } catch (Exception e) {
            log.error("Ошибка при увеличении счетчика долгов для получателя ID: {}", receiverId, e);
            throw e;
        }
    }

    public void userHelpedSomeone(Long helperId) {
        log.info("Обработка помощи от пользователя с ID: {}", helperId);
        try {
            User helper = getUserEntityById(helperId);
            int oldHelpedCount = helper.getHelpedCount();
            int oldDebtCount = helper.getDebtCount();
            UserStatus oldStatus = helper.getStatus();

            helper.setHelpedCount(helper.getHelpedCount() + 1);
            log.debug("Помощник ID {}: helpedCount изменен с {} на {}",
                    helperId, oldHelpedCount, helper.getHelpedCount());

            if (helper.getDebtCount() > 0) {
                helper.setDebtCount(helper.getDebtCount() - 1);
                log.debug("Помощник ID {}: debtCount изменен с {} на {}",
                        helperId, oldDebtCount, helper.getDebtCount());
            }

            if (helper.getDebtCount() == 0 && helper.getStatus() != UserStatus.ACTIVE) {
                helper.setStatus(UserStatus.ACTIVE);
                log.debug("Статус помощника ID {} изменен с {} на ACTIVE",
                        helperId, oldStatus);
            }

            helper.setUpdatedAt(LocalDateTime.now());
            userRepository.save(helper);

            log.info("Данные помощника ID {} успешно обновлены", helperId);

        } catch (Exception e) {
            log.error("Ошибка при обработке помощи от пользователя ID: {}", helperId, e);
            throw e;
        }
    }

    @Transactional
    public void processHelp(Long helperId, Long receiverId) {
        log.info("Начало процесса помощи: helperId={}, receiverId={}", helperId, receiverId);
        try {
            log.debug("Шаг 1: Обработка помощи от пользователя {}", helperId);
            userHelpedSomeone(helperId);

            log.debug("Шаг 2: Обновление счетчика получателя {}", receiverId);
            incrementHelpedCount(receiverId);

            log.info("Процесс помощи между helperId={} и receiverId={} успешно завершен",
                    helperId, receiverId);

        } catch (Exception e) {
            log.error("Ошибка в процессе помощи: helperId={}, receiverId={}",
                    helperId, receiverId, e);
            throw e;
        }
    }
}