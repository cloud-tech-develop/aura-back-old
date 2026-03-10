package com.cloud_technological.aura_pos.utils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;
import java.util.Date;
import springfox.documentation.spring.web.json.Json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
public class MapperRepository {

    private final ObjectMapper objectMapper;

    private final ModelMapper modelMapper = new ModelMapper();

    public MapperRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Método que convierte un Map<String, Object> en un objeto del tipo
     * especificado
     *
     * @param <T>
     * @param map      Map<String, Object>
     * @param dtoClass Class<T>
     * @return T
     */
    public <T> T mapToDto(Map<String, Object> map, Class<T> dtoClass) {
        T dto;
        try {
            // Crear una nueva instancia del objeto DTO
            dto = dtoClass.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String fieldName = entry.getKey();
                Object fieldValue = entry.getValue();

                // Si el valor del campo es nulo, se establece como una cadena vacía
                fieldValue = fieldValue == null ? "" : fieldValue;
                try {
                    Field field = dtoClass.getDeclaredField(fieldName);
                    field.setAccessible(true);

                    // Manejo de campos de tipo array
                    if (field.getType().isArray() && fieldValue instanceof Array) {
                        handleArrayField(field, dto, (Array) fieldValue);
                    } // Manejo de campos de tipo Integer
                    else if (field.getType().equals(Integer.class)) {
                        handleIntegerField(field, dto, fieldValue);
                    } // Manejo de campos de tipo LocalDateTime
                    else if (field.getType().equals(LocalDateTime.class)) {
                        handleLocalDateTimeField(field, dto, fieldValue);
                    } // Asignación directa si el tipo del campo es compatible
                    else if (field.getType().isAssignableFrom(fieldValue.getClass())) {
                        field.set(dto, fieldValue);
                    }
                } catch (NoSuchFieldException e) {
                    // Ignorar si el campo no existe en la clase DTO
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return dto;
    }
    public static <T> T mapResultSetToObject(Map<String, Object> resultMap, Class<T> clazz) {
        T instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
            ObjectMapper objectMapper = new ObjectMapper();

            for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
                String columnName = entry.getKey();
                Object value = entry.getValue();

                try {
                    Field field = clazz.getDeclaredField(columnName);
                    field.setAccessible(true);

                    if (field.getType().equals(LocalDateTime.class)) {
                        field.set(instance, convertToLocalDateTime(value));
                    } else if (field.getType().equals(LocalDate.class)) {
                        field.set(instance, convertToLocalDate(value));
                    } else if (field.getType().equals(LocalTime.class)) {
                        field.set(instance, convertToLocalTime(value));
                    } else if (field.getType().equals(Integer.class)) {
                        if(value != null){
                            field.set(instance, ((Number) value).intValue());
                        }else{
                            field.set(instance, 0);
                        }
                    } else if (field.getType().equals(Long.class)) {
                        if(value != null){
                            field.set(instance, ((Number) value).longValue());
                        }else{
                            field.set(instance, 0L);
                        }
                    } else if (field.getType().equals(Short.class)) {
                        if(value != null){
                            field.set(instance, ((Number) value).shortValue());
                        }else{
                            field.set(instance, 0);
                        }
                    } else if (field.getType().equals(String.class)) {
                        if(value != null){
                            field.set(instance, value);
                        }else{
                            field.set(instance, "");
                        }

                    } else {
                        // Handle other types as needed
                    }
                } catch (NoSuchFieldException e) {
                    // Field not found in the class, ignore or handle accordingly
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to map result set to object", e);
        }

        return instance;
    }

        public static <T> T mapResultSetToObjectNull(Map<String, Object> resultMap, Class<T> clazz) {
        T instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
            ObjectMapper objectMapper = new ObjectMapper();

            for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
                String columnName = entry.getKey();
                Object value = entry.getValue();
                value = value == null ? "" : value;
                try {
                    Field field = clazz.getDeclaredField(columnName);
                    field.setAccessible(true);
                    if (value instanceof String && ((String) value).isEmpty()) {
                        // Si el valor es una cadena vacía, establecer null en lugar de intentar asignar a campos numéricos
                        if (field.getType().equals(LocalDateTime.class) || field.getType().equals(LocalDate.class) ||
                                field.getType().equals(Date.class) || field.getType().equals(LocalTime.class) || field.getType().equals(Time.class)) {
                            field.set(instance, null);
                        }else if (field.getType().equals(Integer.class) || field.getType().equals(Long.class)
                                || field.getType().equals(Short.class)  || field.getType().equals(BigDecimal.class)) {
                            field.set(instance, null);
                        }else if (field.getType().equals(String.class) ) {
                            field.set(instance, null);
                        }else if (field.getType().equals(Json.class) ) {
                            field.set(instance, null);
                        } else {
                            field.set(instance, "");
                        }
                    } else {

                        // Convertir el valor al tipo apropiado antes de asignarlo al campo
                        if (field.getType().equals(Integer.class)) {
                            field.set(instance, ((Number) value).intValue());
                        } else if (field.getType().equals(Long.class)) {
                            field.set(instance, ((Number) value).longValue());
                        } else if (field.getType().equals(Short.class)) {
                            field.set(instance, ((Number) value).shortValue());
                        } else if (field.getType().equals(BigDecimal.class)) {

                            if (value instanceof String) {
                                field.set(instance, new BigDecimal((String) value));
                            } else if (value instanceof Number) {
                                field.set(instance, BigDecimal.valueOf(((Number) value).doubleValue()));
                            }
                        } else if (field.getType().equals(String.class)) {
                            field.set(instance, value.toString());
                        } else if (field.getType().equals(LocalDateTime.class)) {
                            field.set(instance, convertToLocalDateTime(value));
                        } else if (field.getType().equals(LocalDate.class)) {
                            field.set(instance, convertToLocalDate(value));
                        } else if (field.getType().equals(LocalTime.class)) {
                            field.set(instance, convertToLocalTime(value));
                        } else if (field.getType().equals(Json.class)) {
                            field.set(instance, value.toString() );
                        } else {
                            field.set(instance, null );
                        }
                    }
                } catch (NoSuchFieldException e) {
                    // Field not found in the class, ignore or handle accordingly
                }
            }
        } catch (Exception e) {e.printStackTrace();
            throw new RuntimeException("Failed to map result set to object", e);
        }

        return instance;
    }
    private static LocalTime convertToLocalTime(Object value) {
        if (value instanceof Time) {
            return ((Time) value).toLocalTime();
        } else if (value instanceof LocalTime) {
            return (LocalTime) value;
        }
        return null; // O manejar el caso cuando no es convertible a LocalTime
    }

