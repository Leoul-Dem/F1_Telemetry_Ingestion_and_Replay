package com.f1replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class SpringBackendApplicationTests {

    @Test
    @DisplayName("Application main class exists")
    void applicationClassExists() {
        assertNotNull(SpringBackendApplication.class);
    }

    @Test
    @DisplayName("Application has main method")
    void mainMethodExists() throws NoSuchMethodException {
        assertNotNull(SpringBackendApplication.class.getMethod("main", String[].class));
    }
}
