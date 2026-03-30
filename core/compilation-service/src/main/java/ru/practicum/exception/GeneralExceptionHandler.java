package ru.practicum.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFoundException(final NotFoundException e) {
        String reason = "The required object was not found.";
        log.error("{}. {}", reason, e.getMessage());
        return new ErrorResponse(HttpStatus.NOT_FOUND, reason, e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse duplicatedException(final DuplicatedException e) {
        String reason = "The object already exists";
        log.error("{}. {}", reason, e.getMessage());
        return new ErrorResponse(HttpStatus.CONFLICT, reason, e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse validationException(final ValidationException e) {
        String reason = "Data validation failed";
        log.error("{}. {}", reason, e.getMessage());
        return new ErrorResponse(HttpStatus.BAD_REQUEST, reason, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        String reason = "Incorrectly made request.";
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "Field: " + error.getField() + ". Error: " + error.getDefaultMessage() + ". Value: " + error.getRejectedValue())
                .collect(Collectors.joining("; "));
        log.error("{}. {}", reason, message);
        return new ErrorResponse(HttpStatus.BAD_REQUEST, reason, message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflictException(final ConflictException e) {
        String reason = "For the requested operation the conditions are not met.";
        log.error("{}. {}", reason, e.getMessage());
        return new ErrorResponse(HttpStatus.CONFLICT, reason, e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrityViolation(final org.springframework.dao.DataIntegrityViolationException e) {
        String reason = "Integrity constraint has been violated.";
        String message = e.getMessage();
        log.error("{}. {}", reason, message);
        return new ErrorResponse(HttpStatus.CONFLICT, reason, message);
    }
}
