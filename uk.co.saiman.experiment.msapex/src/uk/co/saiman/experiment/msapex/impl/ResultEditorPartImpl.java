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

import static java.util.stream.Collectors.toList;
import static uk.co.strangeskies.fx.FxmlLoadBuilder.buildWith;
import static uk.co.strangeskies.reflection.ConstraintFormula.Kind.LOOSE_COMPATIBILILTY;

import java.util.List;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.Persist;
import org.eclipse.e4.ui.model.application.ui.MDirtyable;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.fx.core.di.LocalInstance;
import org.osgi.framework.Constants;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import uk.co.saiman.experiment.ExperimentLifecycleState;
import uk.co.saiman.experiment.ExperimentResult;
import uk.co.saiman.experiment.msapex.ResultEditorContribution;
import uk.co.saiman.experiment.msapex.ResultEditorPart;
import uk.co.strangeskies.eclipse.ObservableService;

public class ResultEditorPartImpl<T> implements ResultEditorPart<T> {
	private static final String PROTOTYPE_SERVICE = "(" + Constants.SERVICE_SCOPE + "=" + Constants.SCOPE_PROTOTYPE + ")";

	@Inject
	private IEclipseContext context;
	@Inject
	private MPart part;
	@Inject
	private MDirtyable dirty;

	@FXML
	private TabPane tabPane;

	private final ExperimentResult<T> data;
	private final List<ResultEditorContribution<? super T>> contributions;

	@SuppressWarnings("unchecked")
	@Inject
	public ResultEditorPartImpl(
			ExperimentResult<T> data,
			@ObservableService(target = PROTOTYPE_SERVICE) ObservableList<ResultEditorContribution<?>> editorContributions) {
		this.data = data;
		this.contributions = editorContributions
				.stream()
				.filter(
						contribution -> contribution
								.getResultType()
								.satisfiesConstraintFrom(LOOSE_COMPATIBILILTY, data.getResultType().getDataType()))
				.map(contribution -> (ResultEditorContribution<? super T>) contribution)
				.collect(toList());

		attachToLifecycle();
	}

	private void attachToLifecycle() {
		data.getExperimentNode().lifecycleState().changes().addWeakObserver(
				this,
				part -> c -> part.updateExperimentState(c.previousValue(), c.newValue()));
	}

	@PostConstruct
	protected void buildInterface(BorderPane container, @LocalInstance FXMLLoader loader) {
		container.setCenter(buildWith(loader).controller(ResultEditorPart.class, this).loadRoot());

		initializeContributions();
	}

	/*
	 * Let's not bother making this dynamic unless we find a reason, since Eclipse
	 * applications can't be properly dynamic anyway.
	 */
	private void initializeContributions() {
		for (ResultEditorContribution<? super T> contribution : contributions) {
			ContextInjectionFactory.inject(contribution, context);

			Tab tab = new Tab(contribution.getName(), contribution.getContent());
			tab.setClosable(false);
			tabPane.getTabs().add(tab);
		}
	}

	@Override
	public Stream<ResultEditorContribution<? super T>> getContributions() {
		return contributions.stream();
	}

	@Override
	@Persist
	public void save() {
		// model.saveTodo(todo);
		dirty.setDirty(false);
	}

	@Focus
	public void onFocus() {
		// the following assumes that you have a Text field
		// called summary

		// txtSummary.setFocus();
	}

	@Override
	public ExperimentResult<T> getData() {
		return data;
	}

	protected void updateExperimentState(ExperimentLifecycleState previousState, ExperimentLifecycleState newState) {
		System.out.println(previousState + "    ->    " + newState);
	}

	@Override
	public MPart getPart() {
		return part;
	}

	@PreDestroy
	public void dispose(ResultEditorManager manager) {
		manager.removeEditor(this);
	}
}
