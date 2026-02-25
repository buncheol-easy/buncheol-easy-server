package buncheoleasy.buncheol.infrastructure.image;

import buncheoleasy.buncheol.domain.image.BuncheolImage;
import buncheoleasy.buncheol.domain.image.BuncheolImageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisBuncheolImageRepository implements BuncheolImageRepository {

    private final BuncheolImageMapper buncheolImageMapper;

    @Override
    public List<BuncheolImage> saveAll(List<BuncheolImage> buncheolImages) {
        buncheolImageMapper.insertAll(buncheolImages);
        return buncheolImages;
    }
}
