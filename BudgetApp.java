import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.*;
import java.util.Comparator;
import java.util.Optional;

public class BudgetApp extends Application {

    private final ObservableList<Depense> depenses = FXCollections.observableArrayList();
    private final DoubleProperty budgetEstime = new SimpleDoubleProperty(0);
    private final DoubleProperty budgetReel = new SimpleDoubleProperty(0);
    private final DoubleProperty resteEstime = new SimpleDoubleProperty(0);
    private final DoubleProperty resteReel = new SimpleDoubleProperty(0);
    private final DoubleProperty soldeFinMois = new SimpleDoubleProperty(0);

    private TableView<Depense> tableView;
    private static final String DATA_FILE = "budget_data.txt";

    @Override
    public void start(Stage stage) {
        chargerDonnees();

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        root.setTop(createHeader());
        root.setCenter(createCenter());
        root.setBottom(createBottom());

        Scene scene = new Scene(root, screenBounds.getWidth() * 0.9, screenBounds.getHeight() * 0.9);
        scene.getStylesheets().add(getStylesheet());

        stage.setTitle("Gestionnaire de Budget - v4.4 (Mode Vacances)");
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> sauvegarderDonnees());
        updateCalculs();
    }

    private VBox createHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(15));
        header.getStyleClass().add("header");

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("💰 Mon Budget");
        title.getStyleClass().add("title");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnNouveauMois = new Button("🗓️ Nouveau mois");
        btnNouveauMois.getStyleClass().add("btn-header-icon");
        btnNouveauMois.setOnAction(e -> reinitialiserMois());

        topBar.getChildren().addAll(title, spacer, btnNouveauMois);

        FlowPane cardsBox = new FlowPane(12, 12);
        cardsBox.setAlignment(Pos.CENTER);
        cardsBox.getChildren().addAll(
                createBudgetCard("Budget Estimé", budgetEstime),
                createBudgetCard("Budget Réel (Initial)", budgetReel),
                createResultCard("Reste Estimé (Théo)", resteEstime),
                createResultCard("Argent Actuel Compte", resteReel),
                createResultCard("Solde Prévu Fin Mois", soldeFinMois));

        header.getChildren().addAll(topBar, cardsBox);
        return header;
    }

    private VBox createBudgetCard(String label, DoubleProperty property) {
        VBox card = new VBox(6);
        card.getStyleClass().add("budget-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(190);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-label");
        TextField field = new TextField(String.format("%.2f", property.get()));
        field.getStyleClass().add("budget-field");
        field.setAlignment(Pos.CENTER);
        field.setOnAction(e -> {
            try {
                property.set(Double.parseDouble(field.getText().replace(",", ".")));
                updateCalculs();
                sauvegarderDonnees();
            } catch (Exception ex) {
                field.setText(String.format("%.2f", property.get()));
            }
        });
        card.getChildren().addAll(lbl, field);
        return card;
    }

    private VBox createResultCard(String label, DoubleProperty property) {
        VBox card = new VBox(6);
        card.getStyleClass().add("result-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(190);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-label");
        Label val = new Label();
        val.getStyleClass().add("result-value");
        property.addListener((obs, o, v) -> {
            val.setText(String.format("%.2f €", v.doubleValue()));
            val.setStyle(v.doubleValue() < 0 ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: #10b981;");
        });
        val.setText(String.format("%.2f €", property.get()));
        card.getChildren().addAll(lbl, val);
        return card;
    }

    private VBox createCenter() {
        VBox center = new VBox(5);
        center.setPadding(new Insets(10, 20, 10, 20));

        tableView = new TableView<>();
        tableView.setItems(depenses);
        tableView.getStyleClass().add("table");

        // COLONNE ACTIVER / DÉSACTIVER (Pour vos vacances)
        TableColumn<Depense, Boolean> colActive = new TableColumn<>("Inclusion");
        colActive.setCellValueFactory(new PropertyValueFactory<>("active"));
        colActive.setPrefWidth(80);
        colActive.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setGraphic(null);
                else {
                    Depense d = getTableView().getItems().get(getIndex());
                    cb.setSelected(item);
                    cb.setTooltip(new Tooltip("Décocher pour ignorer cette dépense ce mois-ci"));
                    cb.setOnAction(e -> {
                        d.setActive(cb.isSelected());
                        updateCalculs();
                        sauvegarderDonnees();
                        getTableRow().setOpacity(d.isActive() ? 1.0 : 0.4);
                    });
                    setGraphic(cb);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Depense, Boolean> colCochee = new TableColumn<>("Payée");
        colCochee.setCellValueFactory(new PropertyValueFactory<>("cochee"));
        colCochee.setPrefWidth(60);
        colCochee.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setGraphic(null);
                else {
                    Depense d = getTableView().getItems().get(getIndex());
                    cb.setSelected(item);
                    cb.setDisable(!d.isActive()); // On ne peut pas payer une dépense ignorée
                    cb.setOnAction(e -> {
                        d.setCochee(cb.isSelected());
                        updateCalculs();
                        sauvegarderDonnees();
                    });
                    setGraphic(cb);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Depense, String> colNom = new TableColumn<>("Désignation");
        colNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colNom.setPrefWidth(300);

        TableColumn<Depense, Double> colMontant = new TableColumn<>("Montant");
        colMontant.setCellValueFactory(new PropertyValueFactory<>("montant"));
        colMontant.setPrefWidth(100);
        colMontant.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.format("%.2f €", item));
                setAlignment(Pos.CENTER);
            }
        });

        TableColumn<Depense, Boolean> colRec = new TableColumn<>("Mensuel");
        colRec.setCellValueFactory(new PropertyValueFactory<>("recurrente"));
        colRec.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item ? "🔄" : "📍"));
                setAlignment(Pos.CENTER);
            }
        });

        TableColumn<Depense, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(120);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnE = new Button("✏");
            private final Button btnS = new Button("🗑");
            private final HBox box = new HBox(8, btnE, btnS);
            {
                btnE.getStyleClass().add("btn-action-edit");
                btnS.getStyleClass().add("btn-action-delete");
                box.setAlignment(Pos.CENTER);
                btnE.setOnAction(e -> modifierDepense(getTableView().getItems().get(getIndex())));
                btnS.setOnAction(e -> {
                    depenses.remove(getTableView().getItems().get(getIndex()));
                    updateCalculs();
                    sauvegarderDonnees();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // Style des lignes pour les dépenses désactivées
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Depense item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null)
                    setOpacity(item.isActive() ? 1.0 : 0.4);
            }
        });

        tableView.getColumns().addAll(colActive, colCochee, colNom, colMontant, colRec, colActions);
        center.getChildren().add(tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        return center;
    }

    private void modifierDepense(Depense d) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Modifier");
        ButtonType saveBtn = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        TextField en = new TextField(d.getNom());
        TextField em = new TextField(String.valueOf(d.getMontant()));
        CheckBox er = new CheckBox("Mensuel");
        er.setSelected(d.isRecurrente());
        grid.add(new Label("Nom:"), 0, 0);
        grid.add(en, 1, 0);
        grid.add(new Label("Montant:"), 0, 1);
        grid.add(em, 1, 1);
        grid.add(er, 1, 2);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(b -> b == saveBtn);
        dialog.showAndWait().ifPresent(r -> {
            if (r) {
                d.setNom(en.getText());
                d.setMontant(Double.parseDouble(em.getText().replace(",", ".")));
                d.setRecurrente(er.isSelected());
                updateCalculs();
                sauvegarderDonnees();
                tableView.refresh();
            }
        });
    }

    private void updateCalculs() {
        // Seules les dépenses ACTIVES sont comptées
        double totalGlobalActif = depenses.stream()
                .filter(Depense::isActive)
                .mapToDouble(Depense::getMontant).sum();

        double totalPayeActif = depenses.stream()
                .filter(d -> d.isActive() && d.isCochee())
                .mapToDouble(Depense::getMontant).sum();

        resteEstime.set(budgetEstime.get() - totalGlobalActif);
        resteReel.set(budgetReel.get() - totalPayeActif);
        soldeFinMois.set(budgetReel.get() - totalGlobalActif);

        FXCollections.sort(depenses, Comparator.comparing(Depense::isCochee));
    }

    private void reinitialiserMois() {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Réinitialiser le mois ? (Les dépenses ignorées seront réactivées)");
        if (a.showAndWait().get() == ButtonType.OK) {
            depenses.removeIf(d -> !d.isRecurrente());
            depenses.forEach(d -> {
                d.setCochee(false);
                d.setActive(true); // On réactive tout pour le nouveau mois
            });
            updateCalculs();
            sauvegarderDonnees();
        }
    }

    private VBox createBottom() {
        HBox form = new HBox(12);
        form.setPadding(new Insets(15));
        form.getStyleClass().add("form-box");
        form.setAlignment(Pos.CENTER_LEFT);
        TextField tfN = new TextField();
        tfN.setPromptText("Nom...");
        HBox.setHgrow(tfN, Priority.ALWAYS);
        TextField tfM = new TextField();
        tfM.setPromptText("Montant €");
        tfM.setPrefWidth(100);
        CheckBox cbR = new CheckBox("Mensuel");
        Button btnA = new Button("✚");
        btnA.getStyleClass().add("btn-primary-icon");
        btnA.setOnAction(e -> {
            try {
                depenses.add(new Depense(tfN.getText(), Double.parseDouble(tfM.getText().replace(",", ".")),
                        cbR.isSelected()));
                tfN.clear();
                tfM.clear();
                cbR.setSelected(false);
                updateCalculs();
                sauvegarderDonnees();
            } catch (Exception ex) {
            }
        });
        form.getChildren().addAll(tfN, tfM, cbR, btnA);
        return new VBox(form);
    }

    private void sauvegarderDonnees() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DATA_FILE))) {
            writer.write("BUDGETS\n" + budgetEstime.get() + "\n" + budgetReel.get() + "\nDEPENSES\n");
            for (Depense d : depenses) {
                writer.write(d.getNom() + "|" + d.getMontant() + "|" + d.isRecurrente() + "|" + d.isCochee() + "|"
                        + d.isActive() + "\n");
            }
        } catch (IOException e) {
        }
    }

    private void chargerDonnees() {
        File f = new File(DATA_FILE);
        if (!f.exists())
            return;
        try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE))) {
            String line;
            String sec = "";
            while ((line = reader.readLine()) != null) {
                if (line.equals("BUDGETS")) {
                    sec = "BUDGETS";
                    budgetEstime.set(Double.parseDouble(reader.readLine()));
                    budgetReel.set(Double.parseDouble(reader.readLine()));
                } else if (line.equals("DEPENSES"))
                    sec = "DEPENSES";
                else if (sec.equals("DEPENSES") && !line.isEmpty()) {
                    String[] p = line.split("\\|");
                    Depense d = new Depense(p[0], Double.parseDouble(p[1]), Boolean.parseBoolean(p[2]));
                    d.setCochee(Boolean.parseBoolean(p[3]));
                    if (p.length > 4)
                        d.setActive(Boolean.parseBoolean(p[4]));
                    depenses.add(d);
                }
            }
        } catch (Exception e) {
        }
    }

    private String getStylesheet() {
        return "data:text/css," +
                ".root { -fx-background-color: #f1f5f9; }" +
                ".header { -fx-background-color: linear-gradient(to right, #4f46e5, #7c3aed); -fx-background-radius: 0 0 12 12; }"
                +
                ".title { -fx-text-fill: white; }" +
                ".budget-card, .result-card { -fx-background-color: white; -fx-background-radius: 12; -fx-padding: 10; }"
                +
                ".card-label { -fx-font-size: 10; -fx-text-fill: #64748b; -fx-font-weight: bold; }" +
                ".budget-field { -fx-font-size: 16; -fx-font-weight: bold; -fx-background-color: transparent; -fx-border-color: #e2e8f0; -fx-border-radius: 4; }"
                +
                ".result-value { -fx-font-size: 16; -fx-font-weight: bold; }" +
                ".btn-primary-icon { -fx-background-color: #4f46e5; -fx-text-fill: white; -fx-background-radius: 50; -fx-min-width: 40; }"
                +
                ".btn-header-icon { -fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-border-color: white; -fx-border-radius: 5; }"
                +
                ".btn-action-delete { -fx-text-fill: #ef4444; } .btn-action-edit { -fx-text-fill: #0ea5e9; }";
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static class Depense {
        private final StringProperty nom;
        private final DoubleProperty montant;
        private final BooleanProperty recurrente;
        private final BooleanProperty cochee;
        private final BooleanProperty active; // NOUVEAU

        public Depense(String nom, double montant, boolean recurrente) {
            this.nom = new SimpleStringProperty(nom);
            this.montant = new SimpleDoubleProperty(montant);
            this.recurrente = new SimpleBooleanProperty(recurrente);
            this.cochee = new SimpleBooleanProperty(false);
            this.active = new SimpleBooleanProperty(true);
        }

        public String getNom() {
            return nom.get();
        }

        public void setNom(String v) {
            nom.set(v);
        }

        public double getMontant() {
            return montant.get();
        }

        public void setMontant(double v) {
            montant.set(v);
        }

        public boolean isRecurrente() {
            return recurrente.get();
        }

        public void setRecurrente(boolean v) {
            recurrente.set(v);
        }

        public boolean isCochee() {
            return cochee.get();
        }

        public void setCochee(boolean v) {
            cochee.set(v);
        }

        public boolean isActive() {
            return active.get();
        }

        public void setActive(boolean v) {
            active.set(v);
        }

        public StringProperty nomProperty() {
            return nom;
        }

        public DoubleProperty montantProperty() {
            return montant;
        }

        public BooleanProperty activeProperty() {
            return active;
        }
    }
}