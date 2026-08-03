package org.simonhareter.springinit.util;

public record MetaDataConfig(
        String type,
        String language,
        String bootVersion,
        Project project,
        String packaging,
        String configurationFileFormat,
        String javaVersion) {
}
