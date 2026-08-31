# 爱心积分服务

这是一个只依赖 JDK 11 的 HTTP 后端服务。数据仅保存在进程内存中；服务重启后清空。服务支持并发请求，使用有界线程池避免请求洪峰导致线程和队列无限增长。

## 构建与测试

在 PowerShell 中运行：

```powershell
./build.ps1
```

脚本会编译代码、生成 `donation-service.jar` 并执行接口及并发测试。

项目同时提供标准 JUnit 5 测试，可在 IDEA 中打开
`DonationServerJUnitTest.java`，点击类或各个 `@Test` 方法旁的绿色按钮运行；也可执行：

```powershell
mvn test
```

## 启动

```powershell
java -jar donation-service.jar
# 或指定端口
java -jar donation-service.jar 9000
```

也可以运行 `./run.ps1`，默认监听 `8001` 端口。

## 接口

### 获取患者会话

```http
GET /{patientId}/session
```

返回仅含小写字母和数字的会话密钥。同一患者在密钥创建后的 10 分钟内重复请求将得到相同密钥。

### 捐赠积分

```http
POST /{departmentId}/donate?sessionkey={sessionKey}
Content-Type: text/plain

50
```

有效会话成功写入时返回 `204 No Content`；无效或过期会话返回 `403 Forbidden`。每次请求都会作为独立记录永久保存在当前进程内存中；查询榜单时，同一患者、同一科室只展示单次捐赠的最高积分。

### 查询科室榜单

```http
GET /{departmentId}/topdonors
```

按积分降序返回最多 20 位患者，格式示例：

```text
1234=50,5678=30
```

积分相同时按患者 ID 升序排列，以保证结果稳定。科室无记录时返回空字符串。

## 核心设计思路及解决方案

### 1. 总体设计目标

本服务需要同时满足以下约束：

- 只能使用 JDK 原生 HTTP 能力，生产代码不能依赖第三方 Web 框架。
- 数据不写数据库和磁盘，只在当前 JVM 进程内保存。
- 支持大量并发请求，并限制线程、排队请求和单次请求体的资源占用。
- 会话有效期为固定 10 分钟；会话过期不能删除患者已经提交的捐赠数据。
- 每次捐赠必须独立保存，但榜单中同一患者只能出现一次，并展示单次最高积分。

解决方案采用 JDK 11 自带的 `com.sun.net.httpserver.HttpServer`，配合并发容器、不可变领域对象和有界线程池实现。JUnit 仅用于测试，不会进入生产 JAR 的运行时依赖。

### 2. 分层和类职责

```text
DonationServer
    │ 创建服务、线程池和依赖
    ▼
DonationHttpHandler
    │ 路由请求并调用业务组件
    ├── RequestParser / Route / HttpResponseWriter
    │       HTTP 参数校验、路径解析、响应输出
    ├── SessionManager
    │       会话创建、复用、校验、过期与清理
    └── DonationStore
            独立捐赠记录存储
            └── DonationRanking
                    最高积分归并、排序和 TOP20 输出
```

主要源码结构：

```text
src/main/java/com/example/donation/
├── DonationServer.java                 应用入口和生命周期
├── http/
│   ├── DonationHttpHandler.java        三个接口的 HTTP 入口
│   ├── Route.java                      /{id}/{action} 路径解析
│   ├── RequestParser.java              查询参数和请求体校验
│   └── HttpResponseWriter.java         状态码、响应头和正文输出
├── session/
│   ├── Session.java                    不可变会话对象
│   ├── SessionKeyGenerator.java        安全随机密钥生成
│   └── SessionManager.java             并发会话管理
└── donation/
    ├── Donation.java                   一次独立捐赠记录
    ├── DonationStore.java              按科室分片的内存仓库
    └── DonationRanking.java            TOP20 榜单计算
```

### 3. 三个接口的处理流程

#### 获取会话

1. 解析路径中的患者 ID，并拒绝负数或非整数 ID。
2. 在患者索引中原子查询会话。
3. 当前会话未过期时直接返回原密钥，不延长有效期。
4. 会话不存在或已过期时，生成新的 24 位小写字母数字密钥。
5. 返回 `200` 和纯文本密钥。

#### 提交捐赠

1. 解析科室 ID、`sessionkey` 查询参数和纯文本积分请求体。
2. 根据会话密钥反查患者；密钥不存在或过期时返回 `403`，不写入任何数据。
3. 将本次捐赠作为新的不可变记录追加到对应科室的并发队列。
4. 返回 `204 No Content`。该状态表示成功且接口没有响应体，并非网络错误。

