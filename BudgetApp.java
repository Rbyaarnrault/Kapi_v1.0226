import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

public class BudgetApp extends Application {

    // --- DONNÉES ---
    private final ObservableList<Depense> depenses = FXCollections.observableArrayList();
    private final ObservableList<Investissement> investissements = FXCollections.observableArrayList();
    
    private final DoubleProperty budgetEstime = new SimpleDoubleProperty(0);
    private final DoubleProperty budgetReel = new SimpleDoubleProperty(0);
    private final DoubleProperty resteEstime = new SimpleDoubleProperty(0);
    private final DoubleProperty resteReel = new SimpleDoubleProperty(0);
    private final DoubleProperty soldeFinMois = new SimpleDoubleProperty(0);

    private StackPane contentArea;
    private TableView<Depense> tableView;
    private static final String DATA_FILE = "budget_data.txt";
    private static final String INVEST_FILE = "invest_data.txt";

    @Override
    public void start(Stage stage) {
        chargerDonnees();
        chargerInvestissements();

        // --- MODIFICATION ICI : Gestion de la taille relative ---
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        
        // On cible 90% de l'écran
        double width = visualBounds.getWidth() * 0.85;
        double height = visualBounds.getHeight() * 0.85;

        // On impose un minimum pour garder l'appli exploitable (ex: 1024x700)
        double minWidth = 1024;
        double minHeight = 700;

        double finalWidth = Math.max(width, minWidth);
        double finalHeight = Math.max(height, minHeight);
        // -------------------------------------------------------

        BorderPane mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("root");
        mainLayout.setLeft(createSidebar());

        contentArea = new StackPane();
        contentArea.getChildren().add(createBudgetView()); 
        mainLayout.setCenter(contentArea);

        // Utilisation des dimensions calculées
        Scene scene = new Scene(mainLayout, finalWidth, finalHeight);
        scene.getStylesheets().add(getStylesheet());

        stage.setTitle("Kapi - Finance & Patrimoine v3.1");
        
        // Sécurité supplémentaire : empêcher de réduire l'appli trop petit manuellement
        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);

        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> {
            sauvegarderDonnees();
            sauvegarderInvestissements();
        });
        updateCalculs();
    }

    // --- NAVIGATION ---

    private VBox createSidebar() {
        VBox sidebar = new VBox(10); // Espacement réduit pour plus de contrôle
        sidebar.setPadding(new Insets(30, 15, 30, 15));
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(260); // Un peu plus large pour respirer

        // Zone Logo avec dégradé textuel
        VBox logoBox = new VBox();
        logoBox.setAlignment(Pos.CENTER);
        logoBox.setPadding(new Insets(0, 0, 30, 0));
        
        Label logo = new Label("KAPI");
        logo.getStyleClass().add("sidebar-logo");
        
        Label subtitle = new Label("Finance & Patrimoine");
        subtitle.setStyle("-fx-text-fill: #6366f1; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-transform: uppercase; -fx-letter-spacing: 2px;");
        
        logoBox.getChildren().addAll(logo, subtitle);

        // Boutons de navigation
        Button btnBudget = createSidebarBtn("💰  Mon Budget");
        btnBudget.setOnAction(e -> switchView(createBudgetView()));
        // Appliquer le style actif par défaut au premier bouton
        btnBudget.getStyleClass().add("sidebar-btn-active");

        Button btnInvest = createSidebarBtn("📈  Investissements");
        btnInvest.setOnAction(e -> {
            switchView(createInvestView());
            // Logique pour changer le style actif au clic
            resetSidebarStyles(sidebar);
            btnInvest.getStyleClass().add("sidebar-btn-active");
        });
        
        btnBudget.setOnAction(e -> {
            switchView(createBudgetView());
            resetSidebarStyles(sidebar);
            btnBudget.getStyleClass().add("sidebar-btn-active");
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Section Profil en bas (Optionnel, pour le look)
        VBox profileBox = new VBox();
        profileBox.getStyleClass().add("sidebar-profile");
        Label userLabel = new Label("Utilisateur");
        userLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        profileBox.getChildren().add(userLabel);

        sidebar.getChildren().addAll(logoBox, btnBudget, btnInvest, spacer, profileBox);
        return sidebar;
    }

    private void resetSidebarStyles(VBox sidebar) {
        // On retire la classe active de tous les enfants qui sont des boutons
        sidebar.getChildren().forEach(node -> {
            if (node instanceof Button) {
                node.getStyleClass().remove("sidebar-btn-active");
            }
        });
    }

    private Button createSidebarBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("sidebar-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setCursor(javafx.scene.Cursor.HAND);
        return b;
    }

    private void switchView(Node view) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(view);
    }

    // --- VUE 1 : BUDGET ---

    private VBox createBudgetView() {
        VBox view = new VBox(0);
        view.getChildren().addAll(createHeader(), createCenter(), createBottom());
        return view;
    }

    // --- VUE 2 : INVESTISSEMENTS (PRO) ---

    private ScrollPane createInvestView() {
        VBox container = new VBox(25);
        container.setPadding(new Insets(30));
        container.setStyle("-fx-background-color: #f8fafc;");

        // Dashboard Header
        HBox dashHeader = new HBox();
        dashHeader.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Mon Patrimoine");
        title.getStyleClass().add("invest-title");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button btnAddInv = new Button("✚ Nouvel Investissement");
        btnAddInv.getStyleClass().add("btn-add-invest");
        btnAddInv.setOnAction(e -> ouvrirDialogInvest(null));
        dashHeader.getChildren().addAll(title, sp, btnAddInv);

        // Cards de résumé
        double totalInv = investissements.stream().mapToDouble(Investissement::getMontantTotal).sum();
        FlowPane summaryCards = new FlowPane(15, 15);
        summaryCards.getChildren().addAll(
            createStatCard("Total Investi", String.format("%.2f €", totalInv), "#4f46e5"),
            createStatCard("Actifs Détenus", String.valueOf(investissements.size()), "#7c3aed")
        );

        // Zone Graphiques
        HBox chartRow = new HBox(20);
        chartRow.getChildren().addAll(createInvestPieChart(), createInvestLineChart());

        // Table
        TableView<Investissement> investTable = createInvestTable();

        container.getChildren().addAll(dashHeader, summaryCards, chartRow, new Label("Détail des actifs"), investTable);
        
        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private VBox createStatCard(String label, String value, String color) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(15));
        card.setPrefSize(200, 80);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 5, 0, 0, 2);");
        Label l = new Label(label); l.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12;");
        Label v = new Label(value); v.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 20; -fx-font-weight: bold;");
        card.getChildren().addAll(l, v);
        return card;
    }

    private PieChart createInvestPieChart() {
        PieChart pc = new PieChart();
        pc.setTitle("Répartition du Portefeuille");
        pc.getStyleClass().add("chart-white");
        
        var dataMap = investissements.stream()
            .collect(Collectors.groupingBy(Investissement::getType, Collectors.summingDouble(Investissement::getMontantTotal)));
        
        dataMap.forEach((type, total) -> pc.getData().add(new PieChart.Data(type, total)));
        return pc;
    }

    private LineChart<String, Number> createInvestLineChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<String, Number> lc = new LineChart<>(xAxis, yAxis);
        lc.setTitle("Projection (6 mois)");
        lc.getStyleClass().add("chart-white");

        XYChart.Series<String, Number> projection = new XYChart.Series<>();
        projection.setName("Évolution Prévue");

        double totalMensuel = investissements.stream().mapToDouble(Investissement::getInvestMensuel).sum();
        double totalActuel = investissements.stream().mapToDouble(Investissement::getMontantTotal).sum();

        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yy");

        for(int i = 0; i < 7; i++) {
            projection.getData().add(new XYChart.Data<>(now.plusMonths(i).format(fmt), totalActuel + (totalMensuel * i)));
        }

        lc.getData().add(projection);
        return lc;
    }

    private TableView<Investissement> createInvestTable() {
        TableView<Investissement> table = new TableView<>(investissements);
        table.setPrefHeight(300);

        TableColumn<Investissement, String> colNom = new TableColumn<>("Actif");
        colNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        
        TableColumn<Investissement, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));

        TableColumn<Investissement, Double> colTotal = new TableColumn<>("Total Investi");
        colTotal.setCellValueFactory(new PropertyValueFactory<>("montantTotal"));

        TableColumn<Investissement, Double> colMensuel = new TableColumn<>("DCA Mensuel");
        colMensuel.setCellValueFactory(new PropertyValueFactory<>("investMensuel"));

        table.getColumns().addAll(colNom, colType, colTotal, colMensuel);
        return table;
    }

    private void ouvrirDialogInvest(Investissement target) {
        Dialog<Investissement> dialog = new Dialog<>();
        dialog.setTitle("Gestion d'Actif");
        ButtonType saveBtn = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        TextField tfNom = new TextField(); 
        ComboBox<String> cbType = new ComboBox<>(FXCollections.observableArrayList("Crypto", "ETF", "Action", "Épargne", "Autre"));
        TextField tfTotal = new TextField(); 
        TextField tfMensuel = new TextField();

        grid.add(new Label("Nom de l'actif:"), 0, 0); grid.add(tfNom, 1, 0);
        grid.add(new Label("Type:"), 0, 1); grid.add(cbType, 1, 1);
        grid.add(new Label("Capital Actuel (€):"), 0, 2); grid.add(tfTotal, 1, 2);
        grid.add(new Label("DCA Mensuel (€):"), 0, 3); grid.add(tfMensuel, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(b -> {
            if (b == saveBtn) {
                return new Investissement(tfNom.getText(), cbType.getValue(), 
                    Double.parseDouble(tfTotal.getText()), Double.parseDouble(tfMensuel.getText()));
            }
            return null;
        });

        dialog.showAndWait().ifPresent(inv -> {
            investissements.add(inv);
            switchView(createInvestView()); // Refresh
        });
    }

    // --- LOGIQUE BUDGET (HEADERS/CALCULS) ---

    private VBox createHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(20));
        header.getStyleClass().add("header");

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("💰 Mon Budget");
        title.getStyleClass().add("title");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
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
            } catch (Exception ex) { field.setText(String.format("%.2f", property.get())); }
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

        tableView = new TableView<>(depenses);
        tableView.getStyleClass().add("table");

        // 1. Colonne Inclusion (Mode Vacances)
        TableColumn<Depense, Boolean> colActive = new TableColumn<>("Inc.");
        colActive.setCellValueFactory(new PropertyValueFactory<>("active"));
        colActive.setPrefWidth(50);
        colActive.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    Depense d = getTableView().getItems().get(getIndex());
                    cb.setSelected(item);
                    cb.setOnAction(e -> { d.setActive(cb.isSelected()); updateCalculs(); sauvegarderDonnees(); });
                    setGraphic(cb); setAlignment(Pos.CENTER);
                }
            }
        });

        // 2. Colonne Payée (Cochée)
        TableColumn<Depense, Boolean> colCochee = new TableColumn<>("Payée");
        colCochee.setCellValueFactory(new PropertyValueFactory<>("cochee"));
        colCochee.setPrefWidth(60);
        colCochee.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    Depense d = getTableView().getItems().get(getIndex());
                    cb.setSelected(item);
                    cb.setDisable(!d.isActive());
                    cb.setOnAction(e -> { d.setCochee(cb.isSelected()); updateCalculs(); sauvegarderDonnees(); });
                    setGraphic(cb); setAlignment(Pos.CENTER);
                }
            }
        });

        // 3. Désignation
        TableColumn<Depense, String> colNom = new TableColumn<>("Désignation");
        colNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colNom.setPrefWidth(350);

        // 4. Montant
        TableColumn<Depense, Double> colMontant = new TableColumn<>("Montant");
        colMontant.setCellValueFactory(new PropertyValueFactory<>("montant"));
        colMontant.setPrefWidth(120);
        colMontant.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.format("%.2f €", item));
                setAlignment(Pos.CENTER);
                if (!empty) setStyle("-fx-font-weight: bold;");
            }
        });

        // 5. Récurrente (RETOUR)
        TableColumn<Depense, Boolean> colRec = new TableColumn<>("Mensuel");
        colRec.setCellValueFactory(new PropertyValueFactory<>("recurrente"));
        colRec.setPrefWidth(80);
        colRec.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setText(null);
                else {
                    setText(item ? "🔄 Oui" : "📍 Non");
                    setAlignment(Pos.CENTER);
                    setStyle("-fx-font-size: 11px;");
                }
            }
        });

        // 6. Actions (RETOUR)
        TableColumn<Depense, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(120);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnE = new Button("✏");
            private final Button btnS = new Button("🗑");
            private final HBox box = new HBox(10, btnE, btnS);
            {
                btnE.getStyleClass().add("btn-action-edit");
                btnS.getStyleClass().add("btn-action-delete");
                box.setAlignment(Pos.CENTER);
                btnE.setOnAction(e -> modifierDepense(getTableView().getItems().get(getIndex())));
                btnS.setOnAction(e -> {
                    depenses.remove(getTableView().getItems().get(getIndex()));
                    updateCalculs(); sauvegarderDonnees();
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // Style de ligne pour l'opacité (Mode Vacances)
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Depense item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null) setOpacity(item.isActive() ? 1.0 : 0.4);
            }
        });

        tableView.getColumns().addAll(colActive, colCochee, colNom, colMontant, colRec, colActions);
        center.getChildren().add(tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        return center;
    }

    private void modifierDepense(Depense d) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Modifier la dépense");
        dialog.setHeaderText("Modification de : " + d.getNom());

        // Appliquer le style au dialogue pour rester dans le thème
        dialog.getDialogPane().getStylesheets().add(getStylesheet());
        dialog.getDialogPane().getStyleClass().add("root");

        ButtonType saveBtn = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        TextField tfNom = new TextField(d.getNom());
        TextField tfMontant = new TextField(String.valueOf(d.getMontant()));
        CheckBox cbRec = new CheckBox("Dépense Mensuelle (Récurrente)");
        cbRec.setSelected(d.isRecurrente());

        grid.add(new Label("Désignation :"), 0, 0);
        grid.add(tfNom, 1, 0);
        grid.add(new Label("Montant (€) :"), 0, 1);
        grid.add(tfMontant, 1, 1);
        grid.add(cbRec, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // Focus sur le nom par défaut
        javafx.application.Platform.runLater(tfNom::requestFocus);

        dialog.setResultConverter(b -> b == saveBtn);

        dialog.showAndWait().ifPresent(success -> {
            if (success) {
                try {
                    d.setNom(tfNom.getText());
                    d.setMontant(Double.parseDouble(tfMontant.getText().replace(",", ".")));
                    d.setRecurrente(cbRec.isSelected());
                    
                    updateCalculs();
                    sauvegarderDonnees();
                    tableView.refresh(); // Crucial pour voir les changements immédiatement
                } catch (NumberFormatException ex) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Le montant n'est pas valide.");
                    alert.showAndWait();
                }
            }
        });
    }

    private void updateCalculs() {
        double totalGlobalActif = depenses.stream().filter(Depense::isActive).mapToDouble(Depense::getMontant).sum();
        double totalPayeActif = depenses.stream().filter(d -> d.isActive() && d.isCochee()).mapToDouble(Depense::getMontant).sum();
        resteEstime.set(budgetEstime.get() - totalGlobalActif);
        resteReel.set(budgetReel.get() - totalPayeActif);
        soldeFinMois.set(budgetReel.get() - totalGlobalActif);
    }

    private VBox createBottom() {
        HBox form = new HBox(12); form.setPadding(new Insets(15)); form.getStyleClass().add("form-box");
        TextField tfN = new TextField(); tfN.setPromptText("Nom..."); HBox.setHgrow(tfN, Priority.ALWAYS);
        TextField tfM = new TextField(); tfM.setPromptText("Montant €");
        Button btnA = new Button("✚"); btnA.getStyleClass().add("btn-primary-icon");
        btnA.setOnAction(e -> {
            try {
                depenses.add(new Depense(tfN.getText(), Double.parseDouble(tfM.getText().replace(",", ".")), true));
                updateCalculs(); sauvegarderDonnees();
            } catch (Exception ex) {}
        });
        form.getChildren().addAll(tfN, tfM, btnA);
        return new VBox(form);
    }

    // --- PERSISTENCE ---

    private void sauvegarderInvestissements() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(INVEST_FILE))) {
            for (Investissement i : investissements) {
                writer.write(i.getNom() + "|" + i.getType() + "|" + i.getMontantTotal() + "|" + i.getInvestMensuel() + "\n");
            }
        } catch (IOException e) {}
    }

    private void chargerInvestissements() {
        File f = new File(INVEST_FILE); if (!f.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(INVEST_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\|");
                investissements.add(new Investissement(p[0], p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3])));
            }
        } catch (Exception e) {}
    }

    private void chargerDonnees() {
        File f = new File(DATA_FILE); if (!f.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE))) {
            String line; String sec = "";
            while ((line = reader.readLine()) != null) {
                if (line.equals("BUDGETS")) {
                    sec = "BUDGETS"; budgetEstime.set(Double.parseDouble(reader.readLine())); budgetReel.set(Double.parseDouble(reader.readLine()));
                } else if (line.equals("DEPENSES")) sec = "DEPENSES";
                else if (sec.equals("DEPENSES") && !line.isEmpty()) {
                    String[] p = line.split("\\|");
                    Depense d = new Depense(p[0], Double.parseDouble(p[1]), Boolean.parseBoolean(p[2]));
                    d.setCochee(Boolean.parseBoolean(p[3]));
                    if (p.length > 4) d.setActive(Boolean.parseBoolean(p[4]));
                    depenses.add(d);
                }
            }
        } catch (Exception e) {}
    }

    private void sauvegarderDonnees() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DATA_FILE))) {
            writer.write("BUDGETS\n" + budgetEstime.get() + "\n" + budgetReel.get() + "\nDEPENSES\n");
            for (Depense d : depenses) {
                writer.write(d.getNom() + "|" + d.getMontant() + "|" + d.isRecurrente() + "|" + d.isCochee() + "|" + d.isActive() + "\n");
            }
        } catch (IOException e) {}
    }

    private void reinitialiserMois() {
        depenses.removeIf(d -> !d.isRecurrente());
        depenses.forEach(d -> { d.setCochee(false); d.setActive(true); });
        updateCalculs(); sauvegarderDonnees();
    }

    private String getStylesheet() {
        return "data:text/css," +
                ".root { -fx-background-color: #f1f5f9; }" +
                ".sidebar { " +
                "  -fx-background-color: #0f172a; " + // Bleu nuit profond
                "  -fx-border-color: rgba(255, 255, 255, 0.05); " +
                "  -fx-border-width: 0 1 0 0; " +
                "}" +
                
                /* Logo et Titre Sidebar */
                ".sidebar-logo { " +
                "  -fx-text-fill: linear-gradient(to bottom right, #38bdf8, #818cf8); " +
                "  -fx-font-size: 32px; " +
                "  -fx-font-weight: bold; " +
                "}" +
                
                /* Boutons Sidebar */
                ".sidebar-btn { " +
                "  -fx-background-color: transparent; " +
                "  -fx-text-fill: #94a3b8; " +
                "  -fx-font-size: 14px; " +
                "  -fx-font-weight: bold; " +
                "  -fx-padding: 14 20; " +
                "  -fx-background-radius: 12; " +
                "}" +
                ".sidebar-btn:hover { " +
                "  -fx-background-color: rgba(255, 255, 255, 0.05); " +
                "  -fx-text-fill: white; " +
                "}" +
                ".sidebar-btn-active { " +
                "  -fx-background-color: linear-gradient(to right, rgba(79, 70, 229, 0.2), transparent); " +
                "  -fx-text-fill: #38bdf8; " +
                "  -fx-border-color: #38bdf8; " +
                "  -fx-border-width: 0 0 0 4; " +
                "}" +
                ".header { -fx-background-color: linear-gradient(to right, #4f46e5, #7c3aed); -fx-background-radius: 15; -fx-margin: 10; }" +
                ".title { -fx-text-fill: white; }" +
                ".invest-title { -fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #1e293b; }" +
                ".btn-add-invest { -fx-background-color: #4f46e5; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10 20; }" +
                ".chart-white { -fx-background-color: white; -fx-background-radius: 12; -fx-padding: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 10, 0, 0, 5); }" +
                ".budget-card, .result-card { -fx-background-color: white; -fx-background-radius: 12; -fx-padding: 10; }" +
                ".card-label { -fx-font-size: 10; -fx-text-fill: #64748b; -fx-font-weight: bold; }" +
                ".budget-field { -fx-font-size: 16; -fx-font-weight: bold; -fx-background-color: transparent; -fx-border-color: #e2e8f0; -fx-border-radius: 4; }" +
                ".result-value { -fx-font-size: 16; -fx-font-weight: bold; }" +
                ".btn-primary-icon { -fx-background-color: #4f46e5; -fx-text-fill: white; -fx-background-radius: 50; -fx-min-width: 40; }" +
                ".btn-header-icon { -fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-border-color: white; -fx-border-radius: 5; }" + 
                ".btn-action-edit { -fx-background-color: #e0f2fe; -fx-text-fill: #0ea5e9; -fx-cursor: hand; -fx-background-radius: 5; }" +
                ".btn-action-delete { -fx-background-color: #fee2e2; -fx-text-fill: #ef4444; -fx-cursor: hand; -fx-background-radius: 5; }" ;
    }

    public static void main(String[] args) { launch(args); }

    // --- CLASSES DE DONNÉES ---

    public static class Depense {
        private final StringProperty nom;
        private final DoubleProperty montant;
        private final BooleanProperty recurrente;
        private final BooleanProperty cochee;
        private final BooleanProperty active;

        public Depense(String nom, double montant, boolean recurrente) {
            this.nom = new SimpleStringProperty(nom);
            this.montant = new SimpleDoubleProperty(montant);
            this.recurrente = new SimpleBooleanProperty(recurrente);
            this.cochee = new SimpleBooleanProperty(false);
            this.active = new SimpleBooleanProperty(true);
        }
        public String getNom() { return nom.get(); }
        public void setNom(String v) { nom.set(v); }
        public double getMontant() { return montant.get(); }
        public void setMontant(double v) { montant.set(v); }
        public boolean isRecurrente() { return recurrente.get(); }
        public void setRecurrente(boolean v) { recurrente.set(v); }
        public boolean isCochee() { return cochee.get(); }
        public void setCochee(boolean v) { cochee.set(v); }
        public boolean isActive() { return active.get(); }
        public void setActive(boolean v) { active.set(v); }
        public StringProperty nomProperty() { return nom; }
        public DoubleProperty montantProperty() { return montant; }
    }

    public static class Investissement {
        private final StringProperty nom;
        private final StringProperty type;
        private final DoubleProperty montantTotal;
        private final DoubleProperty investMensuel;

        public Investissement(String nom, String type, double total, double mensuel) {
            this.nom = new SimpleStringProperty(nom);
            this.type = new SimpleStringProperty(type);
            this.montantTotal = new SimpleDoubleProperty(total);
            this.investMensuel = new SimpleDoubleProperty(mensuel);
        }
        public String getNom() { return nom.get(); }
        public String getType() { return type.get(); }
        public double getMontantTotal() { return montantTotal.get(); }
        public double getInvestMensuel() { return investMensuel.get(); }
        public StringProperty nomProperty() { return nom; }
        public StringProperty typeProperty() { return type; }
        public DoubleProperty montantTotalProperty() { return montantTotal; }
        public DoubleProperty investMensuelProperty() { return investMensuel; }
    }
}