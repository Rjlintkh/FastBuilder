@echo off
java -Xms512M -Xmx512M -XX:+UseG1GC -jar server.jar nogui
