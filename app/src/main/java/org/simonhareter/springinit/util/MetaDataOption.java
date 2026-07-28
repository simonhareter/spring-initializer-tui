package org.simonhareter.springinit.util;

public record MetaDataOption(
        String id,
        String name,
        String description,
        String action,
        Tags tags) {
}
