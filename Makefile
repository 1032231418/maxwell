all: compile

.make-classpath: pom.xml target
	mvn dependency:copy-dependencies
	mvn dependency:build-classpath | grep -v '^\[' > .classpath

JAVA_SOURCES=$(shell find . -name '*.java')
ANTLR_SOURCES=$(shell find . -name '*.g4')
ANTLR=java -cp target/dependency/antlr4-4.5.jar org.antlr.v4.Tool

compile: .make-classpath
	mkdir -p target/classes target/generated-sources/annotations
	${ANTLR} -package com.zendesk.maxwell.schema.ddl \
		src/main/antlr4/com/zendesk/maxwell/schema/ddl/mysql.g4 -lib src/main/antlr4/imports
	javac -d /Users/ben/src/maxwell/target/classes -classpath $(shell cat .classpath) ${JAVA_SOURCES} \
		-s /Users/ben/src/maxwell/target/generated-sources/annotations -g -nowarn -target 1.7 -source 1.7 -encoding UTF-8





test:

package:


