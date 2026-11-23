package br.dev.cartaorbe;

import org.springframework.http.HttpStatus;

final class ApiProblem extends RuntimeException {
    final HttpStatus status;

    ApiProblem(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
