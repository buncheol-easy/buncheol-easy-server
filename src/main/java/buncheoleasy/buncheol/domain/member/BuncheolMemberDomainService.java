package buncheoleasy.buncheol.domain.member;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuncheolMemberDomainService {

    private final BuncheolMemberRepository buncheolMemberRepository;

    public void createBuncheolMembers(final Long buncheolId, final List<BuncheolMemberParams> params) {
        List<BuncheolMember> newBuncheolMembers = params.stream().map(param ->
                    BuncheolMember.create(
                            buncheolId,
                            param.memberId(),
                            param.memberName(),
                            param.instantPrice(),
                            param.bidAllowed(),
                            param.bidMinPrice()
                    ))
                .toList();

        buncheolMemberRepository.saveAll(newBuncheolMembers);
    }
}
