package  com.akshaya.elmsbackend.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.akshaya.elmsbackend.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}