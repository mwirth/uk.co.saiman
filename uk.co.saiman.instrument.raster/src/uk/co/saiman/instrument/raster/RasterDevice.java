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
 * This file is part of uk.co.saiman.instrument.raster.
 *
 * uk.co.saiman.instrument.raster is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * uk.co.saiman.instrument.raster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.co.saiman.instrument.raster;

import java.util.Set;

import uk.co.saiman.instrument.HardwareDevice;
import uk.co.strangeskies.observable.Observable;

public interface RasterDevice extends HardwareDevice {
	Set<RasterPattern> availableRasterModes();

	RasterPattern getRasterPattern();

	void setRasterPattern(RasterPattern mode);

	void setRasterSize(int width, int height);

	int getRasterWidth();

	int getRasterHeight();

	default int getRasterLength() {
		return getRasterHeight() * getRasterWidth();
	}

	void setRasterDwell(int dwell);

	int getRasterDwell();

	void startRasterOperation();

	boolean isOperating();

	RasterPosition getRasterPosition();

	Observable<RasterPosition> rasterPositionEvents();

	Observable<RasterDevice> startEvents();
}