    private static LocalDate convertToLocalDate(Object value) {
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        } else if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        return null; // O manejar el caso cuando no es convertible a LocalDate
    }
    private static LocalDateTime convertToLocalDateTime(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        return null;
    }

    /**
     * Método que convierte una lista de Map<String, Object> en una lista de
     * objetos del tipo especificado
     *
     * @param <T>
     * @param mapList  List<Map<String, Object>>
     * @param dtoClass Class<T>
     * @return List<T>
     */
    public static <T> List<T> mapListToDtoList(List<Map<String, Object>> mapList, Class<T> dtoClass) {
        List<T> dtoList = new ArrayList<>();
        for (Map<String, Object> map : mapList) {
            dtoList.add(mapResultSetToObject(map, dtoClass));
        }
        return dtoList;
    }
    public static <T> List<T> mapListToDtoListNull(List<Map<String, Object>> mapList, Class<T> dtoClass) {
        List<T> dtoList = new ArrayList<>();
        for (Map<String, Object> map : mapList) {
            dtoList.add(mapResultSetToObjectNull(map, dtoClass));
        }
        return dtoList;
    }

    /**
     * Método auxiliar para manejar campos de tipo array
     *
     * @param <T>
     * @param field
     * @param dto
     * @param fieldValue
     * @throws Exception
     */
    private <T> void handleArrayField(Field field, T dto, Array fieldValue) throws Exception {
        Object[] objectArray = (Object[]) fieldValue.getArray();
        Object[] combinedArray = new Object[objectArray.length];

        for (int i = 0; i < objectArray.length; i++) {
            combinedArray[i] = objectArray[i];
        }

        field.set(dto, combinedArray);
    }

    /**
     * Método auxiliar para manejar campos de tipo Integer
     *
     * @param <T>
     * @param field
     * @param dto
     * @param fieldValue
     * @throws IllegalAccessException
     */
    private <T> void handleIntegerField(Field field, T dto, Object fieldValue) throws IllegalAccessException {
        if (fieldValue instanceof Long) {
            field.set(dto, ((Long) fieldValue).intValue());
        } else if (fieldValue instanceof Integer) {
            field.set(dto, fieldValue);
        } else {
            field.set(dto, 0);
        }
    }

    /**
     * Método auxiliar para manejar campos de tipo LocalDateTime
     *
     * @param <T>
     * @param field
     * @param dto
     * @param fieldValue
     * @throws IllegalAccessException
     */
    private <T> void handleLocalDateTimeField(Field field, T dto, Object fieldValue) throws IllegalAccessException {
        LocalDateTime localDateTime = null;

        if (fieldValue instanceof Timestamp) {
            localDateTime = ((Timestamp) fieldValue).toLocalDateTime();
        } else if (fieldValue instanceof String) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
                localDateTime = LocalDateTime.parse((String) fieldValue, formatter);
            } catch (Exception e) {
                field.set(dto, null);
            }
        }
        field.set(dto, localDateTime);
    }

    /**
     * Convierte un objecto a un elemento mapeable
     *
     * @param obj Object
     * @return Map<String, Object>
     */
    public Map<String, Object> convertObjectToMap(Object obj) {
        return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * Convierte un json a un objeto mapeable
     *
     * @param <T>        Tipo generico
     * @param json       String
     * @param targetType Class<T>
     * @return <T>
     */
    public <T> T convertJsonToObject(String json, Class<T> targetType) {
        try {
            return objectMapper.readValue(json, targetType);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convierte un json a una lista mapeable
     *
     * @param <T>
     * @param json       String
     * @param targetType Class<T>
     * @return List<T>
     */
    public <T> List<T> convertJsonToList(String json, Class<T> targetType) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<T>>() {
            });
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convierte un objeto a un json
     *
     * @param obj Object
     * @return String
     */
    public String convertObjectToJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public <D, T> D mapToDtoModel(T entity, Class<D> outClass) {
        return modelMapper.map(entity, outClass);
    }

    public <D, T> List<D> mapListToDtoModel(List<T> entityList, Class<D> outClass) {
        return entityList.stream()
                .map(entity -> modelMapper.map(entity, outClass))
                .collect(Collectors.toList());
    }
}