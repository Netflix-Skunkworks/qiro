# Qiro

Qiro is an agnostic communication library supporting multiple interaction models
(fire-and-forget, request-response, request-stream, subscription, channel).

## Build and Binaries

<a href='https://travis-ci.org/qiro/qiro/builds'><img src='https://travis-ci.org/qiro/qiro.svg?branch=master'></a>

Snapshots are available via JFrog.

Example:

```groovy
repositories {
    maven { url 'https://oss.jfrog.org/libs-snapshot' }
}

dependencies {
    compile 'io.qiro:qiro-core:0.0.1-SNAPSHOT'
}
```

No releases to Maven Central or JCenter have occurred yet.


## Bugs and Feedback

For bugs, questions and discussions please use the 
[Github Issues](https://github.com/qiro/qiro/issues).


## LICENSE

Copyright 2015 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
