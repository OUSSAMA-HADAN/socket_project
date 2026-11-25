package client.ui;

import client.model.Conversation;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Left column: my profile, search box and the list of chats.
 * <p>
 * The list shows ChatView's conversations through two "views" stacked on top of each other:
 * a FilteredList (hides chats that don't match the search) and a SortedList (keeps them in
 * {@link Conversation#ORDER}). Both update automatically when the original list changes.
 */
public class Sidebar extends VBox {

    /** The conversation that is open (highlighted in blue). */
    private final ObjectProperty<Conversation> active = new SimpleObjectProperty<>();

    /**
     * @param conversations ChatView's list (the sidebar only reads it)
     * @param onOpen        called when the user clicks a chat
     * @param onLogout      called by the log-out button
     */
    public Sidebar(String me, ObservableList<Conversation> conversations,
                   Consumer<Conversation> onOpen, Runnable onLogout) {
        getStyleClass().add("sidebar");
        setPrefWidth(340);
        setMinWidth(280);
        setMaxWidth(420);

        // --- header with my profile
        Label name = new Label(me);
        name.getStyleClass().add("my-name");
        Label status = new Label("online");
        status.getStyleClass().add("my-status");
        Button logout = new Button();
        logout.setGraphic(Icons.of(Icons.LOGOUT, 22));
        logout.getStyleClass().add("icon-button");
        logout.setTooltip(new Tooltip("Log out"));
        logout.setOnAction(e -> onLogout.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, new Avatar(me, 42), new VBox(1, name, status), spacer, logout);
        header.getStyleClass().add("sidebar-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // --- search
        TextField search = new TextField();
        search.setPromptText("Search");
        search.getStyleClass().add("search-field");
        VBox searchBox = new VBox(search);
        searchBox.getStyleClass().add("search-box");

        // --- list: filtered by the search box, sorted by activity
        FilteredList<Conversation> filtered = new FilteredList<>(conversations);
        // Every time the search text changes, change the filter's rule (its "predicate").
        search.textProperty().addListener((obs, old, query) -> {
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(c -> q.isEmpty() || c.getTitle().toLowerCase(Locale.ROOT).contains(q));
        });
        SortedList<Conversation> sorted = new SortedList<>(filtered, Conversation.ORDER);

        ListView<Conversation> list = new ListView<>(sorted);
        list.getStyleClass().add("conversation-list");
        // A ListView reuses a few "cells" while scrolling; the factory says how to create one.
        list.setCellFactory(lv -> new ConversationCell(me, active, onOpen));
        list.setPlaceholder(new Label("No chats found"));
        VBox.setVgrow(list, Priority.ALWAYS);
        active.addListener((obs, old, now) -> list.refresh());

        getChildren().addAll(header, searchBox, list);
    }

    /** Highlights the chat that ChatView opened. */
    public void setActive(Conversation conversation) {
        active.set(conversation);
    }
}
