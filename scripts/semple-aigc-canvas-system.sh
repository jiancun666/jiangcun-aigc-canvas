#!/usr/bin/env bash
set -euo pipefail

APP_NAME="semple-aigc-canvas-system"
APP_HOME="${APP_HOME:-$(pwd)}"
APP_JAR="${APP_JAR:-${APP_NAME}.jar}"
JAR_PATH="${APP_HOME}/${APP_JAR}"
PID_DIR="${APP_HOME}/pids"
PID_FILE="${PID_DIR}/${APP_NAME}.pid"
JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:-}"
APP_ARGS="${APP_ARGS:-}"
STOP_TIMEOUT="${STOP_TIMEOUT:-30}"

is_running() {
  local pid="${1:-}"
  [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1
}

read_pid() {
  [[ -f "${PID_FILE}" ]] && tr -d '[:space:]' < "${PID_FILE}"
}

start_service() {
  local pid
  pid="$(read_pid || true)"
  if is_running "${pid}"; then
    echo "${APP_NAME} is already running (PID=${pid})."
    return 0
  fi
  [[ -f "${JAR_PATH}" ]] || { echo "Missing jar: ${JAR_PATH}"; exit 1; }
  mkdir -p "${PID_DIR}"
  cd "${APP_HOME}"
  nohup "${JAVA_BIN}" ${JAVA_OPTS} -jar "${JAR_PATH}" ${APP_ARGS} >/dev/null 2>&1 &
  echo "$!" > "${PID_FILE}"
  echo "Started ${APP_NAME} (PID=$!)."
}

stop_service() {
  local pid waited=0
  pid="$(read_pid || true)"
  if ! is_running "${pid}"; then
    rm -f "${PID_FILE}"
    echo "${APP_NAME} is not running."
    return 0
  fi
  kill "${pid}"
  while is_running "${pid}"; do
    if (( waited >= STOP_TIMEOUT )); then
      kill -9 "${pid}" >/dev/null 2>&1 || true
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
  rm -f "${PID_FILE}"
  echo "Stopped ${APP_NAME}."
}

status_service() {
  local pid
  pid="$(read_pid || true)"
  if is_running "${pid}"; then
    echo "${APP_NAME} is running (PID=${pid})."
  else
    echo "${APP_NAME} is not running."
  fi
}

case "${1:-}" in
  start) start_service ;;
  stop) stop_service ;;
  restart) stop_service; start_service ;;
  status) status_service ;;
  *) echo "Usage: $0 {start|stop|restart|status}"; exit 1 ;;
esac