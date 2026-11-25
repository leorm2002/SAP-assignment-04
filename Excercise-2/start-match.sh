#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/dttt"
DIR="$(pwd)"

mvn -q install

osascript -e "tell app \"Terminal\" to do script \"cd '$DIR' && mvn -q -pl server/gameengine exec:exec\""
sleep 2
osascript -e "tell app \"Terminal\" to do script \"cd '$DIR' && mvn -q -pl client/game exec:exec\""
osascript -e "tell app \"Terminal\" to do script \"cd '$DIR' && mvn -q -pl client/game exec:exec\""