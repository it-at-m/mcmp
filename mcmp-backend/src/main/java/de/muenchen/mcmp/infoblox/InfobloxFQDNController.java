package de.muenchen.mcmp.infoblox;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Collections;
import java.util.Map;

@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/infobloxFQDN")
public class InfobloxFQDNController {

    private final InfobloxService infobloxService;

    @GetMapping("/getFreeFqdn")
    public String getFreeServerFqdn(@RequestParam String prefix, @RequestParam String application,
                                    @RequestParam String serverType, @RequestParam Long appserviceId,
                                    @RequestParam(required = false) Integer customNumber, @RequestParam String domain) {
        if (customNumber == null) {
            customNumber = 1;
        }
        // TODO: Add cloudId here when multi-cloud is supported
        return infobloxService.calculateFqdn(prefix, application, serverType, appserviceId, customNumber, domain, null);
    }

    @GetMapping(value = "/getFreeDnsEntry", produces = "application/json")
    public Map<String, String> getFreeDnsEntry(@RequestParam final String dnsName, @RequestParam final Long appserviceId) {
        final String dnsEntry = infobloxService.calculateDnsEntry(dnsName, appserviceId);
        return Collections.singletonMap("dnsEntry", dnsEntry);
    }
}
