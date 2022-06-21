
# tool-offline for IoTDB

# Table of Contents
- [Introduction](#Introduction)
- [Quick Start](#Quick Start)
    - [Prerequisites](#Prerequisites)
    - [Compile](#Compile)
- [Maintainers](#Maintainers)
- [Contributing](#Contributing)
- [Contributors](#Contributors)
# Introduction
Tool-offline-for-IoTDB is a TsFile management tool. Currently, we support bit granularity parsing of TsFile and provide
visual display. This tool can Clearly display information of each part of TsFile, details are as follows:
1. The versionNumber.
2. The data layer: contains details of each level and statistic information.
    i. ChunkGroup
    ii. Chunk
    iii. Page
    iv. Point
3. The index layer: displayed in a B+ tree like structure then you can easily view the overall structure of the secondary 
index(entity and measurement granularity).

In addition to displaying data, we also provide the function of querying TimeSeries by keyword. There is a linkage
between the index layer and the data layer, it can quickly locate the desired TimeSeries with details.
# Quick Start
## Prerequisites
To use Tool-offline-for-IoTDB, you need to have:
1. Java >= 1.8 (Note: Because we use JavaFx to develop front end and JavaFx is dependent in JDK11, if the version of
   the JDK you use > 1.8, you need to import the relevant libraries.)
2. Maven >= 3.6
## Compile
You can download the source code from:
```
git clone https://github.com/xxx/xxx.git
```
Under the root path of xxx:
```
> mvn clean package
```
# Maintainers
# Contributing
Feel free to dive in! Open an issue or submit PRs.
# Contributors
This project exists thanks to all the people who contribute.
