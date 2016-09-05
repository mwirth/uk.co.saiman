/*
 * Copyright (C) 2016 Scientific Analysis Instruments Limited <contact@saiman.co.uk>
 *
 * This file is part of uk.co.saiman.simulation.
 *
 * uk.co.saiman.simulation is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * uk.co.saiman.simulation is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.co.saiman.simulation.instrument;

import java.util.Random;

import uk.co.saiman.data.SampledContinuousFunction;

/**
 * A simulation of a signal function derived from a given resolution, depth, and
 * simulated sample point.
 * 
 * @author Elias N Vasylenko
 */
public interface DetectorSimulation {
	public SampledContinuousFunction acquire(Random random, double resolution, int depth, SimulatedSample sample);
}