#### 查询榜单

1. 取得指定科室的全部独立捐赠记录。
2. 使用 `Map<patientId, maxPoints>` 按患者归并单次最高积分。
3. 按积分降序排列；积分相同时按患者 ID 升序排列，保证结果稳定。
4. 截取前 20 项并格式化为 `患者ID=积分,患者ID=积分`。
5. 科室没有数据时返回 `200` 和空字符串。

### 4. 会话并发安全与过期策略

`SessionManager` 维护两个 `ConcurrentHashMap`：

- `byPatient`：患者 ID → 会话，保证同一患者在有效期内复用同一个密钥。
- `byKey`：会话密钥 → 会话，使捐赠接口可以快速反查患者。

同一患者并发获取会话时，通过 `byPatient.compute(...)` 对该患者键执行原子操作，避免生成多个有效会话。新密钥写入反向索引时使用 `putIfAbsent`；即使发生概率极低的随机密钥碰撞，也会重新生成而不会覆盖其他患者。

会话有效期从创建时刻固定计算 10 分钟，重复获取不会续期。过期会话采用两级清理：

- 访问到过期会话时立即从两个索引中条件删除。
- 每 1024 次会话操作扫描一次过期条目，避免每次请求全表扫描。

捐赠记录和会话分开存储，因此清理过期会话不会影响任何历史捐赠数据。

### 5. 捐赠存储和榜单算法

`DonationStore` 使用以下结构：

```text
ConcurrentHashMap<departmentId, ConcurrentLinkedQueue<Donation>>
```

- 外层并发映射按科室分片，不同科室的提交互不阻塞。
- 每个科室使用无锁并发队列追加记录，适合高频写入。
- 每次提交都创建独立 `Donation` 对象，不覆盖、不累加以前的记录。
- 写入时不维护有序榜单，避免所有提交请求竞争同一个排序结构。
- 查询时只处理目标科室的数据，时间复杂度约为 `O(n + u log u)`：`n` 为该科室捐赠记录数，`u` 为不同患者数。

按照题目要求，捐赠数据在进程存活期间永久保留，因此记录数量会随请求增加。这是需求本身的内存取舍；服务重启后全部内存数据清空。

### 6. 高并发和资源控制

- 工作线程数固定在 `4～32` 之间，根据 CPU 核心数计算。
- 等待队列最多保存 1024 个任务，防止请求无限堆积。
- 队列满时使用 `CallerRunsPolicy`，让提交请求的线程执行任务并形成自然背压。
- HTTP backlog 设置为 128，限制操作系统等待接受的连接规模。
- 捐赠请求体最多读取 64 字节，避免超大请求消耗堆内存。
- `Session` 和 `Donation` 均为不可变对象，可安全地在请求线程之间共享。
- 响应显式使用 UTF-8，并设置 `Cache-Control: no-store`，避免会话密钥被缓存。

### 7. 输入校验和错误隔离

- 路径必须严格符合 `/{id}/{action}`。
- ID 必须为非负整数。
- 捐赠接口只允许一个名为 `sessionkey` 的查询参数。
- 积分请求体必须是 Java `int` 范围内的整数。
- 可预期的输入错误通过 `BadRequestException` 统一映射为 `400`。
- 未预期异常只记录在服务端，不向客户端暴露堆栈和内部信息，并返回 `500`。

### 8. 测试方案

项目提供两套测试入口：

- `DonationServerTest`：纯 JDK 离线测试，由 `build.ps1` 执行，不需要下载测试依赖。
- `DonationServerJUnitTest`：标准 JUnit 5 测试，可在 IDEA 中逐个运行测试方法，也可执行 `mvn test`。

测试会启动随机本地端口的真实 HTTP 服务，通过 `HttpClient` 发出请求，覆盖：

- 会话格式和十分钟内复用。
- 无效会话拦截。
- 多次捐赠取单次最高积分。
- 榜单降序和 TOP20 限制。
- 100 个并发捐赠请求的数据安全。
- 非法 ID、非法积分和未知接口状态码。
- 最终可执行 JAR 的端到端调用。

## 状态码约定

- `200`：获取会话或查询榜单成功
- `204`：捐赠成功（无响应体）
- `400`：路径、查询参数或积分格式错误
- `403`：会话密钥无效或过期
- `404`：接口不存在
