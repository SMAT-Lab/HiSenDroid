#!/bin/bash

mvn install:install-file -Dfile=./res/iccta-2019-11-12.jar -DgroupId=au.monash.edu -DartifactId=iccta -Dversion=2019-11-12 -Dpackaging=jar
mvn install:install-file -Dfile=./res/coal-strings-0.1.4.jar -DgroupId=edu.psu.cse.siis -DartifactId=coal-strings -Dversion=0.1.4 -Dpackaging=jar
mvn install:install-file -Dfile=./res/heros-1.0.1-SNAPSHOT.jar -DgroupId=heros -DartifactId=heros -Dversion=1.0.1-SNAPSHOT -Dpackaging=jar
mvn install:install-file -Dfile=./res/soot-infoflow-android-2.7.1.jar -DgroupId=de.tud.sse -DartifactId=soot-infoflow-android -Dversion=2.7.1 -Dpackaging=jar