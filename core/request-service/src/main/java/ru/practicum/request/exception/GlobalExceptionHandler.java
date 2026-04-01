package ru.practicum.request.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException e) {
        log.error("Not found: {}", e.getMessage());
        return new ErrorResponse(
                HttpStatus.NOT_FOUND,
                "Not Found",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException e) {
        log.error("Conflict: {}", e.getMessage());
        return new ErrorResponse(
                HttpStatus.CONFLICT,
                "Conflict",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(ValidationException e) {
        log.error("Validation error: {}", e.getMessage());
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        log.error("Validation error: {}", e.getMessage());
        String errorMessage = e.getBindingResult().getAllErrors().isEmpty()
                ? "Validation error"
                : e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                errorMessage
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleException(Exception e) {
        log.error("Internal server error", e);
        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "Internal server error"
        );
    }
}