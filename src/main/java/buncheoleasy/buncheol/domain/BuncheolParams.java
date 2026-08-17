package buncheoleasy.buncheol.domain;

import java.time.Instant;

public record BuncheolParams(
    Long groupId,
    String title,
    String description,
    String purchaseSite,
    Instant deadline,
    int minHeadcount,
    Integer gs25ShippingFee,
    Integer cuShippingFee,
    FlowType flowType,
    String openChatUrl) {}
