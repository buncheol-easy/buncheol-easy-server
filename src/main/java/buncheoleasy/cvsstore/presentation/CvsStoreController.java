package buncheoleasy.cvsstore.presentation;

import buncheoleasy.cvsstore.application.CvsStoreService;
import buncheoleasy.cvsstore.dto.response.CvsStoreResponse;
import buncheoleasy.global.page.CursorResponse;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/cvs-stores")
@RequiredArgsConstructor
@Validated
public class CvsStoreController {

  private final CvsStoreService cvsStoreService;

  @GetMapping
  public ResponseEntity<CursorResponse<CvsStoreResponse>> searchCvsStores(
      @RequestParam(required = false) final String brand,
      @RequestParam(required = false) @Size(max = 100) final String keyword,
      @RequestParam(required = false) final String cursor,
      @RequestParam(defaultValue = "20") final int size) {
    return ResponseEntity.ok(cvsStoreService.searchStores(brand, keyword, cursor, size));
  }
}
