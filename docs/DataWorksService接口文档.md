# DataWorksService 接口文档

更新时间：2026-10-30。适用于当前 dksdk 源码、Java 8，以及 `dataworks_public_20240902:4.2.5`。

本文以 `com.zdph.dksdk.service.DataWorksService` 实际实现为准，结合所提供的飞天企业版 `enterprise_v318_6_ST` API 文档整理。示例 ID、目录、节点名称均需替换为目标环境实际值。

普通 JAR 接入、Bean 注册及离线交付方式见 [项目说明](../README.md)。调用方使用 `@Import(DataWorksSdkConfiguration.class)`，并在自己的应用中提供配置和校验实现。

## 调用约定

- 通过 Spring 注入 `DataWorksService`，直接调用 Java 方法；本文不定义 HTTP 地址。
- JSON 示例用于展示 Java 对象字段。普通 Response 按小驼峰字段展示；SDK 原生 Response 的 `body`、`headers` 按 Java 属性结构展示，实际 JSON 键名取决于序列化方式。当前 4.2.5 SDK 这两个原生 Response 没有顶层 statusCode 属性。
- 示例可省略不涉及的可选字段，不代表这些字段不存在。表中的“必填”按当前 Service 校验及公司版接口要求说明。
- `projectId`、默认 `projectEnv`、`regionId`、`endpoint` 和鉴权信息来自调用方的 `dataworks.*` 配置，SDK JAR 不携带 application.yml。公司 PDF 要求 `RegionId=default`。创建、提交、提交状态查询、直接发布、运行及运行状态查询已显式传 RegionId；部分节点/日志查询与 deploy 的发布分支仍未显式传入，需以公司环境核验。
- `fileId` 是开发文件 ID；`nodeId` 是调度节点 ID；`deploymentId` 是提交/发布包 ID；`dagId` 是运行工作流 ID；`instanceId` 是实际执行实例 ID，不能相互替代。
- `SUBMIT_ACCEPTED`、`PUBLISH_ACCEPTED`、`RUN_ACCEPTED` 表示请求已受理，最终结果需另行查询。
- 参数校验失败抛 `ConstraintViolationException` 或 `IllegalArgumentException`；SDK/上游调用失败抛 `DataWorksApiException`，通过 `getCode()`、`getMessage()`、`getRequestId()` 获取错误信息。当前返回值没有统一 `ApiResponse` 外层。

```java
@Autowired
private DataWorksService dataWorksService;
```

## 接口总览

| 能力 | Service 方法 | 阿里云 Action | 返回类型 |
| --- | --- | --- | --- |
| 创建任务 | `DataWorksService.create(request)` | `CreateFile` | `CreateScriptResponse` |
| 提交任务 | `DataWorksService.submit(request)` | `SubmitFile` | `SubmitScriptResponse` |
| 获取节点信息 | `DataWorksService.getNodesInfo(nodeName)` | `ListNodes` | `ListNodesResponseBodyData` |
| 获得文件提交状态 | `DataWorksService.getDeployment(deploymentId)` | `GetDeployment` | `GetDeploymentResponse` |
| 发布（检查后发布） | `DataWorksService.deploy(request)` | `GetDeployment`，满足条件后 `DeployFile` | `DeployScriptResponse` |
| 直接发布 | `DataWorksService.deployFile(fileId, nodeId)` | `DeployFile` | `DeployFileResponse` |
| 任务运行（补数据） | `DataWorksService.runScript(request)` | `RunCycleDagNodes` | `RunScriptResponse` |
| 查询运行状态 | `DataWorksService.queryExecution(request)` | `ListInstances` | `QueryExecutionResponse` |
| 查询运行日志 | `DataWorksService.logs(request)` | `GetInstanceLog`；按节点查时先 `ListInstances` | `QueryLogsResponse` |

## 1. 创建任务

方法：`CreateScriptResponse create(CreateScriptRequest request)`。Action：`CreateFile`。

