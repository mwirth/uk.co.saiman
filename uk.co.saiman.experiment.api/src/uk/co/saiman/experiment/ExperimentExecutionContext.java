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
 * This file is part of uk.co.saiman.experiment.api.
 *
 * uk.co.saiman.experiment.api is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * uk.co.saiman.experiment.api is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.co.saiman.experiment;

/**
 * The context of an experiment execution, providing information about the
 * current state, and enabling modification of that state.
 * 
 * @author Elias N Vasylenko
 * @param <T>
 *          the type of the executing node
 */
public interface ExperimentExecutionContext<T> {
	/**
	 * @return the currently executing experiment node
	 */
	ExperimentNode<?, T> node();

	/**
	 * @param resultType
	 *          the type of result
	 * @return the result object now registered to the executing node
	 */
	public <U> ExperimentResult<U> getResult(ExperimentResultType<U> resultType);

	/**
	 * This method provides a target for the submission of results during
	 * execution of an experiment node.
	 * 
	 * @param resultType
	 *          the type of result
	 * @param resultData
	 *          the result
	 * @return the result object now registered to the executing node
	 */
	public <U> ExperimentResult<U> setResult(ExperimentResultType<U> resultType, U resultData);
}
