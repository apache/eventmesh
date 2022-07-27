# 贡献给EventMesh

欢迎使用EventMesh! 本文档是有关如何为EventMesh做出贡献的指南。 如果发现不正确或缺失的内容，请留下评论/建议。

## 开始之前

### 设置您的开发环境

您应该在开发环境中安装了JDK。

### Code Style

将 [EventMesh CheckStyle](./style/checkStyle.xml) 文件导入开发者工具。如果你使用IDEA，你可以通过以下步骤导入：
```shell
Editor -> Code Style -> Java -> Scheme -> Import Scheme -> CheckStyle Configuration
```
如果你在Import Scheme下看不到CheckStyle Configuration选项，你可以先安装CheckStyle-IDEA插件，然后你就可以看到这个选项了。

你也可以通过执行`./gradlew check`来检查代码格式。(NOTE: 这个命令将会检查整个项目中的代码格式， 当你提交一个PR时，CI只会检查在此次PR中被被修改的文件的代码格式)
## 贡献

无论是对于拼写错误，BUG修复还是重要的新功能，我们总是很乐意接受您的贡献。请不要犹豫，在Github Issue上提出或者通过邮件列表进行讨论。

我们非常重视文档以及与其他项目的集成，我们很高兴接受这些方面的改进。

### GitHub工作流程

我们将`develop`分支用作开发分支，这表明这是一个不稳定的分支。

这是贡献者的工作流程 :

1. Fork到您个人仓库
2. 克隆到本地存储库
```git
git clone git@github.com:yourgithub/incubator-eventmesh.git
```
3. 创建一个新分支并对其进行处理
```git
git checkout -b fix_patch_xx
```   
4. 保持分支与主库同步
```git
git remote add upstream git@github.com:apache/incubator-eventmesh.git
git fetch upstream develop:upstream_develop
git rebase upstream_develop
```   
5. 提交您的更改(确保您的提交消息简明扼要)
6. 将您的提交推送到分叉的存储库
7. 创建PR合并请求

请遵循[Pull Requests模板](./.github/PULL_REQUEST_TEMPLATE.md).
请确保PR对应有相应的问题. [GitHub Issues](https://github.com/apache/incubator-eventmesh/issues)

创建PR后，社区会有committer成员帮助review，review通过之后，PR将会合并到主库，相应的Issue会被关闭。

### 打开问题/ PR

我们将使用Issues和Pull Requests作为跟踪器
- [GitHub Issues](https://github.com/apache/incubator-eventmesh/issues)
- [Pull Requests](https://github.com/apache/incubator-eventmesh/pulls)

如果您发现新的Bug，想要新功能或提出新当建议，您可以在GitHub上[创建Issue](https://github.com/apache/incubator-eventmesh/issues/new) ，请按照Issue模板中的准则进行操作。
如果您在文档中发现拼写错误，或者发现代码中存在可以进行微小的优化的地方，您可以无需创建Issue， 直接提交一个PR。

如果您想贡献，请遵循[贡献工作流程](#github-workflow)并创建一个新的拉取请求。 如果您的PR包含较大的更改，例如组件重构或新组件，请写详细文档 有关其设计和使用的信息。
对于PR的标题请依照[ISSUE #xx]进行开头，如果是细小的改动请以[MINOR]进行开头。

【注意】: 单个PR不应太大。如果需要进行重大更改，最好将更改分开 到一些个人PR。

### PR审查

所有PR应由一个或多个committer进行良好的审查。一些原则:

- 可读性: 重要代码应有详细记录。符合我们的[代码风格](./style/checkStyle.xml)
- 优雅: 新功能，类或组件应经过精心设计
- 可测试性: 重要代码应经过良好测试（较高的单元测试覆盖率）

### License审查

EventMesh遵循[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) 政策。 
所有的源代码文件应该在文件头部添加Apache License header，EventMesh会使用[apache/skywalking-eyes](https://github.com/apache/skywalking-eyes)
对源代码文件头进行校验。

EventMesh使用[check-dependencies.sh](tools/dependency-check/check-dependencies.sh)脚本
检查第三方依赖，当你需要添加三方依赖时，你需要将新添加的依赖注册在tool/license/known-dependencies.txt中，
同时新添加的三方库需要满足[Apache对于第三方的政策](https://apache.org/legal/resolved.html)。

当添加依赖时遇到问题时，社区PPMC会协助解决，非常建议在需要添加三方依赖之前与EventMesh社区进行沟通。

### PR合并

PR经过至少一个committer approve之后会由committer负责合并，在合并的时候，committer可以对commits信息进行修改，要求commits信息简洁明了，不重复。
在合并时使用Squash and merge, 要求一个PR保留一个commits。对于大型多人协助的PR，使用Merge进行合并，在合并之前通过rebase修正commits。

## 社区

### 联系我们

邮件：dev@eventmesh.apache.org
