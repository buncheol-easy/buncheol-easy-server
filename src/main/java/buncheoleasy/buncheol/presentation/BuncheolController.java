package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.ImageFile;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.BuncheolStatusRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/buncheols")
@RequiredArgsConstructor
public class BuncheolController {

  private final BuncheolService buncheolService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> holdBuncheol(
      @AuthenticationPrincipal final Long hostId,
      @Valid @RequestPart("request") final HoldBuncheolRequest request,
      @RequestPart(value = "images", required = false) final List<MultipartFile> images) {
    buncheolService.holdBuncheol(hostId, request, toImageFiles(images));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> modifyBuncheol(
      @AuthenticationPrincipal final Long hostId,
      @PathVariable final Long id,
      @Valid @RequestPart("request") final BuncheolModifyRequest request,
      @RequestPart(value = "images", required = false) final List<MultipartFile> images) {
    buncheolService.modifyBuncheol(hostId, id, request, toImageFiles(images));
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<Void> advanceBuncheolStatus(
      @AuthenticationPrincipal final Long hostId,
      @PathVariable final Long id,
      @Valid @RequestBody final BuncheolStatusRequest request) {
    buncheolService.advanceBuncheolStatus(hostId, id, request.status());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> cancelBuncheol(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long id) {
    buncheolService.cancelBuncheol(hostId, id);
    return ResponseEntity.noContent().build();
  }

  private List<ImageFile> toImageFiles(final List<MultipartFile> files) {
    if (files == null) {
      return List.of();
    }
    return files.stream().map(this::toImageFile).toList();
  }

  private ImageFile toImageFile(final MultipartFile file) {
    try {
      return new ImageFile(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.FILE_READ_FAILED);
    }
  }
}
