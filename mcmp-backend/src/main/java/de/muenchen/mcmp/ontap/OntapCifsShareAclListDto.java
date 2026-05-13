package de.muenchen.mcmp.ontap;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OntapCifsShareAclListDto {
    private Long shareAclId;
    private String userOrGroup;
    private String permission;
}
