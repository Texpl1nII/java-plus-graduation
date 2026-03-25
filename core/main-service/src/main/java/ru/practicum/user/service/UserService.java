package ru.practicum.user.service;

import ru.practicum.user.dto.UserDto;
import ru.practicum.user.dto.UserRequestDto;

import java.util.List;

public interface UserService {
    UserDto createUser(UserRequestDto userRequestDto);

    List<UserDto> getUsers(List<Long> ids, int from, int size);

    UserDto getUser(Long userId);

    void deleteUser(Long userId);
}
