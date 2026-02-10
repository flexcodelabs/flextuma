package com.flexcodelabs.flextuma.core.helpers;

import java.util.Map;

public class TemplateUtils {
    public static String fillTemplate(String template, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }
}