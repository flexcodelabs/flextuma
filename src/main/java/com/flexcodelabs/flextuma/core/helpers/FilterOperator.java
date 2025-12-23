package com.flexcodelabs.flextuma.core.helpers;

import lombok.Getter;

@Getter
public enum FilterOperator {
    EQ("eq"),
    NE("ne"),
    LIKE("like"),
    GT("gt"),
    LT("lt"),
    IN("in");

    private final String value;

    FilterOperator(String value) {
        this.value = value;
    }

    public static FilterOperator fromValue(String value) {
        for (FilterOperator op : FilterOperator.values()) {
            if (op.value.equalsIgnoreCase(value))
                return op;
        }
        return EQ;
    }
}