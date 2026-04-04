package ru.practicum.request.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ErrorResponse {
    private int status;  // ← ИЗМЕНЕНО: int вместо HttpStatus
    private String reason;
    private String message;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp = LocalDateTime.now();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error;

    public ErrorResponse(HttpStatus status, String reason, String message) {
        this.status = status.value();  // ← ИЗМЕНЕНО: берем числовое значение
        this.reason = reason;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    // ✅ ДОБАВИТЬ КОНСТРУКТОР ДЛЯ ЧИСЛОВОГО СТАТУСА
    public ErrorResponse(int status, String reason, String message) {
        this.status = status;
        this.reason = reason;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
