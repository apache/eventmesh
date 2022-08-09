# Contributing to EventMesh

Welcome to EventMesh! This document is a guideline about how to contribute to EventMesh. If you find something incorrect
or missing, please leave comments / suggestions.

## Before you get started

### Setting up your development environment

You should have JDK installed in your develop environment.

### Code style

Import [EventMesh CheckStyle](./style/checkStyle.xml) file to your IDE.

For IDEA, you can import check style file by:
```shell
Editor -> Code Style -> Java -> Scheme -> Import Scheme -> CheckStyle Configuration
```
If you can't see CheckStyle Configuration section under Import Scheme, you can install CheckStyle-IDEA plugin first, and you will see it.

You can also use `./gradlew check` to check the code style.
(NOTE: this command will check all file in project, when you submit a pr, the ci will only check the file has been changed in this pr).
## Contributing

We are always very happy to have contributions, whether for typo fix, bug fix or big new features. Please do not ever
hesitate to ask a question or send a pull request.

We strongly value documentation and integration with other projects. We are very glad to accept improvements for these
aspects.

### GitHub workflow

We use the `develop` branch as the development branch, which indicates that this is an unstable branch.

Here are the workflow for contributors:

1. Fork to your own
2. Clone fork to local repository
```git
git clone git@github.com:yourgithub/incubator-eventmesh.git
```
3. Create a new branch and work on it
```git
git checkout -b fix_patch_xx
```  
4. Keep your branch in sync
```git
git remote add upstream git@github.com:apache/incubator-eventmesh.git
git fetch upstream develop:upstream_develop
git rebase upstream_develop
```   
5. Commit your changes (make sure your commit message concise)
6. Push your commits to your forked repository
7. Create a pull request

Please follow [the pull request template](./.github/PULL_REQUEST_TEMPLATE.md). Please make sure the PR has a
corresponding issue. [GitHub Issues](https://github.com/apache/incubator-eventmesh/issues)

After creating a PR, one or more committers will help to review the pull request, after approve, this PR will be merged in to 
EventMesh repository, and the related Issue will be closed.

### Open an issue / PR

We use [GitHub Issues](https://github.com/apache/incubator-eventmesh/issues)
and [Pull Requests](https://github.com/apache/incubator-eventmesh/pulls) for trackers.

If you find a bug in code, or want new features, or want to give suggestions, you
can [open an issue on GitHub](https://github.com/apache/incubator-eventmesh/issues/new) to report it. Please follow the
guideline message in the issue template.

If you want to contribute, please follow the [contribution workflow](#github-workflow) and create a new pull request. Your PR title should start with [ISSUE #xx].
If your PR contains large changes, e.g. component refactor or new components, please write detailed documents about its
design and usage.

If your change is about a typo or small optimize, you needn't create an Issue, just submit a PR and title with [MINOR].

[Note]: A single pull request should not be too large. If heavy changes are required, it's better to separate the
changes to a few individual PRs.

### PR review

All code should be well reviewed by one or more committers. Some principles:

- Readability: Important code should be well-documented. Comply with our [code style](./style/checkStyle.xml).
- Elegance: New functions, classes or components should be well-designed.
- Testability: Important code should be well-tested (high unit test coverage).

### License review

EventMesh follows [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) policy. All source files should 
have the Apache License header added to the file header. EventMesh uses the [apache/skywalking-eyes](https://github.com/apache/skywalking-eyes) to check 
the source file header.

EventMesh uses [check-dependencies.sh](tools/dependency-check/check-dependencies.sh) script to check for third-part dependencies. 
When you need to add a three-part dependency, you need to register the newly added dependency in tool/license/known-dependencies.txt. The newly added three-part libraries need to meet [ASF 3RD PARTY LICENSE POLICY](https://apache.org/legal/resolved.html). 
It is highly recommended communicating with EventMesh community before you need to add a three-part library.

### PR merge

After a PR is approved by at least one committer, it can be merged. Before the merge, the committer can make changes to the commits message, requiring the commits
message to be clear without duplication, and use Squash and Merge to make sure one PR should only contain one commits. 
For large multi-person PR, use Merge to merge, and fix the commits by rebase before merging. 

## Community

### Contact us

Mail: dev@eventmesh.apache.org
