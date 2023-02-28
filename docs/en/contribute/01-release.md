# Release Creation Process

:::caution
The documentation of Release Creation Process is WIP (Work-in-Progress).
:::

## Understand the content and process of Apache publishing

Source Release is the focus of Apache and a must for release; while Binary Release is optional,

Please refer to the link below to find more publishing guidelines on ASF:

- [Apache Release Guide](http://www.apache.org/dev/release-publishing)
- [Apache Release Policy](http://www.apache.org/dev/release.html)
- [Maven Release Info](http://www.apache.org/dev/publishing-maven-artifacts.html)


## Local build environment preparation

Mainly including signing tools, Maven warehouse certification related preparations

### 1. Install GPG

Download the installation package from [GnuPG official website](https://www.gnupg.org/download/index.html). The commands of GnuPG 1.x version and 2.x version are slightly different. The following instructions take **GnuPG-2.x** version as an example

```sh
$ gpg --version #Check the version, it should be 2.x
```

### 2. Generate key with gpg

According to the prompt, generate the key

> Note: Please use the Apache mailbox to generate the GPG Key

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
What key size do you want? (2048) 4096
Requested keysize is 4096 bits
Please specify how long the key should be valid.
         0 = key does not expire
      <n> = key expires in n days
      <n>w = key expires in n weeks
      <n>m = key expires in n months
      <n>y = key expires in n years
Key is valid for? (0)
Key does not expire at all
Is this correct? (y/N) y

GnuPG needs to construct a user ID to identify your key.

Real name: ${enter username}
Email address: ${email address}
Comment: CODE SIGNING KEY
You selected this USER-ID:
    "${enter username} (CODE SIGNING KEY) <${email address}>"

Change (N)ame, (C)omment, (E)mail or (O)kay/(Q)uit? O
You need a Passphrase to protect your secret key. # Fill in the password, which will be often used in the packaging process in the future
```

### 3. View key

```shell
$ gpg --list-keys
pub rsa4096/579C25F5 2021-04-26 # 579C25F5 is the key id
uid [ultimate] ${enter username} <${email address}>
sub rsa4096 2021-04-26

# Send public key to keyserver by key id
$ gpg --keyserver pgpkeys.mit.edu --send-key 579C25F5
# Among them, pgpkeys.mit.edu is a randomly selected keyserver, and the keyserver list is: https://sks-keyservers.net/status/, which are automatically synchronized with each other, and you can choose any one.
$ gpg --keyserver hkp://pgpkeys.mit.edu --recv-keys 579C25F5 # Verify whether it is synchronized to the public network, if the network is not good, you may need to try a few more times
```

**Note: If there are multiple public keys, set the default key. ** Modify `~/.gnupg/gpg.conf`

```sh
# If you have more than 1 secret key in your keyring, you may want to
# uncomment the following option and set your preferred keyid.
default-key 28681CB1
```

**If there are multiple public keys, useless keys can also be deleted:**

```shell
$ gpg --delete-secret-keys 29BBC3CB # Delete the private key first and specify the key id
gpg (GnuPG) 2.2.27; Copyright (C) 2021 g10 Code GmbH
This is free software: you are free to change and redistribute it.
There is NO WARRANTY, to the extent permitted by law.

sec rsa4096/EE8DAE7D29BBC3CB 2021-04-27 mikexue <mikexue@apache.org>

Delete this key from the keyring? (y/N) y
This is a secret key! - really delete? (y/N) y
```

```shell
$ gpg --delete-keys 29BBC3CB # delete public key, specify key id
gpg (GnuPG) 2.2.27; Copyright (C) 2021 g10 Code GmbH
This is free software: you are free to change and redistribute it.
There is NO WARRANTY, to the extent permitted by law.


pub rsa4096/EE8DAE7D29BBC3CB 2021-04-27 mikexue <mikexue@apache.org>

Delete this key from the keyring? (y/N) y
```

Since the public key server has no checking mechanism, anyone can upload the public key in your name, so there is no way to guarantee the reliability of the public key on the server. Usually, you can publish a public key fingerprint on the website, so that others can check whether the downloaded public key is genuine.

```shell
# The fingerprint parameter generates a public key fingerprint:
$ gpg --fingerprint mikexue
pub rsa4096 2021-04-26 [SCA]
       F84A 0041 D70B 37AF 9C7B F0B3 39F4 29D7 579C 25F5
uid [ultimate] mikexue <mikexue@apache.org>
sub rsa4096 2021-04-26 [E]
```

Log in to [https://id.apache.org](https://id.apache.org/), paste the above fingerprint (ie F84A 0041 D70B 37AF 9C7B F0B3 39F4 29D7 579C 25F5) into your user information OpenPGP Public Key Primary Fingerprint



## Publish the Apache Maven repository

> Note: EventMesh uses Gradle to build, you need to modify the gradle related configuration

### 1. Export the private key file

```shell
$ gpg --export-secret-keys -o secring.gpg #The private key file is kept properly, and it needs to be configured later
```

### 2. Prepare branch

Pull the new branch from the trunk branch as the release branch. If you want to release the $`{release_version}` version now, pull the new branch `${release_version}-release` from the develop branch, and then `${release_version}` Release Candidates involves The modification and labeling of all files are carried out in the `${release_version}-release` branch, and merged into the main branch after the final release is completed.

### 3. Update version description

Update the following files of the official website project and submit them to the master branch:

https://github.com/apache/incubator-eventmesh-site/tree/master/events/release-notes

### 4. Configure the gradle.properties file under the root project

```shell
group=org.apache.eventmesh
version=1.2.0-release
#The last 8 digits of the 40-bit public key
signing.keyId=579C25F5
#passphrase filled in when generating the key
signing.password=
#Exported private key file secring.gpg path
signing.secretKeyRingFile=../secring.gpg
#apache account
apacheUserName=
#apache password
apachePassWord=
```

### 5. Check the gradle.properties file under the submodule

```shell
group=org.apache.eventmesh
version=${release_version}
```

### 6. Check and configure the build.gradle file under the root project

```shell
publishing {
     publications {
         mavenJava(MavenPublication) {
             from components.java
             artifact packageSources
             artifact package Javadoc
             versionMapping {
                 usage('java-api') {
                     fromResolutionOf('runtimeClasspath')
                 }
