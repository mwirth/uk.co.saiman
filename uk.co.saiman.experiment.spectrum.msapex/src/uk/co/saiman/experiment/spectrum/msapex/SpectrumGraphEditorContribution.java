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
 * This file is part of uk.co.saiman.experiment.spectrum.msapex.
 *
 * uk.co.saiman.experiment.spectrum.msapex is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * uk.co.saiman.experiment.spectrum.msapex is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.co.saiman.experiment.spectrum.msapex;

import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.e4.ui.model.application.ui.MDirtyable;
import org.eclipse.fx.core.di.LocalInstance;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import uk.co.saiman.data.msapex.ContinuousFunctionChartController;
import uk.co.saiman.experiment.ExperimentResult;
import uk.co.saiman.experiment.msapex.ResultEditorContribution;
import uk.co.saiman.experiment.spectrum.Spectrum;
import uk.co.saiman.experiment.spectrum.SpectrumProperties;
import uk.co.strangeskies.eclipse.Localize;
import uk.co.strangeskies.fx.FxmlLoadBuilder;
import uk.co.strangeskies.reflection.token.TypeToken;

@Component(scope = ServiceScope.PROTOTYPE)
public class SpectrumGraphEditorContribution implements ResultEditorContribution<Spectrum> {
	@Inject
	@Localize
	SpectrumProperties properties;

	@Inject
	MDirtyable dirty;

	@Inject
	ExperimentResult<Spectrum> result;

	@FXML
	private ContinuousFunctionChartController spectrumGraphController;
	private Node content;

	@PostConstruct
	public void initialize(@LocalInstance FXMLLoader loader) {
		content = FxmlLoadBuilder.buildWith(loader).controller(this).loadRoot();

		result.addWeakObserver(this, c -> n -> c.setResultData(n));
		setResultData(result.getData());
	}

	private void setResultData(Optional<Spectrum> data) {
		Platform.runLater(() -> {
			spectrumGraphController.getContinuousFunctions().clear();
			data.ifPresent(d -> spectrumGraphController.getContinuousFunctions().add(d.getRawData()));
		});

		dirty.setDirty(true);
	}

	@Override
	public TypeToken<Spectrum> getResultType() {
		return new TypeToken<Spectrum>() {};
	}

	@Override
	public String getName() {
		return properties.spectrumGraphEditor().toString();
	}

	@Override
	public Node getContent() {
		return content;
	}
}
