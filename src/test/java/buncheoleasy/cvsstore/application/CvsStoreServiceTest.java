package buncheoleasy.cvsstore.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import buncheoleasy.cvsstore.domain.CvsBrand;
import buncheoleasy.cvsstore.domain.CvsStore;
import buncheoleasy.cvsstore.domain.CvsStoreCursor;
import buncheoleasy.cvsstore.domain.CvsStoreRepository;
import buncheoleasy.cvsstore.domain.RankedCvsStore;
import buncheoleasy.cvsstore.dto.response.CvsStoreResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.page.CursorResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("CvsStoreService 테스트")
class CvsStoreServiceTest {

  @Mock private CvsStoreRepository cvsStoreRepository;

  @InjectMocks private CvsStoreService cvsStoreService;

  private static CvsStore store(final long id, final String name) {
    CvsStore store =
        CvsStore.create(
            CvsBrand.GS25,
            "V" + id,
            name,
            "021234567",
            "서울",
            "서울 강남구 테헤란로 " + id,
            "06236",
            new BigDecimal("37.5010000"),
            new BigDecimal("127.0390000"),
            true,
            true);
    ReflectionTestUtils.setField(store, "id", id);
    return store;
  }

  private static RankedCvsStore ranked(final int groupRank, final long id, final String name) {
    return new RankedCvsStore(groupRank, store(id, name));
  }

  @Nested
  @DisplayName("페이지네이션 테스트")
  class PaginationTest {

    @Test
    void size보다_많이_조회되면_hasNext와_nextCursor를_채운다() {
      List<RankedCvsStore> fetched =
          IntStream.rangeClosed(1, 3)
              .mapToObj(i -> ranked(CvsStoreCursor.RANK_NAME, i, "GS25강남" + i + "점"))
              .toList();
      given(cvsStoreRepository.searchPickupStores(isNull(), isNull(), any(), eq(3)))
          .willReturn(fetched);

      CursorResponse<CvsStoreResponse> response =
          cvsStoreService.searchStores(null, null, null, 2);

      assertThat(response.items()).hasSize(2);
      assertThat(response.hasNext()).isTrue();
      // 키워드 없음 → 전체가 지점명 그룹(rank 0)
      assertThat(response.nextCursor()).isEqualTo("0_2");
    }

    @Test
    void 마지막_항목의_그룹_태그로_다음_커서를_만든다() {
      List<RankedCvsStore> fetched =
          List.of(
              ranked(CvsStoreCursor.RANK_NAME, 1L, "GS25강남점"),
              ranked(CvsStoreCursor.RANK_ADDRESS, 2L, "GS25테헤란점"),
              ranked(CvsStoreCursor.RANK_ADDRESS, 3L, "GS25역삼점"));
      given(cvsStoreRepository.searchPickupStores(isNull(), eq("강남"), any(), eq(3)))
          .willReturn(fetched);

      CursorResponse<CvsStoreResponse> response =
          cvsStoreService.searchStores(null, "강남", null, 2);

      // 마지막 노출 항목(테헤란점)은 어댑터가 주소 그룹으로 태깅 → rank 1
      assertThat(response.nextCursor()).isEqualTo("1_2");
    }

    @Test
    void 결과가_size_이하면_nextCursor가_없다() {
      given(cvsStoreRepository.searchPickupStores(isNull(), isNull(), any(), anyInt()))
          .willReturn(List.of(ranked(CvsStoreCursor.RANK_NAME, 1L, "GS25강남점")));

      CursorResponse<CvsStoreResponse> response =
          cvsStoreService.searchStores(null, null, null, 20);

      assertThat(response.items()).hasSize(1);
      assertThat(response.hasNext()).isFalse();
      assertThat(response.nextCursor()).isNull();
    }

    @Test
    void 결과가_없으면_빈_응답을_돌려준다() {
      given(cvsStoreRepository.searchPickupStores(any(), any(), any(), anyInt()))
          .willReturn(List.of());

      CursorResponse<CvsStoreResponse> response =
          cvsStoreService.searchStores(null, null, null, 20);

      assertThat(response).isEqualTo(CursorResponse.empty());
    }

    @Test
    void size는_1과_50_사이로_보정된다() {
      given(cvsStoreRepository.searchPickupStores(any(), any(), any(), anyInt()))
          .willReturn(List.of());

      cvsStoreService.searchStores(null, null, null, 0);
      cvsStoreService.searchStores(null, null, null, 500);

      then(cvsStoreRepository).should().searchPickupStores(isNull(), isNull(), any(), eq(2));
      then(cvsStoreRepository).should().searchPickupStores(isNull(), isNull(), any(), eq(51));
    }
  }

  @Nested
  @DisplayName("파라미터 해석 테스트")
  class ParameterTest {

    @Test
    void 키워드는_트림하고_LIKE_와일드카드를_이스케이프해_전달한다() {
      given(cvsStoreRepository.searchPickupStores(any(), any(), any(), anyInt()))
          .willReturn(List.of());

      cvsStoreService.searchStores(null, "  100%강남  ", null, 20);

      then(cvsStoreRepository)
          .should()
          .searchPickupStores(isNull(), eq("100\\%강남"), any(), anyInt());
    }

    @Test
    void 브랜드는_대소문자_무관하게_해석된다() {
      given(cvsStoreRepository.searchPickupStores(any(), any(), any(), anyInt()))
          .willReturn(List.of());

      cvsStoreService.searchStores("gs25", null, null, 20);

      then(cvsStoreRepository)
          .should()
          .searchPickupStores(eq(CvsBrand.GS25), isNull(), any(), anyInt());
    }

    @Test
    void 커서는_그룹과_id로_파싱해_전달한다() {
      given(cvsStoreRepository.searchPickupStores(any(), any(), any(), anyInt()))
          .willReturn(List.of());

      cvsStoreService.searchStores(null, null, "1_42", 20);

      then(cvsStoreRepository)
          .should()
          .searchPickupStores(
              isNull(), isNull(), eq(new CvsStoreCursor(CvsStoreCursor.RANK_ADDRESS, 42L)),
              anyInt());
    }

    @Test
    void 지원하지_않는_브랜드면_예외가_발생한다() {
      assertThatThrownBy(() -> cvsStoreService.searchStores("SEVEN", null, null, 20))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CVS_BRAND_INVALID);
    }

    @Test
    void 형식이_잘못된_커서면_예외가_발생한다() {
      assertThatThrownBy(() -> cvsStoreService.searchStores(null, null, "abc", 20))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CURSOR_INVALID);
      assertThatThrownBy(() -> cvsStoreService.searchStores(null, null, "9_1_2", 20))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CURSOR_INVALID);
      assertThatThrownBy(() -> cvsStoreService.searchStores(null, null, "7_42", 20))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CURSOR_INVALID);
    }
  }
}