在数据开发中创建文件，返回 `fileId`。创建后需另行提交、发布。所提供 PDF 说明该 Action 不支持创建数据集成节点任务。

### 入参：CreateScriptRequest

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| fileName | String | 是 | 文件名称。 |
| fileType | Integer | 是 | 正整数代码类型，例如 6 Shell、10 ODPS SQL、99 虚拟节点、221 PyODPS 2、1221 PyODPS 3。 |
| fileFolderPath | String | 是 | 已存在的目录路径，例如 `业务流程/123/MaxCompute`；以平台实际目录为准。PDF 标为可选，当前 DTO 要求必填。 |
| content | String | 是 | 与 fileType 匹配的脚本代码；当前 DTO 不允许空白。 |
| fileDescription | String | 否 | 文件描述。 |
| owner | String | 否 | 文件责任人 UID；PDF 说明省略时使用调用者 UID。 |
| connectionName | String | 否 | 执行数据源连接名称，例如 `odps_first`。 |
| cronExpress | String | 否 | DataWorks cron，例如 `00 00 01 * * ?`。PDF 要求最短间隔 5 分钟、每天最早调度时间 00:05。 |
| cycleType | String | 否 | `DAY`：日/周/月；`NOT_DAY`：分钟/小时。 |
| dependentNodeIdList | String | 否 | 跨周期依赖节点列表，格式按目标平台约定。 |
| dependentType | String | 否 | `NONE` 不依赖上一周期；`SELF` 本节点；`CHILD` 一层子节点；`USER_DEFINE` 自定义。 |
| paraValue | String | 否 | 调度参数，空格分隔，例如 `a=x b=y`。 |
| inputList | String | 公司环境必填 | 上游节点的输出名称，英文逗号分隔，例如 `workspace_root`。不是业务流程名称。PDF 标为可选，但此前公司环境返回过必填错误；当前 DTO 没有强制非空注解。 |
| resourceGroupId | Long | 否 | 调度资源组 ID；PDF 标记已废弃，建议使用 resourceGroupIdentifier，两者选一。 |
| resourceGroupIdentifier | String | 否 | 调度资源组标识。 |
| schedulerType | String | 否 | `NORMAL` 正常调度；`MANUAL` 手动；`PAUSE` 暂停；`SKIP` 空跑。 |
| startEffectDate | Long | 否 | 自动调度开始生效时间，毫秒时间戳。 |
| endEffectDate | Long | 否 | 自动调度结束生效时间，毫秒时间戳。 |
| autoParsing | Boolean | 否 | 是否自动解析代码依赖。 |
| autoRerunTimes | Integer | 否 | 出错自动重跑次数，PDF 上限 10。 |
| autoRerunIntervalMillis | Integer | 否 | 自动重跑间隔，毫秒；PDF 上限 1800000（30 分钟）。 |
| rerunMode | String | 否 | `ALL_ALLOWED` 成功/失败均可重跑；`FAILURE_ALLOWED` 仅失败可重跑；`ALL_DENIED` 均不可重跑。 |
| stop | Boolean | 否 | 是否暂停调度。 |

表中来自 PDF 的取值限制并非全部由当前 DTO 本地校验。PDF 中的 `AdvancedSettings`、`StartImmediately`、`StartRightNow` 等未在当前 DTO 暴露。

### 返参：CreateScriptResponse

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| fileId | Long | 新建文件 ID，后续提交时使用。 |
| requestId | String | 本次上游请求追踪 ID。 |

入参示例：

```json
{
  "fileName": "dksdk_demo",
  "fileType": 1221,
  "fileFolderPath": "业务流程/123/MaxCompute",
  "content": "print('hello DataWorks')",
  "inputList": "workspace_root",
  "fileDescription": "研发组调用示例",
  "cronExpress": "00 00 01 * * ?",
  "cycleType": "DAY",
  "autoParsing": false,
  "stop": false
}
```

返参示例：

```json
{"fileId":1000001,"requestId":"req-create-001"}
```

## 2. 提交任务

