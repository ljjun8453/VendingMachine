import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Client2 extends JFrame implements ActionListener {
    private static final int DRINK_COUNT = 8;
    private static final int DEFAULT_STOCK = 10;
    private static final int MAX_TOTAL_MONEY = 7000;
    private static final int MAX_BILL_MONEY = 5000;
    private static final int[] MONEY_UNITS = {10, 50, 100, 500, 1000};
    private static final int[] COIN_UNITS = {500, 100, 50, 10};
    private static final int RESERVE_COIN_COUNT = 10;
    private static final String LOAD_BALANCER_HOST = "127.0.0.1";
    private static final int LOAD_BALANCER_PORT = 9000;

    private String adminPassword = "admin!123";

    private String[] drinkNames = new String[DRINK_COUNT + 1];
    private int[] drinkPrices = new int[DRINK_COUNT + 1];
    private int[] drinkStocks = new int[DRINK_COUNT + 1];
    private int[] drinkSoldCounts = new int[DRINK_COUNT + 1];
    private int[] drinkNext = new int[DRINK_COUNT + 1];
    private int drinkHead = 1;

    private int insertedTotalAmount = 0;
    private int insertedBill1000Count = 0;
    private int[] insertedCoinCounts = new int[COIN_UNITS.length];

    private int[] coinCounts = new int[COIN_UNITS.length];
    private int bill1000Count = 0;

    private int dailyTotalSales = 0;
    private int monthlyTotalSales = 0;
    private int[] dailyDrinkSales = new int[DRINK_COUNT + 1];
    private int[] monthlyDrinkSales = new int[DRINK_COUNT + 1];

    private CardLayout cardLayout;
    private JPanel mainPanel;

    private JLabel moneyLabel;
    private JLabel changeOutputLabel;
    private JLabel drinkOutputLabel;

    private JButton[] drinkButtons;
    private JTextField adminPasswordField;

    private JTable drinkTable;
    private DefaultTableModel drinkTableModel;

    private JLabel coinStatusLabel;
    private JLabel salesStatusLabel;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Socket pushSocket;
    private BufferedReader pushIn;
    private PrintWriter pushOut;
    private PushThread pushThread;
    private String connectedServerName = "서버 미연결";
    private String clientName = "CLIENT-2";

    public static void main(String[] args) {
        new Client2();
    }

    public Client2() {
        drinkNames[1] = "믹스커피";
        drinkNames[2] = "고급믹스커피";
        drinkNames[3] = "물";
        drinkNames[4] = "캔커피";
        drinkNames[5] = "이온음료";
        drinkNames[6] = "고급캔커피";
        drinkNames[7] = "탄산음료";
        drinkNames[8] = "특화음료";

        drinkPrices[1] = 200;
        drinkPrices[2] = 300;
        drinkPrices[3] = 450;
        drinkPrices[4] = 500;
        drinkPrices[5] = 550;
        drinkPrices[6] = 700;
        drinkPrices[7] = 750;
        drinkPrices[8] = 800;

        for (int i = 1; i <= DRINK_COUNT; i++) {
            drinkStocks[i] = DEFAULT_STOCK;

            if (i == DRINK_COUNT) {
                drinkNext[i] = 0;
            } else {
                drinkNext[i] = i + 1;
            }
        }

        for (int i = 0; i < COIN_UNITS.length; i++) {
            coinCounts[i] = RESERVE_COIN_COUNT;
        }

        setTitle("20233685_임재준_네트워크프로그래밍_자판기프로그램");
        setSize(900, 650);
        setMinimumSize(new Dimension(820, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        JPanel salePanel = new JPanel(new GridBagLayout());
        salePanel.setBackground(new Color(218, 221, 226));
        salePanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel machinePanel = new JPanel(new BorderLayout(10, 10));
        machinePanel.setPreferredSize(new Dimension(840, 590));
        machinePanel.setBackground(new Color(42, 51, 61));
        machinePanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(25, 30, 36), 3),
                new EmptyBorder(12, 12, 12, 12)
        ));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(42, 51, 61));

        JLabel titleLabel = new JLabel("자판기 프로그램", SwingConstants.CENTER);
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);

        JLabel subLabel = new JLabel("음료 선택 후 구매하세요", SwingConstants.CENTER);
        subLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        subLabel.setForeground(new Color(220, 225, 230));

        headerPanel.add(titleLabel, BorderLayout.CENTER);
        headerPanel.add(subLabel, BorderLayout.SOUTH);

        JPanel bodyPanel = new JPanel(new BorderLayout(12, 0));
        bodyPanel.setBackground(new Color(42, 51, 61));

        JPanel productOuterPanel = new JPanel(new BorderLayout(5, 5));
        productOuterPanel.setBackground(new Color(28, 35, 43));
        productOuterPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(150, 160, 170), 2),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel showcaseLabel = new JLabel("상품 진열부", SwingConstants.CENTER);
        showcaseLabel.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        showcaseLabel.setForeground(Color.WHITE);
        showcaseLabel.setBorder(new EmptyBorder(0, 0, 6, 0));

        JPanel productGrid = new JPanel(new GridLayout(4, 2, 8, 8));
        productGrid.setBackground(new Color(28, 35, 43));

        drinkButtons = new JButton[DRINK_COUNT + 1];

        for (int i = 1; i <= DRINK_COUNT; i++) {
            JButton button = new JButton();
            button.setFont(new Font("맑은 고딕", Font.BOLD, 13));
            button.setFocusPainted(false);
            button.setBorder(new LineBorder(new Color(120, 130, 140), 1));
            button.setActionCommand("DRINK_" + i);
            button.addActionListener(this);
            drinkButtons[i] = button;
            productGrid.add(button);
        }

        productOuterPanel.add(showcaseLabel, BorderLayout.NORTH);
        productOuterPanel.add(productGrid, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new BorderLayout(8, 8));
        controlPanel.setPreferredSize(new Dimension(255, 0));
        controlPanel.setBackground(new Color(42, 51, 61));

        JPanel moneyInputPanel = new JPanel(new BorderLayout(8, 8));
        moneyInputPanel.setBackground(new Color(42, 51, 61));

        JPanel coinSlotPanel = new JPanel(new BorderLayout());
        coinSlotPanel.setBackground(new Color(230, 232, 235));
        coinSlotPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(80, 85, 90), 2),
                new EmptyBorder(8, 8, 8, 8)
        ));

        JLabel coinSlotLabel = new JLabel("화폐 투입구", SwingConstants.CENTER);
        coinSlotLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));

        moneyLabel = new JLabel("0원", SwingConstants.CENTER);
        moneyLabel.setPreferredSize(new Dimension(120, 24));
        moneyLabel.setOpaque(true);
        moneyLabel.setBackground(new Color(40, 45, 50));
        moneyLabel.setForeground(new Color(93, 255, 151));
        moneyLabel.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        moneyLabel.setBorder(new LineBorder(Color.BLACK, 1));

        coinSlotPanel.add(coinSlotLabel, BorderLayout.NORTH);
        coinSlotPanel.add(moneyLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(6, 1, 6, 6));
        buttonPanel.setBackground(new Color(42, 51, 61));

        for (int money : MONEY_UNITS) {
            JButton button = new JButton(money + "원 투입");
            button.setFont(new Font("맑은 고딕", Font.BOLD, 13));
            button.setFocusPainted(false);
            button.setBackground(new Color(240, 242, 245));
            button.setActionCommand("MONEY_" + money);
            button.addActionListener(this);
            buttonPanel.add(button);
        }

        JButton returnButton = new JButton("화폐 반환");
        returnButton.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        returnButton.setFocusPainted(false);
        returnButton.setBackground(new Color(255, 239, 194));
        returnButton.setActionCommand("RETURN_MONEY");
        returnButton.addActionListener(this);
        buttonPanel.add(returnButton);

        moneyInputPanel.add(coinSlotPanel, BorderLayout.NORTH);
        moneyInputPanel.add(buttonPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new GridLayout(3, 1, 7, 7));
        bottomPanel.setBackground(new Color(42, 51, 61));

        JPanel changeSlot = new JPanel(new BorderLayout());
        changeSlot.setBackground(new Color(230, 232, 235));
        changeSlot.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(70, 75, 80), 2),
                new EmptyBorder(6, 6, 6, 6)
        ));

        JLabel changeLabel = new JLabel("거스름돈 배출구", SwingConstants.CENTER);
        changeLabel.setFont(new Font("맑은 고딕", Font.BOLD, 13));

        changeOutputLabel = new JLabel("0원", SwingConstants.CENTER);
        changeOutputLabel.setOpaque(true);
        changeOutputLabel.setBackground(new Color(35, 38, 42));
        changeOutputLabel.setForeground(Color.WHITE);
        changeOutputLabel.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        changeOutputLabel.setPreferredSize(new Dimension(100, 22));

        changeSlot.add(changeLabel, BorderLayout.NORTH);
        changeSlot.add(changeOutputLabel, BorderLayout.CENTER);

        JPanel drinkOutlet = new JPanel(new BorderLayout());
        drinkOutlet.setBackground(new Color(220, 224, 228));
        drinkOutlet.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(60, 65, 70), 2),
                new EmptyBorder(6, 6, 6, 6)
        ));

        JLabel outletLabel = new JLabel("음료 배출구", SwingConstants.CENTER);
        outletLabel.setFont(new Font("맑은 고딕", Font.BOLD, 13));

        drinkOutputLabel = new JLabel("없음", SwingConstants.CENTER);
        drinkOutputLabel.setOpaque(true);
        drinkOutputLabel.setBackground(new Color(30, 34, 38));
        drinkOutputLabel.setForeground(Color.WHITE);
        drinkOutputLabel.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        drinkOutputLabel.setPreferredSize(new Dimension(100, 22));

        drinkOutlet.add(outletLabel, BorderLayout.NORTH);
        drinkOutlet.add(drinkOutputLabel, BorderLayout.CENTER);

        JButton adminButton = new JButton("관리자 메뉴");
        adminButton.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        adminButton.setFocusPainted(false);
        adminButton.setBackground(new Color(205, 220, 255));
        adminButton.setActionCommand("SHOW_ADMIN_LOGIN");
        adminButton.addActionListener(this);

        bottomPanel.add(changeSlot);
        bottomPanel.add(drinkOutlet);
        bottomPanel.add(adminButton);

        controlPanel.add(moneyInputPanel, BorderLayout.CENTER);
        controlPanel.add(bottomPanel, BorderLayout.SOUTH);

        bodyPanel.add(productOuterPanel, BorderLayout.CENTER);
        bodyPanel.add(controlPanel, BorderLayout.EAST);

        machinePanel.add(headerPanel, BorderLayout.NORTH);
        machinePanel.add(bodyPanel, BorderLayout.CENTER);

        salePanel.add(machinePanel);

        JPanel adminLoginPanel = new JPanel(new GridBagLayout());
        adminLoginPanel.setBackground(new Color(235, 238, 242));
        adminLoginPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel boxPanel = new JPanel(new GridBagLayout());
        boxPanel.setPreferredSize(new Dimension(420, 260));
        boxPanel.setBackground(Color.WHITE);
        boxPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(160, 165, 170), 2),
                new EmptyBorder(25, 25, 25, 25)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 7, 7, 7);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel loginTitleLabel = new JLabel("관리자 로그인", SwingConstants.CENTER);
        loginTitleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 25));

        JLabel passwordLabel = new JLabel("비밀번호");
        passwordLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 16));

        adminPasswordField = new JPasswordField();
        adminPasswordField.setFont(new Font("맑은 고딕", Font.PLAIN, 16));

        JButton loginButton = new JButton("로그인");
        loginButton.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        loginButton.setActionCommand("LOGIN_ADMIN");
        loginButton.addActionListener(this);

        JButton backButton = new JButton("판매 화면으로 돌아가기");
        backButton.setFont(new Font("맑은 고딕", Font.PLAIN, 15));
        backButton.setActionCommand("BACK_TO_SALE");
        backButton.addActionListener(this);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        boxPanel.add(loginTitleLabel, gbc);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        boxPanel.add(passwordLabel, gbc);

        gbc.gridx = 1;
        boxPanel.add(adminPasswordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        boxPanel.add(loginButton, gbc);

        gbc.gridy = 3;
        boxPanel.add(backButton, gbc);

        adminLoginPanel.add(boxPanel);

        JPanel adminPanel = new JPanel(new BorderLayout(10, 10));
        adminPanel.setBackground(new Color(235, 238, 242));
        adminPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel adminTitleLabel = new JLabel("관리자 전용 메뉴");
        adminTitleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 22));

        JButton logoutButton = new JButton("관리자 로그아웃");
        logoutButton.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        logoutButton.setActionCommand("LOGOUT_ADMIN");
        logoutButton.addActionListener(this);

        JPanel adminTopPanel = new JPanel(new BorderLayout());
        adminTopPanel.setBackground(new Color(235, 238, 242));
        adminTopPanel.add(adminTitleLabel, BorderLayout.WEST);
        adminTopPanel.add(logoutButton, BorderLayout.EAST);

        JPanel adminCenterPanel = new JPanel(new BorderLayout(10, 10));
        adminCenterPanel.setBackground(new Color(235, 238, 242));

        String[] columns = {"ID", "음료명", "가격", "재고", "판매수량", "상태"};
        drinkTableModel = new DefaultTableModel(columns, 0);
        drinkTable = new JTable(drinkTableModel);
        drinkTable.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        drinkTable.setRowHeight(25);
        drinkTable.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 13));
        drinkTable.setDefaultEditor(Object.class, null);

        JScrollPane scrollPane = new JScrollPane(drinkTable);

        JPanel adminButtonPanel = new JPanel(new GridLayout(8, 1, 6, 6));
        adminButtonPanel.setPreferredSize(new Dimension(190, 0));
        adminButtonPanel.setBackground(new Color(235, 238, 242));

        JButton restockButton = new JButton("재고 보충");
        JButton changeNameButton = new JButton("음료 이름 변경");
        JButton changePriceButton = new JButton("판매 가격 변경");
        JButton coinStatusButton = new JButton("화폐 현황 조회");
        JButton collectButton = new JButton("수금");
        JButton passwordButton = new JButton("비밀번호 변경");
        JButton salesButton = new JButton("매출 조회");
        JButton refillMoneyButton = new JButton("기본 화폐 보충");

        restockButton.setActionCommand("RESTOCK_DRINK");
        changeNameButton.setActionCommand("CHANGE_DRINK_NAME");
        changePriceButton.setActionCommand("CHANGE_DRINK_PRICE");
        coinStatusButton.setActionCommand("SHOW_COIN_STATUS");
        collectButton.setActionCommand("COLLECT_MONEY");
        passwordButton.setActionCommand("CHANGE_ADMIN_PASSWORD");
        salesButton.setActionCommand("SHOW_SALES");
        refillMoneyButton.setActionCommand("REFILL_RESERVE_MONEY");

        restockButton.addActionListener(this);
        changeNameButton.addActionListener(this);
        changePriceButton.addActionListener(this);
        coinStatusButton.addActionListener(this);
        collectButton.addActionListener(this);
        passwordButton.addActionListener(this);
        salesButton.addActionListener(this);
        refillMoneyButton.addActionListener(this);

        adminButtonPanel.add(restockButton);
        adminButtonPanel.add(changeNameButton);
        adminButtonPanel.add(changePriceButton);
        adminButtonPanel.add(coinStatusButton);
        adminButtonPanel.add(collectButton);
        adminButtonPanel.add(passwordButton);
        adminButtonPanel.add(salesButton);
        adminButtonPanel.add(refillMoneyButton);

        adminCenterPanel.add(scrollPane, BorderLayout.CENTER);
        adminCenterPanel.add(adminButtonPanel, BorderLayout.EAST);

        JPanel adminBottomPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        adminBottomPanel.setBackground(new Color(235, 238, 242));

        coinStatusLabel = new JLabel();
        coinStatusLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 13));

        salesStatusLabel = new JLabel();
        salesStatusLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 13));

        adminBottomPanel.add(coinStatusLabel);
        adminBottomPanel.add(salesStatusLabel);

        adminPanel.add(adminTopPanel, BorderLayout.NORTH);
        adminPanel.add(adminCenterPanel, BorderLayout.CENTER);
        adminPanel.add(adminBottomPanel, BorderLayout.SOUTH);

        mainPanel.add(salePanel, "SALE");
        mainPanel.add(adminLoginPanel, "ADMIN_LOGIN");
        mainPanel.add(adminPanel, "ADMIN");

        add(mainPanel);
        cardLayout.show(mainPanel, "SALE");
        updateAllView();
        setVisible(true);
        connectServer();
        sendRequest("STATUS");
    }

    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();

        if (command.startsWith("DRINK_")) {
            int drinkId = Integer.parseInt(command.substring(6));
            sendRequest("BUY|" + drinkId);
        } else if (command.startsWith("MONEY_")) {
            int money = Integer.parseInt(command.substring(6));
            sendRequest("INSERT|" + money);
        } else if (command.equals("RETURN_MONEY")) {
            sendRequest("RETURN");
        } else if (command.equals("SHOW_ADMIN_LOGIN")) {
            adminPasswordField.setText("");
            cardLayout.show(mainPanel, "ADMIN_LOGIN");
        } else if (command.equals("LOGIN_ADMIN")) {
            String inputPassword = adminPasswordField.getText();
            String response = sendRequest("ADMIN_LOGIN|" + encode(inputPassword));

            if (response != null && response.startsWith("RES|LOGIN_OK|")) {
                adminPasswordField.setText("");
                updateAllView();
                cardLayout.show(mainPanel, "ADMIN");
            }
        } else if (command.equals("BACK_TO_SALE")) {
            adminPasswordField.setText("");
            cardLayout.show(mainPanel, "SALE");
        } else if (command.equals("LOGOUT_ADMIN")) {
            changeOutputLabel.setText("관리자 메뉴를 종료했습니다.");
            cardLayout.show(mainPanel, "SALE");
            updateAllView();
        } else if (command.equals("RESTOCK_DRINK")) {
            int row = drinkTable.getSelectedRow();

            if (row < 0) {
                JOptionPane.showMessageDialog(this, "재고를 보충할 음료를 선택하세요.", "입력 오류", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int drinkId = (int) drinkTableModel.getValueAt(row, 0);

            String input = JOptionPane.showInputDialog(
                    this,
                    drinkNames[drinkId] + " 보충 수량을 입력하세요.",
                    "재고 보충",
                    JOptionPane.QUESTION_MESSAGE
            );

            if (input == null) {
                return;
            }

            try {
                int amount = Integer.parseInt(input.trim());

                if (amount <= 0) {
                    JOptionPane.showMessageDialog(this, "보충 수량은 1개 이상이어야 합니다.", "입력 오류", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                sendRequest("RESTOCK|" + drinkId + "|" + amount);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "숫자만 입력할 수 있습니다.", "입력 오류", JOptionPane.WARNING_MESSAGE);
            }
        } else if (command.equals("CHANGE_DRINK_NAME")) {
            int row = drinkTable.getSelectedRow();

            if (row < 0) {
                JOptionPane.showMessageDialog(this, "이름을 변경할 음료를 선택하세요.", "입력 오류", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int drinkId = (int) drinkTableModel.getValueAt(row, 0);

            String input = JOptionPane.showInputDialog(
                    this,
                    "새 음료 이름을 입력하세요.",
                    drinkNames[drinkId]
            );

            if (input == null) {
                return;
            }

            input = input.trim();

            if (input.isEmpty()) {
                JOptionPane.showMessageDialog(this, "음료 이름은 비워둘 수 없습니다.", "입력 오류", JOptionPane.WARNING_MESSAGE);
                return;
            }

            sendRequest("CHANGE_NAME|" + drinkId + "|" + encode(input));
        } else if (command.equals("CHANGE_DRINK_PRICE")) {
            int row = drinkTable.getSelectedRow();

            if (row < 0) {
                JOptionPane.showMessageDialog(this, "가격을 변경할 음료를 선택하세요.", "입력 오류", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int drinkId = (int) drinkTableModel.getValueAt(row, 0);

            String input = JOptionPane.showInputDialog(
                    this,
                    "새 판매 가격을 입력하세요.",
                    drinkPrices[drinkId]
            );

            if (input == null) {
                return;
            }

            try {
                int price = Integer.parseInt(input.trim());

                if (price <= 0) {
                    JOptionPane.showMessageDialog(this, "가격은 1원 이상이어야 합니다.", "입력 오류", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                if (price % 10 != 0) {
                    JOptionPane.showMessageDialog(this, "가격은 10원 단위로 입력하세요.", "입력 오류", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                sendRequest("CHANGE_PRICE|" + drinkId + "|" + price);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "숫자만 입력할 수 있습니다.", "입력 오류", JOptionPane.WARNING_MESSAGE);
            }
        } else if (command.equals("SHOW_COIN_STATUS")) {
            sendRequest("COIN_STATUS");
        } else if (command.equals("COLLECT_MONEY")) {
            sendRequest("COLLECT");
        } else if (command.equals("CHANGE_ADMIN_PASSWORD")) {
            String input = JOptionPane.showInputDialog(
                    this,
                    "새 관리자 비밀번호를 입력하세요.\n조건: 8자리 이상, 숫자 1개 이상, 특수문자 1개 이상",
                    "관리자 비밀번호 변경",
                    JOptionPane.QUESTION_MESSAGE
            );

            if (input == null) {
                return;
            }

            if (!isValidAdminPassword(input)) {
                JOptionPane.showMessageDialog(this, "비밀번호 조건을 만족하지 않습니다.", "입력 오류", JOptionPane.WARNING_MESSAGE);
                return;
            }

            sendRequest("CHANGE_PASSWORD|" + encode(input));
        } else if (command.equals("SHOW_SALES")) {
            sendRequest("SALES");
        } else if (command.equals("REFILL_RESERVE_MONEY")) {
            sendRequest("REFILL");
        }
    }

    private void connectServer() {
        closeConnection();

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(LOAD_BALANCER_HOST, LOAD_BALANCER_PORT), 1500);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            connectedServerName = LOAD_BALANCER_HOST + ":" + LOAD_BALANCER_PORT;

            out.println("HELLO|" + encode(clientName));
            String helloResponse = in.readLine();

            if (helloResponse != null) {
                applyResponse(helloResponse);
            }

            startPushConnection();
            changeOutputLabel.setText("로드밸런서 연결 완료: " + connectedServerName);
            return;
        } catch (IOException ex) {
            closeConnection();
        }

        connectedServerName = "서버 미연결";
        changeOutputLabel.setText("로드밸런서 연결 실패");
    }

    private String sendRequest(String request) {
        if (socket == null || socket.isClosed() || out == null || in == null) {
            connectServer();
        }

        if (socket == null || socket.isClosed() || out == null || in == null) {
            changeOutputLabel.setText("서버 연결 실패");
            return null;
        }

        try {
            out.println(request);
            String response = in.readLine();

            if (response == null) {
                closeConnection();
                connectServer();
                return null;
            }

            applyResponse(response);
            return response;
        } catch (IOException ex) {
            closeConnection();
            connectServer();
            return null;
        }
    }

    private void applyResponse(String response) {
        String[] part = response.split("\\|", 7);

        if (part.length < 7 || !part[0].equals("RES")) {
            changeOutputLabel.setText("잘못된 서버 응답");
            return;
        }

        String result = part[1];
        String message = decode(part[2]);
        String drinkOut = decode(part[3]);
        String changeOut = decode(part[4]);

        insertedTotalAmount = Integer.parseInt(part[5]);

        applyState(part[6]);

        if (drinkOut.length() > 0) {
            drinkOutputLabel.setText(drinkOut);
        }

        if (changeOut.length() > 0) {
            changeOutputLabel.setText(changeOut);
        }

        updateAllView();

        if (result.equals("DIALOG")) {
            JOptionPane.showMessageDialog(this, message, "관리자 메뉴", JOptionPane.INFORMATION_MESSAGE);
        } else if (result.equals("LOGIN_FAIL")) {
            JOptionPane.showMessageDialog(this, message, "로그인 실패", JOptionPane.ERROR_MESSAGE);
        } else if (result.equals("ERROR")) {
            if (message.length() > 0) {
                changeOutputLabel.setText(message);
            }
        }
    }

    private void applyState(String state) {
        String[] part = state.split("\\|", -1);

        if (part.length < 10) {
            return;
        }

        String[] names = part[0].split(",", -1);
        String[] prices = part[1].split(",", -1);
        String[] stocks = part[2].split(",", -1);
        String[] sold = part[3].split(",", -1);
        String[] daily = part[4].split(",", -1);
        String[] monthly = part[5].split(",", -1);
        String[] coins = part[6].split(",", -1);

        for (int i = 1; i <= DRINK_COUNT; i++) {
            drinkNames[i] = decode(names[i - 1]);
            drinkPrices[i] = Integer.parseInt(prices[i - 1]);
            drinkStocks[i] = Integer.parseInt(stocks[i - 1]);
            drinkSoldCounts[i] = Integer.parseInt(sold[i - 1]);
            dailyDrinkSales[i] = Integer.parseInt(daily[i - 1]);
            monthlyDrinkSales[i] = Integer.parseInt(monthly[i - 1]);
        }

        for (int i = 0; i < COIN_UNITS.length; i++) {
            coinCounts[i] = Integer.parseInt(coins[i]);
        }

        bill1000Count = Integer.parseInt(part[7]);
        dailyTotalSales = Integer.parseInt(part[8]);
        monthlyTotalSales = Integer.parseInt(part[9]);

        if (part.length >= 11) {
            adminPassword = decode(part[10]);
        }
    }

    private void startPushConnection() {
        stopPushConnection();

        try {
            pushSocket = new Socket();
            pushSocket.connect(new InetSocketAddress(LOAD_BALANCER_HOST, LOAD_BALANCER_PORT), 1500);

            pushIn = new BufferedReader(new InputStreamReader(pushSocket.getInputStream(), StandardCharsets.UTF_8));
            pushOut = new PrintWriter(new OutputStreamWriter(pushSocket.getOutputStream(), StandardCharsets.UTF_8), true);

            pushOut.println("SUBSCRIBE|" + encode(clientName));
            String response = pushIn.readLine();

            if ("SUBSCRIBE_OK".equals(response)) {
                pushThread = new PushThread();
                pushThread.start();
            }
        } catch (IOException e) {
            stopPushConnection();
        }
    }

    private void stopPushConnection() {
        try {
            if (pushIn != null) {
                pushIn.close();
            }
        } catch (IOException e) {
        }

        if (pushOut != null) {
            pushOut.close();
        }

        try {
            if (pushSocket != null) {
                pushSocket.close();
            }
        } catch (IOException e) {
        }

        pushIn = null;
        pushOut = null;
        pushSocket = null;
    }

    private class PushThread extends Thread {
        public void run() {
            String message;

            try {
                while (pushIn != null && (message = pushIn.readLine()) != null) {
                    if (message.startsWith("PUSH_STATE|")) {
                        SwingUtilities.invokeLater(new PushUpdateTask(message.substring(11)));
                    }
                }
            } catch (IOException e) {
            }
        }
    }

    private class PushUpdateTask implements Runnable {
        private String state;

        PushUpdateTask(String state) {
            this.state = state;
        }

        public void run() {
            applyState(state);
            updateAllView();
        }
    }

    private void closeConnection() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
        }

        if (out != null) {
            out.close();
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
        }

        in = null;
        out = null;
        socket = null;
    }

    private void updateAllView() {
        moneyLabel.setText(insertedTotalAmount + "원");

        for (int i = 1; i <= DRINK_COUNT; i++) {
            boolean hasEnoughMoney = insertedTotalAmount >= drinkPrices[i];
            boolean canMakeChange = hasEnoughMoney && canMakeChange(insertedTotalAmount - drinkPrices[i]);
            boolean canSelect = drinkStocks[i] > 0 && hasEnoughMoney;
            String status;

            if (drinkStocks[i] <= 0) {
                status = "품절";
            } else if (!hasEnoughMoney) {
                status = "금액 부족";
            } else if (canMakeChange) {
                status = "구매 가능";
            } else {
                status = "거스름돈 부족";
            }

            String text =
                    "<html><center>" +
                            "<b>" + drinkNames[i] + "</b><br>" +
                            drinkPrices[i] + "원<br>" +
                            "재고 " + drinkStocks[i] + "개<br>" +
                            status +
                            "</center></html>";

            drinkButtons[i].setText(text);
            drinkButtons[i].setEnabled(canSelect);

            if (drinkStocks[i] <= 0) {
                drinkButtons[i].setBackground(new Color(190, 190, 190));
            } else if (canMakeChange) {
                drinkButtons[i].setBackground(new Color(189, 235, 190));
            } else if (canSelect) {
                drinkButtons[i].setBackground(new Color(255, 226, 180));
            } else {
                drinkButtons[i].setBackground(new Color(234, 236, 243));
            }
        }

        drinkTableModel.setRowCount(0);

        int current = drinkHead;

        while (current != 0) {
            drinkTableModel.addRow(new Object[]{
                    current,
                    drinkNames[current],
                    drinkPrices[current],
                    drinkStocks[current],
                    drinkSoldCounts[current],
                    drinkStocks[current] <= 0 ? "품절" : "판매 가능"
            });

            current = drinkNext[current];
        }

        coinStatusLabel.setText("화폐 현황: " + getCoinShortStatusText());
        salesStatusLabel.setText("일별 매출: " + dailyTotalSales + "원 / 월별 매출: " + monthlyTotalSales + "원");
    }

    private boolean canMakeChange(int amount) {
        if (amount == 0) {
            return true;
        }

        int remain = amount;

        for (int i = 0; i < COIN_UNITS.length; i++) {
            int use = Math.min(coinCounts[i], remain / COIN_UNITS[i]);
            remain -= use * COIN_UNITS[i];
        }

        return remain == 0;
    }

    private int getCoinIndex(int unit) {
        for (int i = 0; i < COIN_UNITS.length; i++) {
            if (COIN_UNITS[i] == unit) {
                return i;
            }
        }

        return -1;
    }

    private String getCoinShortStatusText() {
        return "500원 " + coinCounts[getCoinIndex(500)] + "개, "
                + "100원 " + coinCounts[getCoinIndex(100)] + "개, "
                + "50원 " + coinCounts[getCoinIndex(50)] + "개, "
                + "10원 " + coinCounts[getCoinIndex(10)] + "개, "
                + "1000원 지폐 " + bill1000Count + "장";
    }

    private boolean isValidAdminPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);

            if (Character.isDigit(ch)) {
                hasDigit = true;
            } else if (!Character.isLetterOrDigit(ch)) {
                hasSpecial = true;
            }
        }

        return hasDigit && hasSpecial;
    }

    private String encode(String text) {
        if (text == null) {
            text = "";
        }

        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }

        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }
}