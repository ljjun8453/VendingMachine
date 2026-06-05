import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Server1 {
    private static final int DRINK_COUNT = 8;
    private static final int DEFAULT_STOCK = 10;
    private static final int MAX_TOTAL_MONEY = 7000;
    private static final int MAX_BILL_MONEY = 5000;
    private static final int[] MONEY_UNITS = {10, 50, 100, 500, 1000};
    private static final int[] COIN_UNITS = {500, 100, 50, 10};
    private static final int RESERVE_COIN_COUNT = 10;

    private static String role;
    private static int listenPort;

    private static String[] peerHosts = new String[10];
    private static int[] peerPorts = new int[10];
    private static int peerCount = 0;

    private static String adminPassword = "admin!123";

    private static String[] drinkNames = new String[DRINK_COUNT + 1];
    private static int[] drinkPrices = new int[DRINK_COUNT + 1];
    private static int[] drinkStocks = new int[DRINK_COUNT + 1];
    private static int[] drinkSoldCounts = new int[DRINK_COUNT + 1];
    private static int[] drinkNext = new int[DRINK_COUNT + 1];
    private static int drinkHead = 1;

    private static int[] coinCounts = new int[COIN_UNITS.length];
    private static int bill1000Count = 0;

    private static int dailyTotalSales = 0;
    private static int monthlyTotalSales = 0;
    private static int[] dailyDrinkSales = new int[DRINK_COUNT + 1];
    private static int[] monthlyDrinkSales = new int[DRINK_COUNT + 1];

    private static DrinkNode drinkListHead;
    private static SimpleStack saleStack = new SimpleStack(200);
    private static SimpleQueue lowStockQueue = new SimpleQueue(200);
    private static PriceTree priceTree = new PriceTree();

    private static PrintWriter[] pushClientOuts = new PrintWriter[100];
    private static String[] pushClientNames = new String[100];
    private static int pushClientCount = 0;

    public static void main(String[] args) {
        role = "SERVER1";
        listenPort = 9001;

        peerCount = 0;

        peerHosts[peerCount] = "127.0.0.1";
        peerPorts[peerCount] = 9002;
        peerCount++;

        peerHosts[peerCount] = "127.0.0.1";
        peerPorts[peerCount] = 9003;
        peerCount++;

        initData();
        startHealthCheckThread();
        startServer();
    }

    private static void initData() {
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

        rebuildDrinkLinkedList();
        rebuildPriceTree();
    }

    private static void startServer() {
        ServerSocket listenSocket = null;

        try {
            listenSocket = new ServerSocket(listenPort);
            System.out.println("[" + role + "] TCP 서버 시작, 포트: " + listenPort);

            while (true) {
                Socket clientSocket = listenSocket.accept();
                ClientThread clientThread = new ClientThread(clientSocket);
                clientThread.start();
            }
        } catch (IOException e) {
            System.out.println("[" + role + "] 서버 오류: " + e.getMessage());
        } finally {
            try {
                if (listenSocket != null) {
                    listenSocket.close();
                }
            } catch (IOException e) {
                System.out.println("[" + role + "] 서버 종료 오류: " + e.getMessage());
            }
        }
    }

    private static void startHealthCheckThread() {
        HealthCheckThread thread = new HealthCheckThread();
        thread.setDaemon(true);
        thread.start();
    }

    private static class HealthCheckThread extends Thread {
        public void run() {
            while (true) {
                for (int i = 0; i < peerCount; i++) {
                    checkPeer(peerHosts[i], peerPorts[i]);
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private static void checkPeer(String host, int port) {
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 1000);
            socket.setSoTimeout(1000);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            out.println("PING");

            String response = in.readLine();

            if (!"PONG".equals(response)) {
                System.out.println("[" + role + "] 헬스체크 비정상: " + host + ":" + port);
            }
        } catch (IOException e) {
            System.out.println("[" + role + "] TCP 헬스체크 실패: " + host + ":" + port);
        } finally {
            closeAll(socket, in, out);
        }
    }

    private static class ClientThread extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String clientName = "UNKNOWN";

        private int insertedTotalAmount = 0;
        private int insertedBill1000Count = 0;
        private int[] insertedCoinCounts = new int[COIN_UNITS.length];

        private String drinkOutput = "없음";
        private String changeOutput = "0원";

        ClientThread(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                String message;

                while ((message = in.readLine()) != null) {
                    if (message.equals("PING")) {
                        out.println("PONG");
                    } else if (message.startsWith("SUBSCRIBE|")) {
                        clientName = decode(message.substring(10));
                        registerPushClient(clientName, out);
                        out.println("SUBSCRIBE_OK");
                    } else if (message.startsWith("SYNC_STATE|")) {
                        applyState(message.substring(11));
                        out.println("SYNC_OK");
                        broadcastStateToClients();
                    } else {
                        out.println(processCommand(message));
                    }
                }
            } catch (IOException e) {
            } finally {
                removePushClient(out);
                closeAll(socket, in, out);
                if (!clientName.equals("UNKNOWN")) {
                    System.out.println("[" + role + "] GUI 클라이언트 연결 종료: " + clientName);
                }
            }
        }

        private String processCommand(String command) {
            if (command.startsWith("HELLO|")) {
                clientName = decode(command.substring(6));

                System.out.println(
                        "[" + role + "] GUI 클라이언트 접속: " +
                                clientName + " / " + socket.getRemoteSocketAddress()
                );

                return makeResponse("OK", "", drinkOutput, changeOutput, insertedTotalAmount);
            }
            if (command.equals("STATUS")) {
                return makeResponse("OK", "", drinkOutput, changeOutput, insertedTotalAmount);
            } else if (command.startsWith("INSERT|")) {
                int money = Integer.parseInt(command.substring(7));
                return insertMoney(money);
            } else if (command.startsWith("BUY|")) {
                int drinkId = Integer.parseInt(command.substring(4));
                return buyDrink(drinkId);
            } else if (command.equals("RETURN")) {
                return returnMoney();
            } else if (command.startsWith("ADMIN_LOGIN|")) {
                String input = decode(command.substring(12));

                if (adminPassword.equals(input)) {
                    return makeResponse("LOGIN_OK", "", drinkOutput, changeOutput, insertedTotalAmount);
                }

                return makeResponse("LOGIN_FAIL", "관리자 비밀번호가 올바르지 않습니다.", drinkOutput, changeOutput, insertedTotalAmount);
            } else if (command.startsWith("RESTOCK|")) {
                String[] part = command.split("\\|");
                int drinkId = Integer.parseInt(part[1]);
                int amount = Integer.parseInt(part[2]);
                return restockDrink(drinkId, amount);
            } else if (command.startsWith("CHANGE_NAME|")) {
                String[] part = command.split("\\|");
                int drinkId = Integer.parseInt(part[1]);
                String name = decode(part[2]);
                return changeDrinkName(drinkId, name);
            } else if (command.startsWith("CHANGE_PRICE|")) {
                String[] part = command.split("\\|");
                int drinkId = Integer.parseInt(part[1]);
                int price = Integer.parseInt(part[2]);
                return changeDrinkPrice(drinkId, price);
            } else if (command.equals("COIN_STATUS")) {
                return makeResponse("DIALOG", getCoinStatusText(), drinkOutput, changeOutput, insertedTotalAmount);
            } else if (command.equals("COLLECT")) {
                return collectMoney();
            } else if (command.startsWith("CHANGE_PASSWORD|")) {
                String password = decode(command.substring(16));
                return changePassword(password);
            } else if (command.equals("SALES")) {
                return makeResponse("DIALOG", getSalesText(), drinkOutput, changeOutput, insertedTotalAmount);
            } else if (command.equals("REFILL")) {
                return refillReserveMoney();
            }

            return makeResponse("ERROR", "알 수 없는 요청입니다.", drinkOutput, changeOutput, insertedTotalAmount);
        }

        private String insertMoney(int money) {
            if (!isAllowedMoney(money)) {
                changeOutput = "사용할 수 없는 화폐입니다.";
                return makeResponse("ERROR", "사용할 수 없는 화폐입니다.", drinkOutput, changeOutput, insertedTotalAmount);
            }

            if (insertedTotalAmount + money > MAX_TOTAL_MONEY) {
                changeOutput = "총 7,000원을 초과할 수 없습니다.";
                return makeResponse("ERROR", "총 7,000원을 초과할 수 없습니다.", drinkOutput, changeOutput, insertedTotalAmount);
            }

            if (money == 1000 && insertedBill1000Count * 1000 + money > MAX_BILL_MONEY) {
                changeOutput = "지폐는 최대 5,000원까지 가능합니다.";
                return makeResponse("ERROR", "지폐는 최대 5,000원까지 가능합니다.", drinkOutput, changeOutput, insertedTotalAmount);
            }

            synchronized (Server1.class) {
                insertedTotalAmount += money;

                if (money == 1000) {
                    insertedBill1000Count++;
                    bill1000Count++;
                } else {
                    int index = getCoinIndex(money);

                    if (index >= 0) {
                        insertedCoinCounts[index]++;
                        coinCounts[index]++;
                    }
                }

                changeOutput = "0원";
                syncToPeers();
                broadcastStateToClients();
            }

            return makeResponse("OK", "", drinkOutput, changeOutput, insertedTotalAmount);
        }

        private String buyDrink(int drinkId) {
            synchronized (Server1.class) {
                if (drinkId < 1 || drinkId > DRINK_COUNT) {
                    changeOutput = "존재하지 않는 음료입니다.";
                    return makeResponse("ERROR", "존재하지 않는 음료입니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                if (drinkStocks[drinkId] <= 0) {
                    changeOutput = drinkNames[drinkId] + "은(는) 품절입니다.";
                    return makeResponse("ERROR", changeOutput, drinkOutput, changeOutput, insertedTotalAmount);
                }

                if (insertedTotalAmount < drinkPrices[drinkId]) {
                    changeOutput = "투입 금액이 부족합니다.";
                    return makeResponse("ERROR", "투입 금액이 부족합니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                int change = insertedTotalAmount - drinkPrices[drinkId];

                if (!canMakeChange(change)) {
                    changeOutput = "거스름돈 부족으로 판매 불가";
                    return makeResponse("ERROR", "거스름돈 부족으로 판매 불가", drinkOutput, changeOutput, insertedTotalAmount);
                }

                makeChange(change);

                drinkStocks[drinkId]--;
                DrinkNode node = findDrinkNodeById(drinkId);

                if (node != null) {
                    node.stock = drinkStocks[drinkId];
                    node.soldCount++;
                    saleStack.push(node.name + " 판매");

                    if (node.stock <= 2) {
                        lowStockQueue.offer(node.name + " 재고 부족: " + node.stock + "개");
                        System.out.println("[" + role + "] 재고 부족 알림 큐 저장: " + node.name + " " + node.stock + "개");
                    }
                }

                drinkSoldCounts[drinkId]++;
                dailyTotalSales += drinkPrices[drinkId];
                monthlyTotalSales += drinkPrices[drinkId];
                dailyDrinkSales[drinkId] += drinkPrices[drinkId];
                monthlyDrinkSales[drinkId] += drinkPrices[drinkId];

                drinkOutput = drinkNames[drinkId];

                if (change > 0) {
                    changeOutput = change + "원";
                } else {
                    changeOutput = "0원";
                }

                insertedTotalAmount = 0;
                insertedBill1000Count = 0;
                clearInsertedCoins();

                syncToPeers();
                broadcastStateToClients();

                return makeResponse("OK", "", drinkOutput, changeOutput, insertedTotalAmount);
            }
        }

        private String returnMoney() {
            synchronized (Server1.class) {
                if (insertedTotalAmount == 0) {
                    changeOutput = "반환할 금액이 없습니다.";
                    return makeResponse("ERROR", "반환할 금액이 없습니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                int amount = insertedTotalAmount;

                for (int i = 0; i < COIN_UNITS.length; i++) {
                    coinCounts[i] = Math.max(0, coinCounts[i] - insertedCoinCounts[i]);
                }

                bill1000Count = Math.max(0, bill1000Count - insertedBill1000Count);

                insertedTotalAmount = 0;
                insertedBill1000Count = 0;
                clearInsertedCoins();

                changeOutput = amount + "원";

                syncToPeers();
                broadcastStateToClients();

                return makeResponse("OK", "", drinkOutput, changeOutput, insertedTotalAmount);
            }
        }

        private String restockDrink(int drinkId, int amount) {
            synchronized (Server1.class) {
                if (drinkId < 1 || drinkId > DRINK_COUNT) {
                    return makeResponse("ERROR", "존재하지 않는 음료입니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                if (amount <= 0) {
                    return makeResponse("ERROR", "보충 수량은 1개 이상이어야 합니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                drinkStocks[drinkId] += amount;
                updateDrinkNode(drinkId);

                syncToPeers();
                broadcastStateToClients();

                return makeResponse("DIALOG", drinkNames[drinkId] + " 재고를 " + amount + "개 보충했습니다.", drinkOutput, changeOutput, insertedTotalAmount);
            }
        }

        private String changeDrinkName(int drinkId, String name) {
            synchronized (Server1.class) {
                if (drinkId < 1 || drinkId > DRINK_COUNT) {
                    return makeResponse("ERROR", "존재하지 않는 음료입니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                if (name == null || name.trim().length() == 0) {
                    return makeResponse("ERROR", "음료 이름은 비워둘 수 없습니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                int searchIndex = searchDrinkByName(name.trim());

                if (searchIndex != -1 && searchIndex != drinkId) {
                    return makeResponse("ERROR", "이미 존재하는 음료 이름입니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                drinkNames[drinkId] = name.trim();
                updateDrinkNode(drinkId);

                syncToPeers();
                broadcastStateToClients();

                return makeResponse("DIALOG", "음료 이름을 변경했습니다.", drinkOutput, changeOutput, insertedTotalAmount);
            }
        }

        private String changeDrinkPrice(int drinkId, int price) {
            synchronized (Server1.class) {
                if (drinkId < 1 || drinkId > DRINK_COUNT) {
                    return makeResponse("ERROR", "존재하지 않는 음료입니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                if (price <= 0) {
                    return makeResponse("ERROR", "가격은 1원 이상이어야 합니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                if (price % 10 != 0) {
                    return makeResponse("ERROR", "가격은 10원 단위로 입력하세요.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                drinkPrices[drinkId] = price;
                updateDrinkNode(drinkId);
                rebuildPriceTree();

                syncToPeers();
                broadcastStateToClients();

                return makeResponse("DIALOG", "판매 가격을 변경했습니다.", drinkOutput, changeOutput, insertedTotalAmount);
            }
        }

        private String collectMoney() {
            synchronized (Server1.class) {
                int collected = 0;

                for (int i = 0; i < COIN_UNITS.length; i++) {
                    if (coinCounts[i] > RESERVE_COIN_COUNT) {
                        int collectCount = coinCounts[i] - RESERVE_COIN_COUNT;
                        collected += collectCount * COIN_UNITS[i];
                        coinCounts[i] = RESERVE_COIN_COUNT;
                    }
                }

                collected += bill1000Count * 1000;
                bill1000Count = 0;

                syncToPeers();
                broadcastStateToClients();

                return makeResponse("DIALOG", "수금 완료: " + collected + "원\n10개를 초과한 동전과 지폐만 수금했습니다.", drinkOutput, changeOutput, insertedTotalAmount);
            }
        }

        private String changePassword(String password) {
            synchronized (Server1.class) {
                if (!isValidAdminPassword(password)) {
                    return makeResponse("ERROR", "비밀번호 조건을 만족하지 않습니다.", drinkOutput, changeOutput, insertedTotalAmount);
                }

                adminPassword = password;

                syncToPeers();
                broadcastStateToClients();

                return makeResponse("DIALOG", "관리자 비밀번호를 변경했습니다.", drinkOutput, changeOutput, insertedTotalAmount);
            }
        }

        private String refillReserveMoney() {
            synchronized (Server1.class) {
                int added = 0;

                for (int i = 0; i < COIN_UNITS.length; i++) {
                    if (coinCounts[i] < RESERVE_COIN_COUNT) {
                        int addCount = RESERVE_COIN_COUNT - coinCounts[i];
                        added += addCount * COIN_UNITS[i];
                        coinCounts[i] = RESERVE_COIN_COUNT;
                    }
                }

                syncToPeers();
                broadcastStateToClients();

                return makeResponse("DIALOG", "기본 화폐 보충 완료: " + added + "원\n부족한 동전만 10개까지 보충했습니다.", drinkOutput, changeOutput, insertedTotalAmount);
            }
        }

        private void clearInsertedCoins() {
            for (int i = 0; i < insertedCoinCounts.length; i++) {
                insertedCoinCounts[i] = 0;
            }
        }
    }

    private static synchronized void syncToPeers() {
        String state = makeState();

        for (int i = 0; i < peerCount; i++) {
            Socket socket = null;
            BufferedReader in = null;
            PrintWriter out = null;

            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(peerHosts[i], peerPorts[i]), 1000);
                socket.setSoTimeout(1000);

                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                out.println("SYNC_STATE|" + state);
                in.readLine();
            } catch (IOException e) {
                System.out.println("[" + role + "] 동기화 실패: " + peerHosts[i] + ":" + peerPorts[i]);
            } finally {
                closeAll(socket, in, out);
            }
        }
    }

    private static synchronized String makeResponse(String result, String message, String drinkOutput, String changeOutput, int insertedTotal) {
        return "RES|" + result + "|" + encode(message) + "|" + encode(drinkOutput) + "|" + encode(changeOutput) + "|" + insertedTotal + "|" + makeState();
    }

    private static synchronized String makeState() {
        StringBuilder sb = new StringBuilder();

        for (int i = 1; i <= DRINK_COUNT; i++) {
            if (i > 1) {
                sb.append(",");
            }

            sb.append(encode(drinkNames[i]));
        }

        sb.append("|");
        appendArray(sb, drinkPrices, 1, DRINK_COUNT);
        sb.append("|");
        appendArray(sb, drinkStocks, 1, DRINK_COUNT);
        sb.append("|");
        appendArray(sb, drinkSoldCounts, 1, DRINK_COUNT);
        sb.append("|");
        appendArray(sb, dailyDrinkSales, 1, DRINK_COUNT);
        sb.append("|");
        appendArray(sb, monthlyDrinkSales, 1, DRINK_COUNT);
        sb.append("|");
        appendArray(sb, coinCounts, 0, COIN_UNITS.length - 1);
        sb.append("|").append(bill1000Count);
        sb.append("|").append(dailyTotalSales);
        sb.append("|").append(monthlyTotalSales);
        sb.append("|").append(encode(adminPassword));

        return sb.toString();
    }

    private static synchronized void applyState(String state) {
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

        rebuildDrinkLinkedList();
        rebuildPriceTree();
    }

    private static void rebuildDrinkLinkedList() {
        drinkListHead = null;
        DrinkNode prev = null;

        for (int i = 1; i <= DRINK_COUNT; i++) {
            DrinkNode node = new DrinkNode(i, drinkNames[i], drinkPrices[i], drinkStocks[i], drinkSoldCounts[i], dailyDrinkSales[i], monthlyDrinkSales[i]);

            if (drinkListHead == null) {
                drinkListHead = node;
            } else {
                prev.next = node;
            }

            prev = node;
        }
    }

    private static DrinkNode findDrinkNodeById(int id) {
        DrinkNode current = drinkListHead;

        while (current != null) {
            if (current.id == id) {
                return current;
            }

            current = current.next;
        }

        return null;
    }

    private static void updateDrinkNode(int id) {
        DrinkNode node = findDrinkNodeById(id);

        if (node != null) {
            node.name = drinkNames[id];
            node.price = drinkPrices[id];
            node.stock = drinkStocks[id];
            node.soldCount = drinkSoldCounts[id];
            node.dailySales = dailyDrinkSales[id];
            node.monthlySales = monthlyDrinkSales[id];
        }
    }

    private static int searchDrinkByName(String name) {
        DrinkNode current = drinkListHead;

        while (current != null) {
            if (current.name.equals(name)) {
                return current.id;
            }

            current = current.next;
        }

        return -1;
    }

    private static void rebuildPriceTree() {
        priceTree = new PriceTree();

        for (int i = 1; i <= DRINK_COUNT; i++) {
            priceTree.insert(drinkPrices[i], i);
        }
    }

    private static synchronized void registerPushClient(String name, PrintWriter out) {
        if (pushClientCount < pushClientOuts.length) {
            pushClientNames[pushClientCount] = name;
            pushClientOuts[pushClientCount] = out;
            pushClientCount++;
            System.out.println("[" + role + "] 실시간 동기화 클라이언트 등록: " + name);
        }
    }

    private static synchronized void removePushClient(PrintWriter out) {
        if (out == null) {
            return;
        }

        for (int i = 0; i < pushClientCount; i++) {
            if (pushClientOuts[i] == out) {
                removePushClientAt(i);
                return;
            }
        }
    }

    private static synchronized void removePushClientAt(int index) {
        for (int i = index; i < pushClientCount - 1; i++) {
            pushClientOuts[i] = pushClientOuts[i + 1];
            pushClientNames[i] = pushClientNames[i + 1];
        }

        pushClientCount--;
        pushClientOuts[pushClientCount] = null;
        pushClientNames[pushClientCount] = null;
    }

    private static synchronized void broadcastStateToClients() {
        String message = "PUSH_STATE|" + makeState();

        for (int i = 0; i < pushClientCount; i++) {
            pushClientOuts[i].println(message);

            if (pushClientOuts[i].checkError()) {
                removePushClientAt(i);
                i--;
            }
        }
    }

    private static class DrinkNode {
        int id;
        String name;
        int price;
        int stock;
        int soldCount;
        int dailySales;
        int monthlySales;
        DrinkNode next;

        DrinkNode(int id, String name, int price, int stock, int soldCount, int dailySales, int monthlySales) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.soldCount = soldCount;
            this.dailySales = dailySales;
            this.monthlySales = monthlySales;
            this.next = null;
        }
    }

    private static class SimpleStack {
        private String[] data;
        private int top;

        SimpleStack(int size) {
            data = new String[size];
            top = -1;
        }

        void push(String value) {
            if (top < data.length - 1) {
                top++;
                data[top] = value;
            }
        }

        String pop() {
            if (top < 0) {
                return null;
            }

            String value = data[top];
            data[top] = null;
            top--;
            return value;
        }
    }

    private static class SimpleQueue {
        private String[] data;
        private int front;
        private int rear;
        private int count;

        SimpleQueue(int size) {
            data = new String[size];
            front = 0;
            rear = 0;
            count = 0;
        }

        void offer(String value) {
            if (count < data.length) {
                data[rear] = value;
                rear = (rear + 1) % data.length;
                count++;
            }
        }

        String poll() {
            if (count == 0) {
                return null;
            }

            String value = data[front];
            data[front] = null;
            front = (front + 1) % data.length;
            count--;
            return value;
        }
    }

    private static class PriceTreeNode {
        int price;
        int drinkId;
        PriceTreeNode left;
        PriceTreeNode right;

        PriceTreeNode(int price, int drinkId) {
            this.price = price;
            this.drinkId = drinkId;
            this.left = null;
            this.right = null;
        }
    }

    private static class PriceTree {
        private PriceTreeNode root;

        void insert(int price, int drinkId) {
            root = insertNode(root, price, drinkId);
        }

        private PriceTreeNode insertNode(PriceTreeNode node, int price, int drinkId) {
            if (node == null) {
                return new PriceTreeNode(price, drinkId);
            }

            if (price < node.price) {
                node.left = insertNode(node.left, price, drinkId);
            } else {
                node.right = insertNode(node.right, price, drinkId);
            }

            return node;
        }

        int search(int price) {
            PriceTreeNode current = root;

            while (current != null) {
                if (price == current.price) {
                    return current.drinkId;
                } else if (price < current.price) {
                    current = current.left;
                } else {
                    current = current.right;
                }
            }

            return -1;
        }
    }

    private static void appendArray(StringBuilder sb, int[] array, int start, int end) {
        for (int i = start; i <= end; i++) {
            if (i > start) {
                sb.append(",");
            }

            sb.append(array[i]);
        }
    }

    private static boolean canMakeChange(int amount) {
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

    private static void makeChange(int amount) {
        int remain = amount;

        for (int i = 0; i < COIN_UNITS.length; i++) {
            int use = Math.min(coinCounts[i], remain / COIN_UNITS[i]);

            if (use > 0) {
                coinCounts[i] -= use;
                remain -= use * COIN_UNITS[i];
            }
        }
    }

    private static int getCoinIndex(int unit) {
        for (int i = 0; i < COIN_UNITS.length; i++) {
            if (COIN_UNITS[i] == unit) {
                return i;
            }
        }

        return -1;
    }

    private static boolean isAllowedMoney(int money) {
        for (int unit : MONEY_UNITS) {
            if (unit == money) {
                return true;
            }
        }

        return false;
    }

    private static String getCoinStatusText() {
        StringBuilder sb = new StringBuilder();
        int total = 0;

        sb.append("[자판기 내 화폐 현황]\n\n");
        sb.append("500원: ").append(coinCounts[getCoinIndex(500)]).append("개\n");
        sb.append("100원: ").append(coinCounts[getCoinIndex(100)]).append("개\n");
        sb.append("50원: ").append(coinCounts[getCoinIndex(50)]).append("개\n");
        sb.append("10원: ").append(coinCounts[getCoinIndex(10)]).append("개\n");
        sb.append("1000원 지폐: ").append(bill1000Count).append("장\n\n");

        for (int i = 0; i < COIN_UNITS.length; i++) {
            total += COIN_UNITS[i] * coinCounts[i];
        }

        total += bill1000Count * 1000;

        sb.append("총 보유 금액: ").append(total).append("원");

        return sb.toString();
    }

    private static String getSalesText() {
        StringBuilder sb = new StringBuilder();

        sb.append("[매출 조회]\n");
        sb.append("오늘 날짜: ").append(java.time.LocalDate.now()).append("\n");
        sb.append("현재 월: ").append(java.time.YearMonth.now()).append("\n\n");
        sb.append("일별 전체 매출: ").append(dailyTotalSales).append("원\n");
        sb.append("월별 전체 매출: ").append(monthlyTotalSales).append("원\n\n");
        sb.append("[음료별 일별 매출]\n");

        int current = drinkHead;

        while (current != 0) {
            sb.append(drinkNames[current]).append(": ").append(dailyDrinkSales[current]).append("원\n");
            current = drinkNext[current];
        }

        sb.append("\n[음료별 월별 매출]\n");

        current = drinkHead;

        while (current != 0) {
            sb.append(drinkNames[current]).append(": ").append(monthlyDrinkSales[current]).append("원\n");
            current = drinkNext[current];
        }

        int[] sortIds = new int[DRINK_COUNT];
        int[] sortValues = new int[DRINK_COUNT];

        for (int i = 0; i < DRINK_COUNT; i++) {
            sortIds[i] = i + 1;
            sortValues[i] = monthlyDrinkSales[i + 1];
        }

        for (int i = 0; i < DRINK_COUNT - 1; i++) {
            for (int j = 0; j < DRINK_COUNT - 1 - i; j++) {
                if (sortValues[j] < sortValues[j + 1]) {
                    int tempValue = sortValues[j];
                    sortValues[j] = sortValues[j + 1];
                    sortValues[j + 1] = tempValue;

                    int tempId = sortIds[j];
                    sortIds[j] = sortIds[j + 1];
                    sortIds[j + 1] = tempId;
                }
            }
        }

        sb.append("\n[월별 매출 정렬 결과]\n");

        for (int i = 0; i < DRINK_COUNT; i++) {
            sb.append(i + 1).append("위: ").append(drinkNames[sortIds[i]]).append(" ").append(sortValues[i]).append("원\n");
        }

        return sb.toString();
    }

    private static boolean isValidAdminPassword(String password) {
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

    private static String encode(String text) {
        if (text == null) {
            text = "";
        }

        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }

        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }

    private static void closeAll(Socket socket, BufferedReader in, PrintWriter out) {
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
    }
}