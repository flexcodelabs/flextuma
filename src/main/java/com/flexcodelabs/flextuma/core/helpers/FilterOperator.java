package com.flexcodelabs.flextuma.core.helpers;

import lombok.Getter;

@Getter
public enum FilterOperator {
    EQ("eq"),
    NE("ne"),
    LIKE("like"),
    ILIKE("ilike"),
    GT("gt"),
    LT("lt"),
    GTE("gte"),
    LTE("lte"),
    IN("in"),
    BTN("btn"),
    STARTS_WITH("startsWith"),
    ENDS_WITH("endsWith"),
    IS_TRUE("isTrue"),
    IS_FALSE("isFalse");

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