package buncheoleasy.user.presentation;

import buncheoleasy.user.application.ShippingAddressService;
import buncheoleasy.user.dto.request.CreateShippingAddressRequest;
import buncheoleasy.user.dto.request.UpdateShippingAddressRequest;
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
@RequiredArgsConstructor
@RequestMapping("/v1/users/me/shipping-addresses")
public class ShippingAddressController {

    private final ShippingAddressService shippingAddressService;

    @PostMapping
    public ResponseEntity<Void> createShippingAddress(@AuthenticationPrincipal final Long userId,
                                                      @Valid @RequestBody final CreateShippingAddressRequest request) {
        shippingAddressService.createShippingAddress(userId, request.shippingMethod(), request.storeName());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateShippingAddress(@AuthenticationPrincipal final Long userId,
                                                      @PathVariable final Long id,
                                                      @Valid @RequestBody final UpdateShippingAddressRequest request) {
        shippingAddressService.updateShippingAddress(userId, id, request.shippingMethod(), request.storeName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShippingAddress(@AuthenticationPrincipal final Long userId,
                                                      @PathVariable final Long id) {
        shippingAddressService.deleteShippingAddress(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ShippingAddressResponse>> getShippingAddresses(
            @AuthenticationPrincipal final Long userId) {
        return ResponseEntity.ok(shippingAddressService.getUserShippingAddresses(userId));
    }
}
