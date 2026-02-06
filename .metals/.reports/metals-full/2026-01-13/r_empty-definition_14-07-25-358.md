error id: file:///C:/Users/barra/Desktop/Ryan/Perso/JavaProjects/BudgetApp/BudgetApp.java:_empty_/`<any>`#setCellValueFactory#
file:///C:/Users/barra/Desktop/Ryan/Perso/JavaProjects/BudgetApp/BudgetApp.java
empty definition using pc, found symbol in pc: _empty_/`<any>`#setCellValueFactory#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 6803
uri: file:///C:/Users/barra/Desktop/Ryan/Perso/JavaProjects/BudgetApp/BudgetApp.java
text:
```scala
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
    private final DoubleProperty resteApresPrevu = new SimpleDoubleProperty(0);

    private TableView<Depense> tableView;
    private static final String DATA_FILE = "budget_data.txt";

    @Override
    public void start(Stage stage) {
        chargerDonnees();

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double targetWidth = screenBounds.getWidth() * 0.9; 
        double targetHeight = screenBounds.getHeight() * 0.9;

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        root.setTop(createHeader());
        root.setCenter(createCenter());
        root.setBottom(createBottom());

        Scene scene = new Scene(root, targetWidth, targetHeight);
        scene.getStylesheets().add(getStylesheet());

        stage.setTitle("Gestionnaire de Budget - Icônes Épurées");
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
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.getStyleClass().add("title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button btnNouveauMois = new Button("🗓️"); //  Changer de mois
        btnNouveauMois.setTooltip(new Tooltip("Démarrer un nouveau mois"));
        btnNouveauMois.getStyleClass().add("btn-header-icon");
        btnNouveauMois.setOnAction(e -> reinitialiserMois());
        
        topBar.getChildren().addAll(title, spacer, btnNouveauMois);

        FlowPane cardsBox = new FlowPane(12, 12);
        cardsBox.setAlignment(Pos.CENTER);
        cardsBox.getChildren().addAll(
            createBudgetCard("Budget Estimé", budgetEstime),
            createBudgetCard("Budget Réel", budgetReel),
            createResultCard("Reste Estimé", resteEstime),
            createResultCard("Reste Réel (Actuel)", resteReel),
            createResultCard("Reste après Prévus", resteApresPrevu)
        );

        header.getChildren().addAll(topBar, cardsBox);
        return header;
    }

    private VBox createBudgetCard(String label, DoubleProperty property) {
        VBox card = new VBox(6);
        card.getStyleClass().add("budget-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(210);
        card.setPrefHeight(95);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-label");

        TextField field = new TextField(String.format("%.2f", property.get()));
        field.getStyleClass().add("budget-field");
        field.setAlignment(Pos.CENTER);
        field.setPrefHeight(40);
        field.setOnAction(e -> {
            try {
                double value = Double.parseDouble(field.getText().replace(",", "."));
                property.set(value);
                updateCalculs();
                sauvegarderDonnees();
            } catch (NumberFormatException ex) {
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
        card.setPrefWidth(210);
        card.setPrefHeight(95);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-label");

        Label value = new Label(String.format("%.2f €", property.get()));
        value.getStyleClass().add("result-value");

        property.addListener((obs, old, val) -> {
            value.setText(String.format("%.2f €", val.doubleValue()));
            value.setStyle(val.doubleValue() < 0 ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: #10b981;");
        });

        card.getChildren().addAll(lbl, value);
        return card;
    }

    private VBox createCenter() {
        VBox center = new VBox(5);
        center.setPadding(new Insets(10, 20, 10, 20));

        tableView = new TableView<>();
        tableView.setItems(depenses);
        tableView.getStyleClass().add("table");

        TableColumn<Depense, Boolean> colCochee = new TableColumn<>("Payée");
        colCochee.setCellValueFactory(new PropertyValueFactory<>("cochee"));
        colCochee.setPrefWidth(60);
        colCochee.setResizable(false);
        colCochee.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    Depense d = getTableView().getItems().get(getIndex());
                    checkBox.setSelected(item);
                    checkBox.setOnAction(e -> {
                        d.setCochee(checkBox.isSelected());
                        updateCalculs();
                        sauvegarderDonnees();
                    });
                    setGraphic(checkBox);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Depense, String> colNom = new TableColumn<>("Désignation");
        colNom.setCellValue@@Factory(new PropertyValueFactory<>("   nom"));
        colNom.setPrefWidth(350);

        TableColumn<Depense, Double> colMontant = new TableColumn<>("Montant");
        colMontant.setCellValueFactory(new PropertyValueFactory<>("montant"));
        colMontant.setPrefWidth(120);
        colMontant.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(String.format("%.2f €", item));
                    //setAlignment(Pos.CENTER_LEFT);
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });

        TableColumn<Depense, Boolean> colRecurrente = new TableColumn<>("Mensuelle ?");
        colRecurrente.setCellValueFactory(new PropertyValueFactory<>("recurrente"));
        colRecurrente.setPrefWidth(70);
        colRecurrente.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(item ? " *Oui" : " Non");
                    setAlignment(Pos.CENTER);
                    setStyle("-fx-font-size: 14px;");
                }
            }
        });

        TableColumn<Depense, Void> colActions = new TableColumn<>("Supprimer");
        colActions.setPrefWidth(80);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnSuppr = new Button("🗑");
            {
                btnSuppr.setTooltip(new Tooltip("Supprimer cette dépense"));
                btnSuppr.getStyleClass().add("btn-action-delete");
                btnSuppr.setOnAction(e -> {
                    depenses.remove(getTableView().getItems().get(getIndex()));
                    updateCalculs();
                    sauvegarderDonnees();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    setGraphic(btnSuppr);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Depense, Void> colEdit = new TableColumn<>("Modifier Prix");
        colEdit.setPrefWidth(80);
        colEdit.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("✏");
            {
                btnEdit.setTooltip(new Tooltip("Modifier le prix"));
                btnEdit.getStyleClass().add("btn-action-edit");
                btnEdit.setOnAction(e -> {
                    Depense d = getTableView().getItems().get(getIndex());
                    modifierPrix(d);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    setGraphic(btnEdit);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        tableView.getColumns().addAll(colCochee, colNom, colMontant, colRecurrente, colEdit, colActions);
        center.getChildren().add(tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        return center;
    }

    private void modifierPrix(Depense d) {
        TextInputDialog dialog = new TextInputDialog(String.format("%.2f", d.getMontant()).replace(",", "."));
        dialog.setTitle("Modifier");
        dialog.setHeaderText("Mettre à jour : " + d.getNom());
        dialog.setContentText("Montant (€) :");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(montant -> {
            try {
                d.setMontant(Double.parseDouble(montant.replace(",", ".")));
                updateCalculs();
                sauvegarderDonnees();
                tableView.refresh();
            } catch (NumberFormatException e) {
                new Alert(Alert.AlertType.ERROR, "Montant invalide").showAndWait();
            }
        });
    }

    private void reinitialiserMois() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Nouveau Mois");
        alert.setHeaderText("Réinitialisation");
        alert.setContentText("Supprimer les dépenses ponctuelles et décocher les mensuelles ?");

        if (alert.showAndWait().get() == ButtonType.OK) {
            depenses.removeIf(d -> !d.isRecurrente());
            depenses.forEach(d -> d.setCochee(false));
            updateCalculs();
            sauvegarderDonnees();
        }
    }

    private VBox createBottom() {
        VBox bottom = new VBox(8);
        bottom.setPadding(new Insets(15));
        bottom.getStyleClass().add("form-box");

        HBox form = new HBox(12);
        form.setAlignment(Pos.CENTER_LEFT);

        TextField tfNom = new TextField(); 
        tfNom.setPromptText("Désignation (Loyer, Courses...)");
        HBox.setHgrow(tfNom, Priority.ALWAYS);

        TextField tfMontant = new TextField(); 
        tfMontant.setPromptText("Montant €");
        tfMontant.setPrefWidth(150);

        CheckBox cbRecurrente = new CheckBox("Mensuelle");

        Button btnAjouter = new Button("✚");
        btnAjouter.setTooltip(new Tooltip("Ajouter la dépense"));
        btnAjouter.getStyleClass().add("btn-primary-icon");
        btnAjouter.setOnAction(e -> {
            try {
                String nom = tfNom.getText().trim();
                double montant = Double.parseDouble(tfMontant.getText().replace(",", "."));
                if (!nom.isEmpty()) {
                    depenses.add(new Depense(nom, montant, cbRecurrente.isSelected()));
                    tfNom.clear(); tfMontant.clear(); cbRecurrente.setSelected(false);
                    updateCalculs(); 
                    sauvegarderDonnees();
                }
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "Montant invalide").showAndWait();
            }
        });

        form.getChildren().addAll(tfNom, tfMontant, cbRecurrente, btnAjouter);
        bottom.getChildren().addAll(form);
        return bottom;
    }

    private void updateCalculs() {
        double totalGlobal = depenses.stream().mapToDouble(Depense::getMontant).sum();
        double totalPaye = depenses.stream().filter(Depense::isCochee).mapToDouble(Depense::getMontant).sum();
        double totalNonCoche = depenses.stream().filter(d -> !d.isCochee()).mapToDouble(Depense::getMontant).sum();

        resteEstime.set(budgetEstime.get() - totalGlobal);
        resteReel.set(budgetReel.get() - totalPaye);
        resteApresPrevu.set(budgetReel.get() - totalNonCoche);

        FXCollections.sort(depenses, Comparator.comparing(Depense::isCochee));
    }

    private void sauvegarderDonnees() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DATA_FILE))) {
            writer.write("BUDGETS\n" + budgetEstime.get() + "\n" + budgetReel.get() + "\nDEPENSES\n");
            for (Depense d : depenses) {
                writer.write(d.getNom() + "|" + d.getMontant() + "|" + d.isRecurrente() + "|" + d.isCochee() + "\n");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void chargerDonnees() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE))) {
            String line; String section = "";
            while ((line = reader.readLine()) != null) {
                if (line.equals("BUDGETS")) {
                    section = "BUDGETS";
                    budgetEstime.set(Double.parseDouble(reader.readLine()));
                    budgetReel.set(Double.parseDouble(reader.readLine()));
                } else if (line.equals("DEPENSES")) section = "DEPENSES";
                else if (section.equals("DEPENSES") && !line.isEmpty()) {
                    String[] p = line.split("\\|");
                    if(p.length >= 4) {
                        Depense d = new Depense(p[0], Double.parseDouble(p[1]), Boolean.parseBoolean(p[2]));
                        d.setCochee(Boolean.parseBoolean(p[3]));
                        depenses.add(d);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private String getStylesheet() {
        return "data:text/css," +
                ".root { -fx-background-color: #f1f5f9; }" +
                ".header { -fx-background-color: linear-gradient(to right, #4f46e5, #7c3aed); -fx-background-radius: 0 0 12 12; }" +
                ".title { -fx-text-fill: white; }" +
                ".budget-card, .result-card { -fx-background-color: white; -fx-background-radius: 12; -fx-padding: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 6, 0, 0, 3); }" +
                ".card-label { -fx-font-size: 11; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-text-transform: uppercase; -fx-padding: 0 0 5 0; }" +
                ".budget-field { -fx-font-size: 18; -fx-font-weight: bold; -fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-text-fill: #1e293b; }" +
                ".budget-value, .result-value { -fx-font-size: 18; -fx-font-weight: bold; }" +
                ".form-box { -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0; }" +
                ".btn-primary-icon { -fx-background-color: #4f46e5; -fx-text-fill: white; -fx-font-size: 18px; -fx-cursor: hand; -fx-background-radius: 50; -fx-min-width: 40; -fx-min-height: 40; }" +
                ".btn-header-icon { -fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-font-size: 20px; -fx-cursor: hand; -fx-background-radius: 8; -fx-border-color: white; -fx-border-width: 1; }" +
                ".btn-action-delete { -fx-background-color: #fee2e2; -fx-text-fill: #ef4444; -fx-font-size: 16px; -fx-cursor: hand; -fx-background-radius: 5; }" +
                ".btn-action-edit { -fx-background-color: #e0f2fe; -fx-text-fill: #0ea5e9; -fx-font-size: 16px; -fx-cursor: hand; -fx-background-radius: 5; }";
    }

    public static void main(String[] args) { launch(args); }

    public static class Depense {
        private final StringProperty nom;
        private final DoubleProperty montant;
        private final BooleanProperty recurrente;
        private final BooleanProperty cochee;

        public Depense(String nom, double montant, boolean recurrente) {
            this.nom = new SimpleStringProperty(nom);
            this.montant = new SimpleDoubleProperty(montant);
            this.recurrente = new SimpleBooleanProperty(recurrente);
            this.cochee = new SimpleBooleanProperty(false);
        }

        public String getNom() { return nom.get(); }
        public double getMontant() { return montant.get(); }
        public void setMontant(double v) { montant.set(v); }
        public boolean isRecurrente() { return recurrente.get(); }
        public boolean isCochee() { return cochee.get(); }
        public void setCochee(boolean v) { cochee.set(v); }
        public StringProperty nomProperty() { return nom; }
        public DoubleProperty montantProperty() { return montant; }
        public BooleanProperty recurrenteProperty() { return recurrente; }
        public BooleanProperty cocheeProperty() { return cochee; }
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/`<any>`#setCellValueFactory#