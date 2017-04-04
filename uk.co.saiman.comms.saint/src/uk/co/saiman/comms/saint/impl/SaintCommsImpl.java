package uk.co.saiman.comms.saint.impl;

import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;
import static uk.co.saiman.comms.saint.InOutBlock.inOutBlock;
import static uk.co.saiman.comms.saint.SaintCommandId.SaintCommandAddress.HV_LAT;
import static uk.co.saiman.comms.saint.SaintCommandId.SaintCommandAddress.HV_PORT;
import static uk.co.saiman.comms.saint.SaintCommandId.SaintCommandAddress.LED_LAT;
import static uk.co.saiman.comms.saint.SaintCommandId.SaintCommandAddress.LED_PORT;
import static uk.co.saiman.comms.saint.SaintCommandId.SaintCommandAddress.NULL;
import static uk.co.saiman.comms.saint.SaintCommandId.SaintCommandAddress.VACUUM_LAT;
import static uk.co.saiman.comms.saint.SaintCommandId.SaintCommandAddress.VACUUM_PORT;
import static uk.co.saiman.comms.saint.SaintCommandId.SaintCommandType.INPUT;
import static uk.co.saiman.comms.saint.SaintCommandId.SaintCommandType.OUTPUT;
import static uk.co.saiman.comms.saint.SaintCommandId.SaintCommandType.PING;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import uk.co.saiman.comms.Command;
import uk.co.saiman.comms.CommandSet;
import uk.co.saiman.comms.CommandSetImpl;
import uk.co.saiman.comms.CommsException;
import uk.co.saiman.comms.NamedBits;
import uk.co.saiman.comms.NumberedBits;
import uk.co.saiman.comms.saint.HighVoltageBit;
import uk.co.saiman.comms.saint.InOutBlock;
import uk.co.saiman.comms.saint.OutBlock;
import uk.co.saiman.comms.saint.SaintCommandId;
import uk.co.saiman.comms.saint.SaintCommandId.SaintCommandAddress;
import uk.co.saiman.comms.saint.SaintCommandId.SaintCommandType;
import uk.co.saiman.comms.saint.SaintComms;
import uk.co.saiman.comms.saint.VacuumBit;
import uk.co.saiman.comms.saint.impl.SaintCommsImpl.SaintCommsConfiguration;
import uk.co.saiman.comms.serial.SerialPort;
import uk.co.saiman.comms.serial.SerialPorts;

@Designate(ocd = SaintCommsConfiguration.class, factory = true)
@Component(
		name = SaintCommsImpl.CONFIGURATION_PID,
		configurationPid = SaintCommsImpl.CONFIGURATION_PID)
