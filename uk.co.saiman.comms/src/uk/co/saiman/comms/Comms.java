package uk.co.saiman.comms;

import java.util.Optional;

import uk.co.strangeskies.utilities.ObservableValue;

/**
 * Some sort of comms interface which can be opened as a byte channel.
 * 
 * @author Elias N Vasylenko
 */
public interface Comms {
	/**
	 * A description of the status of a comms channel.
	 * 
	 * @author Elias N Vasylenko
	 */
	enum CommsStatus {
		/**
		 * The byte channel is currently open. Subsequent invocations of
		 * {@link Comms#openChannel()} will fail until the previously opened byte
		 * channel is closed.
		 */
		OPEN,

		/**
		 * The byte channel is ready to be opened. This does not guarantee that a
		 * subsequent invocation of {@link Comms#openChannel()} will succeed, it
		 * simply indicates that it is expected to succeed. If invocation fails, the
		 * comms channel will enter the {@link #FAULT} state.
		 */
		READY,

		/**
		 * There is a problem with the comms channel. If a comms channel is in the
		 * {@link #FAULT} state, then invocation of {@link Comms#openChannel()}
		 * should either clear the fault or throw an exception describing the fault.
		 */
		FAULT
	}

	/**
	 * @return a human readable name for the channel, where available
	 */
	String getDescriptiveName();

	/**
	 * @return the current status of the channel
	 */
	ObservableValue<CommsStatus> status();

	/**
	 * Kill any open streams of channels and reset the comms system to it's basic
	 * disconnected state, attempting to clear any faults in the process.
	 */
	void reset();

	/**
	 * Indicate that the comms object is in a fault state.
	 * 
	 * @param fault
	 *          the new fault state
	 * @return the given fault state
	 */
	CommsException setFault(CommsException fault);

	/**
	 * @return if the comms object is in a fault state an optional containing the
	 *         cause of the fault, otherwise an empty optional
	 */
	Optional<CommsException> getFault();

	/**
	 * Open a byte channel over the comms interface. The caller is responsible for
	 * closing the channel, and has exclusive access to it until this time.
	 * Successive invocations will fail until the previously returned channel is
	 * closed.
	 * 
	 * @return the opened byte channel
	 */
	CommsChannel openChannel();

	default CommsStream openStream() {
		return openStream(0);
	}

	CommsStream openStream(int packetSize);
}
