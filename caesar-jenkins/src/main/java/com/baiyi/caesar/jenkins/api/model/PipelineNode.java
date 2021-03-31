package com.baiyi.caesar.jenkins.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @Author baiyi
 * @Date 2021/3/30 2:39 下午
 * @Version 1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineNode {

    /**
     * actions: []
     * causeOfBlockage: null
     * displayDescription: null
     * displayName: "检出项目"
     * durationInMillis: 433
     * edges: [,…]
     * 0: {_class: "io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeImpl$EdgeImpl", id: "11", type: "STAGE"}
     * id: "11"
     * type: "STAGE"
     * _class: "io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeImpl$EdgeImpl"
     * firstParent: null
     * id: "6"
     * input: null
     * restartable: true
     * result: "SUCCESS"
     * startTime: "2021-03-01T14:40:07.720+0800"
     * state: "FINISHED"
     * type: "STAGE"
     */

    private String causeOfBlockage;
    private String displayDescription;
    private String displayName;
    private Integer durationInMillis;
    private String firstParent;
    private String id;

    private Boolean restartable;

    private String result;
    //   shape = JsonFormat.Shape.STRING       timezone = "GMT+8"                                           2021-03-01T14:40:07.720+0800
    // @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd'T'HH:mm.SSS'Z'")
    private String startTime;
    private String state;
    private String type;
}
