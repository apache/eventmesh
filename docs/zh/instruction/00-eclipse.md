# eventMesh 导入Eclipse 快速入门说明

### 依赖

```
64位JDK 1.8+;
Gradle至少为5.6, 推荐 5.6.*
eclipse 已安装gradle插件或者eclipse自带gradle插件
```

### 下载源码
git init  

git clone https://github.com/apache/incubator-eventmesh.git

### 项目编译eclipse环境

打开命令行终端，运行gradlew cleanEclipse eclipse

### 配置修改
修改工程名称和settings.gradle 配置文件参数rootProject.name 参数一致

### 修改eclipse.init配置文件,配置lombok以1.18.8版本为例
-javaagent:lombok-1.18.8.jar
-XBootclasspath/a:lombok-1.18.8.jar

### 202106版本eclipse,eclipse.init增加配置参数
--illegal-access=permit


### 导入gradle
打开eclipse,导入gradle项目到IDE里

