package buncheoleasy.buncheol.domain.member;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class BuncheolMember {

    private Long id;
    private final Long buncheolId;
    private final Long memberId;
    private final String memberName;
    private Long instantPrice;
    private boolean bidAllowed;
    private Long bidMinPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BuncheolMember create(final Long buncheolId, final Long memberId, final String memberName,
                                        final Long instantPrice, final boolean bidAllowed, final Long bidMinPrice) {
        return new BuncheolMember(buncheolId, memberId, memberName, instantPrice, bidAllowed, bidMinPrice);
    }

    private BuncheolMember(final Long buncheolId, final Long memberId, final String memberName, final Long instantPrice,
                           final boolean bidAllowed, final Long bidMinPrice) {
        this.buncheolId = buncheolId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.instantPrice = instantPrice;
        this.bidAllowed = bidAllowed;
        this.bidMinPrice = bidMinPrice;
    }
}
