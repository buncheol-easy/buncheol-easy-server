package buncheoleasy.buncheol.infrastructure;

import buncheoleasy.buncheol.domain.Buncheol;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BuncheolMapper {

    void insert(Buncheol buncheol);
}
