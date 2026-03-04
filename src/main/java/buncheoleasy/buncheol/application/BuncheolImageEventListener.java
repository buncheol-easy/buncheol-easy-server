package buncheoleasy.buncheol.application;

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
        List<CompletableFuture<String>> futures = event.images().stream()
                .map(imageFile -> CompletableFuture.supplyAsync(() ->
                        imageUploader.uploadBuncheolImageAndGetUrl(event.buncheolId(), imageFile)
                ))
                .toList();

        List<String> urls = futures.stream()
                .map(future -> {
                    try {
                        return future.join();
                    } catch (CompletionException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (!urls.isEmpty()) {
            log.debug("이미지 {}장 저장 성공", urls.size());
            buncheolImageDomainService.createBuncheolImages(event.buncheolId(), urls);
        }
    }
}
