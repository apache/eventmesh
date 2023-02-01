
# 如何成为EventMesh的贡献者(Contributor)

如果您想向 eventmesh 社区提交代码，成为的新贡献者(Contributor)，请阅读本文档.

本文档描述了如何向社区提交代码的一般流程。如果您在文档中发现任何问题，欢迎留下评论或建议。

## 准备工作

### 开发环境

- 您应该在您的开发环境中安装JDK，建议 JDK 8 及以上版本。
- 您应该在您的开发环境中安装Gradle，建议 Gradle 7 及以上版本。

### 代码风格

将[EventMesh CheckStyle](https://github.com/apache/incubator-eventmesh/blob/master/style/checkStyle.xml) 文件导入到您的IDEA。

对于IDEA，您可以通过以下方式导入检查样式文件:
```shell
    Editor -> Code Style -> Java -> Scheme -> Import Scheme -> CheckStyle Configuration
```

如果您在导入方案下看不到CheckStyle配置部分，您可以先安装CheckStyle-IDEA插件，就可以看到它。

您也可以使用`./gradlew check`来检查代码风格。
(注意：这个命令将检查项目中的所有文件，当您提交一个项目时，CI将只检查这个项目中被修改的文件）。

CheckStyle-IDEA插件导入演示：

a.如图安装CheckStyle-IDEA插件

![CheckStyle01](/images/contribute/checkstyle01.png)

b.下载完成后需要导入EventMesh的检查样式文件，如下步骤：

1.如图：

![CheckStyle02](/images/contribute/checkstyle02.png)

2.当点击+号之后会出现以下界面

![CheckStyle03](/images/contribute/checkstyle03.png)

标号1的地方简单取个名字即可，标号2的地方是EventMesh CheckStyle文件的位置，在项目的style文件夹下。

3.点击finish之后就会出现下图界面

![CheckStyle04](/images/contribute/checkstyle04.png)

将添加的样式检查文件勾选上并Apply

c.当完成以上步骤之后就可以在您完成代码编写后进行样式检查了，步骤如下：

1.选择添加的EM-checks文件。

![CheckStyle05](/images/contribute/checkstyle05.png)

2.点击这两个地方都可检查文件样式

![CheckStyle06](/images/contribute/checkstyle06.png)

这两个标号都只检测当前页的文件样式，标号2在当前页鼠标右键即可出现。

### 工作流程

以下是贡献者提交代码的工作流程:

0. 在提交PR之前需要先创建一个ISSUE，它是用来表达你的想法的，比如你发现了代码中的bug，想修复这个bug，那么需要告知社区管理者或贡献者你的想法，就要创建一个ISSUE用于交流讨论。在EventMesh社区有ISSUE模板，可根据您提出的ISSUE中讨论的内容使用相应的模板。

ISSUE创建示例:
   a.进入到项目仓库的主页，切换到issue标签，点击New issue
   ![contributor01](/images/contribute/zh/contribute14.png)

   b.就会出现下图界面
   ![contributor01](/images/contribute/zh/contribute15.png)
   根据你的需要选择不同的ISSUE模板，如果你觉得没有模板合适，你可以选择打开一个空白模板。
   当前EventMesh提供了六种ISSUE模板
   Bug report: 发现代码bug，请使用此模板
   Documentation Related: 文档相关的，请使用此模板
   Enhancement Request: 代码优化，增强代码实现的，请使用此模板
   Feature Request: 为EventMesh提供新的特性功能的使用此模板
   Question: 对于EventMesh有疑问的，或者有什么不明白的想要提问的，请使用此模板
   Unit Test: 想为EventMesh做一些单元测试的，请使用此模板

   c.根据模板的提示，完成issue内容填写。主要是描述清楚问题所在和解决方案就可以了

   [ISSUE实例](https://github.com/apache/incubator-eventmesh/issues/2148)
   如图：
   ![contributor01](/images/contribute/zh/contribute16.png)

ISSUE与PR关联：
   后续提交PR的时候，PR的标题和内容应该跟ISSUE完成关联，这样才符合开源的规范，如下图所示

   ![contributor01](/images/contribute/zh/contribute17.png)

   上图ISSUE编号为2148，那么你的PR标题前就是 [ISSUE #2148]，这样就关联上了。


1. 在issue创建完成后，将源仓库的项目eventmesh fork到自己仓库当中
例：
   a.点击eventmesh右上角的fork
   ![contributor01](/images/contribute/zh/contribute01.png)
   b.就会出现下图，点击Create fork即可将eventmesh fork到自己的仓库
   ![contributor02](/images/contribute/zh/contribute02.png)


2. fork完成后，克隆自己仓库的代码到本地
```git
git clone git@github.com:yourgithub/incubator-eventmesh.git
```

3. 创建一个新的分支进行工作
```git
git checkout -b fix_patch_xx
```

4. 让您的分支保持同步
```git
git remote add upstream git@github.com:apache/incubator-eventmesh.git
git fetch upstream master:upstream_master
git rebase upstream_master
```

5. 提交您的修改（确保您的提交信息简洁明了）

**特别注意：在提交代码之前要确保你提交代码的邮箱要与GitHub邮箱一致，不然在统计contributor数量时会发生无法正常计入的情况**

- 在IDEA中打开Terminal(终端)，输入以下命令查看本地使用的邮箱信息
```git
git config --global --list
```
如图：
![contributor12](/images/contribute/zh/contribute12.png)
如果你在提交之前还未登陆邮箱，使用注册GitHub账号时的邮箱

- 如果你发现你的邮箱与GitHub邮箱不一致可通过以下git命令进行修改
```git
git config --global user.name 你的目标用户名
git config --global user.email 你的目标邮箱名
```
如图：
![contributor13](/images/contribute/zh/contribute13.png)
修改邮箱同理(也可以用这个命令来登陆邮箱)

例：用IDEA提交代码为例，如果您在IDEA本地修改完毕
   a.点击Commit，点击图片中两个地方中任意一个即可
   ![contributor03](/images/contribute/zh/contribute03.png)
   b.就会出现以下图中界面
   ![contributor04](/images/contribute/zh/contribute04.png)
   （注：如果是新添加的文件需要Add一下，再Commit，一般IDEA都会提示，如果没有提示，按下图操作即可，文件由红色变为绿色Add成功）
   ![contributor09](/images/contribute/zh/contribute09.png)

6. 将您的提交推送到自己的fork仓库
   a.需要push到远程仓库中，注意是您自己的仓库，您需要点击
   ![contributor05](/images/contribute/zh/contribute05.png)
   或者是
   ![contributor06](/images/contribute/zh/contribute06.png)
   b.就会出现以下界面，确认无误后点击右下角push，就push到自己的仓库了
   ![contributor07](/images/contribute/zh/contribute07.png)
   c.当您成功push到自己仓库就会出现下图的提示（在IDEA界面的右下角）
   ![contributor08](/images/contribute/zh/contribute08.png)

7. 创建一个Pull Request (PR)
例：
   a.当您成功push到自己的仓库当中后，进入自己的仓库主页，会出现下图界面
   ![contributor10](/images/contribute/zh/contribute10.png)
   b.点击Compare & pull request，就会出现以下界面，按照图中操作即可创建pull request
   ![contributor11](/images/contribute/zh/contribute11.png)


## 解释

原始仓库：https://github.com/apache/incubator-eventmesh Eventmesh的apache仓库在文中被称为原始仓库。

fork库: 从 https://github.com/apache/eventmesh fork到你自己的个人仓库，成为一个fork库。

因此，首先需要将原EventMesh仓库fork到你自己的仓库中。

## 开发分支

**EventMesh 当前的开发分支是 master。请向该分支提交 PR。**

- 我们建议您在本地master分支的基础上创建一个新的开发分支，在该开发分支上提交代码，并基于该分支向原始仓库的master分支提交PR。

## 贡献类别

### 错误反馈或错误修复

- 无论是bug反馈还是修复，都需要先创建一个ISSUE，对bug进行详细描述，这样社区就可以通过问题记录轻松找到并查看问题和代码。bug反馈问题通常需要包含完整的bug信息描述和可重现的场景。

### 功能的实现，重构

- 如果你计划实现一个新功能（或重构），一定要通过 ISSUE 或其他方式与 eventmesh 核心开发团队沟通，并在沟通过程中详细描述新功能（或重构）、机制和场景。

### 文档改进

- 您可以在 [eventmesh-docs](https://github.com/apache/incubator-eventmesh/tree/master/docs) 找到 eventmesh 文档。文档的补充或改进对 eventmesh 是必不可少的。

## 贡献方式

新的贡献者有两种方式来为 eventmesh 社区作出贡献:

- 如果您在 eventmesh 代码中发现了一个你想修复的 bug，或者你为 eventmesh 提供了一个新功能，请在 eventmesh 中提交一个Issue，并向 eventmesh 提交一份 PR。

- eventmesh 社区有其他贡献者提出的ISSUE，这里是社区整理的 [`issue for first-time contributors`](https://github.com/apache/incubator-eventmesh/issues/888) 是相对简单的 PR，可以帮助你熟悉向 eventmesh 社区做贡献的过程。

## 提交ISSUE指南

- 如果您不知道如何在 eventmesh 上提Issue，请阅读前文，或阅读 [about the issue](https://docs.github.com/cn/issues/tracking-your-work-with-issues/quickstart) 。

- 在 eventmesh 社区，有Issue模板可以参考，如果类型匹配请使用该模板，如果Issue模板不符合你的要求，你可以开一个空的Issue模板，对于问题请带上其匹配的功能标签。

- 对于Issue的名称，请用一句话简要描述你的Issue或目的，为了更好的全球交流，请用英文书写。

##  Pull Request（PR）提交指南

- 如果你不知道如何为 eventmesh 发起一个 PR，请阅读前文，或参见 [about pull request](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request) 。

- 无论是 bug 修复，还是新功能开发（如果这个 pr 是新功能开发，那么关于新功能的文档更新应该包括在这个 pr 中），请向当前开发分支 master 提交 PR。

- 提交的pr应该遵循eventmesh提供的模板以及需要写的提交信息，简单描述你提交的pr是做什么的就可以了，请阅读 [模板详情](https://github.com/apache/incubator-eventmesh/blob/master/.github/PULL_REQUEST_TEMPLATE.md) 。

- 你提交的PR需要与你要修复的问题，或你要提出的问题相关联，所以你的PR标题应该以[ISSUE #xx]开头。

- 如果你的修改是关于一个错别字或小的优化，你不需要创建一个问题，只要提交一个PR和标题为[MINOR]。

**注意：**

- 一个拉动请求不应该太大。如果需要大量的修改，最好将这些修改分成几个单独的 PR。

- 在创建PR后，一个或多个提交人将帮助审查该拉动请求，在批准后，该PR将被合并到eventmesh主源库中，相关的Issue将被关闭。

## 审查

### PR审查

所有的代码都应该由一个或多个提交者进行良好的审查。一些原则。

- 可读性。重要的代码应该有良好的文档。遵守我们的 [代码风格](https://github.com/apache/incubator-eventmesh/blob/master/style/checkStyle.xml) 。

- 优雅性。新的函数、类或组件应该是精心设计的。

- 可测试性。重要的代码应该经过良好的测试（高单元测试覆盖率）。

### 许可证审查

EventMesh遵循 [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) 政策。所有源文件应
在文件头中添加Apache许可证头。EventMesh 使用 [apache/skywalking-eyes](https://github.com/apache/skywalking-eyes) 来检查
源文件的头。

### PR合并

当一个PR被至少一个提交者批准后，它就可以被合并了。合并前，提交者可以对提交信息进行修改，要求提交的
消息要清楚，不能有重复，并使用 Squash 和 Merge 来确保一个 PR 只应包含一个提交。
对于大型的多人PR，使用Merge合并，在合并前通过rebase修复提交。

## 社区

### 联系我们

Mail: dev@eventmesh.apache.org
