# dksdk

面向研发组交付的普通 Maven JAR，提供 DataWorks Service 调用能力。Java 8 / Spring Boot 2.1.14.RELEASE / `javax.validation`，公司 DataWorks SDK 为 `dataworks_public_20240902:4.2.5`。

Maven 坐标保持为 `com.sinogale:dksdk:1.0.0-SNAPSHOT`；Java 包为 `com.zdph.dksdk`。Maven groupId 与包名不必相同，本次保留已有坐标。

## 1. 交付方式

这是供宿主应用引用的普通 JAR，无 main 启动类、HTTP 服务或 Boot repackage。没有配置自动加载的 starter；调用方显式导入配置入口。

- 单项能力：`DataWorksService`，包括创建、提交、发布、运行、状态与日志查询。
- 组合能力：`DataWorksFileLifecycleService.createSubmitCheckAndPublish(CreateScriptRequest)`，内部完成创建、提交、轮询提交状态及发布。
- 底层调用：`DataWorksSdkInvoker`。
- 字段与示例：[接口文档](docs/DataWorksService接口文档.md)。

`src/main/resources` 不携带应用配置；POM 也排除 application*.yml、application*.yaml、application*.properties。配置示例放在 [docs/examples/dataworks-example.yml](docs/examples/dataworks-example.yml)，不进入 JAR。

## 2. 调用方依赖

在现有 Spring Boot 2 应用中加入：

```xml
<dependency>
    <groupId>com.sinogale</groupId>
    <artifactId>dksdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

上例假定宿主已通过 parent/BOM 管理 Spring Boot 版本。未管理版本的调用方需自行指定兼容版本，本库基线是 2.1.14.RELEASE。若宿主已有可用的 `javax.validation.Validator` Bean 和相应 Spring Boot 运行依赖，无需重复添加校验 starter。

本库保留 `spring-boot-starter-validation` 的 provided 范围，Spring 容器与校验实现由宿主提供；Lombok 也仅为编译期 provided 依赖。SLF4J 只依赖 API，由宿主提供日志实现。本库自己的 POM 不添加 Logback/Log4j2 日志绑定。

DataWorks SDK、Fastjson2、Hutool、Swagger 注解和 SLF4J API 是当前代码实际使用的依赖，通过 Maven POM 解析。普通 dksdk JAR 不把这些依赖合并进去；只复制一个 dksdk JAR 不会自动解决依赖。

调用方不需要为了使用本库 DTO 安装 Lombok 编译器处理器；getter/setter 已在构建本库时生成。宿主自己使用 Lombok 的代码仍需自行配置。

兼容基线为 Spring Boot 2 和 javax.validation；不能直接按 Spring Boot 3 / jakarta.validation 的方式接入。

## 3. 注册 Bean

在宿主应用已有配置类中导入入口：

```java
import com.zdph.dksdk.config.DataWorksSdkConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(DataWorksSdkConfiguration.class)
public class DataWorksIntegrationConfiguration {
}
```

这会注册 DataWorksProperties 的配置绑定、SDK Client、DataWorksSdkInvoker、DataWorksService 及 DataWorksFileLifecycleService。

宿主须能扫描到自己的 DataWorksIntegrationConfiguration；不需要另外扫描整个 com.zdph.dksdk 包。DataWorksProperties 由入口上的 EnableConfigurationProperties 统一注册，避免同时作为 Component 注册两份。

Client 使用配置中的 Endpoint、RegionId 和凭据。当前 Client 在容器初始化时创建，调用方应在容器启动前提供配置。构造 Client 本身不代表已经验证凭据或网络可用。

## 4. 宿主配置

将以下配置合并到宿主的 application.yml 或配置中心：

```yaml
dataworks:
  endpoint: ${DATAWORKS_ENDPOINT}
  region-id: ${DATAWORKS_REGION_ID:default}
  project-id: ${DATAWORKS_PROJECT_ID}
  access-key-id: ${DATAWORKS_ACCESS_KEY_ID}
  access-key-secret: ${DATAWORKS_ACCESS_KEY_SECRET}
  project-env: ${DATAWORKS_PROJECT_ENV:PROD}
