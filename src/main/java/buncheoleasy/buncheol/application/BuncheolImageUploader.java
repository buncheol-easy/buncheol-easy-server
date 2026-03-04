package buncheoleasy.buncheol.application;

public interface BuncheolImageUploader {

  String uploadBuncheolImageAndGetUrl(Long buncheolId, ImageFile imageFile);
}
