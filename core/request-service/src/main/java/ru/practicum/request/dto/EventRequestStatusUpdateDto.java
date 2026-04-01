package ru.practicum.request.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.request.enums.RequestStatus;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventRequestStatusUpdateDto {
    @NotEmpty(message = "Request IDs list cannot be empty")
    private List<Long> requestIds;

    @NotNull(message = "Status cannot be null")
    private RequestStatus status;
}
