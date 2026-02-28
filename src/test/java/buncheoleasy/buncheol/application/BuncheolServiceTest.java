package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.image.BuncheolImageDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberDomainService;
import buncheoleasy.buncheol.domain.member.BuncheolMemberParams;
import buncheoleasy.buncheol.dto.request.BuncheolMemberRequest;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.GroupDomainService;
import buncheoleasy.group.domain.member.GroupMember;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolService 단위 테스트")
class BuncheolServiceTest {

    @InjectMocks
    private BuncheolService buncheolService;

    @Mock
    private BuncheolDomainService buncheolDomainService;

    @Mock
    private BuncheolImageDomainService buncheolImageDomainService;

    @Mock
    private BuncheolMemberDomainService buncheolMemberDomainService;

    @Mock
    private GroupDomainService groupDomainService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<BuncheolParams> buncheolParamsCaptor;

    @Captor
    private ArgumentCaptor<List<BuncheolMemberParams>> buncheolMemberParamsCaptor;

    private HoldBuncheolRequest customGroupRequest() {
        return new HoldBuncheolRequest(
                null,
                "테스트 그룹",
                "테스트 분철 제목",
                "분철 설명입니다.",
                "공식 앨범",
                "공식 스토어",
                50_000L,
                LocalDateTime.now().plusDays(7),
                7,
                3000,
                null,
                "국민은행",
                "123-456-789012",
                "홍길동",
                List.of(new BuncheolMemberRequest(null, "멤버A", 50_000L, false, null))
        );
    }

    private HoldBuncheolRequest officialGroupRequest(Long groupId, List<BuncheolMemberRequest> members) {
        return new HoldBuncheolRequest(
                groupId,
                null,
                "공식 그룹 분철 제목",
                null,
                "공식 앨범",
                "공식 스토어",
                50_000L,
                LocalDateTime.now().plusDays(7),
                7,
                3000,
                null,
                "신한은행",
                "123-456-789",
                "홍길동",
                members
        );
    }

    @Nested
    @DisplayName("커스텀 그룹 분철 개최 테스트")
    class HoldBuncheolWithCustomGroupTest {

        @Test
        void 커스텀_그룹으로_분철_개최에_성공한다() {
            // given
            Long hostId = 1L;
            HoldBuncheolRequest request = customGroupRequest();

            Buncheol buncheol = mock(Buncheol.class);
            given(buncheol.getId()).willReturn(10L);
            given(buncheolDomainService.createBuncheol(eq(hostId), any())).willReturn(buncheol);
            willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

            // when
            buncheolService.holdBuncheol(hostId, request, List.of());

            // then
            then(buncheolDomainService).should().createBuncheol(eq(hostId), buncheolParamsCaptor.capture());
            then(buncheolMemberDomainService)
                    .should()
                    .createBuncheolMembers(eq(10L), buncheolMemberParamsCaptor.capture());
            then(eventPublisher).should(never()).publishEvent(any());

            BuncheolParams buncheolParams = buncheolParamsCaptor.getValue();
            assertThat(buncheolParams.groupId()).isNull();
            assertThat(buncheolParams.groupName()).isEqualTo("테스트 그룹");
            assertThat(buncheolParams.title()).isEqualTo("테스트 분철 제목");

            List<BuncheolMemberParams> memberParams = buncheolMemberParamsCaptor.getValue();
            assertThat(memberParams).hasSize(1);
            assertThat(memberParams.getFirst().memberId()).isNull();
            assertThat(memberParams.getFirst().memberName()).isEqualTo("멤버A");
            assertThat(memberParams.getFirst().instantPrice()).isEqualTo(50_000L);
        }

        @Test
        void 이미지가_있는_경우_이미지_업로드_이벤트가_발행된다() {
            // given
            Long hostId = 1L;
            HoldBuncheolRequest request = customGroupRequest();
            List<ImageFile> images = List.of(
                    new ImageFile("image1.jpg", "image/jpeg", new byte[]{1, 2, 3})
            );

            Buncheol buncheol = mock(Buncheol.class);
            given(buncheol.getId()).willReturn(10L);
            given(buncheolDomainService.createBuncheol(eq(hostId), any())).willReturn(buncheol);
            willDoNothing().given(buncheolImageDomainService).validateImageCount(1);

            // when
            buncheolService.holdBuncheol(hostId, request, images);

            // then
            then(eventPublisher).should().publishEvent(any(BuncheolImageUploadEvent.class));
        }

        @Test
        void 이미지_개수가_초과되면_예외가_발생한다() {
            // given
            Long hostId = 1L;
            HoldBuncheolRequest request = customGroupRequest();
            List<ImageFile> images = List.of(
                    new ImageFile("image1.jpg", "image/jpeg", new byte[]{1}),
                    new ImageFile("image2.jpg", "image/jpeg", new byte[]{2}),
                    new ImageFile("image3.jpg", "image/jpeg", new byte[]{3}),
                    new ImageFile("image4.jpg", "image/jpeg", new byte[]{4})
            );

            willThrow(new BusinessException(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED))
                    .given(buncheolImageDomainService).validateImageCount(4);

            // when & then
            assertThatThrownBy(() -> buncheolService.holdBuncheol(hostId, request, images))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED);
        }

