# java-read-comport

Small Maven console app that opens a serial port (default `/dev/ttyACM0`), sends a message (default `ping\n`), and prints the response using jSerialComm.

## Prereqs
- Java 17+
- Maven 3.8+
- Access to the serial device (Linux: add your user to `dialout` and re-login):

```bash
sudo usermod -aG dialout "$USER"
# log out and back in for group change to take effect
```

## Build
```bash
mvn -q -e -DskipTests package
```
This produces a fat jar at `target/java-read-comport-0.1.0-shaded.jar`.

## Quick run
List available ports:
```bash
mvn -q exec:java -Dexec.args="--list"
```
Send ping to `/dev/ttyACM0` at 115200 baud:
```bash
mvn -q exec:java -Dexec.args="--port=/dev/ttyACM0 --baud=115200 --message=ping --timeout=1500"
```
Using the shaded jar:
```bash
java -jar target/java-read-comport-0.1.0-shaded.jar --port=/dev/ttyACM0 --baud=115200 --message=ping
```

## Options
- `--port=/dev/ttyACM0` device to open
- `--baud=115200` baud rate
- `--timeout=1500` read timeout in ms
- `--message=ping` payload to send
- `--newline=true|false` append `\n` to the message (default true)
- `--list` list ports and exit

## Notes
- Some microcontrollers reset when the port opens; the program waits ~200ms before writing.
- If you see a permission error opening the port, verify the device path and your group membership.