方法：`SubmitScriptResponse submit(SubmitScriptRequest request)`。Action：`SubmitFile`。

将文件提交到调度系统开发环境，异步生成任务。返回的发布包 ID 用于查询提交结果。

### 入参与返参

| 方向 | 参数 | 类型 | 说明 |
| --- | --- | --- | --- |
| 入参 | fileId | Long | 必填，正整数，创建任务返回的文件 ID。 |
| 返参 | state | DataWorksOperationState | `SUBMIT_ACCEPTED`：提交已受理。 |
| 返参 | deploymentId | Long | 本次提交生成的发布包 ID。 |
| 返参 | requestId | String | 请求追踪 ID。 |

入参示例：

```json
{"fileId":1000001}
```

返参示例：

```json
{"state":"SUBMIT_ACCEPTED","deploymentId":2000001,"requestId":"req-submit-001"}
```

## 3. 获取节点信息

方法：`ListNodesResponseBody.ListNodesResponseBodyData getNodesInfo(String nodeName)`。Action：`ListNodes`。

### 入参

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| nodeName | String | 否 | 节点名称筛选条件；当前方法没有非空校验，传 null 时不设置该条件。 |

工作空间和环境来自配置。当前固定 `PageNumber=1`、`PageSize=10`，最多返回第一页 10 条；不支持调用方传分页或节点类型。

### 返参：ListNodesResponseBodyData

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| totalCount | Integer | 符合条件的节点总数，不等于当前页条数。 |
| pageNumber | Integer | 页码。 |
| pageSize | Integer | 每页条数。 |
| nodes | List | 节点信息，字段见下表。 |

`nodes[]` 字段：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| nodeId | Long | 调度节点 ID，运行时可用于 rootNodeId/includeNodeIds。 |
| nodeName | String | 节点名称。 |
| programType | String | 节点类型，例如 `ODPS_SQL`、`PYODPS3`。 |
| projectId | Long | 工作空间 ID。 |
| ownerId | String | 负责人 UID。 |
| connection | String | 执行连接。 |
| cronExpress | String | 调度 cron。 |
| description | String | 节点描述。 |
| paramValues | String | 运行参数。 |
| schedulerType | String | NORMAL/MANUAL/PAUSE/SKIP，含义见创建任务。 |
| resGroupName | String | 资源组名称。 |
| priority | Integer | 优先级，PDF 列举 1/3/5/7/8。 |
| repeatability | Boolean | 是否可重复运行。 |
| repeatInterval | Long | 出错后重新调度间隔；所提供 ListNodes PDF 未明确单位，按上游值返回。 |
| relatedFlowId | Long | 关联业务流程 ID。 |
| businessId | Long | 当前 SDK 扩展字段，所提供 PDF 未说明其语义，按上游原值返回。 |
| baselineId | Long | 基线 ID。 |
| dqcType | Integer | 0 有关联数据质量检查，1 无关联。 |
| dqcDescription | String | 数据质量检查分区规则字符串。 |

入参调用示例：

```java
dataWorksService.getNodesInfo("dksdk_demo");
```

返参示例（省略其他节点可选字段）：

```json
{
  "totalCount": 1,
  "pageNumber": 1,
  "pageSize": 10,
  "nodes": [{"nodeId":3000001,"nodeName":"dksdk_demo","programType":"PYODPS3","projectId":10000,"schedulerType":"NORMAL"}]
}
```

当前方法只返回 SDK 的 Data 部分，不包含外层 requestId/success。上游 Data 为 null 时返回 totalCount=0、nodes=[]，此时页码字段可能为 null。

## 4. 获得文件提交状态

方法：`GetDeploymentResponse getDeployment(Long deploymentId)`。Action：`GetDeployment`。

同时支持查询提交包和生产发布包：传提交返回的 ID 可查提交结果；传发布返回的新 ID 可查发布结果。

### 入参

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deploymentId | Long | 是 | 提交或发布操作生成的发布包 ID；当前方法未做 DTO 参数校验。 |

