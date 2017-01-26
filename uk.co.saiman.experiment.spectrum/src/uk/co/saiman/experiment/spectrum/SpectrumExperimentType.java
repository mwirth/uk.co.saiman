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
 * This file is part of uk.co.saiman.experiment.spectrum.
 *
 * uk.co.saiman.experiment.spectrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * uk.co.saiman.experiment.spectrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.co.saiman.experiment.spectrum;

import static uk.co.strangeskies.utilities.Observable.Observation.CONTINUE;
import static uk.co.strangeskies.utilities.Observable.Observation.TERMINATE;

import java.util.stream.Stream;

import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Time;

import uk.co.saiman.acquisition.AcquisitionDevice;
import uk.co.saiman.experiment.ExperimentExecutionContext;
import uk.co.saiman.experiment.ExperimentResultType;
import uk.co.saiman.experiment.ExperimentType;
import uk.co.strangeskies.text.properties.PropertyLoader;

/**
 * Configure the sample position to perform an experiment at. Typically most
 * other experiment nodes will be descendant to a sample experiment node, such
 * that they operate on the configured sample.
 * 
 * @author Elias N Vasylenko
 *
 * @param <T>
 *          the type of sample configuration for the instrument
 */
public abstract class SpectrumExperimentType<T extends SpectrumConfiguration> implements ExperimentType<T> {
	private SpectrumProperties properties;
	private final ExperimentResultType<DefaultFileSpectrum> spectrumResult;

	public SpectrumExperimentType() {
		this(PropertyLoader.getDefaultProperties(SpectrumProperties.class));
	}

	/*
	 * TODO this parameter really should be injected by DS. Hurry up OSGi r7 to
	 * make this possible...
	 */
	public SpectrumExperimentType(SpectrumProperties properties) {
		this.properties = properties;
		spectrumResult = new FileSpectrumExperimentResultType<T>(this);
	}

	protected void setProperties(SpectrumProperties properties) {
		this.properties = properties;
	}

	protected SpectrumProperties getProperties() {
		return properties;
	}

	@Override
	public String getName() {
		return properties.spectrumExperimentName().toString();
	}

	public ExperimentResultType<? extends Spectrum> getSpectrumResult() {
		return spectrumResult;
	}

	protected abstract AcquisitionDevice getAcquisitionDevice();

	@Override
	public void execute(ExperimentExecutionContext<T> context) {
		getAcquisitionDevice().startEvents().addTerminatingObserver(device -> {
			startAcquisition(context, device);
			return TERMINATE;
		});
		getAcquisitionDevice().startAcquisition();

		context.getResult(spectrumResult).getData().get().save();
	}

	public void startAcquisition(ExperimentExecutionContext<T> context, AcquisitionDevice device) {
		int count = device.getAcquisitionCount();

		AccumulatingContinuousFunction<Time, Dimensionless> accumulator = new AccumulatingContinuousFunction<>(
				device.getSampleDomain(),
				device.getSampleIntensityUnits());

		DefaultFileSpectrum fileSpectrum = new DefaultFileSpectrum(
				accumulator,
				context.node().getExperimentDataPath().resolve("data.spectrum"));

		context.setResult(spectrumResult, fileSpectrum);

		device.dataEvents().addTerminatingObserver(
				a -> (device.isAcquiring() && accumulator.accumulate(a) == count) ? TERMINATE : CONTINUE);
	}

	@Override
	public Stream<ExperimentResultType<?>> getResultTypes() {
		return Stream.of(spectrumResult);
	}
}
