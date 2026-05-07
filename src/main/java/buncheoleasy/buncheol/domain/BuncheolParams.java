package buncheoleasy.buncheol.domain;

import java.time.LocalDateTime;

public record BuncheolParams(
    Long groupId,
    String title,
    String description,
    String purchaseSite,
    LocalDateTime deadline,
    Integer gs25ShippingFee,
    Integer cuShippingFee) {}
