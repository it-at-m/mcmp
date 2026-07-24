package de.muenchen.mcmp.user;

import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.HasSpecialRole;
import de.muenchen.mcmp.security.IsAdmin;
import de.muenchen.mcmp.security.IsAuthenticated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @IsAuthenticated
    @GetMapping
    public Map<String, Object> getCurrentUser(Authentication authentication) {
        final Map<String, Object> userInfo = new HashMap<>();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            final Jwt jwt = jwtToken.getToken();

            // Basis User-Info aus JWT Claims
            userInfo.putAll(jwt.getClaims());

            final List<String> authorityStrings = jwtToken.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .toList();
            userInfo.put("authorities", authorityStrings);

            // Mapping der JWT-Claims auf die erwarteten Feldnamen
            if (jwt.getClaimAsString("given_name") != null) {
                userInfo.put("givenname", jwt.getClaimAsString("given_name"));
            }
            if (jwt.getClaimAsString("family_name") != null) {
                userInfo.put("surname", jwt.getClaimAsString("family_name"));
            }
            if (jwt.getClaimAsString("name") != null) {
                userInfo.put("displayName", jwt.getClaimAsString("name"));
            }
            if (jwt.getClaimAsString("email") != null) {
                userInfo.put("email", jwt.getClaimAsString("email"));
            }
            if (jwt.getClaimAsString("preferred_username") != null) {
                userInfo.put("username", jwt.getClaimAsString("preferred_username"));
            }
        }
        log.info("Userinfo {} has logged in.", userInfo);
        return userInfo;
    }

    @IsAdmin
    @GetMapping("/admin")
    public List<UserDTO> getAdminUser(){
        return userService.getAdminUsers();
    }

    @IsAdmin
    @GetMapping("/notAdmin")
    public List<UserDTO> getNotAdminUser(){
       return userService.getNotAdminUsers();
   }

    @IsAdmin
    @PutMapping("/admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateAdminPermission(@RequestBody final UserDTO userDTO){
        log.info("User {} set admin permission to {} for user {}",
                AuthUtils.getUsername(), userDTO.admin(), userDTO.username());
        userService.updateAdminPermission(userDTO);
    }

    @IsAuthenticated
    @PostMapping("/darkmode")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDarkMode(@RequestParam boolean darkMode) {
        final String username = AuthUtils.getUsername();
        log.info("User {} sets dark mode to {}", username, darkMode);
        userService.updateDarkMode(username, darkMode);
    }

    @IsAuthenticated
    @GetMapping("/darkmode")
    public boolean getDarkMode() {
        final String username = AuthUtils.getUsername();
        return userService.getDarkMode(username);
    }

    @IsAuthenticated
    @PutMapping("/loginpage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setLoginPage(@RequestParam String loginPage) {
        final String username = AuthUtils.getUsername();
        log.info("User {} sets login page to {}", username, loginPage);
        userService.updateLoginPage(username, loginPage);
    }

    @IsAuthenticated
    @GetMapping("/loginpage")
    public String getLoginPage() {
        final String username = AuthUtils.getUsername();
        return userService.getLoginPage(username);
    }


    @HasSpecialRole
    @GetMapping("/autocomplete")
    public List<UserAutocompleteDTO> getUsersForAutocomplete(@RequestParam(required = false) String query) {
        return userService.searchUsersForAutocomplete(query);
    }
}

