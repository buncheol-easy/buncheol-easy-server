package buncheoleasy.buncheol.infrastructure.image;

import buncheoleasy.buncheol.domain.image.BuncheolImage;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BuncheolImageMapper {

  void insertAll(@Param("buncheolImages") List<BuncheolImage> buncheolImages);

  void deleteByBuncheolIdExcludingIds(
      @Param("buncheolId") Long buncheolId, @Param("keepImageIds") List<Long> keepImageIds);
}