### 返参：GetDeploymentResponse（SDK 原生对象）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| headers | Map<String, String> | SDK HTTP 响应头。 |
| body.success | Boolean | 上游调用是否成功；失败由 Service 抛异常。 |
| body.requestId | String | 请求追踪 ID。 |
| body.httpStatusCode | Integer | 上游响应体内的 HTTP 状态码。 |
| body.errorCode | String | 上游错误码。 |
| body.errorMessage | String | 接口调用错误信息。 |
| body.data.deployment | Object | 发布包详情，字段见下表。 |

`body.data.deployment` 字段：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| status | Integer | 0 就绪，1 成功，2 失败。判断提交/发布结果使用本字段。 |
| checkingStatus | Integer | SDK 扩展检查状态，PDF 未定义。当前 deploy 将 7 视为检查失败，其他非空值视为等待。 |
| fromEnvironment | Integer | 来源环境：0 本地，1 开发。 |
| toEnvironment | Integer | 目标环境：1 开发，2 生产。 |
| errorMessage | String | 发布包执行错误信息，status=2 时用于排查。 |
| name | String | 发布包名称。 |
| creatorId | String | 发布包创建人 UID。 |
| handlerId | String | 发布包执行人 UID。 |
| createTime | Long | 创建时间，毫秒时间戳。 |
| executeTime | Long | 开始执行时间，毫秒时间戳。 |

入参调用示例：

```java
dataWorksService.getDeployment(2000001L);
```

返参示例（省略响应头等可选字段）：

```json
{
  "body": {
    "success": true,
    "httpStatusCode": 200,
    "requestId": "req-status-001",
    "data": {"deployment":{"status":1,"checkingStatus":null,"fromEnvironment":0,"toEnvironment":1,"errorMessage":null,"name":"dksdk_demo_submit"}}
  }
}
```

## 5. 发布（检查后发布）

方法：`DeployScriptResponse deploy(DeployScriptRequest request)`。Action：先 `GetDeployment`，满足条件后调用 `DeployFile`。

### 入参：DeployScriptRequest

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deploymentId | Long | 是 | 正整数。执行发布时传提交包 ID；仅查询时也可传生产发布包 ID。 |
| fileId | Long | 条件必填 | 正整数文件 ID，发布时与 nodeId 必须且只能填一个。 |
| nodeId | Long | 条件必填 | 正整数调度节点 ID，发布时与 fileId 必须且只能填一个。 |
| checkOnly | boolean | 否 | 默认 false；true 仅查询，此时 fileId/nodeId 可省略。 |

### 返参：DeployScriptResponse

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| state | DataWorksOperationState | 处理状态，见下表。 |
| deploymentId | Long | 本次查询的发布包 ID，与入参一致。 |
| publishDeploymentId | Long | 发布受理后生成的新包 ID，需用它查询最终发布结果。 |
| status | Integer | 所查询包的状态：0 就绪、1 成功、2 失败。 |
| checkingStatus | Integer | 上游扩展检查状态。 |
| fromEnvironment | Integer | 所查询包的来源环境：0 本地、1 开发。 |
| toEnvironment | Integer | 所查询包的目标环境：1 开发、2 生产。 |
| errorMessage | String | 所查询包的错误信息。 |
| statusRequestId | String | 查询发布包的请求追踪 ID。 |
| publishRequestId | String | 发布调用的请求追踪 ID，未发布时为空。 |

| state | 含义与下一步 |
| --- | --- |
| STATUS_ONLY | checkOnly=true，只查询；通过 status 判断结果。 |
| WAITING | 提交状态为空/0，或扩展检查尚未通过；本次未发布。 |
| SUBMIT_FAILED | 提交状态不是 1 且不属于上述等待情况；本次未发布。 |
| CHECK_FAILED | checkingStatus=7；本次未发布。 |
| PUBLISH_ACCEPTED | 发布已受理；使用 publishDeploymentId 查询最终结果。 |

