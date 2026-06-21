package buncheoleasy.buncheol.application.image;

public interface BuncheolImageUploader {

  String uploadBuncheolImageAndGetUrl(Long buncheolId, ImageFile imageFile);
}
