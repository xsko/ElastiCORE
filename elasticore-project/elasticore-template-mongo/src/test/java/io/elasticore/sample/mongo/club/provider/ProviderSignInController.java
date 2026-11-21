package io.elasticore.sample.mongo.club.provider;

import io.elasticore.springboot3.security.SessionCurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * For the tests only: what another provider's callback does once it has found its member - a sign-in
 * from the application's own code, with no password (ClubApi).
 */
@RestController
@RequestMapping("/api/club/provider")
public class ProviderSignInController {

    @PostMapping("/signin/{memberId}")
    public void signIn(@PathVariable("memberId") Long memberId, HttpServletRequest request) {
        SessionCurrentUser.signIn(request, memberId);
    }
}