发布前要求提交包 status=1、checkingStatus=null，且 toEnvironment 非空时必须等于 1，否则拒绝发布。该方法只检查一次，不内置轮询。包与 fileId/nodeId 的对应关系需由调用方保证，当前未核验。

入参示例：

```json
{"deploymentId":2000001,"fileId":1000001,"checkOnly":false}
```

返参示例：

```json
{
  "state": "PUBLISH_ACCEPTED",
  "deploymentId": 2000001,
  "publishDeploymentId": 2000002,
  "status": 1,
  "checkingStatus": null,
  "fromEnvironment": 0,
  "toEnvironment": 1,
  "errorMessage": null,
  "statusRequestId": "req-status-002",
  "publishRequestId": "req-publish-001"
}
```

这里的 status=1、toEnvironment=1 属于原提交包，不代表生产发布已完成。收到 PUBLISH_ACCEPTED 后，使用 `getDeployment(2000002L)`，或 `deploy` 配合 `{"deploymentId":2000002,"checkOnly":true}` 查询。不要反复用原入参触发发布。

## 6. 直接发布

方法：`DeployFileResponse deployFile(Long fileId, Long nodeId)`。Action：`DeployFile`。

直接调用上游发布接口，不预查提交状态。调用方自行保证文件已经满足发布条件。

### 入参

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| fileId | Long | 二选一 | 待发布文件 ID。 |
| nodeId | Long | 二选一 | 待发布调度节点 ID。 |

PDF 说明两者配置一个即可。当前方法没有二选一或正数校验，应由调用方只传一个有效 ID。当前未暴露 PDF 中的 Comment 参数。

### 返参：DeployFileResponse（SDK 原生对象）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| headers | Map<String, String> | HTTP 响应头。 |
| body.data | Long | 新的生产发布包 ID，用于 getDeployment 查询结果。 |
| body.success | Boolean | 上游调用成功，不代表发布包执行完毕。 |
| body.requestId | String | 请求追踪 ID。 |
| body.httpStatusCode | Integer | 响应体内 HTTP 状态码。 |
| body.errorCode | String | 上游错误码。 |
| body.errorMessage | String | 上游错误信息。 |

入参调用示例：

```java
dataWorksService.deployFile(1000001L, null);
// 或使用节点 ID：dataWorksService.deployFile(null, 3000001L);
```

返参示例：

```json
{"body":{"success":true,"httpStatusCode":200,"data":2000002,"requestId":"req-publish-002"}}
```

## 7. 任务运行（补数据）

方法：`RunScriptResponse runScript(RunScriptRequest request)`。Action：`RunCycleDagNodes`。

按业务日期为周期调度节点创建补数据工作流，返回一组 dagId。

### 入参：RunScriptRequest

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| rootNodeId | Long | 是 | 正整数补数据起始节点 ID。 |
| startBizDate | String | 是 | 起始业务日期，格式 `yyyy-MM-dd 00:00:00`。 |
| endBizDate | String | 是 | 结束业务日期，同上，不能早于 startBizDate。 |
| name | String | 是 | 工作流名称；当前封装不做去重或幂等控制。 |
| projectEnv | String | 否 | DEV/PROD，省略使用配置；DTO 接受大小写，Service 转成大写。 |
| parallelism | Boolean | 默认 false | 是否并行补数据，不能显式传 null。公司 PDF 描述为“是否同时对多个节点补数据”；当前 DTO 注释描述多个业务日期并行，具体调度语义需以公司平台为准。 |
| includeNodeIds | String | 是 | 待补数据节点 ID，正整数、英文逗号分隔，不带空格。 |
| excludeNodeIds | String | 否 | 不执行脚本的节点 ID，格式同上；这些节点生成空跑实例，调度后直接成功。 |
| bizBeginTime | String | 否 | 小时调度任务开始时间，HH:mm:ss；当前封装要求与 bizEndTime 同时提供。 |
| bizEndTime | String | 否 | 小时调度任务结束时间，HH:mm:ss。时间取值 00:00:00～23:59:59；当前未校验两者先后关系。 |
| nodeParams | String | 否 | JSON 字符串，键为节点 ID，值为参数字符串，例如 `{"3000001":"a=123 b=456"}`。 |

