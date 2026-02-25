package buncheoleasy.buncheol.application;

import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import java.util.ArrayList;
import java.util.List;
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
        List<String> urls = new ArrayList<>();
        for (ImageFile imageFile : event.images()) {
            String url = imageUploader.uploadBuncheolImageAndGetUrl(
                    event.buncheolId(),
                    imageFile
            );
            log.debug("이미지 저장 성공 URL:{}", url);
            urls.add(url);
        }
        buncheolImageDomainService.createBuncheolImages(
                event.buncheolId(),
                urls
        );
    }
}
