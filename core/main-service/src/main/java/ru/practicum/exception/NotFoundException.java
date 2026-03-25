package ru.practicum.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String entity, Long id) {
        super(String.format("%s with id=%d was not found", entity, id));
    }
}


