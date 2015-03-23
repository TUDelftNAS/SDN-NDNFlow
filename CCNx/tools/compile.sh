#!/bin/sh

# #Copyright (C) 2015, Delft University of Technology, Faculty of Electrical Engineering, Mathematics and Computer Science, Network Architectures and Services, Niels van Adrichem
#
# This file is part of NDNFlow.
#
# NDNFlow is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# NDNFlow is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with OpenNetMon. If not, see <http://www.gnu.org/licenses/>.

JAR_FILE=ccn.jar
JAVA_HOME=${JAVA_HOME:=/usr}
JAVAC=$JAVA_HOME/bin/javac
SCRIPT_DIR=`dirname $0`

CP1=$SCRIPT_DIR/../lib/*.jar
CP1=`echo $CP1 | sed "s/ /:/g"`
CP2=$SCRIPT_DIR/../src
MAIN_CLASS=$CP2/ClientManager.java
DP=$SCRIPT_DIR/../bin

echo $CP1
echo $CP2

rm -R $DP
mkdir $DP

JAVAC_RUN="$JAVAC -cp $CP1:$CP2 -d $DP $MAIN_CLASS"


echo $JAVAC_RUN
$JAVAC_RUN