```

由宿主提供实际环境变量或配置值。库中不内置真实环境地址、项目 ID 或凭据，也不再带 server.port、spring.application.name 等宿主配置。

## 5. 调用示例

在宿主业务 Service 中通过构造器注入：

```java
import com.aliyun.dataworks_20240902.models.DeployFileResponse;
import com.zdph.dksdk.dto.request.CreateScriptRequest;
import com.zdph.dksdk.service.DataWorksFileLifecycleService;
import org.springframework.stereotype.Service;

@Service
public class TaskApplicationService {
    private final DataWorksFileLifecycleService lifecycleService;

    public TaskApplicationService(DataWorksFileLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    public DeployFileResponse createAndPublish(CreateScriptRequest request) {
        return lifecycleService.createSubmitCheckAndPublish(request);
    }
}
```

CreateScriptRequest 的文件目录、类型、代码和 inputList 必须对应目标平台实际值。单独调用创建、提交、查询等能力时，注入 DataWorksService。

组合接口最多查询提交状态 10 次、间隔 3 秒，提交成功后发布。返回 DeployFileResponse.body.data 是新生成的发布包 ID，表示发布已受理；最终结果使用 DataWorksService.getDeployment 查询。

组合流程中途失败可能已经创建文件或提交包，当前不执行自动回滚，也没有幂等去重；调用方应结合异常中的 requestId 与平台记录处理，避免直接重试导致重复创建。

## 6. 构建及内网分发

以下为交付时使用的命令示例，本次修改没有执行编译或测试。

在本项目根目录，使用 JDK 8 和准备完整的本地 Maven 仓库：

```powershell
mvn -o "-Dmaven.repo.local=D:\apache-maven-3.5.3\conf\dk-rep" clean package
```

输出为 `target/dksdk-1.0.0-SNAPSHOT.jar`。交付前必须 clean，避免包名迁移或删除启动类后，target/classes 还残留旧 class。本项目无 Boot repackage，主类文件应直接位于 com/zdph/dksdk 下，不应出现 BOOT-INF/classes。

如果使用自定义 settings，内外网需保持相同 settings/仓库 ID、Maven 版本和插件版本，并显式指定正确的本地仓库。离线仓库需要包含普通依赖、其 POM/父 POM/BOM，以及构建插件及插件依赖。不要只复制业务 JAR。

无私服时，交付文件至少包括：

- dksdk 的 JAR 和同版本 pom.xml。
- 本 README、接口文档与配置示例。
- 公司版 DataWorks SDK 的 JAR、POM，以及宿主本地仓库缺少的其他依赖。

先将公司 DataWorks SDK 按原坐标安装到调用方仓库，再安装 dksdk。安装 dksdk 时指定交付 POM，保留依赖信息：

```powershell
mvn -o "-Dmaven.repo.local=D:\apache-maven-3.5.3\conf\dk-rep" install:install-file "-Dfile=target/dksdk-1.0.0-SNAPSHOT.jar" "-DpomFile=pom.xml"
```

该命令需要 install 插件和依赖已缓存。调用方执行时将 -Dfile、-DpomFile 替换为收到的文件路径。不要使用只带 JAR 并自动生成空 POM 的方式安装本库，否则会丢失依赖声明。

公司 SDK 4.2.5 是自备构件，不保证可从公网仓库下载；它包含部分第三方类，若宿主也使用 Tea/阿里云相关库，需要检查重复类和版本兼容。本次没有修改或重打包这个第三方 JAR。

有内网 Maven 私服时，可将公司 SDK 与 dksdk 各自的 JAR/POM 发布到私服，由调用方按坐标引用。正式发布时建议统一改为固定发布版本，当前保留已有 SNAPSHOT 坐标。

## 7. 依赖和验证说明

- 测试类、测试依赖及 Surefire 显式配置在当前交付工程中已移除；测试不是普通 JAR 的必要组成部分。
- 保留 Spring Boot BOM 做依赖版本管理，它不启动应用，也不被当作第三方 class 合并进主 JAR。脱机解析依然需要 BOM 对应 POM 已在本地仓库。
- 未引入 spring-boot-maven-plugin、shade 或 assembly 来生成可执行包/合并包。
- 当前只完成静态检查，尚未编译、打包或做宿主接入验证。
