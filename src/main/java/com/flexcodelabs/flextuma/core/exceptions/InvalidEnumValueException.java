package com.flexcodelabs.flextuma.core.exceptions;

import java.util.Arrays;

public class InvalidEnumValueException extends RuntimeException {
    private final String fieldName;
    private final String[] enumValues;

    public InvalidEnumValueException(String fieldName, Class<? extends Enum<?>> enumClass) {
        super(String.format("Invalid value for field '%s'", fieldName));
        this.fieldName = fieldName;
        this.enumValues = Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .toArray(String[]::new);
    }

    public String getFieldName() {
        return fieldName;
    }

    public String[] getEnumValues() {
        return enumValues;
    }
}