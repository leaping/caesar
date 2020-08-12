package com.baiyi.caesar.factory.jenkins.engine.impl;

import com.aliyun.oss.model.OSSObjectSummary;
import com.baiyi.caesar.aliyun.oss.handler.AliyunOSSHandler;
import com.baiyi.caesar.builder.jenkins.CiJobBuildArtifactBuilder;
import com.baiyi.caesar.builder.jenkins.CiJobBuildChangeBuilder;
import com.baiyi.caesar.common.util.BeanCopierUtils;
import com.baiyi.caesar.decorator.application.CiJobEngineDecorator;
import com.baiyi.caesar.domain.BusinessWrapper;
import com.baiyi.caesar.domain.ErrorEnum;
import com.baiyi.caesar.domain.generator.caesar.*;
import com.baiyi.caesar.domain.vo.application.CiJobVO;
import com.baiyi.caesar.facade.jenkins.JenkinsCiJobFacade;
import com.baiyi.caesar.factory.jenkins.IJenkinsJobHandler;
import com.baiyi.caesar.factory.jenkins.JenkinsJobHandlerFactory;
import com.baiyi.caesar.factory.jenkins.context.JobBuildContext;
import com.baiyi.caesar.factory.jenkins.engine.JenkinsJobEngineHandler;
import com.baiyi.caesar.jenkins.handler.JenkinsJobHandler;
import com.baiyi.caesar.jenkins.handler.JenkinsServerHandler;
import com.baiyi.caesar.service.aliyun.CsOssBucketService;
import com.baiyi.caesar.service.jenkins.*;
import com.offbytwo.jenkins.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.baiyi.caesar.common.base.Global.ASYNC_POOL_TASK_COMMON;

/**
 * @Author baiyi
 * @Date 2020/8/6 10:56 上午
 * @Version 1.0
 */
@Slf4j
@Component
public class JenkinsJobEngineHandlerImpl implements JenkinsJobEngineHandler {

    @Resource
    private CsCiJobEngineService csCiJobEngineService;

    @Resource
    private JenkinsCiJobFacade jenkinsCiJobFacade;

    @Resource
    private CsJenkinsInstanceService csJenkinsInstanceService;

    @Resource
    private CiJobEngineDecorator ciJobEngineDecorator;

    @Resource
    private JenkinsJobHandler jenkinsJobHandler;

    @Resource
    private CsCiJobBuildService csCiJobBuildService;

    @Resource
    private JenkinsServerHandler jenkinsServerHandler;

    @Resource
    private AliyunOSSHandler aliyunOSSHandler;

    @Resource
    private CsOssBucketService csOssBucketService;

    @Resource
    private CsCiJobBuildArtifactService csCiJobBuildArtifactService;

    @Resource
    private CsCiJobBuildChangeService csCiJobBuildChangeService;

    private static final int TRACK_SLEEP_SECONDS = 5;

    @Override
    public BusinessWrapper<CiJobVO.JobEngine> acqJobEngine(CsCiJob csCiJob) {
        List<CsCiJobEngine> csCiJobEngines = queryCiJobEngine(csCiJob.getId());
        if (CollectionUtils.isEmpty(csCiJobEngines))
            return new BusinessWrapper<>(ErrorEnum.JENKINS_JOB_ENGINE_NOT_CONFIGURED); // 工作引擎未配置
        List<CsCiJobEngine> activeEngines = csCiJobEngines.stream().filter(e ->
                tryJenkinsInstanceActive(e.getJenkinsInstanceId())
        ).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(activeEngines))
            return new BusinessWrapper<>(ErrorEnum.JENKINS_JOB_NO_ENGINES_AVAILABLE); // 没有可用的工作引擎

