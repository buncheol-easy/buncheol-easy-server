package buncheoleasy.delivery.presentation;

import buncheoleasy.delivery.application.DeliveryService;
import buncheoleasy.delivery.dto.request.TrackingRegistrationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

  private final DeliveryService deliveryService;

  @PatchMapping("/{id}/tracking")
  public ResponseEntity<Void> registerTracking(
      @AuthenticationPrincipal final Long hostId,
      @PathVariable final Long id,
      @Valid @RequestBody final TrackingRegistrationRequest request) {
    deliveryService.registerTracking(hostId, id, request.trackingNumber());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/receipt")
  public ResponseEntity<Void> confirmReceipt(
      @AuthenticationPrincipal final Long participantId, @PathVariable final Long id) {
    deliveryService.confirmReceipt(participantId, id);
    return ResponseEntity.noContent().build();
  }
}
