package org.simonhareter.springinit.dtos;

public record MetaData(
        MetaDataField type,
        MetaDataField language,
        MetaDataField bootVersion,
        MetaDataField groupId,
        MetaDataField artifactId,
        MetaDataField packageName,
        MetaDataField packaging,
        MetaDataField configurationFileFormat,
        MetaDataField javaVersion) {
}
