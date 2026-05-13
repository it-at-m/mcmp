package de.muenchen.mcmp.testenvironment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/testenv")
@RequiredArgsConstructor
public class TestEnvController {
        private final TestEnvProperties testEnvProperties;

        @GetMapping
        public boolean isTestEnvEnabled() {
            return testEnvProperties.isEnabled();
        }
}
