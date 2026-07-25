package com.jungwoon.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

    @Query("select sa from SocialAccount sa join fetch sa.user where sa.provider = :provider and sa.providerId = :providerId")
    Optional<SocialAccount> findByProviderAndProviderId(Provider provider, String providerId);
}
