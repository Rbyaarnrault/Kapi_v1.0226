import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BudgetApp extends Application {

    // --- DONNÉES ---
    private final ObservableList<Depense> depenses = FXCollections.observableArrayList();
    private final ObservableList<Investissement> investissements = FXCollections.observableArrayList();
    private final DoubleProperty budgetEstime = new SimpleDoubleProperty(0);
    private final DoubleProperty budgetReel = new SimpleDoubleProperty(0);
    private final DoubleProperty resteEstime = new SimpleDoubleProperty(0);
    private final DoubleProperty resteReel = new SimpleDoubleProperty(0);
    private final DoubleProperty soldeFinMois = new SimpleDoubleProperty(0);
    private final DoubleProperty capaciteInvestMensuelle = new SimpleDoubleProperty(0);

    private StackPane contentArea;
    private TableView<Depense> tableViewBudget;
    private static final String BUDGET_FILE = "budget_data.txt";
    private static final String INVEST_FILE = "invest_data.txt";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    @Override
    public void start(Stage stage) {
        chargerDonnees();
        chargerInvestissements();

        BorderPane mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("root");
        mainLayout.setLeft(createSidebar());

        contentArea = new StackPane();
        contentArea.getChildren().add(createBudgetView()); 
        mainLayout.setCenter(contentArea);

        javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(mainLayout, screen.getWidth() * 0.9, screen.getHeight() * 0.9);
        scene.getStylesheets().add(getStylesheet());

        stage.setTitle("Kapi - Finance Intelligence v3.4.2");
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> { sauvegarderDonnees(); sauvegarderInvestissements(); });
        updateCalculs();
    }

    // --- SIDEBAR & NAVIGATION ---
    private VBox createSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(30, 15, 30, 15));
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(260);

        VBox logoBox = new VBox();
        logoBox.setAlignment(Pos.CENTER);
        logoBox.setPadding(new Insets(0, 0, 30, 0));
        Label logo = new Label("KAPI");
        logo.getStyleClass().add("sidebar-logo");
        Label sub = new Label("STRATÉGIE LONG TERME");
        sub.setStyle("-fx-text-fill: #6366f1; -fx-font-size: 9px; -fx-font-weight: bold; -fx-letter-spacing: 1px;");
        logoBox.getChildren().addAll(logo, sub);

        Button btnBudget = createSidebarBtn("💰 Mon Budget");
        btnBudget.getStyleClass().add("sidebar-btn-active");
        btnBudget.setOnAction(e -> { switchView(createBudgetView()); updateActiveBtn(sidebar, btnBudget); });

        Button btnInvest = createSidebarBtn("📈 Mon Patrimoine");
        btnInvest.setOnAction(e -> { switchView(createInvestView()); updateActiveBtn(sidebar, btnInvest); });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().addAll(logoBox, btnBudget, btnInvest, spacer);
        return sidebar;
    }

    private Button createSidebarBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("sidebar-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    private void updateActiveBtn(VBox sidebar, Button active) {
        sidebar.getChildren().forEach(n -> n.getStyleClass().remove("sidebar-btn-active"));
        active.getStyleClass().add("sidebar-btn-active");
    }

    private void switchView(Node view) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(view);
    }

    // --- VUE BUDGET ---
    private VBox createBudgetView() {
        VBox view = new VBox(0);
        view.getChildren().addAll(createHeader(), createBudgetTableSection(), createBudgetForm());
        return view;
    }

    private VBox createHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(20));
        header.getStyleClass().add("header");
        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("📅 Gestion du mois");
        title.getStyleClass().add("title");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button btnReset = new Button("🗓️ Nouveau mois");
        btnReset.getStyleClass().add("btn-header-icon");
        btnReset.setOnAction(e -> reinitialiserMois());
        topBar.getChildren().addAll(title, sp, btnReset);

        FlowPane cards = new FlowPane(12, 12);
        cards.setAlignment(Pos.CENTER);
        cards.getChildren().addAll(
                createCard("Budget Estimé", budgetEstime, true),
                createCard("Budget Réel", budgetReel, true),
                createCard("Reste Théorique", resteEstime, false),
                createCard("Argent Actuel", resteReel, false),
                createCard("Prévu Fin Mois", soldeFinMois, false)
        );
        header.getChildren().addAll(topBar, cards);
        return header;
    }

    private VBox createCard(String label, DoubleProperty prop, boolean isEditable) {
        VBox card = new VBox(5);
        card.getStyleClass().add("budget-card");
        card.setPrefWidth(180);
        card.setAlignment(Pos.CENTER);
        Label l = new Label(label); l.getStyleClass().add("card-label");
        if(isEditable) {
            TextField f = new TextField(String.format("%.2f", prop.get()));
            f.getStyleClass().add("budget-field");
            f.setAlignment(Pos.CENTER);
            f.setOnAction(e -> {
                try {
                    prop.set(Double.parseDouble(f.getText().replace(",", ".")));
                    updateCalculs(); sauvegarderDonnees();
                } catch(Exception ex) { f.setText(String.format("%.2f", prop.get())); }
            });
            card.getChildren().addAll(l, f);
        } else {
            Label v = new Label(); v.getStyleClass().add("result-value");
            prop.addListener((obs, o, val) -> {
                v.setText(String.format("%.2f €", val.doubleValue()));
                v.setStyle(val.doubleValue() < 0 ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: #10b981;");
            });
            v.setText(String.format("%.2f €", prop.get()));
            card.getChildren().addAll(l, v);
        }
        return card;
    }

    private VBox createBudgetTableSection() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15, 25, 15, 25));
        tableViewBudget = new TableView<>(depenses);
        tableViewBudget.getStyleClass().add("table");

        TableColumn<Depense, Boolean> colActive = new TableColumn<>("Inc.");
        colActive.setCellValueFactory(new PropertyValueFactory<>("active"));
        colActive.setPrefWidth(50);
        colActive.setCellFactory(c -> new TableCell<>(){
            private final CheckBox cb = new CheckBox();
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if(empty) setGraphic(null);
                else {
                    Depense d = getTableView().getItems().get(getIndex());
                    cb.setSelected(item);
                    cb.setOnAction(e -> { d.setActive(cb.isSelected()); updateCalculs(); sauvegarderDonnees(); });
                    setGraphic(cb); setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Depense, String> colNom = new TableColumn<>("Désignation");
        colNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colNom.setPrefWidth(350);

        TableColumn<Depense, Double> colM = new TableColumn<>("Montant");
        colM.setCellValueFactory(new PropertyValueFactory<>("montant"));
        colM.setCellFactory(c -> new TableCell<>(){
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if(!empty) { setText(String.format("%.2f €", item)); setAlignment(Pos.CENTER); setStyle("-fx-font-weight: bold;"); }
                else setText(null);
            }
        });

        TableColumn<Depense, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(120);
        colActions.setCellFactory(c -> new TableCell<>(){
            private final Button btnE = new Button("✏");
            private final Button btnS = new Button("🗑");
            private final HBox hb = new HBox(10, btnE, btnS);
            {
                btnE.getStyleClass().add("btn-action-edit");
                btnS.getStyleClass().add("btn-action-delete");
                hb.setAlignment(Pos.CENTER);
                btnE.setOnAction(e -> BudgetApp.this.modifierDepense(getTableView().getItems().get(getIndex())));
                btnS.setOnAction(e -> { depenses.remove(getTableView().getItems().get(getIndex())); updateCalculs(); sauvegarderDonnees(); });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : hb);
            }
        });

        tableViewBudget.getColumns().addAll(colActive, colNom, colM, colActions);
        box.getChildren().add(tableViewBudget);
        VBox.setVgrow(tableViewBudget, Priority.ALWAYS);
        return box;
    }

    private HBox createBudgetForm() {
        HBox form = new HBox(12);
        form.setPadding(new Insets(15, 25, 25, 25));
        form.setAlignment(Pos.CENTER_LEFT);
        TextField tfN = new TextField(); tfN.setPromptText("Nom dépense..."); HBox.setHgrow(tfN, Priority.ALWAYS);
        TextField tfM = new TextField(); tfM.setPromptText("Montant €"); tfM.setPrefWidth(100);
        CheckBox cbR = new CheckBox("Mensuel");
        Button btnA = new Button("✚ Ajouter"); btnA.getStyleClass().add("btn-primary-icon");
        btnA.setOnAction(e -> {
            try {
                depenses.add(new Depense(tfN.getText(), Double.parseDouble(tfM.getText().replace(",", ".")), cbR.isSelected()));
                tfN.clear(); tfM.clear(); updateCalculs(); sauvegarderDonnees();
            } catch(Exception ex){}
        });
        form.getChildren().addAll(tfN, tfM, cbR, btnA);
        return form;
    }

    // --- VUE PATRIMOINE ---
    private ScrollPane createInvestView() {
        VBox container = new VBox(25);
        container.setPadding(new Insets(30));
        container.setStyle("-fx-background-color: #f8fafc;");

        HBox topRow = new HBox(20);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("📈 Analyse Patrimoniale Globale");
        title.setStyle("-fx-font-size: 26; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        
        Button btnSync = new Button("🔄 Sync. Marché (Long Terme)");
        btnSync.getStyleClass().add("btn-header-icon");
        btnSync.setStyle("-fx-background-color: #4f46e5;");
        btnSync.setOnAction(e -> investissements.forEach(this::actualiserRendementLongTerme));

        VBox invBox = new VBox(5);
        Label lblCap = new Label("Épargne mensuelle (€) :");
        lblCap.setStyle("-fx-font-size: 11; -fx-text-fill: #64748b;");
        TextField tfCap = new TextField(String.format("%.2f", capaciteInvestMensuelle.get()));
        tfCap.setPrefWidth(100);
        tfCap.setOnAction(e -> {
            capaciteInvestMensuelle.set(Double.parseDouble(tfCap.getText().replace(",", ".")));
            sauvegarderInvestissements(); switchView(createInvestView());
        });
        invBox.getChildren().addAll(lblCap, tfCap);

        Button btnAdd = new Button("✚ Nouvel Actif");
        btnAdd.getStyleClass().add("btn-add-invest");
        btnAdd.setOnAction(e -> ouvrirDialogInvest());

        topRow.getChildren().addAll(title, sp, btnSync, invBox, btnAdd);

        double totalValeur = investissements.stream().mapToDouble(i -> i.getParts() * i.getPrixActuel()).sum();
        double totalInvesti = investissements.stream().mapToDouble(Investissement::getCapitalInvesti).sum();
        double benefice = totalValeur - totalInvesti;

        HBox hero = new HBox(20);
        hero.getChildren().addAll(
            createHeroCard("PATRIMOINE TOTAL", totalValeur + investissements.stream().mapToDouble(Investissement::getCashReserve).sum(), "#1e293b"),
            createHeroCard("BÉNÉFICE NET", benefice, benefice >= 0 ? "#10b981" : "#ef4444")
        );

        TableView<Investissement> table = createInvestTable();
        LineChart<String, Number> chart = createProjectionLongTerme();

        container.getChildren().addAll(topRow, hero, new Label("Portefeuille Actif"), table, chart);
        return new ScrollPane(container);
    }

    private VBox createHeroCard(String title, double value, String color) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(400, 130);
        card.setStyle("-fx-background-color: "+color+"; -fx-background-radius: 20;");
        Label t = new Label(title); t.setStyle("-fx-text-fill: rgba(255,255,255,0.6); -fx-font-weight: bold;");
        Label v = new Label(String.format("%.2f €", value)); v.setStyle("-fx-text-fill: white; -fx-font-size: 38; -fx-font-weight: bold;");
        card.getChildren().addAll(t, v);
        return card;
    }

    private TableView<Investissement> createInvestTable() {
        TableView<Investissement> table = new TableView<>(investissements);
        table.setPrefHeight(400);
        table.getStyleClass().add("table");

        TableColumn<Investissement, String> colEnv = new TableColumn<>("Enveloppe");
        colEnv.setCellValueFactory(new PropertyValueFactory<>("enveloppe"));

        TableColumn<Investissement, String> colNom = new TableColumn<>("Actif (Ticker)");
        colNom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNom() + " (" + c.getValue().getTicker() + ")"));
        colNom.setPrefWidth(220);

        TableColumn<Investissement, Double> colRend = new TableColumn<>("Rend. CAGR (Lissé)");
        colRend.setCellValueFactory(new PropertyValueFactory<>("rendementAnnuel"));
        colRend.setCellFactory(c -> new TableCell<>(){
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if(!empty) { setText(String.format("%.2f %%", item * 100)); setStyle("-fx-text-fill: #4f46e5; -fx-font-weight: bold;"); }
                else setText(null);
            }
        });

        TableColumn<Investissement, Double> colCash = new TableColumn<>("Poche Cash");
        colCash.setCellValueFactory(new PropertyValueFactory<>("cashReserve"));
        colCash.setCellFactory(c -> new TableCell<>(){
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if(empty) setText(null);
                else {
                    Investissement inv = getTableView().getItems().get(getIndex());
                    setText(String.format("%.2f €", item));
                    if(item >= inv.getPrixActuel() && inv.getPrixActuel() > 0) 
                        setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-font-weight: bold;");
                    else setStyle("");
                }
            }
        });

        TableColumn<Investissement, Void> colActions = new TableColumn<>("Achat");
        colActions.setCellFactory(c -> new TableCell<>(){
            private final Button btn = new Button("DCA Part");
            {
                btn.setStyle("-fx-font-size: 10;");
                btn.setOnAction(e -> {
                    Investissement inv = getTableView().getItems().get(getIndex());
                    if(inv.getCashReserve() >= inv.getPrixActuel()) {
                        inv.setParts(inv.getParts() + 1);
                        inv.setCashReserve(inv.getCashReserve() - inv.getPrixActuel());
                        inv.setCapitalInvesti(inv.getCapitalInvesti() + inv.getPrixActuel());
                        sauvegarderInvestissements(); table.refresh();
                    }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if(!empty) {
                    Investissement inv = getTableView().getItems().get(getIndex());
                    btn.setDisable(inv.getCashReserve() < inv.getPrixActuel());
                    setGraphic(btn);
                } else setGraphic(null);
            }
        });

        table.getColumns().addAll(colEnv, colNom, colRend, colCash, colActions);
        return table;
    }

    private LineChart<String, Number> createProjectionLongTerme() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Projection Patrimoine Horizon 30 ans (Simulation lissée)");
        chart.getStyleClass().add("chart-white");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Capitalisation");

        LocalDate d = LocalDate.now();
        // Simulation sur 30 ans
        for(int an=0; an<=30; an++) {
            double totalAn = 0;
            for(Investissement inv : investissements) {
                double r = inv.getRendementAnnuel();
                double cap = (inv.getParts() * inv.getPrixActuel()) + inv.getCashReserve();
                double pmtAn = (inv.getAllocation() / 100.0) * capaciteInvestMensuelle.get() * 12;

                double future;
                if(inv.isAccumulant() && r > 0) {
                    future = cap * Math.pow(1 + r, an) + pmtAn * ((Math.pow(1 + r, an) - 1) / r);
                } else {
                    future = cap + (cap * r * an) + (pmtAn * an);
                }
                totalAn += future;
            }
            series.getData().add(new XYChart.Data<>(String.valueOf(d.getYear() + an), totalAn));
        }
        chart.getData().add(series);
        chart.setPrefHeight(350);
        return chart;
    }

    // --- YAHOO ENGINE v3.4.2 ---
    private void actualiserRendementLongTerme(Investissement inv) {
        if(inv.getTicker().isEmpty()) return;
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + inv.getTicker() + "?range=max&interval=1mo";
            
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", USER_AGENT).build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    String b = response.body();
                    double current = extraireValeur(b, "regularMarketPrice");
                    Pattern p = Pattern.compile("\"adjclose\":\\[\\{\"adjclose\":\\[([\\d.]+),");
                    Matcher m = p.matcher(b);
                    double firstPrice = 0; if(m.find()) firstPrice = Double.parseDouble(m.group(1));
                    Pattern pDate = Pattern.compile("\"timestamp\":\\[(\\d+),");
                    Matcher mDate = pDate.matcher(b);
                    long startTs = 0; if(mDate.find()) startTs = Long.parseLong(mDate.group(1));

                    if(current > 0 && firstPrice > 0 && startTs > 0) {
                        double years = (System.currentTimeMillis() / 1000.0 - startTs) / (365.25 * 24 * 3600);
                        double cagr = Math.pow(current / firstPrice, 1.0 / years) - 1;
                        final double finalPrix = current; final double finalCagr = cagr;
                        javafx.application.Platform.runLater(() -> { inv.setPrixActuel(finalPrix); inv.setRendementAnnuel(finalCagr); });
                    }
                });
        } catch(Exception e) {}
    }

    private double extraireValeur(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\":\\s*([\\d.]+)");
        Matcher m = p.matcher(json);
        if(m.find()) return Double.parseDouble(m.group(1));
        return 0;
    }

    private String extraireChaine(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\":\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if(m.find()) return m.group(1);
        return "";
    }

    // --- FORMULAIRES ---
    private void ouvrirDialogInvest() {
        Dialog<Investissement> diag = new Dialog<>();
        diag.setTitle("Nouvel Actif Stratégique");
        ButtonType btnAdd = new ButtonType("Ajouter", ButtonBar.ButtonData.OK_DONE);
        diag.getDialogPane().getButtonTypes().addAll(btnAdd, ButtonType.CANCEL);

        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(20));
        TextField tfN = new TextField(); TextField tfT = new TextField(); tfT.setPromptText("ex: CW8.PA");
        Label lblMsg = new Label(""); lblMsg.setStyle("-fx-text-fill: #6366f1; -fx-font-size: 10;");
        Button btnCheck = new Button("🔍 Tester & Remplir");
        
        ComboBox<String> cbE = new ComboBox<>(FXCollections.observableArrayList("PEA", "Livret A", "Crypto", "Assurance-Vie"));
        TextField tfP = new TextField("0"); TextField tfPU = new TextField("0");
        TextField tfAl = new TextField("0");
        CheckBox cbAcc = new CheckBox("Actif Accumulant (Intérêts composés)"); cbAcc.setSelected(true);

        btnCheck.setOnAction(e -> {
            HttpClient.newHttpClient().sendAsync(
                HttpRequest.newBuilder().uri(URI.create("https://query1.finance.yahoo.com/v8/finance/chart/" + tfT.getText())).header("User-Agent", USER_AGENT).build(), 
                HttpResponse.BodyHandlers.ofString())
            .thenAccept(r -> javafx.application.Platform.runLater(() -> {
                String b = r.body();
                boolean ok = b.contains("regularMarketPrice");
                if (ok) {
                    lblMsg.setText("✅ Ticker validé et rempli");
                    lblMsg.setStyle("-fx-text-fill: #10b981;");
                    double p = extraireValeur(b, "regularMarketPrice");
                    String n = extraireChaine(b, "longName");
                    if (n.isEmpty()) n = extraireChaine(b, "shortName");
                    if (p > 0) tfPU.setText(String.format("%.2f", p));
                    if (!n.isEmpty()) tfN.setText(n);
                } else {
                    lblMsg.setText("⚠️ Ticker non trouvé");
                    lblMsg.setStyle("-fx-text-fill: #ef4444;");
                }
            }));
        });

        g.add(new Label("Nom Actif:"),0,0); g.add(tfN,1,0);
        g.add(new Label("Ticker Yahoo:"),0,1); g.add(tfT,1,1); g.add(btnCheck, 2, 1);
        g.add(lblMsg, 1, 2, 2, 1);
        g.add(new Label("Enveloppe:"),0,3); g.add(cbE,1,3);
        g.add(new Label("Parts:"),0,4); g.add(tfP,1,4);
        g.add(new Label("Prix Actuel (€):"),0,5); g.add(tfPU,1,5);
        g.add(new Label("% Allocation:"),0,6); g.add(tfAl,1,6);
        g.add(cbAcc, 1, 7);

        diag.getDialogPane().setContent(g);
        diag.setResultConverter(b -> {
            if(b == btnAdd) {
                Investissement i = new Investissement(tfN.getText(), tfT.getText(), cbE.getValue(), Double.parseDouble(tfP.getText()), Double.parseDouble(tfPU.getText().replace(",", ".")), Double.parseDouble(tfAl.getText()), cbAcc.isSelected());
                i.setCapitalInvesti(i.getParts() * i.getPrixActuel());
                return i;
            }
            return null;
        });

        diag.showAndWait().ifPresent(inv -> { investissements.add(inv); actualiserRendementLongTerme(inv); sauvegarderInvestissements(); switchView(createInvestView()); });
    }

    private void modifierDepense(Depense d) {
        Dialog<Boolean> dia = new Dialog<>(); dia.setTitle("Modifier");
        ButtonType bOK = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dia.getDialogPane().getButtonTypes().addAll(bOK, ButtonType.CANCEL);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(20));
        TextField n = new TextField(d.getNom()); TextField m = new TextField(String.valueOf(d.getMontant()));
        g.add(new Label("Nom:"),0,0); g.add(n,1,0); g.add(new Label("Montant:"),0,1); g.add(m,1,1);
        dia.getDialogPane().setContent(g);
        dia.setResultConverter(b -> b == bOK);
        dia.showAndWait().ifPresent(r -> { if(r) { d.setNom(n.getText()); d.setMontant(Double.parseDouble(m.getText())); updateCalculs(); sauvegarderDonnees(); tableViewBudget.refresh(); } });
    }

    // --- LOGIQUE COMMUNE ---
    private void updateCalculs() {
        double activeTotal = depenses.stream().filter(Depense::isActive).mapToDouble(Depense::getMontant).sum();
        double paidTotal = depenses.stream().filter(d -> d.isActive() && d.isCochee()).mapToDouble(Depense::getMontant).sum();
        resteEstime.set(budgetEstime.get() - activeTotal);
        resteReel.set(budgetReel.get() - paidTotal);
        soldeFinMois.set(budgetReel.get() - activeTotal);
    }

    private void reinitialiserMois() {
        depenses.removeIf(d -> !d.isRecurrente());
        depenses.forEach(d -> { d.setCochee(false); d.setActive(true); });
        for(Investissement inv : investissements) {
            double part = (inv.getAllocation() / 100.0) * capaciteInvestMensuelle.get();
            inv.setCashReserve(inv.getCashReserve() + part);
        }
        updateCalculs(); sauvegarderDonnees(); sauvegarderInvestissements();
    }

    private void chargerDonnees() {
        File f = new File(BUDGET_FILE); if(!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line; String mode = "";
            while((line = br.readLine()) != null) {
                if(line.equals("BUDGETS")) { mode = "B"; budgetEstime.set(Double.parseDouble(br.readLine())); budgetReel.set(Double.parseDouble(br.readLine())); }
                else if(line.equals("DEPENSES")) mode = "D";
                else if(mode.equals("D") && !line.isEmpty()) {
                    String[] p = line.split("\\|");
                    Depense d = new Depense(p[0], Double.parseDouble(p[1]), Boolean.parseBoolean(p[2]));
                    d.setCochee(Boolean.parseBoolean(p[3])); if(p.length > 4) d.setActive(Boolean.parseBoolean(p[4]));
                    depenses.add(d);
                }
            }
        } catch(Exception e){}
    }

    private void sauvegarderDonnees() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(BUDGET_FILE))) {
            bw.write("BUDGETS\n" + budgetEstime.get() + "\n" + budgetReel.get() + "\nDEPENSES\n");
            for(Depense d : depenses) bw.write(d.getNom()+"|"+d.getMontant()+"|"+d.isRecurrente()+"|"+d.isCochee()+"|"+d.isActive()+"\n");
        } catch(Exception e){}
    }

    private void chargerInvestissements() {
        File f = new File(INVEST_FILE); if(!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            capaciteInvestMensuelle.set(Double.parseDouble(br.readLine()));
            String line;
            while((line = br.readLine()) != null) {
                String[] p = line.split("\\|");
                Investissement i = new Investissement(p[0],p[1],p[2],Double.parseDouble(p[3]),Double.parseDouble(p[4]),Double.parseDouble(p[5]),Boolean.parseBoolean(p[6]));
                i.setCashReserve(Double.parseDouble(p[7])); i.setCapitalInvesti(Double.parseDouble(p[8]));
                if(p.length > 9) i.setRendementAnnuel(Double.parseDouble(p[9]));
                investissements.add(i);
            }
        } catch(Exception e){}
    }

    private void sauvegarderInvestissements() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(INVEST_FILE))) {
            bw.write(capaciteInvestMensuelle.get() + "\n");
            for(Investissement i : investissements) {
                bw.write(i.getNom()+"|"+i.getTicker()+"|"+i.getEnveloppe()+"|"+i.getParts()+"|"+i.getPrixActuel()+"|"+i.getAllocation()+"|"+i.isAccumulant()+"|"+i.getCashReserve()+"|"+i.getCapitalInvesti()+"|"+i.getRendementAnnuel()+"\n");
            }
        } catch(Exception e){}
    }

    private String getStylesheet() {
        return "data:text/css," +
            ".root { -fx-background-color: #f1f5f9; }" +
            ".sidebar { -fx-background-color: #0f172a; }" +
            ".sidebar-logo { -fx-text-fill: linear-gradient(to bottom right, #38bdf8, #818cf8); -fx-font-size: 30; -fx-font-weight: bold; }" +
            ".sidebar-btn { -fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 14; -fx-padding: 15 20; -fx-background-radius: 12; }" +
            ".sidebar-btn:hover { -fx-background-color: rgba(255,255,255,0.05); -fx-text-fill: white; }" +
            ".sidebar-btn-active { -fx-background-color: linear-gradient(to right, rgba(79, 70, 229, 0.2), transparent); -fx-text-fill: #38bdf8; -fx-border-color: #38bdf8; -fx-border-width: 0 0 0 4; }" +
            ".header { -fx-background-color: linear-gradient(to right, #4f46e5, #7c3aed); -fx-background-radius: 15; -fx-margin: 10; }" +
            ".title { -fx-text-fill: white; -fx-font-size: 22; -fx-font-weight: bold; }" +
            ".budget-card { -fx-background-color: white; -fx-background-radius: 12; -fx-padding: 10; }" +
            ".card-label { -fx-font-size: 10; -fx-text-fill: #64748b; -fx-font-weight: bold; }" +
            ".budget-field { -fx-font-weight: bold; -fx-background-color: transparent; -fx-border-color: #e2e8f0; -fx-border-radius: 5; }" +
            ".result-value { -fx-font-size: 16; -fx-font-weight: bold; }" +
            ".table { -fx-background-color: white; -fx-background-radius: 12; }" +
            ".chart-white { -fx-background-color: white; -fx-background-radius: 15; -fx-padding: 15; }" +
            ".btn-add-invest { -fx-background-color: #4f46e5; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 20; }" +
            ".btn-primary-icon { -fx-background-color: #4f46e5; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 15; }" +
            ".btn-header-icon { -fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-border-color: white; -fx-border-radius: 8; }" +
            ".btn-action-edit { -fx-text-fill: #0ea5e9; } .btn-action-delete { -fx-text-fill: #ef4444; }";
    }

    public static void main(String[] args) { launch(args); }

    // --- CLASSES ---
    public static class Depense {
        private final StringProperty nom; private final DoubleProperty montant;
        private final BooleanProperty recurrente; private final BooleanProperty cochee;
        private final BooleanProperty active;
        public Depense(String n, double m, boolean r) {
            this.nom = new SimpleStringProperty(n); this.montant = new SimpleDoubleProperty(m);
            this.recurrente = new SimpleBooleanProperty(r); this.cochee = new SimpleBooleanProperty(false);
            this.active = new SimpleBooleanProperty(true);
        }
        public String getNom() { return nom.get(); } public void setNom(String v) { nom.set(v); }
        public double getMontant() { return montant.get(); } public void setMontant(double v) { montant.set(v); }
        public boolean isRecurrente() { return recurrente.get(); } public boolean isCochee() { return cochee.get(); }
        public void setCochee(boolean v) { cochee.set(v); } public boolean isActive() { return active.get(); }
        public void setActive(boolean v) { active.set(v); }
        public StringProperty nomProperty() { return nom; } public DoubleProperty montantProperty() { return montant; }
    }

    public static class Investissement {
        private final StringProperty nom; private final StringProperty ticker; private final StringProperty enveloppe;
        private final DoubleProperty parts; private final DoubleProperty prixActuel; private final DoubleProperty allocation;
        private final BooleanProperty accumulant; private final DoubleProperty cashReserve; private final DoubleProperty capitalInvesti;
        private final DoubleProperty rendementAnnuel;
        public Investissement(String n, String t, String e, double p, double pr, double al, boolean acc) {
            this.nom = new SimpleStringProperty(n); this.ticker = new SimpleStringProperty(t);
            this.enveloppe = new SimpleStringProperty(e); this.parts = new SimpleDoubleProperty(p);
            this.prixActuel = new SimpleDoubleProperty(pr); this.allocation = new SimpleDoubleProperty(al);
            this.accumulant = new SimpleBooleanProperty(acc); this.cashReserve = new SimpleDoubleProperty(0);
            this.capitalInvesti = new SimpleDoubleProperty(0); this.rendementAnnuel = new SimpleDoubleProperty(0.07);
        }
        public String getNom() { return nom.get(); } public String getTicker() { return ticker.get(); }
        public String getEnveloppe() { return enveloppe.get(); } public double getParts() { return parts.get(); }
        public void setParts(double v) { parts.set(v); } public double getPrixActuel() { return prixActuel.get(); }
        public void setPrixActuel(double v) { prixActuel.set(v); } public double getAllocation() { return allocation.get(); }
        public boolean isAccumulant() { return accumulant.get(); } public double getCashReserve() { return cashReserve.get(); }
        public void setCashReserve(double v) { cashReserve.set(v); } public double getCapitalInvesti() { return capitalInvesti.get(); }
        public void setCapitalInvesti(double v) { capitalInvesti.set(v); } public double getRendementAnnuel() { return rendementAnnuel.get(); }
        public void setRendementAnnuel(double v) { rendementAnnuel.set(v); }
    }
}