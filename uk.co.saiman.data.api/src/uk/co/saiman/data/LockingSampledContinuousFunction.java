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
 * This file is part of uk.co.saiman.data.api.
 *
 * uk.co.saiman.data.api is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * uk.co.saiman.data.api is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.co.saiman.data;

import javax.measure.Quantity;
import javax.measure.Unit;

import uk.co.strangeskies.mathematics.expression.LockingExpression;

/**
 * A simple abstract partial implementation of a
 * {@link SampledContinuousFunction} which ensures all default method
 * implementations lock for reading.
 * 
 * @param <UD>
 *          the type of the units of measurement of values in the domain
 * @param <UR>
 *          the type of the units of measurement of values in the range
 * @author Elias N Vasylenko
 */
public abstract class LockingSampledContinuousFunction<UD extends Quantity<UD>, UR extends Quantity<UR>>
		extends LockingExpression<ContinuousFunction<UD, UR>> implements SampledContinuousFunction<UD, UR> {
	private final SampledDomain<UD> domain;
	private final Unit<UR> rangeUnit;

	public LockingSampledContinuousFunction(SampledDomain<UD> domain, Unit<UR> range) {
		this.domain = domain;
		this.rangeUnit = range;
	}

	@Override
	public SampledDomain<UD> domain() {
		return domain;
	}

	@Override
	public double sample(double xPosition) {
		return read(() -> SampledContinuousFunction.super.sample(xPosition));
	}

	protected Unit<UR> getRangeUnit() {
		return rangeUnit;
	}

	@Override
	public SampledContinuousFunction<UD, UR> resample(SampledDomain<UD> resolvableSampleDomain) {
		return read(() -> SampledContinuousFunction.super.resample(resolvableSampleDomain));
	}

	@Override
	public ContinuousFunction<UD, UR> decoupleValue() {
		return getValue().copy();
	}

	@Override
	protected ContinuousFunction<UD, UR> evaluate() {
		return this;
	}
}
