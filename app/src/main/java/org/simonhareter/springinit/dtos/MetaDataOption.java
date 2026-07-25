package org.simonhareter.springinit.dtos;

public record MetaDataOption(
        String id,
        String name,
        String description,
        String action,
        Tags tags) {
}
