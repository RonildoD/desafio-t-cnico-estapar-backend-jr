package br.com.estapar.garage.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção de negócio da garagem. Carrega o HTTP status apropriado para ser
 * traduzido pelo {@link GlobalExceptionHandler}.
 */
public class GarageException extends RuntimeException {

    private final HttpStatus status;

    public GarageException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static GarageException notFound(String message) {
        return new GarageException(HttpStatus.NOT_FOUND, message);
    }

    public static GarageException conflict(String message) {
        return new GarageException(HttpStatus.CONFLICT, message);
    }

    public static GarageException badRequest(String message) {
        return new GarageException(HttpStatus.BAD_REQUEST, message);
    }
}
