package com.example.caesarandcleopatra;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HelloWorldTest {

    @Test
    public void testHelloWorld() {
        String hello = "Hello, World!";
        assertEquals("Hello, World!", hello);
    }
}