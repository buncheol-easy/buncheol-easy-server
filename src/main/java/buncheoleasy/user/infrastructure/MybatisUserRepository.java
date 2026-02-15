package buncheoleasy.user.infrastructure;

import buncheoleasy.user.domain.SocialInfo;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisUserRepository implements UserRepository {

    private final UserMapper userMapper;

    @Override
    public User save(User user) {
        userMapper.insert(user);
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        return userMapper.findById(id);
    }

    @Override
    public Optional<User> findBySocialInfo(SocialInfo socialInfo) {
        return userMapper.findBySocialInfo(socialInfo.provider().getValue(), socialInfo.providerId());
    }

    @Override
    public boolean isValidUserId(Long id) {
        return userMapper.findById(id).isPresent();
    }

    @Override
    public void withdraw(User user) {
        userMapper.updateDeletedAt(user.getId(), user.getDeletedAt());
    }
}
