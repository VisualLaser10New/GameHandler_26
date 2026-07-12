package com.gameplatform.client.infrastructure.ui.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.function.Function;

/**
 * Convenience column factory used by the
 * {@code LocalAdminDashboard}/{@code GameAdminDashboard}/{@code PlatformAdminDashboard}
 * tables to build string-backed columns in one line.
 */
public final class TableColumns {

    private TableColumns() {}

    public static <S> TableColumn<S, String> addColumn(TableView<S> table,
                                                      String header,
                                                      Function<S, String> getter) {
        TableColumn<S, String> col = new TableColumn<>(header);
        col.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() == null ? "" : nullSafe(getter.apply(c.getValue()))));
        table.getColumns().add(col);
        return col;
    }

    private static String nullSafe(Object o) { return o == null ? "" : o.toString(); }
}