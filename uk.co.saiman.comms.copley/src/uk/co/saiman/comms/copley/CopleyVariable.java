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
package uk.co.saiman.comms.copley;

public enum CopleyVariable {
	LATCHED_FAULT_REGISTER(0xA1),
	TRAJECTORY_PROFILE_MODE(0xC8),
	POSITION_COMMAND(0xCA),
	AMPLIFIER_STATE(0x24),
	START_MOVE(0x01),
	START_HOMING(0x02),
	ACTUAL_POSITION(0x17);

	private final int code;

	private CopleyVariable(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static CopleyVariable forCode(byte code) {
		for (CopleyVariable variable : values())
			if (variable.getCode() == code)
				return variable;

		throw new IllegalArgumentException();
	}
}
