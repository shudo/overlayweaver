CC=gcc
LD_DYNAMIC=$(CC) -shared
#LD_DYNAMIC=ld -Bdynamic
RM=rm -f
MKDIR=mkdir -p
INSTALL=install
JAVAH=javah -jni

JAVA_INCDIR=/usr/local/java/include
JAVA_ARC=linux
JAVA_CLASSPATH=../../target/overlayweaver.jar:../../build

CFLAGS=-O2 -Wall
LIBS=

TARGET=libowmcast.so
TARGETDIR=../../target
OBJ=glue.o igmp.o dgram.o util.o vif.o config.o kern.o inet.o
GENERATED_HDR=ow_ipmulticast_Native.h

all: $(TARGET)

install: $(TARGET)
	$(MKDIR) $(TARGETDIR)
	$(INSTALL) -m 755 $(TARGET) $(TARGETDIR)

libowmcast.so: $(OBJ)
	$(LD_DYNAMIC) -o $@ $(OBJ) $(LIBS)

test: libowmcast.so
	$(CC) -o $@ test.c libowmcast.so

glue.o: ow_ipmulticast_Native.h
	$(CC) -c -o $@ $(CFLAGS) -I$(JAVA_INCDIR) -I$(JAVA_INCDIR)/$(JAVA_ARC) glue.c

ow_ipmulticast_Native.h:
	$(JAVAH) -classpath $(JAVA_CLASSPATH) ow.ipmulticast.Native

clean:
	$(RM) $(TARGET) $(OBJ) $(GENERATED_HDR)
