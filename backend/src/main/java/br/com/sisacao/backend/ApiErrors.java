package br.com.sisacao.backend;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException e) { return ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason()); }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail upstream() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Serviço de agentes indisponível"); }
}