public class SaintCommsImpl extends CommandSetImpl<SaintCommandId>
		implements SaintComms, CommandSet<SaintCommandId> {
	public static final String CONFIGURATION_PID = "uk.co.saiman.comms.saint";
	public static final int MESSAGE_SIZE = 4;
	private static final String STATUS_LED_PREFIX = "STATUS_LED_";

	@SuppressWarnings("javadoc")
	@ObjectClassDefinition(
			id = CONFIGURATION_PID,
			name = "SAINT Comms Configuration",
			description = "The configuration for the underlying serial comms for a SAINT instrument")
	public @interface SaintCommsConfiguration {
		@AttributeDefinition(name = "Serial Port", description = "The serial port for comms")
		String serialPort();
	}

	@Reference(policy = STATIC, policyOption = GREEDY)
	SerialPorts comms;
	private SerialPort port;

	private InOutBlock<NumberedBits> ledStatus;
	private InOutBlock<NamedBits<VacuumBit>> vacuumStatus;
	private InOutBlock<NamedBits<HighVoltageBit>> highVoltageStatus;

	private OutBlock<Integer> highVoltageDAC1;

	public SaintCommsImpl() {
		super(SaintComms.ID, SaintCommandId.class);
	}

	@Activate
	void activate(SaintCommsConfiguration configuration) throws IOException {
		configure(configuration);

		ledStatus = inOutBlock(
				addOutput(LED_LAT, () -> new NumberedBits(STATUS_LED_PREFIX, 8), b -> b.getBytes()[0]),
				addInput(LED_LAT, b -> new NumberedBits(STATUS_LED_PREFIX, 8, new byte[] { b })),
				addInput(LED_PORT, b -> new NumberedBits(STATUS_LED_PREFIX, 8, new byte[] { b })));

		vacuumStatus = inOutBlock(
				addOutput(VACUUM_LAT, () -> new NamedBits<>(VacuumBit.class), b -> b.getBytes()[0]),
				addInput(VACUUM_LAT, b -> new NamedBits<>(VacuumBit.class, new byte[] { b })),
				addInput(VACUUM_PORT, b -> new NamedBits<>(VacuumBit.class, new byte[] { b })));

		highVoltageStatus = inOutBlock(
				addOutput(HV_LAT, () -> new NamedBits<>(HighVoltageBit.class), b -> b.getBytes()[0]),
				addInput(HV_LAT, b -> new NamedBits<>(HighVoltageBit.class, new byte[] { b })),
				addInput(HV_PORT, b -> new NamedBits<>(HighVoltageBit.class, new byte[] { b })));

		/*-
		highVoltageDAC1LSB = saintOutBlock(
				commandSpace,
				new SaintCommandId(OUTPUT, HV_DAC_1_LSB),
				new SaintCommandId(INPUT, HV_DAC_1_LSB),
				new CommandBytes<byte[]>(byte[]::clone, byte[]::clone, 1));
		highVoltageDAC1MSB = saintOutBlock(
				commandSpace,
				new SaintCommandId(OUTPUT, HV_DAC_1_MSB),
				new SaintCommandId(INPUT, HV_DAC_1_MSB),
				new CommandBytes<byte[]>(byte[]::clone, byte[]::clone, 1));
		highVoltageDAC1 = outBlock(data -> {
			byte[] bytes = allocate(4).putInt(reverse(data)).array();
			highVoltageDAC1LSB.request(new byte[] { (byte) (bytes[0] >> 4) });
			highVoltageDAC1MSB.request(new byte[] { (byte) (bytes[0] << 4 | bytes[1] >> 4) });
		}, () -> reverse(highVoltageDAC1LSB.getRequested()[0] << 4 | highVoltageDAC1MSB.getRequested()[0] >> 4));
		*/
	}

	@Modified
	void configure(SaintCommsConfiguration configuration) throws IOException {
		port = comms.getPort(configuration.serialPort());
		setComms(port);
	}

	@Deactivate
	void deactivate() throws IOException {
		unsetComms();
	}

	@Override
	public InOutBlock<NumberedBits> led() {
		return ledStatus;
	}

	@Override
	public InOutBlock<NamedBits<VacuumBit>> vacuum() {
		return vacuumStatus;
	}

	@Override
	public OutBlock<NamedBits<HighVoltageBit>> highVoltage() {
		return highVoltageStatus;
	}

	@Override
	protected void checkComms() {
		ping();
	}

	private void ping() {
		useChannel(
				channel -> executeSaintCommand(PING, NULL, channel, new byte[NULL.getBytes().length]));
	}

	private <T> Supplier<T> addInput(SaintCommandAddress address, Function<Byte, T> inputFunction) {
		return addMultiInput(address, b -> inputFunction.apply(b[0]));
	}

	private <T> Supplier<T> addMultiInput(
			SaintCommandAddress address,
			Function<byte[], T> inputFunction) {
		Command<SaintCommandId, T, Void> inputCommand = addCommand(
				new SaintCommandId(INPUT, address),
				(output, channel) -> {
					byte[] outputBytes = new byte[address.getBytes().length];
					byte[] inputBytes = executeSaintCommand(INPUT, address, channel, outputBytes);
					return inputFunction.apply(inputBytes);
				},
				() -> null);
		return () -> inputCommand.invoke((Void) null);
	}

	private <T> Consumer<T> addOutput(
			SaintCommandAddress address,
			Supplier<T> prototype,
			Function<T, Byte> outputFunction) {
		return addMultiOutput(address, prototype, b -> new byte[] { outputFunction.apply(b) });
	}

	private <T> Consumer<T> addMultiOutput(
			SaintCommandAddress address,
			Supplier<T> prototype,
			Function<T, byte[]> outputFunction) {
		Command<SaintCommandId, Void, T> outputCommand = addCommand(
				new SaintCommandId(OUTPUT, address),
				(output, channel) -> {
					byte[] outputBytes = outputFunction.apply(output);
					executeSaintCommand(OUTPUT, address, channel, outputBytes);
					return null;
				},
				prototype);
		return outputCommand::invoke;
	}

	private byte[] executeSaintCommand(
			SaintCommandType command,
			SaintCommandAddress address,
			ByteChannel channel,
			byte[] output) {
		ByteBuffer buffer;

		int addressSize = address.getSize();
		byte[] addressBytes = address.getBytes();
		buffer = ByteBuffer.allocate(MESSAGE_SIZE * addressSize);
		for (int i = 0; i < addressSize; i++) {
			buffer.put(command.getByte());
			buffer.put(addressBytes[i]);
			buffer.put((byte) 0);
			buffer.put(output[i]);
		}
		try {
			buffer.flip();
			channel.write(buffer);
		} catch (IOException e) {
			throw port.setFault(new CommsException("Problem dispatching command"));
		}

		int inputRead;
		byte[] inputBytes = new byte[addressSize];
		buffer = ByteBuffer.allocate(MESSAGE_SIZE * addressSize);
		try {
			inputRead = channel.read(buffer);
			buffer.flip();
		} catch (IOException e) {
			throw port.setFault(new CommsException("Problem receiving command response"));
		}
		if (inputRead != MESSAGE_SIZE * addressSize) {
			throw port.setFault(new CommsException("Response too short " + inputRead));
		}
		for (int i = 0; i < addressSize; i++) {
			inputBytes[i] = buffer.get();
			buffer.get();
			buffer.get();
			buffer.get();
		}

		return inputBytes;
	}
}