#!/usr/bin/env bash
#
# -Pdev=true : snapshot version
# -Pjdk=1.8  : jdk version,default 1.8
# dist       : resource collect
# tar        : produce tar.gz
# zip        : produce zip
# jar        : produce jar

# package tar.gz/zip
gradle clean -Pdev=true -Pjdk=1.7 dist tar zip

# package jar
gradle clean -Pdev=true -Pjdk=1.7 jar