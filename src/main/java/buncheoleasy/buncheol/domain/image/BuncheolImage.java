package buncheoleasy.buncheol.domain.image;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class BuncheolImage {

    private Long id;
    private final Long buncheolId;
    private final String imageUrl;
    private LocalDateTime createdAt;

    public static BuncheolImage create(final Long buncheolId, final String imageUrl) {
        return new BuncheolImage(buncheolId, imageUrl);
    }

    private BuncheolImage(final Long buncheolId, final String imageUrl) {
        this.buncheolId = buncheolId;
        this.imageUrl = imageUrl;
    }
}
