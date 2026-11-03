package ru.mifi.common;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    public record ErrorDto(Instant timestamp, int status, String message) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorDto> api(ApiException ex) {
        return error(ex.status(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorDto> invalid(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    ResponseEntity<ErrorDto> badRequest(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "Некорректные параметры запроса");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorDto> conflict(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "Нарушено ограничение данных");
    }

    private ResponseEntity<ErrorDto> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorDto(Instant.now(), status.value(), message));
    }
}