        return new BusinessWrapper<>(buildJobEngine(activeEngines));
    }

    private CiJobVO.JobEngine buildJobEngine(List<CsCiJobEngine> activeEngines) {
        Random random = new Random();
        int n = random.nextInt(activeEngines.size());
        CsCiJobEngine csCiJobEngine = activeEngines.get(n);
        return ciJobEngineDecorator.decorator(BeanCopierUtils.copyProperties(csCiJobEngine, CiJobVO.JobEngine.class));
    }

    private boolean tryJenkinsInstanceActive(int jenkinsInstanceId) {
        CsJenkinsInstance csJenkinsInstance = csJenkinsInstanceService.queryCsJenkinsInstanceById(jenkinsInstanceId);
        if (csJenkinsInstance == null) return false;
        return csJenkinsInstance.getIsActive();
    }

    private List<CsCiJobEngine> queryCiJobEngine(int ciJobId) {
        List<CsCiJobEngine> csCiJobEngines = csCiJobEngineService.queryCsCiJobEngineByJobId(ciJobId);
        if (CollectionUtils.isEmpty(csCiJobEngines))
            jenkinsCiJobFacade.createJobEngine(ciJobId);
        return csCiJobEngineService.queryCsCiJobEngineByJobId(ciJobId);
    }

    @Override
    @Async(value = ASYNC_POOL_TASK_COMMON)
    public void trackJobBuild(JobBuildContext jobBuildContext) {
        while (true) {
            try {
                JobWithDetails job = jenkinsServerHandler.getJob(jobBuildContext.getJobEngine().getJenkinsInstance().getName(), jobBuildContext.getCsCiJobBuild().getJobName());
                Build build = jenkinsJobHandler.getJobBuildByNumber(job, jobBuildContext.getCsCiJobBuild().getEngineBuildNumber());
                BuildWithDetails buildWithDetails = build.details();
                recordJobEngine(job, jobBuildContext.getJobEngine());
                if (buildWithDetails.isBuilding()) {
                    TimeUnit.SECONDS.sleep(TRACK_SLEEP_SECONDS); // 执行中
                } else {
                    // 任务完成
                    jobBuildContext.setBuildWithDetails(buildWithDetails);
                    recordJobBuild(jobBuildContext);
                    break;
                }
            } catch (RuntimeException e) {
                log.error(e.getMessage());
                e.printStackTrace();
                break;
            } catch (InterruptedException | IOException e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void recordJobEngine(JobWithDetails job, CiJobVO.JobEngine jobEngine) {
        if (job.getNextBuildNumber() == jobEngine.getNextBuildNumber()) return;
        jobEngine.setNextBuildNumber(job.getNextBuildNumber());
        jobEngine.setLastBuildNumber(job.getNextBuildNumber() - 1);
        csCiJobEngineService.updateCsCiJobEngine(BeanCopierUtils.copyProperties(jobEngine, CsCiJobEngine.class));
    }

    /**
     * 记录构建信息
     */
    private void recordJobBuild(JobBuildContext jobBuildContext) {
        CsCiJobBuild csCiJobBuild = jobBuildContext.getCsCiJobBuild();
        csCiJobBuild.setBuildStatus(jobBuildContext.getBuildWithDetails().getResult().name());
        csCiJobBuild.setEndTime(new Date());
        csCiJobBuild.setFinalized(true);
        csCiJobBuildService.updateCsCiJobBuild(csCiJobBuild);
        jobBuildContext.setCsCiJobBuild(csCiJobBuild);

        jobBuildContext.getBuildWithDetails().getArtifacts().forEach(e -> {
            CsCiJobBuildArtifact csCiJobBuildArtifact = buildCiJobBuildArtifact(jobBuildContext, e);
            csCiJobBuildArtifactService.addCsCiJobBuildArtifact(csCiJobBuildArtifact);
        });

        recordJobBuildChanges(jobBuildContext);
    }

    private void recordJobBuildChanges(JobBuildContext jobBuildContext) {
        List<BuildChangeSet> buildChanges = jobBuildContext.getBuildWithDetails().getChangeSets();
        // 变更记录
        buildChanges.forEach(set ->
                set.getItems().forEach(e -> {
                    CsCiJobBuildChange csCiJobBuildChange = CiJobBuildChangeBuilder.build(jobBuildContext, e);
                    csCiJobBuildChangeService.addCsCiJobBuildChange(csCiJobBuildChange);
                })
        );
    }

    private CsCiJobBuildArtifact buildCiJobBuildArtifact(JobBuildContext jobBuildContext, Artifact artifact) {
        CsCiJobBuildArtifact csCiJobBuildArtifact = CiJobBuildArtifactBuilder.build(jobBuildContext, artifact);
        CsOssBucket ossBucket = acqOssBucket(jobBuildContext.getCsCiJob());
        IJenkinsJobHandler iJenkinsJobHandler = JenkinsJobHandlerFactory.getJenkinsJobBuildByKey(jobBuildContext.getCsCiJob().getJobType());

        String ossPath = iJenkinsJobHandler.acqOssPath(jobBuildContext.getCsCiJobBuild(), csCiJobBuildArtifact);
        csCiJobBuildArtifact.setStoragePath(ossPath);

        List<OSSObjectSummary> objects = aliyunOSSHandler.listObjects(ossBucket.getName(), ossPath);
        if (!CollectionUtils.isEmpty(objects)) {
            OSSObjectSummary ossObjectSummary = objects.get(0);
            csCiJobBuildArtifact.setStoragePath(ossObjectSummary.getKey());
            csCiJobBuildArtifact.setArtifactSize(ossObjectSummary.getSize());
        }
        return csCiJobBuildArtifact;
    }

    private CsOssBucket acqOssBucket(CsCiJob csCiJob) {
        return csOssBucketService.queryCsOssBucketById(csCiJob.getOssBucketId());
    }

}