package com.example.demo.presentation.exception;

import java.util.List;

/**
 * Raised when query parameters of a request are missing, out of range or not one of the allowed values.
 */
public class InvalidQueryParameterException extends RuntimeException {

    private final List<String> errors;

    public InvalidQueryParameterException(List<String> errors) {
        super("Invalid query parameters");
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
