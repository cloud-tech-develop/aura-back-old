package com.cloud_technological.aura_pos.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.fasterxml.jackson.databind.JsonMappingException;

import com.cloud_technological.aura_pos.services.ErrorLogService;

@ControllerAdvice()
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Autowired(required = false)
	private ErrorLogService errorLogService;

	@ExceptionHandler(GlobalException.class)
	public ResponseEntity<?> handleGlobalException(GlobalException ex, HttpServletRequest request) {
		registrarError(ex.getStatus().value(), ex.getMessage(), null, request);
		ApiResponse<Object> response = new ApiResponse<>(ex.getStatus().value(), ex.getMessage(), true, null);
		return new ResponseEntity<>(response, ex.getStatus());
	}

	// Manejo de la excepción RuntimeException
	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<?> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
		registrarError(HttpStatus.CONFLICT.value(), ex.getMessage(), stackTrace(ex), request);
		ApiResponse<Object> response = new ApiResponse<>(HttpStatus.CONFLICT.value(), ex.getMessage(), true, null);
		return new ResponseEntity<>(response, HttpStatus.CONFLICT);
	}

	// Manejo específico para errores de validación
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String errorMessage = ex.getBindingResult().getFieldErrors().stream().findFirst()
				.map(FieldError::getDefaultMessage).orElse("Error de validación.");
		registrarError(HttpStatus.BAD_REQUEST.value(), errorMessage, null, request);
		ApiResponse<Object> response = new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), errorMessage, true, null);
		return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
	}

	// Manejo para errores en validaciones con BindException
	@ExceptionHandler(BindException.class)
	public ResponseEntity<?> handleBindException(BindException ex, HttpServletRequest request) {
		String errorMessage = ex.getBindingResult().getFieldErrors().stream().findFirst()
				.map(FieldError::getDefaultMessage).orElse("Error de validación.");
		registrarError(HttpStatus.BAD_REQUEST.value(), errorMessage, null, request);
		ApiResponse<Object> response = new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), errorMessage, true, null);
		return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
	}

	// Manejo de cualquier otra excepción no especificada
	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> handleGenericException(Exception ex, HttpServletRequest request) {
		registrarError(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage(), stackTrace(ex), request);
		ApiResponse<Object> response = new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage(),
				true, null);
		return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	// Captura de NumberFormatException
	@ExceptionHandler(NumberFormatException.class)
	public ResponseEntity<?> handleNumberFormatException(NumberFormatException ex, HttpServletRequest request) {
		logger.error("NumberFormatException: ", ex);
		registrarError(HttpStatus.BAD_REQUEST.value(), "Formato numérico incorrecto", null, request);
		ApiResponse<Object> response = new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "Formato numérico incorrecto",
				true, null);
		return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
	}

	// Captura de JsonMappingException
	@ExceptionHandler(JsonMappingException.class)
	public ResponseEntity<?> handleJsonMappingException(JsonMappingException ex, HttpServletRequest request) {
		logger.error("JsonMappingException: ", ex);
		registrarError(HttpStatus.BAD_REQUEST.value(), "Error al procesar los datos", null, request);
		ApiResponse<Object> response = new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "Error al procesar los datos",
				true, null);
		return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<?> handleBusinessException(BusinessException ex, HttpServletRequest request) {
		registrarError(ex.getStatus().value(), ex.getMessage(), null, request);
		ApiResponse<Object> response = new ApiResponse<>(ex.getStatus().value(), ex.getMessage(), true, null);
		return new ResponseEntity<>(response, ex.getStatus());
	}

	// ── Registro asíncrono de error ───────────────────────────
	private void registrarError(int status, String mensaje, String detalle, HttpServletRequest request) {
		if (errorLogService == null) return;
		try {
			var auth = SecurityContextHolder.getContext().getAuthentication();
			String username = (auth != null && auth.isAuthenticated()
					&& !"anonymousUser".equals(auth.getName()))
					? auth.getName() : null;
			String metodo   = request != null ? request.getMethod()      : "UNKNOWN";
			String endpoint = request != null ? request.getRequestURI()  : "unknown";
			String ip       = request != null ? request.getRemoteAddr()  : null;
			errorLogService.registrarAsync(metodo, endpoint, status, mensaje, detalle, username, ip);
		} catch (Exception ignored) {
			// Silencioso — nunca debe romper la respuesta al cliente
		}
	}

	private String stackTrace(Exception ex) {
		if (ex == null) return null;
		var sb = new StringBuilder();
		sb.append(ex.toString()).append("\n");
		for (var el : ex.getStackTrace()) {
			sb.append("\tat ").append(el).append("\n");
			if (sb.length() > 4000) { sb.append("..."); break; }
		}
		return sb.toString();
	}
}
