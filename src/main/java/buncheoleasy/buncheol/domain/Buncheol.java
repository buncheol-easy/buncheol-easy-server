package buncheoleasy.buncheol.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Buncheol {

    private Long id;
    private final Long hostId;
    private final Long groupId;
    private final String groupName;
    private String title;
    private String description;
    private final String goodsName;
    private final String storeName;
    private final int originalPrice;
    private LocalDateTime deadline;
    private final int shippingDeadlineDays;
    private Integer gs25ShippingFee;
    private Integer cuShippingFee;
    private String settlementBank;
    private String settlementAccount;
    private String settlementHolder;
    private BuncheolStatus status;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Buncheol create(final Long hostId, final BuncheolParams params) {
        return new Buncheol(hostId, params);
    }

    private Buncheol(final Long hostId, final BuncheolParams params) {
        this.hostId = hostId;
        this.groupId = params.groupId();
        this.groupName = params.groupName();
        this.title = params.title();
        this.description = params.description();
        this.goodsName = params.goodsName();
        this.storeName = params.storeName();
        this.originalPrice = params.originalPrice();
        this.deadline = params.deadline();
        this.shippingDeadlineDays = params.shippingDeadlineDays();
        this.gs25ShippingFee = params.gs25ShippingFee();
        this.cuShippingFee = params.cuShippingFee();
        this.settlementBank = params.settlementBank();
        this.settlementAccount = params.settlementAccount();
        this.settlementHolder = params.settlementHolder();
        this.status = BuncheolStatus.RECRUITING;
    }
}
