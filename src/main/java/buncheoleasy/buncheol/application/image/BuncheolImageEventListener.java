package buncheoleasy.buncheol.application.image;

import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BuncheolImageEventListener {

  private final BuncheolImageUploader imageUploader;
  private final BuncheolImageDomainService buncheolImageDomainService;

  @TransactionalEventListener
  @Async
  public void handleImageUpload(final BuncheolImageUploadEvent event) {
    List<CompletableFuture<String>> futures =
        event.images().stream()
            .map(
                imageFile ->
                    CompletableFuture.supplyAsync(
                        () ->
                            imageUploader.uploadBuncheolImageAndGetUrl(
                                event.buncheolId(), imageFile)))
            .toList();

    // 병렬 업로드지만 결과는 요청 순서대로 수집한다. 실패분은 null 로 남겨 인덱스 정렬을 유지한다(대표사진 인덱스 보정에 필요).
    List<String> results =
        futures.stream()
            .map(
                future -> {
                  try {
                    return future.join();
                  } catch (CompletionException e) {
                    return null;
                  }
                })
            .toList();

    List<String> urls = results.stream().filter(Objects::nonNull).toList();

    if (!urls.isEmpty()) {
      log.debug("이미지 {}장 저장 성공", urls.size());
      buncheolImageDomainService.createBuncheolImages(
          event.buncheolId(), urls, resolveThumbnailPosition(results, event.thumbnailIndex()));
    }
  }

  /**
   * 요청 시 지정한 대표사진 인덱스를 업로드 성공분 리스트 기준 인덱스로 보정한다. 대표사진 업로드가 실패하면 null 을 반환해 플래그를 남기지 않는다(조회 쿼리가
   * MIN(id) 로 폴백).
   */
  private Integer resolveThumbnailPosition(
      final List<String> results, final Integer thumbnailIndex) {
    if (thumbnailIndex == null
        || thumbnailIndex >= results.size()
        || results.get(thumbnailIndex) == null) {
      return null;
    }
    return (int)
        results.subList(0, thumbnailIndex).stream().filter(Objects::nonNull).count();
  }
}
