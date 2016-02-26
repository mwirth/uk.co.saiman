/*
 * Copyright (C) 2016 Scientific Analysis Instruments Limited <contact@saiman.co.uk>
 *
 * This file is part of uk.co.saiman.msapex.acquisition.
 *
 * uk.co.saiman.msapex.acquisition is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * uk.co.saiman.msapex.acquisition is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.co.saiman.msapex.chemistry;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.di.AboutToShow;
import org.eclipse.e4.ui.model.application.ui.menu.ItemType;
import org.eclipse.e4.ui.model.application.ui.menu.MDirectMenuItem;
import org.eclipse.e4.ui.model.application.ui.menu.MMenuElement;
import org.eclipse.e4.ui.model.application.ui.menu.MMenuFactory;
import org.eclipse.e4.ui.workbench.modeling.EPartService;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import uk.co.saiman.chemistry.PeriodicTable;
import uk.co.strangeskies.eclipse.ObservableService;

/**
 * Track periodic tables available through OSGi services and select which table
 * to display in the periodic table part.
 * 
 * @author Elias N Vasylenko
 */
public class PeriodicTablesMenu {
	@Inject
	@ObservableService
	ObservableList<PeriodicTable> periodicTables;

	private PeriodicTablePart periodicTablePart;

	@PostConstruct
	void initialise(EPartService partService) {
		periodicTablePart = (PeriodicTablePart) partService.findPart("uk.co.saiman.msapex.periodictable.part").getObject();

		periodicTables.addListener((ListChangeListener<? super PeriodicTable>) change -> {
			if (periodicTablePart.getPeriodicTableController().getPeriodicTable() == null) {
				while (change.next()) {
					if (change.wasAdded()) {
						periodicTablePart.getPeriodicTableController().setPeriodicTable(change.getAddedSubList().get(0));
						break;
					}
				}
			}
		});

		periodicTables.stream().findAny().ifPresent(periodicTablePart.getPeriodicTableController()::setPeriodicTable);
	}

	@Execute
	static void execute() {
		new Alert(AlertType.INFORMATION, "Hello there").showAndWait();
	}

	@AboutToShow
	void aboutToShow(List<MMenuElement> items) {
		for (PeriodicTable table : new ArrayList<>(periodicTables)) {
			MDirectMenuItem moduleItem = MMenuFactory.INSTANCE.createDirectMenuItem();
			moduleItem.setLabel(table.getName());
			moduleItem.setType(ItemType.RADIO);
			moduleItem.setSelected(periodicTablePart.getPeriodicTableController().getPeriodicTable() == table);
			moduleItem.setObject(new Object() {
				@Execute
				public void execute() {
					if (moduleItem.isSelected()) {
						periodicTablePart.getPeriodicTableController().setPeriodicTable(table);
					}
				}
			});

			items.add(moduleItem);
		}
	}
}