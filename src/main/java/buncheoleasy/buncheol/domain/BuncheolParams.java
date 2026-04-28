package buncheoleasy.buncheol.domain;

import java.time.LocalDateTime;

public record BuncheolParams(
    Long groupId,
    String groupName,
    String groupImage,
    String title,
    String description,
    String goodsName,
    String storeName,
    long originalPrice,
    LocalDateTime deadline,
    int shippingDeadlineDays,
    Integer gs25ShippingFee,
    Integer cuShippingFee,
    String settlementBank,
    String settlementAccount,
    String settlementHolder) {}
