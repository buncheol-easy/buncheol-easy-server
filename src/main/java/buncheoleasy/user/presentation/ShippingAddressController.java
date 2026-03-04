package buncheoleasy.user.presentation;

import buncheoleasy.user.application.ShippingAddressService;
import buncheoleasy.user.dto.request.ShippingAddressRequest;
import buncheoleasy.user.dto.response.ShippingAddressResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users/me/shipping-addresses")
@RequiredArgsConstructor
public class ShippingAddressController {

  private final ShippingAddressService shippingAddressService;

  @PostMapping
  public ResponseEntity<Void> registerShippingAddress(
      @AuthenticationPrincipal final Long userId,
      @Valid @RequestBody final ShippingAddressRequest request) {
    shippingAddressService.registerShippingAddress(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @GetMapping
  public ResponseEntity<List<ShippingAddressResponse>> getUserShippingAddresses(
      @AuthenticationPrincipal final Long userId) {
    return ResponseEntity.ok(shippingAddressService.getUserShippingAddresses(userId));
  }

  @PutMapping("/{id}")
  public ResponseEntity<Void> modifyShippingAddress(
      @AuthenticationPrincipal final Long userId,
      @PathVariable final Long id,
      @Valid @RequestBody final ShippingAddressRequest request) {
    shippingAddressService.modifyShippingAddress(userId, id, request);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> removeShippingAddress(
      @AuthenticationPrincipal final Long userId, @PathVariable final Long id) {
    shippingAddressService.removeShippingAddress(userId, id);
    return ResponseEntity.noContent().build();
  }
}
