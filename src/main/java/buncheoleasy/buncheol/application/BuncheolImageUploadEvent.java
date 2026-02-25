package buncheoleasy.buncheol.application;

import java.util.List;

public record BuncheolImageUploadEvent(
        Long buncheolId,
        List<ImageFile> images
) {}
