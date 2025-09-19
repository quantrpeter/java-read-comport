package com.example.serial;

import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class SerialPing {
	private record Config(String port, int baud, int dataBits, int stopBits, int parity, Duration timeout,
			String message, boolean listOnly, boolean newline) {
	}

	public static void main(String[] args) throws IOException {
		try {
			Config cfg = parseArgs(args);
			if (cfg.listOnly) {
				listPorts();
				return;
			}

			if (cfg.port == null || cfg.port.isBlank()) {
				System.err.println(
						"Error: --port is required (e.g., --port=/dev/ttyACM0). Use --list to see available ports.");
				System.exit(2);
			}

			SerialPort port = SerialPort.getCommPort(cfg.port);
			port.setComPortParameters(cfg.baud, cfg.dataBits, cfg.stopBits, cfg.parity);
			port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, (int) cfg.timeout.toMillis(),
					(int) cfg.timeout.toMillis());

			System.out.printf("Opening %s @ %d baud...%n", cfg.port, cfg.baud);
			if (!port.openPort()) {
				System.err.printf(
						"Failed to open port %s. Check that it exists and you have permission (dialout group).%n",
						cfg.port);
				System.exit(1);
			}
			try {
				// Optional small delay for Arduino-style reset on open
				try {
					Thread.sleep(200);
				} catch (InterruptedException ignored) {
				}

				byte[] payload = cfg.message.getBytes(StandardCharsets.UTF_8);
				if (cfg.newline) {
					payload = (cfg.message + "\n").getBytes(StandardCharsets.UTF_8);
				}
				System.out.printf("-> %s (%d bytes)%n", Arrays.toString(payload), payload.length);
				int written = port.writeBytes(payload, payload.length);
				System.out.printf("Wrote %d bytes, waiting up to %d ms for response...%n", written,
						(int) cfg.timeout.toMillis());

				byte[] buffer = new byte[1024];
				int totalRead = 0;
				long start = System.currentTimeMillis();
				while (System.currentTimeMillis() - start < cfg.timeout.toMillis() && totalRead < buffer.length) {
					int available = port.bytesAvailable();
					if (available < 0) {
						System.out.println("end");
						break;
					}
					if (available == 0) {
						try {
							Thread.sleep(10);
						} catch (InterruptedException ignored) {
						}
						continue;
					}
					int toRead = Math.min(available, buffer.length - totalRead);
					int read = port.getInputStream().read(buffer, totalRead, toRead);
					if (read > 0) {
						totalRead += read;
					}
				}

				if (totalRead == 0) {
					System.out.println("No response received within timeout.");
				} else {
					String text = new String(buffer, 0, totalRead, StandardCharsets.UTF_8);
					System.out.printf("<- %d bytes: %s%n", totalRead, text);
					StringBuilder hex = new StringBuilder();
					for (int i = 0; i < totalRead; i++) {
						hex.append(String.format("%02X ", buffer[i] & 0xFF));
					}
					System.out.printf("   HEX: %s%n", hex.toString().trim());
				}
			} finally {
				try {
					port.closePort();
				} catch (Exception ignored) {
				}
			}
		} catch (IllegalArgumentException ex) {
			System.err.println("Argument error: " + ex.getMessage());
			printHelp();
			System.exit(2);
		}
	}

	private static Config parseArgs(String[] args) {
		Map<String, String> map = new LinkedHashMap<>();
		for (String a : args) {
			if (a.startsWith("--")) {
				int idx = a.indexOf('=');
				if (idx > 2) {
					map.put(a.substring(2, idx), a.substring(idx + 1));
				} else {
					map.put(a.substring(2), "true");
				}
			}
		}

		if (map.containsKey("help")) {
			printHelp();
			System.exit(0);
		}

		String port = map.getOrDefault("port", "/dev/ttyACM0");
		int baud = parseInt(map.get("baud"), 115200);
		int dataBits = parseInt(map.get("dataBits"), 8);
		int stopBits = parseInt(map.get("stopBits"), SerialPort.ONE_STOP_BIT);
		int parity = parseInt(map.get("parity"), SerialPort.NO_PARITY);
		Duration timeout = Duration.ofMillis(parseInt(map.get("timeout"), 1500));
		String message = map.getOrDefault("message", "ping");
		boolean listOnly = Boolean.parseBoolean(map.getOrDefault("list", "false"));
		boolean newline = Boolean.parseBoolean(map.getOrDefault("newline", "true"));

		return new Config(port, baud, dataBits, stopBits, parity, timeout, message, listOnly, newline);
	}

	private static int parseInt(String val, int def) {
		if (val == null)
			return def;
		try {
			return Integer.parseInt(val);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static void listPorts() {
		SerialPort[] ports = SerialPort.getCommPorts();
		System.out.println("Available serial ports:");
		for (SerialPort p : ports) {
			System.out.printf("- %s (%s) %s%n", p.getSystemPortName(), p.getDescriptivePortName(),
					p.getSystemPortPath());
		}
		if (ports.length == 0) {
			System.out.println("(none detected)");
		}
	}

	private static void printHelp() {
		System.out.println("SerialPing - send a message over a serial port and print the response\n" +
				"Usage: java -jar java-read-comport.jar [--port=/dev/ttyACM0] [--baud=115200] [--timeout=1500] [--message=ping] [--newline=true] [--list]\n"
				+
				"Options:\n" +
				"  --port=PATH         Serial device path (default /dev/ttyACM0)\n" +
				"  --baud=N            Baud rate (default 115200)\n" +
				"  --timeout=MS        Timeout in milliseconds for blocking read (default 1500)\n" +
				"  --message=TEXT      Text to send (default 'ping')\n" +
				"  --newline=true|false Append newline to message (default true)\n" +
				"  --list              List available serial ports and exit\n" +
				"  --help              Show this help\n");
	}
}
