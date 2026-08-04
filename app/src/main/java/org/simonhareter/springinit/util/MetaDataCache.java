package org.simonhareter.springinit.util;

public record MetaDataCache(
        long timestamp,
        MetaData data,
        Dependencies dependencies) {
}
