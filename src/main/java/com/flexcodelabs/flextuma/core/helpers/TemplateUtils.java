package com.flexcodelabs.flextuma.core.helpers;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateUtils {
    private TemplateUtils() {
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

    public static String fillTemplate(String template, Map<String, String> values) {
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }

        List<String> missingVariables = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            missingVariables.add(matcher.group(1));
        }

        if (!missingVariables.isEmpty()) {
            String joinedVars = String.join(", ", missingVariables);
            String message;

            if (missingVariables.size() == 1) {
                message = "variable [" + joinedVars + "] has no value.";
            } else {
                message = "variables [" + joinedVars + "] have no values.";
            }

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        return template;
    }
}