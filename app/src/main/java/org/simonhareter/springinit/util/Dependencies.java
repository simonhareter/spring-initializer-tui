package org.simonhareter.springinit.util;

public record Dependencies(
        String type,
        DependencyGroup[] values) {
}
