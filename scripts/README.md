# Service scripts

Each service script supports `start`, `stop`, `restart`, and `status`.

Place the service jar and script in the same deployment directory. By default the script expects `<service-name>.jar`; override it with `APP_JAR` when needed.

```bash
./semple-aigc-canvas-aigc.sh start
./semple-aigc-canvas-aigc.sh status
```

Supported environment variables: `APP_HOME`, `APP_JAR`, `JAVA_BIN`, `JAVA_OPTS`, `APP_ARGS`, and `STOP_TIMEOUT`.