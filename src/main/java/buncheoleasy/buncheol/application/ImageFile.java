package buncheoleasy.buncheol.application;

public record ImageFile(
        String originalFilename,
        String contentType,
        byte[] bytes
) {}
