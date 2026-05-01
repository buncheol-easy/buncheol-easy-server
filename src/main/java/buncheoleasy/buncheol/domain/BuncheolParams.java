package buncheoleasy.buncheol.domain;

import java.time.LocalDateTime;

public record BuncheolParams(
    Long groupId,
    String title,
    String description,
    String storeName,
    LocalDateTime deadline,
    int shippingDeadlineDays,
    Integer gs25ShippingFee,
    Integer cuShippingFee,
    String settlementBank,
    String settlementAccount,
    String settlementHolder) {}