可选字符串不用时省略或设为 null；projectEnv、excludeNodeIds 和时间字段传空字符串会被格式校验拒绝。

### 返参：RunScriptResponse

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| state | DataWorksOperationState | `RUN_ACCEPTED`：运行请求已受理。 |
| dagIds | List<Long> | 工作流 ID 列表，逐个用于查询运行状态。 |
| rootNodeId | Long | 本次起始节点 ID。 |
| projectEnv | String | 实际运行环境。 |
| requestId | String | 请求追踪 ID。 |

入参示例：

```json
{
  "rootNodeId": 3000001,
  "startBizDate": "2026-10-01 00:00:00",
  "endBizDate": "2026-10-02 00:00:00",
  "name": "dksdk_backfill_demo",
  "projectEnv": "PROD",
  "parallelism": false,
  "includeNodeIds": "3000001,3000002",
  "nodeParams": "{\"3000001\":\"a=123 b=456\"}"
}
```

返参示例（列表数量以实际响应为准）：

```json
{"state":"RUN_ACCEPTED","dagIds":[4000001,4000002],"rootNodeId":3000001,"projectEnv":"PROD","requestId":"req-run-001"}
```

## 8. 查询运行状态

方法：`QueryExecutionResponse queryExecution(QueryExecutionRequest request)`。Action：`ListInstances`。

按单个 dagId 分页获取执行实例及状态；多个 dagId 需要分别调用。

### 入参：QueryExecutionRequest

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| dagId | Long | 是 | 正整数，runScript 返回的 dagIds 中某个 ID。 |
| projectEnv | String | 否 | DEV/PROD，省略使用配置，应与运行环境一致。 |
| pageNumber | Integer | 默认 1 | 不能为 null；当前 DTO 允许 1～1000000，但公司 PDF 要求 1～100，接入时按 1～100 使用。 |
| pageSize | Integer | 默认 20 | 不能为 null，范围 1～100。 |

接入差异：公司 PDF 将 NodeId 标为必填，但当前 DTO 不包含 nodeId，Service 仅按 DagId 查询。该调用是否被公司部署版本接受仍需验证；如返回缺少 NodeId，需调整封装后再对接，不能只靠修改入参 JSON 解决。

### 返参：QueryExecutionResponse

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| dagId | Long | 本次查询的工作流 ID。 |
| projectEnv | String | 实际查询环境。 |
| pageNumber | Integer | 当前页。 |
| pageSize | Integer | 每页数量。 |
| totalCount | Integer | 实例总数。 |
| hasMore | boolean | 是否还有下一页。 |
| requestId | String | 请求追踪 ID。 |
| instances | List<ExecutionInstanceResponse> | 当前页实例列表。空列表可能表示尚未生成实例，不代表成功。 |

`instances[]` 字段：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| instanceId | Long | 执行实例 ID，可用于查日志。 |
| nodeId | Long | 节点 ID。 |
| nodeName | String | 节点名称。 |
| status | String | 实例状态，见下表。 |
| bizdate | Long | 业务日期，毫秒时间戳。 |
| beginRunningTime | Long | 开始运行时间，毫秒时间戳；未开始可能为 null。 |
| finishTime | Long | 完成时间，毫秒时间戳；未完成可能为 null。 |
| errorMessage | String | 上游错误信息；PDF 已标记该字段废弃，失败原因应以 GetInstanceLog 为准。 |

| status | 说明 |
| --- | --- |
| NOT_RUN | 未运行。 |
| WAIT_TIME | 等待调度时间。 |
| WAIT_RESOURCE | 等待资源。 |
| RUNNING | 运行中。 |
| CHECKING | 数据质量校验中。 |
| CHECKING_CONDITION | 分支条件校验中。 |
| FAILURE | 执行失败。 |
| SUCCESS | 执行成功。 |

