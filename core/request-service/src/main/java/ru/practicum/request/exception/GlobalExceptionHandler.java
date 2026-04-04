package ru.practicum.request.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException; // ← ДОБАВИТЬ

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParams(MissingServletRequestParameterException e) {
        log.warn("Missing request parameter: {}", e.getMessage());
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),  // ← используем value()
                "Bad Request",
                e.getMessage()
        );
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(BadRequestException e) {
        log.warn("Bad request: {}", e.getMessage());
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),  // ← используем value()
                "Bad Request",
                e.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Type mismatch: {}", e.getMessage());
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Invalid parameter format"
        );
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException e) {
        log.warn("Not found: {}", e.getMessage());
        return new ErrorResponse(
                HttpStatus.NOT_FOUND,
                "Not Found",
                e.getMessage()
        );
    }

    @ExceptionHandler(DuplicatedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicated(DuplicatedException e) {
        log.warn("Duplicate: {}", e.getMessage());
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),  // ← используем value()
                "Conflict",
                e.getMessage()
        );
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException e) {
        log.warn("Conflict: {}", e.getMessage());
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),  // ← Использовать int вместо HttpStatus
                "Conflict",
                e.getMessage()
        );
    }

    // ✅ НОВЫЙ ОБРАБОТЧИК - для отсутствующих ресурсов (actuator/health, /)
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResourceFound(NoResourceFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return new ErrorResponse(
                HttpStatus.NOT_FOUND,
                "Not Found",
                "Resource not found: " + e.getResourcePath()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleException(Exception e) {
        log.error("Internal server error", e);
        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                e.getMessage()
        );
    }
}