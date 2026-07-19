package com.gameplatform.client.infrastructure.ui.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.function.Function;

/**
 * Factory di colonne di convenienza utilizzata dalle tabelle
 * {@code LocalAdminDashboard}, {@code GameAdminDashboard} e
 * {@code PlatformAdminDashboard} per costruire colonne basate su
 * stringhe in una singola riga.
 */
public final class TableColumns {

    /**
     * Costruttore privato per prevenire l'istanziazione della classe
     * utility.
     */
    private TableColumns() {}

    /**
     * Aggiunge una colonna a una tabella esistente con il titolo e la
     * funzione di estrazione del valore specificati.
     * <p>
     * La colonna gestisce automaticamente i valori {@code null}
     * restituiti dall'estrattore, convertendoli in stringa vuota.
     *
     * @param <S>    il tipo degli elementi nella tabella
     * @param table  la tabella a cui aggiungere la colonna; non {@code null}
     * @param header il testo dell'intestazione della colonna
     * @param getter funzione che estrae il valore stringa da un elemento
     *               della tabella; può restituire {@code null}
     * @return la colonna {@link TableColumn} appena creata e aggiunta
     * @throws NullPointerException se {@code table} è {@code null}
     */
    public static <S> TableColumn<S, String> addColumn(TableView<S> table,
                                                       String header,
                                                       Function<S, String> getter) {
        TableColumn<S, String> col = new TableColumn<>(header);
        col.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() == null ? "" : nullSafe(getter.apply(c.getValue()))));
        table.getColumns().add(col);
        return col;
    }

    /**
     * Converte un oggetto nella sua rappresentazione stringa,
     * restituendo una stringa vuota se l'oggetto è {@code null}.
     *
     * @param o l'oggetto da convertire; può essere {@code null}
     * @return la rappresentazione stringa di {@code o}, oppure
     *         {@code ""} se {@code o} è {@code null}
     */
    private static String nullSafe(Object o) { return o == null ? "" : o.toString(); }
}