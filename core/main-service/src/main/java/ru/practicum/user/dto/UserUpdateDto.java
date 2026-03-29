package ru.practicum.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateDto {
    @NotNull
    @Positive
    private Long id;

    @Size(min = 2, max = 250)
    private String name;

    @Email
    @Size(min = 6, max = 254)
    private String email;
}
