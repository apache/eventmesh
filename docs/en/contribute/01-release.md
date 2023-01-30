# Release Creation Process

:::caution
The documentation of Release Creation Process is WIP (Work-in-Progress).
:::

## 理解 Apache 发布的内容和流程

Source Release 是 Apache 关注的重点，也是发布的必须内容；而 Binary Release 是可选项，

请参考以下链接，找到更多关于 ASF 的发布指南:

- [Apache Release Guide](http://www.apache.org/dev/release-publishing)
- [Apache Release Policy](http://www.apache.org/dev/release.html)
- [Maven Release Info](http://www.apache.org/dev/publishing-maven-artifacts.html)


## 本地构建环境准备

主要包括签名工具、Maven 仓库认证相关准备

### 1.安装GPG

在[GnuPG官网](https://www.gnupg.org/download/index.html)下载安装包。GnuPG的1.x版本和2.x版本的命令有细微差别，下列说明以**GnuPG-2.x**版本为例

```sh
$ gpg --version #检查版本，应该为2.x
```

### 2.用gpg生成key

根据提示，生成 key

> 注意：请使用Apache邮箱生成GPG的Key

```shell
$ gpg --full-gen-key
gpg (GnuPG) 2.0.12; Copyright (C) 2009 Free Software Foundation, Inc.
This is free software: you are free to change and redistribute it.
There is NO WARRANTY, to the extent permitted by law.

Please select what kind of key you want:
  (1) RSA and RSA (default)
  (2) DSA and Elgamal
  (3) DSA (sign only)
  (4) RSA (sign only)
Your selection? 1
RSA keys may be between 1024 and 4096 bits long.
What keysize do you want? (2048) 4096
Requested keysize is 4096 bits
Please specify how long the key should be valid.
        0 = key does not expire
     <n>  = key expires in n days
     <n>w = key expires in n weeks
     <n>m = key expires in n months
     <n>y = key expires in n years
Key is valid for? (0)
Key does not expire at all
Is this correct? (y/N) y

GnuPG needs to construct a user ID to identify your key.

Real name: ${输入用户名}
Email address: ${邮箱地址}
Comment: CODE SIGNING KEY
You selected this USER-ID:
   "${输入用户名} (CODE SIGNING KEY) <${邮箱地址}>"

Change (N)ame, (C)omment, (E)mail or (O)kay/(Q)uit? O
You need a Passphrase to protect your secret key. # 填入密码，以后打包过程中会经常用到
```

### 3.查看 key

```shell
$ gpg --list-keys
pub   rsa4096/579C25F5 2021-04-26 # 579C25F5就是key id
uid           [ultimate] ${输入用户名} <${邮箱地址}>
sub   rsa4096 2021-04-26

# 通过key id发送public key到keyserver
$ gpg --keyserver pgpkeys.mit.edu --send-key 579C25F5
# 其中，pgpkeys.mit.edu为随意挑选的keyserver，keyserver列表为：https://sks-keyservers.net/status/，相互之间是自动同步的，选任意一个都可以。
$ gpg --keyserver hkp://pgpkeys.mit.edu --recv-keys 579C25F5 # 验证是否同步到公网，网络不好可能需多试几次
```

**注：如果有多个 public key，设置默认 key。**修改`~/.gnupg/gpg.conf`

```sh
# If you have more than 1 secret key in your keyring, you may want to
# uncomment the following option and set your preferred keyid.
default-key 28681CB1
```

**如果有多个 public key, 也可以删除无用的 key：**

```shell
$ gpg --delete-secret-keys 29BBC3CB # 先删除私钥，指明key id
gpg (GnuPG) 2.2.27; Copyright (C) 2021 g10 Code GmbH
This is free software: you are free to change and redistribute it.
There is NO WARRANTY, to the extent permitted by law.

sec  rsa4096/EE8DAE7D29BBC3CB 2021-04-27 mikexue <mikexue@apache.org>

Delete this key from the keyring? (y/N) y
This is a secret key! - really delete? (y/N) y
```

```shell
$ gpg --delete-keys 29BBC3CB # 删除公钥，指明key id
gpg (GnuPG) 2.2.27; Copyright (C) 2021 g10 Code GmbH
This is free software: you are free to change and redistribute it.
There is NO WARRANTY, to the extent permitted by law.


pub  rsa4096/EE8DAE7D29BBC3CB 2021-04-27 mikexue <mikexue@apache.org>

Delete this key from the keyring? (y/N) y
```

由于公钥服务器没有检查机制，任何人都可以用你的名义上传公钥，所以没有办法保证服务器上的公钥的可靠性。 通常，你可以在网站上公布一个公钥指纹，让其他人核对下载到的公钥是否为真。

```shell
# fingerprint参数生成公钥指纹：
$gpg --fingerprint mikexue
pub   rsa4096 2021-04-26 [SCA]
      F84A 0041 D70B 37AF 9C7B  F0B3 39F4 29D7 579C 25F5
uid           [ultimate] mikexue <mikexue@apache.org>
sub   rsa4096 2021-04-26 [E]
```

登录 [https://id.apache.org](https://id.apache.org/), 将上面的 fingerprint （即 F84A 0041 D70B 37AF 9C7B  F0B3 39F4 29D7 579C 25F5） 粘贴到自己的用户信息中 OpenPGP Public Key Primary Fingerprint



## 发布Apache Maven仓库

> 注：EventMesh使用Gradle构建，需修改gradle相关配置

### 1.导出私钥文件

```shell
$ gpg --export-secret-keys -o secring.gpg #私钥文件妥善保管，后面配置需要
```

### 2.准备分支

从主干分支拉取新分支作为发布分支，如现在要发布$`{release_version}`版本，则从develop分支拉出新分支`${release_version}-release`，此后`${release_version}` Release Candidates涉及的修改及打标签等都在`${release_version}-release`分支进行，最终发布完成后合入主干分支。

### 3.更新版本说明

更新官网项目的如下文件，并提交至master分支：

https://github.com/apache/incubator-eventmesh-site/tree/master/events/release-notes

### 4.配置根项目下gradle.properties文件

```shell
group=org.apache.eventmesh
version=1.2.0-release
#40位公钥的最后8位
signing.keyId=579C25F5
#生成密钥时填的passphrase
signing.password=
#导出的私钥文件secring.gpg路径
signing.secretKeyRingFile=../secring.gpg
#apache 账号
apacheUserName=
#apache 密码
apachePassWord=
```

### 5.检查子模块下gradle.properties文件

```shell
group=org.apache.eventmesh
version=${release_version}
```

### 6.检查并配置根项目下build.gradle文件

```shell
publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            artifact packageSources
            artifact packageJavadoc
            versionMapping {
                usage('java-api') {
                    fromResolutionOf('runtimeClasspath')
                }
                usage('java-runtime') {
                    fromResolutionResult()
                }
            }
            pom {
                name = 'EventMesh'
                description = 'Apache EventMesh'
                url = 'https://github.com/apache/incubator-eventmesh'
                licenses {
                    license {
                        name = 'The Apache License, Version 2.0'
                        url = 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                    }
                }
                developers {
                    developer {
                        id = 'Apache EventMesh(incubating)'
                        name = 'Apache EventMesh(incubating) of ASF'
                        url = 'https://eventmesh.apache.org/'
                    }
                }
                scm {
                    connection = 'scm:git:git@github.com:apache/incubator-eventmesh.git'
                    developerConnection = 'scm:git:git@github.com:apache/incubator-eventmesh.git'
                    url = 'https://github.com/apache/incubator-eventmesh'
                }
            }
        }
    }
    repositories {
        maven {
            def releasesRepoUrl = 'https://repository.apache.org/service/local/staging/deploy/maven2/'
            def snapshotsRepoUrl = 'https://repository.apache.org/content/repositories/snapshots/'
            url = version.endsWith('SNAPSHOT') ? snapshotsRepoUrl : releasesRepoUrl
            credentials {
                username apacheUserName
                password apachePassWord
            }

        }
    }
}

signing {
    sign publishing.publications.mavenJava
}
```

### 7.上传发布包

执行如下命令，需要对jar、源码包、doc和pom等文件签名加密

```shell
$ gradle signMavenJavaPublication publish
```

上述命令执行成功后，待发布版本会自动上传到Apache的临时筹备仓库(staging repository)。所有被deploy到远程[maven仓库](http://repository.apache.org/)的Artifacts都会处于staging状态，访问https://repository.apache.org/#stagingRepositories, 使用Apache的LDAP账户登录后，就会看到上传的版本，`Repository`列的内容即为${STAGING.REPOSITORY}。 点击`Close`来告诉Nexus这个构建已经完成，只有这样该版本才是可用的。 如果电子签名等出现问题，`Close`会失败，可以通过`Activity`查看失败信息。



## 发布Apache SVN仓库

### 1.准备svn本机环境（Apache使用svn托管项目的发布内容）

### 2.checkout到本地目录

```shell
$ svn checkout https://dist.apache.org/repos/dist/dev/incubator/eventmesh/
# 假定本地目录为 ~/apache/eventmesh
```

### 3.添加gpg公钥

添加public key到[KEYS](https://dist.apache.org/repos/dist/dev/incubator/eventmesh/KEYS)文件并提交到SVN仓库（第一次做发布的人需要做这个操作，具体操作参考KEYS文件里的说明）。KEYS主要是让参与投票的人在本地导入，用来校验sign的正确性

Windows

```sh
$ gpg --list-sigs <your name> | out-file -append KEYS -encoding utf8
$ gpg --armor --export <your name> | out-file -append KEYS -encoding utf8
```

> Mac OS/Linux

```sh
$ (gpg --list-sigs <your name> && gpg --armor --export <your name>) >> KEYS
```

### 4.添加待发布内容到SVN目录

```shell
$ cd ~/apache/eventmesh # eventmesh svn根目录
$ mkdir ${release_version}-${rc_version}
```

#### 4.1 创建tag

在`${release_version}-release`分支上创建tag，需带有rc版本，为预发布版本

```shell
$ git tag -a v{$release_version}-{$rc_version} -m "Tagging the ${release_version} first Release Candidate (Candidates start at zero)"
$ git push origin --tags
```

#### 4.2 打包源码

检查项目源码命名，将源码命名为`apache-eventmesh-${release_version}-incubating-src`，将源码打包为tar.gz格式

```shell
$ tar -czvf apache-eventmesh-${release_version}-incubating-source.tar.gz apache-eventmesh-${release_version}-incubating-src
```

#### 4.3 打包二进制

> 编译上一步打包的源码

检查编译后的文件命名，将二进制文件命名为`apache-eventmesh-${release_version}-incubating`

> 注：需将源码根目录下的`NOTICE`文件，`DISCLAIMER-WIP`文件以及`tools/third-party-licenses`目录下的`LICENSE`文件拷贝到二进制的包中

```shell
$ gradle clean jar dist && gradle installPlugin && gradle tar -x test
$ tar -czvf apache-eventmesh-${release_version}-incubating-bin.tar.gz apache-eventmesh-${release_version}-incubating
```

压缩source包、bin包，并将相关的压缩包拷贝到svn本地仓库下`/apache/eventmesh/${release_version}-${rc_version}`

### 5.生成签名/sha512文件

> 针对源码包与二进制包生成签名/sha512文件

```shell
$ for i in *.tar.gz; do echo $i; gpg --print-md SHA512 $i > $i.sha512 ; done #计算sha512
$ for i in *.tar.gz; do echo $i; gpg --armor --output $i.asc --detach-sig $i ; done #计算签名
```

### 6.提交到Apache svn

```shell
$ cd ~/apache/eventmesh # eventmesh svn根目录
$ svn status
$ svn commit -m 'prepare for ${release_version}-${rc_version}'
```



## 验证Release Candidates

详细检查列表请参考官方的[check list](https://cwiki.apache.org/confluence/display/INCUBATOR/Incubator+Release+Checklist)

从以下地址下载要发布的Release Candidates到本地环境：

```shell
https://dist.apache.org/repos/dist/dev/incubator/eventmesh/${release_version}-${rc_version}/
```

然后开始验证环节，验证包含但不限于以下内容和形式

### 1.检查签名和hash等信息

> 由于操作系统不同，检查的命令或有差异，具体可参考[官方检查步骤](https://www.apache.org/info/verification.html)

#### 1.1检查sha512哈希

> Mac OS/Linux

```shell
$ shasum -a apache-eventmesh-${release_version}-incubating-source.tar.gz
#并将输出内容与 apache-eventmesh-${release_version}-${rc_version}-incubating-source.tar.gz.sha512文件内容作对比
$ shasum -a apache-eventmesh-${release_version}-incubating-bin.tar.gz
#并将输出内容与 apache-eventmesh-${release_version}-${rc_version}-incubating-bin.tar.gz.sha512文件内容作对比
```

> Windows

```shell
$ certUtil -hashfile apache-eventmesh-${release_version}-incubating-source.tar.gz SHA512
#并将输出内容与 apache-eventmesh-${release_version}-${rc_version}-incubating-source.tar.gz.sha512文件内容作对比
$ certUtil -hashfile apache-eventmesh-${release_version}-incubating-bin.tar.gz SHA512
#并将输出内容与 apache-eventmesh-${release_version}-${rc_version}-incubating-bin.tar.gz.sha512文件内容作对比
```

#### 1.2检查gpg签名

首先导入发布人公钥。从svn仓库导入KEYS到本地环境。（发布版本的人不需要再导入，帮助做验证的人需要导入，用户名填发版人的即可）

```shell
$ curl https://dist.apache.org/repos/dist/dev/incubator/eventmesh/KEYS >> KEYS
$ gpg --import KEYS
$ gpg --edit-key "${发布人的gpg用户名}"
  > trust

Please decide how far you trust this user to correctly verify other users' keys
(by looking at passports, checking fingerprints from different sources, etc.)

  1 = I don't know or won't say
  2 = I do NOT trust
  3 = I trust marginally
  4 = I trust fully
  5 = I trust ultimately
  m = back to the main menu

Your decision? 5

  > save
```

然后使用如下命令检查签名

```shell
$ gpg --verify apache-eventmesh-${release_version}-incubating-source.tar.gz.asc apache-eventmesh-${release_version}-incubating-source-tar.gz
$ gpg --verify apache-eventmesh-${release_version}-incubating-bin.tar.gz.asc apache-eventmesh-${release_version}-incubating-bin.tar.gz
```

### 2.检查源码包的文件内容

解压缩`apache-eventmesh-${release_version}-incubating-source-tar.gz`，进行如下检查:

- 检查源码包是否包含由于包含不必要文件，致使tar包过于庞大
- 文件夹包含单词`incubating`
- 存在`LICENSE`和`NOTICE`文件
- 存在`DISCLAIMER`文件
- `NOTICE`文件中的年份正确
- 只存在文本文件，不存在二进制文件
- 所有文件的开头都有ASF许可证
- 能够正确编译，单元测试可以通过 (./gradle build) (目前支持JAVA 8/gradle 7.0/idea 2021.1.1及以上)
- 检查是否有多余文件或文件夹，例如空文件夹等

### 3.检查二进制包的文件内容

- 文件夹包含单词`incubating`
- 存在`LICENSE`和`NOTICE`文件
- 存在`DISCLAIMER`文件
- `NOTICE`文件中的年份正确
- 所有文本文件开头都有ASF许可证
- 检查第三方依赖许可证：
  - 第三方依赖的许可证兼容
  - 所有第三方依赖的许可证都在`LICENSE`文件中声名
  - 依赖许可证的完整版全部在`license`目录
  - 如果依赖的是Apache许可证并且存在`NOTICE`文件，那么这些`NOTICE`文件也需要加入到版本的`NOTICE`文件中

你可以参考此文章：[ASF第三方许可证策](https://apache.org/legal/resolved.html)

## 发起投票

> EventMesh 仍在孵化阶段，需要进行两次投票

- EventMesh社区投票，发送邮件至：`dev@eventmesh.apache.org`
- incubator社区投票，发送邮件至：`general@incubator.apache.org` EventMesh毕业后，只需要在EventMesh社区投票

### 1.EventMesh社区投票阶段

1. EventMesh社区投票，发起投票邮件到`dev@eventmesh.apache.org`。PMC需要先按照文档检查版本的正确性，然后再进行投票。 经过至少72小时并统计到3个`+1 PMC member`票后，即可进入下一阶段的投票。
2. 宣布投票结果,发起投票结果邮件到`dev@eventmesh.apache.org`。

### 2.EventMesh社区投票模板

标题：

```
[VOTE] Release Apache EventMesh (incubating) ${release_version} ${rc_version}
```

正文：

```
Hello EventMesh Community,

    This is a call for vote to release Apache EventMesh (incubating) version ${release_version}-${rc_version}.

	Release notes:
	https://github.com/apache/incubator-eventmesh/releases/tag/v${release_version}-${rc_version}

    The release candidates:
    	https://dist.apache.org/repos/dist/dev/incubator/eventmesh/${release_version}-${rc_version}/

    Maven artifacts are available in a staging repository at:
    https://repository.apache.org/content/repositories/orgapacheeventmesh-{staging-id}

	Git tag for the release:
	https://github.com/apache/incubator-eventmesh/tree/v${release_version}-${rc_version}

	Keys to verify the Release Candidate:
	https://downloads.apache.org/incubator/eventmesh/KEYS

	Hash for the release tag:
	#hashCode of this release tag

	GPG user ID:
	${YOUR.GPG.USER.ID}

	The vote will be open for at least 72 hours or until necessary number of votes are reached.

	Please vote accordingly:

	[ ] +1 approve

	[ ] +0 no opinion

	[ ] -1 disapprove with the reason

	Checklist for reference:

	[ ] Download links are valid.

	[ ] Checksums and PGP signatures are valid.

	[ ] Source code distributions have correct names matching the current release.

	[ ] LICENSE and NOTICE files are correct for each EventMesh repo.

	[ ] All files have license headers if necessary.

	[ ] No compiled archives bundled in source archive.

	More detail checklist  please refer:
    https://cwiki.apache.org/confluence/display/INCUBATOR/Incubator+Release+Checklist

Thanks,
Your EventMesh Release Manager
```

### 3.宣布投票结果模板

标题：

```
[RESULT][VOTE] Release Apache EventMesh (incubating) ${release_version} ${rc_version}
```

正文：

```
Hello Apache EventMesh PPMC and Community,

    The vote closes now as 72hr have passed. The vote PASSES with

    xx (+1 non-binding) votes from the PPMC,
    xx (+1 binding) votes from the IPMC,
    xx (+1 non-binding) votes from the rest of the developer community,
    and no further 0 or -1 votes.

    The vote thread: {vote_mail_address}

    I will now bring the vote to general@incubator.apache.org to get approval by the IPMC.
    If this vote passes also, the release is accepted and will be published.

Thank you for your support.
Your EventMesh Release Manager
```

### 4.Incubator社区投票阶段

1. Incubator社区投票，发起投票邮件到`general@incubator.apache.org`，需3个 `+1 IPMC Member`投票，方可进入下一阶段。
2. 宣布投票结果,发起投票结果邮件到`general@incubator.apache.org` 并抄送至`dev@eventmesh.apache.org`。

### 5.Incubator社区投票模板

标题：

```
[VOTE] Release Apache EventMesh (incubating) ${release_version} ${rc_version}
```

内容：

```
Hello Incubator Community,

	This is a call for a vote to release Apache EventMesh(Incubating) version ${release_version} ${rc_version}

	The Apache EventMesh community has voted on and approved a proposal to release
    Apache EventMesh(Incubating) version ${release_version} ${rc_version}

    We now kindly request the Incubator PMC members review and vote on this
    incubator release.

    EventMesh community vote thread:
    • [投票链接]

    Vote result thread:
    • [投票结果链接]

    The release candidate:
    •https://dist.apache.org/repos/dist/dev/incubator/eventmesh/${release_version}-${rc_version}/

	Git tag for the release:
	• https://github.com/apache/incubator-eventmesh/tree/${release_version}-${rc_version}
	Release notes:
	• https://github.com/apache/incubator-eventmesh/releases/tag/${release_version}-${rc_version}

	The artifacts signed with PGP key [填写你个人的KEY], corresponding to [填写你个人的邮箱], that can be found in keys file:
	• https://downloads.apache.org/incubator/eventmesh/KEYS

	The vote will be open for at least 72 hours or until necessary number of votes are reached.

	Please vote accordingly:

	[ ] +1 approve
	[ ] +0 no opinion
	[ ] -1 disapprove with the reason

Thanks,
On behalf of Apache EventMesh(Incubating) community
```

### 6.宣布投票结果模板

标题：

```
[RESULT][VOTE] Release Apache EventMesh (incubating) ${release_version} ${rc_version}
```

内容：

```
Hi all,

	Thanks for reviewing and voting for Apache EventMesh(Incubating) version ${release_version} ${rc_version} release, I am happy to announce the release voting has passed with [投票结果数] binding votes, no +0 or -1 votes.

	 Binding votes are from IPMC
	   - xxx
   	   - xxx
       - xxx

     Non-binding votes:
       +1 xxx
       +0 xxx
       -1 xxx

    The voting thread is:
    • [投票结果链接]

    Many thanks for all our mentors helping us with the release procedure, and all IPMC helped us to review and vote for Apache EventMesh(Incubating) release. I will be 		working on publishing the artifacts soon.

Thanks,
On behalf of Apache EventMesh(Incubating) community
```

## 正式发布

### 1.合并分支

合并`${release_version}-release`分支的改动到`master`分支，合并完成后删除`release`分支

```shell
$ git checkout master
$ git merge origin/${release_version}-release
$ git pull
$ git push origin master
$ git push --delete origin ${release_version}-release
$ git branch -d ${release_version}-release
```

### 2.迁移源码与二进制包

将源码和二进制包从svn的`dev`目录移动到`release`目录

```shell
$ svn mv https://dist.apache.org/repos/dist/dev/incubator/eventmesh/${release_version}-${rc_version} https://dist.apache.org/repos/dist/release/incubator/eventmesh/ -m "transfer packages for ${release_version}-${rc_version}" #移动源码包与二进制包
$ svn delete https://dist.apache.org/repos/dist/release/incubator/eventmesh/KEYS -m "delete KEYS" #清除原有release目录下的KEYS
$ svn cp https://dist.apache.org/repos/dist/dev/incubator/eventmesh/KEYS https://dist.apache.org/repos/dist/release/incubator/eventmesh/ -m "transfer KEYS for ${release_version}-${rc_version}" #拷贝dev目录KEYS到release目录
```

### 3.确认dev和release下的包是否正确

- 确认[dev](https://dist.apache.org/repos/dist/dev/incubator/eventmesh/)下的`${release_version}-${rc_version}`已被删除
- 删除[release](https://dist.apache.org/repos/dist/release/incubator/eventmesh/)目录下上一个版本的发布包，这些包会被自动保存在[这里](https://archive.apache.org/dist/incubator/eventmesh/)

```shell
$ svn delete https://dist.apache.org/repos/dist/release/incubator/eventmesh/${last_release_version} -m "Delete ${last_release_version}"
```

### 4.在Apache Staging仓库发布版本

- 登录http://repository.apache.org , 使用Apache账号登录
- 点击左侧的Staging repositories，
- 搜索EventMesh关键字，选择你最近上传的仓库，投票邮件中指定的仓库
- 点击上方的`Release`按钮，这个过程会进行一系列检查

> 等仓库同步到其他数据源，一般需要24小时

### 5.GitHub版本发布

1.Tag the commit (on which the vote happened) with the release version without `-${RELEASE_CANDIDATE}`. 例如：after a successful vote on `v1.2-rc5`, the hash will be tagged again with `v1.2` only.

2.在 [GitHub Releases](https://github.com/apache/incubator-eventmesh/releases) 页面的 `${release_version}` 版本上点击 `Edit`

编辑版本号及版本说明，并点击 `Publish release`

### 6.更新下载页面

等待并确认新的发布版本同步至 Apache 镜像后，更新如下页面：

https://eventmesh.apache.org/download/

https://eventmesh.apache.org/zh/download/

GPG签名文件和哈希校验文件的下载连接应该使用这个前缀：`https://downloads.apache.org/incubator/eventmesh/`

> 注意：项目下载链接应该使用 https://www.apache.org/dyn/closer.lua 而不是 closer.cgi 或者 mirrors.cgi

### 7.邮件通知版本发布完成

> 请确保Apache Staging仓库已发布成功，一般是在该步骤的24小时后发布邮件

发邮件到 `dev@eventmesh.apache.org` 、 `announce@apache.org`和`general@incubator.apache.org`

标题：

```
[ANNOUNCE] Apache EventMesh (incubating) ${release_version} available
```

正文：

```
Hi all,

Apache EventMesh (incubating) Team is glad to announce the new release of Apache EventMesh (incubating) ${release_version}.

Apache EventMesh (incubating) is a dynamic cloud-native eventing infrastructure used to decouple the application and backend middleware layer, which supports a wide range of use cases that encompass complex multi-cloud, widely distributed topologies using diverse technology stacks.

Download Links: https://eventmesh.apache.org/projects/eventmesh/download/

Release Notes: https://eventmesh.apache.org/events/release-notes/v${release_version}/

Website: https://eventmesh.apache.org/

EventMesh Resources:
- Issue: https://github.com/apache/incubator-eventmesh/issues
- Mailing list: dev@eventmesh.apache.org



Apache EventMesh (incubating) Team
```

