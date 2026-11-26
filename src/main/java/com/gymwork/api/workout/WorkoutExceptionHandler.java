package com.gymwork.api.workout;

import com.gymwork.api.common.exception.GlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class WorkoutExceptionHandler {

    /** 409 com o ID da sessão em andamento, para o app oferecer "retomar treino". */
    @ExceptionHandler(SessionInProgressException.class)
    ProblemDetail handleSessionInProgress(SessionInProgressException ex) {
        ProblemDetail body = GlobalExceptionHandler.problem(HttpStatus.CONFLICT, ex.getMessage());
        body.setProperty("sessionId", ex.getSessionId());
        return body;
    }
}
