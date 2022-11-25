# English 
# Maturity Self Assessment for Apache EventMesh (incubating)

The goals of this maturity model are to describe how Apache projects operate in a concise and high-level way, and to provide a basic framework that projects may choose to use to evaluate themselves.

More details can be found [here](https://community.apache.org/apache-way/apache-project-maturity-model.html).

## Status of this assessment

Mentors and community members are encouraged to contribute to this page and comment on it, the following tables summarize our self-assessment against the **Apache Project Maturity Model**.

- :white_check_mark: means that the related item is in good status.
- :white_large_square: means that the related item need long-term attention.
- :x: means that the related item need to be fixed ASAP.


## Maturity model assessment

**CODE**

| **ID**   | **Description** | **Status** |
| -------- | ----- | ---------- |
| **CD10** | The project produces Open Source software for distribution to the public, at no charge.                                                                                                                                                                         | :white_check_mark:  The project source code is licensed under the `Apache License 2.0`. |
| **CD20** | Anyone can easily discover and access the project's code.                                                                                                                                                                                                    | :white_check_mark:  The [offical website](https://eventmesh.incubator.apache.org/) includes `Github` link which can access GitHub directly. |
| **CD30** | Anyone using standard, widely-available tools, can build the code in a reproducible way.                                                                                                                                                                       | :white_check_mark:   Apache EventMesh provide [how-to-build](https://eventmesh.apache.org/docs/latest/development/eventmesh-compile-and-package) document to tell user how to compile on bare metal. |
| **CD40** | The full history of the project's code is available via a source code control system, in a way that allows anyone to recreate any released version.                                                                                                            | :white_check_mark:  It depends on git, and anyone can view the full history of the project via commit logs. |
| **CD50** | The source code control system establishes the provenance of each line of code in a reliable way, based on strong authentication of the committer. When third parties contribute code, commit messages provide reliable information about the code provenance. | :white_check_mark:  The project uses GitHub and managed by Apache Infra, it ensuring provenance of each line of code to a committer. |

**Licenses and Copyright**

| **ID**   | **Description**                                                                                                                                                                                                                                                                 | **Status** |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| **LC10** | The Apache License, version 2.0, covers the released code.                                                                                                                                                                                                                     | :white_check_mark:  The [LICENSE](https://github.com/apache/incubator-eventmesh/blob/master/LICENSE) is in GitHub repository. And all source files are with APLv2 header, check by `apache-rat:check`. |
| **LC20** | Libraries that are mandatory dependencies of the project's code do not create more restrictions than the Apache License does.                                                                                                                                                   | :white_check_mark:  All [dependencies](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-dist/release-docs/LICENSE) has been checked and non of them create more restrictions than the Apache License does. |
| **LC30** | The libraries mentioned in LC20 are available as Open Source software.                                                                                                                                                                                                          | :white_check_mark:  See [dependencies](https://github.com/apache/incubator-eventmesh/blob/master/eventmesh-dist/release-docs/LICENSE). |
| **LC40** | Committers are bound by an Individual Contributor Agreement (the "Apache iCLA") that defines which code they may commit and how they need to identify code that is not their own. | :white_check_mark:  All committers have iCLAs. |
| **LC50** | The project clearly defines and documents the copyright ownership of everything that the project produces.                                                                                                                                                                              | :white_check_mark:  And all source files are with APLv2 header, check by `apache-rat:check`. |

**Releases**

| **ID**   | **Description**                                                                                                                                                                                                                                                                 | **Status** |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| **RE10** | Releases consist of source code, distributed using standard and open archive formats that are expected to stay readable in the long term.                                                                                                                                       | :white_check_mark:  Source release is distributed via [dist.apache.org](https://dist.apache.org/repos/dist/release/incubator/eventmesh) and linked from [download page](https://linkis.incubator.apache.org/download/main). |
| **RE20** | The project's PMC (Project Management Committee, see CS10) approves each software release in order to make the release an act of the Foundation.                                                                                                                                                                          | :white_check_mark:  All releases have been voted at dev@eventmesh and general@incubator, and have at least 3 PPMC's/IPMC's votes. |
| **RE30** | Releases are signed and/or distributed along with digests that anyone can reliably use to validate the downloaded archives.                                                                                                                                                       | :white_check_mark:  All releases are signed, and the [KEYS](https://downloads.apache.org/incubator/eventmesh/KEYS) is available. |
| **RE40** | The project can distribute convenience binaries alongside source code, but they are not Apache Releases, they are provided with no guarantee. | :white_check_mark:  User can easily build binaries from source code.  Convenience binaries are distributed alongside source code at the same time via <ul><li>[Maven Central Repository](https://mvnrepository.com/artifact/org.apache.eventmesh)</li><li>[dist.apache.org](https://dist.apache.org/repos/dist/release/incubator/linkis/)</li></ul>  |
| **RE50** | The project documents a repeatable release process so that someone new to the project can independently generate the complete set of artifacts required for a release. | :white_check_mark:  We can follow the [release guide](http://linkis.incubator.apache.org/community/release-process.html) to make new Apache eventmesh release. And so far we had 7 different release managers. |

**Quality**

| **ID**   | **Description**                                                                                                                                                                                                                                                                 | **Status** |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| **QU10** | The project is open and honest about the quality of its code. Various levels of quality and maturity for various modules are natural and acceptable as long as they are clearly communicated. | :white_check_mark:  We encourage user to [report issues](https://github.com/apache/incubator-eventmesh/issues) |
| **QU20** | The project puts a very high priority on producing secure software.                                                                                                                                                                                                            | :white_check_mark:  All safety issues will be resolved in priority. |
| **QU30** | The project provides a well-documented, secure and private channel to report security issues, along with a documented way of responding to them. | :white_check_mark:  Website provides a [security page](http://eventmesh.incubator.apache.org/community/security) |
| **QU40** | The project puts a high priority on backwards compatibility and aims to document any incompatible changes and provide tools and documentation to help users transition to new features. | :white_check_mark:  All releases are backwards compatibility. Apache Eventmesh provides [upgrade guide](https://eventmesh.apache.org/docs/latest/upgrade/upgrade-guide) docs  |
| **QU50** | The project strives to respond to documented bug reports in a timely manner. | :white_check_mark:  The project has resolved 1200+ issues and 1700+ pull requests so far, with very prompt response. |

**Community**

| **ID**   | **Description**                                                                                                                                                                                                                                                                 | **Status** |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| **CO10** | The project has a well-known homepage that points to all the information required to operate according to this maturity model. | :white_check_mark:  The [website](https://eventmesh.apache.org/) includes all information user need to run Apache Eventmesh. |
| **CO20** | The community welcomes contributions from anyone who acts in good faith and in a respectful manner, and who adds value to the project. | :white_check_mark:  Apache Eventmesh provides [code submission guide](http://eventmesh.incubator.apache.org/community/how-to-contribute) and welcome all good contributions. |
| **CO30** | Contributions include source code, documentation, constructive bug reports, constructive discussions, marketing and generally anything that adds value to the project. | :white_check_mark:  All good contributions including code and non-code are welcomed. |
| **CO40** | The community strives to be meritocratic and gives more rights and responsibilities to contributors who, over time, add value to the project. | :white_check_mark:  The community has elected 4 new PPMC members and 12 new committers so far. |
| **CO50** | The project documents how contributors can earn more rights such as commit access or decision power, and applies these principles consistently. | :white_check_mark:  With the document [committer guide](http://eventmesh.incubator.apache.org/community/how-to-contribute). |
| **CO60** | The community operates based on consensus of its members (see CS10) who have decision power. Dictators, benevolent or not, are not welcome in Apache projects. | :white_check_mark:  All decisions are made after vote by community members. |
| **CO70** | The project strives to answer user questions in a timely manner. | :white_check_mark:  We use dev@linkis, [github issue](https://github.com/apache/incubator-linkis/issues) and [github discussion](https://github.com/apache/incubator-linkis/discussions) to do this in a timely manner. |

**Consensus**

| **ID**   | **Description**                                                                                                                                                                                                                                                                 | **Status** |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| **CS10** | The project maintains a public list of its contributors who have decision power. The project's PMC (Project Management Committee) consists of those contributors. | :white_check_mark:  See [members](http://linkis.incubator.apache.org/team) with all PPMC members and committers. |
| **CS20** | Decisions require a consensus among PMC members and are documented on the project's main communications channel. The PMC takes community opinions into account, but the PMC has the final word. | :white_check_mark:  All decisions are made by votes on dev@linkis, and with at least 3 PPMC's/IPMC's +1 binding |
| **CS30** | The project uses documented voting rules to build consensus when discussion is not sufficient. | :white_check_mark:  The project uses the standard ASF voting rules. |
| **CS40** |In Apache projects, vetoes are only valid for code commits. The person exercising the veto must justify it with a technical explanation, as per the Apache voting rules defined in CS30. | :white_check_mark:  Apache Eventmesh community has not used the veto power yet except for code commits. |
| **CS50** | All "important" discussions happen asynchronously in written form on the project's main communications channel. Offline, face-to-face or private discussions that affect the project are also documented on that channel. | :white_check_mark:  All important discussions and conclusions are recorded in written form. |

**Independence**

| **ID**   | **Description**                                                                                                                                                                                                                                                                 | **Status** |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| **IN10** | The project is independent from any corporate or organizational influence. | :white_check_mark:  The PPMC members and committer of Apache Linkis are from 10+ companies, and majority of them are NOT From the company that donated this project. |
| **IN20** | Contributors act as themselves, not as representatives of a corporation or organization. | :white_check_mark:  The contributors act on their own initiative without representing a corporation or organization. |

----------------

# 中文

# Apache Linkis 的成熟度自我评估（孵化中）

这个成熟度模型的目标是描述 Apache 项目如何以简洁和高级的方式运行，并提供一个项目可以选择用来评估自己的基本框架。

可以在 [这里](https://community.apache.org/apache-way/apache-project-maturity-model.html) 找到更多详细信息。

## 本次评估的状态

鼓励导师和社区成员对此页面做出贡献并发表评论，下表总结了我们对 **Apache 项目成熟度模型**的自我评估。

- :white_check_mark: 表示相关项目状态良好。
- :white_large_square: 表示相关项目需要长期关注。
- :x: 表示相关项目需要尽快修复。

**代码**

|  **ID** | **描述** | **状态** |
| -------- | ----- | ---------- |
| **CD10** |该项目免费生产开源软件以向公众分发。 | :white_check_mark:  项目源代码在 `Apache License 2.0` 下获得许可。 |
| **CD20** |任何人都可以轻松发现和访问该项目的代码.. | :white_check_mark:  [官方网站](https://linkis.incubator.apache.org/) 包含`Github`图标链接，可以直接访问GitHub。 |
| **CD30** |任何使用标准的、广泛可用的工具的人都可以以可重现的方式构建代码。 | :white_check_mark:  Apache eventmesh 提供如何构建文档来告诉用户如何在裸机上编译。 |
| **CD40** |项目代码的完整历史可通过源代码控制系统获得，任何人都可以重新创建任何已发布的版本。 | :white_check_mark:  它依赖于 git，任何人都可以通过提交日志查看项目的完整历史。 |
| **CD50** |源代码控制系统基于提交者的强身份验证，以可靠的方式确定每一行代码的出处。当第三方贡献代码时，提交消息会提供有关代码来源的可靠信息。 |  :white_check_mark:  |

**许可和版权**

|  **ID** | **描述** | **状态** |
| -------- | ----- | ---------- |
| **LC10** | Apache 许可证 2.0 版涵盖了已发布的代码。 | :white_check_mark:  [许可](https://github.com/apache/incubator-linkis/blob/master/LICENSE) 位于 GitHub 存储库中。并且所有源文件都带有 APLv2 头文件，请通过 `apache-rat:check` 检查。 |
| **LC20** | 作为项目代码的强制依赖项的库不会产生比 Apache 许可证更多的限制。 | :white_check_mark: 所有 [依赖项](https://github.com/apache/incubator-linkis/blob/master/linkis-dist/release-docs/LICENSE) 都已检查，并且没有一个比 Apache 许可证产生更多限制 做。 |
| **LC30** | LC20 中提到的库可作为开源软件使用。 | :white_check_mark: 请参阅 [依赖项](https://github.com/apache/incubator-linkis/blob/master/linkis-dist/release-docs/LICENSE)。 |
| **LC40** |提交者受个人贡献者协议（“Apache iCLA”）的约束，该协议定义了他们可以提交的代码以及他们需要如何识别不合法的代码继承人。 | :white_check_mark:  所有提交者都有 iCLA。 |
| **LC50** |该项目清楚地定义并记录了项目产生的所有内容的版权所有权。 | :white_check_mark:  所有源文件都带有 APLv2 标头，请通过 `apache-rat:check` 检查。 |

**发布**

|  **ID** | **描述** | **状态** |
| -------- | ----- | ---------- |
| **RE10** |版本由源代码组成，使用标准和开放的存档格式分发，预计将长期保持可读性。 | :white_check_mark:  源版本通过 [dist.apache.org](https://dist.apache.org/repos/dist/release/incubator/linkis/) 分发，并从 [下载页面](http://linkis.incubator.apache.org/download/main)。 |
| **RE20** |项目的 PMC（项目管理委员会，参见 CS10）批准每个软件发布，以使发布成为基金会的行为。 | :white_check_mark: 所有版本均已在 dev@linkis 和 general@incubator 投票，并且至少有 3 个 PPMC/IPMC +1  binding 投票。 |
| **RE30** |版本与摘要一起签名和/或分发，任何人都可以可靠地使用它来验证下载的档案。 | :white_check_mark: 所有版本都已签名，并且 [KEYS](https://downloads.apache.org/incubator/linkis/KEYS) 可用。 |
| **RE40** |该项目可以与源代码一起分发便利二进制文件，但它们不是 Apache 版本，它们不提供任何保证。 | :white_check_mark:  用户可以轻松地从源代码构建二进制文件。而且我们不提供二进制文件作为 Apache 版本。 |
| **RE50** |该项目记录了一个可重复的发布过程，以便项目的新手可以独立生成发布所需的完整工件集。 | :white_check_mark: 我们可以按照 [发布指南](https://linkis.apache.org/community/how-to-release) 来制作新的 Apache eventmesh 版本。到目前为止，我们有 7 个不同的发布经理。 |

**质量**

|  **ID** | **描述** | **状态** |
| -------- | ----- | ---------- |
| **QU10** |该项目对其代码的质量是开放和诚实的。各种模块的各种质量和成熟度级别是自然的和可接受的，只要它们被清楚地传达。 | :white_check_mark:  我们鼓励用户[报告问题](https://github.com/apache/incubator-linkis/issues) |
| **QU20** |该项目高度重视生产安全软件。 | :white_check_mark:  所有安全问题将优先解决。 |
| **QU30** |该项目提供了一个有据可查、安全且私密的渠道来报告安全问题，以及有记录的响应方式。 | :white_check_mark:  网站提供 [安全上报](https://linkis.incubator.apache.org/community/security) |
| **QU40** |该项目高度重视向后兼容性，旨在记录任何不兼容的更改，并提供工具和文档来帮助用户过渡到新功能。 | :white_check_mark:  所有版本都向后兼容,并提供了[升级指引文档](http://linkis.incubator.apache.org/docs/latest/upgrade/upgrade-guide)  |
| **QU50** |该项目力求及时响应记录在案的错误报告。 | :white_check_mark:   到目前为止，该项目已经解决了 1200 多个问题和 1700 多个拉取请求，并且响应非常迅速。 |



**社区**

|  **ID** | **描述** | **状态** |
| -------- | ----- | ---------- |
| **CO10** |该项目有一个主页，其中指向了根据此成熟度模型进行操作所需的所有信息。 | :white_check_mark:  [网站](https://linkis.apache.org/) 包含用户运行 Apache eventmesh 所需的所有信息。 |
| **CO20** |社区欢迎任何以诚信和尊重的方式行事并为项目增加价值的人的贡献。 | :white_check_mark:  Apache eventmesh 提供 [代码提交指南](http://linkis.incubator.apache.org/community/pull-request.html) 并欢迎所有好的贡献。 |
| **CO30** |贡献包括源代码、文档、建设性的错误报告、建设性的讨论、营销以及通常为项目增加价值的任何东西。 | :white_check_mark: 欢迎所有好的贡献，包括代码和非代码。 |
| **CO40** |社区努力做到任人唯贤，并为随着时间的推移为项目增加价值的贡献者赋予更多的权利和责任。 | :white_check_mark:  到目前为止，社区已经选出了 12 名新的提交者。 |
| **CO50** |该项目记录了贡献者如何获得更多权利，例如提交访问权或决策权，并始终如一地应用这些原则。 | :white_check_mark:  附上文档 [committer guide](https://linkis.apache.org/community/how-to-contribute)。 |
| **CO60** |社区的运作基于拥有决策权的成员（参见 CS10）的共识。 Apache 项目不欢迎独裁者，无论仁慈与否。 | :white_check_mark: 所有决定都是在社区成员投票后做出的。 |
| **CO70** |该项目力求及时回答用户的问题。 | :white_check_mark:  我们使用 dev@linkis, [Github issue](https://github.com/apache/incubator-linkis/issues) 和 [Github Discussion](https://github.com/apache/incubator-linkis/discussions) 及时完成此操作。 |

**共识**

|  **ID** | **描述** | **状态** |
| -------- | ----- | ---------- |
| **CS10** |该项目维护其具有决策权的贡献者的公开列表。项目的 PMC（项目管理委员会）由这些贡献者组成。 | :white_check_mark:  请参阅所有 PPMC 成员和提交者的 [members](https://linkis.apache.org/team)。 |
| **CS20** |决策需要 PMC 成员之间达成共识，并记录在项目的主要沟通渠道上。 PMC 会考虑社区意见，但 PMC 拥有最终决定权。 | :white_check_mark: 所有决定都是通过 dev@linkis 投票做出的，并且至少有 3 个 PPMC/IPMC 的 +1 绑定。 |
| **CS30** |当讨论不足时，该项目使用记录在案的投票规则来建立共识。 | :white_check_mark:  该项目使用标准的 ASF 投票规则。 |
| **CS40** |在 Apache 项目中，否决仅对代码提交有效。根据 CS30 中定义的 Apache 投票规则，行使否决权的人必须通过技术解释来证明其合理性。 | :white_check_mark:  除了代码提交之外，Apache Linkis 社区尚未使用否决权。 |
| **CS50** |所有“重要”的讨论都是在项目的主要沟通渠道上以书面形式异步进行的。影响项目的线下、面对面或私人讨论也记录在该频道上。 | :white_check_mark:  所有重要的讨论和结论都以书面形式记录下来。 |

**独立**

|  **ID** | **描述** | **状态** |
| -------- | ----- | ---------- |
|**IN10**|项目独立于任何公司或组织影响。|:white_check_mark:  Apache eventmesh的PPMC成员和提交人来自家 15 以上的公司|
|**IN20**|贡献者以自己的身份行事，而不是作为公司或组织的代表。|:white_check_mark:  贡献者自行行动，不代表公司或组织|

