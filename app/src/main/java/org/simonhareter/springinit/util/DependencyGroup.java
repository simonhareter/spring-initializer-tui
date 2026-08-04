package org.simonhareter.springinit.util;

public record DependencyGroup(
        String name,
        Dependency[] values) {

}