入参示例：

```json
{"dagId":4000001,"projectEnv":"PROD","pageNumber":1,"pageSize":20}
```

返参示例：

```json
{
  "dagId": 4000001,
  "projectEnv": "PROD",
  "pageNumber": 1,
  "pageSize": 20,
  "totalCount": 1,
  "hasMore": false,
  "requestId": "req-instances-001",
  "instances": [{"instanceId":5000001,"nodeId":3000001,"nodeName":"dksdk_demo","status":"RUNNING","bizdate":1790784000000,"beginRunningTime":1790874000000,"finishTime":null,"errorMessage":null}]
}
```

Service 不汇总整个工作流的最终状态，也不自动轮询；调用方需读取各页并判断实例状态。

## 9. 查询运行日志

方法：`QueryLogsResponse logs(QueryLogsRequest request)`。

Action：按实例调用 `GetInstanceLog`；按节点先调用 `ListInstances`，再逐实例调用 `GetInstanceLog`。

### 入参：QueryLogsRequest

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| instanceId | Long | 二选一 | 正整数执行实例 ID。查询指定实例日志。 |
| nodeId | Long | 二选一 | 正整数节点 ID。查询该节点昨日业务日期的实例日志，自动分页，每页 20 条。 |

instanceId 与 nodeId 必须且只能填一个。运行环境固定取 `dataworks.project-env`，该 DTO 不支持环境覆盖。按节点查询的“昨日”按应用 JVM 默认时区计算，当前没有使用 bizdateZone 配置。

查询某次补数据的日志，建议先用 queryExecution 取 instanceId，再按实例查询；按 nodeId 查询会限定昨日业务日期。

### 返参：QueryLogsResponse

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| instanceId | Long | 按实例查询时回填，否则为 null。 |
| nodeId | Long | 按节点查询时回填，否则为 null。 |
| logContent | String | 按实例查询的日志；上游日志为 null 时转为空字符串，按节点查询时本字段为 null。 |
| logs | List<DwInstanceLogDetail> | 按节点查询的日志明细；按实例查询时为 []。 |

`logs[]` 字段：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| instanceId | Long | 实例 ID。 |
| nodeId | Long | 节点 ID。 |
| nodeName | String | 节点名称。 |
| dagId | Long | 所属工作流 ID。 |
| dagType | String | 当前聚合代码未填充，通常为 null。 |
| status | String | 实例状态，同运行状态表。 |
| bizdate | String | 业务日期，格式 yyyyMMdd。 |
| cycTime | String | 计划调度时间，格式 yyyy-MM-dd HH:mm:ss。 |
| beginRunningTime | String | 开始运行时间，格式同上。 |
| beginWaitResTime | String | 开始等待资源时间，格式同上。 |
| beginWaitTimeTime | String | 开始等待调度时间，格式同上。 |
| finishTime | String | 运行结束时间，格式同上。 |
| dqcType | Integer | 数据质量检查类型：0 有关联，1 无关联。 |
| paramValues | String | 运行参数。 |
| errorMessage | String | 上游实例错误信息；PDF 已标记废弃，排错以日志为准。 |
| logContent | String | 实例日志内容。 |

时间/日期字段无上游值时，当前聚合代码返回空字符串。QueryLogsResponse 不包含 requestId，调用异常仍可从 DataWorksApiException 获取。

按实例入参示例：

```json
{"instanceId":5000001}
```

返参示例：

```json
{"instanceId":5000001,"nodeId":null,"logContent":"Task started\nhello DataWorks\nTask finished","logs":[]}
```

按节点入参示例：

```json
{"nodeId":3000001}
```

返参示例（假设应用日期为 2026-10-26，省略部分可选字段）：

