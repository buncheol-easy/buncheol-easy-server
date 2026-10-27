package buncheoleasy.push.infrastructure;

import buncheoleasy.push.domain.PushDevice;
import buncheoleasy.push.domain.PushDeviceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaPushDeviceRepositoryAdapter implements PushDeviceRepository {

  private final JpaPushDeviceRepository jpaPushDeviceRepository;

  @Override
  public void upsert(final PushDevice device) {
    jpaPushDeviceRepository.upsert(
        device.getUserId(),
        device.getPlatform().name(),
        device.getToken(),
        device.getEnvironment().name());
  }

  @Override
  public List<PushDevice> findAllByUserId(final Long userId) {
    return jpaPushDeviceRepository.findAllByUserId(userId);
  }

  @Override
  public List<Long> findIdsToTrim(final Long userId, final int keep) {
    return jpaPushDeviceRepository.findIdsToTrim(userId, keep);
  }

  @Override
  public void deleteAllByIdIn(final List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }
    jpaPushDeviceRepository.deleteAllByIdInBatch(ids);
  }

  @Override
  public int deleteOwnedByToken(final Long userId, final String token) {
    return jpaPushDeviceRepository.deleteOwnedByToken(userId, token);
  }

  @Override
  public int deleteByToken(final String token) {
    return jpaPushDeviceRepository.deleteByToken(token);
  }

  @Override
  public int deleteAllByUserId(final Long userId) {
    return jpaPushDeviceRepository.deleteAllByUserId(userId);
  }
}
