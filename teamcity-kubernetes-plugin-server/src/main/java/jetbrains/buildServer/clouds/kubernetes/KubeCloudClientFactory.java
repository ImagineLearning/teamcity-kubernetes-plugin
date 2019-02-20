package jetbrains.buildServer.clouds.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClientException;
import jetbrains.buildServer.agent.Constants;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.kubernetes.auth.KubeAuthStrategyProvider;
import jetbrains.buildServer.clouds.kubernetes.connector.KubeApiConnector;
import jetbrains.buildServer.clouds.kubernetes.connector.KubeApiConnectorImpl;
import jetbrains.buildServer.clouds.kubernetes.podSpec.BuildAgentPodTemplateProviders;
import jetbrains.buildServer.clouds.kubernetes.web.KubeProfileEditController;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by ekoshkin (koshkinev@gmail.com) on 27.05.17.
 */
public class KubeCloudClientFactory implements CloudClientFactory {

    public static final String DISPLAY_NAME = "Kubernetes";
    public static final String ID = "kube"; //should be 6 chars maximum

    private final PluginDescriptor myPluginDescriptor;
    private final ServerSettings myServerSettings;
    private final KubeDataCache myCache;
    private final KubeAuthStrategyProvider myAuthStrategies;
    private final BuildAgentPodTemplateProviders myPodTemplateProviders;
    private final KubeBackgroundUpdater myUpdater;

    public KubeCloudClientFactory(@NotNull final CloudRegistrar registrar,
                                  @NotNull final PluginDescriptor pluginDescriptor,
                                  @NotNull final ServerSettings serverSettings,
                                  @NotNull final KubeAuthStrategyProvider authStrategies,
                                  @NotNull final BuildAgentPodTemplateProviders podTemplateProviders,
                                  @NotNull final KubeDataCache cache,
                                  @NotNull final KubeBackgroundUpdater updater) {
        myPluginDescriptor = pluginDescriptor;
        myServerSettings = serverSettings;
        myAuthStrategies = authStrategies;
        myPodTemplateProviders = podTemplateProviders;
        myCache = cache;
        myUpdater = updater;
        registrar.registerCloudFactory(this);
    }

    @NotNull
    @Override
    public String getCloudCode() {
        return ID;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Nullable
    @Override
    public String getEditProfileUrl() {
        return myPluginDescriptor.getPluginResourcesPath(KubeProfileEditController.EDIT_KUBE_HTML);
    }

    @NotNull
    @Override
    public Map<String, String> getInitialParameterValues() {
        return Collections.emptyMap();
    }

    @NotNull
    @Override
    public PropertiesProcessor getPropertiesProcessor() {
        return new KubeProfilePropertiesProcessor();
    }

    @Override
    public boolean canBeAgentOfType(@NotNull AgentDescription agentDescription) {
        final Map<String, String> map = agentDescription.getAvailableParameters();
        return  map.containsKey(Constants.ENV_PREFIX + KubeContainerEnvironment.SERVER_UUID) &&
                map.containsKey(Constants.ENV_PREFIX + KubeContainerEnvironment.PROFILE_ID) &&
                map.containsKey(Constants.ENV_PREFIX + KubeContainerEnvironment.IMAGE_ID) &&
                map.containsKey(Constants.ENV_PREFIX + KubeContainerEnvironment.INSTANCE_NAME);
    }

    @NotNull
    @Override
    public CloudClientEx createNewClient(@NotNull CloudState cloudState, @NotNull CloudClientParameters cloudClientParameters) {
        try {
            final KubeCloudClientParametersImpl kubeClientParams = KubeCloudClientParametersImpl.create(cloudClientParameters);
          final KubeApiConnector apiConnector = new KubeApiConnectorImpl(kubeClientParams, myAuthStrategies.get(kubeClientParams.getAuthStrategy()));
            List<KubeCloudImage> images = CollectionsUtil.convertCollection(kubeClientParams.getImages(), kubeCloudImageData -> {
                KubeCloudImageImpl kubeCloudImage = new KubeCloudImageImpl(kubeCloudImageData, apiConnector, myCache, myPodTemplateProviders);
                kubeCloudImage.populateInstances();
                return kubeCloudImage;
            });
            return new KubeCloudClient(
                    myServerSettings.getServerUUID(),
                    cloudState.getProfileId(),
                    images,
                    kubeClientParams,
                    myUpdater);
        } catch (Throwable ex){
            if(ex instanceof KubernetesClientException){
                final Throwable cause = ex.getCause();
                if(cause != null) throw new CloudException(cause.getMessage(), cause);
                else throw ex;
            } else throw ex;
        }
    }
}