        @Test
        void 커스텀_그룹에서_멤버명이_없으면_예외가_발생한다() {
            // given
            Long hostId = 1L;
            HoldBuncheolRequest request = new HoldBuncheolRequest(
                    null,
                    "테스트 그룹",
                    "제목",
                    null,
                    "앨범명",
                    "스토어명",
                    50_000L,
                    LocalDateTime.now().plusDays(7),
                    7,
                    3000,
                    null,
                    "국민은행",
                    "123-456",
                    "홍길동",
                    List.of(new BuncheolMemberRequest(null, null, 50_000L, false, null))
            );

            willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

            // when & then
            assertThatThrownBy(() -> buncheolService.holdBuncheol(hostId, request, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_NAME_REQUIRED);
        }
    }

    @Nested
    @DisplayName("공식 그룹 분철 개최 테스트")
    class HoldBuncheolWithOfficialGroupTest {

        @Test
        void 공식_그룹으로_분철_개최에_성공한다() {
            // given
            Long hostId = 1L;
            Long groupId = 100L;
            Long groupMemberId = 200L;

            Group group = new Group(groupId, "공식 그룹명", LocalDateTime.now(), LocalDateTime.now());
            given(groupDomainService.getGroup(groupId)).willReturn(group);

            GroupMember groupMember = new GroupMember(groupMemberId, groupId, "멤버A", LocalDateTime.now(), LocalDateTime.now());
            given(groupDomainService.getGroupMembersByIdsInGroup(eq(groupId), anyList()))
                    .willReturn(List.of(groupMember));

            HoldBuncheolRequest request = officialGroupRequest(
                    groupId,
                    List.of(new BuncheolMemberRequest(groupMemberId, null, 50_000L, false, null))
            );

            Buncheol buncheol = mock(Buncheol.class);
            given(buncheol.getId()).willReturn(10L);
            given(buncheolDomainService.createBuncheol(eq(hostId), any())).willReturn(buncheol);
            willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

            // when
            buncheolService.holdBuncheol(hostId, request, List.of());

            // then
            then(groupDomainService).should().getGroup(groupId);
            then(buncheolDomainService).should().createBuncheol(eq(hostId), buncheolParamsCaptor.capture());
            then(buncheolMemberDomainService)
                    .should()
                    .createBuncheolMembers(eq(10L), buncheolMemberParamsCaptor.capture());

            BuncheolParams buncheolParams = buncheolParamsCaptor.getValue();
            assertThat(buncheolParams.groupId()).isEqualTo(groupId);
            assertThat(buncheolParams.groupName()).isEqualTo("공식 그룹명");

            List<BuncheolMemberParams> memberParams = buncheolMemberParamsCaptor.getValue();
            assertThat(memberParams).hasSize(1);
            assertThat(memberParams.getFirst().memberId()).isEqualTo(groupMemberId);
            assertThat(memberParams.getFirst().memberName()).isEqualTo("멤버A");
            assertThat(memberParams.getFirst().instantPrice()).isEqualTo(50_000L);
        }

        @Test
        void 공식_그룹에서_멤버_ID가_없으면_예외가_발생한다() {
            // given
            Long hostId = 1L;
            Long groupId = 100L;

            Group group = new Group(groupId, "공식 그룹명", LocalDateTime.now(), LocalDateTime.now());
            given(groupDomainService.getGroup(groupId)).willReturn(group);

            HoldBuncheolRequest request = officialGroupRequest(
                    groupId,
                    List.of(new BuncheolMemberRequest(null, "멤버A", 50_000L, false, null))
            );

            willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

            // when & then
            assertThatThrownBy(() -> buncheolService.holdBuncheol(hostId, request, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_OFFICIAL_GROUP_MEMBER_ID_REQUIRED);
        }

        @Test
        void 공식_그룹에서_멤버_ID가_중복되면_예외가_발생한다() {
            // given
            Long hostId = 1L;
            Long groupId = 100L;
            Long groupMemberId = 200L;

            Group group = new Group(groupId, "공식 그룹명", LocalDateTime.now(), LocalDateTime.now());
            given(groupDomainService.getGroup(groupId)).willReturn(group);

            HoldBuncheolRequest request = officialGroupRequest(
                    groupId,
                    List.of(
                            new BuncheolMemberRequest(groupMemberId, null, 50_000L, false, null),
                            new BuncheolMemberRequest(groupMemberId, null, 30_000L, false, null)
                    )
            );

            willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

            // when & then
            assertThatThrownBy(() -> buncheolService.holdBuncheol(hostId, request, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_DUPLICATED);
        }

        @Test
        void 존재하지_않는_그룹ID이면_예외가_발생한다() {
            // given
            Long hostId = 1L;
            Long invalidGroupId = 999L;

            given(groupDomainService.getGroup(invalidGroupId))
                    .willThrow(new BusinessException(ErrorCode.GROUP_NOT_FOUND));

            HoldBuncheolRequest request = officialGroupRequest(
                    invalidGroupId,
                    List.of(new BuncheolMemberRequest(1L, null, 50_000L, false, null))
            );

            willDoNothing().given(buncheolImageDomainService).validateImageCount(0);

            // when & then
            assertThatThrownBy(() -> buncheolService.holdBuncheol(hostId, request, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
        }
    }
}
