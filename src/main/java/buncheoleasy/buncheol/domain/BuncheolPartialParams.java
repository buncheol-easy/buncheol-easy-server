package buncheoleasy.buncheol.domain;

import java.time.LocalDateTime;

public record BuncheolPartialParams(
    String title,
    String description,
    LocalDateTime deadline,
    String settlementBank,
    String settlementAccount,
    String settlementHolder,
    Integer gs25ShippingFee,
    Integer cuShippingFee) {}
