# 贡献给EventMesh

欢迎使用EventMesh! 本文档是有关如何为EventMesh做出贡献的指南。
如果发现不正确或缺失的内容，请留下评论/建议。

## 开始之前

### 设置您的开发环境

您应该在操作系统中安装了JDK。

## 贡献

无论是对于拼写错误，BUG修复还是重要的新功能，我们总是很乐意为您做出贡献。
请不要犹豫，提出问题或发送请求请求。

我们非常重视文档以及与其他项目的集成。
我们很高兴接受这些方面的改进。

### GitHub工作流程

我们将`develop`分支用作开发分支，这表明这是一个不稳定的分支。

这是贡献者的工作流程 :

1.Fork到您个人仓库
2.克隆到本地存储库
3.创建一个新分支并对其进行处理
4.保持分支同步
5.提交您的更改(确保您的提交消息简明扼要)
6.将您的提交推送到分叉的存储库
7.创建PR合并请求

请遵循[Pull Requests模板](./.github/PULL_REQUEST_TEMPLATE.md).
请确保PR对应有相应的问题. [GitHub Issues](https://github.com/WeBankFinTech/EventMesh/issues)

创建PR后，将为拉取请求分配一个或多个审阅者。
审阅者将审阅代码。

在合并PR之前，请压缩所有修订审阅反馈，拼写错误，合并的内容和基于基础的提交内容。
最终的提交消息应该清晰简洁。

### 打开问题/ PR

我们将使用Issues和Pull Requests作为跟踪器 
[GitHub Issues](https://github.com/WeBankFinTech/EventMesh/issues)
[Pull Requests](https://github.com/WeBankFinTech/EventMesh/pulls)

如果您在文档中发现拼写错误，在代码中发现错误，想要新功能或提出建议，
您可以提出问题[在GitHub上打开问题](https://github.com/WeBankFinTech/EventMesh/issues/new)
请按照问题模板中的准则消息进行操作。

如果您想贡献，请遵循[贡献工作流程](#github-workflow)并创建一个新的拉取请求。
如果您的PR包含较大的更改，例如组件重构或新组件，请写详细文档
有关其设计和使用的信息。

请注意，单个拉取请求不应太大。如果需要进行重大更改，最好将更改分开
到一些个人PR。

### 代码审查

所有代码应由一个或多个提交者进行良好的审查。一些原则:

- 可读性: 重要代码应有详细记录。符合我们的代码风格
- 优雅: 新功能，类或组件应经过精心设计
- 可测试性: 重要代码应经过良好测试（较高的单元测试覆盖率）

## 社区

### 联系我们