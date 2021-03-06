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
 * This file is part of uk.co.saiman.experiment.msapex.
 *
 * uk.co.saiman.experiment.msapex is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * uk.co.saiman.experiment.msapex is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.co.saiman.experiment.msapex.impl;

import static uk.co.strangeskies.reflection.ConstraintFormula.Kind.LOOSE_COMPATIBILILTY;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;

import uk.co.saiman.experiment.ExperimentException;
import uk.co.saiman.experiment.ExperimentNode;
import uk.co.saiman.experiment.ExperimentProperties;
import uk.co.strangeskies.eclipse.Localize;
import uk.co.strangeskies.fx.TreeItemData;
import uk.co.strangeskies.fx.TreeItemImpl;
import uk.co.strangeskies.reflection.token.TypeToken;

/**
 * Add an experiment to the workspace.
 * 
 * @author Elias N Vasylenko
 */
public class RunExperiment {
	/**
	 * The ID of the command in the e4 model fragment.
	 */
	public static final String COMMAND_ID = "uk.co.saiman.experiment.msapex.command.runexperiment";

	@Execute
	void execute(MPart part, @Localize ExperimentProperties text) {
		ExperimentPartImpl experimentPart = (ExperimentPartImpl) part.getObject();

		TreeItemImpl<?> item = experimentPart.getExperimentTreeController().getSelection();

		if (item == null) {
			throw new ExperimentException(text.exception().illegalCommandForSelection(COMMAND_ID, null));
		}

		TreeItemData<?> experimentItem = item.getData();

		while (!experimentItem
				.type()
				.satisfiesConstraintTo(LOOSE_COMPATIBILILTY, new TypeToken<ExperimentNode<?, ?>>() {})) {
			experimentItem = experimentItem.parent().orElseThrow(
					() -> new ExperimentException(text.exception().illegalCommandForSelection(COMMAND_ID, item.getData())));
		}

		ExperimentNode<?, ?> experimentNode = (ExperimentNode<?, ?>) experimentItem.data();

		new Thread(experimentNode::process).start();
	}
}
