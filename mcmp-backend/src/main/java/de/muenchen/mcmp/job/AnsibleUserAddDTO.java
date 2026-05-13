package de.muenchen.mcmp.job;

import de.muenchen.mcmp.security.AuthUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@Slf4j
public class AnsibleUserAddDTO {

    @NotBlank(message = "Account name ist erforderlich")
    @Pattern(regexp = "^svc-ans-[a-z0-9-]{1,12}$", message = "Der Name muss mit svc-ans- beginnen und darf max. 20 Zeichen umfassen")
    private String accountName;

    @NotEmpty(message = "Mindestens ein Server muss ausgewählt werden")
    @Size(min = 1, message = "Mindestens ein Server muss ausgewählt werden")
    private List<Long> serverIds;

    public static AnsibleUserAddDTO fromMap(Map<String, Object> awxExtraVars, String jobType) {
        AnsibleUserAddDTO dto = new AnsibleUserAddDTO();

        Object account_nameObj = awxExtraVars.get("account_name");
        Object serversObj = awxExtraVars.get("server_ids");

        if (account_nameObj == null || serversObj == null) {
            log.info("Account name or server IDs not provided by user: {} for job {}", AuthUtils.getUsername(), jobType);
            throw new MissingFormatArgumentException("Account name and server IDs must be provided.");
        }

        String account_name = account_nameObj.toString();
        if (account_name.isBlank() || !account_name.matches("^svc-ans-[a-z0-9-]{1,12}$")) {
            log.info("Invalid account name provided by user: {} for job {}", AuthUtils.getUsername(), jobType);
            throw new IllegalArgumentException("Account name is invalid.");
        }
        dto.setAccountName(account_name);

        final List<Long> serverIds;
        try {
            if (serversObj instanceof List<?>) {
                serverIds = ((List<?>) serversObj).stream()
                        .map(id -> {
                            if (id instanceof Number) {
                                return ((Number) id).longValue();
                            }
                            return Long.parseLong(id.toString());
                        })
                        .collect(Collectors.toList());
            } else {
                throw new ClassCastException("Server IDs is not a List");
            }
        } catch (ClassCastException | NumberFormatException e) {
            log.info("Server IDs not provided by user: {} for job {} in the right format.", AuthUtils.getUsername(), jobType);
            throw new IllegalArgumentException("Server IDs are not provided in the right format.");
        }
        dto.setServerIds(serverIds);
        return dto;
    }
}