```json
{
  "instanceId": null,
  "nodeId": 3000001,
  "logContent": null,
  "logs": [{"instanceId":5000002,"nodeId":3000001,"nodeName":"dksdk_demo","dagId":4000003,"dagType":null,"status":"SUCCESS","bizdate":"20261025","beginRunningTime":"2026-10-26 01:00:00","finishTime":"2026-10-26 01:00:03","logContent":"hello DataWorks"}]
}
```

任一上游请求失败时抛异常，当前不返回部分成功的日志列表。公司 ListInstances PDF 未列出 Bizdate，但当前 SDK 支持该字段且节点日志逻辑会传入，需在公司版本核验筛选效果。

## 对接顺序与文档差异

通常顺序：创建取得 fileId → 提交取得 deploymentId → 查询提交成功 → 检查后发布或直接发布 → 查询新发布包成功 → 查询节点取得 nodeId → 运行取得 dagIds → 分页查询实例取得 instanceId → 查询日志。

本次仅编写文档，以下差异尚未修改实现或进行公司环境验证：

| 项目 | 当前代码与提供的 PDF 差异 |
| --- | --- |
| 查询运行状态 | PDF 要求 NodeId，当前 queryExecution 不传；PDF 页码上限 100，DTO 上限 1000000。 |
| RegionId | PDF 要求 default；getNodesInfo、日志查询及 deploy 的发布分支仍未在请求对象中显式设置，其他已补充方法见调用约定。 |
| 运行并行语义 | 公司 PDF 和当前 DTO 注释对 parallelism 描述不同，应按部署版本确认。 |
| 运行返回示例 | RunCycleDagNodes PDF 字段表标为 List，但示例写成标量 111；当前 SDK 类型及封装均为 List<Long>。 |
| 请求追踪 ID | RunCycleDagNodes PDF 将 RequestId 描述为区域 ID，疑为文档笔误；当前封装作为请求追踪 ID 返回。 |
| 创建任务 | PDF 中路径、代码、InputList 可选；当前 DTO 强制路径/代码，InputList 在此前公司环境报过必填错误。 |
| 检查状态 | checkingStatus 为 SDK 字段，提供的 GetDeployment PDF 未定义其枚举；文档按现有代码行为说明。 |
| 节点查询 | 当前固定第一页 10 条，未暴露分页；PDF 的 Nodes JSON 示例写成对象，字段表和 SDK 类型均为列表。 |

## 资料依据

代码依据：当前 `DataWorksService.java`、`dto/request`、`dto/response`、`DwInstanceLogDetail.java`，以及本地 4.2.5 SDK 的字段类型。

所提供 PDF 均为企业版 `enterprise_v318_6_ST`，每份 1 页。字段语义依据以下本地资料；原文中的示例不一致处已在上文注明。

| PDF | 对应章节 |
| --- | --- |
| [createFile.pdf](C:/Users/63059/Desktop/dataworkssdkAPI/新建文件夹/createFile.pdf) | 创建任务 |
| [submitFile.pdf](C:/Users/63059/Desktop/dataworkssdkAPI/新建文件夹/submitFile.pdf) | 提交任务 |
| [ListNodes.pdf](C:/Users/63059/Desktop/dataworkssdkAPI/新建文件夹/ListNodes.pdf) | 获取节点信息 |
| [GetDeployment.pdf](C:/Users/63059/Desktop/dataworkssdkAPI/新建文件夹/GetDeployment.pdf) | 提交/发布状态 |
| [deployFile.pdf](C:/Users/63059/Desktop/dataworkssdkAPI/新建文件夹/deployFile.pdf) | 检查后发布、直接发布 |
| [RunCycleDagNodes.pdf](C:/Users/63059/Desktop/dataworkssdkAPI/新建文件夹/RunCycleDagNodes.pdf) | 任务运行 |
| [ListInstances.pdf](C:/Users/63059/Desktop/dataworkssdkAPI/新建文件夹/ListInstances.pdf) | 运行状态、节点日志 |
| [GetInstanceLog.pdf](C:/Users/63059/Desktop/dataworkssdkAPI/新建文件夹/GetInstanceLog.pdf) | 运行日志 |
