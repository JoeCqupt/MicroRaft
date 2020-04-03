## JARs

MicroRaft JARs is available in the standard Maven repositories. If you are 
using Maven, just add the following lines to the `dependencies` section in 
your `pom.xml`:

~~~~{.java}
<dependency>
    <groupId>io.microraft</groupId>
    <artifactId>microraft</artifactId>
    <version>1.0</version>
</dependency>
~~~~

If you are using HOCON or YAML files for configuration, the following 
dependencies provide parsers to configure MicroRaft configuration from HOCON 
and YAML files:

~~~~
<dependency>
	<groupId>io.microraft</groupId>
	<artifactId>microraft-hocon</artifactId>
	<version>1.0</version>
</dependency>
~~~~

~~~~
<dependency>
	<groupId>io.microraft</groupId>
	<artifactId>microraft-yaml</artifactId>
	<version>1.0</version>
</dependency>
~~~~

If you don't have Maven but want to build the project on your machine, `mvnw` 
is available in the MicroRaft repository. Just clone 
`https://github.com/metanet/MicroRaft.git` and hit `./mvnw clean package`. 
Then you can get the jars from `microraft/target`, `microraft-hocon/target`, 
and `microraft-yaml/target` directories. 


## Logging

MicroRaft depends on the SLF4J library for logging. Actually it is the only
dependency of MicroRaft. Make sure you enable the `INFO` logging level for the 
the `io.microraft` package. If you are going hard, you can also give 
the `DEBUG` level a shot, but I assure you it will be a bumpy ride.


## What is Next?

OK. You have the MicroRaft JAR and logging also looks good. Now you are ready
to build your CP distributed system! Why don't you start with
[checking out the main abstractions](main-abstractions.md) defined in 
MicroRaft?   