package ru.practicum.request.exception;

public class DuplicatedException extends RuntimeException {
    public DuplicatedException(String message) {
        super(message);
    }
}