/*
 * Copyright (C) 2017 Scientific Analysis Instruments Limited <contact@saiman.co.uk>
 *          ______         ___      ___________
 *       ,'========\     ,'===\    /========== \
 *      /== \___/== \  ,'==.== \   \__/== \___\/
 *     /==_/____\__\/,'==__|== |     /==  /
 *     \========`. ,'========= |    /==  /
 *   ___`-___)== ,'== \____|== |   /==  /
 *  /== \__.-==,'==  ,'    |== '__/==  /_
 *  \======== /==  ,'      |== ========= \
 *   \_____\.-\__\/        \__\\________\/
 *
 * This file is part of uk.co.saiman.comms.copley.
 *
 * uk.co.saiman.comms.copley is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * uk.co.saiman.comms.copley is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.co.saiman.comms.copley.impl;

import static java.util.Optional.of;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;
import static uk.co.saiman.comms.copley.CopleyComms.HEADER_SIZE;
import static uk.co.saiman.comms.copley.VariableBank.ACTIVE;
import static uk.co.saiman.comms.copley.VariableBank.DEFAULT;
import static uk.co.saiman.comms.copley.impl.CopleyCommsImpl.NODE_ID_MASK;
import static uk.co.saiman.comms.copley.impl.CopleyCommsImpl.WORD_SIZE;
import static uk.co.strangeskies.log.Log.Level.ERROR;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import uk.co.saiman.comms.ByteConverters;
import uk.co.saiman.comms.CommsException;
import uk.co.saiman.comms.CommsStream;
import uk.co.saiman.comms.copley.CopleyOperation;
import uk.co.saiman.comms.copley.CopleyVariable;
import uk.co.saiman.comms.copley.VariableBank;
import uk.co.saiman.comms.copley.VariableIdentifier;
import uk.co.saiman.comms.copley.impl.CopleyHardwareSimulation.CopleyHardwareSimulationConfiguration;
import uk.co.saiman.comms.serial.SerialPort;
import uk.co.saiman.comms.serial.SerialPorts;
import uk.co.strangeskies.log.Log;

@Designate(ocd = CopleyHardwareSimulationConfiguration.class, factory = true)
@Component(
		name = CopleyHardwareSimulation.CONFIGURATION_PID,
		configurationPid = CopleyHardwareSimulation.CONFIGURATION_PID,
		immediate = true)
public class CopleyHardwareSimulation {
	static final String CONFIGURATION_PID = "uk.co.saiman.comms.copley.simulation";

	@Reference(cardinality = OPTIONAL, policy = DYNAMIC)
	volatile Log log;

	@SuppressWarnings("javadoc")
	@ObjectClassDefinition(
			id = CONFIGURATION_PID,
			name = "Copley Comms Hardware Simulation Configuration",
			description = "A configuration for a simulation of the Copley motor control interface")
	public @interface CopleyHardwareSimulationConfiguration {
		@AttributeDefinition(
				name = "Serial Port",
				description = "The serial port for the hardware simulation")
		String serialPort();

		@AttributeDefinition(
				name = "Node Number",
				description = "The node number for multi-drop mode dispatch, or 0 for the directly connected node")
		int node()

		default 0;

		@AttributeDefinition(
				name = "Axis Count",
				description = "The number of axes supported by the drive")
		int axes() default 1;
	}

	private class VariableStorage {
		private final List<byte[]> active;
		private final List<byte[]> defaults;

		public VariableStorage(CopleyVariable id) {
			int words;
			switch (id) {
			case ACTUAL_POSITION:
				words = 1;
				break;
			case AMPLIFIER_STATE:
				words = 1;
				break;
			case LATCHED_FAULT_REGISTER:
				words = 1;
				break;
			case POSITION_COMMAND:
				words = 1;
				break;
			case START_HOMING:
				words = 1;
				break;
			case START_MOVE:
				words = 1;
				break;
			case TRAJECTORY_PROFILE_MODE:
				words = 1;
				break;
			default:
				throw new IllegalArgumentException();
			}

			active = new ArrayList<>();
			defaults = new ArrayList<>();
			byte[] bytes = new byte[words * WORD_SIZE];
			for (int i = 0; i < axes; i++) {
				active.add(bytes);
				defaults.add(bytes);
			}
		}

		public byte[] get(int axis, VariableBank bank) {
			switch (bank) {
			case ACTIVE:
				return active.get(axis);
			case DEFAULT:
				return defaults.get(axis);
			default:
				throw new AssertionError();
			}
		}

		public void set(int axis, VariableBank bank, byte[] value) {
			switch (bank) {
			case ACTIVE:
				active.set(axis, value);
				break;
			case DEFAULT:
				defaults.set(axis, value);
				break;
			default:
				throw new AssertionError();
			}
		}

		public void copy(byte axis, VariableBank bank) {
			set(axis, bank, get(axis, bank == ACTIVE ? DEFAULT : ACTIVE));
		}
	}

	@Reference
	ByteConverters converters;

	@Reference(policy = STATIC, policyOption = GREEDY)
	SerialPorts serialPorts;
	private SerialPort port;
	private CommsStream stream;

	private final ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
	private byte currentNode;
	private byte checksum;
	private CopleyOperation operation;
	private ByteBuffer message;

	private int node;
	private int axes;

	private Map<CopleyVariable, VariableStorage> variables = new HashMap<>();

	@Activate
	void activate(CopleyHardwareSimulationConfiguration configuration) throws IOException {
		configure(configuration);
	}

	@Modified
	void configure(CopleyHardwareSimulationConfiguration configuration) throws IOException {
		setPort(configuration.serialPort());
		node = configuration.node();
		axes = configuration.axes();
		variables.clear();
	}

	@Deactivate
	void deactivate() throws IOException {
		closePort();
	}

	private synchronized void setPort(String serialPort) throws IOException {
		closePort();
		port = serialPorts.getPort(serialPort);
		openPort();
	}

	private synchronized void openPort() {
		stream = port.openStream();
		stream.addObserver(buffer -> {
			do {
				boolean onHeader = message == null;
				ByteBuffer currentBuffer = onHeader ? header : message;

				do {
					currentBuffer.put(buffer.get());
				} while (currentBuffer.hasRemaining() && buffer.hasRemaining());

				if (!currentBuffer.hasRemaining()) {
					currentBuffer.flip();

					if (onHeader) {
						receiveHeader();
						if (message.capacity() == 0)
							receiveMessage();
					} else {
						receiveMessage();
					}
				}
			} while (buffer.hasRemaining());
		});
	}

	private synchronized void closePort() throws IOException {
		if (stream != null) {
			stream.close();
			stream = null;
		}
	}

	private void receiveHeader() {
		currentNode = (byte) (header.get() & NODE_ID_MASK);
		checksum = header.get();
		message = ByteBuffer.allocate(header.get() * WORD_SIZE);
		operation = CopleyOperation.getCanonicalOperation(header.get());
		header.clear();
	}

	private void receiveMessage() {
		byte[] result = new byte[] {};

		if (node == currentNode) {
			try {
				VariableIdentifier variable;
				CopleyVariable id;
				switch (operation) {
				case GET_VARIABLE:
					variable = getVariableIdentifier();
					id = CopleyVariable.forCode(variable.variableID);
					result = variables
							.computeIfAbsent(id, VariableStorage::new)
							.get(variable.axis, variable.bank ? DEFAULT : ACTIVE);
					break;
				case SET_VARIABLE:
					variable = getVariableIdentifier();
					id = CopleyVariable.forCode(variable.variableID);
					byte[] value = new byte[message.remaining()];
					message.get(value);
					variables
							.computeIfAbsent(id, VariableStorage::new)
							.set(variable.axis, variable.bank ? DEFAULT : ACTIVE, value);
					break;
				case COPY_VARIABLE:
					variable = getVariableIdentifier();
					id = CopleyVariable.forCode(variable.variableID);
					variables
							.computeIfAbsent(id, VariableStorage::new)
							.copy(variable.axis, variable.bank ? DEFAULT : ACTIVE);
					break;
				case COPLEY_VIRTUAL_MACHINE:
					break;
				case DYNAMIC_FILE_INTERFACE:
					break;
				case ENCODER:
					break;
				case ERROR_LOG:
					break;
				case GET_CAN_OBJECT:
					break;
				case GET_FLASH_CRC:
					break;
				case GET_OPERATING_MODE:
					break;
				case NO_OP:
					break;
				case RESET:
					break;
				case SET_CAN_OBJECT:
					break;
				case SWITCH_OPERATING_MODE:
					break;
				case TRACE_VARIABLE:
					break;
				case TRAJECTORY:
					break;
				default:
					throw new IllegalArgumentException();
				}

				byte checksum = (byte) (CopleyCommsImpl.CHECKSUM ^ result.length);
				for (byte item : result)
					checksum ^= item;

				ByteBuffer response = ByteBuffer.allocate(result.length + HEADER_SIZE);
				response.put((byte) 0);
				response.put(checksum);
				response.put((byte) (result.length / WORD_SIZE));
				response.put((byte) 0);
				response.put(result);
				response.flip();

				stream.write(response);
			} catch (Exception e) {
				of(log).ifPresent(
						l -> l.log(ERROR, new CommsException("Unable to send simulated hardware response", e)));
			}
		}

		message = null;
	}

	private VariableIdentifier getVariableIdentifier() {
		byte[] bytes = new byte[WORD_SIZE];
		message.get(bytes);
		return converters.getConverter(VariableIdentifier.class).fromBytes(bytes);
	}
}
