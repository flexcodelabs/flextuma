package com.flexcodelabs.flextuma;

import org.junit.jupiter.api.Test;

class FlextumaApplicationTest {

    @Test
    void restTemplate_shouldReturnInstance() {
        FlextumaApplication app = new FlextumaApplication();
        org.junit.jupiter.api.Assertions.assertNotNull(app.restTemplate());
    }

    @Test
    void restClientBuilder_shouldReturnBuilder() {
        FlextumaApplication app = new FlextumaApplication();
        org.junit.jupiter.api.Assertions.assertNotNull(app.restClientBuilder());
    }

    @Test
    void main_shouldRun() {
        FlextumaApplication app = new FlextumaApplication();
        org.junit.jupiter.api.Assertions.assertNotNull(app);
    }
}
