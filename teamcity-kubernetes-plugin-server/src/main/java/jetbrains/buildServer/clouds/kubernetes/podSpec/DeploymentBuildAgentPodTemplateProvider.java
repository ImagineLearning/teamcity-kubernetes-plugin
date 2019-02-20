package jetbrains.buildServer.clouds.kubernetes.podSpec;

import com.intellij.openapi.util.Pair;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import java.io.File;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.kubernetes.*;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by ekoshkin (koshkinev@gmail.com) on 15.06.17.
 */
public class DeploymentBuildAgentPodTemplateProvider implements BuildAgentPodTemplateProvider {
    public static final String ID = "deployment-base";

    private final ServerSettings myServerSettings;
    private final DeploymentContentProvider myDeploymentContentProvider;
    private KubePodNameGenerator myPodNameGenerator;

  public DeploymentBuildAgentPodTemplateProvider(@NotNull ServerSettings serverSettings,
                                                 @NotNull DeploymentContentProvider deploymentContentProvider,
                                                 @NotNull final KubePodNameGenerator podNameGenerator) {
    myServerSettings = serverSettings;
    myDeploymentContentProvider = deploymentContentProvider;
    myPodNameGenerator = podNameGenerator;
  }

    @NotNull
    @Override
    public String getId() {
        return ID;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Use pod template from deployment";
    }

    @Nullable
    @Override
    public String getDescription() {
        return null;
    }

  @NotNull
    @Override
    public Pod getPodTemplate(@NotNull CloudInstanceUserData cloudInstanceUserData, @NotNull KubeCloudImage kubeCloudImage, @NotNull KubeCloudClientParameters kubeClientParams) {
        String sourceDeploymentName = kubeCloudImage.getSourceDeploymentName();
        if(StringUtil.isEmpty(sourceDeploymentName))
            throw new KubeCloudException("Deployment name is not set in kubernetes cloud image " + kubeCloudImage.getId());

        Deployment sourceDeployment = myDeploymentContentProvider.findDeployment(sourceDeploymentName, kubeClientParams);
        if(sourceDeployment == null)
            throw new KubeCloudException("Can't find source deployment by name " + sourceDeploymentName);

        final String agentNameProvided = cloudInstanceUserData.getAgentName();
        final String instanceName = StringUtil.isEmpty(agentNameProvided) ? sourceDeploymentName + "-" + UUID.randomUUID().toString() : agentNameProvided;
        final String serverAddress = cloudInstanceUserData.getServerAddress();

        PodTemplateSpec podTemplateSpec = sourceDeployment.getSpec().getTemplate();

        ObjectMeta metadata = podTemplateSpec.getMetadata();
        metadata.setName(instanceName);
        metadata.setNamespace(kubeClientParams.getNamespace());

        String serverUUID = myServerSettings.getServerUUID();
        String cloudProfileId = cloudInstanceUserData.getProfileId();

        Map<String, String> patchedLabels = new HashMap<>();
        patchedLabels.putAll(metadata.getLabels());
        patchedLabels.putAll(CollectionsUtil.asMap(
                KubeTeamCityLabels.TEAMCITY_AGENT_LABEL, "",
                KubeTeamCityLabels.TEAMCITY_SERVER_UUID, serverUUID,
                KubeTeamCityLabels.TEAMCITY_CLOUD_PROFILE, cloudProfileId,
                KubeTeamCityLabels.TEAMCITY_CLOUD_IMAGE, kubeCloudImage.getId()));
        metadata.setLabels(patchedLabels);

        PodSpec spec = podTemplateSpec.getSpec();
        for (Container container : spec.getContainers()){
            Map<String, String> patchedEnvData = new HashMap<>();
            for (EnvVar env : container.getEnv()){
                patchedEnvData.put(env.getName(), env.getValue());
            }

            for (Pair<String, String> env : Arrays.asList(
                    new Pair<>(KubeContainerEnvironment.SERVER_UUID, serverUUID),
                    new Pair<>(KubeContainerEnvironment.PROFILE_ID, cloudProfileId),
                    new Pair<>(KubeContainerEnvironment.IMAGE_ID, kubeCloudImage.getId()),
                    new Pair<>(KubeContainerEnvironment.INSTANCE_NAME, instanceName))){
                patchedEnvData.put(env.first, env.second);
            }

            if(!patchedEnvData.containsKey(KubeContainerEnvironment.SERVER_URL)){
                patchedEnvData.put(KubeContainerEnvironment.SERVER_URL, serverAddress);
            }
            if(!patchedEnvData.containsKey(KubeContainerEnvironment.OFFICIAL_IMAGE_SERVER_URL)){
                patchedEnvData.put(KubeContainerEnvironment.OFFICIAL_IMAGE_SERVER_URL, serverAddress);
            }

            List<EnvVar> patchedEnv = new ArrayList<>();
            for (String envName : patchedEnvData.keySet()){
                patchedEnv.add(new EnvVar(envName, patchedEnvData.get(envName), null));
            }
            container.setEnv(patchedEnv);
        }
        final Pod pod = new Pod();
        pod.setMetadata(metadata);
        pod.setSpec(spec);
        return pod;
    }
}
