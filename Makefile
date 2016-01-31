all: compile

.make-classpath: pom.xml
	mvn dependency:copy-dependencies
	mvn dependency:build-classpath | grep -v '^\[' > .make-classpath

JAVAC=javac
JAVAC_FLAGS += -d target/classes
JAVAC_FLAGS += -sourcepath src/main/java:src/test/java:target/generated-sources/src/main/antlr4
JAVAC_FLAGS += -classpath `cat .make-classpath`
JAVAC_FLAGS += -g -target 1.7 -source 1.7 -encoding UTF-8 -Xlint:-options

ANTLR=java -cp target/dependency/antlr4-4.5.jar org.antlr.v4.Tool
ANTLR_SRC=src/main/antlr4/com/zendesk/maxwell/schema/ddl/mysql.g4
ANTLR_IMPORTS=src/main/antlr4/imports
ANTLR_DIR=target/generated-sources/src/main/antlr4/com/zendesk/maxwell/schema/ddl
ANTLR_OUTPUT=$(ANTLR_DIR)/mysqlBaseListener.java $(ANTLR_DIR)/mysqlLexer.java $(ANTLR_DIR)/mysqlListener.java $(ANTLR_DIR)/mysqlParser.java

$(ANTLR_OUTPUT): $(ANTLR_SRC) $(ANTLR_IMPORTS)/*.g4
	${ANTLR} -package com.zendesk.maxwell.schema.ddl -lib $(ANTLR_IMPORTS) -o target/generated-sources $(ANTLR_SRC)

compile-antlr: $(ANTLR_OUTPUT)

JAVA_SOURCE = $(shell find src/main/java -name '*.java')

target/.java: $(ANTLR_OUTPUT) $(JAVA_SOURCE)
	@mkdir -p target/classes
	$(JAVAC) $(JAVAC_FLAGS) $?
	@touch target/.java

compile-java: target/.java
compile: .make-classpath compile-antlr compile-java

JAVA_TEST_SOURCE=$(shell find src/test/java -name '*.java')
target/.java-test: $(JAVA_TEST_SOURCE)
	@mkdir -p target/classes
	javac -d target/classes -sourcepath src/main/java:src/test/java:target/generated-sources/src/main/antlr4 -classpath `cat .make-classpath` \
		-g -target 1.7 -source 1.7 -encoding UTF-8 $?
	@touch target/.java


compile-test: .make-classpath compile target/.java-test


clean:
	rm -f  target/.java target/.java-test .make-classpath
	rm -rf target/classes
	rm -rf target/generated-sources

TEST_CLASSES=$(shell build/get-test-classes $@)

test: .make-classpath compile-test
	java -classpath `cat .make-classpath`:target/classes org.junit.runner.JUnitCore $(TEST_CLASSES)

MAXWELL_VERSION=$(shell build/current_rev)

package: clean all
	mkdir target/build-jar
	cp -a target/classes/* src/main/resources/* target/build-jar
	jar cvf target/maxwell-${MAXWELL_VERSION}.jar -C target/build-jar .




