package buncheoleasy.buncheol.application.image;

import java.util.List;

public record BuncheolImageUploadEvent(Long buncheolId, List<ImageFile> images) {}
