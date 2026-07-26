package org.simonhareter.springinit.dtos;

public record MetaDataCache(
        long timestamp,
        MetaData data
        ) {}
