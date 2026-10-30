package com.zdph.dksdk.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import com.zdph.dksdk.enums.DataWorksOperationState;

@Data
@ApiModel(value = "SubmitScriptResponse", description = "提交脚本结果")
public class SubmitScriptResponse {
    @ApiModelProperty(value = "提交受理状态；SUBMIT_ACCEPTED 表示已受理，最终结果需查询发布包", example = "SUBMIT_ACCEPTED")
    private DataWorksOperationState state;

    @ApiModelProperty(value = "提交操作生成的发布包 ID，用于检查提交状态", example = "2000001")
    private Long deploymentId;

    @ApiModelProperty(value = "提交文件接口返回的请求追踪 ID")
    private String requestId;

}
