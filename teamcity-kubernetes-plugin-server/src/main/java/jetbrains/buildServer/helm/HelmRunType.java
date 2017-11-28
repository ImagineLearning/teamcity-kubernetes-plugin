package jetbrains.buildServer.helm;

import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.RunType;
import jetbrains.buildServer.serverSide.RunTypeRegistry;
import jetbrains.buildServer.util.PropertiesUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static jetbrains.buildServer.helm.HelmConstants.HELM_PATH_CONFIG_PARAM;
import static jetbrains.buildServer.helm.HelmConstants.HELM_RUN_TYPE;

/**
 * Created by Evgeniy Koshkin (evgeniy.koshkin@jetbrains.com) on 28.11.17.
 */
public class HelmRunType extends RunType implements HelmCommandRegistry {
    private final PluginDescriptor myPluginDescriptor;
    private final Map<String, HelmCommand> myCommandIdToCommandMap = new HashMap<>();

    public HelmRunType(PluginDescriptor pluginDescriptor, RunTypeRegistry runTypeRegistry) {
        myPluginDescriptor = pluginDescriptor;
        runTypeRegistry.registerRunType(this);
    }

    @NotNull
    @Override
    public String getType() {
        return HELM_RUN_TYPE;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Helm";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Run Helm command";
    }

    @Nullable
    @Override
    public PropertiesProcessor getRunnerPropertiesProcessor() {
        return properties -> {
            List<InvalidProperty> result = new Vector<>();
            final String commandId = properties.get(HelmConstants.COMMAND_ID);
            if (PropertiesUtil.isEmptyOrNull(commandId)) {
                result.add(new InvalidProperty(HelmConstants.COMMAND_ID, "Command to run must be specified"));
            }
            final HelmCommand helmCommand = myCommandIdToCommandMap.get(commandId);
            if(helmCommand != null){
                PropertiesProcessor propertiesProcessor = helmCommand.getPropertiesProcessor();
                if(propertiesProcessor != null) {
                    result.addAll(propertiesProcessor.process(properties));
                }
            }
            return result;
        };
    }

    @Nullable
    @Override
    public String getEditRunnerParamsJspFilePath() {
        return myPluginDescriptor.getPluginResourcesPath("helm/editHelm.jsp");
    }

    @Nullable
    @Override
    public String getViewRunnerParamsJspFilePath() {
        return myPluginDescriptor.getPluginResourcesPath("helm/viewHelm.jsp");
    }

    @Nullable
    @Override
    public Map<String, String> getDefaultRunnerProperties() {
        return null;
    }

    @NotNull
    @Override
    public List<Requirement> getRunnerSpecificRequirements(@NotNull Map<String, String> runParameters) {
        return Collections.singletonList(new Requirement(HELM_PATH_CONFIG_PARAM, null, RequirementType.EXISTS));
    }

    @Override
    public void registerCommand(@NotNull HelmCommand helmCommand) {
        myCommandIdToCommandMap.put(helmCommand.getId(), helmCommand);
    }
}
