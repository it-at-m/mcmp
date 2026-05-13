package de.muenchen.mcmp.action;

import de.muenchen.mcmp.awxConfig.AwxConfig;
import de.muenchen.mcmp.awxConfig.AwxConfigRepository;
import de.muenchen.mcmp.snowConfig.SnowConfig;
import de.muenchen.mcmp.snowConfig.SnowConfigRepository;
import de.muenchen.mcmp.testenvironment.TestEnvProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ActionServiceTest {

    @Spy
    private ActionMapper actionMapper = Mappers.getMapper(ActionMapper.class);

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private AwxConfigRepository awxConfigRepository;

    @Mock
    private SnowConfigRepository snowConfigRepository;

    @Mock
    private TestEnvProperties testEnvProperties;

    @InjectMocks
    private ActionService actionService;


    @Test
    public void testUpdateAction() {
        SnowConfig snowConfig = new SnowConfig();
        snowConfig.setId(1L);
        AwxConfig awxConfig = new AwxConfig();
        awxConfig.setId(1L);

        ActionDTO actionDTO = ActionDTO.builder().snowConfig(snowConfig).awxConfig(awxConfig)
                .identifier("test-action").title("Test Action").description("This is a test action")
                .comment("Test comment").executionTitle("Execution Title").executionDescription("Execution Description")
                .successTitle("Success Title").successDescription("Success Description")
                .errorTitle("Error Title").errorDescription("Error Description")
                .enabled(true).quickdiscovery(false).serverInstallation(false).changeRequired(false)
                .changeType("none").changeTemplate("")
                .awxJobEnabled(false).awxTemplateType("template")
                .awxTemplateId(0).awxInventoryId(0).awxCredentials("").awxJobType("").awxLimit("")
                .awxJobTags("").awxSkipTags("").awxExtraVars("").awxScmBranch("").awxVerbosity(0)
                .awxTimeout(0).awxForks(0).awxJobSliceCount(0).awxExecutionEnvironment(0)
                .awxInstanceGroups("").awxLabels("").awxEstimatedRuntime(0).build();

        Action action = new Action();
        action.setSnowConfig(actionDTO.snowConfig());
        action.setAwxConfig(actionDTO.awxConfig());
        action.setIdentifier(actionDTO.identifier());
        action.setTitle(actionDTO.title());
        action.setDescription(actionDTO.description());
        action.setComment(actionDTO.comment());
        action.setErrorTitle(actionDTO.errorTitle());
        action.setErrorDescription(actionDTO.errorDescription());
        action.setExecutionTitle(actionDTO.executionTitle());
        action.setExecutionDescription(actionDTO.executionDescription());
        action.setSuccessTitle(actionDTO.successTitle());
        action.setSuccessDescription(actionDTO.successDescription());
        action.setEnabled(actionDTO.enabled());
        action.setQuickdiscovery(actionDTO.quickdiscovery());
        action.setServerInstallation(actionDTO.serverInstallation());
        action.setChangeRequired(actionDTO.changeRequired());
        action.setChangeType(actionDTO.changeType());
        action.setChangeTemplate(actionDTO.changeTemplate());
        action.setAwxJobEnabled(actionDTO.awxJobEnabled());
        action.setAwxTemplateType(AwxTemplateType.valueOf(actionDTO.awxTemplateType()));
        action.setAwxTemplateId(actionDTO.awxTemplateId());
        action.setAwxInventoryId(actionDTO.awxInventoryId());
        action.setAwxCredentials(actionDTO.awxCredentials());
        action.setAwxJobType(actionDTO.awxJobType());
        action.setAwxLimit(actionDTO.awxLimit());
        action.setAwxJobTags(actionDTO.awxJobTags());
        action.setAwxSkipTags(actionDTO.awxSkipTags());
        action.setAwxExtraVars(actionDTO.awxExtraVars());
        action.setAwxScmBranch(actionDTO.awxScmBranch());
        action.setAwxVerbosity(actionDTO.awxVerbosity());
        action.setAwxTimeout(actionDTO.awxTimeout());
        action.setAwxForks(actionDTO.awxForks());
        action.setAwxJobSliceCount(actionDTO.awxJobSliceCount());
        action.setAwxExecutionEnvironment(actionDTO.awxExecutionEnvironment());
        action.setAwxInstanceGroups(actionDTO.awxInstanceGroups());
        action.setAwxLabels(actionDTO.awxLabels());
        action.setAwxEstimatedRuntime(actionDTO.awxEstimatedRuntime());

        when(actionRepository.findByIdentifier("test-action")).thenReturn(action);
        when(snowConfigRepository.findById(1L)).thenReturn(Optional.of(snowConfig));

        // test the enum mapping
        assertEquals(action.getAwxTemplateType(), actionMapper.toEntity(actionDTO).getAwxTemplateType());

        when(actionRepository.save(any(Action.class))).thenReturn(action);

        actionService.updateAction(actionDTO);
    }

}
